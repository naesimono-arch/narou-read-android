package com.novelreader.ui.skins.k

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 設定画面の公開スコープ機能ゲート（ADR 0027 適用点1）＝「きせかえ」行の出し分け。
 *
 * なぜ golden でなくここで縛るか: golden（[SettingsScreenKScreenshotTest]）は debug の BuildConfig で
 * 撮った1枚しか持てず、**行が消えている側**を1枚も持てない。フラグを引数で受ける形にしてあるので、
 * ここでは両値を同じ入口から通して「on では出る／off では存在しない」を構造で固定する（ADR 0027 決定4）。
 *
 * テーマ行を同時に見るのは、隠す軸を取り違えていないことの確認: テーマ（ライト/セピア/ダーク）は Skin と
 * 独立した軸で公開ビルドでも残す＝ここまで消すとダークモードが失われ明確な後退になる（ADR 0027 制約）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKSkinGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSettings(skinSwitchingEnabled: Boolean) {
        composeTestRule.setContent {
            // 実画面と同じ入口（skin=K）で包む＝行の有無以外の条件を実機と揃える。
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = Skin.MEIKAI_K,
                    onOpenWardrobe = {},
                    skinSwitchingEnabled = skinSwitchingEnabled,
                )
            }
        }
    }

    @Test
    fun `ゲートon(開発ビルド)＝きせかえ行が出る`() {
        setSettings(skinSwitchingEnabled = true)
        composeTestRule.onNodeWithText("きせかえ").assertIsDisplayed()
        composeTestRule.onNodeWithText("テーマ").assertIsDisplayed()
    }

    @Test
    fun `ゲートoff(公開ビルド)＝きせかえ行が存在しない・テーマ行は残る`() {
        setSettings(skinSwitchingEnabled = false)
        // assertIsNotDisplayed でなく assertDoesNotExist＝画面外に押し出されただけ（スクロールすれば押せる）
        // では隠したことにならないため、意味木から消えていることを要求する。
        composeTestRule.onNodeWithText("きせかえ").assertDoesNotExist()
        composeTestRule.onNodeWithText("テーマ").assertIsDisplayed()
    }
}
