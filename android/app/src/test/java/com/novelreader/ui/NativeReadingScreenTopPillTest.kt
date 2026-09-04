package com.novelreader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.tokens
import com.novelreader.viewmodel.NcodeSearchUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「最上部へ」ピル（2026-07-16 実機フィードバック・案C裁定＝reading-backtotop-D.html）の配線担保。
 * 出現条件〈メニュー表示中 かつ 章の3割以上（可視先頭アイテム×10 ≥ 全アイテム×3）〉と、
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

    /**
     * 段落 n 個の章（Plain＋LineBreak の繰り返し＝LazyColumn のアイテム数を稼ぐ）。
     *
     * ⚠️ 各行を字下げ「　」で始めるのが load-bearing。表示側の [splitIntoParagraphs] は
     * 「行頭が字下げ／開き括弧」を段落の切れ目に使う（ADR 0041 決定2 で抽出側から移設）ので、
     * 字下げの無い行を並べると**全部 1 段落へ畳まれて**アイテムが 1 個になり、
     * このテストが必要とする「後半までスクロールした状態」が作れない。
     */
    private fun longChapter(n: Int) = ChapterContent(
        title = "テスト章",
        segments = buildList {
            repeat(n) {
                add(TextSegment.Plain("　これは第${it}段落のテスト本文です。"))
                add(TextSegment.LineBreak)
            }
        }.toImmutableList(),
    )

    private fun setContent(
        topAppBarState: TopAppBarState,
        lazyListState: LazyListState,
        paragraphs: Int = 200,
        // 初回ラベル（2026-09-05 裁定・案S4 の手当）。既定は未消費＝語つきで出る。
        topPillLabelShown: Boolean = false,
        onTopPillLabelShown: () -> Unit = {},
        // 既定 D＝既存テストの前提を変えない。章末印を積むのは J だけなので、重なりを見るテストだけ
        // PORTAL_J を渡す（`ChapterContent` は `LocalSkin` を読んで J のときだけ印の item を積む）。
        skin: Skin = Skin.WAMODERN_D,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                // 束は全フィールド必須（既定値なし＝ReadingFace.kt 冒頭）。旧・既定値に頼っていた値
                //（verticalMode=false／barsVisualReady=true／chapterNumber・totalChapters・peek=null）は実値で明示する。
                ChapterScreenContent(
                    parseResult = ParseResult.Success(longChapter(paragraphs)),
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
                        lazyListState = lazyListState,
                        topAppBarState = topAppBarState,
                        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState),
                        barsVisualReady = true,
                        showChromeHint = false,
                        topPillLabelShown = topPillLabelShown,
                        onTopPillLabelShown = onTopPillLabelShown,
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
        // 後半（150番目のアイテム）から表示開始＝3割閾値（可視先頭×10 ≥ 全×3）を満たす
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list)
        makeChromeVisible(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertIsDisplayed()
        composeTestRule.onNodeWithText("最上部へ").performClick()
        composeTestRule.waitForIdle()

        // 先頭へ戻る＝出現条件（3割以上）が外れてピルも消える（完了フィードバック兼用）
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

    /**
     * 案S4 の核＝**見える器は 32dp・タップ標的は 48dp**。器を縮めた副作用で標的まで縮む退行は
     * 見た目に一切出ない（押しにくくなるだけ）ので、ここで両方の寸法を機械で固定する。
     * ⚠️ 標的 48dp は 2026-09-03 裁定の下限で、「器も標的も 34dp へ」（案S5）は規範割れとして落ちている。
     */
    @Test
    fun `見える器は32dpでもタップ標的は48dpを保つ`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        // 語を見せ切った後＝アイコンのみ＝器がいちばん小さくなる状態で測る（最悪条件）。
        setContent(topBar, list, topPillLabelShown = true)
        makeChromeVisible(topBar)

        // 標的（clickable の semantics ノード）＝48dp 角。
        composeTestRule.onNodeWithContentDescription("最上部へ")
            .assertHeightIsEqualTo(48.dp)
            .assertWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)

        // 見える器＝32dp 角（アイコン 16dp ＋ 左右 S8）。semantics へ出ないので testTag で掴む。
        composeTestRule.onNodeWithTag(ReadingTopPillFaceTag, useUnmergedTree = true)
            .assertHeightIsEqualTo(32.dp)
            .assertWidthIsEqualTo(32.dp)
    }

    /**
     * 通算初回（語つき）の器。**32dp は下限であって固定寸ではない**——11sp の字面がフォントの上下余白ごと
     * 積まれて実測 36dp になる（2026-09-05 のゲートで判明）。ここを 32dp 固定に倒すと、字を大きくした
     * 端末設定（FontLabel は sp）で語が欠けるので、伸びる側を正とした。
     * ⚠️ 上限は**外側の 48dp 等値**が担う（`assertHeightIsAtMost` は compose-ui-test に無い）。
     * 外側は器を包む `sizeIn(min=48dp)` なので、器が 48dp を超えれば外側も一緒に超える＝
     * 「48dp ちょうど」が崩れる。器が標的を押し広げ始めたら、「見える器と当たり判定を分ける」という
     * 案S4 の構造そのものが崩れている合図で、その瞬間にこのテストが落ちる。
     */
    @Test
    fun `語つきの器は32dp以上だが標的48dpの内側に収まる`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list, topPillLabelShown = false)
        makeChromeVisible(topBar)

        composeTestRule.onNodeWithText("最上部へ")
            .assertHeightIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule.onNodeWithTag(ReadingTopPillFaceTag, useUnmergedTree = true)
            .assertHeightIsAtLeast(32.dp)
    }

    /**
     * 初回ラベル（案S4 の手当）。語を見せ切った後はアイコンだけになるが、**読み上げ名は残る**。
     * ⚠️ ここが落ちるときの典型は「Text を消したのに Icon の contentDescription を null のままにした」＝
     * TalkBack から「最上部へ」が完全に消える退行で、画面を見ていても気づけない。
     */
    @Test
    fun `ラベルを見せ切った後はアイコンだけになるが読み上げ名は残る`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list, topPillLabelShown = true)
        makeChromeVisible(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("最上部へ").assertIsDisplayed()
    }

    /**
     * A1（2026-09-05 裁定）＝**スクロール中はピルだけ退場し、止まったら戻る**。
     * ⚠️ 「ピルだけ」が要点で、バーの出没（[TopAppBarState]）はここでは一切動かさない＝
     * スクロール前後で `collapsedFraction` を変えずにピルの出没だけが変わることを見る。
     */
    @Test
    fun `スクロール中はピルだけ消えて止まると戻る`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list, topPillLabelShown = true)
        makeChromeVisible(topBar)
        composeTestRule.onNodeWithContentDescription("最上部へ").assertIsDisplayed()
        val fractionBefore = composeTestRule.runOnIdle { topBar.collapsedFraction }

        // scroll {} のブロックが開いている間だけ isScrollInProgress=true＝指を置いている状態の再現。
        val holder = CoroutineScope(Dispatchers.Main)
        val scrolling = holder.launch { list.scroll { awaitCancellation() } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("最上部へ").assertDoesNotExist()
        // バーは動いていない（A1 が触るのはピルだけ＝裁定の外へ踏み出していないことの確かめ）。
        composeTestRule.runOnIdle { assertEquals(fractionBefore, topBar.collapsedFraction) }

        scrolling.cancel()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("最上部へ").assertIsDisplayed()
    }

    /**
     * ラベルを焼くのは「**出きって、そのあと画面から消えた**」時点。
     * ⚠️ 表示中に焼くと pref の反転がそのまま画面に出て、読者の目の前で語が消え器が縮む。
     * よって「出ている間はまだ焼かれていない」も同じテストで押さえる（片方だけだと逆の実装でも通る）。
     */
    @Test
    fun `初回ラベルは出きって消えた後に焼かれる`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        var burned = 0
        setContent(topBar, list, topPillLabelShown = false, onTopPillLabelShown = { burned++ })

        makeChromeVisible(topBar)
        composeTestRule.onNodeWithText("最上部へ").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, burned) }

        // クロームを畳む＝ピルが退場しきる → ここで初めて焼く。
        makeImmersive(topBar)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(1, burned) }
    }

    @Test
    fun `没入中は章の後半でもピルを出さない`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        setContent(topBar, list)
        makeImmersive(topBar)

        composeTestRule.onNodeWithText("最上部へ").assertDoesNotExist()
    }

    /**
     * 2026-09-05 実機 NG の回帰止め＝**章末印（スキンJ）とピルの矩形が交差しない**。
     *
     * ⚠️ ここが「重なった瞬間に赤くなる」唯一の形。値（[Insets.ReadingChapterEndPillClearance]）を
     * 直接見るのではなく**実際に組んだ矩形**を見るのが要点で、印の行箱・sill2・ピルの器のどれが
     * 変わっても、結果として重なれば落ちる。
     *
     * ⚠️ 印が本文の**最終アイテム**であることが再現条件（最大スクロールでそこへ着地する）。
     * 末尾 index へ送るのはこの着地状態を作るため＝末尾より先へは送れないので到達点が最大スクロールに
     * 一致する（`canScrollForward==false` で前提そのものも検査する）。
     *
     * ⚠️ 比べる相手は**透明なタップ標的（48dp）の上端**＝見える器ではない。器 32dp だけを避けると
     * 通算初回の語つき（実測 36dp）で食い込み、印の上のタップが章頭ジャンプに化ける。
     */
    @Test
    fun `スキンJ の章末印はピルの標的矩形と交差しない`() {
        val topBar = TopAppBarState(0f, 0f, 0f)
        val list = LazyListState(firstVisibleItemIndex = 150)
        // 語を見せ切った後＝アイコンのみ＝実機で重なったのと同じ姿。
        setContent(topBar, list, topPillLabelShown = true, skin = Skin.PORTAL_J)
        makeChromeVisible(topBar)

        // 末尾アイテムまで送る。話数は harness が null で渡すので印は「— 了 —」へ縮退する。
        // ⚠️ `performScrollToNode` ではダメ＝あれは「見えた時点」で止まるので、印が画面のどこに居るかが
        // スクロール量まかせになる（最初にこう書いて、印が画面中ほどでピルへ掛かった状態を測ってしまった）。
        // 裁定が対象にしているのは**最大スクロールで印が着地する位置**なので、末尾 index へ送って
        // それ以上進めないこと（canScrollForward==false）まで確かめてから測る。
        val lastIndex = composeTestRule.runOnIdle { list.layoutInfo.totalItemsCount - 1 }
        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(lastIndex)
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertFalse("最大スクロールに達していない＝この検査の前提が崩れている", list.canScrollForward)
        }

        val mark = composeTestRule.onNodeWithText(CHAPTER_END_MARK).getUnclippedBoundsInRoot()
        val target = composeTestRule.onNodeWithContentDescription("最上部へ").getUnclippedBoundsInRoot()
        val face = composeTestRule.onNodeWithTag(ReadingTopPillFaceTag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "章末印がピルの標的へ食い込んでいる（印 bottom=${mark.bottom} / 標的 top=${target.top}）",
            mark.bottom <= target.top,
        )
        // 見える器との間には (48-32)/2 = 8dp の呼吸が残る（標的を避けた副産物）。
        assertTrue(
            "章末印がピルの器へ食い込んでいる（印 bottom=${mark.bottom} / 器 top=${face.top}）",
            mark.bottom <= face.top,
        )
    }

    /**
     * クリアランスの**導き方**そのものを固定する（上のレイアウトテストが見るのは結果の矩形だけで、
     * 「なぜ 60dp なのか」は見ていない）。ピルの標的が広がったのにクリアランスが据え置かれる、という
     * 片側だけの改訂をここで落とす。
     *
     * ⚠️ 器（[TopPillVisualHeight]）ではなく標的（[TopPillTouchTarget]）から導く＝器は `heightIn(min=)` の
     * 下限で語つきは 36dp まで伸びるため、器基準の値は初回表示だけ食い込む。
     */
    @Test
    fun `章末クリアランスはピルの浮き＋タップ標的から導かれている`() {
        assertEquals(Spacing.S12 + TopPillTouchTarget, Insets.ReadingChapterEndPillClearance)
    }
}

/** 話数不明の章末印（[com.novelreader.ui.skins.j.ChapterEndMarkJ] の縮退形）。 */
private const val CHAPTER_END_MARK = "— 了 —"
