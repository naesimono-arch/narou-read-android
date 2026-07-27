package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.narou.SearchHistory
import com.novelreader.domain.SearchDraft
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DiscoverySearchContent（検索ホームの stateless 描画層）の描画＋コールバック結線テスト（ADR 0009）。
 * 検索範囲チップ・条件調整導線・検索実行の結線がサイレント退行しないことを固定する。「条件を調整」シート
 * （ModalBottomSheet／VM 依存）はルート層が持つため、ここでは onOpenConditionSheet の発火のみ検証する
 * （task_diary #50 によりシート内ノードの可視/クリック検証はしない）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverySearchContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        draft: SearchDraft = SearchDraft(),
        onExecuteSearch: () -> Unit = {},
        onOpenConditionSheet: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                DiscoverySearchContent(
                    draft = draft,
                    history = SearchHistory(),
                    onBack = {},
                    onSetDraft = {},
                    onExecuteSearch = onExecuteSearch,
                    onSearchHistoryWord = {},
                    onPinWord = {},
                    onUnpinWord = {},
                    onRemoveRecentWord = {},
                    onOpenConditionSheet = onOpenConditionSheet,
                )
            }
        }
    }

    @Test
    fun `検索範囲セクションと範囲チップを描画する`() {
        setContent()
        composeTestRule.onNodeWithText("検索範囲").assertIsDisplayed()
        composeTestRule.onNodeWithText("タイトル").assertIsDisplayed()
        composeTestRule.onNodeWithText("あらすじ").assertIsDisplayed()
    }

    @Test
    fun `条件を調整の押下でonOpenConditionSheetが呼ばれる`() {
        var opened = false
        setContent(onOpenConditionSheet = { opened = true })
        composeTestRule.onNodeWithText("条件を調整").performClick()
        assertTrue(opened)
    }

    @Test
    fun `検索語ありのとき検索アイコンでonExecuteSearchが呼ばれる`() {
        var executed = false
        // canSearch=true（word 非空）にして検索アイコンを enabled にする
        setContent(draft = SearchDraft(word = "異世界"), onExecuteSearch = { executed = true })
        composeTestRule.onNodeWithContentDescription("検索する").performClick()
        assertTrue(executed)
    }
}
