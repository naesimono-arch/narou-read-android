package com.novelreader.viewmodel

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
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

    // ---- パターン②「気分転換」（2026-07-24 追加・正本モック discovery-mood-pager-K）。
    // なろうAPIに感情の軸は無いため、タグ文化の定番語をキーワード検索へ翻訳する（word＋inKeyword）。
    TEARFUL(
        title = "泣ける・しんみり",
        cardLabel = "涙腺じんわり",
        description = "泣ける・感動タグの作品。しんみり浸りたい夜に。",
    ),
    EXHILARATING(
        title = "スカッと爽快",
        cardLabel = "痛快・無双系",
        description = "ざまぁ・痛快タグの作品。胸のすく展開を一直線に。",
    ),
    HEARTWARMING(
        title = "ほのぼの日常",
        cardLabel = "まったり癒し",
        description = "ほのぼのタグの作品。事件のない時間をゆっくりと。",
    ),
    THRILLING(
        title = "ハラハラ冒険",
        cardLabel = "手に汗にぎる",
        description = "冒険タグの作品。先が気になって止まらない旅へ。",
    ),

    // ---- パターン③「読み方で選ぶ」（2026-07-24 追加）。作品の種別・長さ・更新の軸で選ぶ。
    COMPLETE_PACK(
        title = "完結をまとめて",
        cardLabel = "完結済だけ",
        description = "完結済みの作品だけ。積み残しなく読み切れる。",
    ),
    FOLLOW_SERIAL(
        title = "連載を追いかける",
        cardLabel = "更新が新しい",
        description = "7日以内に更新された連載中の作品。今動いている物語。",
    ),
    SHORT_SET(
        title = "短編を数本",
        cardLabel = "〜1万字",
        description = "1万字までの短編。すきま時間に数本どうぞ。",
    ),
    EPIC_DIVE(
        title = "超長編にどっぷり",
        cardLabel = "50万字以上",
        description = "50万字以上の超長編。長い旅に出たい日に。",
    ),
    ;

    fun toQuery(): DiscoveryQuery = when (this) {
        SHORT_TRIP -> DiscoveryQuery(types = setOf(NarouNovelType.SHORT), time = "-30")
        // なぜ累計順か: 「一気読み」は積み上がった評価の完結作を薦めるのが自然で、
        // 週間の勢い（連載中心）とは求める軸が違うため。
        BINGE -> DiscoveryQuery(types = setOf(NarouNovelType.KANKETSU), length = "100000-", order = NarouOrder.TOTAL)
        DIALOGUE -> DiscoveryQuery(kaiwaritu = "60-")
        ILLUSTRATED -> DiscoveryQuery(sasie = "1-")
        // パターン②: キーワードはなろうタグ文化の定番語（単語1語＝AND 検索の副作用を避ける）。
        // 順位は既定 WEEKLY のまま＝「きょうの気分」の鮮度を保つ（BINGE の累計とは狙いが別）。
        TEARFUL -> DiscoveryQuery(word = "感動", inKeyword = true)
        EXHILARATING -> DiscoveryQuery(word = "ざまぁ", inKeyword = true)
        HEARTWARMING -> DiscoveryQuery(word = "ほのぼの", inKeyword = true)
        THRILLING -> DiscoveryQuery(word = "冒険", inKeyword = true)
        // パターン③: 種別・長さ・更新の機械軸。完結まとめては積み上がった評価順が自然（BINGE と同理由）。
        COMPLETE_PACK -> DiscoveryQuery(types = setOf(NarouNovelType.KANKETSU), order = NarouOrder.TOTAL)
        FOLLOW_SERIAL -> DiscoveryQuery(types = setOf(NarouNovelType.RENSAI), lastups = setOf(NarouLastup.SEVENDAY))
        SHORT_SET -> DiscoveryQuery(types = setOf(NarouNovelType.SHORT), length = "-10000")
        EPIC_DIVE -> DiscoveryQuery(length = "500000-", order = NarouOrder.TOTAL)
    }

    fun toResultContext(): ResultContext =
        ResultContext(title = title, subtitle = description, source = ResultSource.MOOD, query = toQuery())
}

/**
 * 気分パターン（4件1組・2026-07-24 ユーザー裁定「選択肢セットを複数化し日替わり＋横スワイプ」）。
 * なぜ enum の組で持つか: プリセット同様「体験の署名」＝自由編成させない。K はページャで全組へ
 * スワイプ到達でき、初期表示だけが日替わり（発見の偶然性と再現性の両立）。
 */
enum class MoodPattern(
    /** ページャのインジケータ・日替わり注記に出す短い名。 */
    val displayName: String,
    val presets: List<MoodPreset>,
) {
    CLASSIC("そのまま", listOf(MoodPreset.SHORT_TRIP, MoodPreset.BINGE, MoodPreset.DIALOGUE, MoodPreset.ILLUSTRATED)),
    REFRESH("気分転換", listOf(MoodPreset.TEARFUL, MoodPreset.EXHILARATING, MoodPreset.HEARTWARMING, MoodPreset.THRILLING)),
    STYLE("読み方で選ぶ", listOf(MoodPreset.COMPLETE_PACK, MoodPreset.FOLLOW_SERIAL, MoodPreset.SHORT_SET, MoodPreset.EPIC_DIVE)),
    ;

    companion object {
        /**
         * 日替わりの初期パターン。端末日付の epochDay 剰余＝決定的（乱数不使用）で、同じ日は必ず同じ組から
         * 始まる（再現性）。負値ガードは epochDay が 1970 以前になる異常時計対策の防御。
         */
        fun forEpochDay(epochDay: Long): MoodPattern {
            val i = ((epochDay % entries.size) + entries.size) % entries.size
            return entries[i.toInt()]
        }

        /**
         * K ページャの循環用・仮想ページ総数（2026-07-26 ユーザー裁定「端で止まらない循環スワイプ」）。
         * なぜ仮想大カウントか: Compose Pager にネイティブな循環が無いため、実組数の大倍数を仮想ページと
         * して与え [forPage] の剰余で実組へ写像する標準手法を採る。1000周は指スワイプで端に届かない量。
         */
        val LOOP_PAGE_COUNT: Int = entries.size * 1000

        /** 仮想ページ→実組の写像（循環の核）。負値ガードは forEpochDay と同じ防御（呼び出し側の仕様変更耐性）。 */
        fun forPage(page: Int): MoodPattern {
            val i = ((page % entries.size) + entries.size) % entries.size
            return entries[i]
        }

        /** 循環ページャの初期ページ＝中央帯のうち start と剰余が一致する位置（左右どちらへもほぼ等量スワイプ可）。 */
        fun loopInitialPage(start: MoodPattern): Int {
            val center = LOOP_PAGE_COUNT / 2
            return center - (center % entries.size) + start.ordinal
        }
    }
}
