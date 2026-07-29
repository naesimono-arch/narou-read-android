package com.novelreader

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import com.novelreader.data.NewEpisodeMarkEntity
import com.novelreader.domain.chapterNumberOf
import com.novelreader.model.BookId
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NewEpisodeAlert
import com.novelreader.narou.WebBookCheckState
import com.novelreader.narou.computeNewEpisodeAlerts
import com.novelreader.narou.computeWebNewEpisodeAlerts
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.shouldCheckWebBookNow
import com.novelreader.narou.webNewEpisodeMarkKey
import com.novelreader.scrape.ScrapeException
import com.novelreader.scrape.SiteAdapterRegistry
import kotlinx.coroutines.flow.first

/**
 * U1: 新着話を1日1回チェックし、増分をローカル通知する Worker。対象は2系統:
 *  1) なろう紐付け蔵書（books.ncode 非 null）＝公式APIへ1バルク照会（従来）。
 *  2) Web 蔵書（books.sourceUrl 非 null＝汎用DL基盤取込）＝既読話数が取込済み章数へ追いついた本だけ
 *     目次を再フェッチして差分判定（2026-07-29 既読統合。ゲート・判定は NewEpisodeCheckLogic.kt が正本）。
 * スケジュールは [NovelReaderApplication.onCreate]（PeriodicWork 24h・ネットワーク制約・KEEP）。
 *
 * なぜ FGS でなく WorkManager か: PDF 変換（PdfProcessingService）は「ユーザーが今まさに待つ重処理」
 * だから前景サービスだが、新着チェックは少数リクエストの照会だけの軽処理で、実行時刻の正確さも
 * 要らない。OS のバッチング（Doze 対応）に乗る WorkManager が電池・行儀の両面で適する。
 */
class NewEpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovelReaderApplication
        val dao = AppDatabase.getDatabase(applicationContext).newEpisodeMarkDao()

        val books = app.repository.allBooks.first()
        // 正規化済み ncode → (bookId, 蔵書タイトル)。通知の文言・着地先はローカルの本に揃える。
        val linkedBooks = books.mapNotNull { book ->
            book.ncode?.let { Ncode(it).storageKey }?.takeIf { it.isNotEmpty() }
                ?.let { it to (book.id to book.title) }
        }.toMap()
        // Web 蔵書（汎用DL基盤取込）。sourceUrl/sourceSite は取込時に同時に入る（WebBookImporter）が、
        // 照会は sourceUrl 起点の registry 解決だけを使うため sourceUrl の有無で判定する。
        val webBooks = books.filter { it.sourceUrl != null }

        if (linkedBooks.isEmpty() && webBooks.isEmpty()) {
            // 対象ゼロなら基準値も全掃除する。空リストの NOT IN は SQL が不正になるため、
            // キーとして決して現れない空文字を番兵に渡す（＝実質全削除）。
            dao.pruneExcept(listOf(""))
            return Result.success()
        }

        val marks = dao.getAll().associate { it.ncode to it.lastNotifiedAllNo }
        val alerts = mutableListOf<NewEpisodeAlert>()
        val newMarks = mutableMapOf<String, Int>()

        // --- なろうパス（紐付け蔵書・公式APIへ1バルク照会） ---
        if (linkedBooks.isNotEmpty()) {
            try {
                val currents = app.novelApiRepository.novelDetailsBulk(linkedBooks.keys.map { Ncode(it) })
                val (narouAlerts, narouMarks) = computeNewEpisodeAlerts(linkedBooks, marks, currents)
                alerts += narouAlerts
                newMarks += narouMarks
            } catch (e: NarouApiException) {
                // オフライン等はなろうパスだけ静かにスキップし翌日に任せる。Result.retry() にしない理由:
                // 指数バックオフでも失敗が続く限り再試行が繰り返され、1日1回というレート自制の建付けが
                // 崩れるため（新着通知は1日遅れても実害がない）。Web パスは別ホストへの照会で
                // なろうAPIの失敗と独立のため道連れにしない（基準値は newMarks 非搭載＝自然に据え置き）。
                Log.w(TAG, "なろう新着チェックをスキップ: ${e.userMessage}")
            }
        }

        // --- Web 蔵書パス（既読話数の統合・判定は NewEpisodeCheckLogic.kt の純関数が正本） ---
        if (webBooks.isNotEmpty()) {
            val webStates = collectWebBookStates(webBooks, app)
            val siteTotals = fetchWebSiteTotals(webStates.filter { shouldCheckWebBookNow(it) })
            val (webAlerts, webMarks) = computeWebNewEpisodeAlerts(webStates, marks, siteTotals)
            alerts += webAlerts
            newMarks += webMarks
        }

        alerts.forEach { showNotification(it) }

        // 基準値は通知の成否（権限の有無）に関わらず前進させる。権限が無い間の増分を溜め込むと、
        // 後日許可された瞬間に古い更新まで雪崩のように通知される方が体験が悪いため。
        val now = System.currentTimeMillis()
        dao.upsertAll(newMarks.map { (key, allNo) -> NewEpisodeMarkEntity(key, allNo, now) })
        // 紐付け解除・削除済みの本の基準値を掃除（放置すると再紐付け時に古い基準で誤診する）。
        // keep は「今も在る紐付け ncode」＋「今も在る Web 蔵書のキー」の合併。今回照会しなかった Web 蔵書
        // （読み残し中・フェッチ失敗）の基準値も keep する＝照会を止めるだけで基準値を消すと、追いつき後の
        // 初回に通知済み分まで二重通知するため。
        dao.pruneExcept(linkedBooks.keys.toList() + webBooks.map { webNewEpisodeMarkKey(it.id) })
        return Result.success()
    }

    /** Web 蔵書の判定材料を Room/ファイルシステムから収集する（判定本体は NewEpisodeCheckLogic.kt の純関数側）。 */
    private suspend fun collectWebBookStates(
        webBooks: List<BookEntity>,
        app: NovelReaderApplication,
    ): List<WebBookCheckState> {
        // 章数は実ファイル枚数（chap_N.html）を数える。BookshelfViewModel.chapterCountMap と同じ規約だが、
        // あちらは UI の StateFlow に癒着していて Worker から共有できないため、数え上げ数行を意図的に重複させる
        // （規約の正＝chap_N.html の枚数。ShelfItems の「意図的な式の重複」と同じ扱い）。
        val chapPattern = Regex("chap_\\d+\\.html")
        return webBooks.mapNotNull { book ->
            val sourceUrl = book.sourceUrl ?: return@mapNotNull null
            val dir = book.resolvedHtmlDir(applicationContext.filesDir)
            val deviceChapterCount = dir.listFiles { f -> f.name.matches(chapPattern) }?.size ?: 0
            // 既読話数は蔵書共通の progress テーブルから（Web 蔵書はネイティブ読書面で読む＝
            // web_reading_progress〔なろう WebView 用・ncode キー〕には載らない。実測＝Logic 側 KDoc）。
            val lastRead = chapterNumberOf(app.repository.getProgress(BookId(book.id))?.lastReadFilename) ?: 0
            WebBookCheckState(
                bookId = book.id,
                bookTitle = book.title,
                sourceUrl = sourceUrl,
                deviceChapterCount = deviceChapterCount,
                lastReadChapterNumber = lastRead,
            )
        }
    }

    /**
     * 照会対象の Web 蔵書のサイト総話数（目次の章数）を取得する（bookId → 総話数。失敗した本は載せない）。
     * 規約ゲート: 必ず [SiteAdapterRegistry.resolve] を通す（ADR 0024 の登録ゲートが単一正本）。取込後に
     * Blocked/pending へ移ったサイトの蔵書は Supported にならず、ここで自然に照会対象から外れる。
     * Crawl-delay/per-host スロットルは ScrapeHttpClient が内蔵＝直列ループで足り、追加の sleep はしない。
     */
    private suspend fun fetchWebSiteTotals(eligible: List<WebBookCheckState>): Map<String, Int> {
        if (eligible.isEmpty()) return emptyMap()
        // registry は走行ごとに生成（BookshelfViewModel 等の既存流儀）。1回の doWork 内で1インスタンスを
        // 共有するため、per-host スロットルは同一走行内の全フェッチへ確実に効く。
        val registry = SiteAdapterRegistry()
        val totals = mutableMapOf<String, Int>()
        for (state in eligible) {
            val supported = registry.resolve(state.sourceUrl) as? SiteAdapterRegistry.Resolution.Supported
                ?: continue
            try {
                totals[state.bookId] = supported.adapter.fetchToc(supported.workUrl).chapters.size
            } catch (e: ScrapeException) {
                // 取得系の失敗は ScrapeHttpClient/各アダプタが ScrapeException へ正規化済み（唯一の失敗契約）。
                // 一過性失敗・構造破損はこの本だけスキップして翌日に任せる（構造破損の恒常検知は fixture
                // ゴールデンの領分＝ここで失敗化しない）。基準値は totals 非搭載により据え置き＝真因を
                // 握り潰さず「増分不明の日は判定しない」へ倒す防御。CancellationException は正規化対象外で
                // ここを素通りし Worker のキャンセルへ伝播する。
                Log.w(TAG, "Web新着チェックをスキップ(${state.bookTitle}): ${e.message}")
            }
        }
        return totals
    }

    private fun showNotification(alert: NewEpisodeAlert) {
        // Android 13+ は POST_NOTIFICATIONS が無いと notify が SecurityException になり得るため先に弾く
        // （権限フローは本棚の取込導線に既存＝ここでは静かに諦めるだけでよい）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        // タップで該当の本の読書画面へ（なろう紐付け本は最終章に継続カード＝なろうへの導線がある。
        // Web 蔵書は続き取得の導線が未整備＝本を開くまで。再取込/差分更新の導線は別レーンの宿題）。
        // ContentIntent はOPPO等のOEMで必須（無いと通知自体が表示されない＝task_diary #2）。
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_BOOK_ID, alert.bookId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // requestCode を ncode ハッシュで分ける: 複数の本の通知が同じ PendingIntent に
        // 上書き合流して全通知が同じ本へ飛ぶ取り違えを防ぐ（FLAG_UPDATE_CURRENT の既知の罠）。
        val pending = PendingIntent.getActivity(
            applicationContext, alert.ncode.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, NovelReaderApplication.NEW_EPISODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.bookTitle)
            .setContentText("続きが ${alert.newCount} 話更新されています（全${alert.totalAllNo}話）")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // tag=ncode × 固定 id: 同じ本の通知は上書き・別の本は並ぶ。既存の PDF変換系 ID（1001/1002）
        // と数値衝突しない管理にするため id でなく tag で識別する。
        NotificationManagerCompat.from(applicationContext)
            .notify(notificationTag(alert.ncode), NEW_EPISODE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "NewEpisodeCheckWorker"
        const val UNIQUE_WORK_NAME = "new_episode_check"
        const val NEW_EPISODE_NOTIFICATION_ID = 2001

        /** 新着話通知の tag を組む単一の正本。通知の発行（showNotification）と取り下げ
         *  （NovelReaderApplication.cancelNewEpisodeNotification）で必ず同じ文字列にするため関数化する。
         *  ncode は linkedBooks のキー（正規化済み）だが、取り下げ側は book.ncode を直接渡しうるため
         *  ここでも Ncode.storageKey（trim+大文字）を掛けてズレを吸収する。
         *  Web 蔵書のキー（"web:<bookId>"）にも同じ変換が掛かる（"WEB:…" になる）が、発行・取り下げの
         *  両側が本関数を通る限り決定論で一致するため実害はない（片側だけ生文字列で notify しないこと）。 */
        fun notificationTag(ncode: String): String = "new_episode_${Ncode(ncode).storageKey}"
    }
}
