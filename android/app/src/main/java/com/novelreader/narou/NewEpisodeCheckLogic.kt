package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode

/**
 * U1 新着話チェックの通知1件分（Worker が通知を組み立てるのに必要な最小情報）。
 * title はなろう側でなく**手元の蔵書タイトル**を渡す想定（通知タップで開くのはローカルの本のため、
 * ユーザーが本棚で見ている名前と一致させる）。
 */
data class NewEpisodeAlert(
    /** 通知の同一性キー（通知 tag・PendingIntent requestCode の素）。なろうパス＝正規化 ncode／
     *  Web 蔵書パス＝[webNewEpisodeMarkKey]（"web:<bookId>"）。名は歴史的に ncode のまま（呼び出し全域の churn 回避）。 */
    val ncode: String,
    val bookId: String,
    val bookTitle: String,
    val newCount: Int,
    val totalAllNo: Int,
)

/**
 * 新着話の差分検知（純関数・Worker から呼ぶ）。
 *
 * 基準は「前回通知済みの話数（lastNotifiedAllNo）」であって手元の章数ではない。
 * なぜ: 章数基準だと取込むまで毎日同じ「続き N 話」通知が再送されてノイズになる。
 * 「増えたときだけ・増分だけ」を通知するのが定期チェックの礼儀。
 *
 * 初回（mark 無し）の作品は**通知せず現在値で無音初期化**する。
 * なぜ: 紐付け直後の作品は既に「続きあり」バッジが本棚で見えており、
 * 951話積みの作品を紐付けた瞬間に「951話更新！」と鳴らすのは誤報に近いため。
 *
 * @param linkedBooks 正規化済み ncode → (bookId, 蔵書タイトル)。books.ncode 紐付け作品のみ。
 * @param marks 正規化済み ncode → lastNotifiedAllNo（DB の new_episode_marks）。
 * @param currents バルク照会の結果（ncode/generalAllNo を使う。取得不能・欠落作品は黙ってスキップ）。
 * @return 通知すべきリストと、DB へ upsert する新しい基準値（ncode → allNo。チェックできた作品全部）。
 */
fun computeNewEpisodeAlerts(
    linkedBooks: Map<String, Pair<String, String>>,
    marks: Map<String, Int>,
    currents: List<NarouNovel>,
): Pair<List<NewEpisodeAlert>, Map<String, Int>> {
    val alerts = mutableListOf<NewEpisodeAlert>()
    val newMarks = mutableMapOf<String, Int>()

    for (novel in currents) {
        val ncode = novel.ncode?.let { Ncode(it).storageKey } ?: continue
        val allNo = novel.generalAllNo ?: continue
        val (bookId, bookTitle) = linkedBooks[ncode] ?: continue

        val mark = marks[ncode]
        if (mark != null && allNo > mark) {
            alerts.add(NewEpisodeAlert(ncode, bookId, bookTitle, newCount = allNo - mark, totalAllNo = allNo))
        }
        // 減少（なろう側の話数削除）でも基準は現在値へ追従させる。据え置くと、削除後に
        // 同じ話数まで再投稿されたとき「新着0件なのに増分あり」と誤検知するため。
        newMarks[ncode] = allNo
    }
    return alerts to newMarks
}

// ============================================================
// Web 蔵書パス（U1 への既読話数統合・2026-07-29 ユーザー裁定「組み込む」）
//
// 対象＝汎用Web小説DL基盤で取り込んだ本（books.sourceUrl/sourceSite 非 null・Room v21）。
// なぜ narou/ のこのファイルに同居させるか: 通知1件の形（NewEpisodeAlert）と「増えたときだけ・
// 増分だけ」の基準値前進の礼儀を、なろうパスと単一正本で共有するため。scrape/ へ置くと
// scrape→narou の依存が生まれ、P5 で確立した層分離（scrape は narou に依存しない＝
// ScrapeHttpClient の KDoc 参照）を壊す。
// ============================================================

/**
 * Web 蔵書1冊分の判定材料。Worker が Room/ファイルシステムから読み出して詰める（本関数群は IO を持たない）。
 */
data class WebBookCheckState(
    val bookId: String,
    val bookTitle: String,
    /** 取込元の作品トップ URL（books.sourceUrl）。フェッチ時の規約ゲート（SiteAdapterRegistry.resolve）へ渡す。 */
    val sourceUrl: String,
    /** 端末内に取込済みの章数（novels/<bookId>/chap_N.html の枚数）。Web 取込は目次の全章を落とす
     *  （WebBookImporter.addWebBook＝toc.chapters 全件取得・実測）ため、この値は
     *  **取込時点のサイト総話数のスナップショット**でもある＝初回差分判定のアンカーに使える。 */
    val deviceChapterCount: Int,
    /** 既読話数＝progress.lastReadFilename（chap_N.html）の N。未読は 0。
     *  なぜ web_reading_progress テーブルでないか: あちらは なろう WebView 読書（ncode キー・ADR 0012）の
     *  記録で、scrape 取込の Web 蔵書はネイティブ読書面で読む＝既読位置は PDF 蔵書と同じ
     *  progress テーブル（ProgressEntity）にある（実測）。 */
    val lastReadChapterNumber: Int,
)

/**
 * Web 蔵書の基準値キー（new_episode_marks.ncode 列に入れる値）。
 * "web:" 接頭辞で、なろうの正規化 ncode（trim+大文字の英数のみ＝コロンを含まない）と機械的に
 * 衝突しない名前空間を切る（books.ncode は紐付け時に isValidNcode 検証済み＝任意文字列は入らない）。
 * なぜ bookId 基準か: sourceUrl は正規化仕様の変更で揺れうるが bookId は不変で、本の削除時は
 * Worker の pruneExcept により基準値が自然に掃除される。
 * なぜ新テーブルにしないか: 「前回通知済みの値」という意味論がなろうの基準値と完全同型で、
 * 既存テーブルの同居ならスキーマ変更（Migration）が不要なため（列名 ncode は歴史的名残＝Entity 側コメント参照）。
 */
fun webNewEpisodeMarkKey(bookId: String): String = "web:$bookId"

/** 基準値キーの逆写像（[webNewEpisodeMarkKey] の対）。Web 蔵書のキーでなければ null＝なろうの基準値行。
 *  なぜ対で置くか: "web:" という名前空間の綴りを組む側と解く側で二重定義すると、片方だけ変えたときに
 *  無音で全件マッチしなくなる（バッジが黙って出なくなる型の欠陥）。接頭辞の知識はこの1ファイルに閉じる。 */
fun bookIdFromWebNewEpisodeMarkKey(markKey: String): String? =
    markKey.removePrefix("web:").takeIf { it != markKey && it.isNotEmpty() }

/**
 * 本棚「続きあり」バッジの Web 蔵書版＝表示すべき新着話数（null＝バッジを出さない）。
 *
 * なろう側は実時間の詳細照会（総話数）と手元章数の差で出す（ContinuationLogic.computeContinuation）。
 * Web 蔵書には実時間の照会が無い——サイトへの再フェッチは低頻度アクセスの原則（ADR 0024）で
 * Worker の1日1回に限っているため、**最後に Worker が観測したサイト総話数（new_episode_marks の基準値）**が
 * 端末の持つ唯一の観測値になる。よってバッジもその値と手元章数の差で出す＝通知と同じ増分を指す。
 *
 * @param siteTotal 基準値（[webNewEpisodeMarkKey] の行）。null＝未チェック（＝観測がない＝出さない）。
 * @param deviceChapterCount 手元の取込済み章数（chap_N.html の枚数）。0 以下は比較基準が作れず出さない
 *        （なろう版 computeContinuationCore の pdfChapterCount<=0 と同じ防御）。
 */
fun webNewEpisodeCount(siteTotal: Int?, deviceChapterCount: Int): Int? {
    if (siteTotal == null || deviceChapterCount <= 0) return null
    // 再取込で手元章数が基準値へ追いつけば差は 0 以下＝自然にバッジが消える（追いつき判定を別に持たない）。
    return (siteTotal - deviceChapterCount).takeIf { it > 0 }
}

/**
 * この Web 蔵書を今回サイト照会（目次の再フェッチ）の対象にするか＝**既読話数の統合点**。
 * 「最終章を開いた（既読話数 >= 取込済み章数）」本だけを対象にする。なぜ:
 * - 読み残しがある間は端末内に続きが既にあり、サイト側の新着は行動（再取込）につながらない
 *   ＝通知しても「まだ読んでいない本の続報」ノイズになる。
 * - なろうパスは公式APIへの1バルク照会で済むが、Web はサイトへ1蔵書=1リクエストのスクレイプ
 *   ＝低頻度アクセスの原則（ADR 0024）の礼儀上、照会は情報が行動可能な本だけに絞って最小化する。
 * - 「最終章を開いた＝追いつき」の近似は Web 系の既存判定（ShelfItems.webReadingStatusFor 近似①）と
 *   同じ割り切り（章内の末尾到達は章総量を DB に持たず観測できない）。
 * deviceChapterCount<=0 は実体欠損・抽出異常の防御（比較基準が無いため照会しない）。
 */
fun shouldCheckWebBookNow(state: WebBookCheckState): Boolean =
    state.deviceChapterCount > 0 && state.lastReadChapterNumber >= state.deviceChapterCount

/**
 * Web 蔵書の新着差分検知（純関数・Worker から呼ぶ）。
 *
 * なろうパス（[computeNewEpisodeAlerts]）との意図的な差＝**初回（基準値なし）は無音初期化ではなく
 * 「取込済み章数」を基準に即判定する**。なぜ: Web 取込は目次の全章を落とすため取込時点で
 * 端末章数＝サイト総話数が保証され、「サイト総話数 − 取込済み章数」は取込後に実際に増えた話数
 * そのもの＝なろうの「951話積み作品を紐付けた瞬間に951話通知」型の誤報が構造的に起きない。
 * 逆に無音初期化すると、初回チェックまでに増えた分（アプリからユーザーが知る術のない情報）を
 * 恒久に取りこぼす。
 *
 * @param states Worker が集めた Web 蔵書の判定材料（照会対象外の本を含んでよい＝siteTotals に無ければ何もしない）。
 * @param marks 基準値キー（[webNewEpisodeMarkKey]）→ 前回通知済みサイト総話数（なろうの基準値と同テーブル同居）。
 * @param siteTotals bookId → 今回フェッチしたサイト総話数（目次の章数）。フェッチ失敗・照会対象外の本は
 *        **含めないこと**＝その本の基準値は据え置かれる（誤った前進・巻き戻しをしない契約）。
 * @return 通知すべきリストと、upsert する新基準値（キーは [webNewEpisodeMarkKey]。チェックできた本のみ）。
 */
fun computeWebNewEpisodeAlerts(
    states: List<WebBookCheckState>,
    marks: Map<String, Int>,
    siteTotals: Map<String, Int>,
): Pair<List<NewEpisodeAlert>, Map<String, Int>> {
    val alerts = mutableListOf<NewEpisodeAlert>()
    val newMarks = mutableMapOf<String, Int>()

    for (state in states) {
        val siteTotal = siteTotals[state.bookId] ?: continue
        val key = webNewEpisodeMarkKey(state.bookId)
        // 基準＝前回通知済み値があればそれ、無ければ取込済み章数（初回即判定＝上の KDoc の理由）。
        val baseline = marks[key] ?: state.deviceChapterCount
        if (siteTotal > baseline) {
            alerts.add(
                NewEpisodeAlert(
                    ncode = key,
                    bookId = state.bookId,
                    bookTitle = state.bookTitle,
                    newCount = siteTotal - baseline,
                    totalAllNo = siteTotal,
                ),
            )
        }
        // 減少（サイト側の話数削除・非公開化）でも基準は現在値へ追従させる（なろうパスと同じ理由＝
        // 削除後に同じ話数まで再投稿されたときの誤検知防止）。
        newMarks[key] = siteTotal
    }
    return alerts to newMarks
}
