package com.novelreader.ui.skins.k

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 設定画面の開発節「栞先端を固定（観察）」（2026-08-25）の露出ゲートと操作の届き先。
 *
 * 露出は既存の「栞アニメ（試作）」と同じ節＝可否も同じ引数（ADR 0027 決定4 の流儀で BuildConfig を
 * 画面から直読みしない）。release 側（visible=false）では**節ごと存在しない**ことを assertDoesNotExist で
 * 固定する＝観察器が出荷ビルドの設定画面に現れない不変条件。
 *
 * 送りボタンは実機で片手のまま隣の tip へ渡り歩くための導線なので、「解除→0」「0→隣」「解除で null」の
 * 3点を届き先まで通して固定する（ラベルだけ在って配線が繋がっていない事故を防ぐ）。
 */
@RunWith(RobolectricTestRunner::class)
// 送りボタン5個が実機幅に収まるかの断言は文字の実測幅に依るので NATIVE（legacy 影は glyph 幅を持たない）。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKShioriDebugTipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSettings(
        rowVisible: Boolean,
        tipIndex: Int? = null,
        onChange: (Int?) -> Unit = {},
    ) {
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    // 「データ」節の〈診断の記録〉行の飛び先。この観点では叩かないので no-op。
                    onOpenDiagnosticsExport = {},
                    skinSwitchingEnabled = true,
                    shioriHighLoadRowVisible = rowVisible,
                    shioriDebugTipIndex = tipIndex,
                    onShioriDebugTipChange = onChange,
                )
            }
        }
    }

    @Test
    fun `debug では固定行と現在値が出る`() {
        setSettings(rowVisible = true, tipIndex = 3)
        composeTestRule.onNodeWithText("栞先端を固定（観察）").assertExists()
        composeTestRule.onNodeWithText("tip 3 に固定中", substring = true).assertExists()
    }

    @Test
    fun `固定なしの表示は「固定しない」`() {
        setSettings(rowVisible = true, tipIndex = null)
        composeTestRule.onNodeWithText("固定しない", substring = true).assertExists()
    }

    @Test
    fun `release側（visible=false）＝固定行ごと存在しない`() {
        setSettings(rowVisible = false, tipIndex = 3)
        composeTestRule.onNodeWithText("栞先端を固定（観察）").assertDoesNotExist()
        composeTestRule.onNodeWithText("tip 3 に固定中", substring = true).assertDoesNotExist()
    }

    @Test
    fun `送りは「解除→tip0→隣」・解除は null を返す`() {
        // setContent は1テストにつき1回しか呼べないため、実機と同じ「押す→値が戻ってくる→行が更新される」
        // 一巡を state 駆動で組み、同じ画面のまま連続して送る。
        var received: Int? = -1
        var current by mutableStateOf<Int?>(null)
        composeTestRule.setContent {
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    // 「データ」節の〈診断の記録〉行の飛び先。この観点では叩かないので no-op。
                    onOpenDiagnosticsExport = {},
                    skinSwitchingEnabled = true,
                    shioriHighLoadRowVisible = true,
                    shioriDebugTipIndex = current,
                    onShioriDebugTipChange = { received = it; current = it },
                )
            }
        }

        composeTestRule.onNodeWithText("＋1").performScrollTo().performClick()
        assertEquals(0, received) // 解除 → tip 0（高負荷アニメの先頭へ1タップで入れる）
        composeTestRule.onNodeWithText("＋1").performScrollTo().performClick()
        assertEquals(1, received) // 0 → 1（隣へ渡り歩ける）
        composeTestRule.onNodeWithText("解除").performScrollTo().performClick()
        assertNull(received)
        composeTestRule.onNodeWithText("固定しない", substring = true).assertExists()
    }

    @Test
    fun `送りボタン5個が360dp幅に収まる（実機で解除まで押せる）`() {
        // なぜ機械で見るか: 溢れて右端が画面外へ出ても Robolectric のクリックは通ってしまい、
        // テストは緑のまま実機だけが「解除が押せない」行き止まりになる（観察器としては致命）。
        setSettings(rowVisible = true, tipIndex = 173) // 説明文が最長になる状態で見る
        val right = composeTestRule.onNodeWithText("解除").performScrollTo().getUnclippedBoundsInRoot().right
        assertTrue("送りの列が画面幅を超えている: 解除の右端=$right", right <= 360.dp)
    }
}
