package com.novelreader.scrape

import com.novelreader.scrape.adapter.AkatsukiAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 暁アダプタの純ロジック単体テスト（ネットワーク・fixture 非依存）。URL 正規化と大文字/小文字/rb 省略形のルビ変換を固定する。
 * Jsoup・java.net.URI はいずれも純 JVM 実装のため Robolectric 不要（org.json を要する目次解析は AkatsukiGoldenTest 側）。
 */
class AkatsukiAdapterUnitTest {

    private val adapter = AkatsukiAdapter()
    private val canonical = "https://www.akatsuki-novels.com/stories/index/novel_id~4679"

    @Test
    fun canonicalWorkUrl_indexForm_isKeptCanonical() {
        assertEquals(canonical, adapter.canonicalWorkUrl(canonical))
    }

    @Test
    fun canonicalWorkUrl_episodeForm_normalizesToIndex() {
        assertEquals(
            canonical,
            adapter.canonicalWorkUrl("https://www.akatsuki-novels.com/stories/view/70561/novel_id~4679"),
        )
    }

    @Test
    fun canonicalWorkUrl_bareHostAndHttp_normalizesToWwwHttps() {
        assertEquals(canonical, adapter.canonicalWorkUrl("http://akatsuki-novels.com/stories/index/novel_id~4679"))
        assertEquals(canonical, adapter.canonicalWorkUrl("http://akatsuki-novels.com/stories/view/81871/novel_id~4679"))
    }

    @Test
    fun canonicalWorkUrl_otherSiteOrNonStoryPath_isNull() {
        assertNull(adapter.canonicalWorkUrl("https://kakuyomu.jp/works/16816927859675616240"))
        assertNull(adapter.canonicalWorkUrl("https://www.akatsuki-novels.com/tops/"))
        assertNull(adapter.canonicalWorkUrl("not a url"))
    }

    // ---- ルビ変換（parseChapter 経由＝実運用の経路そのものを検証） ----

    private fun bodyOf(rubyHtml: String): String {
        val html = "<html><body><h2>題</h2><div class=\"body-novel\">前${rubyHtml}後</div></body></html>"
        return adapter.parseChapter(html, refTitle = "fallback").body.joinToString("\n")
    }

    @Test
    fun ruby_uppercaseTagsWithRb_convertToIntermediate() {
        // 実 HTML は大文字 <RUBY><RB>base</RB><RP>(</RP><RT>reading</RT><RP>)</RP></RUBY>。RP は捨てる。
        val body = bodyOf("<RUBY><RB>約束された勝利の剣</RB><RP>(</RP><RT>エクスカリバー</RT><RP>)</RP></RUBY>")
        assertTrue(body, body.contains("前|約束された勝利の剣《エクスカリバー》後"))
    }

    @Test
    fun ruby_lowercaseTagsWithRb_convertToIntermediate() {
        val body = bodyOf("<ruby><rb>漢字</rb><rt>かんじ</rt></ruby>")
        assertTrue(body, body.contains("前|漢字《かんじ》後"))
    }

    @Test
    fun ruby_rbOmittedForm_convertToIntermediate() {
        val body = bodyOf("<ruby>孵化<rt>うま</rt></ruby>")
        assertTrue(body, body.contains("前|孵化《うま》後"))
    }
}
