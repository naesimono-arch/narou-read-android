# ProcessingState への一本化パターン

> 旧 `task_diary.md` §21（本アプリ固有の実装パターン）
> ここは **コードが正本**。「なぜこのパターンか」に絞る。

`_isProcessing: Boolean` を `ProcessingState(isProcessing, percent, phase)` に置き換えると、
「処理中かどうか」「何%か」「どのフェーズか」を単一の StateFlow で管理でき、UI 側の collectAsState も 1 箇所で済む。
try/finally で成功・失敗いずれの場合も `ProcessingState()` にリセットされるよう保証すること。

**状態にフラグを足すとき、高頻度コールバックでの再生成が他経路の更新を巻き戻す罠（bd7f38d）**:
`isStopping` のような「別経路（停止操作）で立てるフラグ」を `ProcessingState` に足すと、進捗
`onProgress` が高頻度で `ProcessingState(...)` を**新規生成**するたびにデフォルト false で上書きされ、
立てたはずの停止状態が巻き戻る。→ フラグは **Service フィールド（lock 保護）に保持し、コールバック内で
読み直して載せる**こと。「単一 StateFlow は便利」だが、**ライターが複数いる時は各ライターが全フィールドを
尊重する**必要がある（一本化＝単一ライター前提ではない）。
