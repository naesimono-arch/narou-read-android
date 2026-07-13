package com.novelreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 栞書影の決定論生成が /design 正本（JS: bookshelf-shiori-final-D.html の hashStr/mulberry32）と
 * 一致することを固定するゴールデンテスト。
 *
 * なぜゴールデン値を JS から算出して埋め込むか: Kotlin 側の FNV-1a/mulberry32 移植が正本と
 * ビット単位で同一系列であることを保証するため（＝実機とモックで同じ本が同じ絵になる）。
 * 期待値は正本 JS を Node で実行して得た（hue/xFrac/lenFrac/tipIndex）。
 */
class ShioriGeneratorTest {

    // 現行の先端総数（正本 TIPS 配列と一致）。ゴールデン値はこの数で算出している。
    // 2026-07-13: 31→174 に増補（既存31＋増補143＝陰陽/武具/家紋/鳥獣/花/書斎系）。tipCount 依存は
    // tipIndex のみ（hue/xFrac/lenFrac は棒の乱数系列で先に引かれ総数非依存）＝下の tipIndex 期待値のみ更新。
    private val tipCount = 174

    @Test
    fun `hash は JS hashStr と一致する`() {
        assertEquals(-2103210536, shioriHash("テスト"))
        assertEquals(1004382250, shioriHash("テスト|B"))
        assertEquals(-456985773, shioriHash("星降る夜のパン屋と魔法使い"))
        assertEquals(1512983421, shioriHash("黒の魔王と契約した俺、気づけば最強の従者に"))
    }

    @Test
    fun `params は JS 正本のゴールデン値と一致する`() {
        val a = shioriParams("テスト", tipCount)
        assertEquals(20, a.hue)
        assertEquals(154, a.tipIndex)
        assertEquals(0.29818349f, a.xFrac, 1e-4f)
        assertEquals(0.34783724f, a.lenFrac, 1e-4f)

        val b = shioriParams("星降る夜のパン屋と魔法使い", tipCount)
        assertEquals(260, b.hue)
        assertEquals(136, b.tipIndex)
        assertEquals(0.19621739f, b.xFrac, 1e-4f)
        assertEquals(0.32776147f, b.lenFrac, 1e-4f)

        val c = shioriParams("黒の魔王と契約した俺、気づけば最強の従者に", tipCount)
        assertEquals(330, c.hue)
        assertEquals(23, c.tipIndex)
        assertEquals(0.28875232f, c.xFrac, 1e-4f)
        assertEquals(0.34312687f, c.lenFrac, 1e-4f)
    }

    @Test
    fun `同じ title は常に同じ params（決定論）`() {
        val t = "追放された万能薬師、辺境でスローライフを始める"
        assertEquals(shioriParams(t, tipCount), shioriParams(t, tipCount))
    }

    @Test
    fun `shioriHue は shioriParams の hue と一致し PALETTE の要素`() {
        // 整合の生命線: 目録の色帯・共有アクセントが引く shioriHue と、書架の栞が引く
        // shioriParams(...).hue は必ず同一系列＝同じ本が書架/目録で同じ色相になる。
        val titles = listOf(
            "テスト", "星降る夜のパン屋と魔法使い", "黒の魔王と契約した俺、気づけば最強の従者に",
            "追放された万能薬師、辺境でスローライフを始める", "白河すずら", "あ",
        )
        for (t in titles) {
            assertEquals("hue mismatch: $t", shioriParams(t, tipCount).hue, shioriHue(t))
            assertTrue("hue in palette: ${shioriHue(t)}", SHIORI_PALETTE.contains(shioriHue(t)))
        }
    }

    @Test
    fun `hue は PALETTE の要素・xLenFrac と tipIndex は範囲内`() {
        val titles = listOf(
            "テスト", "あ", "星降る夜のパン屋と魔法使い",
            "辺境伯家の次男は今日も書庫にこもる", "白猫亭の看板娘は元宮廷魔術師",
        )
        for (t in titles) {
            val p = shioriParams(t, tipCount)
            assertTrue("hue in palette: ${p.hue}", SHIORI_PALETTE.contains(p.hue))
            assertTrue("xFrac range: ${p.xFrac}", p.xFrac in 0.14f..0.36f)
            assertTrue("lenFrac range: ${p.lenFrac}", p.lenFrac in 0.30f..0.60f)
            assertTrue("tipIndex range: ${p.tipIndex}", p.tipIndex in 0 until tipCount)
        }
    }
}
