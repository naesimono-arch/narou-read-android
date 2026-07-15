package com.novelreader.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import com.novelreader.model.ParseResult
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 没入モード（クローム非表示）での a11y 到達回復（device-verify 2026-07-16 #4）の退行固定テスト。
 *
 * 真因: 上下バーは graphicsLayer{translationY} で画面外へ退避しており、Compose は画面外ノードを
 * a11y ツリーから除外する＝没入中は戻る/目次/前章/次章/表示設定の clickable ノードが 0 になり
 * TalkBack スワイプ走査で到達不能。是正は描画層ルートへ「実ボタンと同一コールバック」の customActions を
 * 没入中だけ貼ることで、ローカルコンテキストメニューから各操作への到達を回復する（視覚・タップ挙動は不変）。
 *
 * 対象は描画層 [ChapterScreenContent]（クローム可視状態＝topAppBarState と各コールバックが揃う葉）。
 * ReadingSettingsSheetTest 同様、枠でなく Content を直接組んで state+customActions を検証する。
 * parseResult は静的な Error（=ReadingErrorScreen）にする＝Loading の CircularProgressIndicator の
 * 無限アニメで clock が idle にならず waitForIdle が固まるのを避けるため。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeReadingScreenA11yTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    /**
     * [ChapterScreenContent] を描画する。topAppBarState を呼び出し側で用意し、描画後に
     * heightOffset を限界値/0 へ突き当てて没入/クローム表示を作り分ける（collapsedFraction=1/0）。
     */
    private fun setContent(
        topAppBarState: TopAppBarState,
        prevFile: String = "c0002.html",
        nextFile: String = "c0004.html",
        navEnabled: Boolean = true,
        onNavigateTo: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ChapterScreenContent(
                // 静的描画（無限アニメ回避）。customActions はルート Box に付くため parseResult には非依存。
                parseResult = ParseResult.Error("章を開けませんでした", "c0003.html"),
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
                lazyListState = rememberLazyListState(),
                topAppBarState = topAppBarState,
                scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
                prevFile = prevFile,
                nextFile = nextFile,
                navEnabled = navEnabled,
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
                onNavigateTo = onNavigateTo,
                onNavigateToBookshelf = {},
                onRetryParse = {},
            )
        }
    }

    /** 没入（クローム非表示）へ倒す: heightOffset を限界値へ突き当て collapsedFraction=1 にする。 */
    private fun makeImmersive(state: TopAppBarState) = composeTestRule.runOnIdle {
        // TopAppBar 実測後に heightOffsetLimit は負値になるが、測定前フォールバックも保証する。
        val limit = if (state.heightOffsetLimit < 0f) state.heightOffsetLimit else -100f
        state.heightOffsetLimit = limit
        state.heightOffset = limit
    }

    /** クローム表示へ倒す: heightOffset=0 で collapsedFraction=0 にする。 */
    private fun makeChromeVisible(state: TopAppBarState) = composeTestRule.runOnIdle {
        if (state.heightOffsetLimit >= 0f) state.heightOffsetLimit = -100f
        state.heightOffset = 0f
    }

    /** customActions を持つノード（＝読書画面ルート Box）から CustomAccessibilityAction 一覧を取り出す。 */
    private fun customActions(): List<CustomAccessibilityAction> =
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .onFirst()
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            ?: emptyList()

    @Test
    fun `没入中は端章でない章で5つのcustomActionsを露出する`() {
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state)
        makeImmersive(state)

        val labels = customActions().map { it.label }
        assertEquals(
            listOf("戻る", "目次を開く", "前の章", "次の章", "表示設定"),
            labels,
        )
    }

    @Test
    fun `クローム表示中はcustomActionsを露出しない（実ボタンとの二重発話を避ける）`() {
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state)
        makeChromeVisible(state)

        // customActions キー自体が未定義＝没入時のみ付与される。
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `没入中の端章では隣接章が無い前後章アクションを出さない`() {
        // 先頭章相当: prevFile が index.html へ縮退＝「前の章」を出さない（「目次を開く」が同遷移を担う）。
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state, prevFile = "index.html", nextFile = "c0002.html")
        makeImmersive(state)

        val labels = customActions().map { it.label }
        assertEquals(
            listOf("戻る", "目次を開く", "次の章", "表示設定"),
            labels,
        )
    }

    @Test
    fun `目次未ロード中（navEnabled_false）は前後章アクションを出さない`() {
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state, navEnabled = false)
        makeImmersive(state)

        val labels = customActions().map { it.label }
        // navEnabled=false＝実ボタンが disabled のときと一致（戻る/目次/表示設定のみ）。
        assertEquals(listOf("戻る", "目次を開く", "表示設定"), labels)
    }

    @Test
    fun `前の章アクション起動でonNavigateToが前章ファイルで呼ばれる`() {
        var navigatedTo: String? = null
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state, prevFile = "c0002.html", onNavigateTo = { navigatedTo = it })
        makeImmersive(state)

        val prev = customActions().first { it.label == "前の章" }
        composeTestRule.runOnUiThread { assertTrue(prev.action()) }
        composeTestRule.runOnIdle { assertEquals("c0002.html", navigatedTo) }
    }

    @Test
    fun `目次を開くアクション起動でonNavigateToがindexで呼ばれる`() {
        var navigatedTo: String? = null
        val state = TopAppBarState(0f, 0f, 0f)
        setContent(state, onNavigateTo = { navigatedTo = it })
        makeImmersive(state)

        val toToc = customActions().first { it.label == "目次を開く" }
        composeTestRule.runOnUiThread { assertTrue(toToc.action()) }
        composeTestRule.runOnIdle { assertEquals("index.html", navigatedTo) }
    }
}
