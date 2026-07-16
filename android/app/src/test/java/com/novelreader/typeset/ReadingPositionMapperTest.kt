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
    fun `モード切替_異なる寸法でも段落indexを維持しfractionを新寸法へ按分する`() {
        // 横書き（LazyColumn）で先頭可視: list index=6（＝段落5, header=1）・offset=300px・旧アイテム高=600px。
        val captured = ReadingPositionMapper.fromScroll(
            firstVisibleItemIndex = 6,
            firstVisibleItemScrollOffset = 300,
            firstVisibleItemSizePx = 600,
            headerItemCount = 1,
        )
        assertEquals("段落 index", 5, captured.paragraphIndex)
        assertEquals("fraction=0.5", 0.5f, captured.fraction, 1e-4f)
        // 縦書き（LazyRow）へ切替後は当該段落の寸法（列幅）が変わる（例: 900px）。同じ段落＝同じ list index
        // へ復帰し、offset は fraction を新寸法へ按分した値になる（＝P5 の位置維持の核）。
        val (index, offset) = ReadingPositionMapper.toScroll(captured, itemSizePx = 900, headerItemCount = 1)
        assertEquals("同一段落（同一 list index）へ復帰", 6, index)
        assertEquals("fraction 0.5 を新寸法 900 へ按分", 450, offset)
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
