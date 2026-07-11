package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
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

    private fun setContent(
        uiState: BookshelfUiState,
        // 読書状態フィルタは progress（読了度）と章数（分母）から状態を導くため、両方を差し込めるようにする。
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        onFabClick: () -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                BookshelfContent(
                    uiState = uiState,
                    progressMap = progressMap,
                    chapterCountMap = chapterCountMap,
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
        // 栞書影は題字を Canvas 描画するため text ノードを持たず、表紙の contentDescription=題名 で確認する。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
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

    // ────── 読書状態フィルタのチップ行（すべて/よみかけ/未読/読了） ──────

    @Test
    fun `蔵書ありなら固定4状態チップが出て「すべて」既定で全カード表示`() {
        // ラベルと違い状態チップは固定4個で常設（表示条件は FindGuideBand と同じ＝棚が非空なら出す）。
        // なぜ両本によみかけ進捗を与えるか: 未読カードは進捗行に「未読」を描くため、チップ「未読」と文字が
        // 衝突して onNodeWithText が複数ノードで落ちる。よみかけ進捗（N話 X%）にしてカード側の「未読」を消し、
        // 4チップが各1ノードで数えられるようにする。
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん")),
            ),
            progressMap = mapOf(
                "b1" to ProgressEntity("b1", "chap_3.html"),
                "b2" to ProgressEntity("b2", "chap_3.html"),
            ),
            chapterCountMap = mapOf("b1" to 10, "b2" to 10),
        )
        composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
        composeTestRule.onNodeWithText("よみかけ").assertIsDisplayed()
        composeTestRule.onNodeWithText("未読").assertIsDisplayed()
        composeTestRule.onNodeWithText("読了").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("坊っちゃん").assertIsDisplayed()
    }

    @Test
    fun `状態チップ選択で該当状態の本だけに絞り込まれ「すべて」で戻る`() {
        // b1=よみかけ（chap_3/全10章）・b2=未読（進捗なし）。「よみかけ」で b1 のみ残す。
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん")),
            ),
            progressMap = mapOf("b1" to ProgressEntity("b1", "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10, "b2" to 10),
        )
        composeTestRule.onNodeWithText("よみかけ").performClick()
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("坊っちゃん").assertCountEquals(0)

        composeTestRule.onNodeWithText("すべて").performClick()
        composeTestRule.onNodeWithContentDescription("坊っちゃん").assertIsDisplayed()
    }

    @Test
    fun `絞り込み0件は蔵書ゼロ扱いにせず該当なし文言を出す`() {
        // 進捗なし＝全て未読の棚で「読了」を選ぶと 0 件になる。
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である")),
            )
        )
        composeTestRule.onNodeWithText("読了").performClick()
        composeTestRule.onNodeWithText("この分類の本はありません").assertIsDisplayed()
        // 「蔵書ゼロ」の空状態と混同しない（EmptyBookshelf は出さない）
        composeTestRule.onNodeWithText("本棚はまだ空です").assertDoesNotExist()
        // チップ行は出続けて「すべて」へ戻れる
        composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
    }
}
