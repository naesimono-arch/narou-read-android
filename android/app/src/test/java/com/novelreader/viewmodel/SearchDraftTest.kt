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
    fun `default - SearchDraftのデフォルト値でinTitleがtrueでありtoQueryに引き継がれること`() {
        val draft = SearchDraft()
        assertTrue(draft.inTitle)
        assertTrue(draft.toQuery().inTitle)
    }

    @Test
    fun `withRangeToggled - 境界値テスト（最後の1つは外れない、2つON時は外れる、OFFからONは常に可）`() {
        val d1 = SearchDraft() // inTitle = true, others = false
        assertTrue(d1.inTitle)
        assertFalse(d1.inStory)
        assertFalse(d1.inKeyword)
        assertFalse(d1.inWriter)

        // ONが1つだけの状態で、それをOFFにしようとするトグルは無視される
        val d2 = d1.withRangeToggled(SearchRange.TITLE)
        assertTrue(d2.inTitle)

        // OFF -> ON は常に可能
        val d3 = d1.withRangeToggled(SearchRange.STORY)
        assertTrue(d3.inTitle)
        assertTrue(d3.inStory)

        // ONが2つの状態なら、片方をOFFにできる
        val d4 = d3.withRangeToggled(SearchRange.TITLE)
        assertFalse(d4.inTitle)
        assertTrue(d4.inStory)

        // 残りが1つになった状態で、それをOFFにしようとするトグルは無視される
        val d5 = d4.withRangeToggled(SearchRange.STORY)
        assertTrue(d5.inStory)
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
