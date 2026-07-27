package com.novelreader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
 * 読書フロー脱出（[popToBookshelfTab]）の契約テスト。
 *
 * 固定する契約（Back 統一裁定 2026-07-19「目次→本棚」× K タブ構造 2026-07-24）:
 *   ① 目次で back()==null になった後の脱出が「実際に有効な pop」であり、タブ層＝本棚ページへ着地する。
 *   ② deep link 入場等で Pager が他タブに居ても、脱出は本棚ページへスナップする（pop だけだと
 *      「目次→さがす/設定」に化ける）。
 *   ③ バグ機序の固定: スタックに無いルート名への popBackStack は黙って無視される（false・現在地不動）
 *      ＝2026-07-25 実機バグ（旧 "bookshelf" への pop が黙殺され目次に幽閉）の再発防波堤。
 *      pop 先とルート登録は [TAB_HOST_ROUTE] の単一正本共有で乖離を封じる。
 *
 * NavHost は MainActivity と同型の最小トポロジ（TAB_HOST_ROUTE 起点＋reading が上に積まれる）を
 * 共有定数で組む＝プロダクションのルート名が変わればこのテストも同時に追従する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingEscapeNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState

    /** MainActivity と同型の最小 NavHost（tabs 起点・reading 1枚上積み）。tabs 内は実物の TabPagerHost。 */
    private fun setUpNav(initialTabPage: Int) {
        composeTestRule.setContent {
            navController = rememberNavController()
            pager = rememberPagerState(initialPage = initialTabPage, pageCount = { 3 })
            NavHost(navController = navController, startDestination = TAB_HOST_ROUTE) {
                composable(TAB_HOST_ROUTE) {
                    TabPagerHost(
                        pagerState = pager,
                        pages = listOf({ Text("SHELF") }, { Text("DISC") }, { Text("SET") }),
                    )
                }
                composable("reading/{bookId}/{startFile}") { Text("READING") }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { navController.navigate("reading/b1/index.html") }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? =
        composeTestRule.runOnIdle { navController.currentBackStackEntry?.destination?.route }

    @Test
    fun escapeFromReading_popsToTabHost_onBookshelfPage() {
        // ① 通常入場（本棚ページから読書へ）: 脱出でタブ層へ戻り本棚ページのまま。
        setUpNav(initialTabPage = 0)
        assertEquals("reading/{bookId}/{startFile}", currentRoute())
        composeTestRule.runOnIdle { popToBookshelfTab(navController, pager) }
        composeTestRule.waitForIdle()
        assertEquals("脱出は有効な pop としてタブ層へ戻ること", TAB_HOST_ROUTE, currentRoute())
        assertEquals("着地は本棚ページ", 0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun escapeFromReading_snapsPagerToBookshelf_evenFromOtherTabPage() {
        // ② deep link 入場相当: Pager が設定タブに居ても契約は「目次→本棚」＝pop＋スナップで本棚着地。
        setUpNav(initialTabPage = 2)
        composeTestRule.runOnIdle { popToBookshelfTab(navController, pager) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("他タブに居ても本棚ページへスナップ", 0, composeTestRule.runOnIdle { pager.currentPage })
    }

    @Test
    fun popToMissingRoute_isSilentlyIgnored_2026_07_25_bugMechanism() {
        // ③ 真因の機序を固定: スタックに無いルートへの pop は例外を出さず false で無視され、
        //    ユーザーは現在画面（目次）に幽閉される。だから pop 先は TAB_HOST_ROUTE 定数共有が必須。
        setUpNav(initialTabPage = 0)
        val popped = composeTestRule.runOnIdle { navController.popBackStack("bookshelf", false) }
        composeTestRule.waitForIdle()
        assertFalse("スタックに無いルートへの pop は黙って無視される（バグの機序）", popped)
        assertEquals("現在地が動かない＝幽閉の再現", "reading/{bookId}/{startFile}", currentRoute())
        // 対して正しい脱出は同じ状態から必ず成功する（①との対比で機序を1テスト内でも可視化）。
        composeTestRule.runOnIdle { popToBookshelfTab(navController, pager) }
        composeTestRule.waitForIdle()
        assertTrue("正規の脱出後はタブ層", currentRoute() == TAB_HOST_ROUTE)
    }
}
