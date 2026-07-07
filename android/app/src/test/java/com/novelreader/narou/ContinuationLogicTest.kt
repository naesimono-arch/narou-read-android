package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
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
        assertEquals("N2959KI", newEpisodes.ncode)
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
        assertEquals("N2959KI", upToDate.ncode)
        assertEquals(139, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_pdfMoreThanTotal() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 139, noveltypeCompact = 1)
        val result = computeContinuation(pdfChapterCount = 140, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals("N2959KI", upToDate.ncode)
        assertEquals(139, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_shortStoryUpToDate() {
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 1, noveltypeCompact = 2)
        val result = computeContinuation(pdfChapterCount = 1, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals("N2959KI", upToDate.ncode)
        assertEquals(1, upToDate.totalEpisodes)
    }

    @Test
    fun testComputeContinuation_shortStoryTotalMoreThanPdf() {
        // 短編設定（novelType=2）であるため、なろう側の話数が多くても UpToDate になる
        val novel = NarouNovel(ncode = "N2959KI", generalAllNo = 5, noveltypeCompact = 2)
        val result = computeContinuation(pdfChapterCount = 3, novel = novel)
        assertTrue(result is ContinuationInfo.UpToDate)
        val upToDate = result as ContinuationInfo.UpToDate
        assertEquals("N2959KI", upToDate.ncode)
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
        assertEquals("https://ncode.syosetu.com/n2959ki/", narouWorkUrl("N2959KI"))
        assertEquals("https://ncode.syosetu.com/n2959ki/128/", narouEpisodeUrl("N2959KI", 128))
        
        // トリムおよび小文字化の確認
        assertEquals("https://ncode.syosetu.com/n2959ki/", narouWorkUrl("  N2959KI  "))
        assertEquals("https://ncode.syosetu.com/n2959ki/128/", narouEpisodeUrl("  N2959KI  ", 128))
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
}
