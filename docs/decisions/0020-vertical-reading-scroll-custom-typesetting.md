# 0020. 縦書きモードは「右→左 連続横スクロール × 自前Compose組版（公式成熟までのつなぎ）」で実装する

- ステータス: Accepted（2026-07-16 ユーザーと方針合意）
- 関連ADR: [0005](0005-ui-n-visual-language-D.md)（視覚言語D）／[0010](0010-narou-unmodified-handoff-custom-tabs.md)・[0011](0011-narou-pdf-import-webview-limited-reintroduction.md)（WebViewの適用境界。本ADRは自前コンテンツの読書面なので0010の規約論とは独立）
- 関連資産: 触感モック `docs/design-candidates/reading-vertical-scroll-D.html`（採用骨格A）・`reading-vertical-paged-D.html`（不採用骨格B・比較記録として保持）
- 実装プラン: `.claude/plans/vertical-reading-mode.md`

## Context（背景）

蔵書リーダー（純Compose組版・横書きLazyColumn）に縦書きリーディングモードを追加する。2026-07-16 に外部調査＋触感モック2案（A=連続横スクロール／B=右→左ページ送り）で検討し、ユーザーがAを選択した。

前提となる技術事実（2026-07時点の外部調査）:
- 縦書きUXは「右→左ページ送り」が紙由来の伝統標準だが、スマホ実装では「右→左の連続横スクロール」も実務主流（カクヨムWeb版・Readiumは縦書き時スクロール強制）。
- Compose/Androidネイティブは長年縦書き非対応。Android 16 (API 36) で OS に `VERTICAL_TEXT_FLAG` が入り、公式 `androidx.text:text-vertical`（ルビ/圏点スパン付き）が登場したが **alpha ＋ API 36 縛り**＝ユーザー範囲が狭くまだ本命にできない。
- WebView + CSS `writing-mode:vertical-rl` は縦書き表示が最も枯れているが、採ると読書位置保存・没入・章送り・TTS/a11y 資産を2系統維持する「混在の税金」が恒久化する。
- 現行のジェスチャは「本文スクロール=縦・章送り=横ドラッグ」で軸分離しており、縦書き（本文が横スクロール）は章送りと軸衝突する＝どの方式でも再設計必須。

## Decision（決定）

1. **骨格＝連続横スクロール（右→左）**。ページ送りは採らない（ユーザーの触感選択）。
2. **描画＝自前のCompose組版エンジン**。WebViewは使わず、text-vertical も直採用しない。
3. **自前実装は「公式が世間一般に馴染むまでのつなぎ」と位置づける**が、品質は妥協しない（手抜き禁止＝ユーザー明示指示）。
   - 組版層は `VerticalTypesetter` interface で隔離し、**出口条件**＝text-vertical が stable 化しかつ API 36 未満の利用者比率が無視できる水準になったら乗換を検討する。
   - **v1スコープ（すべて必須）**: 文字クラス分類（正立/回転/句読点位置替え・未知→正立フォールバック）／行頭行末禁則／ルビ（行の右側）／**縦中横**（ユーザー裁定でv1必須）。
4. **文字クラス分類＋グリフ描画（vert feature＋Canvas回転の2段構え）は読書画面専用にせず共有部品として切る**。第2の消費者＝本棚書影の縦組み題字 `ShioriCover.drawShioriTitle`（現状は全文字正立直描きで「（）」「～」「ー」が崩れて見える実害あり）。
5. **読書位置の正本＝「段落index＋段落内進捗fraction」**。縦⇄横切替時も同じ段落を維持（ユーザー裁定）。同一モード内の保存は現行 `ProgressEntity(scrollIndex/scrollOffset)` と同型を保つ（LazyRow も同APIのため。DBスキーマ変更なし）。
6. **設定＝全書籍共通**（ユーザー裁定）。`app_prefs` に `reading_vertical` を追加し ReadingSettingsSheet に露出。行間スライダーは縦書きでは列送り（横方向ピッチ）へ写像し値を共有。
7. **章送り＝終端オーバースクロール**。縦書き時は横ドラッグ章送りを無効化し、本文終端（左端）を越える未消費スクロールを nestedScroll で捕捉して既存の引っ張りプレビュー資産（ChapterPeekPanel/settleSwipe）へ流す。

## Consequences（帰結）

- 組版の正しさ（文字クラスの網羅・フォント差）を自分で保有する。緩和＝入力は自作パイプライン出力の閉じたサブセット（段落＋ルビ＋区切り＋前後書きのみ）で、未知文字は正立フォールバックに落とす。
- 組版ドメイン層は純Kotlinに保ち JVMテストで担保（実機依存は「vert feature の実効性」1点にスパイクで隔離）。
- TTS/a11y（ルビの著者読み置換・没入 customActions・読了検出）は縦書き経路でも再現が必須＝退行させたら不合格。
- 将来 text-vertical へ乗り換える場合、捨てるのは `DefaultVerticalTypesetter` 実装のみ（interface・文字クラス表・テスト・ジェスチャ配線は残る）。

## Why-not（採らなかった選択肢）

- **WebView + CSS（骨格Aとの相性は最良）**: 縦書き表示は最小コストだが、位置保存/没入/章送り/TTS/a11y の二重系維持という恒久税と、リーダーを一度WebView→Composeへ寄せた構成を割る点で不採用。
- **androidx.text:text-vertical 直採用**: alpha（API破壊変更が現に発生）＋ API 36 限定でユーザー範囲が狭い。ただし将来の本命候補のため interface の差し替え先として設計に織り込む。
- **骨格B（ページ送り）**: 触感比較でユーザーがAを選択。比較モックは `reading-vertical-paged-D.html` として保持（将来ページ送りを追加する場合の骨格記録）。
- **切替時に章頭へ戻す**: 実装最小だが途中切替のたびに場所を見失う。段落維持を採用。
