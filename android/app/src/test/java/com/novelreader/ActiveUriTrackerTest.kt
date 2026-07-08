package com.novelreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二重取込のべき等ガード（UX監査 F-G 公理3）の中核＝取込中 URI 集合の純ロジック検証。
 * PdfProcessingService から Android 依存無しに切り出したため、Robolectric 不要で単体テストできる。
 */
class ActiveUriTrackerTest {

    @Test
    fun `register - 初回は true・同一URIの再投入は false`() {
        val tracker = ActiveUriTracker()
        // 1回目＝新規取込として受理
        assertTrue(tracker.register("content://docs/1"))
        // 2回目＝取込中の重複投入としてスキップ（二重変換・二重登録を防ぐ）
        assertFalse(tracker.register("content://docs/1"))
    }

    @Test
    fun `register - 別URIは互いに独立して受理される`() {
        val tracker = ActiveUriTracker()
        assertTrue(tracker.register("content://docs/1"))
        assertTrue(tracker.register("content://docs/2"))
        // 既存の 1 は依然ブロック、2 もブロック
        assertFalse(tracker.register("content://docs/1"))
        assertFalse(tracker.register("content://docs/2"))
    }

    @Test
    fun `release - 完了後の同一URI再投入は新規扱いに戻る`() {
        val tracker = ActiveUriTracker()
        assertTrue(tracker.register("content://docs/1"))
        // 処理完了で在庫から外すと、完了後の再取込は新規として受理される
        // （キュー重複ガードは「取込中の二重投入」限定。完了後は蔵書照合が担う）
        tracker.release("content://docs/1")
        assertTrue(tracker.register("content://docs/1"))
    }

    @Test
    fun `clear - 全消し後は全URIが新規扱いに戻る`() {
        val tracker = ActiveUriTracker()
        tracker.register("content://docs/1")
        tracker.register("content://docs/2")
        // 停止・タイムアウト等でキューごと破棄した後は再投入を新規として受け付ける
        tracker.clear()
        assertTrue(tracker.register("content://docs/1"))
        assertTrue(tracker.register("content://docs/2"))
    }
}
