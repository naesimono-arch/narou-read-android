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
 * 本棚の並び順キー（二層）。ADR 0016 の実装（2026-07-16 改訂・層反転）。
 *
 * なぜ二層か: 旧・単層 `MAX(addedAt, lastReadAt)` では取込順と読書順が混ざり並びの意図が読めなかった。
 * そこで tier で「触ったか否か」を大分類し、層内を時刻降順にする二層キーにする。
 *
 * なぜ未読を上（層反転・2026-07-16 実使用フィードバック）: 旧版は「読書中（lastReadAt>0）」を上層に置いたが、
 * 実使用で「取り込んだばかりの未読が読みかけの下に埋もれて見つけにくい」不満が出た。ユーザー裁定により
 * 「取り込んだ本＝まだ読んでいない本を最上位に」へ反転する。すなわち:
 *   ・tier1（上層）＝未読/未接触（lastReadAt=0）: `addedAt` 降順＝最近入れた本ほど上。
 *   ・tier0（下層）＝触った本（lastReadAt>0）: `lastReadAt` 降順＝最後に触った本ほど上。
 * BookDao.getAllBooks の ORDER BY もこの二層規則に一致させている（並びの正は DAO）。
 *
 * ※受け入れたトレードオフ（ADR 0016 改訂節）: 読みかけの本が、入れたての未読新刊より下へ下がる。
 *   旧版のトレードオフ（放置本が未読新刊の上に居座る）を実使用で嫌ったユーザーが、明示的にこちらを選好した。
 */
data class RecencyKey(val tier: Int, val value: Long) : Comparable<RecencyKey> {
    override fun compareTo(other: RecencyKey): Int {
        val t = tier.compareTo(other.tier)
        return if (t != 0) t else value.compareTo(other.value)
    }
}

/** 蔵書の並び順キーを二層規則で作る（未読=tier1/addedAt が上層・触った本=tier0/lastReadAt が下層）。 */
internal fun recencyKeyOf(addedAt: Long, lastReadAt: Long): RecencyKey =
    if (lastReadAt > 0L) RecencyKey(tier = 0, value = lastReadAt)
    else RecencyKey(tier = 1, value = addedAt)

/**
 * 蔵書リストと Web由来（未取込）リストを「最近の活動順」で1本にマージする純関数。
 *
 * - 蔵書の並びは BookDao.getAllBooks（二層: 未読を上・触った本を下）が正本で、ここでは崩さない。
 *   Web 由来カードの挿入位置を決めるためだけに、同じ二層規則（recencyKeyOf）をここでも計算する。
 *   **意図的な式の重複**: DAO クエリの並びキーは SELECT 列に出ておらず、取り出すには一覧クエリの
 *   返却型ごと変える必要があり、既存呼び出し・テストへの波及が大きい。規則は recencyKeyOf の
 *   数行なので、返却型変更よりコメントで対にする方を選んだ（挙動の正は DAO 側）。
 * - Web 由来（未取込）カードも蔵書と同一の二層規則へ写像する（2026-07-16 層反転に追従）:
 *   web 読書進捗で「触った」記録がある（webLastReadAt に接触時刻がある）カードは tier0（下層）＝その
 *   最終接触時刻で並べ、未接触のカードは tier1（上層）＝発見/追加時刻（addedAt）降順で並べる。
 *   蔵書の recencyKeyOf(addedAt, lastReadAt) にそのまま食わせられる（web の lastReadAt＝最終接触時刻）。
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
    // ncode(正規化済み大文字)→web 読書の最終接触時刻(lastReadAt)。二層ソートで「触った web を下層へ沈める」ために使う。
    // なぜ episode 表示用マップと別立てか: 表示は episode(話数)・並びは接触時刻(ミリ秒)と必要な量が異なり、
    // episode を時刻代わりに使うと蔵書の lastReadAt と桁が違い層内順が壊れる（近似で並びを嘘にしない）。
    // 既定 emptyMap は既存テスト・呼び出し互換（接触時刻なしなら全 web が未接触＝上層扱いで従来どおり）。
    webLastReadAt: Map<String, Long> = emptyMap(),
): List<ShelfItem> {
    val importedNcodes = books.mapNotNull { it.ncode?.trim()?.uppercase() }.toSet()

    val bookItems = books.map { book ->
        val lastReadAt = progressMap[book.id]?.lastReadAt ?: 0L
        ShelfItem.Book(book, recencyKeyOf(book.addedAt, lastReadAt))
    }
    // Web 由来カードも蔵書と同一の二層規則でキー化する（触った=tier0/接触時刻・未接触=tier1/addedAt）。
    // 蔵書列は DAO が二層降順、web 列はここで同じキー降順に整列してから二層キーでマージする。
    val webItems: List<Pair<WebNovelEntity, RecencyKey>> = webNovels
        .filterNot { it.ncode.trim().uppercase() in importedNcodes }
        .map { it to recencyKeyOf(it.addedAt, webLastReadAt[it.ncode.trim().uppercase()] ?: 0L) }
        .sortedByDescending { it.second }

    // Web カードに読書位置を載せる。web_novels.ncode も web_reading_progress.ncode も trim+uppercase 正規化済みで
    // 保存されるため、同じ正規化キーで引ける（表記ゆれで「読んだのに続きが出ない」を防ぐ二重の安全として再正規化）。
    fun webItem(n: WebNovelEntity): ShelfItem.Web =
        ShelfItem.Web(n, webReadingProgress[n.ncode.trim().uppercase()] ?: 0)

    // 両列とも二層キー降順ソート済みの前提でマージする（books は DAO・webItems は直前の sort が保証）。
    val result = ArrayList<ShelfItem>(bookItems.size + webItems.size)
    var bi = 0
    var wi = 0
    while (bi < bookItems.size && wi < webItems.size) {
        // 二層キーで比較し、同値は蔵書優先（>= で book を先に置く）。
        if (bookItems[bi].recencyKey >= webItems[wi].second) {
            result.add(bookItems[bi]); bi++
        } else {
            result.add(webItem(webItems[wi].first)); wi++
        }
    }
    while (bi < bookItems.size) { result.add(bookItems[bi]); bi++ }
    while (wi < webItems.size) { result.add(webItem(webItems[wi].first)); wi++ }
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
