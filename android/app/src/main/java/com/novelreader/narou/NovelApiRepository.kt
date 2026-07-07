package com.novelreader.narou

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.lastupApiParam
import com.novelreader.narou.model.typeApiParam
import com.novelreader.narou.network.NarouApiService
import com.novelreader.narou.network.NarouNetwork
import retrofit2.HttpException
import java.io.IOException

class NarouApiException(val userMessage: String, cause: Throwable) : Exception(userMessage, cause)

class NovelApiRepository(
    private val service: NarouApiService = NarouNetwork.service,
    private val timeSource: () -> Long = System::currentTimeMillis
) {
    // インメモリ TTL キャッシュ。キーはクエリキャッシュキーまたは "detail_" + ncode。
    private val cache = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(
        val cachedTimeMs: Long,
        val result: DiscoveryResult
    )

    companion object {
        // なぜ6時間キャッシュにするか: なろうのランキングや各種検索データは頻繁に変更されないため、
        // 頻繁なAPIアクセスを防ぎ転送量制限を回避するなろうAPIのマナーに従うため。
        const val RANKING_TTL_MS = 6 * 60 * 60 * 1000L // 6時間

        // なぜ of で項目を絞るか:
        // (1) あらすじ(story)はあらすじ非表示の一覧では転送しないことでデータ転送量を大幅に削減するため。
        // (2) genreは詳細ジャンルのラベル表示、timeは読了目安時間の表示に必要であるため。
        const val OF_LIST = "t-n-w-gp-dp-wp-mp-qp-ga-e-l-nt-g-ti"

        // なぜキャッシュ上限を50にするか:
        // 週間ランキングと異なり、ディスカバリ検索ではクエリの種類が多様になり、
        // インメモリキャッシュが際限なく膨らんでメモリを圧迫するのを防ぐため。
        const val MAX_CACHE_SIZE = 50
    }

    /**
     * キャッシュに結果を保存する。上限50を超えた場合は最古のエントリを削除する。
     */
    private fun putCache(key: String, result: DiscoveryResult, now: Long) {
        // なぜ最古のエントリを削除するか:
        // キャッシュ件数が上限を超えた場合、タイムスタンプが最も古い（最後に取得されたのが最も古い）
        // エントリを追い出すことで、直近に利用されたクエリキャッシュを効果的に保持するため。
        if (cache.size >= MAX_CACHE_SIZE && !cache.containsKey(key)) {
            val oldestKey = cache.minByOrNull { it.value.cachedTimeMs }?.key
            if (oldestKey != null) {
                cache.remove(oldestKey)
            }
        }
        cache[key] = CacheEntry(now, result)
    }

    /**
     * APIコールの例外をなろうAPIのドメイン例外に正規化する。
     */
    private inline fun <T> wrapApiException(block: () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            throw NarouApiException("なろうサーバとの通信に失敗しました（コード: ${e.code()}）。", e)
        } catch (e: IOException) {
            // UnknownHostException も IOException のサブクラス
            throw NarouApiException("ネットワークに接続できません。通信環境を確認して再試行してください。", e)
        }
    }

    /**
     * 両リストを order のソートキーで降順マージする。
     */
    internal fun mergeByOrder(
        a: List<NarouNovel>,
        b: List<NarouNovel>,
        order: NarouOrder
    ): List<NarouNovel> {
        // なぜ: 両サブクエリは API 側で既に同一 order でソート済みのため、同じキーでマージすれば全体の上位 limit 件が正しく得られる。
        val comparator = when (order) {
            NarouOrder.DAILY -> compareByDescending<NarouNovel> { it.dailyPoint ?: 0 }
            NarouOrder.WEEKLY -> compareByDescending<NarouNovel> { it.weeklyPoint ?: 0 }
            NarouOrder.MONTHLY -> compareByDescending<NarouNovel> { it.monthlyPoint ?: 0 }
            NarouOrder.QUARTER -> compareByDescending<NarouNovel> { it.quarterPoint ?: 0 }
            NarouOrder.TOTAL -> compareByDescending<NarouNovel> { it.globalPoint ?: 0 }
            NarouOrder.NEW -> compareByDescending<NarouNovel> { it.generalLastup ?: "" }
        }
        return (a + b).sortedWith(comparator)
    }

    /**
     * 汎用ディスカバリ検索を実行する。
     * キャッシュがあればそれを返し、無ければAPIから取得してキャッシュする。
     */
    suspend fun discover(query: DiscoveryQuery): DiscoveryResult {
        // SHORT+RENSAI マージ経路
        if (query.types == setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI)) {
            // なぜ: allcount は短編と連載中が排反なので単純加算で正確。
            val short = discover(query.copy(types = setOf(NarouNovelType.SHORT)))
            val rensai = discover(query.copy(types = setOf(NarouNovelType.RENSAI)))
            val mergedNovels = mergeByOrder(short.novels, rensai.novels, query.order).take(query.limit)
            return DiscoveryResult(short.allcount + rensai.allcount, mergedNovels)
        }

        val cacheKey = query.cacheKey()
        val now = timeSource()
        val cached = cache[cacheKey]

        if (cached != null && (now - cached.cachedTimeMs) < RANKING_TTL_MS) {
            return cached.result
        }

        val result = wrapApiException {
            // DiscoveryQuery から API パラメータへのマッピング
            val wordParam = query.word?.takeIf { it.isNotBlank() }
            val notWordParam = query.notWord?.takeIf { it.isNotBlank() }

            // 検索範囲。選択した項目のみ 1 を送り、非選択は送らない（全て非選択なら全項目対象）。
            // なぜ 0 を明示送信しないか: なろうAPIマニュアル§4.1は「1を指定して抽出対象にする／
            // 4項目すべて未指定なら全項目対象」としか定義しておらず、0 送信時の挙動は未定義のため
            // （実装によっては「0でも指定あり」と誤解釈されるリスクがある）。
            val titleParam = if (query.inTitle) 1 else null
            val exParam = if (query.inStory) 1 else null
            val keywordParam = if (query.inKeyword) 1 else null
            val wnameParam = if (query.inWriter) 1 else null

            val biggenreParam = query.biggenres.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("-")
            val genreParam = query.genres.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("-")

            // 同一属性が include と exclude の両方にある場合、無害化のため両側から除外する
            // なぜ：UI側でガードするが、万一混入すると isX=1&notX=1 で結果が空になり原因を追えないため防御的に落とす。
            val resolvedInclude = query.attrsInclude.filter { it !in query.attrsExclude }.toSet()
            val resolvedExclude = query.attrsExclude.filter { it !in query.attrsInclude }.toSet()

            // なぜ istt を特別に判定するか:
            // なろうAPIでは istensei=1 と istenni=1 を同時に指定すると「かつ(AND)」になってしまい、
            // どちらか一方のみを満たす作品が除外されるため、両方 true の場合は「または(OR)」を意味する istt=1 を使用する。
            val hasTensei = NarouAttr.TENSEI in resolvedInclude
            val hasTenni = NarouAttr.TENNI in resolvedInclude
            val isttParam = if (hasTensei && hasTenni) 1 else null
            val istenseiParam = if (hasTensei && !hasTenni) 1 else null
            val istenniParam = if (hasTenni && !hasTensei) 1 else null

            val nottenseiParam = if (NarouAttr.TENSEI in resolvedExclude) 1 else null
            val nottenniParam = if (NarouAttr.TENNI in resolvedExclude) 1 else null

            val iszankokuParam = if (NarouAttr.ZANKOKU in resolvedInclude) 1 else null
            val notzankokuParam = if (NarouAttr.ZANKOKU in resolvedExclude) 1 else null

            val isr15Param = if (NarouAttr.R15 in resolvedInclude) 1 else null
            val notr15Param = if (NarouAttr.R15 in resolvedExclude) 1 else null

            val isblParam = if (NarouAttr.BL in resolvedInclude) 1 else null
            val notblParam = if (NarouAttr.BL in resolvedExclude) 1 else null

            val isglParam = if (NarouAttr.GL in resolvedInclude) 1 else null
            val notglParam = if (NarouAttr.GL in resolvedExclude) 1 else null

            val list = service.search(
                of = OF_LIST,
                order = query.order.apiValue,
                lim = query.limit,
                word = wordParam,
                notword = notWordParam,
                title = titleParam,
                ex = exParam,
                keyword = keywordParam,
                wname = wnameParam,
                biggenre = biggenreParam,
                genre = genreParam,
                istensei = istenseiParam,
                istenni = istenniParam,
                istt = isttParam,
                nottensei = nottenseiParam,
                nottenni = nottenniParam,
                iszankoku = iszankokuParam,
                notzankoku = notzankokuParam,
                isr15 = isr15Param,
                notr15 = notr15Param,
                isbl = isblParam,
                notbl = notblParam,
                isgl = isglParam,
                notgl = notglParam,
                type = typeApiParam(query.types),
                lastup = lastupApiParam(query.lastups, now),
                time = query.time,
                length = query.length,
                kaiwaritu = query.kaiwaritu,
                sasie = query.sasie
            )

            // なぜ allcount を分離するか:
            // なろう小説APIはレスポンス配列の先頭要素にのみ "allcount" を格納し、
            // 2件目以降に作品情報を返す仕様になっているため。
            val allcount = list.firstOrNull()?.allcount ?: 0
            val novels = list.drop(1)

            DiscoveryResult(allcount, novels)
        }

        putCache(cacheKey, result, now)
        return result
    }

    /**
     * Nコードを指定して、1件の小説詳細を取得する。
     * キャッシュがあればそれを返し、無ければAPIから取得してキャッシュする。
     */
    suspend fun novelDetail(ncode: String): NarouNovel? {
        val trimmedNcode = ncode.trim()
        val cacheKey = "detail_$trimmedNcode"
        val now = timeSource()
        val cached = cache[cacheKey]

        if (cached != null && (now - cached.cachedTimeMs) < RANKING_TTL_MS) {
            return cached.result.novels.firstOrNull()
        }

        val result = wrapApiException {
            // なぜ of = null (全項目)を指定するか:
            // 小説詳細画面や本文リーダーなどで、小説の全情報（あらすじや各種ポイント含む）
            // を不足なく取得して表示に利用するため。
            val list = service.search(
                ncode = trimmedNcode,
                lim = 1,
                of = null
            )
            val allcount = list.firstOrNull()?.allcount ?: 0
            val novels = list.drop(1)
            DiscoveryResult(allcount, novels)
        }

        val novel = result.novels.firstOrNull()
        if (novel != null) {
            putCache(cacheKey, result, now)
        }
        return novel
    }

    fun clearCache() {
        cache.clear()
    }
}
