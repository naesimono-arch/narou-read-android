package com.novelreader.ui.skins.m

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 章扉の漢数字変換（reading-M .num「第 百二十七 話」様式）の位取り規則を固定する。
 * 特に「一十/一百/一千を書かない」規則と、範囲外の算用数字縮退（防御）を回帰で守る。
 */
class KanjiNumberTest {

    @Test
    fun `位取り記法で変換する`() {
        assertEquals("一", kanjiNumber(1))
        assertEquals("十", kanjiNumber(10))
        assertEquals("十一", kanjiNumber(11))
        assertEquals("二十", kanjiNumber(20))
        assertEquals("百二十七", kanjiNumber(127))
        assertEquals("三百四十", kanjiNumber(340))
        assertEquals("千一", kanjiNumber(1001))
        assertEquals("九千九百九十九", kanjiNumber(9999))
    }

    @Test
    fun `一十・一百・一千は書かない`() {
        assertEquals("十", kanjiNumber(10))
        assertEquals("百", kanjiNumber(100))
        assertEquals("千", kanjiNumber(1000))
        assertEquals("百十", kanjiNumber(110))
    }

    @Test
    fun `範囲外は算用数字へ縮退する（表示を壊さない防御）`() {
        assertEquals("0", kanjiNumber(0))
        assertEquals("-3", kanjiNumber(-3))
        assertEquals("10000", kanjiNumber(10000))
    }
}
