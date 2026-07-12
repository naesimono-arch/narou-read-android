# 本アプリ固有の実装パターン

このディレクトリは **本アプリ固有の実装パターン**（「なぜこのパターンを選んだか」とコード/コミット参照）。

> ここは **コードが正本**。実装の詳細はコードを読めば分かるので、各ファイルは「**なぜこのパターンか**」とコード/コミット参照に絞る。実装が変わったら **コード側を直し**、ここのコピーは増やさないこと（二重管理を避ける）。

旧 `task_diary.md` の Part II から移設。各ファイル冒頭に旧 `§N` を注記して固定IDの追跡を担保している。

| パターン | 内容 | 旧ID |
|---|---|---|
| [processing-state](processing-state.md) | ProcessingState への一本化 | §21 |
| [service-queue-loop](service-queue-loop.md) | Service 内キュー + シングルループ処理 | §23 |
| [topappbar-overlay](topappbar-overlay.md) | TopAppBar オーバーレイ化 + NestedScrollConnection 非消費 | §24 |
| [multi-branch-integration](multi-branch-integration.md) | 多ブランチ統合＝統合ブランチ --no-ff → main へ ff-only（スキーマJSON保全の罠込み） | —（2026-07-08 新規） |
| [narou-api-discovery](narou-api-discovery.md) | なろうAPI 発見・検索の実装パターン（別系統隔離・withContext不要・Moshi codegen・段階チップ翻訳・ncode人間確定・継続取得タイミング） | —（2026-07-08 新規・旧 STATUS-api-lab §2） |
| [discovery-terminology](discovery-terminology.md) | 発見・検索まわりの用語辞書（1概念=1語＝見つける/探す。発見・単独の検索を表層に出さない） | —（2026-07-12 新規・監査 ia Minor） |
