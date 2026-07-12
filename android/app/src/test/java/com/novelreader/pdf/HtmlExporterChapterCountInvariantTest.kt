package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 章数が二経路（chap_N.html ファイル数 ／ index.html の目次エントリ数）で導出されることに対する不変条件テスト
 * （UX監査 ssot・要検証項目のテスト固定）。現状 HtmlExporter が両者をロックステップ生成して一致するが、
 * 将来どちらか片方だけを変える退行（例: 目次だけ間引く・chap だけ増やす）を testDebugUnitTest で検知するため、
 * 「chap ファイル数 == 目次 <li> 数 == 入力章数」を固定する。
 */
class HtmlExporterChapterCountInvariantTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun chapters(n: Int): List<ProcessedChapter> =
        (1..n).map { ProcessedChapter("第${it}話　テスト章", "本文${it}") }

    /** index.html 中の目次リンク（<li><a href="chap_N.html">）の数。 */
    private fun tocEntryCount(indexHtml: String): Int =
        Regex("<li><a href=\"chap_\\d+\\.html\">").findAll(indexHtml).count()

    /** 出力ディレクトリ内の chap_N.html ファイル数。 */
    private fun chapFileCount(dir: File): Int =
        dir.listFiles { f -> f.name.matches(Regex("chap_\\d+\\.html")) }?.size ?: 0

    @Test
    fun `chap ファイル数と目次エントリ数は入力章数と三者一致する`() {
        // 単数・複数・二桁の代表 N で不変条件を固定する。
        for (n in listOf(1, 2, 5, 23)) {
            val out = tmp.newFolder("out_$n")
            HtmlExporter.exportToMobileHtml(chapters(n), out, "テスト小説", "id") { _, _ -> }
            val index = File(out, "index.html").readBytes().toString(Charsets.UTF_8)

            val toc = tocEntryCount(index)
            val files = chapFileCount(out)
            assertEquals("N=$n: 目次エントリ数が入力章数と一致", n, toc)
            assertEquals("N=$n: chap ファイル数が入力章数と一致", n, files)
            // 二経路（chap ファイル数 ⇔ 目次数）の一致＝将来の silent divergence 検知の要。
            assertEquals("N=$n: chap ファイル数と目次数が一致", files, toc)
        }
    }
}
