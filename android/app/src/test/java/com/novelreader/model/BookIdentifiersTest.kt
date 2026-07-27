package com.novelreader.model

import com.novelreader.narou.model.Ncode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 識別子 value class 群（BookIdentifiers.kt の BookId/ChapterFilename と、対で導入された
 * narou.model.Ncode）の**現行挙動の固定**。
 *
 * 目的: 「ncode 型化」リファクタの安全網。各型のコンストラクタ/equals は意図的に
 * 「正規化・検証を一切持たない素通し」（大文字小文字・前後空白・不正形式をそのまま保持し、
 * equals は下地 String に委譲）。2026-07-27 の型化で用途別正規化は Ncode の**アクセサ**
 * （storageKey/urlSlug/apiParam/sameWorkAs＝加算のみ）へ集約されたが、生成経路と等価性は
 * 従来どおり＝素通し固定テストは生きたまま。落ちたら「挙動変更を選んだ」証跡としてテストを
 * 更新すること（黙って通る変更は正規化漏れの疑い）。アクセサの挙動固定は本クラス後半。
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

    // ---- ここから Ncode 用途別アクセサ（2026-07-27 型化）の挙動固定 ----

    @Test
    fun ncode_storageKey_trimsAndUppercases() {
        // 保存キー形: trim＋大文字（NcodeLinkSheet 由来の保存正規化と同一）。元の value は不変。
        assertEquals("N1234AB", Ncode(" n1234ab ").storageKey)
        assertEquals("N1234AB", Ncode("N1234AB").storageKey)
        assertEquals(" n1234ab ", Ncode(" n1234ab ").value)
        // 不正形式・空も検証せず機械的に変換（弾くのは isValidNcode 等の呼び出し側のまま）。
        assertEquals("NOT-AN-NCODE", Ncode(" not-an-ncode ").storageKey)
        assertEquals("", Ncode("  ").storageKey)
    }

    @Test
    fun ncode_urlSlug_trimsAndLowercases() {
        // URL スラッグ形: trim＋小文字（narouWorkUrl/narouEpisodeUrl の URL パス正規化と同一）。
        assertEquals("n1234ab", Ncode(" N1234AB ").urlSlug)
        assertEquals("n1234ab", Ncode("n1234ab").urlSlug)
    }

    @Test
    fun ncode_apiParam_trimsOnlyAndPreservesCase() {
        // API パラメータ形: trim のみ（なろう API は大小無視のため case を保持して送る）。
        assertEquals("N1234ab", Ncode(" N1234ab ").apiParam)
        assertEquals("", Ncode("   ").apiParam)
    }

    @Test
    fun ncode_sameWorkAs_ignoresWhitespaceAndCase_butEqualsStaysStrict() {
        // 表記ゆれ無視の同一作品判定（trim+ignoreCase）。equals の厳密性（素通し）はそのまま。
        assertEquals(true, Ncode(" n1234ab ").sameWorkAs(Ncode("N1234AB")))
        assertEquals(false, Ncode("n1234ab").sameWorkAs(Ncode("n9999zz")))
        assertNotEquals(Ncode(" n1234ab "), Ncode("N1234AB"))
    }

    @Test
    fun ncode_normalizedForStorage_wrapsStorageKeyAsValue() {
        // 境界ファクトリ: 値そのものが保存キー形に正規化された Ncode を作る（linkNcode が素通し永続化するため）。
        assertEquals(Ncode("N1234AB"), Ncode.normalizedForStorage(" n1234ab "))
        assertEquals("N1234AB", Ncode.normalizedForStorage(" n1234ab ").value)
    }

    @Test
    fun distinctIdentifierTypes_neverEqualEvenWithSameUnderlyingString() {
        // 型分離の動機そのもの: 同じ下地 String でも別型なら等価にならない（引数取り違え防止の担保）。
        val raw = "same-string"
        assertNotEquals(BookId(raw) as Any, ChapterFilename(raw) as Any)
        assertNotEquals(BookId(raw) as Any, Ncode(raw) as Any)
    }
}
