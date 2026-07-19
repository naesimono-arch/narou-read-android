package com.novelreader.scrape

import com.novelreader.scrape.adapter.KakuyomuAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * カクヨムアダプタの純ロジック（URL 正規化・ルビ変換）の単体テスト。jsoup のみで org.json 非依存＝素の JVM で動く。
 * ルビは実 fixture に実例が無かった（著者任意で人気作でも不使用が多い）ため、標準 `<ruby>` マークアップを
 * 合成 HTML で検証する（レンダ形は標準タグ＝カクヨムヘルプの入力記法 `｜漢字《かんじ》` の描画結果）。
 */
class KakuyomuAdapterUnitTest {

    private val adapter = KakuyomuAdapter()

    @Test
    fun canonicalWorkUrl_normalizesWorkAndEpisodeUrls() {
        val work = "https://kakuyomu.jp/works/16816927859675616240"
        assertEquals(work, adapter.canonicalWorkUrl(work))
        // 話ページ URL でも作品トップへ正規化する。
        assertEquals(work, adapter.canonicalWorkUrl("$work/episodes/16816927859675631302"))
        // www 有り・末尾スラッシュも許容。
        assertEquals(work, adapter.canonicalWorkUrl("https://www.kakuyomu.jp/works/16816927859675616240/"))
        // 別サイト・非作品 URL は null。
        assertNull(adapter.canonicalWorkUrl("https://ncode.syosetu.com/n1234ab/"))
        assertNull(adapter.canonicalWorkUrl("https://kakuyomu.jp/"))
    }

    @Test
    fun parseChapter_convertsRubyToIntermediateNotation() {
        val html = """
            <div class="widget-episodeTitle">第1話</div>
            <div class="widget-episodeBody js-episode-body">
              <p id="p1">　<ruby>漢字<rt>かんじ</rt></ruby>のテスト。</p>
              <p id="p2" class="blank"><br /></p>
              <p id="p3">通常の段落。</p>
            </div>
        """.trimIndent()

        val chapter = adapter.parseChapter(html, refTitle = "fallback")

        assertEquals("第1話", chapter.title)
        assertEquals(listOf("　|漢字《かんじ》のテスト。", "", "通常の段落。"), chapter.body)
    }

    @Test
    fun parseChapter_rbWrappedRubyAlsoConverts() {
        val html = """
            <div class="widget-episodeBody js-episode-body">
              <p id="p1"><ruby><rb>星</rb><rt>ほし</rt></ruby></p>
            </div>
        """.trimIndent()
        val chapter = adapter.parseChapter(html, refTitle = "t")
        assertEquals(listOf("|星《ほし》"), chapter.body)
    }
}
