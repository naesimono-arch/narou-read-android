package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performSemanticsAction
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
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

    /** ラック⇄一覧のビュー状態（P 自身が prefs 所有）を先置きする（ヒンジの setHingeDetentPref と同流儀）。 */
    private fun setRackViewPref(rack: Boolean) {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.P_RACK_VIEW, rack).commit()
    }

    private fun setContent(
        skin: Skin,
        uiState: BookshelfUiState,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        rackViewP: Boolean = true,
        onOpenBook: (BookEntity) -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
        onOpenWardrobe: () -> Unit = {},
        onFabClick: () -> Unit = {},
        onCancelProcessing: () -> Unit = {},
        appTheme: ReadingTheme = ReadingTheme.LIGHT,
        onThemeChange: (ReadingTheme) -> Unit = {},
        followingSystem: Boolean = false,
        onFollowSystem: () -> Unit = {},
    ) {
        // ラック⇄一覧のビュー状態は P 自身が prefs 所有（2026-07-27 移設・p_hinge_detent と同流儀）＝
        // pref 先置きで面を選ぶ（旧引数 rackViewP の代替。アサーション意図は不変）。
        setRackViewPref(rackViewP)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = uiState,
                        progressMap = progressMap,
                        chapterCountMap = chapterCountMap,
                        newEpisodeNovelMap = emptyMap(),
                        processingState = ProcessingState(),
                        // 束は全フィールド必須（既定 no-op 廃止＝2026-07-27 純構造リファクタ）。旧テストの
                        // 個別引数と同じ値を束へ写しただけ＝アサーション意図は不変。
                        actions = ShelfActions(
                            onOpenBook = onOpenBook,
                            onFabClick = onFabClick,
                            onOpenDiscovery = onOpenDiscovery,
                            onOpenWardrobe = onOpenWardrobe,
                            onCancelProcessing = onCancelProcessing,
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
                        onDeleteBooks = { _, _ -> },
                        snackbarHostState = remember { SnackbarHostState() },
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
        // トグル状態は P 自身が所有（移設後）＝押下の結果「一覧面が実際に出る」ことで結線を検証する。
        setContent(Skin.CARTRIDGE_P, BookshelfUiState.Content(emptyList()), rackViewP = true)
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("ラック表示に切替").assertIsDisplayed()
    }

    @Test
    fun `P装着×一覧モードはD構造フォールバック＋ラックへ戻るボタンが出る`() {
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            rackViewP = false,
        )
        // 一覧＝D 構造へトークン写像（可読フォールバック）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        // グリッド切替の座がスキンPでは「ラック表示へ戻る」になる＝押すとラック面へ実際に戻る（結線の実挙動検証）。
        composeTestRule.onNodeWithContentDescription("ラック表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
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
    fun `読了カセットは進捗表示がCLEAR刻印になり百分率は出ない（遊び心P1）`() {
        setContent(
            Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "読了の物語"))),
            // reachedEnd=true＝FINISHED（readingStatusFor の実績フラグ）。近似の高%でなく実績で判定する。
            progressMap = mapOf(
                "b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_88.html", reachedEnd = true),
            ),
            chapterCountMap = mapOf("b1" to 88),
        )
        // 進捗表示の位置が CLEAR‼ 刻印へ差し替わる（100% と CLEAR は同じ場所を占める＝モック④）。
        composeTestRule.onNodeWithText("CLEAR‼").assertIsDisplayed()
        // 読了は「全88話」stage（未読と同語だが未読バッジが無い＝CLEAR‼ で判別）。
        composeTestRule.onNodeWithText("全88話").assertIsDisplayed()
        // 進捗% はもう出ない（CLEAR‼ が占有）。
        composeTestRule.onNodeWithText("100%").assertDoesNotExist()
    }

    @Test
    fun `Pのラック頭から⋮（テーマ・通知）を撤去した（系2）`() {
        // テーマ・通知は設定タブ（SettingsScreenK）へ移行済み＝P の頭に⋮メニューは無い（モック P も頭は⋮無し）。
        // 装い・表示切替は別ボタンとして温存。撤去すると⋮は空になるためボタンごと除いた。
        setContent(Skin.CARTRIDGE_P, BookshelfUiState.Content(emptyList()))
        composeTestRule.onNodeWithContentDescription("メニュー").assertDoesNotExist()
        composeTestRule.onNodeWithText("システムに従う").assertDoesNotExist()
    }

    @Test
    fun `取込中はカートリッジ書き込みバナーが出る`() {
        setRackViewPref(true)
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
                        // 束は全フィールド必須（既定 no-op 廃止＝2026-07-27 純構造リファクタ）。値は旧引数と同じ no-op。
                        actions = ShelfActions(
                            onOpenBook = {},
                            onFabClick = {},
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
                            appTheme = ReadingTheme.LIGHT,
                            onThemeChange = {},
                            followingSystem = false,
                            onFollowSystem = {},
                        ),
                        onDeleteBooks = { _, _ -> },
                        snackbarHostState = remember { SnackbarHostState() },
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

    // ────── H3 二画面ヒンジ（上部可変） ──────

    /** app_prefs へヒンジのディテント index を先置きし、pref からの復元を検証するためのヘルパー。 */
    private fun setHingeDetentPref(index: Int) {
        // キーは PrefKeys 参照（2026-07-27 の全数張替に追従＝setRackViewPref と同流儀）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putInt(PrefKeys.P_HINGE_DETENT, index).commit()
    }

    private fun readingBookState() = BookshelfUiState.Content(listOf(book("b1", "読みかけの物語")))
    private val readingProgress = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html"))
    private val readingChapters = mapOf("b1" to 10)

    @Test
    fun `hero がいるとヒンジバーが出て既定は均衡でフル液晶が見える`() {
        // 既定ディテント=均衡（pref 未設定）＝上画面はフル液晶＝NOW PLAYING と つづきから読む が到達可能。
        setContent(
            Skin.CARTRIDGE_P, readingBookState(),
            progressMap = readingProgress, chapterCountMap = readingChapters,
        )
        composeTestRule.onNodeWithContentDescription("上下2画面の配分（ヒンジ）").assertIsDisplayed()
        composeTestRule.onNodeWithText("均衡").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithText("つづきから読む").assertIsDisplayed()
    }

    @Test
    fun `hero がいなければヒンジバーは出ない`() {
        // 続きから（挿さっている本）が無いときは二画面ヒンジ自体を出さない（上画面に見せる中身が無い）。
        setContent(Skin.CARTRIDGE_P, BookshelfUiState.Content(listOf(book("b1", "未読の物語"))), chapterCountMap = mapOf("b1" to 8))
        composeTestRule.onNodeWithContentDescription("上下2画面の配分（ヒンジ）").assertDoesNotExist()
    }

    @Test
    fun `pref のディテント最小で開くとミニストリップと最小ラベルが出て続きから到達口が残る`() {
        // p_hinge_detent=0（最小）を先置き＝アプリ再起動でも取り分が戻る（pref 永続化）。上画面は1行ミニストリップ。
        setHingeDetentPref(0)
        setContent(
            Skin.CARTRIDGE_P, readingBookState(),
            progressMap = readingProgress, chapterCountMap = readingChapters,
        )
        // ヒンジの段ラベルが最小・ミニの NOW ラベルが見える（NOW は "NOW PLAYING" と別語＝一意）。
        composeTestRule.onNodeWithText("最小").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOW").assertIsDisplayed()
        // 最小でも続きから到達口（ミニの▶＝contentDescription「つづきから読む」）は残り、押せる。
        composeTestRule.onNodeWithContentDescription("つづきから読む").assertHasClickAction()
    }
}
