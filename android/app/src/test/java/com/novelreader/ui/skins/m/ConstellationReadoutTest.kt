package com.novelreader.ui.skins.m

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.ui.BookshelfContent
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 星図M の銘（モック `.const`）の readout が1行に収まることの回帰。
 *
 * 何を守るか（2026-07-30 実機観察の真因）: モックの `.const{width:196px}` は**内容幅**で、`left/right:24px`
 * は画面端からの片側オフセット。実装は 216dp の枠に左右 24dp の padding を入れており内容幅が 168dp しか
 * 無かった＝モックより 28dp 狭く、未読の readout「未読 · 全N話　まだ星は結ばれていない」が
 * 「…結ばれてい／ない」と割れていた。幅は今は readout の実採寸から決まる（rememberConstBlockWidth）。
 *
 * 画面幅の修飾子を明示するのは、Robolectric の既定画面（320dp）だと実機（360dp）と違う幅で判定して
 * しまうため＝golden 群（BookshelfKScreenshotTest 等）と同じ w360dp に揃える。
 *
 * このテストが赤くなる条件: 銘ブロックの幅算出（下限 196dp・実採寸・上限 308dp）を壊す／readout の
 * 文言を伸ばす／描画と採寸のフォントサイズがズレる。逆に色・座標・星の描画を変えても緑のまま通る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ConstellationReadoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(book: BookEntity, chapterCount: Int) {
        // 星図面を選ぶ（面の状態は M 自身が prefs で所有＝引数では渡せない）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.M_SKY_VIEW, true).commit()
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSkin provides Skin.SEIZU_M,
                LocalSkinTokens provides Skin.SEIZU_M.tokens,
            ) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = BookshelfUiState.Content(listOf(book)),
                        progressMap = emptyMap(),
                        chapterCountMap = mapOf(book.id to chapterCount),
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
                    )
                }
            }
        }
    }

    /** 実際に組まれた行数を取る（「見た目で1行か」を字面の長さで推定せず、レイアウト結果で判定する）。 */
    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().lineCount
    }

    private fun book(id: String, title: String) =
        BookEntity(id = id, title = title, htmlDirPath = "/nonexistent/$id")

    @Test
    fun `未読の readout は1行に収まる（2桁話数＝モックと同条件）`() {
        setContent(book("b1", "転生したら最弱スキルだった"), chapterCount = 88)
        val readout = "未読 · 全88話　まだ星は結ばれていない"
        val node = composeTestRule.onNodeWithText(readout, useUnmergedTree = true)
        assertEquals("未読 readout が折り返している", 1, node.lineCount())
    }

    @Test
    fun `未読の readout は3桁話数でも1行に収まる（実蔵書に221・282・860話がある）`() {
        setContent(book("b1", "転生したら最弱スキルだった"), chapterCount = 860)
        val readout = "未読 · 全860話　まだ星は結ばれていない"
        val node = composeTestRule.onNodeWithText(readout, useUnmergedTree = true)
        assertEquals("3桁話数で readout が折り返している", 1, node.lineCount())
    }
}
