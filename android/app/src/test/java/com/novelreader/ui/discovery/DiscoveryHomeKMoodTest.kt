package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.Skin
import com.novelreader.viewmodel.DiscoveryUiState
import com.novelreader.viewmodel.MoodPattern
import com.novelreader.viewmodel.MoodPreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * スキンK「さがす」の きょうの気分ページャ・高さ安定枠の回帰テスト（実機報告 2026-07-29）。
 *
 * 固定するもの:
 *  1) 組（MoodPattern）ごとに文言の折返し行数＝ページ高が違っても、気分ブロック直下の
 *     日替わり注記の縦位置が組切替（循環スワイプ）で動かない＝下部レイアウトのがくん対策。
 *     3組すべてを巡回して検証する（どの組間の高低差でも破れないこと）。
 *  2) 高さ予約ゴースト（不可視の全組格子）が semantics に漏れない・実カードのタップ結線が生きている。
 *
 * LocalSkin を直接 provide して DiscoveryHomeContent のルーター分岐から K を検証する（DiscoverySkyMTest と同型）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryHomeKMoodTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setHome(onPickMood: (MoodPreset) -> Unit = {}) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides Skin.MEIKAI_K) {
                MaterialTheme {
                    DiscoveryHomeContent(
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
                    )
                }
            }
        }
    }

    /** 日替わり注記の上端 Y（dp）＝気分ブロック高の観測点。ここが動く＝下部全体ががくんと動く。 */
    private fun noteTop(): Float =
        composeTestRule.onNode(hasText("日替わり", substring = true))
            .getUnclippedBoundsInRoot().top.value

    /** 現在ページ（today から [step] 組先・循環）の先頭カード上で左スワイプ＝次の組へ送る。 */
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
        val today = MoodPattern.forEpochDay(LocalDate.now().toEpochDay())
        val baseline = noteTop()

        // 2回のスワイプで全3組を巡回＝どの組間に高低差があっても注記位置の不変を固定する。
        var current = today
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
        val today = MoodPattern.forEpochDay(LocalDate.now().toEpochDay())
        // ゴースト（不可視の全組格子）が clearAndSetSemantics を失うと同名ノードが重複する。
        composeTestRule.onAllNodes(hasText(today.presets[0].title)).assertCountEquals(1)
        composeTestRule.onNodeWithText(today.presets[0].title).performClick()
        assertEquals(today.presets[0], picked)
    }
}
