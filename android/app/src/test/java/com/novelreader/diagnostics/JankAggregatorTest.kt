package com.novelreader.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * フレーム落ち集計の純ロジックを固定する。
 *
 * ここで守りたいのは「実利用の裾（たまに来る長いフレーム）が平均に埋もれて消えない」こと＝
 * バケット分類とパーセンタイル近似の境界挙動。実際のフレーム供給（JankStats）は端末側なので対象外。
 */
class JankAggregatorTest {

    private fun ms(v: Double): Long = (v * 1_000_000).toLong()

    @Test
    fun `画面ごとに分けて数える`() {
        val agg = JankAggregator()
        agg.record("bookshelf", ms(8.0), isJank = false)
        agg.record("bookshelf", ms(40.0), isJank = true)
        agg.record("reading", ms(8.0), isJank = false)

        val byScreen = agg.snapshot().associateBy { it.screen }
        // Long リテラルで書く: assertEquals(Int, Long) は (Object,Object) 版に解決され Integer≠Long で必ず落ちる
        assertEquals(2L, byScreen["bookshelf"]!!.frameCount)
        assertEquals(1L, byScreen["bookshelf"]!!.jankCount)
        assertEquals(1L, byScreen["reading"]!!.frameCount)
        assertEquals(0L, byScreen["reading"]!!.jankCount)
    }

    @Test
    fun `バケットは上限以下を含む（境界の取り違え防止）`() {
        val agg = JankAggregator()
        agg.record("s", ms(8.0), isJank = false)   // <=8   → 0番
        agg.record("s", ms(16.0), isJank = false)  // <=16  → 1番
        agg.record("s", ms(16.1), isJank = true)   // <=24  → 2番
        val buckets = agg.snapshot().single().bucketCounts
        assertEquals(1L, buckets[0])
        assertEquals(1L, buckets[1])
        assertEquals(1L, buckets[2])
    }

    @Test
    fun `上限を超えた外れ値は最終バケットへ落ちる`() {
        val agg = JankAggregator()
        agg.record("s", ms(1200.0), isJank = true)
        val stat = agg.snapshot().single()
        // 最終バケット＝上限なしの受け皿（既定バケット数9 なのでインデックス9）
        assertEquals(1L, stat.bucketCounts.last())
        assertEquals(10, stat.bucketCounts.size)
        assertEquals(1200.0, stat.worstMs, 0.01)
    }

    @Test
    fun `パーセンタイルは上界として読める値を返す`() {
        val agg = JankAggregator()
        repeat(90) { agg.record("s", ms(8.0), isJank = false) }
        repeat(10) { agg.record("s", ms(100.0), isJank = true) }
        val stat = agg.snapshot().single()

        // 90% は 8ms バケットで満ちる＝「P90 は 8ms 以内」
        assertEquals(8.0, stat.percentileMs(0.90), 0.01)
        // 99% は 100ms バケットまで行く＝裾が平均に埋もれず出る
        assertEquals(100.0, stat.percentileMs(0.99), 0.01)
        assertEquals(10.0, stat.jankPercent, 0.01)
    }

    @Test
    fun `最終バケットに落ちた分のパーセンタイルは実測の最悪値で答える`() {
        // 上限なしバケットの「上界」は無限大で読めないため、そこだけは実測値を返す約束。
        val agg = JankAggregator()
        repeat(99) { agg.record("s", ms(8.0), isJank = false) }
        agg.record("s", ms(900.0), isJank = true)
        val stat = agg.snapshot().single()
        assertEquals(900.0, stat.percentileMs(1.0), 0.01)
    }

    @Test
    fun `記録が無ければゼロで割らない`() {
        val stat = ScreenJank("s", 0, 0, 0.0, List(10) { 0L }, JankAggregator.DEFAULT_BUCKET_UPPER_BOUNDS_MS)
        assertEquals(0.0, stat.jankPercent, 0.0)
        assertEquals(0.0, stat.percentileMs(0.90), 0.0)
    }

    @Test
    fun `reset で書き出し済みの区間を捨てる`() {
        val agg = JankAggregator()
        agg.record("s", ms(8.0), isJank = false)
        agg.reset()
        assertTrue(agg.snapshot().isEmpty())
    }

    @Test
    fun `セッション要約はフレーム数の多い画面から並べる`() {
        // 数フレームしか描いていない画面の jank 率は分母が小さく誤読を招くので下へ送る＝
        // 実際に長く使われた画面が上に来ることを固定する。
        val agg = JankAggregator()
        repeat(3) { agg.record("rare", ms(8.0), isJank = false) }
        repeat(100) { agg.record("busy", ms(8.0), isJank = false) }
        val report = JankTracker.formatReport(
            agg.snapshot(),
            nowMillis = java.time.Instant.parse("2026-07-27T14:30:12Z").toEpochMilli(),
            zone = java.time.ZoneId.of("Asia/Tokyo"),
        )
        val lines = report.trim().lines()
        assertTrue(lines[0].contains("session end 2026-07-27T23:30:12+09:00"))
        assertTrue(lines[1].startsWith("busy"))
        assertTrue(lines[2].startsWith("rare"))
    }

    @Test
    fun `要約行は画面名と裾の指標を含む`() {
        val agg = JankAggregator()
        repeat(10) { agg.record("bookshelf", ms(40.0), isJank = true) }
        val line = agg.snapshot().single().formatLine()
        assertTrue(line.contains("bookshelf"))
        assertTrue(line.contains("frames=10"))
        assertTrue(line.contains("jank=100.0%"))
        assertTrue(line.contains("worst=40.0ms"))
    }
}
