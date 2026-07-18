package com.novelreader.ui.skins.m

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 流星の時間スケジューリング（B/C・2026-07-19 裁定）の統計的性質を純関数で担保。
 *
 * 固定するもの:
 *  1) 間隔＝指数分布 inter-arrival で MIN..MAX に収まり、平均は約60s帯（差し戻し③＝旧≒108sより貴重すぎを是正）
 *  2) 4パターンが全て出現し、二連（DOUBLE）が最稀・微流星（FAINT）が最頻＝1パターンへ固まらない・稀さの規律
 *  3) 重み境界で pick が重み順に割り当てる（決定的な純関数）
 *  4) 流星群（差し戻し④）はごく低確率（~2.5%）で分岐し、5〜9本を 3〜6秒へ密集・尾α上限0.52（+0.1緩和）
 *
 * これらは Compose 非依存の純関数ゆえ Robolectric 不要（決定性生成＝フィールド側と規律を分離している証左）。
 */
class MeteorSchedulingTest {

    @Test
    fun `間隔は指数分布で下限上限に収まり平均は約60s`() {
        val rnd = Random(42)
        val n = 40000
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        var sum = 0.0
        repeat(n) {
            val d = nextMeteorDelayMs(rnd.nextFloat())
            assertTrue("d=$d 範囲外", d in MeteorTuning.INTERVAL_MIN_MS..MeteorTuning.INTERVAL_MAX_MS)
            if (d < min) min = d
            if (d > max) max = d
            sum += d
        }
        val mean = sum / n
        // 差し戻し③: MIN20s+Exp(mean42s)・上限150s打ち切りで実測平均 ≒60s（非打ち切り理論値62sより裾切りで微減）。
        assertTrue("mean=$mean は50〜70s帯外", mean in 50_000.0..70_000.0)
        // 下限近傍（u≈1＝待ち短）が出ることを確認（指数分布の左裾）。
        assertEquals(MeteorTuning.INTERVAL_MIN_MS.toDouble(), min.toDouble(), 2_000.0)
    }

    @Test
    fun `流星群はごく低確率で分岐する`() {
        val rnd = Random(99)
        val n = 200000
        var showers = 0
        repeat(n) { if (isShowerSpawn(rnd.nextFloat())) showers++ }
        val rate = showers.toDouble() / n
        // 期待 ~2.5%（SHOWER_PROB）。統計ゆらぎを見込み 1.5〜3.5% 帯に収まること＝「ごく稀」の担保。
        assertTrue("群の発生率 $rate が想定帯(1.5〜3.5%)外", rate in 0.015..0.035)
    }

    @Test
    fun `流星群は5〜9本を3〜6秒へ密集させ尾αは0_52上限`() {
        val rnd = Random(123)
        // seed を変えつつ多数の群を組み、本数・掃過・尾α・phase・パターン混在の規律を全数検査。
        var sawMixedPattern = false
        repeat(500) {
            val ev = buildMeteorShower { rnd.nextFloat() }
            assertTrue("本数 ${ev.streaks.size} が5〜9外", ev.streaks.size in 5..9)
            assertTrue("掃過 ${ev.durationMs}ms が3〜6秒外", ev.durationMs in 3_000..6_000)
            val patterns = HashSet<Any>()
            for (s in ev.streaks) {
                assertTrue("尾α ${s.tailAlpha} が緩和上限0.52超", s.tailAlpha <= 0.52f + 1e-4f)
                assertTrue("phase ${s.phase} が[0,0.8)外", s.phase >= 0f && s.phase < 0.8f)
            }
            // パターン混在の確認は下の全群横断で（1群が単一パターンな回もあるため）。
            for (s in ev.streaks) patterns.add(s.len) // len はパターン別レンジ＝混在の代理指標
            if (patterns.size > 1) sawMixedPattern = true
        }
        assertTrue("群内でパターンが混在した形跡がない", sawMixedPattern)
    }

    @Test
    fun `4パターンが全て出現し二連が最稀・微流星が最頻`() {
        val rnd = Random(7)
        val counts = HashMap<MeteorPattern, Int>()
        repeat(60000) {
            val p = pickMeteorPattern(rnd.nextFloat())
            counts[p] = (counts[p] ?: 0) + 1
        }
        for (p in MeteorPattern.entries) {
            assertTrue("$p が一度も出ない", (counts[p] ?: 0) > 0)
        }
        assertEquals(MeteorPattern.DOUBLE, counts.minByOrNull { it.value }!!.key) // 二連＝ごく稀
        assertEquals(MeteorPattern.FAINT, counts.maxByOrNull { it.value }!!.key)  // 微流星＝最頻
    }

    @Test
    fun `pickMeteorPattern は重み境界で重み順に割り当てる`() {
        assertEquals(MeteorPattern.FAINT, pickMeteorPattern(0f))
        assertEquals(MeteorPattern.DOUBLE, pickMeteorPattern(0.999f))
    }
}
