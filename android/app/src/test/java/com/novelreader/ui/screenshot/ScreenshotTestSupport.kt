package com.novelreader.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors

/**
 * Roborazzi スクリーンショットテストの共有基盤（ADR 0009 増補1）。
 *
 * なぜスクリーンショットテストを足すか: 「ライトとセピアが同色」級のテーマ退行・トークン変更の
 * 意図せぬ波及・フォントスケール拡大時のレイアウト破綻を機械検知するため（ADR 0014 §A の
 * 「④コードは③モックとの乖離をスクリーンショットで検出する」層）。ピクセル単位の意匠美の
 * 判定は対象外（そこは ADR 0005 §B の実機フィードバック後詰め層のまま）。
 *
 * なぜ既定ゲート（testDebugUnitTest）に golden 比較を載せないか: Roborazzi はシステムプロパティ
 * （roborazzi.test.record/verify）未指定時は captureRoboImage が no-op になる標準挙動のため、素の
 * testDebugUnitTest では比較が走らず既存ゲートの速度・安定性を保てる。記録は recordRoborazziDebug、
 * 検証は verifyRoborazziDebug の明示実行運用とする（ADR 0009 増補1・ゲート方針）。
 *
 * golden の置き場は src/test/screenshots/（build/ 配下でなく git 追跡する参照画像）。
 * JVM のフォントレンダリングは環境依存のため golden は WSL(Linux) 記録を正とする（ADR 0009 増補1）。
 */
internal object ScreenshotConfig {
    /** golden PNG の格納先。Robolectric 単体テストの作業ディレクトリ（app/）からの相対。 */
    const val SCREENSHOT_DIR = "src/test/screenshots"

    /** テーマ × フォントスケールのマトリクス。テーマ退行検知のため 3 テーマ全てを回す。 */
    val THEMES = listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK)

    /** 1.0=既定、2.0=拡大時のレイアウト破綻検知。 */
    val FONT_SCALES = listOf(1.0f, 2.0f)

    /** ParameterizedRobolectricTestRunner 用: 全テーマ × 全スケールの直積。 */
    fun matrix(): List<Array<Any>> =
        THEMES.flatMap { theme ->
            FONT_SCALES.map { scale -> arrayOf<Any>(theme, scale) }
        }

    fun themeLabel(theme: ReadingTheme): String = theme.name.lowercase()

    /** ファイル名の scale 部（"1.0" / "2.0"）。 */
    fun scaleLabel(scale: Float): String = scale.toString()
}

/**
 * golden ファイル名の共通部（`<画面>_<状態>_<テーマ>_<スケール>.png`）。既存 golden の命名規約に揃える。
 * スキン非依存の純粋な整形＝スキン別の support ではなくここに置く（K/D 双方の状態別 golden が使う）。
 */
internal fun goldenName(screen: String, caseId: String, theme: ReadingTheme, fontScale: Float): String =
    "${screen}_${caseId}_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"

/**
 * 指定テーマ・フォントスケールで content を描画し、golden PNG を記録/検証する。
 *
 * - NovelReaderTheme(theme) で包む＝Material colorScheme・ShelfColors をテーマ追従させる。
 * - 読書系 Composable は colors: ReadingColors も要るため theme.colors を content へ渡す。
 * - フォントスケールは LocalDensity を上書きして与える（density は端末値を維持し fontScale だけ変える）。
 *   なぜ @Config(qualifiers) でなく LocalDensity 上書きか: リソース修飾子には fontScale の直接指定が
 *   無く、Compose の sp 換算に確実・局所的に効くこの方式が安定するため。
 */
internal fun ComposeContentTestRule.captureThemed(
    theme: ReadingTheme,
    fontScale: Float,
    fileName: String,
    content: @Composable (ReadingColors) -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            NovelReaderTheme(theme = theme) {
                content(theme.colors)
            }
        }
    }
    onRoot().captureRoboImage(
        filePath = "${ScreenshotConfig.SCREENSHOT_DIR}/$fileName",
    )
}
