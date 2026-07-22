package com.novelreader.scrape

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

/**
 * [ScrapeHttpClient] の per-host スロットルとリトライ/バックオフの単体テスト。
 *
 * 決定論の作り方（既存 scrape テストの nowMs/sleep 差替え設計を踏襲）:
 * - 仮想時計 [FakeClock] を注入し、sleep は実待機せず仮想時刻を進めるだけ＝実時間ゼロで挙動を観測する。
 * - 生 GET は [ScriptedFetch] で差し替え（呼び出し回数を数え、応答/例外を1件ずつ台本で返す）。
 * - 乱数は seed 固定（Full Jitter の上限は base=500ms＝Retry-After の秒より十分小さく、比較を安定させる）。
 */
class ScrapeHttpClientTest {

    /** 仮想時計＝時刻源と待機の継ぎ目。sleep は実待機せず now を進め、待機量を記録する。 */
    private class FakeClock(start: Long = 1_000_000L) {
        var now = start
            private set
        val sleeps = mutableListOf<Long>()
        suspend fun sleep(ms: Long) {
            sleeps.add(ms)
            if (ms > 0) now += ms
        }
    }

    /** 生 GET の台本＝呼び出しごとに [steps] の関数を順に実行（本文 return か例外 throw）。回数を [calls] に数える。 */
    private class ScriptedFetch(private vararg val steps: () -> String) {
        var calls = 0
            private set
        val fetch: suspend (String) -> String = {
            val step = steps[minOf(calls, steps.size - 1)]
            calls++
            step()
        }
    }

    private fun client(
        clock: FakeClock,
        fetch: ScriptedFetch,
        globalFloorMs: Long = 1000L,
        maxRetries: Int = 2,
    ) = ScrapeHttpClient(
        globalFloorMs = globalFloorMs,
        maxRetries = maxRetries,
        nowMs = { clock.now },
        sleep = clock::sleep,
        random = Random(0),
        fetch = fetch.fetch,
    )

    // ① 同一ホストへの連打は crawlDelayMs 待つ。
    @Test
    fun sameHost_consecutive_waitsCrawlDelay() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch({ "a" }, { "b" })
        val http = client(clock, fetch)
        val url = "https://kakuyomu.jp/works/1"

        http.getString(url, crawlDelayMs = 2500L) // 初回は待たない（直前リクエスト無し）
        http.getString(url, crawlDelayMs = 2500L) // 2回目は crawlDelay を待つ

        assertEquals(listOf(2500L), clock.sleeps)
    }

    // ② 別ホストはそのホストの crawlDelay を消化済みでなくても、横断のグローバル床 1000ms のみ待つ。
    @Test
    fun differentHost_waitsOnlyGlobalFloor() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch({ "a" }, { "b" })
        val http = client(clock, fetch)

        http.getString("https://kakuyomu.jp/works/1", crawlDelayMs = 2500L)
        http.getString("https://example.com/x", crawlDelayMs = 2500L) // 別ホスト＝crawlDelay は無関係

        // crawlDelay(2500) ではなくグローバル床(1000) だけ待つ。
        assertEquals(listOf(1000L), clock.sleeps)
    }

    // ③ 429 + Retry-After はその秒数以上待って再試行し、成功を返す。
    @Test
    fun http429_withRetryAfter_waitsAtLeastThatAndRetries() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch(
            { throw HttpStatusException(429, retryAfterSeconds = 3, message = "429") },
            { "ok" },
        )
        val http = client(clock, fetch)

        val body = http.getString("https://kakuyomu.jp/works/1")

        assertEquals("ok", body)
        assertEquals(2, fetch.calls) // 1回失敗→1回成功
        // Retry-After=3s を下限に尊重＝3000ms 以上の待機が1回起きている（Full Jitter 上限500msでは超えない）。
        assertTrue("expected a backoff >= 3000ms, got ${clock.sleeps}", clock.sleeps.any { it >= 3000L })
    }

    // ④ 403 は再試行せず1回で ScrapeException（BAN 回避＝これ以上叩かない）。
    @Test
    fun http403_failsImmediatelyWithoutRetry() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch({ throw HttpStatusException(403, retryAfterSeconds = null, message = "403") })
        val http = client(clock, fetch)

        try {
            http.getString("https://kakuyomu.jp/works/1")
            fail("expected ScrapeException")
        } catch (e: ScrapeException) {
            assertTrue(e.message!!.contains("403"))
        }
        assertEquals(1, fetch.calls) // 再試行していない
    }

    // ④' 404 も恒久失敗＝再試行しない。
    @Test
    fun http404_failsImmediatelyWithoutRetry() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch({ throw HttpStatusException(404, retryAfterSeconds = null, message = "404") })
        val http = client(clock, fetch)

        try {
            http.getString("https://kakuyomu.jp/works/1")
            fail("expected ScrapeException")
        } catch (e: ScrapeException) {
            assertTrue(e.message!!.contains("404"))
        }
        assertEquals(1, fetch.calls)
    }

    // ⑤ リトライ上限超過で失敗する（総試行 = 1 + maxRetries）。
    @Test
    fun retryable_exhaustsThenFails() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch({ throw IOException("transient") })
        val http = client(clock, fetch, maxRetries = 2)

        try {
            http.getString("https://kakuyomu.jp/works/1")
            fail("expected ScrapeException")
        } catch (e: ScrapeException) {
            assertTrue(e.cause is IOException)
        }
        assertEquals(3, fetch.calls) // 1 + maxRetries(2)
    }

    // ⑥ 503 は一時障害扱いで再試行し、回復すれば成功を返す。
    @Test
    fun http503_isRetriedThenSucceeds() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch(
            { throw HttpStatusException(503, retryAfterSeconds = null, message = "503") },
            { "recovered" },
        )
        val http = client(clock, fetch)

        val body = http.getString("https://kakuyomu.jp/works/1")

        assertEquals("recovered", body)
        assertEquals(2, fetch.calls)
    }

    // ⑦ 一過性 IOException は再試行し、回復すれば成功（timeout/DNS/瞬断のクラス）。
    @Test
    fun transientIOException_isRetriedThenSucceeds() = runBlocking {
        val clock = FakeClock()
        val fetch = ScriptedFetch(
            { throw IOException("dns") },
            { "ok" },
        )
        val http = client(clock, fetch)

        assertEquals("ok", http.getString("https://kakuyomu.jp/works/1"))
        assertEquals(2, fetch.calls)
    }
}
