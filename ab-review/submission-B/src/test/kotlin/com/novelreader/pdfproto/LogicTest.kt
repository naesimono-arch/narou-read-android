package com.novelreader.pdfproto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun ch(
    text: String,
    fontName: String?,
    size: Double,
    x0: Double,
    top: Double,
    bottom: Double = top + size,
) = CharBox(text, fontName, size, x0, top, bottom)

class CheckIsTitleTest {
    @Test fun boldCorrectSize() = assertTrue(ParserRules.checkIsTitle("HogeB Bold", 14.0))
    @Test fun boldWithinTolerance() = assertTrue(ParserRules.checkIsTitle("NotoSerifCJK Bold", 13.95))
    @Test fun nonBold() = assertFalse(ParserRules.checkIsTitle("NotoSerifCJK Regular", 14.0))
    @Test fun wrongSize() = assertFalse(ParserRules.checkIsTitle("HogeB Bold", 7.0))
    @Test fun nullFontName() = assertFalse(ParserRules.checkIsTitle(null, 14.0))
}

class SplitIntoChaptersTest {
    @Test fun emptyInput() = assertEquals(0, ChapterProcessor.splitIntoChapters(emptyList()).size)

    @Test fun noTitleMarkers() {
        val r = ChapterProcessor.splitIntoChapters(listOf("本文A", "本文B"))
        assertEquals(1, r.size)
        assertEquals("作品情報・プロローグ", r[0].title)
        assertEquals(listOf("本文A", "本文B"), r[0].body)
    }

    @Test fun singleChapter() {
        val r = ChapterProcessor.splitIntoChapters(listOf("【題名】第一話　始まり", "本文A", "本文B"))
        assertEquals(1, r.size)
        assertEquals("第一話　始まり", r[0].title)
    }

    @Test fun multipleChapters() {
        val r = ChapterProcessor.splitIntoChapters(listOf("【題名】第一話", "本文1", "【題名】第二話", "本文2"))
        assertEquals(2, r.size)
        assertEquals("第一話", r[0].title)
        assertEquals("第二話", r[1].title)
    }

    @Test fun consecutiveTitlesNoBodyBetween() {
        val r = ChapterProcessor.splitIntoChapters(listOf("【題名】第一話", "【題名】第二話", "本文"))
        assertEquals(1, r.size)
        assertEquals("第二話", r[0].title)
        assertEquals(listOf("本文"), r[0].body)
    }
}

class GroupCharsByLineTest {
    @Test fun sameXGrouped() {
        val r = TextProcessor.groupCharsByLine(
            listOf(ch("あ", "R", 14.0, 100.0, 50.0), ch("い", "R", 14.0, 100.0, 70.0))
        )
        assertEquals(1, r.size)
        assertEquals(2, r.values.first().size)
    }

    @Test fun closeXWithinTolerance() {
        val anchor = ch("基", "R", 14.0, 100.0, 50.0)
        val close = ch("あ", "R", 14.0, 100.05, 50.0)  // 差0.05 → 同グループ
        val far = ch("い", "R", 14.0, 100.20, 50.0)     // 差0.20 → 別グループ
        val r = TextProcessor.groupCharsByLine(listOf(anchor, close, far))
        assertEquals(2, r.size)
    }
}

class AssociateRubyTest {
    @Test fun rubyAttachedToNearest() {
        val body = ch("漢", "R", 14.0, 200.0, 50.0)
        val ruby = ch("か", "R", 7.0, 200.0 + ParserRules.RUBY_OFFSET_X, 50.0)
        val linesDict = LinkedHashMap<Double, MutableList<CharBox>>().apply { put(200.0, mutableListOf(body)) }
        TextProcessor.associateRuby(linesDict, listOf(ruby))
        assertEquals("か", body.rubyText)
    }

    @Test fun rubyNoMatchIgnored() {
        val body = ch("漢", "R", 14.0, 200.0, 50.0)
        val ruby = ch("か", "R", 7.0, 999.0, 50.0)
        val linesDict = LinkedHashMap<Double, MutableList<CharBox>>().apply { put(200.0, mutableListOf(body)) }
        TextProcessor.associateRuby(linesDict, listOf(ruby))
        assertEquals(null, body.rubyText)
    }
}

class BuildLineStrTest {
    @Test fun rubyRunBuilt() {
        val c = ch("字", "R", 14.0, 100.0, 50.0).apply { rubyText = "よみ" }
        assertEquals("|字《よみ》", TextProcessor.buildLineStr(listOf(c)))
    }

    @Test fun plainTextNoRuby() {
        val cs = listOf(ch("あ", "R", 14.0, 100.0, 50.0), ch("い", "R", 14.0, 100.0, 60.0))
        assertEquals("あい", TextProcessor.buildLineStr(cs))
    }

    @Test fun consecutiveRubyMerged() {
        val c1 = ch("漢", "R", 14.0, 100.0, 50.0).apply { rubyText = "か" }
        val c2 = ch("字", "R", 14.0, 100.0, 60.0).apply { rubyText = "じ" }
        assertEquals("|漢字《かじ》", TextProcessor.buildLineStr(listOf(c1, c2)))
    }

    @Test fun mixedRubyAndPlain() {
        val r = ch("漢", "R", 14.0, 100.0, 50.0).apply { rubyText = "か" }
        val p = ch("字", "R", 14.0, 100.0, 60.0)
        assertEquals("|漢《か》字", TextProcessor.buildLineStr(listOf(r, p)))
    }

    @Test fun whitespaceSkipped() {
        val cs = listOf(ch(" ", "R", 14.0, 100.0, 50.0), ch("あ", "R", 14.0, 100.0, 60.0))
        assertEquals("あ", TextProcessor.buildLineStr(cs))
    }
}

class ProcessPagesTest {
    /** 5ページ構成: page0-2スキップ, page3有効, page4スキップ(total-1)。 */
    @Test fun pageExclusionTitleDetectionPagenoExclusion() {
        val bold = "NotoSerifCJK Bold"
        val reg = "NotoSerifCJK Regular"
        val skip = ch("除外", reg, 14.0, 200.0, 50.0)
        val title = ch("話", bold, 14.0, 200.0, 50.0)
        val body = ch("本", reg, 14.0, 180.0, 70.0)
        val pageno = ch("1", reg, ParserRules.FONT_SIZE_PAGE, 100.0, ParserRules.PAGE_NUM_Y)
        val pages = listOf(
            listOf(skip), emptyList(), emptyList(), listOf(title, body, pageno), listOf(skip)
        )
        val result = TextProcessor.processPages(pages, totalPages = 5)
        val joined = result.joinToString("\n")
        assertFalse(joined.contains("除外"))
        assertTrue(result.any { it.contains("【題名】") })
        assertFalse(joined.contains("1"))
    }
}

class ParseNodesTest {
    @Test fun plainOnly() {
        val nodes = ChapterProcessor.parseNodes("吾輩は猫である。")
        assertEquals(listOf(Node.Plain("吾輩は猫である。")), nodes)
    }

    @Test fun rubyOnly() {
        val nodes = ChapterProcessor.parseNodes("|名前《なまえ》")
        assertEquals(listOf(Node.Ruby("名前", "なまえ")), nodes)
    }

    @Test fun mixed() {
        val nodes = ChapterProcessor.parseNodes("彼は|名前《なまえ》を呼んだ")
        assertEquals(
            listOf(Node.Plain("彼は"), Node.Ruby("名前", "なまえ"), Node.Plain("を呼んだ")),
            nodes
        )
    }
}

class ForewordAfterwordTest {
    @Test fun forewordPrepended() {
        val chapters = listOf(
            RawChapter("前書き", mutableListOf("前書き本文")),
            RawChapter("第一話", mutableListOf("本文")),
        )
        val r = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, r.size)
        assertTrue(r[0].body.contains("前書き本文"))
    }

    @Test fun afterwordAppended() {
        val chapters = listOf(
            RawChapter("第一話", mutableListOf("本文")),
            RawChapter("後書き", mutableListOf("後書き本文")),
        )
        val r = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, r.size)
        assertTrue(r[0].body.contains("後書き本文"))
    }

    @Test fun onlyAfterwordDropped() {
        val chapters = listOf(RawChapter("後書き", mutableListOf("後書き本文")))
        assertEquals(0, ChapterProcessor.processForewordAfterword(chapters).size)
    }
}
