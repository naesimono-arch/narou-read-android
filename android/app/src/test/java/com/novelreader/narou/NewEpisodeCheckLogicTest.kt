package com.novelreader.narou

import com.novelreader.narou.model.NarouNovel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodeCheckLogicTest {

    private val linked = mapOf(
        "N1111AA" to ("book-1" to "本A"),
        "N2222BB" to ("book-2" to "本B"),
    )

    private fun novel(ncode: String, allNo: Int?) = NarouNovel(ncode = ncode, generalAllNo = allNo)

    @Test
    fun `初回（基準値なし）は通知せず現在値で無音初期化する`() {
        val (alerts, newMarks) = computeNewEpisodeAlerts(
            linked, marks = emptyMap(), currents = listOf(novel("N1111AA", 951)),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("N1111AA" to 951), newMarks)
    }

    @Test
    fun `増分があれば増えた話数だけ通知し基準値を前進させる`() {
        val (alerts, newMarks) = computeNewEpisodeAlerts(
            linked, marks = mapOf("N1111AA" to 950), currents = listOf(novel("N1111AA", 955)),
        )
        assertEquals(1, alerts.size)
        assertEquals(NewEpisodeAlert("N1111AA", "book-1", "本A", newCount = 5, totalAllNo = 955), alerts[0])
        assertEquals(mapOf("N1111AA" to 955), newMarks)
    }

    @Test
    fun `変化なしは通知しない（毎日同じ通知の再送を防ぐ）`() {
        val (alerts, newMarks) = computeNewEpisodeAlerts(
            linked, marks = mapOf("N1111AA" to 955), currents = listOf(novel("N1111AA", 955)),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("N1111AA" to 955), newMarks)
    }

    @Test
    fun `話数減少（なろう側の削除）は通知せず基準値を現在値へ追従させる`() {
        val (alerts, newMarks) = computeNewEpisodeAlerts(
            linked, marks = mapOf("N1111AA" to 955), currents = listOf(novel("N1111AA", 950)),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("N1111AA" to 950), newMarks)
    }

    @Test
    fun `紐付けに無い作品・話数欠落・ncode表記ゆれは安全に処理される`() {
        val (alerts, newMarks) = computeNewEpisodeAlerts(
            linked,
            marks = mapOf("N2222BB" to 10),
            currents = listOf(
                novel("n2222bb", 12),      // 小文字で返っても大文字正規化で一致する
                novel("N9999ZZ", 100),     // 紐付けに無い → 無視
                novel("N1111AA", null),    // 話数欠落 → 無視
            ),
        )
        assertEquals(1, alerts.size)
        assertEquals("N2222BB", alerts[0].ncode)
        assertEquals(2, alerts[0].newCount)
        assertEquals(mapOf("N2222BB" to 12), newMarks)
    }

    @Test
    fun `複数作品の同時更新はそれぞれ通知される`() {
        val (alerts, _) = computeNewEpisodeAlerts(
            linked,
            marks = mapOf("N1111AA" to 100, "N2222BB" to 200),
            currents = listOf(novel("N1111AA", 103), novel("N2222BB", 201)),
        )
        assertEquals(listOf("N1111AA" to 3, "N2222BB" to 1), alerts.map { it.ncode to it.newCount })
    }
}
