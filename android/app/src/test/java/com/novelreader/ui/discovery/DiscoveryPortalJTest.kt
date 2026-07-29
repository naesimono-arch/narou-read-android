package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンJ「ポータル」の発見ルーター（ADR 0022 §1）＋ DiscoveryHomePortalJ / DiscoveryResultPortalJ の
 * 描画分岐・結線テスト。DiscoveryCartridgePTest と同型（LocalSkin を直接 provide しテーマ SideEffect を切り離す）。
 *
 * 固定するもの:
 *  1) J 装着でホームの扉の回廊構造が出る＝D 構造でない（D の「すべて →」ジャンル入口リンクが出ず、回廊のジャンル入口は矢印なしの「すべて」）
 *  2) D 装着では従来の D 描画のまま（ルーターが D 経路を横取りしない）
 *  3) 主要結線（検索・ジャンル・期間タブ・ランキング行タップ）＋モック省略の D 機能（本棚へ戻る）
 *  4) 結果一覧（回廊の着地）の見出し・件数・戻る（Up）・行タップ結線
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryPortalJTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setHome(
        skin: Skin,
        state: DiscoveryUiState,
        order: NarouOrder = NarouOrder.WEEKLY,
        onBack: () -> Unit = {},
        onOpenDetail: (com.novelreader.narou.model.Ncode) -> Unit = {},
        onOpenSearch: () -> Unit = {},
        onPickBiggenre: (Int, String) -> Unit = { _, _ -> },
        onSelectOrder: (NarouOrder) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin) {
                MaterialTheme {
                    DiscoveryHomeContent(
                        order = order,
                        state = state,
                        onBack = onBack,
                        onOpenDetail = onOpenDetail,
                        onOpenGenre = {},
                        onPickBiggenre = onPickBiggenre,
                        onOpenSearch = onOpenSearch,
                        onPickMood = {},
                        onSelectOrder = onSelectOrder,
                        onRefresh = {},
                    )
                }
            }
        }
    }

    private fun setResult(
        skin: Skin,
        ctx: ResultContext?,
        state: DiscoveryUiState,
        onUp: () -> Unit = {},
        onOpenDetail: (com.novelreader.narou.model.Ncode) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin) {
                MaterialTheme {
                    DiscoveryResultContent(
                        ctx = ctx,
                        state = state,
                        onUp = onUp,
                        onEditConditions = {},
                        onOpenDetail = onOpenDetail,
                        onChangeOrder = {},
                        onChangeGenreFilter = { _, _ -> },
                        onRefresh = {},
                        onLoadMore = {},
                    )
                }
            }
        }
    }

    private fun novel(title: String, ncode: String) = workSummary(title = title, ncode = ncode)

    @Test
    fun `J装着でホームの回廊構造が出てD構造の入口リンクは出ない`() {
        setHome(Skin.PORTAL_J, DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("扉の向こう", "N1"))))
        // 回廊の署名＝見出し「見つける」＋気分節＋扉の象徴文字（J 固有の遊び。巨大グリフは領域外へ溢れうる＝exists で確認）。
        composeTestRule.onNodeWithText("見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("きょうの気分").assertIsDisplayed()
        composeTestRule.onNodeWithText("旅").assertExists() // .amb-tabi の象徴文字＝J だけが描く扉の絵
        composeTestRule.onNodeWithContentDescription("本棚に戻る").assertIsDisplayed()
        // D 発見のジャンル入口リンク「すべて →」は出ない＝画面丸ごと分岐している（回廊は矢印なしの「すべて」チップ）。
        composeTestRule.onNodeWithText("すべて →").assertDoesNotExist()
    }

    @Test
    fun `D装着では回廊構造が出ず従来のD描画のまま`() {
        setHome(Skin.WAMODERN_D, DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("扉の向こう", "N1"))))
        // D はジャンル入口リンク「すべて →」を出し、J 固有の扉の象徴文字は描かない（「きょうの気分」節は D/J 共通なので判定に使わない）。
        composeTestRule.onNodeWithText("すべて →").assertIsDisplayed()
        composeTestRule.onNodeWithText("旅").assertDoesNotExist()
    }

    @Test
    fun `ホームの検索フィールドでonOpenSearchが呼ばれる`() {
        var searched = false
        setHome(Skin.PORTAL_J, DiscoveryUiState.Empty, onOpenSearch = { searched = true })
        // K 形伝播で検索アイコンは常時可視の実検索フィールドへ格上げ（モック .search）。プレースホルダ文タップで onOpenSearch。
        composeTestRule.onNodeWithText("作品名・作者名・キーワードで探す").performClick()
        assertTrue(searched)
    }

    @Test
    fun `ホームの本棚へ戻るでonBackが呼ばれる`() {
        var backed = false
        setHome(Skin.PORTAL_J, DiscoveryUiState.Empty, onBack = { backed = true })
        composeTestRule.onNodeWithContentDescription("本棚に戻る").performClick()
        assertTrue(backed)
    }

    @Test
    fun `ホームのジャンル入口チップでonPickBiggenreが呼ばれる`() {
        var picked: Pair<Int, String>? = null
        setHome(Skin.PORTAL_J, DiscoveryUiState.Empty, onPickBiggenre = { code, label -> picked = code to label })
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("恋愛"))
        composeTestRule.onNodeWithText("恋愛").performClick()
        assertEquals(1 to "恋愛", picked)
    }

    @Test
    fun `ホームの期間タブでonSelectOrderが呼ばれる`() {
        var selected: NarouOrder? = null
        setHome(Skin.PORTAL_J, DiscoveryUiState.Empty, onSelectOrder = { selected = it })
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("日間"))
        composeTestRule.onNodeWithText("日間").performClick()
        assertEquals(NarouOrder.DAILY, selected)
    }

    @Test
    fun `ホームのランキング行タップでonOpenDetailが呼ばれる`() {
        var opened: String? = null
        setHome(
            Skin.PORTAL_J,
            DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("いま人がくぐる扉", "N42"))),
            onOpenDetail = { opened = it.value },
        )
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("いま人がくぐる扉"))
        composeTestRule.onNodeWithText("いま人がくぐる扉").performClick()
        assertEquals("N42", opened)
    }

    @Test
    fun `結果一覧の回廊着地で見出し・件数・行が出て戻るが結線される`() {
        var upped = false
        setResult(
            Skin.PORTAL_J,
            ctx = ResultContext(title = "30分の小さな旅", source = ResultSource.MOOD, query = DiscoveryQuery()),
            state = DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("短い旅の物語", "N7"))),
            onUp = { upped = true },
        )
        composeTestRule.onNodeWithText("30分の小さな旅").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 作品").assertIsDisplayed()
        composeTestRule.onNodeWithText("短い旅の物語").assertExists()
        // .back（‹ 見つける）＝発見ホームへの固定 Up。
        composeTestRule.onNodeWithText("見つける").performClick()
        assertTrue(upped)
    }

    @Test
    fun `結果一覧の行タップでonOpenDetailが呼ばれる`() {
        var opened: String? = null
        setResult(
            Skin.PORTAL_J,
            ctx = ResultContext(title = "ファンタジー", source = ResultSource.GENRE, query = DiscoveryQuery()),
            state = DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("辺境の扉", "N9"))),
            onOpenDetail = { opened = it.value },
        )
        composeTestRule.onNodeWithText("辺境の扉").performClick()
        assertEquals("N9", opened)
    }

    @Test
    fun `文脈nullの結果一覧は退去せず最小ローディングで待つ`() {
        // process death 復帰中＝ctx null。J も D/M/P と同じく退去せず最小ローディングを出す（分岐先内部で扱う）。
        setResult(Skin.PORTAL_J, ctx = null, state = DiscoveryUiState.Loading)
        composeTestRule.onNodeWithText("扉をひらいています…").assertIsDisplayed()
    }

    @Test
    fun `初回ロードは直近ランキング未確定ゆえ扉をひらく表示`() {
        // lastContent が無い初回は骨格を出せない＝status 行で待つ（stale-while-revalidate の下限）。
        setHome(Skin.PORTAL_J, DiscoveryUiState.Loading)
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("扉をひらいています…"))
        composeTestRule.onNodeWithText("扉をひらいています…").assertExists()
    }

    @Test
    fun `再取得のLoading中も直近ランキングを保持し扉をひらく行へ全置換しない`() {
        // 期間タブ切替の scroll リセット回帰の固定（実機 2026-07-19）。Content→Loading で行が status 行へ
        // 全置換されると LazyColumn が縮んでアンカーを失いトップへ落ちる。Loading 中も直近 Content の行
        // （同 key=ncode）を出し続けることでアンカーを保つ＝この置換が起きないことを固定する。
        val stateHolder = mutableStateOf<DiscoveryUiState>(
            DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("いま人がくぐる扉", "N42"))),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides Skin.PORTAL_J) {
                MaterialTheme {
                    DiscoveryHomeContent(
                        order = NarouOrder.WEEKLY,
                        state = stateHolder.value,
                        onBack = {},
                        onOpenDetail = {},
                        onOpenGenre = {},
                        onPickBiggenre = { _, _ -> },
                        onOpenSearch = {},
                        onPickMood = {},
                        onSelectOrder = {},
                        onRefresh = {},
                    )
                }
            }
        }
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("いま人がくぐる扉"))
        composeTestRule.onNodeWithText("いま人がくぐる扉").assertExists()
        stateHolder.value = DiscoveryUiState.Loading
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("いま人がくぐる扉").assertExists()          // 骨格保持
        composeTestRule.onNodeWithText("扉をひらいています…").assertDoesNotExist()  // 全置換していない
    }
}
