package com.novelreader.ui.skins.k

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.theme.ReadingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 明快K「本棚」グリッドの**横向き5列**スクリーンショット回帰（ADR 0009 増補1）。
 *
 * なぜ縦向きの [BookshelfKScreenshotTest] と別クラスか: 画面の向きは Robolectric の `@Config(qualifiers)`
 * ＝**クラス単位**の設定で、同一クラス内のケースとして混ぜられないため。
 *
 * なぜ撮るか: 「縦=2列／横=5列」は 2026-07-26 のユーザー裁定（案L5・正本モック
 * docs/design-candidates/skins/bookshelf-K-landscape.html）で、横だけ列数を変える理由も
 * 「縦と同じ2列だと横800dp級で書影が364dpへ肥大し1画面の収納数が激減する」と明文化されている。
 * にもかかわらず、この分岐（`LocalConfiguration.orientation` 判定）には検査が一切無く、
 * 回転時だけ壊れる退行は**実機を横にした人にしか気づけない**状態だった。
 *
 * 縦向き golden と**同じフィクスチャ（[KShelfFixtures.mixedData]）**を使う: 裁定が
 * 「変数は列数のみ・余白/アスペクト比/キャプション構成は縦横同値」と定めているため、同一データで
 * 撮った2枚の差分がそのまま「列数だけが変わる」ことの証明になる（データを変えるとその対応が崩れる）。
 *
 * 1枚（ライト×スケール1.0）に絞る理由: この golden が固定する主張は列数と、それに従属するセル幅・
 * 書影の実寸だけで、色トークンとフォントスケール破綻は縦向きの全数マトリクスが既に張っている。
 * 同じ検出力を横向きでも買い直す価値は無い。
 *
 * このテストが赤くなる条件:
 *  ・横向き時の列数が 5 から変わる（縦と同じ2列へ戻す・4列や6列へ変える）
 *  ・向き判定そのものが壊れる（回転しても縦の2列のまま＝書影が肥大する）
 *  ・列間 S32・左右 S24・書影アスペクト 3:4・キャプション1行 clamp が横向きだけ別値になる
 *  ・ヘッダ/フィルタチップ/FAB が横向きで別レイアウトへ分岐した場合
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// w>h と land を両方明示する（どちらか一方だと Robolectric 側の整合規則に解釈を委ねることになる）。
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class BookshelfKLandscapeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        // 横向きの裁定はグリッド面のもの＝リストへ落ちないようグリッドを先置きする。
        setKGridView(true)
        val theme = ReadingTheme.LIGHT
        val fontScale = 1.0f

        composeTestRule.captureSkinK(
            theme,
            fontScale,
            goldenName("BookshelfK", "grid_landscape", theme, fontScale),
        ) { _ ->
            BookshelfK(
                data = KShelfFixtures.mixedData(),
                chrome = KShelfFixtures.chrome(KShelfFixtures.mixedStatusCounts),
                actions = KShelfFixtures.actions,
                selection = KShelfFixtures.selection,
                webActions = KShelfFixtures.webActions,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}
