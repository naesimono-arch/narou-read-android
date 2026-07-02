package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 合成 char dict ヘルパー（bottom 省略時は top+size）。移植元 test_logic.py の _ch 相当。 */
private fun ch(
    text: String,
    fontName: String?,
    size: Double,
    x0: Double,
    top: Double,
    bottom: Double = top + size,
) = CharBox(text, fontName, size, x0, top, bottom)

/**
 * TextProcessor（列復元・ルビ紐付け・段落縫合・本文抽出コア）のテスト。
 * 移植元: submission-B LogicTest / python test_logic.py の該当クラス群。
 */
class TextProcessorTest {

    // --- groupCharsByLine ---
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

    // --- associateRuby ---
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

    // --- buildLineStr ---
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

    // NBSP(U+00A0) も空白としてスキップされること（Python \xa0 の再現。WHITESPACE 欠落バグの回帰防止）
    @Test fun nbspSkipped() {
        val cs = listOf(ch("\u00a0", "R", 14.0, 100.0, 50.0), ch("あ", "R", 14.0, 100.0, 60.0))
        assertEquals("あ", TextProcessor.buildLineStr(cs))
    }

    // --- processPages ---
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

    /** 進捗コールバックが有効ページごとに (pct, processed, bodyTotal) を通知する（10〜60%）。 */
    @Test fun progressCallbackReportsPerPage() {
        val body = ch("本", "R", 14.0, 180.0, 70.0)
        // 6ページ → bodyTotal=max(6-4,1)=2、有効ページは index 3,4
        val pages = listOf(
            emptyList<CharBox>(), emptyList(), emptyList(), listOf(body), listOf(body), emptyList()
        )
        val calls = mutableListOf<Triple<Int, Int, Int>>()
        TextProcessor.processPages(pages, totalPages = 6) { pct, processed, total ->
            calls.add(Triple(pct, processed, total))
        }
        assertEquals(2, calls.size)
        assertEquals(Triple(10, 0, 2), calls[0])   // pct = 10 + int(0/2*50) = 10
        assertEquals(Triple(35, 1, 2), calls[1])   // pct = 10 + int(1/2*50) = 35
    }
}
