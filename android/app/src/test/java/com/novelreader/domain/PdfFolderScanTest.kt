package com.novelreader.domain

import com.novelreader.data.BookEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF フォルダ走査（2026-07-29 案X）の純ロジック検証。
 *
 * なぜ JVM テストで固める必要が強いか（監督の実機実測3）: 実機の蔵書は既に手作業で復旧済みで
 * hasContent が全冊 true＝案X の動作を実機の自然状態では観測できない。走査・照合・集計の正しさは
 * ここでしか担保できないため、対象選別／並べ替え／早期終了／停止／不一致の扱いまで全て固定する。
 */
class PdfFolderScanTest {

    private fun book(id: String, sha: String?, sourceUri: String? = null) =
        BookEntity(id, "本$id", "/nonexistent/$id", "著", contentSha256 = sha, sourceUri = sourceUri)

    private fun candidate(name: String) = ScanCandidate(uri = "content://tree/doc/$name", displayName = name)

    // ── 走査対象の選別 ────────────────────────────────────────────────

    @Test
    fun `buildScanTargets - 欠落かつ指紋を持つ本だけが対象（自動分岐と旧取込は除く）`() {
        val books = listOf(
            book("lost", "sha-lost", sourceUri = "content://docs/a"),   // ②指紋あり＝対象
            book("old", null),                                          // ③指紋なし＝対象外
            book("auto", "sha-auto", sourceUri = "content://docs/b"),   // ①自動＝対象外（二重取込防止）
            book("intact", "sha-intact"),                               // 欠落していない＝plans に無い
        )
        val plans = mapOf(
            "lost" to ReimportPlan.PickPdfPermissionLost("kuro.pdf", "sha-lost"),
            "old" to ReimportPlan.PickPdfNoRecord(null),
            "auto" to ReimportPlan.AutoPdf("content://docs/b"),
        )
        val targets = buildScanTargets(books, plans)
        assertEquals(listOf("lost"), targets.map { it.bookId })
        assertEquals("kuro.pdf", targets.single().fileNameHint)
        assertEquals("sha-lost", targets.single().contentSha256)
    }

    @Test
    fun `buildScanTargets - Web 本は走査に混ざらない（作品ページから戻すのが正経路）`() {
        val books = listOf(book("w", "sha-w"))
        val plans = mapOf("w" to ReimportPlan.AutoWeb("https://example.com/w/1"))
        assertTrue(buildScanTargets(books, plans).isEmpty())
    }

    // ── 候補の並べ替え（早期終了を効かせるための順序）────────────────────────────

    @Test
    fun `orderScanCandidates - 取込元と同名を先頭・次に蔵書らしい名前・残りは列挙順`() {
        val targets = listOf(ScanTarget("b1", "本", "sha-1", fileNameHint = "Kuro.pdf"))
        val ordered = orderScanCandidates(
            listOf(candidate("manual.pdf"), candidate("n1234ab.pdf"), candidate("kuro.pdf"), candidate("other.pdf")),
            targets,
            isLikelyNovelPdf = { it.startsWith("n") },
        )
        assertEquals(
            listOf("kuro.pdf", "n1234ab.pdf", "manual.pdf", "other.pdf"),
            ordered.map { it.displayName },
        )
    }

    @Test
    fun `orderScanCandidates - ヒントが無くても順序は安定（列挙順を保つ）`() {
        val names = listOf("a.pdf", "b.pdf", "c.pdf")
        val ordered = orderScanCandidates(names.map(::candidate), emptyList())
        assertEquals(names, ordered.map { it.displayName })
    }

    // ── 照合本体 ────────────────────────────────────────────────────

    @Test
    fun `scanPdfFolder - 指紋一致で本とファイルが結びつく（ファイル名は判定に使わない）`() = runTest {
        val targets = listOf(
            ScanTarget("b1", "薬師", "sha-1"),
            ScanTarget("b2", "魔王", "sha-2"),
        )
        // 実機のなろうPDF は n0000xx.pdf 形式＝名前からは本を判別できない。内容だけで当てられることを固定する。
        val hashes = mapOf("n0001aa.pdf" to "sha-2", "n0002bb.pdf" to "sha-1", "misc.pdf" to "sha-x")
        val report = scanPdfFolder(
            targets = targets,
            enumerate = { listOf(candidate("n0001aa.pdf"), candidate("n0002bb.pdf"), candidate("misc.pdf")) },
            hashOf = { hashes[it.displayName] },
        )
        assertEquals(2, report.matchedCount)
        assertEquals(
            mapOf("b1" to "n0002bb.pdf", "b2" to "n0001aa.pdf"),
            report.matches.associate { it.target.bookId to it.candidate.displayName },
        )
        assertEquals(0, report.unmatchedCount)
        assertFalse(report.cancelled)
    }

    @Test
    fun `scanPdfFolder - 全冊当たった時点で残りは読まない（早期終了）`() = runTest {
        val targets = listOf(ScanTarget("b1", "本", "sha-1"))
        val read = mutableListOf<String>()
        val report = scanPdfFolder(
            targets = targets,
            enumerate = { listOf(candidate("hit.pdf"), candidate("huge1.pdf"), candidate("huge2.pdf")) },
            hashOf = { read += it.displayName; if (it.displayName == "hit.pdf") "sha-1" else "other" },
        )
        assertEquals(listOf("hit.pdf"), read)
        assertEquals(1, report.hashedCount)
        assertEquals(3, report.candidateCount) // 総数は正直に報告する（読んだ数と別物）
    }

    @Test
    fun `scanPdfFolder - 一致しなかった本は unmatched に残る（黙って消えない）`() = runTest {
        val targets = listOf(ScanTarget("b1", "薬師", "sha-1"), ScanTarget("b2", "魔王", "sha-2"))
        val report = scanPdfFolder(
            targets = targets,
            enumerate = { listOf(candidate("a.pdf")) },
            hashOf = { "sha-1" },
        )
        assertEquals(listOf("b1"), report.matches.map { it.target.bookId })
        assertEquals(listOf("b2"), report.unmatched.map { it.bookId })
    }

    @Test
    fun `scanPdfFolder - 読めないファイルは件数で報告し走査は続行する`() = runTest {
        val targets = listOf(ScanTarget("b1", "本", "sha-1"))
        val report = scanPdfFolder(
            targets = targets,
            enumerate = { listOf(candidate("broken.pdf"), candidate("ok.pdf")) },
            hashOf = { if (it.displayName == "broken.pdf") null else "sha-1" },
        )
        assertEquals(1, report.unreadableCount)
        assertEquals(1, report.matchedCount) // 壊れた1件で走査全体が落ちない
    }

    @Test
    fun `scanPdfFolder - 停止しても そこまでの一致は結果に残る（部分成果を巻き戻さない）`() = runTest {
        val targets = listOf(ScanTarget("b1", "薬師", "sha-1"), ScanTarget("b2", "魔王", "sha-2"))
        var hashed = 0
        val report = scanPdfFolder(
            targets = targets,
            enumerate = { listOf(candidate("a.pdf"), candidate("b.pdf"), candidate("c.pdf")) },
            hashOf = { hashed++; "sha-1" },
            // 1件読んだ直後に停止要求が立つ状況を模す。
            isCancelled = { hashed >= 1 },
        )
        assertTrue(report.cancelled)
        assertEquals(1, report.hashedCount)
        assertEquals(listOf("b1"), report.matches.map { it.target.bookId })
        assertEquals(listOf("b2"), report.unmatched.map { it.bookId })
    }

    @Test
    fun `scanPdfFolder - 対象ゼロなら列挙すらしない（無駄な IO を起こさない）`() = runTest {
        var enumerated = false
        val report = scanPdfFolder(
            targets = emptyList(),
            enumerate = { enumerated = true; emptyList() },
            hashOf = { null },
        )
        assertFalse(enumerated)
        assertEquals(0, report.candidateCount)
    }

    @Test
    fun `scanPdfFolder - 進捗は列挙直後に総数を出し 1件ごとに前進する`() = runTest {
        val progress = mutableListOf<ScanProgress>()
        scanPdfFolder(
            targets = listOf(ScanTarget("b1", "本", "sha-1"), ScanTarget("b2", "本2", "sha-2")),
            enumerate = { listOf(candidate("a.pdf"), candidate("b.pdf")) },
            hashOf = { if (it.displayName == "a.pdf") "sha-1" else "sha-2" },
            onProgress = { progress += it },
        )
        assertEquals(
            listOf(
                ScanProgress(hashed = 0, total = 2, matched = 0),
                ScanProgress(hashed = 1, total = 2, matched = 1),
                ScanProgress(hashed = 2, total = 2, matched = 2),
            ),
            progress,
        )
    }

    @Test
    fun `scanPdfFolder - 同一内容のコピーが2つあっても本は1冊にしか結びつかない`() = runTest {
        val report = scanPdfFolder(
            targets = listOf(ScanTarget("b1", "本", "sha-1")),
            enumerate = { listOf(candidate("orig.pdf"), candidate("copy.pdf")) },
            hashOf = { "sha-1" },
        )
        assertEquals(1, report.matchedCount)
        assertEquals("orig.pdf", report.matches.single().candidate.displayName)
    }
}
