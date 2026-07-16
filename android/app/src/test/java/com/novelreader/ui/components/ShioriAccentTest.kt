package com.novelreader.ui.components

import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.skins.SkinD
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * shioriAccentFor（書架⇔目録で共有するアクセント色の純関数）と、その明度を供給する SkinD.shiori を固定する。
 *
 * なぜテーマ3値を厳密に固定するか: 正本 consistency-D の THEMES.accL（ライト52/セピア48/ダーク62）と
 * S=0.48 が「同じ本＝同じ色相・同じ明度」の整合を担保するため。
 *
 * P1 スキン骨格導入で明度の由来が変わった: 旧 shioriAccentFor は surface の luminance／BackgroundSepia
 * 一致から明度を推定していたが、これは D 前提の暗黙結合＝スキン導入で壊れるため根絶した。明度は
 * SkinD.shiori(theme).accentLightness が正本になり、shioriAccentFor はそれを受け取って色へ変換するだけ。
 * よって回帰は 2 段で押さえる: (1) SkinD.shiori が変種ごとに正しい L を返す、(2) shioriAccentFor が
 * その L を S=0.48 の HSL へ素通しする。ここが崩れると栞の明度が退行する。
 */
class ShioriAccentTest {

    private val hue = 200

    @Test
    fun `SkinD_shiori の accentLightness はテーマ3値`() {
        assertEquals(0.52f, SkinD.shiori(ReadingTheme.LIGHT).accentLightness)
        assertEquals(0.48f, SkinD.shiori(ReadingTheme.SEPIA).accentLightness)
        assertEquals(0.62f, SkinD.shiori(ReadingTheme.DARK).accentLightness)
    }

    @Test
    fun `ライト L0_52`() {
        val l = SkinD.shiori(ReadingTheme.LIGHT).accentLightness
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.52f), shioriAccentFor(hue, l))
    }

    @Test
    fun `セピア L0_48`() {
        val l = SkinD.shiori(ReadingTheme.SEPIA).accentLightness
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.48f), shioriAccentFor(hue, l))
    }

    @Test
    fun `ダーク L0_62`() {
        val l = SkinD.shiori(ReadingTheme.DARK).accentLightness
        assertEquals(hslToColor(hue.toFloat(), 0.48f, 0.62f), shioriAccentFor(hue, l))
    }
}
