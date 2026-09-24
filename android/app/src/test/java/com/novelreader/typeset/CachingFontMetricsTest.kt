package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * advance キャッシュ（改善 D）の当たり外れ・キーの独立性・上限と破棄・スレッド直列化の固定。
 *
 * 版面がキャッシュ有無で一致することは `CachedAdvanceLayoutIdentityTest` が組版器越しに担保する
 * （こちらは覆い単体の挙動＝当たり外れが見える唯一の場所）。
 */
class CachingFontMetricsTest {

    private val source = VariableFontMetrics()

    private val metrics = CachingFontMetrics(source)

    @Test
    fun `同じキーは2回目以降 実測を叩かない`() {
        val first = metrics.verticalAdvance("あ", CharClass.UPRIGHT, 16f)
        val second = metrics.verticalAdvance("あ", CharClass.UPRIGHT, 16f)
        val third = metrics.verticalAdvance("あ", CharClass.UPRIGHT, 16f)

        assertEquals("同じキーは同じ値", first, second, 0f)
        assertEquals("同じキーは同じ値", first, third, 0f)
        assertEquals("実測は1回だけ", 1, source.verticalCalls)
        assertEquals(CachingFontMetrics.Stats(hits = 2, misses = 1, entries = 1), metrics.stats())
    }

    @Test
    fun `キーの3成分はどれが違っても別エントリになる`() {
        // 同じ "3" でも向きで縦送りが割れる（UPRIGHT＝em マス / ROTATE＝回転後の実測幅）。
        // ここを1エントリにまとめると「3日」の字面接触（golden 監査 2026-08-06 G-3）が再来する。
        val upright = metrics.verticalAdvance("3", CharClass.UPRIGHT, 16f)
        val rotate = metrics.verticalAdvance("3", CharClass.ROTATE, 16f)
        // 同じ字・同じ向きでもサイズが違えば別（本文とルビは同じ組版の中で別サイズを引く）。
        val small = metrics.verticalAdvance("3", CharClass.UPRIGHT, 8f)
        // 字が違えば当然別。
        val other = metrics.verticalAdvance("日", CharClass.UPRIGHT, 16f)

        assertEquals("向き違いは実測どおり別値", VariableFontMetrics.advanceOf("3", CharClass.ROTATE, 16f), rotate, 0f)
        assertTrue("向き違いが同値に潰れていない", upright != rotate)
        assertTrue("サイズ違いが同値に潰れていない", upright != small)
        assertTrue("字違いが同値に潰れていない", upright != other)
        assertEquals("4キーとも実測を1回ずつ", 4, source.verticalCalls)
        assertEquals(CachingFontMetrics.Stats(hits = 0, misses = 4, entries = 4), metrics.stats())
    }

    @Test
    fun `返す値は実測と完全一致する`() {
        // 近似も丸めもしない（D の裁定＝定数化は不採用・キャッシュは実測値の記憶に徹する）。
        for (text in listOf("あ", "漢", "、", "ー", "…………", "12", "A")) {
            for (charClass in CharClass.values()) {
                for (size in listOf(8f, 13.5f, 16f, 24f)) {
                    val expected = VariableFontMetrics.advanceOf(text, charClass, size)
                    // 1回目（外れ）と2回目（当たり）の両方が実測と一致すること。
                    assertEquals(expected, metrics.verticalAdvance(text, charClass, size), 0f)
                    assertEquals(expected, metrics.verticalAdvance(text, charClass, size), 0f)
                }
            }
        }
    }

    @Test
    fun `上限を超えたら最後に使われたのが最も古いものから捨てる`() {
        val tiny = CachingFontMetrics(source, maxEntries = 3)
        tiny.verticalAdvance("一", CharClass.UPRIGHT, 16f)
        tiny.verticalAdvance("二", CharClass.UPRIGHT, 16f)
        tiny.verticalAdvance("三", CharClass.UPRIGHT, 16f)
        // 「一」を触って最新にする＝accessOrder が効いていれば次の破棄対象は「二」になる。
        tiny.verticalAdvance("一", CharClass.UPRIGHT, 16f)
        tiny.verticalAdvance("四", CharClass.UPRIGHT, 16f)

        assertEquals("上限を超えて抱え込まない", 3, tiny.stats().entries)
        val callsBefore = source.verticalCalls
        tiny.verticalAdvance("一", CharClass.UPRIGHT, 16f)
        assertEquals("触った「一」は残っている", callsBefore, source.verticalCalls)
        tiny.verticalAdvance("二", CharClass.UPRIGHT, 16f)
        assertEquals("最も古い「二」は捨てられている", callsBefore + 1, source.verticalCalls)
    }

    @Test
    fun `横幅は覚えず毎回実測へ通す`() {
        // キーが任意長の文字列＝母数の上界が置けないため意図的に素通し（KDoc の理由）。
        metrics.horizontalAdvance("あいうえお", 16f)
        metrics.horizontalAdvance("あいうえお", 16f)
        assertEquals(2, source.horizontalCalls)
        assertEquals("縦送りの表は汚さない", 0, metrics.stats().entries)
    }

    @Test
    fun `上限0は構築時に弾く`() {
        try {
            CachingFontMetrics(source, maxEntries = 0)
            fail("maxEntries=0（毎回ミス）は覆う意味が無く、静かに性能だけ落ちるので構築時に落とす")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxEntries"))
        }
    }

    @Test
    fun `複数スレッドから同時に引いても値が壊れず実測は1キー1回に収まる`() {
        // 背景組版（Dispatchers.Default）と composition 段の同期組版が同じ寸法源を共有する構造の再現。
        val keys = (0 until 50).map { Triple("字$it", CharClass.values()[it % CharClass.values().size], 16f) }
        val threads = 4
        val start = CountDownLatch(1)
        // ワーカー内の assert は本体スレッドの JUnit に届かない（失敗しても緑になる）＝
        // 食い違いを持ち帰って本体で落とす。
        val mismatches = java.util.Collections.synchronizedList(ArrayList<String>())
        val workers = (0 until threads).map {
            Thread {
                start.await()
                repeat(200) {
                    for ((text, charClass, size) in keys) {
                        val expected = VariableFontMetrics.advanceOf(text, charClass, size)
                        val actual = metrics.verticalAdvance(text, charClass, size)
                        if (actual != expected) mismatches.add("$text/$charClass/$size: $actual != $expected")
                    }
                }
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join() }

        assertEquals("並行でも実測値と食い違わない", emptyList<String>(), mismatches.toList())
        val stats = metrics.stats()
        assertEquals("キーの数だけ実測（錠で直列化＝取りこぼしも二重計測も無い）", keys.size.toLong(), stats.misses)
        assertEquals("実測回数も一致", keys.size, source.verticalCalls)
        assertEquals("当たり外れの総和が呼び出し総数", (threads * 200 * keys.size).toLong(), stats.hits + stats.misses)
    }
}
