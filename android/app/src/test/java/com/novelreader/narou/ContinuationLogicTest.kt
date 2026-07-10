package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ContinuationLogicTest {

    @Test
    fun testComputeContinuation_newEpisodes() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 139, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 127, novel = novel)
        assertTrue(result is ContinuationInfo.NewEpisodes)
        val newEpisodes = result as ContinuationInfo.NewEpisodes
        assertEquals(Ncode("N2959KI"), newEpisodes.ncode)
        assertEquals(139, newEpisodes.totalEpisodes)
        assertEquals(127, newEpisodes.pdfEpisodes)
        assertEquals(128, newEpisodes.nextEpisode)
        assertEquals(12, newEpisodes.newCount)
    }

    @Test
    fun testComputeContinuation_upToDate() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 139, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 139, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals(Ncode("N2959KI"), upToDate.ncode)
        assertEquals(139, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_pdfMoreThanTotal() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 139, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 140, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals(Ncode("N2959KI"), upToDate.ncode)
        assertEquals(139, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_shortStoryUpToDate() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 1, noveltypeCompact = 2)
        val result = computeContinuation(pdfChapterCount = 1, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals(Ncode("N2959KI"), upToDate.ncode)
        assertEquals(1, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_shortStoryTotalMoreThanPdf() {
        // 短編設定（novelType=2）であるため、なろう側の話数が多くても UpToDate になる
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 5, noveltypeCompact = 2)
        val result = computeContinuation(pdfChapterCount = 3, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals(Ncode("N2959KI"), upToDate.ncode)
        assertEquals(5, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_generalAllNoNull() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = null, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 10, novel = novel)
        assertNull(result)
    }

    @Test
    fun testComputeContinuation_ncodeNull() {
        val novel = NarouNovel(ncode = null, generalAllNo = 100, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 10, novel = novel)
        assertNull(result)
    }

    @Test
    fun testComputeContinuation_ncodeBlank() {
        val novel = NarouNovel(ncode = "   ", generalAllNo = 100, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 10, novel = novel)
        assertNull(result)
    }

    @Test
    fun testComputeContinuation_pdfZero() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 100, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 0, novel = novel)
        assertNull(result)
    }

    @Test
    fun testComputeContinuation_pdfNegative() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 100, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = -5, novel = novel)
        assertNull(result)
    }

    @Test
    fun testNarouUrls() {
        assertEquals("https://ncode.syosetu.com/n2959ki/", narouWorkUrl(Ncode("N2959KI")))
        assertEquals("https://ncode.syosetu.com/n2959ki/128/", narouEpisodeUrl(Ncode("N2959KI"), 128))

        // トリムおよび小文字化の確認（正規化はサイト側で従来どおり施すため Ncode 生値に空白があってもよい）
        assertEquals("https://ncode.syosetu.com/n2959ki/", narouWorkUrl(Ncode("  N2959KI  ")))
        assertEquals("https://ncode.syosetu.com/n2959ki/128/", narouEpisodeUrl(Ncode("  N2959KI  "), 128))
    }

    @Test
    fun testIsValidNcode() {
        assertTrue(isValidNcode("N2959KI"))
        assertTrue(isValidNcode("n0563jr"))
        assertTrue(isValidNcode(" N2959KI ")) // トリムされて有効になること
        assertFalse(isValidNcode(""))
        assertFalse(isValidNcode("   "))
        assertFalse(isValidNcode("2959KI"))   // Nがない
        assertFalse(isValidNcode("N29KI"))    // 数字が2桁
        assertFalse(isValidNcode("N2959KIX"))  // 英字が3桁
    }

    // ── parseNarouEpisodeNumber（機能②・WebView 読書位置の URL 抽出）──────────────────────
    @Test
    fun testParseNarouEpisodeNumber_episodePage() {
        // 話ページ .../<ncode>/N/ から話数を取り出す。ncode の大文字小文字は URL(小文字)と正規化して照合。
        assertEquals(128, parseNarouEpisodeNumber("https://ncode.syosetu.com/n2959ki/128/", Ncode("N2959KI")))
        // 末尾スラッシュ無しも許容。
        assertEquals(1, parseNarouEpisodeNumber("https://ncode.syosetu.com/n2959ki/1", Ncode("N2959KI")))
        // ncode を大文字で渡しても小文字化して一致する。
        assertEquals(955, parseNarouEpisodeNumber("https://ncode.syosetu.com/n6169dz/955/", Ncode("n6169dz")))
    }

    @Test
    fun testParseNarouEpisodeNumber_nonEpisodePages() {
        // 目次(作品トップ)は話数ではない＝記録しない。
        assertNull(parseNarouEpisodeNumber("https://ncode.syosetu.com/n2959ki/", Ncode("N2959KI")))
        // 別作品の話ページは当該作品の記録対象でない。
        assertNull(parseNarouEpisodeNumber("https://ncode.syosetu.com/n0000aa/5/", Ncode("N2959KI")))
        // 感想・ユーザーページなど別ホストは対象外。
        assertNull(parseNarouEpisodeNumber("https://novelcom.syosetu.com/impression/list/ncode/n2959ki/", Ncode("N2959KI")))
        // 話数が0や非数字は不正として弾く。
        assertNull(parseNarouEpisodeNumber("https://ncode.syosetu.com/n2959ki/0/", Ncode("N2959KI")))
        assertNull(parseNarouEpisodeNumber("https://ncode.syosetu.com/n2959ki/abc/", Ncode("N2959KI")))
    }
}
