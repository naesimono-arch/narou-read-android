package com.novelreader.ui.theme

import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.skins.SkinC
import com.novelreader.ui.theme.skins.SkinD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スキンC「夜行」の固定1変種契約・テーマクランプの純関数・意味文字コントラスト回帰（D 3変種＋C）の検証。
 *
 * なぜ Robolectric 不要か: SkinTokens は darkColorScheme()/ReadingColors 等の純 Compose データを返すだけで
 * Android ランタイムに触れない。コントラスト比も Color の sRGB 成分から WCAG 相対輝度を計算する純ロジック。
 */
class SkinCTest {

    // ---- WCAG コントラスト比ヘルパー（テスト内実装＝汎用）----
    // sRGB 成分→相対輝度→コントラスト比 (L1+0.05)/(L2+0.05)。閾値 4.5:1 は通常文字の AA。
    private fun channel(c: Float): Double {
        val cs = c.toDouble()
        return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    // ---- (a) 固定1変種契約 ----

    @Test
    fun `SkinC は DARK のみを supportedThemes に持つ`() {
        assertEquals(listOf(ReadingTheme.DARK), SkinC.supportedThemes)
    }

    @Test
    fun `SkinC は全 theme 入力に対し同一の夜行値を返す（theme 非依存）`() {
        // 防御的設計の担保: supportedThemes 外の LIGHT/SEPIA が渡っても DARK と同値＝別スキン色が漏れない。
        for (t in ReadingTheme.values()) {
            assertEquals(SkinC.reading(ReadingTheme.DARK), SkinC.reading(t))
            // ColorScheme/ShelfColors/ShioriColors は単一 val を返す＝参照同一で同値。
            assertTrue(SkinC.material(ReadingTheme.DARK) === SkinC.material(t))
            assertEquals(SkinC.shelf(ReadingTheme.DARK), SkinC.shelf(t))
            assertEquals(SkinC.shiori(ReadingTheme.DARK), SkinC.shiori(t))
        }
    }

    // ---- (b) テーマクランプの純関数（UI テスト不要＝トップレベル関数を直接呼ぶ）----

    @Test
    fun `clampThemeToSkin は supportedThemes 外を first へ丸め・内はそのまま`() {
        // C=[DARK]: 非対応の LIGHT/SEPIA は DARK へクランプ、DARK は不変。
        assertEquals(ReadingTheme.DARK, clampThemeToSkin(ReadingTheme.LIGHT, SkinC))
        assertEquals(ReadingTheme.DARK, clampThemeToSkin(ReadingTheme.SEPIA, SkinC))
        assertEquals(ReadingTheme.DARK, clampThemeToSkin(ReadingTheme.DARK, SkinC))
        // D=3変種: すべて自分自身（丸め無し）。
        for (t in ReadingTheme.values()) {
            assertEquals(t, clampThemeToSkin(t, SkinD))
        }
    }

    // ---- (c) 意味文字コントラスト回帰（text/infoText/unreadLabel/ruby × 地色）----

    private fun assertMeaningPairsAA(skinName: String, tokens: SkinTokens) {
        for (theme in tokens.supportedThemes) {
            val r = tokens.reading(theme)
            val bg = r.background // 地色＝素地（本棚背景 colorScheme.background も全変種で reading.background と同値）
            val shelf = tokens.shelf(theme)
            val pairs = listOf(
                "text" to r.text,
                "infoText" to r.infoText,
                "ruby" to r.ruby,
                "unreadLabel" to shelf.unreadLabel,
            )
            for ((label, fg) in pairs) {
                val ratio = contrastRatio(fg, bg)
                assertTrue(
                    "$skinName/$theme $label が地色に対し AA 未達: ${"%.2f".format(ratio)}:1 (<4.5)",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `意味文字ペアは地色に対し AA 4_5対1 以上（D 3変種）`() {
        assertMeaningPairsAA("SkinD", SkinD)
    }

    @Test
    fun `意味文字ペアは地色に対し AA 4_5対1 以上（C 夜行）`() {
        assertMeaningPairsAA("SkinC", SkinC)
    }
}
