package com.novelreader.ui.skins.m

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常駐 backdrop の視差コントローラ（無限スクロール空・2026-07-19 裁定①③）の純ロジック担保。
 *
 * 固定するもの（トーラス化＝クランプ撤廃）:
 *  1) 下スクロール（consumed.y<0）で視差オフセットが増える＝天の川粒帯が上へ滑る（従前の向きを踏襲）
 *  2) 上限で止まらず tileHeight で mod 正規化＝いくらスクロールしても [0,tile) を回り続ける（無限・Float も育たない）
 *  3) ちょうど1タイルぶんのスクロールで元の offset へ戻る＝トーラスの連続性（描画の 2 タイル記録と周期一致）
 *  4) 上スクロールも 0 に張り付かず mod で正の周期側へ回り込む
 *  5) reduce-motion では一切積まない（現状の視差無効と同義）
 *  6) setTileHeight は周期変更へ追従し既存 offset を再正規化する（実測タイル高への補正）
 */
class SkyParallaxControllerTest {

    private val tile = 1000f
    private val factor = 0.08f

    @Test
    fun `下スクロールで視差が増える`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = false)
        // consumed.y=-100（内容が上へ）を1回＝offset は +100*0.08=8。
        c.onScrollDelta(-100f)
        assertEquals(8f, c.offsetPx, 1e-3f)
    }

    @Test
    fun `上限で止まらずタイル高で mod 正規化され無限に流れる`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = false)
        // 総移動 1000×500×0.08 = 40000px ≫ tile。旧実装は 40 でクランプ＝そこで天の川が固まった。
        repeat(1000) { c.onScrollDelta(-500f) }
        // クランプせず [0,tile) へ正規化され続ける（＝上限で止まらない）。40000 mod 1000 = 0。
        assertTrue(c.offsetPx >= 0f && c.offsetPx < tile)
        assertEquals(0f, c.offsetPx, 1e-2f)
    }

    @Test
    fun `ちょうど1タイルスクロールで offset が元へ戻る＝トーラス連続性`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = false)
        c.onScrollDelta(-200f) // +16
        val start = c.offsetPx
        assertTrue(start > 0f)
        // tile/factor ぶん下スクロール＝ちょうど周期1周ぶん offset が進み、mod で同じ位置へ戻る。
        c.onScrollDelta(-(tile / factor))
        assertEquals(start, c.offsetPx, 1e-2f)
    }

    @Test
    fun `上スクロールも0に張り付かずmodで正の周期へ回り込む`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = false)
        // 上スクロール（内容が下へ）＝ 0-300*0.08 = -24 → mod tile → tile-24=976（負クランプでなく回り込み）。
        c.onScrollDelta(300f)
        assertEquals(tile - 24f, c.offsetPx, 1e-2f)
    }

    @Test
    fun `reduce-motion では積まない`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = true)
        c.onScrollDelta(-1000f)
        assertEquals(0f, c.offsetPx, 1e-3f)
    }

    @Test
    fun `setTileHeight は周期変更に追従し offset を再正規化する`() {
        val c = SkyParallaxController(0f, tile, factor, reduceMotion = false)
        repeat(5) { c.onScrollDelta(-500f) } // offset = 200
        assertEquals(200f, c.offsetPx, 1e-2f)
        c.setTileHeight(150f) // 実測タイル高で周期を更新 → 200 を [0,150) へ再mod = 50
        assertTrue(c.offsetPx >= 0f && c.offsetPx < 150f)
        assertEquals(50f, c.offsetPx, 1e-2f)
    }

    // ===== 可視窓カバレッジ回帰（差し戻し1＝最上部の途切れ／wrap で「バッと」出る不具合）=====
    // 是正実装は 2 タイル {0,1} を不動クリップ下で translationY 平行移動する。任意 offset∈[0,tile) で画面が覆われること、
    // かつ旧バグ（clip と translationY 同居→tile k=1 が切り落とされ実効タイル {0} へ退化）だと覆えないことを固定する。
    @Test
    fun `2タイル記録なら任意offsetで可視窓が隙間なく覆われる`() {
        // off=0 から周期直前まで細かく掃引＝どこにも空白（＝天の川の途切れ）が出ない。
        var off = 0f
        while (off < tile) {
            assertTrue("off=$off で2タイルが可視窓を覆えていない", torusWindowCovered(off, tile, listOf(0, 1)))
            off += tile / 512f
        }
    }

    @Test
    fun `1タイルへ退化した旧バグ相当ではoffset0超で可視窓が途切れる`() {
        // off=0 だけは1タイルでちょうど覆えるが、少しでも滑らせると下端に空白＝差し戻しの症状を純関数で再現。
        assertTrue(torusWindowCovered(0f, tile, listOf(0)))
        assertTrue("旧バグ相当が途切れずに見えてしまう", !torusWindowCovered(tile * 0.5f, tile, listOf(0)))
        assertTrue("周期直前で全面途切れ（wrapでバッと復帰）を再現できていない", !torusWindowCovered(tile * 0.99f, tile, listOf(0)))
    }
}
