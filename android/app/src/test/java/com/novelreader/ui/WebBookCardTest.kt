package com.novelreader.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
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
                    onRemove = {},
                    // グリッド書影は 2:3 で縦長。幅無制約だと丈がテスト viewport を超えメタ行が「未表示」判定に
                    // なるため実寸に近い幅を与える（意匠でなくテスト環境の都合）。
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        // 書影＝栞は Canvas 描画で題字が text ノードを持たず、contentDescription=title で識別する。
        composeTestRule.onNodeWithContentDescription("蜘蛛ですが、なにか？").assertExists()
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
                    onRemove = {},
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        // 書影（contentDescription=title）をタップして openCalled が発火するか検証。
        composeTestRule.onNodeWithContentDescription("蜘蛛ですが、なにか？").performClick()
        assertTrue(openCalled)
    }

    @Test
    fun `Grid 可視⋮メニューから onRemove と onImport が呼ばれる`() {
        var removeCalled = false
        var importCalled = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = { importCalled = true },
                    onRemove = { removeCalled = true },
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        // 系1: メニューはキャプション行右端の可視⋮から開く（旧・長押しは系3で複数選択の入口へ譲った）。
        // 1. 可視⋮をタップしてドロップダウンメニューを開く
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // 2. 「縦書きPDFを取り込む」をタップ
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").performClick()
        assertTrue(importCalled)

        // 3. もう一度⋮を開いて「本棚から外す」をタップ
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("本棚から外す").performClick()
        assertTrue(removeCalled)
    }

    @Test
    fun `Grid 長押しで onEnterSelection が呼ばれる（系3＝複数選択の入口）`() {
        var enterSelection = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {},
                    onEnterSelection = { enterSelection = true },
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        // 長押し＝選択モードへ（旧・長押しメニューは可視⋮へ移設済み）。
        composeTestRule.onNodeWithContentDescription("蜘蛛ですが、なにか？")
            .performTouchInput { longClick() }
        assertTrue(enterSelection)
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

    // ────── 機能②: 読書位置(lastReadEpisode>0)がある Web カードの「続きから」導線 ──────

    @Test
    fun `Grid 記録があると「続きから 第N話」が出て「なろう・未取込」は消える`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {},
                    lastReadEpisode = 52,
                    onResume = {},
                    // グリッドカードは書影 2:3 で縦長。幅無制約だと丈がテスト viewport を超えメタ行が「未表示」判定に
                    // なるため、実際のグリッド1枠に近い幅を与えて可視域に収める（意匠でなくテスト環境の都合）。
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        composeTestRule.onNodeWithText("続きから 第52話").assertIsDisplayed()
        // 記録があるカードは「なろう・未取込」ではなく続き導線に差し替わる。
        composeTestRule.onNodeWithText("なろう・未取込").assertDoesNotExist()
    }

    @Test
    fun `Grid 進捗あり＝カード本体タップで onResume（再開に統一・PDFと同じ身振り）`() {
        // continuity Major: 進捗ありの Web カードは主タップが再開へ統一され、目次(onOpen)は呼ばない。
        var openCalled = false
        var resumeCalled = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = { openCalled = true },
                    onImport = {},
                    onRemove = {},
                    lastReadEpisode = 52,
                    onResume = { resumeCalled = true },
                    // 上と同理由: グリッドの実寸に近い幅を与えて可視域に収める。
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        // 書影（=カード本体）タップで再開。
        composeTestRule.onNodeWithContentDescription("蜘蛛ですが、なにか？").performClick()
        assertTrue("進捗ありの主タップは onResume を呼ぶ", resumeCalled)
        assertTrue("進捗ありの主タップは目次(onOpen)を呼ばない", !openCalled)
    }

    @Test
    fun `Grid 進捗あり＝目次は⋮メニューへ降格して残る`() {
        // 主タップを再開に譲った目次導線は、長押しメニューの「なろうの目次を開く」から辿れる。
        var openCalled = false
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = { openCalled = true },
                    onImport = {},
                    onRemove = {},
                    lastReadEpisode = 52,
                    onResume = {},
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("なろうの目次を開く").performClick()
        assertTrue("⋮メニューの目次で onOpen が呼ばれる", openCalled)
    }

    @Test
    fun `Grid 未読＝目次項目は⋮メニューに出さない（主タップが目次のため）`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？")
        composeTestRule.setContent {
            MaterialTheme {
                WebGridBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {},
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        // 可視⋮を開いても、未読カードは主タップが目次のため「なろうの目次を開く」を出さない。
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("なろうの目次を開く").assertDoesNotExist()
    }

    @Test
    fun `List 記録があると「続きから 第N話」が出る`() {
        val novel = webNovel("n1234a", "蜘蛛ですが、なにか？", writer = "馬場翁")
        composeTestRule.setContent {
            MaterialTheme {
                WebListBookCard(
                    novel = novel,
                    onOpen = {},
                    onImport = {},
                    onRemove = {},
                    lastReadEpisode = 7,
                    onResume = {},
                )
            }
        }
        composeTestRule.onNodeWithText("続きから 第7話").assertIsDisplayed()
        composeTestRule.onNodeWithText("なろう・未取込").assertDoesNotExist()
    }
}
