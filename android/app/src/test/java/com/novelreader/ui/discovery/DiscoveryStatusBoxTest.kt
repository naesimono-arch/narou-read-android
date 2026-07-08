package com.novelreader.ui.discovery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DiscoveryStatusBox（発見系リスト領域の Loading/Empty/Error 共通表示）の分岐テスト（ADR 0009）。
 * なぜ固定するか: sealed DiscoveryStatus で「読込中かつエラー」等の不正組合せを排除した設計の
 * 実効（各状態が排他的に正しく描画され、Error の onRetry が null/非null で出し分く）を担保するため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryStatusBoxTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Loading状態はプログレスインジケータを描画する`() {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryStatusBox(
                    status = DiscoveryStatus.Loading,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        // 円形インジケータは ProgressBarRangeInfo semantics を持つ。テキストが無い状態のため
        // これで Loading 分岐が描かれたことを確認する。
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }

    @Test
    fun `Empty状態は渡したメッセージを表示する`() {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryStatusBox(
                    status = DiscoveryStatus.Empty("該当する作品がありません"),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        composeTestRule.onNodeWithText("該当する作品がありません").assertIsDisplayed()
    }

    @Test
    fun `Error状態はメッセージを表示しonRetryがnullなら再試行ボタンは出ない`() {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryStatusBox(
                    status = DiscoveryStatus.Error("通信に失敗しました", onRetry = null),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        composeTestRule.onNodeWithText("通信に失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("再試行").assertDoesNotExist()
    }

    @Test
    fun `Error状態でonRetry非nullなら再試行ボタンを表示しクリックで呼ばれる`() {
        var retried = false
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryStatusBox(
                    status = DiscoveryStatus.Error("通信に失敗しました", onRetry = { retried = true }),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        composeTestRule.onNodeWithText("再試行").assertIsDisplayed().performClick()
        assertTrue(retried)
    }
}
