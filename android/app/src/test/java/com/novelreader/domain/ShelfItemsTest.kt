package com.novelreader.domain

import com.novelreader.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShelfItems.kt の「並び順キー生成」と「取込済み ncode 集合」の純関数に対する契約テスト。
 *
 * 対象（いずれも従来 mergeShelfItems / activeWebNovels 経由の間接検証しか無かった関数）:
 * - [recencyKeyOf]: 蔵書の並び順キー（未読=tier1/addedAt・触った本=tier0/lastReadAt）
 * - [webRecencyKeyOf]: Web由来カードの並び順キー（tier 特権なし＝常に tier0）
 * - [importedNcodeKeys]: 蔵書内の ncode 保存キー集合（trim+大文字の正規化＋重複除去）
 *
 * なぜ com.novelreader.viewmodel.ShelfItemsTest と分けて domain パッケージに置くか:
 * テスト対象 ShelfItems.kt が com.novelreader.domain に居るため、原則どおり実装と同じ package に置く
 * （既存の viewmodel 側テストは 2026-07-27 の domain 切り出し前から在るもので、移設は本テストの範囲外）。
 * 単純名が同じ ShelfItemsTest でも package が異なれば FQCN は別＝JVM・JUnit・Gradle いずれも衝突しない。
 * 併存する2ファイルの役割分担: こちらは「キー生成の式そのもの」、viewmodel 側は「マージ・フィルタ・集計の
 * 振る舞い」。並び順バグは式とマージ比較のどちらでも起こり得るため、両者を分けて直接固定する。
 */
class ShelfItemsTest {

    // ============================================================
    // recencyKeyOf
    // ============================================================

    @Test
    fun `recencyKeyOf - 触った本 (lastReadAt gt 0) は tier0 で lastReadAt が value になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 500L)
        assertEquals(0, key.tier)
        assertEquals(500L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt の最小正数境界 (1L) で tier0 に切り替わり addedAt は無視される`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 1L)
        assertEquals(0, key.tier)
        assertEquals(1L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt が Long MAX_VALUE でも正しく tier0 になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = Long.MAX_VALUE)
        assertEquals(0, key.tier)
        assertEquals(Long.MAX_VALUE, key.value)
    }

    @Test
    fun `recencyKeyOf - 未読 (lastReadAt eq 0) は tier1 で addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 0L)
        assertEquals(1, key.tier)
        assertEquals(1000L, key.value)
    }

    @Test
    fun `recencyKeyOf - 負の lastReadAt (-1L) は未読扱いとなり tier1 で addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 2000L, lastReadAt = -1L)
        assertEquals(1, key.tier)
        assertEquals(2000L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt が Long MIN_VALUE でも tier1 で addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 2000L, lastReadAt = Long.MIN_VALUE)
        assertEquals(1, key.tier)
        assertEquals(2000L, key.value)
    }

    @Test
    fun `recencyKeyOf - addedAt の極端な値 (0, Long MAX_VALUE, 負数) が未読時にそのまま value に反映される`() {
        assertEquals(RecencyKey(1, 0L), recencyKeyOf(addedAt = 0L, lastReadAt = 0L))
        assertEquals(RecencyKey(1, Long.MAX_VALUE), recencyKeyOf(addedAt = Long.MAX_VALUE, lastReadAt = 0L))
        assertEquals(RecencyKey(1, -500L), recencyKeyOf(addedAt = -500L, lastReadAt = 0L))
    }

    // ============================================================
    // RecencyKey.compareTo（2つのキー関数が表現する順序そのものの契約）
    // ============================================================

    @Test
    fun `RecencyKey の compareTo - tier が value に優先し同 tier 内は value 比較・同値は0`() {
        val tier0Small = RecencyKey(0, 100L)
        val tier0Large = RecencyKey(0, 200L)
        val tier1Small = RecencyKey(1, 10L)

        // 同 tier 内は value の大小。
        assertTrue(tier0Small < tier0Large)
        assertTrue(tier0Large > tier0Small)

        // tier が優先＝tier0 の value がどれほど大きくても tier1 を上回れない
        // （compareTo が tier 差で決着し value まで進まない。未接触 web 恒久最上位バグの機序そのもの）。
        assertTrue(tier0Small < tier1Small)
        assertTrue(RecencyKey(0, Long.MAX_VALUE) < tier1Small)

        // 同値は 0＝mergeShelfItems の「同値キーは蔵書優先（>= で book を先に置く）」が成立する前提。
        assertEquals(0, RecencyKey(0, 100L).compareTo(RecencyKey(0, 100L)))
    }

    // ============================================================
    // webRecencyKeyOf
    // ============================================================

    @Test
    fun `webRecencyKeyOf - 触った Web (lastReadAt gt 0) は tier0 で lastReadAt が value になる`() {
        val key = webRecencyKeyOf(addedAt = 1000L, lastReadAt = 500L)
        assertEquals(0, key.tier)
        assertEquals(500L, key.value)
    }

    @Test
    fun `webRecencyKeyOf - lastReadAt の最小正数境界 (1L) で value に 1L が入る`() {
        val key = webRecencyKeyOf(addedAt = 1000L, lastReadAt = 1L)
        assertEquals(0, key.tier)
        assertEquals(1L, key.value)
    }

    @Test
    fun `webRecencyKeyOf - 未接触 (lastReadAt eq 0) でも tier0 のまま value に addedAt が入る`() {
        val key = webRecencyKeyOf(addedAt = 1000L, lastReadAt = 0L)
        assertEquals(0, key.tier)
        assertEquals(1000L, key.value)
    }

    @Test
    fun `webRecencyKeyOf - 負の lastReadAt (-1L や Long MIN_VALUE) も tier0 で value に addedAt が入る`() {
        val keyNeg1 = webRecencyKeyOf(addedAt = 1500L, lastReadAt = -1L)
        assertEquals(0, keyNeg1.tier)
        assertEquals(1500L, keyNeg1.value)

        val keyMin = webRecencyKeyOf(addedAt = 1500L, lastReadAt = Long.MIN_VALUE)
        assertEquals(0, keyMin.tier)
        assertEquals(1500L, keyMin.value)
    }

    @Test
    fun `webRecencyKeyOf と recencyKeyOf の差分契約 - 未読・未接触時の tier 特権の有無`() {
        val addedAt = 3000L
        val lastReadAt = 0L

        val bookKey = recencyKeyOf(addedAt, lastReadAt)
        val webKey = webRecencyKeyOf(addedAt, lastReadAt)

        // 蔵書未読は tier1（特権あり・最上層）
        assertEquals(1, bookKey.tier)
        assertEquals(addedAt, bookKey.value)

        // Web未接触は tier0（特権なし・通常キー）＝2026-07-26 裁定変更の実体をキー生成レベルで固定する。
        assertEquals(0, webKey.tier)
        assertEquals(addedAt, webKey.value)

        // 比較すると未読蔵書 (tier1) の方が未接触 Web (tier0) より大きくなる
        assertTrue(bookKey > webKey)
    }

    // ============================================================
    // importedNcodeKeys
    // ============================================================

    @Test
    fun `importedNcodeKeys - 空リストを入力した場合は空の Set が返る`() {
        val result = importedNcodeKeys(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `importedNcodeKeys - 全ての本の ncode が null の場合は空の Set が返る`() {
        val books = listOf(
            createBook(id = "b1", ncode = null),
            createBook(id = "b2", ncode = null),
        )
        val result = importedNcodeKeys(books)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `importedNcodeKeys - 小文字や前後空白を含む ncode が大文字かつトリムされて Set に入る`() {
        val books = listOf(
            createBook(id = "b1", ncode = "n1234ab"),
            createBook(id = "b2", ncode = "  n5678cd  "),
            createBook(id = "b3", ncode = "N9999ZZ"),
        )
        val result = importedNcodeKeys(books)
        assertEquals(setOf("N1234AB", "N5678CD", "N9999ZZ"), result)
    }

    @Test
    fun `importedNcodeKeys - 表記揺れを含む同一 ncode は重複除去されて1つの正規化キーになる`() {
        val books = listOf(
            createBook(id = "b1", ncode = "n1234ab"),
            createBook(id = "b2", ncode = "N1234AB"),
            createBook(id = "b3", ncode = "  N1234ab  "),
            createBook(id = "b4", ncode = null),
        )
        val result = importedNcodeKeys(books)
        assertEquals(setOf("N1234AB"), result)
    }

    @Test
    fun `importedNcodeKeys - 空文字や空白のみの ncode はトリムされて空文字キーとして入る`() {
        // 現状の実装は空文字キーを弾かない（null だけを落とす）＝現仕様の固定。
        // ここが空 Set になるべきかは isPromotedWeb 側のガード（Ncode("").storageKey は空 web ncode としか
        // 一致しない）に委ねられており、本関数単体では判断材料が無いため現挙動をそのまま契約とする。
        val books = listOf(
            createBook(id = "b1", ncode = ""),
            createBook(id = "b2", ncode = "   "),
        )
        val result = importedNcodeKeys(books)
        assertEquals(setOf(""), result)
    }

    // ---- Helper ----

    private fun createBook(id: String, ncode: String?): BookEntity {
        return BookEntity(
            id = id,
            title = "Test Title $id",
            htmlDirPath = "/tmp/$id",
            ncode = ncode,
        )
    }
}
