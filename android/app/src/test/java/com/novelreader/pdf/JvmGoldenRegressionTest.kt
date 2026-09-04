package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * 恒久回帰ゲート: JVM 単体テスト（Robolectric）で pdfbox-android が実PDF を抽出し、
 * ParserRules / DetectedRules リファクタが golden 4本の抽出結果を保存しているかを testDebugUnitTest で守る。
 *
 * [PDFBoxResourceLoader.init] を Robolectric の Context で効かせ、AAR 同梱の CMap/glyphlist を
 * ロードして CID→Unicode グリフ解決を実機と一致させる（これにより実機 androidTest を待たず本文抽出の
 * 回帰を testDebugUnitTest 内で検出できる）。実機 [PdfExtractorDeviceSpikeTest] と合格ラインを揃える。
 *
 * 合格ライン（短編・中編・単話の3本は body_sha256 完全一致・長編 N6169DZ のみ許容帯）。
 * PDF と golden はどちらも git 追跡下（sample_pdfs 配下の .pdf と ab-review/golden_regression 配下の .pdf.json）。
 * gradle の cwd がモジュールでもルートでも解決できるよう user.dir から両ディレクトリを持つ祖先を遡って探す
 * （無ければ assert 前に fail させ「無い」ことを明示する＝スキップしない）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JvmGoldenRegressionTest {

    @Before
    fun initResourceLoader() {
        // 本番 NovelReaderApplication.onCreate と同じく PDDocument.load の前に一度だけ init
        // （AAR 同梱 CMap/glyphlist のロード。これを欠くと CID→Unicode 解決がズレて sha256 が狂う）。
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun n1453lw_shortWork_bodyExactMatch() = runOne(Fixture("N1453LW", exactBody = true))

    @Test
    fun n2959ki_mediumWork_bodyExactMatch() = runOne(Fixture("N2959KI", exactBody = true))

    @Test
    fun n6169dz_longWork_toleranceBand() = runOne(Fixture("N6169DZ", exactBody = false))

    // 単話（【題名】マーカー皆無）＝章見出し構造が無い作品。単一章タイトルに表紙由来の作品タイトルが
    // 流用され「作品情報・プロローグ」の嘘見出しが目次に出ないことを body 完全一致で守る。
    @Test
    fun n5368ml_singleChapterWork_bodyExactMatch() = runOne(Fixture("N5368ML", exactBody = true))

    // 単話＋前書き/後書き＝**第4本 N5368ML が覆えていなかった型**（そちらは前書きも後書きも無い単話）。
    // なろうの生成器は単話でも前書き/後書きブロックに Bold 見出しを打ち、話タイトルが空なので
    // 裸の「（前書き）」「（後書き）」だけが出る。この2件を章見出しと数えて単話判定が外れると、
    // 後書き付きは全文が嘘見出し「作品情報・プロローグ」章に入り、前書き付きは畳み込み先の通常章が
    // 現れず**章数 0＝本文全損**になる（2026-09-04 の実機症状と、その調査で判明した併発）。
    // この標本の穴そのものが当時の真因なので、第5本として corpus へ入れて機械で塞ぐ。
    @Test
    fun n7668gf_singleChapterWithForewordAfterword_bodyExactMatch() =
        runOne(Fixture("N7668GF", exactBody = true))

    // 前付けが2ページしかない極小作品（総4ページ＝表紙／注意事項／本文／クレジット）。
    // 何を守るか＝**この修正が無ければ本文が1文字も残らない型**。旧実装は前付けを定数3ページで
    // 切っていたため、処理対象ページが1枚も残らず段落0件になっていた（無作為標本42本中6本＝14.3%）。
    // 見出しを持たないページ2 を本文と確定できるのは「捨てると本文ページが0枚になる＝作品に本文が
    // 無いことはありえない」という構造の argument で、その枝をここで実 PDF ごと固定する。
    @Test
    fun n5892fb_minimalFrontMatterWork_bodyExactMatch() =
        runOne(Fixture("N5892FB", exactBody = true))

    private data class Fixture(val name: String, val exactBody: Boolean)

    private data class Snapshot(
        val title: String,
        val author: String,
        val paragraphCount: Int,
        val blankParagraphCount: Int,
        val chapterCount: Int,
        val chapterTitles: List<String>,
        val totalChars: Int,
        val rubyRunCount: Int,
        val bodySha256: String,
        val headParagraphs: List<String>,
        /**
         * **章本文**に残った挿絵記法（なろう／みてみん `＜iコード｜ユーザID＞`）の件数＝常に 0 が契約。
         *
         * なぜ golden の項目でなく不変条件として持つか: golden の body_sha256 が測っているのは
         * **抽出出力**（[PdfExtractor.runFinalEngine] の段落列）で、記法は抽出段では保持される側
         * （ルビ記法と同じ＝解釈は [ChapterProcessor] の層）。∴ 記法の除去は body_sha256 を1ビットも
         * 動かさず、golden だけでは実機に出る症状（本文冒頭に記号列が見える）を検出できない。
         * ここが章本文まで見る唯一のゲートなので、期待値ゼロの不変条件として据える。
         * 実測の分布＝N5892FB 1 件・N6169DZ 126 件（単独行 118／行末インライン 8）・他 4 本 0 件。
         */
        val illustrationTagsInChapters: Int,
    )

    private fun runOne(fx: Fixture) {
        val repoRoot = resolveRepoRoot()
        val pdf = File(repoRoot, "sample_pdfs/${fx.name}.pdf")
        val goldenFile = File(repoRoot, "ab-review/golden_regression/${fx.name}.pdf.json")
        assertTrue("PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        assertTrue("golden が無い: ${goldenFile.absolutePath}", goldenFile.isFile)

        val golden = JSONObject(goldenFile.readText())
        val report = StringBuilder("\n===== JVMスパイク ${fx.name} (${if (fx.exactBody) "body完全一致要求" else "許容帯"}) =====\n")

        val snap = buildSnapshot(pdf)
        if (System.getenv("UPDATE_GOLDEN") == "1") {
            writeGolden(goldenFile, snap, golden)
            println("  [UPDATE_GOLDEN] 採り直した: ${goldenFile.absolutePath}")
            return
        }
        val gateOk = compareToGolden(snap, fx, golden, report)
        report.append("  → 判定: ${mark(gateOk)}\n")

        // 結果は必ず stdout へ（pass でも残差を見たい）。
        println(report.toString())
        assertTrue("JVMスパイク ${fx.name} ゲート失敗:\n$report", gateOk)
    }

    /**
     * golden を現在の出力で採り直す（`UPDATE_GOLDEN=1` のときだけ走る）。
     *
     * なぜテストの中に置くか: 旧ハーネス `ab-review/golden_regression.py` は撤去済みの Python エンジンを
     * import しており**復旧不能**で、採り直しの手順が「誰かが手で書く」しか無い状態だった
     * （`docs/knowledge/golden-regression-baselines.md`）。期待値を作る経路と突き合わせる経路が
     * 同じ [buildSnapshot] であることが、この形の要点＝「golden だけ別の作り方をしていて、
     * そもそも比較になっていない」という壊れ方をしない。
     *
     * ⚠️ **採り直しは仕様変更を追認する操作**なので、必ず先に赤い差分レポートを読んで
     * 「変わってよい理由」を持ってから走らせること。elapsed_sec は計測値でゲート対象外のため引き継ぐ。
     */
    private fun writeGolden(file: File, snap: Snapshot, previous: JSONObject) {
        val o = JSONObject()
        o.put("title", snap.title)
        o.put("author", snap.author)
        o.put("paragraph_count", snap.paragraphCount)
        o.put("blank_paragraph_count", snap.blankParagraphCount)
        o.put("chapter_count", snap.chapterCount)
        o.put("chapter_titles", org.json.JSONArray(snap.chapterTitles))
        o.put("total_chars", snap.totalChars)
        o.put("ruby_run_count", snap.rubyRunCount)
        o.put("body_sha256", snap.bodySha256)
        o.put("elapsed_sec", previous.optDouble("elapsed_sec", 0.0))
        o.put("head_paragraphs", org.json.JSONArray(snap.headParagraphs))
        file.writeText(o.toString(1) + "\n")
    }

    /** DeviceSpikeTest.buildSnapshot と同一手順（同一 PDDocument へメタ→本文の順で 2 回ストリップ）。 */
    private fun buildSnapshot(pdfFile: File): Snapshot {
        PDDocument.load(pdfFile).use { doc ->
            val meta = PdfExtractor.extractBookMeta(doc)
            // 本番 PdfBookExtractor と同経路にする＝meta.title を fallback として渡し、
            // 版面由来のブロック開始も同じように受け渡す（単話の前書き終端の判定に使われる）。
            // ここを本番と揃えておかないと、golden は「本番が通らない経路」を守ることになる。
            val blockStarts = mutableSetOf<Int>()
            val paragraphs = PdfExtractor.runFinalEngine(doc, null, blockStarts)
            val chapters = ChapterProcessor.processForewordAfterword(
                ChapterProcessor.splitIntoChapters(paragraphs, meta.title, blockStarts)
            )
            val bodyText = paragraphs.joinToString("\n")
            return Snapshot(
                title = meta.title,
                author = meta.author,
                paragraphCount = paragraphs.size,
                blankParagraphCount = paragraphs.count { it == "" },
                chapterCount = chapters.size,
                chapterTitles = chapters.map { it.title },
                totalChars = bodyText.codePointCount(0, bodyText.length),
                rubyRunCount = paragraphs.sumOf { p -> p.count { it == '《' } },
                bodySha256 = sha256Hex(bodyText),
                headParagraphs = paragraphs.take(3),
                illustrationTagsInChapters = chapters.sumOf { c -> ILLUSTRATION_TAG_PROBE.findAll(c.body).count() },
            )
        }
    }

    private fun compareToGolden(snap: Snapshot, fx: Fixture, g: JSONObject, report: StringBuilder): Boolean {
        val titleOk = snap.title == g.getString("title")
        val authorOk = snap.author == g.getString("author")
        report.append("  title : ${mark(titleOk)} now=${snap.title.q()} golden=${g.getString("title").q()}\n")
        report.append("  author: ${mark(authorOk)} now=${snap.author.q()} golden=${g.getString("author").q()}\n")
        val chapterCountOk = exactLine(report, "chapter_count", snap.chapterCount, g.getInt("chapter_count"))

        val gTitles = (0 until g.getJSONArray("chapter_titles").length())
            .map { g.getJSONArray("chapter_titles").getString(it) }
        val titleDiffs = chapterTitleDiffCount(snap.chapterTitles, gTitles)
        val titlesOk = if (fx.exactBody) titleDiffs == 0 else titleDiffs <= TOL_CHAPTER_TITLE_DIFFS
        val titlePolicy = if (fx.exactBody) "完全一致" else "±${TOL_CHAPTER_TITLE_DIFFS}件"
        report.append("  chapter_titles【$titlePolicy】: ${mark(titlesOk)} 不一致 $titleDiffs 件 (${snap.chapterTitles.size} 章)\n")
        if (titleDiffs > 0) {
            val d = firstDivergence(snap.chapterTitles, gTitles) ?: 0
            report.append("    最初の差[章 $d] now=${snap.chapterTitles.getOrNull(d).q()} golden=${gTitles.getOrNull(d).q()}\n")
        }

        val gBody = g.getString("body_sha256")
        val bodyOk: Boolean
        if (fx.exactBody) {
            bodyOk = snap.bodySha256 == gBody
            report.append("  body_sha256【完全一致要求】: ${mark(bodyOk)} now=${snap.bodySha256.take(12)}… golden=${gBody.take(12)}…\n")
            refLine(report, "total_chars", snap.totalChars, g.getInt("total_chars"))
            refLine(report, "ruby_run_count", snap.rubyRunCount, g.getInt("ruby_run_count"))
            refLine(report, "paragraph_count", snap.paragraphCount, g.getInt("paragraph_count"))
        } else {
            report.append("  body_sha256（許容帯モード＝gate非対象）: now=${snap.bodySha256.take(12)}… golden=${gBody.take(12)}…\n")
            val cOk = tolPct(report, "total_chars", snap.totalChars, g.getInt("total_chars"), TOL_CHARS_PCT)
            val rOk = tolPct(report, "ruby_run_count", snap.rubyRunCount, g.getInt("ruby_run_count"), TOL_RUBY_PCT)
            val pOk = tolAbs(report, "paragraph_count", snap.paragraphCount, g.getInt("paragraph_count"), TOL_PARA_ABS)
            val bOk = tolAbs(report, "blank_paragraph_count", snap.blankParagraphCount, g.getInt("blank_paragraph_count"), TOL_BLANK_ABS)
            bodyOk = cOk && rOk && pOk && bOk
        }

        val gHead = (0 until g.getJSONArray("head_paragraphs").length())
            .map { g.getJSONArray("head_paragraphs").getString(it) }
        val firstDiff = firstDivergence(snap.headParagraphs, gHead)
        if (firstDiff != null) {
            report.append("  head_paragraphs(診断): ✗ 段落[$firstDiff] で最初の差\n")
            report.append("    now   =${snap.headParagraphs.getOrNull(firstDiff)?.take(80).q()}\n")
            report.append("    golden =${gHead.getOrNull(firstDiff)?.take(80).q()}\n")
        } else {
            report.append("  head_paragraphs(診断): ✓ (先頭${gHead.size}段落一致)\n")
        }

        // 期待値ゼロの不変条件（golden の項目ではない＝採り直しで追認できない）。理由は Snapshot の KDoc。
        val illustOk = snap.illustrationTagsInChapters == 0
        report.append("  挿絵記法の残留【常に0】: ${mark(illustOk)} now=${snap.illustrationTagsInChapters}\n")

        return titleOk && authorOk && chapterCountOk && titlesOk && bodyOk && illustOk
    }

    /**
     * 挿絵記法の検出子。**本番 [ChapterProcessor] の正規表現とは独立に書く**（同じ式を共有すると、
     * 本番側を緩めたときにゲートも同時に緩んで気づけない）。形の根拠はなろうヘルプ helppageid/44。
     * 検査対象が HTML 化済みの章本文なので、半角形が漏れたときの `&lt;`/`&gt;` 形も拾う。
     */
    private val ILLUSTRATION_TAG_PROBE =
        Regex("""(?:[＜<]|&lt;)[ｉi][0-9０-９]+[｜|][0-9０-９]+(?:[＞>]|&gt;)""")

    /** user.dir から sample_pdfs/ を持つ祖先ディレクトリを探す（gradle の cwd がモジュールでも root でも解決）。 */
    private fun resolveRepoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory && File(d, "ab-review").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException("sample_pdfs/ を含むリポジトリルートが user.dir=${System.getProperty("user.dir")} から見つからない")
    }

    private fun exactLine(report: StringBuilder, label: String, now: Int, golden: Int): Boolean {
        val ok = now == golden
        report.append("  $label【完全一致】: ${mark(ok)} now=$now golden=$golden (${(now - golden).signed()})\n")
        return ok
    }

    private fun tolPct(report: StringBuilder, label: String, now: Int, golden: Int, tol: Double): Boolean {
        val deltaPct = if (golden != 0) (now - golden) * 100.0 / golden else 0.0
        val ok = kotlin.math.abs(deltaPct) <= tol
        report.append("  $label【±$tol%】: ${mark(ok)} now=$now golden=$golden (${"%+.4f".format(deltaPct)}%)\n")
        return ok
    }

    private fun tolAbs(report: StringBuilder, label: String, now: Int, golden: Int, tol: Int): Boolean {
        val delta = now - golden
        val ok = kotlin.math.abs(delta) <= tol
        report.append("  $label【±$tol】: ${mark(ok)} now=$now golden=$golden (${delta.signed()})\n")
        return ok
    }

    private fun refLine(report: StringBuilder, label: String, now: Int, golden: Int) {
        report.append("  $label(参考): now=$now golden=$golden (${(now - golden).signed()})\n")
    }

    private fun Int.signed(): String = if (this >= 0) "+$this" else "$this"

    private fun firstDivergence(a: List<String>, b: List<String>): Int? {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) if (a.getOrNull(i) != b.getOrNull(i)) return i
        return null
    }

    private fun chapterTitleDiffCount(a: List<String>, b: List<String>): Int {
        val n = maxOf(a.size, b.size)
        var c = 0
        for (i in 0 until n) if (a.getOrNull(i) != b.getOrNull(i)) c++
        return c
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    private fun String?.q(): String = if (this == null) "<null>" else "\"$this\""

    companion object {
        private const val TOL_CHARS_PCT = 0.05
        private const val TOL_RUBY_PCT = 1.5
        private const val TOL_PARA_ABS = 8
        private const val TOL_BLANK_ABS = 8
        private const val TOL_CHAPTER_TITLE_DIFFS = 15
    }
}
