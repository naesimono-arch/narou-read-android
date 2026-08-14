package com.novelreader.ui.skins.k

import androidx.compose.runtime.mutableStateOf
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
 *
 * 「行が現在の装い名を表示する」契約もここが持つ（2026-08-14 に値を副文から trailing へ移した際、
 * この契約を持っていた WardrobeRowDescriptionTest が役目を終えて消えたため引き取った）。golden だけに
 * 任せられない理由は上と同じ＝画素は明快1件しか張っておらず、他の装い名で写し先が壊れても誰も気付かない。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenKSkinGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * 装着中の装い。テスト1本につき `setContent` は1回しか呼べないため、全スキンを1本で通すには
     * 引数でなく状態として持ち替える必要がある（値を変えれば行が組み直される）。
     */
    private val currentSkin = mutableStateOf(Skin.MEIKAI_K)

    private fun setSettings(skinSwitchingEnabled: Boolean) {
        composeTestRule.setContent {
            // 包む装いは K で固定する（＝行の有無・値以外の条件を動かさない）。ここで見たいのは
            // 「currentSkin 引数が行の右端へ写ること」で、それは装いの意匠とは独立に成立すべき契約。
            NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                SettingsScreenK(
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    followingSystem = false,
                    onFollowSystem = {},
                    currentSkin = currentSkin.value,
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

    @Test
    fun `きせかえ行の右端に現在の装い名が出る（全スキン）`() {
        setSettings(skinSwitchingEnabled = true)
        Skin.entries.forEach { skin ->
            currentSkin.value = skin
            composeTestRule.waitForIdle()
            // 行そのものと値を対で見る＝行が消えたのに値だけ残る/その逆を、どちらの向きでも取り逃さない。
            composeTestRule.onNodeWithText("きせかえ").assertIsDisplayed()
            // 名前の長さに依存しないことまで含めて全スキンを回す（旧 WardrobeRowDescriptionTest から継承）。
            // 完全一致マッチなので、副文や他行の語（「…「きせかえ」から選べます」等）とは衝突しない。
            composeTestRule.onNodeWithText(skin.displayName).assertIsDisplayed()
        }
    }
}
