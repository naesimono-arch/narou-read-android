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
 * 穴3（移植の最大リスク）実機スパイク。
 *
 * pdfbox-android の [PDFBoxResourceLoader.init] が実機で効き、ToUnicode CMap を持たない CID フォントの
 * グリフ解決（CID→Unicode）が pdfminer 版ゴールデンと同一 Unicode を返すかを、実PDF3件で実測する。
 * これは Chaquopy→Kotlin+PDFBox 移植の最大リスクで、デスクトッププロト(submission-B)では
 * 長編でルビ P/R 約81%・行カバレッジ約93%の残差が観測されていた（task_diary #31）。実機はさらにズレうる。
 *
 * ゴールデン: ab-review/golden_regression 配下の各 .pdf.json（現行 Python エンジンのスナップショット）と、
 * 実PDF sample_pdfs 配下の各 .pdf を androidTest/assets/spike/ へローカル配置して使う
 * （9MB 重複を避けるため assets/spike は gitignore 済み。CI 非対象＝golden_regression.py と同じ「実PDFは分離」方針）。
 * golden_regression.py の build_snapshot と同一指標で突き合わせる。
 *
 * 性質: これは pass/fail ゲートではなく「穴3の残差を1回で可視化する診断」。
 *   - assert するのは最低限の init 疎通ゲート＝**全PDFで title/author が一致**すること
 *     （表紙のグリフ解決が根本的に効かなければここで落ちる＝穴3が KILL 不能と即判る）。
 *   - body_sha256 / 文字数差 / 段落数差 / ルビ数差 は logcat と失敗メッセージへ出し、
 *     人間/Claude が「残差が許容範囲か（穴3 KILL 可）」を判断する材料にする。
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
    fun spike_extractionMatchesPdfminerGolden() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // アセットは androidTest APK（instrumentation）側 Context に入る。targetContext ではない点に注意。
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        // 実PDF資産（gitignore・bring-your-own）が無ければ穏当にスキップする。
        // 正本は sample_pdfs/ と ab-review/golden_regression/ で、CI 等には持ち込まない分離方針（golden_regression.py と同じ）。
        Assume.assumeTrue(
            "androidTest/assets/spike/ に実PDFが無いためスキップ（配置は sample_pdfs/ と ab-review/golden_regression/ から）",
            testAssets.list("spike")?.isNotEmpty() == true,
        )

        val report = StringBuilder("\n===== 穴3 実機スパイク結果（PDFBox-android CID→Unicode 実測）=====\n")
        var allMetaOk = true

        for (name in FIXTURES) {
            report.append("\n■ $name\n")

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

            val metaOk = compareToGolden(snap, golden, report)
            if (!metaOk) allMetaOk = false
        }

        // 結果の回収を三重化する（穴3の残差は pass でも必ず見たいため）：
        //  (1) アプリ内部ストレージへファイル出力 → `adb exec-out run-as com.novelreader cat files/…` で確実に取れる
        //      （ColorOS の scoped storage でも debuggable アプリなら run-as で読める）。
        //  (2) logcat（TAG=SpikeHole3）。長文は 1 メッセージが切られうるので PDF 単位で分割出力する。
        //  (3) assert 失敗時は失敗メッセージにも全文。
        val text = report.toString()
        File(ctx.filesDir, "spike_hole3_report.txt").writeText(text)
        text.split("\n■ ").forEachIndexed { i, chunk ->
            Log.i(TAG, if (i == 0) chunk else "■ $chunk")
        }
        assertTrue(
            "穴3スパイク: title/author が一致しない PDF がある＝PDFBoxResourceLoader.init が" +
                "実機で効いていない（CID→Unicode 解決不能）。詳細:\n$report",
            allMetaOk,
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
            val chapters = ChapterProcessor.processForewordAfterword(
                ChapterProcessor.splitIntoChapters(paragraphs)
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
     * ゴールデンと全指標を突き合わせ、差分を report へ追記する。戻り値＝title/author が両方一致したか（init 疎通ゲート）。
     */
    private fun compareToGolden(snap: Snapshot, g: JSONObject, report: StringBuilder): Boolean {
        val titleOk = snap.title == g.getString("title")
        val authorOk = snap.author == g.getString("author")
        report.append("  title : ${mark(titleOk)} now=${snap.title.q()} golden=${g.getString("title").q()}\n")
        report.append("  author: ${mark(authorOk)} now=${snap.author.q()} golden=${g.getString("author").q()}\n")

        line(report, "paragraph_count", snap.paragraphCount, g.getInt("paragraph_count"))
        line(report, "blank_paragraph_count", snap.blankParagraphCount, g.getInt("blank_paragraph_count"))
        line(report, "chapter_count", snap.chapterCount, g.getInt("chapter_count"))
        line(report, "total_chars", snap.totalChars, g.getInt("total_chars"))
        line(report, "ruby_run_count", snap.rubyRunCount, g.getInt("ruby_run_count"))

        val gBody = g.getString("body_sha256")
        val bodyOk = snap.bodySha256 == gBody
        report.append("  body_sha256: ${mark(bodyOk)} now=${snap.bodySha256.take(12)}… golden=${gBody.take(12)}…\n")

        // 章題（順序込み）の一致。
        val gTitles = (0 until g.getJSONArray("chapter_titles").length())
            .map { g.getJSONArray("chapter_titles").getString(it) }
        report.append("  chapter_titles: ${mark(snap.chapterTitles == gTitles)} (${snap.chapterTitles.size} 章)\n")
        if (snap.chapterTitles != gTitles) {
            report.append("    now   =${snap.chapterTitles}\n")
            report.append("    golden =$gTitles\n")
        }

        // 先頭段落の一致（ハッシュ不一致時に「どこで最初にズレるか」を見る）。
        val gHead = (0 until g.getJSONArray("head_paragraphs").length())
            .map { g.getJSONArray("head_paragraphs").getString(it) }
        val firstDiff = firstDivergence(snap.headParagraphs, gHead)
        if (firstDiff != null) {
            report.append("  head_paragraphs: ✗ 段落[${firstDiff}] で最初の差\n")
            val idx = firstDiff
            report.append("    now   =${snap.headParagraphs.getOrNull(idx)?.take(60).q()}\n")
            report.append("    golden =${gHead.getOrNull(idx)?.take(60).q()}\n")
        } else {
            report.append("  head_paragraphs: ✓ (先頭${gHead.size}段落一致)\n")
        }

        return titleOk && authorOk
    }

    private fun line(report: StringBuilder, label: String, now: Int, golden: Int) {
        val ok = now == golden
        val delta = if (golden != 0) " (%+.1f%%)".format((now - golden) * 100.0 / golden) else ""
        report.append("  $label: ${mark(ok)} now=$now golden=$golden$delta\n")
    }

    /** 2 リストで最初に食い違う index。全一致なら null。 */
    private fun firstDivergence(a: List<String>, b: List<String>): Int? {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            if (a.getOrNull(i) != b.getOrNull(i)) return i
        }
        return null
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    // null 安全な簡易クオート（ログ用）。
    private fun String?.q(): String = if (this == null) "<null>" else "\"$this\""

    companion object {
        private const val TAG = "SpikeHole3"
        private val FIXTURES = listOf("N1453LW.pdf", "N2959KI.pdf", "N6169DZ.pdf")
    }
}
