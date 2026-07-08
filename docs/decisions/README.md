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
