package com.novelreader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** K の気分ページャ循環化（仮想大カウント＋剰余写像）の論理ページ写像を固定するテスト。 */
class MoodPatternTest {

    @Test
    fun `forPage - 仮想ページが実組数の剰余で循環写像されること`() {
        // 1周目: 0,1,2 がそのまま entries 順。
        assertEquals(MoodPattern.CLASSIC, MoodPattern.forPage(0))
        assertEquals(MoodPattern.REFRESH, MoodPattern.forPage(1))
        assertEquals(MoodPattern.STYLE, MoodPattern.forPage(2))
        // 2周目以降も同じ並びで循環する（右端→先頭へ続く挙動の核）。
        assertEquals(MoodPattern.CLASSIC, MoodPattern.forPage(MoodPattern.entries.size))
        assertEquals(MoodPattern.STYLE, MoodPattern.forPage(MoodPattern.LOOP_PAGE_COUNT - 1))
        // 負値ガード（forEpochDay と同じ防御）: 落ちずに末尾へ巻き戻る。
        assertEquals(MoodPattern.STYLE, MoodPattern.forPage(-1))
    }

    @Test
    fun `loopInitialPage - 中央帯にあり写像すると開始組へ戻ること`() {
        MoodPattern.entries.forEach { start ->
            val page = MoodPattern.loopInitialPage(start)
            // 剰余写像で必ず開始組（＝日替わりで選ばれた組）が初期表示になる。
            assertEquals(start, MoodPattern.forPage(page))
            // 中央帯＝左右どちらへも十分な余白があり、実用上端に到達しない。
            assertTrue(page > MoodPattern.LOOP_PAGE_COUNT / 4)
            assertTrue(page < MoodPattern.LOOP_PAGE_COUNT * 3 / 4)
        }
    }

    @Test
    fun `forEpochDay と loopInitialPage - 日替わり初期組が循環化後も同じ日で再現されること`() {
        val today = MoodPattern.forEpochDay(20_660) // 2026-07-26 の epochDay（決定的な代表値）
        assertEquals(today, MoodPattern.forPage(MoodPattern.loopInitialPage(today)))
    }
}
