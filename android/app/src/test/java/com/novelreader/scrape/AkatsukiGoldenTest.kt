package com.novelreader.scrape

import com.novelreader.scrape.adapter.AkatsukiAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 暁抽出の恒久回帰＝**破損監視の核**（KakuyomuGoldenTest と同方針）。
 *
 * 保存済み実 HTML（`test/resources/scrape_fixtures/akatsuki/`・2026-07-23 取得スナップショット）を
 * ネットワーク非依存でパースし、目次件数・話順・本文抽出（ルビ変換・前後書き除外）を固定値と突き合わせる。
 * 暁が HTML 構造を変えたら件数/本文が変わって `testDebugUnitTest` が赤くなり、サイト変更を機械検知できる。
 *
 * jsoup を JVM で動かすため Robolectric を使う（KakuyomuGoldenTest / JvmGoldenRegressionTest と同方針）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AkatsukiGoldenTest {

    private val adapter = AkatsukiAdapter()
    private val workUrl = "https://www.akatsuki-novels.com/stories/index/novel_id~4679"

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("scrape_fixtures/akatsuki/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun toc_parsesOrderedEpisodeList_excludingChapterHeadings() {
        val toc = adapter.parseToc(fixture("toc_4679.html"), workUrl)

        // 作品メタ（固定値）: 最初の <h3>＝作品名・/users/view リンク＝著者。
        assertEquals("【IS】例えばこんな生活は。", toc.meta.title)
        assertEquals("海戦型", toc.meta.author)
        assertEquals(workUrl, toc.meta.workUrl)

        // 件数＝構造ドリフト検知の主センサ。章見出し行（colspan の <b>…</b>・リンク無し）が混入すると 66 を超える。
        assertEquals(66, toc.chapters.size)

        // 先頭・末尾の話題と URL（順序が壊れていないことの固定点）。
        val first = toc.chapters.first()
        assertEquals("パラレル外伝　例えばこんなISはISじゃなくてOFだろ", first.title)
        assertEquals("https://www.akatsuki-novels.com/stories/view/81871/novel_id~4679", first.chapterUrl)
        val last = toc.chapters.last()
        assertEquals("例えばこんな……あとがき", last.title)
        assertEquals("https://www.akatsuki-novels.com/stories/view/63989/novel_id~4679", last.chapterUrl)

        // 全 URL が話 URL 形・題が非空。章見出し文字列（見出し行のテキスト）が話題に紛れていない。
        assertTrue(toc.chapters.all { STORY_URL_RE.containsMatchIn(it.chapterUrl) })
        assertTrue(toc.chapters.all { it.title.isNotBlank() })
        assertFalse(toc.chapters.any { it.title == "本編" || it.title == "例えばこんなオマケって" })
    }

    @Test
    fun chapter_convertsRuby_uppercaseTags() {
        val chapter = adapter.parseChapter(fixture("episode_70561.html"), refTitle = "fallback")

        assertEquals("例えばこんな厳しい事を言われれば普通凹むだろ", chapter.title)
        val joined = chapter.body.joinToString("\n")
        // 大文字 <RUBY><RB>base</RB>…<RT>reading</RT></RUBY> が中間記法へ変換される実例（3件固定）。
        assertTrue(joined.contains("|篠ノ之束《ねーちゃん》"))
        assertTrue(joined.contains("|博士《しょうじょ》"))
        assertTrue(joined.contains("|孵化《うま》"))
        // 本文中のルビは 3 件ちょうど（後書きにはルビが無い＝中央ブロックだけを見ている証跡）。
        assertEquals(3, RUBY_MARK_RE.findAll(joined).count())
    }

    @Test
    fun chapter_excludesForewordAndAfterword() {
        val chapter = adapter.parseChapter(fixture("episode_106103.html"), refTitle = "fallback")

        assertEquals("例えばこんな俺は赦されないと思ってただろ", chapter.title)
        val joined = chapter.body.joinToString("\n")
        // 前書き（「ギャグを求めている人は…」）・後書き（「真田親子の懺悔…」）が本文へ混入しない。
        assertFalse(joined.contains("ギャグを求めている"))
        assertFalse(joined.contains("真田親子の懺悔"))
        // 中央ブロック（本文）が正しく拾えている＝先頭は本文の書き出し。
        assertTrue(joined.contains("ゴエモンの顔からは一切の人間らしい表情が無くなっていた"))
        // 本文は 20 字以上（空抽出でない）。
        assertTrue("body chars=${joined.length}", joined.replace("\n", "").length >= 20)
    }

    companion object {
        private val STORY_URL_RE =
            Regex("""^https://www\.akatsuki-novels\.com/stories/view/\d+/novel_id~4679$""")
        private val RUBY_MARK_RE = Regex("""\|[^|《》]+《[^|《》]+》""")
    }
}
