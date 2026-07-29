package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.viewmodel.DiscoveryUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 発見ホームの**不変条件**テスト（L1・2026-07-29）。
 *
 * 「この実装のこの分岐」を固定する個別テスト（DiscoveryHomeContentTest / DiscoverySkyMTest 等）とは役割が違う:
 * こちらは [DiscoveryHomeRegistry] に登録された**全実装へ同じ性質**をかける。実装が増えたら登録簿へ1行足せば
 * 検査対象になり、足し忘れは DiscoveryHomeInvariantCoverageTest（L2）が落とす。
 *
 * 固定する不変条件:
 *  1) 一覧を送った状態でキャッシュ無しの期間切替が起きても、一覧が先頭へクランプされない
 *     （＝ランキング領域の高さが崩壊しない。2026-07-19 の「勝手にトップへ戻る」バグと、2026-07-29 に
 *       K のページャ化で初訪ページから再発した同型を、実装非依存の形で捕まえる）
 *  2) Empty は骨格・控えで覆い隠さず「0件」を正直に出す
 *  3) Error は骨格・控えで覆い隠さず理由を出す
 *
 * (2)(3) は (1) の対処（高さを保つ骨・控え）が行き過ぎて「無い・失敗した」を隠すのを禁じる対の条件＝
 * 片側だけ強めると必ずもう片方が壊れる関係なので、同じ登録簿で必ず一緒に回す。
 *
 * 観測点について: 「先頭へクランプされたか」は LazyListState を直接読めない（各実装が内部に持つ）ため、
 * **先頭セクション見出しが合成されているか**で見る。LazyColumn は可視域外の item を破棄するので、送った位置に
 * 留まっていれば先頭 item はツリーに居ない。一覧が status 1行へ潰れると総コンテンツ高が画面高付近まで縮み、
 * どこまで送っていても先頭 item が可視域へ入る＝ツリーに現れる。テスト側に実装の内部状態を持ち込まずに
 * 機序そのものを観測できる（末尾リンクの可視でも観測できるが、行高がスキンごとに違うぶん余白が読みにくいので
 * 判定は先頭側1点に絞る）。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeInvariantTest(
    @Suppress("unused") private val label: String, // パラメタライズド表示名（"{0}"）用
    private val impl: DiscoveryHomeImpl,
) {

    companion object {
        /** 一覧の行数。ランキング領域が画面高を大きく超える＝送る余地があることが前提条件。 */
        private const val RANKING_ROWS = 20

        /**
         * 送り先の行（中ほど）。ここまで送ると先頭セクション（気分節）は必ず可視域外＝どの実装でも
         * 「先頭 item が居ない」が成立する。末尾まで送らないのは、行高がスキンごとに違うため
         * 末尾側の余白を当てにした判定を避けるため。
         */
        private const val SCROLL_ANCHOR_ROW = 12

        /** 全実装で共通の先頭セクション見出し＝先頭へ戻ったことの観測点。 */
        private const val TOP_SECTION = "きょうの気分"

        private const val ERROR_MESSAGE = "通信に失敗しました"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun implementations(): List<Array<Any>> =
            DiscoveryHomeRegistry.implementations.map { arrayOf(it.displayName, it) }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    /** order と state は必ず同時に差し替える（別フレームで動かすと控えの取り違えという別の話が混ざる）。 */
    private data class HomeInput(val order: NarouOrder, val state: DiscoveryUiState)

    private val content = DiscoveryUiState.Content(
        allcount = RANKING_ROWS,
        novels = (1..RANKING_ROWS).map { workSummary(title = "作品$it", ncode = "N$it") },
    )

    private fun setHome(input: MutableState<HomeInput>) {
        composeTestRule.setContent {
            // 入口は共通の DiscoveryHomeContent＝スキンで実装が分岐する（ADR 0022 §1）。テーマの SideEffect を
            // 巻き込まないよう LocalSkin だけ差し替える（既存のスキン別テストと同型のハーネス）。
            CompositionLocalProvider(LocalSkin provides impl.skin) {
                MaterialTheme {
                    DiscoveryHomeContent(
                        order = input.value.order,
                        state = input.value.state,
                        onBack = {},
                        onOpenDetail = {},
                        onOpenGenre = {},
                        onPickBiggenre = { _, _ -> },
                        onOpenSearch = {},
                        onPickMood = {},
                        // VM 代役。K は期間ページャとタブを order 単一情報源で同期するため、
                        // 書き戻さないと実機と挙動が変わる（同値要求は VM 側で no-op）。
                        onSelectOrder = { requested -> input.value = input.value.copy(order = requested) },
                        onRefresh = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * 画面本体の縦 LazyColumn を特定して合成域を送る。hasScrollToNodeAction だけだと気分/期間の横ページャや
     * チップ列も一致するため、縦スクロール軸を持つノードで絞る（横を動かすと検証対象ごと壊れる）。
     */
    private fun scrollListTo(text: String) {
        composeTestRule.onAllNodes(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).onFirst().performScrollToNode(hasText(text))
    }

    /** LazyColumn は可視域外の item を破棄する＝合成されていないこと＝画面に出ていないこと。 */
    private fun assertNotComposed(text: String, why: String) {
        val nodes = composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("[${impl.displayName}] $why（'$text' が合成されている）", nodes.isEmpty())
    }

    /** 一覧を中ほどまで送った状態を作る（各不変条件の共通の出発点）。 */
    private fun scrollIntoRanking(input: MutableState<HomeInput>) {
        setHome(input)
        scrollListTo("作品$SCROLL_ANCHOR_ROW")
        // 出発点が「先頭 item が居ない」であることを確かめてから本題へ入る（前提が崩れたまま緑になるのを防ぐ）。
        assertNotComposed(TOP_SECTION, "前提が崩れている: 一覧の中ほどまで送ったのに先頭セクションが見えている")
    }

    @Test
    fun `一覧を送った状態でキャッシュ無しの期間切替が起きても先頭へクランプされない`() {
        val input = mutableStateOf(HomeInput(NarouOrder.WEEKLY, content))
        scrollIntoRanking(input)

        // キャッシュ無しの期間切替＝新しい order で一旦 Loading（VM の loadHome と同じ順序）。
        // このとき一覧領域の高さが行数ぶんから status 1行ぶんへ崩壊すると、総コンテンツ高が縮んで
        // LazyListState が可視アンカーに留まれず先頭側へクランプされる＝再発の機序そのもの。
        input.value = HomeInput(NarouOrder.MONTHLY, DiscoveryUiState.Loading)
        composeTestRule.waitForIdle()

        assertNotComposed(
            TOP_SECTION,
            "期間切替でランキング領域の高さが崩壊し一覧が先頭へクランプされた" +
                "（控えの無い面は status 1行に潰さず、行数ぶんの骨格＝RankingListSkeleton で高さを保つこと）",
        )
    }

    @Test
    fun `Empty は骨格で覆い隠さず0件を正直に出す`() {
        val input = mutableStateOf(HomeInput(NarouOrder.WEEKLY, content))
        scrollIntoRanking(input)

        input.value = HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Empty)
        composeTestRule.waitForIdle()

        scrollListTo(impl.emptyText)
        composeTestRule.onNodeWithText(impl.emptyText).assertExists()
        // 直近の行を骨格として出し続けていたら「0件」を行で覆い隠したことになる（高さ保持より正直さが上位）。
        assertNotComposed("作品1", "Empty なのに直近ランキングの行が残っている＝0件を覆い隠している")
    }

    @Test
    fun `Error は骨格で覆い隠さず理由を出す`() {
        val input = mutableStateOf(HomeInput(NarouOrder.WEEKLY, content))
        scrollIntoRanking(input)

        input.value = HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Error(ERROR_MESSAGE))
        composeTestRule.waitForIdle()

        scrollListTo(ERROR_MESSAGE)
        composeTestRule.onNodeWithText(ERROR_MESSAGE).assertExists()
        assertNotComposed("作品1", "Error なのに直近ランキングの行が残っている＝失敗を覆い隠している")
    }
}
