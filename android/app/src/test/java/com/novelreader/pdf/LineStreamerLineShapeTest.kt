package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列→**原文の行**の復元規則（ADR 0041 決定2）を条件ごとに 1 本ずつ縛る回帰ゲート。
 *
 * ## なぜ実 PDF の突合と別に要るか
 * [WebAnchoredOracleTest] の S3-line は実 PDF の行構造を web 原文オラクルと突き合わせるが、
 * 赤くなったときに**どの規則が崩れたか**は分からない（1 本の等式に全条件が畳まれている）。
 * 復元規則は 5 分岐あり、うち 2 つは原理的に曖昧な境界に置いた経験則＝
 * 触ったときに「どちらへ倒したか」を明示的に固定しておかないと、静かに読み味だけが変わる。
 *
 * 合成ページなら列の文字数と前後の字を完全に指定できるので、分岐ごとに独立して赤にできる。
 * 版面は [DetectedRules.FALLBACK]（列ピッチ 22.68・容量 [ParserRules.COLUMN_CAPACITY]）に合わせる。
 */
class LineStreamerLineShapeTest {

    private val originX = 741.35
    private val step = ParserRules.LINE_STEP_X
    private val cap = ParserRules.COLUMN_CAPACITY

    /** 列 [j]（0＝右端）に文字列 [s] を 1 文字 1 セルで縦に並べる。 */
    private fun col(j: Int, s: String): List<CharBox> =
        s.mapIndexed { i, ch ->
            val top = 100.0 + i * ParserRules.FONT_SIZE_BODY_TITLE
            CharBox(
                ch.toString(), "Mincho", ParserRules.FONT_SIZE_BODY_TITLE,
                originX - j * step, top, top + ParserRules.FONT_SIZE_BODY_TITLE,
            )
        }

    /** 列を右から順に詰めて 1 ページに置き、出てきた行列を返す。 */
    private fun linesOf(vararg columns: String): List<String> = streamPage(
        columns.mapIndexed { j, s -> col(j, s) }.flatten(),
    )

    /** 列番号を明示して 1 ページに置く（空きスロットを作りたいとき）。 */
    private fun linesOfAt(vararg placed: Pair<Int, String>): List<String> = streamPage(
        placed.map { (j, s) -> col(j, s) }.flatten(),
    )

    private fun streamPage(chars: List<CharBox>): List<String> {
        val out = mutableListOf<String>()
        val s = TextProcessor.LineStreamer(5, DetectedRules.FALLBACK) { out.add(it) }
        s.addPage(3, chars)
        s.finish()
        return out
    }

    private fun repeat(n: Int, c: Char = 'あ') = c.toString().repeat(n)

    @Test fun 容量に満たない列はそこで行が終わる() {
        // 折り返しは容量いっぱいでしか起きない＝短い列は必ず行末（曖昧さの無い確定判定）。
        assertEquals(listOf("あああ", "いいい"), linesOf("あああ", "いいい"))
    }

    @Test fun 容量ちょうどの列は次の列へ続く() {
        assertEquals(listOf(repeat(cap) + "いい"), linesOf(repeat(cap), "いい"))
    }

    @Test fun 容量ちょうどでも文末文字の次が全角空白なら行末とみなす() {
        // 「。」で終わった行の次が字下げで始まる＝新しい行、という経験則（コーパス実測で優勢）。
        val full = repeat(cap - 1) + "。"
        assertEquals(listOf(full, "　いい"), linesOf(full, "　いい"))
    }

    @Test fun 容量ちょうどでも閉じ括弧の次が開き括弧なら行末とみなす() {
        // 対話行が連続する形。インライン用法（同じ行の中で 」「 が並ぶ）より実測で優勢。
        val full = repeat(cap - 1) + "」"
        assertEquals(listOf(full, "「いい"), linesOf(full, "「いい"))
    }

    @Test fun 容量プラス1で末尾が禁則文字ならぶら下がりとして次列へ続く() {
        val hang = repeat(cap) + "。"
        assertEquals(listOf(hang + "いい"), linesOf(hang, "いい"))
    }

    @Test fun 容量プラス1でも次列が行頭指標で始まるなら行末とみなす() {
        val hang = repeat(cap) + "。"
        assertEquals(listOf(hang, "「いい"), linesOf(hang, "「いい"))
    }

    @Test fun 容量プラス1で末尾が禁則文字でなければwidow回避として行末() {
        // 行の残りが 1 文字のときだけ起きる引き込み＝そこで行は終わっている（確定判定）。
        val widow = repeat(cap) + "字"
        assertEquals(listOf(widow, "いい"), linesOf(widow, "いい"))
    }

    @Test fun 空きスロットを挟めば容量ちょうどでも行は切れる() {
        // 列 0 と列 2 に置く＝間の 1 列が空＝空行 1 つ。空きは曖昧さの無い行末。
        assertEquals(
            listOf(repeat(cap), "", "いい"),
            linesOfAt(0 to repeat(cap), 2 to "いい"),
        )
    }

    @Test fun ルビが付いていても判定は版面の字で行う() {
        // ルビは `|親《読み》` として行文字列へ埋め込まれるが、行の復元は**版面に置かれた字**を見る
        // ＝末尾がルビ記法の `》` に化けても「容量ちょうどで続く」判定が壊れないこと。
        val body = col(0, repeat(cap)) + col(1, "いい")
        // 先頭列の最終文字へルビを付ける（親列 x0 + rubyOffsetX のレーンに 1 文字置く）。
        val rubyTop = 100.0 + (cap - 1) * ParserRules.FONT_SIZE_BODY_TITLE
        val ruby = CharBox(
            "よ", "Mincho", ParserRules.FONT_SIZE_RUBY,
            originX + ParserRules.RUBY_OFFSET_X, rubyTop, rubyTop + ParserRules.FONT_SIZE_RUBY,
        )
        assertEquals(
            listOf(repeat(cap - 1) + "|あ《よ》" + "いい"),
            streamPage(body + listOf(ruby)),
        )
    }
}
