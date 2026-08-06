package com.novelreader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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
        // gridMode はテスト毎に異なるため各テストが該当モードを指定してシードする（DB 投入自体は冪等）。
        // ⚠️ 2026-08-05 以前はこの指定が**効いていなかった**（シーダーが D の is_grid_view しか書かず、
        // benchmark ビルドは ADR 0027 のゲートで明快K へクランプされ K は k_grid_view を読むため）＝
        // scrollList / scrollGrid が両方とも K のグリッドを測っていた。シーダー側で両キーを書くよう
        // 是正済み（LibrarySeedReceiver の why 参照）。**この是正で scrollList の実測値は初めてリスト面の
        // ものになる＝過去のベースラインとは比較不能**（scrollGrid 側は従来と同じ面＝連続性あり）。
        measureScroll("scrollList", gridMode = false)
    }

    @Test
    fun scrollGrid() {
        measureScroll("scrollGrid", gridMode = true)
    }

    /** cold start → 本棚を掴んで下フリング×3・上フリング×3。フレーム時間は FrameTimingMetric が採取する。 */
    private fun measureScroll(testName: String, gridMode: Boolean) {
        // measureRepeated 開始前の時刻。採用する JSON がこの走行で書き出されたものかを
        // lastModified で検証するために使う（残骸 JSON による偽判定防止＝ScrollBudget 参照）。
        val startedAtEpochMs = System.currentTimeMillis()

        // シードは初回反復の setupBlock で1回だけ行う: 投入は冪等で、本ベンチの measureBlock は棚を
        // フリングするだけ＝蔵書・progress を汚さないため、毎反復の再配達（前面起動＋broadcast 往復）は
        // 純オーバーヘッド。毎反復のリセットが要る章送り系（[ChapterFlipBenchmark]／[TabSwipeBenchmark]）
        // とはここだけ意図的に違う。
        var seeded = false

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
                if (!seeded) {
                    // 配達は 2026-08-06 の修理形（前面生存プロセスへ＋resultData 実在検証＋全消し前置き）＝
                    // 機序と各検証の why は [clearAndSeedLibrary] の KDoc に集約
                    // （一次情報＝docs/knowledge/coloros-broadcast-silent-drop.md）。
                    clearAndSeedLibrary(count = SEED_COUNT, gridMode = gridMode)
                    seeded = true
                }
                killProcess()
                pressHome()
                startActivityAndWait()
                // 起動の実効を検証: launcher 自身も scrollable を持つため scrollable 待ちだけでは
                // 「アプリ未起動のままホームを計測」する事故を素通りさせる（上記の実測事故の再発防止ガード）。
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }
                // シード副作用の UI 検証: scrollable 待ちは状態フィルタチップ行（水平 scrollable）でも真に
                // なるため、棚が空のままでも素通りして空振り計測になりうる——冊数ヘッダで「計測する面が
                // 100冊 を表示している」ことまで留める（詳細な why は [verifySeededShelfCount]）。
                verifySeededShelfCount(SEED_COUNT)
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
        // シード配達の宛先・action は共通ヘルパ（LibrarySeeding.kt）側の契約値に集約した。
        const val SEED_COUNT = 100
    }
}
