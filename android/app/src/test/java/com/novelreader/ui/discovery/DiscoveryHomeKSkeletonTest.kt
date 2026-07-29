package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
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

    /** 骨格領域の数（骨は clearAndSetSemantics で1ノードに畳まれ、この読み上げ文言だけを名乗る）。 */
    private fun skeletonCount(): Int =
        composeTestRule.onAllNodesWithContentDescription(RankingSkeletonDescription).fetchSemanticsNodes().size

    private fun textCount(text: String): Int =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `控えの無い初回ロードは骨格を描く`() {
        setHome(mutableStateOf(HomeInput(NarouOrder.WEEKLY, DiscoveryUiState.Loading)))
        // 骨格が無ければここで「スクロール先が見つからない」で落ちる＝status 1行に潰れている証拠。
        scrollListTo(hasContentDescription(RankingSkeletonDescription))
        assertTrue("初回ロードでランキング領域に骨格が出ていない", skeletonCount() > 0)
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
        assertTrue("再取得中に直近の行が消えた（控えが効いていない）", textCount("週間の作品1") > 0)
        assertEquals("控えがあるのに骨格へ差し替わっている", 0, skeletonCount())
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
        assertEquals("初訪ページに他期間（週間）の行が流用されている", 0, textCount("週間の作品1"))
        scrollListTo(hasContentDescription(RankingSkeletonDescription))
        assertTrue("初訪ページに骨格が出ていない（status 1行に潰れている疑い）", skeletonCount() > 0)
    }
}
