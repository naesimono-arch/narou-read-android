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
/**
 * U2 ラベル絞り込みの純関数（mergeShelfItems の前段に噛ませる）。
 *
 * - selectedLabelId=null（「すべて」）は無加工で素通しする。
 * - ラベル選択中の Web由来カードは**全部落とす**。なぜ: ラベルは PDF 蔵書（books）のみ付与対象
 *   （BookLabelEntity の why 参照）で、Web カードはどのラベルにもマッチし得ない。「すべて」以外で
 *   マッチ不能なカードを混ぜると「ラベルを付けたのに関係ない Web カードが残る」誤解を生むため。
 *
 * @param bookLabelIds bookId→付与済み labelId 集合（BookshelfUiState.Content.bookLabelIds）。
 */
fun filterShelfByLabel(
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    selectedLabelId: String?,
    bookLabelIds: Map<String, Set<String>>,
): Pair<List<BookEntity>, List<WebNovelEntity>> {
    if (selectedLabelId == null) return books to webNovels
    return books.filter { bookLabelIds[it.id]?.contains(selectedLabelId) == true } to emptyList()
}

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
