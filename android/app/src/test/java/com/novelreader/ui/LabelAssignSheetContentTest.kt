package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.novelreader.data.LabelEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LabelAssignSheetContent（U2 ラベル付与シートの中身）の結線テスト。
 * ModalBottomSheet ごとではなく Content を直接組むのは Robolectric で sheet 内の
 * assert/performClick が不安定なため（task_diary #50 の既定パターン）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelAssignSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun label(id: String, name: String) = LabelEntity(id = id, name = name, createdAt = 0L)

    private fun setContent(
        labels: List<LabelEntity> = emptyList(),
        assigned: Set<String> = emptySet(),
        onToggle: (String, Boolean) -> Unit = { _, _ -> },
        onCreateLabel: (String) -> Unit = {},
        onDeleteLabel: (LabelEntity) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                LabelAssignSheetContent(
                    bookTitle = "吾輩は猫である",
                    labels = labels,
                    assignedLabelIds = assigned,
                    onToggle = onToggle,
                    onCreateLabel = onCreateLabel,
                    onDeleteLabel = onDeleteLabel,
                )
            }
        }
    }

    @Test
    fun `付与済みはチェックON・未付与はOFFで表示されタップでonToggleが飛ぶ`() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        setContent(
            labels = listOf(label("l1", "異世界"), label("l2", "あとで読む")),
            assigned = setOf("l1"),
            onToggle = { id, checked -> toggles.add(id to checked) },
        )
        // Checkbox は2つ（l1=ON・l2=OFF）。行順は labels の順（createdAt 昇順を上流が保証）。
        val boxes = composeTestRule.onAllNodes(isToggleable())
        boxes[0].assertIsOn()
        boxes[1].assertIsOff()

        boxes[1].performClick()
        assertEquals(listOf("l2" to true), toggles)
        boxes[0].performClick()
        assertEquals(listOf("l2" to true, "l1" to false), toggles)
    }

    @Test
    fun `空白のみの入力では作成ボタンが押せず、入力すると作成が飛んで欄が空に戻る`() {
        var created: String? = null
        setContent(onCreateLabel = { created = it })

        composeTestRule.onNodeWithText("作成").assertIsNotEnabled()
        // placeholder の Text ではなく入力アクションを持つ TextField 本体へ打ち込む
        composeTestRule.onNode(hasSetTextAction()).performTextInput("完結済み")
        composeTestRule.onNodeWithText("作成").assertIsEnabled().performClick()
        assertEquals("完結済み", created)
        // 入力欄がクリアされ placeholder が再表示される（連続作成の導線）
        composeTestRule.onNodeWithText("新しいラベル名").assertIsDisplayed()
    }

    @Test
    fun `ラベル削除は確認ダイアログを経てonDeleteLabelが飛ぶ`() {
        var deleted: LabelEntity? = null
        setContent(
            labels = listOf(label("l1", "異世界")),
            onDeleteLabel = { deleted = it },
        )
        composeTestRule.onNodeWithContentDescription("ラベル「異世界」を削除").performClick()
        // 確認ダイアログの文言（全蔵書から外れることの明示）
        composeTestRule.onNodeWithText("ラベルの削除").assertIsDisplayed()
        composeTestRule.onNodeWithText("削除").performClick()
        assertEquals("l1", deleted?.id)
    }

    @Test
    fun `ラベルゼロのときは案内文を出す`() {
        setContent(labels = emptyList())
        composeTestRule.onAllNodes(isToggleable()).assertCountEquals(0)
        composeTestRule.onNodeWithText("ラベルはまだありません。下の入力欄から作成できます。").assertIsDisplayed()
    }
}
