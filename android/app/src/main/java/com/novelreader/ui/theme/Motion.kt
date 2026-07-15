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

// 読了バッジ「了」の押印（案A・ADR 0014 §motion 追補「適用裁定の記録」）。本棚がある本を
// 「初めて読了として描く」瞬間に一度だけ再生する朱印のスタンプ。値の組み立て（scale 1.2→1.0 の単調ダウン＋
// 回転 -7°→0°＋透過）は BookCard の seal graphicsLayer 側で行い、ここは duration/easing スロットのみ正本化する
//（禁止則②: 野良既定に委ねない）。なぜ overshoot/bounce 無しか: 禁止則③（overshoot/bounce/spring 振動の禁止）に
// 触れないため scale を 1.0 未満へ揺り戻さない単調ダウンで「押し当てて離す」を表現する（easing は着地の減速感を出す
// 強い ease-out）。なぜ 220ms か: 離散的な enter 類型として reveal 上限 250ms 内（禁止則①を満たす）。
// 読了という状態変化の伝達＝原則5「静謐＝フィードバックのための motion」に合致（装飾でない・初回一回きり・自動ループ無し）。
const val MotionDurationSeal: Int = 220
val MotionEasingSeal: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
