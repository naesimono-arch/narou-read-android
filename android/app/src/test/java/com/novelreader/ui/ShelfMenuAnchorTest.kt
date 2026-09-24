package com.novelreader.ui

import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * アプリバー ⋮ の [androidx.compose.material3.DropdownMenu] が「ヘッダの途中」に開かないことの回帰
 * （M の観測野帳＝`BookshelfLogM` と J のグリッド面＝`BookshelfGridJ` の2サイト）。
 *
 * 何を守るか（2026-08-17 実機再現の真因・`docs/knowledge/dropdown-anchor-aligned-to-header-first-line.md`）:
 * `DropdownMenu` は**アンカーの下端**に上端を合わせて開く。⋮ の IconButton を包む Box をアンカーにすると、
 * アイコンは複数行ヘッダの1行目ぶんの高さしか占めないので、アンカー下端〜ヘッダ下端の帯（＝副題の字面上部）が
 * メニューの外に残って覗く。是正はアンカーを「⋮ の左右端 × ヘッダ**全体**の下端」へ出すことなので、
 * 守るべき不変条件は **メニュー上端 ≥ ヘッダ最下段テキストの下端**（絶対座標でなく関係）。
 *
 * なぜ golden でなくレイアウト結果で縛るか: M/J の本棚面には golden が1枚も無く（`GoldenCoverageTest` が
 * 撮影対象外と宣言）、この不変条件だけのために撮影ハーネスを新設するのは重い——`GridStatusLineWrapTest` が
 * D 側で採ったのと同じ判断。座標はレイアウト結果から直接取れるので推定でなく実測で判定できる。
 *
 * ⚠️ なぜメニュー側だけ `SemanticsNode` の座標を使わないか（機序）: `DropdownMenu` は `Popup`＝**別ウィンドウ**で
 * 描かれ、その中のノードの `positionInWindow` はポップアップ窓の原点基準になる。窓をまたげる `positionOnScreen` は
 * Robolectric では使えない——`ShadowViewRootImpl.relayoutWindow` が窓フレームを一切埋めないため
 * `AttachInfo.mWindowLeft/mWindowTop` が全窓 0 のままで、ポップアップ窓の画面位置が復元されない。
 * 一方 Compose の `PopupLayout` は算出結果を `WindowManager.LayoutParams.x/y` へ書いて `updateViewLayout` を呼び、
 * Robolectric の `ShadowWindowManagerImpl.addView` は実装本体（`WindowManagerImpl`）へ委譲するので
 * その LayoutParams はポップアップ View の `layoutParams` としてそのまま読める。値の基準はアンカーの
 * `positionInWindow`（＝ホスト窓座標）なので、ヘッダ側の窓座標と直接比較できる。
 *
 * 画面幅を明示するのは Robolectric の既定（320dp）が実機（360dp）と違い、ヘッダの折返し＝高さが変わるため
 * （golden 群・`GridStatusLineWrapTest` と同じ w360dp に揃える）。
 *
 * このテストが赤くなる条件: アンカーを ⋮ を包む Box へ戻す／ヘッダ行に3行目を足してアンカーの帯から外す／
 * メニューをヘッダの上側に開くよう position provider を変える。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
// ⚠️ [GraphicsMode] NATIVE 必須。不変条件〈メニュー上端 ≧ ヘッダ最下段テキストの下端〉の余裕は
// テキストの実高（フォントメトリクス）で決まり、既定の LEGACY はその実測を持たない
// （docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md・行高も作り物）。
// ヘッダが実寸より痩せるとアンカー下端との帯が縮み、守りたい「副題に被る」退行が再現しなくなる。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShelfMenuAnchorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val book = BookEntity(id = "b1", title = "扉の本", htmlDirPath = "/nonexistent/b1")

    /**
     * 面（M=星図/観測野帳・J=デッキ/グリッド）はスキン自身が prefs で所有する＝引数では渡せないため先置きする
     * （既存の BookshelfLogMTest / BookshelfPortalJTest と同じ作法）。
     */
    private fun setContent(skin: Skin) {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.M_SKY_VIEW, false)   // M＝観測野帳（LogPlate のヘッダ）
            .putBoolean(PrefKeys.J_DECK_VIEW, false)  // J＝グリッド面（GridTopBar のヘッダ）
            .commit()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSkin provides skin, LocalSkinTokens provides skin.tokens) {
                MaterialTheme {
                    BookshelfContent(
                        uiState = BookshelfUiState.Content(listOf(book)),
                        progressMap = emptyMap(),
                        chapterCountMap = mapOf(book.id to 5),
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

    /** そのノードが属するウィンドウの最上位 View（ポップアップなら `PopupLayout`・ホストなら DecorView）。 */
    private fun windowRootOf(node: SemanticsNode): View {
        var v: View = (node.root as? ViewRootForTest)?.view
            ?: error("Compose の View ルートが取れない（ViewRootForTest でない）")
        while (true) {
            val parent = v.parent
            if (parent is View) v = parent else break
        }
        return v
    }

    /** ⋮ メニュー窓の上端（ホスト窓と同じ座標系の px）。取り方の機序はクラス KDoc の⚠️節。 */
    private fun menuTopPx(menuItemText: String, headerText: String): Int {
        val menuNode = composeTestRule.onNodeWithText(menuItemText, useUnmergedTree = true).fetchSemanticsNode()
        val headerNode = composeTestRule.onNodeWithText(headerText, useUnmergedTree = true).fetchSemanticsNode()
        val menuWindow = windowRootOf(menuNode)
        // 同じ窓なら「メニューが Popup として出ていない」＝下の y 比較が無意味になるので、先にそこで落とす。
        assertNotSame("⋮ メニューが別ウィンドウ（Popup）で出ていない＝座標比較の前提が崩れている", windowRootOf(headerNode), menuWindow)
        val params = menuWindow.layoutParams
        assertTrue("Popup 窓の LayoutParams が WindowManager 由来でない: $params", params is WindowManager.LayoutParams)
        return (params as WindowManager.LayoutParams).y
    }

    /** ヘッダ内テキストの下端（ホスト窓座標の px）。クリップに左右されないよう位置＋高さで出す。 */
    private fun bottomInWindowPx(text: String): Float {
        val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        return node.positionInWindow.y + node.size.height
    }

    @Test
    fun `M観測野帳の⋮メニューは銘の2行目より下に開く`() {
        setContent(Skin.SEIZU_M)
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // 銘は〈題字「本棚」＋ .lmeta「観測 N 天体 …」〉の2行。露出していたのは2行目の字面上部なので、
        // 比較の相手は2行目のテキスト（"観測 " は .lmeta 先頭の実文言＝行の下端を代表する）。
        // メニュー本体は M 固有の「高負荷スカイ（試作）」節（debug ビルドでのみ出る＝JVM テストは debug）。
        val menuTop = menuTopPx(menuItemText = "高負荷スカイ（試作）", headerText = "観測 ")
        val metaBottom = bottomInWindowPx("観測 ")
        assertTrue(
            "M 観測野帳の⋮メニューが銘の2行目に被る（メニュー上端=$menuTop px < 2行目下端=$metaBottom px）" +
                "＝アンカーがヘッダ全体の下端でなく1行目に整列している",
            menuTop >= metaBottom,
        )
    }

    @Test
    fun `Jグリッド面の⋮メニューは題字の行より下に開く`() {
        setContent(Skin.PORTAL_J)
        composeTestRule.onNodeWithContentDescription("メニュー").performClick()
        // g-top は〈題字「本棚」＋冊数〉の1行だが、行の下余白（.g-top padding-bottom 12px）ぶんアイコン下端は
        // ヘッダ下端より上に来る＝M と同型の被り。題字はベースライン揃えの中で最も下端が低いので基準に採る。
        val menuTop = menuTopPx(menuItemText = "PDFを追加", headerText = "本棚")
        val titleBottom = bottomInWindowPx("本棚")
        assertTrue(
            "J グリッド面の⋮メニューが g-top の題字に被る（メニュー上端=$menuTop px < 題字下端=$titleBottom px）" +
                "＝アンカーがヘッダ全体の下端でなくアイコンの下端に整列している",
            menuTop >= titleBottom,
        )
    }
}
