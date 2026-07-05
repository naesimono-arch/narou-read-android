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
        val pageTicks: List<Pair<Int, Int>> = emptyList(),
        val metaError: Throwable? = null,
        val engineError: Throwable? = null,
    ) : PdfHandle {
        var closed = false
        override fun extractMeta(): BookMeta {
            metaError?.let { throw it }
            return meta
        }
        override fun runEngine(onPageProgress: (Int, Int) -> Unit): List<String> {
            engineError?.let { throw it }
            pageTicks.forEach { (processed, total) -> onPageProgress(processed, total) }
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
        // 1章分の段落（【題名】マーカーで章が立つ）＋ページ進捗2ティック。
        val handle = FakeHandle(
            meta = BookMeta("テスト小説", "テスト作者"),
            paragraphs = listOf("【題名】第一話", "本文1", "本文2"),
            pageTicks = listOf(0 to 4, 1 to 4),
        )
        val events = mutableListOf<Ev>()
        val result = run(handle, events)

        // 戻り値＝メタ、handle は use で確実にクローズ。
        assertEquals(BookMeta("テスト小説", "テスト作者"), result)
        assertTrue(handle.closed)

        // step の並び: 0 / 1(開始)+1(tick)+1(tick) / 2(章)+2(前後書き) / 3(開始)+3(HTML1章分)
        assertEquals(listOf(0, 1, 1, 1, 2, 2, 3, 3), events.map { it.step })

        // step0 はタイトル未確定＝空。以後は確定タイトルを載せる。
        assertEquals(Ev(0, 0f, "タイトルを読み取っています…", ""), events.first())
        assertTrue(events.drop(1).all { it.title == "テスト小説" })

        // 本文ページ進捗の step-local と件数表記（processed+1 / total）。
        assertEquals(0f, events[2].local, 0f)
        assertEquals("本文を抽出しています… (1/4ページ)", events[2].phase)
        assertEquals(0.25f, events[3].local, 0f)
        assertEquals("本文を抽出しています… (2/4ページ)", events[3].phase)

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
