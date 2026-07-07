package com.novelreader.narou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchHistoryLogicTest {

    @Test
    fun `withRecentAdded - 先頭挿入・既出の繰り上げ・trim・空白無視が働くこと`() {
        val h = SearchHistory(recent = listOf("b", "c"))
        assertEquals(listOf("a", "b", "c"), h.withRecentAdded(" a ").recent)
        assertEquals(listOf("c", "b"), h.withRecentAdded("c").recent)
        assertSame(h, h.withRecentAdded("   "))
    }

    @Test
    fun `withRecentAdded - ピン済みの語は recent に重複させないこと`() {
        val h = SearchHistory(pinned = listOf("薬師"))
        assertSame(h, h.withRecentAdded("薬師"))
    }

    @Test
    fun `withRecentAdded - 上限20件を超えたら古い側から切り捨てること`() {
        val full = SearchHistory(recent = (1..20).map { "w$it" })
        val added = full.withRecentAdded("new")
        assertEquals(20, added.recent.size)
        assertEquals("new", added.recent.first())
        assertEquals("w19", added.recent.last()) // w20 が押し出される
    }

    @Test
    fun `withPinned - recentから昇格しピン列の末尾に足されること・上限10で無視されること`() {
        val h = SearchHistory(pinned = listOf("p1"), recent = listOf("a", "b"))
        val pinned = h.withPinned("b")
        assertEquals(listOf("p1", "b"), pinned.pinned)
        assertEquals(listOf("a"), pinned.recent)

        val full = SearchHistory(pinned = (1..10).map { "p$it" })
        assertSame(full, full.withPinned("new"))
    }

    @Test
    fun `withUnpinned - ピンから外れ recent の先頭へ戻ること`() {
        val h = SearchHistory(pinned = listOf("p1", "p2"), recent = listOf("a"))
        val unpinned = h.withUnpinned("p1")
        assertEquals(listOf("p2"), unpinned.pinned)
        assertEquals(listOf("p1", "a"), unpinned.recent)
        assertSame(h, h.withUnpinned("不明"))
    }

    @Test
    fun `withRecentRemoved - 指定語だけが消えること`() {
        val h = SearchHistory(recent = listOf("a", "b"))
        assertEquals(listOf("b"), h.withRecentRemoved("a").recent)
    }
}
