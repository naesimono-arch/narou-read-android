package com.novelreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.ProcessingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 本棚のダイアログ状態が構成変更（回転・ダーク切替＝Activity 再生成）をまたいで残ることの回帰。
 *
 * なぜ要るか（2026-08-17 実機で発見）: MainActivity は `android:configChanges` を一切宣言していないため、
 * 回転のたびに Activity が作り直され、plain `remember` に置いたダイアログ状態はそこで捨てられる。
 * 実害は「本文欠落本の復旧ダイアログを開いたまま横向きにすると消える」で、同じ欠陥型が本棚の各所に
 * 散っていた（掃引で削除確認・通知 priming も是正）。表示を立てる書き込みはどれも1操作ぶんしか無いので、
 * 一度消えると自力では戻らない＝操作が丸ごと無かったことになる。
 *
 * [StateRestorationTester] は setContent した内容の保存/復元を実際に走らせる（＝rememberSaveable を
 * 通らない状態はここで消える）ので、「回転で消えるか」を実機なしで観測できる
 * （[NcodeLinkSheetStateRestorationTest] と同じ手法）。
 *
 * ⚠️ ここで固定できるのは描画層 [BookshelfContent] が持つ状態だけ。実際に発見された
 * `reimportTargetId`（復旧ダイアログの対象）はルート層 BookshelfScreen が持ち、そこは
 * BookshelfViewModel＝NovelReaderApplication＋Room リポジトリの実グラフを要求するため
 * （既存の JVM テストは VM 単体を mockk で組む形しか持たない）、この構成では組めない。
 * 同一の保持方法（id を rememberSaveable・実体は books から引き直す）を共有することでのみ守っている。
 */
@RunWith(RobolectricTestRunner::class)
// 実端末相当の画面（NcodeLinkSheetStateRestorationTest と同一）。既定の短い画面だと下端の選択バーや
// ダイアログ内のチェック行が画面外へ落ちてタップ位置が不安定になる。
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class BookshelfDialogStateRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // sourceUri あり＝「取込元のPDFファイルも削除する」チェックが出る本（deletableCount>0 の条件）。
    private val book = BookEntity(
        id = "b1",
        title = "吾輩は猫である",
        htmlDirPath = "/nonexistent/b1",
        sourceUri = "content://docs/src1",
    )

    private fun setContent(): StateRestorationTester {
        // グリッド描画へ固定（BookshelfContentTest と同じ pref 先置き＝描画条件を既存テストと揃える）。
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PrefKeys.IS_GRID_VIEW, true).commit()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            MaterialTheme {
                BookshelfContent(
                    uiState = BookshelfUiState.Content(listOf(book)),
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
                )
            }
        }
        return restorationTester
    }

    /** 長押しで選択モードへ入り、下端バーの「削除」で確認ダイアログを開く（本番と同じ操作順）。 */
    private fun openDeleteConfirm() {
        composeTestRule.onNodeWithContentDescription("吾輩は猫である").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1冊選択中").assertIsDisplayed()
        // 完全一致なので確認ダイアログ側の「削除する」とは衝突しない（下端バーのボタンだけを指す）。
        composeTestRule.onNodeWithText("削除").performClick()
    }

    @Test
    fun `削除確認ダイアログは構成変更をまたいで残る`() {
        val restorationTester = setContent()
        openDeleteConfirm()
        composeTestRule.onNodeWithText("選択した1件を本棚から削除しますか？").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        // plain remember 時代の症状そのもの: 選択（selectedIds/selectionMode＝既に Saveable）は残るのに
        // ダイアログだけ消え、「選択中なのに確認が無い」半端な復元になっていた。
        composeTestRule.onNodeWithText("選択した1件を本棚から削除しますか？").assertExists()
        composeTestRule.onNodeWithText("1冊選択中").assertExists()
    }

    @Test
    fun `取込元PDFも削除するのチェックは構成変更をまたいで残る`() {
        val restorationTester = setContent()
        openDeleteConfirm()
        // 行全体が toggleable（Role.Checkbox）＝ラベルのノードがそのままトグルを指す。
        composeTestRule.onNodeWithText("取込元のPDFファイルも削除する（1件）").performClick()
        composeTestRule.onNodeWithText("取込元のPDFファイルも削除する（1件）").assertIsOn()

        restorationTester.emulateSavedInstanceStateRestore()

        // 開閉だけ復元してチェックが落ちると、ON にしたつもりの取込元PDF削除が黙って OFF に戻る
        // （破壊操作の同意が静かに消える）＝開閉フラグと対で守る。
        composeTestRule.onNodeWithText("取込元のPDFファイルも削除する（1件）").assertIsOn()
    }
}
