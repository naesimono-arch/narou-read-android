package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.colors
import com.novelreader.ui.theme.tokens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ReadingSettingsSheetContent（表示設定シートの内容）の表示＋テーマ選択コールバックのテスト（ADR 0009）。
 * なぜシート枠（ModalBottomSheet）でなく Content を対象にするか: 枠は別ウィンドウ描画＋部分展開で
 * 下部が画面外に出るため、Robolectric では可視判定・クリック注入が不安定（実測で assertIsDisplayed／
 * performClick 経由の結線検証が落ちた）。テストの狙いは state+callback の葉である内容の検証なので、
 * 枠を剥がした Content を直接組む。値ラベルの表示（fontSize/lineHeight/margin の写経が崩れて
 * いないこと）とチップ結線という退行が痛い箇所に集中する（過剰網羅は避ける）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingSettingsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val colors = ReadingTheme.LIGHT.colors

    private fun setSheet(
        readingTheme: ReadingTheme = ReadingTheme.LIGHT,
        onThemeChange: (ReadingTheme) -> Unit = {},
        // スキンM の意匠分岐（テーマ固定表示・星のつまみ）検証用。既定 D＝既存テストは完全不変。
        skin: Skin = Skin.WAMODERN_D,
    ) {
        composeTestRule.setContent {
            // LocalSkin（意匠分岐）と LocalSkinTokens（テーマ節の畳み判定）は本番では対で供給される。
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                ReadingSettingsSheetContent(
                    colors = colors,
                    readingTheme = readingTheme,
                    onThemeChange = onThemeChange,
                    fontSize = 18,
                    onFontSizeChange = {},
                    onFontSizePersist = {},
                    lineHeightEm = 2.5f,
                    onLineHeightChange = {},
                    onLineHeightPersist = {},
                    bodyMarginDp = 20,
                    onBodyMarginChange = {},
                    onBodyMarginPersist = {},
                )
            }
        }
    }

    @Test
    fun `M装着ではテーマ3択の代わりに固定表示行を出しスライダー値は不変`() {
        setSheet(skin = Skin.SEIZU_M)
        // 固定表示（settings-M .theme-fixed）＝何が装着されているか＋変種切替の所在。
        composeTestRule.onNodeWithText("星図 ・ 夜の相").assertIsDisplayed()
        composeTestRule.onNodeWithText("ほかの装いは本棚の「装いの間」から").assertIsDisplayed()
        // 3択チップは出ない（1変種＝押しても変わらないチップを出さない）。
        composeTestRule.onNodeWithText("ライト").assertDoesNotExist()
        // ロジック共有の証左＝スライダー現在値は D と同一書式のまま。
        composeTestRule.onNodeWithText("18sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.5").assertIsDisplayed()
    }

    @Test
    fun `見出しとテーマ3択と各スライダーの現在値を表示する`() {
        setSheet()
        composeTestRule.onNodeWithText("表示設定").assertIsDisplayed()
        composeTestRule.onNodeWithText("ライト").assertIsDisplayed()
        composeTestRule.onNodeWithText("セピア").assertIsDisplayed()
        composeTestRule.onNodeWithText("ダーク").assertIsDisplayed()
        // 現在値ラベル（写経した書式のまま出ること）
        composeTestRule.onNodeWithText("18sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("20dp").assertIsDisplayed()
    }

    @Test
    fun `テーマチップのタップでonThemeChangeが該当テーマで呼ばれる`() {
        var picked: ReadingTheme? = null
        setSheet(onThemeChange = { picked = it })
        composeTestRule.onNodeWithText("セピア").performClick()
        assertEquals(ReadingTheme.SEPIA, picked)
    }
}
