# Service 内キュー + シングルループ処理パターン  ★★

> 旧 `task_diary.md` §23（本アプリ固有の実装パターン）
> ここは **コードが正本**。「なぜこのパターンか」に絞る。

複数の URI が短時間に `onStartCommand()` に来ても無言破棄せず直列処理するパターン。

```kotlin
private val lock = ReentrantLock()
private val uriQueue = ArrayDeque<Uri>()
private var isLoopRunning = false

override fun onStartCommand(intent: Intent?, ...): Int {
    val uri = intent.data ?: return START_NOT_STICKY
    val shouldStart = lock.withLock {
        uriQueue.add(uri)
        if (!isLoopRunning) { isLoopRunning = true; true } else false
    }
    if (shouldStart) startProcessingLoop()
    return START_NOT_STICKY
}
```

**設計のポイント**:
- `lock.withLock {}` で「追加+起動判定」と「取り出し+終了判定」をアトミック化することで競合ゼロ
- `isLoopRunning` フラグで多重起動を防止。ループ終了時に `isEmpty()` の確認と同一ロックで行う
- WakeLock はフィールドではなくローカル変数で管理（フィールド共有だと旧ループが誤解放するリスクがある）
- **WakeLock は「ループ全体で1回」ではなく「PDF 1件ごと」に acquire/release する**。`acquire(10*60*1000)` の10分上限はバッチ総処理時間とは無関係で、ループ単位で1度だけ取ると複数 PDF の合計が10分を超えた時点で自動解放され、OPPO 等にバックグラウンド kill されて残り PDF が孤立する（`task_diary.md` §4 の WakeLock 不十分問題とは別軸の「取得粒度」の話）
- ループが例外で破綻した場合の finally ブロックで `isLoopRunning = false` のフェイルセーフが必要

コード: `PdfProcessingService.kt`（65abfe4 で導入）
