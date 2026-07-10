package com.novelreader.viewmodel

import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfItemsTest {

    private fun book(id: String, addedAt: Long, ncode: String? = null) =
        BookEntity(id = id, title = "本$id", htmlDirPath = "/p/$id", addedAt = addedAt, ncode = ncode)

    private fun web(ncode: String, addedAt: Long) =
        WebNovelEntity(ncode = ncode, title = "Web$ncode", writer = "作者", generalAllNo = 10, addedAt = addedAt)

    @Test
    fun `蔵書とWeb由来が最近の活動順で混在する`() {
        // books は DAO 並び（降順）を模す: b1(300) > b2(100)
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))

        val items = mergeShelfItems(books, emptyMap(), webs)

        assertEquals(listOf("book:b1", "web:N1111AA", "book:b2"), items.map { it.key })
    }

    @Test
    fun `進捗の lastReadAt が addedAt より新しければそちらを蔵書のキーにする`() {
        // b1 は追加が古い(100)が直近に読んだ(400)＝Web(300) より上に来る（DAO の MAX 式と同じ判断）
        val books = listOf(book("b1", 100))
        val progress = mapOf("b1" to ProgressEntity("b1", "chap_1.html", lastReadAt = 400))
        val webs = listOf(web("N1111AA", 300))

        val items = mergeShelfItems(books, progress, webs)

        assertEquals(listOf("book:b1", "web:N1111AA"), items.map { it.key })
    }

    @Test
    fun `取込済み ncode の Web カードは非表示になる（自然昇格）`() {
        // 大文字小文字の表記ゆれ（books=小文字・web=大文字）でも一致して落ちること
        val books = listOf(book("b1", 300, ncode = "n1111aa"))
        val webs = listOf(web("N1111AA", 200), web("N2222BB", 100))

        val items = mergeShelfItems(books, emptyMap(), webs)

        assertEquals(listOf("book:b1", "web:N2222BB"), items.map { it.key })
    }

    @Test
    fun `同値キーは蔵書を先に置く`() {
        val books = listOf(book("b1", 200))
        val webs = listOf(web("N1111AA", 200))

        val items = mergeShelfItems(books, emptyMap(), webs)

        assertEquals(listOf("book:b1", "web:N1111AA"), items.map { it.key })
    }

    @Test
    fun `両方空なら空（本棚の空状態判定に使う）`() {
        assertEquals(emptyList<ShelfItem>(), mergeShelfItems(emptyList(), emptyMap(), emptyList()))
    }

    // ────── 機能②: Web カードへ読書位置(lastReadEpisode)を載せる ──────

    @Test
    fun `Web由来カードに読書位置がひも付く（記録あり→続きから表示の前提）`() {
        val webs = listOf(web("N1111AA", 200), web("N2222BB", 100))
        // 記録は正規化済み大文字キー。N1111AA=第52話まで読んだ、N2222BB=未記録。
        val progress = mapOf("N1111AA" to 52)

        val items = mergeShelfItems(emptyList(), emptyMap(), webs, progress)

        val a = items.first { it.key == "web:N1111AA" } as ShelfItem.Web
        val b = items.first { it.key == "web:N2222BB" } as ShelfItem.Web
        assertEquals(52, a.lastReadEpisode)
        assertEquals(0, b.lastReadEpisode)  // 未記録は 0（＝未読でカードは「なろう・未取込」表示）
    }

    @Test
    fun `読書位置を省略すると全Webカードが未読(0)`() {
        // 既定引数の後方互換: 4引数版の既存呼び出しは lastReadEpisode=0 で不変。
        val items = mergeShelfItems(emptyList(), emptyMap(), listOf(web("N1111AA", 200)))
        assertEquals(0, (items.single() as ShelfItem.Web).lastReadEpisode)
    }

    // ────── U2 filterShelfByLabel（ラベル絞り込みの純関数） ──────

    @Test
    fun `ラベル未選択（すべて）は無加工で素通しする`() {
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))

        val (fb, fw) = filterShelfByLabel(books, webs, selectedLabelId = null, bookLabelIds = emptyMap())

        assertEquals(books, fb)
        assertEquals(webs, fw)
    }

    @Test
    fun `ラベル選択中は付与済みの蔵書だけ残りWebカードは全部落ちる`() {
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))
        val assignments = mapOf("b1" to setOf("label-1", "label-2"))

        val (fb, fw) = filterShelfByLabel(books, webs, selectedLabelId = "label-1", bookLabelIds = assignments)

        assertEquals(listOf("b1"), fb.map { it.id })
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }

    @Test
    fun `どの本にも付いていないラベルを選ぶと蔵書0件になる（該当なし表示の前提）`() {
        val books = listOf(book("b1", 300))
        val assignments = mapOf("b1" to setOf("label-1"))

        val (fb, fw) = filterShelfByLabel(books, emptyList(), selectedLabelId = "label-9", bookLabelIds = assignments)

        assertEquals(emptyList<BookEntity>(), fb)
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }
}
