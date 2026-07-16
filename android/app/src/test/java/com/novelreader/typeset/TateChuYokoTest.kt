package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 縦中横 run 検出の合成テスト。当初は数字/!?のみだったが、2026-07-17 裁定で半角英字2〜3字run
 * （AW/SIM 等の略号が実データに存在）へ拡張（根拠は detectTateChuYokoRuns の KDoc）。
 * 旧蔵書コーパス（全角のみ）の「発火ゼロ」回帰は引き続き有効。
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
    fun `半角英字2〜3字は縦中横`() {
        // 実データ由来の略号（N3957FQ: AW/SIM 等）。
        assertEquals(listOf(TcyRun(0, 2)), detectTateChuYokoRuns("AW"))
        assertEquals(listOf(TcyRun(0, 3)), detectTateChuYokoRuns("SIM"))
    }

    @Test
    fun `半角英字1字と4字以上は縦中横にしない`() {
        // 1字（単独の E 等）は正立・4字以上（OLEM 等）は各字回転＝いずれも縦中横runにはならない。
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("E"))
        assertEquals(emptyList<TcyRun>(), detectTateChuYokoRuns("OLEM"))
    }

    @Test
    fun `同種ごとに別ランとして拾う`() {
        // "ab12cd" → 英字・数字は種別が違うため別ラン（混成 "ab12" にはしない）。全部長さ2＝3run とも縦中横。
        assertEquals(
            listOf(TcyRun(0, 2), TcyRun(2, 4), TcyRun(4, 6)),
            detectTateChuYokoRuns("ab12cd"),
        )
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
