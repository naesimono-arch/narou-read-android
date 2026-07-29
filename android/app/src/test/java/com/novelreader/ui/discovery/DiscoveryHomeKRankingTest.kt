package com.novelreader.ui.discovery

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンK「さがす」ランキングの期間スワイプ（2026-07-29）の回帰テスト。
 *
 * 固定する契約:
 *  1) ランキング行の上での横スワイプで期間が進み（onSelectOrder 発火）、期間タブの選択表示が追従する
 *  2) 期間タブのタップでページが送られ、選択表示（＝ページャ現在地由来）が追従する＝ページが実際に動いた証明
 *  3) 端ページ（日間/新着）での余りスワイプは外側タブ Pager へ伝播しない（rankingEdgeSeal の封止・機構レベル）
 *
 * 外側タブ Pager は TabPagerHost と同型の最小ハーネス〈HorizontalPager の中央ページに発見ホーム〉で再現する
 * （実 TabPagerHost は MainActivity 配線・deferNeighborPages 等の無関係な足場を要求するため。封止の機構は
 *  「入れ子スクロールの余りが親 Pager の scrollable に届くか」だけで決まり、この同型で等価に検証できる）。
 * order は VM（homeOrder）の代役として test 側の MutableState で持ち、onSelectOrder で書き戻す。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeKRankingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val orderState = mutableStateOf(NarouOrder.WEEKLY)
    private var outerPager: PagerState? = null

    /** ランキング1行だけの Content（行ノード＝スワイプの起点。1件なら期間ページが縦に短くスクロール制御が楽）。 */
    private val contentState = DiscoveryUiState.Content(
        allcount = 1,
        novels = listOf(workSummary(title = "作品W", ncode = "N1")),
    )

    private fun setHost(
        initialOrder: NarouOrder = NarouOrder.WEEKLY,
        recordedOrders: MutableList<NarouOrder>? = null,
    ) {
        orderState.value = initialOrder
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides Skin.MEIKAI_K) {
                MaterialTheme {
                    // 外側タブ Pager の代役: 中央（page 1）が発見ホーム・両隣はダミー。封止が破れると
                    // 端ページでの余りスワイプが outer を動かし settledPage が 1 から外れる。
                    val outer = rememberPagerState(initialPage = 1, pageCount = { 3 })
                    outerPager = outer
                    HorizontalPager(state = outer) { page ->
                        when (page) {
                            1 -> DiscoveryHomeContent(
                                order = orderState.value,
                                state = contentState,
                                onBack = {},
                                onOpenDetail = {},
                                onOpenGenre = {},
                                onPickBiggenre = { _, _ -> },
                                onOpenSearch = {},
                                onPickMood = {},
                                // VM 代役: setHomeOrder と同じく order を書き戻す（同値は VM 側で no-op だが
                                // 発火回数の検証のため記録は全数残す）。
                                onSelectOrder = {
                                    recordedOrders?.add(it)
                                    orderState.value = it
                                },
                                onRefresh = {},
                            )
                            else -> Text(if (page == 0) "外側ダミー左" else "外側ダミー右")
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * 画面本体の縦 LazyColumn を特定して合成域を送る。hasScrollToNodeAction だけだと外側 Pager・
     * 気分/期間の横ページャも一致するため、縦スクロール軸を持つ唯一のノードで絞る（誤って外側 Pager を
     * スクロールさせると検証対象のタブ位置ごと壊れる）。
     */
    private fun scrollListTo(text: String) {
        composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).performScrollToNode(hasText(text))
    }

    /** ランキング行上のスワイプ。durationMillis=50 の理由は DiscoveryHomeKMoodTest と同じ（確実にフリング閾値超え）。 */
    private fun swipeOnRankingRow(toLeft: Boolean) {
        composeTestRule.onNodeWithText("作品W").performTouchInput {
            if (toLeft) swipeLeft(durationMillis = 50) else swipeRight(durationMillis = 50)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `ランキング行の横スワイプで次の期間へ進みタブ選択が追従する`() {
        val recorded = mutableListOf<NarouOrder>()
        setHost(NarouOrder.WEEKLY, recorded)
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = true)
        // settledPage 経由でちょうど1回だけ発火（初回発行 drop や order 書き戻しの往復で二重発火しない）。
        assertEquals(listOf(NarouOrder.MONTHLY), recorded)
        scrollListTo("月間")
        composeTestRule.onNodeWithText("月間").assertIsSelected()
        composeTestRule.onNodeWithText("週間").assertIsNotSelected()
    }

    @Test
    fun `期間タブのタップでページが送られ選択が追従する`() {
        val recorded = mutableListOf<NarouOrder>()
        setHost(NarouOrder.WEEKLY, recorded)
        scrollListTo("累計")
        composeTestRule.onNodeWithText("累計").performClick()
        composeTestRule.waitForIdle()
        // タップ1回ぶんだけ発火（ページ追従アニメの settle が同値でもう一度 VM を叩かない）。
        assertEquals(listOf(NarouOrder.TOTAL), recorded)
        // 選択表示はページャ現在地由来＝これが選択済みになる＝ページが実際に「累計」まで動いた証明。
        composeTestRule.onNodeWithText("累計").assertIsSelected()
        composeTestRule.onNodeWithText("週間").assertIsNotSelected()
    }

    @Test
    fun `端ページでの余りスワイプは外側タブPagerへ伝播しない`() {
        setHost(NarouOrder.NEW) // 右端ページ（新着）から開始
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = true) // 右端でさらに左スワイプ＝余りが全量発生
        assertEquals("右端での左スワイプが外側タブを動かした", 1, outerPager!!.settledPage)

        // 左端（日間）へ移して逆向きも封止されることを確認（order 書換→LaunchedEffect がページを追従）。
        orderState.value = NarouOrder.DAILY
        composeTestRule.waitForIdle()
        scrollListTo("作品W")
        swipeOnRankingRow(toLeft = false) // 左端でさらに右スワイプ
        assertEquals("左端での右スワイプが外側タブを動かした", 1, outerPager!!.settledPage)
        // ダミーページが合成されていない＝外側 Pager が微動もしていない傍証。
        composeTestRule.onNodeWithText("外側ダミー左").assertDoesNotExist()
        composeTestRule.onNodeWithText("外側ダミー右").assertDoesNotExist()
    }
}
