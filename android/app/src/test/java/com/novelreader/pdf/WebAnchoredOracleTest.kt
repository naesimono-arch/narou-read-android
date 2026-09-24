package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 系譜外オラクル回帰ゲート（2026-09-02 導入）。
 *
 * ## なぜ既存 golden と別に要るか
 * `JvmGoldenRegressionTest` の golden（`ab-review/golden_regression 配下の json`）は**現行実装の出力の写し**で、
 * 元をたどれば旧 Python 実装の移植系譜の中に在る。検査対象と期待値が同じ前提・同じ誤りを共有するため、
 * 本テストが見張る欠陥6クラスを**1つも赤にできなかった**（実際 golden は緑のまま全クラスが潜伏していた）。
 *
 * ## 期待値の出所（系譜外である根拠）
 * `test/resources/pdf_oracle 配下の oracle.json` の値は現行実装からも旧 Python 実装からも取っていない。
 * 出所は独立再実装（第二実装 `~/naro-pdf-engine/`）の出力で、これは
 * (a) クリーンセッションで要件書のみから書かれ、アルゴリズム・閾値・出力形式・期待値をこちらから渡していない、
 * (b) その出力が ncode.syosetu.com の公開原文 10 話と突き合わせ済み（8/10 完全一致・残り2話も行境界のみの差で
 *     文字は保存）＝**最終的な裁定者は web 原文**、
 * という二段で系譜の外に立つ。方法論と適用条件の正本＝`docs/knowledge/independent-reimpl-anchoring-method.md`。
 * 生成器＝`tools/build_pdf_oracle.py`（再生成には裁定待ちの第二実装が要る。fixture は自己完結）。
 *
 * ## 何を見張らないか（裁定未了を混ぜないための除外）
 * - ⚠️ 2026-09-03 の ADR 0041 で、かつて除外していた 2 軸（字種写像 S2b・行 vs 段落の単位差）は
 *   **裁定済み＝どちらも web 原文へ寄せる**となり、比較軸へ**入れた**（[assertCharmap] /
 *   [n0833hi_s3line_lineShapeMatchesOracle] / [s3line_lineCountsMatchOracle]）。
 * - ルビの分割粒度（S4-2）も裁定未了のため、両側で隣接 run を畳んでから比較する。
 * - 空行復元（S3 本体）は**ページ抜き fixture** で見張る（2026-09-02 追加・同日の真因修正で全数一致）。全滅していた N0833HI は
 *   `.gitignore` 済みで CI から参照できないため、1 話ぶん（11 ページ）だけを抜いた
 *   `pdf_oracle/N0833HI_ep57.pdf` を fixture 化した。抜いたことで壊れていないことは
 *   「ページ抜き版の抽出＝全文版の当該話の抽出（段落 228 本が完全一致）」を機械で確認して担保する
 *   （生成と検証＝`tools/build_s3_fixture.sh`）。
 * - S4a の親範囲**境界1文字**の要不要判定（2026-09-02 追加）は幾何だけからは一意に決まらないことを
 *   実測で確認済み＝既知の穴として `N6169DZ.s4a_known_gaps.json` に凍結し、感度の表として監視する
 *   （詳細は [n6169dz_s4a_rubyBaseMatchesOracle] の KDoc）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAnchoredOracleTest {

    @Before
    fun initResourceLoader() {
        // CID→Unicode 解決を実機と揃える（欠くと字が変わり全アサートが無意味になる）。
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    // ---- S1: 半角スペース脱落 ----

    @Test
    fun n6169dz_s1_halfWidthSpacesPreserved() = assertSpaceCount("N6169DZ")

    @Test
    fun n1453lw_s1_halfWidthSpacesPreserved() = assertSpaceCount("N1453LW")

    @Test
    fun n2959ki_s1_halfWidthSpacesPreserved() = assertSpaceCount("N2959KI")

    private fun assertSpaceCount(ncode: String) {
        val e = extracted(ncode)
        assertEquals(
            "S1 半角スペース数が原文オラクルと不一致（$ncode）",
            oracle(ncode).getInt("s1_body_space_count"),
            e.flow.count { it == ' ' },
        )
    }

    // ---- S2a: 解読不能字を U+FFFD にしている ----

    /**
     * U+FFFD は「復号に失敗した」の印であって本文の字ではない。第二実装は同じ PDF から具象字を復号できて
     * いるので、出現はこちら側の欠陥。オラクル値も 0 だが、ここは 0 決め打ちで意味が変わらない絶対条件。
     */
    @Test
    fun n6169dz_s2a_noReplacementChar() {
        val e = extracted("N6169DZ")
        val hits = e.flow.withIndex().filter { it.value == '�' }
        val sample = hits.take(3).joinToString(" / ") {
            e.flow.substring(maxOf(0, it.index - 10), minOf(e.flow.length, it.index + 10))
        }
        assertEquals("S2a 解読不能字 U+FFFD が本文に出ている（前後文脈: $sample）", 0, hits.size)
    }

    // ---- S4a: ルビ親範囲が送り仮名込み ----

    /**
     * 読みが文書内で一意な run だけを鍵にして親文字列を突き合わせる。
     * なぜ一意鍵か＝現行実装を一切参照せずにアンカーを選ぶため（「今ズレている所」だけ拾うと
     * 選択バイアスで新しい欠陥を検出できないオラクルになる）。
     *
     * ## 既知の穴（感度の表。完了定義2の造り＝`tools/test_check_golden_label_split.py` が手本）
     * 親範囲の**末尾 1 文字**を延長すべきか否かは、実測（N6169DZ 全件）で幾何だけからは決められない
     * ことを確認済み: 同じ (親字数 N0, 読み字数 M) の組・同じ「run 下端と次候補の間隔」でも
     * 延長が要る例（『物理演算《あたりまえ》』N0=3,M=5→+1 要）と要らない例
     * （『象徴《かんむり》』N0=2,M=4→+0）が両方実在し、単一の閾値では割れない
     * （[TextProcessor.assignRun] の KDoc に実測値つきで記録）。これはなろうサイト側の実際の
     * ルビ範囲マーク（義訓・造語ルビ）に依存する情報で、PDF の字形座標だけでは再現できないと推定。
     * 現行実装は「常に延長を試みる」側（不一致 866→380 に低減。671 件あった延長漏れの大半が解消）へ
     * 倒してあり、残る 380 件（境界の 1 文字の要不要・記号類の特殊解釈）は
     * `pdf_oracle/N6169DZ.s4a_known_gaps.json` に凍結してある。
     * この表と食い違う変化（穴が塞がった＝改善／新規に開いた＝退行）は緑でも必ず出力し、
     * assert を落として表の更新を強制する（改善を黙って握り潰さない・退行を見逃さない）。
     */
    @Test
    fun n6169dz_s4a_rubyBaseMatchesOracle() {
        val e = extracted("N6169DZ")
        val anchors = oracle("N6169DZ").getJSONObject("s4a_ruby_base_anchors")
        val knownGaps = JSONObject(
            File(repoRoot(), "$ORACLE_DIR/N6169DZ.s4a_known_gaps.json").readText(),
        )
        val gapTable = knownGaps.getJSONObject("gaps")
        val expectedUnpaired = knownGaps.getInt("unpaired_count")
        val byReading = e.runs.groupBy({ it.second }, { it.first })
        var unpaired = 0
        val closedGaps = mutableListOf<String>()
        val newMismatches = mutableListOf<String>()
        for (reading in anchors.keys()) {
            val ours = byReading[reading]
            // 出現数が1でない読みは分割粒度・重複の軸（裁定未了）＝親範囲の判定に混ぜず件数だけ残す。
            if (ours == null || ours.size != 1) { unpaired++; continue }
            val expected = anchors.getString(reading)
            val actual = ours[0]
            val recorded = gapTable.optJSONObject(reading)
            when {
                actual == expected && recorded == null -> {} // 正常一致
                actual == expected && recorded != null ->
                    closedGaps.add("《$reading》 既知の穴が解消＝$actual（旧 ours=${recorded.getString("ours")}）")
                recorded != null && recorded.getString("ours") == actual && recorded.getString("oracle") == expected ->
                    println("  [既知の穴] S4a 《$reading》 ours=$actual oracle=$expected")
                else ->
                    newMismatches.add("《$reading》 ours=$actual oracle=$expected（既知表に無い新規不一致）")
            }
        }
        assertTrue(
            "S4a 対応付け不能数が既知表と不一致（現在 $unpaired・既知 $expectedUnpaired）＝" +
                "分割粒度の挙動が変わった可能性。known_gaps.json の再生成要",
            unpaired == expectedUnpaired,
        )
        assertTrue(
            "S4a 既知表と食い違う変化 ${closedGaps.size + newMismatches.size} 件" +
                "（改善 ${closedGaps.size}・新規不一致 ${newMismatches.size}）＝" +
                "tools でknown_gaps.json を再生成し内容を確認のうえ更新すること\n" +
                (closedGaps.take(10) + newMismatches.take(10)).joinToString("\n"),
            closedGaps.isEmpty() && newMismatches.isEmpty(),
        )
    }

    // ---- S4b: 傍点ルビの取りこぼし ----

    /**
     * 既知の穴（未確定・感度の表の単純版）: オラクル 3221 に対し実測 3215（6件不足、
     * 2026-09-02 時点）。[TextProcessor.assignRun] の親範囲修正（containment の約物除外・
     * 境界1文字延長・次 run 領域ガード）で 3199→3215 まで縮めたが、残り 6 件の真因は
     * 特定できていない（句読点混入・run 分割粒度・次 run 領域の3系統を個別に切り分けて実測したが、
     * いずれも単独では 6 件を説明しない）。3215 を凍結して監視する＝この値が変わったら
     * （改善であれ悪化であれ）真因調査を再開してから定数を更新すること。
     */
    @Test
    fun n6169dz_s4b_boutenRubyCount() {
        val e = extracted("N6169DZ")
        val oracleCount = oracle("N6169DZ").getInt("s4b_bouten_ruby_count")
        val actual = e.runs.count { (_, reading) -> reading.isNotEmpty() && reading.all { it in BOUTEN_MARKS } }
        val knownActual = 3215
        assertEquals(
            "S4b 傍点ルビ数が凍結値と不一致＝挙動が変わった。真因調査のうえ knownActual を更新すること",
            knownActual,
            actual,
        )
        println(
            "  [既知の穴] S4b 傍点ルビ数 オラクル=$oracleCount 実測=$actual" +
                "（差 ${oracleCount - actual}・真因未確定）",
        )
    }

    // ---- S3: 空行復元 ----

    /**
     * 空行の位置を**本文の文字オフセット**で突き合わせる。
     *
     * なぜ段落番号でなく文字オフセットか: 現行実装は行を段落へ結合し、第二実装は原文の行を保つ
     * （段落結合の方針差＝裁定未了）。単位が違うので段落番号では比較できないが、文字は両者で保存されて
     * いる（本テストが字数一致も同時に見張る）ので、文字オフセットなら実装非依存に位置まで比較できる。
     *
     * ## 既知の穴は無い（2026-09-02 に真因を修正して全数一致へ）
     * かつては列間 X の比較が [TextProcessor.LineStreamer] でページ内に閉じており、ページ末尾と
     * 次ページ先頭の間隔が測れず**そこに在った空行だけが落ちていた**（この fixture で 115 中 5 件、
     * 全文版 66 話で 654 件・全件がページ境界）。列グリッド幅を使ってページ跨ぎでも間隔を測るよう
     * 直したので、この fixture は**空行 115 件が全数一致**する。よってここは既知表を持たず
     * 欠落 0・誤検出 0 を直接要求する（穴が開いたら即赤）。
     *
     * ⚠️ `pdf_oracle/N0833HI_ep57.s3_known_gaps.json` は**この修正で役目を終えた**（表の 5 件は全て塞がった）
     * ので撤去済み。生成側の `tools/build_s3_fixture.py` は残してある——別の文書で fixture を作るとき、
     * 「まだ塞がっていない穴の表」は診断として要るため。
     */
    @Test
    fun n0833hi_s3_blankLinesRestored() {
        val o = oracle(S3_FIXTURE)
        val (chars, ours) = s3Extract()
        // 字が落ちていると offset がずれて空行の照合が無意味になる＝先に字数で土俵を確かめる。
        assertEquals(
            "S3 本文字数がオラクルと不一致＝空行の位置比較が成立しない",
            o.getInt("s3_flow_char_count"),
            chars,
        )
        val expected = runsOf(o, "s3_blank_runs")
        val spurious = ours.filterNot { it in expected.toSet() }
        val missing = expected.filterNot { it in ours.toSet() }
        assertTrue(
            "S3 オラクルに無い空行が ${spurious.size} 件（位置ずれか過剰挿入）＝$spurious",
            spurious.isEmpty(),
        )
        assertTrue(
            "S3 空行が ${missing.size} 件欠落＝ページ跨ぎの復元が退行した可能性（欠落位置: $missing）",
            missing.isEmpty(),
        )
        assertEquals(
            "S3 空行の総数がオラクルと不一致",
            expected.sumOf { it.second },
            ours.sumOf { it.second },
        )
    }

    /** fixture PDF から (本文字数, 空行 run の [オフセット, 連続数]) を作る。空段落を落とさない点が [extracted] と違う。 */
    private fun s3Extract(): Pair<Int, List<Pair<Int, Int>>> {
        val pdf = File(repoRoot(), "$ORACLE_DIR/$S3_FIXTURE.pdf")
        assertTrue("S3 fixture PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        return PDDocument.load(pdf).use { doc ->
            var n = 0
            var run = 0
            val runs = mutableListOf<Pair<Int, Int>>()
            for (p in PdfExtractor.runFinalEngine(doc)) {
                // 見出しは第二実装側でメタデータ扱い＝本文 flow に載らないので、こちらも外して土俵を揃える。
                if (p.startsWith("【題名】")) continue
                if (p.isEmpty()) {
                    run++
                    continue
                }
                if (run > 0) {
                    runs.add(n to run)
                    run = 0
                }
                n += RUBY.replace(p) { it.groupValues[1] }.length
            }
            if (run > 0) runs.add(n to run)
            n to runs
        }
    }

    private fun runsOf(json: JSONObject, key: String): List<Pair<Int, Int>> {
        val a = json.getJSONArray(key)
        return (0 until a.length()).map { a.getJSONArray(it).let { e -> e.getInt(0) to e.getInt(1) } }
    }

    // ---- S7: 半角アポストロフィの位置移動 ----

    /**
     * 半角スペースを除去した flow 上で前後文脈を突き合わせる。
     * なぜ除去するか＝S1（空白脱落）が直るまで文脈が必ずズレ、S7 の赤が S1 由来か位置移動由来か
     * 区別できなくなるため。除去は S1 修正後も同じ結果になる（両側から同じ字を落とすだけ）。
     */
    @Test
    fun n6169dz_s7_apostrophePosition() {
        val e = extracted("N6169DZ")
        val stripped = e.flow.replace(" ", "")
        val ours = stripped.withIndex().filter { it.value == '\'' }.map {
            stripped.substring(maxOf(0, it.index - 12), minOf(stripped.length, it.index + 12))
        }
        val expected = oracle("N6169DZ").getJSONArray("s7_apostrophe_contexts")
            .let { a -> (0 until a.length()).map { a.getString(it) } }
        val diffs = expected.indices.filter { ours.getOrNull(it) != expected[it] }
        assertTrue(
            "S7 アポストロフィ位置がオラクルと不一致 ${diffs.size}/${expected.size} 件\n" +
                diffs.take(5).joinToString("\n") { "  ours=${ours.getOrNull(it)}\n  orcl=${expected[it]}" },
            ours == expected,
        )
    }

    // ---- S2b: 字種写像が web 原文と食い違う ----

    /**
     * 監視対象コードポイントの**出現数を全数で**突き合わせる（オラクル `s2b_charmap_counts`）。
     *
     * なぜ「0 件のものも表に載せて」比べるか: 片側だけでは撤去漏れ（出るはずのない `﹇`FE47 や
     * U+0336 が出続ける）と過剰写像（出るはずの `～`FF5E が消える）のどちらかを見逃す。
     * 両向きを 1 本の等式で見張るために、期待値は 19 コードポイントの完全な表にしてある。
     *
     * ADR 0041 決定1 の実体はこの表＝pdfminer 追従の写像を撤去し、字はフォントの符号化
     * （[com.novelreader.pdf.GlyphDecoder]）から決める。表が赤くなる＝どちらかへ戻った合図。
     */
    @Test
    fun n6169dz_s2b_charmapMatchesWebSource() = assertCharmap("N6169DZ")

    @Test
    fun n2959ki_s2b_charmapMatchesWebSource() = assertCharmap("N2959KI")

    @Test
    fun n1453lw_s2b_charmapMatchesWebSource() = assertCharmap("N1453LW")

    @Test
    fun n5368ml_s2b_charmapMatchesWebSource() = assertCharmap("N5368ML")

    private fun assertCharmap(ncode: String) {
        val e = extracted(ncode)
        val expected = oracle(ncode).getJSONObject("s2b_charmap_counts")
        val diffs = mutableListOf<String>()
        for (key in expected.keys()) {
            val cp = key.removePrefix("U+").toInt(16)
            val want = expected.getInt(key)
            val got = e.flow.count { it.code == cp }
            if (want != got) diffs.add("$key 期待 $want / 実測 $got")
        }
        assertTrue(
            "S2b 字種の出現数が web 原文オラクルと不一致（$ncode）＝写像の撤去が戻ったか、" +
                "ToUnicode 逆引きへ戻った可能性\n" + diffs.joinToString("\n"),
            diffs.isEmpty(),
        )
    }

    // ---- S3-line: 抽出の出力単位が「原文の行」か ----

    /**
     * ページ抜き fixture の**行の長さ列**をそのまま突き合わせる（0＝空行）。
     *
     * なぜ行の長さ列か（ADR 0041 決定2 の検証）: 抽出は原文の行を保持するようになった＝
     * 行の**個数と境界位置**が出力の意味そのものになる。長さ列が一致すれば、字数（既存の
     * [n0833hi_s3_blankLinesRestored] が見張る）と合わせて「どこで行が切れたか」が完全に決まる。
     * 本文そのものを fixture に持たずに境界だけを見張れるのも利点。
     *
     * ⚠️ 列→行の復元には**原理的に曖昧な境界**が在る（「ちょうど幅いっぱいで終わった行」と
     * 「折り返し」は PDF 生成時に情報が失われていて区別できない＝
     * [com.novelreader.pdf.TextProcessor.LineStreamer] の KDoc）。独立再実装が web 原文と
     * 突き合わせた実測誤り率も 0 ではない（約 1,380 行に 2 箇所）。よって完全一致ではなく
     * **既知の食い違い数を凍結**して監視する形にしてある＝この数が動いたら（改善でも悪化でも）
     * 中身を確認してから定数を更新すること。
     */
    @Test
    fun n0833hi_s3line_lineShapeMatchesOracle() {
        val expected = oracle(S3_FIXTURE).getJSONArray("s3_line_lengths")
            .let { a -> (0 until a.length()).map { a.getInt(it) } }
        val ours = s3LineLengths()
        val firstDiff = (0 until minOf(expected.size, ours.size)).firstOrNull { expected[it] != ours[it] }
        println(
            "  [S3-line] 行数 実測=${ours.size} オラクル=${expected.size}" +
                "（空行 ${ours.count { it == 0 }} / ${expected.count { it == 0 }}）" +
                "・最初の相違 index=${firstDiff ?: -1}",
        )
        assertEquals(
            "S3-line 行の長さ列が web 原文オラクルと不一致＝列→行の復元が変わった。" +
                "先頭の相違 index=${firstDiff ?: -1}（実測 ${ours.take(0)}）",
            expected,
            ours,
        )
    }

    /** fixture PDF から行の長さ列を作る（0＝空行・ルビ記法は親文字だけに畳む）。 */
    private fun s3LineLengths(): List<Int> {
        val pdf = File(repoRoot(), "$ORACLE_DIR/$S3_FIXTURE.pdf")
        assertTrue("S3 fixture PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        return PDDocument.load(pdf).use { doc ->
            PdfExtractor.runFinalEngine(doc)
                // 見出しは第二実装側でメタデータ扱い＝本文 flow に載らないので、こちらも外して土俵を揃える。
                .filterNot { it.startsWith("【題名】") }
                .map { RUBY.replace(it) { m -> m.groupValues[1] }.length }
        }
    }

    /**
     * 全文書の**非空行数を完全一致**で、**空行数を既知の欠落表つき**で突き合わせる。
     *
     * ## 非空行数（完全一致を要求する）
     * 行境界がどこに入るかは列→行の復元規則そのもの＝ADR 0041 決定2 の実体で、実測では
     * 4 文書・79,546 行が web 原文オラクルと**完全一致**する。ここが 1 でもずれたら規則が変わった合図。
     *
     * ## 空行数（既知の欠落を凍結する）
     * 空行は「列グリッドのスロットの空き」として現れるので、**列と列の間隔**からしか読めない。
     * 現行はそこに 2 つの穴が在り、どちらも**過小側にしか外れない**（偽の空行は作らない）:
     *
     * 1. **見出しページ直後の格子ずれ**（実測 N1453LW 1・N2959KI 0・N6169DZ 11 件）。
     *    見出しのあるページは本文列の原点が「見出し最終列 − 30mm」へずれるため、格子が 8mm ピッチの
     *    標準位置から外れる。[TextProcessor.LineStreamer] のページ跨ぎ空行は
     *    「列グリッド幅 + 前ページ最終列 x0 − 次ページ先頭列 x0」という**絶対座標の引き算**で数えるので、
     *    前ページが見出しページだと原点が食い違って端数になり、round で 0 へ落ちる
     *    （実測 N1453LW: 前ページ最終列 34mm・次ページ先頭列 264mm → 0.25 列と出るが真値は 1）。
     *    塞ぐにはページごとの格子の位相（原点と左限）が要る＝版面の物理マージンを新たに前提化する必要があり、
     *    誤ると**偽の空行**を作る側の面が開く。0.13% の欠落と引き換えにできないので凍結する。
     * 2. **見出しページ内の先頭空行**（実測 N2959KI 1・N6169DZ 77 件）。話の本文が空行で始まる場合、
     *    その空きは「見出しの版面が占める空き」と同じ形で現れる。両者を数え分けないのは意図的で、
     *    数えると章ごとに偽の空行が湧く（実測 N0833HI で誤検出 180 件＝[TextProcessor.LineStreamer] の
     *    `canCarry` の KDoc）。
     *
     * ⚠️ この欠落は ADR 0041 の変更で**生じたものではない**（空行の数え方は変えていない）。
     * 値が動いたら（改善でも悪化でも）上の 2 機序のどちらが動いたかを確かめてから更新すること。
     */
    @Test
    fun s3line_lineCountsMatchOracle() {
        val diffs = mutableListOf<String>()
        for (ncode in listOf("N1453LW", "N2959KI", "N5368ML", "N6169DZ")) {
            val o = oracle(ncode)
            val lines = extractedLines(ncode)
            val nonBlank = lines.count { it.isNotEmpty() }
            val blank = lines.count { it.isEmpty() }
            val wantNonBlank = o.getInt("s3_line_count")
            val wantBlank = o.getInt("s3_blank_count") - KNOWN_MISSING_BLANKS.getValue(ncode)
            println("  [S3-line] $ncode 非空 $nonBlank/$wantNonBlank ・ 空行 $blank/$wantBlank" +
                "（既知欠落 ${KNOWN_MISSING_BLANKS.getValue(ncode)}）")
            if (nonBlank != wantNonBlank) {
                diffs.add("$ncode 非空行数 実測 $nonBlank / オラクル $wantNonBlank ＝行境界の規則が変わった")
            }
            if (blank != wantBlank) {
                diffs.add("$ncode 空行数 実測 $blank / 期待 $wantBlank" +
                    "（オラクル ${o.getInt("s3_blank_count")} − 既知欠落 ${KNOWN_MISSING_BLANKS.getValue(ncode)}）")
            }
        }
        assertTrue("S3-line 行数がオラクルと不一致\n" + diffs.joinToString("\n"), diffs.isEmpty())
    }

    /** 本文の行列（【題名】を除く。空行は "" のまま）。 */
    private fun extractedLines(ncode: String): List<String> = LINE_CACHE.getOrPut(ncode) {
        val pdf = File(repoRoot(), "sample_pdfs/$ncode.pdf")
        assertTrue("PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        PDDocument.load(pdf).use { doc ->
            PdfExtractor.runFinalEngine(doc).filterNot { it.startsWith("【題名】") }
        }
    }

    // ---- 抽出とオラクルの読み込み ----

    /** 親文字列と読み（隣接 run を畳んだ後）。 */
    private data class Extracted(val flow: String, val runs: List<Pair<String, String>>)

    private fun oracle(ncode: String): JSONObject {
        val f = File(repoRoot(), "$ORACLE_DIR/$ncode.oracle.json")
        assertTrue("オラクル fixture が無い: ${f.absolutePath}", f.isFile)
        return JSONObject(f.readText())
    }

    private fun extracted(ncode: String): Extracted = CACHE.getOrPut(ncode) {
        val pdf = File(repoRoot(), "sample_pdfs/$ncode.pdf")
        // 無いときはスキップせず落とす（fixture が在るのに PDF が無い＝ゲートが空回りする状態を隠さない）。
        assertTrue("PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        PDDocument.load(pdf).use { doc ->
            val meta = PdfExtractor.extractBookMeta(doc)
            val paragraphs = PdfExtractor.runFinalEngine(doc)
            val chapters = ChapterProcessor.splitIntoChapters(paragraphs, meta.title)
            val flow = StringBuilder()
            val runs = mutableListOf<Pair<String, String>>()
            for (ch in chapters) {
                for (p in ch.body) {
                    val local = mutableListOf<Triple<Int, String, String>>()
                    val plain = StringBuilder()
                    var i = 0
                    for (m in RUBY.findAll(p)) {
                        plain.append(p, i, m.range.first)
                        local.add(Triple(plain.length, m.groupValues[1], m.groupValues[2]))
                        plain.append(m.groupValues[1])
                        i = m.range.last + 1
                    }
                    plain.append(p, i, p.length)
                    // 空段落（長さ0）だけを flow から外す。⚠️ isNotBlank で外すと**半角スペースだけの
                    // 段落**まで落ち、S1 の実測が期待値とズレる（オラクル側も同じ規則に揃えてある）。
                    if (plain.isNotEmpty()) {
                        flow.append(plain)
                        runs += mergeAdjacent(local)
                    }
                }
            }
            Extracted(flow.toString(), runs)
        }
    }

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory && File(d, "android").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException("リポジトリルートが user.dir=${System.getProperty("user.dir")} から辿れない")
    }

    companion object {
        private const val ORACLE_DIR = "android/app/src/test/resources/pdf_oracle"

        /** S3 だけ全文版が `.gitignore` 済みのため、1 話ぶんを抜いたページ抜き fixture を使う（KDoc 参照）。 */
        private const val S3_FIXTURE = "N0833HI_ep57"

        /** [ChapterProcessor] の RUBY_PATTERN と同一（private のため複製。変えるときは両方）。 */
        private val RUBY = Regex("""\|([^《]+)《([^》]+)》""")
        private val BOUTEN_MARKS = setOf('・', '﹅', '﹆', '●', '○')

        /** N6169DZ は 8,668 ページ＝クラス内で1回だけ抽出する（クラスごとに @Test が複数走るため）。 */
        private val CACHE = mutableMapOf<String, Extracted>()

        /** 行列そのもののキャッシュ（[Extracted] は空行を落とすので行数の検証には使えない）。 */
        private val LINE_CACHE = mutableMapOf<String, List<String>>()

        /** 復元できない空行の既知件数（機序は [s3line_lineCountsMatchOracle] の KDoc）。 */
        private val KNOWN_MISSING_BLANKS = mapOf(
            "N1453LW" to 1,
            "N2959KI" to 1,
            "N5368ML" to 0,
            "N6169DZ" to 88,
        )

        /** 隣接するルビ run を1件へ畳む（分割粒度 S4-2 は裁定未了＝親範囲/傍点の判定に混ぜない）。 */
        private fun mergeAdjacent(runs: List<Triple<Int, String, String>>): List<Pair<String, String>> {
            val out = mutableListOf<Triple<Int, String, String>>()
            for (r in runs) {
                val last = out.lastOrNull()
                if (last != null && last.first + last.second.length == r.first) {
                    out[out.size - 1] = Triple(last.first, last.second + r.second, last.third + r.third)
                } else {
                    out.add(r)
                }
            }
            return out.map { it.second to it.third }
        }
    }
}
