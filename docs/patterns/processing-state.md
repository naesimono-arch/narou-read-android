# ProcessingState への一本化パターン

> 旧 `task_diary.md` §21（本アプリ固有の実装パターン）
> ここは **コードが正本**。「なぜこのパターンか」に絞る。

`_isProcessing: Boolean` を `ProcessingState(isProcessing, percent, phase)` に置き換えると、
「処理中かどうか」「何%か」「どのフェーズか」を単一の StateFlow で管理でき、UI 側の collectAsState も 1 箇所で済む。
try/finally で成功・失敗いずれの場合も `ProcessingState()` にリセットされるよう保証すること。
