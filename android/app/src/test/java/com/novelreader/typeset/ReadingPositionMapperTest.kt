package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スクロール状態⇔ParagraphPosition 変換の検証。往復同一性・ヘッダ丸め・fraction clamp。
 */
class ReadingPositionMapperTest {

    /** fromScroll→toScroll で元の (index, offset) を復元する往復同一性。 */
    private fun assertRoundTrip(index: Int, offset: Int, size: Int, header: Int = 1) {
        val pos = ReadingPositionMapper.fromScroll(index, offset, size, header)
        val (i2, o2) = ReadingPositionMapper.toScroll(pos, size, header)
        assertEquals("index 復元", index, i2)
        assertEquals("offset 復元", offset, o2)
    }

    @Test
    fun `往復同一性_複数ケース`() {
        assertRoundTrip(index = 3, offset = 150, size = 600) // fraction 0.25
        assertRoundTrip(index = 5, offset = 200, size = 800) // fraction 0.25
        assertRoundTrip(index = 2, offset = 0, size = 500) // 段落頭
        assertRoundTrip(index = 10, offset = 480, size = 640) // fraction 0.75
        assertRoundTrip(index = 1, offset = 333, size = 999) // fraction 1/3（round で復元）
        assertRoundTrip(index = 4, offset = 100, size = 400, header = 0) // ヘッダ無し構成
    }

    @Test
    fun `ヘッダ上は最初の段落先頭に丸める`() {
        // index=0（ヘッダ）は paragraphIndex=0, fraction=0。
        val pos = ReadingPositionMapper.fromScroll(0, 50, 100, headerItemCount = 1)
        assertEquals(ParagraphPosition(0, 0f), pos)
    }

    @Test
    fun `段落indexはヘッダ数を差し引く`() {
        val pos = ReadingPositionMapper.fromScroll(3, 0, 100, headerItemCount = 1)
        assertEquals(2, pos.paragraphIndex)
        val (index, _) = ReadingPositionMapper.toScroll(ParagraphPosition(2, 0f), 100, headerItemCount = 1)
        assertEquals(3, index)
    }

    @Test
    fun `fractionは0以上1未満にclampする`() {
        // offset > size でも fraction は 1 未満に収める。
        val pos = ReadingPositionMapper.fromScroll(3, 900, 600, headerItemCount = 1)
        assertTrue("fraction < 1", pos.fraction < 1f)
        assertTrue("fraction >= 0", pos.fraction >= 0f)
    }

    @Test
    fun `アイテム寸法0以下はfraction0に倒す`() {
        val pos = ReadingPositionMapper.fromScroll(3, 100, 0, headerItemCount = 1)
        assertEquals(0f, pos.fraction, 0f)
        // toScroll 側も寸法0以下で offset=0。
        val (_, offset) = ReadingPositionMapper.toScroll(ParagraphPosition(2, 0.5f), 0, headerItemCount = 1)
        assertEquals(0, offset)
    }
}
