package com.novelreader.ui

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
import com.novelreader.domain.ReimportPlan
import com.novelreader.domain.reimportStatusLabel
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
 * 2列グリッドの状態行が1行に収まることの回帰（D 共通描画 と 明快K の両方）。
 *
 * 何を守るか（2026-07-30 実機観察の真因）: 正本モック（skins/bookshelf-D.html の `.caprow`+`.state`、
 * skins/bookshelf-K.html の `.cap`+`.st`）はどちらも**状態行をキャプション行の兄弟＝カード全幅**に置く。
 * 実装は両方とも状態行をキャプション行の左カラム（weight(1f)）へ入れ子にしており、状態行の幅が
 * カード幅 −可視⋮のタップ面 32dp になっていた。K で最長の状態行「本文なし・タップで再取込」は
 * その 32dp ぶんだけ溢れて折り返し、⋮ が消える選択モードでだけ1行に収まる——という症状の出方が、
 * 足りない幅の出所が⋮であることの証拠だった。
 *
 * なぜ golden でなくレイアウト結果で縛るか: D 側の本棚には golden が1枚も無く（screenshots/ は
 * BookshelfK_* のみ）、この不変条件だけのために D 用の撮影ハーネスを新設するのは重い。行数は
 * TextLayoutResult から直接取れるので、字数からの推定でなく**実際に組まれた行数**で判定できる。
 *
 * 画面幅を明示するのは Robolectric の既定（320dp）が実機（360dp）と違い、カード幅＝折り返し条件
 * そのものが変わってしまうため（golden 群と同じ w360dp に揃える）。
 *
 * このテストが赤くなる条件: 状態行をキャプション行へ入れ子に戻す／可視⋮を状態行と同じ Row へ置く／
 * 状態行の文言を伸ばす／グリッドの列幅・contentPadding・列間を詰める。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class GridStatusLineWrapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 欠落本1冊だけの棚。題名は短くしてキャプション行の折返しを判定から外す（見るのは状態行だけ）。 */
    private val missingBook =
        BookEntity(id = "b_missing", title = "硝子の海図", htmlDirPath = "/nonexistent/b_missing", author = "南 灯")

    /** 棚に出る状態行の文言は domain が正本＝テストで literal を二重定義しない（ズレたら気づけないため）。 */
    private val statusLabel = reimportStatusLabel(ReimportPlan.PickPdfNoRecord(contentSha256 = "sha-fixture"))

    private fun setContent(skin: Skin) {
        // 表示面（グリッド）はスキン自身が prefs で所有する＝引数では渡せないため先置きする。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.IS_GRID_VIEW, true)
            .putBoolean(PrefKeys.K_GRID_VIEW, true)
            .commit()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = BookshelfUiState.Content(listOf(missingBook)),
                        progressMap = emptyMap(),
                        chapterCountMap = mapOf(missingBook.id to 12),
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
                        reimportPlans = mapOf(
                            missingBook.id to ReimportPlan.PickPdfNoRecord(contentSha256 = "sha-fixture"),
                        ),
                    )
                }
            }
        }
    }

    /** 実際に組まれた行数（字数からの推定でなくレイアウト結果で判定する）。 */
    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().lineCount
    }

    @Test
    fun `D共通描画のグリッドで欠落の状態行が1行に収まる`() {
        setContent(Skin.WAMODERN_D)
        val node = composeTestRule.onNodeWithText(statusLabel, useUnmergedTree = true)
        assertEquals("D グリッドの状態行が折り返している（⋮ に幅を奪われていないか）", 1, node.lineCount())
    }

    @Test
    fun `明快Kのグリッドで欠落の状態行が1行に収まる`() {
        setContent(Skin.MEIKAI_K)
        val node = composeTestRule.onNodeWithText(statusLabel, useUnmergedTree = true)
        assertEquals("K グリッドの状態行が折り返している（⋮ に幅を奪われていないか）", 1, node.lineCount())
    }
}
