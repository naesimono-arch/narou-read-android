package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
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
 * スキンP「カートリッジ」の本棚ルーター（ADR 0022 §1）＋ BookshelfCartridgeP の描画分岐・結線テスト。
 *
 * 固定するもの:
 *  1) P 装着×ラックモードで D 構造でなく P ラック（POCKET NOVEL 機体・CARTRIDGE LIBRARY）が出ること／
 *     D 装着では従来描画が不変なこと
 *  2) ラック⇄一覧トグルの結線（ラック内の一覧ボタン・一覧側のラックボタンの両方向）
 *  3) hero（よみかけ先頭）の NOW PLAYING＋「つづきから読む」と未読の「未読」バッジの出し分け＋開く結線
 *  4) P（3変種スキン）の⋮メニューでテーマ節が出ること（M の1変種畳みとの対比＝supportedThemes 単一真実源）
 *  5) 取込中＝カートリッジ書き込みバナー・装い/PDF追加/見つける導線の結線
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、Theme の SideEffect（window 直叩き）を
 * テストから切り離しルーター分岐だけを検証するため（トークン束の契約は SkinMPJTest が担う）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfCartridgePTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String, author: String = "") =
        BookEntity(id = id, title = title, author = author, htmlDirPath = "/nonexistent/$id")

    private fun setContent(
        skin: Skin,
        uiState: BookshelfUiState,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        rackViewP: Boolean = true,
        onToggleRackP: () -> Unit = {},
        onOpenBook: (BookEntity) -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
        onOpenWardrobe: () -> Unit = {},
        onFabClick: () -> Unit = {},
        onCancelProcessing: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = uiState,
                        progressMap = progressMap,
                        chapterCountMap = chapterCountMap,
                        newEpisodeNovelMap = emptyMap(),
                        processingState = ProcessingState(),
                        appTheme = ReadingTheme.LIGHT,
                        onThemeChange = {},
                        isGridView = false,
                        onToggleView = {},
                        onFabClick = onFabClick,
                        onOpenBook = onOpenBook,
                        onDeleteBooks = {},
                        onOpenDiscovery = onOpenDiscovery,
                        onOpenWardrobe = onOpenWardrobe,
                        onCancelProcessing = onCancelProcessing,
                        snackbarHostState = remember { SnackbarHostState() },
                        rackViewP = rackViewP,
                        onToggleRackP = onToggleRackP,
                    )
                }
            }
        }
    }

    @Test
    fun `P装着×ラックモードではラックが出てD構造は出ない`() {
        setContent(Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // P ラックの署名＝カセットライブラリ見出し（機体銘板 POCKET NOVEL はデッキ銘板と2箇所に出るため
        // 一意な CARTRIDGE LIBRARY で判定する）。
        composeTestRule.onNodeWithText("CARTRIDGE LIBRARY").assertIsDisplayed()
        // D 構造（栞書影カード＝題名を contentDescription で持つ）は出ない＝画面丸ごと分岐している。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
    }

    @Test
    fun `D装着ではラックが出ず従来描画のまま`() {
        setContent(Skin.WAMODERN_D, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("CARTRIDGE LIBRARY").assertDoesNotExist()
    }

    @Test
    fun `ラック内の一覧ボタンで一覧トグルが結線される`() {
        var toggled = 0
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(emptyList()),
            rackViewP = true, onToggleRackP = { toggled++ },
        )
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        assertTrue("ラック→一覧のトグルが呼ばれていない", toggled == 1)
    }

    @Test
    fun `P装着×一覧モードはD構造フォールバック＋ラックへ戻るボタンが出る`() {
        var toggled = false
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            rackViewP = false, onToggleRackP = { toggled = true },
        )
        // 一覧＝D 構造へトークン写像（可読フォールバック）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        // グリッド切替の座がスキンPでは「ラック表示へ戻る」になる。
        composeTestRule.onNodeWithContentDescription("ラック表示に切替").performClick()
        assertTrue("一覧→ラックのトグルが呼ばれていない", toggled)
    }

    @Test
    fun `よみかけ先頭がheroとしてNOW PLAYINGとつづきから読むを持ち押すと開く`() {
        var opened: BookEntity? = null
        val reading = book("b1", "読みかけの物語")
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(reading)),
            // chap_3 まで読了・全10話＝READING（hero 条件）。
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10),
            onOpenBook = { opened = it },
        )
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithText("つづきから読む").performClick()
        assertTrue("hero の読書導線が onOpenBook に結線されていない", opened?.id == "b1")
    }

    @Test
    fun `未読の本は未読バッジを持ちNOW PLAYINGは出ない`() {
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "未読の物語"))),
            chapterCountMap = mapOf("b1" to 8),
        )
        // 未読カセットのセーブ欄＝「全8話」（"未読" は絞り込みチップと同語で衝突するため一意な stage 文字で判定）。
        composeTestRule.onNodeWithText("全8話").assertIsDisplayed()
        // 続きから（挿さっている本）は無いので NOW PLAYING / つづきから読む は出ない。
        composeTestRule.onNodeWithText("つづきから読む").assertDoesNotExist()
    }

    @Test
    fun `Pのラックメニューはテーマ3択と通知節を出す`() {
        setContent(Skin.CARTRIDGE_P, BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // P は3変種（LIGHT/SEPIA/DARK）＝テーマ節を出す（M の1変種畳みとの対比・supportedThemes 単一真実源）。
        composeTestRule.onNodeWithText("テーマ").assertIsDisplayed()
        composeTestRule.onNodeWithText("通知").assertIsDisplayed()
    }

    @Test
    fun `取込中はカートリッジ書き込みバナーが出る`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSkin provides Skin.CARTRIDGE_P,
                LocalSkinTokens provides Skin.CARTRIDGE_P.tokens,
            ) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = BookshelfUiState.Content(emptyList()),
                        progressMap = emptyMap(),
                        chapterCountMap = emptyMap(),
                        newEpisodeNovelMap = emptyMap(),
                        processingState = ProcessingState(
                            isProcessing = true, title = "山賊令嬢の華麗なる転身", phase = "本文を読み込み中…",
                        ),
                        appTheme = ReadingTheme.LIGHT,
                        onThemeChange = {},
                        isGridView = false,
                        onToggleView = {},
                        onFabClick = {},
                        onOpenBook = {},
                        onDeleteBooks = {},
                        onOpenDiscovery = {},
                        onCancelProcessing = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        rackViewP = true,
                        onToggleRackP = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("取り込み中").assertIsDisplayed()
        composeTestRule.onNodeWithText("山賊令嬢の華麗なる転身").assertIsDisplayed()
    }

    @Test
    fun `装い・PDF追加・見つける導線が結線される`() {
        var wardrobe = false
        var fab = false
        var discovery = false
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(emptyList()),
            onOpenWardrobe = { wardrobe = true },
            onFabClick = { fab = true },
            onOpenDiscovery = { discovery = true },
        )
        composeTestRule.onNodeWithContentDescription("着せ替え").performClick()
        composeTestRule.onNodeWithText("新しい物語を見つける").performClick()
        // PDF追加は LazyColumn 末尾の空きスロット。テスト表示域では折り返し下端に部分表示となり
        // ジェスチャの中心座標が可視域外へ落ちる（本番配線は node の click action で担保済み＝assertHasClickAction）。
        // 幾何に依存せず配線そのものを検証するため semantics の OnClick を直接発火する。
        composeTestRule.onNodeWithText("PDFを追加").assertHasClickAction()
        composeTestRule.onNodeWithText("PDFを追加")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertTrue("装いの間の結線が無い", wardrobe)
        assertTrue("見つける導線の結線が無い", discovery)
        assertTrue("PDF追加の結線が無い", fab)
    }
}
