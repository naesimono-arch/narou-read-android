package com.novelreader.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.fail

/**
 * LibrarySeedReceiver への seed 配達の共通形（2026-08-06 の修理形＝[PdfImportBenchmark] setupBlock ①〜②と同形）。
 * [BookshelfScrollBenchmark]・[ChapterFlipBenchmark]・[TabSwipeBenchmark] が使う。
 *
 * なぜ「前面生存プロセスへの配達」か: ColorOS は背景発／HANS 凍結中に加え、**dead プロセスへの shell
 * broadcast（--include-stopped-packages 付きでも）を端末状態依存で沈黙不達**にする（2026-08-06 実機 3/3 実証
 * ＝第3の遮断様態。旧処方「force-stop で dead 化してから配達」は恒常則ではなかった）。前面＝生存・非凍結の
 * プロセスへの配達は同日 3/3 で安定（機序の一次情報＝docs/knowledge/coloros-broadcast-silent-drop.md）。
 *
 * なぜ seed の前に ImportBenchReceiver mode=clear の全消しを置くか:
 *  - seed の resultCode（bench_seed 件数）は bench_seed_* 行しか数えず、pdf-import ベンチが最終反復後に残す
 *    取込1冊などの残骸を検知できない。残骸が居ると (a) 副作用の UI 検証「N冊」が N+残骸 冊に化けて偽 FAIL、
 *    (b) 残骸の実書影が計測面に混ざり描画コストの再現性が崩れる、(c) addedAt が新しい残骸が本棚先頭を奪い
 *    「章送り計測の書＝addedAt 降順の先頭」のシーダー契約が崩れる。全消し→seed で総数と面を決定論化する。
 *  - clear の期待 resultCode=0 は ordered broadcast の**初期値 0 と衝突**し、resultCode だけでは不達を
 *    検知できない。resultData の実在（handleClear が必ず載せる "cleared books…"）まで検証する（修理形の核）。
 *
 * 旧処方の `--include-stopped-packages` は付けない: あの保険は「force-stop 直後の stopped 状態の dead 宛」
 * 前提だった。本形は am start 済み＝stopped が解除された生存プロセス宛なので該当しない（[PdfImportBenchmark]
 * の clear/start も同様に付けていない）。
 *
 * shell 実行のハングリスクは run_macrobenchmark.sh の SIGQUIT 除細動ループが前提＝このヘルパを使うベンチは
 * 必ず同スクリプト経由で実行する（docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md）。
 */
internal fun MacrobenchmarkScope.clearAndSeedLibrary(
    count: Int,
    gridMode: Boolean,
    chapterCount: Int = 0,
) {
    // ① dead 化＝HANS 凍結なしを決定論化（凍結中プロセスへは shell 発 broadcast も配達スキップされるため、
    //    いったん非凍結へ倒してから前面起動する。[PdfImportBenchmark] ① と同形）。
    device.executeShellCommand("am force-stop ${BenchmarkTargets.TARGET_PACKAGE}")
    // ①' 前面起動: dead 宛の配達は端末状態依存で沈黙不達するため、生きた前面プロセスにしてから配達する。
    pressHome()
    startActivityAndWait()
    if (!device.wait(Until.hasObject(By.pkg(BenchmarkTargets.TARGET_PACKAGE)), 10_000)) {
        fail("seed 配達用の前面起動に失敗（アプリが前面に来なかった）")
    }
    // ② 全消し（books/progress/pending_jobs 全行＋novels/ さらい）。resultCode=0（削除後 books 総数）に
    //    加えて resultData の実在まで検証する（期待 0 は ordered の初期値と同値＝不達が成功に化けるため）。
    val clearOut = device.executeShellCommand(
        "am broadcast -n ${BenchmarkTargets.TARGET_PACKAGE}/$IMPORT_RECEIVER_CLASS" +
            " -a $ACTION_IMPORT --es mode clear"
    )
    val cleared = resultCodeOf(clearOut)
    if (cleared != 0 || !clearOut.contains("cleared books")) {
        fail("白紙化が配達されなかったか失敗（result=$cleared・期待 0＋resultData に配達証跡）。am broadcast 出力: $clearOut")
    }
    // ③ seed 配達（clear と同じ前面生存プロセスへ）。期待 resultCode=count は初期値 0 と衝突しないため
    //    count 不一致でも不達は落とせるが、配達証跡（LibrarySeedReceiver が必ず載せる "seeded count…"）の
    //    実在まで見るのが修理形。「配達はされたが seed 自体が失敗」の場合は resultCode=0＋
    //    resultData "seed failed: 理由" になり、fail メッセージ中の am broadcast 出力で不達と区別できる。
    //    chapterCount=0 は受信側の既定値と同じ「章なし」＝明示しても従来挙動と完全に同一（受信側契約）。
    val seedOut = device.executeShellCommand(
        "am broadcast -n ${BenchmarkTargets.TARGET_PACKAGE}/$SEED_RECEIVER_CLASS -a $ACTION_SEED" +
            " --ei count $count --ei chapterCount $chapterCount --ez gridMode $gridMode"
    )
    val seeded = resultCodeOf(seedOut)
    if (seeded != count || !seedOut.contains("seeded count")) {
        fail("シードが配達されなかったか失敗（result=$seeded・期待 $count＋resultData に配達証跡）。am broadcast 出力: $seedOut")
    }
}

/**
 * seed 副作用の UI 検証（[PdfImportBenchmark] setupBlock ③' の 0冊 検証と同形）: 本棚の冊数ヘッダが
 * 「${count}冊」になるのを待つ。厳密値で張れるのは [clearAndSeedLibrary] が全消し前置きで総数を決定論化
 * しているため（ヘッダは蔵書＋Web由来の総数だが、ベンチ環境の Web 由来は常に 0＝pdf-import の 0冊/1冊
 * 検証と同じ前提）。ヘッダは明快K のグリッド/リスト両面で無条件に描画される（BookshelfK の KHeader）。
 *
 * なぜ配達検証（resultCode/resultData）だけで足りず UI まで見るか: 留めたいのは「計測される面が
 * シード済み蔵書を表示していること」。scrollable 待ちは状態フィルタチップ行（水平 scrollable）でも真に
 * なるため、棚が空のままでも素通りして空振り計測になりうる——DB 実体と画面の対応はここで検証する。
 *
 * 呼び出しは killProcess→再起動の**後**に行うこと: ImportBenchReceiver の clear は raw execSQL で Room の
 * invalidation を蹴らず、生きたプロセスの本棚表示は陳腐化したままになる（[PdfImportBenchmark] ③' (a) と
 * 同根＝再起動後の表示だけが DB の実体を映す）。
 */
internal fun MacrobenchmarkScope.verifySeededShelfCount(count: Int) {
    if (!device.wait(Until.hasObject(By.text("${count}冊")), 10_000)) {
        fail("シード後の本棚が ${count}冊 表示にならない（seed 未反映か冊数ヘッダの意匠変更を疑う）")
    }
}

/** `am broadcast` 出力の "result=N" を取り出す（ordered 配達完了時に setResultCode の値が載る）。 */
private fun resultCodeOf(out: String): Int? =
    Regex("""result=(-?\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()

// Receiver の契約値（値の正本は app/src/benchmark 側の各 Receiver）。[PdfImportBenchmark] は修理形の手本
// として自前の同値定数を持っており重複するが、手本の自己完結を崩さないためここからは参照しない。
private const val IMPORT_RECEIVER_CLASS = "com.novelreader.bench.ImportBenchReceiver"
private const val ACTION_IMPORT = "com.novelreader.benchmark.action.IMPORT_PDF"
private const val SEED_RECEIVER_CLASS = "com.novelreader.bench.LibrarySeedReceiver"
private const val ACTION_SEED = "com.novelreader.benchmark.action.SEED_LIBRARY"
