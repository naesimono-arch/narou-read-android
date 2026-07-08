package com.novelreader.ui.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 作品詳細の最終更新ラベル整形（手書き日付パース）の純関数テスト。
 * なぜ固定するか: なろうAPIが日付形式を保証しないため防御的に握り潰す設計にしており、
 * 正常系の整形と、不正入力で例外を出さず null に倒れる挙動をどちらも回帰から守るため。
 */
class NovelDetailLabelTest {

    @Test
    fun `正常系 - 日時文字列の日付部から和暦風ラベルへ整形されること`() {
        assertEquals("2024年1月5日 更新", formatLastupLabel("2024-01-05 12:34:56"))
        // 時刻部が無くても日付だけで整形できること
        assertEquals("2023年12月31日 更新", formatLastupLabel("2023-12-31"))
    }

    @Test
    fun `異常系 - null・空・区切り不足・非数値は例外を出さず nullになること`() {
        assertNull(formatLastupLabel(null))
        assertNull(formatLastupLabel(""))
        assertNull(formatLastupLabel("2024-01")) // 年月日に満たない
        assertNull(formatLastupLabel("2024/01/05")) // 区切りが "-" でなく分割できない
        assertNull(formatLastupLabel("abcd-ef-gh")) // 数値変換に失敗
    }
}
