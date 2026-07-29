package com.novelreader.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.novelreader.data.PendingJobDao
import com.novelreader.data.PendingJobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 責務: pending_jobs（処理キューの永続記帳）の直列化された読み書きと、取込 URI 永続権限の返却。
 *
 * [DefaultBookRepository] の責務分割（2026-07-27 構造リファクタ）で委譲抽出した協力クラス。
 * enqueue 時に記帳し、変換の成否確定時に削除する。残っている行 = 強制終了
 * （OEM kill/OOM/onTimeout）で中断された未完了ジョブとして起動時リカバリが検出する。
 */
internal class PendingJobStore(
    private val context: Context,
    private val pendingJobDao: PendingJobDao,
) {

    // pending_jobs（永続キュー）への全書き込みを直列化する排他ロック（タスク1の恒久策）。
    // なぜ Mutex か / なぜ limitedParallelism(1) では不成立だったか:
    //   旧設計は enqueue の記帳(insert)と明示停止の全消し(deleteAll)を Dispatchers.IO.limitedParallelism(1) の
    //   単一スロットへ launch して投入順を守らせる狙いだった。しかし各メソッド冒頭の withContext(Dispatchers.IO) が
    //   スロットを手放して汎用IOプールへ再ディスパッチするうえ、Room の suspend DAO も内部で自前 executor へ
    //   再ディスパッチするため、単一スロットは実際の DB 着地を1件も直列化できていなかった（coroutine 意味論として不成立）。
    //   帰結として「PDF投入直後に停止」で insert が deleteAll の後に着地し、破棄済みジョブが pending_jobs に復活
    //   → 次回起動の runStartupRecoveryOnce が勝手に再変換する窓が残った。
    //   Mutex はロックを「suspend な DAO 呼び出しの完了まで保持」するため、どのディスパッチャで走ろうと相手の
    //   pending_jobs 書き込みが割り込めない＝実際の DB 書き込みを相互排他できる（limitedParallelism に無い保証）。
    //   投入順は main スレッド(onStartCommand)からの launch 順＋Mutex の FIFO 公平性で保たれる。
    private val pendingJobMutex = Mutex()

    /** enqueue の記帳。REPLACE のため再開時の再投入でも二重行にならない。 */
    // pendingJobMutex で全消し(clearPendingJobs)と直列化する。素の IO 並行だと「追加直後に停止」で
    // この insert が全消しの後に着地し、破棄済みジョブが復活する（フィールド pendingJobMutex の why 参照）。
    suspend fun add(uri: String, displayName: String) = withContext(Dispatchers.IO) {
        pendingJobMutex.withLock {
            pendingJobDao.insert(PendingJobEntity(uri, displayName, System.currentTimeMillis()))
        }
    }

    /** 未完了ジョブ一覧（enqueue 順）。起動時リカバリの検出用。 */
    suspend fun getAll(): List<PendingJobEntity> =
        withContext(Dispatchers.IO) { pendingJobDao.getAll() }

    /** 再開不能と判明したジョブの除去（権限喪失時など）。永続権限も返す。 */
    // pending_jobs 書き込みは pendingJobMutex で一律直列化する（settlePendingJob 自体はロックを持たないため
    // 呼び出し側で取る＝Mutex は非再入なので二重取得によるデッドロックを避ける設計）。
    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        pendingJobMutex.withLock { settlePendingJob(Uri.parse(uri)) }
    }

    /** 全ジョブの除去（ユーザーの明示停止＝「再開してほしくない」意思の反映）。 */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // pendingJobMutex で enqueue の記帳(addPendingJob)と直列化する。これが無いと「追加直後に停止」で
        // insert が deleteAll をすり抜けて後着し、破棄済みジョブが復活する（フィールド pendingJobMutex の why 参照）。
        pendingJobMutex.withLock {
            // deleteAll の前に各行の永続権限を返す（行を先に消すと解放対象の URI が分からなくなる）
            pendingJobDao.getAll().forEach { releasePersistedPermission(context, Uri.parse(it.uri)) }
            pendingJobDao.deleteAll()
        }
    }

    /** 変換の成否確定（成功/重複）時の確定処理＝記帳削除＋永続権限の返却（ロックはここで取る）。
     *  なぜ withContext(Dispatchers.IO) を付けないか: 呼び出し側（PdfBookImporter.addBook）は既に
     *  IO/NonCancellable 上で走っており、余計な再ディスパッチを挟まない＝分割前の呼び出し形と同一に保つ。 */
    suspend fun settleJob(pdfUri: Uri) {
        pendingJobMutex.withLock { settlePendingJob(pdfUri) }
    }

    /** 取込失敗時の記帳削除（永続権限は意図的に残す）。なぜ settle でないか＝M7 再試行の成立
     *  （PdfBookImporter.addBook の失敗経路コメント参照）。withContext を付けない理由は [settleJob] と同じ。 */
    suspend fun deleteRowKeepingPermission(uriString: String) {
        pendingJobMutex.withLock { pendingJobDao.deleteByUri(uriString) }
    }

    /** pending_jobs の記帳を消し、再開用に取得した永続 URI 権限も返す。
     *  変換の成否が確定した時点（成功=Room 登録済み／失敗=エラー通知確定）で呼ぶ。 */
    private suspend fun settlePendingJob(pdfUri: Uri) {
        pendingJobDao.deleteByUri(pdfUri.toString())
        releasePersistedPermission(context, pdfUri)
    }

    /**
     * 起動時クリーンアップ: keepUris に紐付かない「孤児」の永続 URI 権限を解放する（恒久リーク回収）。
     *
     * なぜこれが必要か（root cause）: 取込失敗時は M7 の「再試行」を成立させるため、addBook の失敗経路が
     * settlePendingJob（権限解放込み）ではなく pending_jobs 行の削除のみを行い、永続 URI 権限を意図的に
     * 残す。しかし再試行 Snackbar はプロセス生存中にしか出せないため、再試行されないまま終わった失敗分の
     * 権限は「どの経路でも解放されない」恒久リークになり、端末上限(128件)へ向けて溜まり続ける。そこで
     * 次回アプリ起動時に「keepUris 非紐付けの永続権限＝もう誰も要さない置き土産」として回収する。
     *
     * ⚠ keepUris の構成（呼び出し側 NovelReaderApplication が union して渡す）:
     *   ① 現在の pending_jobs が保持する URI（＝処理中・再開対象の取込。取込1経路の永続権限）
     *   ② books.sourceUri（＝取込元PDFを後で削除できるよう「本の生存中ずっと」保持する取込元 URI）
     * かつては「永続権限を取るのは取込1経路のみ・books は取込元 URI を持たない」ため ① だけで孤児判定が
     * 成り立っていた。取込元PDF削除機能で books が sourceUri を持つようになり、この本たちの権限は変換完了後も
     * 保持し続ける必要がある（本削除時に deleteDocument で使う）。よって ② を keepUris へ足さないと、
     * 起動のたびに現役蔵書の取込元権限を誤解放し、その後の取込元PDF削除が権限失効で失敗する。
     *
     * 【誤解放しない根拠】変換中の URI を誤って解放しないこと: 本メソッドは runStartupRecoveryOnce の
     * processingState==null ガード下（Service 非稼働）でのみ呼ばれ、かつ起動直後で新規取込より前に走る。
     * 稼働中に処理される URI は enqueue 時に必ず pending_jobs 行を持つため keepUris に含まれ、解放対象外。
     * 再開対象（resumable）の URI も pending_jobs 行を持つので keepUris で保護される。
     *
     * @param keepUris 保持すべき URI 集合（現在の pending_jobs URI ∪ books.sourceUri。呼び出し側で union）。
     */
    suspend fun releaseOrphanedPermissions(keepUris: Set<String>) = withContext(Dispatchers.IO) {
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            // SAF ツリー権限（ACTION_OPEN_DOCUMENT_TREE で得た「PDFのある場所」＝案X）は孤児掃除の対象外。
            // なぜ除外が要るか（真因）: 孤児判定は「persisted − keepUris」の差集合で、keepUris の構成は
            // pending_jobs の URI と books.sourceUri＝いずれも document URI しかない。ツリー URI は構造上
            // どちらにも現れないため、除外しないと次の起動で必ず解放され、「一度教えた場所を覚えている」
            // という案X の前提（次回は選ばずに自動走査）が毎回壊れる。
            // 上限予算の観点でも安全: ツリー権限は同時に1件だけ保持する（新しい場所を選んだ時点で
            // BookshelfViewModel が古いツリー権限を解放する）。
            .filterNot { DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri.toString() }
            .toSet()
        orphanedPermissionUris(persistedReadUris, keepUris).forEach { uri ->
            releasePersistedPermission(context, Uri.parse(uri))
        }
    }
}

/** takePersistableUriPermission（BookshelfViewModel.addBook）の対。永続権限は端末全体で
 *  上限があるため用が済んだら返す。未取得（プロバイダ非対応等）だと SecurityException に
 *  なるので防御する（返せなくても実害は上限消費のみ）。
 *  READ|WRITE を指定するのは、取込元PDF削除を可能にする本が WRITE 権限も保持しているため
 *  （両方まとめて返す）。保持していない flag の解放は無害な no-op＝READ のみ保持の再開ジョブ URI にも安全。
 *  なぜトップレベル関数か: pending_jobs の確定処理（本ファイル）と本削除（LibraryDeleter.deleteBook）の
 *  両方が使う共有ロジックのため、どちらかのクラスに私有させず同パッケージの共通関数に置く。 */
internal fun releasePersistedPermission(context: Context, pdfUri: Uri) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            pdfUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

/**
 * 起動時に解放すべき「孤児」の永続 URI 権限を判定する純関数（テスト対象）。
 * 持続化された読み取り権限のうち pending_jobs 非紐付けのもの＝もう再試行され得ない失敗取込の
 * 置き土産を差集合で選ぶ。UI・contentResolver 非依存で回収ロジックの中核を単体テストするため分離する
 * （releaseOrphanedPermissions が persistedUriPermissions 取得と実解放の副作用を担い、判定はここ）。
 */
internal fun orphanedPermissionUris(
    persistedReadUris: Set<String>,
    keepUris: Set<String>,
): Set<String> = persistedReadUris - keepUris
