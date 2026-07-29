package com.novelreader.scrape

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * `scrape/` 専用の HTTP クライアント。
 *
 * なぜ `narou/network/NarouNetwork` を流用しないか: あちらは BASE_URL=api.syosetu.com 固定の Retrofit ＝
 * 任意サイトの任意 URL を GET できない。ここは素の OkHttp で任意 URL を取る。
 *
 * 行儀（handover 確定事項＝脆さ織り込み・低頻度アクセス／新サイトアダプタ増設の土台）:
 * - 意味のある User-Agent を送る（なろう同様の作法）。
 * - **per-host スロットル**: ホスト別に直近リクエスト時刻を保持し、次リクエストは
 *   「そのホストの [crawlDelayMs]（アダプタが宣言）」と「全ホスト横断のグローバル床 [globalFloorMs]」の
 *   **両方**を満たすまで待つ。単一プロセス内の直列化のみ（分散前提でない）。
 * - **リトライ/バックオフ**: HTTP 429/503 と一過性の IOException のみ、Full Jitter で最大 [maxRetries] 回だけ
 *   再試行し、`Retry-After`（秒数値）を下限として尊重する。403/404 等の恒久失敗は再試行しない（[isRetryable]）。
 *
 * なぜ narou の `retryWithBackoff` を import せず自前で持つか: P5 で確立した層分離（scrape は narou に依存しない）を
 * 守るため。ロジックは同型（Full Jitter＋Retry-After 尊重）だが、層をまたぐ import を新設しない方針を優先し、
 * 意図的に重複させる（narou 側の変更が scrape の取得挙動を巻き込むのを防ぐ）。
 */
class ScrapeHttpClient(
    private val client: OkHttpClient = defaultClient,
    /** 全ホスト横断の最低間隔（グローバル床）。既定 1000ms。 */
    private val globalFloorMs: Long = 1000L,
    /** 追加試行回数（総試行 = 1 + maxRetries）。既定2＝合計最大3回で体感の待ち（数秒）に収める。 */
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 500L,
    private val maxDelayMs: Long = 4_000L,
    /** テスト用に時刻源・待機・乱数を差し替え可能にする（本番は System.currentTimeMillis / delay / Random.Default）。 */
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default,
    /** 1回分の生 GET（成功で本文・非2xxで [HttpStatusException]・空本文で [ScrapeException]）。テストの継ぎ目。 */
    private val fetch: suspend (String) -> String = defaultFetch(client),
) {
    private val gate = Mutex()
    private var lastRequestAtAny = 0L
    private val lastRequestByHost = HashMap<String, Long>()

    /**
     * URL を GET して本文文字列を返す。per-host スロットルを挟み、一過性失敗は Full Jitter で再試行する。
     * @param crawlDelayMs このホストへ課す最低間隔（アダプタの [NovelSiteAdapter.crawlDelayMs]）。既定はグローバル床。
     */
    suspend fun getString(url: String, crawlDelayMs: Long = globalFloorMs): String {
        val host = hostOf(url)
        var attempt = 0
        while (true) {
            throttle(host, crawlDelayMs)
            try {
                return fetch(url)
            } catch (e: CancellationException) {
                // 協調キャンセルはエラーでなく制御フロー＝再試行せず素通し（呼び出し側のスコープ終了を尊重）。
                throw e
            } catch (e: Throwable) {
                // 上限到達・非再試行例外はここで確定失敗（呼び出し側契約の [ScrapeException] へ正規化して投げる）。
                if (attempt >= maxRetries || !isRetryable(e)) throw asScrapeException(e, url)
                // Full Jitter（AWS流）: sleep = random[0, min(cap, base*2^attempt)]。指数で上限へ寄せつつ
                // 同時失敗した群れの再試行タイミングを散らして再輻輳（thundering herd）を防ぐ。
                // Retry-After が明示されていればそれを下限として尊重する（429/503 のサーバ指示）。
                val expCap = (baseDelayMs shl attempt).coerceIn(1L, maxDelayMs)
                val jitter = random.nextLong(expCap + 1)
                val wait = maxOf(jitter, retryAfterMsOf(e) ?: 0L)
                sleep(wait)
                attempt++
            }
        }
    }

    /**
     * この例外を再試行してよいか（症状でなく分類で判断）。
     * **403 は即時中止（リトライ禁止）**——競合サービスの実測作法で、403 はレート/認証由来の「これ以上叩くな」の
     * 合図であり、再試行は BAN を早める。404 も対象不在＝再試行しても変わらない恒久失敗（[isRetryable] の既定 false 側）。
     * 再試行するのは 429/503（一時的な混雑・メンテ）と一過性 IOException（timeout/DNS/瞬断）のみ。
     */
    private fun isRetryable(e: Throwable): Boolean = when (e) {
        is HttpStatusException -> e.code == 429 || e.code == 503
        is IOException -> true
        else -> false // ScrapeException（空本文・パース失敗）等は再試行で直らない恒久失敗
    }

    /** 429/503 の Retry-After（秒）をミリ秒で返す。HTTP-date 形式は解釈せず null（＝Full Jitter へフォールバック）。 */
    private fun retryAfterMsOf(e: Throwable): Long? =
        (e as? HttpStatusException)?.retryAfterSeconds?.times(1000L)

    /** 確定失敗を呼び出し側契約の [ScrapeException] へ正規化（既に ScrapeException ならそのまま）。原因は cause に保全。 */
    private fun asScrapeException(e: Throwable, url: String): ScrapeException = when (e) {
        is ScrapeException -> e
        is HttpStatusException -> ScrapeException("HTTP ${e.code} for $url", e)
        else -> ScrapeException("fetch failed for $url", e)
    }

    /**
     * per-host スロットル: ホスト別 crawlDelay と全ホスト横断のグローバル床の両方を満たすまで待つ。
     * なぜグローバル床が要るか: 新サイトアダプタ増設で複数ホストへ同時 DL する際、各ホストが自分の
     * crawlDelay だけ守っても端末→網の総送出レートは青天井になり得る。1000ms の床で全体の秒間
     * リクエスト数に上限を掛け、回線・相手網双方への突発負荷（＝BAN/輻輳）を防ぐ。
     * ロック内で待つ＝複数コルーチンでも直列化（原設計を踏襲）。ネットワーク I/O 中はロックを持たない。
     */
    private suspend fun throttle(host: String, crawlDelayMs: Long) {
        gate.withLock {
            val now = nowMs()
            val sinceHost = now - (lastRequestByHost[host] ?: 0L)
            val sinceAny = now - lastRequestAtAny
            val wait = maxOf(crawlDelayMs - sinceHost, globalFloorMs - sinceAny)
            if (wait > 0) sleep(wait)
            // 待機後の実時刻を記録する（sleep 中も実時間は進むため now ではなく再取得した値を正とする）。
            val after = nowMs()
            lastRequestByHost[host] = after
            lastRequestAtAny = after
        }
    }

    companion object {
        const val USER_AGENT = "NovelReader-Android/1.0"

        private val defaultClient: OkHttpClient by lazy {
            // なぜ callTimeout（全体上限）が要るか: OkHttp の既定では全体タイムアウトが無制限で、
            // 低速だが切れない接続に当たると1回の GET が永久に完了しない。この client は章本文の取得に
            // 使われ、呼び出し元（WebBookImporter.addWebBook）は章を直列に回すため、1本詰まると
            // 取込全体が無言で止まり進捗バーが動かないまま固まる（no-network-timeout の症状）。
            // 章 HTML は数十〜数百 KB で数秒で返るのが正常なので、上位のリトライ（retryWithBackoff）が
            // 一過性失敗として拾える程度の余裕を見て 30 秒で打ち切る（なろう API クライアントと同値）。
            OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /** 本番の生 GET。非2xx は [HttpStatusException]（code＋Retry-After 秒）、空本文は [ScrapeException]。 */
        private fun defaultFetch(client: OkHttpClient): suspend (String) -> String = { url ->
            withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // Retry-After は秒数値のみ解釈（HTTP-date 形式は null＝Full Jitter へ委ねる）。
                        val retryAfter = resp.header("Retry-After")?.trim()?.toLongOrNull()
                        throw HttpStatusException(resp.code, retryAfter, "HTTP ${resp.code} for $url")
                    }
                    resp.body?.string() ?: throw ScrapeException("empty body for $url")
                }
            }
        }

        private fun hostOf(url: String): String =
            runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull() ?: ""
    }
}

/**
 * 非2xx を表す scrape 内部例外（リトライ判定と Retry-After 抽出のため code とヘッダ秒を保持）。
 * narou の Retrofit `HttpException` 相当を scrape 層へ自前で持つ（narou 非依存＝P5 層分離を維持）。
 * 呼び出し側へは [ScrapeHttpClient.getString] が [ScrapeException] へ正規化して渡す（外向き契約は不変）。
 */
internal class HttpStatusException(
    val code: Int,
    val retryAfterSeconds: Long?,
    message: String,
) : Exception(message)

/** scrape 層の取得・解析失敗を表す例外（呼び出し側でフォールバック導線へ落とす）。
 *  open＝サブ型（構造変更の疑い [ScrapeStructureException]）で意味を細分できるようにする（`is ScrapeException` 捕捉は不変）。 */
open class ScrapeException(message: String, cause: Throwable? = null) : Exception(message, cause)
