package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.tokens
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * スキンJ「ポータル」の本棚ルーター（ADR 0022 §1）＋ BookshelfPortalJ の描画分岐・結線テスト。
 *
 * 固定するもの:
 *  1) J 装着×デッキモードで D 構造でなく J ポータルデッキ（横スワイプ扉・スワイプヒント）が出ること／
 *     D 装着では従来描画が不変なこと
 *  2) デッキ⇄一覧トグルの結線（デッキ内のグリッドボタン・一覧側のデッキボタンの両方向）
 *  3) hero（よみかけ先頭）の「続きから読む」＋開く結線／未読は「読む」で「続きから読む」は出ない出し分け
 *  4) J（3変種スキン）の⋮メニューでテーマ節が出ること（M の1変種畳みとの対比＝supportedThemes 単一真実源）
 *  5) 取込中＝扉を仕立てているバナー・装い/見つける導線・PDF追加(メニュー移植)の結線
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、Theme の SideEffect（window 直叩き）を
 * テストから切り離しルーター分岐だけを検証するため（トークン束の契約は SkinMPJTest が担う）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfPortalJTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String, author: String = "") =
        BookEntity(id = id, title = title, author = author, htmlDirPath = "/nonexistent/$id")

    private fun setContent(
        skin: Skin,
        uiState: BookshelfUiState,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        deckViewJ: Boolean = true,
        onToggleDeckJ: () -> Unit = {},
        onOpenBook: (BookEntity) -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
        onOpenWardrobe: () -> Unit = {},
        onFabClick: () -> Unit = {},
        processingState: ProcessingState = ProcessingState(),
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = uiState,
                        progressMap = progressMap,
                        chapterCountMap = chapterCountMap,
                        newEpisodeNovelMap = emptyMap(),
                        processingState = processingState,
                        appTheme = ReadingTheme.DARK,
                        onThemeChange = {},
                        isGridView = false,
                        onToggleView = {},
                        onFabClick = onFabClick,
                        onOpenBook = onOpenBook,
                        onDeleteBooks = {},
                        onOpenDiscovery = onOpenDiscovery,
                        onOpenWardrobe = onOpenWardrobe,
                        onCancelProcessing = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        deckViewJ = deckViewJ,
                        onToggleDeckJ = onToggleDeckJ,
                    )
                }
            }
        }
    }

    @Test
    fun `J装着×デッキモードではポータルデッキが出てD構造は出ない`() {
        setContent(Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // J デッキの署名＝横スワイプヒント（この画面固有の文言）。
        composeTestRule.onNodeWithText("← スワイプで次の物語へ →").assertIsDisplayed()
        // D 構造（ListBookCard＝題名を contentDescription で持つ）は出ない＝画面丸ごと分岐している。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
        // D の発見帯「新しい物語を見つける」も出ない（J では発見は最後尾の扉＝改行入りの別ノード）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
    }

    @Test
    fun `D装着ではデッキが出ず従来描画のまま`() {
        setContent(Skin.WAMODERN_D, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("← スワイプで次の物語へ →").assertDoesNotExist()
    }

    @Test
    fun `デッキ内のグリッドボタンで一覧トグルが結線される`() {
        var toggled = 0
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))),
            deckViewJ = true, onToggleDeckJ = { toggled++ },
        )
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        assertTrue("デッキ→一覧のトグルが呼ばれていない", toggled == 1)
    }

    @Test
    fun `J装着×一覧モードはD構造フォールバック＋デッキへ戻るボタンが出る`() {
        var toggled = false
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            deckViewJ = false, onToggleDeckJ = { toggled = true },
        )
        // 一覧＝D 構造へトークン写像（可読フォールバック）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        // グリッド切替の座がスキンJでは「デッキ表示へ戻る」になる。
        composeTestRule.onNodeWithContentDescription("デッキ表示に切替").performClick()
        assertTrue("一覧→デッキのトグルが呼ばれていない", toggled)
    }

    @Test
    fun `よみかけ先頭がheroとして続きから読むを持ち押すと開く`() {
        var opened: BookEntity? = null
        val reading = book("b1", "読みかけの物語")
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(reading)),
            // chap_3 まで読了・全10話＝READING（hero 条件）。
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10),
            onOpenBook = { opened = it },
        )
        composeTestRule.onNodeWithText("続きから読む").performClick()
        assertTrue("hero の読書導線が onOpenBook に結線されていない", opened?.id == "b1")
    }

    @Test
    fun `未読の扉は読むボタンを持ち続きから読むは出ない`() {
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "未読の物語"))),
            chapterCountMap = mapOf("b1" to 8),
        )
        // 未読扉の CTA は「読む」（初読）＝「続きから読む」は出ない。
        composeTestRule.onNodeWithText("読む").assertIsDisplayed()
        composeTestRule.onNodeWithText("続きから読む").assertDoesNotExist()
    }

    @Test
    fun `Jのデッキメニューはテーマ3択と通知節を出す`() {
        setContent(Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))))
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // J は3変種（DARK/LIGHT/SEPIA）＝テーマ節を出す（M の1変種畳みとの対比・supportedThemes 単一真実源）。
        composeTestRule.onNodeWithText("テーマ").assertIsDisplayed()
        composeTestRule.onNodeWithText("通知").assertIsDisplayed()
    }

    @Test
    fun `取込中は扉を仕立てているバナーが出る`() {
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))),
            processingState = ProcessingState(
                isProcessing = true, title = "山賊令嬢の華麗なる転身", phase = "本文を読み込み中…",
            ),
        )
        composeTestRule.onNodeWithText("山賊令嬢の華麗なる転身").assertIsDisplayed()
        composeTestRule.onNodeWithText("本文を読み込み中…").assertIsDisplayed()
    }

    @Test
    fun `装い・見つける・PDF追加が結線される`() {
        var wardrobe = false
        var fab = false
        var discovery = false
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))),
            onOpenWardrobe = { wardrobe = true },
            onFabClick = { fab = true },
            onOpenDiscovery = { discovery = true },
        )
        composeTestRule.onNodeWithContentDescription("着せ替え").performClick()
        composeTestRule.onNodeWithContentDescription("見つける").performClick()
        // PDF追加はメニュー移植（発見扉は「新しい物語＝発見」で手元 PDF 取込とは別のため）。
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("PDFを追加").assertHasClickAction()
        composeTestRule.onNodeWithText("PDFを追加")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertTrue("装いの間の結線が無い", wardrobe)
        assertTrue("見つける導線の結線が無い", discovery)
        assertTrue("PDF追加の結線が無い", fab)
    }
}
