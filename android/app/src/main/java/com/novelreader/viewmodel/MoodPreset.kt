package com.novelreader.viewmodel

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder

/**
 * 気分プリセット（D3）: 範囲絞り込み（length/time/kaiwaritu/sasie）を
 * 「きょうの気分」の言葉に翻訳した発見導線。ホームの最上段に置く。
 * なぜ enum か: プリセットは体験の署名であり自由編集させない（編集可能な条件は検索の条件シートが担う）。
 */
enum class MoodPreset(
    val title: String,
    /** ホームのカードに出す短い説明。 */
    val cardLabel: String,
    /** 結果一覧の文脈ヘッダに出す説明文。 */
    val description: String,
) {
    SHORT_TRIP(
        title = "30分の小さな旅",
        cardLabel = "短編 ・ 読了30分まで",
        description = "短い時間で完結する物語。読了目安30分まで・短編のみ。",
    ),
    BINGE(
        title = "今夜の一気読み",
        cardLabel = "完結済 ・ 10万字以上",
        description = "結末まで一気に。完結済み・10万字以上の作品。",
    ),
    DIALOGUE(
        title = "会話でさくさく",
        cardLabel = "会話率 60% 以上",
        description = "会話率60%以上。テンポよく読み進められる作品。",
    ),
    ILLUSTRATED(
        title = "挿絵と歩く",
        cardLabel = "挿絵のある作品",
        description = "挿絵のある作品。絵とともに進む物語。",
    ),
    ;

    fun toQuery(): DiscoveryQuery = when (this) {
        SHORT_TRIP -> DiscoveryQuery(type = NarouNovelType.SHORT, time = "-30")
        // なぜ累計順か: 「一気読み」は積み上がった評価の完結作を薦めるのが自然で、
        // 週間の勢い（連載中心）とは求める軸が違うため。
        BINGE -> DiscoveryQuery(type = NarouNovelType.KANKETSU, length = "100000-", order = NarouOrder.TOTAL)
        DIALOGUE -> DiscoveryQuery(kaiwaritu = "60-")
        ILLUSTRATED -> DiscoveryQuery(sasie = "1-")
    }

    fun toResultContext(): ResultContext =
        ResultContext(title = title, subtitle = description, source = ResultSource.MOOD, query = toQuery())
}
