package com.novelreader.parser

import com.novelreader.model.TextSegment
import com.novelreader.model.TocEntry
import org.junit.Assert.*
import org.junit.Test

class ChapterHtmlParserTest {

    private fun wrapChapterHtml(content: String, title: String = "テスト章"): String = """
        <!DOCTYPE html>
        <html lang="ja">
        <head><meta charset="UTF-8"><title>$title</title></head>
        <body>
            <div class="container">
                <h1>$title</h1>
                <div class="content">$content</div>
            </div>
            <div class="nav-footer">
                <a href="index.html">← 前へ</a>
                <a href="index.html">目次</a>
                <a href="chap_2.html">次へ →</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    // --- ルビ ---

    @Test
    fun `ルビ付きテキスト`() {
        val html = wrapChapterHtml("""<ruby>漢<rt>かん</rt></ruby>""")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals("テスト章", result.title)
        assertEquals(1, result.segments.size)
        val ruby = result.segments[0] as TextSegment.Ruby
        assertEquals("漢", ruby.base)
        assertEquals("かん", ruby.reading)
    }

    @Test
    fun `同長ルビ連続`() {
        val html = wrapChapterHtml(
            """<ruby>漢<rt>かん</rt></ruby><ruby>字<rt>じ</rt></ruby>"""
        )
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(2, result.segments.size)
        val ruby1 = result.segments[0] as TextSegment.Ruby
        assertEquals("漢", ruby1.base)
        assertEquals("かん", ruby1.reading)
        val ruby2 = result.segments[1] as TextSegment.Ruby
        assertEquals("字", ruby2.base)
        assertEquals("じ", ruby2.reading)
    }

    @Test
    fun `異長ルビ`() {
        val html = wrapChapterHtml("""<ruby>漢字<rt>かんじ</rt></ruby>""")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(1, result.segments.size)
        val ruby = result.segments[0] as TextSegment.Ruby
        assertEquals("漢字", ruby.base)
        assertEquals("かんじ", ruby.reading)
    }

    // --- 改行 ---

    @Test
    fun `改行保持`() {
        val html = wrapChapterHtml("一行目\n二行目")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(3, result.segments.size)
        assertEquals(TextSegment.Plain("一行目"), result.segments[0])
        assertEquals(TextSegment.LineBreak, result.segments[1])
        assertEquals(TextSegment.Plain("二行目"), result.segments[2])
    }

    @Test
    fun `CRLF混在`() {
        val html = wrapChapterHtml("一行目\r\n二行目")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(3, result.segments.size)
        assertEquals(TextSegment.Plain("一行目"), result.segments[0])
        assertEquals(TextSegment.LineBreak, result.segments[1])
        assertEquals(TextSegment.Plain("二行目"), result.segments[2])
    }

    @Test
    fun `連続改行`() {
        val html = wrapChapterHtml("一行目\n\n\n二行目")
        val result = ChapterHtmlParser.parseHtml(html)
        // 一行目, LB, LB, LB, 二行目
        assertEquals(5, result.segments.size)
        assertEquals(TextSegment.Plain("一行目"), result.segments[0])
        assertEquals(TextSegment.LineBreak, result.segments[1])
        assertEquals(TextSegment.LineBreak, result.segments[2])
        assertEquals(TextSegment.LineBreak, result.segments[3])
        assertEquals(TextSegment.Plain("二行目"), result.segments[4])
    }

    // --- 先頭末尾トリム ---

    @Test
    fun `先頭末尾空白トリム`() {
        // div.content の直下にHTMLインデント由来の改行が入るケース
        val html = wrapChapterHtml("\n本文テキスト\n")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(1, result.segments.size)
        assertEquals(TextSegment.Plain("本文テキスト"), result.segments[0])
    }

    // --- 水平線 ---

    @Test
    fun `水平線`() {
        val html = wrapChapterHtml("前文<hr>後文")
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(3, result.segments.size)
        assertEquals(TextSegment.Plain("前文"), result.segments[0])
        assertEquals(TextSegment.HorizontalRule, result.segments[1])
        assertEquals(TextSegment.Plain("後文"), result.segments[2])
    }

    // --- 前書き・後書き ---

    @Test
    fun `前書きブロック`() {
        val html = wrapChapterHtml(
            """<div style="background-color: #f9f9f9; padding: 15px; border: 1px solid #eee; margin-bottom: 20px;"><b>（前書き）</b><br>前書き本文</div>"""
        )
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(1, result.segments.size)
        val block = result.segments[0] as TextSegment.StyledBlock
        assertEquals("（前書き）", block.label)
        assertTrue(block.segments.any {
            it is TextSegment.Plain && it.text == "前書き本文"
        })
    }

    @Test
    fun `後書きブロック`() {
        val html = wrapChapterHtml(
            """<div style="background-color: #f9f9f9; padding: 15px; border: 1px solid #eee; margin-top: 20px;"><b>（後書き）</b><br>後書き本文</div>"""
        )
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(1, result.segments.size)
        val block = result.segments[0] as TextSegment.StyledBlock
        assertEquals("（後書き）", block.label)
        assertTrue(block.segments.any {
            it is TextSegment.Plain && it.text == "後書き本文"
        })
    }

    // --- nav-footer 無視 ---

    @Test
    fun `navFooter無視`() {
        val html = wrapChapterHtml("本文テキスト")
        val result = ChapterHtmlParser.parseHtml(html)
        // nav-footer 内のリンクテキストが混入していないことを確認
        val allText = result.segments.filterIsInstance<TextSegment.Plain>().joinToString("") { it.text }
        assertFalse(allText.contains("前へ"))
        assertFalse(allText.contains("次へ"))
        assertFalse(allText.contains("目次"))
    }

    // --- 目次パース ---

    @Test
    fun `目次パース`() {
        val html = """
            <html><body>
                <ul class="index-list">
                    <li><a href="chap_1.html">第一話</a></li>
                    <li><a href="chap_2.html">第二話</a></li>
                </ul>
            </body></html>
        """.trimIndent()
        val toc = ChapterHtmlParser.parseTocHtml(html)
        assertEquals(2, toc.size)
        assertEquals(TocEntry("第一話", "chap_1.html"), toc[0])
        assertEquals(TocEntry("第二話", "chap_2.html"), toc[1])
    }

    @Test
    fun `目次0件`() {
        val html = """
            <html><body>
                <ul class="index-list"></ul>
            </body></html>
        """.trimIndent()
        val toc = ChapterHtmlParser.parseTocHtml(html)
        assertTrue(toc.isEmpty())
    }

    // --- 不正rubyタグ ---

    @Test
    fun `不正rubyタグ - rt欠落`() {
        val html = wrapChapterHtml("""<ruby>漢字</ruby>""")
        val result = ChapterHtmlParser.parseHtml(html)
        // rt欠落でもクラッシュしない。reading は空文字列になる
        assertEquals(1, result.segments.size)
        val ruby = result.segments[0] as TextSegment.Ruby
        assertEquals("漢字", ruby.base)
        assertEquals("", ruby.reading)
    }

    // --- golden HTML ---

    @Test
    fun `golden HTML chap_1`() {
        val html = """
        <!DOCTYPE html>
        <html lang="ja">
        <head><meta charset="UTF-8"><title>第一話　ゴールデンテスト</title></head>
        <body>
            <div class="container">
                <h1>第一話　ゴールデンテスト</h1>
                <div class="content">
この<ruby>物語<rt>ものがたり</rt></ruby>は始まる。
第二段落。
                </div>
            </div>
            <div class="nav-footer">
                <a href="index.html">← 前へ</a>
                <a href="index.html">目次</a>
                <a href="chap_2.html">次へ →</a>
            </div>
        </body>
        </html>
        """.trimIndent()
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals("第一話　ゴールデンテスト", result.title)

        // "この" + Ruby("物語", "ものがたり") + "は始まる。" + LB + "第二段落。"
        val segs = result.segments
        assertEquals(TextSegment.Plain("この"), segs[0])
        assertEquals(TextSegment.Ruby("物語", "ものがたり"), segs[1])
        assertEquals(TextSegment.Plain("は始まる。"), segs[2])
        assertEquals(TextSegment.LineBreak, segs[3])
        assertEquals(TextSegment.Plain("第二段落。"), segs[4])
        assertEquals(5, segs.size)
    }

    // --- ルビ混在テキスト ---

    @Test
    fun `ルビと通常テキストの混在`() {
        val html = wrapChapterHtml(
            """すべては<ruby>終<rt>お</rt></ruby>わった。"""
        )
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals(3, result.segments.size)
        assertEquals(TextSegment.Plain("すべては"), result.segments[0])
        assertEquals(TextSegment.Ruby("終", "お"), result.segments[1])
        assertEquals(TextSegment.Plain("わった。"), result.segments[2])
    }

    // --- content div が存在しない ---

    @Test
    fun `content divなし`() {
        val html = """<html><body><h1>タイトル</h1></body></html>"""
        val result = ChapterHtmlParser.parseHtml(html)
        assertEquals("タイトル", result.title)
        assertTrue(result.segments.isEmpty())
    }
}
