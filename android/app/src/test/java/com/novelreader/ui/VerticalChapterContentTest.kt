package com.novelreader.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import androidx.compose.foundation.text.BasicText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [VerticalChapterContent]（縦書き章本文）と [ChapterScreenContent] の縦書き分岐の退行固定。
 *
 * 検証点（P3 完了定義）:
 * (a) 段落の a11y spoken が当て字の著者読みに置換されて存在する（RubyText.kt:238-248 の縦書き移植）。
 * (b) 継続スロットが末尾アイテムに描かれる。
 * (c) verticalMode の既定 false で従来の横書き ChapterContent 経路（LazyColumn＝縦スクロール）になり、
 *     true で縦書き LazyRow（横スクロール）になる＝分岐が配線されている。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class VerticalChapterContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    // 当て字『魔剣(つるぎ)』を含む小さな章。spoken 置換の検証に使う。
    private val atejiContent = ChapterContent(
        title = "テスト章",
        segments = persistentListOf(
            TextSegment.Plain("この"),
            TextSegment.Ruby("魔剣", "つるぎ"),
            TextSegment.Plain("を置いた。"),
        ),
    )

    @Test
    fun `段落のspokenは当て字を著者読みに置換して読み上げる`() {
        composeTestRule.setContent {
            VerticalChapterContent(
                content = atejiContent,
                colors = colors,
                fontSize = 17,
                lineHeightEm = 2.4f,
                bodyMarginDp = 20,
            )
        }

        // spoken＝「この」＋reading「つるぎ」＋「を置いた。」。親漢字「魔剣」ではなく読みが積まれる。
        composeTestRule
            .onNode(hasContentDescription("このつるぎを置いた。"), useUnmergedTree = true)
            .assertExists()
        // 親漢字「魔剣」を含む読み上げノードは存在しない（二重読み・当て字読みの回避）。
        composeTestRule
            .onAllNodes(hasContentDescription("魔剣", substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `継続スロットが末尾アイテムに描かれる`() {
        composeTestRule.setContent {
            VerticalChapterContent(
                content = atejiContent,
                colors = colors,
                fontSize = 17,
                lineHeightEm = 2.4f,
                bodyMarginDp = 20,
                continuation = { BasicText("__CONTINUATION__") },
            )
        }

        // 小さな章＝全アイテムが横幅内に収まり、末尾（左端）の継続スロットも可視で描かれる。
        composeTestRule.onNodeWithText("__CONTINUATION__").assertIsDisplayed()
    }

    @Test
    fun `verticalMode既定falseは横書きChapterContent（縦スクロール）経路`() {
        setChapterScreenContent(verticalMode = false)
        // LazyColumn は VerticalScrollAxisRange を公開する＝横書き経路である証拠。
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `verticalMode_trueは縦書きVerticalChapterContent（横スクロール）経路`() {
        setChapterScreenContent(verticalMode = true)
        // LazyRow は HorizontalScrollAxisRange を公開する＝縦書き経路である証拠。
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `縦書き中はTopAppBarに章題テキストを出さない（2026-07-29裁定a）`() {
        setChapterScreenContent(verticalMode = true)
        // TopAppBar の章題 Text は縦書きでは出さない。縦書きは列高確保のため上端クリアランスを省略して
        // おり、バー可視時に列上端が題字の下へ潜るため（NativeReadingScreen.kt の title 分岐コメント参照）。
        // 縦書きの章見出し（VerticalChapterHeader）は Text でなく contentDescription＝Text ノードは 0 になる。
        composeTestRule.onAllNodesWithText("テスト章").assertCountEquals(0)
        // 章題は本文先頭の章見出しが担い続ける（heading・contentDescription で読み上げ到達も維持）。
        composeTestRule
            .onNode(hasContentDescription("テスト章"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `横書きではTopAppBarの章題テキストを従来どおり出す（裁定aの適用範囲固定）`() {
        setChapterScreenContent(verticalMode = false)
        // バー題字＋本文の章見出し（横書き ChapterHeader は Text 描画）の2ノード＝バー側が消えていない証拠。
        composeTestRule.onAllNodesWithText("テスト章").assertCountEquals(2)
    }

    /** [ChapterScreenContent] を ParseResult.Success で描画し、本文スロットの分岐を検証可能にする。 */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun setChapterScreenContent(verticalMode: Boolean) {
        composeTestRule.setContent {
            val topAppBarState = TopAppBarState(0f, 0f, 0f)
            // 束は全フィールド必須（既定値なし＝ReadingFace.kt 冒頭）。検証対象の verticalMode 以外で
            // 旧・既定値に頼っていた値（barsVisualReady=true／chapterNumber・totalChapters・peek=null／
            // onVerticalModeChange=no-op）は実値で明示する＝描画内容は従来と同一。
            ChapterScreenContent(
                parseResult = ParseResult.Success(atejiContent),
                colors = colors,
                typography = ReadingTypography(
                    fontSize = 17,
                    onFontSizeChange = {},
                    onFontSizePersist = {},
                    lineHeightEm = 2.4f,
                    onLineHeightChange = {},
                    onLineHeightPersist = {},
                    bodyMarginDp = 20,
                    onBodyMarginChange = {},
                    onBodyMarginPersist = {},
                    verticalMode = verticalMode,
                    onVerticalModeChange = {},
                ),
                theme = ThemeControl(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = true,
                    onFollowSystem = {},
                ),
                chrome = ReadingChrome(
                    lazyListState = rememberLazyListState(),
                    topAppBarState = topAppBarState,
                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
                    barsVisualReady = true,
                    showChromeHint = false,
                ),
                nav = ChapterNav(
                    prevFile = "c0002.html",
                    nextFile = "c0004.html",
                    navEnabled = true,
                    isLastChapter = false,
                    chapterNumber = null,
                    totalChapters = null,
                    onNavigateTo = {},
                    onNavigateToBookshelf = {},
                ),
                ncodeLink = NcodeLink(
                    bookTitle = "テスト書名",
                    ncode = null,
                    ncodeSearchState = NcodeSearchUiState.Loading,
                    onSearchNcode = {},
                    onRetryNcodeSearch = {},
                    onLinkNcode = {},
                ),
                continuationCta = ContinuationCta(
                    continuationInfo = null,
                    onReadContinuation = {},
                    onOpenWorkPage = {},
                ),
                prevPeek = null,
                nextPeek = null,
                showReturnChip = false,
                onReturnToContinuation = {},
                onRetryParse = {},
            )
        }
    }
}
