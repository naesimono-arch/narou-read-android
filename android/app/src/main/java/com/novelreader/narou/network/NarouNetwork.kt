package com.novelreader.narou.network

import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NarouNetwork {
    private const val BASE_URL = "https://api.syosetu.com/"

    // なぜ User-Agent を設定するか: なろうAPIは利用マニュアルで推奨されているように、
    // 行儀の良いUA（意味のあるUser-Agent）を求めるため。
    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", "NovelReader-Android/1.0")
                .build()
            return chain.proceed(request)
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        // なぜ callTimeout（全体上限）を設けるか: OkHttp の既定では全体タイムアウトが無制限で、
        // 低速だが切れない接続だと検索/詳細のリクエストが永久に完了せず、呼び出し元のコルーチンを
        // 掴んだままになりうる（新着チェック Worker 等では実行枠を無駄に占有する）。ランキング/検索/
        // 作品詳細の応答は小さく数秒で返るのが正常なので、接続確立から本文受信までの全体を 30 秒で打ち切る。
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // なぜ KotlinJsonAdapterFactory を付けないか:
    // このプロジェクトでは Moshi の @JsonClass(generateAdapter = true) による codegen アダプタを使用しているため、
    // 実行時のリフレクションを伴う KotlinJsonAdapterFactory を追加して kotlin-reflect を持ち込む必要がないから。
    private val moshi = Moshi.Builder().build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: NarouApiService by lazy {
        retrofit.create(NarouApiService::class.java)
    }
}
