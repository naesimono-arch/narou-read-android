package com.novelreader.domain

import com.novelreader.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShelfItems.kt の「並び順キー生成」と「取込済み ncode 集合」の純関数に対する契約テスト。
 *
 * 対象（いずれも従来 mergeShelfItems / activeWebNovels 経由の間接検証しか無かった関数）:
 * - [recencyKeyOf]: 蔵書・Web由来カード共通の並び順キー（単一タイムライン＝触った本は lastReadAt・
 *   未読/未接触は addedAt の降順。2026-09-01 に旧 webRecencyKeyOf と統合）
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
    // recencyKeyOf（蔵書・Web由来カード共通。単一タイムライン）
    // ============================================================

    @Test
    fun `recencyKeyOf - 触った (lastReadAt gt 0) は lastReadAt が value になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 500L)
        assertEquals(500L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt の最小正数境界 (1L) で addedAt は無視される`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 1L)
        assertEquals(1L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt が Long MAX_VALUE でも正しく value になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, key.value)
    }

    @Test
    fun `recencyKeyOf - 未読・未接触 (lastReadAt eq 0) は addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 1000L, lastReadAt = 0L)
        assertEquals(1000L, key.value)
    }

    @Test
    fun `recencyKeyOf - 負の lastReadAt (-1L) は未読扱いとなり addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 2000L, lastReadAt = -1L)
        assertEquals(2000L, key.value)
    }

    @Test
    fun `recencyKeyOf - lastReadAt が Long MIN_VALUE でも addedAt が value になる`() {
        val key = recencyKeyOf(addedAt = 2000L, lastReadAt = Long.MIN_VALUE)
        assertEquals(2000L, key.value)
    }

    @Test
    fun `recencyKeyOf - addedAt の極端な値 (0, Long MAX_VALUE, 負数) が未読時にそのまま value に反映される`() {
        assertEquals(RecencyKey(0L), recencyKeyOf(addedAt = 0L, lastReadAt = 0L))
        assertEquals(RecencyKey(Long.MAX_VALUE), recencyKeyOf(addedAt = Long.MAX_VALUE, lastReadAt = 0L))
        assertEquals(RecencyKey(-500L), recencyKeyOf(addedAt = -500L, lastReadAt = 0L))
    }

    @Test
    fun `recencyKeyOf - 蔵書にもWeb由来カードにも同一関数として使える（2026-09-01 統合の契約）`() {
        // 単一タイムラインでは蔵書とWebカードを区別する特別扱いが無い＝同じ引数なら同じキーになる。
        assertEquals(recencyKeyOf(addedAt = 1000L, lastReadAt = 500L), recencyKeyOf(addedAt = 1000L, lastReadAt = 500L))
        assertEquals(recencyKeyOf(addedAt = 3000L, lastReadAt = 0L), recencyKeyOf(addedAt = 3000L, lastReadAt = 0L))
    }

    // ============================================================
    // RecencyKey.compareTo
    // ============================================================

    @Test
    fun `RecencyKey の compareTo - value の大小のみで比較される`() {
        val small = RecencyKey(100L)
        val large = RecencyKey(200L)

        assertTrue(small < large)
        assertTrue(large > small)

        // 同値は 0＝mergeShelfItems の「同値キーは蔵書優先（>= で book を先に置く）」が成立する前提。
        assertEquals(0, RecencyKey(100L).compareTo(RecencyKey(100L)))
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
