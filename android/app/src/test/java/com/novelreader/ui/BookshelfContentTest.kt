package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.data.BookEntity
import com.novelreader.data.LabelEntity
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BookshelfContent（本棚の stateless 描画層）の状態分岐＋主要コールバック結線テスト（ADR 0009）。
 * state-holder / UI 分割で VM から切り出した葉が対象。Loading/Content(空)/Content(蔵書あり) の
 * 描画分岐（cold start の空フラッシュ対策 F-O の要）と、検索導線・追加導線の結線がサイレント退行
 * しないことを固定する。プラットフォーム副作用（PDF 選択・権限・バッテリー）はルート層 BookshelfScreen
 * が持つためここでは検証しない（過剰網羅を避ける）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String) =
        BookEntity(id = id, title = title, htmlDirPath = "/nonexistent/$id")

    private fun label(id: String, name: String) =
        LabelEntity(id = id, name = name, createdAt = 0L)

    private fun setContent(
        uiState: BookshelfUiState,
        onFabClick: () -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                BookshelfContent(
                    uiState = uiState,
                    progressMap = emptyMap(),
                    newEpisodeNovelMap = emptyMap(),
                    processingState = ProcessingState(),
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    isGridView = true,
                    onToggleView = {},
                    deleteUiMode = 1,
                    onToggleDeleteMode = {},
                    onFabClick = onFabClick,
                    onOpenBook = {},
                    onDeleteBook = {},
                    onOpenDiscovery = onOpenDiscovery,
                    onCancelProcessing = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    @Test
    fun `Content(空)ではEmptyBookshelfを出し見つける導線帯は出さない`() {
        setContent(BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithText("本棚はまだ空です").assertIsDisplayed()
        // 空棚では帯と EmptyBookshelf が重なるため帯は出さない設計
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
    }

    @Test
    fun `Loading中は空メッセージを出さない（cold startの空フラッシュ対策）`() {
        setContent(BookshelfUiState.Loading)
        // Loading はスケルトンのみ。Content(空) が確定するまで空状態を出さない
        composeTestRule.onNodeWithText("本棚はまだ空です").assertDoesNotExist()
    }

    @Test
    fun `Content(蔵書あり)では書名と見つける導線帯を出し空メッセージは出さない`() {
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // 書影焼き込み＋メタの2箇所に題字が出るため onAllNodes の先頭で存在を確認する
        composeTestRule.onAllNodesWithText("吾輩は猫である").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("本棚はまだ空です").assertDoesNotExist()
    }

    @Test
    fun `検索アイコンでonOpenDiscovery・追加ボタンでonFabClickが呼ばれる`() {
        var openedDiscovery = false
        var fabClicked = false
        setContent(
            BookshelfUiState.Content(emptyList()),
            onFabClick = { fabClicked = true },
            onOpenDiscovery = { openedDiscovery = true },
        )
        composeTestRule.onNodeWithContentDescription("小説を探す").performClick()
        assertTrue(openedDiscovery)
        // 空状態の「PDFを追加する」ボタンも FAB と同じ onFabClick を叩く
        composeTestRule.onNodeWithText("PDFを追加する").performClick()
        assertTrue(fabClicked)
    }

    // ────── U2 ラベル絞り込みチップ行 ──────

    @Test
    fun `ラベル未作成ならチップ行を行ごと出さない（モック仕様）`() {
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        composeTestRule.onNodeWithText("すべて").assertDoesNotExist()
    }

    @Test
    fun `ラベルがあればチップ行が出て「すべて」が既定選択で全カード表示`() {
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん")),
                labels = listOf(label("l1", "異世界")),
                bookLabelIds = mapOf("b1" to setOf("l1")),
            )
        )
        composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
        composeTestRule.onNodeWithText("異世界").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("吾輩は猫である").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("坊っちゃん").onFirst().assertIsDisplayed()
    }

    @Test
    fun `ラベルチップ選択で付与済みの本だけに絞り込まれ「すべて」で戻る`() {
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん")),
                labels = listOf(label("l1", "異世界")),
                bookLabelIds = mapOf("b1" to setOf("l1")),
            )
        )
        composeTestRule.onNodeWithText("異世界").performClick()
        composeTestRule.onAllNodesWithText("吾輩は猫である").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("坊っちゃん").assertCountEquals(0)

        composeTestRule.onNodeWithText("すべて").performClick()
        composeTestRule.onAllNodesWithText("坊っちゃん").onFirst().assertIsDisplayed()
    }

    @Test
    fun `絞り込み0件は蔵書ゼロ扱いにせず該当なし文言を出す`() {
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である")),
                labels = listOf(label("l1", "異世界"), label("l2", "あとで読む")),
                bookLabelIds = mapOf("b1" to setOf("l1")),
            )
        )
        composeTestRule.onNodeWithText("あとで読む").performClick()
        composeTestRule.onNodeWithText("このラベルの本はありません").assertIsDisplayed()
        // 「蔵書ゼロ」の空状態と混同しない（EmptyBookshelf は出さない）
        composeTestRule.onNodeWithText("本棚はまだ空です").assertDoesNotExist()
        // チップ行は出続けて「すべて」へ戻れる
        composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
    }
}
