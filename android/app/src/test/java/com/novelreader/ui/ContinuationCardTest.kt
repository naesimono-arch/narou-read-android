package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.narou.ContinuationInfo
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ContinuationCard（章末の継続案内カード）の状態別表示＋コールバック結線テスト（ADR 0009）。
 * なぜ固定するか: NewEpisodes（新着あり）のときだけ主ボタン「続きを読む」を出し、UpToDate（追いつき）
 * では出さないという分岐と、3つの導線（続き読む/作品ページ/解除）の結線がサイレント退行しないため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContinuationCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private val newEpisodes = ContinuationInfo.NewEpisodes(
        ncode = "n1234ab",
        totalEpisodes = 130,
        pdfEpisodes = 127,
        nextEpisode = 128,
        newCount = 3,
    )

    private fun setCard(
        info: ContinuationInfo,
        onReadContinuation: () -> Unit = {},
        onOpenWorkPage: () -> Unit = {},
        onUnlink: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ContinuationCard(
                info = info,
                colors = colors,
                bodyMarginDp = 15,
                onReadContinuation = onReadContinuation,
                onOpenWorkPage = onOpenWorkPage,
                onUnlink = onUnlink,
            )
        }
    }

    @Test
    fun `NewEpisodesでは続きを読む主ボタンと新着件数の説明文を表示する`() {
        setCard(newEpisodes)
        composeTestRule.onNodeWithText("第128話から続きを読む").assertIsDisplayed()
        // 説明文に手元PDF話数・新着件数が織り込まれることを部分一致で確認
        composeTestRule.onNodeWithText("新着3話", substring = true).assertIsDisplayed()
    }

    @Test
    fun `UpToDateでは主ボタンを出さず追いついた旨を表示する`() {
        setCard(ContinuationInfo.UpToDate(ncode = "n1234ab", totalEpisodes = 130))
        composeTestRule.onNodeWithText("追いついています", substring = true).assertIsDisplayed()
        // 主ボタンは NewEpisodes 専用なので存在しないこと
        composeTestRule.onNodeWithText("続きを読む", substring = true).assertDoesNotExist()
    }

    @Test
    fun `続きを読む主ボタンのクリックでonReadContinuationが呼ばれる`() {
        var read = false
        setCard(newEpisodes, onReadContinuation = { read = true })
        composeTestRule.onNodeWithText("第128話から続きを読む").performClick()
        assertTrue(read)
    }

    @Test
    fun `作品ページを見るクリックでonOpenWorkPageが呼ばれる`() {
        var opened = false
        setCard(newEpisodes, onOpenWorkPage = { opened = true })
        composeTestRule.onNodeWithText("作品ページを見る").performClick()
        assertTrue(opened)
    }

    @Test
    fun `紐付けを解除クリックでonUnlinkが呼ばれる`() {
        var unlinked = false
        setCard(newEpisodes, onUnlink = { unlinked = true })
        composeTestRule.onNodeWithText("紐付けを解除").performClick()
        assertTrue(unlinked)
    }
}
