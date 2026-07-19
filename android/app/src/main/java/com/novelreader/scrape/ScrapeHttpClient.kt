package com.novelreader.scrape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `scrape/` 専用の HTTP クライアント。
 *
 * なぜ `narou/network/NarouNetwork` を流用しないか: あちらは BASE_URL=api.syosetu.com 固定の Retrofit ＝
 * 任意サイトの任意 URL を GET できない。ここは素の OkHttp で任意 URL を取る。
 *
 * 行儀（handover 確定事項＝脆さ織り込み・低頻度アクセス）:
 * - 意味のある User-Agent を送る（なろう同様の作法）。
 * - **Crawl-delay: 直近リクエストから最低 [minIntervalMs] 空ける**（既定 1000ms＝カクヨム robots の Crawl-delay:1 準拠）。
 *   連続章 DL でサイトへ過負荷をかけないための保険。単一プロセス内の直列化のみ（分散前提でない）。
 */
class ScrapeHttpClient(
    private val client: OkHttpClient = defaultClient,
    private val minIntervalMs: Long = 1000L,
    /** テスト用に時刻源と待機を差し替え可能にする（本番は System.currentTimeMillis / delay）。 */
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()
    private var lastRequestAt = 0L

    /** URL を GET して本文文字列を返す。Crawl-delay を挟み、非 2xx は例外。 */
    suspend fun getString(url: String): String {
        throttle()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ScrapeException("HTTP ${resp.code} for $url")
                }
                resp.body?.string() ?: throw ScrapeException("empty body for $url")
            }
        }
    }

    /** 直近リクエストからの経過が [minIntervalMs] 未満なら差分だけ待つ（複数コルーチンでも直列に）。 */
    private suspend fun throttle() {
        gate.withLock {
            val wait = minIntervalMs - (nowMs() - lastRequestAt)
            if (wait > 0) sleep(wait)
            lastRequestAt = nowMs()
        }
    }

    companion object {
        const val USER_AGENT = "NovelReader-Android/1.0"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }
    }
}

/** scrape 層の取得・解析失敗を表す例外（呼び出し側でフォールバック導線へ落とす）。
 *  open＝サブ型（構造変更の疑い [ScrapeStructureException]）で意味を細分できるようにする（`is ScrapeException` 捕捉は不変）。 */
open class ScrapeException(message: String, cause: Throwable? = null) : Exception(message, cause)
