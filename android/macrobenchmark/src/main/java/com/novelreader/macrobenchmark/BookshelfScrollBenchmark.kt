package com.novelreader.macrobenchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 本棚スクロール時の frame timing（jank）計測。100冊のフェイク蔵書をシードしてから、
 * リスト表示（[scrollList]）とグリッド表示（[scrollGrid]）でフリング往復のフレーム時間を測る。
 *
 * 予算 assert は実装しない（較正方針＝初回は計測のみ）。値が溜まってから閾値を決める。
 */
@RunWith(AndroidJUnit4::class)
class BookshelfScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollList() {
        // gridMode はテスト毎に異なるため各テスト冒頭で該当モードを指定してシードする（DB 投入自体は冪等）。
        seedLibrary(gridMode = false)
        measureScroll()
    }

    @Test
    fun scrollGrid() {
        seedLibrary(gridMode = true)
        measureScroll()
    }

    /**
     * LibrarySeedReceiver へ ordered broadcast を送り、100冊を投入して完了を待つ。
     *
     * なぜシェル `am broadcast` でなく sendOrderedBroadcast か:
     * ColorOS では UiAutomation のシェル実行が pipe EOF を返さず永久ハングする
     * （docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md）。シェルを経由しないアプリ内
     * broadcast なら同ハングを回避でき、かつ ordered broadcast の resultReceiver で完了・件数を確実に受け取れる。
     */
    private fun seedLibrary(gridMode: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().context
        // 明示コンポーネント指定（暗黙 action だけだと別アプリの Receiver へは届かない）。
        val intent = Intent().apply {
            setClassName(TARGET_PACKAGE, RECEIVER_CLASS)
            action = ACTION_SEED
            putExtra("count", SEED_COUNT)
            putExtra("gridMode", gridMode)
        }

        val latch = CountDownLatch(1)
        // resultReceiver は別スレッドから読むため配列に退避（onReceive はメインルーパで走る）。
        // resultData も拾う: シーダーは失敗時に理由の要約をここへ載せる＝fail メッセージの診断材料。
        val resultCode = intArrayOf(Int.MIN_VALUE)
        val resultData = arrayOfNulls<String>(1)
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                resultCode[0] = getResultCode()
                resultData[0] = getResultData()
                latch.countDown()
            }
        }

        context.sendOrderedBroadcast(intent, null, resultReceiver, null, 0, null, null)

        if (!latch.await(60, TimeUnit.SECONDS)) {
            fail("シード用 ordered broadcast が 60s 以内に完了しなかった（Receiver 未登録 or DB 投入ハングの疑い）")
        }
        // 期待件数に満たなければ即 fail（黙って計測を続けない）。resultCode は投入後の bench_seed 件数。
        if (resultCode[0] != SEED_COUNT) {
            fail("シード結果 resultCode=${resultCode[0]}（期待 $SEED_COUNT）: ${resultData[0]}。投入未達のまま計測しない")
        }
    }

    /** cold start → 本棚を掴んで下フリング×3・上フリング×3。フレーム時間は FrameTimingMetric が採取する。 */
    private fun measureScroll() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            // compilationMode は既定（未指定）＝CompilationMode.DEFAULT。
            setupBlock = {
                pressHome()
                startActivityAndWait()
                // 本棚のスクロール可能コンテナ（testTag が皆無なので scrollable フラグで掴む）が出るまで待つ。
                device.wait(Until.hasObject(By.scrollable(true)), 10_000)
            }
        ) {
            val shelf = device.findObject(By.scrollable(true))
            // エッジのシステムジェスチャ（戻る等）と競合しないようフリング開始マージンを広げる。
            shelf.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                shelf.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                shelf.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.LibrarySeedReceiver"
        const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"
        const val SEED_COUNT = 100
    }
}
