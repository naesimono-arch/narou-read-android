package com.novelreader.ui.components

import com.novelreader.ui.theme.BackgroundSepia
import com.novelreader.ui.theme.SurfaceDark
import com.novelreader.ui.theme.SurfaceLight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * shioriAccentFor（書架⇔目録で共有するアクセント色の純関数）の明度分岐を固定する。
 *
 * なぜテーマ3値を厳密に固定するか: 正本 consistency-D の THEMES.accL（ライト52/セピア48/ダーク62）と
 * S=0.48 が「同じ本＝同じ色相・同じ明度」の整合を担保するため。特にセピアは surface==BackgroundSepia の
 * 一致でのみ判別する（SepiaColorScheme は LightColorScheme.copy で surface だけが固有値）＝ここが崩れると
 * セピア時にライト明度へ倒れて意匠が退行するため回帰で押さえる。
 */
class ShioriAccentTest {

    private val hue = 200

    @Test
    fun `ライト surface は L0_52`() {
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.52f), shioriAccentFor(hue, SurfaceLight))
    }

    @Test
    fun `セピア surface は L0_48`() {
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.48f), shioriAccentFor(hue, BackgroundSepia))
    }

    @Test
    fun `ダーク surface は L0_62`() {
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.62f), shioriAccentFor(hue, SurfaceDark))
    }
}
