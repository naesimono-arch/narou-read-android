package com.novelreader.typeset

import com.novelreader.model.TextSegment
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 既定組版器の統合検証。Plain＋Ruby 混在の小段落で列数・幅・列0が最右・y積算・
 * StyledBlock 拒否を確認する。
 */
class DefaultVerticalTypesetterTest {

    private val typesetter = DefaultVerticalTypesetter(FakeMonospaceMetrics())

    private val constraints = TypesetConstraints(
        columnHeightPx = 50f, // 5マス
        fontSizePx = 10f,
        rubyFontSizePx = 5f,
        columnAdvancePx = 20f,
        indentFirstColumn = false, // y積算を素直に検証するためインデント無効
    )

    @Test
    fun `小段落の列数と幅と列0最右とy積算`() {
        val segments = listOf(
            TextSegment.Plain("あいう"),
            TextSegment.Ruby("漢", "かん"),
            TextSegment.Plain("えお"),
        )
        val layout = typesetter.typeset(segments, constraints)

        // 6ユニット / 5マス = 2列。
        assertEquals(2, layout.columnCount)
        assertEquals(40f, layout.widthPx, 1e-4f) // 2列 × 列送り20
        assertEquals(50f, layout.heightPx, 1e-4f) // 最長列 5マス × 10

        // 列0が最右＝x が最大。
        val maxXGlyph = layout.glyphs.maxByOrNull { it.x }!!
        assertEquals(0, maxXGlyph.columnIndex)
        val col0Glyphs = layout.glyphs.filter { it.columnIndex == 0 }
        val col1Glyphs = layout.glyphs.filter { it.columnIndex == 1 }
        assertTrue("列0のxが列1より大きい", col0Glyphs.first().x > col1Glyphs.first().x)

        // 列0の y 積算 0,10,20,30,40。
        assertEquals(listOf(0f, 10f, 20f, 30f, 40f), col0Glyphs.map { it.y })
        assertEquals("あいう漢え", col0Glyphs.joinToString("") { it.text })
        assertEquals("お", col1Glyphs.joinToString("") { it.text })

        // ルビが1件配置され、親文字「漢」の読みが載る。
        assertEquals(1, layout.rubies.size)
        assertEquals("かん", layout.rubies[0].text)
        assertEquals(0, layout.rubies[0].columnIndex)
    }

    @Test
    fun `半角数字の縦中横が1グリフになる`() {
        val layout = typesetter.typeset(listOf(TextSegment.Plain("第12話")), constraints)
        val tcy = layout.glyphs.filter { it.charClass == CharClass.TATE_CHU_YOKO }
        assertEquals(1, tcy.size)
        assertEquals("12", tcy[0].text)
    }

    @Test
    fun `StyledBlockは例外`() {
        val block = TextSegment.StyledBlock("前書き", persistentListOf(TextSegment.Plain("中身")))
        try {
            typesetter.typeset(listOf(block), constraints)
            fail("StyledBlock は IllegalArgumentException を投げるべき")
        } catch (e: IllegalArgumentException) {
            // 期待どおり。
        }
    }
}
