package com.novelreader.typeset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-2b（新規6作品）のエッジケース実データコーパスによる回帰固定。
 *
 * 実測の要点（vertical-mode-p0-2b-halfwidth-followup.md が正本）:
 * - Web原文で半角だった英数字・!?も、なろうPDF抽出後は全て全角＝半角runは9作品で0件
 *   → 縦中横は本コーパスでも発火しないことを固定（半角対応は合成テスト担保のまま）。
 * - 一方で単幅の特殊字（ギリシャ・ローマ数字・〝〞・――・波ダッシュ異体）は作者ごとに出没する
 *   → 全角化の一般則に頼らず、コードポイント単位の分類が必要＝代表字の分類を固定。
 */
class EdgecaseCorpusTest {

    private fun corpusBodyLines(): List<String> {
        val stream = javaClass.getResourceAsStream("/typeset/edgecase_corpus.txt")
            ?: error("コーパス未配置: src/test/resources/typeset/edgecase_corpus.txt")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trimEnd() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.replaceFirst(Regex("^\\[[^]]*] "), "") }
                .toList()
        }
    }

    @Test
    fun `エッジコーパスでも縦中横は発火しない`() {
        // 全行が全角（半角ASCII run 0件）という実測の固定。半角の縦中横は合成ケース側で担保する。
        val bodyLines = corpusBodyLines()
        assertTrue("本文行が読めていること", bodyLines.isNotEmpty())
        for (line in bodyLines) {
            assertEquals("本文行に縦中横が出ないこと: $line", emptyList<TcyRun>(), detectTateChuYokoRuns(line))
        }
    }

    @Test
    fun `作者依存の単幅特殊字は正立に分類される`() {
        // ギリシャ（N3957FQ 629回）・ローマ数字（N9463BR Ⅳ）は分類表に載せず「未知→UPRIGHT」の
        // 防御既定で正立になることを固定（2026-07-17 実機計測で vert 変化なし＝正立が正解と確認済み）。
        for (ch in listOf("α", "β", "γ", "Δ", "Ⅳ")) {
            assertEquals("$ch は正立", CharClass.UPRIGHT, CharClassifier.classify(ch))
        }
    }

    @Test
    fun `縦書き用引用符は位置替えに分類される`() {
        // 〝〞（N8809BK 593回）は 2026-07-17 実機計測で vert 有効＝縦用の位置替え字形を持つ
        // → 正立既定でなく PUNCT_REPOSITION（描画層が vert を適用する経路）に載せる。
        for (ch in listOf("〝", "〞")) {
            assertEquals("$ch は位置替え", CharClass.PUNCT_REPOSITION, CharClassifier.classify(ch))
        }
    }

    @Test
    fun `ダッシュと波ダッシュは異体も含め回転に分類される`() {
        // ――は U+2015 の連続・波は本文=U+301C/タイトル=U+FF5E と出所で異体が割れる実測
        // → どちらのコードポイントも ROTATE に載っていることを固定。
        for (ch in listOf("―", "—", "〜", "～", "…", "‥")) {
            assertEquals("$ch は回転", CharClass.ROTATE, CharClassifier.classify(ch))
        }
    }

    @Test
    fun `全角英字略号は各字正立に分類される`() {
        // ＡＷ等の全角略号（N3957FQ で1365回）は縦中横でなく各字正立が正しい組み方。
        for (ch in "ＡＷＳＩＭ") {
            assertEquals("$ch は正立", CharClass.UPRIGHT, CharClassifier.classify(ch.toString()))
        }
        assertEquals("全角略号は縦中横runにならない", emptyList<TcyRun>(), detectTateChuYokoRuns("ＡＷ"))
    }
}
