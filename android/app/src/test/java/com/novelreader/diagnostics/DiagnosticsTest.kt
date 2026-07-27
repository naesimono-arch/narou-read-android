package com.novelreader.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 端末内診断の純ロジック（整形・ローテーション選定・異常終了の判定）を固定する。
 *
 * なぜ純ロジックだけを JVM で見るか: 実際の採取は端末の状態（メモリ・版数）と
 * SharedPreferences に依存し実機でしか再現しないが、**回収した人が読む形**と
 * **溜め過ぎない規則**と**異常終了と数える条件**は端末非依存＝ここで固定できる。
 */
class DiagnosticsTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun event(
        kind: DiagnosticEvent.Kind = DiagnosticEvent.Kind.CRASH,
        stackTrace: String? = "java.lang.IllegalStateException: boom\n\tat Foo.bar(Foo.kt:1)\n",
        screen: String? = "reading/abc123",
        availableMemMb: Long? = 512,
        totalMemMb: Long? = 5743,
        lowMemory: Boolean? = false,
    ) = DiagnosticEvent(
        kind = kind,
        epochMillis = Instant.parse("2026-07-27T14:30:12.345Z").toEpochMilli(),
        screen = screen,
        threadName = "main",
        stackTrace = stackTrace,
        appVersionName = "1.0",
        appVersionCode = 1L,
        deviceModel = "HUAWEI ELE-L29",
        androidRelease = "10",
        androidSdk = 29,
        availableMemMb = availableMemMb,
        totalMemMb = totalMemMb,
        lowMemory = lowMemory,
    )

    @Test
    fun `format は回収者が読む1件のテキストを組む`() {
        val text = event().format(zone)
        assertTrue(text.startsWith("=== novel-reader diagnostics ==="))
        assertTrue(text.contains("kind: CRASH"))
        // 時刻はタイムゾーン込みで出す（回収時に「いつの端末時刻か」が曖昧にならないため）
        assertTrue(text.contains("time: 2026-07-27T23:30:12.345+09:00"))
        assertTrue(text.contains("device: HUAWEI ELE-L29 / Android 10 (API 29)"))
        assertTrue(text.contains("screen: reading/abc123"))
        assertTrue(text.contains("memory: avail 512MB / total 5743MB / lowMemory=false"))
        assertTrue(text.contains("--- stack trace ---"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun `欠測値は空欄でなく明示的に不明と書く`() {
        // 空欄だと「取れなかった」のか「そういう値だった」のか回収時に区別できないため。
        val text = event(screen = null, availableMemMb = null, totalMemMb = null, lowMemory = null)
            .format(zone)
        assertTrue(text.contains("screen: (unknown)"))
        assertTrue(text.contains("memory: (n/a)"))
    }

    @Test
    fun `異常終了はスタックトレース節を持たない`() {
        // ABNORMAL_EXIT は例外を伴わない推定なので、空の節を出して「トレースが取れた」と誤読させない。
        val text = event(kind = DiagnosticEvent.Kind.ABNORMAL_EXIT, stackTrace = null).format(zone)
        assertTrue(text.contains("kind: ABNORMAL_EXIT"))
        assertFalse(text.contains("--- stack trace ---"))
    }

    @Test
    fun `stackTraceOf は cause の連鎖まで含める`() {
        val cause = IllegalArgumentException("root cause")
        val trace = DiagnosticEvent.stackTraceOf(RuntimeException("wrapper", cause))
        assertTrue(trace.contains("wrapper"))
        assertTrue(trace.contains("Caused by"))
        assertTrue(trace.contains("root cause"))
    }

    @Test
    fun `ファイル名は時刻順と辞書順が一致する`() {
        // 一覧・間引きをソートだけで済ませる前提（DiagnosticsStore.expired）が崩れないことの固定。
        val older = DiagnosticsStore.fileNameOf(1_700_000_000_000, DiagnosticEvent.Kind.CRASH)
        val newer = DiagnosticsStore.fileNameOf(1_800_000_000_000, DiagnosticEvent.Kind.ABNORMAL_EXIT)
        assertTrue(older < newer)
        assertEquals("event-1700000000000-crash.txt", older)
        assertEquals("event-1800000000000-abnormal_exit.txt", newer)
    }

    @Test
    fun `expired は新しい方から keep 件を残す`() {
        val names = (1L..5L).map { DiagnosticsStore.fileNameOf(it, DiagnosticEvent.Kind.CRASH) }
        val dropped = DiagnosticsStore.expired(names, keep = 2)
        // 残るのは 5,4／消えるのは 3,2,1
        assertEquals(3, dropped.size)
        assertTrue(dropped.contains(DiagnosticsStore.fileNameOf(1, DiagnosticEvent.Kind.CRASH)))
        assertTrue(dropped.contains(DiagnosticsStore.fileNameOf(3, DiagnosticEvent.Kind.CRASH)))
        assertFalse(dropped.contains(DiagnosticsStore.fileNameOf(4, DiagnosticEvent.Kind.CRASH)))
    }

    @Test
    fun `expired は件数が上限以下なら何も消さない`() {
        val names = listOf(DiagnosticsStore.fileNameOf(1, DiagnosticEvent.Kind.CRASH))
        assertTrue(DiagnosticsStore.expired(names, keep = DiagnosticsStore.KEEP_EVENTS).isEmpty())
    }

    @Test
    fun `trimToTail は上限以下なら丸ごと残す`() {
        assertEquals("a\nb\n", DiagnosticsStore.trimToTail("a\nb\n", 100))
    }

    @Test
    fun `trimToTail は古い側を捨て行の途中では切らない`() {
        // 行の途中で切ると壊れた1行が先頭に残り、読む側が数値を誤読する。
        val text = "old line 1\nold line 2\nnewest line\n"
        val trimmed = DiagnosticsStore.trimToTail(text, 20)
        assertTrue(trimmed.endsWith("newest line\n"))
        assertFalse(trimmed.contains("old line 1"))
        // 先頭は必ず行頭から始まる（途中で切られた残骸が無い）
        trimmed.lineSequence().first().let { assertTrue(it.isEmpty() || text.contains("\n$it")) }
    }

    @Test
    fun `trimToTail は改行の無い巨大な1行を捨てずに残す`() {
        // 丸ごと捨てると症状の唯一の記録が消えるため、多少溢れても残す方を選ぶ約束。
        val single = "x".repeat(100)
        assertEquals(single, DiagnosticsStore.trimToTail(single, 10))
    }

    @Test
    fun `異常終了は「開いたまま」かつ時刻が既知のときだけ数える`() {
        assertTrue(SessionWatch.shouldReportAbnormalExit(wasOpen = true, lastSeenAt = 1_700_000_000_000))
        // 正常に背面へ回っていた＝以後の kill は Android の正常動作なので数えない
        assertFalse(SessionWatch.shouldReportAbnormalExit(wasOpen = false, lastSeenAt = 1_700_000_000_000))
        // 時刻未記録で「1970年に落ちた」という無意味な記録を作らない
        assertFalse(SessionWatch.shouldReportAbnormalExit(wasOpen = true, lastSeenAt = 0L))
    }
}
