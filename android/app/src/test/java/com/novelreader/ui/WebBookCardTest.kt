package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.data.WebNovelEntity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WebBookCard (WebGridBookCard / WebListBookCard) の UI 結線・非表示条件の Robolectric テスト。
 * 未取り込みカードに進捗情報が表示されないこと、および操作メニューのコールバック結線を確認する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebBookCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun webNovel(ncode: String, title: String, writer: String = "作者") =
        WebNovelEntity(
            ncode = ncode,
            title = title,
            writer = writer,
            generalAllNo = 10,
            addedAt = System.currentTimeMillis()
        )

    @Test
    fun `Grid タイトルと「なろう・未取込」が表示される`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {}
                )
            }
        }
        // 書影内とカード下の2箇所にタイトルが表示されるため、onAllNodes の先頭で存在を確認する
        composeTestRule.onAllNodesWithText("蜘蛛ですが、なにか？").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("なろう・未取込").assertIsDisplayed()
    }

    @Test
    fun `Grid 進捗%テキストが存在しないこと`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {}
                )
            }
        }
        // 未取り込みのため進捗系の表現が存在しないことを確認
        composeTestRule.onNodeWithText("話", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("%", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("未読").assertDoesNotExist()
    }

    @Test
    fun `Grid カードタップで onOpen が呼ばれる`() {
        var openCalled = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = { openCalled = true },
                    onImport = {},
                    onRemove = {}
                )
            }
        }
        // タイトル表示部分をクリックして openCalled が発火するか検証
        composeTestRule.onAllNodesWithText("蜘蛛ですが、なにか？").onFirst().performClick()
        assertTrue(openCalled)
    }

    @Test
    fun `Grid メニューから onRemove と onImport が呼ばれる`() {
        var removeCalled = false
        var importCalled = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = { importCalled = true },
                    onRemove = { removeCalled = true }
                )
            }
        }

        // 1. ⋮ ボタンをタップしてドロップダウンメニューを開く
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()

        // 2. 「縦書きPDFを取り込む」をタップ
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").performClick()
        assertTrue(importCalled)

        // 3. もう一度メニューを開いて「本棚から外す」をタップ
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("本棚から外す").performClick()
        assertTrue(removeCalled)
    }

    @Test
    fun `List タイトル・作者・「なろう・未取込」が表示される`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？", writer = "馬場翁")
        composeTestRule.setContent {
            MaterialTheme {
                WebListBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {}
                )
            }
        }
        composeTestRule.onNodeWithText("蜘蛛ですが、なにか？").assertIsDisplayed()
        composeTestRule.onNodeWithText("馬場翁").assertIsDisplayed()
        composeTestRule.onNodeWithText("なろう・未取込").assertIsDisplayed()
    }
}
