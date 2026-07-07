package com.novelreader.viewmodel

import com.novelreader.narou.model.NarouAttr
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
        assertTrue(SearchDraft(filters = SearchFilters(types = setOf(NarouNovelType.SHORT))).canSearch)
    }

    @Test
    fun `toQuery - 検索語のtrim・空→null・フィルタの引き渡しが正しいこと`() {
        val draft = SearchDraft(
            word = " 薬師 ",
            inTitle = true,
            filters = SearchFilters(
                types = setOf(NarouNovelType.KANKETSU),
                lastups = setOf(NarouLastup.THISMONTH),
                attrsInclude = setOf(NarouAttr.TENSEI),
                attrsExclude = setOf(NarouAttr.ZANKOKU),
                length = "100000-",
                kaiwaritu = "60-",
            ),
        )
        val query = draft.toQuery()
        assertEquals("薬師", query.word)
        assertTrue(query.inTitle)
        assertFalse(query.inStory)
        assertEquals(setOf(NarouNovelType.KANKETSU), query.types)
        assertEquals(setOf(NarouLastup.THISMONTH), query.lastups)
        assertTrue(NarouAttr.TENSEI in query.attrsInclude)
        assertFalse(NarouAttr.TENNI in query.attrsInclude)
        assertTrue(NarouAttr.ZANKOKU in query.attrsExclude)
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
            SearchFilters(types = setOf(NarouNovelType.SHORT), attrsInclude = setOf(NarouAttr.TENSEI), time = "-30").activeCount()
        )
    }

    @Test
    fun `buildCustomRange - 正常値・空欄・非数値・minとmaxの反転救済が正しく機能すること`() {
        assertEquals("10000-100000", buildCustomRange("1", "10", 10000))
        assertEquals("10000-100000", buildCustomRange("10", "1", 10000)) // 反転救済
        assertEquals("50000-", buildCustomRange("5", "", 10000))
        assertEquals("-50000", buildCustomRange("", "5", 10000))
        assertNull(buildCustomRange("", "", 10000))
        assertNull(buildCustomRange("abc", "def", 10000))
    }

    @Test
    fun `parseCustomRange - 範囲文字列からUI用の値に正しく復元できること`() {
        assertEquals(Pair("1", "10"), parseCustomRange("10000-100000", 10000))
        assertEquals(Pair("5", ""), parseCustomRange("50000-", 10000))
        assertEquals(Pair("", "5"), parseCustomRange("-50000", 10000))
        assertEquals(Pair("", ""), parseCustomRange(null, 10000))
        assertEquals(Pair("", ""), parseCustomRange("", 10000))
        assertEquals(Pair("", ""), parseCustomRange("invalid", 10000))
    }

    @Test
    fun `SearchFilters - lengthとtimeが相互排他されること`() {
        // なぜ time と文字数指定の併用不可（マニュアル§4.4）＝両方送ったときの挙動が未定義のため、モデル層で同時に立たないことを保証する。
        val f1 = SearchFilters(length = "10000-100000")
        val f2 = f1.withTime("30-120")
        assertNull(f2.length)
        assertEquals("30-120", f2.time)

        val f3 = f2.withLength("100000-")
        assertNull(f3.time)
        assertEquals("100000-", f3.length)
    }

    @Test
    fun `containsWordToken - トークン判定が正しく行われること`() {
        // 半角スペース区切り
        assertTrue(containsWordToken("aa bb cc", "bb"))
        assertFalse(containsWordToken("aa bb cc", "dd"))

        // 全角スペース区切り
        assertTrue(containsWordToken("aa　bb　cc", "bb"))
        assertFalse(containsWordToken("aa　bb　cc", "dd"))

        // 混在
        assertTrue(containsWordToken("aa bb　cc", "cc"))

        // 空
        assertFalse(containsWordToken("", "aa"))
    }

    @Test
    fun `toggleWordToken - トークンが正しく追加・除去され、空白が正規化されること`() {
        // 追加: 末尾へ半角スペース区切り
        assertEquals("aa bb", toggleWordToken("aa", "bb"))

        // 除去: 指定トークンが消え、余分な空白が正規化される
        assertEquals("aa cc", toggleWordToken("aa bb cc", "bb"))

        // 全角スペース混在でのトグル
        // 除去
        assertEquals("aa cc", toggleWordToken("aa　bb　cc", "bb"))
        // 追加（全角スペースを分割したうえで、末尾に半角スペースで追加）
        assertEquals("aa bb cc dd", toggleWordToken("aa　bb　cc", "dd"))

        // 除去後の空白正規化（連続スペースなどのクリーンアップ）
        assertEquals("aa cc", toggleWordToken("  aa   bb   cc  ", "bb"))

        // 重複追加なし（トグルなので、すでにあるものは除去される）
        assertEquals("aa cc", toggleWordToken("aa bb cc", "bb"))
    }

    @Test
    fun `toggleType - 全選択は空へ畳み、単純トグルも正しく機能すること`() {
        // 空から追加
        assertEquals(setOf(NarouNovelType.SHORT), toggleType(emptySet(), NarouNovelType.SHORT))
        // 既存から追加
        assertEquals(setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI), toggleType(setOf(NarouNovelType.SHORT), NarouNovelType.RENSAI))
        // 既存から削除
        assertEquals(emptySet<NarouNovelType>(), toggleType(setOf(NarouNovelType.SHORT), NarouNovelType.SHORT))
        // 全3種選択は空集合へ正規化
        assertEquals(emptySet<NarouNovelType>(), toggleType(setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI), NarouNovelType.KANKETSU))
    }

    @Test
    fun `toggleLastup - 非連続防止が正しく機能すること`() {
        // ギャップ点灯: SEVENDAY + LASTMONTH -> THISMONTH も点灯して3種全点灯
        assertEquals(
            setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH, NarouLastup.LASTMONTH),
            toggleLastup(setOf(NarouLastup.SEVENDAY), NarouLastup.LASTMONTH)
        )
        assertEquals(
            setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH, NarouLastup.LASTMONTH),
            toggleLastup(setOf(NarouLastup.LASTMONTH), NarouLastup.SEVENDAY)
        )

        // 3種全点灯から THISMONTH 消灯 -> ギャップ解消のため LASTMONTH も消灯して SEVENDAY だけが残る
        assertEquals(
            setOf(NarouLastup.SEVENDAY),
            toggleLastup(setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH, NarouLastup.LASTMONTH), NarouLastup.THISMONTH)
        )

        // 連続する消去
        assertEquals(
            setOf(NarouLastup.SEVENDAY),
            toggleLastup(setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH), NarouLastup.THISMONTH)
        )
        assertEquals(
            setOf(NarouLastup.LASTMONTH),
            toggleLastup(setOf(NarouLastup.THISMONTH, NarouLastup.LASTMONTH), NarouLastup.THISMONTH)
        )
        
        // 全3種選択は空集合へは畳まない
        assertEquals(
            setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH, NarouLastup.LASTMONTH),
            toggleLastup(setOf(NarouLastup.SEVENDAY, NarouLastup.THISMONTH), NarouLastup.LASTMONTH)
        )
    }
}

