package com.novelreader.narou

import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.network.NarouApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
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
            NarouNovel(title = "作品1", ncode = "N1111A", novelType = 1, end = 1),
            NarouNovel(title = "作品2", ncode = "N2222A", novelType = 2, end = 0)
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

        val result = repository.novelDetail("N1234AB")

        assertNotNull(result)
        assertEquals("詳細作品", result!!.title)
        assertEquals("N1234AB", result.ncode)

        // キャッシュの検証：2回目は API が呼ばれない
        val cachedResult = repository.novelDetail("N1234AB")
        assertEquals(result, cachedResult)
        coVerify(exactly = 1) { service.search(ncode = "N1234AB", lim = 1, of = null) }
    }

    @Test
    fun `novelDetail - 作品要素なし (allcountのみ) の場合に null が返ること`() = runTest {
        coEvery { service.search(ncode = "N9999XX", lim = 1, of = null) } returns listOf(
            NarouNovel(allcount = 0)
        )

        val result = repository.novelDetail("N9999XX")

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
}
