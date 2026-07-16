package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 禁則つき列分割の検証。FakeMonospaceMetrics（1マス=fontSizePx）で
 * fontSizePx=10・容量50px＝1列5マスの決定的な盤面を使う。
 */
class LineBreakerTest {

    private val metrics = FakeMonospaceMetrics()
    private val fontSize = 10f
    private val capacity = 50f // 5マス

    /** 1文字=1ユニット（charClass は折返しに無関係なので UPRIGHT 固定）でユニット列を作る。 */
    private fun units(text: String): List<TypesetUnit> =
        text.map { TypesetUnit(it.toString(), CharClass.UPRIGHT, isRubyBase = false, segmentIndex = -1) }

    private fun texts(column: Column): String = column.units.joinToString("") { it.unit.text }

    private fun breakUp(units: List<TypesetUnit>, indent: Boolean = false): List<Column> =
        LineBreaker.breakIntoColumns(units, capacity, metrics, fontSize, indent)

    @Test
    fun `普通の折返し`() {
        val cols = breakUp(units("あいうえおかき")) // 7字
        assertEquals(2, cols.size)
        assertEquals("あいうえお", texts(cols[0]))
        assertEquals("かき", texts(cols[1]))
        // y 積算の確認。
        assertEquals(0f, cols[0].units[0].yTop)
        assertEquals(40f, cols[0].units[4].yTop)
    }

    @Test
    fun `句読点は前列へ追い込み`() {
        // 6字目が「。」→ 改列せず前列へ押し込み＝前列6字。
        val cols = breakUp(units("あいうえお。"))
        assertEquals(1, cols.size)
        assertEquals("あいうえお。", texts(cols[0]))
        // 容量超過（y=50 に配置）を許容している。
        assertEquals(50f, cols[0].units[5].yTop)
    }

    @Test
    fun `閉じ括弧は前列へ追い込み`() {
        val cols = breakUp(units("あいうえお」"))
        assertEquals(1, cols.size)
        assertEquals("あいうえお」", texts(cols[0]))
    }

    @Test
    fun `連続する行頭禁則は続けて追い込み`() {
        val cols = breakUp(units("あいうえお。」"))
        assertEquals(1, cols.size)
        assertEquals("あいうえお。」", texts(cols[0]))
        assertEquals(7, cols[0].units.size)
    }

    @Test
    fun `開き括弧は次列へ追い出し`() {
        // 5字目が開き括弧「で列末に来る→次列へ追い出し。前列は4字。
        val cols = breakUp(units("あいうえ「お"))
        assertEquals(2, cols.size)
        assertEquals("あいうえ", texts(cols[0]))
        assertEquals("「お", texts(cols[1]))
        // 追い出された「は次列先頭 y=0。
        assertEquals(0f, cols[1].units[0].yTop)
    }

    @Test
    fun `段落頭インデントで先頭列は4字しか入らない`() {
        val cols = breakUp(units("あいうえおか"), indent = true)
        assertEquals(4, cols[0].units.size)
        assertEquals("あいうえ", texts(cols[0]))
        // インデント分 y は fontSizePx から始まる。
        assertEquals(10f, cols[0].units[0].yTop)
        assertEquals("おか", texts(cols[1]))
        assertEquals(0f, cols[1].units[0].yTop)
    }

    @Test
    fun `縦中横runは1ユニットとして折れる`() {
        val list = listOf(
            TypesetUnit("あ", CharClass.UPRIGHT, false, -1),
            TypesetUnit("い", CharClass.UPRIGHT, false, -1),
            TypesetUnit("12", CharClass.TATE_CHU_YOKO, false, -1),
            TypesetUnit("う", CharClass.UPRIGHT, false, -1),
            TypesetUnit("え", CharClass.UPRIGHT, false, -1),
        )
        val cols = breakUp(list)
        // 5ユニット（縦中横含む）が1列に収まる。
        assertEquals(1, cols.size)
        assertEquals(5, cols[0].units.size)
        val tcy = cols[0].units[2]
        assertEquals("12", tcy.unit.text)
        assertEquals(CharClass.TATE_CHU_YOKO, tcy.unit.charClass)
        // 縦中横も1マス＝advance は fontSizePx、y は3マス目=20。
        assertEquals(10f, tcy.advance)
        assertEquals(20f, tcy.yTop)
        assertTrue("縦中横は分割されず1ユニット", cols[0].units.count { it.unit.text == "12" } == 1)
    }
}
