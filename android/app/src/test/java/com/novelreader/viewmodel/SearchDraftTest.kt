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
    fun `toQuery - notWordがtrimされ空白のみはnullとして送出されないこと`() {
        // なぜ: 除外語も word と同じ trim→空→null 変換で一貫させ、空白のみの入力で notword を送らない
        assertEquals("R15", SearchDraft(notWord = "  R15 ").toQuery().notWord)
        assertNull(SearchDraft(notWord = "   ").toQuery().notWord)
        assertNull(SearchDraft().toQuery().notWord)
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
    fun `resultTitle - 複数キーワードは件数表記に畳まれること`() {
        // 複数語の羅列見出しは1行で見切れるため件数の要約形にする（各語は結果一覧の KEYWORD チップに出る）
        assertEquals("キーワード3件", SearchDraft(word = "魔法 学園　日常").resultTitle())
        assertEquals("キーワード2件", SearchDraft(word = " 薬師  スローライフ ").resultTitle())
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
    fun `wordTokens - 半角・全角スペースで分割され空トークンが除去されること`() {
        // 半角・全角スペース区切り（containsWordToken/toggleWordToken と同一規則）
        assertEquals(listOf("aa", "bb", "cc"), wordTokens("aa bb　cc"))
        // 連続・前後空白は空トークンとして除去され、順序は保持される
        assertEquals(listOf("aa", "bb"), wordTokens("  aa   bb  "))
        // 空文字列・空白のみは空リスト
        assertTrue(wordTokens("").isEmpty())
        assertTrue(wordTokens("   　 ").isEmpty())
        // 単一トークン
        assertEquals(listOf("薬師"), wordTokens("薬師"))
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
    fun `Set化メンバシップが containsWordToken と全ケースで同値であること`() {
        // なぜ: DiscoverySearchContent はチップ選択判定を wordTokens(word).toSet() の O(1) メンバシップへ
        // 置き換えた（Regex 再コンパイル＋線形探索の削減）。従来の containsWordToken（List.contains）と
        // Set.contains が全ケースで一致することを固定し、パフォーマンス改善が挙動を変えていないことを担保する。
        val cases = listOf(
            "aa bb　cc" to "bb",
            "aa bb　cc" to "dd",
            "" to "aa",
            "  aa   bb  " to "aa",
            "薬師 スローライフ" to "スローライフ",
            "薬師　スローライフ" to "薬師",
        )
        for ((word, token) in cases) {
            assertEquals(
                "word=\"$word\" token=\"$token\"",
                containsWordToken(word, token),
                wordTokens(word).toSet().contains(token),
            )
        }
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

    @Test
    fun `selectedStepIndices - プリセット単段・合成2段・開端含む合成・カスタム値・nullのパースが正しいこと`() {
        // プリセット単段
        assertEquals(setOf(0), selectedStepIndices("-10000", LENGTH_STEPS))
        assertEquals(setOf(1), selectedStepIndices("10000-100000", LENGTH_STEPS))
        assertEquals(setOf(4), selectedStepIndices("1000000-", LENGTH_STEPS))

        // 合成2段
        assertEquals(setOf(1, 2), selectedStepIndices("10000-500000", LENGTH_STEPS))
        assertEquals(setOf(2, 3), selectedStepIndices("100000-1000000", LENGTH_STEPS))

        // 開端含む合成
        assertEquals(setOf(0, 1), selectedStepIndices("-100000", LENGTH_STEPS))
        assertEquals(setOf(3, 4), selectedStepIndices("500000-", LENGTH_STEPS))

        // カスタム値 -> 空
        assertEquals(emptySet<Int>(), selectedStepIndices("25000-80000", LENGTH_STEPS))
        // null -> 空
        assertEquals(emptySet<Int>(), selectedStepIndices(null, LENGTH_STEPS))
    }

    @Test
    fun `toggleRangeStep - 隣接追加・非隣接追加・端の消灯・中抜き消灯・全段点灯・全消しが正しく機能すること`() {
        // 隣接追加
        assertEquals("10000-500000", toggleRangeStep("10000-100000", 2, LENGTH_STEPS))

        // 非隣接追加で間の段が点灯
        // 10000-100000 (index=1) と 500000-1000000 (index=3) -> index=2も点灯し 10000-1000000
        assertEquals("10000-1000000", toggleRangeStep("10000-100000", 3, LENGTH_STEPS))

        // 端の消灯 (上端消去で下側が残る)
        // 10000-500000 (index=1,2) から index=2 を消去 -> 10000-100000 (index=1のみ残る)
        assertEquals("10000-100000", toggleRangeStep("10000-500000", 2, LENGTH_STEPS))

        // 中抜き消灯 (kを境に下側 [i..k-1] を残す)
        // 10000-1000000 (index=1,2,3) から index=2 を消去 -> 10000-100000 (index=1のみ残る)
        assertEquals("10000-100000", toggleRangeStep("10000-1000000", 2, LENGTH_STEPS))

        // 下端の消灯 (残る上側を保つ。下端を外して選択全体が消えるのは期待に反する)
        // 10000-500000 (index=1,2) から index=1 を消去 -> 100000-500000 (index=2が残る)
        assertEquals("100000-500000", toggleRangeStep("10000-500000", 1, LENGTH_STEPS))

        // 全段点灯 -> null
        // -10000 (index=0) から index=4 を点灯 -> 全段点灯となり null へ正規化
        assertNull(toggleRangeStep("-10000", 4, LENGTH_STEPS))

        // 全消し -> null
        // 100000-500000 (index=2 of 単段) から index=2 を消去 -> 空となり null
        assertNull(toggleRangeStep("100000-500000", 2, LENGTH_STEPS))
    }

    @Test
    fun `toggleRangeStep - カスタム値からの切り替えで干渉がなくタップしたチップが選択されること`() {
        // カスタム値 "25000-80000" に index=1 (10000-100000) をトグル
        // カスタム値は selectedStepIndices で emptySet() になるため、新規に index=1 のみが選択される
        assertEquals("10000-100000", toggleRangeStep("25000-80000", 1, LENGTH_STEPS))
    }

    @Test
    fun `buildCustomRange - 負数入力は不正なレンジ文字列を生成せず無視されること`() {
        // なぜ: 負数を通すと "-50000-50000" のようなハイフン3連の不正形を API 挙動未定義のまま送出してしまう
        assertNull(buildCustomRange("-5", "", 10000))
        assertEquals("-50000", buildCustomRange("-5", "5", 10000)) // 負数側だけ無視され max のみの開レンジになる
    }

    @Test
    fun `buildCustomRange - Int上限を超える乗算は桁あふれで負数化せず上限へ丸められること`() {
        // なぜ: 30万(万字)=3×10^9 は Int を溢れて負数になり不正形を送出する（Long 計算＋上限丸めで防ぐ）
        assertEquals("${Int.MAX_VALUE}-", buildCustomRange("300000", "", 10000))
    }

    @Test
    fun `normalizeCustomRangeInput - 全角数字は半角へ写像され数字以外は除去されること`() {
        // なぜ: 欄に見えているのに toIntOrNull が null になる「見えている条件が送出されない」
        // サイレント無効（ADR 0007 原則2違反・nottensei 欠落と同族）を入力層で構造的に防ぐ
        assertEquals("100", normalizeCustomRangeInput("１００"))
        assertEquals("15", normalizeCustomRangeInput("1O5万字-")) // 英字 O（数字でない）・単位・記号は落ちる
        assertEquals("", normalizeCustomRangeInput("abc-"))
    }

    @Test
    fun `toQuery - ジャンル（biggenres・genres）が DiscoveryQuery へ引き渡されること`() {
        val q1 = SearchDraft(filters = SearchFilters(biggenres = setOf(1))).toQuery()
        assertEquals(setOf(1), q1.biggenres)
        assertTrue(q1.genres.isEmpty())

        val q2 = SearchDraft(filters = SearchFilters(genres = setOf(101, 201))).toQuery()
        assertEquals(setOf(101, 201), q2.genres)
        assertTrue(q2.biggenres.isEmpty())
    }

    @Test
    fun `activeCount - ジャンル選択が有効条件として数えられること`() {
        // 大ジャンル1件で +1
        assertEquals(1, SearchFilters(biggenres = setOf(1)).activeCount())
        // 詳細ジャンル2件で +2
        assertEquals(2, SearchFilters(genres = setOf(101, 102)).activeCount())
        // 他条件との合算（作品種別1 + 詳細ジャンル1 = 2）
        assertEquals(
            2,
            SearchFilters(types = setOf(NarouNovelType.SHORT), genres = setOf(305)).activeCount()
        )
    }

    @Test
    fun `isActive - ジャンル選択だけでも有効になること`() {
        assertTrue(SearchFilters(biggenres = setOf(2)).isActive)
        assertTrue(SearchFilters(genres = setOf(201)).isActive)
        assertTrue(SearchDraft(filters = SearchFilters(genres = setOf(201))).canSearch)
    }

    @Test
    fun `withBiggenreToggled - 大ジャンルの足し引きと詳細ジャンルの相互排他が機能すること`() {
        // 空から大ジャンル追加
        val f1 = SearchFilters().withBiggenreToggled(1)
        assertEquals(setOf(1), f1.biggenres)
        assertTrue(f1.genres.isEmpty())

        // 同じコードを再タップで除去（＝ジャンル指定なしへ戻る）
        val f2 = f1.withBiggenreToggled(1)
        assertTrue(f2.biggenres.isEmpty())
        assertTrue(f2.genres.isEmpty())

        // 詳細ジャンルが立っている状態で大ジャンルを立てると、詳細は相互排他で掃かれる
        // なぜ: biggenre と genre を同時送信すると API 上 AND で交わりが空になり結果が消えるため（§4.2）
        val f3 = SearchFilters(genres = setOf(101)).withBiggenreToggled(2)
        assertEquals(setOf(2), f3.biggenres)
        assertTrue(f3.genres.isEmpty())
    }

    @Test
    fun `withGenreToggled - 詳細ジャンルの足し引きと大ジャンルの相互排他が機能すること`() {
        // 空から詳細ジャンル追加
        val f1 = SearchFilters().withGenreToggled(101)
        assertEquals(setOf(101), f1.genres)
        assertTrue(f1.biggenres.isEmpty())

        // 同一大ジャンル内で別の詳細を追加（複数選択は同一パラメータ内 OR で安全）
        val f2 = f1.withGenreToggled(102)
        assertEquals(setOf(101, 102), f2.genres)
        assertTrue(f2.biggenres.isEmpty())

        // 詳細を再タップで除去
        val f3 = f2.withGenreToggled(101)
        assertEquals(setOf(102), f3.genres)

        // 大ジャンルが立っている状態で詳細を立てると、大ジャンルは相互排他で掃かれる
        val f4 = SearchFilters(biggenres = setOf(1)).withGenreToggled(201)
        assertEquals(setOf(201), f4.genres)
        assertTrue(f4.biggenres.isEmpty())
    }
}

