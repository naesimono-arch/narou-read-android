package com.novelreader.narou

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

/**
 * リトライ単一集約点 [retryWithBackoff] の決定論テスト（UX監査 add+errtext）。
 * delayFn を注入して実待機を捨て（即時ディレイ）、Random は固定 seed で Full Jitter を再現可能にする。
 */
class RetryWithBackoffTest {

    /** 実待機を記録する fake clock。返り値は捨てる（待たない）。 */
    private class RecordingDelay {
        val waits = mutableListOf<Long>()
        val fn: suspend (Long) -> Unit = { waits.add(it) }
    }

    @Test
    fun `成功したら再試行せず値を返す`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        val result = retryWithBackoff(delayFn = delay.fn, isRetryable = { true }) {
            calls++; "ok"
        }
        assertEquals("ok", result)
        assertEquals("1回で成功＝呼び出しは1回", 1, calls)
        assertTrue("成功時は待機しない", delay.waits.isEmpty())
    }

    @Test
    fun `一過性失敗の後に成功したら再試行して成功する`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        val result = retryWithBackoff(delayFn = delay.fn, random = Random(1), isRetryable = { true }) {
            calls++
            if (calls < 3) throw IOException("transient") else "recovered"
        }
        assertEquals("recovered", result)
        assertEquals("2回失敗＋3回目成功", 3, calls)
        assertEquals("失敗2回分だけ待機する", 2, delay.waits.size)
    }

    @Test
    fun `再試行を使い切ったら最後の例外を投げる`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        val boom = IOException("still failing")
        val thrown = runCatching {
            retryWithBackoff(maxRetries = 2, delayFn = delay.fn, isRetryable = { true }) {
                calls++; throw boom
            }
        }.exceptionOrNull()
        assertSame(boom, thrown)
        assertEquals("総試行 = 1 + maxRetries(2)", 3, calls)
    }

    @Test
    fun `非リトライ対象は即座に投げ再試行しない`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        val fatal = IllegalStateException("4xx 相当")
        val thrown = runCatching {
            retryWithBackoff(delayFn = delay.fn, isRetryable = { false }) {
                calls++; throw fatal
            }
        }.exceptionOrNull()
        assertSame(fatal, thrown)
        assertEquals("再試行しない＝呼び出しは1回", 1, calls)
        assertTrue(delay.waits.isEmpty())
    }

    @Test
    fun `CancellationException は再試行せず素通しする`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        val thrown = runCatching {
            retryWithBackoff(delayFn = delay.fn, isRetryable = { true }) {
                calls++; throw CancellationException("scope closed")
            }
        }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals("キャンセルは制御フロー＝再試行しない", 1, calls)
    }

    @Test
    fun `Retry-After が指定されればジッタより優先し下限として尊重する`() = runTest {
        val delay = RecordingDelay()
        var calls = 0
        // Full Jitter 上限（base=500, attempt0 で最大500）を超える Retry-After=2000ms を返す。
        runCatching {
            retryWithBackoff(
                maxRetries = 1,
                baseDelayMs = 500L,
                delayFn = delay.fn,
                random = Random(42),
                retryAfterMs = { 2_000L },
                isRetryable = { true },
            ) { calls++; throw IOException("429") }
        }
        assertEquals(1, delay.waits.size)
        assertTrue("Retry-After(2000ms) が下限として効く", delay.waits.first() >= 2_000L)
    }
}
