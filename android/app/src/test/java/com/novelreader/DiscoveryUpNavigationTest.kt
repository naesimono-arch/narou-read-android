package com.novelreader

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 発見フロー（結果一覧・作品詳細）の「階層 up 一本化」の契約テスト。
 *
 * 2026-07-29 ユーザー裁定「わかりやすく」＝旧「←＝発見ホーム固定 Up／システム Back＝履歴 pop」の
 * 二本立て（D 統一 2026-07-12）を廃止し、読書側（章→目次→本棚・2026-07-23 統一）と同じ
 * 「← も Back も階層を1段上がる」一規則へ統一した（ADR 0026）。
 * 発見の階層＝〈発見ホーム（さがすタブ）→ 結果一覧 → 作品詳細〉。検索/ジャンル画面は結果の親でなく
 * 「条件編集の横道」＝up はそれらを飛ばして畳む（検索画面へ戻るのは「条件を変更」の明示導線のみ）。
 *
 * 固定する契約:
 *   ① 結果一覧の up（←・Back 共通）＝発見ホーム。検索/ジャンルが下に残っていても飛ばして畳み、
 *      Pager は「さがす」ページへスナップ（deep link 相当で他タブに居ても着地が化けない）。
 *   ② 作品詳細の up（←・Back 共通）＝直近の結果一覧。発見ホーム直行入場（直下がタブ層）だけは
 *      発見ホームへ＝スナップ込み。詳細→結果一覧→ホームの2段で必ず上がりきる。
 *   ③ 機序の固定: 消えたルート名への pop は黙って無視され現在地が動かない（2026-07-27 バグの再現。
 *      popToTab へのリテラル封鎖を守り続ける理由）。
 *   ④ キーワード再検索は「タブ層より上を畳んでから result を1枚」＝経路に依らず [tabs, result]。
 *      結果一覧・詳細が多段に重ならないこの畳みが、②の「1 pop＝常に一段上」と「再検索の重なりは
 *      同じ結果一覧段（up は履歴を全部は遡らない）」を機構的に保証する。
 *   ⑤ システム Back は ← と同一の up 関数を通る（Back ディスパッチ経由で①②と同じ着地になること）。
 *
 * NavHost は MainActivity と同型の最小トポロジを共有定数 [TAB_HOST_ROUTE] で組み、Back 側は
 * MainActivity の BackHandler 配線をミラーして本物の up 関数（[popToTab]/[upFromDiscoveryDetail]）を
 * 叩く＝プロダクションのルート名・up 実装が変わればここも同時に落ちる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryUpNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState

    /**
     * MainActivity と同型の最小 NavHost（tabs 起点＋発見の深い画面が上に積まれる）。
     * result/detail の BackHandler は MainActivity の配線ミラー＝「Back も同じ up 関数」（契約⑤）。
     */
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
                composable("discovery/result") {
                    BackHandler { popToTab(navController, pager, KTab.DISCOVER) }
                    Text("RESULT")
                }
                composable("discovery/detail/{ncode}") {
                    BackHandler { upFromDiscoveryDetail(navController, pager) }
                    Text("DETAIL")
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun navigate(route: String) {
        composeTestRule.runOnIdle { navController.navigate(route) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? =
        composeTestRule.runOnIdle { navController.currentBackStackEntry?.destination?.route }

    private fun previousRoute(): String? =
        composeTestRule.runOnIdle { navController.previousBackStackEntry?.destination?.route }

    private fun currentPage(): Int = composeTestRule.runOnIdle { pager.currentPage }

    /** 代表導線: さがす → 検索 → 結果一覧 → 作品詳細。 */
    private fun enterDetailViaSearch() {
        navigate("discovery/search")
        navigate("discovery/result")
        navigate("discovery/detail/n1234ab")
    }

    @Test
    fun upFromResult_skipsSearch_landsOnDiscoverHome() {
        // ① 検索経由の結果一覧でも、up は横道（検索画面）を飛ばして一段上の親＝発見ホームへ畳む。
        setUpNav()
        navigate("discovery/search")
        navigate("discovery/result")
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.DISCOVER) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.DISCOVER.ordinal, currentPage())
        assertNull("検索画面も畳まれてタブ層が最上段に残ること", previousRoute())
    }

    @Test
    fun upFromDetail_viaResult_landsOnRecentResult() {
        // ② 結果一覧経由の作品詳細の up は一段上＝直近の結果一覧（旧・発見ホーム固定 Up の廃止）。
        setUpNav()
        enterDetailViaSearch()
        assertEquals("discovery/detail/{ncode}", currentRoute())
        composeTestRule.runOnIdle { upFromDiscoveryDetail(navController, pager) }
        composeTestRule.waitForIdle()
        assertEquals("詳細の一段上は直近の結果一覧", "discovery/result", currentRoute())
        // 続けて up すれば発見ホーム＝〈詳細→結果一覧→ホーム〉の2段で必ず上がりきる。
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.DISCOVER) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun upFromDetail_directEntry_snapsPagerToDiscover() {
        // ② の直行入場枝: 直下がタブ層の詳細（発見ホーム直・deep link 相当）は一段上＝発見ホーム。
        //    Pager が本棚ページに居ても pop だけでは本棚に化けるため、スナップ込み（popToTab）で受ける。
        setUpNav(initialTabPage = KTab.BOOKSHELF.ordinal)
        navigate("discovery/detail/n1234ab")
        composeTestRule.runOnIdle { upFromDiscoveryDetail(navController, pager) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("他タブに居てもさがすページへスナップ", KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun backFromResult_dispatch_landsOnDiscoverHome() {
        // ⑤ システム Back も ← と同一の up（BackHandler ミラー経由）: 結果一覧の Back は
        //    旧・履歴 pop（検索画面へ落ちる）ではなく発見ホームへ。
        setUpNav()
        navigate("discovery/search")
        navigate("discovery/result")
        pressBack()
        assertEquals("Back は検索画面でなく発見ホームへ", TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun backFromDetail_dispatch_walksHierarchy_resultThenHome() {
        // ⑤ 作品詳細からの Back 連打は階層を1段ずつ: 詳細 → 直近の結果一覧 → 発見ホーム。
        setUpNav()
        enterDetailViaSearch()
        pressBack()
        assertEquals("1回目の Back は直近の結果一覧へ", "discovery/result", currentRoute())
        pressBack()
        assertEquals("2回目の Back で発見ホームへ", TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.DISCOVER.ordinal, currentPage())
    }

    @Test
    fun popToRemovedDiscoveryRoute_isSilentlyIgnored_2026_07_27_bugMechanism() {
        // ③ 機序を固定: タブ Pager 化で消えたルート "discovery" への pop は例外を出さず
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
        //    この畳みが「result/detail は多段に重ならない」を保証し、②の up（1 pop＝一段上）と
        //    「再検索の重なりは同じ結果一覧段」の裁定を機構的に支える。
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
            previousRoute(),
        )
    }
}
