package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.domain.ReimportPlan
import com.novelreader.domain.ScanProgress
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
 * 本文欠落バナー（案C）と PDF フォルダ走査バナー（案X）が **全スキンの本棚6面** へ配線されていることの回帰テスト。
 *
 * なぜ必要か（2026-08-07 `fix: M/P/J スキンの配線落ち7件` の再発防止）: 走査の起動は route 層で全スキン共通なのに、
 * M/P/J の6面は `ShelfChrome` の `folderScan`/`onScanStop`/`sweepBannerVisible` を**受け取って捨てて**いた。
 * 進捗も停止ボタンも描かれず、走査を始めたら**中断する手段が無い**面が5つ以上あった。当時の修正はテストを伴わず、
 * 面ごとの既存テスト（BookshelfLogMTest ほか）はバグが在ったときも緑だった＝この機序を誰も見張っていない。
 *
 * ⚠️ 「必須引数にして渡し忘れをコンパイルエラーにする」構造封鎖はこのクラスのバグを止めない
 *（`skins/ShelfFace.kt` 冒頭の設計メモと同じ限界＝止まるのは「渡し忘れ」で、「受け取って捨てる」は素通しする）。
 * よって配線の実在は描画で確かめるしかない。
 *
 * 何を縛るか: 面ごとに ①走査中は進捗バナーが出ること ②その「停止」が `onScanStop` を実際に呼ぶこと
 * ③欠落検出中は案Cバナーが出て「まとめて再取込」が `onSweepConfirm` を呼ぶこと。
 *
 * `@GraphicsMode(NATIVE)` を付けない理由: 本テストの主張は**存在と結線**だけで寸法・位置を一切見ない
 *（LEGACY の「文字幅＝文字数」代用計測に結果が依存しない）。寸法を見るテストへ育てるときは NATIVE が要る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShelfScanBannerWiringTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var scanStopped = false
    private var sweepConfirmed = false

    /**
     * 面を1つ選んで本棚を描く。面の選択はスキン（[LocalSkin]）＋各スキンが prefs で持つビュー切替
     * （M=星図/観測野帳・P=ラック/一覧・J=デッキ/グリッド）の組で決まる＝各面のテストと同じ作法。
     */
    private fun setFace(
        skin: Skin,
        viewPrefKey: String? = null,
        viewPrefValue: Boolean = true,
        folderScan: ScanProgress? = null,
        sweepBannerVisible: Boolean = false,
    ) {
        if (viewPrefKey != null) {
            RuntimeEnvironment.getApplication()
                .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(viewPrefKey, viewPrefValue).commit()
        }
        val uiState = BookshelfUiState.Content(
            listOf(BookEntity(id = "b1", title = "走査中の本", author = "", htmlDirPath = "/nonexistent/b1")),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = uiState,
                        progressMap = emptyMap(),
                        newEpisodeNovelMap = emptyMap(),
                        processingState = ProcessingState(),
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
                            appTheme = ReadingTheme.DARK,
                            onThemeChange = {},
                            followingSystem = false,
                            onFollowSystem = {},
                        ),
                        onDeleteBooks = { _, _ -> },
                        snackbarHostState = remember { SnackbarHostState() },
                        // 案C の冊数は本テストの主張ではない（見るのは配線の有無）＝1件だけ置いて非0にする。
                        reimportPlans = mapOf("b1" to ReimportPlan.AutoPdf(sourceUri = "content://test/b1.pdf")),
                        sweepBannerVisible = sweepBannerVisible,
                        onSweepLater = {},
                        onSweepConfirm = { sweepConfirmed = true },
                        folderScan = folderScan,
                        onScanStop = { scanStopped = true },
                    )
                }
            }
        }
    }

    /** 走査バナーが出て「停止」が実配線であること（面ごとに同じ主張を当てる）。 */
    private fun assertScanBannerWired() {
        composeTestRule.onNodeWithText("PDFを照合しています").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 / 10").assertIsDisplayed()
        composeTestRule.onNodeWithText("停止").performClick()
        assertTrue("「停止」が onScanStop へ配線されていない＝走査を中断できない", scanStopped)
    }

    /** 案Cバナーが出て「まとめて再取込」が実配線であること。 */
    private fun assertSweepBannerWired() {
        composeTestRule.onNodeWithText("本文データが見つからない本が 1冊あります").assertIsDisplayed()
        composeTestRule.onNodeWithText("まとめて再取込").performClick()
        assertTrue("「まとめて再取込」が onSweepConfirm へ配線されていない", sweepConfirmed)
    }

    private val scanning = ScanProgress(hashed = 3, total = 10, matched = 1)

    @Test
    fun 星図M_星図面_走査バナーと停止が配線されている() {
        setFace(Skin.SEIZU_M, PrefKeys.M_SKY_VIEW, viewPrefValue = true, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun 星図M_観測野帳_走査バナーと停止が配線されている() {
        setFace(Skin.SEIZU_M, PrefKeys.M_SKY_VIEW, viewPrefValue = false, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun カートリッジP_ラック面_走査バナーと停止が配線されている() {
        setFace(Skin.CARTRIDGE_P, PrefKeys.P_RACK_VIEW, viewPrefValue = true, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun カートリッジP_一覧面_走査バナーと停止が配線されている() {
        setFace(Skin.CARTRIDGE_P, PrefKeys.P_RACK_VIEW, viewPrefValue = false, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun ポータルJ_デッキ面_走査バナーと停止が配線されている() {
        setFace(Skin.PORTAL_J, PrefKeys.J_DECK_VIEW, viewPrefValue = true, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun ポータルJ_グリッド面_走査バナーと停止が配線されている() {
        setFace(Skin.PORTAL_J, PrefKeys.J_DECK_VIEW, viewPrefValue = false, folderScan = scanning)
        assertScanBannerWired()
    }

    @Test
    fun 星図M_星図面_欠落バナーと再取込が配線されている() {
        setFace(Skin.SEIZU_M, PrefKeys.M_SKY_VIEW, viewPrefValue = true, sweepBannerVisible = true)
        assertSweepBannerWired()
    }

    @Test
    fun 星図M_観測野帳_欠落バナーと再取込が配線されている() {
        setFace(Skin.SEIZU_M, PrefKeys.M_SKY_VIEW, viewPrefValue = false, sweepBannerVisible = true)
        assertSweepBannerWired()
    }

    @Test
    fun カートリッジP_ラック面_欠落バナーと再取込が配線されている() {
        setFace(Skin.CARTRIDGE_P, PrefKeys.P_RACK_VIEW, viewPrefValue = true, sweepBannerVisible = true)
        assertSweepBannerWired()
    }

    @Test
    fun カートリッジP_一覧面_欠落バナーと再取込が配線されている() {
        setFace(Skin.CARTRIDGE_P, PrefKeys.P_RACK_VIEW, viewPrefValue = false, sweepBannerVisible = true)
        assertSweepBannerWired()
    }

    @Test
    fun ポータルJ_デッキ面_欠落バナーと再取込が配線されている() {
        setFace(Skin.PORTAL_J, PrefKeys.J_DECK_VIEW, viewPrefValue = true, sweepBannerVisible = true)
        assertSweepBannerWired()
    }

    @Test
    fun ポータルJ_グリッド面_欠落バナーと再取込が配線されている() {
        setFace(Skin.PORTAL_J, PrefKeys.J_DECK_VIEW, viewPrefValue = false, sweepBannerVisible = true)
        assertSweepBannerWired()
    }
}
