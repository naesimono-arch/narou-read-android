package com.novelreader.viewmodel

import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ────── chapterNumberOf（lastReadFilename → 章番号の純関数） ──────
    // カード進捗・読書状態の両方が同じ章番号抽出を使うため VM 層の純関数へ吊り上げた（旧 BookCard 内ロジック）。

    @Test
    // テスト名に「.」は使えない（Android ターゲットの Kotlin はバッククォート名でも「.」を不正文字として拒否）。
    fun `chapterNumberOf - chap_N 形式の章ファイル名は章番号 N を返す`() {
        assertEquals(12, chapterNumberOf("chap_12.html"))
    }

    @Test
    fun `chapterNumberOf - chap_ 形式でないファイル名は null`() {
        // index.html 等は「章ファイルではない」＝章番号なし。startsWith("chap_") でない経路。
        assertNull(chapterNumberOf("index.html"))
    }

    @Test
    fun `chapterNumberOf - null（未読）は null`() {
        assertNull(chapterNumberOf(null))
    }

    // ────── readingStatusFor（進捗＋総章数 → 読書状態の純関数） ──────
    // fraction（progressFractionFor）を状態3値へ畳み込む: null→未読 / >=1→読了 / それ以外→よみかけ。

    @Test
    fun `readingStatusFor - 進捗なしは未読`() {
        // progress null → 章番号 null → fraction null → UNREAD。
        assertEquals(ReadingStatus.UNREAD, readingStatusFor(progress = null, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - 章ファイルでない進捗（index）は未読`() {
        // lastReadFilename が chap_ 形式でない＝章番号 null → fraction null → UNREAD。
        val progress = ProgressEntity("b1", "index.html")
        assertEquals(ReadingStatus.UNREAD, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - 中間章はよみかけ`() {
        // chap_3 / 全10章 → fraction 0.3（0<x<1）→ READING。
        val progress = ProgressEntity("b1", "chap_3.html")
        assertEquals(ReadingStatus.READING, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - 最終章の未スクロールはよみかけ（開いた瞬間読了にしない）`() {
        // chap_10 / 全10章 / 先頭 → fraction (N-1)/N = 0.9 < 1 → READING（F-N の「100%の嘘を出さない」を状態でも保つ）。
        val progress = ProgressEntity("b1", "chap_10.html", scrollIndex = 0, scrollOffset = 0)
        assertEquals(ReadingStatus.READING, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - 最終章をスクロール済みなら読了`() {
        // chap_10 / 全10章 / スクロール済み → fraction 1.0 → FINISHED。
        val progress = ProgressEntity("b1", "chap_10.html", scrollIndex = 2, scrollOffset = 0)
        assertEquals(ReadingStatus.FINISHED, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - 総章数0は未読（章ファイル未検出時はカード表示「未読」と一致させる）`() {
        // なぜ未読で固定するか: 章数計算（chap_*.html の数え上げ）がまだ 0 を返す段階では
        // fraction が null（0除算回避）になり、カード側の「未読」表示と読書状態を一致させる不変条件。
        val progress = ProgressEntity("b1", "chap_3.html")
        assertEquals(ReadingStatus.UNREAD, readingStatusFor(progress, totalChaps = 0))
    }

    // ────── filterShelfByStatus（読書状態フィルタの純関数。旧 filterShelfByLabel の同型置換） ──────

    @Test
    fun `状態未選択（すべて）は無加工で素通しする`() {
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))

        val (fb, fw) = filterShelfByStatus(
            books, webs, selectedStatus = null,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
        )

        assertEquals(books, fb)
        assertEquals(webs, fw)
    }

    @Test
    fun `状態選択中は該当状態の蔵書だけ残りWebカードは全部落ちる`() {
        // b1=よみかけ（chap_3/全10章）・b2=未読（進捗なし）。READING 選択で b1 のみ残る。
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))
        val progressMap = mapOf("b1" to ProgressEntity("b1", "chap_3.html"))
        val chapterCounts = mapOf("b1" to 10, "b2" to 10)

        val (fb, fw) = filterShelfByStatus(
            books, webs, selectedStatus = ReadingStatus.READING,
            progressMap = progressMap, chapterCounts = chapterCounts,
        )

        assertEquals(listOf("b1"), fb.map { it.id })
        // ラベル絞り込みと同じく、状態は books のみに付く概念のため Web カードは全落とし。
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }

    @Test
    fun `該当状態の本が無ければ蔵書0件になる（該当なし表示の前提）`() {
        // b1 はよみかけ。誰も読了していない状態で FINISHED を選ぶと 0 件。
        val books = listOf(book("b1", 300))
        val progressMap = mapOf("b1" to ProgressEntity("b1", "chap_3.html"))
        val chapterCounts = mapOf("b1" to 10)

        val (fb, fw) = filterShelfByStatus(
            books, emptyList(), selectedStatus = ReadingStatus.FINISHED,
            progressMap = progressMap, chapterCounts = chapterCounts,
        )

        assertEquals(emptyList<BookEntity>(), fb)
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }
}
