package com.novelreader.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.model.ParseResult
import com.novelreader.ui.skins.ThemeControl
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
 * 下端バーの前後章ボタン配置の退行固定テスト（2026-07-29 ユーザー裁定）。
 *
 * 契約: 横書き＝[前章｜目次｜表示設定｜次章]（モック reading-D.html の並び）／
 * 縦書き（右→左進行）＝端の2ボタンを鏡像にした [次章｜目次｜表示設定｜前章]。
 * なぜ鏡像か: 縦書きは左へ読み進む＝押す方向と進む方向を一致させるため。
 * モック reading-vertical-scroll-D.html の下端バーは reading-D の流用（鏡像未規定）＝裁定が正。
 *
 * 検証は2層: ①並び＝各ボタン（ラベル Text がアクセシブルネーム）の x 座標順で固定
 * ②結線＝ラベルと遷移先の対応（鏡像でラベルだけ入れ替え遷移先を入れ替え忘れる退行が最悪のため）。
 * NativeReadingScreenA11yTest と同じく枠でなく [ChapterScreenContent] を直接組む
 * （parseResult=Error は Loading の無限アニメで clock が idle にならないのを避ける既知の作法）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingBottomBarMirrorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private fun setContent(
        verticalMode: Boolean,
        onNavigateTo: (String) -> Unit = {},
    ) {
        // 初期値のまま＝collapsedFraction 0（クローム表示）で下端バーが見えている状態。
        // 合成の外で生成する（NativeReadingScreenA11yTest と同作法＝再合成での作り直しを避ける）。
        val topAppBarState = TopAppBarState(0f, 0f, 0f)
        composeTestRule.setContent {
            // 束は全フィールド必須（配線忘れをコンパイルエラーにする・ReadingFace.kt 冒頭）。
            ChapterScreenContent(
                parseResult = ParseResult.Error("章を開けませんでした", "c0003.html"),
                colors = colors,
                typography = ReadingTypography(
                    fontSize = 18,
                    onFontSizeChange = {},
                    onFontSizePersist = {},
                    lineHeightEm = 2.5f,
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
                    onNavigateTo = onNavigateTo,
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

    /** ラベル Text（＝ボタンのアクセシブルネーム）を持つマージ済みノードの左端 x 座標。並びの検証に使う。 */
    private fun xOf(label: String): Float =
        composeTestRule.onNodeWithText(label).fetchSemanticsNode().positionInRoot.x

    @Test
    fun `横書きは従来並び＝前章が左端で次章が右端`() {
        setContent(verticalMode = false)
        assertTrue("前章は目次より左", xOf("前章") < xOf("目次"))
        assertTrue("目次は表示設定より左", xOf("目次") < xOf("表示設定"))
        assertTrue("表示設定は次章より左", xOf("表示設定") < xOf("次章"))
    }

    @Test
    fun `縦書きは鏡像並び＝次章が左端で前章が右端`() {
        setContent(verticalMode = true)
        assertTrue("次章は目次より左", xOf("次章") < xOf("目次"))
        assertTrue("目次は表示設定より左", xOf("目次") < xOf("表示設定"))
        assertTrue("表示設定は前章より左", xOf("表示設定") < xOf("前章"))
    }

    @Test
    fun `縦書きでもラベルと遷移先の対応は不変＝次章タップでnextFileへ`() {
        // 鏡像はあくまで配置の入れ替え。ラベルだけ入れ替えて遷移先を入れ替え忘れると
        // 「次章を押すと前章へ戻る」最悪の退行になるため、結線を独立に固定する。
        var navigatedTo: String? = null
        setContent(verticalMode = true, onNavigateTo = { navigatedTo = it })
        composeTestRule.onNodeWithText("次章").performClick()
        composeTestRule.runOnIdle { assertEquals("c0004.html", navigatedTo) }
    }

    @Test
    fun `縦書きの前章タップでprevFileへ`() {
        var navigatedTo: String? = null
        setContent(verticalMode = true, onNavigateTo = { navigatedTo = it })
        composeTestRule.onNodeWithText("前章").performClick()
        composeTestRule.runOnIdle { assertEquals("c0002.html", navigatedTo) }
    }
}
