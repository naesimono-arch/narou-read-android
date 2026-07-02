package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChapterProcessor.processForewordAfterword の前後書き整形・ルビ・エスケープテスト。
 * 移植元: python test_logic.py TestProcessForewordAfterwword（12件）。
 */
class ProcessForewordAfterwordTest {

    private fun chap(title: String, vararg body: String) = RawChapter(title, body.toMutableList())

    @Test fun rubySingleChar() {
        // 1文字ルビ → <ruby>字<rt>よみ</rt></ruby>
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|字《よみ》")))
        assertTrue(result[0].body.contains("<ruby>字<rt>よみ</rt></ruby>"))
    }

    @Test fun rubyMultiCharSameLength() {
        // 2文字+2文字 → 1文字ずつ分割
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|漢字《かじ》")))
        assertTrue(result[0].body.contains("<ruby>漢<rt>か</rt></ruby>"))
        assertTrue(result[0].body.contains("<ruby>字<rt>じ</rt></ruby>"))
    }

    @Test fun rubyMultiCharDifferentLength() {
        // 親文字とルビの文字数が異なる → まとめて 1 つの ruby タグ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|三文字《よみ》")))
        assertTrue(result[0].body.contains("<ruby>三文字<rt>よみ</rt></ruby>"))
    }

    @Test fun htmlSpecialCharsAreEscaped() {
        // 本文中の < > & はエスケープされ生タグとして解釈されない
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "a < b & c > d")))
        val body = result[0].body
        assertTrue(body.contains("a &lt; b &amp; c &gt; d"))
        assertFalse(body.contains("a < b"))
    }

    @Test fun escapeAndRubyCoexist() {
        // エスケープ後もルビは <ruby> へ変換され、親文字内の & も実体参照になる
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|A&B《えび》")))
        assertTrue(result[0].body.contains("<ruby>A&amp;B<rt>えび</rt></ruby>"))
    }

    @Test fun forewordPrepended() {
        // 前書きは次章の先頭へ付与
        val chapters = listOf(chap("前書き", "前書き本文"), chap("第一話", "本文"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（前書き）"))
        assertTrue(result[0].body.contains("前書き本文"))
    }

    @Test fun afterwordAppended() {
        // 後書きは直前章の末尾へ付与
        val chapters = listOf(chap("第一話", "本文"), chap("後書き", "後書き本文"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（後書き）"))
    }

    @Test fun noForewordAfterword() {
        // 前書き・後書きなし → そのまま通過
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "本文")))
        assertEquals(1, result.size)
        assertEquals("第一話", result[0].title)
    }

    @Test fun onlyForewordNoFollowingChapter() {
        // 前書きのみ＝tempForeword がセットされるが使われずドロップ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("前書き", "前書き本文")))
        assertEquals(emptyList<ProcessedChapter>(), result)
    }

    @Test fun onlyAfterwordNoPrecedingChapter() {
        // 後書きのみ＝前章が無いため if finalChapters チェックでドロップ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("後書き", "後書き本文")))
        assertEquals(emptyList<ProcessedChapter>(), result)
    }

    @Test fun rubyUnmatchedEmptyReading() {
        // |字《》 → [^》]+ は空にマッチせずマーカーがそのまま残る（クラッシュしない）
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|字《》")))
        assertTrue(result[0].body.contains("|字《》"))
        assertFalse(result[0].body.contains("<ruby>"))
    }

    @Test fun rubyInAfterwordBody() {
        // 後書き本文のルビマーカーも変換される
        val chapters = listOf(chap("第一話", "本文"), chap("後書き", "|字《よみ》"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertTrue(result[0].body.contains("<ruby>字<rt>よみ</rt></ruby>"))
    }
}
