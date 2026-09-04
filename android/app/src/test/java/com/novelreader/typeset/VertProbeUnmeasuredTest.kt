package com.novelreader.typeset

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「実データに出るのに vert 実機計測が無い字」の台帳（[VertFeatureCoverage.UNMEASURED_IN_VERT_PROBE]）が
 * 実際の計測データ（`test/resources/typeset/vert_probe_pgem10.jsonl`）と食い違っていないことを守る。
 *
 * ## なぜテストで縛るか
 * 「未検証」の記録はドキュメントに書くだけだと**腐っても誰も気づかない**——実際、`CharClass.kt` の
 * コメントは 2026-09-03 まで「〝〞〟の3字とも実機計測済み」と書いていたが、計測データに 〟U+301F は
 * 存在しなかった（実データの主役 593 件が未検証のまま「計測済み」と読める状態で 1 ヶ月半潜伏した）。
 * 実機が使えない便では分類を足せてしまう一方で計測は足せないので、**未検証の申告だけが増える**。
 * 増えた申告が後で計測されたときに消し忘れると、今度は逆向きの嘘（計測済みなのに未計測と書いてある）に
 * なるため、突合を機械に持たせる。
 *
 * ⚠️ このテストは「計測せよ」とは言わない（実機が要る＝`/device-verify` 案件）。言えるのは
 * **台帳と計測データが一致しているか**だけ。計測を足したらこの集合から消す＝そのときこのテストが道標になる。
 */
class VertProbeUnmeasuredTest {

    /** 計測 JSONL に現れるコードポイント集合（1行1計測・`"codepoint": 12289` 形式）。 */
    private fun measuredCodePoints(): Set<Int> {
        val stream = javaClass.getResourceAsStream("/typeset/vert_probe_pgem10.jsonl")
            ?: error("計測データ未配置: src/test/resources/typeset/vert_probe_pgem10.jsonl")
        val re = Regex("\"codepoint\"\\s*:\\s*(\\d+)")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { re.find(it)?.groupValues?.get(1)?.toInt() }.toSet()
        }
    }

    @Test
    fun `未計測として記録した字が計測データに存在しないこと`() {
        val measured = measuredCodePoints()
        assertTrue("計測データが読めていること（97 コードポイント想定）", measured.size > 50)
        val contradictions = VertFeatureCoverage.UNMEASURED_IN_VERT_PROBE
            .filter { it.codePointAt(0) in measured }
        assertTrue(
            "UNMEASURED_IN_VERT_PROBE に載っているのに vert_probe_pgem10.jsonl で計測済みの字がある＝" +
                "計測を足したときの台帳更新漏れ。該当字を集合から外し、分類の根拠を実測へ書き換えること: " +
                contradictions.joinToString(" ") { "$it(U+%04X)".format(it.codePointAt(0)) },
            contradictions.isEmpty(),
        )
    }

    @Test
    fun `2026-09-03 棚卸しの24字が計測済みであること`() {
        // 2026-09-04 に PGEM10 の実機で計測した 24 字。JSONL からこの実測が落ちると、コメントだけが
        // 「実測で裏付けた」と言い張る旧状態（301F で 1 ヶ月半潜伏した嘘）へ逆戻りするので名指しで留める。
        // ⚠️ この 24 字は「もう分類が確定した」という意味ではない——vert が効くと分かった字
        // （〟￣ゝヽヾ）と効かないと分かった字の扱いは knowledge 側が持つ。ここが守るのは実測の存在だけ。
        val measured = measuredCodePoints()
        val stocktake = "〟￣－—‐–↑↓‼⁉％℃°′″＃＄ヽヾゝ“”‘’"
        // toList()＝String のままだと joinToString が生えない（CharSequence には無い）。
        val missing = stocktake.filter { it.code !in measured }.toList()
        assertTrue(
            "棚卸し 24 字のうち計測データから消えた字がある: " +
                missing.joinToString(" ") { "$it(U+%04X)".format(it.code) },
            missing.isEmpty(),
        )
        assertTrue("棚卸しは 24 字のはず", stocktake.length == 24)
    }
}
