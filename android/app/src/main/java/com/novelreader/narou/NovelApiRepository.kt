package com.novelreader.narou

import android.util.Log
import com.novelreader.discovery.model.WorkDetail
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.model.DiscoveryPage
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.model.lastupApiParam
import com.novelreader.narou.model.typeApiParam
import com.novelreader.narou.network.NarouApiService
import com.novelreader.narou.network.NarouNetwork
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import kotlin.random.Random

class NarouApiException(val userMessage: String, cause: Throwable) : Exception(userMessage, cause)

/**
 * リトライの単一集約点（UX監査 add+errtext・エラー分類10-C）。一過性失敗のみ指数バックオフ＋Full Jitter で
 * 数回だけ再試行し、恒久失敗（4xx・応答解釈不能 等）は即座に投げ直す。なろうAPI DL と縦書きPDF DL の
 * 双方が同一ロジックを通すための共有ユーティリティ（各サイトに散らばった手書きリトライを1箇所へ集約）。
 *
 * @param maxRetries 追加試行回数（総試行 = 1 + maxRetries）。既定2＝合計最大3回で、体感の待ち（数秒）に収める。
 * @param isRetryable この例外で再試行してよいか。false なら即 rethrow（＝症状でなく分類で判断する）。
 * @param retryAfterMs サーバが明示した待機（429 Retry-After 等）。返れば Full Jitter より優先（下限として尊重）。
 * @param delayFn 実待機。テストで即時化（{ } や虚時間）して決定論にするための継ぎ目。
 * @param random Full Jitter の乱数源。テストで固定 seed を注入できる。
 */
internal suspend fun <T> retryWithBackoff(
    maxRetries: Int = 2,
    baseDelayMs: Long = 500L,
    maxDelayMs: Long = 4_000L,
    random: Random = Random.Default,
    delayFn: suspend (Long) -> Unit = { delay(it) },
    retryAfterMs: (Throwable) -> Long? = { null },
    isRetryable: (Throwable) -> Boolean,
    block: suspend () -> T,
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            // 協調キャンセルはエラーでなく制御フロー＝再試行せず素通し（呼び出し側のスコープ終了を尊重）。
            throw e
        } catch (e: Throwable) {
            if (attempt >= maxRetries || !isRetryable(e)) throw e
            // Full Jitter（AWS流）: sleep = random[0, min(cap, base*2^attempt)]。指数で上限へ近づけつつ
            // 同時失敗した群れの再試行タイミングを散らして再輻輳（thundering herd）を防ぐ。
            // Retry-After が明示されていればそれを下限として尊重する（429 のサーバ指示）。
            val expCap = (baseDelayMs shl attempt).coerceIn(1L, maxDelayMs)
            val jitter = random.nextLong(expCap + 1)
            val wait = maxOf(jitter, retryAfterMs(e) ?: 0L)
            delayFn(wait)
            attempt++
        }
    }
}

class NovelApiRepository(
    private val service: NarouApiService = NarouNetwork.service,
    private val timeSource: () -> Long = System::currentTimeMillis
) {
    // インメモリ TTL キャッシュ。キーはクエリキャッシュキーまたは "detail_" + ncode。
    // なぜ Mutex で守るか: かつては「全呼び出しが Main dispatcher で直列化される」暗黙前提で
    // 素の mutableMap のまま成立していたが、U1 新着チェック（WorkManager＝バックグラウンド実行）が
    // この前提を破るため、読み書き・追い出しを排他し任意の dispatcher から安全に呼べるようにする。
    // ロックは Map 操作の瞬間のみ保持し、API 呼び出し中は持たない。したがって同一クエリの同時ミスで
    // 二重フェッチは起こり得るが、成功結果は同一で後勝ち上書きされるだけなので許容する
    // （in-flight 重複排除は複雑さに見合わない。転送量マナーは 6h TTL と逐次呼び出し側で担保）。
    private val cache = mutableMapOf<String, CacheEntry>()
    private val cacheMutex = Mutex()

    private data class CacheEntry(
        val cachedTimeMs: Long,
        val result: RawResult
    )

    // なぜ内部を Raw（NarouNovel 保持）で回すか（P5 第2段）: 公開戻り値はサイト非依存 [WorkSummary] へ
    // 写像するが、ソート/マージ（novelupdatedAt・各種 point 等の API 固有値）とキャッシュは NarouNovel の
    // まま行う必要がある（要約に落とすと並べ替えキーが失われる）。したがって取得・キャッシュ・マージは
    // Raw のまま内部で完結させ、写像は公開メソッドの境界（discover/discoverPage/novelDetail）でのみ行う。
    private data class RawResult(val allcount: Int, val novels: List<NarouNovel>)
    private data class RawPage(val allcount: Int, val novels: List<NarouNovel>, val reachedApiLimit: Boolean)

    companion object {
        private const val TAG = "NovelApiRepository"

        // なぜ6時間キャッシュにするか: なろうのランキングや各種検索データは頻繁に変更されないため、
        // 頻繁なAPIアクセスを防ぎ転送量制限を回避するなろうAPIのマナーに従うため。
        const val RANKING_TTL_MS = 6 * 60 * 60 * 1000L // 6時間

        // なぜ of で項目を絞るか:
        // (1) あらすじ(story)はあらすじ非表示の一覧では転送しないことでデータ転送量を大幅に削減するため。
        // (2) genreは詳細ジャンルのラベル表示、timeは読了目安時間の表示に必要であるため。
        // (3) nu(novelupdated_at)は「新着」順×短編+連載中の2クエリマージのソートキーに必要
        //     （order=new は新着更新順＝novelupdated_at 降順。欠くとキーが全件 null になり
        //       安定ソートが連結順のまま take で片側だけ残す破綻を起こす）。
        const val OF_LIST = "t-n-w-gp-dp-wp-mp-qp-ga-e-l-nt-g-ti-nu"

        // なぜキャッシュ上限を50にするか:
        // 週間ランキングと異なり、ディスカバリ検索ではクエリの種類が多様になり、
        // インメモリキャッシュが際限なく膨らんでメモリを圧迫するのを防ぐため。
        const val MAX_CACHE_SIZE = 50

        // なろうAPIの取得エンベロープ（narou_api_manual.md §3.2）。
        //   lim（最大出力数）は 1〜500、st（表示開始位置）は 1〜2000。
        // つまり通常検索は st=2000 までしかページを進められず、全件がこれを超える場合は途中で頭打ちになる。
        // フルページング（F-J）はこの上限を検出して「取得上限に達しました」を明示するために使う。
        const val API_LIM_MAX = 500
        const val API_ST_MAX = 2000
    }

    /**
     * キャッシュに結果を保存する。上限50を超えた場合は最古のエントリを削除する。
     */
    private suspend fun putCache(key: String, result: RawResult, now: Long) = cacheMutex.withLock {
        // なぜ最古のエントリを削除するか:
        // キャッシュ件数が上限を超えた場合、タイムスタンプが最も古い（最後に取得されたのが最も古い）
        // エントリを追い出すことで、直近に利用されたクエリキャッシュを効果的に保持するため。
        // サイズ判定〜削除〜挿入を1つのロック区間で行う（分割すると並列 put で上限を突き破る）。
        if (cache.size >= MAX_CACHE_SIZE && !cache.containsKey(key)) {
            val oldestKey = cache.minByOrNull { it.value.cachedTimeMs }?.key
            if (oldestKey != null) {
                cache.remove(oldestKey)
            }
        }
        cache[key] = CacheEntry(now, result)
    }

    /**
     * TTL 内の有効なキャッシュ値を返す（無効・不在は null）。
     * なぜ TTL 判定までロック内で行うか: 取得と判定を分けると、判定の合間に追い出し・上書きが
     * 挟まって古い参照へ判定を下す紛れが生じるため、読み出しの一貫性をロック区間で閉じる。
     */
    private suspend fun getCacheValid(key: String, now: Long): RawResult? = cacheMutex.withLock {
        val cached = cache[key] ?: return@withLock null
        if ((now - cached.cachedTimeMs) < RANKING_TTL_MS) cached.result else null
    }

    /**
     * この例外を再試行してよいか（UX監査 add+errtext）。一過性のみ true にし、恒久失敗は false で即エラーへ。
     * なぜ Json 系を IOException より先に判定するか: JsonEncodingException は IOException のサブクラスで、
     * メンテページ等の非JSON応答（HTTP 200+HTML）がこれに落ちる。再試行しても直らない恒久失敗なので
     * ネットワーク一過性（下の IOException=true）と混同しないよう先に false へ落とす。
     */
    private fun isNarouRetryable(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code() == 429 || e.code() in 500..599 // 混雑/一時障害のみ再試行、4xx は非
        is JsonDataException, is JsonEncodingException -> false      // 応答の形は再試行で変わらない
        is IOException -> true                                       // timeout/DNS/瞬断＝一過性
        else -> false
    }

    /** 429 の Retry-After（秒）をミリ秒で返す。HTTP-date 形式は解釈せず null（＝Full Jitter へフォールバック）。 */
    private fun narouRetryAfterMs(e: Throwable): Long? =
        if (e is HttpException && e.code() == 429)
            e.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()?.times(1000L)
        else null

    /**
     * APIコールの例外をなろうAPIのドメイン例外に正規化する。
     * 実 API 呼び出しは [retryWithBackoff] を通し、一過性失敗（IO/timeout/5xx/429）のみ数回再試行してから
     * 残余を下記の分類でユーザー向けメッセージへ翻訳する（リトライの単一集約点＝UX監査 add+errtext）。
     */
    private suspend fun <T> wrapApiException(block: suspend () -> T): T {
        try {
            return retryWithBackoff(
                isRetryable = ::isNarouRetryable,
                retryAfterMs = ::narouRetryAfterMs,
                block = block,
            )
        } catch (e: HttpException) {
            // Nielsen#9（M8）: 生の HTTP コード(503 等)はユーザーに意味を持たない技術用語なので UI へ出さない。
            // 状態カテゴリごとに「次にどうすればよいか」が伝わる平易な日本語へ正規化する。診断に要る原コードは
            // Log へ残す（症状隠しではなく「機械語→人語」の翻訳＝根本の失敗事実は cause と Log に保全）。
            Log.w(TAG, "なろうAPI HTTPエラー: code=${e.code()}", e)
            val message = when (e.code()) {
                429 -> "アクセスが集中しています。少し時間をおいて再試行してください。"
                in 500..599 -> "なろうのサーバが一時的に混み合っているようです。時間をおいて再試行してください。"
                in 400..499 -> "リクエストを処理できませんでした。時間をおいて再試行してください。"
                else -> "なろうサーバとの通信に失敗しました。時間をおいて再試行してください。"
            }
            throw NarouApiException(message, e)
        } catch (e: JsonDataException) {
            // なぜ捕捉するか: レスポンスの形はサーバ都合でアプリ更新と独立に変わる外部入力であり、
            // 「想定外」ではなく期待すべき失敗系。RuntimeException のまま素通しすると、
            // 静かに非表示へ倒す前提の本棚バッジ・読書画面（NarouApiException だけを握る）まで
            // クラッシュが波及するため、ここでドメイン例外へ正規化する（cause 保持で診断可能性は残る）。
            throw NarouApiException("なろうの応答を解釈できませんでした。時間をおいて再試行してください。", e)
        } catch (e: JsonEncodingException) {
            // なぜ IOException より先に分けるか: JsonEncodingException は IOException のサブクラスで、
            // メンテページ等の非JSON応答（HTTP 200+HTML）がこれに落ちる。下の汎用 IOException に
            // 流すと「ネットワークに接続できません」と誤案内し、再試行しても直らない原因切り分けを妨げるため。
            throw NarouApiException("なろうの応答を解釈できませんでした。時間をおいて再試行してください。", e)
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
            // なぜ novelupdatedAt か: API の order=new は「新着更新順」＝novelupdated_at 降順
            // （narou_api_manual.md §3）。日時文字列 "yyyy-MM-dd HH:mm:ss" は辞書順＝時系列順。
            NarouOrder.NEW -> compareByDescending<NarouNovel> { it.novelupdatedAt ?: "" }
        }
        return (a + b).sortedWith(comparator)
    }

    /**
     * 汎用ディスカバリ検索を実行する（先頭ページ）。
     * キャッシュがあればそれを返し、無ければAPIから取得してキャッシュする。
     * 既存呼び出し（ホーム・結果一覧の初回・作品バッジ等）の互換のため DiscoveryResult を返す薄いラッパー。
     */
    suspend fun discover(query: DiscoveryQuery): DiscoveryResult {
        val page = discoverPageRaw(query, offset = 0)
        return DiscoveryResult(page.allcount, page.novels.toSummaries())
    }

    /** NarouNovel の内部リストを公開用のサイト非依存要約へ写像する（title/writer 欠落はここで除去）。 */
    private fun List<NarouNovel>.toSummaries(): List<WorkSummary> = mapNotNull { it.toWorkSummary() }

    /**
     * ページ単位のディスカバリ取得（フルページング＝F-J）の**公開境界**。内部の Raw 取得を
     * サイト非依存の [DiscoveryPage]（[WorkSummary] 一覧）へ写像して返す。offset 契約は [discoverPageRaw] 参照。
     */
    suspend fun discoverPage(rawQuery: DiscoveryQuery, offset: Int): DiscoveryPage {
        val page = discoverPageRaw(rawQuery, offset)
        return DiscoveryPage(page.allcount, page.novels.toSummaries(), page.reachedApiLimit)
    }

    /**
     * ページ単位のディスカバリ取得（フルページング＝F-J）。
     * @param offset これまでに読み込み済みの件数（0始まり）。次ページは st=offset+1（narou_api_manual.md §3.2:
     *   「3作品目以降なら3」＝st は1始まりの表示開始位置）で取得する。offset==0 は st を送らず先頭に委ねる
     *   （既存の一覧・ホーム経路と同一リクエストにし、キャッシュとテスト互換を保つため）。
     * 返り値の reachedApiLimit は「次ページが API エンベロープ（st>2000／マージ経路は累計>500）に阻まれて
     * 取得不能」を表し、VM が全件到達(Complete)と取得上限(ApiLimit)を判別するのに使う。
     */
    private suspend fun discoverPageRaw(rawQuery: DiscoveryQuery, offset: Int): RawPage {
        // word/notWord は入口で trim し「キー生成」と「実送信」を一致させる。
        // なぜ: cacheKey() は trim 済みで比較する一方、送信側が素通しだと前後空白付きの word
        // （NcodeLinkSheet 経由で実測）が「同一キャッシュキー・別実リクエスト」になり、
        // 空白違いの検索が最初の1回の結果で誤ヒットし続ける不整合の温床になるため。
        val query = rawQuery.copy(word = rawQuery.word?.trim(), notWord = rawQuery.notWord?.trim())

        // SHORT+RENSAI マージ経路
        if (query.types == setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI)) {
            // なぜ st でオフセットできないか: API に複合 type が無く2クエリを都度マージするため、st は
            // 各サブクエリ個別の開始位置にしかならずマージ列のオフセットにならない。代わりに各サブを
            // 「先頭から累計(offset+limit)件」取り直してマージし直し、[offset, offset+limit) を切り出す。
            // なぜ正確（近似でない）か: order でソート済みの2列 A,B について「マージ後の上位K件」は必ず
            // A[0,K)∪B[0,K) に含まれるため、累計K件ずつ取れば境界のK件目まで正しく決まる。
            if (offset >= API_LIM_MAX) {
                // 累計が lim 上限(500)に達しており、これ以上は取得不能。allcount は VM が初回ページで
                // 保持済みのため 0 でも害はない（load-more では allcount を上書きしない設計）。
                return RawPage(allcount = 0, novels = emptyList(), reachedApiLimit = true)
            }
            val fetchCount = (offset + query.limit).coerceAtMost(API_LIM_MAX)
            val short = discoverPageRaw(query.copy(types = setOf(NarouNovelType.SHORT), limit = fetchCount), 0)
            val rensai = discoverPageRaw(query.copy(types = setOf(NarouNovelType.RENSAI), limit = fetchCount), 0)
            val mergedNovels = mergeByOrder(short.novels, rensai.novels, query.order).take(fetchCount)
            val page = mergedNovels.drop(offset)
            // なぜ: allcount は短編と連載中が排反なので単純加算で正確。
            val allcount = short.allcount + rensai.allcount
            val loadedAfter = offset + page.size
            // 累計が 500 に張り付き、かつ総数がそれを超えていれば以降は lim 上限で取得不能＝取得上限。
            val reached = loadedAfter >= API_LIM_MAX && loadedAfter < allcount
            return RawPage(allcount, page, reached)
        }

        // st は1始まりの表示開始位置。offset==0 は既定（先頭）に委ねて省略する。
        val st = if (offset <= 0) null else offset + 1
        // st が API 上限(2000)を超える要求は投げられない＝ここで取得上限として返す
        // （通常この分岐は VM 側の再入で来る前に reachedApiLimit で止まるが、防御的に弾く）。
        if (st != null && st > API_ST_MAX) {
            return RawPage(allcount = 0, novels = emptyList(), reachedApiLimit = true)
        }

        // なぜ offset をキャッシュキーに含めるか: ページごとに返る作品スライスが異なるため、offset を
        // 混ぜないと 2ページ目に 1ページ目の結果を返してしまう。追加ページも 6h TTL でキャッシュするのは、
        // 同一クエリの再ページングや再訪時に無駄な再取得を避け、なろうAPIの転送量マナー（§6）に沿うため。
        val cacheKey = query.cacheKey() + "|off:$offset"
        val now = timeSource()
        val cached = getCacheValid(cacheKey, now)
        if (cached != null) {
            return toRawPage(cached, offset)
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
                st = st,
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

            RawResult(allcount, novels)
        }

        putCache(cacheKey, result, now)
        return toRawPage(result, offset)
    }

    /**
     * 取得済み RawResult を、offset を踏まえた RawPage（reachedApiLimit 付き）へ変換する。
     * 通常経路の取得上限判定: 次ページ開始位置 st'=(offset+size)+1 が 2000 を超える、かつ総数に未達なら取得上限。
     * なぜ result 由来で再計算するか: キャッシュには生の result のみ保持し、reachedApiLimit は
     * offset 依存の派生値なので都度計算する（キャッシュヒット時も正しく求まる）。
     */
    private fun toRawPage(result: RawResult, offset: Int): RawPage {
        val loadedAfter = offset + result.novels.size
        val reached = loadedAfter >= API_ST_MAX && loadedAfter < result.allcount
        return RawPage(result.allcount, result.novels, reached)
    }

    /**
     * Nコードを指定して、1件の小説詳細を取得する（サイト非依存の [WorkDetail] で返す＝公開境界）。
     * キャッシュがあればそれを返し、無ければAPIから取得してキャッシュする。
     * title/writer 欠落（[com.novelreader.narou.toWorkDetail] が null）や該当なしは null。
     */
    suspend fun novelDetail(ncode: Ncode): WorkDetail? {
        // 境界変換点: Retrofit(NarouApiService) は生 String を要求するため .value をここでほどく。
        val trimmedNcode = ncode.value.trim()
        val cacheKey = "detail_$trimmedNcode"
        val now = timeSource()
        val cached = getCacheValid(cacheKey, now)
        if (cached != null) {
            return cached.novels.firstOrNull()?.toWorkDetail()
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
            RawResult(allcount, novels)
        }

        // なぜ空結果（NotFound）もキャッシュするか: なろう側で削除・検索除外された作品に紐付いた本があると、
        // novelDetail は空を返し続ける。非キャッシュのままだと本棚バッジ等が表示のたびに実リクエストを
        // 発行し続け、6h TTL で守っている転送量マナー（narou_api_manual.md §6）に反するため、
        // 成功応答である限り空でも TTL 付きで負キャッシュする（通信失敗は wrapApiException で throw 済み＝ここに来ない）。
        putCache(cacheKey, result, now)
        return result.novels.firstOrNull()?.toWorkDetail()
    }

    /**
     * 複数 ncode の詳細を1リクエストで取得する（U1 新着チェックのバルク照会）。
     *
     * なぜここだけ NarouNovel のまま返すか（写像しない例外）: 唯一の呼び出し元は NewEpisodeCheckWorker →
     * [computeNewEpisodeAlerts]（narou/ 内部ロジック）であり、UI/viewmodel へは漏れない（NarouNovel の型は
     * 外へ出るが Worker は推論で素通しするだけ）。さらにこの経路は of="t-n-ga" で writer を取得しないため、
     * toWorkSummary/toWorkDetail に通すと writer 欠落で全件 null に落ち新着検知が全滅する。よって新着チェックの
     * narou 内部パイプラインは Raw のまま通す。
     */
    suspend fun novelDetailsBulk(ncodes: List<Ncode>): List<NarouNovel> {
        // なぜ: 照合対象の ncode リストが空なら、なろうAPIにリクエストを投げる必要がないため即座に空リストを返す。
        if (ncodes.isEmpty()) return emptyList()

        return wrapApiException {
            // なぜ 500 件ごとにチャンク分割するか: なろうAPIの lim（最大出力数）上限は 500（API_LIM_MAX＝
            // narou_api_manual.md §3.2）。紐付け蔵書が 501 件を超えると単一リクエストでは lim>500 が上限で
            // 頭打ちになり、501 件目以降の詳細＝新着検知がサイレント欠落する。チャンクごとに個別照会して
            // 結果を連結することで全件を漏れなく取得する（現実には 501 件超は稀だが、欠落は静かで気付けないため防ぐ）。
            ncodes.chunked(API_LIM_MAX).flatMap { chunk ->
                val ncodeParam = chunk.joinToString("-") { it.value.trim() }
                // なぜ of = "t-n-ga" なのか: U1 新着チェックにおいて必要な項目はタイトル(t)、Nコード(n)、全話数(ga)のみであり、転送量を最小限に抑えるため。
                // なぜ意図的にキャッシュに乗せないか: 新着話の検知は情報の鮮度が本質であり、6時間TTLキャッシュに乗せると当日中の更新を翌日扱いにするなど検知の遅延を引き起こすため。また、このメソッドは1日1回等のWorkerによる呼び出しを想定しており、APIレート制限等の負荷も問題にならないため（チャンク間の待機も既存のマージ経路＝2サブクエリ連投に倣い設けない）。
                val list = service.search(
                    ncode = ncodeParam,
                    lim = chunk.size,
                    of = "t-n-ga"
                )
                list.drop(1)
            }
        }
    }
}
