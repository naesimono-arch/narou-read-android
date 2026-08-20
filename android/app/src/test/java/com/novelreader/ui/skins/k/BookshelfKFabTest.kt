package com.novelreader.ui.skins.k

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.novelreader.domain.ReadingStatus
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * K 本棚の拡張FAB「PDFを追加」の**読み上げ名**と**出没条件**の回帰（2026-08-07 実機 PGEM10／2026-08-20 裁定②）。
 *
 * 絵でなく構造（semantics ツリー）で縛る:
 *  1) FAB に読み上げ名が無い。M3 の ExtendedFloatingActionButton は text スロットの意味を a11y へ渡さず、
 *     ラベルが見えていてもノードは Role=Button だけ＝TalkBack が主要操作を読めない。
 *  2) 空棚（蔵書0）では FAB を出さない。同じ操作が拡張FABと空棚CTA で二重に出ており、fontScale 2.0 では
 *     FAB が CTA へ被っていた（実機 PGEM10）。器側で回避帯を予約する旧処方をやめ、二重表示そのものを
 *     消す裁定②を採った＝押す対象は空棚CTA〈PDFを追加〉一本。
 *  3) ⚠️ ただし「この分類の本はありません」（状態フィルタで0件・蔵書はある）は空棚ではない。
 *     あちらは CTA を持たないので FAB を隠すと PDF 追加の導線が全部消える＝**残ること**を張る。
 *
 * 2) と 3) は対でしか意味を持たない（片方だけでは「常に隠す」誤実装が通ってしまう）ので必ず2本で持つ。
 * 旧テスト「空棚のCTAは末尾まで送るとFAB帯の外に出る」は FAB の存在が前提＝裁定②で成立しなくなったため、
 * この2本へ置き換えた（回避帯 Insets.ScrollBottomForFab も同じ便で撤去済み＝避ける相手が居ない）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class BookshelfKFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `拡張FABは見える文字と同じ読み上げ名を持つ`() {
        setKGridView(true)
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, 1.0f) { _ ->
            BookshelfK(
                data = KShelfFixtures.mixedData(),
                chrome = KShelfFixtures.chrome(KShelfFixtures.mixedStatusCounts),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        // 名前で引けること自体が回帰の本体（M3 がラベルを a11y へ渡さないため、呼び出し側で与えないと無名に戻る）。
        composeTestRule.onNodeWithContentDescription("PDFを追加").assertHasClickAction()
    }

    @Test
    fun `蔵書0の空棚では拡張FABが存在しない`() {
        setKGridView(true)
        val counts: Map<ReadingStatus, Int> = emptyMap()
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, 1.0f) { _ ->
            BookshelfK(
                data = KShelfFixtures.emptyData(),
                chrome = KShelfFixtures.chrome(counts),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        // 空状態そのものが出ていることを先に確かめる（描画に失敗しただけでも FAB 不在は成立するため）。
        composeTestRule.onNodeWithText("まだ本がありません").assertExists()
        // FAB は contentDescription、空棚 CTA は可視テキスト＝同じ語でも取り違えない。
        composeTestRule.onNodeWithContentDescription("PDFを追加").assertDoesNotExist()
        // 押す対象は CTA 一本に残る（＝導線ごと消えていない）。
        composeTestRule.onNodeWithText("PDFを追加").assertHasClickAction()
    }

    @Test
    fun `状態フィルタで0件でも蔵書があれば拡張FABは残る`() {
        setKGridView(true)
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, 1.0f) { _ ->
            BookshelfK(
                // 未読1冊だけの棚で「読了」を選ぶ＝一覧は空だが蔵書はある（空棚ではない）。
                data = KShelfFixtures.unreadOnlyData(),
                chrome = KShelfFixtures.chrome(mapOf(ReadingStatus.UNREAD to 1))
                    .copy(selectedStatus = ReadingStatus.FINISHED),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        // この面であることの確認（空棚 CTA は出ない＝PDF 追加の導線は FAB しか無い）。
        composeTestRule.onNodeWithText("この分類の本はありません").assertExists()
        composeTestRule.onNodeWithText("まだ本がありません").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("PDFを追加").assertHasClickAction()
    }
}
