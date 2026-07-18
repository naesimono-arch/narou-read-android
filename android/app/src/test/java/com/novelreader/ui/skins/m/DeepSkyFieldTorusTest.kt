package com.novelreader.ui.skins.m

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 天の川粒帯の縦トーラス化（無限スクロール空・2026-07-19 裁定①）の座標検証。
 *
 * 固定するもの:
 *  1) 帯粒・核の fy は全て トーラス座標 [0,1) に収まる（境界外の橋渡し粒も mod 1.0 で畳まれる）
 *  2) 境界近傍（fy≈0 と fy≈1）の両方に十分な帯粒が在る＝タイル境界で帯がぶつ切りにならない（橋渡し粒の存在）
 *
 * buildDeepSkyField は Compose Color を生成するため Robolectric 下で走らせる（既存 BookshelfSkyMTest と同じ）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepSkyFieldTorusTest {

    @Test
    fun `帯粒の fy は全てトーラス座標 0以上1未満 に収まる`() {
        val field = buildDeepSkyField()
        for (p in field.band) {
            assertTrue("band fy=${p.fy} が [0,1) 外", p.fy >= 0f && p.fy < 1f)
        }
        for (m in field.microSea) {
            assertTrue("micro fy=${m.fy} が [0,1) 外", m.fy >= 0f && m.fy < 1f)
        }
        for (s in field.scatter) {
            assertTrue("scatter fy=${s.fy} が [0,1) 外", s.fy >= 0f && s.fy < 1f)
        }
    }

    @Test
    fun `境界近傍のfy0付近とfy1付近の両方に帯粒が在る＝シーム橋渡し`() {
        val field = buildDeepSkyField()
        val nearTop = field.band.count { it.fy < 0.06f }
        val nearBottom = field.band.count { it.fy > 0.94f }
        // 境界の両側に相当数の帯粒があること＝橋渡し帯（BAND_BRIDGE 延長生成）が両タイルへ粒を供給している。
        assertTrue("fy≈0 近傍の帯粒 $nearTop 個は不足（橋渡し欠落）", nearTop > 50)
        assertTrue("fy≈1 近傍の帯粒 $nearBottom 個は不足（橋渡し欠落）", nearBottom > 50)
    }
}
