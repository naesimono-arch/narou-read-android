package com.novelreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ダイアログ面（`surfaceContainerHigh`）に載る文字の AA 回帰（全スキン × 全対応テーマ）。
 *
 * なぜ Python 検査（`tools/check_design_tokens.py`）があるのに Kotlin 側でも測るか:
 * 前者は Kotlin を **正規表現でパース**して値を再構成する（`withSkinContainerTiers()` の
 * `copy(...)` も字面から写している）ため、パース前提が崩れると静かに測れなくなる。ここは
 * **実オブジェクトの ColorScheme をそのまま読む**＝パース経路を通らない二重化で、既定ゲート
 * （testDebugUnitTest）にも乗る。SkinCTest の意味文字コントラスト回帰と同じ流儀。
 *
 * なぜ Robolectric 不要か: SkinTokens は純 Compose データを返すだけで Android ランタイムに触れない。
 */
class DialogContrastTest {

    // WCAG 2.x 相対輝度→コントラスト比（SkinCTest と同式。テスト内実装＝本番へ持ち込まない汎用計算）。
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

    /** 全スキン × 対応テーマの (ラベル, ダイアログ面, ShelfColors, ColorScheme) を回す。 */
    private fun forEachDialogSurface(body: (String, Color, ShelfColors, ColorScheme) -> Unit) {
        for (skin in Skin.values()) {
            val tokens = skin.tokens
            for (theme in tokens.supportedThemes) {
                val scheme = tokens.material(theme)
                body("${skin.name}/$theme", scheme.surfaceContainerHigh, tokens.shelf(theme), scheme)
            }
        }
    }

    @Test
    fun `ダイアログ面は各スキンの素地と同一（withSkinContainerTiers の契約）`() {
        // NovelReaderAlertDialog の KDoc とコントラスト実測値は「ダイアログ面＝surface」を前提に書かれている。
        // ここが崩れると（M3 baseline の紫へ戻る等）本文色の裏取りが丸ごと無効になるため型ではなく値で縛る。
        forEachDialogSurface { label, dialogSurface, _, scheme ->
            assertEquals("$label: surfaceContainerHigh が surface と乖離", scheme.surface, dialogSurface)
        }
    }

    @Test
    fun `ダイアログ本文（NovelReaderAlertDialog の既定色）は全スキンで AA 4_5対1 を満たす`() {
        // 既定色の出所は LocalShelfColors.current.infoText ＝ SkinTokens.shelf(theme).infoText。
        // M3 既定の onSurfaceVariant（装飾専用スロット・ADR 0014-D）では D LIGHT 3.79:1 で未達だった件の回帰止め。
        forEachDialogSurface { label, dialogSurface, shelf, _ ->
            val ratio = contrastRatio(shelf.infoText, dialogSurface)
            assertTrue("$label: ダイアログ本文 infoText が %.2f:1 で AA 未達".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun `ダイアログ題字（M3 既定の onSurface）は全スキンで AA 4_5対1 を満たす`() {
        // 題字は M3 既定（DialogTokens.HeadlineColor = OnSurface）のまま採用した＝その判断の裏取り。
        forEachDialogSurface { label, dialogSurface, _, scheme ->
            val ratio = contrastRatio(scheme.onSurface, dialogSurface)
            assertTrue("$label: ダイアログ題字 onSurface が %.2f:1 で AA 未達".format(ratio), ratio >= 4.5)
        }
    }
}
