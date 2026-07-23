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

    private fun web(ncode: String, addedAt: Long, generalAllNo: Int = 10) =
        WebNovelEntity(ncode = ncode, title = "Web$ncode", writer = "作者", generalAllNo = generalAllNo, addedAt = addedAt)

    @Test
    fun `蔵書とWeb由来が最近の活動順で混在する`() {
        // books は DAO 並び（降順）を模す: b1(300) > b2(100)
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("N1111AA", 200))

        val items = mergeShelfItems(books, emptyMap(), webs)

        assertEquals(listOf("book:b1", "web:N1111AA", "book:b2"), items.map { it.key })
    }

    @Test
    fun `触った蔵書は未接触の Web カードより下層に沈む（層反転・2026-07-16）`() {
        // 反転後: b1 は読んだ実績（lastReadAt=400）ゆえ tier0（下層）、未接触 Web（addedAt=300）は tier1（上層）＝上。
        // 旧規則では読書中の b1 が上だったが、実使用フィードバックで未読/未接触を上へ反転した。
        val books = listOf(book("b1", 100))
        val progress = mapOf("b1" to ProgressEntity("b1", "chap_1.html", lastReadAt = 400))
        val webs = listOf(web("N1111AA", 300))

        val items = mergeShelfItems(books, progress, webs)

        assertEquals(listOf("web:N1111AA", "book:b1"), items.map { it.key })
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
    fun `未読の新刊は読書中の本より上に来る（二層ソート・層反転・2026-07-16）`() {
        // 実使用フィードバックによる層反転: 未読新刊 bNew(addedAt=1000・tier1=上層) が、昔読んだきりの
        // bOld(lastReadAt=50・tier0=下層) より上。旧 ADR0016（読書中が上）を実使用で棄却した新仕様。
        // books は新 DAO 並び（二層降順）を模す＝未読 bNew(tier1) が先、既読 bOld(tier0) が後。
        val books = listOf(book("bNew", 1000), book("bOld", 10))
        val progress = mapOf("bOld" to ProgressEntity("bOld", "chap_1.html", lastReadAt = 50))

        val items = mergeShelfItems(books, progress, emptyList())

        assertEquals(listOf("book:bNew", "book:bOld"), items.map { it.key })
    }

    @Test
    fun `未接触の Web 新刊は読書中の蔵書より上に入る（Webも層反転）`() {
        // 反転後: 未接触 Web(addedAt=9999・tier1=上層) は、読書中 b1(lastReadAt=50・tier0=下層) より上。
        val books = listOf(book("b1", 10))
        val progress = mapOf("b1" to ProgressEntity("b1", "chap_1.html", lastReadAt = 50))
        val webs = listOf(web("N9999ZZ", 9999))

        val items = mergeShelfItems(books, progress, webs)

        assertEquals(listOf("web:N9999ZZ", "book:b1"), items.map { it.key })
    }

    // ────── 層反転（2026-07-16 実使用フィードバック）を固定する追加ケース ──────

    @Test
    fun `未読同士は addedAt 降順（未読クラスタ内は入れたてが上）`() {
        // どちらも未接触＝tier1。層内は addedAt 降順で bNew(300) が bOld(100) より上。
        val books = listOf(book("bNew", 300), book("bOld", 100))

        val items = mergeShelfItems(books, emptyMap(), emptyList())

        assertEquals(listOf("book:bNew", "book:bOld"), items.map { it.key })
    }

    @Test
    fun `既読同士は lastReadAt 降順（触った本クラスタ内は最後に触った順）`() {
        // どちらも触った＝tier0。層内は lastReadAt 降順で bRecent(400) が bStale(100) より上。
        // addedAt は逆順(bRecent=10 < bStale=20)でも lastReadAt が層内順を支配することを固定する。
        // books は新 DAO 並び（tier0 内 lastReadAt 降順）を模す＝bRecent が先。
        val books = listOf(book("bRecent", 10), book("bStale", 20))
        val progress = mapOf(
            "bRecent" to ProgressEntity("bRecent", "chap_1.html", lastReadAt = 400),
            "bStale" to ProgressEntity("bStale", "chap_1.html", lastReadAt = 100),
        )

        val items = mergeShelfItems(books, progress, emptyList())

        assertEquals(listOf("book:bRecent", "book:bStale"), items.map { it.key })
    }

    @Test
    fun `Web は触った記録ありで下層・未接触で上層（web も蔵書と同一の二層規則）`() {
        // 触った web(NTOUCH01・最終接触 5000) は tier0（下層）、未接触 web(NFRESH01・addedAt=9999) は tier1（上層）＝上。
        // webLastReadAt は episode 表示用マップとは別に「接触時刻」を運ぶ（並びは時刻で決める）。
        val webs = listOf(web("NTOUCH01", 100), web("NFRESH01", 9999))
        val webLastReadAt = mapOf("NTOUCH01" to 5000L)

        val items = mergeShelfItems(
            emptyList(), emptyMap(), webs,
            webLastReadAt = webLastReadAt,
        )

        assertEquals(listOf("web:NFRESH01", "web:NTOUCH01"), items.map { it.key })
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
            webReadingProgress = emptyMap(),
        )

        assertEquals(books, fb)
        assertEquals(webs, fw)
    }

    // 注: 旧「Web判定材料が未配線(null)なら全部落ちる」テストは撤去した。webReadingProgress を必須引数化し
    //     null フォールバック分岐そのものを削ったため（配線漏れをコンパイル時に強制検出する）。蔵書側フィルタの
    //     回帰は下の「Web判定材料を配線しても蔵書側フィルタは従来どおり」がカバーする。

    // ────── webReadingStatusFor（Web作品の読書状態分類・全分岐） ──────
    // 近似2点（最終話を開いた=読了／generalAllNo は登録時スナップショット）を含む確定仕様を固定する。

    @Test
    fun `webReadingStatusFor - 読書位置なし(null)は未読`() {
        assertEquals(ReadingStatus.UNREAD, webReadingStatusFor(lastReadEpisode = null, generalAllNo = 10))
    }

    @Test
    fun `webReadingStatusFor - lastReadEpisode0は未読`() {
        assertEquals(ReadingStatus.UNREAD, webReadingStatusFor(lastReadEpisode = 0, generalAllNo = 10))
    }

    @Test
    fun `webReadingStatusFor - 途中(0より大きく全話数未満)はよみかけ`() {
        assertEquals(ReadingStatus.READING, webReadingStatusFor(lastReadEpisode = 5, generalAllNo = 10))
    }

    @Test
    fun `webReadingStatusFor - 最終話到達(全話数と同値)は読了`() {
        // 近似①: 最終話を開いた＝読了の割り切り。
        assertEquals(ReadingStatus.FINISHED, webReadingStatusFor(lastReadEpisode = 10, generalAllNo = 10))
    }

    @Test
    fun `webReadingStatusFor - 全話数を超える読書位置も読了(登録時スナップショットが縮んだ場合の防御)`() {
        // 近似②の裏面: generalAllNo は登録時スナップショットゆえ、後で話数が減った等で ep>allNo になっても読了扱い。
        assertEquals(ReadingStatus.FINISHED, webReadingStatusFor(lastReadEpisode = 12, generalAllNo = 10))
    }

    @Test
    fun `webReadingStatusFor - generalAllNo0のガード 読んでいてもよみかけ(読了に誤分類しない)`() {
        // 話数不明(0)は読了条件から外す。ep>0 なら読了ではなくよみかけへ落とす。
        assertEquals(ReadingStatus.READING, webReadingStatusFor(lastReadEpisode = 3, generalAllNo = 0))
    }

    @Test
    fun `webReadingStatusFor - generalAllNo0かつ未読は未読`() {
        assertEquals(ReadingStatus.UNREAD, webReadingStatusFor(lastReadEpisode = 0, generalAllNo = 0))
    }

    // ────── filterShelfByStatus（Web判定材料を配線した本来の挙動） ──────

    @Test
    fun `未読フィルタは未読のWebだけ残す(行なし・0話)`() {
        // wUnreadA=読書位置行なし・wUnreadB=0話・wReading=途中(3/10)・wFinished=最終話到達(10/10)。
        val webs = listOf(
            web("NUNREADA", 400), web("NUNREADB", 300), web("NREADING", 200), web("NFINISH0", 100),
        )
        val progress = mapOf("NUNREADB" to 0, "NREADING" to 3, "NFINISH0" to 10)

        val (fb, fw) = filterShelfByStatus(
            emptyList(), webs, selectedStatus = ReadingStatus.UNREAD,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
            webReadingProgress = progress,
        )

        assertEquals(emptyList<BookEntity>(), fb)
        assertEquals(listOf("NUNREADA", "NUNREADB"), fw.map { it.ncode })
    }

    @Test
    fun `読みかけフィルタは途中のWebだけ残す`() {
        val webs = listOf(web("NUNREAD1", 300), web("NREADING", 200), web("NFINISH0", 100))
        val progress = mapOf("NREADING" to 4, "NFINISH0" to 10)

        val (_, fw) = filterShelfByStatus(
            emptyList(), webs, selectedStatus = ReadingStatus.READING,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
            webReadingProgress = progress,
        )

        assertEquals(listOf("NREADING"), fw.map { it.ncode })
    }

    @Test
    fun `読了フィルタは最終話到達のWebだけ残す`() {
        val webs = listOf(web("NREADING", 200), web("NFINISH0", 100))
        val progress = mapOf("NREADING" to 4, "NFINISH0" to 10)

        val (_, fw) = filterShelfByStatus(
            emptyList(), webs, selectedStatus = ReadingStatus.FINISHED,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
            webReadingProgress = progress,
        )

        assertEquals(listOf("NFINISH0"), fw.map { it.ncode })
    }

    @Test
    fun `読了フィルタでgeneralAllNo0のWebは残らない(読了ガード)`() {
        // generalAllNo=0 は話数不明。読み込んでいても FINISHED に分類されない（READING 扱い）＝読了フィルタで消える。
        val webs = listOf(web("NNOCOUNT", 200, generalAllNo = 0))
        val progress = mapOf("NNOCOUNT" to 99)

        val (_, fw) = filterShelfByStatus(
            emptyList(), webs, selectedStatus = ReadingStatus.FINISHED,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
            webReadingProgress = progress,
        )

        assertEquals(emptyList<WebNovelEntity>(), fw)
    }

    @Test
    fun `Web分類は ncode を再正規化して進捗を引く(表記ゆれで漏れない)`() {
        // エンティティ側 ncode が小文字でも、lookup 前に trim+uppercase するため正規化済みキーの進捗に一致する。
        val webs = listOf(web("n1234ab", 200))
        val progress = mapOf("N1234AB" to 10) // 保存時正規化済みキー（大文字）

        val (_, fw) = filterShelfByStatus(
            emptyList(), webs, selectedStatus = ReadingStatus.FINISHED,
            progressMap = emptyMap(), chapterCounts = emptyMap(),
            webReadingProgress = progress,
        )

        assertEquals(listOf("n1234ab"), fw.map { it.ncode })
    }

    @Test
    fun `Web判定材料を配線しても蔵書側フィルタは従来どおり`() {
        // books 側回帰: 配線ありでも books は readingStatusFor で分類され、Web と独立に動く。
        // b1=よみかけ(chap_3/10)・b2=未読。READING 選択で b1 のみ・Web は未読の wA が落ちる。
        val books = listOf(book("b1", 300), book("b2", 100))
        val webs = listOf(web("NWEBA", 200))
        val progressMap = mapOf("b1" to ProgressEntity("b1", "chap_3.html"))
        val chapterCounts = mapOf("b1" to 10, "b2" to 10)

        val (fb, fw) = filterShelfByStatus(
            books, webs, selectedStatus = ReadingStatus.READING,
            progressMap = progressMap, chapterCounts = chapterCounts,
            webReadingProgress = emptyMap(), // 配線済み・進捗ゼロ＝Web は全て未読
        )

        assertEquals(listOf("b1"), fb.map { it.id })
        assertEquals(emptyList<WebNovelEntity>(), fw) // NWEBA は未読ゆえ READING フィルタで消える
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
            webReadingProgress = emptyMap(),
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
            webReadingProgress = emptyMap(),
        )

        assertEquals(listOf("b1"), fb.map { it.id })
        assertEquals(emptyList<WebNovelEntity>(), fw)
    }

    // ────── shelfStatusCounts（状態チップ件数・蔵書とWebを合流して数える） ──────

    @Test
    fun `状態件数は蔵書とWeb作品を同一状態へ合流して数える`() {
        // 蔵書: b1=よみかけ(chap_3/10)・b2=未読(進捗なし)・b3=読了(reachedEnd)。
        val books = listOf(book("b1", 300), book("b2", 200), book("b3", 100))
        val progressMap = mapOf(
            "b1" to ProgressEntity("b1", "chap_3.html"),
            "b3" to ProgressEntity("b3", "chap_10.html", reachedEnd = true),
        )
        val chapterCounts = mapOf("b1" to 10, "b3" to 10)
        // Web: NWR=よみかけ(4/10)・NWU=未読(0話)・NWF=読了(10/10)・NWU2=進捗行なし＝未読。
        val webs = listOf(web("NWR", 400), web("NWU", 350), web("NWF", 320), web("NWU2", 310))
        val webReadingProgress = mapOf("NWR" to 4, "NWU" to 0, "NWF" to 10)

        val counts = shelfStatusCounts(books, webs, progressMap, chapterCounts, webReadingProgress)

        // よみかけ: b1 + NWR = 2／未読: b2 + NWU + NWU2 = 3／読了: b3 + NWF = 2
        assertEquals(2, counts[ReadingStatus.READING])
        assertEquals(3, counts[ReadingStatus.UNREAD])
        assertEquals(2, counts[ReadingStatus.FINISHED])
    }

    @Test
    fun `状態件数はWeb作品のみでも数える(蔵書0件でWebが件数に入る＝本バグの回帰防止)`() {
        // 蔵書ゼロでも Web が状態件数に載ることを固定（旧実装は蔵書のみ集計で Web が常に0だった＝チップが誤って dim）。
        val webs = listOf(web("NWR", 200), web("NWF", 100))
        val webReadingProgress = mapOf("NWR" to 3, "NWF" to 10)

        val counts = shelfStatusCounts(emptyList(), webs, emptyMap(), emptyMap(), webReadingProgress)

        assertEquals(1, counts[ReadingStatus.READING])
        assertEquals(1, counts[ReadingStatus.FINISHED])
        assertNull(counts[ReadingStatus.UNREAD]) // 未読の Web は無い＝キー無し（0件チップの dim 前提）
    }
}
