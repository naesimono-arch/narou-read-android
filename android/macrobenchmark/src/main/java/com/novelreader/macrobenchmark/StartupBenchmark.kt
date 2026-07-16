package com.novelreader.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * コールド起動の計測。
 * 既定は計測のみ（従来挙動不変）。instrumentation 引数 `enableBudgetAssert true` のときだけ、
 * measureRepeated 完了直後に起動予算を assert する（判定の値源・予算の由来は [StartupBudget] 参照）。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        // measureRepeated 開始前の時刻。採用する JSON がこの走行で書き出されたものかを
        // lastModified で検証するために使う（残骸 JSON による偽判定防止＝StartupBudget 参照）。
        val startedAtEpochMs = System.currentTimeMillis()

        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT
        ) {
            pressHome()
            startActivityAndWait()
        }

        // ゲート ON のときだけ予算判定。measureRepeated は void で結果を返さないため、
        // このリターン時点で書き出し済みの benchmarkData.json を読んで判定する（StartupBudget 参照）。
        if (StartupBudget.isBudgetAssertEnabled()) {
            StartupBudget.assertColdStartupWithinBudget(startedAtEpochMs)
        }
    }
}
