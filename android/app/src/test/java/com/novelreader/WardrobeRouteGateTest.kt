package com.novelreader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 装いの間ルート "wardrobe" の公開スコープ機能ゲート（ADR 0027 適用点2）＝[wardrobeRoute] の登録可否。
 *
 * 何を守るか: 入口（設定「きせかえ」行）を隠しても、ルートが生きていれば deep link・復元・将来の
 * navigate 追加で到達できてしまう。「公開ビルドではグラフに存在しない」ことを両値で固定する。
 *
 * なぜ本物の NavHost（[MainActivity] の NovelReaderApp）で検証しないか: あちらは ViewModel を要求する塊で
 * JVM から組めない。ゆえに判定だけを [wardrobeRoute] へ切り出し、最小の NavHost へ載せて graph を直接問う。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WardrobeRouteGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 最小 NavHost へ [wardrobeRoute] だけを載せ、グラフに "wardrobe" 目的地が在るかを返す。 */
    private fun wardrobeDestination(skinSwitchingEnabled: Boolean): NavDestination? {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {}
                wardrobeRoute(skinSwitchingEnabled) {}
            }
        }
        return composeTestRule.runOnIdle { navController.graph.findNode("wardrobe") }
    }

    @Test
    fun `ゲートon(開発ビルド)＝装いの間ルートが登録される`() {
        assertNotNull("開発ビルドで装いの間へ行けなくなっている", wardrobeDestination(skinSwitchingEnabled = true))
    }

    @Test
    fun `ゲートoff(公開ビルド)＝装いの間ルートがグラフに存在しない`() {
        assertNull("公開ビルドに装いの間ルートが残っている", wardrobeDestination(skinSwitchingEnabled = false))
    }
}
