package com.novelreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 栞アニメ高負荷モードの振り付けデータ（ShioriHighLoadChoreo）の構造テスト。
 *
 * 何を固定するか＝モック（bookshelf-shiori-highload-K.html）が機械照合で担保する「9種の非画一性」の
 * Kotlin 側鏡像（/shiori-anim 鉄則2）。初稿が「共通語彙の割当」で画一的と差し戻された経緯（2026-08-05）から、
 * 実装改修で9種が同じ見え方に収斂する回帰をデータ層で止める:
 *  ②動かすプロパティの組合せが9通りとも異なる（署名プロパティは各1種だけ）
 *  ③周期9種すべて相異（tip3 のみ左右2周期）
 *  ④イージングを tip 間で使い回さない
 * 加えて〈静止値=静的意匠と同値〉〈線追従の恒久規則（全 tip 適用・同周期・遅相）〉〈キー値の写経〉を固定する。
 * ①keyframes 非共有は Kotlin ではトラック実体の非共有として同時に担保される（同一インスタンスの再掲のみ許容）。
 */
class ShioriHighLoadChoreoTest {

    private val choreos = SHIORI_HIGHLOAD_CHOREOS

    @Test
    fun `一覧は tip 0〜8 の9件で順序どおり`() {
        assertEquals(SHIORI_HIGHLOAD_TIP_COUNT, choreos.size)
        choreos.forEachIndexed { i, c -> assertEquals(i, c.tip) }
        assertEquals(
            listOf("魚尾", "一粒", "結び玉", "二又房", "三又房", "総角", "蝶結び", "玉と尾", "数珠"),
            choreos.map { it.name },
        )
    }

    @Test
    fun `照合② プロパティ集合はモックの内訳どおりで9通り相異`() {
        // モック style 冒頭の②内訳（0:rotate+translateY+stroke-width／…／8:translateY+scale）の写し。
        val expected = listOf(
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_Y, ShioriChoreoProp.STROKE_WIDTH),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.SCALE),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_Y, ShioriChoreoProp.SCALE),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.SKEW_X),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_X),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_Y, ShioriChoreoProp.FILTER),
            setOf(ShioriChoreoProp.SCALE, ShioriChoreoProp.OPACITY),
            setOf(ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_Y, ShioriChoreoProp.DASH_OFFSET),
            setOf(ShioriChoreoProp.TRANSLATE_Y, ShioriChoreoProp.SCALE),
        )
        assertEquals(expected, choreos.map { it.props })
        assertEquals("プロパティ集合が9通り相異であること", 9, choreos.map { it.props }.toSet().size)
    }

    @Test
    fun `照合② 署名プロパティは各1種だけが持つ`() {
        val signatures = mapOf(
            ShioriChoreoProp.STROKE_WIDTH to 0,
            ShioriChoreoProp.SKEW_X to 3,
            ShioriChoreoProp.TRANSLATE_X to 4,
            ShioriChoreoProp.FILTER to 5,
            ShioriChoreoProp.OPACITY to 6,
            ShioriChoreoProp.DASH_OFFSET to 7,
        )
        for ((prop, ownerTip) in signatures) {
            val owners = choreos.filter { prop in it.props }.map { it.tip }
            assertEquals("署名 $prop は tip $ownerTip 専有", listOf(ownerTip), owners)
        }
    }

    @Test
    fun `照合③ 周期はモック値どおりで tip 間の重複ゼロ`() {
        val expected = listOf(
            setOf(7.4f), setOf(9.5f), setOf(8.8f), setOf(3.9f, 5.3f),
            setOf(6.8f), setOf(11f), setOf(12f), setOf(10.5f), setOf(8.4f),
        )
        assertEquals(expected, choreos.map { it.periods })
        // ペア全比較で重複ゼロ（tip3 の2周期も他 tip と交差しない）。
        for (i in choreos.indices) for (j in i + 1 until choreos.size) {
            assertTrue(
                "周期の重複: tip $i と tip $j",
                (choreos[i].periods intersect choreos[j].periods).isEmpty(),
            )
        }
    }

    @Test
    fun `照合④ イージング（cubic-bezier）を tip 間で使い回さない`() {
        for (i in choreos.indices) for (j in i + 1 until choreos.size) {
            assertTrue(
                "イージングの使い回し: tip $i と tip $j",
                (choreos[i].beziers intersect choreos[j].beziers).isEmpty(),
            )
        }
    }

    @Test
    fun `恒久規則 線追従は全 tip に在り・同周期・遅相つき・イージングは主動作と連成`() {
        for (c in choreos) {
            assertTrue("tip ${c.tip}: 線追従層が無い", c.follow.isNotEmpty())
            for (bf in c.follow) {
                // 同周期（tip3 は支配的な左織り 3.9s への近似追従＝tip 周期集合の一員であること）。
                assertTrue("tip ${c.tip}: bf 周期が装飾と不一致", bf.periodSec in c.periods)
                // 微遅相（BF_LAG）＝紐が「わずかに遅れて」応える物理感。0 だと連成でなく同体に見える。
                assertTrue("tip ${c.tip}: bf 遅相ゼロ", bf.delaySec > 0f)
                // イージングは各 tip の主動作と同値（モック注記＝連成して見せるため・bf は照合対象外）。
                assertTrue("tip ${c.tip}: bf イージングが装飾と無関係", bf.bezier in c.beziers)
                // 線追従の変形は傾ぎ（rotate）か縦張力（scaleY）のみ＝描画側 when の契約。
                assertTrue(
                    "tip ${c.tip}: bf のプロパティが契約外",
                    bf.prop == ShioriChoreoProp.ROTATE || bf.prop == ShioriChoreoProp.SCALE,
                )
            }
        }
    }

    @Test
    fun `静止値は静的意匠と同値（位相0で rest 値・rest 値はプロパティの恒等値）`() {
        for (c in choreos) {
            for (tr in c.tracks + c.follow) {
                val restA = tr.keys.first().a
                val restB = tr.keys.first().b
                // 位相 0（= delay ちょうど）で最初のキー値＝長い静止の入りが rest から始まる。
                assertEquals("tip ${c.tip} ${tr.prop}: 位相0のa", restA, tr.aAt(tr.delaySec), 1e-5f)
                assertEquals("tip ${c.tip} ${tr.prop}: 位相0のb", restB, tr.bAt(tr.delaySec), 1e-5f)
                // rest 値はプロパティの恒等値（＝静止時は identity 変形で静的描画と一致する根拠）。
                val identity = when (tr.prop) {
                    ShioriChoreoProp.ROTATE, ShioriChoreoProp.TRANSLATE_X, ShioriChoreoProp.TRANSLATE_Y,
                    ShioriChoreoProp.SKEW_X, ShioriChoreoProp.DASH_OFFSET -> 0f
                    ShioriChoreoProp.SCALE, ShioriChoreoProp.OPACITY, ShioriChoreoProp.FILTER -> 1f
                    // t0-flex の rest は SHIORI_TIPS の魚尾の線幅と同値（stroke-width だけ恒等値が形状固有）。
                    ShioriChoreoProp.STROKE_WIDTH -> 2.6f
                }
                assertEquals("tip ${c.tip} ${tr.prop}: rest=恒等値", identity, restA, 1e-5f)
                if (tr.prop != ShioriChoreoProp.SCALE && tr.prop != ShioriChoreoProp.FILTER) {
                    assertEquals(identity, restB, 1e-5f)
                }
            }
        }
    }

    @Test
    fun `キー値の写経スポット（モック keyframes の頂点値）`() {
        // t0-L 69%＝rotate(15deg)（水中の一閃の打ち出し）。
        assertEquals(15f, ShioriT0.ROT_L.aAt(0.69f * 7.4f), 0.5f)
        // t5-s 38%＝係数 4.5（--k 比例の扇の開き）。
        assertEquals(4.5f, ShioriT5.STRAND.aAt(0.38f * 11f), 0.2f)
        // t7-ink 40%＝stroke-dashoffset 3.2（払い際の墨切れ）。delay .12s を足して位相を合わせる。
        assertEquals(3.2f, ShioriT7.INK.aAt(0.12f + 0.40f * 10.5f), 0.2f)
        // t1 6%＝rotate(11deg)（振り子の初振れ）。
        assertEquals(11f, ShioriT1.ROT.aAt(0.06f * 9.5f), 0.5f)
    }

    @Test
    fun `合成可否ゲート shioriHighLoadActive の全組合せ`() {
        // 動く条件: ON×生成色×tip0..8×非低減。
        assertTrue(shioriHighLoadActive(enabled = true, hasAccentOverride = false, tipIndex = 0, reduceMotion = false))
        assertTrue(shioriHighLoadActive(enabled = true, hasAccentOverride = false, tipIndex = 8, reduceMotion = false))
        // トグル OFF（既定・release 常時）＝完全静止。
        assertFalse(shioriHighLoadActive(enabled = false, hasAccentOverride = false, tipIndex = 0, reduceMotion = false))
        // Web未取込の青磁署名（accentOverride）は対象外＝「沈めた署名」を保つ。
        assertFalse(shioriHighLoadActive(enabled = true, hasAccentOverride = true, tipIndex = 0, reduceMotion = false))
        // tip 9〜173 は裁定スコープ外＝静止のまま。
        assertFalse(shioriHighLoadActive(enabled = true, hasAccentOverride = false, tipIndex = 9, reduceMotion = false))
        assertFalse(shioriHighLoadActive(enabled = true, hasAccentOverride = false, tipIndex = 173, reduceMotion = false))
        // reduce-motion は完全静止（モック @media (prefers-reduced-motion) の等価）。
        assertFalse(shioriHighLoadActive(enabled = true, hasAccentOverride = false, tipIndex = 0, reduceMotion = true))
    }

    @Test
    fun `カード位相は title 決定論で 0〜16s に収まり同題は同位相`() {
        val a1 = shioriHighLoadPhaseSec("同じ題名の本")
        val a2 = shioriHighLoadPhaseSec("同じ題名の本")
        val b = shioriHighLoadPhaseSec("別の題名の本")
        assertEquals(a1, a2, 0f)
        assertTrue(a1 in 0f..16f && b in 0f..16f)
        // 位相分散の目的（棚全体が同期して踊らない）＝異なる題で異なる位相になる代表例を固定。
        assertFalse(a1 == b)
    }
}
