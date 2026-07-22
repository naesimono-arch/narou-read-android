package com.novelreader.scrape.adapter

import com.novelreader.pdf.RawChapter
import com.novelreader.scrape.HealthProbe
import com.novelreader.scrape.NovelSiteAdapter
import com.novelreader.scrape.ScrapeException
import com.novelreader.scrape.ScrapeHttpClient
import com.novelreader.scrape.ScrapedChapterRef
import com.novelreader.scrape.ScrapedToc
import com.novelreader.scrape.ScrapedWorkMeta
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 暁〜小説投稿サイト〜（akatsuki-novels.com）用アダプタ。
 *
 * 構造の正本＝保存済み fixture（`test/resources/scrape_fixtures/akatsuki/`・2026-07-23 取得スナップショット）:
 * - **目次は素の DOM**（カクヨムのような Apollo/JSON ストアは無い旧来型サーバサイド HTML）。作品名＝最初の `<h3>`・
 *   著者＝`/users/view/{uid}` リンク・話一覧＝`table.list` 内で `/stories/view/` アンカーを持つ `<tr>` のみ
 *   （章見出し行は colspan の `<b>…</b>` だけでリンクを持たない＝話として数えない）。ページネーション無し。
 * - **本文は複数ありうる `div.body-novel`**。前書き/後書きも同じ `div.body-novel` で表現され、直前に
 *   `<div><b>前書き</b></div>`／`<div><b>後書き</b></div>` マーカー div を伴う。本文はそのマーカーに属さない
 *   中央ブロックのみ（前書き/後書きは取り込まない＝PDF 蔵書と同じ「本文純度」契約）。段落は `<br>` 区切り・
 *   連続 `<br>` は空行として保持し、カクヨムの blank `<p>` と同じ出力契約（空文字エントリ）に揃える。
 * - ルビは大文字 `<RUBY><RB>base</RB><RP>(</RP><RT>reading</RT><RP>)</RP></RUBY>`。jsoup の HTML パーサが
 *   タグ名を小文字化するため case を問わず処理でき、`<rb>` 省略形も防御的に拾える。RP は捨て中間記法 `|base《reading》` へ。
 *
 * 規約: 個人運営の投稿サイトのため [crawlDelayMs] を既定より厚めに宣言する。スロットルは [ScrapeHttpClient] が担保。
 */
class AkatsukiAdapter(
    private val http: ScrapeHttpClient = ScrapeHttpClient(),
) : NovelSiteAdapter {

    override val siteKey: String = "akatsuki"
    override val displayName: String = "暁"

    // robots に Crawl-delay 宣言は無いが、個人運営サイトへの配慮で既定 2500ms より厚い 3000ms を明示する
    // （章連続 DL で相手網に負荷を寄せない）。getString へ渡して per-host スロットルの下限に使う（宣言だけでは効かない）。
    override val crawlDelayMs: Long = 3000L

    // 破損監視（層3）の自己診断: fixture ゴールデン（AkatsukiGoldenTest）の元作品を使う＝期待値の二重管理を避ける。
    // minChapters=30 は撮影時 66 話を大きく下回る保守値。著者の整理でも 30 話は割りにくく、
    // セレクタ破損（話数が数件〜0 へ激減）だけを赤にする（通常の増減では赤にしない）。
    override val healthProbe: HealthProbe =
        HealthProbe(workUrl = "https://www.akatsuki-novels.com/stories/index/novel_id~4679", minChapters = 30)

    override fun canonicalWorkUrl(inputUrl: String): String? {
        val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
        if (host != HOST && host != BARE_HOST) return null
        // 作品トップ（index 形）・話ページ（view 形）のいずれからも novel_id を取り、canonical index 形へ正規化する
        // （非 www・http もここで www・https の canonical に畳む）。どちらの形でもなければ非該当＝null。
        val id = INDEX_RE.find(inputUrl)?.groupValues?.get(1)
            ?: VIEW_RE.find(inputUrl)?.groupValues?.get(1)
            ?: return null
        return "https://$HOST/stories/index/novel_id~$id"
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

    /** 目次 HTML から順序付き目次を組む。[canonicalWorkUrl]＝meta に載せる正規トップ URL。 */
    fun parseToc(html: String, canonicalWorkUrl: String): ScrapedToc {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h3")?.text()?.trim()?.ifBlank { null }
            ?: throw ScrapeException("作品名の <h3> が見つからない（暁の構造変更の可能性）")
        // 著者は /users/view/{uid} リンク（目次に1つだけ）。無い作品もあり得るため null 許容。
        val author = doc.selectFirst("a[href*=/users/view/]")?.text()?.trim()?.ifBlank { null }

        val chapters = mutableListOf<ScrapedChapterRef>()
        for (row in doc.select("table.list tr")) {
            // 話行の判定は「/stories/view/ アンカーの有無」。章見出し行（colspan の <b>…</b> でリンク無し）は
            // ここで自然に弾かれ、話として数えない。thead の <th> 行も同様にリンクを持たず除外される。
            val anchor = row.select("a[href]").firstOrNull { STORY_LINK_RE.containsMatchIn(it.attr("href")) }
                ?: continue
            val epTitle = anchor.text().trim().ifBlank { "（無題）" }
            chapters.add(ScrapedChapterRef(epTitle, absoluteUrl(anchor.attr("href"))))
        }
        if (chapters.isEmpty()) {
            throw ScrapeException("目次に話が1件も無い（構造変更 or 非公開作品の可能性）")
        }
        return ScrapedToc(ScrapedWorkMeta(title, author, canonicalWorkUrl), chapters)
    }

    /** 章 HTML から本文段落列（中間ルビ記法）を組む。[refTitle]＝目次由来のフォールバック題。 */
    fun parseChapter(html: String, refTitle: String): RawChapter {
        val doc = Jsoup.parse(html)
        // 話題は <h2>（<h1> は作品名なので使わない）。無ければ目次由来のフォールバック題。
        val title = doc.selectFirst("h2")?.text()?.trim()?.ifBlank { null } ?: refTitle

        val bodies = doc.select("div.body-novel")
        if (bodies.isEmpty()) {
            throw ScrapeException("本文コンテナ div.body-novel が無い（構造変更の可能性）")
        }
        // 前書き/後書きマーカーに属さない中央ブロックを本文とする。複数残る異常時は非空の先頭を採り、
        // 本文純度（前後書き非混入）を最優先する。非空ブロックが無ければ抽出失敗。
        val main = bodies.firstOrNull {
            !isForewordAfterwordMarker(it.previousElementSibling()) &&
                bodyParagraphs(it).any { p -> p.isNotBlank() }
        } ?: throw ScrapeException("前書き/後書き以外の本文ブロックが空 or 無い（構造変更の可能性）")

        val paragraphs = bodyParagraphs(main)
        // 全段落が空＝抽出失敗のサイン。破損監視（fixture ゴールデン差分）とは別に実行時にも弾く。
        if (paragraphs.all { it.isBlank() }) {
            throw ScrapeException("本文が空（セレクタ不一致 or 構造変更の可能性）")
        }
        return RawChapter(title, paragraphs)
    }

    /** 直前の要素が前書き/後書きの見出し `<div><b>前書き|後書き</b></div>` かを判定する。 */
    private fun isForewordAfterwordMarker(prev: Element?): Boolean {
        if (prev == null) return false
        val label = (prev.selectFirst("b")?.text() ?: prev.text()).trim()
        return label == "前書き" || label == "後書き"
    }

    /**
     * `div.body-novel` の子ノードを走査し `<br>` 区切りで段落列を組む。連続 `<br>` は空文字（空行）として保持し
     * カクヨムの blank `<p>` と同じ出力契約に揃える。ルビは `|base《reading》`・その他インライン要素は可視テキストへ。
     */
    private fun bodyParagraphs(body: Element): MutableList<String> {
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

    /**
     * `<br>` 区切りで生じる HTML 整形由来の改行（\n・\r）だけを端から除く。全角スペース(　)による字下げは
     * 本文の一部として保持する（カクヨム版と同じ字下げ保持契約）。`&nbsp;` や空白のみの行は空文字へ畳んで空行にする。
     */
    private fun normalizeParagraph(raw: String): String {
        val trimmed = raw.trim { it == '\n' || it == '\r' }
        return if (trimmed.isBlank()) "" else trimmed
    }

    /** `<ruby>漢字<rt>かんじ</rt></ruby>`（`<rb>` 包み・RP 付きも許容）→ `|漢字《かんじ》`。ASCII パイプは applyRuby の要件。 */
    private fun convertRuby(ruby: Element): String {
        val reading = ruby.select("rt").text().trim()
        val base = ruby.clone().apply { select("rt, rp").remove() }.text().trim()
        return if (base.isNotEmpty() && reading.isNotEmpty()) "|$base《$reading》" else ruby.text()
    }

    /** 相対 href（例 `/stories/view/70561/novel_id~4679`）を絶対 URL へ。既に絶対ならそのまま。 */
    private fun absoluteUrl(href: String): String =
        if (href.startsWith("http")) href else "https://$HOST${if (href.startsWith("/")) href else "/$href"}"

    companion object {
        private const val HOST = "www.akatsuki-novels.com"
        private const val BARE_HOST = "akatsuki-novels.com"
        private val INDEX_RE = Regex("""/stories/index/novel_id~(\d+)""")
        private val VIEW_RE = Regex("""/stories/view/\d+/novel_id~(\d+)""")
        private val STORY_LINK_RE = Regex("""/stories/view/\d+/novel_id~\d+""")
    }
}
