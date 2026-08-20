package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
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
import org.robolectric.annotation.GraphicsMode

/**
 * スキンK「さがす」の きょうの気分ページャ・高さ安定枠の回帰テスト（実機報告 2026-07-29 / 2026-08-20）。
 *
 * 固定するもの:
 *  1) 組（MoodPattern）ごとに文言の折返し行数＝ページ高が違っても、気分ブロック直下の
 *     日替わり注記の縦位置が組切替（循環スワイプ）で動かない＝下部レイアウトのがくん対策。
 *     3組すべてを巡回して検証する（どの組間の高低差でも破れないこと）。
 *  2) 同じ巡回で**枠の内側**のカード縦位置も動かない＝「枠内で上下にがくがく」対策（2026-08-20）。
 *     1) と 2) は別物で、1) だけでは枠内の揺れを通す（枠は動かず中身だけが動く形になる）。
 *  3) 高さ予約ゴースト（不可視の全組格子）が semantics に漏れない・実カードのタップ結線が生きている。
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
// NATIVE 必須（2026-08-20）: 既定の LEGACY は実フォントを使わない代用計測で、**どの組も同じ行数・同じ高さ**に
// なる。組ごとの高低差こそがこのファイルの検証対象なので、LEGACY で回すと本クラスは全緑のまま検出力ゼロだった
// （ゴーストを外しても・Pager がページを毎回センタリングし直しても緑）。qualifiers も実機同等の 360dp-xhdpi を
// 明示して、折返し行数を実機の条件に寄せる。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class DiscoveryHomeKMoodTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * [fontScale] は端末の文字サイズ設定の再現。折返し行数＝組ごとの高低差はスケールで拡大するため、
     * 縦位置の不変は等倍だけでなく拡大側でも押さえる（この検証機は 2.0 での破綻歴がある）。
     */
    private fun setHome(onPickMood: (MoodPreset) -> Unit = {}, fontScale: Float = 1f) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
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
    }

    /** 日替わり注記の上端 Y（dp）＝気分ブロック高の観測点。ここが動く＝下部全体ががくんと動く。 */
    private fun noteTop(): Float =
        composeTestRule.onNode(hasText("日替わり", substring = true))
            .getUnclippedBoundsInRoot().top.value

    /**
     * 現在組の先頭カード題字の上端 Y（dp）＝**枠の内側**でのカード縦位置の観測点。
     * 枠（安定枠 Box）自体が動かなくても、ここが組ごとに動けば「枠内で上下にがくがく」に見える。
     */
    private fun cardTop(pattern: MoodPattern): Float =
        composeTestRule.onNodeWithText(pattern.presets[0].title)
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

    /**
     * 枠内の縦位置不変（2026-08-20 実機報告「スワイプしていくと枠内で上下にがくがくと動く」の回帰）。
     *
     * 注記（枠の外）が動かないことは上のテストが押さえているが、それだけでは**枠の内側**の揺れを通す。
     * Pager の高さは viewport に居るページの最大高＝覗き見せがあるので隣の組の高さで毎回変わり、
     * verticalAlignment 既定の CenterVertically がその可変高の中で各ページを再センタリングしていた。
     */
    private fun assertCardTopStableAcrossPatterns(fontScale: Float) {
        setHome(fontScale = fontScale)
        val baseline = cardTop(START_PATTERN)

        var current = START_PATTERN
        repeat(MoodPattern.entries.size - 1) {
            val previous = current
            swipeToNextPattern(previous)
            current = previous.next()
            composeTestRule.onNodeWithText(previous.presets[0].title).assertDoesNotExist()
            assertEquals("組 $current で枠内のカード縦位置が動いた", baseline, cardTop(current), 0.5f)
        }
    }

    @Test
    fun `気分の組を送っても枠内のカード縦位置が動かない`() = assertCardTopStableAcrossPatterns(fontScale = 1f)

    @Test
    fun `文字サイズ2倍でも気分の組を送って枠内のカード縦位置が動かない`() =
        assertCardTopStableAcrossPatterns(fontScale = 2f)

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
