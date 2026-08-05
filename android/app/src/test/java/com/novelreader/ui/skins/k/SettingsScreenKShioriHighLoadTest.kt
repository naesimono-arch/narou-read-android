package com.novelreader.ui.skins.k

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
// onNode は SemanticsNodeInteractionsProvider のメソッド＝top-level import 不可（rule 経由で呼ぶ）
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 設定画面の開発節「栞アニメ（試作）」（ADR 0023 の明快K展開・2026-08-06 裁定）の露出ゲート。
 *
 * 星図M「高負荷スカイ（試作）」と同型の導線を、ADR 0027 決定4 の流儀（露出可否を引数で受ける）で
 * 両値とも JVM で固定する: debug（visible=true）×明快K でだけ行が出て、release（visible=false）では
 * 節ごと**存在しない**（assertDoesNotExist＝画面外でなく意味木から消えている）。トグル値も MainActivity 側で
 * BuildConfig.DEBUG と AND されるため、release は「行なし×値 false」の二重で常時 OFF が保たれる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKShioriHighLoadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSettings(
        rowVisible: Boolean,
        skin: Skin = Skin.MEIKAI_K,
        onChange: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            NovelReaderTheme(skin = skin, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = skin,
                    onOpenWardrobe = {},
                    skinSwitchingEnabled = true,
                    shioriHighLoadRowVisible = rowVisible,
                    shioriHighLoadK = false,
                    onShioriHighLoadChange = onChange,
                )
            }
        }
    }

    @Test
    fun `debug×明快K＝開発節と栞アニメ行が出る`() {
        setSettings(rowVisible = true)
        // assertIsDisplayed でなく assertExists: 設定リストは長く、開発節は 640dp ビューポートの外に
        // 置かれうる（スクロールで到達可能なら「出ている」）。不在側の対は assertDoesNotExist で厳密。
        composeTestRule.onNodeWithText("開発").assertExists()
        composeTestRule.onNodeWithText("栞アニメ（試作）").assertExists()
    }

    @Test
    fun `release側（visible=false）＝節ごと存在しない`() {
        setSettings(rowVisible = false)
        composeTestRule.onNodeWithText("開発").assertDoesNotExist()
        composeTestRule.onNodeWithText("栞アニメ（試作）").assertDoesNotExist()
    }

    @Test
    fun `明快K以外の装いでは出さない（K本棚の栞にしか効かないノブ）`() {
        setSettings(rowVisible = true, skin = Skin.WAMODERN_D)
        composeTestRule.onNodeWithText("栞アニメ（試作）").assertDoesNotExist()
    }

    @Test
    fun `トグル操作は onShioriHighLoadChange へ届く`() {
        var received: Boolean? = null
        setSettings(rowVisible = true, onChange = { received = it })
        // Switch は toggleable の併合境界＝行（merge 済みでラベル文字列を持つ）の下の別ノードとして残る。
        // 通知トグルなど他の Switch と区別するため「栞アニメ行を祖先に持つ toggleable」で特定する。
        composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasText("栞アニメ（試作）", substring = true)))
            .performScrollTo()
            .performClick()
        assertEquals(true, received)
    }
}
