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
    fun `読書中の本は未読新刊より上に来る（二層ソート・続きから読むが支配的タスク）`() {
        // ia Major: 未読新刊 b_new(addedAt=1000) が、昔読んだきり b_old(addedAt=10・lastReadAt=50) を
        // 押し下げない。読書中(tier1)は addedAt がどれだけ新しい未読(tier0)より必ず上。
        // books は DAO 並び（二層降順）を模す＝b_old(tier1) が先、b_new(tier0) が後。
        val books = listOf(book("bOld", 10), book("bNew", 1000))
        val progress = mapOf("bOld" to ProgressEntity("bOld", "chap_1.html", lastReadAt = 50))

        val items = mergeShelfItems(books, progress, emptyList())

        assertEquals(listOf("book:bOld", "book:bNew"), items.map { it.key })
    }

    @Test
    fun `未読の Web 新刊も読書中の蔵書より下に入る（Webは第2層）`() {
        // 読書中 b1(tier1) は、addedAt が最新の未取込 Web(tier0/9999) より上。
        val books = listOf(book("b1", 10))
        val progress = mapOf("b1" to ProgressEntity("b1", "chap_1.html", lastReadAt = 50))
        val webs = listOf(web("N9999ZZ", 9999))

        val items = mergeShelfItems(books, progress, webs)

        assertEquals(listOf("book:b1", "web:N9999ZZ"), items.map { it.key })
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
    fun `readingStatusFor - 最終章をスクロール済みでもよみかけ（読了の嘘を出さない）`() {
        // ssot Major: chap_10 / 全10章 / スクロール済み → fraction 0.95（<1f）→ READING。
        // 1行スクロール＝読了の嘘を消す。真の読了は末尾到達フラグ（未配線）に結ぶまで成立しない。
        val progress = ProgressEntity("b1", "chap_10.html", scrollIndex = 2, scrollOffset = 0)
        assertEquals(ReadingStatus.READING, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - reachedEnd=false ならどの章位置でも読了にしない`() {
        // 進捗率から読了を導出しない不変条件（公理8）: reachedEnd が立っていない限り、最終章の
        // どこに居ても FINISHED にはならず READING に留まる（1行スクロール＝読了の嘘を出さない）。
        val atTop = ProgressEntity("b1", "chap_10.html", scrollIndex = 0, scrollOffset = 0)
        val scrolled = ProgressEntity("b1", "chap_10.html", scrollIndex = 9, scrollOffset = 999)
        assertEquals(ReadingStatus.READING, readingStatusFor(atTop, totalChaps = 10))
        assertEquals(ReadingStatus.READING, readingStatusFor(scrolled, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - reachedEnd=true は読了（末尾到達の実績で了印を点ける）`() {
        // ssot Major: 読書画面が最終章の末尾を可視化して立てた reachedEnd を正本に FINISHED を返す。
        val progress = ProgressEntity("b1", "chap_10.html", scrollIndex = 5, scrollOffset = 0, reachedEnd = true)
        assertEquals(ReadingStatus.FINISHED, readingStatusFor(progress, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - reachedEnd=true は sticky（前の章へ戻って読み直しても読了維持）`() {
        // 読了は実績のため、読み直しで lastReadFilename が中間章へ戻っても reachedEnd が立っていれば FINISHED。
        val reReading = ProgressEntity("b1", "chap_2.html", scrollIndex = 3, scrollOffset = 0, reachedEnd = true)
        assertEquals(ReadingStatus.FINISHED, readingStatusFor(reReading, totalChaps = 10))
    }

    @Test
    fun `readingStatusFor - reachedEnd=true は章数未確定（totalChaps=0）でも読了`() {
        // reachedEnd は事実なので、章数え上げがまだ 0 を返す段階でも読了として扱う（進捗率導出より優先）。
        val progress = ProgressEntity("b1", "chap_1.html", reachedEnd = true)
        assertEquals(ReadingStatus.FINISHED, readingStatusFor(progress, totalChaps = 0))
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

    // ────── relativeReadLabel（最後に読んだ相対時刻・continuity Minor） ──────

    @Test
    fun `relativeReadLabel - 未記録や未来は null`() {
        val now = 1_000_000_000_000L
        assertNull(relativeReadLabel(0L, now))
        assertNull(relativeReadLabel(now + 5_000L, now))
    }

    @Test
    fun `relativeReadLabel - 今日昨日N日前N週間前Nヶ月前N年前`() {
        val day = 24L * 60 * 60 * 1000
        val now = 10_000L * day
        assertEquals("今日", relativeReadLabel(now - day / 2, now))
        assertEquals("昨日", relativeReadLabel(now - day - 1, now))
        assertEquals("3日前", relativeReadLabel(now - 3 * day, now))
        assertEquals("1週間前", relativeReadLabel(now - 7 * day, now))
        assertEquals("2週間前", relativeReadLabel(now - 14 * day, now))
        assertEquals("1ヶ月前", relativeReadLabel(now - 30 * day, now))
        assertEquals("1年前", relativeReadLabel(now - 365 * day, now))
    }

    // ────── filterBooksByQuery（蔵書内 LIKE フィルタの純関数・UI は保留） ──────

    @Test
    fun `空クエリは全蔵書を素通しする`() {
        val books = listOf(book("b1", 300), book("b2", 100))
        assertEquals(books, filterBooksByQuery(books, ""))
        assertEquals(books, filterBooksByQuery(books, "   "))
    }

    @Test
    fun `タイトル部分一致で絞り込む（大小文字・前後空白を無視）`() {
        val a = BookEntity(id = "a", title = "転生賢者の異世界", htmlDirPath = "/a", author = "山田")
        val b = BookEntity(id = "b", title = "スライム倒して300年", htmlDirPath = "/b", author = "森田")
        val list = listOf(a, b)
        assertEquals(listOf("a"), filterBooksByQuery(list, " 異世界 ").map { it.id })
    }

    @Test
    fun `著者名にも一致する`() {
        val a = BookEntity(id = "a", title = "本A", htmlDirPath = "/a", author = "Kirishima Aoi")
        val b = BookEntity(id = "b", title = "本B", htmlDirPath = "/b", author = "森田")
        val list = listOf(a, b)
        assertEquals(listOf("a"), filterBooksByQuery(list, "kirishima").map { it.id })
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

    @Test
    fun `読了フィルタは reachedEnd の本だけを残す（了印・読了フィルタの復活）`() {
        // b1=読了（reachedEnd=true）・b2=よみかけ（chap_3）。FINISHED 選択で b1 のみ残る＝読了フィルタが機能する。
        val books = listOf(book("b1", 300), book("b2", 100))
        val progressMap = mapOf(
            "b1" to ProgressEntity("b1", "chap_10.html", reachedEnd = true),
            "b2" to ProgressEntity("b2", "chap_3.html"),
        )
        val chapterCounts = mapOf("b1" to 10, "b2" to 10)

        val (fb, fw) = filterShelfByStatus(
            books, emptyList(), selectedStatus = ReadingStatus.FINISHED,
            progressMap = progressMap, chapterCounts = chapterCounts,
        )

        assertEquals(listOf("b1"), fb.map { it.id })
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }
}
