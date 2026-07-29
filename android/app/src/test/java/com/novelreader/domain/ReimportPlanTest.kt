package com.novelreader.domain

import com.novelreader.data.BookEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本文欠落→再取込提案（2026-07-29 案B＋案C）の純ロジック検証。
 * テスト契約: 検出（実体有無）／4分岐の分類／指紋の一度だけ表示／まとめて再取込の対象選別（①④のみ）。
 * 再取込が既存行を保持する契約は repository 層のテスト（BookRepositoryTest / AddWebBookTest）が固定する。
 */
class ReimportPlanTest {

    private fun book(
        id: String = "b1",
        sourceUri: String? = null,
        sourceUrl: String? = null,
        htmlDirPath: String = "/nonexistent/$id",
    ) = BookEntity(id, "本$id", htmlDirPath, "著", sourceUri = sourceUri, sourceUrl = sourceUrl)

    // ── 検出（実体有無）: hasContent は index.html の実在を代表点にする ─────────────────

    @Test
    fun `hasContent - index_html があれば本文あり`() {
        val filesDir = createTempDir(prefix = "reimportHas")
        try {
            val dir = File(filesDir, "novels/b1").apply { mkdirs() }
            File(dir, "index.html").writeText("<html></html>")
            assertTrue(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - ディレクトリごと消えていれば欠落（Auto Backup が DB のみ復元した形）`() {
        val filesDir = createTempDir(prefix = "reimportMissing")
        try {
            assertFalse(book("b1", htmlDirPath = File(filesDir, "novels/b1").absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - ディレクトリはあるが index が無い torn 状態も欠落扱い`() {
        val filesDir = createTempDir(prefix = "reimportTorn")
        try {
            val dir = File(filesDir, "novels/b1").apply { mkdirs() }
            File(dir, "chap_1.html").writeText("x") // 書きかけ残骸のみ
            assertFalse(book("b1", htmlDirPath = dir.absolutePath).hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `hasContent - 保存パスが他端末の残骸でも bookId 再導出（resolvedHtmlDir）で実体を見つける`() {
        val filesDir = createTempDir(prefix = "reimportResolve")
        try {
            // htmlDirPath は存在しない旧端末パス。実体は filesDir/novels/<id> の規約位置にある。
            val canonical = File(filesDir, "novels/b1").apply { mkdirs() }
            File(canonical, "index.html").writeText("<html></html>")
            assertTrue(book("b1", htmlDirPath = "/data/old-device/novels/b1").hasContent(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── 4分岐の分類 ──────────────────────────────────────────────────────

    @Test
    fun `classifyReimport - sourceUri あり＋権限生存＝AutoPdf（分岐①）`() {
        val plan = classifyReimport(book(sourceUri = "content://docs/a"), hasPersistedRead = { true })
        assertEquals(ReimportPlan.AutoPdf("content://docs/a"), plan)
        assertTrue(plan.isAuto)
    }

    @Test
    fun `classifyReimport - sourceUri あり＋権限失効＝PickPdfPermissionLost（分岐②・ファイル名ヒント付き）`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fkuro.pdf"
        val plan = classifyReimport(book(sourceUri = uri), hasPersistedRead = { false })
        assertEquals(ReimportPlan.PickPdfPermissionLost("kuro.pdf"), plan)
        assertFalse(plan.isAuto)
    }

    @Test
    fun `classifyReimport - sourceUri NULL＝PickPdfNoRecord（分岐③・v20 前の旧取込）`() {
        val plan = classifyReimport(book(), hasPersistedRead = { true })
        assertEquals(ReimportPlan.PickPdfNoRecord, plan)
        assertFalse(plan.isAuto)
    }

    @Test
    fun `classifyReimport - sourceUrl あり＝AutoWeb（分岐④・権限判定より優先）`() {
        val plan = classifyReimport(
            book(sourceUrl = "https://example.com/works/1"),
            hasPersistedRead = { error("Web 本では権限照会自体を呼ばない") },
        )
        assertEquals(ReimportPlan.AutoWeb("https://example.com/works/1"), plan)
        assertTrue(plan.isAuto)
    }

    @Test
    fun `buildReimportPlans - 欠落本だけが地図に載る`() {
        val missing = book("m1", sourceUri = "content://docs/a")
        val intact = book("ok1", sourceUri = "content://docs/b")
        val plans = buildReimportPlans(
            listOf(missing, intact),
            isContentMissing = { it.id == "m1" },
            hasPersistedRead = { true },
        )
        assertEquals(setOf("m1"), plans.keys)
        assertEquals(ReimportPlan.AutoPdf("content://docs/a"), plans["m1"])
    }

    // ── ファイル名ヒント（分岐②の選び直し材料）────────────────────────────────

    @Test
    fun `sourceFileNameHint - SAF 授権ID（primary区切り）からファイル名を復元する`() {
        assertEquals(
            "黒の魔王.pdf",
            sourceFileNameHint(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2F%E9%BB%92%E3%81%AE%E9%AD%94%E7%8E%8B.pdf",
            ),
        )
    }

    @Test
    fun `sourceFileNameHint - raw パス形式（Downloads プロバイダ）からも末尾ファイル名を切り出す`() {
        assertEquals(
            "foo.pdf",
            sourceFileNameHint(
                "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Ffoo.pdf",
            ),
        )
    }

    @Test
    fun `sourceFileNameHint - 復元できない URI は null（ダイアログは手がかり行を出さないだけ）`() {
        assertNull(sourceFileNameHint("content://docs/"))
    }

    // ── 指紋の一度だけ表示（案C・「新規に検出した際に一度だけ」）───────────────────────

    @Test
    fun `sweep指紋 - 初検出は表示・提示後の同一集合は再表示しない・新たな欠落で再表示する`() {
        // 初検出（seen 空）＝表示。
        assertTrue(shouldShowReimportSweep(setOf("a", "b"), emptySet()))
        // 「あとで」/実行で seen={a,b} を保存 → 同一集合では出さない。
        val seen = setOf("a", "b")
        assertFalse(shouldShowReimportSweep(setOf("a", "b"), seen))
        // 一部復旧で集合が縮んでも（b だけ残存）出さない＝縮小は新規検出ではない。
        assertFalse(shouldShowReimportSweep(setOf("b"), seen))
        // 新たな欠落 c が増えたら再表示。
        assertTrue(shouldShowReimportSweep(setOf("b", "c"), seen))
        // 欠落ゼロなら当然出さない。
        assertFalse(shouldShowReimportSweep(emptySet(), seen))
    }

    @Test
    fun `sweep指紋 - prune で復旧済み本を seen から外す＝再欠落は新規検出として拾う`() {
        val seen = setOf("a", "b")
        // a が復旧して欠落は {b} → seen も {b} へ刈り込む。
        val pruned = pruneReimportSeenIds(seen, missingIds = setOf("b"))
        assertEquals(setOf("b"), pruned)
        // その後 a が再び欠落＝seen に無い＝バナーが出る（黙殺しない）。
        assertTrue(shouldShowReimportSweep(setOf("a", "b"), pruned))
    }

    // ── まとめて再取込の対象選別（①④のみ＝ピッカー必須分を混ぜない）──────────────────

    @Test
    fun `reimportBreakdown - 4系統を数え autoTotal は①④だけを含む`() {
        val plans = listOf(
            ReimportPlan.AutoPdf("content://docs/1"),
            ReimportPlan.AutoPdf("content://docs/2"),
            ReimportPlan.AutoWeb("https://example.com/w/1"),
            ReimportPlan.PickPdfPermissionLost("a.pdf"),
            ReimportPlan.PickPdfNoRecord,
        )
        val b = reimportBreakdown(plans)
        assertEquals(2, b.autoPdf)
        assertEquals(1, b.autoWeb)
        assertEquals(1, b.pickPermissionLost)
        assertEquals(1, b.pickNoRecord)
        assertEquals(5, b.total)
        assertEquals(3, b.autoTotal)     // ①2冊＋④1冊＝自動実行の対象
        assertEquals(2, b.manualTotal)   // ②③はカード起点の個別対応へ回す
        // 実行対象の選別そのもの（VM runSweepReimport の filterIsInstance と同じ規則）: isAuto が①④だけ true。
        assertEquals(3, plans.count { it.isAuto })
    }

    // ── 状態行文言（案B・棚面に4種の語彙を発明しない）───────────────────────────

    @Test
    fun `reimportStatusLabel - PDF系は同文・Webだけ再取得の文言`() {
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.AutoPdf("u")))
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.PickPdfPermissionLost(null)))
        assertEquals("本文なし・タップで再取込", reimportStatusLabel(ReimportPlan.PickPdfNoRecord))
        assertEquals("Web作品・再取得できます", reimportStatusLabel(ReimportPlan.AutoWeb("u")))
    }
}
