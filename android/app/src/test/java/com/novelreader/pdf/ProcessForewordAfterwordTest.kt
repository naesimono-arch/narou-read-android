package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChapterProcessor.processForewordAfterword の前後書き整形・ルビ・エスケープテスト。
 * 移植元: python test_logic.py TestProcessForewordAfterwword（12件）。
 */
class ProcessForewordAfterwordTest {

    private fun chap(title: String, vararg body: String) = RawChapter(title, body.toMutableList())

    @Test fun rubySingleChar() {
        // 1文字ルビ → <ruby>字<rt>よみ</rt></ruby>
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|字《よみ》")))
        assertTrue(result[0].body.contains("<ruby>字<rt>よみ</rt></ruby>"))
    }

    @Test fun rubyMultiCharSameLength() {
        // 2文字+2文字 → 1文字ずつ分割
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|漢字《かじ》")))
        assertTrue(result[0].body.contains("<ruby>漢<rt>か</rt></ruby>"))
        assertTrue(result[0].body.contains("<ruby>字<rt>じ</rt></ruby>"))
    }

    @Test fun rubyMultiCharDifferentLength() {
        // 親文字とルビの文字数が異なる → まとめて 1 つの ruby タグ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|三文字《よみ》")))
        assertTrue(result[0].body.contains("<ruby>三文字<rt>よみ</rt></ruby>"))
    }

    @Test fun htmlSpecialCharsAreEscaped() {
        // 本文中の < > & はエスケープされ生タグとして解釈されない
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "a < b & c > d")))
        val body = result[0].body
        assertTrue(body.contains("a &lt; b &amp; c &gt; d"))
        assertFalse(body.contains("a < b"))
    }

    @Test fun escapeAndRubyCoexist() {
        // エスケープ後もルビは <ruby> へ変換され、親文字内の & も実体参照になる
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|A&B《えび》")))
        assertTrue(result[0].body.contains("<ruby>A&amp;B<rt>えび</rt></ruby>"))
    }

    @Test fun forewordPrepended() {
        // 前書きは次章の先頭へ付与
        val chapters = listOf(chap("前書き", "前書き本文"), chap("第一話", "本文"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（前書き）"))
        assertTrue(result[0].body.contains("前書き本文"))
    }

    @Test fun afterwordAppended() {
        // 後書きは直前章の末尾へ付与
        val chapters = listOf(chap("第一話", "本文"), chap("後書き", "後書き本文"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（後書き）"))
    }

    @Test fun noForewordAfterword() {
        // 前書き・後書きなし → そのまま通過
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "本文")))
        assertEquals(1, result.size)
        assertEquals("第一話", result[0].title)
    }

    @Test fun onlyForewordNoFollowingChapter() {
        // 前書きのみ＝tempForeword がセットされるが使われずドロップ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("前書き", "前書き本文")))
        assertEquals(emptyList<ProcessedChapter>(), result)
    }

    @Test fun onlyAfterwordNoPrecedingChapter() {
        // 後書きのみ＝前章が無いため if finalChapters チェックでドロップ
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("後書き", "後書き本文")))
        assertEquals(emptyList<ProcessedChapter>(), result)
    }

    @Test fun rubyUnmatchedEmptyReading() {
        // |字《》 → [^》]+ は空にマッチせずマーカーがそのまま残る（クラッシュしない）
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("第一話", "|字《》")))
        assertTrue(result[0].body.contains("|字《》"))
        assertFalse(result[0].body.contains("<ruby>"))
    }

    @Test fun rubyInAfterwordBody() {
        // 後書き本文のルビマーカーも変換される
        val chapters = listOf(chap("第一話", "本文"), chap("後書き", "|字《よみ》"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertTrue(result[0].body.contains("<ruby>字<rt>よみ</rt></ruby>"))
    }

    // ── 構造マーカー判定（監査 A1: structural-marker-substring-match の回帰固定）──
    // 塞ぐ穴は2つあり、両方を同時に満たす形でしか通らないよう固定する:
    //  (a) PDF: 実 corpus のマーカーは〈話タイトル＋末尾「（後書き）」〉＝これは畳み込まれねばならない
    //      （裸の「後書き」は sample_pdfs 4本の Bold 見出し 全数抽出で 0 件。完全一致だけだと畳み込み全滅）
    //  (b) Web: 著者記述の章題は「前書き/後書き」を含むだけで畳み込まれてはならない（章ごと消える）

    @Test fun titleContainingMarkerIsNormalChapter() {
        // 「後書きにかえて」等＝マーカーを部分に含む著者記述タイトルは通常章として残る
        val chapters = listOf(chap("第一話", "本文一"), chap("後書きにかえて", "本文二"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(2, result.size)
        assertEquals("後書きにかえて", result[1].title)
        assertTrue(result[1].body.contains("本文二"))
        // 前章へ畳み込まれていない（旧実装は本文二が第一話末尾へ吸収され章が消えた）
        assertFalse(result[0].body.contains("本文二"))
    }

    @Test fun firstChapterContainingMarkerIsNotDropped() {
        // 先頭章がマーカーを含む題でも本文ごと破棄されない
        // （旧実装は前書き扱い→後続章なしで finalChapters が空＝本文が丸ごと失われた）
        val result = ChapterProcessor.processForewordAfterword(listOf(chap("前書きのような話", "唯一の本文")))
        assertEquals(1, result.size)
        assertEquals("前書きのような話", result[0].title)
        assertTrue(result[0].body.contains("唯一の本文"))
    }

    @Test fun whitespacePaddedMarkerStillFolds() {
        // PDF 機械生成マーカーの周辺空白揺れは trim で吸収し、従来どおり畳み込む
        val chapters = listOf(chap(" 前書き ", "前書き本文"), chap("第一話", "本文"), chap("　後書き", "後書き本文"))
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（前書き）"))
        assertTrue(result[0].body.contains("（後書き）"))
    }

    // ── (a) PDF 実 corpus の形＝〈話タイトル＋末尾の括弧付きマーカー〉が畳み込まれること ──
    // 文字列は sample_pdfs/N2959KI.pdf の実在見出しをそのまま使う（golden の章題は「９　手を焼いてるよ」＝
    // 括弧付きの方が畳み込まれた結果。この便の前は完全一致判定で畳み込まれず章数 131→233 に増えて赤だった）。

    @Test fun pdfTrailingAfterwordMarkerFolds() {
        val chapters = listOf(
            chap("９　手を焼いてるよ", "本編"),
            chap("９　手を焼いてるよ（後書き）", "あとがき本文"),
        )
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertEquals("９　手を焼いてるよ", result[0].title)
        assertTrue(result[0].body.contains("（後書き）"))
        assertTrue(result[0].body.contains("あとがき本文"))
    }

    @Test fun pdfTrailingForewordMarkerFolds() {
        val chapters = listOf(
            chap("クソゲーマニア、神ゲーに挑まんとす（前書き）", "まえがき本文"),
            chap("クソゲーマニア、神ゲーに挑まんとす", "本編"),
        )
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(1, result.size)
        assertTrue(result[0].body.contains("（前書き）"))
        assertTrue(result[0].body.contains("まえがき本文"))
        assertTrue(result[0].body.contains("本編"))
    }

    @Test fun pdfTrailingNonMarkerParenthesisIsNormalChapter() {
        // 「末尾が全角括弧なら構造マーカー」への安易な一般化を封じる。
        // 同 corpus に実在する括弧付きの話題（「１２９　罪人の独白（前編）」「（後編）」）は通常章。
        val chapters = listOf(
            chap("１２９　罪人の独白（前編）", "本文一"),
            chap("１３０　罪人の独白（後編）", "本文二"),
        )
        val result = ChapterProcessor.processForewordAfterword(chapters)
        assertEquals(2, result.size)
        assertEquals("１２９　罪人の独白（前編）", result[0].title)
        assertEquals("１３０　罪人の独白（後編）", result[1].title)
    }

    // ── (b) 著者記述タイトル（Web 取込）はマーカー判定自体を掛けない ──

    @Test fun authorWrittenTitlesAreNeverFolded() {
        // 形がマーカーと完全に一致しても、出自が著者記述なら畳み込まない。
        // Web 経路の前後書きは GenericSiteAdapter が HTML パース時点で除外済み＝ここで畳む対象は
        // 構造上存在せず、判定を掛ける利得がゼロで誤爆＝章消失の損失だけがある。
        val chapters = listOf(
            chap("第一話", "本文一"),
            chap("例えばこんな……あとがき（後書き）", "本文二"),
            chap("後書き", "本文三"),
        )
        val result = ChapterProcessor.processForewordAfterword(chapters, ChapterTitleSource.AUTHOR_WRITTEN)
        assertEquals(3, result.size)
        assertEquals("後書き", result[2].title)
        assertTrue(result[2].body.contains("本文三"))
        assertFalse(result[0].body.contains("本文二"))
    }

    @Test fun authorWrittenLeadingMarkerTitleKeepsItsBody() {
        // 先頭章がマーカー形でも本文ごと破棄されない（旧・部分一致の最悪ケース＝第1話が丸ごと消える）
        val result = ChapterProcessor.processForewordAfterword(
            listOf(chap("前書き", "唯一の本文")), ChapterTitleSource.AUTHOR_WRITTEN,
        )
        assertEquals(1, result.size)
        assertEquals("前書き", result[0].title)
        assertTrue(result[0].body.contains("唯一の本文"))
    }

    @Test fun rubyMarkerDoesNotSwallowAcrossLines() {
        // ⚠️ ルビ記法は行内に閉じる。章本文は行を "\n" で連結した1本の文字列なので、
        // 親文字の文字クラスが改行を許すと「ルビでない縦線」1つで次の《…》までの全文が
        // 1つの <ruby> へ潰れる（実測で潰れ得た最大は 2,886 字・改行 88 本）。
        // 既存の半角ルビの挙動は変わらない（corpus 23,017 件のうち改行を含むものは 0 件）。
        val result = ChapterProcessor.processForewordAfterword(
            listOf(chap("第一話", "|区切りだけの行", "普通の行", "本文|親《よみ》")),
        )
        val body = result[0].body
        assertTrue(body.contains("<ruby>親<rt>よみ</rt></ruby>"))
        // 飲み込まれていない＝間の行が本文として残っている
        assertTrue(body.contains("|区切りだけの行"))
        assertTrue(body.contains("普通の行"))
    }
}
