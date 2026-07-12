package com.novelreader.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * HTML ディレクトリ導出の一元化（UX監査 portable・復元耐性の下地）の回帰。
 * 保存パスが実在すればそれを、無ければ bookId から filesDir/novels/<bookId> を再導出することを固定する。
 */
class BookEntityPathTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `resolveHtmlDir - filesDir と bookId から決定的に導出する`() {
        val filesDir = File("/data/user/0/com.novelreader/files")
        assertEquals(
            File(filesDir, "novels/abc12345"),
            BookEntity.resolveHtmlDir(filesDir, "abc12345"),
        )
    }

    @Test
    fun `resolvedHtmlDir - 保存パスが実在すればそれを使う`() {
        val filesDir = tmp.newFolder("files")
        val stored = File(filesDir, "novels/id01").apply { mkdirs() }
        val book = BookEntity("id01", "本", stored.absolutePath)
        assertEquals(stored, book.resolvedHtmlDir(filesDir))
    }

    @Test
    fun `resolvedHtmlDir - 保存パスが不在なら bookId から再導出する（別端末復元の下地）`() {
        val filesDir = tmp.newFolder("files")
        // 旧端末の絶対パス（この端末には実在しない）を保存した本を、新端末の filesDir で解決する。
        val book = BookEntity("id01", "本", "/old-device/data/com.novelreader/files/novels/id01")
        assertEquals(File(filesDir, "novels/id01"), book.resolvedHtmlDir(filesDir))
    }
}
