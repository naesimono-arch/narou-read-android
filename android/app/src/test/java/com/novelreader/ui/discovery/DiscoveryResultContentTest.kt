package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouNovel
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DiscoveryResultContent（結果一覧の stateless 描画層）の状態分岐＋コールバック結線テスト（ADR 0009）。
 * process death 復帰中の文脈 null（最小ローディング）・Content 本体・Error 再試行の分岐がサイレント退行
 * しないことを固定する。VM 依存（並び順/ジャンル変更・追加読込）はルート層が持つため検証しない。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryResultContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ctx(title: String) = ResultContext(
        title = title,
        source = ResultSource.GENRE,
        query = DiscoveryQuery(),
    )

    private fun setContent(
        ctx: ResultContext?,
        state: DiscoveryUiState,
        onRefresh: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryResultContent(
                    ctx = ctx,
                    state = state,
                    onUp = {},
                    onBack = {},
                    onOpenDetail = {},
                    onChangeOrder = {},
                    onChangeGenreFilter = { _, _ -> },
                    onRefresh = onRefresh,
                    onLoadMore = {},
                )
            }
        }
    }

    @Test
    fun `文脈nullは復元待ちの最小ローディングを描く（強制退去しない）`() {
        setContent(ctx = null, state = DiscoveryUiState.Loading)
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }

    @Test
    fun `Content状態は文脈見出しと作品名を描画する`() {
        setContent(
            ctx = ctx("テスト結果"),
            state = DiscoveryUiState.Content(allcount = 1, novels = listOf(NarouNovel(title = "結果作品"))),
        )
        composeTestRule.onNodeWithText("テスト結果").assertIsDisplayed()
        composeTestRule.onNodeWithText("結果作品").assertExists()
    }

    @Test
    fun `Error状態はメッセージを出し再試行でonRefreshが呼ばれる`() {
        var refreshed = false
        setContent(
            ctx = ctx("テスト結果"),
            state = DiscoveryUiState.Error("通信に失敗しました"),
            onRefresh = { refreshed = true },
        )
        composeTestRule.onNodeWithText("通信に失敗しました").assertIsDisplayed()
        composeTestRule.onNodeWithText("再試行").performClick()
        assertTrue(refreshed)
    }
}
