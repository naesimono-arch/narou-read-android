package com.novelreader.macrobenchmark

object BenchmarkTargets {
    // :app の benchmark ビルドタイプは applicationIdSuffix ".benchmark" で別パッケージ化してある
    // （実機の実蔵書 com.novelreader の DB/蔵書を計測用データ投入で壊さないため）
    const val TARGET_PACKAGE = "com.novelreader.benchmark"
}
