package com.novelreader.pdf

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * 支配区間 [PdfExtractor.loadPages] を**ページ範囲で分割して並列に走らせた**ときの短縮比と等価性を、
 * **実機で**測るスパイク。ゲートではなく材料出し（採否は監督が決める）。本番コードは一切変更しない。
 *
 * なぜ実機で測り直すか: JVM（`ExtractPhaseProfileTest.profileParallelLoadPages`・8 スレッド）では
 * 長編 K=3 で 1.5x・等価性は全 K で完全一致まで確認済み（`docs/knowledge/pdf-extract-engine-cost-ceiling.md`
 * 「並列化だけが天井の外側にある」）。**未確認は「端末で同じ比が出るか」だけ**＝big.LITTLE の非対称コアと
 * サーマル制御は JVM と別物で、採否ライン **1.3x** を跨ぐかはここでしか分からない。
 *
 * なぜ [PdfExtractorDeviceSpikeTest] に相乗りせず別クラスにしたか: あちらは**恒久の pass/fail 精度回帰ゲート**で、
 * 3 PDF を1回ずつ抽出する。こちらは同一 PDF を10回走らせる**使い捨ての計測**＝走行時間もメモリ形状も
 * 目的も違う。相乗りさせるとゲートの所要が数倍になり、計測都合の失敗がゲートの赤として現れる。
 * 資産の置き場（androidTest/assets/spike）・結果の回収方式（filesDir ファイル＋logcat＋assert メッセージ）は
 * あちらの作法をそのまま踏襲する。
 *
 * ## 測り方（順序効果の打ち消し）
 *
 * 単一と並列は**同一プロセス・同一 fixture・同一ウォームアップ**で測り、順序を回文
 * （単一, K=1, K=2, K=3, K=4, K=4, K=3, K=2, K=1, 単一）に並べる。位置 i と 2n-1-i の和が
 * どの変異でも一定になるので、**サーマルドリフトや残留ウォームアップのような単調な時間傾向が
 * 全変異へ等しく配分される**（＝比から落ちる）。よって採否に使うのは 2 回の**平均**の比であり、
 * 最小値の比ではない（最小は打ち消しを壊す。参考値として併記だけする）。
 *
 * K=1（並列ハーネスをスレッド1本で回す）は**ハーネス自身の取り分**を測る対照群＝PDF の再オープンと
 * 連結のコスト。単一比が 1.0 を大きく割るなら、並列の伸びはその負債を返してから始まることになる。
 *
 * ## メモリ規律（走行結果を持ち越さない＝ここを外すと ColorOS に殺される）
 *
 * 長編 N6169DZ の CharBox 列は **1 セットで端末ヒープの大半**を占める。旧実装は 1 走行ぶんの結果を
 * ループ局所変数（`timed`/`loaded`）に持ったまま次の走行を呼んでいたため、**常に 2 セットが同時に生き**、
 * 天井に貼り付いて GC スラッシュ（約 26 秒の窓のうち約 24 秒が GC）→ ColorOS の
 * `o-kill(109) reason=13 subreason=2109 importance=100` を受けていた（前面のまま system kill。
 * `OutOfMemoryError` は 1 件も出ない＝ART が投げる前に殺される）。実測でも劣化は累進で、
 * 4000 ページ時に 単一 22.8s → K=1 26.1s → K=2 **137.3s** → K=3 kill と進んだ。
 *
 * 対策は [fold] ＝走行結果を**照合に必要な最小形（ページ別文字数＋64bit フィンガープリント）へ畳んでから返す**こと。
 * 畳んだ時点で CharBox 列は到達不能になり、次の走行が確保を始める前に回収できる。
 * 等価性 assert は落とさない（畳んだ値で全走行を照合する）。グリフ数が [STRICT_COMPARE_GLYPH_LIMIT] 以下の
 * 文書だけは従来どおり全フィールド厳密比較のために列を残す（中編で数十 MB＝天井に影響しない）。
 *
 * ## 実行（`/device-verify` の am instrument 直叩き。connectedAndroidTest は禁忌＝蔵書が消える）
 *
 * **順序が肝: `monkey` は `am instrument` の「後」に打つ。** `am instrument` は対象プロセスを
 * 再起動するので、**先に打った前面化は無効化される**（これで中編 1 回目の K=2 が Hans に凍結され 685 秒かかった。
 * task_diary #38 の回避策は「instrumentation が動き出してから前面化する」ことで初めて効く）。
 *
 * ```
 * gw --init-script /home/qingj/ext-build/novel-reader-init.gradle installDebug installDebugAndroidTest
 * adb shell am instrument -w \
 *   -e class 'com.novelreader.pdf.PdfParallelLoadPagesSpikeTest#parallelLoadPages_long_N6169DZ' \
 *   com.novelreader.test/androidx.test.runner.AndroidJUnitRunner &
 * sleep 8 && adb shell monkey -p com.novelreader -c android.intent.category.LAUNCHER 1   # ★ 起動後に前面化
 * wait
 * ```
 *
 * 結果の回収（[PdfExtractorDeviceSpikeTest] と同じ三重化）:
 * 1. `adb exec-out run-as com.novelreader cat files/parallel_loadpages_N6169DZ.txt`（正本）
 * 2. `adb logcat -s PdfParallelSpike:I`（1 行 1 メッセージ＝長文切り詰めを避ける）
 * 3. assert 失敗時は失敗メッセージにも全文
 *
 * レポートは**1 行出るたびにファイルへ書き戻す**。ColorOS は CPU 集中の instrumentation を数分で
 * kill / freeze する（task_diary #37・#38）ため、途中で落ちてもそこまでの行が必ず残るようにしている。
 *
 * 引数（省略可・`-e` で渡す）:
 * - `-e ks 1,3` … 測る並列度。既定 `1,2,3,4`。kill されるなら減らすと走行が短くなる。
 * - `-e maxPages 2000` … 先頭 N ページだけを対象にする（既定 0＝全ページ）。#37 の「PDF を切詰める」対策。
 *   指定時は単一側も endPage=N で揃える＝比は同じ土俵のまま。
 */
@RunWith(AndroidJUnit4::class)
class PdfParallelLoadPagesSpikeTest {

    @Before
    fun initResourceLoader() {
        // PDDocument.load の前に一度だけ必要（task_diary #31）。CMap/glyphlist を本番と揃える。
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    /** 中編 799 ページ（JVM: 単一 632ms・K=3 で 2.2x）。短時間で回るので先にこちらで健全性を見る。 */
    @Test
    fun parallelLoadPages_medium_N2959KI() = runSpike("N2959KI.pdf")

    /** 長編 8,668 ページ（JVM: 単一 5,406ms・K=3 で 1.5x）。**採否ラインの判定はこちらが正本**。 */
    @Test
    fun parallelLoadPages_long_N6169DZ() = runSpike("N6169DZ.pdf")

    private fun runSpike(assetName: String) {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        // アセットは androidTest APK（instrumentation）側 Context に入る。targetContext ではない。
        val testAssets = instr.context.assets
        val fixture = assetName.removeSuffix(".pdf")

        Assume.assumeTrue(
            "androidTest/assets/spike/$assetName が無いためスキップ（配置元＝sample_pdfs/）",
            testAssets.list("spike")?.contains(assetName) == true,
        )

        val args = InstrumentationRegistry.getArguments()
        val ks = (args.getString(ARG_KS) ?: DEFAULT_KS)
            .split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 1 }.distinct()
        check(ks.isNotEmpty()) { "-e $ARG_KS の指定が空（例: -e $ARG_KS 1,3）" }
        val maxPagesArg = args.getString(ARG_MAX_PAGES)?.trim()?.toIntOrNull() ?: 0

        val report = Reporter(File(ctx.filesDir, "parallel_loadpages_$fixture.txt"), TAG)
        val pdf = File.createTempFile(fixture, ".pdf", ctx.cacheDir)
        try {
            testAssets.open("spike/$assetName").use { input ->
                pdf.outputStream().use { input.copyTo(it) }
            }
            measureAll(pdf, fixture, ks, maxPagesArg, report)
        } finally {
            pdf.delete()
        }
    }

    private fun measureAll(pdf: File, fixture: String, ks: List<Int>, maxPagesArg: Int, report: Reporter) {
        val totalPages = PDDocument.load(pdf).use { it.numberOfPages }
        val pages = if (maxPagesArg in 1 until totalPages) maxPagesArg else totalPages

        report.emit("=== ParallelLoadPages(device): $fixture at ${java.util.Date()} ===")
        report.emit("端末: ${Build.MODEL} / ${Build.DEVICE} / SDK ${Build.VERSION.SDK_INT}")
        report.emit("availableProcessors=${Runtime.getRuntime().availableProcessors()}")
        report.emit("ヒープ上限: ${fmt(maxHeapMb())}MB（走行ごとの実測ピークを各行に併記する）")
        report.emit("ページ: 対象 $pages / 全 $totalPages" + (if (pages < totalPages) "（-e $ARG_MAX_PAGES で切詰め）" else ""))
        report.emit("K: ${ks.joinToString(",")}（K=1 は並列ハーネス自身の取り分＝再オープン＋連結の対照群）")

        // ウォームアップ（捨て）。静的キャッシュ（PDFBoxResourceLoader/CMap/glyphlist）と ART の JIT を、
        // 単一経路・並列経路の**両方**で温める。全ページ 1 周を捨てる JVM 版と違い先頭数百ページに切詰めるのは、
        // 実機では 1 周ぶんの熱が後続の測定を汚すため（1 ページあたりの仕事は同一なので JIT はこれで足りる）。
        // 残った単調ドリフトは回文順による打ち消しが受け持つ。
        val warmPages = minOf(pages, WARMUP_PAGES)
        singleLoad(pdf, warmPages)
        parallelLoad(pdf, ks.max(), warmPages)
        report.emit("ウォームアップ完了（単一・並列 K=${ks.max()} を先頭 $warmPages ページで各1回・計測から除外）")

        // 回文順（S,K1,…,Kn,Kn,…,K1,S）＝各変異を前半と後半で1回ずつ。KEY_SINGLE は本番 loadPages。
        val forward = listOf(KEY_SINGLE) + ks
        val order = forward + forward.reversed()

        val times = LinkedHashMap<Int, MutableList<Double>>()
        var reference: RunResult? = null
        var equivalenceOk = true
        var noErrors = true
        var peakOfAll = 0.0
        val t0 = System.nanoTime()

        for ((idx, key) in order.withIndex()) {
            val label = if (key == KEY_SINGLE) "単一(本番loadPages)" else "K=$key"
            // 前走行の CharBox 列は [fold] を抜けた時点で到達不能になっている。次の確保が始まる前に
            // ここで確実に返させる＝天井付近で「確保と回収が競う」形（＝旧実装が殺された形）を作らない。
            // 計測区間の外なので所要時間には入らない。
            System.gc()
            Thread.sleep(GC_SETTLE_MS)
            val usedBefore = usedHeapMb()
            val run = if (key == KEY_SINGLE) singleLoad(pdf, pages) else parallelLoad(pdf, key, pages)
            if (run.error != null) {
                // 並列化の可否そのものが計測対象＝握り潰さず型と内容をそのまま出す（OutOfMemoryError も含む）。
                noErrors = false
                report.emit("[${idx + 1}/${order.size}] $label: ⚠ 失敗 ${run.error::class.java.name}: ${run.error.message}")
                continue
            }
            // 最初の走行（単一）を等価性の基準にする。畳み済みなので基準の保持コストはページ数ぶんの
            // Int/Long 配列だけ（長編 8,668 ページでも 100KB 台）。
            val ref = reference ?: run.also {
                reference = it
                report.emit(
                    "基準: pages=${it.pageCount} glyphs=${it.glyphs} 照合=" +
                        (if (it.strictPages != null) "全フィールド厳密" else "ページ単位フィンガープリント")
                )
            }
            val verdict = verdictAgainst(ref, run)
            if (!verdict.startsWith("完全一致")) equivalenceOk = false

            times.getOrPut(key) { ArrayList(2) }.add(run.ms)
            peakOfAll = maxOf(peakOfAll, run.peakUsedMb)
            report.emit(
                "[${idx + 1}/${order.size}] t+${fmt((System.nanoTime() - t0) / 1_000_000_000.0)}s " +
                    "$label: ${fmt(run.ms)}ms " +
                    "heap ${fmt(usedBefore)}→${fmt(run.peakUsedMb)}MB 等価性: $verdict"
            )
        }

        val singleTimes = times[KEY_SINGLE]
        if (singleTimes.isNullOrEmpty()) {
            report.emit("⚠ 単一基準が取れていない＝比を出せない")
        } else {
            val singleMean = singleTimes.average()
            report.emit("")
            report.emit("--- まとめ（採否に使うのは【平均比】。最小比は打ち消しを壊すので参考のみ）---")
            report.emit("変異 / 前半ms / 後半ms / 平均ms / 平均比 / 最小比")
            for ((key, ts) in times) {
                val label = if (key == KEY_SINGLE) "単一" else "K=$key"
                val mean = ts.average()
                report.emit(
                    "$label / ${ts.joinToString(" / ") { fmt(it) }} / ${fmt(mean)} / " +
                        "${fmt(singleMean / mean)}x / ${fmt(singleMean / ts.min())}x"
                )
            }
            if (singleTimes.size == 2) {
                // 1.0 から離れるほど走行中に機械側が振れている（サーマル・他プロセス）＝比そのものを疑う材料。
                report.emit("単一のドリフト（後半/前半）: ${fmt(singleTimes[1] / singleTimes[0])}x")
            }
            times[3]?.let {
                val ratio = singleMean / it.average()
                report.emit("採否ライン $ADOPTION_THRESHOLD x に対し K=3 平均比 = ${fmt(ratio)}x → ${if (ratio >= ADOPTION_THRESHOLD) "ライン到達" else "ライン未達"}")
            }
        }
        report.emit("等価性: ${if (equivalenceOk) "全走行で基準と一致" else "⚠ 不一致あり（上の各行を参照）"}")
        // 走行間で持ち越しが無ければ、各走行の直前使用量は基底へ戻り、ピークは 1 セットぶんで頭打ちになる。
        // ここが上限に張り付くなら、まだどこかが residents を握っている（判断材料として必ず残す）。
        report.emit("ヒープ: 全走行ピーク ${fmt(peakOfAll)}MB / 上限 ${fmt(maxHeapMb())}MB")

        assertTrue(
            "並列 loadPages スパイクで問題を検出（数値は端末の " +
                "files/parallel_loadpages_$fixture.txt にも残っている）:\n$report",
            equivalenceOk && noErrors,
        )
    }

    // ==========================================================
    // 計測の実体
    // ==========================================================

    /**
     * 1 走行の結果を**照合に必要な最小形へ畳んだもの**（[fold] が作る）。
     *
     * なぜ CharBox 列をそのまま持たないか＝クラス KDoc「メモリ規律」の項。要点は、長編は 1 セットで
     * 端末ヒープの大半を占めるので、走行結果を局所変数に持ったまま次の走行を始めると常に 2 セットが
     * 同時に生き、天井で GC スラッシュ→ ColorOS の system kill に至ること。
     * [counts]/[fingerprint] はページ数ぶんの配列だけ＝長編 8,668 ページでも 100KB 台に収まる。
     */
    private class RunResult(
        val ms: Double,
        val pageCount: Int,
        val glyphs: Long,
        /** ページ別の文字数（食い違いの場所を先に特定するため、指紋とは別に持つ）。 */
        val counts: IntArray,
        /** ページ別の 64bit 指紋（[fingerprintPages]）。 */
        val fingerprint: LongArray,
        /** グリフ数が [STRICT_COMPARE_GLYPH_LIMIT] 以下のときだけ残す全フィールド厳密比較用の実体。 */
        val strictPages: List<List<CharBox>>?,
        /** 走行終了直後（列が全て生きている時点）のヒープ使用量 MB＝実測ピークの近似。 */
        val peakUsedMb: Double,
        val error: Throwable? = null,
    )

    /**
     * 走行結果を [RunResult] へ畳む。**この関数を抜けた時点で CharBox 列は到達不能**になり、
     * 次の走行が確保を始める前に GC が回収できる——保持経路を断つのが本関数の唯一の目的。
     * 畳みは計測停止後に行うので所要時間には入らない。
     */
    private fun fold(ms: Double, peakUsedMb: Double, pages: List<List<CharBox>>): RunResult {
        var glyphs = 0L
        val counts = IntArray(pages.size)
        for (p in pages.indices) {
            counts[p] = pages[p].size
            glyphs += pages[p].size
        }
        return RunResult(
            ms = ms,
            pageCount = pages.size,
            glyphs = glyphs,
            counts = counts,
            fingerprint = fingerprintPages(pages),
            strictPages = if (glyphs <= STRICT_COMPARE_GLYPH_LIMIT) pages else null,
            peakUsedMb = peakUsedMb,
        )
    }

    private fun failedRun(t: Throwable) =
        RunResult(Double.NaN, 0, 0L, IntArray(0), LongArray(0), null, 0.0, t)

    /**
     * 単一スレッド基準。PDF のオープン込みで測る（並列版と同じ土俵にするため）。
     * 全ページ対象なら**本番 [PdfExtractor.loadPages] をそのまま**呼ぶ。ページ切詰め時だけは
     * 本番に上限引数が無いので同じ設定の [GlyphStripper] を直に組む（進捗コールバック以外は同一経路）。
     */
    private fun singleLoad(pdf: File, pages: Int): RunResult {
        val start = System.nanoTime()
        return try {
            var ms = 0.0
            var peak = 0.0
            val loaded = PDDocument.load(pdf).use { doc ->
                val l = if (pages >= doc.numberOfPages) {
                    PdfExtractor.loadPages(doc)
                } else {
                    val stripper = GlyphStripper().apply {
                        sortByPosition = false
                        startPage = 1
                        endPage = pages
                    }
                    stripper.getText(doc)
                    stripper.pages
                }
                ms = elapsedMs(start)
                // PDDocument を閉じる前＝抽出結果とパース済み COS の両方が生きている点＝実測ピークに最も近い。
                peak = usedHeapMb()
                l
            }
            fold(ms, peak, loaded)
        } catch (t: Throwable) {
            failedRun(t)
        }
    }

    /**
     * ページ範囲を K 分割し、各スレッドが**自分の [PDDocument] を開いて**担当範囲だけ収集 → ページ順に連結。
     * 時間には PDF の K 回オープンと連結も含める（実装したときに実際に払うコストのため）。
     * 自前 PDDocument が要るのは PDFBox の COSDocument がスレッド安全でないため。
     */
    private fun parallelLoad(pdf: File, k: Int, pages: Int): RunResult {
        val bounds = splitRanges(pages, k)
        val pool = Executors.newFixedThreadPool(k)
        try {
            val start = System.nanoTime()
            val futures = bounds.map { (from, to) ->
                pool.submit(
                    Callable {
                        PDDocument.load(pdf).use { doc ->
                            val stripper = RangeGlyphProbeStripper(from, to)
                            stripper.getText(doc)
                            stripper.pages.toList()
                        }
                    }
                )
            }
            val merged = ArrayList<List<CharBox>>(pages)
            for (f in futures) merged.addAll(f.get())
            val ms = elapsedMs(start)
            val peak = usedHeapMb()
            return fold(ms, peak, merged)
        } catch (t: Throwable) {
            // ExecutionException は原因を剥がして返す（スレッド安全性・メモリ不足の観測が主目的）。
            return failedRun((t as? ExecutionException)?.cause ?: t)
        } finally {
            pool.shutdownNow()
        }
    }

    /** [0, totalPages) を先頭から順に K 個の連続範囲（両端含む・0始まり）へ分ける。 */
    private fun splitRanges(totalPages: Int, k: Int): List<Pair<Int, Int>> {
        val per = totalPages / k
        val rem = totalPages % k
        var from = 0
        return (0 until k).map { i ->
            val size = per + if (i < rem) 1 else 0
            val range = from to (from + size - 1)
            from += size
            range
        }
    }

    // ==========================================================
    // 等価性の照合（JVM 版 ExtractPhaseProfileTest と同一指標）
    // ==========================================================

    /** 全 CharBox を全フィールドで突き合わせ、最初の食い違いを返す（一致なら "完全一致"）。 */
    private fun strictVerdict(expected: List<List<CharBox>>, actual: List<List<CharBox>>): String {
        if (expected.size != actual.size) return "⚠ ページ数不一致 ${expected.size} vs ${actual.size}"
        for (p in expected.indices) {
            val e = expected[p]
            val a = actual[p]
            if (e.size != a.size) return "⚠ page $p の文字数不一致 ${e.size} vs ${a.size}"
            for (i in e.indices) {
                if (e[i] != a[i]) return "⚠ page $p char $i 不一致 ${e[i]} vs ${a[i]}"
            }
        }
        return "完全一致（${expected.size} ページ・全フィールド）"
    }

    /**
     * 基準と当該走行を突き合わせる。両方が実体を残している（＝小さい文書）ときだけ全フィールド厳密、
     * それ以外は畳んだ値どうしの照合。**どちらの経路でも全走行が assert 対象**であることは変わらない。
     */
    private fun verdictAgainst(ref: RunResult, run: RunResult): String {
        val refPages = ref.strictPages
        val runPages = run.strictPages
        return if (refPages != null && runPages != null) strictVerdict(refPages, runPages)
        else fingerprintVerdict(ref, run)
    }

    /** ページ単位フィンガープリントでの照合（大文書用。2 セット同時保持を避けるため）。 */
    private fun fingerprintVerdict(ref: RunResult, run: RunResult): String {
        if (ref.pageCount != run.pageCount) {
            return "⚠ ページ数不一致 ${ref.pageCount} vs ${run.pageCount}"
        }
        for (p in 0 until ref.pageCount) {
            if (ref.counts[p] != run.counts[p]) {
                return "⚠ page $p の文字数不一致 ${ref.counts[p]} vs ${run.counts[p]}"
            }
            if (ref.fingerprint[p] != run.fingerprint[p]) return "⚠ page $p のフィンガープリント不一致"
        }
        return "完全一致（${run.pageCount} ページ・全フィールドを畳んだ 64bit 照合）"
    }

    /**
     * ページごとに text/fontName/size/x0/top/bottom を順序込みで 64bit へ畳む。
     * double は `doubleToRawLongBits` で**ビット等価**を見る（丸めを挟まない＝ズレを見逃さない）。
     */
    private fun fingerprintPages(pages: List<List<CharBox>>): LongArray {
        // List<Long> ではなく LongArray なのは、長編 8,668 ページぶんの Long ボクシングを避けるため
        // （このスパイクの主題がヒープ天井なので、照合側が余分な確保をしない形にしておく）。
        val out = LongArray(pages.size)
        for (p in pages.indices) {
            val page = pages[p]
            var h = 1125899906842597L
            for (c in page) {
                h = 31 * h + c.text.hashCode()
                h = 31 * h + (c.fontName?.hashCode() ?: 0)
                h = 31 * h + java.lang.Double.doubleToRawLongBits(c.size)
                h = 31 * h + java.lang.Double.doubleToRawLongBits(c.x0)
                h = 31 * h + java.lang.Double.doubleToRawLongBits(c.top)
                h = 31 * h + java.lang.Double.doubleToRawLongBits(c.bottom)
            }
            out[p] = 31 * h + page.size
        }
        return out
    }

    /**
     * [GlyphStripper] の収集ロジックの**コピー**に、ページ範囲 [fromIndex]..[toIndex]
     * （0 始まり・両端含む）だけを処理して打ち切る `processPages` を組み合わせたプローブ。
     * **JVM 版 `ExtractPhaseProfileTest.RangeGlyphProbeStripper` の写し＝どちらかを変えたら両方追従させること**
     * （実機値を JVM の表と直接比べるには、分割の実装まで同じである必要がある）。
     *
     * なぜ本体を継承しないか: 採否が決まっていない形状のために本番クラスを `open` にしない。
     *
     * なぜ `startPage`/`endPage` を 0..0 にするか: `processPages` を差し替えると `currentPageNo`
     * （private・setter 無し）が 0 のままになり、`processPage` 先頭の範囲判定
     * `currentPageNo in startPage..endPage` を通せなくなるため。`startBookmarkPageNumber` /
     * `endBookmarkPageNumber` は `processPages` を通らないので既定の 0 のままだが、判定は
     * 「-1 でなければ currentPageNo と比較」で 0 同士なら両方通る（javap -c で確認済み）。
     */
    private class RangeGlyphProbeStripper(
        private val fromIndex: Int,
        private val toIndex: Int,
    ) : PDFTextStripper() {

        val pages: MutableList<MutableList<CharBox>> = mutableListOf()
        private var current: MutableList<CharBox> = mutableListOf()

        init {
            sortByPosition = false
            startPage = 0
            endPage = 0
        }

        override fun processPages(tree: PDPageTree) {
            var index = 0
            for (page in tree) {
                if (index > toIndex) break
                if (index >= fromIndex) processPage(page)
                index++
            }
        }

        override fun startPage(page: PDPage) {
            current = mutableListOf()
            pages.add(current)
            super.startPage(page)
        }

        // 本番 [GlyphStripper] と同じ復号器を使う（等価性スパイクが本番と別の字を作らないようにするため）。
        private val decoder = GlyphDecoder()

        override fun processTextPosition(text: TextPosition) {
            val s = decoder.decode(text)
            if (s.isNullOrEmpty()) return
            val bottom = text.yDirAdj.toDouble()
            current.add(
                CharBox(
                    text = s,
                    fontName = text.font?.name,
                    size = text.fontSizeInPt.toDouble(),
                    x0 = text.xDirAdj.toDouble(),
                    top = bottom - text.heightDir.toDouble(),
                    bottom = bottom,
                )
            )
        }
    }

    /**
     * 1 行出るたびに logcat とファイルの両方へ流す。**行単位**で logcat へ出すのは 1 メッセージの
     * 長さ制限で切り詰められないため。**毎行ファイルを書き戻す**のは ColorOS が CPU 集中の
     * instrumentation を kill / freeze する（task_diary #37・#38）から＝途中で落ちても既出の行は残る。
     */
    private class Reporter(private val outFile: File, private val tag: String) {
        private val sb = StringBuilder()
        fun emit(line: String) {
            sb.appendLine(line)
            Log.i(tag, line)
            outFile.writeText(sb.toString())
        }

        override fun toString(): String = sb.toString()
    }

    private fun elapsedMs(startNs: Long): Double = (System.nanoTime() - startNs) / 1_000_000.0

    /** 現在のヒープ使用量 MB。走行ごとに残して「持ち越しが無いこと」を数字で見せるため。 */
    private fun usedHeapMb(): Double {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / MB
    }

    private fun maxHeapMb(): Double = Runtime.getRuntime().maxMemory() / MB

    private fun fmt(v: Double): String = String.format("%.1f", v)

    companion object {
        private const val TAG = "PdfParallelSpike"
        private const val ARG_KS = "ks"
        private const val ARG_MAX_PAGES = "maxPages"
        private const val DEFAULT_KS = "1,2,3,4"

        /** 単一スレッド基準を表す擬似 K。実際の並列度ではないので 1 と衝突しない 0 を使う。 */
        private const val KEY_SINGLE = 0

        /** handover の採否ライン。これ未満なら進捗の atomic 化とキャンセル再設計の代償に見合わない。 */
        private const val ADOPTION_THRESHOLD = 1.3

        /** ウォームアップで回す先頭ページ数（計測からは除外）。 */
        private const val WARMUP_PAGES = 400

        private const val MB = 1024.0 * 1024.0

        /**
         * 走行前に明示 GC を掛けたあと、並行 GC が終わるのを待つ時間。
         * 待たずに次の確保を始めると、回収が追いつく前に確保が天井へ達しうる（旧実装が殺された形）。
         */
        private const val GC_SETTLE_MS = 300L

        /**
         * これを超える文書では厳密比較（CharBox 列を 2 セット同時保持）を諦め、ページ単位
         * フィンガープリント照合へ落とす閾値（JVM 版と同値）。N6169DZ の 338 万グリフは 2 セットで
         * 端末ヒープに収まらない。N2959KI の 38 万は厳密比較のまま通る。
         */
        private const val STRICT_COMPARE_GLYPH_LIMIT = 1_000_000
    }
}
