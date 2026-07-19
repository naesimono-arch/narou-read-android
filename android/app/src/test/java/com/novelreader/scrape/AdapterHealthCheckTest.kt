package com.novelreader.scrape

import com.novelreader.pdf.RawChapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 破損監視・層3（[AdapterHealthCheck]）の単体テスト。**実ネットワークを張らない**: 固定応答を返す
 * fake アダプタ（成功／各種失敗）を渡し、緑/赤判定と理由テキストを固定する。1件の失敗が他判定を巻き込まないことも見る。
 */
class AdapterHealthCheckTest {

    // ── 成功: TOC が下限以上・先頭章の本文が非空 → 緑 ──────────────────────
    @Test
    fun `runAll - 健全なアダプタは緑`() = runTest {
        val reports = AdapterHealthCheck(listOf(HealthyFake())).runAll()
        assertEquals(1, reports.size)
        assertTrue(reports[0].healthy)
        assertEquals("healthy", reports[0].siteKey)
    }

    // ── 赤①: TOC の章数が期待下限未満 ────────────────────────────────────
    @Test
    fun `runAll - 章数が期待下限未満は赤`() = runTest {
        val reports = AdapterHealthCheck(listOf(TocTooShortFake())).runAll()
        assertFalse(reports[0].healthy)
        assertTrue("理由に章数不足が出る: ${reports[0].detail}", reports[0].detail.contains("期待下限"))
    }

    // ── 赤②: 先頭章の本文が空 ────────────────────────────────────────────
    @Test
    fun `runAll - 先頭章の本文が空は赤`() = runTest {
        val reports = AdapterHealthCheck(listOf(EmptyBodyFake())).runAll()
        assertFalse(reports[0].healthy)
        assertTrue("理由に本文空が出る: ${reports[0].detail}", reports[0].detail.contains("本文が空"))
    }

    // ── 赤③: 取得が例外を投げても赤に集約し、他アダプタの判定は続行する ──────
    @Test
    fun `runAll - 例外は赤に集約し他アダプタの緑判定を巻き込まない`() = runTest {
        val reports = AdapterHealthCheck(listOf(ThrowingFake(), HealthyFake())).runAll()
        assertEquals(2, reports.size)
        assertFalse("例外アダプタは赤", reports[0].healthy)
        assertTrue("例外メッセージが理由に出る: ${reports[0].detail}", reports[0].detail.contains("取得失敗"))
        assertTrue("後続の健全アダプタは緑のまま", reports[1].healthy)
    }

    // ── fake 群（fetchToc/fetchChapter だけ差し替え・URL 解決は使わない）──────

    private abstract class BaseFake(
        override val siteKey: String,
        minChapters: Int,
    ) : NovelSiteAdapter {
        override val displayName: String = siteKey
        override val healthProbe: HealthProbe = HealthProbe("https://x.example/works/1", minChapters)
        override fun canonicalWorkUrl(inputUrl: String): String? = null // 健全性診断では使わない
    }

    private class HealthyFake : BaseFake("healthy", minChapters = 1) {
        override suspend fun fetchToc(workUrl: String): ScrapedToc = ScrapedToc(
            ScrapedWorkMeta("作品", "著者", workUrl),
            listOf(ScrapedChapterRef("第一話", "$workUrl/episodes/1")),
        )
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            RawChapter("第一話", mutableListOf("十分に長い本文がここにある。"))
    }

    private class TocTooShortFake : BaseFake("tooshort", minChapters = 5) {
        override suspend fun fetchToc(workUrl: String): ScrapedToc = ScrapedToc(
            ScrapedWorkMeta("作品", "著者", workUrl),
            listOf(ScrapedChapterRef("第一話", "$workUrl/episodes/1")),
        )
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            RawChapter("第一話", mutableListOf("本文"))
    }

    private class EmptyBodyFake : BaseFake("emptybody", minChapters = 1) {
        override suspend fun fetchToc(workUrl: String): ScrapedToc = ScrapedToc(
            ScrapedWorkMeta("作品", "著者", workUrl),
            listOf(ScrapedChapterRef("第一話", "$workUrl/episodes/1")),
        )
        // 全行 blank＝実文字 0（本文セレクタ破損を模す）。
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            RawChapter("第一話", mutableListOf("", "　"))
    }

    private class ThrowingFake : BaseFake("throwing", minChapters = 1) {
        override suspend fun fetchToc(workUrl: String): ScrapedToc =
            throw ScrapeException("取得失敗（ネットワーク断を模す）")
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            throw ScrapeException("到達しない")
    }
}
