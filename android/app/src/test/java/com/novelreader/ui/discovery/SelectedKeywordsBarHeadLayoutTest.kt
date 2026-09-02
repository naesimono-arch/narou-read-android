package com.novelreader.ui.discovery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.width
import com.novelreader.domain.SearchDraft
import com.novelreader.narou.SearchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 選択中キーワード帯（[DiscoverySearchContent] の bottomBar）の**見出し行**の版面テスト（裁定＝案 A'）。
 *
 * 何を守るのか: 正本 `docs/design-candidates/discovery/discovery-search-D.html` の `.sel-bar-head` は
 * `flex-wrap` 未指定＝**1行構図**で、折返しの許可は同じ帯の中で `.sel-chips` にだけ与えられている。
 * 実装は見出しに `Modifier.weight(1f)`（＝`flex:1 1 0`＝正本に無い伸長指定）を足していたため、
 * fontScale 2.0 で見出しが残り幅で2行に折れ、縦センターの「すべて解除」が2行の谷間へ落ちていた。
 * 案 A' は見出しを「選択中のキーワード」＋「◯件」の2本へ割り、**前半だけを縮退・末尾省略**して
 * 件数を必ず残す。守るべき性質は次の3つで、どれが欠けても裁定の意図が崩れる:
 *   1. 件数が丸ごと残る（省略・クリップされない）＝案 A に対する A' の存在理由そのもの
 *   2. 見出し行が1行に保たれる（見出し2本と「すべて解除」が同じ帯に同居する）
 *   3. 「すべて解除」が右端に居続ける（`justify-content: space-between` の翻訳）。
 *      ⚠️ 3 は `fill = false` 化で**壊れやすくなった箇所**——伸長で右へ押していたのを
 *      `Arrangement.SpaceBetween` に置き換えたので、arrangement が外れると静かに見出しの真横へ戻る。
 *
 * ⚠️ **削られたかどうかは [TextLayoutResult.hasVisualOverflow] で測らない**（初版はこれで書いて2件とも
 * 偽の赤になった）。wrap-content の Text——ここでは `weight(1f, fill = false)` と weight 無しの子＝
 * **自分の実測幅ちょうどで配置される Text**——は、全字が見えていても同フラグが true を返す
 * （先行実測 2026-08-17＝`ui/skins/k/SettingsRowWidthLayoutTest` の KDoc。丸め由来と推定・真因未確定）。
 * 旧実装が `weight(1f)`（fill = true）で器いっぱいに引き伸ばされていた頃は幅に余りがあり true にならなかったので、
 * A' で fill = false へ戻した瞬間に初めて踏んだ。⇒ **真偽が反転しない指標＝`getLineEnd(0, visibleEnd = true)`
 * （＝実際に見えている文字数）だけを使う。**
 *
 * ⚠️ [GraphicsMode.Mode.NATIVE] は必須。既定の LEGACY は実フォントを使わず文字幅＝文字数の定数になり、
 * fontScale を上げても幅が1px も動かない＝「溢れる／省略される」を前提にした本ファイルは
 * **全緑のまま検出力ゼロ**になる（`docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`）。
 *
 * 端末幅は実機と同じ 360dp（帯の左右内側余白 S24 を引いた可用幅 312dp が省略の発火条件を決めるため、
 * qualifiers を外すと意味が変わる）。高さを盛るのは fontScale 2.0 で帯が既定画面からはみ出さないようにするため。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1200dp-xhdpi")
class SelectedKeywordsBarHeadLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * 選択トークン3件で帯を出す。正本 `docs/design-candidates/discovery/discovery-search-D.html` の見本は2件だが、
     * ここを3件にするのは見出し前半の縮退（末尾省略）を確実に踏ませるため（＝2.0 側の可視文字数断言の前提）。
     *
     * @param fontScale [LocalDensity] を差し替えて fontScale だけを変える（density は qualifiers の
     *   xhdpi=2 を保つ）。`@Config(qualifiers)` に fontScale の直接指定が無いためこの張り方になる
     *   （既存 `DiscoverySearchContentTest` / `NovelDetailCoverBlockLayoutTest` と同一の作法）。
     */
    private fun showBar(fontScale: Float) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = fontScale,
                ),
            ) {
                MaterialTheme {
                    DiscoverySearchContent(
                        draft = SearchDraft(word = "ほのぼの スローライフ 悪役令嬢"),
                        history = SearchHistory(),
                        onBack = {},
                        onSetDraft = {},
                        onExecuteSearch = {},
                        onSearchHistoryWord = {},
                        onPinWord = {},
                        onUnpinWord = {},
                        onRemoveRecentWord = {},
                        onOpenConditionSheet = {},
                    )
                }
            }
        }
    }

    /**
     * 実際に組まれた結果（行数と可視文字数）を返す。箱の高さではなく描画結果を見る。
     * なぜ箱でなく [TextLayoutResult] か: `maxLines = 1` は上限であって「全字入った」保証ではなく、
     * 入らなければ黙って末尾が省略される（＝件数側で起きたら A' の意図が崩れているのに高さは変わらない）。
     */
    private fun layoutOf(text: String, substring: Boolean = false): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeTestRule.onNode(hasText(text, substring = substring), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.first()
    }

    /** 実際に見えている文字数（末尾省略・クリップを除いた1行目の行末オフセット）。 */
    private val TextLayoutResult.visibleChars: Int get() = getLineEnd(0, visibleEnd = true)

    /** 描こうとしている文字数（＝全字可視なら [visibleChars] と一致する）。 */
    private val TextLayoutResult.textLength: Int get() = layoutInput.text.length

    private fun boundsOf(text: String, substring: Boolean = false): DpRect =
        composeTestRule.onNode(hasText(text, substring = substring), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

    /** 2つの矩形が同じ帯に居る（縦方向に重なる）＝見出し行が1行に保たれていることの操作的定義。 */
    private fun assertSameBand(message: String, a: DpRect, b: DpRect) {
        assertTrue(
            "$message: $a / $b",
            a.top.value < b.bottom.value && b.top.value < a.bottom.value,
        )
    }

    @Test
    fun `fontScale 2_0 でも件数は丸ごと残り見出し行は1行に保たれる`() {
        showBar(fontScale = 2.0f)

        val head = layoutOf("選択中のキーワード")
        val count = layoutOf("3件", substring = true)
        // 較正値を残す（この版面で何dp・何字になったかが分からないと、次に閾値を疑う人が測り直せない）。
        println(
            "[selbar-head] 2.0 前半: 可視${head.visibleChars}/${head.textLength}字" +
                "・実幅=${boundsOf("選択中のキーワード").width}" +
                " / 件数: 可視${count.visibleChars}/${count.textLength}字" +
                "・実幅=${boundsOf("3件", substring = true).width}" +
                " / すべて解除: 実幅=${boundsOf("すべて解除").width}",
        )
        // maxLines = 1 なので lineCount は 1 を超えられない＝この2本は安全網であって検出力は無い。
        // 「1行が保たれる」の実質的な番人は下の assertSameBand（3者が同じ帯に居る）。
        assertEquals("見出し前半が2行へ折れている", 1, head.lineCount)
        assertEquals("件数が2行へ折れている", 1, count.lineCount)
        // A' の肝: 削るのは前半だけ。件数は weight を持たない＝Row が weight 無しの子を先に固有幅で
        // 測るので、残り幅の按分対象にならず1字も削られない。
        assertEquals(
            "件数が ${count.visibleChars} 字しか見えていない＝件数まで削られている（案A へ退行）",
            count.textLength,
            count.visibleChars,
        )
        // 前半が実際に削られていること自体も測る。ここが全字だとこの端末幅では縮退の経路を一度も
        // 踏んでおらず、上の断言が自明に真になる（検出力ゼロのテストになる）。
        assertTrue(
            "fontScale 2.0 でも前半が全字（${head.visibleChars}字）出ている＝縮退の前提が崩れている",
            head.visibleChars < head.textLength,
        )
        assertTrue("前半が1字も読めない＝器が痩せすぎ", head.visibleChars >= 1)

        val headBounds = boundsOf("選択中のキーワード")
        val countBounds = boundsOf("3件", substring = true)
        val clearBounds = boundsOf("すべて解除")
        assertSameBand("見出し前半と件数が別の行に居る", headBounds, countBounds)
        assertSameBand("見出しと「すべて解除」が別の行に居る（谷間落ちの再発）", headBounds, clearBounds)
        // 並びは 前半 → 件数 → すべて解除。件数が右端へ飛ぶ（＝3要素を素の SpaceBetween に並べた形）は不可。
        assertTrue(
            "件数が見出し前半の右隣に居ない: $headBounds / $countBounds",
            headBounds.right.value <= countBounds.left.value + TOLERANCE_DP,
        )
        assertTrue(
            "「すべて解除」が件数より左に居る: $countBounds / $clearBounds",
            countBounds.right.value <= clearBounds.left.value + TOLERANCE_DP,
        )
        // 右端定位置（SpaceBetween）。帯の右内側は 360-24=336dp、TextButton の contentPadding 12dp を
        // 引いた 324dp がラベル右端の期待値。伸長をやめた分ここは arrangement だけが支えている。
        assertTrue(
            "「すべて解除」が右端から離れている（SpaceBetween が外れた疑い）: $clearBounds",
            clearBounds.right.value >= 312f,
        )
    }

    @Test
    fun `fontScale 1_0 では見出しは省略されず1行のまま`() {
        showBar(fontScale = 1.0f)

        val head = layoutOf("選択中のキーワード")
        val count = layoutOf("3件", substring = true)
        println(
            "[selbar-head] 1.0 前半: 可視${head.visibleChars}/${head.textLength}字" +
                "・実幅=${boundsOf("選択中のキーワード").width}" +
                " / 件数: 可視${count.visibleChars}/${count.textLength}字" +
                "・実幅=${boundsOf("3件", substring = true).width}" +
                " / すべて解除: 実幅=${boundsOf("すべて解除").width}",
        )
        assertEquals("見出し前半が2行へ折れている", 1, head.lineCount)
        assertEquals("件数が2行へ折れている", 1, count.lineCount)
        // 1.0 は可用幅に収まる＝省略は発動しない。1字でも欠けたら等倍の版面が退行している
        // （golden `DiscoverySearchScreen_drafted_*_1.0` と同じ絵を守る番人）。
        assertEquals("等倍で見出しが ${head.visibleChars} 字に削られている", head.textLength, head.visibleChars)
        assertEquals("等倍で件数が ${count.visibleChars} 字に削られている", count.textLength, count.visibleChars)
        assertSameBand(
            "等倍で見出しと「すべて解除」が別の行に居る",
            boundsOf("選択中のキーワード"),
            boundsOf("すべて解除"),
        )
    }

    private companion object {
        /** dp は密度換算の丸めで端数が出うるので 0.5dp（xhdpi の 1px）まで許す。 */
        const val TOLERANCE_DP = 0.5f
    }
}
