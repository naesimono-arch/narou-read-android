package com.novelreader.typeset

import com.novelreader.ui.compose.RubyLayoutHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ルビの列右側配置の検証。列割り当て済みユニットを手組みで与え、座標と按分を確認する。
 */
class RubyPlacerTest {

    private val metrics = FakeMonospaceMetrics()
    private val fontSize = 10f
    private val rubyFontSize = 5f

    /** ルビ親文字1マスの配置ユニットを作る（各字 advance=fontSize）。 */
    private fun baseUnit(ch: String, segmentIndex: Int, yTop: Float): PlacedUnit =
        PlacedUnit(TypesetUnit(ch, CharClass.UPRIGHT, isRubyBase = true, segmentIndex = segmentIndex), yTop, fontSize)

    @Test
    fun `1列内センタリング座標`() {
        val column = Column(listOf(baseUnit("漢", 0, 0f), baseUnit("字", 0, 10f)))
        val rubies = RubyPlacer.place(
            columns = listOf(column),
            columnCenterX = listOf(100f),
            rubyReadings = mapOf(0 to "かんじ"),
            fontSizePx = fontSize,
            rubyFontSizePx = rubyFontSize,
            metrics = metrics,
        )
        assertEquals(1, rubies.size)
        val r = rubies[0]
        assertEquals("かんじ", r.text)
        assertEquals(0, r.columnIndex)
        // x = 列中心100 + 本文半幅5 + ルビ半幅2.5 = 107.5
        assertEquals(107.5f, r.x, 1e-4f)
        // 親文字スパン 0..20 の中央10、ルビ長=3字×5=15 → y = 10 - 7.5 = 2.5
        assertEquals(2.5f, r.y, 1e-4f)
    }

    @Test
    fun `列跨ぎ按分はsplitRubyReadingと同じ分割`() {
        val reading = "かんじじゅくご"
        val col0 = Column(listOf(baseUnit("漢", 0, 0f), baseUnit("字", 0, 10f)))
        val col1 = Column(listOf(baseUnit("熟", 0, 0f), baseUnit("語", 0, 10f)))
        val rubies = RubyPlacer.place(
            columns = listOf(col0, col1),
            columnCenterX = listOf(100f, 50f),
            rubyReadings = mapOf(0 to reading),
            fontSizePx = fontSize,
            rubyFontSizePx = rubyFontSize,
            metrics = metrics,
        )
        val expected = RubyLayoutHelper.splitRubyReading(reading, listOf(2, 2))
        assertEquals(expected, rubies.map { it.text })
        assertEquals(listOf(0, 1), rubies.map { it.columnIndex })
    }

    @Test
    fun `ルビ長が親文字スパンより長いとはみ出し許容`() {
        val column = Column(listOf(baseUnit("山", 0, 0f)))
        val rubies = RubyPlacer.place(
            columns = listOf(column),
            columnCenterX = listOf(100f),
            rubyReadings = mapOf(0 to "やまやまやま"), // 6字×5=30 >> スパン10
            fontSizePx = fontSize,
            rubyFontSizePx = rubyFontSize,
            metrics = metrics,
        )
        assertEquals(1, rubies.size)
        // 中央合わせのまま上へはみ出す（y が親文字スパン天 0 より上＝負）。
        assertTrue("ルビ天がスパン上端より上へはみ出す", rubies[0].y < 0f)
    }
}
