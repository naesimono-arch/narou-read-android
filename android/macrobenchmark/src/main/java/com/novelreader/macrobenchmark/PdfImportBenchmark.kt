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
 * 予算 assert は本ベンチでは未実装（初回の実測でフェーズ分布を掴んでから較正して追加する方針＝[ScrollBudget]/
 * [StartupBudget] と同じ流儀。較正前の恣意的な閾値で「黙って緑」を作らない）。
 *
 * ColorOS の broadcast 沈黙不達（背景/凍結プロセスへの配達が result=0 の正常完了に化ける）と、その回避
 * （clear は force-stop で dead＝非凍結にしてから shell broadcast、start は前面＝生存・非凍結へ送る）の機序は
 * docs/knowledge/coloros-broadcast-silent-drop.md／[BookshelfScrollBenchmark] の KDoc と同一根拠。shell 実行の
 * ハングリスクは run_macrobenchmark.sh の SIGQUIT 除細動ループが前提＝このベンチは必ず同スクリプト経由で実行する。
 */
@RunWith(AndroidJUnit4::class)
class PdfImportBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun importLargePdf() {
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
                // ① dead 化＝HANS 凍結なしを決定論化（clear broadcast を確実配達させる前提）。
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                // ② 白紙化: books/progress/pending_jobs 全消し＋novels/ さらい。resultCode=削除後 books 総数=0 を検証。
                //    --include-stopped-packages: force-stop 直後の stopped state での配達除外を塞ぐ保険。
                val clearOut = device.executeShellCommand(
                    "am broadcast --include-stopped-packages" +
                        " -n $TARGET_PACKAGE/$RECEIVER_CLASS -a $ACTION_IMPORT --es mode clear"
                )
                val cleared = resultCodeOf(clearOut)
                if (cleared != 0) {
                    fail("白紙化 result=$cleared（期待 0＝削除後 books 総数）。am broadcast 出力: $clearOut")
                }
                // ③ コールド性を自前確保してから前面起動（本棚が start 目的地）。
                killProcess()
                pressHome()
                startActivityAndWait()
                // 起動の実効を検証（launcher も scrollable を持つため By.pkg で前面化を必ず確認＝[BookshelfScrollBenchmark] 参照）。
                if (!device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 10_000)) {
                    fail("対象アプリが前面に来なかった（ホーム画面のまま計測しない）")
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
            // 取込完了を UI で待つ: 本棚の書影カードに N6169DZ の**著者名**が現れるまで最大10分。
            // ⚠ 作品タイトルで検知してはならない（2026-07-18 実機トレースで確定した罠）:
            // ProcessingBanner は meta 抽出直後（取込開始の数秒後）から**変換中タイトルを表示**するため、
            // タイトル文字列の出現待ちはバナーに即マッチして早発火し、measureBlock が抽出途中で終わる
            // ＝perfetto capture が Extract#engine の途中で切れ、TraceSectionMetric が完全スライス0件で
            // 全メトリクス 0.0 になる（トレース本体に B マーカーだけ残っていたことをバイナリ検分で実証。
            // 初回走行で2反復だけ値が出たのは、案内ダイアログが偶然バナーを a11y から隠して検知を
            // 完了まで遅延させていたため＝クリーン反復ほど 0 になる逆説）。
            // 著者名はバナーに出ず（バナー＝タイトル＋フェーズのみ）、DB 登録後の書影カードにのみ出る
            // ＝「取込完了」と1:1 に対応する検知信号。出なければ fail（次反復へ黙って進まない）。
            if (!device.wait(Until.hasObject(By.text(BOOK_AUTHOR)), 10 * 60 * 1000L)) {
                fail("取込が完了しなかった（本棚カードに著者「$BOOK_AUTHOR」が現れなかった）")
            }
            // 念のため取り込まれた本がタイトルでも同定できることを確認（著者一致だけの偶然マッチ防止）。
            if (!device.wait(Until.hasObject(By.textContains(BOOK_TITLE_HEAD)), 10_000)) {
                fail("著者は出たが作品タイトル「$BOOK_TITLE_HEAD」を同定できない（想定外の状態）")
            }
        }
    }

    /** `am broadcast` 出力の "result=N" を取り出す（ordered 配達完了時に setResultCode の値が載る）。 */
    private fun resultCodeOf(out: String): Int? =
        Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        val TARGET_PACKAGE = BenchmarkTargets.TARGET_PACKAGE
        const val RECEIVER_CLASS = "com.novelreader.bench.ImportBenchReceiver"
        const val ACTION_IMPORT = "com.novelreader.benchmark.action.IMPORT_PDF"

        // N6169DZ の正本値（出典＝ab-review/golden_regression/N6169DZ.pdf.json の "title"/"author"）:
        //   title「シャングリラ・フロンティア〜クソゲーハンター、神ゲーに挑まんとす〜」（〜 は WAVE DASH U+301C）。
        // 完了検知の主信号は著者（バナーに出ない＝早発火しない。measureBlock の why 参照）。
        // タイトルは従信号＝曖昧文字（〜）を含まない先頭句の部分一致で同定のみ行う。
        const val BOOK_AUTHOR = "硬梨菜"
        const val BOOK_TITLE_HEAD = "シャングリラ・フロンティア"
    }
}
