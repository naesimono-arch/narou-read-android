package com.novelreader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * 同型スナックバー集約の純関数契約（2026-07-29 裁定④）。
 * 対象＝Channel(BUFFERED) 直列消費で「閉じた直後に同型が即再表示」される一括投入
 * （複数PDF再取込→全件「取り込み済み」＝2026-07-16 実機確定）を「N件は取り込み済みです」1本へ畳む。
 */
@RunWith(JUnit4::class)
class AggregateErrorEventsTest {

    private val key = AppErrorEvent.KEY_DUPLICATE_IMPORT
    private fun dup(title: String, transient: Boolean = false) =
        AppErrorEvent("「$title」は既に取り込み済みです", transient = transient, aggregationKey = key)

    @Test
    fun `同型3件は「3件は取り込み済みです」1本へ集約する`() {
        val out = aggregateErrorEvents(listOf(dup("A"), dup("B"), dup("C")))
        assertEquals(1, out.size)
        assertEquals("3件は取り込み済みです", out[0].message)
    }

    @Test
    fun `1件だけなら原文のまま（個別の題名情報を捨てない）`() {
        val out = aggregateErrorEvents(listOf(dup("A")))
        assertEquals(listOf("「A」は既に取り込み済みです"), out.map { it.message })
    }

    @Test
    fun `key 無しイベントは順序ごと素通しし集約は最初の出現位置に置く`() {
        val fail = AppErrorEvent("取り込みに失敗しました", retryUri = "content://x")
        val out = aggregateErrorEvents(listOf(dup("A"), fail, dup("B")))
        assertEquals(listOf("2件は取り込み済みです", "取り込みに失敗しました"), out.map { it.message })
        // アクション持ち（retryUri）はそのまま残る（集約で操作を失わない）。
        assertEquals("content://x", out[1].retryUri)
    }

    @Test
    fun `transient は全員一致のときだけ引き継ぐ（混在は残置側＝false へ安全に倒す）`() {
        // 全員 transient（Web 重複のみの一括）→ Short 自動消滅のまま。
        assertTrue(aggregateErrorEvents(listOf(dup("A", true), dup("B", true)))[0].transient)
        // PDF 重複（残置）と Web 重複（自動消滅）の混在 → 勝手に自動消滅させず「閉じる」残置へ。
        assertFalse(aggregateErrorEvents(listOf(dup("A", true), dup("B", false)))[0].transient)
    }

    @Test
    fun `単発イベントのリストはそのまま返す`() {
        val info = AppErrorEvent("中断されていた変換 1 件を再開します")
        assertEquals(listOf(info), aggregateErrorEvents(listOf(info)))
    }
}
