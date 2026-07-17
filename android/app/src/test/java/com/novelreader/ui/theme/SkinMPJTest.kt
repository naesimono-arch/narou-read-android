package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.skins.SkinJ
import com.novelreader.ui.theme.skins.SkinM
import com.novelreader.ui.theme.skins.SkinP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スキン M（星図）/ P（カートリッジ）/ J（ポータル）の変種契約・テーマクランプ・意味文字コントラスト回帰。
 *
 * SkinCTest と同水準の契約を新3スキンへ課す（ADR 0022 §2 の supportedThemes 実態に沿って）。
 * なぜ SkinCTest の assertMeaningPairsAA を流用せず別ヘルパーか: SkinC は本棚背景（colorScheme.background）と
 * 読書背景（reading.background）が全変種で同値だが、P は読書=温白スクリーン/本棚=プラ筐体、J は読書=3テーマ/
 * 本棚=固定ダーク森面と「家系で地色が分かれる」。ゆえに読書トークンは reading.background に、本棚トークン
 * （shelf.unreadLabel/infoText）は material(theme).background に対して各々 AA を判定する（契約を弱めず正確化）。
 */
class SkinMPJTest {

    private fun channel(c: Float): Double {
        val cs = c.toDouble()
        return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAA(label: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label が地色に対し AA 未達: ${"%.2f".format(ratio)}:1 (<4.5)", ratio >= 4.5)
    }

    /** 読書の意味文字は reading.background に、本棚の意味文字は material.background に対し AA 4.5:1 以上。 */
    private fun assertMeaningPairsAA(skinName: String, tokens: SkinTokens) {
        for (theme in tokens.supportedThemes) {
            val r = tokens.reading(theme)
            assertAA("$skinName/$theme text", r.text, r.background)
            assertAA("$skinName/$theme infoText", r.infoText, r.background)
            assertAA("$skinName/$theme ruby", r.ruby, r.background)
            val shelfBg = tokens.material(theme).background
            val shelf = tokens.shelf(theme)
            assertAA("$skinName/$theme unreadLabel", shelf.unreadLabel, shelfBg)
            assertAA("$skinName/$theme shelf.infoText", shelf.infoText, shelfBg)
        }
    }

    // ---- supportedThemes 契約（ADR 0022 §2 のモック実態準拠）----

    @Test
    fun `M は DARK のみ・P と J は DARK LIGHT SEPIA の3テーマ`() {
        assertEquals(listOf(ReadingTheme.DARK), SkinM.supportedThemes)
        // P は追補ドラフト承認で3テーマ化（ADR 0022 §2 追記）。既定=先頭 LIGHT。
        assertEquals(listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK), SkinP.supportedThemes)
        assertEquals(listOf(ReadingTheme.DARK, ReadingTheme.LIGHT, ReadingTheme.SEPIA), SkinJ.supportedThemes)
        // 先頭=既定変種（M=DARK・P=LIGHT・J=DARK）。
        assertEquals(ReadingTheme.DARK, SkinM.supportedThemes.first())
        assertEquals(ReadingTheme.LIGHT, SkinP.supportedThemes.first())
        assertEquals(ReadingTheme.DARK, SkinJ.supportedThemes.first())
    }

    // ---- 固定1変種の防御（M は全 theme 入力で同一値）----

    @Test
    fun `M は全 theme 入力に対し同一の星図値を返す（theme 非依存）`() {
        for (t in ReadingTheme.values()) {
            assertEquals(SkinM.reading(ReadingTheme.DARK), SkinM.reading(t))
            assertTrue(SkinM.material(ReadingTheme.DARK) === SkinM.material(t))
            assertEquals(SkinM.shelf(ReadingTheme.DARK), SkinM.shelf(t))
            assertEquals(SkinM.shiori(ReadingTheme.DARK), SkinM.shiori(t))
        }
    }

    // ---- P/J は読書のみ変種・material/shelf/shiori は固定筐体面（P=プラ筐体・J=ダーク森面・ADR 0022 §2）----

    @Test
    fun `P の material shelf shiori は theme 非依存の固定筐体面・reading は3テーマで別物`() {
        for (t in ReadingTheme.values()) {
            // chrome（筐体・緑LCD・HUD/コンソール）はテーマ不変＝どの theme 入力でも同一（既定 LIGHT 値）。
            assertTrue(SkinP.material(ReadingTheme.LIGHT) === SkinP.material(t))
            assertEquals(SkinP.shelf(ReadingTheme.LIGHT), SkinP.shelf(t))
            assertEquals(SkinP.shiori(ReadingTheme.LIGHT), SkinP.shiori(t))
        }
        // 読書面（--screen/--rd-*）は3変種が互いに異なる背景を持つ（変種が実際に作用している証拠）。
        assertNotEquals(SkinP.reading(ReadingTheme.LIGHT).background, SkinP.reading(ReadingTheme.SEPIA).background)
        assertNotEquals(SkinP.reading(ReadingTheme.SEPIA).background, SkinP.reading(ReadingTheme.DARK).background)
        // 反面、chrome スロット（divider=--line・accent=--lcd）は3テーマとも同値（テーマ不変の署名色）。
        assertEquals(SkinP.reading(ReadingTheme.LIGHT).divider, SkinP.reading(ReadingTheme.DARK).divider)
        assertEquals(SkinP.reading(ReadingTheme.LIGHT).accent, SkinP.reading(ReadingTheme.DARK).accent)
    }

    @Test
    fun `J の material shelf shiori は theme 非依存の固定値・reading は3テーマで別物`() {
        for (t in ReadingTheme.values()) {
            assertTrue(SkinJ.material(ReadingTheme.DARK) === SkinJ.material(t))
            assertEquals(SkinJ.shelf(ReadingTheme.DARK), SkinJ.shelf(t))
            assertEquals(SkinJ.shiori(ReadingTheme.DARK), SkinJ.shiori(t))
        }
        // 読書は3変種が互いに異なる背景を持つ（変種が実際に作用している証拠）。
        assertNotEquals(SkinJ.reading(ReadingTheme.DARK).background, SkinJ.reading(ReadingTheme.LIGHT).background)
        assertNotEquals(SkinJ.reading(ReadingTheme.LIGHT).background, SkinJ.reading(ReadingTheme.SEPIA).background)
    }

    // ---- テーマクランプの純関数 ----

    @Test
    fun `clampThemeToSkin は M の非対応 theme を first へ丸め・P J は全対応`() {
        // M=[DARK]: 非対応は first（DARK）へ。
        assertEquals(ReadingTheme.DARK, clampThemeToSkin(ReadingTheme.LIGHT, SkinM))
        assertEquals(ReadingTheme.DARK, clampThemeToSkin(ReadingTheme.SEPIA, SkinM))
        // P=3変種・J=3変種: すべて自分自身（丸めなし）。
        for (t in ReadingTheme.values()) {
            assertEquals(t, clampThemeToSkin(t, SkinP))
            assertEquals(t, clampThemeToSkin(t, SkinJ))
        }
    }

    // ---- 意味文字コントラスト回帰（text/infoText/ruby × 読書地・unreadLabel/infoText × 本棚地）----

    @Test
    fun `M の意味文字ペアは地色に対し AA 4_5対1 以上`() = assertMeaningPairsAA("SkinM", SkinM)

    @Test
    fun `P の意味文字ペアは地色に対し AA 4_5対1 以上`() = assertMeaningPairsAA("SkinP", SkinP)

    @Test
    fun `J の意味文字ペアは地色に対し AA 4_5対1 以上`() = assertMeaningPairsAA("SkinJ", SkinJ)
}
