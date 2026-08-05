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
}
