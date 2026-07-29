package com.novelreader.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.model.TocEntry
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.NcodeSearchUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * push 遷移中の構造骨（案A・2026-07-29 裁定）の状態切替契約を deferHeavyContent レベルで固定する。
 * 契約＝「遷移窓（defer=true）は重い実内容をコンポーズせず骨、クロームは実描画のまま。
 * settle 後（defer=false）は実内容」。P2 の BookshelfContentTest『遷移中(deferHeavyContent)は…』と同型。
 * 遷移アニメ自体（250ms の窓の開閉＝NavHost/Transition の currentState 反転）は Robolectric では
 * 決定的に再現できないため、窓の開閉は信号（Boolean と純関数 isTocToChapterPush）のレベルで固定する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TransitionSkeletonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    // ────── 目次: 遷移窓の骨差し替え契約 ──────

    private fun setToc(defer: Boolean) {
        composeTestRule.setContent {
            NativeTableOfContentsScreen(
                tocState = TocState.Content(
                    listOf(
                        TocEntry(title = "第一章 出会い", fileName = "chap_1.html"),
                        TocEntry(title = "第二章 旅立ち", fileName = "chap_2.html"),
                    )
                ),
                colors = colors,
                currentChapterFile = null,
                onSelectChapter = {},
                onNavigateToBookshelf = {},
                onRetry = {},
                deferHeavyContent = defer,
            )
        }
    }

    @Test
    fun `目次_遷移中(deferHeavyContent)は章リストを骨へ差し替えトップバーは実描画のまま`() {
        setToc(defer = true)
        // クローム（題字・戻る）は実描画＝案A「軽量部は本物・重い可変部だけ骨」の分担。
        composeTestRule.onNodeWithText("目次").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("本棚に戻る").assertIsDisplayed()
        // 章リスト（重い実内容）は窓中コンポーズされない＝Content を渡していても章題は存在しない。
        composeTestRule.onNodeWithText("第一章 出会い").assertDoesNotExist()
    }

    @Test
    fun `目次_settle後(defer解除)は実内容の章題を出す`() {
        setToc(defer = false)
        composeTestRule.onNodeWithText("第一章 出会い").assertIsDisplayed()
    }

    // ────── 本文: 遷移窓の骨差し替え契約 ──────

    // ruby 込みの小さな章（VerticalChapterContentTest と同形）: 本文非コンポーズの検証に spoken ノードを使う。
    private val chapterContent = ChapterContent(
        title = "テスト章",
        segments = persistentListOf(
            TextSegment.Plain("この"),
            TextSegment.Ruby("魔剣", "つるぎ"),
            TextSegment.Plain("を置いた。"),
        ),
    )

    /** [ChapterScreenContent] を ParseResult.Success で描画（束の実値は VerticalChapterContentTest の写経）。 */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun setChapter(defer: Boolean) {
        composeTestRule.setContent {
            val topAppBarState = TopAppBarState(0f, 0f, 0f)
            ChapterScreenContent(
                parseResult = ParseResult.Success(chapterContent),
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
                    verticalMode = false,
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
                deferHeavyContent = defer,
            )
        }
    }

    @Test
    fun `本文_遷移中(deferHeavyContent)は本文だけ骨でバー題字は実描画のまま`() {
        setChapter(defer = true)
        // 「テスト章」はバー題字（クローム＝実描画）の1ノードのみ＝本文側の章見出し Text は骨に置き換わる。
        composeTestRule.onAllNodesWithText("テスト章").assertCountEquals(1)
        // 本文段落（spoken 置換済み読み上げノード）もコンポーズされない＝重いテキスト measure が窓外へ移送される核心。
        composeTestRule
            .onAllNodes(hasContentDescription("このつるぎを置いた。"), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `本文_settle後(defer解除)は実内容(バー題字と本文見出しの2ノード)`() {
        setChapter(defer = false)
        composeTestRule.onAllNodesWithText("テスト章").assertCountEquals(2)
        composeTestRule
            .onAllNodes(hasContentDescription("このつるぎを置いた。"), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    // ────── 骨信号の向き契約（純関数） ──────

    @Test
    fun `骨信号は目次から章へのpushのときだけ立つ`() {
        // push（目次→章）＝対象。
        assertTrue(isTocToChapterPush("index.html", "chap_1.html"))
        // pop（章→目次）＝退場側が既測コンテンツで安価なため対象外（2026-07-29 裁定）。
        assertFalse(isTocToChapterPush("chap_1.html", "index.html"))
        // 話送り（章→章）＝遷移なしの瞬間切替。骨を挟むと1フレームちらつき退行になるため対象外。
        assertFalse(isTocToChapterPush("chap_1.html", "chap_2.html"))
        // 静止（遷移なし）。
        assertFalse(isTocToChapterPush("index.html", "index.html"))
    }
}
