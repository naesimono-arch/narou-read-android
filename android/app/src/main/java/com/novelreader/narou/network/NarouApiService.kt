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
    ): List<NarouNovel>
}
