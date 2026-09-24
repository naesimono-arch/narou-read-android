package com.novelreader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.ui.skins.k.KTab
import com.novelreader.ui.tabs.TabPagerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * タブ層（[TabPagerHost]）の契約テスト。
 *
 * 2026-07-24 のタブ Pager 化（横スワイプ・ADR 0022 スロット契約）で、タブ切替は NavHost ルート入替
 * （旧 navigateKTab）から Pager のページ切替へ移行した。旧テストが固定していた「さがす→本棚が確実に戻る」
 * レース（currentBackStackEntryAsState の DROP_OLDEST 由来）は、タブがナビゲーションでなくなったことで
 * 機構ごと消滅＝本テストは新契約を固定する:
 *   ① Back の階層 up 契約＝page 0 以外での Back は本棚（page 0）へ戻す
 *   ② page 0 では Back を消費しない（Activity 既定＝アプリ退出へ委ねる）
 *   ③ スロット index と KTab.ordinal の対応（本棚0/さがす1/設定2）でページが描画される
 *   ④ **page 0 から移動した後**も ①が成り立つ（2026-08-14 実機バグ①の再発防止。initialPage 固定の
 *      ①②だけでは「起動時 page 0 →タブ移動」という実アプリ唯一の経路が素通しになる）
 * 同一タブ再タップの no-op は animateScrollToPage(同ページ) の標準挙動＝Pager 側の契約として固定不要。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KTabNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var pager: PagerState

    private fun setUpTabs(initialPage: Int) {
        composeTestRule.setContent {
            pager = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
            TabPagerHost(
                pagerState = pager,
                pages = listOf(
                    { Text("SHELF") },
                    { Text("DISC") },
                    { Text("SET") },
                ),
            )
        }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun backOnDiscoverPage_returnsToBookshelfPage() {
        // 旧契約「さがす→本棚が確実に戻る」の継承形＝Back の階層 up。
        setUpTabs(initialPage = 1)
        composeTestRule.onNodeWithText("DISC").assertIsDisplayed()
        pressBack()
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
        composeTestRule.onNodeWithText("SHELF").assertIsDisplayed()
    }

    @Test
    fun backOnSettingsPage_returnsToBookshelfPage() {
        // 設定（末尾ページ）からも「家」は本棚＝隣の「さがす」でなく page 0 へ直行する契約。
        setUpTabs(initialPage = 2)
        composeTestRule.onNodeWithText("SET").assertIsDisplayed()
        pressBack()
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun backAfterMovingAwayFromHomePage_isConsumedAndReturnsHome() {
        // 実アプリの経路（起動＝page 0 →タブ移動）を通す唯一のテスト。既存3本は initialPage で
        // 「最初から page 0 以外」に置いており、**page 0 から移った後**に Back 割込みが立つ経路が
        // 素通しだった＝2026-08-14 実機バグ①（設定タブの Back でアプリ終了）が緑のまま抜けた穴。
        // ここが固定するのは「移動後に有効コールバックが在り、Back が page 0 へ戻す」ことだけ。
        // ⚠️ **このテストは旧実装（enabled 反転形）でも3アサートとも緑になる**＝真因の再発防止には
        // なっていない。JVM/Robolectric は onBackPressedDispatcher を直接叩くため deque の選択しか
        // 観測できず、真因である**OS の OnBackInvokedDispatcher への登録**は観測できないため。
        // 再発防止の番人は形で止める側＝HazardousPatternScanTest の
        // 「BackHandler は enabled の反転でなくコンポーズの有無で表す」が持つ（実機確認も別途必要）。
        setUpTabs(initialPage = 0)
        composeTestRule.runOnIdle { pager.requestScrollToPage(2) }
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.runOnIdle {
                composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
            },
        )
        pressBack()
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun backOnBookshelfPage_isNotConsumed() {
        // page 0 では枠の BackHandler がそもそもコンポーズされない＝Dispatcher に有効コールバックが無い
        // （＝システム既定のアプリ退出へ素通しされ、Predictive Back の「ホームへ戻る」プレビューが効く）
        // ことを固定する。旧実装の enabled=false と観測値は同じで、違いは OS への登録が残らないこと。
        setUpTabs(initialPage = 0)
        assertFalse(
            composeTestRule.runOnIdle {
                composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
            },
        )
    }

    @Test
    fun backHandlerEnabled_onNonHomePages() {
        // ①の裏面＝page 0 以外では Back を枠が受ける（有効コールバックが在る）。
        setUpTabs(initialPage = 1)
        assertTrue(
            composeTestRule.runOnIdle {
                composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
            },
        )
    }

    @Test
    fun pages_renderBySlotIndex() {
        // スロット index=KTab.ordinal（本棚0/さがす1/設定2）の対応でページが描かれる。
        // ordinal を直接固定するのは、枠側（TabPagerHost）が「家＝page 0」を数値で持つため
        //（HOME_TAB_PAGE。枠に KTab を持ち込まない ADR 0022 の規律の代償として、一致はここで固定する）。
        // KTab の並べ替えでこの assert が落ちたら、枠の HOME_TAB_PAGE も併せて直すこと。
        assertEquals(0, KTab.BOOKSHELF.ordinal)
        setUpTabs(initialPage = 0)
        composeTestRule.onNodeWithText("SHELF").assertIsDisplayed()
    }
}
