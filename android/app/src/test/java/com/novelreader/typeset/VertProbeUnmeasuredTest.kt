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
        assertTrue("計測データが読めていること（73 コードポイント想定）", measured.size > 50)
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
    fun `301Fと FFE3が未計測であることを名指しで固定する`() {
        // 委譲仕様が名指しした2字。集合から不用意に消えると「未検証である」事実まで消えるので、
        // 計測されたときにだけ（上のテストが赤くなる形で）外れるよう二重に留める。
        val measured = measuredCodePoints()
        assertTrue("〟U+301F は実機未計測のはず", 0x301F !in measured)
        assertTrue("￣U+FFE3 は実機未計測のはず", 0xFFE3 !in measured)
        assertTrue("〟が未計測台帳に載っていること", "〟" in VertFeatureCoverage.UNMEASURED_IN_VERT_PROBE)
        assertTrue("￣が未計測台帳に載っていること", "￣" in VertFeatureCoverage.UNMEASURED_IN_VERT_PROBE)
    }
}
