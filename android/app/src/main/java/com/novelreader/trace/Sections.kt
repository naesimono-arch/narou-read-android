package com.novelreader.trace

/**
 * `androidx.tracing.trace` の薄いラッパー。PDF 取込パイプラインの各フェーズを Perfetto トレース区間として
 * 区切り、Macrobenchmark の `TraceSectionMetric`（区間名で slice を拾う）で個別のフェーズ時間を計測できる
 * ようにする。挿入点（[com.novelreader.pdf.PdfBookExtractor]／[com.novelreader.repository.DefaultBookRepository]）は
 * `Sections.trace("…") { 既存式 }` の1行で包むだけにでき、diff を最小に保つ。
 *
 * なぜ挿入点で `androidx.tracing.trace` を直接呼ばずラッパーを噛ませるか（JVM ゴールデン回帰の死守・最重要）:
 * PdfBookExtractor / DefaultBookRepository は `testDebugUnitTest`（JVM 実行）のゴールデン回帰が踏むコード。
 * `androidx.tracing.trace` は内部で `android.os.Trace`（ネイティブ）に触れるため、JVM では未モックの
 * RuntimeException（"not mocked"）を起こしてテストを巻き込み得る。現構成は
 * `testOptions.unitTests.returnDefaultValues=true` で既定値が返り例外にはならないが、その設定に依存せず
 * 安全側へ倒すため、クラスロード時に `android.os.Trace` の可用性を1回だけ判定し、不可なら完全素通し（no-op）にする。
 *
 * ⚠ 可用性は `isEnabled()` の「戻り値」ではなく「例外を投げずに呼べたか」で判定する（ここを誤ると計測ゼロ）:
 * `android.os.Trace.isEnabled()` は「トレース捕捉が今まさに有効なとき」だけ true を返す。もし戻り値でゲート
 * すると、クラスロード時点で捕捉が走っていない実機では常に no-op となり、Macrobenchmark が区間を1つも拾えない。
 * ここでは戻り値を捨て「例外なく到達できた＝実機ランタイム上で `android.os.Trace` が使える」を可用性の意味に採る。
 * 実機では常に区間を発行し（begin/end 自体は捕捉が無ければネイティブ側で軽い no-op）、捕捉中の走行だけが記録される。
 */
object Sections {

    // true＝実機（android.os.Trace が例外なく呼べる）。false＝JVM 単体テストで未モック例外に落ちた場合。
    // なぜ戻り値を捨てるか: 上の ⚠ の通り isEnabled() の真偽は「現在トレース捕捉中か」でしかなく、
    // 可用性判定には「例外を投げずに到達したか」だけを使う（catch に落ちなければ実機とみなす）。
    // @PublishedApi: 下の inline 関数から参照するため internal 可視性を維持したまま公開する。
    @PublishedApi
    internal val available: Boolean = try {
        android.os.Trace.isEnabled()
        true
    } catch (t: Throwable) {
        false
    }

    /**
     * [name] のトレース区間で [block] を包んで実行し、その戻り値を返す。
     * 実機では begin/end を発行し（捕捉中のみ記録）、JVM（トレース不可）では block をそのまま実行して素通しする。
     * 例外伝播・return 経路・値は一切変えない（try/finally で endSection するため、block が投げても区間は必ず閉じる）。
     *
     * なぜ `androidx.tracing.trace` へ委譲せず beginSection/endSection を直接呼ぶか:
     * ktx の trace は block が **crossinline** 宣言のため、非 crossinline な本関数の block を渡せない
     * （コンパイルエラーを実測）。かといって本関数の block を crossinline にすると、今度は suspend 呼び出し
     * （挿入点の一部＝Import#insertDb は suspend な DAO を包む）が inline 透過を失って通らなくなる。
     * static な beginSection/endSection の直接呼びなら block は完全 inline のまま＝suspend 呼び出しも
     * 非局所 return も素直に通り、挿入点の挙動を一切変えない。
     */
    inline fun <T> trace(name: String, block: () -> T): T {
        if (!available) return block()
        androidx.tracing.Trace.beginSection(name)
        try {
            return block()
        } finally {
            androidx.tracing.Trace.endSection()
        }
    }
}
