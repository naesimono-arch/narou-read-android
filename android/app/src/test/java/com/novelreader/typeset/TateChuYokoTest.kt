package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 縦中横 run 検出の合成テスト。P0-2 で実蔵書に半角対象が事実上ゼロと確認済みのため
 * 担保はここの合成ケース＋実データコーパスの「発火ゼロ」回帰で行う。
 */
class TateChuYokoTest {

    @Test
    fun `半角数字2桁3桁は縦中横`() {
        assertEquals(listOf(TcyRun(0, 2)), detectTateChuYokoRuns("12"))
        assertEquals(listOf(TcyRun(0, 3)), detectTateChuYokoRuns("345"))
    }

    @Test
    fun `半角数字1桁は縦中横にしない`() {
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("1"))
    }

    @Test
    fun `半角数字4桁以上は縦中横にしない`() {
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("1234"))
    }

    @Test
    fun `半角感嘆疑問の2桁3桁は縦中横`() {
        assertEquals(listOf(TcyRun(0, 2)), detectTateChuYokoRuns("!?"))
        assertEquals(listOf(TcyRun(0, 2)), detectTateChuYokoRuns("!!"))
        assertEquals(listOf(TcyRun(0, 3)), detectTateChuYokoRuns("!!?"))
    }

    @Test
    fun `半角疑問1個は縦中横にしない`() {
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("?"))
    }

    @Test
    fun `英字に挟まれた数字ランのみ拾う`() {
        // "ab12cd" → 英字はラン対象外・数字 "12" だけが縦中横（index 2..4）。
        assertEquals(listOf(TcyRun(2, 4)), detectTateChuYokoRuns("ab12cd"))
    }

    @Test
    fun `全角数字は一切対象外`() {
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("１２"))
    }

    /**
     * P0-2 の「実蔵書で縦中横は発火しない」実測を回帰固定する。
     * コーパスの本文行（コメント/空行を除き [出典ID] プレフィックスを剥がす）で run が0件であることを確認。
     * なぜプレフィックスを剥がすか: [N6169DZ] 等はテスト由来のタグで本文ではない
     * （4桁ID自体は 4桁扱いで縦中横にならないが、本文の計測意図に合わせ本文だけを対象にする）。
     */
    @Test
    fun `実データコーパスで縦中横は発火しない`() {
        val stream = javaClass.getResourceAsStream("/typeset/tatechuyoko_corpus.txt")
            ?: error("コーパス未配置: src/test/resources/typeset/tatechuyoko_corpus.txt")
        val bodyLines = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trimEnd() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.replaceFirst(Regex("^\\[[^]]*] "), "") }
                .toList()
        }
        assertTrue("本文行が読めていること", bodyLines.isNotEmpty())
        for (line in bodyLines) {
            assertEquals("本文行に縦中横が出ないこと: $line", emptyList<TcyRun>(), detectTateChuYokoRuns(line))
        }
    }
}
