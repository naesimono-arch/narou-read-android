package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        // なぜ既定値でなく明示の draft か: 既定は 2026-08-21 に4項目 ON へ変わった（placeholder の約束との
        // 食い違いを直した）ため、既定＝最後の1つ ではなくなった。この番人が測るのは「ONが1つのときの振る舞い」
        // なので、その状態を自前で組み立てて既定値の変更から独立させる。
        setStatefulContent(SearchDraft(inTitle = true, inStory = false, inKeyword = false, inWriter = false))
        composeTestRule.onNodeWithText("タイトル").assertIsSelected().assertIsEnabled()
        composeTestRule.onNodeWithText("タイトル").performClick()
        composeTestRule.onNodeWithText("タイトル").assertIsSelected().assertIsEnabled()
        composeTestRule.onNodeWithText("検索範囲は1つ以上必要です").assertIsDisplayed()
    }

    @Test
    fun `最後の1つの範囲チップは外せないことをstateDescriptionで読み上げる`() {
        // disabled を外すと TalkBack の「無効」が消えるため、その補填が落ちていないことを固定する（a11y の後退防止）。
        // 同上（既定は4項目 ON なので「ONが1つ」の状態は明示で作る）。
        setStatefulContent(SearchDraft(inTitle = true, inStory = false, inKeyword = false, inWriter = false))
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
        // 残り2つ＝ロックが効かない最小の状態。既定が4項目 ON になった後も「2つ」を測るため4項目とも明示する
        //（明示しないと inKeyword/inWriter が既定の true で入り、名前どおりの2つ選択にならない）。
        setStatefulContent(SearchDraft(inTitle = true, inStory = true, inKeyword = false, inWriter = false))
        composeTestRule.onNodeWithText("あらすじ").performClick()
        composeTestRule.onNodeWithText("あらすじ").assertIsNotSelected()
        composeTestRule.onNodeWithText("タイトル").assertIsSelected()
    }

    // 実機と同じ幅360dpで測る。高さを盛るのは、fontScale 2.0 だと履歴節が既定画面（h470dp）の外へ出て
    // 未配置＝boundsInRoot が Rect.Zero になり、当たり判定を測れないため（画面外ノードは絵にも a11y にも
    // 乗らない＝docs/knowledge/compose-offscreen-nodes-pruned-from-a11y-tree.md）。
    @Config(qualifiers = "w360dp-h1200dp-xhdpi")
    @Test
    fun `履歴チップの語・ピン・×は互いに重ならず各48dp以上の当たり判定を持つ`() {
        // 2026-08-17 の実機ダンプ（fontScale 2.0）で「×が語句チップへ16px食い込む」「ピンの当たり幅が44dp」
        // として現れた欠陥の番人。機序は本番側 HistoryChip のコメント（実寸48dp未満のノードは当たり判定が
        // 左右へ均等拡張され、隣の実寸領域へ潜り込んだ帯は隣の勝ちになる＝取り消し導線の無い削除へ誤着弾）。
        // 短い語ほど拡張量が大きいので1文字語を撮る。GraphicsMode 既定(LEGACY)は文字幅を字数近似で返す
        //（docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md）＝どの語でも
        // 「実寸が48dp未満に落ちる」最悪ケースを常に踏む条件になるため、この番人にはむしろ好都合。
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 2.0f,
                ),
            ) {
                MaterialTheme {
                    DiscoverySearchContent(
                        draft = SearchDraft(),
                        history = SearchHistory(recent = listOf("剣")),
                        onBack = {},
                        onSetDraft = {},
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
        val word = composeTestRule.onNodeWithText("剣").fetchSemanticsNode()
        val pin = composeTestRule.onNodeWithContentDescription("ピン留めする").fetchSemanticsNode()
        val delete = composeTestRule.onNodeWithContentDescription("履歴から削除").fetchSemanticsNode()
        // touchBoundsInRoot＝実際の当たり判定（実寸が最小標的未満なら拡張済みの矩形）。
        val minPx = with(composeTestRule.density) { 48.dp.toPx() }
        listOf("語" to word, "ピン" to pin, "×" to delete).forEach { (name, node) ->
            val b = node.touchBoundsInRoot
            assertTrue("$name の当たり判定が48dp未満: $b", b.width >= minPx && b.height >= minPx)
        }
        assertTrue(
            "語とピンの当たり判定が重なっている: ${word.touchBoundsInRoot} / ${pin.touchBoundsInRoot}",
            !word.touchBoundsInRoot.overlaps(pin.touchBoundsInRoot),
        )
        assertTrue(
            "語と×の当たり判定が重なっている: ${word.touchBoundsInRoot} / ${delete.touchBoundsInRoot}",
            !word.touchBoundsInRoot.overlaps(delete.touchBoundsInRoot),
        )
    }
}
