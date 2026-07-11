package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * PdfBookExtractor.process の進捗列・戻り値・例外分類テスト。
 * 移植元: test_logic.py TestProcessPdf（正常系＋各エラー変換）。実 PDF は fake engine で置換する。
 */
class PdfBookExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Ev(val step: Int, val local: Float, val phase: String, val title: String)

    /** 抽出結果をキャンド返しし、任意でページ進捗を発火／例外を投げる fake handle。 */
    private class FakeHandle(
        val meta: BookMeta,
        val paragraphs: List<String>,
        val loadTicks: List<Pair<Int, Int>> = emptyList(),
        val pageTicks: List<Pair<Int, Int>> = emptyList(),
        val metaError: Throwable? = null,
        val engineError: Throwable? = null,
    ) : PdfHandle {
        var closed = false
        override fun extractMeta(): BookMeta {
            metaError?.let { throw it }
            return meta
        }
        override fun runEngine(onProgress: (EnginePhase, Int, Int) -> Unit): List<String> {
            engineError?.let { throw it }
            // 実装(runFinalEngine)と同順＝load(全ページ抽出)を先に、process(段落化)を後に発火する。
            loadTicks.forEach { (loaded, total) -> onProgress(EnginePhase.LOAD, loaded, total) }
            pageTicks.forEach { (processed, total) -> onProgress(EnginePhase.PROCESS, processed, total) }
            return paragraphs
        }
        override fun close() { closed = true }
    }

    private class FakeEngine(val handle: FakeHandle) : PdfEngine {
        override fun open(pdfFile: File): PdfHandle = handle
    }

    private fun run(handle: FakeHandle, events: MutableList<Ev>? = null): BookMeta =
        PdfBookExtractor.process(
            FakeEngine(handle),
            File("dummy.pdf"),
            "book_id",
            tmp.newFolder(),
        ) { step, local, phase, title -> events?.add(Ev(step, local, phase, title)) }

    @Test fun happyPathEmitsFourStepProgressAndReturnsMeta() {
        // 1章分の段落（【題名】マーカーで章が立つ）＋ load 2ティック・process 2ティック。
        val handle = FakeHandle(
            meta = BookMeta("テスト小説", "テスト作者"),
            paragraphs = listOf("【題名】第一話", "本文1", "本文2"),
            loadTicks = listOf(0 to 4, 2 to 4),
            pageTicks = listOf(0 to 4, 2 to 4),
        )
        val events = mutableListOf<Ev>()
        val result = run(handle, events)

        // 戻り値＝メタ、handle は use で確実にクローズ。
        assertEquals(BookMeta("テスト小説", "テスト作者"), result)
        assertTrue(handle.closed)

        // step の並び: 0 / 1(開始)+1(load×2)+1(process×2) / 2(章)+2(前後書き) / 3(開始)+3(HTML1章分)
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 2, 2, 3, 3), events.map { it.step })

        // step0 はタイトル未確定＝空。以後は確定タイトルを載せる。
        assertEquals(Ev(0, 0f, "タイトルを読み取っています…", ""), events.first())
        assertTrue(events.drop(1).all { it.title == "テスト小説" })

        // step-1 開始は「読み込み」フェーズ語・local=0・ページ数無し（総ページ未確定のため tick 前は付けない）。
        assertEquals(Ev(1, 0f, "本文を読み込んでいます… 0%", "テスト小説"), events[1])

        // LOAD(読み込み): local = LOAD_WEIGHT(0.75) × loaded/total。%（主・通し）＋ページ n/m（副・フェーズ内）。
        assertEquals(0f, events[2].local, 0f)
        assertEquals("本文を読み込んでいます… 0%（0/4ページ）", events[2].phase)
        assertEquals(0.375f, events[3].local, 1e-6f)   // 0.75 × 2/4 → 37%
        assertEquals("本文を読み込んでいます… 37%（2/4ページ）", events[3].phase)

        // PROCESS(整形): local = LOAD_WEIGHT + (1-LOAD_WEIGHT) × processed/total。フェーズ語が変わり % は巻き戻らない。
        // 副表示のページは本文ページ基準で 0 から数え直すが、フェーズ語「整形」で別工程と読ませ 2周目錯覚を回避。
        assertEquals(0.75f, events[4].local, 1e-6f)    // 0.75 + 0.25×0/4 → 75%
        assertEquals("本文を整形しています… 75%（0/4ページ）", events[4].phase)
        assertEquals(0.875f, events[5].local, 1e-6f)   // 0.75 + 0.25×2/4 → 87%
        assertEquals("本文を整形しています… 87%（2/4ページ）", events[5].phase)

        // HTML が実際に書き出されている（export 経路まで通っている）。
        val outDirs = tmp.root.listFiles()?.firstOrNull { it.isDirectory }
        assertTrue(outDirs?.let { File(it, "index.html").exists() } == true)
    }

    @Test(expected = EncryptedPdfError::class)
    fun passwordErrorIsClassifiedEncrypted() {
        run(FakeHandle(BookMeta("t", "a"), emptyList(), metaError = IOException("bad password")))
    }

    @Test(expected = InsufficientStorageError::class)
    fun noSpaceErrorIsClassifiedStorage() {
        run(FakeHandle(BookMeta("t", "a"), emptyList(), engineError = IOException("No space left on device")))
    }

    @Test(expected = CorruptedPdfError::class)
    fun genericIoErrorIsClassifiedCorrupted() {
        run(FakeHandle(BookMeta("t", "a"), emptyList(), engineError = IOException("Invalid xref table")))
    }

    @Test(expected = IllegalStateException::class)
    fun unknownErrorPassesThrough() {
        // どの分類にも当てはまらない例外はラップせず素通し（app.py bare raise 相当）。
        run(FakeHandle(BookMeta("t", "a"), emptyList(), metaError = IllegalStateException("boom")))
    }
}
