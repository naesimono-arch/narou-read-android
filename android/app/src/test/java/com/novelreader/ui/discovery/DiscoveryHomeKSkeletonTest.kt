package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.skins.k.rankingPageTestTag
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンK ランキング期間ページャの「控えの有無」による描き分けテスト（2026-07-29 の再発修正）。
 *
 * 全実装共通の不変条件（先頭へクランプしない／Empty・Error を覆い隠さない）は DiscoveryHomeInvariantTest が
 * 見る。こちらは K 固有の設計——**期間別の控え**と、控えの無いページで高さを保つ**構造スケルトン**——が
 * 意図どおりに切り替わることを固定する。
 *
 * なぜ期間別の控えが要るか: 旧実装の単一控えを期間跨ぎで流用すると、週間の行が月間ページに載って誤誘導になる。
 * その代償として「初訪ページには控えが無い」状態が生まれ、そこを status 1行で描いたのが再発の真因だった。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeKSkeletonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class HomeInput(val order: NarouOrder, val state: DiscoveryUiState)

    private val weeklyContent = DiscoveryUiState.Content(
        allcount = 3,
        novels = (1..3).map { workSummary(title = "週間の作品$it", ncode = "W$it") },
    )

    private fun setHome(input: MutableState<HomeInput>) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides Skin.MEIKAI_K) {
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
                        onSelectOrder = { requested -> input.value = input.value.copy(order = requested) },
                        onRefresh = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** 縦スクロール軸を持つノード（画面本体の LazyColumn）を送る＝横ページャを誤って動かさない。 */
    private fun scrollListTo(matcher: SemanticsMatcher) {
        composeTestRule.onAllNodes(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).onFirst().performScrollToNode(matcher)
    }

    // ── 判定は「どの期間ページの子孫か」で絞る ──────────────────────────────
    // なぜ木全体を数えないか（2026-07-31）: このテストが禁じたいのは〈月間ページに週間の行が載る＝
    // 期間別控えの設計が壊れた〉だけで、〈隣の週間ページが自分の行を描いている〉は正常（画面にも
    // TalkBack にも出ない）。木全体を数える検証は両者を区別できず、合成されるページ数が変わるだけで
    // 意味が変わる脆い代理指標になる——実際、隣接ページ常駐を試した際に実装が正しいまま誤検知した
    //（常駐化自体は Pager の高さ規約と両立せず撤回。経緯は DiscoveryHomeK の RankingPagerK 内コメント）。
    // 閾値を緩めるのではなく、不変条件の適用範囲をページ単位へ正す。

    /** 指定期間ページの骨格領域の数（骨は clearAndSetSemantics で1ノードに畳まれ、この読み上げ文言だけを名乗る）。 */
    private fun skeletonCountOn(order: NarouOrder): Int =
        composeTestRule.onAllNodes(
            hasContentDescription(RankingSkeletonDescription) and onPage(order),
        ).fetchSemanticsNodes().size

    /** 指定期間ページの子孫に [text] を持つノードの数。 */
    private fun textCountOn(order: NarouOrder, text: String): Int =
        composeTestRule.onAllNodes(hasText(text) and onPage(order)).fetchSemanticsNodes().size

    /** 木全体での出現数（ページを問わない検査＝「そもそも描かれていない」ことの確認用）。 */
    private fun textCount(text: String): Int =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun onPage(order: NarouOrder): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(rankingPageTestTag(order)))

    @Test
    fun `控えの無い初回ロードは骨格を描く`() {
        setHome(mutableStateOf(HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Loading)))
        // 骨格が無ければここで「スクロール先が見つからない」で落ちる＝status 1行に潰れている証拠。
        scrollListTo(hasContentDescription(RankingSkeletonDescription))
        assertTrue("初回ロードでランキング領域に骨格が出ていない", skeletonCountOn(NarouOrder.WEEKLY) > 0)
        // 骨は文字を描かない（意匠は案A＝構造プレースホルダ）。旧 status の文言は読み上げだけが引き継ぐ。
        assertEquals("骨格が文字として描かれている（案A は文字なし）", 0, textCount(RankingSkeletonDescription))
    }

    @Test
    fun `控えのある期間は再取得中も直近の行を出し続ける`() {
        val input = mutableStateOf(HomeInput(NarouOrder.WEEKLY, weeklyContent))
        setHome(input)
        scrollListTo(hasText("週間の作品1"))
        // 同じ期間の再取得＝控えが効く（stale-while-revalidate）。
        input.value = HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Loading)
        composeTestRule.waitForIdle()
        assertTrue(
            "再取得中に直近の行が消えた（控えが効いていない）",
            textCountOn(NarouOrder.WEEKLY, "週間の作品1") > 0,
        )
        assertEquals(
            "控えがあるのに週間ページが骨格へ差し替わっている",
            0,
            skeletonCountOn(NarouOrder.WEEKLY),
        )
    }

    @Test
    fun `控えの無い初訪ページは骨格を描き他期間の行を流用しない`() {
        val input = mutableStateOf(HomeInput(NarouOrder.WEEKLY, weeklyContent))
        setHome(input)
        scrollListTo(hasText("週間の作品1"))
        // 一度も開いていない期間へ移る＝控えが無い。ここを status 1行にすると高さが崩壊して先頭へクランプされる。
        input.value = HomeInput(NarouOrder.MONTHLY, DiscoveryUiState.Loading)
        composeTestRule.waitForIdle()
        // 期間別に控えを分けた狙い＝週間の行が月間ページに載る誤誘導を起こさないこと（送る前に確かめる）。
        assertEquals(
            "初訪ページ（月間）に他期間（週間）の行が流用されている",
            0,
            textCountOn(NarouOrder.MONTHLY, "週間の作品1"),
        )
        scrollListTo(hasContentDescription(RankingSkeletonDescription))
        assertTrue(
            "初訪ページ（月間）に骨格が出ていない（status 1行に潰れている疑い）",
            skeletonCountOn(NarouOrder.MONTHLY) > 0,
        )
        // 上の「月間に週間の行が 0 件」が**空振りで通っていない**ことの裏取り（2026-07-31）。
        // 0 件は「流用されていない」だけでなく「そもそも行が描かれない」「fixture の文言が変わった」でも
        // 成立してしまうため、単独だと退行を素通しする。そこで週間へ戻し、**再取得を待たず**（state は
        // Loading のまま）に控えの行が戻ることを見る＝期間を跨いでも週間の控えが生き残っていた証拠になり、
        // 同時に文言・描画が生きていることも示す。
        // なぜ「月間を見ている最中に週間ページを数える」形にしないか: このページャは隣接ページを常駐させない
        // （beyondViewportPageCount 不使用＝高さ規約と両立しないため撤回。経緯は RankingPagerK 内コメント）。
        // よって非表示ページはそもそも合成されず、ツリーに無いのが正常。観点は残し、見る位置だけを変える。
        input.value = HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Loading)
        composeTestRule.waitForIdle()
        scrollListTo(hasText("週間の作品1"))
        assertTrue(
            "期間を往復したら週間の控えが失われた（控えが期間別に保持されていない）",
            textCountOn(NarouOrder.WEEKLY, "週間の作品1") > 0,
        )
    }
}
