package com.novelreader.ui.skins.k

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 設定行（[SettingsScreenK] の `KSettingsRow`）が **fontScale 2.0 でも行名を失わない**ことを固定する
 * 不変条件テスト。型＝`docs/knowledge/unweighted-trailing-steals-row-width.md`（非加重 trailing の幅先取り）、
 * 裁定＝`docs/design-candidates/skins/candidates/settings-K-row-width-candidates.html` の**案B**
 * （値に上限を付けて省略し、行名に幅を予約する）。
 *
 * なぜ golden（絵）でなくレイアウト値の断言か: この型は golden だと**壊れた絵を正として焼く**
 * （実際 `SettingsScreenK_followsystem_light_2.0.png` は「テーマ」が `…` だけに潰れた絵で初回記録された＝
 * `docs/knowledge/golden-record-bakes-in-regressions.md`）。verify は同名 golden との一致しか見ないので、
 * 焼いた後は永久に緑になる。ここでは [TextLayoutResult] を semantics 経由で取り出し、
 * **行名の可視文字数**という「何が正しいか」を直接言い切る。
 *
 * 固定する契約は2本で、**両方**が要る:
 *  1. 2.0 で行名「テーマ」が全字可視（＝値の側が省略されて幅を譲っている）。
 *  2. 1.0 では値「システムに従う」が無傷（＝上限が 1.0 の版面に届いていない）。
 *     案Bの前提そのもの＝上限を欲張って下げると 1.0 の絵が動く（golden 全枚の再記録＝退行）。
 *
 * この番人が特に見ている退行の向きは「行名の予約が痩せる」側: API 34 の sp→dp は**非線形**で、
 * 予約を `(16.sp * 4).toDp()` とまとめて換算すると 112dp が 68dp まで痩せ、行名が1字しか残らない
 *（実装中に実際に踏んだ・実測 2026-08-17）。上限定数を触った人がここで止まるように、可視文字数で縛る。
 *
 * ⚠️ 断言は [TextLayoutResult.hasVisualOverflow] でなく**可視文字数**で置く。この画面の Text は
 * いずれも「自分の実測幅ちょうど」で配置されるため、字が全部見えている 1.0 でも同フラグが true を
 * 返す（実測 2026-08-17: 1.0 の行名・値ともに全字可視で true）。丸め由来と推定されるが未確定なので、
 * 真偽が反転しない指標＝`getLineEnd(0, visibleEnd = true)` だけを使う。
 *
 * 撮る状態が `followingSystem=true` なのは、テーマ行に出る文言がこの値（7字）で最長になるため
 *（既定の「ライト」等は3字＝2.0 でも上限に届かず、この破綻が原理的に再現しない）。
 * 360dp 幅で見るのは案Bの上限 224dp がその版面で較正されているから（実機の主戦場もこの幅）。
 */
@RunWith(RobolectricTestRunner::class)
// 文字の実測幅が要る断言なので描画は NATIVE（legacy 影は glyph 幅を持たず、幅の破綻を再現できない）。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsRowWidthLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `テーマ行の行名が fontScale 2_0 でも全字読める`() {
        setSettings(fontScale = 2.0f)
        val title = layoutOf(node(TITLE))
        val visible = title.getLineEnd(0, visibleEnd = true)
        println(
            "[settings-row] 2.0 行名「$TITLE」: 可視${visible}文字" +
                " / 実幅=${widthPx(TITLE)}px（値=${widthPx(VALUE)}px）",
        )
        assertEquals("行名が折り返している（1行で読める前提が崩れた）", 1, title.lineCount)
        assertEquals(
            "行名の可視文字が ${visible} 文字＝値が行幅を先取りして何の行か読めない（破綻時の実測は0文字）",
            TITLE.length,
            visible,
        )
    }

    /**
     * 2.0 で削られる側が**値**であること（案Bが「何を失うか」として選んだ側）と、それでも
     * 値が丸ごと消えてはいないこと。行名を守った代償がここに出ていることを明示的に記録する
     *（値は叩けば復元できる＝ダイアログが現在値を選択状態で見せる。行名には復元手段が無い）。
     */
    @Test
    fun `テーマ行の値は fontScale 2_0 で省略される（失う側は値）`() {
        setSettings(fontScale = 2.0f)
        val value = layoutOf(node(VALUE))
        val visible = value.getLineEnd(0, visibleEnd = true)
        println(
            "[settings-row] 2.0 値「$VALUE」: 可視${visible}文字" +
                " / 実幅=${widthPx(VALUE)}px",
        )
        assertEquals("値が2行になった＝行高が変わる（案Bは行高も骨格も不変が条件）", 1, value.lineCount)
        assertTrue("値が ${visible} 文字＝全字出ている＝上限が効いていない（破綻時の実測は7文字）", visible < VALUE.length)
        assertTrue("値が1文字も読めない＝上限を切りすぎ", visible >= 1)
    }

    /** 1.0 は全行不変＝上限に届かない（届いていたら既存 golden 1.0 が動く＝退行）。 */
    @Test
    fun `fontScale 1_0 では行名も値も無傷（上限が届かない）`() {
        setSettings(fontScale = 1.0f)
        val title = layoutOf(node(TITLE))
        val value = layoutOf(node(VALUE))
        println(
            "[settings-row] 1.0 行名: 可視${title.getLineEnd(0, visibleEnd = true)}文字・実幅=${widthPx(TITLE)}px" +
                " / 値: 可視${value.getLineEnd(0, visibleEnd = true)}文字・実幅=${widthPx(VALUE)}px",
        )
        assertEquals("1.0 で行名が切れている", TITLE.length, title.getLineEnd(0, visibleEnd = true))
        assertEquals("1.0 で値が折り返している", 1, value.lineCount)
        assertEquals(
            "1.0 で値が省略された＝上限が 1.0 の版面まで下りてきている（既存 golden 1.0 が動く＝退行）",
            VALUE.length,
            value.getLineEnd(0, visibleEnd = true),
        )
    }

    private fun setSettings(fontScale: Float) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            // fontScale だけ差し替える（density は端末値のまま）＝golden 撮影ヘルパ captureThemed と同方式。
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                NovelReaderTheme(skin = Skin.MEIKAI_K, theme = ReadingTheme.LIGHT) {
                    SettingsScreenK(
                        appTheme = ReadingTheme.LIGHT,
                        onThemeChange = {},
                        // 値が最長になる状態＝この破綻の worst case（golden の followsystem case と同条件）。
                        followingSystem = true,
                        onFollowSystem = {},
                        currentSkin = Skin.MEIKAI_K,
                        onOpenWardrobe = {},
                        // 「データ」節の〈診断の記録〉行の飛び先。この観点では叩かないので no-op。
                        onOpenDiagnosticsExport = {},
                        skinSwitchingEnabled = true,
                    )
                }
            }
        }
    }

    /**
     * 文字ノードの名指し。**未マージ木**で引くのが必須＝テーマ行は `selectable` で子孫がマージされ、
     * マージ木では行名も値も同じ「行ノード」に当たる（そのノードの GetTextLayoutResult は
     * 最初の子＝行名の layout を返すので、値を測ったつもりで行名を測ってしまう）。
     */
    private fun node(text: String): SemanticsNodeInteraction =
        composeTestRule.onNodeWithText(text, useUnmergedTree = true)

    /** 断言そのものではなく較正の記録用（何 px 残ったかを実測値として println に残す）。 */
    private fun widthPx(text: String): Int = node(text).fetchSemanticsNode().size.width

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = requireNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action) {
            "GetTextLayoutResult アクションが無い＝Text ノードでない"
        }
        action(results)
        return results.first()
    }

    private companion object {
        /** テーマ行の行名。省略されても semantics のテキストは全文のまま＝壊れている側でも名指しできる。 */
        const val TITLE = "テーマ"

        /** `followingSystem=true` のときの値＝この行に出る文言の最長（7字）。 */
        const val VALUE = "システムに従う"
    }
}
