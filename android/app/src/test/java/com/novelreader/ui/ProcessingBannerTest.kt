package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.viewmodel.ProcessingSource
import com.novelreader.viewmodel.ProcessingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 取込中バナーの供給元出し分けの契約（2026-07-29 裁定②）。
 * PDF＝4段ステップ変換の器（ステッパー・進捗バー・「ステップ n/4」）を出す。
 * WEB＝章単位取得でステップ概念が無く、器を出すと「ステップ 1/4」「タイトル」で凍結表示になるため
 * 出さない（章進行は phase 行「章 i/N 取得中」へ一本化＝既存バナー語彙の出し分けのみ）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessingBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setBanner(state: ProcessingState) {
        composeTestRule.setContent {
            MaterialTheme {
                ProcessingBanner(processingState = state, onStop = {})
            }
        }
    }

    @Test
    fun `PDF - ステッパー（ラベル4語とステップ計数）を表示する`() {
        setBanner(
            ProcessingState(
                isProcessing = true, stepIndex = 0, title = "PDF本",
                phase = "本文を読み込んでいます… 45%（3/12ページ）",
            )
        )
        composeTestRule.onNodeWithText("ステップ 1/4").assertExists()
        composeTestRule.onNodeWithText("タイトル").assertExists() // ステッパーの先頭ラベル
        composeTestRule.onNodeWithText("HTML").assertExists()     // ステッパーの末尾ラベル
    }

    @Test
    fun `WEB - ステッパーを出さず章進行（phase）と停止だけを出す`() {
        setBanner(
            ProcessingState(
                isProcessing = true, title = "Web小説", phase = "章 2/5 取得中",
                source = ProcessingSource.WEB,
            )
        )
        // 凍結表示の原因だった PDF の器は WEB では描かない（裁定②の真因固定）。
        composeTestRule.onNodeWithText("ステップ 1/4").assertDoesNotExist()
        composeTestRule.onNodeWithText("タイトル").assertDoesNotExist()
        composeTestRule.onNodeWithText("HTML").assertDoesNotExist()
        // 既存の章進行表示へ一本化（題名・phase・停止は従来どおり）。
        composeTestRule.onNodeWithText("Web小説").assertExists()
        composeTestRule.onNodeWithText("章 2/5 取得中").assertExists()
        composeTestRule.onNodeWithText("停止").assertExists()
    }

    @Test
    fun `WEB - 停止中は「停止しています…」を出し停止ボタンを隠す（PDF と同じ意味論）`() {
        setBanner(
            ProcessingState(
                isProcessing = true, title = "Web小説", phase = "章 2/5 取得中",
                isStopping = true, source = ProcessingSource.WEB,
            )
        )
        composeTestRule.onNodeWithText("停止しています…").assertExists()
        composeTestRule.onNodeWithText("停止").assertDoesNotExist()
    }
}
