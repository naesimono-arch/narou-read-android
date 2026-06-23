package com.novelreader.pdfproto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * CLI エントリポイント。
 *   抽出:      run --args="<pdf_path> [output_dir]"
 *   デバッグ:  run --args="<pdf_path> --dump"            座標キャリブレーション用ダンプ
 *   計測:      run --args="<pdf_path> --bench 5"         抽出時間ベンチマーク
 *   検証:      run --args="<pdf_path> --compare <golden.json>"  ゴールデン精度比較
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: <pdf_path> [output_dir] [--dump] [--bench N] [--compare golden.json]")
        exitProcess(2)
    }

    val positional = args.filterNot { it.startsWith("--") }
    val pdfPath = positional.getOrNull(0) ?: run {
        System.err.println("PDF パスを指定してください")
        exitProcess(2)
    }
    val pdfFile = File(pdfPath)
    if (!pdfFile.exists()) {
        System.err.println("ファイルが見つかりません: $pdfPath")
        exitProcess(2)
    }

    when {
        args.contains("--dump") -> runDump(pdfFile)
        args.contains("--bench") -> {
            val n = args.getOrNull(args.indexOf("--bench") + 1)?.toIntOrNull() ?: 5
            runBench(pdfFile, n)
        }
        args.contains("--compare") -> {
            val goldenPath = args.getOrNull(args.indexOf("--compare") + 1) ?: run {
                System.err.println("--compare にはゴールデン JSON のパスが必要です")
                exitProcess(2)
            }
            runCompare(pdfFile, File(goldenPath))
        }
        else -> {
            val outputDir = positional.getOrNull(1) ?: "output"
            runExtract(pdfFile, File(outputDir))
        }
    }
}

/** PDF を 1 冊分の Book へ抽出する（抽出・比較・ベンチで共有する単一の手順）。 */
private fun extractBook(doc: PDDocument): Book {
    val meta = PdfExtractor.extractBookMeta(doc)
    val paragraphs = PdfExtractor.runFinalEngine(doc)
    val rawChapters = ChapterProcessor.splitIntoChapters(paragraphs)
    val finalChapters = ChapterProcessor.processForewordAfterword(rawChapters)
    return Book(meta.title, meta.author, ChapterProcessor.buildChapters(finalChapters))
}

private fun runExtract(pdfFile: File, outputDir: File) {
    try {
        loadDocument(pdfFile).use { doc ->
            val book = extractBook(doc)
            outputDir.mkdirs()
            val outFile = File(outputDir, pdfFile.nameWithoutExtension + ".json")
            outFile.writeText(json.encodeToString(book))

            System.err.println("OK: ${outFile.path}")
            System.err.println("  title=${book.title} / author=${book.author} / chapters=${book.chapters.size}")
        }
    } catch (e: EncryptedPdfError) {
        System.err.println("EncryptedPdfError: ${e.message}")
        exitProcess(3)
    } catch (e: CorruptedPdfError) {
        System.err.println("CorruptedPdfError: ${e.message}")
        exitProcess(4)
    }
}

/**
 * PDF を開く。暗号化・破損をユーザー向け例外へ分類する（app.py の classifyError 相当）。
 */
private fun loadDocument(pdfFile: File): PDDocument =
    try {
        PDDocument.load(pdfFile)
    } catch (e: InvalidPasswordException) {
        throw EncryptedPdfError(e.message ?: "encrypted", e)
    } catch (e: IOException) {
        throw CorruptedPdfError(e.message ?: "corrupted", e)
    }

// ---------------------------------------------------------------------------
// フェーズ1: 座標・フォントサイズのキャリブレーション用ダンプ
// ---------------------------------------------------------------------------
private fun runDump(pdfFile: File) {
    loadDocument(pdfFile).use { doc ->
        val pages = PdfExtractor.loadPages(doc)
        println("totalPages=${doc.numberOfPages}, extractedPages=${pages.size}")

        // フォントサイズのヒストグラム（全ページ）
        val sizeHist = sortedMapOf<Double, Int>()
        pages.flatten().forEach { c ->
            val key = (c.size * 100).toInt() / 100.0
            sizeHist[key] = (sizeHist[key] ?: 0) + 1
        }
        println("---- font size histogram (size -> count) ----")
        sizeHist.entries.sortedByDescending { it.value }.take(15).forEach {
            println("  size=%.2f  count=%d".format(it.key, it.value))
        }

        // 最初の本文ページ(index 3)を詳細表示
        val bodyPage = pages.getOrNull(3) ?: return@use
        println("---- page index 3: first 12 glyphs ----")
        bodyPage.take(12).forEach { c ->
            println("  '%s' font=%s size=%.2f x0=%.2f top=%.2f bottom=%.2f"
                .format(c.text, c.fontName, c.size, c.x0, c.top, c.bottom))
        }

        // 12pt(ページ番号候補)の位置 → PAGE_NUM_Y との整合確認
        println("---- ~12pt glyphs (page-number candidates) across body pages ----")
        pages.drop(3).take(5).forEachIndexed { i, page ->
            page.filter { ParserRules.isClose(it.size, ParserRules.FONT_SIZE_PAGE, absTol = 0.3) }
                .forEach { c ->
                    println("  page=${i + 3} '%s' top=%.2f bottom=%.2f (PAGE_NUM_Y=%.2f)"
                        .format(c.text, c.top, c.bottom, ParserRules.PAGE_NUM_Y))
                }
        }
    }
}

// ---------------------------------------------------------------------------
// フェーズ3: 抽出時間ベンチマーク
// ---------------------------------------------------------------------------
private fun runBench(pdfFile: File, iterations: Int) {
    // ウォームアップ
    repeat(2) { loadDocument(pdfFile).use { doc -> extractBook(doc) } }
    val times = mutableListOf<Long>()
    repeat(iterations) {
        val start = System.nanoTime()
        loadDocument(pdfFile).use { doc -> extractBook(doc) }
        times.add((System.nanoTime() - start) / 1_000_000)
    }
    times.sort()
    val median = times[times.size / 2]
    println("bench(${pdfFile.name}): runs=$iterations median=${median}ms min=${times.first()}ms max=${times.last()}ms")
}

// ---------------------------------------------------------------------------
// フェーズ2: ゴールデン精度比較（Python リファレンス由来の golden.json と照合）
// ---------------------------------------------------------------------------
private fun runCompare(pdfFile: File, goldenFile: File) {
    if (!goldenFile.exists()) {
        System.err.println("ゴールデンが見つかりません: ${goldenFile.path}")
        exitProcess(2)
    }
    val parser = Json { ignoreUnknownKeys = true }
    val golden = parser.decodeFromString<Book>(goldenFile.readText())
    val candidate = loadDocument(pdfFile).use { extractBook(it) }
    val result = GoldenComparator.compare(golden, candidate)
    // 比較結果は数値なので stdout（化けない）。本文ログは stderr に分離。
    println(GoldenComparator.format(pdfFile.name, result))
}
