package com.novelreader.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.ReadingErrorScreen
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ReadingErrorScreen の 3テーマ × fontScale{1.0,2.0} スクリーンショット回帰（ADR 0009 増補1）。
 * 全色を自前で塗る（Scaffold 外から呼ばれる）葉なので、テーマ退行がそのまま可視化される好対象。
 * ゲート非同乗の理由と golden 運用は ScreenshotTestSupport.kt のヘッダ参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ReadingErrorScreenScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "ReadingErrorScreen_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"
        composeTestRule.captureThemed(theme, fontScale, name) { colors ->
            ReadingErrorScreen(
                message = "ファイルが見つかりません",
                colors = colors,
                onNavigateToBookshelf = {},
                onRetry = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}_scale{1}")
        fun data(): List<Array<Any>> = ScreenshotConfig.matrix()
    }
}
