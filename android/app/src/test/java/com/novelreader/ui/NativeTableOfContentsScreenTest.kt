package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.model.TocEntry
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NativeTableOfContentsScreen の TocState 4状態（Loading/Empty/Error/Content）の描画分岐と
 * コールバック結線テスト（ADR 0009）。
 * なぜ固定するか: 「非同期パース中の一瞬」と「真に0件」を区別するために sealed 4状態へ分けた設計
 * （公理8・状態の可視性）の核心が、Loading で誤って「章が見つかりません」を出さないこと等に依るため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeTableOfContentsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private fun setToc(
        state: TocState,
        currentChapterFile: String? = null,
        onSelectChapter: (String) -> Unit = {},
        onNavigateToBookshelf: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            NativeTableOfContentsScreen(
                tocState = state,
                colors = colors,
                currentChapterFile = currentChapterFile,
                onSelectChapter = onSelectChapter,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = onRetry,
            )
        }
    }

    @Test
    fun `Loading状態ではスケルトンのみで空メッセージやエラーを出さない`() {
        setToc(TocState.Loading)
        // 題字は出るが、Empty/Error のテキストは出ないことで Loading 分岐を確認する
        composeTestRule.onNodeWithText("目次").assertIsDisplayed()
        composeTestRule.onNodeWithText("章が見つかりません").assertDoesNotExist()
        composeTestRule.onNodeWithText("目次の読み込みに失敗しました").assertDoesNotExist()
    }

    @Test
    fun `Empty状態では章が見つかりませんを表示する`() {
        setToc(TocState.Empty)
        composeTestRule.onNodeWithText("章が見つかりません").assertIsDisplayed()
    }

    @Test
    fun `Error状態ではメッセージを表示し再試行クリックでonRetryが呼ばれる`() {
        var retried = false
        setToc(TocState.Error("index.html が壊れています"), onRetry = { retried = true })
        composeTestRule.onNodeWithText("目次の読み込みに失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("index.html が壊れています").assertIsDisplayed()
        composeTestRule.onNodeWithText("再試行").performClick()
        assertTrue(retried)
    }

    @Test
    fun `Content状態では章題を表示し章クリックでファイル名付きコールバックが呼ばれる`() {
        var selected: String? = null
        setToc(
            TocState.Content(
                listOf(
                    TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
                    TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
                )
            ),
            onSelectChapter = { selected = it },
        )
        composeTestRule.onNodeWithText("第一章 出会い").assertIsDisplayed()
        composeTestRule.onNodeWithText("第二章 旅立ち").performClick()
        assertEquals("chap_2.html", selected)
    }

    @Test
    fun `Content状態で章題が空なら第N章にフォールバックする`() {
        setToc(
            TocState.Content(
                listOf(TocEntry(title = "", fileName = "chap_1.html"))
            )
        )
        composeTestRule.onNodeWithText("第1章").assertIsDisplayed()
    }

    @Test
    fun `戻るナビゲーションクリックでonNavigateToBookshelfが呼ばれる`() {
        var navigated = false
        setToc(TocState.Empty, onNavigateToBookshelf = { navigated = true })
        composeTestRule.onNodeWithContentDescription("本棚に戻る").performClick()
        assertTrue(navigated)
    }
}
