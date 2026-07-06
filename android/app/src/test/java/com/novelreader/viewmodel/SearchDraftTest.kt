package com.novelreader.viewmodel

import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDraftTest {

    @Test
    fun `canSearch - 検索語が空でも絞り込みがあれば実行できること`() {
        assertFalse(SearchDraft().canSearch)
        assertFalse(SearchDraft(word = "   ").canSearch)
        assertTrue(SearchDraft(word = "薬師").canSearch)
        assertTrue(SearchDraft(filters = SearchFilters(type = NarouNovelType.SHORT)).canSearch)
    }

    @Test
    fun `toQuery - 検索語のtrim・空→null・フィルタの引き渡しが正しいこと`() {
        val draft = SearchDraft(
            word = " 薬師 ",
            inTitle = true,
            filters = SearchFilters(
                type = NarouNovelType.KANKETSU,
                lastup = NarouLastup.THISMONTH,
                tensei = true,
                excludeZankoku = true,
                length = "100000-",
                kaiwaritu = "60-",
            ),
        )
        val query = draft.toQuery()
        assertEquals("薬師", query.word)
        assertTrue(query.inTitle)
        assertFalse(query.inStory)
        assertEquals(NarouNovelType.KANKETSU, query.type)
        assertEquals(NarouLastup.THISMONTH, query.lastup)
        assertTrue(query.tensei)
        assertFalse(query.tenni)
        assertTrue(query.excludeZankoku)
        assertEquals("100000-", query.length)
        assertEquals("60-", query.kaiwaritu)
        assertNull(SearchDraft(word = "  ").toQuery().word)
    }

    @Test
    fun `resultTitle - 検索語ありは鉤括弧、条件のみは固定文言になること`() {
        assertEquals("「薬師」", SearchDraft(word = " 薬師 ").resultTitle())
        assertEquals(
            "条件で探す",
            SearchDraft(filters = SearchFilters(sasie = "1-")).resultTitle()
        )
    }

    @Test
    fun `SearchFilters - activeCount が有効条件の数を返すこと`() {
        assertEquals(0, SearchFilters().activeCount())
        assertEquals(
            3,
            SearchFilters(type = NarouNovelType.SHORT, tensei = true, time = "-30").activeCount()
        )
    }
}
