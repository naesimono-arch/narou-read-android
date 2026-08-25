package com.novelreader

import com.novelreader.data.PendingJobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 起動時リカバリ（NovelReaderApplication.runStartupRecoveryOnce）の意思決定を担う純関数の検証
 * （UX監査 measure §E: 回復パスの退行を JVM テストで発火・固定する）。
 * Android 依存（Intent 発火・ContentResolver）を持たない [StartupRecovery.computePlan] のみを対象にする。
 */
class StartupRecoveryTest {

    private fun job(uri: String, name: String = "", at: Long = 0, attempts: Int = 0) =
        PendingJobEntity(uri = uri, displayName = name, enqueuedAt = at, attempts = attempts)

    @Test
    fun `pending 空なら全リストが空・keepUris も空（＝全孤児権限を解放する）`() {
        val plan = StartupRecovery.computePlan(pending = emptyList(), persistedReadUris = setOf("content://x"))
        assertTrue(plan.resumable.isEmpty())
        assertTrue(plan.lost.isEmpty())
        // keepUris が空＝releaseOrphanedPermissions(空) で全孤児権限を解放する順序を表現する
        assertTrue(plan.keepPermissionUris.isEmpty())
    }

    @Test
    fun `権限が生きているものだけ resumable・失効は lost へ振り分ける`() {
        val alive = job("content://a", "A")
        val dead = job("content://b", "B")
        val plan = StartupRecovery.computePlan(
            pending = listOf(alive, dead),
            persistedReadUris = setOf("content://a"),
        )
        assertEquals(listOf(alive), plan.resumable)
        assertEquals(listOf(dead), plan.lost)
    }

    @Test
    fun `keepUris は pending 全体（resumable と lost の両方）を含む`() {
        val a = job("content://a")
        val b = job("content://b")
        val plan = StartupRecovery.computePlan(
            pending = listOf(a, b),
            persistedReadUris = setOf("content://a"), // b は失効
        )
        // 失効分も keep に含める（現行仕様の踏襲＝resumable の権限を確実に守る。lost は persisted に
        // 無いため解放対象にも上がらず実害なし）
        assertEquals(setOf("content://a", "content://b"), plan.keepPermissionUris)
    }

    @Test
    fun `resumable は pending の enqueue 順（入力順）を保つ`() {
        val first = job("content://1", at = 100)
        val second = job("content://2", at = 200)
        val third = job("content://3", at = 300)
        val plan = StartupRecovery.computePlan(
            pending = listOf(first, second, third),
            persistedReadUris = setOf("content://1", "content://2", "content://3"),
        )
        // partition は入力順を保存する＝再投入順＝元のキュー順が保たれる
        assertEquals(listOf(first, second, third), plan.resumable)
        assertTrue(plan.lost.isEmpty())
    }

    @Test
    fun `全て権限失効なら resumable 空・lost 全件`() {
        val a = job("content://a")
        val b = job("content://b")
        val plan = StartupRecovery.computePlan(
            pending = listOf(a, b),
            persistedReadUris = emptySet(),
        )
        assertTrue(plan.resumable.isEmpty())
        assertEquals(listOf(a, b), plan.lost)
    }

    // ── 再起動ループの止め金（取込がプロセスごと落ちる PDF で「二度と起動しない」を防ぐ） ────────

    @Test
    fun `再開回数が上限未満なら従来どおり再開する`() {
        val fresh = job("content://a", "A", attempts = 0)
        val once = job("content://b", "B", attempts = StartupRecovery.MAX_RESUME_ATTEMPTS - 1)
        val plan = StartupRecovery.computePlan(
            pending = listOf(fresh, once),
            persistedReadUris = setOf("content://a", "content://b"),
        )
        assertEquals(listOf(fresh, once), plan.resumable)
        assertTrue(plan.exhausted.isEmpty())
    }

    @Test
    fun `再開回数が上限に達したジョブは自動再開せず exhausted へ落とす`() {
        // これが無いと「起動→再開→同じ地点でプロセス死」が永久に続き、アプリが二度と起動しなくなる。
        val doomed = job("content://big", "巨大PDF", attempts = StartupRecovery.MAX_RESUME_ATTEMPTS)
        val plan = StartupRecovery.computePlan(
            pending = listOf(doomed),
            persistedReadUris = setOf("content://big"),
        )
        assertTrue("上限到達ジョブを再開してはならない", plan.resumable.isEmpty())
        assertEquals(listOf(doomed), plan.exhausted)
        // 権限は keep 側に残す（この後 removePendingJob が settle して返す＝解放の主体を二重化しない）。
        assertTrue(plan.keepPermissionUris.contains("content://big"))
    }

    @Test
    fun `上限を超えた回数でも exhausted に落ちる（等号だけを見ていないこと）`() {
        val doomed = job("content://big", attempts = StartupRecovery.MAX_RESUME_ATTEMPTS + 5)
        val plan = StartupRecovery.computePlan(listOf(doomed), setOf("content://big"))
        assertTrue(plan.resumable.isEmpty())
        assertEquals(1, plan.exhausted.size)
    }

    @Test
    fun `権限喪失が上限判定より優先される（lost と exhausted は排他）`() {
        // 権限が無いものは再開しようがない＝再試行回数に関わらず lost。両方に現れると二重通知になる。
        val doomed = job("content://gone", attempts = StartupRecovery.MAX_RESUME_ATTEMPTS)
        val plan = StartupRecovery.computePlan(listOf(doomed), persistedReadUris = emptySet())
        assertEquals(listOf(doomed), plan.lost)
        assertTrue(plan.exhausted.isEmpty())
        assertTrue(plan.resumable.isEmpty())
    }
}
