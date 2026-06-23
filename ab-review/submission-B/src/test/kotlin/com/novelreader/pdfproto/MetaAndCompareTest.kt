package com.novelreader.pdfproto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun cb(text: String, size: Double, x0: Double, top: Double) =
    CharBox(text, "R", size, x0, top, top + size)

class TitleFromCharsTest {
    @Test fun emptyReturnsPlaceholder() =
        assertEquals("不明なタイトル", PdfExtractor.titleFromChars(emptyList()))

    @Test fun picksMaxSizeAndOrdersByTopThenX0() {
        val chars = listOf(
            cb("小", 12.0, 0.0, 0.0),     // 小さい→除外
            cb("イ", 20.0, 50.0, 10.0),   // 同じ最大サイズ、top=10
            cb("タ", 20.0, 10.0, 0.0),    // top=0
            cb("ル", 20.0, 30.0, 0.0),    // top=0, x0=30
        )
        // top 昇順 → 同 top は x0 昇順 ：タ(0,10) ル(0,30) イ(10,50)
        assertEquals("タルイ", PdfExtractor.titleFromChars(chars))
    }
}

class AuthorFromCharsTest {
    @Test fun picks12ptExcludingFooter() {
        val chars = listOf(
            cb("著", ParserRules.FONT_SIZE_AUTHOR, 100.0, 50.0),
            cb("者", ParserRules.FONT_SIZE_AUTHOR, 120.0, 50.0),
            cb("脚", ParserRules.FONT_SIZE_AUTHOR, 100.0, ParserRules.COVER_FOOTER_Y), // フッター→除外
            cb("大", 20.0, 100.0, 50.0), // サイズ違い→除外
        )
        assertEquals("著者", PdfExtractor.authorFromChars(chars))
    }
}

class NormalizeTest {
    @Test fun waveDashCanonicalized() =
        assertEquals(GoldenComparator.normalize("〜"), GoldenComparator.normalize("～"))

    @Test fun plainTextUnchanged() =
        assertEquals("吾輩は猫である", GoldenComparator.normalize("吾輩は猫である"))
}

class GoldenComparatorTest {
    private fun book(title: String, author: String, vararg chapters: Chapter) =
        Book(title, author, chapters.toList())

    private fun ch(title: String, vararg nodes: Node) = Chapter(title, nodes.toList())

    @Test fun identicalBooksScorePerfect() {
        val b = book("題", "作", ch("第一話", Node.Plain("本文"), Node.Ruby("漢字", "かんじ")))
        val r = GoldenComparator.compare(b, b)
        assertTrue(r.titleMatch)
        assertTrue(r.authorMatch)
        assertEquals(1.0, r.chapterTitleMatchRate)
        assertEquals(1.0, r.lineCoverage)
        assertEquals(1.0, r.rubyPrecision)
        assertEquals(1.0, r.rubyRecall)
    }

    @Test fun waveDashDifferenceIsNotPenalized() {
        val golden = book("ロA〜B", "作", ch("章", Node.Plain("text")))
        val cand = book("ロA～B", "作", ch("章", Node.Plain("text")))
        assertTrue(GoldenComparator.compare(golden, cand).titleMatch)
    }

    @Test fun missingRubyLowersRecall() {
        val golden = book("t", "a", ch("章", Node.Ruby("漢", "かん"), Node.Ruby("字", "じ")))
        val cand = book("t", "a", ch("章", Node.Ruby("漢", "かん")))
        val r = GoldenComparator.compare(golden, cand)
        assertEquals(0.5, r.rubyRecall)
        assertEquals(1.0, r.rubyPrecision)
    }

    @Test fun noRubyOnBothSidesIsFullScore() {
        val b = book("t", "a", ch("章", Node.Plain("ルビ無し本文")))
        val r = GoldenComparator.compare(b, b)
        assertEquals(1.0, r.rubyPrecision)
        assertEquals(1.0, r.rubyRecall)
    }

    @Test fun chapterCountMismatchReported() {
        val golden = book("t", "a", ch("一", Node.Plain("x")), ch("二", Node.Plain("y")))
        val cand = book("t", "a", ch("一", Node.Plain("x")))
        val r = GoldenComparator.compare(golden, cand)
        assertEquals(2, r.goldenChapters)
        assertEquals(1, r.candidateChapters)
        assertFalse(r.goldenChapters == r.candidateChapters)
    }
}
