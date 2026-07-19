package com.novelreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 共有テキストからの URL 抽出 [WebImportIntentParser.firstUrl] の単体テスト（P3 取込導線）。
 * 純関数＝Android 非依存で素の JVM で動く。共有テキストの代表形を固定する。
 */
class WebImportIntentParserTest {

    @Test
    fun `タイトル＋改行＋URL 形は URL 部分だけを取り出す`() {
        val text = "小説のタイトル\nhttps://kakuyomu.jp/works/123"
        assertEquals("https://kakuyomu.jp/works/123", WebImportIntentParser.firstUrl(text))
    }

    @Test
    fun `URL のみのテキストはそのまま返す`() {
        assertEquals(
            "https://kakuyomu.jp/works/123",
            WebImportIntentParser.firstUrl("https://kakuyomu.jp/works/123"),
        )
    }

    @Test
    fun `URL を含まないテキストは null`() {
        assertNull(WebImportIntentParser.firstUrl("ただの本文で URL は無い"))
    }

    @Test
    fun `http 形も抽出できる`() {
        assertEquals("http://example.com/novel/1", WebImportIntentParser.firstUrl("http://example.com/novel/1"))
    }

    @Test
    fun `null と空白のみは null`() {
        assertNull(WebImportIntentParser.firstUrl(null))
        assertNull(WebImportIntentParser.firstUrl("   "))
    }

    @Test
    fun `URL の後に続く文があっても空白までを URL とみなす`() {
        // 共有テキストが「URL + 空白 + 続き」の形でも最初のトークンだけを取る（末尾整形はアダプタ側）。
        assertEquals(
            "https://kakuyomu.jp/works/123",
            WebImportIntentParser.firstUrl("https://kakuyomu.jp/works/123 で読んでいます"),
        )
    }
}
