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

    private fun job(uri: String, name: String = "", at: Long = 0) =
        PendingJobEntity(uri = uri, displayName = name, enqueuedAt = at)

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
}
