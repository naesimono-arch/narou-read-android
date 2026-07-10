package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel

/**
 * U1 新着話チェックの通知1件分（Worker が通知を組み立てるのに必要な最小情報）。
 * title はなろう側でなく**手元の蔵書タイトル**を渡す想定（通知タップで開くのはローカルの本のため、
 * ユーザーが本棚で見ている名前と一致させる）。
 */
data class NewEpisodeAlert(
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
        val ncode = novel.ncode?.trim()?.uppercase() ?: continue
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
