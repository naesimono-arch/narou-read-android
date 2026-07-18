package com.novelreader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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
 * 予算 assert（instrumentation 引数 `enableBudgetAssert` が true のときのみ）＝measureRepeated 完了
 * 直後に [FlipBudget] が benchmarkData.json を読んで P50/P90/P99 を判定する（初回実測から較正した
 * 予算値・判定の値源の機序は同オブジェクト参照）。既定は従来どおり計測のみ。
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
        // measureRepeated が書き出す benchmarkData.json が「今回の走行」のものであることを
        // lastModified で検証するために使う（残骸 JSON による偽判定防止＝FlipBudget 参照）。
        val startedAtEpochMs = System.currentTimeMillis()
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
                val currentChapter = i + 1   // 着地は第1章。i 回目のスワイプ前に表示中の章。
                val expectedChapter = i + 2  // 1回目のスワイプで第2章 → 30回目で第31章。
                swipeToNextChapter()
                // 遷移「コミット」の実信号＝旧章タイトルの消滅を待つ（2026-07-18 実機切り分けで確定）:
                // 引っ張りプレビューはドラッグ開始直後から次章の冒頭（ChapterHeader）を先読み描画するため、
                // 「次章タイトルの出現」は settle 前でも真になり、コミットの証拠にならない。waitForIdle も
                // Compose の settle アニメを busy と見なさず素通しする（accessibility イベントが静かなため）。
                // その2つだけで次のスワイプへ進むと、遷移コミット前のツリーへ注入して章送りが壊れる
                // （実機で2〜3回目のスワイプが再現的に不発。手動の約2秒間隔では完全安定＝アプリ側は健全）。
                // プレビュー中は新旧タイトルが併存し、settle 完了＝ナビゲーション確定で旧章が畳まれて消える
                // ため、「旧章の gone」がコミットと1:1 に対応する。
                if (!device.wait(Until.gone(By.textStartsWith("第${currentChapter}章")), 5_000)) {
                    // 診断: fail 時点で a11y ツリーに居る章タイトルを列挙する。「旧章が残存」には
                    // ①章送り自体が不発（旧章のみ居る）②章送りは成功したが旧章ノードも併存
                    // （隣章プレビューの先読みコンポーズ等＝検知側の偽 FAIL）の2様があり、
                    // ツリーの実内容だけが両者を切り分けられる。
                    val visible = device.findObjects(By.textStartsWith("第"))
                        .mapNotNull { it.text }.distinct().sorted()
                    fail("章送りがコミットしなかった（${i + 1}回目のスワイプ後も第${currentChapter}章が残存）。" +
                        "ツリー内の章タイトル: $visible")
                }
                // コミット後の表示章が期待どおりかを確認（旧章が消えただけで別章へ飛んでいないことの検証）。
                if (!device.wait(Until.hasObject(By.textStartsWith("第${expectedChapter}章")), 5_000)) {
                    fail("章送り後に第${expectedChapter}章が表示されていない（${i + 1}回目のスワイプ）")
                }
                // 新章 Content の入力受付が整うまでの固定マージン（2026-07-18 切り分けの帰結）:
                // 章切替で ChapterScreenContent は毎回作り直され、bodyWidthPx（onSizeChanged で実測）が
                // 0 の初期化窓では draggable の clamp が min=max=0 になり、その間に注入したスワイプは
                // 全 delta が潰れて settle 不発＝「無視」される。しかも上の gone はスライドアニメ末尾
                // （旧章が画面外＝a11y 除外）で navigate 前に真になるため、待ちナシだと初期化窓を直撃する。
                // この窓は a11y から観測不能（幅確定を示すノードが無い）ため、固定 400ms で跨ぐ。
                // 400ms の根拠: 手動 `input swipe` の実証帯（+250〜700ms 間隔で 10/10 全弾命中）の中央値相当。
                // 注入方式切替（UiObject2→UiDevice→shell input）では直らず、失敗が常に2回目以降
                // （初章の Content は画面入場時に幅確定済み）である事実と唯一整合する機序への対処。
                // 計測への影響: sleep 中は静止＝フレームが出ないため FrameTiming の分位には乗らない。
                Thread.sleep(400)
            }
        }
        // このリターン時点で書き出し済みの benchmarkData.json を読んで判定する（FlipBudget 参照）。
        if (FlipBudget.isBudgetAssertEnabled()) {
            FlipBudget.assertFlipWithinBudget(startedAtEpochMs)
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
     * 画面中央の高さで右→左の高速スワイプ（＝次章）を shell `input swipe` で注入する。
     *
     * 注入方式の経緯（2026-07-18 切り分け）: 章送り不発は UiObject2.swipe（遅い注入）→
     * UiDevice.swipe（高速注入）→ shell input と3方式を替えても症状不変で、**真因は注入経路ではなく
     * 新章 Content の入力受付初期化窓**だった（measureBlock 側の固定マージンが恒久対処＝そちらのコメント参照）。
     * shell input を採用のまま残すのは、手動 `input swipe` の 10/10 実証と完全同形＝検証済み経路であり、
     * UiDevice.swipe へ戻す利得（僅かな高速化）が再実証コストに見合わないため。shell 実行のハング癖は
     * run_macrobenchmark.sh の SIGQUIT 除細動ループが前提として吸収する（シード broadcast と同じ作法）。
     *
     * 座標の根拠: y=画面中央（本文領域のど真ん中）・x=幅の 0.8→0.2（移動 0.6W≒約250dp ＝
     * 章送り確定条件 96dp 超を大きく満たし、起点は右エッジの戻るジェスチャ帯（数十px）から十分離れる）。
     * 尺 100ms は手動実証と同値。
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeToNextChapter() {
        val w = device.displayWidth
        val y = device.displayHeight / 2
        device.executeShellCommand(
            "input swipe ${(w * 0.8f).toInt()} $y ${(w * 0.2f).toInt()} $y 100"
        )
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
