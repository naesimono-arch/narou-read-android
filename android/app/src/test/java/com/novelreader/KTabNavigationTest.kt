package com.novelreader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun backOnBookshelfPage_isNotConsumed() {
        // page 0 では BackHandler が enabled=false＝Dispatcher に有効コールバックが無い
        // （＝システム既定のアプリ退出へ素通しされる）ことを固定する。
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
        setUpTabs(initialPage = 0)
        composeTestRule.onNodeWithText("SHELF").assertIsDisplayed()
    }
}
