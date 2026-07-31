package com.novelreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 章題の話数ラベル分離（[splitChapterTitle]）の契約。
 *
 * 実データの出所: golden 回帰コーパス `ab-review/golden_regression` 配下の各 `.pdf.json` の `chapter_titles`
 * （KDoc にワイルドカードのパスを書かないのは Kotlin の入れ子ブロックコメントでビルドが落ちるため＝
 * `docs/knowledge/kotlin-nested-block-comment-breaks-kdoc.md`）
 * （N1453LW=`０１．…` 形／N2959KI=`１　…` 形／N6169DZ・N5368ML=ラベル無し）。
 * 期待値は「実蔵書に在る形だけを確実に切り、曖昧な形は切らない」＝**題を削る誤爆より現状維持を選ぶ**。
 */
class ChapterTitleTest {

    // ---- 形A: 助数詞つき ----

    @Test
    fun `第N話 は区切りがあれば話数と題に割れる`() {
        val parts = splitChapterTitle("第127話 雨上がりの城門にて")
        assertEquals(127, parts.episodeNumber)
        assertEquals("雨上がりの城門にて", parts.body)
        assertEquals("第127話 ", parts.rawLabel)
    }

    @Test
    fun `助数詞のあとに区切りが無ければ切らない`() {
        // 「12話目の冒険」を `12話`＋`目の冒険` に割らない（題を削る誤爆の防止）。
        val parts = splitChapterTitle("12話目の冒険")
        assertNull(parts.episodeNumber)
        assertEquals("12話目の冒険", parts.body)
        assertNull(parts.rawLabel)
    }

    // ---- 形B: 数字＋約物（実蔵書 N1453LW） ----

    @Test
    fun `全角数字と読点区切りの実データが割れる`() {
        val parts = splitChapterTitle("０１．婚約の継続をされたいのですか？")
        assertEquals(1, parts.episodeNumber)
        assertEquals("婚約の継続をされたいのですか？", parts.body)
        assertEquals("０１．", parts.rawLabel)
    }

    @Test
    fun `小数や章節番号は割らない`() {
        // 「2.5次元」を `2.`＋`5次元…` にしない（約物の直後が数字なら切らない）。
        val parts = splitChapterTitle("2.5次元の彼女")
        assertNull(parts.episodeNumber)
        assertEquals("2.5次元の彼女", parts.body)
    }

    // ---- 形C: 数字＋空白（曖昧・目次順の照合が要る。実蔵書 N2959KI） ----

    @Test
    fun `数字と空白だけの形は目次順と一致したときだけ割れる`() {
        val ambiguous = "１　嵐の夕暮れ"
        // 照合材料あり・一致 → 割る
        val matched = splitChapterTitle(ambiguous, expectedNumber = 1)
        assertEquals(1, matched.episodeNumber)
        assertEquals("嵐の夕暮れ", matched.body)
        // 照合材料なし → 曖昧なので割らない（原文のまま）
        assertNull(splitChapterTitle(ambiguous).episodeNumber)
        assertEquals(ambiguous, splitChapterTitle(ambiguous).body)
        // 照合材料あり・不一致 → 題の一部かもしれないので割らない
        assertNull(splitChapterTitle(ambiguous, expectedNumber = 7).episodeNumber)
    }

    @Test
    fun `目次順と食い違う数字始まりの題は削られない`() {
        // 第3話の題が「100 万回生きた猫」でも `100` をラベルとして削らない。
        val parts = splitChapterTitle("100　万回生きた猫", expectedNumber = 3)
        assertNull(parts.episodeNumber)
        assertEquals("100　万回生きた猫", parts.body)
    }

    // ---- ラベルを持たない実データ ----

    @Test
    fun `ラベルの無い題は原文のまま返る`() {
        for (raw in listOf(
            "貴方はなんのためにゲームをしますか？",                       // N6169DZ
            "「僕はアナベルと結婚するから。君は成り上がりの騎士団長へ嫁ぎなよ」", // N5368ML
            "やせいの　へんたいが　とびだした　！",                       // N6169DZ（先頭が数字でない）
            "シャングリラ・フロンティア〜クソゲーハンター、神ゲーに挑まんとす〜",
        )) {
            val parts = splitChapterTitle(raw, expectedNumber = 1)
            assertNull("ラベル無し題を割ってはいけない: $raw", parts.episodeNumber)
            assertEquals(raw, parts.body)
        }
    }

    @Test
    fun `漢数字は対象外（題の先頭語と区別できないため切らない）`() {
        val parts = splitChapterTitle("第一話　始まりの朝", expectedNumber = 1)
        assertNull(parts.episodeNumber)
        assertEquals("第一話　始まりの朝", parts.body)
    }

    // ---- 情報を失わない防御 ----

    @Test
    fun `ラベルだけで題が空になる場合は切らない`() {
        // 見出しから題が消えて「話数しか出ない章」になる方が情報を失う。
        val parts = splitChapterTitle("第12話　")
        assertNull(parts.episodeNumber)
        assertEquals("第12話　", parts.body)
    }

    @Test
    fun `異常に長い数字列は話数として捏造しない`() {
        val raw = "99999999999999．謎の暗号"
        val parts = splitChapterTitle(raw)
        assertNull(parts.episodeNumber)
        assertEquals(raw, parts.body)
    }
}
