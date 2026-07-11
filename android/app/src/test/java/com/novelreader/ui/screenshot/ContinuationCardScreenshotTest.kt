package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.ContinuationCard
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ContinuationCard（章末の継続案内カード・NewEpisodes 状態）の 3テーマ × fontScale{1.0,2.0}
 * スクリーンショット回帰（ADR 0009 増補1）。カード枠線・ブロック背景・本文色がテーマトークンから
 * 引かれるため、トークン波及の退行検知に向く。ゲート非同乗の理由は ScreenshotTestSupport.kt 参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ContinuationCardScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val newEpisodes = ContinuationInfo.NewEpisodes(
        ncode = Ncode("n1234ab"),
        totalEpisodes = 130,
        pdfEpisodes = 127,
        nextEpisode = 128,
        newCount = 3,
    )

    @Test
    fun capture() {
        val name = "ContinuationCard_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"
        composeTestRule.captureThemed(theme, fontScale, name) { colors ->
            // カードは wrap 型なので、テーマ素地の背景を敷いて版面として捉える。
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                ContinuationCard(
                    info = newEpisodes,
                    colors = colors,
                    bodyMarginDp = 15,
                    onReadContinuation = {},
                    onOpenWorkPage = {},
                    onUnlink = {},
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
