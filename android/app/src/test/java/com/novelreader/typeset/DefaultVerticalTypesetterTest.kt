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
    fun `連続リーダーは1ユニットへ結合され実寸を占有する`() {
        // 「……」（…×2）は結合1ユニット・ROTATE・縦送りはフェイク規則どおり2マス＝20f。
        val layout = typesetter.typeset(listOf(TextSegment.Plain("だ……。")), constraints)
        val leader = layout.glyphs.first { it.text == "……" }
        assertEquals(CharClass.ROTATE, leader.charClass)
        assertEquals(20f, leader.advancePx, 1e-4f)
        // だ(1マス)の後ろに続く＝y=10 から2マス占有。
        assertEquals(10f, leader.y, 1e-4f)
    }

    @Test
    fun `リーダー結合は同一字のみで上限4`() {
        // 異種（…と‥）は結合しない。
        val mixed = typesetter.typeset(listOf(TextSegment.Plain("…‥")), constraints)
        assertEquals(listOf("…", "‥"), mixed.glyphs.map { it.text })
        // 同一字6連は上限4で割れる（4+2）。
        val long = typesetter.typeset(listOf(TextSegment.Plain("………………")), constraints)
        assertEquals(listOf("…………", "……"), long.glyphs.map { it.text })
    }

    @Test
    fun `異体字セレクタつきの約物は1ユニットのまま行頭禁則になる`() {
        // 実データの ‼U+203C・⁉U+2049 は VS15(U+FE0E) を伴って届く（N3957FQ で 1,253 件）。
        // 書記素分割が VS を切り離さないこと（1ユニット）と、その状態で禁則が効くことを一気に固定する
        // ＝分割層と禁則層のどちらが壊れても、実データ相当の入力で赤くなる。
        val layout = typesetter.typeset(listOf(TextSegment.Plain("あいうえお\u203C\uFE0E")), constraints)
        val mark = layout.glyphs.first { it.text.startsWith("\u203C") }
        assertEquals("VS が切り離されず1ユニットであること", "\u203C\uFE0E", mark.text)
        // 行頭禁則が効いた結果、5マス目の「お」を道連れに列1へ追い出される（列頭に来ない）。
        assertEquals(1, mark.columnIndex)
        val col1 = layout.glyphs.filter { it.columnIndex == 1 }
        assertEquals("お", col1.first().text)
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
