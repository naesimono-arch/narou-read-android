package com.novelreader

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * K恒常ナビのタブ遷移 [navigateKTab] の回帰テスト。
 *
 * なぜ固定するか: 「さがす→本棚に稀に遷移できない」報告への防御的是正で、遷移可否の判定を
 * スナップショット currentRoute（collectAsState/DROP_OLDEST で遅延・drop しうる）から
 * ライブの currentDestination へ移した（[navigateKTab] KDoc 参照）。本テストは実 NavController で
 * ①報告された「さがす→本棚」往復が確実に戻ること ②3タブが相互到達可能なこと
 * ③同一タブ再タップが no-op（スタック不変）で挙動不変なこと、を固定する。
 * 注意: 元症状はフレーム競合起因で決定的な再現は不能。本テストは是正後の意図された遷移契約を固定する
 * ものであり、旧実装のレース自体を再現するものではない。
 *
 * 全スキン化（2026-07-23）: 恒常ボトムナビと設定タブを MEIKAI_K 限定から D/C/M/P/J へ一般化した。
 * [navigateKTab] はスキン非依存（引数は navController と route のみ・分岐に skin を持たない）＝タブ往復契約は
 * どのスキンでも同一。よって本テストの遷移契約はそのまま全スキンの契約として成立する（[settingsTab_reachableAndRoundTrips]）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KTabNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUpNav() {
        composeTestRule.setContent {
            nav = rememberNavController()
            // 本番と同じ start=bookshelf の3タブ最小グラフ（popUpTo("bookshelf") が解決可能な構造）。
            NavHost(nav, startDestination = "bookshelf") {
                composable("bookshelf") { Text("SHELF") }
                composable("discovery") { Text("DISC") }
                composable("settings") { Text("SET") }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun route(): String? = composeTestRule.runOnIdle { nav.currentDestination?.route }
    private fun backSize(): Int = composeTestRule.runOnIdle { nav.currentBackStack.value.size }
    private fun tap(route: String) {
        composeTestRule.runOnIdle { navigateKTab(nav, route) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun discoveryToBookshelf_roundTrip_alwaysLands() {
        setUpNav()
        assertEquals("bookshelf", route())
        tap("discovery")
        assertEquals("discovery", route())
        // 報告された経路: さがす→本棚。是正後は確実に本棚へ戻る。
        tap("bookshelf")
        assertEquals("bookshelf", route())
    }

    @Test
    fun allThreeTabs_mutuallyReachable() {
        setUpNav()
        tap("discovery")
        assertEquals("discovery", route())
        tap("settings")
        assertEquals("settings", route())
        tap("bookshelf")
        assertEquals("bookshelf", route())
    }

    @Test
    fun settingsTab_reachableAndRoundTrips() {
        // 恒常ナビの全スキン化（2026-07-23）で設定タブは全スキンから到達可能になった（旧: MEIKAI_K 限定）。
        // 設定へ直接入り本棚へ確実に戻れることを固定する＝新たに一般化された目的地の往復契約。
        // navigateKTab はスキン非依存のため、この契約は非Kスキン（D/C/M/P/J）でもそのまま成立する。
        setUpNav()
        assertEquals("bookshelf", route())
        tap("settings")
        assertEquals("settings", route())
        tap("bookshelf")
        assertEquals("bookshelf", route())
    }

    @Test
    fun reTapCurrentTab_isNoOp_stackUnchanged() {
        setUpNav()
        tap("discovery")
        assertEquals("discovery", route())
        val before = backSize()
        // 同一タブ再タップ: ライブ判定でも早期 return＝スタックを増やさない（挙動不変を固定）。
        tap("discovery")
        assertEquals("discovery", route())
        assertEquals(before, backSize())
    }
}
