package com.novelreader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「最上部へ」ピル（2026-07-16 実機フィードバック・案C裁定＝reading-backtotop-D.html）の配線担保。
 * 出現条件〈メニュー表示中 かつ 章の半分以上（可視先頭アイテム×2 ≥ 全アイテム）〉と、
 * タップで章先頭へ戻る（＝条件が外れてピルが消える）動作を Robolectric で固定する。
 * ハーネスは NativeReadingScreenA11yTest と同型（描画層 Content を直接組む・heightOffset 突き当てで
 * メニュー表示/没入を作り分ける）。parseResult は Success（無限アニメを持たない）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeReadingScreenTopPillTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    /** 段落 n 個の章（Plain＋LineBreak の繰り返し＝LazyColumn のアイテム数を稼ぐ）。 */
    private fun longChapter(n: Int) = ChapterContent(
        title = "テスト章",
        segments = buildList {
            repeat(n) {
                add(TextSegment.Plain("これは第${it}段落のテスト本文です。"))
                add(TextSegment.LineBreak)
            }
        }.toImmutableList(),
    )

    private fun setContent(
        topAppBarState: TopAppBarState,
        lazyListState: LazyListState,
        paragraphs: Int = 200,
    ) {
        composeTestRule.setContent {
            ChapterScreenContent(
                parseResult = ParseResult.Success(longChapter(paragraphs)),
                colors = colors,
                fontSize = 18,
                onFontSizeChange = {},
                onFontSizePersist = {},
                lineHeightEm = 2.5f,
                onLineHeightChange = {},
                onLineHeightPersist = {},
                bodyMarginDp = 20,
                onBodyMarginChange = {},
                onBodyMarginPersist = {},
                readingTheme = ReadingTheme.LIGHT,
                onThemeChange = {},
                followingSystem = true,
                onFollowSystem = {},
                lazyListState = lazyListState,
                topAppBarState = topAppBarState,
                scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
                prevFile = "c0002.html",
                nextFile = "c0004.html",
                navEnabled = true,
                isLastChapter = false,
                ncode = null,
                continuationInfo = null,
                showChromeHint = false,
                showReturnChip = false,
                onReturnToContinuation = {},
                bookTitle = "テスト書名",
                ncodeSearchState = NcodeSearchUiState.Loading,
                onSearchNcode = {},
                onRetryNcodeSearch = {},
                onLinkNcode = {},
                onReadContinuation = {},
                onOpenWorkPage = {},
                onNavigateTo = {},
                onNavigateToBookshelf = {},
                onRetryParse = {},
            )
        }
    }

    /** メニュー表示へ倒す（collapsedFraction=0）。A11yTest と同じ突き当て方。 */
    private fun makeChromeVisible(state: TopAppBarState) = composeTestRule.runOnIdle {
        if (state.heightOffsetLimit >= 0f) state.heightOffsetLimit = -100f
        state.heightOffset = 0f
    }

    /** 没入へ倒す（collapsedFraction=1）。 */
    private fun makeImmersive(state: TopAppBarState) = composeTestRule.runOnIdle {
        val limit = if (state.heightOffsetLimit < 0f) state.heightOffsetLimit else -100f
        state.heightOffsetLimit = limit
        state.heightOffset = limit
    }

    @Test
    fun `章の後半かつメニュー表示中はピルが出てタップで先頭へ戻り消える`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        // 後半（150番目のアイテム）から表示開始＝可視先頭×2 ≥ 全アイテムを満たす
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list)
        makeChromeVisible(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertIsDisplayed()
        composeTestRule.onNodeWithText("最上部へ").performClick()
        composeTestRule.waitForIdle()

        // 先頭へ戻る＝出現条件（半分以上）が外れてピルも消える（完了フィードバック兼用）
        composeTestRule.runOnIdle { assertEquals(0, list.firstVisibleItemIndex) }
        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
    }

    @Test
    fun `章の前半ではメニュー表示中でもピルを出さない`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 0)
        setContent(topBar, list)
        makeChromeVisible(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
    }

    @Test
    fun `没入中は章の後半でもピルを出さない`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list)
        makeImmersive(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
    }
}
