package com.novelreader.narou.network

import com.novelreader.narou.model.NarouNovel
import retrofit2.http.GET
import retrofit2.http.Query

interface NarouApiService {
    @GET("novelapi/api/")
    suspend fun search(
        @Query("out") out: String = "json",
        // なぜ of/order/lim を Nullable にしているか:
        // Retrofit はクエリパラメータの引数が null の場合、そのクエリパラメータをリクエストURLから自動的に省略する仕様であるため。
        @Query("of") of: String? = null,       // 縦スライスは "t-n-w-s-gp-ga-e-l-nt"
        @Query("order") order: String? = null, // 縦スライスは "weekly"
        @Query("lim") lim: Int? = null,
        @Query("st") st: Int? = null,
        @Query("word") word: String? = null,
        @Query("notword") notword: String? = null,
        @Query("title") title: Int? = null,
        @Query("ex") ex: Int? = null,
        @Query("keyword") keyword: Int? = null,
        @Query("wname") wname: Int? = null,
        @Query("biggenre") biggenre: String? = null,
        @Query("genre") genre: String? = null,
        @Query("istensei") istensei: Int? = null,
        @Query("istenni") istenni: Int? = null,
        @Query("istt") istt: Int? = null,
        @Query("iszankoku") iszankoku: Int? = null,
        @Query("notzankoku") notzankoku: Int? = null,
        @Query("type") type: String? = null,
        @Query("lastup") lastup: String? = null,
        @Query("length") length: String? = null,
        @Query("time") time: String? = null,
        @Query("kaiwaritu") kaiwaritu: String? = null,
        @Query("sasie") sasie: String? = null,
        @Query("ncode") ncode: String? = null,
    ): List<NarouNovel>
}
