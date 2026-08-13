package com.novelreader.ui.skins.k

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.novelreader.domain.ReadingStatus
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * K 本棚の拡張FAB「PDFを追加」の**読み上げ名**と**下端クリアランス**の回帰（2026-08-07 実機 PGEM10 起点）。
 *
 * 実機で観測された2件を、絵でなく構造（semantics ツリーと座標）で縛る:
 *  1) FAB に読み上げ名が無い。M3 の ExtendedFloatingActionButton は text スロットの意味を a11y へ渡さず、
 *     ラベルが見えていてもノードは Role=Button だけ＝TalkBack が主要操作を読めない。
 *  2) 空棚 CTA「PDFを追加」が FAB に覆われる。空棚だけが器を別に持ち、グリッド/リストが
 *     contentPadding で予約している [com.novelreader.ui.theme.Insets.ScrollBottomForFab] を
 *     一切適用していなかった（＝同じ操作の二重表示の一方が他方を隠し、実機で書影タップの誤着弾も出た）。
 *
 * 2) の縛り方: 「静止時に重ならない」ではなく「**内容の末尾まで送れば必ず帯の外に出る**」を見る。
 * fontScale 2.0・360x640dp では空棚の内容（≈423dp）が FAB 帯を除いた可視域（≈410dp）より高く、
 * 静止時の残り重なりは器の予約では消せない（消すには意匠側＝FAB の出没/縮退が要る）。予約が効いていれば
 * 送り切った位置で CTA は帯の外に出る＝それがこのトークンの契約そのもの。実機（Box 実効高 ≈712dp）では
 * 予約込みで収まるため静止時も覆われない。
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
    fun `空棚のCTAは末尾まで送るとFAB帯の外に出る`() {
        setKGridView(true)
        val counts: Map<ReadingStatus, Int> = emptyMap()
        composeTestRule.setSkinKContent(ReadingTheme.LIGHT, 2.0f) { _ ->
            BookshelfK(
                data = KShelfFixtures.emptyData(),
                chrome = KShelfFixtures.chrome(counts),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
        // 空棚で縦スクロールを持つ器は空状態の Column ただ1つ（チップ行は横スクロール）。
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        composeTestRule.waitForIdle()

        // FAB は contentDescription、空棚 CTA は可視テキスト＝同じ語でも取り違えない。
        val fabTop = composeTestRule.onNodeWithContentDescription("PDFを追加")
            .fetchSemanticsNode().boundsInRoot.top
        val ctaBottom = composeTestRule.onNodeWithText("PDFを追加")
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "空棚 CTA が拡張FABの帯に食い込んでいる（CTA下端=$ctaBottom / FAB上端=$fabTop）",
            ctaBottom <= fabTop,
        )
    }
}
