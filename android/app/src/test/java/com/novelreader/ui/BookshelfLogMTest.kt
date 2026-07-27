package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
 * スキンM「星図」の本棚・一覧面『観測野帳』（BookshelfLogM）＝ADR 0022 追記その2 の是正
 * （M の一覧トグル先を D構造フォールバックから M 自身の l* 意匠へ差し替え）の描画分岐・機能結線テスト。
 *
 * 固定するもの（D一覧の機能全数を観測野帳が引き継ぐ＝欠落ゼロが合格条件）:
 *  1) M 装着×一覧モードで D 構造（栞書影＝題名を contentDescription で持つ）でなく観測野帳（銘 meta・時系列節）が出ること
 *  2) 星図⇄一覧トグル（一覧側の「星図表示に切替」）の結線
 *  3) readout の状態別表記＝よみかけの %／読了の「読了」（reachedEnd 実績）／未読の「最初の星を灯す」バッジ
 *  4) 時系列節（今夜／未収蔵の星）の見出しが観測の recency で綴じられること
 *  5) 選択モード＝長押しで選択ヘッダ（N 天体を選択・星を消す）が出て削除確認へ進むこと
 *  6) Web由来（未収蔵）行の「この星を迎える」（取込）と⋯メニュー（外す）の結線
 *  7) 見つける導線・装い・PDF追加・取込中バナーの結線（星図面と同じ地平/機体語彙で）
 *
 * ルーターは BookshelfContent 経由で検証する（skyViewM=false で観測野帳へ落ちる）。選択モード状態は
 * BookshelfContent が所有する単一の状態機械＝長押し→再コンポーズ→選択ヘッダ描画まで端から端で通す。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfLogMTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String, author: String = "") =
        BookEntity(id = id, title = title, author = author, htmlDirPath = "/nonexistent/$id")

    private fun setContent(
        uiState: BookshelfUiState,
        skin: Skin = Skin.SEIZU_M,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        skyViewM: Boolean = false, // 既定＝観測野帳（本テストの主対象）
        onOpenBook: (BookEntity) -> Unit = {},
        onDeleteBooks: (List<BookEntity>, Boolean) -> Unit = { _, _ -> },
        onOpenDiscovery: () -> Unit = {},
        onOpenWardrobe: () -> Unit = {},
        onFabClick: () -> Unit = {},
        onImportWebNovel: (WebNovelEntity) -> Unit = {},
        onRemoveWebNovel: (WebNovelEntity) -> Unit = {},
        processingState: ProcessingState = ProcessingState(),
    ) {
        // 星図⇄一覧のビュー状態は M 自身が prefs 所有（2026-07-27 移設）＝pref 先置きで面を選ぶ
        // （旧引数 skyViewM の代替。アサーション意図は不変）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.M_SKY_VIEW, skyViewM).commit()
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
                            onOpenDiscovery = onOpenDiscovery,
                            onOpenWardrobe = onOpenWardrobe,
                            onCancelProcessing = {},
                        ),
                        webActions = ShelfWebActions(
                            onOpenWebNovel = {},
                            onResumeWebNovel = { _, _ -> },
                            onImportWebNovel = onImportWebNovel,
                            onRemoveWebNovel = onRemoveWebNovel,
                        ),
                        theme = ThemeControl(
                            appTheme = ReadingTheme.DARK,
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
    fun `M装着×一覧モードでは観測野帳が出てD構造は出ない`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_2.html", lastReadAt = System.currentTimeMillis())),
            chapterCountMap = mapOf("b1" to 10),
        )
        // 観測野帳の署名＝銘の題字が Text（星図面と地平を共有＝「まだ知らない星を探しに」）。
        composeTestRule.onNodeWithText("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertIsDisplayed()
        // D 構造（栞書影カード＝題名を contentDescription で持つ・発見帯「新しい物語を見つける」）は出ない。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
    }

    @Test
    fun `一覧内の星図切替ボタンで星図へ戻るトグルが結線される`() {
        // トグル状態は M 自身が所有（移設後）＝押下の結果「星図面が実際に出る」ことで結線を検証する。
        setContent(BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithContentDescription("星図表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
    }

    @Test
    fun `よみかけ蔵書は今夜の節に到達話数と光度を持つ`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "読みかけの物語", author = "霧島あおい"))),
            // 直近に読んだ＝lastReadAt を現在時刻に＝epoch「今夜」。chap_3/全10話＝READING 30%。
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html", lastReadAt = System.currentTimeMillis())),
            chapterCountMap = mapOf("b1" to 10),
        )
        composeTestRule.onNodeWithText("今夜").assertIsDisplayed()
        composeTestRule.onNodeWithText("第3話").assertIsDisplayed()
        composeTestRule.onNodeWithText("30%").assertIsDisplayed()
    }

    @Test
    fun `読了行は光度が読了になり百分率は出ない`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "読了の物語"))),
            // reachedEnd=true＝FINISHED（近似の高%でなく実績で判定＝嘘の100%を出さない）。
            progressMap = mapOf(
                "b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_88.html", lastReadAt = System.currentTimeMillis(), reachedEnd = true),
            ),
            chapterCountMap = mapOf("b1" to 88),
        )
        // 「読了」はフィルタチップ（読書状態）と観測票 readout の両方に出る＝2件で readout 側の存在を担保する
        //（チップは常設1件・読了行の光度で+1件。P一覧が「未読」チップ衝突を一意語で避けたのと同種の回避）。
        composeTestRule.onAllNodesWithText("読了").assertCountEquals(2)
        composeTestRule.onNodeWithText("全88話").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertDoesNotExist()
    }

    @Test
    fun `未読の本はまだ観測なしの節で最初の星を灯すバッジを持つ`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "未読の物語"))),
            chapterCountMap = mapOf("b1" to 8),
        )
        composeTestRule.onNodeWithText("まだ観測なし").assertIsDisplayed()
        composeTestRule.onNodeWithText("未観測 · 最初の星を灯す").assertIsDisplayed()
    }

    @Test
    fun `長押しで選択ヘッダが出て削除確認へ進める`() {
        var deleted: List<BookEntity>? = null
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "選択される物語"))),
            chapterCountMap = mapOf("b1" to 5),
            onDeleteBooks = { books, _ -> deleted = books },
        )
        // 観測票を長押し＝選択モードへ（状態は BookshelfContent 所有→再コンポーズで選択ヘッダが描かれる）。
        composeTestRule.onNodeWithText("選択される物語").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1 天体を選択").assertIsDisplayed()
        // 星を消す→確認ダイアログ→削除する で onDeleteBooks が結線される。
        composeTestRule.onNodeWithText("星を消す").performClick()
        composeTestRule.onNodeWithText("削除する").performClick()
        assertTrue("選択削除が onDeleteBooks へ結線されていない", deleted?.map { it.id } == listOf("b1"))
    }

    @Test
    fun `Web由来行が未収蔵の節に出て取込と外すが結線される`() {
        var imported = false
        var removed = false
        setContent(
            BookshelfUiState.Content(
                books = emptyList(),
                webNovels = listOf(
                    WebNovelEntity(ncode = "N1234AB", title = "未収蔵のなろう作品", writer = "作者名", generalAllNo = 10, addedAt = 1L),
                ),
            ),
            onImportWebNovel = { imported = true },
            onRemoveWebNovel = { removed = true },
        )
        composeTestRule.onNodeWithText("未収蔵の星").assertIsDisplayed()
        composeTestRule.onNodeWithText("未収蔵のなろう作品").assertIsDisplayed()
        // この星を迎える＝取込の結線。
        composeTestRule.onNodeWithText("この星を迎える").performClick()
        assertTrue("Web カードの取込導線が結線されていない", imported)
        // ⋯＝未収蔵作品の行内メニュー→ 本棚から外す。
        composeTestRule.onNodeWithContentDescription("未収蔵作品のメニュー").performClick()
        composeTestRule.onNodeWithText("本棚から外す").performClick()
        assertTrue("Web カードの外す導線が結線されていない", removed)
    }

    @Test
    fun `見つける導線・装い・PDF追加が結線される`() {
        var discovery = false
        var wardrobe = false
        var fab = false
        setContent(
            BookshelfUiState.Content(emptyList()),
            onOpenDiscovery = { discovery = true },
            onOpenWardrobe = { wardrobe = true },
            onFabClick = { fab = true },
        )
        composeTestRule.onNodeWithText("まだ知らない星を探しに").performClick()
        composeTestRule.onNodeWithContentDescription("着せ替え").performClick()
        // PDF追加＝地平の「新しい星を迎える」。下端で部分表示となり得るため semantics OnClick を直接発火する。
        composeTestRule.onNodeWithText("新しい星を迎える")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertTrue("見つける導線の結線が無い", discovery)
        assertTrue("装いの間の結線が無い", wardrobe)
        assertTrue("PDF追加の結線が無い", fab)
    }

    @Test
    fun `取込中は星への変換バナーが観測野帳でも出る`() {
        setContent(
            BookshelfUiState.Content(emptyList()),
            processingState = ProcessingState(
                isProcessing = true, title = "山賊令嬢の華麗なる転身", phase = "本文を読み込み中…",
            ),
        )
        composeTestRule.onNodeWithText("山賊令嬢の華麗なる転身", substring = true).assertIsDisplayed()
    }

    @Test
    fun `M一覧モードの⋮メニューはテーマ・通知を撤去し高負荷スカイのみ残す（系2）`() {
        setContent(BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // テーマ・通知は設定タブ（SettingsScreenK）へ移行済みで⋮から撤去（系2）。M は元々テーマ節なし。
        // 残るのは M 固有の非設定項目＝高負荷スカイ試作トグル（ADR 0023・debug ビルドの星図M でのみ出る）。
        composeTestRule.onNodeWithText("テーマ").assertDoesNotExist()
        composeTestRule.onNodeWithText("通知").assertDoesNotExist()
        composeTestRule.onNodeWithText("高負荷スカイ（試作）").assertIsDisplayed()
    }

    @Test
    fun `D装着では観測野帳は出ない`() {
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            skin = Skin.WAMODERN_D,
        )
        // D 構造＝発見帯が出る＝観測野帳ではない。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("未収蔵の星").assertDoesNotExist()
    }
}
