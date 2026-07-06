package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class NarouJsonParseTest {

    @Test
    fun `なろうAPIレスポンスJSONが正しくパースされ、フィールド値がオブジェクトにマッピングされること`() {
        val moshi = Moshi.Builder().build()
        val listType = Types.newParameterizedType(List::class.java, NarouNovel::class.java)
        val jsonAdapter = moshi.adapter<List<NarouNovel>>(listType)

        // テスト用 golden ファイルを読み込む
        val jsonStream = javaClass.classLoader?.getResourceAsStream("narou/weekly_ranking.json")
            ?: throw IllegalStateException("Resource not found")
        val jsonString = jsonStream.bufferedReader().use { it.readText() }

        val result = jsonAdapter.fromJson(jsonString)
        requireNotNull(result)

        // 1. 先頭要素の検証: allcount のみが設定され、作品情報はすべて null
        val firstElement = result.first()
        assertEquals(1043215, firstElement.allcount)
        assertNull(firstElement.title)
        assertNull(firstElement.ncode)
        assertNull(firstElement.writer)
        assertNull(firstElement.story)
        assertNull(firstElement.globalPoint)
        assertNull(firstElement.generalAllNo)
        assertNull(firstElement.end)
        assertNull(firstElement.length)
        assertNull(firstElement.novelType)

        // 2. drop(1) 以下の作品情報の検証
        val novels = result.drop(1)
        assertEquals(3, novels.size)

        // すべての作品要素で allcount は null であること
        novels.forEach { novel ->
            assertNull(novel.allcount)
        }

        // 3. 各作品要素の個別マッピング・意味解釈の検証

        // 1件目: 連載中 (end=1, noveltype=1)
        val novel1 = novels[0]
        assertEquals("転生賢者の異世界ライフ", novel1.title)
        assertEquals("N1234AB", novel1.ncode)
        assertEquals("山田太郎", novel1.writer)
        assertEquals("異世界に転生した主人公が、最弱スキルで最強を目指す物語。", novel1.story)
        assertEquals(987654, novel1.globalPoint) // global_point -> globalPoint
        assertEquals(320, novel1.generalAllNo) // general_all_no -> generalAllNo
        assertEquals(1, novel1.end) // end = 1 (連載中)
        assertEquals(1250000, novel1.length) // length -> length
        assertEquals(1, novel1.novelType) // noveltype -> novelType (1 = 連載)

        // 2件目: 完結済連載 (end=0, noveltype=1)
        val novel2 = novels[1]
        assertEquals("完結した勇者の物語", novel2.title)
        assertEquals("N5678CD", novel2.ncode)
        assertEquals("鈴木花子", novel2.writer)
        assertEquals("全10章で綺麗に完結した王道ファンタジー。", novel2.story)
        assertEquals(543210, novel2.globalPoint)
        assertEquals(152, novel2.generalAllNo)
        assertEquals(0, novel2.end) // end = 0 (完結済)
        assertEquals(680000, novel2.length)
        assertEquals(1, novel2.novelType) // novelType (1 = 連載)

        // 3件目: 短編 (end=0, noveltype=2, general_all_no=1)
        val novel3 = novels[2]
        assertEquals("たった一夜の短編", novel3.title)
        assertEquals("N9999ZZ", novel3.ncode)
        assertEquals("佐藤次郎", novel3.writer)
        assertEquals("一話完結の短編小説。", novel3.story)
        assertEquals(12000, novel3.globalPoint)
        assertEquals(1, novel3.generalAllNo) // general_all_no = 1
        assertEquals(0, novel3.end) // end = 0 (短編)
        assertEquals(8500, novel3.length)
        assertEquals(2, novel3.novelType) // novelType (2 = 短編)
    }
}
