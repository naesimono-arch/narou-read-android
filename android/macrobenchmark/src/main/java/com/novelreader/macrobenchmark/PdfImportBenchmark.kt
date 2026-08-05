package com.novelreader.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 大PDF（N6169DZ＝約8.5MB・951章）の取込にかかるフェーズ別時間を計測する。app 側の抽出パイプラインへ
 * 挿入した Perfetto トレース区間（`Import#…` / `Extract#…`）を [TraceSectionMetric] で拾い、コピー・SHA・
 * 抽出・HTML 書き出し・DB 登録の内訳を可視化する。
 *
 * なぜ FrameTimingMetric を入れないか: 取込中は UI がほぼ静止（進捗バーのみ）でフレームが皆無になり、
 * FrameTimingMetric が 0 件で死ぬリスクがある（本棚スクロール／章送りベンチの frame 0 件死と同根＝
 * docs/knowledge/macrobenchmark-frametiming-scroll-pitfalls.md）。取込は TraceSection で測る。
 *
 * なぜ assets 同梱の実PDF を本番経路（[ImportBenchReceiver] mode=start → PdfProcessingService ACTION_START）で
 * 取り込むか: Android 11+ は push 方式（/sdcard/Android/data 直 push 不可・/data/local/tmp は SELinux で app 読取不可）が
 * 成立しないため、benchmark variant の assets に実PDF を載せ、アプリ自身に cacheDir へ展開させて本番の
 * FGS 取込経路をそのまま計測する（app/build.gradle の copyBenchmarkPdfAsset・[ImportBenchReceiver] 参照）。
 *
 * 予算 assert（instrumentation 引数 `enableBudgetAssert` が true のときのみ）＝measureRepeated 完了
 * 直後に [ImportBudget] が benchmarkData.json を読んで Import#extract / Extract#engine の median を
 * 判定する（初回実測から較正した予算値・median のみ判定する理由は同オブジェクト参照）。既定は従来どおり計測のみ。
 *
 * ColorOS の broadcast 沈黙不達（背景/凍結プロセスへの配達が result=0 の正常完了に化ける）は
 * docs/knowledge/coloros-broadcast-silent-drop.md が正本。さらに 2026-08-06 実機実証で「dead プロセスへの
 * shell broadcast も端末状態依存で同様に沈黙不達になる」ことが確定したため、clear/start とも
 * 前面＝生存・非凍結のプロセスへ配達する（setupBlock ①' のコメント参照）。shell 実行の
 * ハングリスクは run_macrobenchmark.sh の SIGQUIT 除細動ループが前提＝このベンチは必ず同スクリプト経由で実行する。
 */
@RunWith(AndroidJUnit4::class)
class PdfImportBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun importLargePdf() {
        // measureRepeated が書き出す benchmarkData.json が「今回の走行」のものであることを
        // lastModified で検証するために使う（残骸 JSON による偽判定防止＝ImportBudget 参照）。
        val startedAtEpochMs = System.currentTimeMillis()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            // 各区間は1 iteration に1回だけ発生するため Mode は First/Sum で一致（ここでは Sum に統一）。
            // Extract#engine / Extract#exportHtml / Import#extract は非 suspend の同期実行で begin/end が同一
            // スレッドに閉じる＝信頼できる。Import#insertDb は suspend な Room 呼び出しを包むため、Room の
            // executor 再ディスパッチで begin/end が別スレッドになりスライスが分裂しうる（値は要実測・app 側コメント参照）。
            metrics = listOf(
                TraceSectionMetric("Import#extract", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Extract#engine", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Extract#exportHtml", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Import#insertDb", TraceSectionMetric.Mode.Sum),
            ),
            // 1回の取込が長い（分オーダー）ため反復は3回に抑える。
            iterations = 3,
            // startupMode=COLD は使わない（setupBlock 後に force-stop する仕様＝[BookshelfScrollBenchmark] 参照）。
            // 反復間の白紙化は setupBlock の mode=clear＋killProcess で自前確保する。
            startupMode = null,
            // compilationMode は既定（未指定）＝CompilationMode.DEFAULT。
            setupBlock = {
                // ① dead 化＝HANS 凍結なしを決定論化（凍結プロセスへの配達スキップを断つ）。
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                // ①' clear は「生きた前面プロセス」へ配達する（2026-08-06 実機実証）:
                //    ColorOS は dead プロセスへの shell broadcast（--include-stopped-packages 付きでも）を
                //    端末状態依存で黙って落とす——プロセス起動なし・ordered の初期値 result=0 のまま
                //    「Broadcast completed」に化け、期待値 0（削除後 books 総数）と衝突して検知不能だった。
                //    残骸蔵書 → SHA 早期遮断(①')が 57ms で Duplicate → 上書き確認ダイアログが本棚を
                //    a11y から隠し完了検知 10 分タイムアウト、が旧 NG の全機序。前面＝生存プロセスへの
                //    配達は同日 3/3 で安定（不達は dead 時のみ 3/3 再現）。
                pressHome()
                startActivityAndWait()
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("clear 配達用の前面起動に失敗（アプリが前面に来なかった）")
                }
                // ② 白紙化: books/progress/pending_jobs 全消し＋novels/ さらい。resultCode=削除後 books 総数=0
                //    に加え resultData の実在（handleClear が必ず載せる "cleared books"）まで検証する:
                //    不達 broadcast は data を持たないため、result=0 だけの判定では不達が成功に化ける。
                val clearOut = device.executeShellCommand(
                    "am broadcast -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_IMPORT --es mode clear"
                )
                val cleared = resultCodeOf(clearOut)
                if (cleared != 0 || !clearOut.contains("cleared books")) {
                    fail("白紙化が配達されなかったか失敗（result=$cleared・期待 0＋resultData に配達証跡）。am broadcast 出力: $clearOut")
                }
                // ③ コールド性を自前確保してから前面起動（本棚が start 目的地）。killProcess を clear 直後に
                //    置くことで、①' の前面起動が誘発しうる startup-recovery の再取込（残 pending_jobs 起点）が
                //    走っていても DB 登録（抽出 24s の末尾）前に必ず打ち切られる＝clear 後の混入なし。
                killProcess()
                pressHome()
                startActivityAndWait()
                // 起動の実効を検証（launcher も scrollable を持つため By.pkg で前面化を必ず確認＝[BookshelfScrollBenchmark] 参照）。
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
                }
                // ③' 白紙状態の実効を UI で検証する（2026-08-06 実測の罠2件の砦）:
                //    (a) clear の raw execSQL は Room の invalidation を蹴らず、生きたプロセスの本棚表示は
                //        旧冊数のまま陳腐化する——直後の killProcess→再起動（③）で必ず 0冊 に戻るのが前提。
                //    (b) 冊数ヘッダは DB 駆動＝変換中も 0冊 のまま（measureBlock の完了検知「1冊」の前提条件）。
                if (!device.wait(Until.hasObject(By.text("0冊")), 10_000)) {
                    fail("白紙化後の本棚が 0冊 表示にならない（clear 不達か冊数ヘッダの意匠変更を疑う）")
                }
            }
        ) {
            // measureBlock: アプリ前面＝生存・非凍結なので shell broadcast(mode=start) が配達される（force-stop しない）。
            val startOut = device.executeShellCommand(
                "am broadcast -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_IMPORT --es mode start"
            )
            val accepted = resultCodeOf(startOut)
            // resultCode=1 は「取込の起動受理」（取込完了ではない）。受理されなければ即 fail（黙って計測を続けない）。
            if (accepted != 1) {
                fail("取込の起動が受理されず result=$accepted（期待 1）。am broadcast 出力: $startOut")
            }
            // 取込完了を UI で待つ: 本棚の書影カードの content-desc に作品タイトルが現れるまで最大10分。
            // ⚠ タイトルの **text** で検知してはならない（2026-07-18 実機トレースで確定した罠）:
            // ProcessingBanner は meta 抽出直後（取込開始の数秒後）から変換中タイトルを text 表示するため、
            // text 出現待ちはバナーに即マッチして早発火し、measureBlock が抽出途中で終わる
            // ＝perfetto capture が Extract#engine の途中で切れ、TraceSectionMetric が完全スライス0件で
            // 全メトリクス 0.0 になる。
            // 旧信号「著者名の text」は 2026-08-06 に死んだ（本棚カードの意匠変更で著者テキストが
            // カードから消えた＝成功取込後の uiautomator ダンプで著者 0 件を実測）。現行の完了信号は
            // **content-desc**: 書影カードだけが desc=作品タイトルを持ち、バナーは text のみで desc を
            // 持たない（変換中ダンプで実測）＝DB 登録後のカード出現と 1:1 で早発火しない。
            // 照合は曖昧文字（〜 U+301C/FF5E）を含まない先頭句の部分一致で行う。
            if (!device.wait(Until.hasObject(By.descContains(BOOK_TITLE_HEAD)), 10 * 60 * 1000L)) {
                fail("取込が完了しなかった（本棚カードの content-desc に「$BOOK_TITLE_HEAD」が現れなかった）")
            }
            // 従確認: 冊数ヘッダが 1冊 になること（DB 駆動＝変換中は 0冊 のまま。setupBlock ③' で
            // 事前に 0冊 を検証済みのため、1冊 への遷移は「この取込による登録が 1 件だけ」の同定になる）。
            if (!device.wait(Until.hasObject(By.text("1冊")), 10_000)) {
                fail("カードは出たが冊数が 1冊 にならない（重複登録か白紙化不全を疑う）")
            }
        }
        // このリターン時点で書き出し済みの benchmarkData.json を読んで判定する（ImportBudget 参照）。
        if (ImportBudget.isBudgetAssertEnabled()) {
            ImportBudget.assertImportWithinBudget(startedAtEpochMs)
        }
    }

    /** `am broadcast` 出力の "result=N" を取り出す（ordered 配達完了時に setResultCode の値が載る）。 */
    private fun resultCodeOf(out: String): Int? =
        Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.ImportBenchReceiver"
        const val ACTION_IMPORT = "com.novelreader.benchmark.action.IMPORT_PDF"

        // N6169DZ の正本値（出典＝ab-review/golden_regression/N6169DZ.pdf.json の "title"）:
        //   title「シャングリラ・フロンティア〜クソゲーハンター、神ゲーに挑まんとす〜」（〜 は WAVE DASH U+301C）。
        // 完了検知の主信号はカード content-desc の先頭句部分一致（measureBlock の why 参照。旧主信号の
        // 著者名テキストは 2026-08-06 のカード意匠でカードから消えており使えない）。
        const val BOOK_TITLE_HEAD = "シャングリラ・フロンティア"
    }
}
