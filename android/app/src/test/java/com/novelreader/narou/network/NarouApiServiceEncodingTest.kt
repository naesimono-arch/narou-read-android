package com.novelreader.narou.network

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Retrofit が実際に組み立てる URL のエンコード形を、MockWebServer で発行した実リクエストから録取して固定するテスト。
 *
 * なぜ必要か: 既存の API テストは mockk で NarouApiService を差し替えており、Retrofit の @Query が
 * 日本語 word をどう percent-encoding するか・null 引数が本当に URL から省かれるか等の「実配線」は未検証だった。
 * ここでは実 Retrofit（本番と同じ MoshiConverterFactory）経由で1本発行し、クエリ文字列を固定する。
 */
class NarouApiServiceEncodingTest {

    private lateinit var server: MockWebServer
    private lateinit var service: NarouApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // 本番 NarouNetwork と同じ変換器構成（codegen Moshi）で組む。
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
        service = retrofit.create(NarouApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    // メソッド名に % を含めない（本リポジトリは /mnt/c で Windows と共有のため、クラスファイル名に化けると問題を起こす）
    fun `search - 日本語wordはUTF8のpercentエンコードになり空白はプラスでなくパーセント20・null引数はURLから省かれること`() = runBlocking {
        // レスポンスは最小（先頭要素 allcount のみ）で足りる。関心は送信URLのエンコード形。
        server.enqueue(MockResponse().setBody("""[{"allcount":0}]"""))

        service.search(
            of = "t-n-w-gp-dp-wp-mp-qp-ga-e-l-nt-g-ti-nu",
            order = "weeklypoint",
            word = "転生 悪役", // 日本語＋半角空白でエンコード形を固定
            // 他のクエリ引数は既定 null のまま＝URL から省かれることを併せて検証する
        )

        val recorded = server.takeRequest()
        val rawQuery = recorded.requestUrl!!.encodedQuery!!

        // 送信URLのエンコード形を丸ごと固定する（引数の宣言順にクエリが並ぶ）。
        // 転生=E8BBA2 E7949F / 悪役=E682AA E5BDB9、半角空白は %20（+ ではない）。
        assertEquals(
            "out=json" +
                "&of=t-n-w-gp-dp-wp-mp-qp-ga-e-l-nt-g-ti-nu" +
                "&order=weeklypoint" +
                "&word=%E8%BB%A2%E7%94%9F%20%E6%82%AA%E5%BD%B9",
            rawQuery
        )

        // null 引数（lim/st/genre 等）が URL に一切現れないことを明示的にも固定する。
        assertTrue("null引数は省略されるべき", !rawQuery.contains("lim=") && !rawQuery.contains("genre="))

        // デコードすれば元の日本語＋空白へ戻る（percent-encoding のラウンドトリップ）。
        assertEquals("転生 悪役", recorded.requestUrl!!.queryParameter("word"))
    }
}
