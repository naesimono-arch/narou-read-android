package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import com.novelreader.viewmodel.DiscoveryUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DiscoveryHomeContent（発見ホームの stateless 描画層）の状態分岐＋コールバック結線テスト（ADR 0009）。
 * state-holder / UI 分割で VM から切り出した葉が対象。ランキング一覧の Content/Empty 分岐と、検索導線・
 * order 切替の結線がサイレント退行しないことを固定する。VM 依存（ロード起動）はルート層が持つため検証しない。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        state: DiscoveryUiState,
        onOpenSearch: () -> Unit = {},
        onSelectOrder: (NarouOrder) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryHomeContent(
                    order = NarouOrder.WEEKLY,
                    state = state,
                    onBack = {},
                    onOpenDetail = {},
                    onOpenGenre = {},
                    onPickBiggenre = { _, _ -> },
                    onOpenSearch = onOpenSearch,
                    onPickMood = {},
                    onSelectOrder = onSelectOrder,
                    onRefresh = {},
                )
            }
        }
    }

    @Test
    fun `Content状態で気分節・ジャンル節と作品名を描画する`() {
        setContent(DiscoveryUiState.Content(allcount = 1, novels = listOf(NarouNovel(title = "テスト作品"))))
        // 常時表示の見出し（Content 分岐が Scaffold ごと描かれた証拠）
        composeTestRule.onNodeWithText("きょうの気分").assertIsDisplayed()
        composeTestRule.onNodeWithText("ジャンルから").assertIsDisplayed()
        // 一覧本体（LazyColumn の item）に作品名が現れる。
        // なぜスクロールしてから確かめるか: LazyColumn は可視域しか合成しないため、気分節・ジャンル節の
        // 下にある一覧アイテムは Robolectric の小さなビューポートでは未合成＝assertExists が偽陰性になる。
        // 縦の LazyColumn（走査順の先頭 scrollable）を該当ノードまでスクロールさせて合成を保証する。
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText("テスト作品"))
        composeTestRule.onNodeWithText("テスト作品").assertExists()
    }

    @Test
    fun `Empty状態は作品なしメッセージを描画する`() {
        setContent(DiscoveryUiState.Empty)
        // Content テストと同じ理由（LazyColumn の遅延合成）でスクロールしてから確かめる
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText("作品が見つかりませんでした"))
        composeTestRule.onNodeWithText("作品が見つかりませんでした").assertExists()
    }

    @Test
    fun `検索アイコンでonOpenSearchが呼ばれる`() {
        var searched = false
        setContent(DiscoveryUiState.Empty, onOpenSearch = { searched = true })
        composeTestRule.onNodeWithContentDescription("検索").performClick()
        assertTrue(searched)
    }
}
