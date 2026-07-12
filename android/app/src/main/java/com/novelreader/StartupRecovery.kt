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
     */
    data class Plan(
        val keepPermissionUris: Set<String>,
        val resumable: List<PendingJobEntity>,
        val lost: List<PendingJobEntity>,
    )

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
        val (resumable, lost) = pending.partition { it.uri in persistedReadUris }
        return Plan(keepPermissionUris, resumable, lost)
    }
}
