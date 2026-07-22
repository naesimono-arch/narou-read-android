package com.novelreader.scrape.adapter

import com.novelreader.pdf.RawChapter
import com.novelreader.scrape.HealthProbe
import com.novelreader.scrape.NovelSiteAdapter
import com.novelreader.scrape.ScrapeException
import com.novelreader.scrape.ScrapeHttpClient
import com.novelreader.scrape.ScrapedChapterRef
import com.novelreader.scrape.ScrapedToc
import com.novelreader.scrape.ScrapedWorkMeta
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * カクヨム（kakuyomu.jp）用アダプタ。
 *
 * 構造の正本＝`.claude/plans/scraping-foundation-design-2026-07-20.md`「カクヨム実構造の確定」節（ライブ recon）:
 * - **目次は `<script id="__NEXT_DATA__">` の Apollo 正規化ストアが正本**（可視 DOM のアンカーは数件しか出ない）。
 *   順序＝`Work:{id}.tableOfContentsV2[] → TableOfContentsChapter.episodeUnions[] → Episode{id,title}` を平坦化。
 *   ストアには関連作品も混在するため**必ず URL の workId で Work を選ぶ**。
 * - **章本文は DOM**: `.widget-episodeBody.js-episode-body > p`。空行は `<p class="blank">`。章題は `.widget-episodeTitle`。
 *   ルビは標準 `<ruby>base<rt>reading</rt></ruby>`（出現時のみ）→ 中間記法 `|base《reading》` へ変換。
 *
 * robots: `/works/{id}/episodes/{id}` は許容（`/read$` のみ Disallow）。スロットルは [ScrapeHttpClient] が担保。
 */
class KakuyomuAdapter(
    private val http: ScrapeHttpClient = ScrapeHttpClient(),
) : NovelSiteAdapter {

    override val siteKey: String = "kakuyomu"
    override val displayName: String = "カクヨム"

    // カクヨム robots は Crawl-delay:1 だが、章連続 DL の礼儀として 2500ms を明示宣言する（相手網に優しい保守値）。
    // この値を getString へ渡して per-host スロットルの下限に使う（宣言だけでは効かないため取得呼び出しで引き渡す）。
    override val crawlDelayMs: Long = 2500L

    // 破損監視（層3）の自己診断: fixture ゴールデン（KakuyomuGoldenTest）の元作品を使う＝期待値の二重管理を避ける。
    // minChapters=100 は当該作の実章数（撮影時 593 話）を大きく下回る保守値。著者の整理でも 100 話を割ることは
    // 考えにくく、セレクタ破損（章数が数件〜0 へ激減）だけを赤にする（通常の増減では赤にしない）。
    override val healthProbe: HealthProbe =
        HealthProbe(workUrl = "https://kakuyomu.jp/works/16816927859675616240", minChapters = 100)

    override fun canonicalWorkUrl(inputUrl: String): String? {
        val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
        if (host != "kakuyomu.jp" && host != "www.kakuyomu.jp") return null
        val workId = WORK_ID_RE.find(inputUrl)?.groupValues?.get(1) ?: return null
        return "https://kakuyomu.jp/works/$workId"
    }

    override suspend fun fetchToc(workUrl: String): ScrapedToc {
        val workId = WORK_ID_RE.find(workUrl)?.groupValues?.get(1)
            ?: throw ScrapeException("workId を URL から抽出できない: $workUrl")
        val html = http.getString(workUrl, crawlDelayMs)
        return parseToc(html, workId)
    }

    override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter {
        val html = http.getString(ref.chapterUrl, crawlDelayMs)
        return parseChapter(html, ref.title)
    }

    // ---- 純関数（fixture ゴールデンの検証対象＝ネットワーク非依存） ----

    /** 目次 HTML（`__NEXT_DATA__` 込み）から順序付き目次を組む。 */
    fun parseToc(html: String, workId: String): ScrapedToc {
        val doc = Jsoup.parse(html)
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw ScrapeException("__NEXT_DATA__ が見つからない（カクヨムの構造変更の可能性）")
        val store = findObjectContainingKey(JSONObject(nextData), "Work:$workId")
            ?: throw ScrapeException("Apollo ストア（Work:$workId）が見つからない")

        val work = store.getJSONObject("Work:$workId")
        val title = work.optString("title").ifBlank { "無題" }
        val author = resolveAuthorName(store, work)

        val chapters = mutableListOf<ScrapedChapterRef>()
        val tocRefs = work.optJSONArray("tableOfContentsV2") ?: JSONArray()
        for (i in 0 until tocRefs.length()) {
            val chapterKey = tocRefs.getJSONObject(i).optString("__ref")
            if (chapterKey.isBlank()) continue
            val chapter = store.optJSONObject(chapterKey) ?: continue
            val episodeUnions = chapter.optJSONArray("episodeUnions") ?: continue
            for (j in 0 until episodeUnions.length()) {
                val epKey = episodeUnions.getJSONObject(j).optString("__ref")
                if (epKey.isBlank()) continue
                val ep = store.optJSONObject(epKey) ?: continue
                val epId = ep.optString("id")
                if (epId.isBlank()) continue
                val epTitle = ep.optString("title").ifBlank { "（無題）" }
                chapters.add(
                    ScrapedChapterRef(
                        title = epTitle,
                        chapterUrl = "https://kakuyomu.jp/works/$workId/episodes/$epId",
                    ),
                )
            }
        }
        if (chapters.isEmpty()) {
            throw ScrapeException("目次にエピソードが1件も無い（構造変更 or 非公開作品の可能性）")
        }
        val meta = ScrapedWorkMeta(title, author, "https://kakuyomu.jp/works/$workId")
        return ScrapedToc(meta, chapters)
    }

    /** 章 HTML から本文段落列（中間ルビ記法）を組む。[refTitle]＝目次由来のフォールバック題。 */
    fun parseChapter(html: String, refTitle: String): RawChapter {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst(".widget-episodeTitle")?.text()?.trim()?.ifBlank { null } ?: refTitle
        val body = doc.selectFirst(".widget-episodeBody.js-episode-body")
            ?: throw ScrapeException("本文コンテナ .widget-episodeBody が無い（構造変更の可能性）")

        val paragraphs = mutableListOf<String>()
        for (p in body.select("p")) {
            if (p.hasClass("blank")) {
                paragraphs.add("")
            } else {
                paragraphs.add(paragraphText(p))
            }
        }
        // 全段落が空＝抽出失敗のサイン。破損監視（fixture ゴールデン差分）とは別に実行時にも弾く。
        if (paragraphs.all { it.isBlank() }) {
            throw ScrapeException("本文が空（セレクタ不一致 or 構造変更の可能性）")
        }
        return RawChapter(title, paragraphs)
    }

    /** <p> の子ノードを走査し、ルビは `|base《reading》`・その他インライン要素は可視テキストへ落とす。 */
    private fun paragraphText(p: Element): String {
        val sb = StringBuilder()
        for (node in p.childNodes()) {
            when {
                node is TextNode -> sb.append(node.wholeText) // 全角字下げ等の原文空白を保つ
                node is Element && node.tagName() == "ruby" -> sb.append(convertRuby(node))
                node is Element && node.tagName() == "br" -> {} // 空行は blank <p> で別途表現されるため無視
                node is Element -> sb.append(node.text()) // 傍点 <em> 等は可視文字のみ（既存リーダーに傍点概念なし）
            }
        }
        return sb.toString()
    }

    /** `<ruby>漢字<rt>かんじ</rt></ruby>`（<rb> 包みも許容）→ `|漢字《かんじ》`。ASCII パイプは applyRuby の要件。 */
    private fun convertRuby(ruby: Element): String {
        val reading = ruby.select("rt").text().trim()
        val base = ruby.clone().apply { select("rt, rp").remove() }.text().trim()
        return if (base.isNotEmpty() && reading.isNotEmpty()) "|$base《$reading》" else ruby.text()
    }

    private fun resolveAuthorName(store: JSONObject, work: JSONObject): String? {
        // author は {__ref:"UserAccount:..."} の形。フィールド名は "author" 前方一致で拾う（引数付きキー対策）。
        val authorKey = work.keys().asSequence().firstOrNull { it == "author" || it.startsWith("author(") }
            ?: return null
        val ref = work.optJSONObject(authorKey)?.optString("__ref")?.ifBlank { null } ?: return null
        val account = store.optJSONObject(ref) ?: return null
        return account.optString("activityName").ifBlank { account.optString("name").ifBlank { null } }
    }

    /** JSON を再帰探索し、指定キーを直接持つ最初の JSONObject（＝Apollo 正規化ストア）を返す。 */
    private fun findObjectContainingKey(root: Any?, key: String): JSONObject? {
        when (root) {
            is JSONObject -> {
                if (root.has(key)) return root
                for (k in root.keys()) {
                    findObjectContainingKey(root.opt(k), key)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until root.length()) {
                    findObjectContainingKey(root.opt(i), key)?.let { return it }
                }
            }
        }
        return null
    }

    companion object {
        private val WORK_ID_RE = Regex("""/works/(\d+)""")
    }
}
