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
 * 長時間の章送り（スワイプで次章へ進み続ける）時の frame timing（jank）計測。
 * 実HTML 50章を持つ計測用の1冊（title「章送り計測の書」）をシードし、chap_1 本文に着地してから
 * 左スワイプ（＝次章）を30回連続で送り、その間のフレーム時間を FrameTimingMetric で採る。
 *
 * 章送りは本文レイアウト再構築＋HTML パース＋ルビ整形を毎回走らせる重い遷移で、本棚スクロール
 * （[BookshelfScrollBenchmark]）とは別種の jank 源になりうるため独立ベンチとして分ける。
 *
 * 予算 assert は本ベンチでは実装しない（初回の実測で分布を掴んでから較正して追加する方針＝
 * [ScrollBudget] と同じ流儀。較正前の恣意的な閾値で「黙って緑」を作らないため）。
 * よって instrumentation 引数 `enableBudgetAssert` / `budget*` は本クラスでは参照しない。
 *
 * COLD 性・シード配達・前面ガード・StaleObjectException 再試行の各作法は [BookshelfScrollBenchmark]
 * と同一の根拠（ColorOS の沈黙不達／COLD の force-stop 仕様／launcher も scrollable を持つ／
 * Compose ツリー変化での stale）に基づく。詳細な機序は同クラスの KDoc とコメントを参照。
 */
@RunWith(AndroidJUnit4::class)
class ChapterFlipBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flipChapters() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            // startupMode=COLD は「setupBlock の後」に対象プロセスを force-stop する仕様のため使わない
            // （ここで使うと着地済みの読書画面が殺され、ホームをスワイプして frame 0 件で落ちる）。
            // 反復間のコールド性は setupBlock 冒頭の killProcess() で自前確保する（[BookshelfScrollBenchmark]
            // と同じ根拠＝docs/knowledge/macrobenchmark-frametiming-scroll-pitfalls.md §1）。
            startupMode = null,
            // compilationMode は既定（未指定）＝CompilationMode.DEFAULT。
            setupBlock = {
                // (1) 毎反復シードする。DB 投入自体は冪等だが、目的は progress を chap_1.html へ戻す
                //     決定論リセット（前反復が第31章まで送った状態を持ち越さない）。件数不一致は即 fail。
                seedChapterBook()

                // (2) コールド起動して前面ガード。launcher 自身も scrollable を持つため（同 knowledge §2）、
                //     scrollable 待ちでは未起動を検知できない。By.pkg で対象アプリの前面化を必ず検証する。
                killProcess()
                pressHome()
                startActivityAndWait()
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }

                // (3) 本棚（addedAt 降順で計測用の書が先頭付近に出る）から計測用の書を掴んで開く。
                //     念のため出現待ち10s。見つからなければ fail（本棚未表示 or シード契約違反を黙って計測しない）。
                val book = device.wait(Until.findObject(By.text(MEASURE_BOOK_TITLE)), 10_000)
                if (book == null) {
                    fail("本棚に『$MEASURE_BOOK_TITLE』が現れなかった（シード契約違反 or 本棚未表示の疑い）")
                }
                book!!.click()

                // (4) chap_1 本文への着地を確認。progress があるので目次でなく本文先頭（第1章）に直接着地する
                //     はず。第1章の Text（TopAppBar/ChapterHeader いずれかに全文で出る）を待ち、出なければ fail
                //     （目次着地＝シーダー契約違反や progress 未反映を黙って計測しない）。
                if (!device.wait(Until.hasObject(By.textStartsWith("第1章")), 10_000)) {
                    fail("chap_1 本文（第1章）に着地しなかった（目次着地＝progress リセット未反映の疑い）")
                }
            }
        ) {
            // 前進30回の章送り。第1章に着地済みの状態から始め、i 回目のスワイプ後に第(i+2)章の出現を待つ。
            // 最終は第31章（全50章の範囲内＝端章での送れない事故は起きない）。
            repeat(FLIP_COUNT) { i ->
                val expectedChapter = i + 2  // 着地は第1章。1回目のスワイプで第2章 → 30回目で第31章。
                swipeToNextChapter()
                // 章送りの発火を確認。左スワイプが touch slop 未満だとタップ扱い（没入クロームのトグル）に
                // 化けて章が進まないため、次章タイトルの出現を必ず待つ。出なければ fail（空振りしたまま
                // 計測を続けない）。
                if (!device.wait(Until.hasObject(By.textStartsWith("第${expectedChapter}章")), 5_000)) {
                    fail("章送りが第${expectedChapter}章へ進まなかった（${i + 1}回目のスワイプ）。" +
                        "スワイプがタップ扱い（クローム トグル）に化けた or 移動量 96dp 未満の疑い")
                }
            }
        }
    }

    /**
     * LibrarySeedReceiver へ shell `am broadcast` を送り、計測用の書（実HTML 50章・progress=chap_1）を
     * 投入して完了を待つ。作法は [BookshelfScrollBenchmark.seedLibrary] と同一。
     *
     * shell 経由（app-to-app の sendOrderedBroadcast ではなく）と force-stop 前置きの理由は ColorOS の
     * broadcast 沈黙不達（docs/knowledge/coloros-broadcast-silent-drop.md）。「dead＝非凍結状態への
     * shell broadcast」だけが確実に配達される。shell 実行のハングリスクは run_macrobenchmark.sh の
     * SIGQUIT 除細動ループが前提＝このベンチは必ず同スクリプト経由で実行する。
     *
     * chapterCount 50 を足すと最新の1冊が「章送り計測の書」＝実HTML 50章・progress を chap_1.html へ
     * 強制リセットしてシードされる（アプリ側シーダーの確定契約）。gridMode は本ベンチでは不使用のため false。
     */
    private fun seedChapterBook() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // force-stop で「プロセス dead＝HANS 凍結なし」を保証（COLD 計測前なのでアプリ状態を壊す副作用は無い）。
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        // --include-stopped-packages: force-stop 直後の stopped state で配達除外される穴を塞ぐ保険。
        val out = device.executeShellCommand(
            "am broadcast --include-stopped-packages" +
                " -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_SEED" +
                " --ei count $SEED_COUNT --ei chapterCount $CHAPTER_COUNT --ez gridMode false"
        )
        // am broadcast は ordered 配達の完了まで待ち「Broadcast completed: result=N, data="…"」を出力する。
        // 期待件数に満たなければ即 fail（黙って計測を続けない）。result は投入後の bench_seed 件数。
        val result = Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
        if (result != SEED_COUNT) {
            fail("シード結果 result=$result（期待 $SEED_COUNT）。am broadcast 出力: $out")
        }
    }

    /**
     * 読書画面を毎回新しく掴んで左スワイプ（＝次章）する。
     * 読書画面には本文の縦 LazyColumn（scrollable）があり、その領域上での水平スワイプが章送りの
     * 水平 draggable を叩く。複数 scrollable から誤爆しないよう可視領域が最大高のものを選ぶ。
     * swipe は 0.8f（可視幅の80%）で移動量 96dp 超（章送り確定条件）を確実に満たす。
     *
     * UiObject2 は毎回取り直す: 章送りで本文セマンティクスツリーが総入れ替えになるため、掴んだ参照を
     * 使い回すと StaleObjectException で死ぬ。取り直し直後でも遷移アニメ中のツリー変化と競合して stale に
     * なりうるため、1回だけ取り直して再試行する（2連続 stale は異常として伝播）。
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeToNextChapter() {
        fun grab(): UiObject2 {
            val candidates = device.findObjects(By.scrollable(true))
            if (candidates.isEmpty()) fail("scrollable が見つからない（読書画面未表示の疑い）")
            val content = candidates.maxByOrNull { it.visibleBounds.height() }!!
            // エッジのシステムジェスチャ（戻る等）と競合しないようスワイプ開始マージンを広げる。
            // 左スワイプの起点が右エッジのジェスチャ帯に食い込むと誤爆するため特に重要。
            content.setGestureMargin(device.displayWidth / 5)
            return content
        }
        try {
            grab().swipe(Direction.LEFT, 0.8f)
        } catch (e: StaleObjectException) {
            device.waitForIdle()
            grab().swipe(Direction.LEFT, 0.8f)
        }
    }

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.LibrarySeedReceiver"
        const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"
        const val SEED_COUNT = 100
        const val CHAPTER_COUNT = 50
        const val FLIP_COUNT = 30
        const val MEASURE_BOOK_TITLE = "章送り計測の書"
    }
}
