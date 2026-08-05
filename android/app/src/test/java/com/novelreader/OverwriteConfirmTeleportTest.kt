package com.novelreader

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
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
 * 上書き確認テレポート（背面 PDF 取込の「取込済み」通知タップ→上書き確認ダイアログ・U1 残り 2026-08-06）の
 * 契約テスト。固定する契約:
 *   ① Intent 往復: Service が積む [OverwriteConfirmTeleport.launchIntent] を MainActivity 側の
 *      [OverwriteConfirmTeleport.isRequested] が必ず拾う（キーは object 内 private＝この往復だけが正本）。
 *   ② 無関係 Intent の不干渉: launcher・変換完了 deep link（EXTRA_BOOK_ID）・共有取込（SEND）では
 *      false＝他の消費流儀の保留を潰さない。
 *   ③ 着地: 旗が立つと深い画面（reading）を畳み、Pager が他タブに居ても本棚ページへスナップして
 *      消費（false 戻し）される（[OverwriteConfirmLandingEffect]）。ダイアログ本体は
 *      BookshelfViewModel.overwritePrompt の状態駆動（BookshelfViewModelTest が担保）＝本テストは
 *      「ホスト（本棚ページ）が compose される場所まで届くこと」を固定する。
 *   ④ cold start 形（スタック＝tabs のみ・page 0）でも同じ1実装が no-op 着地で安全に消費される。
 *
 * NavHost は ReadingEscapeNavigationTest と同型の最小トポロジ（TAB_HOST_ROUTE 起点＋reading 上積み）を
 * 共有定数で組む＝プロダクションのルート名変更にテストが同時追従する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverwriteConfirmTeleportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ── ①② Intent 契約 ─────────────────────────────────────────────

    @Test
    fun launchIntent_isPickedUpByIsRequested_roundTrip() {
        // Service 側（launchIntent）と MainActivity 側（isRequested）が同じキーで結ばれている往復契約。
        assertTrue(OverwriteConfirmTeleport.isRequested(OverwriteConfirmTeleport.launchIntent(context)))
    }

    @Test
    fun launchIntent_targetsMainActivity_withSingleTopClearTop() {
        val intent = OverwriteConfirmTeleport.launchIntent(context)
        // 明示 component: launcher 解決に依存せず必ず MainActivity（intent 受け口）へ届く。
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        // singleTop 経路（稼働中は onNewIntent）と多重起動回避の型（openBookIntent と同じフラグ組）。
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun isRequested_isFalse_forUnrelatedIntents() {
        // null（intent 無し）。
        assertFalse(OverwriteConfirmTeleport.isRequested(null))
        // launcher 起動。
        assertFalse(
            OverwriteConfirmTeleport.isRequested(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ),
        )
        // 変換完了通知の deep link（EXTRA_BOOK_ID）＝別の消費流儀。旗と誤認して本棚へ引き戻さない。
        assertFalse(
            OverwriteConfirmTeleport.isRequested(
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_BOOK_ID, "b1"),
            ),
        )
        // 共有取込（ACTION_SEND）＝pendingWebImportUrl の流儀。
        assertFalse(
            OverwriteConfirmTeleport.isRequested(
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://example.com"),
            ),
        )
    }

    // ── ③④ 着地契約 ────────────────────────────────────────────────

    private lateinit var navController: NavHostController
    private lateinit var pager: PagerState
    private var requested by mutableStateOf(false)

    /** MainActivity と同型の最小トポロジ（tabs 起点＋reading 上積み可能）＋実物の着地エフェクト。 */
    private fun setUpNav(initialTabPage: Int, pushReading: Boolean) {
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
            // プロダクションの結線そのもの（NovelReaderApp が呼ぶのと同じ引数形）を検証対象にする。
            OverwriteConfirmLandingEffect(
                requested = requested,
                navController = navController,
                tabPagerState = pager,
                onConsumed = { requested = false },
            )
        }
        composeTestRule.waitForIdle()
        if (pushReading) {
            composeTestRule.runOnIdle { navController.navigate("reading/b1/index.html") }
            composeTestRule.waitForIdle()
        }
    }

    private fun currentRoute(): String? =
        composeTestRule.runOnIdle { navController.currentBackStackEntry?.destination?.route }

    @Test
    fun landing_fromDeepScreen_onOtherTab_reachesBookshelfPage_andConsumes() {
        // ③ 最悪形: 深い画面（reading）が前面・Pager は設定タブ＝ダイアログのホストから二重に遠い。
        setUpNav(initialTabPage = 2, pushReading = true)
        assertEquals("reading/{bookId}/{startFile}", currentRoute())
        // 通知タップ（onNewIntent 相当）＝旗を立てる。
        composeTestRule.runOnIdle { requested = true }
        composeTestRule.waitForIdle()
        assertEquals("深い画面を畳んでタブ層へ", TAB_HOST_ROUTE, currentRoute())
        assertEquals("本棚ページ（ダイアログのホスト）へスナップ", 0, composeTestRule.runOnIdle { pager.currentPage })
        assertFalse("着地後に消費（再発火防止）", composeTestRule.runOnIdle { requested })
    }

    @Test
    fun landing_notRequested_doesNotNavigate() {
        // 旗が立っていない通常運転では何も畳まない（読書中の深い画面を勝手に壊さない）。
        setUpNav(initialTabPage = 0, pushReading = true)
        composeTestRule.waitForIdle()
        assertEquals("reading/{bookId}/{startFile}", currentRoute())
    }

    @Test
    fun landing_coldStartShape_isNoOpButConsumed() {
        // ④ cold start 形: スタック＝tabs のみ・page 0 で初回コンポーズから旗が立っている。
        // pop もスナップも no-op で例外なく着地し、旗は消費される。
        requested = true
        setUpNav(initialTabPage = 0, pushReading = false)
        assertEquals(TAB_HOST_ROUTE, currentRoute())
        assertEquals(0, composeTestRule.runOnIdle { pager.currentPage })
        assertFalse(composeTestRule.runOnIdle { requested })
    }
}
