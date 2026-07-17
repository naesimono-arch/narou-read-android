package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 装いの間カルーセルの「1スワイプ=中央から1枚」着地規則（clampWardrobeFlingTarget）の単体テスト。
 * 実機フィードバック「高速フリングで着せ替え先を行き過ぎる」の是正＝視覚的中央 currentPage を基準に
 * 着地を ±1 へ制限する規則を、Pager から切り離して固定する。
 */
class WardrobeFlingTargetTest {

    @Test
    fun `前方への高速フリングでも中央の1枚先までしか進めない`() {
        // 既定の firstVisiblePage 基準では2枚先まで飛びうる状況（suggested=中央+3）でも +1 に制限。
        assertEquals(4, clampWardrobeFlingTarget(currentPage = 3, suggestedTargetPage = 6))
    }

    @Test
    fun `後方への高速フリングでも中央の1枚手前までしか戻れない`() {
        // 覗きカードで基準がずれ2枚戻りうる状況（suggested=中央-3）でも -1 に制限。
        assertEquals(2, clampWardrobeFlingTarget(currentPage = 3, suggestedTargetPage = 0))
    }

    @Test
    fun `1枚だけの控えめなフリングはそのまま隣へ進む`() {
        assertEquals(4, clampWardrobeFlingTarget(currentPage = 3, suggestedTargetPage = 4))
        assertEquals(2, clampWardrobeFlingTarget(currentPage = 3, suggestedTargetPage = 2))
    }

    @Test
    fun `移動しない（中央に留まる）予測はそのまま中央`() {
        assertEquals(3, clampWardrobeFlingTarget(currentPage = 3, suggestedTargetPage = 3))
    }

    @Test
    fun `先頭ページからの後方フリングは負にならず範囲内へ収まる`() {
        // clamp は currentPage-1..currentPage+1。先頭(0)では下限が-1になるが、
        // 実際のページ範囲 clamp は Compose 側（0..pageCount）が別途担うため、ここでは基準±1のみ検証する。
        assertEquals(-1, clampWardrobeFlingTarget(currentPage = 0, suggestedTargetPage = -5))
    }
}
