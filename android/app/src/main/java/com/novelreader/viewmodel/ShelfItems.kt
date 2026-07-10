package com.novelreader.viewmodel

import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity

/**
 * 融合本棚の表示1行/1枠（(b) Web由来・未取込カードの並置＝bookshelf-fusion-D モック）。
 * 蔵書（取込済み）と Web 由来（未取込）を同じ棚に「最近の活動順」で混在させるための表示モデル。
 */
sealed interface ShelfItem {
    /** LazyGrid/LazyColumn の key に使う安定 ID（蔵書 id と ncode は衝突しない前提を型で分離）。 */
    val key: String

    data class Book(val book: BookEntity, internal val recencyKey: Long) : ShelfItem {
        override val key: String get() = "book:${book.id}"
    }

    /** lastReadEpisode: 機能②の WebView 読書位置（最後に開いた話。0＝未読）。>0 でカードに「続きから読む 第N話」を出す。 */
    data class Web(val novel: WebNovelEntity, val lastReadEpisode: Int = 0) : ShelfItem {
        override val key: String get() = "web:${novel.ncode}"
    }
}

/**
 * 蔵書リストと Web由来（未取込）リストを「最近の活動順」で1本にマージする純関数。
 *
 * - 蔵書の並びは BookDao.getAllBooks（`MAX(addedAt, lastReadAt) DESC`）が正本で、ここでは崩さない。
 *   Web 由来カードの挿入位置を決めるためだけに、同じ式（addedAt と progress.lastReadAt の大きい方）を
 *   ここでも計算する。**意図的な式の重複**: DAO クエリの並びキーは SELECT 列に出ておらず、
 *   取り出すには一覧クエリの返却型ごと変える必要があり、既存呼び出し・テストへの波及が大きい。
 *   式は2行の単純比較なので、返却型変更よりコメントで対にする方を選んだ（挙動の正は DAO 側）。
 * - 取込済み（books.ncode と同一 ncode）の Web カードは非表示にする＝PDF 取込が完了した時点で
 *   蔵書カードへ「自然昇格」し、二重表示しない。比較は保存時正規化（trim+uppercase）と同じ形で行う。
 * - 同値キーのときは蔵書を先に置く（読める実体がある方が優先という判断）。
 */
fun mergeShelfItems(
    books: List<BookEntity>,
    progressMap: Map<String, ProgressEntity>,
    webNovels: List<WebNovelEntity>,
    // 機能②: ncode(正規化済み大文字)→最後に開いた話。Web カードの「続きから読む 第N話」表示に使う。
    // 既定 emptyList 相当（emptyMap）は既存テスト・呼び出しの互換のため（読書位置なしなら全カード未読表示で不変）。
    webReadingProgress: Map<String, Int> = emptyMap(),
): List<ShelfItem> {
    val importedNcodes = books.mapNotNull { it.ncode?.trim()?.uppercase() }.toSet()

    val bookItems = books.map { book ->
        val lastReadAt = progressMap[book.id]?.lastReadAt ?: 0L
        ShelfItem.Book(book, maxOf(book.addedAt, lastReadAt))
    }
    val webItems = webNovels
        .filterNot { it.ncode.trim().uppercase() in importedNcodes }
        .sortedByDescending { it.addedAt }

    // Web カードに読書位置を載せる。web_novels.ncode も web_reading_progress.ncode も trim+uppercase 正規化済みで
    // 保存されるため、同じ正規化キーで引ける（表記ゆれで「読んだのに続きが出ない」を防ぐ二重の安全として再正規化）。
    fun webItem(n: WebNovelEntity): ShelfItem.Web =
        ShelfItem.Web(n, webReadingProgress[n.ncode.trim().uppercase()] ?: 0)

    // 両列とも降順ソート済みの前提でマージする（books は DAO・webNovels は直前の sort が保証）。
    val result = ArrayList<ShelfItem>(bookItems.size + webItems.size)
    var bi = 0
    var wi = 0
    while (bi < bookItems.size && wi < webItems.size) {
        if (bookItems[bi].recencyKey >= webItems[wi].addedAt) {
            result.add(bookItems[bi]); bi++
        } else {
            result.add(webItem(webItems[wi])); wi++
        }
    }
    while (bi < bookItems.size) { result.add(bookItems[bi]); bi++ }
    while (wi < webItems.size) { result.add(webItem(webItems[wi])); wi++ }
    return result
}

/** 読書状態の分類。本棚フィルタ「すべて/よみかけ/未読/読了」の正本（すべて＝null で表現）。 */
enum class ReadingStatus { READING, UNREAD, FINISHED }

/** "chap_12.html" → 12。chap_ 形式以外（index.html 等）・null は null。 */
fun chapterNumberOf(lastReadFilename: String?): Int? =
    lastReadFilename
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

// ============================================================
// 本棚カードの進捗割合を、章位置＋（最終章のみ）章内スクロール位置から算出する。
// ※本棚カードの表示（BookProgressRow）と状態フィルタの分類（readingStatusFor）が同じ計算を
//   共有する単一正本にするため、旧 BookCard.kt からこの viewmodel 層へ移動（ロジックは不変）。
//
// なぜ単純な chapNum/totalChaps を使わないか（F-N）:
// それだと最終章のファイルを開いた瞬間、章内を1行も読んでいなくても progress=1.0 になり
// 「100%」と嘘表示していた（章 index 単独算出でスクロール実位置を無視していたのが根因）。
// 章の総量（総アイテム数・総高さ）は DB に保存しておらず（ProgressEntity が持つのは
// LazyList の firstVisibleItemIndex/Offset だけ）、厳密な章内% は原理的に出せない。
// そこで最終章に限り、確実に判る「先頭か否か」だけを使って過大表示を避ける:
//   ・先頭（未スクロール）＝まだ最終章を読み始めていない → (N-1)/N（あと1章ぶん未読）
//   ・少しでもスクロール済み＝読み進めている → 1.0（読了間近とみなす）
// 中間章は従来どおり chapNum/totalChaps（そこは嘘にならないため挙動を変えない）。
// 進捗の書込側は一切変更せず、表示計算のみで嘘を消す。
// ============================================================
fun progressFractionFor(
    chapNum: Int?,
    totalChaps: Int,
    scrollIndex: Int,
    scrollOffset: Int,
): Float? {
    if (chapNum == null || totalChaps <= 0) return null
    return if (chapNum >= totalChaps) {
        // 最終章。章内スクロールを加味する。
        val atTop = scrollIndex == 0 && scrollOffset == 0
        if (atTop) (totalChaps - 1).toFloat() / totalChaps else 1f
    } else {
        chapNum.toFloat() / totalChaps
    }
}

/**
 * 蔵書1冊の読書状態を分類する（本棚フィルタ「よみかけ/未読/読了」の判定）。
 *
 * なぜ progressFractionFor を経由するか（不変条件）: カードの進捗行（BookProgressRow）は
 * progressFraction==null のとき「未読」を表示する。分類がこの表示と食い違うと、カードは「未読」なのに
 * 「未読」フィルタに出ない/出る、という矛盾が起きる。そこで表示とまったく同じ計算から状態を導く:
 *   fraction==null → UNREAD／fraction>=1f → FINISHED／それ以外 → READING。
 */
fun readingStatusFor(progress: ProgressEntity?, totalChaps: Int): ReadingStatus {
    val fraction = progressFractionFor(
        chapterNumberOf(progress?.lastReadFilename),
        totalChaps,
        progress?.scrollIndex ?: 0,
        progress?.scrollOffset ?: 0,
    )
    return when {
        fraction == null -> ReadingStatus.UNREAD
        fraction >= 1f -> ReadingStatus.FINISHED
        else -> ReadingStatus.READING
    }
}

/**
 * 読書状態フィルタの純関数（mergeShelfItems の前段に噛ませる）。
 *
 * - selectedStatus=null（「すべて」）は無加工で素通しする。
 * - 状態選択中の Web由来カードは**全部落とす**。なぜ: 未取込カードは読書進捗を持たず、「未読」ではなく
 *   「未取込」という別状態。「すべて」以外にマッチし得ないカードを混ぜると誤解を生むため（旧ラベル絞りと同じ判断）。
 * - books は各本の読書状態（readingStatusFor）が selectedStatus に一致するものだけ残す。
 */
fun filterShelfByStatus(
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    selectedStatus: ReadingStatus?,
    progressMap: Map<String, ProgressEntity>,
    chapterCounts: Map<String, Int>,
): Pair<List<BookEntity>, List<WebNovelEntity>> {
    if (selectedStatus == null) return books to webNovels
    return books.filter {
        readingStatusFor(progressMap[it.id], chapterCounts[it.id] ?: 0) == selectedStatus
    } to emptyList()
}
