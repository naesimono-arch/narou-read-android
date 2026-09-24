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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 診断の記録（書き出し）画面の「階層 up 一本化」の契約テスト（ADR 0026 を新ルートへ適用）。
 *
 * この画面は設定タブ「データ」節の1行からだけ push される深い画面で、一段上は常に**設定タブ**。
 * 固定する契約:
 *   ① ←（[DIAGNOSTICS_EXPORT_ROUTE] の up 関数）の着地＝タブ層・設定ページ。
 *   ② システム Back も ← と同一の up を通る（MainActivity の BackHandler ミラー経由で①と同じ着地）。
 *   ③ Pager が他タブに居ても設定ページへ**スナップ**する。素の popBackStack だとタブ層へ戻るだけで
 *      着地が本棚ページに化けるため、[popToTab] を通すことがこの契約の実体
 *      （深い画面からの復帰・deep link 相当でしか起きないが、起きたときに黙って壊れる型）。
 *   ④ 設定タブへ上がりきった後は**タブ層の Back 契約へ戻る**（もう一度 Back で本棚ページ）＝
 *      この画面の BackHandler が居残って Back を食い続けない。
 *
 * NavHost は MainActivity と同型の最小トポロジを共有定数（[TAB_HOST_ROUTE]・[DIAGNOSTICS_EXPORT_ROUTE]）で
 * 組み、Back 側は MainActivity の配線をミラーして本物の up 実装（[popToTab]）を叩く
 * ＝プロダクションのルート名・up 実装が変わればここも同時に落ちる（[DiscoveryUpNavigationTest] と同流儀）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsExportUpNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState

    /** MainActivity と同型の最小 NavHost（tabs 起点＋診断の記録が上に積まれる）。 */
    private fun setUpNav(initialTabPage: Int = KTab.SETTINGS.ordinal) {
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
                composable(DIAGNOSTICS_EXPORT_ROUTE) {
                    // MainActivity の配線ミラー＝「Back も ← と同じ up 関数」（契約②）。
                    BackHandler { popToTab(navController, pager, KTab.SETTINGS) }
                    Text("DIAG")
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openDiagnosticsExport() {
        composeTestRule.runOnIdle { navController.navigate(DIAGNOSTICS_EXPORT_ROUTE) }
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

    @Test
    fun upFromDiagnosticsExport_landsOnSettingsTab() {
        // ① ← の着地＝タブ層・設定ページ（設定から1段深いので、一段上は必ず設定）。
        setUpNav()
        openDiagnosticsExport()
        assertEquals(DIAGNOSTICS_EXPORT_ROUTE, currentRoute())
        composeTestRule.runOnIdle { popToTab(navController, pager, KTab.SETTINGS) }
        composeTestRule.waitForIdle()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.SETTINGS.ordinal, currentPage())
        assertNull("タブ層が最上段＝上に何も残っていないこと", previousRoute())
    }

    @Test
    fun backFromDiagnosticsExport_dispatch_landsOnSettingsTab() {
        // ② システム Back も ← と同一の up（BackHandler ミラー経由）＝着地は①と一致する。
        setUpNav()
        openDiagnosticsExport()
        pressBack()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(KTab.SETTINGS.ordinal, currentPage())
    }

    @Test
    fun upFromDiagnosticsExport_snapsPagerToSettings_whenPagerElsewhere() {
        // ③ Pager が本棚ページに居ても設定ページへスナップする。素の pop だと本棚に化ける＝
        //    popToTab を通していることの実体的な証拠（この差はここでしか見えない）。
        setUpNav(initialTabPage = KTab.BOOKSHELF.ordinal)
        openDiagnosticsExport()
        pressBack()
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals("他タブに居ても設定ページへスナップ", KTab.SETTINGS.ordinal, currentPage())
    }

    @Test
    fun backAfterUp_returnsToBookshelfPage_tabContractResumes() {
        // ④ 上がりきった後はタブ層の Back 契約（ADR 0022 追記・ADR 0026 追記 2026-08-14）へ戻る＝
        //    この画面の BackHandler が居残って Back を食い続けない。連打で 診断→設定→本棚 と1段ずつ上がる。
        setUpNav()
        openDiagnosticsExport()
        pressBack()
        assertEquals(KTab.SETTINGS.ordinal, currentPage())
        pressBack()
        assertEquals("設定ページからの Back はタブ層の契約どおり本棚ページへ", KTab.BOOKSHELF.ordinal, currentPage())
        assertEquals(TAB_HOST_ROUTE, currentRoute())
    }
}
