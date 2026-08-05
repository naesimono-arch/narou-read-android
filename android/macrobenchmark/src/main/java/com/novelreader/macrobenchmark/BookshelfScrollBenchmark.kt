package com.novelreader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 本棚スクロール時の frame timing（jank）計測。100冊のフェイク蔵書をシードしてから、
 * リスト表示（[scrollList]）とグリッド表示（[scrollGrid]）でフリング往復のフレーム時間を測る。
 *
 * 既定は計測のみ（従来挙動不変）。instrumentation 引数 `enableBudgetAssert true` のときだけ、
 * measureRepeated 完了直後に jank 予算を assert する（判定の値源・予算の由来は [ScrollBudget] 参照）。
 */
@RunWith(AndroidJUnit4::class)
class BookshelfScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollList() {
        // gridMode はテスト毎に異なるため各テスト冒頭で該当モードを指定してシードする（DB 投入自体は冪等）。
        // ⚠️ 2026-08-05 以前はこの指定が**効いていなかった**（シーダーが D の is_grid_view しか書かず、
        // benchmark ビルドは ADR 0027 のゲートで明快K へクランプされ K は k_grid_view を読むため）＝
        // scrollList / scrollGrid が両方とも K のグリッドを測っていた。シーダー側で両キーを書くよう
        // 是正済み（LibrarySeedReceiver の why 参照）。**この是正で scrollList の実測値は初めてリスト面の
        // ものになる＝過去のベースラインとは比較不能**（scrollGrid 側は従来と同じ面＝連続性あり）。
        seedLibrary(gridMode = false)
        measureScroll("scrollList")
    }

    @Test
    fun scrollGrid() {
        seedLibrary(gridMode = true)
        measureScroll("scrollGrid")
    }

    /**
     * LibrarySeedReceiver へ shell `am broadcast` を送り、100冊を投入して完了を待つ。
     *
     * なぜ app-to-app の sendOrderedBroadcast ではなく shell 経由か（2026-07-17 実機実測で確定）:
     * ColorOS は broadcast を2様に**沈黙不達**にする（いずれも「Broadcast completed: result=0」の正常完了に化ける）。
     *   ① 背景アプリ（このテストプロセス）発の broadcast は、dead な対象プロセスの起動を伴う配達が遮断される
     *      （自動起動制限。FLAG_INCLUDE_STOPPED_PACKAGES でも不達を実測）。
     *   ② プロセスが生きていても OplusHansManager（凍結管理）が凍結中プロセスへの配達をスキップする
     *      （shell 発でも不達になることを実測）。
     * 唯一確実だった条件＝「dead（＝非凍結）状態への shell broadcast」（AMS がプロセス起動込みで配達する）。
     * そこで force-stop で dead 状態を決定論化してから shell で送る。shell 実行のハングリスク
     * （docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md）は run_macrobenchmark.sh の
     * SIGQUIT 除細動ループが前提＝このベンチは必ず同スクリプト経由で実行する。
     */
    private fun seedLibrary(gridMode: Boolean) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // force-stop で「プロセス dead＝HANS 凍結なし」を保証（COLD 計測前なのでアプリ状態を壊す副作用は無い）。
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        // --include-stopped-packages: force-stop 直後の stopped state で配達除外される穴を塞ぐ保険。
        val out = device.executeShellCommand(
            "am broadcast --include-stopped-packages" +
                " -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_SEED" +
                " --ei count $SEED_COUNT --ez gridMode $gridMode"
        )
        // am broadcast は ordered 配達の完了まで待ち「Broadcast completed: result=N, data="…"」を出力する。
        // 期待件数に満たなければ即 fail（黙って計測を続けない）。result は投入後の bench_seed 件数。
        val result = Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
        if (result != SEED_COUNT) {
            fail("シード結果 result=$result（期待 $SEED_COUNT）。am broadcast 出力: $out")
        }
    }

    /** cold start → 本棚を掴んで下フリング×3・上フリング×3。フレーム時間は FrameTimingMetric が採取する。 */
    private fun measureScroll(testName: String) {
        // measureRepeated 開始前の時刻。採用する JSON がこの走行で書き出されたものかを
        // lastModified で検証するために使う（残骸 JSON による偽判定防止＝ScrollBudget 参照）。
        val startedAtEpochMs = System.currentTimeMillis()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            // startupMode=COLD は使わない: COLD は「setupBlock の後」に対象プロセスを force-stop する仕様
            // （起動ベンチが launch を measureBlock に書くのはこのため）。ここで使うと setup 内の起動が
            // 無効化され、ホーム画面をフリングして frame 0 件で落ちる（実機実測＝トレースのフレーム帰属が
            // launcher/quicksearchbox のみだった）。反復間のコールド性は killProcess() で自前確保する。
            startupMode = null,
            // compilationMode は既定（未指定）＝CompilationMode.DEFAULT。
            setupBlock = {
                killProcess()
                pressHome()
                startActivityAndWait()
                // 起動の実効を検証: launcher 自身も scrollable を持つため scrollable 待ちだけでは
                // 「アプリ未起動のままホームを計測」する事故を素通りさせる（上記の実測事故の再発防止ガード）。
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }
                // 本棚のスクロール可能コンテナ（testTag が皆無なので scrollable フラグで掴む）が出るまで待つ。
                device.wait(Until.hasObject(By.scrollable(true)), 10_000)
            }
        ) {
            // UiObject2 はフリング毎に取り直す: 本棚はスクロールでセマンティクスツリーが変わる
            // （発見帯の退避 collapse 等）ため、掴んだ参照を使い回すと2回目以降の fling が
            // StaleObjectException で死ぬ（実機で実測）。
            repeat(3) {
                flingShelf(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                flingShelf(Direction.UP)
                device.waitForIdle()
            }
        }

        // ゲート ON のときだけ予算判定。measureRepeated は void で結果を返さないため、
        // このリターン時点で書き出し済みの benchmarkData.json を読んで判定する（ScrollBudget 参照）。
        if (ScrollBudget.isBudgetAssertEnabled()) {
            ScrollBudget.assertScrollWithinBudget(testName, startedAtEpochMs)
        }
    }

    /**
     * 本棚のスクロールコンテナを毎回新しく掴んでフリングする。
     * 複数の scrollable（水平の状態フィルタチップ列など）から誤爆しないよう、可視領域が最大高のものを選ぶ
     * （本棚のグリッド/リストが画面の大半を占める前提）。取り直し直後でも退避アニメ中のツリー変化と
     * 競合して stale になりうるため、1回だけ取り直して再試行する（2連続 stale は異常として伝播）。
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.flingShelf(direction: Direction) {
        fun grab(): UiObject2 {
            val candidates = device.findObjects(By.scrollable(true))
            if (candidates.isEmpty()) fail("scrollable が見つからない（本棚未表示の疑い）")
            val shelf = candidates.maxByOrNull { it.visibleBounds.height() }!!
            // エッジのシステムジェスチャ（戻る等）と競合しないようフリング開始マージンを広げる。
            shelf.setGestureMargin(device.displayWidth / 5)
            return shelf
        }
        try {
            grab().fling(direction)
        } catch (e: StaleObjectException) {
            device.waitForIdle()
            grab().fling(direction)
        }
    }

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.LibrarySeedReceiver"
        const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"
        const val SEED_COUNT = 100
    }
}
