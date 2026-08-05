# Robolectric×Compose のアニメテスト2大落とし穴——captureToImage と advanceTimeBy の「進まない」

2026-08-06・栞アニメ高負荷モードのテスト（`ShioriCoverHighLoadTest`）で連続して踏んだ。
どちらも decompile/bytecode 実査で機序確定済み。同型のアニメ×撮像テストを書くときは最初からここを踏まえる。

## ① paused mainClock 下で `captureToImage` は完走しない

- 機序: `captureToImage` 前段の `forceRedraw` が「次の描画フレーム」を `waitUntil` で待つが、
  paused mainClock（`autoAdvance=false`）では仮想時計が進まず新フレームが永遠に来ない＝2000ms timeout。
- 処方: **Roborazzi の `captureRoboImage(File, RoborazziOptions(taskType=Record))`**（1.30.1 実査で
  撮像経路に forceRedraw/waitUntil 不在＝View 直 draw・software canvas で draw ラムダを毎回再実行）→
  一時ファイル→`BitmapFactory.decodeFile`→`Bitmap.sameAs` 比較。`taskType=Record` 明示は
  素の testDebugUnitTest では record/verify プロパティ未指定で captureRoboImage が no-op になるため。

## ② `mainClock.advanceTimeBy` は snapshot apply 通知を汲まない

- 機序: state 書き込みの apply 通知は GlobalSnapshotManager→AndroidUiDispatcher＝**Robolectric main looper 便**で
  流れるが、`advanceTimeBy` は kotlinx TestCoroutineScheduler を進めるだけで **looper を汲まない**
  （AbstractMainTestClock 実査＝`testScheduler.advanceTimeBy+runCurrent` のみ）。
- 症状の型: トグル等の state 変更直後に advance→capture すると、invalidation が次の capture 内 `waitForIdle`
  まで Recomposer へ届かず**1捕獲ぶん位相がずれる**（変更前の構図を撮る／時計コルーチンの0点が遅れる）。
  「アニメが合成されていないように見える」が実装は健全、という誤診を生む。
- 処方: **state 変更の直後に `composeTestRule.waitForIdle()` を1行**（looper を汲んで invalidation を届けてから
  時計を進める）。アサートの緩和や区間広げで吸収しない（位相の根本ズレは残る）。

実装現物とアサート設計＝`android/app/src/test/java/com/novelreader/ui/components/ShioriCoverHighLoadTest.kt`
（各行に機序コメントあり）。関連: ADR 0009（Compose UI テストは Robolectric で回す方針）。
