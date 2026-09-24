package com.novelreader.typeset

/**
 * [FontMetricsProvider] が測った縦送りを `(unitText, charClass, sizePx)` で覚える薄い覆い（改善 D の本体）。
 *
 * ## なぜ「定数化」ではなくキャッシュなのか
 * 縦送りを `fontSizePx` 一律と決め打つ案（D のもう半分）は**不採用**＝書体差で版面がずれる
 * （P0-1 実測で serif の小書き仮名の advance が 64→65px に割れた。根拠は [FontMetricsProvider] の KDoc が正本）。
 * この型は値を**作らない**——delegate が実際に測った値をそのまま配るだけなので、版面はキャッシュの有無で
 * 1ビットも変わらない（機械での担保＝`CachedAdvanceLayoutIdentityTest`：実データ全行を両経路で組んで
 * [ParagraphLayout] を丸ごと突き合わせる）。
 *
 * ## なぜ3つ全部がキーなのか（どれか1つでも落とすと版面が壊れる）
 * - `unitText`: 字が違えば幅が違う。
 * - `charClass`: 同じ "3" でも UPRIGHT（単独ラン＝em マス）と ROTATE（4字以上ランの1字＝半角幅）で
 *   縦送りが違う。落とすと先に来た向きの値が後から来た向きへ配られ、「3日」で字面が接触した
 *   golden 監査 2026-08-06 G-3 と**同じ壊れ方**を再現する。
 * - `sizePx`: 本文・ルビ・章見出し・見出しルビは**同じ章の中で別サイズ**を引く（1回の組版の中でも
 *   本文 `fontSizePx` とルビ `rubyFontSizePx` の2種が走る）。落とすとルビが本文の寸法で置かれる。
 *
 * ## 寿命＝この覆い1個（＝組版器1個＝章1つ）。プロセス常駐の1個にはしない
 * 書体は delegate インスタンスの属性で、**キーに入っていない**。プロセス共有にすると別書体で作られた
 * [com.novelreader.typeset.render.PaintFontMetrics] の実測値が混ざりうる＝D が名指しで否定した
 * 「書体差で版面がずれる」を自分の手で作ることになる。章を跨いだ再利用を捨てる代価はミス1回
 * ＝`Paint.measureText` 1回ぶんで、リスクと桁が違う。
 *
 * ## スレッド
 * 版面は背景（`Dispatchers.Default`＝[ChapterTypesetStore.typeset]）と composition 段
 * （[ChapterTypesetStore.typesetInComposition]）の両方から組まれ、**寸法源はその1インスタンスを共有**する。
 * よって表の読み書きは錠で守る。delegate 呼び出しまで錠の中に入れてあるのは意図的で、
 * `PaintFontMetrics` は `Paint` を1本使い回して `textSize` を書き換えてから測る＝2スレッドが同時に入ると
 * **他方のサイズで測った値**が返りうる（`Paint` は thread-safe ではない）。ここで直列化すると、
 * 縦送りの経路に限ってはその競合が起こらなくなる。
 * ⚠️ これは D の副次効果であって、実機で観測された不具合の修正ではない（潜在的な競合を閉じただけ）。
 */
class CachingFontMetrics(
    private val delegate: FontMetricsProvider,
    /**
     * 抱えるエントリ数の上限。超えたら**最後に使われたのが最も古い**ものから捨てる（LRU）。
     *
     * なぜ上限が要るか: キーの母数は〈章に出る異なりユニット数〉×〈生きているサイズ数〉で、
     * 後者はスライダーが動くたびに増える（フォントは離散10値・1ドラッグで必ず全値を舐める）。
     * 上限なしだと「回数は減ったが常駐メモリが青天井」になる＝[ChapterTypesetStore] の
     * グリフ予算を置いたのと同じ理由でここにも要る。
     *
     * なぜ 4,096 か: 定常（本文・ルビ・見出し・見出しルビの4サイズ）で必要なのは
     * 異なりユニット数 × 4。1章数千字の異なり字は 500〜1,000 程度（常用漢字 2,136 が上界の目安）＝
     * 定常 2,000〜4,000 を丸ごと収め、ドラッグで積んだ他サイズの残骸だけが古い順に落ちる水準。
     * メモリは1エントリ ≈ 100B（キー参照2つ＋float 2つ＋`HashMap.Entry`）＝4,096 で約 0.4MB と、
     * 版面予算（12,000グリフ ≈ 1MB 強）と同じ桁に収まる。**外したときの罰はミス1回＝`measureText` 1回**
     * （版面は変わらない・遅くなるだけ）なので、上限を攻める必要がそもそも無い。
     *
     * なぜ「サイズが変わったら全捨て」でなく LRU か: ドラッグは 14→24→14 と往復する。
     * 直前のサイズを捨てる方式だと戻りで全ミスになり、ドラッグ中こそ効かないキャッシュになる。
     */
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : FontMetricsProvider {

    init {
        require(maxEntries >= 1) { "maxEntries は1以上（0は「毎回ミス」＝覆う意味が無い）: $maxEntries" }
    }

    private val lock = Any()

    // accessOrder=true＝get でも順序が更新される LRU。removeEldestEntry で上限超過を1件ずつ落とす。
    // 同じ形（LinkedHashMap の LRU）を章本文キャッシュ・版面の常駐表でも使っている＝機序を揃えてある。
    private val cache = object : LinkedHashMap<AdvanceKey, Float>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AdvanceKey, Float>): Boolean =
            size > maxEntries
    }

    private var hits = 0L

    private var misses = 0L

    override fun verticalAdvance(unitText: String, charClass: CharClass, fontSizePx: Float): Float {
        val key = AdvanceKey(unitText, charClass, fontSizePx)
        synchronized(lock) {
            val cached = cache[key]
            if (cached != null) {
                hits++
                return cached
            }
            val measured = delegate.verticalAdvance(unitText, charClass, fontSizePx)
            cache[key] = measured
            misses++
            return measured
        }
    }

    /**
     * 横幅は**覚えない**（素通し）。
     *
     * なぜ覚えないか: キーが任意長の文字列＝母数が字種でなく文字列の組合せで、上限の根拠が置けない。
     * しかも縦組みの本番経路にこの呼び出しは1つも無い（現状の呼び手は単体テストだけ）＝
     * 覚える利得がゼロで、青天井のリスクだけが残る。錠は通す——delegate が同じ `Paint` を共有するため、
     * 直列化の理由は縦送りと同じ（クラス KDoc の「スレッド」）。
     */
    override fun horizontalAdvance(text: String, fontSizePx: Float): Float =
        synchronized(lock) { delegate.horizontalAdvance(text, fontSizePx) }

    /** 当たり外れと常駐エントリ数の観測値（キャッシュが実際に効いていることをテストで固定するため）。 */
    fun stats(): Stats = synchronized(lock) { Stats(hits = hits, misses = misses, entries = cache.size) }

    /** [stats] の返り値。`misses` が delegate（＝実測）を叩いた回数そのもの。 */
    data class Stats(val hits: Long, val misses: Long, val entries: Int)

    /**
     * キャッシュキー。
     *
     * ⚠️ `sizePx` の同値判定は data class が生成する `Float.compare` 意味論（-0.0f と 0.0f は別・NaN 同士は同値）。
     * どちらも「同じキー＝同じ実測値」を壊さない（前者は余計なミスが1回増えるだけ、後者は delegate が
     * NaN に対して返す値を配るだけ）ため、寸法源としての正しさには影響しない。
     */
    private data class AdvanceKey(val unitText: String, val charClass: CharClass, val sizePx: Float)

    companion object {
        /** 既定の上限（根拠は [maxEntries] の KDoc）。 */
        const val DEFAULT_MAX_ENTRIES: Int = 4_096

        private const val INITIAL_CAPACITY = 512

        private const val LOAD_FACTOR = 0.75f

        /**
         * 二重に覆わない包み方。すでにこの覆いなら**そのまま**返す。
         * なぜ要るか: 覆いを足す場所が組版器の中（[DefaultVerticalTypesetter]）なので、呼び出し側が
         * 自前で覆って渡すと表が2枚できて、上の段だけが当たり下の段は死蔵メモリになる。
         */
        fun wrapping(delegate: FontMetricsProvider): FontMetricsProvider =
            if (delegate is CachingFontMetrics) delegate else CachingFontMetrics(delegate)
    }
}
