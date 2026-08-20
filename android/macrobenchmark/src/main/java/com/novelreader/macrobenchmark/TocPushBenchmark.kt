package com.novelreader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 本棚→**目次**の push 遷移（と Back の pop）の frame timing（jank）計測。
 *
 * なぜ既存3ベンチに足さず独立させたか＝**この遷移だけ性格が逆で、率では見つからない**:
 * 2026-08-19 の実機計測（各12窓・gfxinfo）で、本棚→目次は p50 11.0 / p90 19.4ms と平凡なのに
 * **p95 61.6 / p99 90.6 / 最大 138.0ms**（約8フレーム落ち）で、**12窓中11窓で 60ms 超が必ず1枚**出た
 * ＝偶発でなく構造。にもかかわらず jank 率は 13.83% と低い。対照の本棚→本文は率 20.77% と高い一方で
 * **尾は 56.0ms 頭打ち・100ms 超ゼロ**。着地先の中身だけを変えた同一手順でこうなるので、疑いは
 * **目次側の初回組み立て**にある（一次情報＝docs/knowledge/ranking-pager-jank-slow-ui-thread.md 末尾
 * 「率と尾は別の指標」）。**率で見張るゲートではこの重さを一生検出できない**ため、尾（P99）を主眼に
 * 独立した枠を立てる。
 *
 * 成立条件＝**progress 行を持たない本**（ここが本ベンチ固有の要点）:
 * 着地先は MainActivity の `startFile = getLastRead(bookId) ?: "index.html"` で決まる。既存シードの
 * 「章送り計測の書」は毎シード progress=chap_1 へリセットされる契約＝**必ず本文へ直行**し目次を踏まない。
 * よって [clearAndSeedLibrary] の `tocBook=true` で「章はあるが progress 行が無い」2冊目
 * （題「目次計測の書」）を足し、それを開く。
 *
 * **1反復に複数回の開閉を入れられる理由**（[TabSwipeBenchmark.openBookAndReturn] が末尾1回に絞ったのとの差）:
 * あちらは本文へ着地するため ChapterScreen の debounce/onStop フラッシュが lastReadAt>0 を書き、
 * 本棚の二層ソートで対象本が未読99冊の下へ沈んで2回目の find が空振りした。目次は違う——
 * **index.html は進捗保存のブロック対象**（NativeReadingScreen「【生命線】index.html（目次）への遷移は
 * 進捗を保存しない」）＝何度開閉しても progress 行が生まれず、本棚の並びも着地先も不変。
 * ゆえに [PUSH_COUNT] 回／反復で窓数を稼げる（5反復×6回＝30窓＝08-19 の12窓より厚い）。
 *
 * COLD 性・前面ガード・シード配達の作法は既存3ベンチと同一の根拠に基づく。
 */
@RunWith(AndroidJUnit4::class)
class TocPushBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pushToToc() {
        // 採用する JSON が今回の走行のものかを lastModified で検証するために使う（残骸 JSON 対策）。
        val startedAtEpochMs = System.currentTimeMillis()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            // startupMode=COLD は setupBlock の後に force-stop する仕様＝着地済みの面が殺されるため使わない。
            // 反復間のコールド性は setupBlock の killProcess() で自前確保する（既存3ベンチと同根）。
            startupMode = null,
            setupBlock = {
                // tocBook=true が本ベンチの成立条件（クラス KDoc「成立条件」）。gridMode/verticalMode を
                // 明示するのは既存ベンチと同じ面で測り、数字を並べて読めるようにするため。
                clearAndSeedLibrary(
                    count = SEED_COUNT,
                    gridMode = true,
                    chapterCount = CHAPTER_COUNT,
                    verticalMode = false,
                    tocBook = true,
                )

                killProcess()
                pressHome()
                startActivityAndWait()
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }

                // シード副作用の UI 検証（why は [verifySeededShelfCount]）。
                verifySeededShelfCount(SEED_COUNT)

                if (!device.wait(Until.hasObject(SHELF_MARKER), 10_000)) {
                    fail("本棚に着地しなかった＝push の起点が確定していない")
                }
            }
        ) {
            repeat(PUSH_COUNT) { i ->
                openTocAndReturn(i)
            }
        }

        if (TocPushBudget.isBudgetAssertEnabled()) {
            TocPushBudget.assertTocPushWithinBudget(startedAtEpochMs)
        }
    }

    /** 目次計測の書を開き（push）、Back で本棚へ戻す（pop）。[i] は fail 文言用の回数。 */
    private fun MacrobenchmarkScope.openTocAndReturn(i: Int) {
        // 書影の掴み方は [TabSwipeBenchmark.openBookAndReturn] と同形（text→desc の順）:
        // 題名ノードの出方はスキンで違い、和モダンD は題を Canvas 描画するため text を持たない。
        val book = device.wait(Until.findObject(By.text(TOC_BOOK_TITLE)), 5_000)
            ?: device.wait(Until.findObject(By.desc(TOC_BOOK_TITLE)), 5_000)
        if (book == null) {
            fail(
                "本棚に『$TOC_BOOK_TITLE』が現れなかった（${i + 1}回目）。シードの tocBook 指定が効いていない" +
                    "／前回の開閉で本棚の並びが変わった疑い"
            )
        }
        book!!.click()

        // 着地の徴は目次にしか無いノードを使う（章タイトルは本文にも目次にも出て面の判別にならない）。
        if (!device.wait(Until.hasObject(TOC_MARKER), 10_000)) {
            // 空振りの切り分け: 本文へ行ってしまったのなら progress 行が生まれている＝
            // 「目次は進捗を保存しない」前提が崩れたか、tocBook でない本を掴んでいる。
            val landedOnBody = device.hasObject(By.textStartsWith("第1章"))
            fail(
                if (landedOnBody) {
                    "目次でなく**本文**に着地した（${i + 1}回目）＝progress 行が存在する。" +
                        "tocBook の本を掴めていないか、目次経路が進捗を書くようになった疑い"
                } else {
                    "目次に着地しなかった（${i + 1}回目・本が開いていない／目次の意匠変更の疑い）"
                }
            )
        }
        // push アニメと目次の初回組み立てを最後まで走らせてから pop する（窓を混ぜない）。
        // 静止中はフレームが出ないため分位には乗らない。
        Thread.sleep(400)

        // 目次からの Back は本棚へ直行（内部スタックが空＝onNavigateToBookshelf）。
        device.pressBack()
        // gone を先に待つ（pop のコミット）→ そのうえで本棚の徴を確認する。本棚の徴は push 中も
        // ツリーに居うるため、出現だけでは pop の証拠にならない（TabSwipeBenchmark.awaitTab と同根）。
        if (!device.wait(Until.gone(TOC_MARKER), 10_000)) {
            fail("Back で目次から出られなかった（${i + 1}回目）")
        }
        if (!device.wait(Until.hasObject(SHELF_MARKER), 10_000)) {
            fail("目次からの Back で本棚へ戻らなかった（${i + 1}回目・pop 先が本棚でない疑い）")
        }
        // 次の click までに pop の settle を跨ぐ固定マージン（settle 中のタップは子セルへ届かない＝
        // TabSwipeBenchmark が実機で確定した 600ms と同値・同根）。
        Thread.sleep(600)
    }

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val SEED_COUNT = 100
        const val CHAPTER_COUNT = 50

        /** 1反復あたりの開閉回数。5反復×6＝30窓（2026-08-19 の12窓より厚い）。 */
        const val PUSH_COUNT = 6

        /** シーダーが tocBook=true のとき作る「章はあるが progress 行が無い」本の固定題。 */
        const val TOC_BOOK_TITLE = "目次計測の書"

        /** 本棚＝読書状態フィルタの先頭チップ（D/K 共通・TabSwipeBenchmark と同じ徴）。 */
        val SHELF_MARKER: BySelector = By.text("すべて")

        /** 目次＝「本棚に戻る」ボタンの contentDescription（2026-08-06 実機 dump で確認済みの徴）。 */
        val TOC_MARKER: BySelector = By.desc("本棚に戻る")
    }
}
