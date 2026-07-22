package com.novelreader.scrape.generic

import com.novelreader.pdf.RawChapter
import com.novelreader.scrape.HealthProbe
import com.novelreader.scrape.NovelSiteAdapter
import com.novelreader.scrape.ScrapeException
import com.novelreader.scrape.ScrapeHttpClient
import com.novelreader.scrape.ScrapedChapterRef
import com.novelreader.scrape.ScrapedToc
import com.novelreader.scrape.ScrapedWorkMeta
import com.novelreader.scrape.convertRuby
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * 「旧来型サーバサイド HTML」サイト用の汎用抽出エンジン。振る舞いはすべて [profile]（[SiteProfile]）が決め、
 * 1 プロファイル = 1 アダプタインスタンスとして生成する（設計正本
 * `.claude/plans/generic-adapter-design-2026-07-23.md`）。
 *
 * 出自: 退役した暁専用アダプタ実装の parseToc/parseChapter を純関数のまま汎用化したもの。暁の回帰
 * golden がそのままこのエンジンの回帰になる（挙動差ゼロが移植の完了条件）。ルビ変換は [convertRuby] 共有ヘルパへ。
 *
 * 対象外（専用アダプタで温存）: カクヨムのような JSON（`__NEXT_DATA__` の Apollo ストア）系は CSS セレクタ表で
 * 表現できないため統合しない（競合の非対称戦略「重要は厚く・他は薄く」）。
 */
class GenericSiteAdapter(
    private val profile: SiteProfile,
    private val http: ScrapeHttpClient = ScrapeHttpClient(),
) : NovelSiteAdapter {

    override val siteKey: String = profile.siteKey
    override val displayName: String = profile.displayName
    override val crawlDelayMs: Long = profile.crawlDelayMs
    override val healthProbe: HealthProbe = profile.healthProbe

    override fun canonicalWorkUrl(inputUrl: String): String? {
        val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
        // 完全一致 ＋ `.host` サフィックス（`www.` 等の下位ドメインを拾う）。どの登録ホストでもなければ非該当。
        if (profile.hosts.none { host == it || host.endsWith(".$it") }) return null
        // 作品トップ・話ページのどちらの形でも workUrlRe が canonical 化に要る値（例: 作品 id）を capture する。
        // 一致しなければこのサイトの作品/話 URL ではない（トップページ等）＝null。
        val match = profile.workUrlRe.find(inputUrl) ?: return null
        return applyTemplate(profile.workUrlTemplate, match)
    }

    override suspend fun fetchToc(workUrl: String): ScrapedToc {
        val html = http.getString(workUrl, crawlDelayMs)
        return parseToc(html, workUrl)
    }

    override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter {
        val html = http.getString(ref.chapterUrl, crawlDelayMs)
        return parseChapter(html, ref.title)
    }

    // ---- 純関数（fixture ゴールデンの検証対象＝ネットワーク非依存） ----

    /** 目次 HTML から順序付き目次を組む。[canonicalWorkUrl]＝meta に載せる正規トップ URL（相対リンクの基点にも使う）。 */
    fun parseToc(html: String, canonicalWorkUrl: String): ScrapedToc {
        val doc = Jsoup.parse(html)
        val title = firstText(doc, profile.titleSelectors)
            ?: throw ScrapeException("作品名（${profile.titleSelectors}）が見つからない（構造変更の可能性）")
        val author = profile.authorSelector?.let { doc.selectFirst(it)?.text()?.trim()?.ifBlank { null } }

        // 相対 href（例 `/stories/view/...`）を絶対化する基点＝正規トップ URL の origin（scheme://host）。
        // canonical 側で www/https へ正規化済みのため、目次リンクも同じ正規ホストで絶対化される。
        val origin = originOf(canonicalWorkUrl)
        val chapters = mutableListOf<ScrapedChapterRef>()
        for (a in doc.select(profile.tocLinkSelector)) {
            val href = a.attr("href")
            // episodeUrlRe に一致する話リンクだけを話として数える。章見出し行（リンク無し or 別形リンク）や
            // ヘッダはここで自然に弾かれる（暁では colspan の `<b>…</b>` 見出し行が該当）。
            if (!profile.episodeUrlRe.containsMatchIn(href)) continue
            val epTitle = a.text().trim().ifBlank { "（無題）" }
            chapters.add(ScrapedChapterRef(epTitle, absoluteUrl(href, origin)))
        }
        if (chapters.isEmpty()) {
            throw ScrapeException("目次に話が1件も無い（構造変更 or 非公開作品の可能性）")
        }
        return ScrapedToc(ScrapedWorkMeta(title, author, canonicalWorkUrl), chapters)
    }

    /** 章 HTML から本文段落列（中間ルビ記法）を組む。[refTitle]＝目次由来のフォールバック題。 */
    fun parseChapter(html: String, refTitle: String): RawChapter {
        val doc = Jsoup.parse(html)
        // 話題は titleSelectors の先頭一致（暁では作品名 h1 を避けるため h3→h2 の連鎖で h2 に解決）。無ければ目次由来の題。
        val title = firstText(doc, profile.titleSelectors) ?: refTitle

        val bodies = selectBodies(doc)
        if (bodies.isEmpty()) {
            throw ScrapeException("本文コンテナ（${profile.bodySelectors}）が無い（構造変更の可能性）")
        }
        // 前書き/後書きマーカーに属さない中央ブロックを本文とする。複数残る異常時は非空の先頭を採り、
        // 本文純度（前後書き非混入）を最優先する。非空ブロックが無ければ抽出失敗。
        val main = bodies.firstOrNull {
            !isForewordMarker(it.previousElementSibling()) && paragraphsOf(it).any { p -> p.isNotBlank() }
        } ?: throw ScrapeException("前書き/後書き以外の本文ブロックが空 or 無い（構造変更の可能性）")

        val paragraphs = paragraphsOf(main)
        // 全段落が空＝抽出失敗のサイン。破損監視（fixture ゴールデン差分）とは別に実行時にも弾く。
        if (paragraphs.all { it.isBlank() }) {
            throw ScrapeException("本文が空（セレクタ不一致 or 構造変更の可能性）")
        }
        return RawChapter(title, paragraphs)
    }

    // ---- 内部ヘルパ ----

    /** [selectors] を先頭から試し、最初に得た非空テキストを返す（作品名/話題の解決）。全滅なら null。 */
    private fun firstText(doc: Document, selectors: List<String>): String? {
        for (sel in selectors) {
            val t = doc.selectFirst(sel)?.text()?.trim()
            if (!t.isNullOrBlank()) return t
        }
        return null
    }

    /** bodySelectors のフォールバック連鎖: 先頭セレクタで取れなければ次へ（`a ?? b` 式）。全滅なら空。 */
    private fun selectBodies(doc: Document): Elements {
        for (sel in profile.bodySelectors) {
            val els = doc.select(sel)
            if (els.isNotEmpty()) return els
        }
        return Elements()
    }

    /** 本文コンテナの子ノードを [ParagraphMode] に従って段落列へ落とす。 */
    private fun paragraphsOf(body: Element): MutableList<String> = when (profile.paragraphMode) {
        ParagraphMode.BR -> brParagraphs(body)
        ParagraphMode.P -> pParagraphs(body)
    }

    /**
     * BR モード: コンテナ直下を走査し `<br>` 区切りで段落を切る。連続 `<br>` は空文字（空行）として保持し、
     * P モードの blank `<p>` と同じ出力契約に揃える。ルビは `|base《reading》`・その他インライン要素は可視テキストへ。
     */
    private fun brParagraphs(body: Element): MutableList<String> {
        val paragraphs = mutableListOf<String>()
        val sb = StringBuilder()
        for (node in body.childNodes()) {
            when {
                node is TextNode -> sb.append(node.wholeText) // 全角字下げ等の原文空白を保つ
                node is Element && node.tagName() == "br" -> {
                    paragraphs.add(normalizeParagraph(sb.toString()))
                    sb.setLength(0)
                }
                node is Element && node.tagName() == "ruby" -> sb.append(convertRuby(node))
                node is Element -> sb.append(node.text()) // 傍点/リンク等は可視文字のみ（既存リーダーに傍点概念なし）
            }
        }
        // 末尾 <br> 以降に残るバッファ＝最後の段落。
        if (sb.isNotEmpty()) paragraphs.add(normalizeParagraph(sb.toString()))
        return paragraphs
    }

    /** P モード: `<p>` を段落単位とし、blank クラスの `<p>` は空行（空文字）にする。中身はインライン走査で組む。 */
    private fun pParagraphs(body: Element): MutableList<String> {
        val paragraphs = mutableListOf<String>()
        for (p in body.select("p")) {
            if (p.hasClass("blank")) paragraphs.add("") else paragraphs.add(inlineText(p))
        }
        return paragraphs
    }

    /** 要素の子ノードを走査し、ルビは `|base《reading》`・その他インライン要素は可視テキストへ落とす（P モードの段落中身）。 */
    private fun inlineText(el: Element): String {
        val sb = StringBuilder()
        for (node in el.childNodes()) {
            when {
                node is TextNode -> sb.append(node.wholeText) // 全角字下げ等の原文空白を保つ
                node is Element && node.tagName() == "ruby" -> sb.append(convertRuby(node))
                node is Element && node.tagName() == "br" -> {} // P モードでは空行は blank <p> が表す＝<br> は無視
                node is Element -> sb.append(node.text())
            }
        }
        return sb.toString()
    }

    /**
     * `<br>` 区切りで生じる HTML 整形由来の改行（\n・\r）だけを端から除く。全角スペース(　)による字下げは
     * 本文の一部として保持する。`&nbsp;` や空白のみの行は空文字へ畳んで空行にする。
     */
    private fun normalizeParagraph(raw: String): String {
        val trimmed = raw.trim { it == '\n' || it == '\r' }
        return if (trimmed.isBlank()) "" else trimmed
    }

    /** 直前の要素が前書き/後書きの見出し（`<div><b>前書き|後書き</b></div>` 等）かを [SiteProfile.forewordMarkers] で判定。 */
    private fun isForewordMarker(prev: Element?): Boolean {
        if (prev == null) return false
        val label = (prev.selectFirst("b")?.text() ?: prev.text()).trim()
        return label in profile.forewordMarkers
    }

    /** 相対 href を [origin]（scheme://host）で絶対化する。既に絶対（http〜）ならそのまま。 */
    private fun absoluteUrl(href: String, origin: String): String =
        if (href.startsWith("http")) href
        else origin + (if (href.startsWith("/")) href else "/$href")

    /** URL から origin（scheme://host）を取り出す。解釈不能なら空文字（相対リンクは絶対化されず素通し）。 */
    private fun originOf(url: String): String {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return ""
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return ""
        return "$scheme://$host"
    }

    /** テンプレート中の `{1}`,`{2}`… を [match] の capture group で置換して canonical URL を組む。 */
    private fun applyTemplate(template: String, match: MatchResult): String {
        var out = template
        for (i in 1 until match.groupValues.size) {
            out = out.replace("{$i}", match.groupValues[i])
        }
        return out
    }
}
