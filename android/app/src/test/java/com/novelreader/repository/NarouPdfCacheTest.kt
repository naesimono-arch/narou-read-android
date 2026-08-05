package com.novelreader.repository

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * なろう縦書きPDF取込の DL 一時領域（cacheDir/pdf_import/）の所在・名前照合の検証。
 * テスト契約: findFor は「保存名の大小文字が DB の ncode と食い違っても」storageKey 軸で一致させ、
 * 不在（OS の cache 掃除後）は null で正直に返す（＝ReimportPlan の分類が嘘の自動提案をしない土台）。
 */
class NarouPdfCacheTest {

    private val cacheDir = createTempDir(prefix = "narouPdfCache")

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun put(name: String): File =
        File(NarouPdfCache.dir(cacheDir), name).apply {
            parentFile!!.mkdirs()
            writeText("pdf-bytes")
        }

    @Test
    fun `findFor - 保存名と ncode の大小文字が違っても storageKey 軸で一致する`() {
        // 保存名は DL 時の URL 由来（小文字が典型）・DB の ncode は表記ゆれしうる（正本＝Ncode.storageKey）。
        val f = put("n1453lw.pdf")
        assertEquals(f, NarouPdfCache.findFor(cacheDir, "N1453LW"))
        assertEquals(f, NarouPdfCache.findFor(cacheDir, " n1453lw "))
    }

    @Test
    fun `findFor - 不在なら null（OS が cache を消した後は正直に SAF 分岐へ落とすための契約）`() {
        assertNull(NarouPdfCache.findFor(cacheDir, "n1453lw"))          // ディレクトリごと無い
        put("n9999zz.pdf")
        assertNull(NarouPdfCache.findFor(cacheDir, "n1453lw"))          // 別作品の PDF しか無い
    }

    @Test
    fun `findFor - pdf 以外の拡張子は候補にしない（部分DLの別名残骸を掴まない防御）`() {
        put("n1453lw.tmp")
        assertNull(NarouPdfCache.findFor(cacheDir, "n1453lw"))
    }

    // ── 削除（本削除時カスケード・起動時孤児掃除の機械部分）────────────────────────

    @Test
    fun `deleteFor - 対応する PDF だけを消す（別作品の復旧資源は巻き添えにしない）`() {
        put("n1453lw.pdf")
        val other = put("n9999zz.pdf")
        NarouPdfCache.deleteFor(cacheDir, "N1453LW")
        assertNull(NarouPdfCache.findFor(cacheDir, "n1453lw"))
        assertEquals(other, NarouPdfCache.findFor(cacheDir, "n9999zz"))
        NarouPdfCache.deleteFor(cacheDir, "n0000aa") // 不在は no-op（例外にしない）
    }

    @Test
    fun `sweepOrphans - 蔵書の ncode と pending 参照だけを残し、残骸は名前を問わず回収する`() {
        val kept = put("n1453lw.pdf")            // 現存する蔵書の復旧資源（AutoCachePdf）
        val pending = put("n7777xx.pdf")         // 再開待ちの DL 実体（pending_jobs が参照）
        put("n9999zz.pdf")                       // 削除済みの本の残骸
        put("garbage.pdf")                       // ncode 名でない残骸（部分DL等）
        val swept = NarouPdfCache.sweepOrphans(
            cacheDir,
            keepNcodes = setOf("N1453LW"),       // 正規化済み（Ncode.storageKey 形）で渡す契約
            keepFileNames = setOf("n7777xx.pdf"),
        )
        assertEquals(2, swept)
        assertEquals(kept, NarouPdfCache.findFor(cacheDir, "n1453lw"))
        assertEquals(pending, NarouPdfCache.findFor(cacheDir, "n7777xx"))
        assertNull(NarouPdfCache.findFor(cacheDir, "n9999zz"))
    }

    @Test
    fun `sweepOrphans - ディレクトリ不在（まだ一度も DL していない端末）は 0 件で正常終了`() {
        assertEquals(0, NarouPdfCache.sweepOrphans(cacheDir, emptySet(), emptySet()))
    }
}
