package com.novelreader.narou

import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.network.NarouApiService
import com.squareup.moshi.JsonDataException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class NovelApiRepositoryTest {

    private lateinit var service: NarouApiService
    private lateinit var repository: NovelApiRepository
    private var currentTime = 1000L

    @Before
    fun setUp() {
        service = mockk(relaxed = true)
        repository = NovelApiRepository(service = service, timeSource = { currentTime })
    }

    private fun createMockResponse(): List<NarouNovel> {
        return listOf(
            NarouNovel(allcount = 100),
            NarouNovel(title = "作品1", ncode = "N1111A", noveltypeCompact = 1, end = 1),
            NarouNovel(title = "作品2", ncode = "N2222A", noveltypeCompact = 2, end = 0)
        )
    }

    @Test
    fun `discover - レスポンスの先頭要素から allcount を分離し、残りを novels リストとして返すこと`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        val result = repository.discover(DiscoveryQuery())

        assertEquals(100, result.allcount)
        assertEquals(2, result.novels.size)
        assertEquals("作品1", result.novels[0].title)
        assertEquals("作品2", result.novels[1].title)
    }

    @Test
    fun `discover - キャッシュ有効期間内であれば API は 1 回しか呼ばれず、キャッシュから値が返されること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any(), word = any()) } returns createMockResponse()

        val query = DiscoveryQuery(word = "テスト")
        val result1 = repository.discover(query)
        val result2 = repository.discover(query)

        assertEquals(result1, result2)
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), word = "テスト") }
    }

    @Test
    fun `discover - キャッシュ有効期限 TTL (6時間) 経過後は API が再呼び出しされ、新しいデータが取得されること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        val query = DiscoveryQuery()
        repository.discover(query)

        currentTime += 6 * 60 * 60 * 1000L

        repository.discover(query)

        coVerify(exactly = 2) { service.search(of = any(), order = any(), lim = any()) }
    }

    @Test
    fun `discover - API が IOException を投げた場合、ユーザーメッセージを保持した NarouApiException に変換されて再スローされること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws IOException("No route to host")

        var thrown: NarouApiException? = null
        try {
            repository.discover(DiscoveryQuery())
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull("IOException は NarouApiException に正規化されて throw される", thrown)
        assertEquals("ネットワークに接続できません。通信環境を確認して再試行してください。", thrown!!.userMessage)
        assertEquals("No route to host", thrown.cause?.message)
    }

    @Test
    fun `discover - DiscoveryQuery の条件が正しい引数で service に渡されること`() = runTest {
        coEvery {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                word = any(),
                title = any(),
                ex = any(),
                keyword = any(),
                wname = any(),
                genre = any(),
                istensei = any(),
                istenni = any(),
                istt = any(),
                iszankoku = any(),
                notzankoku = any(),
                isr15 = any(),
                notr15 = any(),
                isbl = any(),
                notbl = any(),
                isgl = any(),
                notgl = any(),
                type = any(),
                lastup = any(),
                time = any()
            )
        } returns createMockResponse()

        // ケース1: 複合検索クエリ
        val query1 = DiscoveryQuery(
            word = "異世界",
            inTitle = true,
            genres = setOf(101, 201),
            attrsInclude = setOf(NarouAttr.TENSEI),
            types = setOf(NarouNovelType.KANKETSU),
            lastups = setOf(NarouLastup.SEVENDAY),
            time = "30-"
        )
        repository.discover(query1)

        coVerify(exactly = 1) {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                word = "異世界",
                title = 1,
                // 非選択の検索範囲は 0 でなく null（=クエリ省略）を期待する。
                // なぜ: なろうAPIは「1で抽出対象指定・未指定なら全項目」定義で 0 の挙動が未定義のため、
                // Repository は選択項目のみ 1 を送る実装になっている。
                ex = null,
                keyword = null,
                wname = null,
                genre = "101-201",
                istensei = 1,
                istenni = null,
                istt = null,
                iszankoku = null,
                notzankoku = null,
                isr15 = null,
                notr15 = null,
                isbl = null,
                notbl = null,
                isgl = null,
                notgl = null,
                type = "er",
                lastup = "sevenday",
                time = "30-",
                st = null,
                notword = null,
                biggenre = null,
                length = null,
                kaiwaritu = null,
                sasie = null,
                ncode = null
            )
        }

        // ケース2: 転生+転移(istt)かつ残酷描写除外
        val query2 = DiscoveryQuery(
            attrsInclude = setOf(NarouAttr.TENSEI, NarouAttr.TENNI),
            attrsExclude = setOf(NarouAttr.ZANKOKU)
        )
        repository.discover(query2)

        coVerify(exactly = 1) {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                word = null,
                title = null,
                ex = null,
                keyword = null,
                wname = null,
                genre = null,
                istensei = null,
                istenni = null,
                istt = 1,
                iszankoku = null,
                notzankoku = 1,
                isr15 = null,
                notr15 = null,
                isbl = null,
                notbl = null,
                isgl = null,
                notgl = null,
                type = null,
                lastup = null,
                time = null,
                st = null,
                notword = null,
                biggenre = null,
                length = null,
                kaiwaritu = null,
                sasie = null,
                ncode = null
            )
        }
    }

    @Test
    fun `discover - order が異なる query は別キャッシュキーとして扱われ API が複数回呼ばれること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } returns createMockResponse()

        repository.discover(DiscoveryQuery(order = NarouOrder.WEEKLY))
        repository.discover(DiscoveryQuery(order = NarouOrder.DAILY))

        coVerify(exactly = 1) { service.search(order = "weeklypoint", of = any(), lim = any()) }
        coVerify(exactly = 1) { service.search(order = "dailypoint", of = any(), lim = any()) }
    }

    @Test
    fun `discover - キャッシュ上限 50 を超えて異なるクエリを実行したとき最古のエントリが削除されること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any(), word = any()) } returns createMockResponse()

        // 51個の異なるクエリを実行
        for (i in 1..51) {
            currentTime += 1000L // タイムスタンプを順に進める
            repository.discover(DiscoveryQuery(word = "query_$i"))
        }

        // 51回 API が呼ばれているはず
        coVerify(exactly = 51) { service.search(of = any(), order = any(), lim = any(), word = any()) }

        // 最も古い query_1 をもう一度実行（キャッシュから追い出されているため API が呼ばれるはず）
        repository.discover(DiscoveryQuery(word = "query_1"))

        // query_1 は 2回呼ばれているはず (初回 + 追い出し後の再取得)
        coVerify(exactly = 2) { service.search(of = any(), order = any(), lim = any(), word = "query_1") }
    }

    @Test
    fun `novelDetail - ncode 指定で service が呼ばれ作品要素が返ること`() = runTest {
        val mockNovel = NarouNovel(title = "詳細作品", ncode = "N1234AB")
        coEvery { service.search(ncode = "N1234AB", lim = 1, of = null) } returns listOf(
            NarouNovel(allcount = 1),
            mockNovel
        )

        val result = repository.novelDetail(Ncode("N1234AB"))

        assertNotNull(result)
        assertEquals("詳細作品", result!!.title)
        assertEquals("N1234AB", result.ncode)

        // キャッシュの検証：2回目は API が呼ばれない
        val cachedResult = repository.novelDetail(Ncode("N1234AB"))
        assertEquals(result, cachedResult)
        coVerify(exactly = 1) { service.search(ncode = "N1234AB", lim = 1, of = null) }
    }

    @Test
    fun `novelDetail - 作品要素なし (allcountのみ) の場合に null が返ること`() = runTest {
        coEvery { service.search(ncode = "N9999XX", lim = 1, of = null) } returns listOf(
            NarouNovel(allcount = 0)
        )

        val result = repository.novelDetail(Ncode("N9999XX"))

        assertNull(result)
        coVerify(exactly = 1) { service.search(ncode = "N9999XX", lim = 1, of = null) }
    }

    @Test
    fun `discover - 属性指定の矛盾無害化、istt振替、not系送出が正しく機能すること`() = runTest {
        coEvery {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                istensei = any(),
                istenni = any(),
                istt = any(),
                iszankoku = any(),
                notzankoku = any(),
                isr15 = any(),
                notr15 = any(),
                isbl = any(),
                notbl = any(),
                isgl = any(),
                notgl = any()
            )
        } returns createMockResponse()

        // 1. 同一属性 (R15) の include/exclude 矛盾指定は、両側から除外（無害化）されること
        // 2. TENSEI と TENNI が include に両方あるとき istt=1 が送られ istensei/istenni は null になること
        // 3. 除外属性 (ZANKOKU) が attrsExclude にあるとき notzankoku = 1 が送られること
        val query = DiscoveryQuery(
            attrsInclude = setOf(NarouAttr.TENSEI, NarouAttr.TENNI, NarouAttr.R15),
            attrsExclude = setOf(NarouAttr.ZANKOKU, NarouAttr.R15) // R15 が矛盾
        )

        repository.discover(query)

        coVerify(exactly = 1) {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                istensei = null,
                istenni = null,
                istt = 1,
                iszankoku = null,
                notzankoku = 1,
                isr15 = null, // 矛盾のため null
                notr15 = null // 矛盾のため null
            )
        }
    }

    @Test
    fun `discover - 転生・転移の除外が nottensei nottenni として送られること`() = runTest {
        coEvery {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                nottensei = any(),
                nottenni = any()
            )
        } returns createMockResponse()

        // なぜこのケースを固定するか: UI の除外行には NarouAttr 全種（転生・転移含む）が並ぶ。
        // ここが送られないと「チップは選択表示されるのに絞り込みが効かない」サイレント無効になる
        // （バッチ2実装時に実際に欠落していた回帰）。
        val query = DiscoveryQuery(
            attrsExclude = setOf(NarouAttr.TENSEI, NarouAttr.TENNI)
        )

        repository.discover(query)

        coVerify(exactly = 1) {
            service.search(
                of = any(),
                order = any(),
                lim = any(),
                istensei = null,
                istenni = null,
                istt = null,
                nottensei = 1,
                nottenni = 1
            )
        }
    }

    @Test
    fun `typeApiParam - 全8通りの組合せに対して正しいAPIパラメータまたはnullが返ること`() {
        val SHORT = NarouNovelType.SHORT
        val RENSAI = NarouNovelType.RENSAI
        val KANKETSU = NarouNovelType.KANKETSU

        // 0種
        assertNull(com.novelreader.narou.model.typeApiParam(emptySet()))
        // 1種
        assertEquals("t", com.novelreader.narou.model.typeApiParam(setOf(SHORT)))
        assertEquals("r", com.novelreader.narou.model.typeApiParam(setOf(RENSAI)))
        assertEquals("er", com.novelreader.narou.model.typeApiParam(setOf(KANKETSU)))
        // 2種
        assertEquals("ter", com.novelreader.narou.model.typeApiParam(setOf(SHORT, KANKETSU)))
        assertEquals("re", com.novelreader.narou.model.typeApiParam(setOf(RENSAI, KANKETSU)))
        assertNull(com.novelreader.narou.model.typeApiParam(setOf(SHORT, RENSAI))) // マージ対象
        // 3種
        assertNull(com.novelreader.narou.model.typeApiParam(setOf(SHORT, RENSAI, KANKETSU)))
    }

    @Test
    fun `lastupApiParam - 単一指定はプリセット文字列を返し、複数指定はJST基準で連続レンジに合成されること`() {
        val zone = java.time.ZoneId.of("Asia/Tokyo")
        val now = java.time.ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, zone)
        val nowMs = now.toInstant().toEpochMilli()

        val SEVENDAY = NarouLastup.SEVENDAY
        val THISMONTH = NarouLastup.THISMONTH
        val LASTMONTH = NarouLastup.LASTMONTH

        // 空
        assertNull(com.novelreader.narou.model.lastupApiParam(emptySet(), nowMs, zone))
        // 単一
        assertEquals("sevenday", com.novelreader.narou.model.lastupApiParam(setOf(SEVENDAY), nowMs, zone))
        assertEquals("thismonth", com.novelreader.narou.model.lastupApiParam(setOf(THISMONTH), nowMs, zone))
        assertEquals("lastmonth", com.novelreader.narou.model.lastupApiParam(setOf(LASTMONTH), nowMs, zone))

        // 複数
        val t7dayStart = now.minusDays(7).toEpochSecond()
        val tThisMonthStart = now.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth())
            .truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochSecond()
        val tLastMonthStart = now.minusMonths(1).with(java.time.temporal.TemporalAdjusters.firstDayOfMonth())
            .truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochSecond()
        val tEnd = now.toEpochSecond()

        // {THISMONTH, LASTMONTH}
        // min(LASTMONTH.start, THISMONTH.start) = tLastMonthStart
        // max(LASTMONTH.end, THISMONTH.end) = tEnd
        assertEquals("$tLastMonthStart-$tEnd", com.novelreader.narou.model.lastupApiParam(setOf(THISMONTH, LASTMONTH), nowMs, zone))

        // {SEVENDAY, THISMONTH}
        // min は now の日付に依存する（月初7日以内なら 7日前・8日以降なら月初）ため minOf で普遍化する
        // max(SEVENDAY.end, THISMONTH.end) = tEnd
        assertEquals("${minOf(t7dayStart, tThisMonthStart)}-$tEnd", com.novelreader.narou.model.lastupApiParam(setOf(SEVENDAY, THISMONTH), nowMs, zone))

        // 3種全選択（toggleLastup は全点灯を許す＝UIから到達可能な経路）
        // min は先月1日・max は now
        assertEquals("$tLastMonthStart-$tEnd", com.novelreader.narou.model.lastupApiParam(setOf(SEVENDAY, THISMONTH, LASTMONTH), nowMs, zone))

        // 非連続組 {SEVENDAY, LASTMONTH}（UI は間を自動点灯して防ぐが、万一漏れた場合の
        // 「min-max の広い側へ倒す」防御が仕様どおり動くことを固定する）
        assertEquals("$tLastMonthStart-$tEnd", com.novelreader.narou.model.lastupApiParam(setOf(SEVENDAY, LASTMONTH), nowMs, zone))
    }

    @Test
    fun `discover - SHORTとRENSAIが両方指定されたとき2回APIが呼ばれて結果が降順マージかつlimit切りされること`() = runTest {
        // 短編用APIモック（order=WEEKLY, type="t", lim=3）
        val shortNovels = listOf(
            NarouNovel(allcount = 40),
            NarouNovel(title = "短編1", ncode = "NS1", weeklyPoint = 300),
            NarouNovel(title = "短編2", ncode = "NS2", weeklyPoint = 100)
        )
        // 連載用APIモック（order=WEEKLY, type="r", lim=3）
        val rensaiNovels = listOf(
            NarouNovel(allcount = 60),
            NarouNovel(title = "連載1", ncode = "NR1", weeklyPoint = 400),
            NarouNovel(title = "連載2", ncode = "NR2", weeklyPoint = 200)
        )

        coEvery {
            service.search(
                of = any(), order = any(), lim = 3,
                type = "t", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        } returns shortNovels

        coEvery {
            service.search(
                of = any(), order = any(), lim = 3,
                type = "r", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        } returns rensaiNovels

        val query = DiscoveryQuery(
            types = setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI),
            order = NarouOrder.WEEKLY,
            limit = 3
        )

        val result = repository.discover(query)

        // allcount は単純加算 40 + 60 = 100
        assertEquals(100, result.allcount)

        // novels はマージかつ降順ソート
        // NR1(400) > NS1(300) > NR2(200) > NS2(100) の上位3件
        assertEquals(3, result.novels.size)
        assertEquals("NR1", result.novels[0].ncode) // 連載1 (400)
        assertEquals("NS1", result.novels[1].ncode) // 短編1 (300)
        assertEquals("NR2", result.novels[2].ncode) // 連載2 (200)

        // service.search がそれぞれ 1 回ずつ呼ばれたことを確認
        coVerify(exactly = 1) {
            service.search(
                of = any(), order = any(), lim = 3, type = "t", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        }
        coVerify(exactly = 1) {
            service.search(
                of = any(), order = any(), lim = 3, type = "r", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        }
    }

    @Test
    fun `discover - SHORTとRENSAIの新着順マージが novelupdated_at 降順になること（nullは最下位）`() = runTest {
        // なぜこの回帰テストか: OF_LIST に nu(novelupdated_at) が無かった時期、NEW順マージのキーが
        // 全件 null になり安定ソートが連結順のまま take で短編だけを残す破綻が実在した。
        // OF_LIST の nu 欠落とマージキーの両方をここで固定する。
        assertTrue("OF_LIST は新着順マージのソートキー nu を転送すること",
            NovelApiRepository.OF_LIST.split("-").contains("nu"))

        val shortNovels = listOf(
            NarouNovel(allcount = 10),
            NarouNovel(ncode = "NS1", novelupdatedAt = "2026-07-07 10:00:00"),
            NarouNovel(ncode = "NS2", novelupdatedAt = "2026-07-05 10:00:00")
        )
        val rensaiNovels = listOf(
            NarouNovel(allcount = 20),
            NarouNovel(ncode = "NR1", novelupdatedAt = "2026-07-06 10:00:00"),
            NarouNovel(ncode = "NR2", novelupdatedAt = null)
        )
        coEvery {
            service.search(
                of = any(), order = any(), lim = 4, type = "t", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        } returns shortNovels
        coEvery {
            service.search(
                of = any(), order = any(), lim = 4, type = "r", lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        } returns rensaiNovels

        val result = repository.discover(
            DiscoveryQuery(
                types = setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI),
                order = NarouOrder.NEW,
                limit = 4
            )
        )

        // 短編・連載が更新日時順に交互へ混ざること（連結順のままなら NS1,NS2,NR1,NR2 になる）
        assertEquals(listOf("NS1", "NR1", "NS2", "NR2"), result.novels.map { it.ncode })
    }

    @Test
    fun `discover - 5xx が平易な日本語へ正規化され生の HTTPコードを含まないこと（M8）`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws
            HttpException(Response.error<List<NarouNovel>>(503, "server error".toResponseBody(null)))

        var thrown: NarouApiException? = null
        try {
            repository.discover(DiscoveryQuery())
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(
            "なろうのサーバが一時的に混み合っているようです。時間をおいて再試行してください。",
            thrown!!.userMessage
        )
        // M8 回帰防止の本質: UI 文言に生の HTTPコード（503）が漏れないこと。原コードは cause/Log に保全。
        assertFalse(thrown.userMessage.contains("503"))
        assertTrue(thrown.cause is HttpException)
    }

    @Test
    fun `discover - 429 が混雑を伝える平易な文言へ正規化され生コードを含まないこと（M8）`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws
            HttpException(Response.error<List<NarouNovel>>(429, "too many".toResponseBody(null)))

        var thrown: NarouApiException? = null
        try {
            repository.discover(DiscoveryQuery())
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(
            "アクセスが集中しています。少し時間をおいて再試行してください。",
            thrown!!.userMessage
        )
        assertFalse(thrown.userMessage.contains("429"))
    }

    @Test
    fun `discover - 4xx が平易な文言へ正規化され生コードを含まないこと（M8）`() = runTest {
        // 429 は専用分岐なので、それ以外の 4xx（404）が汎用 4xx 文言へ落ちることを確認する。
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws
            HttpException(Response.error<List<NarouNovel>>(404, "not found".toResponseBody(null)))

        var thrown: NarouApiException? = null
        try {
            repository.discover(DiscoveryQuery())
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(
            "リクエストを処理できませんでした。時間をおいて再試行してください。",
            thrown!!.userMessage
        )
        assertFalse(thrown.userMessage.contains("404"))
    }

    @Test
    fun `discover - JSON型不一致(JsonDataException)が解釈エラーの NarouApiException に正規化されること`() = runTest {
        // なぜ固定するか: レスポンスの形はサーバ都合で変わる外部入力。素通しすると
        // NarouApiException だけを握る本棚バッジ・読書画面までクラッシュが波及する。
        coEvery { service.search(of = any(), order = any(), lim = any()) } throws
            JsonDataException("Expected an int but was STRING at path \$[1].kaiwaritu")

        var thrown: NarouApiException? = null
        try {
            repository.discover(DiscoveryQuery())
        } catch (e: NarouApiException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals("なろうの応答を解釈できませんでした。時間をおいて再試行してください。", thrown!!.userMessage)
    }

    @Test
    fun `novelDetail - 見つからない結果も負キャッシュされ再照会しないこと`() = runTest {
        // なぜ負キャッシュか: なろう側で削除された紐付け作品は空応答が続く。非キャッシュだと
        // 本棚バッジが表示のたびに実リクエストを発行し続け転送量マナーに反する。
        coEvery { service.search(ncode = "N9999XX", lim = 1, of = null) } returns
            listOf(NarouNovel(allcount = 0))

        assertNull(repository.novelDetail(Ncode("N9999XX")))
        assertNull(repository.novelDetail(Ncode("N9999XX")))

        coVerify(exactly = 1) { service.search(ncode = "N9999XX", lim = 1, of = null) }
    }

    // ── フルページング（F-J） ──

    @Test
    fun `discoverPage - offset=0 では st を送らず（st=null）先頭ページを返すこと`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any(), st = null) } returns createMockResponse()

        val page = repository.discoverPage(DiscoveryQuery(), offset = 0)

        assertEquals(100, page.allcount)
        assertEquals(2, page.novels.size)
        assertFalse(page.reachedApiLimit)
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), st = null) }
    }

    @Test
    fun `discoverPage - offset 指定で st=offset+1 を送りそのページを返すこと`() = runTest {
        val page2 = listOf(
            NarouNovel(allcount = 100),
            NarouNovel(title = "31番目", ncode = "N31"),
            NarouNovel(title = "32番目", ncode = "N32"),
        )
        coEvery { service.search(of = any(), order = any(), lim = any(), st = 31) } returns page2

        val page = repository.discoverPage(DiscoveryQuery(), offset = 30)

        assertEquals(100, page.allcount)
        assertEquals("N31", page.novels[0].ncode)
        assertFalse(page.reachedApiLimit)
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), st = 31) }
    }

    @Test
    fun `discoverPage - 次ページ開始位置が st 上限(2000)を超えると reachedApiLimit=true になること`() = runTest {
        // st=1986 での取得は可能（<=2000）だが、取得後の累計 1985+30=2015 は 2000 を超え、
        // 総数(5000)にも未達＝次ページは st>2000 で取得不能。
        val novels = (1..30).map { NarouNovel(ncode = "N$it") }
        val resp = listOf(NarouNovel(allcount = 5000)) + novels
        coEvery { service.search(of = any(), order = any(), lim = any(), st = 1986) } returns resp

        val page = repository.discoverPage(DiscoveryQuery(), offset = 1985)

        assertEquals(30, page.novels.size)
        assertTrue(page.reachedApiLimit)
    }

    @Test
    fun `discoverPage - st 上限(2000)を超える要求は API を呼ばず取得上限として空を返すこと`() = runTest {
        // offset=2000 → st=2001 > 2000。防御的に API を叩かず reachedApiLimit=true・空で返す。
        val page = repository.discoverPage(DiscoveryQuery(), offset = 2000)

        assertTrue(page.novels.isEmpty())
        assertTrue(page.reachedApiLimit)
        coVerify(exactly = 0) { service.search(of = any(), order = any(), lim = any(), st = any()) }
    }

    @Test
    fun `discoverPage - offset をキャッシュキーに含み、別 offset は別リクエスト・同 offset はキャッシュになること`() = runTest {
        coEvery { service.search(of = any(), order = any(), lim = any(), st = null) } returns createMockResponse()
        coEvery { service.search(of = any(), order = any(), lim = any(), st = 31) } returns createMockResponse()

        repository.discoverPage(DiscoveryQuery(), offset = 0)
        repository.discoverPage(DiscoveryQuery(), offset = 0)   // キャッシュヒット
        repository.discoverPage(DiscoveryQuery(), offset = 30)
        repository.discoverPage(DiscoveryQuery(), offset = 30)  // キャッシュヒット

        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), st = null) }
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), st = 31) }
    }

    /** SHORT/RENSAI サブクエリを type・lim で個別にモックする（他の全パラメータは既定/任意にマッチ）。 */
    private fun mockMergeSub(type: String, lim: Int, novels: List<NarouNovel>) {
        coEvery {
            service.search(
                of = any(), order = any(), lim = lim, st = any(), type = type,
                lastup = any(), word = any(), title = any(), ex = any(), keyword = any(), wname = any(), genre = any(),
                istensei = any(), istenni = any(), istt = any(), nottensei = any(), nottenni = any(),
                iszankoku = any(), notzankoku = any(), isr15 = any(), notr15 = any(), isbl = any(), notbl = any(), isgl = any(), notgl = any(),
                time = any(), length = any(), kaiwaritu = any(), sasie = any()
            )
        } returns novels
    }

    @Test
    fun `discoverPage - SHORT+RENSAI マージ経路が累計取得で offset スライスを返すこと`() = runTest {
        // 短編 weeklyPoint: S1=350,S2=250,S3=150,S4=50 / 連載: R1=400,R2=300,R3=200,R4=100
        // マージ降順: R1,S1,R2,S2,R3,S3,R4,S4
        val shortLim2 = listOf(NarouNovel(allcount = 40),
            NarouNovel(ncode = "S1", weeklyPoint = 350), NarouNovel(ncode = "S2", weeklyPoint = 250))
        val rensaiLim2 = listOf(NarouNovel(allcount = 60),
            NarouNovel(ncode = "R1", weeklyPoint = 400), NarouNovel(ncode = "R2", weeklyPoint = 300))
        val shortLim4 = listOf(NarouNovel(allcount = 40),
            NarouNovel(ncode = "S1", weeklyPoint = 350), NarouNovel(ncode = "S2", weeklyPoint = 250),
            NarouNovel(ncode = "S3", weeklyPoint = 150), NarouNovel(ncode = "S4", weeklyPoint = 50))
        val rensaiLim4 = listOf(NarouNovel(allcount = 60),
            NarouNovel(ncode = "R1", weeklyPoint = 400), NarouNovel(ncode = "R2", weeklyPoint = 300),
            NarouNovel(ncode = "R3", weeklyPoint = 200), NarouNovel(ncode = "R4", weeklyPoint = 100))
        mockMergeSub("t", 2, shortLim2)
        mockMergeSub("r", 2, rensaiLim2)
        mockMergeSub("t", 4, shortLim4)
        mockMergeSub("r", 4, rensaiLim4)

        val query = DiscoveryQuery(
            types = setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI),
            order = NarouOrder.WEEKLY,
            limit = 2,
        )

        // 1ページ目（offset=0, 累計2）: マージ上位2 = R1,S1
        val page0 = repository.discoverPage(query, offset = 0)
        assertEquals(100, page0.allcount) // 40+60
        assertEquals(listOf("R1", "S1"), page0.novels.map { it.ncode })
        assertFalse(page0.reachedApiLimit)

        // 2ページ目（offset=2, 累計4→take4→drop2）: R2,S2
        val page1 = repository.discoverPage(query, offset = 2)
        assertEquals(listOf("R2", "S2"), page1.novels.map { it.ncode })
        assertFalse(page1.reachedApiLimit)
    }

    @Test
    fun `discoverPage - マージ経路は offset が lim 上限(500)以上で API を呼ばず取得上限になること`() = runTest {
        val query = DiscoveryQuery(
            types = setOf(NarouNovelType.SHORT, NarouNovelType.RENSAI),
            order = NarouOrder.WEEKLY,
            limit = 30,
        )

        val page = repository.discoverPage(query, offset = 500)

        assertTrue(page.novels.isEmpty())
        assertTrue(page.reachedApiLimit)
        coVerify(exactly = 0) { service.search(of = any(), order = any(), lim = any(), type = "t", st = any()) }
    }

    @Test
    fun `discover - word の前後空白は trim されて送信され、空白違いは同一キャッシュに当たること`() = runTest {
        // なぜ: cacheKey() は trim 済みで比較する一方、送信が素通しだと「同一キー・別実リクエスト」の
        // 不整合になる（NcodeLinkSheet 経由の word で実測）。入口 trim で両者が一致することを固定する。
        coEvery { service.search(of = any(), order = any(), lim = any(), word = any()) } returns createMockResponse()

        repository.discover(DiscoveryQuery(word = " 転生 "))
        repository.discover(DiscoveryQuery(word = "転生"))

        // 送信は trim 済みの1回のみ（2回目は同一キャッシュキーでヒット）・素通し空白付きの送信は無いこと
        coVerify(exactly = 1) { service.search(of = any(), order = any(), lim = any(), word = "転生") }
        coVerify(exactly = 0) { service.search(of = any(), order = any(), lim = any(), word = " 転生 ") }
    }

    @Test
    fun `cache - 複数スレッドからの並列アクセスでも例外なく上限50を維持すること`() = runTest {
        // なぜ: U1 新着チェック（WorkManager）で Main 以外からの呼び出しが始まるため、
        // Mutex 排他が実際にマルチスレッド並列で壊れない（ConcurrentModificationException 等が
        // 出ない・追い出しが上限を守る）ことをスモークで固定する。
        coEvery { service.search(of = any(), order = any(), lim = any(), word = any()) } returns createMockResponse()

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(100) { i ->
                    launch {
                        repository.discover(DiscoveryQuery(word = "query-$i"))
                    }
                }
            }
        }

        // 100 個の別クエリを並列投入しても、キャッシュ追い出しが例外なく完了していれば
        // 直後の同一クエリ再実行がクラッシュせず結果を返す（値の健全性スモーク）。
        val again = repository.discover(DiscoveryQuery(word = "query-99"))
        assertEquals(100, again.allcount)
    }

    @Test
    fun `novelDetailsBulk - ncode がダッシュ連結され of=t-n-ga と lim=件数 で送られること`() = runTest {
        val mockNovels = listOf(
            NarouNovel(allcount = 2),
            NarouNovel(title = "作品1", ncode = "N1111AA"),
            NarouNovel(title = "作品2", ncode = "N2222BB")
        )
        coEvery { service.search(ncode = "N1111AA-N2222BB", lim = 2, of = "t-n-ga") } returns mockNovels

        val result = repository.novelDetailsBulk(listOf(Ncode("N1111AA"), Ncode("N2222BB")))

        assertEquals(2, result.size)
        assertEquals("N1111AA", result[0].ncode)
        assertEquals("N2222BB", result[1].ncode)
        coVerify(exactly = 1) { service.search(ncode = "N1111AA-N2222BB", lim = 2, of = "t-n-ga") }
    }

    @Test
    fun `novelDetailsBulk - 空リストは API を呼ばず空を返すこと`() = runTest {
        val result = repository.novelDetailsBulk(emptyList())
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { service.search(ncode = any(), lim = any(), of = any()) }
    }

    @Test
    fun `novelDetailsBulk - 2回呼ぶと2回 API が呼ばれること（キャッシュに乗らない）`() = runTest {
        val mockNovels = listOf(
            NarouNovel(allcount = 1),
            NarouNovel(title = "作品1", ncode = "N1111AA")
        )
        coEvery { service.search(ncode = "N1111AA", lim = 1, of = "t-n-ga") } returns mockNovels

        val ncodes = listOf(Ncode("N1111AA"))
        repository.novelDetailsBulk(ncodes)
        repository.novelDetailsBulk(ncodes)

        coVerify(exactly = 2) { service.search(ncode = "N1111AA", lim = 1, of = "t-n-ga") }
    }

    @Test
    fun `novelDetailsBulk - 500件ちょうどは1リクエストで送られること（境界の下側）`() = runTest {
        // なぜ境界を固定するか: なろうAPIの lim 上限は500。500件までは分割不要で単一リクエストに収める。
        val ncodes = (1..500).map { Ncode("N%04dAA".format(it)) }
        coEvery { service.search(ncode = any(), lim = 500, of = "t-n-ga") } returns
            listOf(NarouNovel(allcount = 500)) + (1..500).map { NarouNovel(ncode = "N%04dAA".format(it)) }

        val result = repository.novelDetailsBulk(ncodes)

        assertEquals(500, result.size)
        coVerify(exactly = 1) { service.search(ncode = any(), lim = 500, of = "t-n-ga") }
        // lim>500 の単一リクエストが投げられていないこと（サイレント欠落の温床）
        coVerify(exactly = 0) { service.search(ncode = any(), lim = 501, of = "t-n-ga") }
    }

    @Test
    fun `novelDetailsBulk - 501件は500件ごとに分割され複数リクエストの結果が連結されること（境界の上側）`() = runTest {
        // なぜ固定するか: lim 上限500を超える501件目以降が単一リクエストだとサイレント欠落する。
        // 500件ごとにチャンク分割して全件（501件）が漏れなく返ることを担保する。
        val ncodes = (1..501).map { Ncode("N%04dAA".format(it)) }

        // 1チャンク目（500件）と2チャンク目（1件）を lim で識別してモックする。
        coEvery { service.search(ncode = any(), lim = 500, of = "t-n-ga") } returns
            listOf(NarouNovel(allcount = 500)) + (1..500).map { NarouNovel(ncode = "N%04dAA".format(it)) }
        coEvery { service.search(ncode = any(), lim = 1, of = "t-n-ga") } returns
            listOf(NarouNovel(allcount = 1), NarouNovel(ncode = "N0501AA"))

        val result = repository.novelDetailsBulk(ncodes)

        assertEquals(501, result.size)
        assertEquals("N0001AA", result.first().ncode)
        assertEquals("N0501AA", result.last().ncode)
        coVerify(exactly = 1) { service.search(ncode = any(), lim = 500, of = "t-n-ga") }
        coVerify(exactly = 1) { service.search(ncode = any(), lim = 1, of = "t-n-ga") }
    }
}
