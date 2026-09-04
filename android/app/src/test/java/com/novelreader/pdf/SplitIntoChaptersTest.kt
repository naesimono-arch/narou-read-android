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

    @Test fun noTitleMarkersWithFallbackUsesFallbackTitle() {
        // 単話（マーカー皆無）＋fallback指定 → 単一章タイトルは fallback（表紙由来の作品タイトル相当）
        val result = ChapterProcessor.splitIntoChapters(listOf("本文A", "本文B"), "作品タイトルX")
        assertEquals(1, result.size)
        assertEquals("作品タイトルX", result[0].title)
        assertEquals(listOf("本文A", "本文B"), result[0].body)
    }

    @Test fun noTitleMarkersWithoutFallbackKeepsDefaultTitle() {
        // マーカー皆無＋引数省略 → 従来どおり既定タイトル（既存挙動不変）
        val result = ChapterProcessor.splitIntoChapters(listOf("本文A", "本文B"))
        assertEquals(1, result.size)
        assertEquals("作品情報・プロローグ", result[0].title)
    }

    @Test fun withTitleMarkerFallbackDoesNotAffectLeadingBody() {
        // マーカー有り＋fallback指定 → 先頭本文群は従来どおり「作品情報・プロローグ」章（fallback は無効）
        val paragraphs = listOf("先頭本文", "【題名】第一話", "本文1")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        assertEquals(2, result.size)
        assertEquals("作品情報・プロローグ", result[0].title)
        assertEquals(listOf("先頭本文"), result[0].body)
        assertEquals("第一話", result[1].title)
    }

    // ---- 単話（実在の章見出しゼロ）× 前書き/後書き見出し =====================================
    // 実データ由来: なろうの縦書きPDF は単話でも前書き/後書きブロックに Bold 見出しを打ち、
    // 話タイトルが空なので裸の「（前書き）」「（後書き）」になる（実測 N0089HK・N7668GF）。
    // 旧実装は【題名】の総数で単話を判定していたため、この2つを章見出しと数えて判定が外れていた。

    @Test fun singleEpisodeWithAfterwordUsesFallbackTitle() {
        // 後書きのみの単話＝実機症状。本文章が「作品情報・プロローグ」に化けないこと。
        val paragraphs = listOf("本文A", "本文B", "【題名】（後書き）", "後書き本文")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        assertEquals(2, result.size)
        assertEquals("作品タイトルX", result[0].title)
        assertEquals(listOf("本文A", "本文B"), result[0].body)
        assertEquals("（後書き）", result[1].title)
    }

    @Test fun singleEpisodeWithAfterwordEndsAsOneChapterWithWorkTitle() {
        // 通し（後処理まで）: 後書きは本文章の末尾へ畳まれ、単一章のタイトルは作品タイトルのまま。
        val paragraphs = listOf("本文A", "【題名】（後書き）", "後書き本文")
        val final = ChapterProcessor.processForewordAfterword(
            ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX"),
        )
        assertEquals(1, final.size)
        assertEquals("作品タイトルX", final[0].title)
        assertTrue(final[0].body.contains("本文A"))
        assertTrue(final[0].body.contains("後書き本文"))
    }

    @Test fun singleEpisodeWithForewordDoesNotSplitAndKeepsAllBody() {
        // 前書き付き単話は、旧実装だと前書き以降が丸ごと畳み込み先不在で消えていた（章数 0）。
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "本文A", "【題名】（後書き）", "後書き本文")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        assertEquals(2, result.size)
        assertEquals("作品タイトルX", result[0].title)
        assertEquals(listOf("前書き本文", "本文A"), result[0].body)
        assertEquals("（後書き）", result[1].title)
    }

    @Test fun singleEpisodeWithForewordSurvivesForewordAfterwordProcessing() {
        // 通し: 章数 0（本文全損）にならず、作品タイトルの単一章に全段落が残ること。
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "本文A", "【題名】（後書き）", "後書き本文")
        val final = ChapterProcessor.processForewordAfterword(
            ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX"),
        )
        assertEquals(1, final.size)
        assertEquals("作品タイトルX", final[0].title)
        assertTrue(final[0].body.contains("前書き本文"))
        assertTrue(final[0].body.contains("本文A"))
        assertTrue(final[0].body.contains("後書き本文"))
    }

    @Test fun serialWorkWithStructuralMarkersKeepsLegacyLeadingChapter() {
        // 連載（実在の章見出しあり）＝構造マーカーが混ざっても従来どおり。先頭本文群は
        // 「作品情報・プロローグ」章のまま＝fallback は無効（単話判定の訂正が連載へ漏れない保証）。
        val paragraphs = listOf(
            "先頭本文", "【題名】第一話（前書き）", "前書き本文", "【題名】第一話", "本文1",
        )
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        assertEquals(3, result.size)
        assertEquals("作品情報・プロローグ", result[0].title)
        assertEquals("第一話（前書き）", result[1].title)
        assertEquals("第一話", result[2].title)
    }

    // ---- 単話の前書き終端（blockStarts）＝版面由来のブロック境界を1箇所だけ使う ----

    @Test fun forewordIsBoxedWhenBlockBoundaryIsKnown() {
        // 添字: 0=前書き見出し 1=前書き本文 2,3=版面の余り由来の空行 4=本文 5=後書き見出し 6=後書き本文
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "", "", "本文A", "【題名】（後書き）", "後書き本文")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", setOf(4))
        assertEquals(3, result.size)
        assertEquals("（前書き）", result[0].title)
        // 末尾の空行（版面の余り）は落とし、作者の本文だけを残す
        assertEquals(listOf("前書き本文"), result[0].body)
        assertEquals("作品タイトルX", result[1].title)
        assertEquals(listOf("本文A"), result[1].body)
        assertEquals("（後書き）", result[2].title)
    }

    @Test fun forewordBoxLooksTheSameAsSerialAfterProcessing() {
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "", "本文A", "【題名】（後書き）", "後書き本文")
        val final = ChapterProcessor.processForewordAfterword(
            ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", setOf(3)),
        )
        assertEquals(1, final.size)
        assertEquals("作品タイトルX", final[0].title)
        // 連載と同じ装飾（前書きは冒頭のボックス＋<hr>、後書きは末尾）＝単話だけ別の見えにしない
        assertTrue(final[0].body.startsWith("<div"))
        assertTrue(final[0].body.contains("<b>（前書き）</b>"))
        assertTrue(final[0].body.contains("<b>（後書き）</b>"))
        assertTrue(final[0].body.contains("前書き本文"))
        assertTrue(final[0].body.contains("本文A"))
    }

    @Test fun withoutBlockBoundaryFallsBackToNotSplitting() {
        // 未観測ケース（前書きが1ページを満たして次ページへ続く等）でブロック境界が拾えないとき。
        // 枠は付かないが、本文も順序も落とさない側へ倒す。
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "本文A", "【題名】（後書き）", "後書き本文")
        val final = ChapterProcessor.processForewordAfterword(
            ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", emptySet()),
        )
        assertEquals(1, final.size)
        assertEquals("作品タイトルX", final[0].title)
        assertTrue(final[0].body.contains("前書き本文"))
        assertTrue(final[0].body.contains("本文A"))
        assertTrue(final[0].body.contains("後書き本文"))
    }

    @Test fun blockBoundaryPointingAtHeadingOnlyTailIsIgnored() {
        // 境界の先に実体のある本文が無い＝切ると前書きの畳み込み先が生まれず本文が消える。切らない。
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "【題名】（後書き）", "後書き本文")
        val final = ChapterProcessor.processForewordAfterword(
            ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", setOf(2)),
        )
        assertEquals(1, final.size)
        assertTrue(final[0].body.contains("前書き本文"))
        assertTrue(final[0].body.contains("後書き本文"))
    }

    @Test fun serialWorkIgnoresBlockStartsEntirely() {
        // ⚠️ 狭めた条件の封鎖: 実在の章見出しが在れば blockStarts は一切参照されない。
        // 将来この適用範囲をうっかり広げたら、このテストが赤くなる。
        val paragraphs = listOf("先頭本文", "【題名】第一話", "本文1", "", "", "本文2")
        val without = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        val with = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", setOf(2, 3, 5))
        assertEquals(without.map { it.title }, with.map { it.title })
        assertEquals(without.map { it.body }, with.map { it.body })
    }

    @Test fun afterwordSubstringInChapterTitleIsSplit() {
        // タイトルに「後書き」を含む話も通常章として分離される
        val paragraphs = listOf("【題名】第一話", "本文1", "【題名】第五話　後書きの話", "本文2")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(2, result.size)
        assertEquals("第五話　後書きの話", result[1].title)
        assertTrue(!result[0].body.contains("第五話　後書きの話"))
    }

    // ── 挿絵記法（なろう／みてみん `＜iコード｜ユーザID＞`）の除去 ──
    // 実機症状（2026-09-05）: 極小作品の本文冒頭に `＜ｉ３４９８１３｜２７５４９＞` が文字として出ていた。
    // 実測の形は全角のみ（生成器が半角英数を全角へ倒すため）で、単独行 118 件・行末インライン 8 件。

    @Test fun illustrationTagOnItsOwnLineBecomesBlankLineNotDroppedLine() {
        // 単独行の挿絵は「行は残し中身だけ落とす」＝原文の行構造（ADR 0041 決定2）に一致させる。
        // 行ごと消さないことは段落添字の不変にも効く（blockStarts の添字体系が動かない）。
        val paragraphs = listOf("＜ｉ３４９８１３｜２７５４９＞", "　本文A")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX")
        assertEquals(listOf("", "　本文A"), result[0].body)
    }

    @Test fun illustrationTagInlineKeepsSurroundingText() {
        // 行末インライン形（実測: 後書きの告知行）。前後の地の文は1字も落とさない。
        val paragraphs = listOf("コミカライズ版が好評発売中です！＜ｉ６３６９１６｜２２９５１＞")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(listOf("コミカライズ版が好評発売中です！"), result[0].body)
    }

    @Test fun illustrationTagHalfWidthFormIsAlsoStripped() {
        // 生成器は半角英数を全角へ倒すので実測は全角のみ。ヘルプ helppageid/44 の正式書式は半角
        // （`<i3724|23>`）なので、生成器が変わっても素通しへ戻らないよう半角形も受ける。
        val result = ChapterProcessor.splitIntoChapters(listOf("前<i3724|23>後"))
        assertEquals(listOf("前後"), result[0].body)
    }

    @Test fun angleBracketsInProseAreNotStripped() {
        // ⚠️ 誤爆の封鎖: `＜…＞` は地の文の括弧としても使われる（sample_pdfs 全12本で挿絵タグ以外に15件）。
        // `ｉ`＋数字・数字という形にアンカーしているので、括弧ごと本文を飲むことはない。
        val paragraphs = listOf("＜第一報＞が届いた", "＜２７５４９｜３４９８１３＞", "＜ｉあ｜い＞")
        val result = ChapterProcessor.splitIntoChapters(paragraphs)
        assertEquals(paragraphs, result[0].body)
    }

    @Test fun illustrationTagDoesNotShiftStructuralDecisions() {
        // 記法の除去が構造の判定へ染み出さないことの封鎖。挿絵だけの行が空行に変わっても、
        // 前書き終端（blockStarts は**素の**段落列に対して判定される）の結論は挿絵の有無で動かない。
        // ＝章の数・タイトル・各章の行数が、挿絵行を地の文に置き換えた場合と一致する。
        val withTag = listOf("【題名】（前書き）", "前書き本文", "＜ｉ１｜２＞", "前書き続き", "　本文A")
        val withProse = listOf("【題名】（前書き）", "前書き本文", "地の文", "前書き続き", "　本文A")
        val a = ChapterProcessor.splitIntoChapters(withTag, "作品タイトルX", setOf(4))
        val b = ChapterProcessor.splitIntoChapters(withProse, "作品タイトルX", setOf(4))
        assertEquals(b.map { it.title }, a.map { it.title })
        assertEquals(b.map { it.body.size }, a.map { it.body.size })
        assertEquals(listOf("前書き本文", "", "前書き続き"), a[0].body)
    }

    @Test fun illustrationAtForewordBlockEndIsAbsorbedByExistingTrailingBlankTrim() {
        // ⚠️ 唯一の相互作用として記録する（隠さない）: 前書きブロックの**最終行**が挿絵だけの行だと、
        // 除去後の空行が既存の「ブロック末尾の空行は版面の余り＝落とす」処理に吸われて行ごと消える。
        // 版面の余りと区別する材料はこの層に無く、落とす向きは既存仕様と同じ（＝新しい判断を足さない）。
        val paragraphs = listOf("【題名】（前書き）", "前書き本文", "＜ｉ１｜２＞", "　本文A")
        val result = ChapterProcessor.splitIntoChapters(paragraphs, "作品タイトルX", setOf(3))
        assertEquals(listOf("前書き本文"), result[0].body)
    }

    // ── 全角縦線のルビ記法（なろうは半角/全角どちらも正式＝ヘルプ helppageid/42）──
    // 実測: corpus 12 本の全角縦線は挿絵タグを除き 8 件で、ルビはそのうち 1 件だけ。
    // 残り 7 件は地の文の区切り 3 件と「（）をルビにしない」打ち消し記法 4 件＝触ってはいけない側。

    @Test fun fullWidthRubyMarkerIsNormalizedToAscii() {
        val result = ChapterProcessor.splitIntoChapters(listOf("こいつの｜未練《ねがい》は"))
        assertEquals(listOf("こいつの|未練《ねがい》は"), result[0].body)
    }

    @Test fun fullWidthPipeWithoutReadingIsLeftAlone() {
        // ⚠️ 誤爆の封鎖その1: 地の文の区切りとしての全角縦線（実測 N3957FQ に 3 件）。
        // 同じ行に《…》が続かないので寄せない＝著者の文字がそのまま残る。
        val paragraphs = listOf("地形的には【廃棄場｜スラム街｜都市】な感じだ")
        assertEquals(paragraphs, ChapterProcessor.splitIntoChapters(paragraphs)[0].body)
    }

    @Test fun fullWidthPipeBeforeParenthesisIsLeftAlone() {
        // ⚠️ 誤爆の封鎖その2: なろうの「（）をルビにしない」打ち消し記法（実測 N6169DZ に 4 件）。
        // 縦線を一律 ASCII へ寄せると、この 4 件まで巻き込んでルビ変換の入力にしてしまう。
        val paragraphs = listOf("｜触手（手足）にカジキの頭が生えている")
        assertEquals(paragraphs, ChapterProcessor.splitIntoChapters(paragraphs)[0].body)
    }

    @Test fun fullWidthRubyNormalizationDoesNotReachAcrossLines() {
        // 正規化は本文1行ずつに掛かる＝別の行の《…》とは結び付かない。
        // これが崩れると、ルビでない縦線1つで次の《…》までの全文が1つのルビへ潰れる
        // （行境界を外した場合に潰れ得た量の実測は RUBY_PATTERN の注記）。
        val paragraphs = listOf("｜区切り", "普通の行", "地の文の《引用》です")
        assertEquals(paragraphs, ChapterProcessor.splitIntoChapters(paragraphs)[0].body)
    }

    @Test fun fullWidthRubyReachesRubyTagAfterProcessing() {
        // 経路の通し確認: 全角で書かれたルビが最終的に <ruby> まで届く（記法のまま出ない）。
        val chapters = ChapterProcessor.splitIntoChapters(listOf("【題名】第一話", "こいつの｜未練《ねがい》は"))
        val processed = ChapterProcessor.processForewordAfterword(chapters)
        assertTrue(processed[0].body.contains("<ruby>未練<rt>ねがい</rt></ruby>"))
        assertTrue(!processed[0].body.contains("｜"))
    }
}
