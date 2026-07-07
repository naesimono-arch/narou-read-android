package com.novelreader.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RubyLayoutHelper の純粋関数部分（書記素分割・ルビ分割）のユニットテスト。
 * TextLayoutResult に依存する calculateRubyPositions は実 TextLayoutResult が必要なため
 * JVM では検証できず、対応する Instrumented/screenshot テストは未整備（宿題は handover 台帳参照）。
 * ※かつて「Instrumented test で別途検証」と書かれていたが該当テストは実在しなかった（2026-07-08 訂正）。
 */
class RubyLayoutHelperTest {

    // ── splitGraphemes ──

    @Test
    fun `splitGraphemes - 通常のひらがな`() {
        val result = RubyLayoutHelper.splitGraphemes("ものがたり")
        assertEquals(listOf("も", "の", "が", "た", "り"), result)
    }

    @Test
    fun `splitGraphemes - サロゲートペアを含む文字列`() {
        // 𠮷 (U+20BB7) は UTF-16 でサロゲートペア（2つの Char）
        val result = RubyLayoutHelper.splitGraphemes("𠮷野家")
        assertEquals(3, result.size)
        assertEquals("𠮷", result[0])
        assertEquals("野", result[1])
        assertEquals("家", result[2])
    }

    @Test
    fun `splitGraphemes - 空文字列`() {
        val result = RubyLayoutHelper.splitGraphemes("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `splitGraphemes - ASCII文字`() {
        val result = RubyLayoutHelper.splitGraphemes("abc")
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `splitGraphemes - 単一文字`() {
        val result = RubyLayoutHelper.splitGraphemes("あ")
        assertEquals(listOf("あ"), result)
    }

    // ── splitRubyReading ──

    @Test
    fun `splitRubyReading - 同一行（分割なし）`() {
        val result = RubyLayoutHelper.splitRubyReading("ものがたり", listOf(3))
        assertEquals(1, result.size)
        assertEquals("ものがたり", result[0])
    }

    @Test
    fun `splitRubyReading - 2行またぎ均等分割`() {
        // 親文字4文字: 2文字+2文字、ルビ4文字 → 2+2
        val result = RubyLayoutHelper.splitRubyReading("かんじだ", listOf(2, 2))
        assertEquals(2, result.size)
        assertEquals("かん", result[0])
        assertEquals("じだ", result[1])
    }

    @Test
    fun `splitRubyReading - 2行またぎ不均等分割`() {
        // 親文字3文字: 1文字+2文字、ルビ "ものがたり"(5書記素)
        // 1行目: 5 * 1 / 3 = 1書記素 → "も"
        // 2行目: 残り → "のがたり"
        val result = RubyLayoutHelper.splitRubyReading("ものがたり", listOf(1, 2))
        assertEquals(2, result.size)
        assertEquals("も", result[0])
        assertEquals("のがたり", result[1])
    }

    @Test
    fun `splitRubyReading - 3行またぎ`() {
        // 親6文字: 2+2+2、ルビ "あいうえおか"(6書記素) → 2+2+2
        val result = RubyLayoutHelper.splitRubyReading("あいうえおか", listOf(2, 2, 2))
        assertEquals(3, result.size)
        assertEquals("あい", result[0])
        assertEquals("うえ", result[1])
        assertEquals("おか", result[2])
    }

    @Test
    fun `splitRubyReading - 空ルビ`() {
        val result = RubyLayoutHelper.splitRubyReading("", listOf(2, 3))
        assertEquals(2, result.size)
        assertEquals("", result[0])
        assertEquals("", result[1])
    }

    @Test
    fun `splitRubyReading - 空の行リスト`() {
        val result = RubyLayoutHelper.splitRubyReading("あいう", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `splitRubyReading - サロゲートペアを含むルビの分割`() {
        // "𠮷あい" = 3書記素、親文字 1+2 → 1行目に1書記素、2行目に残り
        val result = RubyLayoutHelper.splitRubyReading("𠮷あい", listOf(1, 2))
        assertEquals(2, result.size)
        assertEquals("𠮷", result[0])
        assertEquals("あい", result[1])
    }

    @Test
    fun `splitRubyReading - 端数は最終行に吸収`() {
        // 親3文字: 1+2、ルビ "あいう"(3書記素)
        // 1行目: 3 * 1 / 3 = 1 → "あ"
        // 2行目: 残り → "いう"
        val result = RubyLayoutHelper.splitRubyReading("あいう", listOf(1, 2))
        assertEquals(2, result.size)
        assertEquals("あ", result[0])
        assertEquals("いう", result[1])
    }

    @Test
    fun `splitRubyReading - 親文字数が全て0`() {
        val result = RubyLayoutHelper.splitRubyReading("あいう", listOf(0, 0))
        assertEquals(2, result.size)
        // totalBaseChars == 0 のため全て空文字列
        assertEquals("", result[0])
        assertEquals("", result[1])
    }
}
