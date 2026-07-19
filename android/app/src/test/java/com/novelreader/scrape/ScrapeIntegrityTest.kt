package com.novelreader.scrape

import com.novelreader.pdf.RawChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 破損監視・層1（[ScrapeIntegrity]）の純ロジック単体テスト。ネットワーク非依存＝素の JVM で動く。
 * 3つの検知条件（空 TOC・全章空本文・異常に短い本文）と正常系が通ることを固定する。
 */
class ScrapeIntegrityTest {

    private fun toc(vararg titles: String): ScrapedToc = ScrapedToc(
        meta = ScrapedWorkMeta("作品", "著者", "https://x.example/works/1"),
        chapters = titles.map { ScrapedChapterRef(it, "https://x.example/works/1/episodes/$it") },
    )

    private fun chap(vararg lines: String): RawChapter = RawChapter("章", lines.toMutableList())

    // ── 条件①: 目次の章数 0 ─────────────────────────────────────────────
    @Test
    fun `verify - 空 TOC は ScrapeStructureException`() {
        assertThrows(ScrapeStructureException::class.java) {
            ScrapeIntegrity.verify(toc(), emptyList())
        }
    }

    // ── 条件②: 全章の本文が実文字 0（全行 blank）─────────────────────────
    @Test
    fun `verify - 全章空本文は ScrapeStructureException`() {
        // 空文字・全角空白（U+3000）・半角空白のみ＝実文字 0。
        val chapters = listOf(chap("", "　", " "), chap("", "\t"))
        assertThrows(ScrapeStructureException::class.java) {
            ScrapeIntegrity.verify(toc("a", "b"), chapters)
        }
    }

    // ── 条件③: 本文合計が床値未満（0 超だが異常に短い）─────────────────────
    @Test
    fun `verify - 床値未満の短小本文は ScrapeStructureException`() {
        // 実文字 5（床値 20 未満）。ナビ断片だけが漏れた破損を模す。
        assertThrows(ScrapeStructureException::class.java) {
            ScrapeIntegrity.verify(toc("a"), listOf(chap("あいうえお")))
        }
    }

    // ── 正常系: 床値以上は素通り（例外を投げない）────────────────────────
    @Test
    fun `verify - 床値以上の本文は素通りする`() {
        // 実文字 24（>=20）。全角字下げは数えないが本文が十分に長い。
        val body = chap("　人の役に立つ仕事をするように。", "二段落目もある。")
        // 例外が飛ばなければ成功（戻り値は Unit）。
        ScrapeIntegrity.verify(toc("a"), listOf(body))
    }

    // ── 床値の境界: ちょうど 20 字は素通り・19 字は弾く ──────────────────
    @Test
    fun `verify - 床値ちょうど20字は通り19字は弾く`() {
        val exact20 = "あ".repeat(20)
        ScrapeIntegrity.verify(toc("a"), listOf(chap(exact20))) // 例外なし＝OK

        val short19 = "あ".repeat(19)
        assertThrows(ScrapeStructureException::class.java) {
            ScrapeIntegrity.verify(toc("a"), listOf(chap(short19)))
        }
    }

    // ── realCharCount: 全角/半角空白・タブを非文字として数えない ─────────
    @Test
    fun `realCharCount - 空白類を除いた実文字数を返す`() {
        assertEquals(0, realCharCount(listOf("", "　", " ", "\t")))
        assertEquals(3, realCharCount(listOf("　あ　い", "う ")))
    }

    // ── 派生型: ScrapeStructureException は ScrapeException として捕捉できる ─
    @Test
    fun `ScrapeStructureException は ScrapeException 派生`() {
        val e = assertThrows(ScrapeException::class.java) {
            ScrapeIntegrity.verify(toc(), emptyList())
        }
        assert(e is ScrapeStructureException)
    }
}
