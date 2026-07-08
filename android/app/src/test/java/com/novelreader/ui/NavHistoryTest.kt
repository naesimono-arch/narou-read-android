package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 章⇄目次の内部遷移履歴（Back の遡行順）を決める pushNavHistory の単体テスト。
 * Back キーの挙動に直結するため、重複排除・上限・遡行順の3点を固定する。
 */
class NavHistoryTest {

    @Test
    fun `新しい遷移先は末尾に積まれる`() {
        assertEquals(listOf("a.html", "b.html"), pushNavHistory(listOf("a.html"), "b.html"))
    }

    @Test
    fun `直前と同じ遷移先は積まない（連打・現在章の再選択）`() {
        val history = listOf("a.html", "b.html")
        // 同一参照を返す＝新規リストを作らない（無駄な再コンポジションも避ける意図）
        assertSame(history, pushNavHistory(history, "b.html"))
    }

    @Test
    fun `連続でなければ同じファイルも積める（a→b→a）`() {
        assertEquals(
            listOf("a.html", "b.html", "a.html"),
            pushNavHistory(listOf("a.html", "b.html"), "a.html"),
        )
    }

    @Test
    fun `目次(index)も履歴に積まれる＝Back で章へ戻れる`() {
        assertEquals(
            listOf("c1.html", "index.html"),
            pushNavHistory(listOf("c1.html"), "index.html"),
        )
    }

    @Test
    fun `上限を超えると最古から捨てる`() {
        val result = pushNavHistory(listOf("a", "b", "c"), "d", max = 3)
        assertEquals(listOf("b", "c", "d"), result)
    }

    @Test
    fun `上限ちょうどでは切り詰めない`() {
        val result = pushNavHistory(listOf("a", "b"), "c", max = 3)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `上限超過時も末尾（最新の遷移先）は必ず残る`() {
        val result = pushNavHistory(listOf("a", "b", "c", "d"), "e", max = 2)
        assertEquals(listOf("d", "e"), result)
    }
}
