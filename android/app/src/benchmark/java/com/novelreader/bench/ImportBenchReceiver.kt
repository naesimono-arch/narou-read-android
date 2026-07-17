package com.novelreader.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.core.content.ContextCompat
import com.novelreader.PdfProcessingService
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import java.io.File
import kotlin.concurrent.thread

/**
 * Macrobenchmark の「大PDF取込」計測用 BroadcastReceiver。計測対象アプリ（benchmark ビルド＝
 * com.novelreader.benchmark）に対して、本番の取込経路（[PdfProcessingService] ACTION_START）を発火させる
 * `mode=start` と、反復可能にするための白紙化 `mode=clear` を提供する。
 *
 * なぜ benchmark ソースセット限定か（出荷物に含まれない根拠）:
 * このファイルは src/benchmark 配下にのみ存在するため debug/release ビルドには一切コンパイルされない。
 * つまりユーザーへ配布される APK にこの Receiver は存在せず、取込トリガの攻撃面を出荷物に残さない
 * （[LibrarySeedReceiver] と同じ論証）。
 *
 * なぜ exported=true でも安全か（Manifest overlay 側にも明記）:
 * benchmark ビルドは applicationIdSuffix ".benchmark" で実蔵書(com.novelreader)と別パッケージに隔離され、
 * かつ debug/release には Receiver 自体が存在しない。DB も計測用の使い捨てで、実蔵書を触らない。
 *
 * ⚠ FGS を background 文脈から起動しない前提: `mode=start` は内部で startForegroundService を呼ぶため、
 * Android 12+ のバックグラウンド FGS 起動制限に触れないよう、ベンチ側は必ず**アプリを前面にしてから**この
 * broadcast を送る（[com.novelreader.macrobenchmark.PdfImportBenchmark] の measureBlock がその順序を守る）。
 * 前面かつ非凍結のプロセスへは shell broadcast が確実に配達される（背面/凍結の沈黙不達は
 * docs/knowledge/coloros-broadcast-silent-drop.md）。
 */
class ImportBenchReceiver : BroadcastReceiver() {

    companion object {
        /** 取込トリガの action。ベンチ側は明示コンポーネント＋この action で ordered broadcast する。 */
        const val ACTION_IMPORT = "com.novelreader.benchmark.action.IMPORT_PDF"

        /** benchmark variant の assets に同梱した実PDF（build.gradle の copyBenchmarkPdfAsset が供給）。 */
        private const val ASSET_PDF = "sample_pdfs/N6169DZ.pdf"

        /** cacheDir 上のコピー先ファイル名（毎回上書き＝反復間で状態を持ち越さない）。 */
        private const val CACHE_PDF = "N6169DZ.pdf"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Receiver のライフサイクルより DB/FGS 起動が長生きするため applicationContext を掴む。
        val appContext = context.applicationContext
        val mode = intent.getStringExtra("mode") ?: "start"

        // goAsync で処理完了まで Receiver を生かす（ordered broadcast の resultCode/Data を確実に返すため）。
        // DB I/O・ファイルコピーは別スレッドで回してメインを塞がない（Receiver の ANR 上限 ~10s 内に収まる）。
        val pending = goAsync()
        thread {
            try {
                when (mode) {
                    "clear" -> handleClear(appContext, pending)
                    else -> handleStart(appContext, pending)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * `mode=start`: assets の実PDF を cacheDir へ上書きコピーし、本番経路（[PdfProcessingService] ACTION_START）を
     * startForegroundService で起動する。取込完了は待たない（Receiver の ANR 上限 10s のため。完了検知はベンチ側が
     * UI で行う）。resultCode=1（起動受理）・resultData に cache パス。失敗時は resultCode=0＋理由を resultData に残す。
     */
    private fun handleStart(appContext: Context, pending: PendingResult) {
        try {
            val cacheFile = File(appContext.cacheDir, CACHE_PDF)
            appContext.assets.open(ASSET_PDF).use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }

            // なぜ VmPolicy を LAX に落とすか（benchmark 限定コードの割り切り）:
            // 本番経路の Intent 契約は `data = 取込元 Uri` で、Service は contentResolver.openInputStream で読む。
            // ここでは assets 由来の file:// を渡すが、targetSdk 34 の既定 VmPolicy は file:// Uri を Intent で
            // プロセス外（AMS）へ渡すと FileUriExposedException で死ぬ（DETECT_VM_FILE_URI_EXPOSURE）。本番の
            // content:// なら起きないが、ベンチでは FileProvider を足さず本番の startForegroundService 契約を崩さない
            // ために、この計測専用プロセスに限り exposure 検出を無効化する（出荷物に存在しない bench コードの割り切り）。
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)

            val serviceIntent = Intent(appContext, PdfProcessingService::class.java).apply {
                action = PdfProcessingService.ACTION_START
                data = Uri.fromFile(cacheFile)
            }
            // 前面アプリ発なので Android 12+ の FGS 起動制限には触れない（KDoc の前提）。
            ContextCompat.startForegroundService(appContext, serviceIntent)

            pending.setResultCode(1)
            pending.setResultData(cacheFile.absolutePath)
        } catch (t: Throwable) {
            // 失敗を黙って PASS に化けさせない（resultCode!=1 でベンチ側が fail する）。
            pending.setResultCode(0)
            pending.setResultData("import start failed: ${t.message}")
        }
    }

    /**
     * `mode=clear`: ベンチアプリの DB は使い捨てなので白紙化して反復可能にする。books/progress 全行削除＋
     * `filesDir/novels/` 再帰削除で、SHA 重複遮断・べき等ガード（同一内容の再取込を弾く仕組み）を外して同じ PDF を
     * 何度でも取り込めるようにする。pending_jobs も消す（残ると次回起動の startup-recovery が同じ本を再取込し、
     * 計測前の setupBlock 中に取込が走って計測を汚すため＝白紙化の一部）。resultCode=削除後の books 総数（0 期待）。
     *
     * なぜ execSQL 直叩きか（bench 限定の割り切り）: BookDao/ProgressDao には全行削除の口が無い（本番に不要なため）。
     * 計測専用コードなので AppDatabase の SupportSQLiteDatabase へ直接 DELETE を発行する（本番 API を汚さない）。
     */
    private fun handleClear(appContext: Context, pending: PendingResult) {
        try {
            val db = AppDatabase.getDatabase(appContext)
            val writable = db.openHelper.writableDatabase
            writable.beginTransaction()
            try {
                writable.execSQL("DELETE FROM books")
                writable.execSQL("DELETE FROM progress")
                writable.execSQL("DELETE FROM pending_jobs")
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }

            // 変換済み HTML（filesDir/novels/<bookId>/）も一掃する。実 HTML を書き出す取込経路を反復するため、
            // 残骸が孤立 HTML 掃除やディスク圧迫に化けないよう毎回さらにする。
            File(appContext.filesDir, BookEntity.NOVELS_SUBDIR).deleteRecursively()

            // 削除後の books 総数を数え resultCode に載せる（0 期待。非0 ならベンチ側が fail する）。
            val remaining = writable.query("SELECT COUNT(*) FROM books").use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
            pending.setResultCode(remaining)
            pending.setResultData("cleared books/progress/pending_jobs + novels/ (remaining books=$remaining)")
        } catch (t: Throwable) {
            // resultCode を 0 期待から外して失敗を可視化する（clear は 0 期待なので -1 で不一致 fail させる）。
            pending.setResultCode(-1)
            pending.setResultData("clear failed: ${t.message}")
        }
    }
}
