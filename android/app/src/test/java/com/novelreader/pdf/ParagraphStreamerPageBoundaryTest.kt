package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ページ境界の空行復元（S3）を**機序のレベルで**見張る回帰ゲート。
 *
 * ## なぜ実 PDF の fixture と別に要るか
 * [WebAnchoredOracleTest.n0833hi_s3_blankLinesRestored] は実 PDF 1 話ぶんで結果を突き合わせるが、
 * 見えるのは「合計が合っているか」だけで、**どの条件で入れ／どの条件で入れないか**は縛れない。
 * この欠陥の再発は「入れるべき所で入れない」より「入れてはいけない所で入れる」（＝偽の空行）の方が
 * 害が大きく、その 2 条件（題名ページ・非隣接ページ）は実測でそれぞれ 180 件・2 件の誤検出を生んだ
 * 実績がある（N0833HI / N3957FQ）。合成ページなら列グリッドを完全に固定できるので、
 * 条件ごとに 1 本ずつ赤にできる。
 *
 * 版面は [DetectedRules.FALLBACK]（実測値＝1 ページ 30 列・列ピッチ 22.68）に合わせて組む。
 */
class ParagraphStreamerPageBoundaryTest {

    /** 列グリッドの右端（0 列目の x0）。値そのものに意味は無く、[COLS] 列が収まればよい。 */
    private val originX = 741.35
    private val step = ParserRules.LINE_STEP_X

    /** 1 ページの列数＝列グリッド幅 ÷ 列ピッチ + 1（FALLBACK 実測の 30 列）。 */
    private val cols = Math.round(ParserRules.COLUMN_SPAN_X / step).toInt() + 1

    /** 0 始まりの列番号 [j] の x0。右（0 列目）から左へ 1 列ずつ送る縦組みの版面。 */
    private fun x(j: Int) = originX - j * step

    /** 列 [j] に本文 1 文字だけを置いた CharBox（フォントは Bold を含まない＝題名にならない）。 */
    private fun body(j: Int, t: String) =
        CharBox(t, "Mincho", ParserRules.FONT_SIZE_BODY_TITLE, x(j), 100.0, 114.0)

    /** 題名グリフ（Bold＋本文サイズ）を 0 列目に置く。 */
    private fun title(t: String) =
        CharBox(t, "Mincho-Bold", ParserRules.FONT_SIZE_BODY_TITLE, x(0), 50.0, 64.0)

    /** 列 [range] を 1 文字ずつ [t] で埋めたページ。 */
    private fun page(range: IntRange, t: String) = range.map { body(it, t) }

    /**
     * ページ 3..(3+pages.size-1) として流す。先頭 3 ページ（表紙）と最終ページ（クレジット）は
     * [TextProcessor.ParagraphStreamer] が捨てる仕様なので、その外側に 1 ページ足した総数を渡す。
     */
    private fun stream(vararg pages: List<CharBox>): List<String> {
        val out = mutableListOf<String>()
        val s = TextProcessor.ParagraphStreamer(3 + pages.size + 1, DetectedRules.FALLBACK) { out.add(it) }
        for ((i, p) in pages.withIndex()) s.addPage(3 + i, p)
        s.finish()
        return out
    }

    // ---- 復元される側 ----

    @Test fun 次ページ先頭が1列空いていれば空行1つを復元する() {
        // 前ページは左端まで詰まっている＝空きは次ページ側の 1 列だけ。
        val r = stream(page(0 until cols, "あ"), page(1 until cols, "い"))
        assertEquals(listOf("あ".repeat(cols), "", "い".repeat(cols - 1)), r)
    }

    @Test fun 前ページ末尾が2列余っていれば空行2つを復元する() {
        val r = stream(page(0..(cols - 3), "あ"), page(0 until cols, "い"))
        assertEquals(listOf("あ".repeat(cols - 2), "", "", "い".repeat(cols)), r)
    }

    @Test fun 前ページの余りと次ページの空きは合算される() {
        // 前 1 列 + 次 1 列＝2 つ。両端を別々に測るのではなく合計で出るのがこの実装の要点。
        val r = stream(page(0..(cols - 2), "あ"), page(1 until cols, "い"))
        assertEquals(listOf("あ".repeat(cols - 1), "", "", "い".repeat(cols - 1)), r)
    }

    // ---- 復元してはいけない側（＝偽の空行を作らない条件） ----

    @Test fun 隙間が無ければ段落はページを跨いで繋がったまま() {
        val r = stream(page(0 until cols, "あ"), page(0 until cols, "い"))
        assertEquals(listOf("あ".repeat(cols) + "い".repeat(cols)), r)
    }

    @Test fun 題名のあるページには空行を入れない() {
        // 章の題名は版面の先頭側を占有し本文はその左から始まる＝この空きは空行ではない。
        val r = stream(
            page(0 until cols, "あ"),
            listOf(title("章")) + page(4 until cols, "い"),
        )
        assertEquals(listOf("あ".repeat(cols), "【題名】章", "い".repeat(cols - 4)), r)
    }

    @Test fun 本文列を持たないページを挟んだら空行を入れない() {
        // 挿絵・区切りページを挟むと「列グリッドが連続している」という前提自体が崩れる。
        // 空行を入れない＝この改修前と同じく段落は繋がったままになる（挙動を変えないことが要件）。
        val r = stream(page(0 until cols, "あ"), emptyList(), page(5 until cols, "い"))
        assertEquals(listOf("あ".repeat(cols) + "い".repeat(cols - 5)), r)
    }

    @Test fun 版面が読めない文書では空行を入れない() {
        // columnSpanX が 0（＝検出も退避も効かない仮想の版面）なら差は必ず負＝1 つも入れない。
        val out = mutableListOf<String>()
        val rules = DetectedRules.FALLBACK.copy(columnSpanX = 0.0)
        val s = TextProcessor.ParagraphStreamer(6, rules) { out.add(it) }
        s.addPage(3, page(0..(cols - 3), "あ"))
        s.addPage(4, page(1 until cols, "い"))
        s.finish()
        assertEquals(listOf("あ".repeat(cols - 2) + "い".repeat(cols - 1)), out)
    }
}
