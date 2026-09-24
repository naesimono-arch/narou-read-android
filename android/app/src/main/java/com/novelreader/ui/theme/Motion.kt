package com.novelreader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

// ============================================================
// motion トークン（duration/easing/spring のスロット）
// 原則: motion はフィードバック（状態変化の伝達）のみ。装飾アニメ・自動ループは無し。
// なぜトークン化するか: ADR 0005-B の「後詰め層」に motion の正本がゼロという穴があり、
// 実測値が各画面へ散在・重複していた。値そのものは実機調整で更新してよいが、
// スロット（duration/easing/spring）と上記原則を正本化する＝ADR 0014 §motion。
// したがって値の変更は必ずこのファイル1箇所で行い、呼び出し側は直書きしない。
// ============================================================

// カードのタップ押下スケール（Apple Books 的な触感）。
// BookCard（書架/目録）・WebBookCard（Web 由来カードの書架/目録）の 4 箇所で共有する押下フィードバック。
// なぜ NoBouncy か: Design/08 禁止則③（overshoot/bounce/spring 振動の禁止）＋同 G 適用例
// 「押下フィードバックにカスタムのスケールバウンスを足さない」に旧値 dampingRatio=0.6f が抵触
// （復帰時にわずかに跳ねる＝2026-07-12 UX/Design 全層監査 d-motion Major）。stiffness=400f の
// 素早い追従は維持し、跳ねだけを除去する。押下の targetValue（0.96/0.98）は
// 呼び出し側の意匠差なのでトークン化しない（スロットは spring 仕様のみ）。
val MotionSpringCard: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

// 読書画面の没入バーを全表示/全非表示へ吸着させる settle 用 spring（NativeReadingScreen.settleTopBar）。
// StiffnessMediumLow のバウンシー挙動でバーの出没を軽快に見せる（自前 settle の触感復元。詳細は使用側コメント）。
val MotionSpringBarSettle: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

// PDF 処理中バナーの進捗バーが目標値へ追従する spring。ステップ切替時の瞬時リセットは
// 使用側の key(stepIndex) 状態再構成で行う＝アニメではないためトークン外。
// なぜ尺固定 tween（旧 MotionDurationProgress=400ms）でなく spring か（2026-07-16 実機計測・残7⑥の真因）:
// 進捗は抽出のページ単位で高頻度（実測10〜25ms間隔）に更新され、尺 tween を都度張り直すと毎回キャンセル
// されて easing 序盤の平坦区間しか進まない（表示値が最大約1%で張り付き・アニメ完了0回を実測）。
// spring は retarget 時に現在速度を引き継ぐため高頻度更新でも前進が途切れない。バウンドで進捗が
// 逆行して見えるのは不適のため NoBouncy。剛性 StiffnessLow＝目標到達おおよそ数百ms で旧 400ms の体感を近似。
// 進行類型が禁止則①（350ms 上限）の適用外である裁定は ADR 0014「適用裁定の記録」（2026-07-12・確認バッチ E）。
val MotionSpringProgressFollow: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)

// バナー等の入退場 duration（ms）。Design/08-C（enter/exit は別指定・exit は enter より短い＝加速して消える）
// に基づき2値で彫る。なぜ2値か: 出現は「気づかせる」ため長め、退場は「作業の邪魔をしない」ため短め、
// と目的が異なる（reveal 250 / dismiss 150。禁止則①の 350ms 上限内）。
const val MotionDurationReveal: Int = 250
const val MotionDurationDismiss: Int = 150

// ヒント・題字などの fade/crossfade 用 duration（ms）。復帰ヒント（NativeReadingScreen）・
// 詳細画面バー題字（NovelDetailScreen）等の「そっと現れて消える」同型演出で共有する。
// なぜトークン化するか: Design/08 禁止則②（duration/easing を野良既定に委ねずトークン経由）。
const val MotionDurationCrossfade: Int = 250

// 画面遷移（NavHost の横スライド push）と目次⇄本文の切替（NativeReadingScreen の AnimatedContent）で
// 共有する duration（ms）。なぜ新設か: NavHost に遷移指定が無く navigation-compose 2.7 系の野良既定
// fadeIn/fadeOut(tween(700)) が全遷移に効いていた＝禁止則②（duration/easing を野良既定に委ねずトークン経由）に
// NavHost だけが抵触。なぜ 250ms か: 競合5アプリ実測では読書系の実尺は 100〜250ms・章送りでも 400ms
//（docs/reference/06 §3）で、既定 700ms はその倍以上＝「もっさり」の第一容疑。250ms は離散的な enter/exit を
// 縛る禁止則①の 350ms 上限内。遷移の型を fade でなく slide push にした裁定＝ADR 0019（3案実機比較）。
// motion は ADR 0005-B 実機後詰め層のため値・型とも実機で決めてよい（HTMLモック非対象）。
const val MotionDurationNavTransition: Int = 250

// 明快K: ボトムナビのタブ切替（本棚⇄さがす⇄設定）＝Pager の animateScrollToPage の尺
// （TabPagerHost・MainActivity の onSelectTab。DiscoveryHomeK のランキング Pager も同スロットを踏襲）。
// なぜタップも slide（ページスライド）か: タブの Pager 化でスワイプは指追従のスライドになったため、
// タップだけ旧 crossfade だと同じ切替の運動言語が割れる＝タップも同トークン長のページスライドへ統一
// （旧 crossfade は廃止＝ADR 0022 追記 2026-07-24。旧理由「方向を語る slide は誤った空間語彙」は
// Pager 化以前の、バー静止＋コンテンツ入替え前提の裁定だった）。
const val MotionDurationKTabSwitch: Int = 150

// M星図スキンだけの画面遷移＝フェードスルー（退出 fadeOut 先行→進入 fadeIn。ADR 0019 追記「M星図の例外」・
// 2026-07-19 ユーザー裁定）。なぜ M だけ slide でなく fade か: M は固定天球（常駐 backdrop）を全画面で共有する
// アーキテクチャで、slide は「世界（空）ごと」動かしてしまい壁紙が切り替わる違和感を生む＝コンテンツのみを
// シームレスに差し替えるフェードにする。方向概念が消えるため pop も同型（対称）。尺は Nav 遷移バジェット
// 250ms 内で二分（退出を先行させ、進入は退出ぶんだけ遅らせて後半に）。NavHost と目次⇄本文 AnimatedContent で共有。
const val MotionDurationSeizuFadeOut: Int = MotionDurationNavTransition / 2       // 退出（先行・0..125ms）
const val MotionDurationSeizuFadeIn: Int = MotionDurationNavTransition / 2        // 進入の尺（125ms）
const val MotionDurationSeizuFadeInDelay: Int = MotionDurationNavTransition / 2   // 進入の遅延（退出ぶん＝先に退出）

// 表示設定シートのライブプレビュー退避（案3「一行残し」・2026-07-29 ユーザー裁定。
// 正本モック＝docs/design-candidates/reading-settings-livepreview-D.html の PROPOSAL 較正値）。
// スライダーを押している間だけシート・スクリム・読書クローム（上下バー）を引き、離すと復帰する。
// なぜ復帰(260ms)を引き(160ms)より長くするか: 「手を離した瞬間にシートが戻ってくる圧」を弱め、
// 本文の効き目を見た後の視線移動に余韻を残すため（モック PROPOSAL コメントの写経）。
// easing はモック CSS の ease-out / ease を CubicBezierEasing で同値写経する
//（禁止則②: duration/easing を野良既定に委ねずトークン経由）。両尺とも禁止則①の 350ms 上限内。
const val MotionDurationSettingsPeekHide: Int = 160
const val MotionDurationSettingsPeekReturn: Int = 260
val MotionEasingSettingsPeekHide: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f) // CSS ease-out
val MotionEasingSettingsPeekReturn: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS ease

// 読了バッジ「了」の押印（案A・ADR 0014 §motion 追補「適用裁定の記録」）。本棚がある本を
// 「初めて読了として描く」瞬間に一度だけ再生する朱印のスタンプ。値の組み立て（scale 1.2→1.0 の単調ダウン＋
// 回転 -7°→0°＋透過）は BookCard の seal graphicsLayer 側で行い、ここは duration/easing スロットのみ正本化する
//（禁止則②: 野良既定に委ねない）。なぜ overshoot/bounce 無しか: 禁止則③（overshoot/bounce/spring 振動の禁止）に
// 触れないため scale を 1.0 未満へ揺り戻さない単調ダウンで「押し当てて離す」を表現する（easing は着地の減速感を出す
// 強い ease-out）。なぜ 220ms か: 離散的な enter 類型として reveal 上限 250ms 内（禁止則①を満たす）。
// 読了という状態変化の伝達＝原則5「静謐＝フィードバックのための motion」に合致（装飾でない・初回一回きり・自動ループ無し）。
const val MotionDurationSeal: Int = 220
val MotionEasingSeal: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

// 「さがす」検索範囲チップの**最後の1つ**を押したときの応答＝注記の横揺れ（案A・2026-08-07 ユーザー裁定。
// 正本モック docs/design-candidates/skins/search-range-lock-A.html の `@keyframes nudge` / `.22s ease-in-out`）。
// なぜ必要か: 制約を「押せなさ（disabled で淡色化）」で示すのをやめた結果、押下が状態を変えない
// ＝そのままでは無反応（死んだアフォーダンス）になる。「入力は届いた／でも外せない」だけを動きで返す。
// なぜ同値の MotionDurationSeal(220) を借りずスロットを足すか: 値が同じでも意味が別（押印＝状態変化の祝祭／
// こちらは状態が変わらないことの通知）で、片方を実機調整したときにもう片方が巻き添えで動くのを避ける。
// なぜ 220ms か: モック較正値。離散的な enter/exit を縛る禁止則①の 350ms 上限内。
// なぜ ease-in-out か: 往復（0→−→＋→0）の折返しを角立てずに繋ぐ。色の点滅は伴わない（意味の無い色変化を作らない）。
// 揺れ幅（振幅）は呼び出し側の意匠差なのでトークン化しない（押下スケールの targetValue と同じ扱い）。
const val MotionDurationRangeLockNudge: Int = 220
val MotionEasingRangeLockNudge: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f) // CSS ease-in-out

// 教示カード列（IntroOverlay）の 1 枚めくり＝**横スライド（shared axis X）**。正本モック
// docs/design-candidates/tutorial-onboarding-K.html の `@keyframes oslide`（.25s / cubic-bezier(0,0,.58,1) /
// 40% までフェードを待つ）を同値で写経する。移動量は新しい寸法を発明せず Spacing.S32（32dp）を使う。
// なぜ新設か: 2026-09-07 のユーザー実機所見「『次へ』をタップするたびにパッと切り替わる」＝めくりに
// 遷移が一切無く、カードが差し替わるだけだった。
// なぜ同値の MotionDurationNavTransition(250) を借りないか: あちらは**画面そのもの**の push、こちらは
// **1 枚の面の中で中身だけ**が入れ替わる＝意味が別で、片方を実機調整したときにもう片方が巻き添えで
// 動くのを避ける（Seal/Nudge/EdgeFade と同じ判断）。禁止則①の 350ms 上限内。
// なぜカード幅ぶんの押し出し（full push）にしないか: 320dp 級の面を 250ms で走らせると小さな面の中では
// 騒がしく、原則5「静謐＝フィードバックのための motion」から外れる。移動は**方向を語るだけ**にし、
// 入れ替わりはフェードが担う。
const val MotionDurationIntroCardFlip: Int = 250

// 上のフェードスルーの内訳＝**退場が先（100ms）→ 入場は退場ぶん遅れて 150ms**（合計が上の尺と一致する）。
// なぜ同時クロスフェードにしないか: 32dp しか動かさないため、同時に薄く重ねると 2 枚の文字がほぼ同じ位置で
// 二重に見える（移動量の大きい push なら潰れる重なりが、小さい移動では読めなくなる）。
// 型は M星図の MotionDurationSeizuFadeOut/In/InDelay と同じ「退出先行のフェードスルー」だが、あちらは
// 画面遷移の尺を二分した値＝用途が別なので借りない。
const val MotionDurationIntroCardFlipFadeOut: Int = 100
const val MotionDurationIntroCardFlipFadeIn: Int = 150
val MotionEasingIntroCardFlip: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f) // CSS ease-out（モック写経）

// 横スクロール行の端フェードの点灯/消灯（2026-08-20 ユーザー裁定①。正本モック
// docs/design-candidates/skins/bookshelf-K.html・同 -D.html の `.fade{transition:opacity .18s ease}`）。
// なぜ motion が要るか: 点灯条件は canScrollBackward/canScrollForward＝**端に触れた瞬間に真偽が跳ねる**ため、
// 尺ゼロだと指を止めた位置で帯がパチンと現れ/消える（スクロールの連続感の中で唯一そこだけ離散的になる）。
// 状態変化（まだ続く／もう無い）の伝達であって装飾ではない＝原則5に合致・禁止則①の 350ms 上限内。
// なぜ同じ CSS ease の MotionEasingSettingsPeekReturn(260ms) を借りないか: 値が近いだけで意味が別
//（あちらは面の退避と復帰）＝片方を実機調整したときにもう片方が巻き添えで動くのを避ける（Seal/Nudge と同じ判断）。
const val MotionDurationEdgeFade: Int = 180
val MotionEasingEdgeFade: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS ease
