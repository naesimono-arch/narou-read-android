package com.novelreader

import com.novelreader.narou.WebBookCheckState
import com.novelreader.pdf.RawChapter
import com.novelreader.scrape.HealthProbe
import com.novelreader.scrape.NovelSiteAdapter
import com.novelreader.scrape.ScrapeException
import com.novelreader.scrape.ScrapedChapterRef
import com.novelreader.scrape.ScrapedToc
import com.novelreader.scrape.ScrapedWorkMeta
import com.novelreader.scrape.SiteAdapterRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1 Web 蔵書パスの **結線** テスト（[NewEpisodeCheckWorker.fetchWebSiteTotals]）。
 *
 * 何を縛るか＝「**どの本が実際にサイトへ再フェッチされたか**」の1点。判定そのものの契約
 * （shouldCheckWebBookNow の真偽・差分計算・基準値の据え置き）は WebNewEpisodeCheckLogicTest が、
 * URL 解決の3値は SiteAdapterRegistryTest が既に持つので**ここでは再表明しない**。
 * ここでしか見えないのは「ゲートを通った本だけにリクエストが出る」という結線で、フェッチ回数を
 * 記録するフェイクのアダプタ束を [SiteAdapterRegistry] へ差して観測する（実ネットワークは張らない）。
 */
class NewEpisodeCheckWorkerTest {

    private fun state(
        bookId: String,
        device: Int,
        lastRead: Int,
        sourceUrl: String = workUrlOf(bookId),
    ) = WebBookCheckState(
        bookId = bookId,
        bookTitle = "Web本$bookId",
        sourceUrl = sourceUrl,
        deviceChapterCount = device,
        lastReadChapterNumber = lastRead,
    )

    @Test
    fun `読了した本だけがサイトへ照会される（読み残しの本へは1リクエストも出ない）`() = runTest {
        val adapter = RecordingAdapter(chapters = mapOf(workUrlOf("done") to 13))
        val totals = NewEpisodeCheckWorker.fetchWebSiteTotals(
            states = listOf(
                state("done", device = 10, lastRead = 10), // 最終章に到達＝照会対象
                state("reading", device = 10, lastRead = 9), // 読み残し＝端末内に続きがある
                state("unread", device = 10, lastRead = 0), // 未読
                state("empty", device = 0, lastRead = 0), // 実体欠損（比較基準が作れない）
            ),
            registry = SiteAdapterRegistry(adapters = listOf(adapter)),
        )

        assertEquals("照会は読了本の1冊のみ", listOf(workUrlOf("done")), adapter.fetchedWorkUrls)
        // 総話数＝目次の章数。載るのは実際に照会できた本だけ（残り3冊は基準値ごと据え置かれる）。
        assertEquals(mapOf("done" to 13), totals)
    }

    @Test
    fun `規約ゲートで落ちるサイトの蔵書は読了でも照会されない`() = runTest {
        // 取込後にサイトが Blocked/pending へ移った蔵書・アダプタが外れた蔵書が該当する。
        // registry の解決が Supported 以外なら、フェッチ手前で落ちること（＝1リクエストも出ない）を固定する。
        val adapter = RecordingAdapter(chapters = mapOf(workUrlOf("ok") to 5))
        val totals = NewEpisodeCheckWorker.fetchWebSiteTotals(
            states = listOf(
                state("blocked", device = 3, lastRead = 3, sourceUrl = "https://ncode.syosetu.com/n1234ab/"),
                state("unknown", device = 3, lastRead = 3, sourceUrl = "https://unknown.example/works/9"),
                state("ok", device = 3, lastRead = 3),
            ),
            registry = SiteAdapterRegistry(adapters = listOf(adapter)),
        )

        assertEquals(listOf(workUrlOf("ok")), adapter.fetchedWorkUrls)
        assertEquals(mapOf("ok" to 5), totals)
    }

    @Test
    fun `フェッチに失敗した本は結果に載らず 他の本の照会は続く`() = runTest {
        // 失敗の本を totals へ載せない＝基準値が据え置かれ「増分不明の日は判定しない」へ倒れる
        // （据え置き側の帰結は WebNewEpisodeCheckLogicTest が持つ。ここは入口＝非搭載になることだけ）。
        val adapter = RecordingAdapter(
            chapters = mapOf(workUrlOf("ng") to 7, workUrlOf("ok") to 9),
            failing = setOf(workUrlOf("ng")),
        )
        val totals = NewEpisodeCheckWorker.fetchWebSiteTotals(
            states = listOf(state("ng", device = 3, lastRead = 3), state("ok", device = 3, lastRead = 3)),
            registry = SiteAdapterRegistry(adapters = listOf(adapter)),
        )

        assertEquals("失敗しても後続の本を道連れにしない", listOf(workUrlOf("ng"), workUrlOf("ok")), adapter.fetchedWorkUrls)
        assertEquals(mapOf("ok" to 9), totals)
    }

    @Test
    fun `照会対象が1冊も無ければアダプタ束に一切触らない`() = runTest {
        val adapter = RecordingAdapter(chapters = emptyMap())
        val totals = NewEpisodeCheckWorker.fetchWebSiteTotals(
            states = listOf(state("reading", device = 10, lastRead = 9)),
            registry = SiteAdapterRegistry(adapters = listOf(adapter)),
        )

        assertTrue(totals.isEmpty())
        assertTrue("URL 解決すら走らない", adapter.fetchedWorkUrls.isEmpty())
    }

    private fun workUrlOf(bookId: String) = "https://${RecordingAdapter.HOST}/works/$bookId"

    /**
     * 目次取得の呼び出し先 URL を記録するだけのテスト用アダプタ（ネットワーク非依存）。
     * [chapters] の値がその作品の章数＝サイト総話数、[failing] の URL は ScrapeException を投げる
     * （ScrapeHttpClient/各アダプタが失敗を正規化した後の姿＝本関数が受け取る唯一の失敗契約）。
     */
    private class RecordingAdapter(
        private val chapters: Map<String, Int>,
        private val failing: Set<String> = emptySet(),
    ) : NovelSiteAdapter {
        override val siteKey: String = "recording"
        override val displayName: String = "記録用テストサイト"

        /** 実際に目次を取りに行った作品 URL（呼ばれた順）。 */
        val fetchedWorkUrls = mutableListOf<String>()

        override fun canonicalWorkUrl(inputUrl: String): String? {
            val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
            return if (host == HOST) inputUrl.trim() else null
        }

        override suspend fun fetchToc(workUrl: String): ScrapedToc {
            fetchedWorkUrls += workUrl
            if (workUrl in failing) throw ScrapeException("テスト用の一過性失敗")
            val count = chapters.getValue(workUrl)
            return ScrapedToc(
                ScrapedWorkMeta("テスト作品", null, workUrl),
                (1..count).map { ScrapedChapterRef("第${it}話", "$workUrl/episodes/$it") },
            )
        }

        // 新着チェックは目次の章数しか見ない＝本文取得へ来たら結線が壊れている（黙って通さず落とす）。
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            throw AssertionError("新着チェックは本文を取得しないはず: ${ref.chapterUrl}")

        // 破損監視・層3 の自己診断宣言（本テストは probe を実行しないが IF 実装のため必須）。
        override val healthProbe: HealthProbe = HealthProbe("https://$HOST/works/probe", minChapters = 1)

        companion object {
            const val HOST = "recording.example"
        }
    }
}
