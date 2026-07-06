package com.novelreader.narou

import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.network.NarouApiService
import com.novelreader.narou.network.NarouNetwork
import retrofit2.HttpException
import java.io.IOException

class NarouApiException(val userMessage: String, cause: Throwable) : Exception(userMessage, cause)

class NovelApiRepository(
    private val service: NarouApiService = NarouNetwork.service,
    private val timeSource: () -> Long = System::currentTimeMillis
) {
    // インメモリ TTL キャッシュ。キーは "order_limit" とする。
    private val cache = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(
        val cachedTimeMs: Long,
        val result: DiscoveryResult
    )

    companion object {
        // なぜ6時間キャッシュにするか: なろうのランキングは日次更新で頻繁に変更されないため、
        // 頻繁なAPIアクセスを防ぎ転送量制限を回避するなろうAPIのマナーに従うため。
        const val RANKING_TTL_MS = 6 * 60 * 60 * 1000L // 6時間
    }

    /**
     * なろう週間ランキングを取得する。
     * キャッシュがあればそれを返し、無ければAPIから取得してキャッシュする。
     */
    suspend fun weeklyRanking(limit: Int = 30): DiscoveryResult {
        val cacheKey = "weekly_$limit"
        val now = timeSource()
        val cached = cache[cacheKey]

        if (cached != null && (now - cached.cachedTimeMs) < RANKING_TTL_MS) {
            return cached.result
        }

        try {
            // なぜ of で項目を絞るか: なろう小説API利用マニュアル§6.1に従い、
            // 必要な項目のみを絞り込むことでデータ転送量を軽減し、利用制限を回避するため。
            val list = service.search(
                of = "t-n-w-s-gp-ga-e-l-nt",
                order = "weekly",
                lim = limit
            )

            // なぜ allcount を分離するか:
            // なろう小説APIはレスポンス配列の先頭要素にのみ "allcount" を格納し、
            // 2件目以降に作品情報を返す仕様になっているため。
            val allcount = list.firstOrNull()?.allcount ?: 0
            val novels = list.drop(1)

            val result = DiscoveryResult(allcount, novels)
            cache[cacheKey] = CacheEntry(now, result)
            return result
        } catch (e: HttpException) {
            throw NarouApiException("なろうサーバとの通信に失敗しました（コード: ${e.code()}）。", e)
        } catch (e: IOException) {
            // UnknownHostException も IOException のサブクラス
            throw NarouApiException("ネットワークに接続できません。通信環境を確認して再試行してください。", e)
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
