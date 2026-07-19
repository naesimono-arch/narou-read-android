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
 * スキンM「星図」の発見ルーター（ADR 0022 §1）＋ DiscoveryHomeSkyM / DiscoveryResultSkyM の描画分岐・結線テスト。
 *
 * 固定するもの（C2 仕様書 §5）:
 *  1) M 装着でホームの星図構造が出る＝D 構造でない（D 発見の「すべて →」ジャンル入口リンクが出ない）
 *  2) D 装着では従来の D 描画のまま（ルーターが D 経路を横取りしない）
 *  3) 主要結線（検索・ジャンル・期間タブ・ランキング行タップ）
 *  4) 結果一覧の M 星図で見出し・件数・戻る（Up）・行タップが結線される
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、ルーター分岐だけを検証しテーマ SideEffect を
 * 切り離すため（BookshelfSkyMTest / TocSkyMTest と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverySkyMTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setHome(
        skin: Skin,
        state: DiscoveryUiState,
        order: NarouOrder = NarouOrder.WEEKLY,
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
                        onBack = {},
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
                        onBack = {},
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
    fun `M装着でホームの星図構造が出てD構造の入口リンクは出ない`() {
        setHome(Skin.SEIZU_M, DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("星の物語", "N1"))))
        // 星図の署名＝本棚へ戻る（モック省略の D 機能を M 意匠で欠落なく写した先頭導線）＋気分節。
        composeTestRule.onNodeWithContentDescription("本棚に戻る").assertIsDisplayed()
        composeTestRule.onNodeWithText("きょうの気分").assertIsDisplayed()
        // D 発見のジャンル入口リンク「すべて →」は出ない＝画面丸ごと分岐している。
        composeTestRule.onNodeWithText("すべて →").assertDoesNotExist()
    }

    @Test
    fun `D装着では星図が出ず従来のD描画のまま`() {
        setHome(Skin.WAMODERN_D, DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("星の物語", "N1"))))
        composeTestRule.onNodeWithText("すべて →").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("本棚に戻る").assertDoesNotExist()
    }

    @Test
    fun `ホームの検索アイコンでonOpenSearchが呼ばれる`() {
        var searched = false
        setHome(Skin.SEIZU_M, DiscoveryUiState.Empty, onOpenSearch = { searched = true })
        composeTestRule.onNodeWithContentDescription("探す").performClick()
        assertTrue(searched)
    }

    @Test
    fun `ホームのジャンルチップでonPickBiggenreが呼ばれる`() {
        var picked: Pair<Int, String>? = null
        setHome(Skin.SEIZU_M, DiscoveryUiState.Empty, onPickBiggenre = { code, label -> picked = code to label })
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("恋愛"))
        composeTestRule.onNodeWithText("恋愛").performClick()
        assertEquals(1 to "恋愛", picked)
    }

    @Test
    fun `ホームの期間タブでonSelectOrderが呼ばれる`() {
        var selected: NarouOrder? = null
        setHome(Skin.SEIZU_M, DiscoveryUiState.Empty, onSelectOrder = { selected = it })
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("日間"))
        composeTestRule.onNodeWithText("日間").performClick()
        assertEquals(NarouOrder.DAILY, selected)
    }

    @Test
    fun `ホームのランキング行タップでonOpenDetailが呼ばれる`() {
        var opened: String? = null
        setHome(
            Skin.SEIZU_M,
            DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("観測される星", "N42"))),
            onOpenDetail = { opened = it.value },
        )
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("観測される星"))
        composeTestRule.onNodeWithText("観測される星").performClick()
        assertEquals("N42", opened)
    }

    @Test
    fun `初回ロードは直近ランキング未確定ゆえ観測中を表示`() {
        // lastContent が無い初回は骨格を出せない＝status 行で待つ（stale-while-revalidate の下限）。
        // status 行は見出し群の下＝LazyColumn の畳み込み外ゆえ scroll してから存在を確認する。
        setHome(Skin.SEIZU_M, DiscoveryUiState.Loading)
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("観測しています…"))
        composeTestRule.onNodeWithText("観測しています…").assertExists()
    }

    @Test
    fun `再取得のLoading中も直近ランキングを保持し観測中へ全置換しない`() {
        // 期間タブ切替の scroll リセット回帰の固定（実機 2026-07-19）。Content→Loading で行が status 行へ
        // 全置換されると LazyColumn が縮んでスクロールアンカーを失いトップへ落ちる。Loading 中も直近 Content の
        // 行（同 key=ncode）を出し続けることでアンカーを保つ＝この置換が起きないことを固定する。
        val stateHolder = mutableStateOf<DiscoveryUiState>(
            DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("観測される星", "N42"))),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides Skin.SEIZU_M) {
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
        // ランキング行は見出し群の下＝畳み込み外ゆえ scroll して合成させてから存在を確認する。
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("観測される星"))
        composeTestRule.onNodeWithText("観測される星").assertExists()
        // キャッシュ無しの再取得＝一旦 Loading を挟む。骨格保持なら scroll 位置ごと行が残る。
        stateHolder.value = DiscoveryUiState.Loading
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("観測される星").assertExists()          // 骨格保持
        composeTestRule.onNodeWithText("観測しています…").assertDoesNotExist()  // 全置換していない
    }

    @Test
    fun `結果一覧のM星図で見出し・件数・行が出て戻るが結線される`() {
        var upped = false
        setResult(
            Skin.SEIZU_M,
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
            Skin.SEIZU_M,
            ctx = ResultContext(title = "ファンタジー", source = ResultSource.GENRE, query = DiscoveryQuery()),
            state = DiscoveryUiState.Content(allcount = 1, novels = listOf(novel("辺境の星譜", "N9"))),
            onOpenDetail = { opened = it.value },
        )
        composeTestRule.onNodeWithText("辺境の星譜").performClick()
        assertEquals("N9", opened)
    }
}
