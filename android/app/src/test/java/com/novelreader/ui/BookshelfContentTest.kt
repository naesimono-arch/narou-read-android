package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
        onDeleteBooks: (List<BookEntity>) -> Unit = {},
        deferHeavyContent: Boolean = false,
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                BookshelfContent(
                    deferHeavyContent = deferHeavyContent,
                    uiState = uiState,
                    progressMap = progressMap,
                    chapterCountMap = chapterCountMap,
                    newEpisodeNovelMap = emptyMap(),
                    processingState = ProcessingState(),
                    appTheme = ReadingTheme.LIGHT,
                    onThemeChange = {},
                    isGridView = true,
                    onToggleView = {},
                    onFabClick = onFabClick,
                    onOpenBook = {},
                    onDeleteBooks = onDeleteBooks,
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
    fun `遷移中(deferHeavyContent)はカードをスケルトンへ差替えヘッダは残す`() {
        // P2 遷移ジャンク対策の配線担保: enter アニメ中は重い Lazy グリッドがコンポジションから外れ
        //（＝表紙カードが存在しない）、帯・フィルタのヘッダは実表示のまま残ることを固定する。
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))), deferHeavyContent = true)
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
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
        // ラベルは用語辞書（docs/patterns/discovery-terminology.md）＝着地画面名「見つける」に一致させた。
        composeTestRule.onNodeWithContentDescription("見つける").performClick()
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
    fun `0件の状態チップは dim(disabled)で押せず袋小路に落とさない`() {
        // ia Minor: よみかけ1件だけの棚では「未読」「読了」は 0 件。押せる状態のまま空表示に落とすのでなく、
        // enabled=false（dim）にして押下不能にする＝空表示の袋小路を先に塞ぐ。
        // なぜ よみかけ にするか: UNREAD 本はカード進捗行に「未読」文字を描き、チップ「未読」と onNodeWithText が
        // 衝突する。よみかけ進捗（N話 X%）ならカードに状態語が出ず、チップだけを一意に指せる。
        setContent(
            BookshelfUiState.Content(
                books = listOf(book("b1", "吾輩は猫である")),
            ),
            progressMap = mapOf("b1" to ProgressEntity("b1", "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10),
        )
        // よみかけは 1 件＝enabled。未読・読了は 0 件＝disabled（押せない）。
        composeTestRule.onNodeWithText("よみかけ").assertIsEnabled()
        composeTestRule.onNodeWithText("読了").assertIsNotEnabled()
        composeTestRule.onNodeWithText("未読").assertIsNotEnabled()
        // 読了を押しても絞り込まれない（本は出たまま・空文言は出ない）。
        composeTestRule.onNodeWithText("読了").performClick()
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onNodeWithText("この分類の本はありません").assertDoesNotExist()
    }

    // ────── 複数選択→まとめて削除（残8・案B裁定） ──────

    @Test
    fun `長押しで選択モードに入り削除確定でonDeleteBooksへ選択本が渡る`() {
        var deleted: List<BookEntity>? = null
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん"))),
            onDeleteBooks = { deleted = it },
        )
        // 長押しで選択モードへ（その本を選択）＝下端バーに件数と削除が出る。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1冊選択中").assertIsDisplayed()
        // 削除→確認ダイアログ→削除する で onDeleteBooks に選択本(b1)が渡る。
        composeTestRule.onNodeWithText("削除").performClick()
        composeTestRule.onNodeWithText("削除する").performClick()
        assertTrue(deleted?.map { it.id } == listOf("b1"))
    }
}
