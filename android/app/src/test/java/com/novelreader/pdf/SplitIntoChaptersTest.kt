package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChapterProcessor.splitIntoChapters の章分割テスト。
 * 移植元: python test_logic.py TestSplitIntoChapters（8件）。
 */
class SplitIntoChaptersTest {

    @Test fun emptyInput() =
        assertEquals(emptyList<RawChapter>(), ChapterProcessor.splitIntoChapters(emptyList()))

    @Test fun noTitleMarkers() {
        // 題名マーカーなし → 既定タイトルで 1 章にまとめる
        val result = ChapterProcessor.splitIntoChapters(listOf("本文A", "本文B"))
        assertEquals(1, result.size)
        assertEquals("作品情報・プロローグ", result[0].title)
        assertEquals(listOf("本文A", "本文B"), result[0].body)
    }

    @Test fun singleChapter() {
        val paragraphs = listOf("【題名】第一話　始まり", "本文A", "本文B")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(1, result.size)
        // 中間の全角スペースは trim 対象外＝保持される
        assertEquals("第一話　始まり", result[0].title)
        assertEquals(listOf("本文A", "本文B"), result[0].body)
    }

    @Test fun multipleChapters() {
        val paragraphs = listOf("【題名】第一話", "本文1", "【題名】第二話", "本文2")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(2, result.size)
        assertEquals("第一話", result[0].title)
        assertEquals("第二話", result[1].title)
    }

    @Test fun afterwordTitleBecomesSeparateChapter() {
        // 後書きも通常章として分離される（後処理は processForewordAfterword）
        val paragraphs = listOf("【題名】第一話", "本文1", "【題名】後書き", "後書き本文")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(2, result.size)
        assertEquals("後書き", result[1].title)
        assertTrue(result[1].body.contains("後書き本文"))
    }

    @Test fun afterwordWithNoBodyIsDropped() {
        // 題名直後に本文が無い章は currentBody が空のためドロップ
        val paragraphs = listOf("【題名】第一話", "本文1", "【題名】後書き")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(1, result.size)
        assertEquals("第一話", result[0].title)
    }

    @Test fun consecutiveTitlesNoBodyBetween() {
        // 本文のない章はサイレントドロップ（仕様明文化）
        val paragraphs = listOf("【題名】第一話", "【題名】第二話", "本文")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(1, result.size)
        assertEquals("第二話", result[0].title)
        assertEquals(listOf("本文"), result[0].body)
    }

    @Test fun afterwordSubstringInChapterTitleIsSplit() {
        // タイトルに「後書き」を含む話も通常章として分離される
        val paragraphs = listOf("【題名】第一話", "本文1", "【題名】第五話　後書きの話", "本文2")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(2, result.size)
        assertEquals("第五話　後書きの話", result[1].title)
        assertTrue(!result[0].body.contains("第五話　後書きの話"))
    }
}
