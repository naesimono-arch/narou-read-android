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
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
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
        onToggleSkyM: () -> Unit = {},
        onOpenBook: (BookEntity) -> Unit = {},
    ) {
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
                        appTheme = ReadingTheme.DARK,
                        onThemeChange = {},
                        isGridView = false,
                        onToggleView = {},
                        onFabClick = {},
                        onOpenBook = onOpenBook,
                        onDeleteBooks = {},
                        onOpenDiscovery = {},
                        onCancelProcessing = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        skyViewM = skyViewM,
                        onToggleSkyM = onToggleSkyM,
                    )
                }
            }
        }
    }

    @Test
    fun `M装着×星図モードでは星図が出てD構造の帯は出ない`() {
        setContent(Skin.SEIZU_M, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        // 星図の署名＝地平の導線（発見・PDF追加）と銘。
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertIsDisplayed()
        composeTestRule.onNodeWithText("新しい星を迎える").assertIsDisplayed()
        // D 構造（発見帯・栞書影グリッド）は出ない＝画面丸ごと分岐している。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertDoesNotExist()
    }

    @Test
    fun `D装着では星図が出ず従来描画のまま`() {
        setContent(Skin.WAMODERN_D, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))))
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        composeTestRule.onNodeWithText("まだ知らない星を探しに").assertDoesNotExist()
    }

    @Test
    fun `星図内の一覧ボタンと一覧側の星図ボタンで両方向トグルが結線される`() {
        var toggled = 0
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(emptyList()),
            skyViewM = true, onToggleSkyM = { toggled++ },
        )
        composeTestRule.onNodeWithContentDescription("一覧表示に切替").performClick()
        assertTrue("星図→一覧のトグルが呼ばれていない", toggled == 1)
    }

    @Test
    fun `M装着×一覧モードはD構造フォールバック＋星図へ戻るボタンが出る`() {
        var toggled = false
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
            skyViewM = false, onToggleSkyM = { toggled = true },
        )
        // 一覧＝D 構造へトークン写像（可読フォールバック）。
        composeTestRule.onNodeWithText("新しい物語を見つける").assertIsDisplayed()
        // グリッド切替の座がスキンMでは「星図へ戻る」になる。
        composeTestRule.onNodeWithContentDescription("星図表示に切替").performClick()
        assertTrue("一覧→星図のトグルが呼ばれていない", toggled)
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
        // 正本 R1 の輝度上限（帯 0.42／核 0.46）＝連続する靄で contrast を落とさない絶対条件。
        val field = buildDeepSkyField()
        assertTrue("粒が想定レンジ外（生成が壊れている）", field.band.size in 2400..2900)
        assertTrue("輝度上限 0.46 を超える粒がある＝題名が潰れうる", field.band.all { it.alpha <= 0.46f })
    }

    @Test
    fun `Mの一覧モードの⋮メニューはテーマ節を畳み通知節は残す`() {
        setContent(
            Skin.SEIZU_M, BookshelfUiState.Content(emptyList()),
            skyViewM = false,
        )
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // M は固定1変種＝テーマ3択は無意味なので節ごと非表示（supportedThemes が単一真実源）。
        composeTestRule.onNodeWithText("テーマ").assertDoesNotExist()
        composeTestRule.onNodeWithText("通知").assertIsDisplayed()
    }
}
