# 設計判断（ADR）

このディレクトリは **設計判断・Why-not（なぜその代替を採らなかったか）の正本**。

- ファイル名は連番 `NNNN-kebab-case.md`。番号は採番順の固定ID（リナンバーしない）。
- 1判断＝1ファイル。「採用しなかった理由」も立派な ADR。
- 旧 `task_diary.md` の Part III（設計判断・運用メモ）から移設したものは、各ファイル冒頭に旧 `§N` を注記して固定IDの追跡を担保している。

| ADR | 内容 | 旧ID |
|---|---|---|
| [0001](0001-no-hilt.md) | Hilt（DIフレームワーク）不採用 | §22 |
| [0002](0002-no-usecase-layer.md) | UseCase 層（Clean Architecture 中間層）不採用 | §22 |
| [0003](0003-atomic-commit-from-impl-order.md) | Atomic Commit は実装順序から設計する | §20 |
| [0004](0004-branch-aware-memory-and-doc-architecture.md) | ブランチ非依存 auto-memory への対処＋doc アーキテクチャ | — |
| [0005](0005-ui-n-visual-language-D.md) | UI-n 視覚言語に D「和モダン・余白」採用（スコープ・Why-not 含む） | — |
| [0006](0006-detect-fabricated-execution-static-analysis.md) | 実行捏造ハルシネーションのトランスクリプト静的解析検知（スコープ・Why-not） | — |
| [0007](0007-search-ux-three-principles.md) | 検索UXの3原則（①見えている条件はその場で変えられる ②検索の仕組みを隠さない ③語彙を知らなくても絞り込める） | — |
| [0008](0008-no-hook-dispatcher.md) | コミット系フックの単一ディスパッチャ不採用（フックは並列実行・共有は定義モジュール hooks_common.py のみ）。旧 0007＝二重採番解消で移動（2026-07-08） | — |
| [0009](0009-robolectric-for-compose-ui-tests.md) | 葉 Composable の UI テストは Robolectric（JVM・testDebugUnitTest 同乗）で回す（androidTest 非採用＝ColorOS freeze/kill・実機必須／スクショ非採用＝ADR 0005 §B） | — |
| [0010](0010-narou-unmodified-handoff-custom-tabs.md) | なろう作品は「加工なし送客（Chrome Custom Tabs）」を既定とする（WebView 内包・本文ネイティブ描画は規約NGで不採用・案A裏付け） | — |
| [0011](0011-narou-pdf-import-webview-limited-reintroduction.md) | なろう公式縦書きPDF取り込み導線に WebView を限定再導入（案B・0010 を"取り込み"用途に限り補完／案A・C 却下・スパイクで setDownloadListener 発火確定） | — |
| [0012](0012-narou-reading-webview-position-tracking.md) | なろう作品の"閲覧"を加工なし・URL 観測のみ・JS 注入ゼロの WebView で行い読書位置を記録し続きから再開（0010 の閲覧送客の既定を機能②で更新／自己申告 UI・番号一覧 却下） | — |
| [0013](0013-pdf-multiselect-standard-picker-post-import-filter.md) | PDF 複数選択は標準ピッカー＋取込段でのなろう形式フィルタ（SAF は MIME しか絞れず選択画面での絞り込み/ソート不可＝独自ピッカー案A・無確認スキップ案B' 却下） | — |
| [0014](0014-design-principles-and-source-layers.md) | デザイン原則5箇条＋禁止則表の正式化と正本の層構造宣言（原則/トークン/モック/コードの4層・可読性＞美学・Style Dictionary 不採用＝一致検査スクリプトの現実解） | — |
| [0015](0015-layered-auto-backup.md) | 層別 Auto Backup＝メタデータ層（DB・prefs・DataStore）のみ include・HTML 実体（novels/）除外（旧 allowBackup=false を上書き／全量・現状維持・独自エクスポート却下） | — |
| [0016](0016-bookshelf-two-tier-recency-sort.md) | 本棚既定ソートの二層化＝読書中(lastReadAt)を上層・未読(addedAt)を下層（純lastReadAt単キー・現状維持・ソートUI先行 却下） | — |
| [0017](0017-doc-system-git-log-as-ledger.md) | 管理ドキュメント体系の再編＝完了履歴は git log・STATUS は現在値60行・CLAUDE.md はルーター・知見は docs/knowledge 1知見1ファイル・規約はサイズ番人とセット（テスト強制3点フック撤去の ROI 裁定／task_diary 全面分割・完了ログ圧縮保持・docs 専用コミット継続 却下） | — |
| [0018](0018-derived-mock-drift-optin-sync-check.md) | 派生モックの陳腐化検知＝DERIVED_SYNC opt-in 実値突合＋@derives/frozen メタ（コミットゲート hook・PostToolUse 警告・opt-out 全数必須・同期日メタ 却下） | — |
| [0019](0019-nav-transition-slide-push.md) | 画面遷移モーションを slide push に統一＝進む右→左/戻る左→右・250ms・目次⇄本文も同向き・章送りは瞬間据え置き（fade/shared-axis Z・章送り即スライド・bounce 却下） | — |
| [0021](0021-ui-skin-framework.md) | UIスキン機構＝スキンはトークン束の着せ替え（構造・余白・motion は全スキン共通／各スキン1変種開始・初弾C夜行・栞の luminance/Sepia 推定根絶／I・J 構造スキンは枠外。0020 は縦書きブランチ予約済み） | — |
| [0022](0022-skin-structural-layer.md) | スキン第二層＝画面構造の切替（M/P/J: 画面入口の薄い when(skin) ルーター・読書/設定は共通骨格＋部品分岐・M=[DARK]/J=[D,L,S]読書のみ変種/P=[LIGHT]開始・「現在地の脈動」類型承認・食い違い値は家系分離。画面ファクトリ束/読書複製/P即3テーマ 却下） | — |
