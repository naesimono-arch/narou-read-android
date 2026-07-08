package com.novelreader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
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
    ) {
        composeTestRule.setContent {
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
