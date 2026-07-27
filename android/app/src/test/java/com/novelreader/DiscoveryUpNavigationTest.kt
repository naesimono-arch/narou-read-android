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
import com.novelreader.ui.skins.k.KTab
import com.novelreader.ui.tabs.TabPagerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 発見フロー（結果一覧・作品詳細）の固定 Up（[popToTab] × [KTab.DISCOVER]）の契約テスト。
 *
 * 2026-07-27 実機バグ「検索後、作品を取り込む導線で ← が反応しない」の再発防波堤。
 * 真因は [ReadingEscapeNavigationTest] が固定した 07-25 の目次バグと同一機序＝K タブ Pager 化
 * （2026-07-24・ADR 0022）でルート "discovery" が消えたのに `popBackStack("discovery", false)` が
 * 残留し、pop が false で黙殺されて ← が完全に無反応になっていた（例外は出ずコンパイルも通る）。
 *
 * 固定する契約:
 *   ① 結果一覧・作品詳細どちらからでも Up はタブ層へ着地し、Pager は「さがす」ページを指す。
 *   ② Pager が他タブに居ても（deep link 入場相当）「さがす」へスナップする＝経路非依存の固定 Up。
 *   ③ 機序の固定: 消えたルート名への pop は黙って無視され現在地が動かない（＝バグの再現）。
 *   ④ キーワード再検索は「タブ層より上を畳んでから result を1枚」＝経路に依らず [tabs, result]。
 *
 * NavHost は MainActivity と同型の最小トポロジを共有定数 [TAB_HOST_ROUTE] で組む
 * ＝プロダクションのルート名が変われば本テストも同時に追従する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryUpNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState

    /** MainActivity と同型の最小 NavHost（tabs 起点＋発見の深い画面が上に積まれる）。 */
    private fun setUpNav(initialTabPage: Int = KTab.DISCOVER.ordinal) {
        composeTestRule.setContent {
            navController = rememberNavController()
            pager = rememberPagerState(initialPage = initialTabPage, pageCount = { KTab.entries.size })
            NavHost(navController = navController, startDestination = TAB_HOST_ROUTE) {
                composable(TAB_HOST_ROUTE) {
                    TabPagerHost(
                        pagerState = pager,
                        pages = listOf({ Text("SHELF") }, { Text("DISC") }, { Text("SET") }),
                    )
                }
                composable("discovery/search") { Text("SEARCH") }
                composable("discovery/result") { Text("RESULT") }
                composable("discovery/detail/{ncode}") { Text("DETAIL") }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun navigate(route: String) {
        composeTestRule.runOnIdle { navController.navigate(route) }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? =
        composeTestRule.runOnIdle { navController.currentBackStackEntry?.destination?.route }

    private fun currentPage(): Int = composeTestRule.runOnIdle { pager.currentPage }

    /** 実機で報告された導線: さがす → 検索 → 結果一覧 → 作品詳細（ここから取り込みへ進む）。 */
    private fun enterDetailViaSearch() {
        navigate("discovery/search")
        navigate("discovery/result")
        navigate("discovery/detail/n1234ab")
    }

    @Test
    fun upFromDetail_landsOnDiscoverTab() {
        // ① 検索経由で深く潜った作品詳細から、← が一段上の親（発見ホーム＝さがすタブ）へ確実に抜ける。
        setUpNav()
        enterDetailViaSearch()
        assertEquals("discovery/detail/{ncode}", currentRoute())
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.DISCOVER) }
        composeTestRule.waitForIdle()
        assertEquals("← は有効な pop としてタブ層へ戻ること", TAB_HOST_ROUTE, currentRoute())
        assertEquals("着地はさがすページ", KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun upFromResult_landsOnDiscoverTab() {
        // ① の結果一覧版（検索画面が下に残っていても発見ホームまで一気に上がる＝固定 Up）。
        setUpNav()
        navigate("discovery/search")
        navigate("discovery/result")
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.DISCOVER) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun upFromDetail_snapsPagerToDiscover_evenFromOtherTabPage() {
        // ② Pager が本棚ページに居る状態（deep link・共有取込からの入場相当）でも、
        //    発見の ← 契約は「発見ホームへ」＝pop だけでは本棚に化けるためスナップが要る。
        setUpNav(initialTabPage = KTab.BOOKSHELF.ordinal)
        navigate("discovery/detail/n1234ab")
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.DISCOVER) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("他タブに居てもさがすページへスナップ", KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun popToRemovedDiscoveryRoute_isSilentlyIgnored_2026_07_27_bugMechanism() {
        // ③ 真因の機序を固定: タブ Pager 化で消えたルート "discovery" への pop は例外を出さず
        //    false で無視され、ユーザーは作品詳細に留め置かれる（＝押しても何も起きない ←）。
        setUpNav()
        enterDetailViaSearch()
        val popped = composeTestRule.runOnIdle { navController.popBackStack("discovery", false) }
        composeTestRule.waitForIdle()
        assertFalse("消えたルートへの pop は黙って無視される（バグの機序）", popped)
        assertEquals("現在地が動かない＝無反応の再現", "discovery/detail/{ncode}", currentRoute())
    }

    @Test
    fun keywordResearch_collapsesToSingleResultAboveTabHost() {
        // ④ キーワード再検索（作品詳細のキーワードタップ）は、経路に依らず [tabs, result] へ畳む。
        //    旧 popUpTo("discovery") は消えたルート指定で畳みが無言で効かず、
        //    detail の下に古い result が残ってスタックが経路依存に割れていた。
        setUpNav()
        enterDetailViaSearch()
        composeTestRule.runOnIdle {
            navController.navigate("discovery/result") {
                launchSingleTop = true
                popUpTo(TAB_HOST_ROUTE) { inclusive = false }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals("discovery/result", currentRoute())
        assertEquals(
            "result の1つ下は必ずタブ層（旧 result・detail・search は畳まれている）",
            TAB_HOST_ROUTE,
            composeTestRule.runOnIdle { navController.previousBackStackEntry?.destination?.route },
        )
    }
}
