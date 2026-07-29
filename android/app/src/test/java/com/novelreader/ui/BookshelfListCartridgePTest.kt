package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * スキンP「カートリッジ」の本棚・一覧面（BookshelfListCartridgeP）＝ADR 0022 追記その2 の是正
 * （一覧トグル先を D構造フォールバックから P自身の `.li` 意匠へ差し替え）の描画分岐・機能結線テスト。
 *
 * 固定するもの（D一覧の機能全数を P 一覧が引き継ぐ＝欠落ゼロが合格条件）:
 *  1) P 装着×一覧モードで D 構造（栞書影＝題名を contentDescription で持つ）でなく P 一覧
 *     （POCKET NOVEL 機体・CARTRIDGE LIBRARY）が出ること／一覧→ラックのトグル結線
 *  2) `.li` の状態別表記＝未読バッジ・読了 CLEAR‼（水平・reachedEnd 実績）・よみかけの %
 *  3) 選択モード＝長押しで下端の選択バー（N本選択中・全選択・削除）が出て、削除確認へ進むこと
 *  4) Web由来（未取込）行の表示と⋮操作（取り込む/外す）の結線
 *  5) PDF追加・hero NOW PLAYING・取込中バナーの結線と発見・装い導線の不在
 *     （2026-07-29 K形正本追従＝発見は「さがす」タブ・装いは設定タブへ移管）
 *
 * ルーターは BookshelfContent 経由で検証する（rackViewP=false で一覧面へ落ちる）。選択モード状態は
 * BookshelfContent が所有する単一の状態機械＝長押し→再コンポーズ→選択バー描画まで端から端で通す。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfListCartridgePTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String, author: String = "") =
        BookEntity(id = id, title = title, author = author, htmlDirPath = "/nonexistent/$id")

    private fun setContent(
        uiState: BookshelfUiState,
        skin: Skin = Skin.CARTRIDGE_P,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        rackViewP: Boolean = false, // 既定＝一覧面（本テストの主対象）
        onOpenBook: (BookEntity) -> Unit = {},
        onDeleteBooks: (List<BookEntity>, Boolean) -> Unit = { _, _ -> },
        onFabClick: () -> Unit = {},
        onImportWebNovel: (WebNovelEntity) -> Unit = {},
        onRemoveWebNovel: (WebNovelEntity) -> Unit = {},
        processingState: ProcessingState = ProcessingState(),
    ) {
        // ラック⇄一覧のビュー状態は P 自身が prefs 所有（2026-07-27 移設）＝pref 先置きで面を選ぶ
        // （旧引数 rackViewP の代替。アサーション意図は不変）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.P_RACK_VIEW, rackViewP).commit()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = uiState,
                        progressMap = progressMap,
                        chapterCountMap = chapterCountMap,
                        newEpisodeNovelMap = emptyMap(),
                        processingState = processingState,
                        // 束は全フィールド必須（既定 no-op 廃止＝2026-07-27 純構造リファクタ）。旧テストの
                        // 個別引数と同じ値を束へ写しただけ＝アサーション意図は不変。
                        actions = ShelfActions(
                            onOpenBook = onOpenBook,
                            onFabClick = onFabClick,
                            // 発見・装いは P の両面から撤去済み（K形正本追従）＝束の契約上 no-op を渡す。
                            onOpenDiscovery = {},
                            onOpenWardrobe = {},
                            onCancelProcessing = {},
                        ),
                        webActions = ShelfWebActions(
                            onOpenWebNovel = {},
                            onResumeWebNovel = { _, _ -> },
                            onImportWebNovel = onImportWebNovel,
                            onRemoveWebNovel = onRemoveWebNovel,
                        ),
                        theme = ThemeControl(
                            appTheme = ReadingTheme.LIGHT,
                            onThemeChange = {},
                            followingSystem = false,
                            onFollowSystem = {},
                        ),
                        onDeleteBooks = onDeleteBooks,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
    }

    @Test
    fun `P装着×一覧モードではP一覧が出てD構造は出ない`() {
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // P 一覧の署名＝機体銘（CARTRIDGE LIBRARY）が出る＝D構造フォールバックではない。
        composeTestRule.onNodeWithText("CARTRIDGE LIBRARY").assertIsDisplayed()
        // D 構造（栞書影カード＝題名を contentDescription で持つ）は出ない＝P 自身の意匠へ差し替わっている。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
    }

    @Test
    fun `一覧内のラック切替ボタンでラックへ戻るトグルが結線される`() {
        // トグル状態は P 自身が所有（移設後）＝押下の結果「ラック面が実際に出る」ことで結線を検証する。
        setContent(BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithContentDescription("ラック表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
    }

    @Test
    fun `未読の本は未読バッジと全話数を持つ`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "未読の物語"))),
            chapterCountMap = mapOf("b1" to 8),
        )
        // 未読カセット行のセーブ欄＝「全8話」（"未読" は絞り込みチップと同語で衝突するため一意な stage 文字で判定）。
        composeTestRule.onNodeWithText("全8話").assertIsDisplayed()
    }

    @Test
    fun `読了行は進捗表記がCLEAR刻印になり百分率は出ない`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "読了の物語"))),
            // reachedEnd=true＝FINISHED（近似の高%でなく実績で判定＝嘘の100%を出さない）。
            progressMap = mapOf(
                "b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_88.html", reachedEnd = true),
            ),
            chapterCountMap = mapOf("b1" to 88),
        )
        composeTestRule.onNodeWithText("CLEAR‼").assertIsDisplayed()
        composeTestRule.onNodeWithText("全88話").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertDoesNotExist()
    }

    @Test
    fun `よみかけ蔵書は一覧面でもheroのNOW PLAYINGとつづきから読むを持つ`() {
        var opened: BookEntity? = null
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "読みかけの物語"))),
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10),
            onOpenBook = { opened = it },
        )
        // 一覧面もラックと同じく NOW PLAYING ヒーローを持つ（機体の世界を保つ＝READING の検出結線）。
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithText("つづきから読む").performClick()
        assertTrue("hero の読書導線が onOpenBook に結線されていない", opened?.id == "b1")
    }

    @Test
    fun `長押しで選択バーが出て削除確認へ進める`() {
        var deleted: List<BookEntity>? = null
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "選択される物語"))),
            chapterCountMap = mapOf("b1" to 5),
            onDeleteBooks = { books, _ -> deleted = books },
        )
        // 行を長押し＝選択モードへ（状態は BookshelfContent 所有→再コンポーズで P 選択バーが描かれる）。
        composeTestRule.onNodeWithText("選択される物語").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1本選択中").assertIsDisplayed()
        composeTestRule.onNodeWithText("全選択").assertIsDisplayed()
        // 削除→確認ダイアログ→削除する で onDeleteBooks が結線される。
        composeTestRule.onNodeWithText("削除").performClick()
        composeTestRule.onNodeWithText("削除する").performClick()
        assertTrue("選択削除が onDeleteBooks へ結線されていない", deleted?.map { it.id } == listOf("b1"))
    }

    @Test
    fun `Web由来行が表示されメニューから取り込むと外すが結線される`() {
        var imported = false
        var removed = false
        setContent(
            BookshelfUiState.Content(
                books = emptyList(),
                webNovels = listOf(
                    WebNovelEntity(ncode = "N1234AB", title = "未取込のなろう作品", writer = "作者名", generalAllNo = 10, addedAt = 1L),
                ),
            ),
            onImportWebNovel = { imported = true },
            onRemoveWebNovel = { removed = true },
        )
        composeTestRule.onNodeWithText("未取込のなろう作品").assertIsDisplayed()
        composeTestRule.onNodeWithText("未取込").assertIsDisplayed()
        // ⋮＝未取込作品の行内メニュー（機体トップの "メニュー" と固有ラベルで区別）→ 取り込む → 外す。
        composeTestRule.onNodeWithContentDescription("未取込作品のメニュー").performClick()
        composeTestRule.onNodeWithText("縦書きPDFを取り込む").performClick()
        assertTrue("Web カードの取り込み導線が結線されていない", imported)
        composeTestRule.onNodeWithContentDescription("未取込作品のメニュー").performClick()
        composeTestRule.onNodeWithText("本棚から外す").performClick()
        assertTrue("Web カードの外す導線が結線されていない", removed)
    }

    @Test
    fun `PDF追加が結線され発見・装い導線は出ない`() {
        var fab = false
        setContent(
            BookshelfUiState.Content(emptyList()),
            onFabClick = { fab = true },
        )
        // 発見（ShopBand）・装いボタンは撤去済み（2026-07-29 K形正本追従＝ラック面と同じ裁定）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("着せ替え").assertDoesNotExist()
        // PDF追加は一覧末尾の空きスロット＝下端で部分表示となりタップ中心が可視域外へ落ちる。
        // 幾何に依存せず配線を検証するため semantics の OnClick を直接発火する（ラック面テストと同手法）。
        composeTestRule.onNodeWithText("PDFを追加")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertTrue("PDF追加の結線が無い", fab)
    }

    @Test
    fun `取込中はカートリッジ書き込みバナーが一覧面でも出る`() {
        setContent(
            BookshelfUiState.Content(emptyList()),
            processingState = ProcessingState(
                isProcessing = true, title = "山賊令嬢の華麗なる転身", phase = "本文を読み込み中…",
            ),
        )
        composeTestRule.onNodeWithText("取り込み中").assertIsDisplayed()
        composeTestRule.onNodeWithText("山賊令嬢の華麗なる転身").assertIsDisplayed()
    }

    @Test
    fun `D装着では一覧面のP機体は出ない`() {
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))), skin = Skin.WAMODERN_D)
        composeTestRule.onNodeWithText("CARTRIDGE LIBRARY").assertDoesNotExist()
    }
}
