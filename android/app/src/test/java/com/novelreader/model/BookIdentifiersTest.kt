package com.novelreader.model

import com.novelreader.narou.model.Ncode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 識別子 value class 群（BookIdentifiers.kt の BookId/ChapterFilename と、対で導入された
 * narou.model.Ncode）の**現行挙動の固定**。
 *
 * 目的: 後続で予定されている「ncode 型化」リファクタの安全網。現行の各型は意図的に
 * 「正規化・検証を一切持たない素通し」（大文字小文字・前後空白・不正形式をそのまま保持し、
 * equals は下地 String に委譲＝正規化は narouWorkUrl 等の用途サイトごとに分散）であり、
 * リファクタで型側へ正規化/検証を集約するなら本テストが**意図的に**落ちて差分を可視化する。
 * ＝落ちたら「挙動変更を選んだ」証跡としてテストを更新すること（黙って通る変更は正規化漏れの疑い）。
 */
class BookIdentifiersTest {

    @Test
    fun ncode_caseIsPreservedAndSignificant() {
        // 大文字小文字: 正規化されず保持され、等価判定でも区別される（現行＝素通し）。
        assertEquals("N1234AB", Ncode("N1234AB").value)
        assertNotEquals(Ncode("N1234AB"), Ncode("n1234ab"))
        assertEquals(Ncode("n1234ab"), Ncode("n1234ab"))
    }

    @Test
    fun ncode_whitespaceIsPreserved() {
        // 前後空白: trim されずそのまま保持される（trim は用途サイト側の責務のまま）。
        assertEquals(" n1234ab ", Ncode(" n1234ab ").value)
        assertNotEquals(Ncode(" n1234ab "), Ncode("n1234ab"))
    }

    @Test
    fun ncode_invalidFormIsAcceptedAsIs() {
        // 不正形式: 型は検証を持たず何でも包める（弾くのは isValidNcode 等の呼び出し側）。
        assertEquals("not-an-ncode", Ncode("not-an-ncode").value)
        assertEquals("", Ncode("").value)
    }

    @Test
    fun bookId_passThroughAndStringEquality() {
        // UUID 先頭8桁トークンをそのまま包む（正規化の余地なし＝KDoc 明記）。equals は下地 String 委譲。
        assertEquals("a1b2c3d4", BookId("a1b2c3d4").value)
        assertEquals(BookId("a1b2c3d4"), BookId("a1b2c3d4"))
        assertNotEquals(BookId("a1b2c3d4"), BookId("A1B2C3D4"))
    }

    @Test
    fun chapterFilename_passThroughAndStringEquality() {
        // 「index.html か否か」等の既存等価判定にそのまま使われるため素通し（検証追加は既存判定を変えうる）。
        assertEquals("chap_1.html", ChapterFilename("chap_1.html").value)
        assertEquals(ChapterFilename("index.html"), ChapterFilename("index.html"))
        assertNotEquals(ChapterFilename("index.html"), ChapterFilename("chap_1.html"))
    }

    @Test
    fun distinctIdentifierTypes_neverEqualEvenWithSameUnderlyingString() {
        // 型分離の動機そのもの: 同じ下地 String でも別型なら等価にならない（引数取り違え防止の担保）。
        val raw = "same-string"
        assertNotEquals(BookId(raw) as Any, ChapterFilename(raw) as Any)
        assertNotEquals(BookId(raw) as Any, Ncode(raw) as Any)
    }
}
