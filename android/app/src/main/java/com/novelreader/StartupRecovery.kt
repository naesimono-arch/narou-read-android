package com.novelreader

import com.novelreader.data.PendingJobEntity

/**
 * 起動時リカバリ（[NovelReaderApplication.runStartupRecoveryOnce]）の意思決定を担う純関数。
 *
 * なぜ抽出するか（UX監査 measure §E『回復パスの意図的発火』）: 起動リカバリの統合順序
 * ——①空 pending でも先に孤児権限を解放 → ②pending を「再開可能／権限喪失」に振り分け——は
 * 退行してもゲート（testDebugUnitTest）が緑のまま通る死角だった（発火テスト 0 件）。
 * partition と keepUris の導出をここへ集約し JVM テストで固定することで、Android 依存（Intent 発火・
 * ContentResolver）を持たない中核ロジックを機械検証可能にする。副作用（権限解放・再投入）は
 * 呼び出し側に残すが、その順序は本 Plan の値（空 pending でも keepPermissionUris を算出できる）で決まる。
 */
object StartupRecovery {

    /**
     * @property keepPermissionUris 権限を解放しない URI 集合（＝現 pending 全て）。呼び出し側は
     *   `releaseOrphanedPermissions(keepPermissionUris)` に渡す。pending が空なら空集合＝全孤児を解放する
     *   （＝「pending 空でも権限解放を先に走らせる」順序をこの値が表現する）。
     * @property resumable 生きた読み取り権限が残り再投入できるジョブ（enqueue 昇順を保つ）。
     * @property lost 権限喪失で再開不能なジョブ（pending 行を掃除し、ユーザーへ通知する）。
     * @property exhausted 再開を [MAX_RESUME_ATTEMPTS] 回試して毎回プロセスごと落ちたジョブ
     *   （pending 行を掃除し、ユーザーへ「この本は取り込めなかった」と伝える）。
     */
    data class Plan(
        val keepPermissionUris: Set<String>,
        val resumable: List<PendingJobEntity>,
        val lost: List<PendingJobEntity>,
        val exhausted: List<PendingJobEntity> = emptyList(),
    )

    /**
     * 同じジョブを起動時リカバリが再開してよい上限回数。これを超えたら**二度と自動再開しない**。
     *
     * なぜ上限が要るか（直している欠陥）: pending_jobs の行は「enqueue 時に書き、成否が確定したら消す」
     * 設計なので、**プロセスごと死ぬと必然的に行が残る**。取込が決定的に落ちる PDF（例: 端末のヒープ天井を
     * 越える巨大文書）では、起動 → 再開 → 同じ地点で死ぬ、が永久に続き、ユーザーから見ると
     * 「アプリが二度と起動しない」。失敗を catch できたときは失敗経路が行を消すのでループにならない
     * ＝**catch が走らないケースだけ**が問題で、そこはコード側の例外処理では原理的に塞げない。
     *
     * なぜ 2 か: 一過性の死（他アプリのメモリ圧で OS に回収された等）からは回復させたいので 1 では狭い。
     * 一方で決定的に落ちる文書に何度も付き合わせる意味は無く、待たされる時間だけが増える。
     * 「2 回試してどちらも死んだなら決定的」と見なすのが、回復性と行き止まり回避のつり合い点。
     */
    const val MAX_RESUME_ATTEMPTS = 2

    /**
     * pending と現在生きている読み取り権限 URI 集合から復旧計画を算出する。
     *
     * @param pending getPendingJobs の結果（enqueue 昇順）。
     * @param persistedReadUris contentResolver.persistedUriPermissions のうち isReadPermission な URI 文字列集合。
     */
    fun computePlan(
        pending: List<PendingJobEntity>,
        persistedReadUris: Set<String>,
    ): Plan {
        // keepUris は「再開対象（resumable）だけ」でなく pending 全体にする（現行仕様の踏襲）:
        // 権限喪失（lost）分は persisted に無く、そもそも解放対象にも上がらないため、pending 全 URI を
        // keep に渡しても実害はなく、resumable の権限を確実に守れる。
        val keepPermissionUris = pending.map { it.uri }.toSet()
        val (alive, lost) = pending.partition { it.uri in persistedReadUris }
        // 権限は生きていても、再開を規定回数試して毎回プロセスごと落ちたものは自動再開の対象から外す。
        // ⚠️ 判定に使うのは「これまでに再開した回数」で、加算は再投入の**前**に行われている
        // （PendingJobStore.markResumeAttempt の why）。よってここで上限に達している行は
        // 「上限回ぶん再開して、そのたびに成否を確定できずに死んだ」ものだけを指す。
        val (exhausted, resumable) = alive.partition { it.attempts >= MAX_RESUME_ATTEMPTS }
        return Plan(keepPermissionUris, resumable, lost, exhausted)
    }
}
