package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.skins.k.DiscoveryHomeK
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンK「さがす」の きょうの気分ページャ・高さ安定枠の回帰テスト（実機報告 2026-07-29）。
 *
 * 固定するもの:
 *  1) 組（MoodPattern）ごとに文言の折返し行数＝ページ高が違っても、気分ブロック直下の
 *     日替わり注記の縦位置が組切替（循環スワイプ）で動かない＝下部レイアウトのがくん対策。
 *     3組すべてを巡回して検証する（どの組間の高低差でも破れないこと）。
 *  2) 高さ予約ゴースト（不可視の全組格子）が semantics に漏れない・実カードのタップ結線が生きている。
 *
 * 巡回の起点を [START_PATTERN] に固定する（2026-07-30）。以前は `MoodPattern.forEpochDay(LocalDate.now()…)`
 * と本番と同じ導出をテスト側にも書いていたが、それは
 *   ・**本番実装の写経**＝導出規則が壊れてもテストが同じ式で追随するため、そこに検出力が無い
 *   ・失敗時の再現条件が実行日に依存する＝落ちた日と別の日には同じ絵で再現できない
 * という二重の弱さがあった。[DiscoveryHomeK] の `initialMoodPattern` への state hoisting で起点を
 * 注入できるようになったため、実時計への依存を捨てる（日付→組の導出規則そのものは固定 epochDay を
 * 使う MoodPatternTest が受け持つ＝検証の役割を分ける）。巡回で3組すべてを通るので、起点を固定しても
 * 「どの組が描けるか」のカバレッジは減らない。
 *
 * ルーター（[DiscoveryHomeContent]）を経由せず K の画面を直接組む: 本テストが見るのは気分ブロック内部の
 * 高さと semantics であってスキン分岐ではない。K へのルーター分岐は DiscoveryHomeKRankingTest・
 * DiscoveryHomeKSkeletonTest・DiscoveryHomeInvariantTest が既に固定している。ここで重ねて経由すると
 * 起点の組を注入する経路が無くなる（K 固有の引数を共通ルーターの署名へ足すのは本末転倒）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeKMoodTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setHome(onPickMood: (MoodPreset) -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoveryHomeK(
                    order = NarouOrder.WEEKLY,
                    state = DiscoveryUiState.Empty,
                    onBack = {},
                    onOpenDetail = {},
                    onOpenGenre = {},
                    onPickBiggenre = { _, _ -> },
                    onOpenSearch = {},
                    onPickMood = onPickMood,
                    onSelectOrder = {},
                    onRefresh = {},
                    // 起点の組を固定＝実時計から切り離す（本番の既定値は日付導出のまま）。
                    initialMoodPattern = START_PATTERN,
                )
            }
        }
    }

    /** 日替わり注記の上端 Y（dp）＝気分ブロック高の観測点。ここが動く＝下部全体ががくんと動く。 */
    private fun noteTop(): Float =
        composeTestRule.onNode(hasText("日替わり", substring = true))
            .getUnclippedBoundsInRoot().top.value

    /** 現在ページ（起点から順に循環）の先頭カード上で左スワイプ＝次の組へ送る。 */
    private fun swipeToNextPattern(from: MoodPattern) {
        // durationMillis=50: 既定200msだとカード幅由来のスワイプ速度が snap のフリング閾値（400dp/s）
        // すれすれになり、端数で元ページへ戻り得る。短時間化で確実にフリング＝次ページ確定にする。
        composeTestRule.onNodeWithText(from.presets[0].title)
            .performTouchInput { swipeLeft(durationMillis = 50) }
        composeTestRule.waitForIdle()
    }

    private fun MoodPattern.next(): MoodPattern =
        MoodPattern.entries[(ordinal + 1) % MoodPattern.entries.size]

    @Test
    fun `気分の組切替でも日替わり注記の縦位置が動かない`() {
        setHome()
        val baseline = noteTop()

        // 2回のスワイプで全3組を巡回＝どの組間に高低差があっても注記位置の不変を固定する。
        var current = START_PATTERN
        repeat(MoodPattern.entries.size - 1) {
            val previous = current
            swipeToNextPattern(previous)
            current = previous.next()
            // スワイプが空振りしていないことの証明は「前の組のカードが視界外＝破棄済み」で取る
            //（次の組の先頭カードは覗き見せでスワイプ前から存在し得るため existence では証明にならない）。
            composeTestRule.onNodeWithText(previous.presets[0].title).assertDoesNotExist()
            composeTestRule.onNodeWithText(current.presets[0].title).assertExists()
            assertEquals("組 $current への切替で注記が動いた", baseline, noteTop(), 0.5f)
        }
    }

    @Test
    fun `高さ予約ゴーストはsemanticsに漏れずカードのタップ結線は生きている`() {
        var picked: MoodPreset? = null
        setHome(onPickMood = { picked = it })
        // ゴースト（不可視の全組格子）が clearAndSetSemantics を失うと同名ノードが重複する。
        composeTestRule.onAllNodes(hasText(START_PATTERN.presets[0].title)).assertCountEquals(1)
        composeTestRule.onNodeWithText(START_PATTERN.presets[0].title).performClick()
        assertEquals(START_PATTERN.presets[0], picked)
    }

    companion object {
        /** 巡回の起点。どの組から始めても3組すべてを通るため、enum 先頭を基準点に採る。 */
        private val START_PATTERN = MoodPattern.CLASSIC
    }
}
