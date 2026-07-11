package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.ReadingSettingsSheetContent
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ReadingSettingsSheetContent（表示設定シートの中身）の 3テーマ × fontScale{1.0,2.0} スクリーンショット
 * 回帰（ADR 0009 増補1）。テーマ3択チップ・スライダー値ラベルが並ぶため、テーマ退行とフォント拡大時の
 * レイアウト破綻の双方を捉えやすい。ModalBottomSheet 枠は Robolectric で不安定（別ウィンドウ描画・部分展開）
 * なため中身の Content を直接組む（semantics 版 ReadingSettingsSheetTest と同じ理由）。
 * ゲート非同乗の理由は ScreenshotTestSupport.kt 参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ReadingSettingsSheetScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "ReadingSettingsSheetContent_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"
        composeTestRule.captureThemed(theme, fontScale, name) { colors ->
            // シートの中身は自前で背景を持たないため、テーマ素地を敷いて捉える。
            // 現在選択テーマ（readingTheme）は描画テーマと一致させ、チップの選択ハイライトを対象に含める。
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                ReadingSettingsSheetContent(
                    colors = colors,
                    readingTheme = theme,
                    onThemeChange = {},
                    fontSize = 18,
                    onFontSizeChange = {},
                    onFontSizePersist = {},
                    lineHeightEm = 2.5f,
                    onLineHeightChange = {},
                    onLineHeightPersist = {},
                    bodyMarginDp = 20,
                    onBodyMarginChange = {},
                    onBodyMarginPersist = {},
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
