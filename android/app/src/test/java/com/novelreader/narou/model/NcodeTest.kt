package com.novelreader.narou.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [Ncode] value class の等価性と「素通し（非正規化）」契約を固定するテスト。
 * なぜこのテストか: Ncode は取り違え防御（型で ncode と他の String を区別）が目的だが、
 * equals は下地 String に委譲するため case-insensitive ではない。将来「Ncode で包めば表記ゆれが
 * 吸収される」と誤解して大文字/小文字の正規化をコンストラクタへ足すと、用途別正規化（URL=小文字/
 * 保存=大文字）の既存挙動を壊す。その退行を検知するための番人。
 */
class NcodeTest {

    @Test
    fun `同一文字列の Ncode は等価`() {
        assertEquals(Ncode("N1234AB"), Ncode("N1234AB"))
    }

    @Test
    fun `value はそのまま保持され正規化されない（素通し契約）`() {
        // 大文字・小文字・前後空白のいずれも生値のまま。正規化は各利用サイトの責務（Ncode の KDoc 参照）。
        assertEquals("  n1234ab  ", Ncode("  n1234ab  ").value)
    }

    @Test
    fun `大文字小文字が違えば別物（equals は下地 String に委譲＝case-sensitive）`() {
        assertNotEquals(Ncode("N1234AB"), Ncode("n1234ab"))
    }
}
