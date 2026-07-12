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

    data class Book(val book: BookEntity, internal val recencyKey: RecencyKey) : ShelfItem {
        override val key: String get() = "book:${book.id}"
    }

    /** lastReadEpisode: 機能②の WebView 読書位置（最後に開いた話。0＝未読）。>0 でカードに「続きから読む 第N話」を出す。 */
    data class Web(val novel: WebNovelEntity, val lastReadEpisode: Int = 0) : ShelfItem {
        override val key: String get() = "web:${novel.ncode}"
    }
}

/**
 * 本棚の並び順キー（二層）。ia Major「既定ソート＝続きから読む」2026-07-12 の実装。
 *
 * なぜ二層か: 旧・単層 `MAX(addedAt, lastReadAt)` では PDF 取込のたび未読新刊（addedAt=now）が
 * 読みかけ本の上へ来て、支配的タスク（続きから読む）の対象が先頭から押し下げられていた。
 * そこで「読書中（lastReadAt>0）」を第1層＝常に上、「未読/未取込（lastReadAt=0）」を第2層＝下に分け、
 * 層内は 第1層＝lastReadAt / 第2層＝addedAt の降順にする。これで新規取込は未読クラスタの先頭に入り、
 * 読みかけ本を押し下げない。BookDao.getAllBooks の ORDER BY もこの二層規則に一致させている（並びの正は DAO）。
 *
 * ※トレードオフ（ADR 起票要）: 半年前に一度だけ読んで放置した本も「読書中」層として、
 *   今追加したばかりの未読新刊より上に来る。監査公理「続きから読むが支配的タスク」に沿う選択だが、
 *   別解（放置本の減衰・お気に入り昇格）もあり得るため設計判断として ADR に記録する。
 */
data class RecencyKey(val tier: Int, val value: Long) : Comparable<RecencyKey> {
    override fun compareTo(other: RecencyKey): Int {
        val t = tier.compareTo(other.tier)
        return if (t != 0) t else value.compareTo(other.value)
    }
}

/** 蔵書の並び順キーを二層規則で作る（読書中=tier1/lastReadAt・未読=tier0/addedAt）。 */
internal fun recencyKeyOf(addedAt: Long, lastReadAt: Long): RecencyKey =
    if (lastReadAt > 0L) RecencyKey(tier = 1, value = lastReadAt)
    else RecencyKey(tier = 0, value = addedAt)

/**
 * 蔵書リストと Web由来（未取込）リストを「最近の活動順」で1本にマージする純関数。
 *
 * - 蔵書の並びは BookDao.getAllBooks（二層: 読書中を上・未読を下）が正本で、ここでは崩さない。
 *   Web 由来カードの挿入位置を決めるためだけに、同じ二層規則（recencyKeyOf）をここでも計算する。
 *   **意図的な式の重複**: DAO クエリの並びキーは SELECT 列に出ておらず、取り出すには一覧クエリの
 *   返却型ごと変える必要があり、既存呼び出し・テストへの波及が大きい。規則は recencyKeyOf の
 *   数行なので、返却型変更よりコメントで対にする方を選んだ（挙動の正は DAO 側）。
 *   Web 由来（未取込）カードは常に第2層（tier0/addedAt）＝未読クラスタへ挿入する。
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
        ShelfItem.Book(book, recencyKeyOf(book.addedAt, lastReadAt))
    }
    // Web 由来は常に未読クラスタ（tier0/addedAt）。降順に整列して books とマージする。
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
        // Web は tier0/addedAt。二層キーで比較し、同値は蔵書優先（>= で book を先に置く）。
        if (bookItems[bi].recencyKey >= RecencyKey(tier = 0, value = webItems[wi].addedAt)) {
            result.add(bookItems[bi]); bi++
        } else {
            result.add(webItem(webItems[wi])); wi++
        }
    }
    while (bi < bookItems.size) { result.add(bookItems[bi]); bi++ }
    while (wi < webItems.size) { result.add(webItem(webItems[wi])); wi++ }
    return result
}

/**
 * 最後に読んだ相対時刻ラベル（continuity Minor「いつぶりか」2026-07-12）。lastReadAt<=0 や未来は null。
 * なぜ日粒度か: 本棚カードは「昨日の続き」と「数週間ぶり」を区別できれば十分で、分秒の精度は不要。
 * 閾値は素直な経過時間ベース（今日<24h / 昨日<48h / N日前<7日 / N週間前<30日 / Nヶ月前<365日 / N年前）。
 * ※このラベル自体は HTML モック未表現＝最終ユーザー確認バッチ対象（実装は最小・静かな添え）。
 */
fun relativeReadLabel(lastReadAt: Long, now: Long): String? {
    if (lastReadAt <= 0L) return null
    val diff = now - lastReadAt
    if (diff < 0L) return null   // 端末時計のズレ等で未来になった場合の防御
    val day = 24L * 60 * 60 * 1000
    return when {
        diff < day -> "今日"
        diff < 2 * day -> "昨日"
        diff < 7 * day -> "${diff / day}日前"
        diff < 30 * day -> "${diff / (7 * day)}週間前"
        diff < 365 * day -> "${diff / (30 * day)}ヶ月前"
        else -> "${diff / (365 * day)}年前"
    }
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
//   ・少しでもスクロール済み＝読み進めている → (N-0.5)/N（最終章を読み途中。1f は出さない）
// 中間章は従来どおり chapNum/totalChaps（そこは嘘にならないため挙動を変えない）。
// 進捗の書込側は一切変更せず、表示計算のみで嘘を消す。
//
// なぜ最終章スクロール中を 1f にしないか（公理8・ssot Major 2026-07-12）:
// 旧実装は「1行でもスクロール＝1.0」だったため、最終章を1行送った瞬間に 100%・朱印『了』・
// READING フィルタから消えて読了へ移動していた＝「今読んでいる本」が『よみかけ』で見つからない嘘。
// スクロールしただけでは末尾に到達した保証は無い（章内総量を DB に持たないため末尾検出は不能）ので、
// READING に留まる <1f（最終章を読み途中を表す (N-0.5)/N）を返す。真の読了（100%・『了』）は
// 「末尾到達フラグ」にのみ結ぶべきだが、そのフラグは ProgressEntity に無く、読書画面での
// 末尾検出→保存の配線が要る（=別レーンの配線依頼）。本関数だけでは 1f を出せないため、
// 到達フラグが入るまで readingStatusFor の FINISHED は成立しない（近似で嘘の 100% を出さない選択）。
// ============================================================
fun progressFractionFor(
    chapNum: Int?,
    totalChaps: Int,
    scrollIndex: Int,
    scrollOffset: Int,
): Float? {
    if (chapNum == null || totalChaps <= 0) return null
    return if (chapNum >= totalChaps) {
        // 最終章。章内スクロールを加味するが、末尾到達は判定できないため 1f は出さない。
        val atTop = scrollIndex == 0 && scrollOffset == 0
        if (atTop) (totalChaps - 1).toFloat() / totalChaps
        else (totalChaps - 0.5f) / totalChaps
    } else {
        chapNum.toFloat() / totalChaps
    }
}

/**
 * 蔵書1冊の読書状態を分類する（本棚フィルタ「よみかけ/未読/読了」の判定）。
 *
 * なぜ読了を reachedEnd（末尾到達の実績）にのみ結ぶか（公理8・ssot Major 2026-07-12）: 進捗率から
 * 読了を導出すると「最終章を開いた/1行スクロールした瞬間に読了」の嘘になる（章内総量を DB に持たず
 * 末尾到達を判定できないため）。読書画面が本当に末尾を可視化したときだけ立つ reachedEnd を正本とし、
 * それが true の本のみ『了』印・読了フィルタへ移す（sticky＝読み直しでも維持）。
 *
 * なぜ残りは progressFractionFor を経由するか（不変条件）: カードの進捗行（BookProgressRow）は
 * progressFraction==null のとき「未読」を表示する。分類がこの表示と食い違うと、カードは「未読」なのに
 * 「未読」フィルタに出ない/出る、という矛盾が起きる。そこで未読/よみかけの境目は表示と同じ計算から導く:
 *   reachedEnd → FINISHED／fraction==null → UNREAD／それ以外 → READING。
 */
fun readingStatusFor(progress: ProgressEntity?, totalChaps: Int): ReadingStatus {
    // 読了は末尾到達の実績にのみ結ぶ（進捗率からの導出はしない）。
    if (progress?.reachedEnd == true) return ReadingStatus.FINISHED
    val fraction = progressFractionFor(
        chapterNumberOf(progress?.lastReadFilename),
        totalChaps,
        progress?.scrollIndex ?: 0,
        progress?.scrollOffset ?: 0,
    )
    // reachedEnd=false のときは進捗率がどれだけ高くても FINISHED にしない（嘘の読了を出さない）。
    return if (fraction == null) ReadingStatus.UNREAD else ReadingStatus.READING
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

/**
 * 蔵書内フィルタ（ia Major「既知アイテム高速路」2026-07-12 のロジック層・先行実装）。
 * タイトルまたは著者に query を含む蔵書だけを残す純関数（大文字小文字・前後空白を無視した部分一致＝LIKE 相当）。
 *
 * なぜロジックだけ先に入れるか（意匠は保留）: 蔵書内検索欄そのものは HTML モック（bookshelf-*-D.html）に
 * 未表現＝意匠新設はユーザー確認事項のため UI は保留し、テスト可能なフィルタ規則だけを正本として先に固定する。
 * Web 由来カードは対象外（未取込は検索対象の蔵書ではない＝状態フィルタと同じ扱い）。
 * 空 query は無加工で素通し（絞り込み無し）。
 */
fun filterBooksByQuery(books: List<BookEntity>, query: String): List<BookEntity> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return books
    return books.filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
}
