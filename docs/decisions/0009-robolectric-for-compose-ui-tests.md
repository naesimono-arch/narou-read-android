# 0009. 葉 Composable の UI テストは Robolectric（JVM）で回す

- ステータス: Accepted
- 日付: 2026-07-08
- 出自: chrisbanes-skills `compose-ui-testing-patterns` 観点のテスト投資（handover 系統レビュー残）。state+callback の理想形なのに UI テスト0本だった葉 Composable への担保追加。
- 関連実装: `app/build.gradle`（testOptions・test 依存）・`src/test/java/com/novelreader/ui/**`（新設テスト）・対象葉 Composable の `@Preview`
- 意匠の上位規範: ADR 0005（視覚言語D）。本 ADR は「テストの実行方式」の判断であり見た目の正本は引き続きモック＋0005。

## Context（背景）

`ReadingErrorScreen` / `DiscoveryStatusBox` / `NativeTableOfContentsScreen` / `ContinuationCard` / `ReadingSettingsSheet` は、いずれも `state` を引数で受け `callback` を上へ渡すだけの純表示 Composable（ホイスト済みの理想形）に整っている。にもかかわらず UI テストが0本で、次のような退行を機械で捕まえられなかった:

- 状態分岐の描画（`TocState` 4状態の出し分け・`DiscoveryStatus` の Loading/Empty/Error 排他・`ContinuationInfo` の NewEpisodes/UpToDate によるボタン出し分け）。
- コールバック結線（章クリック→ファイル名付き `onSelectChapter`・再試行→`onRetry`・テーマチップ→`onThemeChange` 等）。

Compose UI テストの実行環境は実質2択だった:
1. **(a) Robolectric（JVM 上でシミュレートし `testDebugUnitTest` に同乗）**
2. **(b) androidTest（`connectedAndroidTest`＝実機/エミュレータ必須）**

## Decision

**葉 Composable の semantics ベース UI テストは (a) Robolectric で書き、`testDebugUnitTest` ゲートに同乗させる。**

- 依存: `org.robolectric:robolectric:4.11.1`（compileSdk/targetSdk 34 を正式サポートする安定版）＋ `androidx.compose.ui:ui-test-junit4`（compose-bom 2025.02.00 管理下・版指定不要）＋ `ui-test-manifest`（`createComposeRule` の ComponentActivity 供給）。
- `testOptions.unitTests.includeAndroidResources = true` を有効化（Robolectric が merged manifest/リソースを読むのに必須。純JVMテストには無影響）。
- テストは **semantics（テキスト・contentDescription・onClick・ProgressBarRangeInfo）ベース**で書く。各部品3〜6ケース・過剰網羅しない。

## Why-not（採らなかった選択肢）

- **(b) androidTest（実機/エミュレータ）**: ①実機必須のため CI・ローカルの `testDebugUnitTest` ゲートに同乗できずフィードバックが遅い。②本プロジェクトの検証端末 OPPO/ColorOS は androidTest 実行時の freeze/kill 問題を実測している（`task_diary.md` #36-38）ため、純表示部品の回帰検出という軽い目的に対して実行コストとフレークが見合わない。状態機械の描画・結線の検証にピクセルは要らず、Robolectric の semantics 検証で十分。androidTest は Room migration など**実機ランタイムが本質的に必要なもの**に温存する。
- **スクリーンショットテスト**: 見た目の画素比較はスコープ外（ADR 0005 §B＝操作感・組版の質感・没入クロームは HTML→Compose で最も劣化する層のため実機フィードバックで後詰め、という方針に従う）。意匠の目視確認は `@Preview` の追加（分岐ごと）で担い、自動テストは semantics に限定する。
- **Compose を薄くして純関数だけ JVM テスト**: ラベル導出の純関数は既に別途テスト済み（`DiscoveryCommonLabelsTest` 等）。残るのは「状態→描画」「クリック→callback」という Composition 内の結線であり、これは Composable を実際に構成して検証するしかない。

## 検証

- 既存の `testDebugUnitTest`（PDF 抽出・parser・ViewModel 等の純JVMテスト多数）を壊さないことが絶対条件。Robolectric テストは `@RunWith(RobolectricTestRunner::class)` を付けたクラスにのみ効き、既存の素の JUnit テストの実行系には介入しない。
- 今後、葉 Composable を追加・改修する際は本方式でテストを足す。実機ランタイム依存（`TextLayoutResult` 実測が要るルビ配置 `calculateRubyPositions` 等）は Robolectric でも賄えないため対象外（宿題は handover 台帳参照）。

## 増補1（2026-07-12）: スクリーンショットテストの Why-not を部分撤回し Roborazzi を増分導入

当初 Why-not で「見た目の画素比較はスコープ外（意匠の目視は @Preview が担う）」としたが、デザイン正本の層構造整備（ADR 0014 §A）で「④コードは③モックとの乖離をスクリーンショットテストで検出する」層を正式化したため、**本 ADR の Robolectric 基盤に Roborazzi を同乗させる形で部分撤回**する。

- **何を検知するか**: 「ライトとセピアが同色」級のテーマ退行・トークン変更の意図せぬ波及・フォントスケール拡大時のレイアウト破綻。ピクセル単位の意匠美の判定は引き続き人間の目視（ADR 0005 §B の後詰め層は対象外のまま）。
- **ゲート方針**: golden 比較は既定の `testDebugUnitTest` ゲートに**載せない**（Roborazzi はプロパティ未指定なら素通し＝既存ゲートの速度・安定性を守る）。記録は `recordRoborazziDebug`・検証は `verifyRoborazziDebug` を明示実行する運用。
- **golden の環境依存**: JVM のフォントレンダリングは環境依存のため、golden は WSL(Linux) 記録を正とする。Windows 側で verify が乖離しても即退行ではない（再記録で判断）。
