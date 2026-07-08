package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ReadingErrorScreen（葉 Composable）の状態分岐＋コールバック結線テスト（ADR 0009・Robolectric）。
 * なぜ UI テストを足すか: state+callback の理想形（純表示）なのに従来テスト0本で、onRetry の
 * null/非null による再試行ボタン出し分けやボタン結線がサイレント退行しても捕まえられなかったため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingErrorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // 読書テーマの色トークン（描画に必須。値そのものはテスト対象外なので LIGHT 固定で足りる）
    private val colors = ReadingTheme.LIGHT.colors

    @Test
    fun `固定見出しと渡したメッセージが表示される`() {
        composeTestRule.setContent {
            ReadingErrorScreen(
                message = "ファイルが見つかりません",
                colors = colors,
                onNavigateToBookshelf = {},
            )
        }
        composeTestRule.onNodeWithText("読み込みに失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("ファイルが見つかりません").assertIsDisplayed()
    }

    @Test
    fun `onRetryがnullなら再試行ボタンは表示されない`() {
        composeTestRule.setContent {
            ReadingErrorScreen(
                message = "エラー",
                colors = colors,
                onNavigateToBookshelf = {},
                onRetry = null,
            )
        }
        composeTestRule.onNodeWithText("再試行").assertDoesNotExist()
    }

    @Test
    fun `onRetry非nullなら再試行ボタンを表示しクリックでコールバックが呼ばれる`() {
        var retried = false
        composeTestRule.setContent {
            ReadingErrorScreen(
                message = "エラー",
                colors = colors,
                onNavigateToBookshelf = {},
                onRetry = { retried = true },
            )
        }
        composeTestRule.onNodeWithText("再試行").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun `本棚に戻るクリックでonNavigateToBookshelfが呼ばれる`() {
        var navigated = false
        composeTestRule.setContent {
            ReadingErrorScreen(
                message = "エラー",
                colors = colors,
                onNavigateToBookshelf = { navigated = true },
            )
        }
        composeTestRule.onNodeWithText("本棚に戻る").performClick()
        assertTrue(navigated)
    }
}
