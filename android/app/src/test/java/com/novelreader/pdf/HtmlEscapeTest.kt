package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * htmlEscape（Python html.escape(s, quote=True) の忠実移植）のテスト。
 * ChapterProcessor（本文）と HtmlExporter（タイトル）が共有する唯一の実装で、
 * Python 出力とのバイト等価がゴールデン回帰の前提＝置換対象5文字と置換順をここで固定する。
 */
class HtmlEscapeTest {

    @Test
    fun eachSpecialCharacter_escaped() {
        // quote=True 相当＝ " と ' も含む5文字全種。&#x27;（&apos; でない）が Python 準拠の要。
        assertEquals("&amp;", htmlEscape("&"))
        assertEquals("&lt;", htmlEscape("<"))
        assertEquals("&gt;", htmlEscape(">"))
        assertEquals("&quot;", htmlEscape("\""))
        assertEquals("&#x27;", htmlEscape("'"))
    }

    @Test
    fun mixedString_allOccurrencesEscaped() {
        assertEquals(
            "&lt;a href=&quot;x&quot;&gt;It&#x27;s A&amp;B&lt;/a&gt;",
            htmlEscape("""<a href="x">It's A&B</a>""")
        )
    }

    @Test
    fun alreadyEscapedEntity_ampersandEscapedAgain() {
        // 既にエスケープ済みの文字列も & を再エスケープする（冪等ではない）。Python html.escape と同一挙動で、
        // 「入力の & は常に文字データ」という前提の固定＝二重適用すると壊れることの明文化。
        assertEquals("&amp;amp;", htmlEscape("&amp;"))
        assertEquals("&amp;lt;", htmlEscape("&lt;"))
    }

    @Test
    fun replacementOrder_ampersandFirst_noDoubleEscapeOfGeneratedEntities() {
        // & を最優先で置換するため、< から生成された &lt; の & が二重エスケープされない（実装の置換順の固定）。
        assertEquals("&lt;&gt;&amp;", htmlEscape("<>&"))
    }

    @Test
    fun emptyAndPlainStrings_passThrough() {
        assertEquals("", htmlEscape(""))
        // 対象5文字を含まない日本語テキストは無変換で素通し。
        assertEquals("吾輩は猫である。改行\nタブ\tも対象外", htmlEscape("吾輩は猫である。改行\nタブ\tも対象外"))
    }
}
