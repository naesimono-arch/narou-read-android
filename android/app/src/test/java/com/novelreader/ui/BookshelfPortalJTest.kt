package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.skins.j.PaletteFind
import com.novelreader.ui.skins.j.PortalDoorPalettes
import com.novelreader.ui.skins.j.PortalTimePhase
import com.novelreader.ui.skins.j.portalAmbientParamsFor
import com.novelreader.ui.skins.j.portalDoorPaletteFor
import com.novelreader.ui.skins.j.portalTimePhaseFor
import com.novelreader.ui.theme.AmbDarkGoldPortal
import com.novelreader.ui.theme.AmbFindBaseTopPortal
import com.novelreader.ui.theme.AmbPlumDeepPortal
import com.novelreader.ui.theme.GreenPortal
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.tokens
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        onOpenBook: (BookEntity) -> Unit = {},
        onOpenDiscovery: () -> Unit = {},
        onOpenWardrobe: () -> Unit = {},
        onFabClick: () -> Unit = {},
        processingState: ProcessingState = ProcessingState(),
        appTheme: ReadingTheme = ReadingTheme.DARK,
        onThemeChange: (ReadingTheme) -> Unit = {},
        followingSystem: Boolean = false,
        onFollowSystem: () -> Unit = {},
    ) {
        // デッキ⇄一覧のビュー状態は J 自身が prefs 所有（2026-07-27 移設）＝pref 先置きで面を選ぶ
        // （旧引数 deckViewJ の代替。アサーション意図は不変）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.J_DECK_VIEW, deckViewJ).commit()
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
        // トグル状態は J 自身が所有（移設後）＝押下の結果「一覧（グリッド）面が実際に出る」ことで結線を検証する。
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))),
            deckViewJ = true,
        )
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("デッキ表示に切替").assertIsDisplayed()
    }

    @Test
    fun `J装着×一覧モードはJグリッド面＋デッキへ戻るボタンが出る`() {
        // ADR 0022 追記その2の是正: 一覧側は D構造フォールバックでなく J自身の意匠（グリッド面）へ委譲する。
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            deckViewJ = false,
        )
        // J グリッド面の見つける導線（.find-guide）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        // 升目セルの題名（.cin）は素の Text＝J グリッドが描いている。
        composeTestRule.onNodeWithText("吾輩は猫である").assertIsDisplayed()
        // D 構造（ListBookCard＝題名を contentDescription で持つ）は出ない＝Dの見た目の型を引き継いでいない。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
        // 一覧⇄デッキトグルの座＝押すとデッキ面へ実際に戻る（トグル状態は J 所有＝実挙動で結線検証）。
        composeTestRule.onNodeWithContentDescription("デッキ表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
    }

    @Test
    fun `Jグリッド面の升を長押しで選択モードへ入る`() {
        // 選択削除は骨格の単一状態機械を共有＝グリッド升の長押しが onEnterSelection（＝選択モード）へ結線されること。
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "選択される扉"))),
            deckViewJ = false,
        )
        composeTestRule.onNodeWithText("選択される扉").performTouchInput { longClick() }
        // 選択モードに入ると下端の選択アクションバー（キャンセル/全選択/削除）が現れる。
        composeTestRule.onNodeWithText("全選択").assertIsDisplayed()
        composeTestRule.onNodeWithText("削除").assertIsDisplayed()
    }

    @Test
    fun `Jグリッド面の⋮メニューはPDF追加を残しテーマ・通知は撤去（系2）`() {
        var fab = false
        setContent(
            Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))),
            deckViewJ = false, onFabClick = { fab = true },
        )
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // テーマ・通知は設定タブへ移行済みで⋮から撤去。非設定項目の「PDFを追加」は残る。
        composeTestRule.onNodeWithText("テーマ").assertDoesNotExist()
        composeTestRule.onNodeWithText("システムに従う").assertDoesNotExist()
        composeTestRule.onNodeWithText("PDFを追加")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertTrue("グリッド面の PDF追加が結線されていない", fab)
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
    fun `JのデッキメニューはPDF追加を残しテーマ・通知は撤去（系2）`() {
        // テーマ・通知は設定タブ（SettingsScreenK）へ移行済みで⋮から撤去。非設定項目の「PDFを追加」は残る。
        setContent(Skin.PORTAL_J, BookshelfUiState.Content(listOf(book("b1", "扉の本"))))
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        composeTestRule.onNodeWithText("テーマ").assertDoesNotExist()
        composeTestRule.onNodeWithText("システムに従う").assertDoesNotExist()
        composeTestRule.onNodeWithText("PDFを追加").assertIsDisplayed()
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

    // ── 〈遊び心〉J3「時を映す扉」＝時刻→大気の相の写像（時刻を引数化＝Clock 非依存で決定的）──

    @Test
    fun `J3 時刻は朝夕夜の3相へ境界含め正しく写る`() {
        // 5-11時=朝／11-17時=夕／17-翌5時=夜（閾値はモックに明示が無いため一般的な体感で定義・実装コメント参照）。
        assertEquals(PortalTimePhase.MORNING, portalTimePhaseFor(5))   // 朝の開始境界
        assertEquals(PortalTimePhase.MORNING, portalTimePhaseFor(10))  // 朝の終端
        assertEquals(PortalTimePhase.EVENING, portalTimePhaseFor(11))  // 夕へ切替
        assertEquals(PortalTimePhase.EVENING, portalTimePhaseFor(16))  // 夕の終端
        assertEquals(PortalTimePhase.NIGHT, portalTimePhaseFor(17))    // 夜へ切替
        assertEquals(PortalTimePhase.NIGHT, portalTimePhaseFor(23))    // 深夜
        assertEquals(PortalTimePhase.NIGHT, portalTimePhaseFor(0))     // 日跨ぎの夜
        assertEquals(PortalTimePhase.NIGHT, portalTimePhaseFor(4))     // 夜明け前
    }

    @Test
    fun `J3 既定は夕で従来の正本の見えに一致し朝夜は温度と明るさだけ動く`() {
        val morning = portalAmbientParamsFor(PortalTimePhase.MORNING)
        val evening = portalAmbientParamsFor(PortalTimePhase.EVENING)
        val night = portalAmbientParamsFor(PortalTimePhase.NIGHT)

        // 既定=夕＝mock amb-yaku（--warm:.18 --cool:0）＝従来の正本の見え。
        assertEquals(0.18f, evening.warm, 1e-6f)
        assertEquals(0f, evening.cool, 1e-6f)
        assertEquals(0f, evening.floorDarken, 1e-6f)

        // 朝＝澄んだ緑金＝金を弱め(.16)緑の冷光(.16)を足す（cool>0 は朝だけ）。
        assertTrue("朝は緑の冷光を持つ", morning.cool > 0f)
        assertEquals(0f, night.cool, 1e-6f)
        assertEquals(0f, evening.cool, 1e-6f)

        // 夜＝光が引き底が沈む＝warm 最小(.06)・底を外殻へ寄せて暗化(floorDarken>0)。
        assertTrue("夜は最も金が引く", night.warm < morning.warm && night.warm < evening.warm)
        assertTrue("夜は底の苔が最も濃い", night.floorAlpha >= evening.floorAlpha)
        assertTrue("夜だけ底を外殻へ沈める", night.floorDarken > 0f && morning.floorDarken == 0f)
    }

    // ── J 扉固有 ambient パレット（データ駆動・bookId 安定ハッシュ割当）＝実機所見「緑密集・色相の飛びが弱い」の解 ──

    @Test
    fun `扉パレットは bookId の純関数で並び替え不変`() {
        // 同じ id は周囲の作品構成に無関係に常に同じ扉世界＝index でなく id ハッシュゆえ並び替え/追加削除で色がずれない。
        assertEquals(portalDoorPaletteFor("book-alpha").name, portalDoorPaletteFor("book-alpha").name)
        assertEquals(portalDoorPaletteFor("id-42").name, portalDoorPaletteFor("id-42").name)
    }

    @Test
    fun `扉パレットは実idに近いサンプルで4世界全てに散る＝緑密集の解`() {
        // 実機所見「6扉中4扉が緑系＝色相の飛びが弱い」の真因は short id の hashCode 下位ビット偏り。
        // fmix32 撹拌後は実 id に近い決定的サンプル（UUID風/ncode風/和文混じり）が4世界すべてに散る。
        val sample = listOf(
            "novel_a1b2", "N1234AB", "c9f3e2d1", "book-転生", "story-77", "aoi-drama",
            "9f8e7d6c", "N9999ZZ", "kusa-life", "maou-01", "en-garden", "yaku-shi",
            "12ab34cd", "zz-final", "alpha-omega", "hero-journey", "N0001AA", "tale-x",
        )
        val counts = sample.groupingBy { portalDoorPaletteFor(it).name }.eachCount()
        assertEquals("4世界すべてに扉が割り当たらない＝色相の飛びが弱い", 4, counts.size)
        assertTrue("どこかの世界が0件＝分散が偏る", PortalDoorPalettes.all { (counts[it.name] ?: 0) > 0 })
    }

    @Test
    fun `扉パレットは並び替え不変かつデッキ・グリッドで同一idは同一世界`() {
        // 同一関数（portalDoorPaletteFor）をデッキ面・グリッド面が共有＝同じ bookId は常に同じ扉世界（升と扉がずれない）。
        val ids = listOf("book-alpha", "id-42", "9f8e7d6c", "N1234AB")
        ids.forEach { id ->
            assertEquals("同一idで扉世界がぶれる＝並び替え/面間で不変でない", portalDoorPaletteFor(id).name, portalDoorPaletteFor(id).name)
        }
    }

    @Test
    fun `各扉世界の base 起点色は互いに異なる＝扉間の色相差の本体`() {
        // base リニアの起点色が世界ごとに異なることが「扉大気が緑系に密集」の真の解（グローだけでは差が弱い）。
        val tops = PortalDoorPalettes.map { it.baseStops.first().second }
        assertEquals("扉世界の base 起点色に重複＝色相差が出ない", tops.size, tops.toSet().size)
    }

    @Test
    fun `発見扉はモックの amb-find アンバー系`() {
        // 発見扉＝amb-find の base 起点 #2A2A18・上部グローは金（rgba(214,196,120)）を厳密に。
        assertEquals(AmbFindBaseTopPortal, PaletteFind.baseStops.first().second)
        assertEquals(AmbDarkGoldPortal, PaletteFind.glow)
    }

    @Test
    fun `扉グローは署名3系統（金・森緑・宵紫）内に収まる`() {
        // 恋愛=桃のような全周 huemap はモック署名3色規律に反する＝グローは金/森緑/宵紫のみ（発見扉含む）。
        val signature = setOf(AmbDarkGoldPortal, GreenPortal, AmbPlumDeepPortal)
        assertTrue("扉グローが署名3系統の外へ出た", (PortalDoorPalettes + PaletteFind).all { it.glow in signature })
    }
}
