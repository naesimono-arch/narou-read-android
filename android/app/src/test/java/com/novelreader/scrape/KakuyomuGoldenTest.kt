package com.novelreader.scrape

import com.novelreader.scrape.adapter.KakuyomuAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * カクヨム抽出の恒久回帰＝**破損監視の核**（handover 最優先B「急な変化の監視」の実体）。
 *
 * 保存済み実 HTML（`test/resources/scrape_fixtures/kakuyomu/`・2026-07-20 取得スナップショット）を
 * ネットワーク非依存でパースし、目次順序・件数・章本文の抽出結果を固定値と突き合わせる。
 * カクヨムが HTML/JSON 構造を変えたら parse が例外を投げるか件数/本文が変わり、`testDebugUnitTest` が
 * 赤くなって**サイト変更を機械検知**できる（fixture を撮り直して差分を見れば復旧点が分かる）。
 *
 * org.json（TOC の Apollo ストア解析）を JVM で動かすため Robolectric を使う（`JvmGoldenRegressionTest` と同方針）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KakuyomuGoldenTest {

    private val adapter = KakuyomuAdapter()

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("scrape_fixtures/kakuyomu/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun toc_parsesOrderedEpisodeList() {
        val workId = "16816927859675616240"
        val toc = adapter.parseToc(fixture("toc_$workId.html"), workId)

        // 作品メタ: 当該 workId の Work を選べている（関連作品の混入に負けていない）。
        assertTrue("title=${toc.meta.title}", toc.meta.title.contains("領民0人スタート"))
        assertEquals("https://kakuyomu.jp/works/$workId", toc.meta.workUrl)

        // 件数（スナップショット固定値）＝構造ドリフト検知の主センサ。
        assertEquals(593, toc.chapters.size)

        // 先頭エピソードの題・URL（順序が壊れていないことの固定点）。
        val first = toc.chapters.first()
        assertEquals("第1話　領主生活の始まり", first.title)
        assertEquals(
            "https://kakuyomu.jp/works/$workId/episodes/16816927859675631302",
            first.chapterUrl,
        )

        // 全 URL が正規形・題が非空。
        assertTrue(toc.chapters.all { it.chapterUrl.startsWith("https://kakuyomu.jp/works/$workId/episodes/") })
        assertTrue(toc.chapters.all { it.title.isNotBlank() })
    }

    @Test
    fun chapter_parsesBodyParagraphs() {
        val epId = "16816927859675631302"
        val chapter = adapter.parseChapter(fixture("episode_$epId.html"), refTitle = "fallback")

        assertEquals("第1話　領主生活の始まり", chapter.title)
        // 先頭は空段落（<p class="blank">）→ 本文が続く。
        assertTrue("body size=${chapter.body.size}", chapter.body.size > 10)
        assertEquals("", chapter.body.first())
        // 原文の全角字下げを保ったまま本文段落が拾えている。
        assertTrue(chapter.body.any { it == "　人の役に立つ仕事をするように。" })
        // 全空でない（実行時の抽出失敗ガードと同じ観点）。
        assertTrue(chapter.body.any { it.isNotBlank() })
    }
}
