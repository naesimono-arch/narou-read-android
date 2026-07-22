package com.novelreader.scrape

import com.novelreader.scrape.generic.GenericSiteAdapter
import com.novelreader.scrape.generic.ParagraphMode
import com.novelreader.scrape.generic.SiteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 汎用エンジン（[GenericSiteAdapter]）のプロファイル駆動挙動を、暁固有の fixture に依存しない合成 HTML で固定する
 * （設計正本のテスト計画 2）。段落モード両系統・bodySelectors フォールバック連鎖・episodeUrlRe フィルタ・
 * pending ゲート回帰を、エンジンが将来のプロファイル追加で崩れないよう機械で押さえる。jsoup のみ＝素の JVM で動く。
 */
class GenericSiteAdapterUnitTest {

    /** BR モード: `<br>` 区切りで段落を切り、連続 `<br>` は空行として保持・ルビは中間記法へ。 */
    @Test
    fun paragraphMode_BR_splitsOnBrAndKeepsBlankLines() {
        val adapter = GenericSiteAdapter(profile(paragraphMode = ParagraphMode.BR))
        val html = "<h2>題</h2><div class=\"body\">一行目<br>二行目<ruby>漢<rt>かん</rt></ruby><br><br>四行目</div>"
        val body = adapter.parseChapter(html, refTitle = "fallback").body
        assertEquals(listOf("一行目", "二行目|漢《かん》", "", "四行目"), body)
    }

    /** P モード: `<p>` を段落単位とし、`<p class="blank">` を空行にする・ルビは中間記法へ。 */
    @Test
    fun paragraphMode_P_treatsEachPAsParagraphAndBlankClassAsEmpty() {
        val adapter = GenericSiteAdapter(profile(paragraphMode = ParagraphMode.P))
        val html = "<h2>題</h2><div class=\"body\"><p>x</p><p class=\"blank\"></p><p>y<ruby>漢<rt>かん</rt></ruby></p></div>"
        val body = adapter.parseChapter(html, refTitle = "fallback").body
        assertEquals(listOf("x", "", "y|漢《かん》"), body)
    }

    /** bodySelectors フォールバック連鎖: 先頭セレクタが不在なら次のセレクタで本文を拾う。 */
    @Test
    fun bodySelectors_fallbackChain_usesNextSelectorWhenFirstAbsent() {
        val adapter = GenericSiteAdapter(
            profile(paragraphMode = ParagraphMode.BR, bodySelectors = listOf("div.missing", "div.present")),
        )
        val html = "<h2>題</h2><div class=\"present\">本文だけがここにある</div>"
        val chapter = adapter.parseChapter(html, refTitle = "fallback")
        assertEquals(listOf("本文だけがここにある"), chapter.body)
    }

    /** episodeUrlRe 判定: tocLinkSelector で拾った `<a>` のうち href が episodeUrlRe に一致する話だけを数える。 */
    @Test
    fun episodeUrlRe_filtersNonEpisodeAnchors() {
        val adapter = GenericSiteAdapter(
            profile(
                tocLinkSelector = "a[href]",
                episodeUrlRe = Regex("""/read/\d+"""),
                titleSelectors = listOf("h1"),
            ),
        )
        val html = """
            <h1>作品名</h1>
            <a href="/read/1">第1話</a>
            <a href="/about">これは話ではない</a>
            <a href="/read/2">第2話</a>
        """.trimIndent()
        val toc = adapter.parseToc(html, "https://example.com/work/1")

        assertEquals("作品名", toc.meta.title)
        assertEquals(2, toc.chapters.size)
        assertEquals(listOf("第1話", "第2話"), toc.chapters.map { it.title })
        // 相対 href が canonicalWorkUrl の origin で絶対化される。
        assertEquals(
            listOf("https://example.com/read/1", "https://example.com/read/2"),
            toc.chapters.map { it.chapterUrl },
        )
    }

    /**
     * 規約ゲート回帰: NG 裁定4サイト（2026-07-23 グレー保守裁定＝blockedHosts）＋裁定待ちハーメルン（pendingHosts）の
     * URL は Supported にならず Blocked（公式送り）へ落ちる。
     * catch-all（G2）追加時にこれらが取り込み対象へ滑り落ちないことの防波堤（設計正本 pendingHosts ゲート）。
     */
    @Test
    fun pendingHosts_areNeverSupported() {
        val registry = SiteAdapterRegistry()
        val pendingUrls = listOf(
            "https://syosetu.org/novel/123456/",
            "https://www.alphapolis.co.jp/novel/123/456",
            "https://www.pixiv.net/novel/show.php?id=12345",
            "https://no-ichigo.jp/novel/123",
            "https://www.berrys-cafe.jp/novel/123",
        )
        for (url in pendingUrls) {
            val r = registry.resolve(url)
            assertTrue("$url が Supported に落ちた（pending ゲート漏れ）: $r", r !is SiteAdapterRegistry.Resolution.Supported)
            assertTrue("$url が Blocked（公式送り）でない: $r", r is SiteAdapterRegistry.Resolution.Blocked)
        }
    }

    // ---- テスト用プロファイル生成（未使用フィールドはダミー） ----

    private fun profile(
        paragraphMode: ParagraphMode = ParagraphMode.BR,
        bodySelectors: List<String> = listOf("div.body"),
        tocLinkSelector: String = "a[href]",
        episodeUrlRe: Regex = Regex("""/read/\d+"""),
        titleSelectors: List<String> = listOf("h2", "h1"),
    ): SiteProfile = SiteProfile(
        siteKey = "test",
        displayName = "テスト",
        hosts = listOf("example.com"),
        workUrlRe = Regex("""/work/(\d+)"""),
        workUrlTemplate = "https://example.com/work/{1}",
        tocLinkSelector = tocLinkSelector,
        episodeUrlRe = episodeUrlRe,
        titleSelectors = titleSelectors,
        authorSelector = null,
        bodySelectors = bodySelectors,
        paragraphMode = paragraphMode,
        healthProbe = HealthProbe("https://example.com/work/1", minChapters = 1),
    )
}
