package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

    /**
     * onSetDraft を state へ書き戻す＝実画面と同じ「押す→ドメインが決めた次の draft で再描画」の輪を閉じる。
     * 最後の1つの保護は SearchDraft.withRangeToggled が持つため、UI 側の押下抑止を外しても輪が回れば外れない。
     */
    private fun setStatefulContent(initial: SearchDraft) {
        composeTestRule.setContent {
            MaterialTheme {
                var draft by remember { mutableStateOf(initial) }
                DiscoverySearchContent(
                    draft = draft,
                    history = SearchHistory(),
                    onBack = {},
                    onSetDraft = { draft = it },
                    onExecuteSearch = {},
                    onSearchHistoryWord = {},
                    onPinWord = {},
                    onUnpinWord = {},
                    onRemoveRecentWord = {},
                    onOpenConditionSheet = {},
                )
            }
        }
    }

    @Test
    fun `最後の1つの範囲チップは押しても選択が外れず淡色化もされない`() {
        // 案A（2026-08-07 ユーザー裁定）: disabled による淡色化は「選択から外れた」と読めるため廃止した。
        // 見た目（選択済み＝藍）と押下可能性を保ったまま、押しても外れないことを固定する。
        setStatefulContent(SearchDraft()) // 既定＝タイトルのみ ON＝最後の1つ
        composeTestRule.onNodeWithText("タイトル").assertIsSelected().assertIsEnabled()
        composeTestRule.onNodeWithText("タイトル").performClick()
        composeTestRule.onNodeWithText("タイトル").assertIsSelected().assertIsEnabled()
        composeTestRule.onNodeWithText("検索範囲は1つ以上必要です").assertIsDisplayed()
    }

    @Test
    fun `最後の1つの範囲チップは外せないことをstateDescriptionで読み上げる`() {
        // disabled を外すと TalkBack の「無効」が消えるため、その補填が落ちていないことを固定する（a11y の後退防止）。
        setStatefulContent(SearchDraft())
        composeTestRule.onNodeWithText("タイトル").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "選択中。検索範囲は1つ以上必要なため、これ以上外せません",
            )
        )
        // 最後の1つでないチップには付けない（＝ロックの語が常時読み上げられてしまわない）。
        composeTestRule.onNodeWithText("あらすじ").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription).not()
        )
    }

    @Test
    fun `2つ選択中の範囲チップは押すと外れる`() {
        setStatefulContent(SearchDraft(inTitle = true, inStory = true))
        composeTestRule.onNodeWithText("あらすじ").performClick()
        composeTestRule.onNodeWithText("あらすじ").assertIsNotSelected()
        composeTestRule.onNodeWithText("タイトル").assertIsSelected()
    }
}
