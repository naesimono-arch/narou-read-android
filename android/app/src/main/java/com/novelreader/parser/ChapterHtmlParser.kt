package com.novelreader.parser

import com.novelreader.model.ChapterContent
import com.novelreader.model.TextSegment
import com.novelreader.model.TocEntry
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File

/**
 * 章HTMLファイルをパースして構造化データに変換するパーサー。
 * 対象は html_exporter.py が生成した chap_N.html / index.html のみ。
 */
object ChapterHtmlParser {

    /** 章HTMLをパースして ChapterContent を返す。ファイルが存在しない場合は null。 */
    fun parse(file: File): ChapterContent? {
        if (!file.exists()) return null
        val doc = Jsoup.parse(file, "UTF-8")
        val title = doc.selectFirst("h1")?.text().orEmpty()
        val contentDiv = doc.selectFirst("div.content") ?: return ChapterContent(title, persistentListOf())
        val segments = parseNodes(contentDiv.childNodes())
        return ChapterContent(title, trimEdgeBreaks(segments).toImmutableList())
    }

    /** 章HTMLを文字列からパースする（テスト用）。 */
    fun parseHtml(html: String): ChapterContent {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h1")?.text().orEmpty()
        val contentDiv = doc.selectFirst("div.content") ?: return ChapterContent(title, persistentListOf())
        val segments = parseNodes(contentDiv.childNodes())
        return ChapterContent(title, trimEdgeBreaks(segments).toImmutableList())
    }

    /** index.html をパースして目次エントリのリストを返す。 */
    fun parseToc(file: File): List<TocEntry> {
        if (!file.exists()) return emptyList()
        val doc = Jsoup.parse(file, "UTF-8")
        return parseTocHtml(doc.html())
    }

    /** index.html の文字列から目次をパースする（テスト用）。 */
    fun parseTocHtml(html: String): List<TocEntry> {
        val doc = Jsoup.parse(html)
        return doc.select("ul.index-list li a").map { a ->
            TocEntry(
                title = a.text(),
                fileName = a.attr("href"),
            )
        }
    }

    private fun parseNodes(nodes: List<Node>): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        for (node in nodes) {
            when {
                node is TextNode -> {
                    // なぜ getWholeText か: Jsoup の text() は空白を正規化して \n を消すため、
                    // pre-wrap 前提のHTMLでは改行情報が失われる
                    val text = node.wholeText.replace("\r", "")
                    splitTextWithLineBreaks(text, segments)
                }
                node is Element -> parseElement(node, segments)
            }
        }
        return segments
    }

    private fun parseElement(element: Element, segments: MutableList<TextSegment>) {
        when (element.tagName()) {
            "ruby" -> {
                val base = buildString {
                    // なぜ子ノードを手動走査するか: <ruby>漢<rt>かん</rt>字<rt>じ</rt></ruby> のような
                    // 構造で rt 以外のテキストノードだけを親文字として取る必要があるため
                    for (child in element.childNodes()) {
                        if (child is TextNode) append(child.wholeText.trim())
                        else if (child is Element && child.tagName() != "rt") append(child.text())
                    }
                }
                val reading = element.selectFirst("rt")?.text().orEmpty()
                if (base.isNotEmpty()) {
                    segments.add(TextSegment.Ruby(base, reading))
                }
            }
            "hr" -> segments.add(TextSegment.HorizontalRule)
            "br" -> segments.add(TextSegment.LineBreak)
            "b" -> {
                // 太字テキスト（前書き・後書きのラベル等）
                val text = element.text()
                if (text.isNotEmpty()) {
                    segments.add(TextSegment.Plain(text))
                }
            }
            "div" -> {
                if (isStyledBlock(element)) {
                    val label = element.selectFirst("b")?.text().orEmpty()
                    // なぜ <b> を除外して再パースするか: label は StyledBlock.label に格納済みなので、
                    // 子セグメントに重複して含めないため
                    val innerNodes = element.childNodes().filter { child ->
                        !(child is Element && child.tagName() == "b")
                    }
                    val innerSegments = parseNodes(innerNodes)
                    segments.add(TextSegment.StyledBlock(label, trimEdgeBreaks(innerSegments).toImmutableList()))
                } else if (element.hasClass("nav-footer")) {
                    // nav-footer は無視（Compose側で再実装するため）
                } else {
                    // 未知の div は中身だけ再帰走査
                    parseNodes(element.childNodes()).forEach { segments.add(it) }
                }
            }
            "rt" -> {
                // ruby の子として処理済みなので単独出現時は無視
            }
            else -> {
                // 未知の要素は中身だけ取る
                parseNodes(element.childNodes()).forEach { segments.add(it) }
            }
        }
    }

    /**
     * 前書き・後書きブロックの判定（AND条件）。
     * なぜ AND 条件か: ラベル一致だけだと本文中に <b>（前書き）</b> を含む段落を誤検知する。
     * スタイル属性との併用で chapter_processor.py が生成する構造のみに一致させる。
     */
    private fun isStyledBlock(element: Element): Boolean {
        val hasBackgroundStyle = element.attr("style").contains("background-color")
        val firstB = element.selectFirst("b")
        val label = firstB?.text().orEmpty()
        val hasKnownLabel = label == "（前書き）" || label == "（後書き）"
        return hasBackgroundStyle && hasKnownLabel
    }

    /** テキストを \n で分割し、Plain と LineBreak のセグメントに変換する。 */
    private fun splitTextWithLineBreaks(text: String, segments: MutableList<TextSegment>) {
        if (text.isEmpty()) return
        val parts = text.split("\n")
        for ((index, part) in parts.withIndex()) {
            if (part.isNotEmpty()) {
                segments.add(TextSegment.Plain(part))
            }
            if (index < parts.size - 1) {
                segments.add(TextSegment.LineBreak)
            }
        }
    }

    /**
     * .content div 直下の先頭/末尾の空白・改行をトリム。
     * なぜ: HTMLインデント由来の不要な空白・改行を除去するため。
     */
    private fun trimEdgeBreaks(segments: List<TextSegment>): List<TextSegment> {
        if (segments.isEmpty()) return segments

        var start = 0
        while (start < segments.size && isTrimmable(segments[start])) start++

        var end = segments.size - 1
        while (end >= start && isTrimmable(segments[end])) end--

        return if (start > end) emptyList() else segments.subList(start, end + 1)
    }

    private fun isTrimmable(segment: TextSegment): Boolean {
        return segment is TextSegment.LineBreak ||
            (segment is TextSegment.Plain && segment.text.isBlank())
    }
}
