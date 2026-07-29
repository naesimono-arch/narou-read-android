// なぜ domain/ か: 本ファイルは ViewModel でない純ロジック（本棚表示モデルと並び・分類・選択の
// 純関数のみ。Android フレームワーク非依存）のため、ViewModel 実装が住む viewmodel/ から
// 「UI 状態管理を持たない業務ロジックの置き場」として独立させた（2026-07-27 構造リファクタ）。
package com.novelreader.domain

import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.narou.model.Ncode

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
 * Web 由来（未取込）カードの並び順キー。**tier 特権なし＝常に tier0** で、「自身の直近の操作時刻」
 * （触った web＝最終接触時刻・未接触＝棚に置いた時刻 addedAt）を通常キーとして並ぶ。
 *
 * なぜ蔵書の recencyKeyOf を使わないか（2026-07-26 実機ユーザー報告の裁定変更）: 旧実装は未接触 web を
 * 蔵書と同じ規則で tier1（上層）へ写像していたが、実棚では蔵書がほぼ全て「触った本」（tier0）になるため、
 * 未接触 web カードが唯一の tier1 住人として**恒久最上位に張り付き**、直近に取り込んだ・読んだ作品を
 * 差し置いた（tier が値に優先する比較ゆえ、読んだ直後の本 tier0 は何をしても上回れない）。
 * tier1 は「取込という意図的操作を経た未読の実蔵書」の特権に限定し、web カード（発見メモ・取込前）は
 * 特権なしの通常キーへ降ろす＝ADR 0016 初版の「Web 由来カードは tier0 扱い」への部分回帰（要 ADR 追記）。
 */
internal fun webRecencyKeyOf(addedAt: Long, lastReadAt: Long): RecencyKey =
    RecencyKey(tier = 0, value = if (lastReadAt > 0L) lastReadAt else addedAt)

/**
 * 蔵書へ取り込み済みの作品を表す ncode の保存キー集合（trim+大文字＝[Ncode.storageKey]）。
 * 「自然昇格」（取込済みの Web カードを棚から引っ込める）判定の単一正本で、[activeWebNovels] と
 * [mergeShelfItems] が共有する（規則を二重実装せず、数える側と並べる側が食い違わないようにする）。
 */
internal fun importedNcodeKeys(books: List<BookEntity>): Set<String> =
    books.mapNotNull { b -> b.ncode?.let { Ncode(it).storageKey } }.toSet()

/**
 * 「自然昇格」を適用した Web由来カードの正味一覧＝**蔵書へ取り込み済み（books.ncode 一致）の行を落とす**。
 *
 * なぜ必要か（2026-07-29 実機報告「本棚ヘッダの冊数が実際とずれる」の真因）: なろう作品を「本棚に置く」と
 * web_novels に行が入り、その作品を取り込んでも（ADR 0011 の縦書きPDF取り込み・NcodeLinkSheet の手動紐付け）
 * web_novels 行は**意図的に残す**設計になっている（棚からは昇格で引っ込め、行の掃除はユーザー操作に委ねる）。
 * ところが昇格規則は一覧生成 [mergeShelfItems] の内側にしか無かったため、同じ web_novels を素で数える側
 * ——ヘッダ冊数（`books.size + webNovels.size`）と状態チップ件数（[shelfStatusCounts]）——は、棚に1枚も
 * 出ていないゴースト行まで数えていた。結果、ヘッダの冊数が実カード枚数より「取込済み Web 行の数」だけ多くなる。
 *
 * そこで昇格を**棚データの供給点で一度だけ**適用し、数える側・絞る側・並べる側が同じ正味リストを見るようにする
 * （適用箇所＝BookshelfViewModel.uiState。表示側で辻褄を合わせるのではなく、ゴースト行を棚データへ入れない）。
 */
fun activeWebNovels(books: List<BookEntity>, webNovels: List<WebNovelEntity>): List<WebNovelEntity> {
    val imported = importedNcodeKeys(books)
    // 比較は保存時正規化（trim+大文字）と同じ形で行う＝表記ゆれで昇格が漏れないようにする。
    return webNovels.filterNot { Ncode(it.ncode).storageKey in imported }
}

/**
 * 蔵書リストと Web由来（未取込）リストを「最近の活動順」で1本にマージする純関数。
 *
 * - 蔵書の並びは BookDao.getAllBooks（二層: 未読を上・触った本を下）が正本で、ここでは崩さない。
 *   Web 由来カードの挿入位置を決めるためだけに、同じ二層規則（recencyKeyOf）をここでも計算する。
 *   **意図的な式の重複**: DAO クエリの並びキーは SELECT 列に出ておらず、取り出すには一覧クエリの
 *   返却型ごと変える必要があり、既存呼び出し・テストへの波及が大きい。規則は recencyKeyOf の
 *   数行なので、返却型変更よりコメントで対にする方を選んだ（挙動の正は DAO 側）。
 * - Web 由来（未取込）カードは **tier 特権なし（常に tier0）** で「直近の操作時刻」により並ぶ
 *   （webRecencyKeyOf。触った web＝最終接触時刻・未接触＝addedAt）。未接触 web を tier1 へ写像すると
 *   実棚（蔵書が全て tier0）で恒久最上位に張り付くための裁定変更＝2026-07-26。詳細は webRecencyKeyOf の KDoc。
 * - 取込済み（books.ncode と同一 ncode）の Web カードは非表示にする＝PDF 取込が完了した時点で
 *   蔵書カードへ「自然昇格」し、二重表示しない。比較は保存時正規化（trim+uppercase）と同じ形で行う。
 *   ※この昇格は 2026-07-29 以降 [activeWebNovels] が棚データの供給点（BookshelfViewModel.uiState）で
 *   適用済みのため、本関数内の除外は通常 no-op。それでも残すのは、純関数を直接呼ぶテスト・将来の別供給経路
 *   への防御網として。なお本関数の books は状態フィルタ後の部分集合が渡りうる（filterShelfByStatus →
 *   mergeShelfItems の順で使う）ため、**ここだけでは昇格を取りこぼす**（フィルタで蔵書側が落ちるとゴースト
 *   Web カードが復活する）。昇格の正は供給点側であって、この行ではない。
 * - 同値キーのときは蔵書を先に置く（読める実体がある方が優先という判断）。
 */
fun mergeShelfItems(
    books: List<BookEntity>,
    progressMap: Map<String, ProgressEntity>,
    webNovels: List<WebNovelEntity>,
    // 機能②: ncode(正規化済み大文字)→最後に開いた話。Web カードの「続きから読む 第N話」表示に使う。
    // 既定 emptyList 相当（emptyMap）は既存テスト・呼び出しの互換のため（読書位置なしなら全カード未読表示で不変）。
    webReadingProgress: Map<String, Int> = emptyMap(),
    // ncode(正規化済み大文字)→web 読書の最終接触時刻(lastReadAt)。web カードの並びキー（触った web は接触時刻で並ぶ）に使う。
    // なぜ episode 表示用マップと別立てか: 表示は episode(話数)・並びは接触時刻(ミリ秒)と必要な量が異なり、
    // episode を時刻代わりに使うと蔵書の lastReadAt と桁が違い層内順が壊れる（近似で並びを嘘にしない）。
    // 既定 emptyMap は既存テスト・呼び出し互換（接触時刻なしなら全 web が未接触＝自身の addedAt で並ぶ）。
    webLastReadAt: Map<String, Long> = emptyMap(),
): List<ShelfItem> {
    val importedNcodes = importedNcodeKeys(books)

    val bookItems = books.map { book ->
        val lastReadAt = progressMap[book.id]?.lastReadAt ?: 0L
        ShelfItem.Book(book, recencyKeyOf(book.addedAt, lastReadAt))
    }
    // Web 由来カードは tier 特権なしの通常キー（webRecencyKeyOf＝常に tier0・直近の操作時刻）でキー化する。
    // 蔵書列は DAO が二層降順、web 列はここでキー降順に整列してから同じ RecencyKey 比較でマージする。
    val webItems: List<Pair<WebNovelEntity, RecencyKey>> = webNovels
        .filterNot { Ncode(it.ncode).storageKey in importedNcodes }
        .map { it to webRecencyKeyOf(it.addedAt, webLastReadAt[Ncode(it.ncode).storageKey] ?: 0L) }
        .sortedByDescending { it.second }

    // Web カードに読書位置を載せる。web_novels.ncode も web_reading_progress.ncode も trim+uppercase 正規化済みで
    // 保存されるため、同じ正規化キーで引ける（表記ゆれで「読んだのに続きが出ない」を防ぐ二重の安全として再正規化）。
    fun webItem(n: WebNovelEntity): ShelfItem.Web =
        ShelfItem.Web(n, webReadingProgress[Ncode(n.ncode).storageKey] ?: 0)

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
 * Web作品（web_novels 由来・未取込）1件の読書状態を分類する（本棚フィルタ「よみかけ/未読/読了」用）。
 *
 * 判定規則（UI仕様として確定・2点の割り切り＝近似を含む）:
 *   ・未読     = 読書位置なし（lastReadEpisode==null）または lastReadEpisode==0
 *   ・よみかけ = lastReadEpisode>0 かつ 読了条件を満たさない
 *   ・読了     = generalAllNo>0 かつ lastReadEpisode>=generalAllNo
 *
 * なぜ近似①「最終話を開いた＝読了」か: Web は WebView 読書ゆえ記録できるのは「最後に開いた話数
 *   (lastReadEpisode)」だけで、話の末尾まで読み切った実績（蔵書側 reachedEnd に相当するもの）を持てない。
 *   最終話を開いた事実をもって読了とみなす割り切りで代替する。
 * なぜ近似②「generalAllNo は登録時スナップショット」か: 全話数は本棚登録時点の値で、その後の連載更新には
 *   追随しない。連載が伸びれば実際は未完でも「最終話到達」と判定し得るが、基準点を登録時の1値に固定する割り切り。
 * 蔵書側 readingStatusFor との対比: あちらは末尾到達フラグ reachedEnd の実績にのみ読了を結ぶ厳密判定
 *   （ShelfItems.kt の readingStatusFor 参照）。Web は末尾実績を持てず非対称ゆえ、上記近似で代替する。
 * generalAllNo<=0 のガード: 話数不明の作品を「読了」へ誤分類しないよう読了条件から外し、未読/よみかけへ落とす。
 */
fun webReadingStatusFor(lastReadEpisode: Int?, generalAllNo: Int): ReadingStatus {
    val ep = lastReadEpisode ?: 0
    return when {
        ep <= 0 -> ReadingStatus.UNREAD
        generalAllNo > 0 && ep >= generalAllNo -> ReadingStatus.FINISHED
        else -> ReadingStatus.READING
    }
}

/**
 * 読書状態フィルタの純関数（mergeShelfItems の前段に噛ませる）。
 *
 * - selectedStatus=null（「すべて」）は無加工で素通しする。
 * - books は各本の読書状態（readingStatusFor）が selectedStatus に一致するものだけ残す。
 * - Web由来カードも webReadingStatusFor で分類し selectedStatus 一致のみ残す（Web作品も未読/読みかけ/読了
 *   フィルタへ正しく分類される＝本来の挙動）。
 *
 * webReadingProgress を必須（非null）にしている理由: これを省略できると、新スキンが本関数を呼ぶ際に
 * 配線を忘れても無音でコンパイルが通り、Web 作品が全フィルタから消える（＝本バグの再発）。コンパイル時に
 * 全呼び出し元へ配線を強制するため、後方互換の null フォールバックは撤去した。Web 判定材料が無い場面では
 * emptyMap を明示的に渡す（＝全 Web を進捗ゼロ＝未読として分類する）こと。
 */
fun filterShelfByStatus(
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    selectedStatus: ReadingStatus?,
    progressMap: Map<String, ProgressEntity>,
    chapterCounts: Map<String, Int>,
    // ncode(正規化済み大文字)→最後に開いた話。Web作品の状態分類(webReadingStatusFor)に使う。必須（上のドキュメント参照）。
    webReadingProgress: Map<String, Int>,
): Pair<List<BookEntity>, List<WebNovelEntity>> {
    if (selectedStatus == null) return books to webNovels
    val filteredBooks = books.filter {
        readingStatusFor(progressMap[it.id], chapterCounts[it.id] ?: 0) == selectedStatus
    }
    // Web も状態分類して該当のみ残す。ncode 再正規化は参照側 mergeShelfItems（webItem）と揃える
    // ＝保存時正規化(trim+uppercase)と同形で引き、表記ゆれで分類が漏れるのを防ぐ二重の安全。
    val filteredWeb = webNovels.filter {
        webReadingStatusFor(
            webReadingProgress[Ncode(it.ncode).storageKey],
            it.generalAllNo,
        ) == selectedStatus
    }
    return filteredBooks to filteredWeb
}

/**
 * 状態チップの件数集計（純関数）。蔵書は readingStatusFor、Web作品は webReadingStatusFor で分類し、
 * 同一 ReadingStatus へ合流して数える。
 *
 * なぜ Web を含めるか: 状態フィルタ（filterShelfByStatus）が Web も分類対象にしたため、チップの件数だけ
 * 蔵書のみだと「READING が 0 件で dim なのにフィルタすると Web が出る」といった件数と表示の食い違いが起きる。
 * 判定は filterShelfByStatus と同じ2関数を共有し、チップ件数と絞り込み後の実件数を必ず一致させる（独自再実装しない）。
 */
fun shelfStatusCounts(
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    progressMap: Map<String, ProgressEntity>,
    chapterCounts: Map<String, Int>,
    webReadingProgress: Map<String, Int>,
): Map<ReadingStatus, Int> {
    val counts = mutableMapOf<ReadingStatus, Int>()
    books.forEach {
        val s = readingStatusFor(progressMap[it.id], chapterCounts[it.id] ?: 0)
        counts[s] = (counts[s] ?: 0) + 1
    }
    webNovels.forEach {
        val s = webReadingStatusFor(webReadingProgress[Ncode(it.ncode).storageKey], it.generalAllNo)
        counts[s] = (counts[s] ?: 0) + 1
    }
    return counts
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

// ============================================================
// 複数選択削除に Web由来カードを統合するための純ロジック（系3・2026-07-24）。
//
// 選択キーの接頭辞規約: Web由来（未取込）カードは ShelfItem.Web.key＝"web:<ncode>" で選択集合に載る。
// 蔵書は従来どおり bare な book.id で載る（"web:" 接頭辞を持たない）。両者は "web:" の有無で機械的に分離できる
// （book.id は Room 生成の ID で "web:" 始まりにはならない前提）。この非対称（web だけ接頭辞）を採ったのは、
// 全スキン共有の selectedIds を蔵書側は無改変のまま保ち、Web参加を差分で足すため（M/P/J 一覧の蔵書選択を壊さない）。
// ============================================================

/** 選択キー接頭辞（ShelfItem.Web.key と同一）。Web由来カードの選択キーは "web:<ncode>"。 */
const val WEB_SELECTION_KEY_PREFIX: String = "web:"

/** 選択キー一覧から Web由来（"web:<ncode>"）の ncode 一覧を取り出す純関数（蔵書の bare id は除外）。 */
fun webNcodesInSelection(selectedKeys: List<String>): List<String> =
    selectedKeys.filter { it.startsWith(WEB_SELECTION_KEY_PREFIX) }
        .map { it.removePrefix(WEB_SELECTION_KEY_PREFIX) }

/** 選択キー一覧から蔵書（"web:" 接頭辞でない＝bare book.id）の id 一覧を取り出す純関数。 */
fun bookIdsInSelection(selectedKeys: List<String>): List<String> =
    selectedKeys.filterNot { it.startsWith(WEB_SELECTION_KEY_PREFIX) }

/**
 * 複数選択削除の確認ダイアログ本文を、選択内訳（蔵書数・Web数）で出し分ける純関数。
 *
 * なぜ出し分けるか: 現行の一律文言「変換済みの本文データも削除されます」は Web作品には虚偽＝Web は本文データを
 * 端末に持たず（WebView 読書）、外すことで失うものが無く再検索で即復元できる。内訳ごとに正しい不可逆性を伝える:
 *  ・蔵書のみ = 本文データも消える不可逆／・Webのみ = 失うもの無し・再検索で戻せる／・混在 = 両者を併記。
 */
fun deleteConfirmBody(bookCount: Int, webCount: Int): String = when {
    webCount <= 0 ->
        // 蔵書のみ（従来文言を維持）。bookCount==0 の異常系もここへ落ちるが、削除は count>0 でしか到達しない。
        "変換済みの本文データも削除されます。この操作は取り消せません。"
    bookCount <= 0 ->
        // Web のみ＝失うもの無し・再検索で即復元可（現行文言が虚偽になる対象）。
        "本棚から外します。Web作品は失うものがなく、あとで再検索すればすぐ戻せます。"
    else ->
        // 混在＝蔵書の不可逆と Web の可逆を併記。
        "蔵書は変換済みの本文データも削除され、取り消せません（Web作品は本棚から外すだけで、再検索で戻せます）。"
}
