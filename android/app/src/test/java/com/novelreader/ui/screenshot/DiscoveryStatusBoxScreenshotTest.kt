package com.novelreader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.novelreader.ui.discovery.DiscoveryStatus
import com.novelreader.ui.discovery.DiscoveryStatusBox
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DiscoveryStatusBox（発見系リスト領域の共通ステータス表示・Error 状態）の 3テーマ × fontScale{1.0,2.0}
 * スクリーンショット回帰（ADR 0009 増補1）。発見系は Material colorScheme（NovelReaderTheme が
 * テーマ追従で provide）から色を引くため、「ライトとセピアが同色」級のテーマ退行の検知対象。
 * メッセージ＋再試行ボタンを持つ Error 状態を代表として撮る。ゲート非同乗の理由は
 * ScreenshotTestSupport.kt 参照。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class DiscoveryStatusBoxScreenshotTest(
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        val name = "DiscoveryStatusBox_${ScreenshotConfig.themeLabel(theme)}_${ScreenshotConfig.scaleLabel(fontScale)}.png"
        composeTestRule.captureThemed(theme, fontScale, name) { _ ->
            // DiscoveryStatusBox は Material colorScheme を使う（ReadingColors は取らない）。
            // 発見系の版面素地として colorScheme.background を敷く。
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                DiscoveryStatusBox(
                    status = DiscoveryStatus.Error("通信に失敗しました", onRetry = {}),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
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
