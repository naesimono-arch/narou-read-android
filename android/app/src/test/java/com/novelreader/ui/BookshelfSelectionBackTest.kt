package com.novelreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 本棚の選択モード BackHandler の前面条件テスト（2026-08-06 監査 B8 の固定）。
 *
 * 機序: タブ枠（TabPagerHost）は beyondViewportPageCount=1 で隣ページを常駐コンポーズし、
 * OnBackPressedDispatcher は後着優先＝ページ側の BackHandler が枠の「本棚へ戻る」に必ず勝つ。
 * enabled が selectionMode だけだと、選択モードのまま他タブへ移った後の Back を隠れた本棚が
 * 1回黙って食う（画面無変化）。isFrontTab=false でハンドラが登録されないことを Dispatcher の
 * hasEnabledCallbacks で直接観測する（KTabNavigationTest と同じ観測手法）。
 *
 * BookshelfContentTest と分けるのは、Dispatcher へ触るには createAndroidComposeRule（activity 付き）が
 * 必要で、既存テスト群の createComposeRule 構成を動かさないため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookshelfSelectionBackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun book(id: String, title: String) =
        BookEntity(id = id, title = title, htmlDirPath = "/nonexistent/$id")

    private fun setContent(isFrontTab: Boolean) {
        // グリッド描画へ固定（BookshelfContentTest と同じ pref 先置き＝描画条件を既存テストと揃える）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.IS_GRID_VIEW, true).commit()
        composeTestRule.setContent {
            MaterialTheme {
                BookshelfContent(
                    uiState = BookshelfUiState.Content(listOf(book("b1", "吾輩は猫である"))),
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
                        appTheme = ReadingTheme.LIGHT,
                        onThemeChange = {},
                        followingSystem = false,
                        onFollowSystem = {},
                    ),
                    onDeleteBooks = { _, _ -> },
                    snackbarHostState = remember { SnackbarHostState() },
                    isFrontTab = isFrontTab,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** 長押しで選択モードへ入れる（下端バー「1冊選択中」の出現で成立を確認）。 */
    private fun enterSelectionMode() {
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1冊選択中").assertIsDisplayed()
    }

    @Test
    fun `前面では選択モード中のBackが選択を解除する（従来挙動の維持）`() {
        setContent(isFrontTab = true)
        enterSelectionMode()
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        // 選択モードが解け、本は残る（削除でなく解除であること）。
        composeTestRule.onNodeWithText("1冊選択中").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").assertIsDisplayed()
    }

    @Test
    fun `非前面では選択モード中でもBackを消費しない（監査B8）`() {
        // 実運用では「前面で選択モードに入り→他タブへスワイプ」で非前面＋選択モードの組合せになる。
        // テストでは同じ末端状態を isFrontTab=false のまま長押しで作る（状態機械は同一）。
        setContent(isFrontTab = false)
        enterSelectionMode()
        // Dispatcher に有効コールバックが無い＝隠れた本棚は Back を食わず、枠（タブ層）や Activity 既定へ
        // 素通しされる。本棚以外に BackHandler を持つ部品はこの描画に無いため、この観測がそのまま
        // 選択モード BackHandler の enabled を指す。
        assertFalse(
            composeTestRule.runOnIdle {
                composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
            },
        )
    }

    @Test
    fun `前面の選択モードはBackハンドラを登録している（非前面テストの対照）`() {
        // 非前面テストが「そもそも誰も登録していないから false」で偽緑にならないための対照ペア。
        setContent(isFrontTab = true)
        enterSelectionMode()
        assertTrue(
            composeTestRule.runOnIdle {
                composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
            },
        )
    }
}
