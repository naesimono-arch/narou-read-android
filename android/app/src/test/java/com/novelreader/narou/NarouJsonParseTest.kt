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

    @Test
    fun `新フィールドを含むJSONが正しくパースされること`() {
        val moshi = Moshi.Builder().build()
        val listType = Types.newParameterizedType(List::class.java, NarouNovel::class.java)
        val jsonAdapter = moshi.adapter<List<NarouNovel>>(listType)

        val jsonString = """
            [
              {"allcount": 1},
              {
                "title": "新フィールドテスト",
                "ncode": "N1111AA",
                "genre": 101,
                "keyword": "キーワード1 キーワード2",
                "general_firstup": "2026-07-01 12:00:00",
                "general_lastup": "2026-07-07 18:00:00",
                "time": 45,
                "fav_novel_cnt": 1500,
                "review_cnt": 15,
                "all_hyoka_cnt": 80,
                "sasie_cnt": 12,
                "kaiwaritu": 35,
                "daily_point": 100,
                "weekly_point": 700,
                "monthly_point": 3000,
                "quarter_point": 9000,
                "istensei": 1,
                "istenni": 0,
                "iszankoku": 1,
                "isstop": 0
              }
            ]
        """.trimIndent()

        val result = jsonAdapter.fromJson(jsonString)
        requireNotNull(result)
        assertEquals(2, result.size)

        val novel = result[1]
        assertEquals("新フィールドテスト", novel.title)
        assertEquals("N1111AA", novel.ncode)
        assertEquals(101, novel.genre)
        assertEquals("キーワード1 キーワード2", novel.keyword)
        assertEquals("2026-07-01 12:00:00", novel.generalFirstup)
        assertEquals("2026-07-07 18:00:00", novel.generalLastup)
        assertEquals(45, novel.time)
        assertEquals(1500, novel.favNovelCnt)
        assertEquals(15, novel.reviewCnt)
        assertEquals(80, novel.allHyokaCnt)
        assertEquals(12, novel.sasieCnt)
        assertEquals(35, novel.kaiwaritu)
        assertEquals(100, novel.dailyPoint)
        assertEquals(700, novel.weeklyPoint)
        assertEquals(3000, novel.monthlyPoint)
        assertEquals(9000, novel.quarterPoint)
        assertEquals(1, novel.istensei)
        assertEquals(0, novel.istenni)
        assertEquals(1, novel.iszankoku)
        assertEquals(0, novel.isstop)
    }
}
