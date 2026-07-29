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
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.skins.m.buildDeepSkyField
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
 * スキンM「星図」の本棚ルーター（ADR 0022 §1）＋ BookshelfSkyM の描画分岐・結線テスト。
 *
 * 固定するもの:
 *  1) M 装着×星図モードで D 構造でなく星図（地平の導線群）が出ること／D 装着では従来描画が不変なこと
 *  2) 星図⇄一覧トグルの結線（星図内の一覧ボタン・一覧側の星図ボタンの両方向）
 *  3) hero（よみかけ先頭）の「この星から読む」と未読の「最初の星を灯す」の出し分け＋開く結線
 *  4) M（1変種スキン）の⋮メニューでテーマ節が畳まれること（C から続く畳み漏れの回帰固定）
 *
 * NovelReaderTheme でなく LocalSkin を直接 provide するのは、Theme の SideEffect（window 直叩き）を
 * テストから切り離しルーター分岐だけを検証するため（トークン束の契約は SkinMPJTest が担う）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfSkyMTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun book(id: String, title: String) =
        BookEntity(id = id, title = title, htmlDirPath = "/nonexistent/$id")

    private fun setContent(
        skin: Skin,
        uiState: BookshelfUiState,
        progressMap: Map<String, ProgressEntity> = emptyMap(),
        chapterCountMap: Map<String, Int> = emptyMap(),
        skyViewM: Boolean = true,
        onOpenBook: (BookEntity) -> Unit = {},
    ) {
        // 星図⇄一覧のビュー状態は M 自身が prefs 所有（2026-07-27 移設・p_hinge_detent と同流儀）＝
        // テストは pref 先置きで面を選ぶ（旧引数 skyViewM の代替。アサーション意図は不変）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.M_SKY_VIEW, skyViewM).commit()
        composeTestRule.setContent {
            // LocalSkin（ルーター分岐）と LocalSkinTokens（メニューのテーマ節畳み判定）は本番では
            // NovelReaderTheme が対で供給する＝テストでも対で流し、判定源のズレを作らない。
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
                            appTheme = ReadingTheme.DARK,
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
    }

    @Test
    fun `M装着×星図モードでは星図が出てD構造は出ない`() {
        setContent(Skin.SEIZU_M, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // 星図の署名＝地平の導線（PDF追加）と銘。発見導線「まだ知らない星を探しに」は撤去済み
        //（2026-07-29 K形正本 bookshelf-M.html 追従＝発見は「さがす」タブへ分離。不在の固定は下で行う）。
        composeTestRule.onNodeWithText("新しい星を迎える").assertIsDisplayed()
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("見つける").assertDoesNotExist()
        // D 構造（栞書影グリッド）は出ない＝画面丸ごと分岐している。
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
    }

    @Test
    fun `D装着では星図が出ず従来描画のまま`() {
        setContent(Skin.WAMODERN_D, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // D の判定＝トップバー題字「本棚」＋文字目録の行題字（Text）。CD=題名でないのは、D 既定の
        // 文字目録は題字を素の Text で描き、CD=題名を持つのは栞書影（グリッド面のみ）のため
        //（2026-07-29 ゲートFAIL の真因＝マーカー選定ミス。displayed 検査は維持＝行が実寸で描かれる証拠）。
        composeTestRule.onNodeWithText("本棚").assertIsDisplayed()
        composeTestRule.onNodeWithText("吾輩は猫である").assertIsDisplayed()
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertDoesNotExist()
    }

    @Test
    fun `星図内の一覧ボタンと一覧側の星図ボタンで両方向トグルが結線される`() {
        // トグル状態は M 自身が所有（移設後）＝コールバック計数でなく「面が実際に切り替わる」ことで結線を検証する。
        setContent(Skin.SEIZU_M, BookshelfUiState.Content(emptyList()), skyViewM = true)
        // 星図→一覧: 一覧面の署名＝「星図表示に切替」ボタンが現れる。
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("星図表示に切替").assertIsDisplayed()
        // 一覧→星図: 戻る方向も同じ実挙動で担保（両方向）。
        composeTestRule.onNodeWithContentDescription("星図表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
    }

    @Test
    fun `M装着×一覧モードはM自身の観測野帳＋星図へ戻るボタンが出る`() {
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            skyViewM = false,
        )
        // 一覧＝M 自身の意匠『観測野帳』（ADR 0022 追記その2＝旧・D構造フォールバックの格下げ是正）。
        // 星図面と地平を共有＝「新しい星を迎える」が出る（観測野帳の下辺 SkyHorizon。発見行は撤去済み）。
        composeTestRule.onNodeWithText("新しい星を迎える").assertIsDisplayed()
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertDoesNotExist()
        // 銘の操作クラスタの「星図表示に切替」で星図面へ実際に戻る（トグル状態は M 所有＝実挙動で結線検証）。
        composeTestRule.onNodeWithContentDescription("星図表示に切替").performClick()
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").assertIsDisplayed()
    }

    @Test
    fun `よみかけ先頭がheroとして「この星から読む」を持ち押すと開く`() {
        var opened: BookEntity? = null
        val reading = book("b1", "読みかけの物語")
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(listOf(reading)),
            // chap_3 まで読了・全10話＝READING（hero 条件）。
            progressMap = mapOf("b1" to ProgressEntity(bookId = "b1", lastReadFilename = "chap_3.html")),
            chapterCountMap = mapOf("b1" to 10),
            onOpenBook = { opened = it },
        )
        composeTestRule.onNodeWithText("◈ 続きから").assertIsDisplayed()
        composeTestRule.onNodeWithText("この星から読む").performClick()
        assertTrue("hero の読書導線が onOpenBook に結線されていない", opened?.id == "b1")
    }

    @Test
    fun `未読の本は「最初の星を灯す」バッジを持つ`() {
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(listOf(book("b1", "未読の物語"))),
            chapterCountMap = mapOf("b1" to 8),
        )
        composeTestRule.onNodeWithText("最初の星を灯す").assertIsDisplayed()
        composeTestRule.onNodeWithText("この星から読む").assertDoesNotExist()
    }

    // ---- R1「深空」レイヤーの決定性・輝度上限（描画は Canvas ゆえ、純関数の不変条件で担保）----

    @Test
    fun `深空フィールドは決定的＝同じ生成を2回で完全一致する`() {
        // 固定 seed 生成＝再コンポーズ（＝再生成）で星が踊らないことの単体担保。
        val a = buildDeepSkyField()
        val b = buildDeepSkyField()
        assertTrue("粒数が生成毎に変わる＝非決定的", a.band.size == b.band.size)
        assertTrue("散開微星の数が変わる＝非決定的", a.scatter.size == b.scatter.size)
        assertTrue("アクセント星の数が変わる＝非決定的", a.accent.size == b.accent.size)
        // 先頭・末尾の粒座標が一致＝乱数列が完全再現されている。
        assertTrue(a.band.first().fx == b.band.first().fx && a.band.first().fy == b.band.first().fy)
        assertTrue(a.band.last().fx == b.band.last().fx && a.band.last().fy == b.band.last().fy)
    }

    @Test
    fun `天の川粒の輝度は上限を超えない＝題名可読の担保`() {
        // R1s の輝度上限＝帯・核とも 0.42 へ統一（面輝度キャップ＝題名可読を全帯で担保する不可侵の規律）。
        val field = buildDeepSkyField()
        // 粒帯＝本体(target 6500)＋核(≤1260)。トーラス化(2026-07-19)で帯だけ境界外へ ±BAND_BRIDGE 延長生成し fy=mod で
        // 畳む＝橋渡し粒ぶん総数が増える（本体密度は視野域で不変・増分は境界近傍に集中）。生成破綻の検知レンジ。
        assertTrue("粒が想定レンジ外（生成が壊れている）", field.band.size in 8800..9600)
        assertTrue("輝度上限 0.42 を超える粒がある＝題名が潰れうる", field.band.all { it.alpha <= 0.42f })
        // 散開微星（背景を沈める帯外微星）＝R1s 改訂1 で 520点。
        assertTrue("散開微星が想定外の数", field.scatter.size == 520)
        // 超微星の海（最深・帯構造に従属＝Great Rift で一部間引かれ 3200 未満）。
        assertTrue("超微星の海が想定外の数", field.microSea.size in 2200..3200)
        // pip/スパイクは離散点ゆえ面輝度キャップに非抵触＝BandParticle.alpha（粒本体）のみを規律対象とする。
    }

    @Test
    fun `Mの一覧モードの⋮メニューはテーマ・通知を撤去し高負荷スカイのみ残す（系2）`() {
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(emptyList()),
            skyViewM = false,
        )
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // テーマ・通知は設定タブ（SettingsScreenK）へ移行済みで⋮から撤去（系2）。M は元々テーマ節なし。
        // 残るのは M 固有の非設定項目＝高負荷スカイ試作トグル（ADR 0023・debug ビルドの星図M でのみ出る）。
        composeTestRule.onNodeWithText("テーマ").assertDoesNotExist()
        composeTestRule.onNodeWithText("通知").assertDoesNotExist()
        composeTestRule.onNodeWithText("高負荷スカイ（試作）").assertIsDisplayed()
    }
}
