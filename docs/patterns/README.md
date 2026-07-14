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
| [spacing-token-translation](spacing-token-translation.md) | 余白 dp → Spacing/Insets 翻訳と較正値の保護（検査ロジック流用で機械化・盲目一括置換が構造/較正値を潰す機序・コメント突合＋較正マーカー走査で炙り出し） | —（2026-07-13 新規・F(2) 残債再翻訳） |
| [scroll-collapse-band](scroll-collapse-band.md) | スクロールで畳むヘッダ要素の2方式（閾値式 AnimatedVisibility vs スクロール連動式）＋ AnimatedVisibility にカスタム exit を渡すと既定 shrink が外れ予約スペースが終了時に snap する罠 | —（2026-07-14 新規・C② 発見帯 完全退避） |
