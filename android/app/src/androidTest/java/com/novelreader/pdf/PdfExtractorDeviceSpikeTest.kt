package com.novelreader.pdf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * PDF 抽出の精度回帰ゲート（実機・androidTest）。2026-07-16 以降、常時実行の正本ゲートは JVM 側
 * [JvmGoldenRegressionTest]（testDebugUnitTest 同乗・同一合格ライン）＝本テストは assets 手動配置時のみの
 * 実機二重化（OEM 固有挙動の最終確認用）。
 *
 * 沿革: 元は穴3（移植の最大リスク）の実機スパイク＝pdfbox-android の [PDFBoxResourceLoader.init] が実機で効き
 * ToUnicode CMap を持たない CID フォントのグリフ解決（CID→Unicode）が pdfminer 版ゴールデンと同一 Unicode を
 * 返すかを実測する診断だった（穴3 は Task 2 で KILL 済＝task_diary #35）。**Phase 4 でこれを恒久の pass/fail
 * 精度回帰ゲートへ昇格**した（クラス名は task_diary #35 / STATUS Task 2 の歴史参照のため据え置き）。
 *
 * ゴールデン: ab-review/golden_regression 配下の各 .pdf.json（Python(pdfminer) エンジンのスナップショット＝安定
 * 参照。ネイティブが正規化して合わせる側なので再生成不要）と、実PDF sample_pdfs 配下の各 .pdf を
 * androidTest/assets/spike/ へローカル配置して使う（9MB 重複を避けるため assets/spike は gitignore 済み。
 * CI 非対象＝資産があれば判定・無ければ Assume スキップ。golden_regression.py の build_snapshot と同一指標で突合）。
 *
 * 合格ライン（Phase 4・ユーザー承認 2026-07-05）:
 *   ① 全PDF: title / author / chapter_count を**完全一致**で要求（構造カウントは決定的）。
 *      chapter_titles(順序込) は短中編は完全一致、長編は ≤15 件の不一致まで許容（③ 参照）。
 *   ② 短中編(N1453LW/N2959KI・ルビ無し): body_sha256 **完全一致**を要求（実機で完全一致を実証済）。
 *   ③ 長編(N6169DZ): pdfminer が吸収していた抽出エッジのため body_sha256 は非対象とし、各指標を**許容帯**で判定
 *      （厳しめ: total_chars ±0.05% / ruby_run_count ±1.5% / paragraph ±8 / blank ±8。実測ドリフト
 *       char+0.012%/ruby+0.97%/para+5 の上に僅かな余裕＝回帰は捕捉しつつ既知エッジは通す）。章題テキストも
 *       本文と同じ CID→Unicode グリフ写像差（ダッシュ FF0D→2212・矢印 2191/2193→2190/2192・アポストロフィ
 *       座標順＝実測11件・task_diary #35）の影響を受けるため ≤15 件許容。
 *   head_paragraphs は診断のみ（gate 判定には使わない）。
 *
 * 注意: N6169DZ(350万字) は約2分かかり、素の androidTest は前景サービス保護が無いため ColorOS の fg_cpu kill
 * リスクがある（task_diary #37）。ただし本ゲートは HTML 書き出しをせず抽出＋指標算出のみで軽量なため Task 2 では
 * 完走した。kill された場合は端末を前面・充電状態にして再実行するか、長編のみ実書FGSフローで別途検証する。
 */
@RunWith(AndroidJUnit4::class)
class PdfExtractorDeviceSpikeTest {

    @Before
    fun initResourceLoader() {
        // task_diary #31: PDDocument.load の前に一度だけ必要。実機 Context を instrumentation から取得。
        // AAR 同梱の Adobe glyphlist/CMap 資産をここでロードする（これが穴3の検証対象そのもの）。
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(ctx)
    }

    /** golden_regression.build_snapshot と同一フィールドの実機抽出結果。 */
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
    )

    @Test
    fun pdfExtraction_matchesGolden_regressionGate() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // アセットは androidTest APK（instrumentation）側 Context に入る。targetContext ではない点に注意。
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        // 実PDF資産（gitignore・bring-your-own）が無ければ穏当にスキップする。
        // 正本は sample_pdfs/ と ab-review/golden_regression/ で、CI 等には持ち込まない分離方針（golden_regression.py と同じ）。
        Assume.assumeTrue(
            "androidTest/assets/spike/ に実PDFが無いためスキップ（配置は sample_pdfs/ と ab-review/golden_regression/ から）",
            testAssets.list("spike")?.isNotEmpty() == true,
        )

        val report = StringBuilder("\n===== PDF抽出 精度回帰ゲート（PDFBox-android vs pdfminer golden）=====\n")
        var allGateOk = true

        for (fx in FIXTURES) {
            val name = fx.name
            report.append("\n■ $name (${if (fx.exactBody) "body完全一致要求" else "許容帯"})\n")

            // アセット PDF を一時ファイルへ展開（PDDocument.load(File) に渡すため）。
            val tmp = File.createTempFile(name, ".pdf", ctx.cacheDir)
            testAssets.open("spike/$name").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            val golden = JSONObject(
                testAssets.open("spike/$name.json").bufferedReader().use { it.readText() }
            )

            val snap = try {
                buildSnapshot(tmp)
            } finally {
                tmp.delete()
            }

            val gateOk = compareToGolden(snap, fx, golden, report)
            report.append("  → 判定: ${mark(gateOk)}\n")
            if (!gateOk) allGateOk = false
        }

        // 結果の回収を三重化する（pass でも残差の推移を見たいため）：
        //  (1) アプリ内部ストレージへファイル出力 → `adb exec-out run-as com.novelreader cat files/…` で確実に取れる
        //      （ColorOS の scoped storage でも debuggable アプリなら run-as で読める）。
        //  (2) logcat（TAG=PdfAccuracyGate）。長文は 1 メッセージが切られうるので PDF 単位で分割出力する。
        //  (3) assert 失敗時は失敗メッセージにも全文。
        val text = report.toString()
        File(ctx.filesDir, "pdf_accuracy_gate_report.txt").writeText(text)
        text.split("\n■ ").forEachIndexed { i, chunk ->
            Log.i(TAG, if (i == 0) chunk else "■ $chunk")
        }
        assertTrue(
            "PDF抽出 精度回帰ゲート失敗（合格ライン未達の PDF がある）。合格ライン=title/author/章数/章題は完全一致・" +
                "短中編は body_sha256 完全一致・長編は許容帯。詳細:\n$report",
            allGateOk,
        )
    }

    /**
     * 開いた PDF から golden_regression.build_snapshot と同一手順で指標を作る。
     * production の PdfBoxEngine.open と同じく、同一 PDDocument に対しメタ抽出→本文抽出の順で 2 回ストリップする。
     */
    private fun buildSnapshot(pdfFile: File): Snapshot {
        PDDocument.load(pdfFile).use { doc ->
            val meta = PdfExtractor.extractBookMeta(doc)
            val paragraphs = PdfExtractor.runFinalEngine(doc)
            // 本番 PdfBookExtractor と同経路にするため meta.title を fallback として渡す
            // （題名マーカー有りの既存検体は分岐に入らず出力不変。単話のみ作品タイトルが単一章名になる）。
            val chapters = ChapterProcessor.processForewordAfterword(
                ChapterProcessor.splitIntoChapters(paragraphs, meta.title)
            )

            val bodyText = paragraphs.joinToString("\n")
            return Snapshot(
                title = meta.title,
                author = meta.author,
                paragraphCount = paragraphs.size,
                blankParagraphCount = paragraphs.count { it == "" },
                chapterCount = chapters.size,
                chapterTitles = chapters.map { it.title },
                // Python len(str) はコードポイント数。Kotlin の length は UTF-16 単位のため
                // codePointCount で厳密に合わせる（サロゲート対の混入時に差が出ないように）。
                totalChars = bodyText.codePointCount(0, bodyText.length),
                rubyRunCount = paragraphs.sumOf { p -> p.count { it == '《' } },
                bodySha256 = sha256Hex(bodyText),
                headParagraphs = paragraphs.take(3),
            )
        }
    }

    /**
     * ゴールデンと突き合わせ、合格ライン（クラス KDoc 参照）で pass/fail を判定する。差分は report へ追記。
     * 戻り値＝この PDF がゲートを通過したか。
     */
    private fun compareToGolden(snap: Snapshot, fx: Fixture, g: JSONObject, report: StringBuilder): Boolean {
        // ① 構造は決定的＝完全一致で要求（title / author / chapter_count / chapter_titles 順序込）。
        val titleOk = snap.title == g.getString("title")
        val authorOk = snap.author == g.getString("author")
        report.append("  title : ${mark(titleOk)} now=${snap.title.q()} golden=${g.getString("title").q()}\n")
        report.append("  author: ${mark(authorOk)} now=${snap.author.q()} golden=${g.getString("author").q()}\n")
        val chapterCountOk = exactLine(report, "chapter_count", snap.chapterCount, g.getInt("chapter_count"))

        // 章題テキスト: 短中編は完全一致、長編(許容帯)は不一致 ≤TOL_CHAPTER_TITLE_DIFFS 件まで許容
        // （本文と同じグリフエッジの影響を受けるため。章数(count)は上で完全一致を要求済み）。
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

        // ② 本文の忠実度。短中編は body_sha256 完全一致、長編は各指標を許容帯で判定（body_sha256 は非対象）。
        val gBody = g.getString("body_sha256")
        val bodyOk: Boolean
        if (fx.exactBody) {
            bodyOk = snap.bodySha256 == gBody
            report.append("  body_sha256【完全一致要求】: ${mark(bodyOk)} now=${snap.bodySha256.take(12)}… golden=${gBody.take(12)}…\n")
            // 参考（完全一致なら当然一致・回帰の目視用に残す）。
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

        // 先頭段落は診断のみ（gate 判定には使わない）。ハッシュ/許容帯不一致時に「どこで最初にズレるか」を見る材料。
        val gHead = (0 until g.getJSONArray("head_paragraphs").length())
            .map { g.getJSONArray("head_paragraphs").getString(it) }
        val firstDiff = firstDivergence(snap.headParagraphs, gHead)
        if (firstDiff != null) {
            report.append("  head_paragraphs(診断): ✗ 段落[$firstDiff] で最初の差\n")
            report.append("    now   =${snap.headParagraphs.getOrNull(firstDiff)?.take(60).q()}\n")
            report.append("    golden =${gHead.getOrNull(firstDiff)?.take(60).q()}\n")
        } else {
            report.append("  head_paragraphs(診断): ✓ (先頭${gHead.size}段落一致)\n")
        }

        return titleOk && authorOk && chapterCountOk && titlesOk && bodyOk
    }

    /** 完全一致を要求する指標。report へ追記し pass/fail を返す。 */
    private fun exactLine(report: StringBuilder, label: String, now: Int, golden: Int): Boolean {
        val ok = now == golden
        report.append("  $label【完全一致】: ${mark(ok)} now=$now golden=$golden (${(now - golden).signed()})\n")
        return ok
    }

    /** 許容帯(%)で判定する指標。|Δ%| <= tolPct なら pass。 */
    private fun tolPct(report: StringBuilder, label: String, now: Int, golden: Int, tolPct: Double): Boolean {
        val deltaPct = if (golden != 0) (now - golden) * 100.0 / golden else 0.0
        val ok = kotlin.math.abs(deltaPct) <= tolPct
        // Δ% だけを個別に format する（report 文字列には ± や 【】等の % 以外の文字＋リテラル % が混じるため、
        // 文字列全体を .format すると % を書式指定子と誤読して UnknownFormatConversionException になる）。
        val deltaStr = "%+.4f".format(deltaPct)
        report.append("  $label【±$tolPct%】: ${mark(ok)} now=$now golden=$golden ($deltaStr%)\n")
        return ok
    }

    /** 許容帯(絶対値)で判定する指標。|now-golden| <= tolAbs なら pass。 */
    private fun tolAbs(report: StringBuilder, label: String, now: Int, golden: Int, tolAbs: Int): Boolean {
        val delta = now - golden
        val ok = kotlin.math.abs(delta) <= tolAbs
        report.append("  $label【±$tolAbs】: ${mark(ok)} now=$now golden=$golden (${delta.signed()})\n")
        return ok
    }

    /** 参考表示のみ（gate 判定に使わない）。 */
    private fun refLine(report: StringBuilder, label: String, now: Int, golden: Int) {
        report.append("  $label(参考): now=$now golden=$golden (${(now - golden).signed()})\n")
    }

    private fun Int.signed(): String = if (this >= 0) "+$this" else "$this"

    /** 2 リストで最初に食い違う index。全一致なら null。 */
    private fun firstDivergence(a: List<String>, b: List<String>): Int? {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            if (a.getOrNull(i) != b.getOrNull(i)) return i
        }
        return null
    }

    /** 2 リストで位置ごとに食い違う件数（長さ差も差分として数える）。章題の許容帯判定に使う。 */
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

    // null 安全な簡易クオート（ログ用）。
    private fun String?.q(): String = if (this == null) "<null>" else "\"$this\""

    /** ゲート対象PDFと精度ポリシー。exactBody=true は body_sha256 完全一致を要求（短中編＝ルビ無し・実機で完全一致実証）。
     *  false は長編 N6169DZ＝pdfminer が吸収していた抽出エッジのため body は許容帯で判定（sha256 は非対象）。 */
    private data class Fixture(val name: String, val exactBody: Boolean)

    companion object {
        private const val TAG = "PdfAccuracyGate"
        private val FIXTURES = listOf(
            Fixture("N1453LW.pdf", exactBody = true),
            Fixture("N2959KI.pdf", exactBody = true),
            Fixture("N6169DZ.pdf", exactBody = false),
        )
        // N6169DZ 許容帯（厳しめ・ユーザー承認 2026-07-05）。実測ドリフト char+0.012%/ruby+0.97%/para+5 の上に僅かな余裕。
        private const val TOL_CHARS_PCT = 0.05   // total_chars ±0.05%
        private const val TOL_RUBY_PCT = 1.5     // ruby_run_count ±1.5%
        private const val TOL_PARA_ABS = 8       // paragraph_count ±8
        private const val TOL_BLANK_ABS = 8      // blank_paragraph_count ±8
        // 章題テキストは本文と同じ CID→Unicode グリフ写像差の影響を受ける。N6169DZ の実測は 11 件で、全て
        // グリフ写像差＝①ダッシュ変種 U+FF0D→U+2212 が6件 ②矢印回転 U+2191/2193→U+2190/2192 が3件
        // ③アポストロフィ座標順 `兎'ｓ`↔`'鳥…` が2件（task_diary #35。当初「1件」は旧spikeが最初の差だけ表示した
        // 過少記録）。①②の9件は正規化で golden に寄せられるが Phase 4 は抽出不変方針のため handover backlog へ。
        // 長編のみ ≤15 件の不一致を許容（現11件＋余裕・章題が大きく崩れる回帰は捕捉）。章数(count)は完全一致のまま。
        private const val TOL_CHAPTER_TITLE_DIFFS = 15
    }
}
