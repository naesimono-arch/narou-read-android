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
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.ReadingTheme
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
 * BookshelfContent（本棚の stateless 描画層）の状態分岐＋主要コールバック結線テスト（ADR 0009）。
 * state-holder / UI 分割で VM から切り出した葉が対象。Loading/Content(空)/Content(蔵書あり) の
 * 描画分岐（cold start の空フラッシュ対策 F-O の要）と、追加導線の結線がサイレント退行
 * しないことを固定する（発見・装い導線は 2026-07-29 K形正本追従で本棚から撤去済み＝不在も固定する）。プラットフォーム副作用（PDF 選択・権限・バッテリー）はルート層 BookshelfScreen
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
        onDeleteBooks: (List<BookEntity>, Boolean) -> Unit = { _, _ -> },
        deferHeavyContent: Boolean = false,
        // テーマ節（⋮メニュー）の検証用。読書設定シートと同じ単一真実源をそのまま差し込む。
        appTheme: ReadingTheme = ReadingTheme.LIGHT,
        onThemeChange: (ReadingTheme) -> Unit = {},
        followingSystem: Boolean = false,
        onFollowSystem: () -> Unit = {},
    ) {
        // グリッド/リスト状態は D 描画部が prefs 所有へ移設済み（旧引数 isGridView の撤去）＝pref 先置きで
        // 旧テストと同じグリッド描画を保つ（アサーション意図は不変）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.IS_GRID_VIEW, true).commit()
        composeTestRule.setContent {
            MaterialTheme {
                BookshelfContent(
                    deferHeavyContent = deferHeavyContent,
                    uiState = uiState,
                    progressMap = progressMap,
                    chapterCountMap = chapterCountMap,
                    newEpisodeNovelMap = emptyMap(),
                    processingState = ProcessingState(),
                    // 束は全フィールド必須（既定 no-op 廃止＝2026-07-27 純構造リファクタ）。旧テストの
                    // 個別引数と同じ値を束へ写しただけ＝アサーション意図は不変。
                    actions = ShelfActions(
                        onOpenBook = {},
                        onFabClick = onFabClick,
                        // 発見・装いは D 描画部から撤去済み（K形正本追従）＝束の契約上 no-op を渡す。
                        onOpenDiscovery = {},
                        onOpenWardrobe = {},
                        onCancelProcessing = {},
                    ),
                    webActions = ShelfWebActions(
                        onOpenWebNovel = {},
                        onResumeWebNovel = { _, _ -> },
                        onImportWebNovel = {},
                        onRemoveWebNovel = {},
                    ),
                    theme = ThemeControl(
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        followingSystem = followingSystem,
                        onFollowSystem = onFollowSystem,
                    ),
                    onDeleteBooks = onDeleteBooks,
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    @Test
    fun `Content(空)ではEmptyBookshelfを出す`() {
        setContent(BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithText("本棚はまだ空です").assertIsDisplayed()
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
        //（＝表紙カードが存在しない）、フィルタのヘッダは実表示のまま残ることを固定する
        //（発見帯は 2026-07-29 K形正本追従で撤去済み＝ヘッダの生存確認は状態チップで行う）。
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))), deferHeavyContent = true)
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
        composeTestRule.onNodeWithText("すべて").assertIsDisplayed()
    }

    @Test
    fun `Content(蔵書あり)では書名を出し空メッセージと発見・装い導線は出さない`() {
        setContent(BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // 栞書影は題字を Canvas 描画するため text ノードを持たず、表紙の contentDescription=題名 で確認する。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onNodeWithText("本棚はまだ空です").assertDoesNotExist()
        // 発見帯・トップバー🔍・装いの間（Checkroom）は撤去済み（2026-07-29 K形正本 bookshelf-D.html 追従＝
        // 発見は「さがす」タブ・装いは設定タブへ移管）。再出現の退行をここで固定する。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("見つける").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("着せ替え").assertDoesNotExist()
    }

    @Test
    fun `追加ボタンでonFabClickが呼ばれる`() {
        var fabClicked = false
        setContent(
            BookshelfUiState.Content(emptyList()),
            onFabClick = { fabClicked = true },
        )
        // 空状態の「PDFを追加する」ボタンも FAB と同じ onFabClick を叩く
        composeTestRule.onNodeWithText("PDFを追加する").performClick()
        assertTrue(fabClicked)
    }

    // ────── 読書状態フィルタのチップ行（すべて/よみかけ/未読/読了） ──────

    @Test
    fun `蔵書ありなら固定4状態チップが出て「すべて」既定で全カード表示`() {
        // ラベルと違い状態チップは固定4個で常設（棚が非空なら出す）。
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

    // ────── 本棚⋮の撤去（テーマ・通知・診断は設定タブ SettingsScreenK へ移行・系2 2026-07-24） ──────

    @Test
    fun `本棚からテーマ・通知の重複導線を撤去した（系2）`() {
        // 設定重複の撤去を固定: 本棚トップバーの⋮（テーマ4択・通知・診断）を撤去したため、本棚のどこにも
        // テーマの「システムに従う」は出ない（設定タブが単一正本）。カードの可視⋮（系1）は残るが中身は「選択」のみ。
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            appTheme = ReadingTheme.DARK,
            followingSystem = true,
        )
        composeTestRule.onNodeWithText("システムに従う").assertDoesNotExist()
        composeTestRule.onNodeWithText("ライト").assertDoesNotExist()
    }

    // ────── 複数選択→まとめて削除（残8・案B裁定） ──────

    @Test
    fun `長押しで選択モードに入り削除確定でonDeleteBooksへ選択本が渡る`() {
        var deleted: List<BookEntity>? = null
        setContent(
            BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"), book("b2", "坊っちゃん"))),
            onDeleteBooks = { books, _ -> deleted = books },
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
