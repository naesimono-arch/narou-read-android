# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了の正本は **git log**・現況は `STATUS.md`・
> 実コミット等の一次情報は `.claude/plans/`・腐りにくい知見は `docs/knowledge/`〔1知見=1ファイル。`task_diary.md` は凍結アーカイブ〕）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ下の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[機能②・要判断] U1 新着チェックとの整合**: 続きあり判定は現状 PDF 蔵書の generalAllNo 突合のまま。Web カードの既読話数（web_reading_progress）を U1 基準へ組み込むかは別タスク（機能②実装時からの持ち越し）。
- **[取込] 「PDFを取り込む」ボタンの不安定さ**（2026-07-10 ユーザー報告・**再現条件未特定**）: FAB/空状態ボタンからの picker 起動が不安定に感じられることがある。まず再現条件の聞き取り（どの画面のボタンか・無反応か遅延か・バッテリー最適化ダイアログ絡みか）→ logcat での再現観察から。推定候補: 通知権限→picker の2段ゲート・バッテリー最適化ダイアログの分岐（`launchPdfPicker` 周り）だが未確定のため決め打ち修正はしない。
- **[目次/本文] モーション最適化 — 残りは P1/P2 のみ**（2026-07-15）: 確定差分は適用・**遷移は実機確認済みで slide push に統一**（ADR 0019・ルート遷移＋目次⇄本文を同じ向き/尺 250ms＝進む右→左/戻る左→右）／本文 stretch オーバースクロール無効化（D2・目次/本棚は据え置き）。競合解析＝`docs/reference/06-competitor-reading-motion.md`。**残**: **P1**＝章→章（話送り）は現状どおり瞬間で据え置き中＝要望が出たらスライド化（kakuyomu 400ms が実在目安・新規演出のため症状指差し待ち）／**P2〔切り分け済み・要対策〕**＝**画面遷移（slide push）が毎回軽くカクつく**（2026-07-15 報告）を **2026-07-16 に debug/release 切り分け実機計測済み**（一次情報＝`.claude/plans/reading-transition-jank-measurement-2026-07-16.md`＝再現手順＋全数値）。結果: **debug は jank を誇張するが release でも残る実問題**（本棚⇄本文が最悪＝Janky 9.35%・99%tile 93ms／目次⇄本文 6.53%・61ms＠60Hz・各≈460〜475フレーム）。中央値は両ビルド健全（9〜12ms＝定常は滑らか・遷移の瞬間だけ落ちる）・**UI スレッド律速**（gfxinfo `Slow UI thread` 支配・`Slow bitmap uploads=0`・`Missed Vsync≈0`＝GPU転送でなくメインスレッド composition/measure/layout/draw記録）。コンテンツ温めても残る＝**遷移先の初回コンポジション＋戻りの本棚グリッド〔`ShioriCover` の Canvas 描画〕がアニメ第1フレーム群と同居（仮説①）が有力**・重い読込（仮説②）は主因でない。**2026-07-16 仮説①の狙い撃ち実験を実施し棄却**（`LocalIsTransitioning`＝遷移中フラグで ShioriCover の先端＋題字スキップ＋本文コンポーズのアニメ後送り→同一端末状態のベースライン再計測との gfxinfo 比較で効果なし・目次⇄本文はむしろ悪化〔Slow UI 34→50・Missed Vsync 0→5〕＝負荷は消えず移動しただけ。全数値と判定根拠＝同 plan 追試節・パッチは revert 済み）。**次の一手＝Perfetto escalate**（`runtime-tracing` 計装込み再ビルド。gfxinfo の狙い撃ちでは収束せず＝当初基準どおり。候補の主因＝NavHost 遷移自体のレイヤ記録・目次画面コンポーズ・テキスト measure 等は未切り分け）。
- **[本棚] 栞のワンポイント（先端意匠）＋棒の長さをPDF取込時に完全ランダム抽選→固定**（2026-07-15 ユーザー提起・要`/db-migration`ゲート）: 現状 `ShioriGenerator.kt`の`tipIndex`・`lenFrac`は色相・位置と同じくタイトル文字列のハッシュ→擬似乱数（mulberry32）で決定論的に導出（同じ本は常に同じ絵。コード冒頭コメント・HTMLモック正本対応・`ShioriGeneratorTest`の前提）。**方針確定**: 取込の瞬間に真の乱数で1回抽選→以後その本はDBへ永続化してずっと固定（色相・xFrac位置は現状通りタイトル由来の決定論のまま据え置き＝対象は先端意匠と長さのみ）。**設計素案（着手時の備忘・未実装）**: ①`BookEntity`へ`shioriTipIndex: Int?`・`shioriLenFrac: Float?`相当の列を新設・nullable・Migration v18→v19は`ADD COLUMN ... DEFAULT NULL`（`ncode`/`contentSha256`と同じ既存パターン）②既存蔵書はNULLのまま→描画側nullなら旧来のタイトル由来決定論値へフォールバック＝**着手前に取り込んだ本の見た目は変わらない**③乱数の発生源は`DefaultBookRepository.kt:277`のBookEntity生成箇所（先端の総数の正本はCompose非依存の`ShioriGenerator.kt`側に定数化し、repositoryが`ShioriCover.kt`＝Compose依存ファイルをimportしない設計）④`shioriParams(title, tipCount, persistedTipIndex, persistedLenFrac)`のように非nullなら該当乱数消費をスキップして差し替え⑤`WebBookCard`（未取込のweb発見カード・BookEntity無し）は据え置きで現状のタイトル由来決定論のまま。**未着手＝実装なし**（今回はタスク積みのみ）。
- **[目次・対象画面/挙動は要確認] 既読話は一覧の最下部から表示**（2026-07-15 実機フィードバック）: 既に読み終えた話は一覧を開いた際に一番下から表示（＝現在地付近へ自動スクロール）にしたい。対象画面（目次かシリーズ一覧か）・現状の起点挙動は着手時に要確認。
- **[読書画面] 最上部へ戻るボタンの追加**（2026-07-15 実機フィードバック・新機能）: 本文内で最上部まで一気に移動できるボタンを新設。
- **[読書画面] 縦書き表示モードの実装**（2026-07-15 実機フィードバック・新機能）: 縦書きで読めるモードをまだ実装していない（現状は横書きのみ＝`717045e`で確定した実装事実）。新規実装として要設計（意匠絡み＝`/visual-language`ゲート）。
- **[取込/削除] 本削除時にPDF本体も削除するか確認するダイアログ**（2026-07-15 ユーザー提起）: 現状は前提が無い——取込時のPDFは`cacheDir`への一時コピー→変換→即削除（`DefaultBookRepository.kt:184`付近）で、`BookEntity`スキーマに取込元URIを保持する列が無い（books は取込元 URI を持たない、と既存コメントに明記済み）。変換完了後は`takePersistableUriPermission`も解放される。**単純なダイアログ追加ではなく設計変更が要る**: ①取込元URIをスキーマへ永続化するmigration ②変換後も読み取り+書き込み永続権限を保持し続ける必要（端末上限128件の予算を消費し続ける・現状は用済み次第解放する設計と衝突） ③削除実行時、プロバイダ都合でdeleteが失敗しうる（既に移動/削除済み・権限失効・書き込み非対応プロバイダ）ハンドリングが要る。
- **[読書画面] Backスタックを「覗きcollapse」方式へ再設計（07/12決定を実機体感で棄却）**（2026-07-15）: 現行（`NativeReadingScreen.kt:155-235`）は「本棚＞目次＞本文」固定2階層でBackを常にcollapseする方式（07/12・旧navHistory全逆再生バグの対処として導入）。**実際に使うとこの固定2階層自体が悪UX**——本棚から目次を経由せず直接本文へ来た場合もBackが目次を強制通過する。**新方針**: Backは実際に辿った経路を反映しつつ、「覗き」（目次⇄章のプレビュー往復、既存の`jumpOrigin`＝参照ジャンプと同じ区別）は新しい段を積まず置き換える（Jetpack Navigationの`popUpTo(inclusive=true)`相当）。本棚→本文直行ならBack1発で本棚、目次から章を何度覗いてもスタックは増えない＝旧navHistoryバグ（覗くたび段が積まれる）も再発させない。**詳細は実装しながら調整**（ユーザー方針＝わからない箇所は使ってみて手戻しでよい）。App barの←（Up）ボタンは別呼び出し経路（各画面が直接呼ぶ）のため、今回のBack再設計と同じ挙動にするか据え置くかは着手時に判断。
- **[PDF変換] 抽出ロジックのハードコード脱却（座標/フォントサイズの絶対値依存を解消）**（2026-07-15 ユーザー提起）: `ParserRules.kt`の判定定数（`FONT_SIZE_BODY_TITLE=14.0`・`FONT_SIZE_RUBY=7.0`・`FONT_SIZE_PAGE=12.0`・`PAGE_NUM_Y=528.98`・`RUBY_OFFSET_X=14.84`・`LINE_STEP_X=22.68`等）が現行のPDF出力形状に絶対値で超ハードコード＝**PDF提供元（なろう側）がほんの少しでも生成側の数値を変更したら一瞬で全滅する**という脆さが本質（「別形状のPDFへの対応」ではなく、同じ形状のまま起きる微小変更への耐性が目的）。`TextProcessor.kt`（本文分類/ルビ結合/空行判定/ページ番号除去）・`PdfExtractor.kt`（表紙著者名判定/最大フォントサイズ判定）が全面依存。**方向性**: 絶対値ではなく相対値/自動検出（本文フォントサイズは最頻値から動的検出・ルビは絶対オフセットでなく親文字サイズ比、ページ番号は絶対Y座標でなくページ下部相対位置、等）へ寄せて微小な数値変更を吸収できる柔軟性を持たせる。**注意**: 変更はゴールデン回帰ハーネス（`ab-review/golden_regression`・N2959KI/N6169DZ等の既知の際どい確定挙動を含む）で検証必須＝所在は memory `golden-regression-baseline`。

---

## ★UX/Design 全層監査 — 残タスク（2026-07-12）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理候補）に対し novel-reader 全体を多エージェント監査（45体・敵対的検証済み）した指摘の、**残っている作業だけ**の action list（消化したら行を消す）。
> 消化済み分の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A 統合報告／§B 全指摘詳細・良い点含む）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）・実装＝git log（`ui/polish`）。
> **対象ブランチ**: `ui/polish`（この worktree＝ext4）。ゲート＝`cd android && testDebugUnitTest`（init-script 不要）＋`python3 tools/check_design_tokens.py`。**意匠絡みは Compose で自己判断せず ADR0005/0014＋モック正本に先に接地**。実機絡みは PushNotification→目視OK→コミット。

### 残0: main への統合
- 残1（発見帯 collapse）の完全退避が実機OKで一段落＝`ui/polish` を main へ --no-ff 統合可（コミットは worktree 内セッションから＝guard の機序は memory `ff-merge-sentinel-not-consumed`）。

### 残1: 発見帯 collapse 退避アニメ 体感の追い込み（deferred polish・ADR0005-B 実機後詰め層）
- 本棚発見帯『新しい物語を見つける』の **collapse は「完全退避」で確定・実装・実機OK 済み**（2026-07-14）。裁定の経緯＝案B「スクロール退避」の初回翻訳（下スクロールで帯を1行版へ restyle）を実機却下→**同一要素の restyle をやめ、帯は restyle せず `shrinkVertically`＋fade で高さ0へ畳んで退避・状態フィルタは sticky で常時 top に残す**へ再設計（ユーザー原則「位置/役割が同じなら restyle しない・退避のときだけ消す」）。帯＋フィルタを Lazy 外の固定ヘッダへ hoist（`LazyVerticalGrid` に stickyHeader 無し＝グリッド/リスト両モード一律 sticky の素直な解）・帯の可視は `derivedStateOf`（先頭到達＝先頭書影が最上部付近〔8dp デッドゾーン〕のみ true）で駆動。
- **残るのは退避アニメの体感が『不足』**（2026-07-14 実機・ユーザー所見・「まぁいい」で現状採用）＝終了時スナップ（slideOut の予約スペース一括除去）は shrink 化で解消したが、**閾値トリガの AnimatedVisibility（8dp 超で 150ms 縮小）はスクロールと完全連動しない**ため、退避開始のタイミングに軽い不連続感が残る。**要すればスクロール連動方式へ再設計**＝band 高さ∝スクロールオフセットを nestedScroll で連続縮小する collapsing header 本来型（duration/easing のトークン調整でも一部改善余地）。**発見は第二の柱＝いずれ本節ごと再調整予定**（ユーザー 2026-07-14）。
- 試作/裁定の記録＝`docs/design-candidates/discovery/bookshelf-band-collapse-D.html`（却下1行restyle vs 完全退避の実スクロール対比）・`bookshelf-band-tailtile-D.html`（完全退避 vs 案C末尾タイル）。原型4案＝`bookshelf-band-reposition-D.html`。ADR0005/0014 接地。

### 残2: 要検証 実機送り（6件・`/device-verify`・コード修正で閉じない）
- 回転レース: 回転直前の最終スクロールデルタ(≤400ms)が保存に間に合わずレース→章を読みつつ即回転反復で DB 突合。
- 回転オフセット: 形態遷移（回転/折り畳み）で段落内 px オフセットが指す行がずれる→長段落で回転し何行ずれるか。
- ノッチ横向き: 横向き/サイドノッチで行頭・行末がカットアウトに欠ける→`displayCutout` 合成を横向き目視。
- TalkBack到達: 没入バー退避時に戻る/目次/前後章へ TalkBack スワイプ走査で到達可能か。
- ルビ掛け: 長ルビ・隣接ルビ間アキ制御なし→長ルビ実データで実機目視。
- 大フォント行数: 大フォント×広余白で1行字数が極端減（18sp≒18字/24sp≒11字）→各設定の体感リズム確認。

### 残3: 人間テスト（第三者便のみ残）
> 本人＋実機で可能な分は 2026-07-14 に消化済み（T1/T5/T6/T7=OK・T2=要改修→★残7⑤。結果＝`.claude/plans/usability-test-results-2026-07-14.md`・派生改修＝★残5〜8）。残るのは**初見/時間差が本質で本人テスト不成立の2件**＝T3（二読書面の操作言語混乱）・T4（中央タップトグルの再発見。コード事実＝ヒントは通算1回きり・再表示条件なし）。実施は第三者ユーザビリティテスト便で（プロトコル＝`.claude/plans/usability-test-protocol-2026-07-12.md`）。

### 残5: 人間テストT6派生＝読書クローム出没の視覚ノイズ2件（2026-07-14 本人実機・要改修）
- **①バー出没時に本文がちらつく**（中央タップ・スワイプ復帰の両経路で発生）: 候補＝バー出没に伴うインセット/パディング変化で本文がリフロー or 再コンポーズの一瞬の空白。**決め打ち修正禁止**（memory `feedback-perf-jank-is-real-signal`）＝まず再現を録画/レイヤ検査で機序特定してから。
- **②没入⇄復帰のたびにステータスバー領域が黒⇄明灰で明滅**（目がチカチカする・ユーザー原文）: 候補＝システムバー hide/show でステータスバー背景が「黒（非表示時の地）⇄明灰（表示時の背景色）」に急変。edge-to-edge で本文をステータスバー背後まで敷き常時同色にする／読書中は常時非表示に固定する等の方式比較から（意匠絡み＝ADR0005/0014 接地の上で）。
- 一次情報＝`.claude/plans/usability-test-results-2026-07-14.md` T6。

### 残6: 作品詳細の固定バー＝既読分岐でPDF取込が降格される一貫性欠如（2026-07-14 ユーザー裁定・要改修）
- **症状**: 未読では「縦書きPDFを取り込む」が藍の主CTA最上段だが、一度「ブラウザで読む」を押す（`lastReadEpisode > 0`）と主CTAが「第N話のはじめから読む」＋「最初から（目次）」に変わり、取込がゴースト第3位へ追いやられる。**UIとしての一貫性がない＝修正対象**（ユーザー裁定 2026-07-14。2026-07-12 の「既読では続きからが主」裁定を上書き）。
- **コード所在**: `NovelDetailScreen.kt:260-299`（既読分岐の主CTA）・`:391-413`（降格された取込ゴースト）。付随バグ的事実＝「最初から（目次）」（`:289`）だけ .btn-ghost トークン未適用の素 OutlinedButton（contentColor=primary藍）＝兄弟のゴーストより明るく見える。
- **進め方**: 意匠絡み＝`/visual-language` ゲート・discovery-detail-D.html（モック正本）の改訂とセットで階層を再設計（取込を既読でも上位に保つ形）。一次情報＝`.claude/plans/usability-test-results-2026-07-14.md` 派生所見。

### 残7: 取込フィードバック/通知系（2026-07-14 実使用フィードバック・要改修）
> ①終端通知のFGS同一ID道連れ除去バグ・②重複の前面無言棄却・§86撤回（完了通知の常時発行）は 2026-07-14 に修正済み＝git log が正本。残りは以下。
- **⑤表示設定シートにスコープ予告なし（T2確定）**: 配色トグル周りに「すべての画面の配色」等の全書籍スコープ明示を追加（意匠絡み＝モック正本とセット）。
- **⑥処理中バナー下部のプログレスバーが進捗を反映しない**（2026-07-15 実機フィードバック・未確定）: `ProcessingBanner.kt:181` の `LinearProgressIndicator` を `Animatable` で `stepLocalPercent` へ `animateTo` 駆動しているが、実機でバーが伸びない。候補＝①中間の `stepLocalPercent` 更新イベントが実機の抽出速度で十分来ず 0f 付近で止まる ②高頻度更新で `animateTo` が `tween(MotionDurationProgress)` 完了前に毎回リスタート ③尺と更新間隔の不整合。決め打ち修正でなく実機で `stepLocalPercent` の実際の流れ（logcat）とバー描画を計測してから（memory `feedback-perf-jank-is-real-signal`）。**関連**: 同バナー phase から数値%を外した（ページ数の可読化）ため数値進捗はページ数のみ＝このバー修正の優先度が上がった。

### 残8: 本棚操作の要望2件（2026-07-14 実使用フィードバック・新機能）
- **①複数選択→まとめて削除**: 長押しから選択モード等。削除系＝実機検証は捨て本で（memory `device-verify-delegation-no-destructive-on-real-library`）。
- **②並び順の改訂**: 取り込んだ本は最上位（未読ゆえ）→以降は「最後に触った順」。現行の二層ソート（ADR 0016）の改訂＝ADR追補とセットで設計。

### 残4: 監査派生 backlog（新規タスク）
- **蔵書内フィルタ/series 束ね UI**（確認バッチC④＝保留）: ロジック `filterBooksByQuery` は実装済み・UI はモック未表現のため保留（`BookshelfScreen.kt:442`／`ShelfItems.kt:37`）。series 束ねはスキーマ変更要（設計案のみ）。
- **目次の部/編 折り畳み**: 抽出パイプラインに階層データ無し＝**抽出側の新機能**。実PDF→HTML の階層有無は要検証で「フラット確定」＝畳みは前提データ欠如で現状不成立。
- **Macrobenchmark 新設**: measure 要検証（大PDF/10倍蔵書/長時間送りの予算漸進劣化を P90/P99 で assert）＝独立タスク。INTERNET 無しで出荷後テレメトリ不能の代替。
- **lint 残 warnings（任意改善・非ブロック）**: UsableSpace×2（`DefaultBookRepository.kt` の抽出前空き容量チェック）＝`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなり ENOSPC で変換終盤失敗を招くため、現状の保守的 `usableSpace` は意図的。触るなら API26 分岐・例外処理込みの設計判断が要る（純機械修正ではない）。※ ModifierParameter×3 は Compose 規約準拠で解消済み。

## UI/UX 宿題

- **[モック追従・構造] 発見系モックの情報/装飾テキスト再分類**（2026-07-12 `a9a6a5c` 実装時に留置）: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ引き上げ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の discovery/*.html モックへの追従は、`--ink-soft` を共有する **10〜16箇所/ファイルの情報・装飾テキストの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` へのマッピング追加が必要＝構造的大改修と判定し留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレ（コードが AA へ引き上げた面をモックが `--ink-soft` のまま持つ）は未検知**になる点に注意。

## なろうAPI 発見・検索機能（第2の柱）

> Phase 0〜4 完了（現況＝`STATUS.md` §0・実装＝git log）。目標ロードマップ・作る機能一覧の一次情報＝plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」。

## リファクタ / 技術的負債（deferred）

- **検索画面 S3＝カテゴリ列の LazyColumn 化（保留・要否判断）**: 重さの正体は「カテゴリ展開状態での操作毎の全画面再コンポーズ」で、S1（選択判定 Set 化・Regex 定数化）/S2（strong skipping＋@Immutable）は解消済み・実機体感は軽快（2026-07-11 実測）。残る理論コスト＝非 Lazy Column 上の22カテゴリ/115チップ（`DiscoverySearchScreen.kt:203-207`）の画面外存在コストと「全展開のまま再訪」の初回構成。**体感問題が再報告されるまで保留が妥当**。
- **R8/リソース収縮が無効**（`android/app/build.gradle` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。

## workflow / tooling

- **[bestpractice 突合の回収候補（2026-07-12 調査）]**: ①スキル frontmatter の `triggers:` は標準外フィールドの可能性（自動発動は `description` 単体が正）＝ハーネスに解釈されているか検証 ②`block_destructive_migration.py` の Bash 経路が素朴な部分文字列一致（`FOO=1 cmd`・`$()` ですり抜け）＝settings permissions の `if` フィールド化を検討（主経路の Edit/Write 捕捉は健在で実害小） ③サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う）。

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発): agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。
- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする。基準＝0 errors/28 warnings（2026-07-15 時点＝ModifierParameter×3 解消後）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分は **ADR 0006（増補含む）と git log が正本**。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。**進捗（2026-07-11・増補6）**: Tier E カテゴリ別突合が**現ターン分**の同根系列を吸収した。**過去ターンの汎用主張の掘り下げは引き続き将来課題**（Tier E は現ターン境界での判定）。
- **[2026-07-11・増補6 で切り出し] L44/L55 型「先行実行フレーミング」（完了主張形を持たない幻の先行実行参照）の検知**: 事象N の L44「出力が返っていないので結果を確認します」（存在しない先行 tool_use への参照）・L55「再作成します」（未実行の再作成宣言）は**完了・検証の断言ではなく段取り宣言**＝照合キーが無く Tier E の完了語トリガに当たらない。検知には「主張以前に対応する tool_use が無い先行実行参照」という別系統の突合が要る。要較正・真陽性は台帳N の L44/L55。
- **[2026-07-11・増補6 保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、**委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合**して裏取りする案。**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。真陽性が観測されたら着手。
- **[2026-07-11・増補6 副産物] 370800c1 の台帳レター事象化（人間確認待ち）**: D4 較正の slug 全走査で発見した未登録真陽性＝assistant text 内に `user[Request interrupted for tool use]`＋幻のユーザー発話を自己生成（幻発話テキストの実人間入力不在は transcript 直読で確認済み）。D4（CLI）と A3 変種拡張（live）の両方で active。人間確認が取れたら台帳の追記手順（事後検証モード）でレター事象化。
- **[2026-07-11・増補6 残課題] D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合の導入は将来課題。
- **[2026-07-11・増補6 で切り出し] Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **[抽出] 単話（1話完結）作品の縦書きPDF変換で、本文が「作品情報（プロローグ）」側に乗り章題名も出ない**（2026-07-09 PDF取り込み導線の実機通し検証中にユーザー観測・対象 n2959ki）: 単話作品は章見出し／目次構造が無いため、章分割が本文を作品情報ページの続き扱いで流し込むと推定（**未確定**・要調査）。**やること**: ①n2959ki の抽出結果現物（`novels/<id>/index.html`・`chap_N.html` 構成）で事象を再現確認 ②単話 PDF の構造に対する分割ルール（`ParserRules`/`ChapterProcessor`）の扱いを設計 ③**ゴールデン基準との整合に注意**＝N2959KI はゴールデン本（`ab-review/golden_regression`）であり、基準自体がこの挙動を「正」として固定している可能性がある——修正はゴールデン更新とセットで判断すること。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/archive/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。栞の他4型（A箔/C小口/D蔵書印/E綴じ紐）もスキン資産として保持（ADR 0005 C 方針）。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。
