package com.novelreader.ui.skins.k

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 横向き構造の導入（ADR 0034＝Rail 化・T1 横一列化）が、**縦向きを1ミリも動かしていない**ことを固定する。
 *
 * ## なぜ「縦向きが変わっていない」を明示的に測るのか
 * 今回の変更は〈本文の Column を Row で包む〉〈ヘッダとチップ行を条件分岐にする〉という**版面の骨に触る**形で
 * 入っており、縦向きの経路は「分岐の false 側を通っているだけ」＝**壊れても縦向きの既存テストが赤くなるとは
 * 限らない**（既存の縦向き golden は色とフォントスケールの網羅が主目的で、`Row` 追加のような
 * レイアウトノードの増減はピクセルが同じなら通ってしまう）。
 * そこで「結線が来ていても縦向きでは Rail が立たず、帯が出て、固定トップが従来値のまま」を
 * **座標で**押さえる。ここが緑なら、縦向きへの染み出しは構造的に無い。
 *
 * [GraphicsMode] NATIVE 必須の理由は [KLandscapeRailT1Test] と同じ（固定トップに題字の行高が入る）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KPortraitUnchangedByRailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rankingContent = DiscoveryUiState.Content(
        allcount = 3,
        novels = (1..3).map { workSummary(title = "作品$it", ncode = "N%04dAA".format(it)) },
    )

    @Test
    fun `縦向きは結線が来ていても Rail を立てず 帯と題字が従来どおり出る`() {
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            WithRail {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        DiscoveryHomeK(
                            order = NarouOrder.WEEKLY,
                            state = rankingContent,
                            onBack = {},
                            onOpenDetail = {},
                            onOpenGenre = {},
                            onPickBiggenre = { _, _ -> },
                            onOpenSearch = {},
                            onPickMood = {},
                            onSelectOrder = {},
                            onRefresh = {},
                            initialMoodPattern = MoodPattern.CLASSIC,
                        )
                    }
                    KBottomNav(current = KTab.DISCOVER, onSelect = {})
                }
            }
        }

        val rootTop = composeTestRule.onRoot().getUnclippedBoundsInRoot().top
        val list = composeTestRule.onNode(
            hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).getUnclippedBoundsInRoot()
        val fixedTop = list.top - rootTop
        println("[縦向き実測] さがす fixedTop=$fixedTop viewport=${list.bottom - list.top}")
        // 縦向きの固定トップ＝題字「さがす」＋検索欄の縦積み。横向きの Rail 化で動いてはならない。
        assertEquals("縦向きの固定トップが動いた（横向き変更が縦へ染み出している）", PORTRAIT_FIXED_TOP, fixedTop)

        // 帯（KBottomNav）が縦向きでは必ず出る＝タブ3本のラベルが見えている。
        composeTestRule.onNodeWithText("本棚").assertIsDisplayed()
        composeTestRule.onNodeWithText("設定").assertIsDisplayed()
        // 検索欄は固定トップの内側（リスト上端より上）に居る＝T1 の横1行へは畳まれていない。
        val placeholder = composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).getUnclippedBoundsInRoot()
        assertTrue(
            "検索欄が固定トップの外へ出た＝縦向きで T1 が誤って効いている: ${placeholder.bottom} > ${list.top}",
            placeholder.bottom <= list.top,
        )
    }

    @Test
    fun `縦向きの本棚は拡張FABと題字ヘッダが従来どおり`() {
        setKGridView(true)
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, fontScale = 1.0f) { _ ->
            WithRail {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        BookshelfK(
                            data = KShelfFixtures.mixedData(),
                            chrome = KShelfFixtures.chrome(KShelfFixtures.mixedStatusCounts),
                            actions = KShelfFixtures.actions,
                            selection = KShelfFixtures.selection,
                            webActions = KShelfFixtures.webActions,
                            snackbarHostState = remember { SnackbarHostState() },
                        )
                    }
                    KBottomNav(current = KTab.BOOKSHELF, onSelect = {})
                }
            }
        }

        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        // 拡張FAB は本文の右下に残る（Rail 上端の円形へ移っていない）＝ラベルぶん幅がある。
        val fab = composeTestRule.onNodeWithContentDescription("PDFを追加").getUnclippedBoundsInRoot()
        println("[縦向き実測] 本棚 FAB=${fab.left}..${fab.right}（画面右端 ${root.right}）")
        assertTrue("縦向きの FAB が右下から動いた", fab.right > root.right - 32.dp)
        assertTrue(
            "縦向きの FAB が円形化した（拡張ラベルを失った）: 幅=${fab.right - fab.left}",
            fab.right - fab.left > 80.dp,
        )
        // 冊数は本文ヘッダ側に居る（Rail へ移っていない）＝左端 80dp の外。
        val count = composeTestRule.onNodeWithText("冊", substring = true).getUnclippedBoundsInRoot()
        assertTrue("縦向きで冊数が Rail 幅の内側へ寄った＝T1 が誤って効いている", count.right > KNavigationRailWidth)
    }

    /** 結線（[LocalKTabSelect]）が来ている状態を作る＝それでも縦向きでは何も変わらないことを見る。 */
    @Composable
    private fun WithRail(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKTabSelect provides { _: KTab -> }) { content() }
    }

    private companion object {
        const val SEARCH_PLACEHOLDER = "作品名・作者名・キーワードで探す"

        /** 縦向きの固定トップ（初回実行の println を焼いた値）。 */
        val PORTRAIT_FIXED_TOP = 120.5.dp
    }
}
