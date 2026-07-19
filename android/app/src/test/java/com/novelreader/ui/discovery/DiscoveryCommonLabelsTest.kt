package com.novelreader.ui.discovery

import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.workPoints
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 一覧行のラベル導出純関数のテスト（入力はサイト非依存の WorkSummary）。
 * なぜ固定するか: 連載状態（SerialState）や欠損時の話数非表示など、リファクタでサイレントに壊れると
 * 一覧全行の誤表示になるため。novelType/end→SerialState の写像そのものは NarouNovelMapperTest が担保する。
 */
class DiscoveryCommonLabelsTest {

    @Test
    fun `novelStatusLabel - SHORTは短編・COMPLETEDは完結・ONGOINGは連載中になること`() {
        assertEquals("短編", novelStatusLabel(workSummary(serialState = SerialState.SHORT, chapterCount = 1)))
        assertEquals("完結 88話", novelStatusLabel(workSummary(serialState = SerialState.COMPLETED, chapterCount = 88)))
        assertEquals("連載中 127話", novelStatusLabel(workSummary(serialState = SerialState.ONGOING, chapterCount = 127)))
        // 話数欠損時は話数を捏造せず状態のみ表示（chapterCount が null の場合も「全0話/1話」等を出さない）
        assertEquals("連載中", novelStatusLabel(workSummary(serialState = SerialState.ONGOING)))
        assertEquals("完結", novelStatusLabel(workSummary(serialState = SerialState.COMPLETED)))
    }

    @Test
    fun `novelStatusLabel - SHORT は話数に依らず短編になること`() {
        // なぜ: なろうの short(novelType=2) は end/話数がどうであれ短編。マッパで SHORT に畳んだ後もラベルが
        // 話数へ引きずられて「完結 1話」等に化けないことを固定する（かつての novel_type キー取りこぼし回帰の後継）。
        assertEquals("短編", novelStatusLabel(workSummary(serialState = SerialState.SHORT, chapterCount = 1)))
    }

    @Test
    fun `readTimeLabel - 60分未満は分表記・60分以上は四捨五入の時間表記になること`() {
        assertEquals("約12分", readTimeLabel(workSummary(readMinutes = 12)))
        assertEquals("約59分", readTimeLabel(workSummary(readMinutes = 59)))
        assertEquals("約1時間", readTimeLabel(workSummary(readMinutes = 60)))
        assertEquals("約1時間", readTimeLabel(workSummary(readMinutes = 89)))  // 119/60=1（切り捨て側の境界）
        assertEquals("約2時間", readTimeLabel(workSummary(readMinutes = 90)))  // 120/60=2（繰り上げ側の境界）
    }

    @Test
    fun `readTimeLabel - readMinutes欠損時はlengthCharsから500字毎切り上げで導出され両方欠損はnullになること`() {
        assertEquals("約2分", readTimeLabel(workSummary(lengthChars = 501)))  // (501+499)/500=2
        assertEquals("約1分", readTimeLabel(workSummary(lengthChars = 500)))
        assertNull(readTimeLabel(workSummary()))
    }

    @Test
    fun `pointLabel - orderに対応するptがカンマ区切りで返り、NEWは累計へ倒れ、値なしはnullになること`() {
        assertEquals("週間 12,345pt", pointLabel(NarouOrder.WEEKLY, workSummary(points = workPoints(weekly = 12345))))
        assertEquals("日間 100pt", pointLabel(NarouOrder.DAILY, workSummary(points = workPoints(daily = 100))))
        // NEW に対応する期間ptが無いため累計で代用する仕様
        assertEquals("累計 999pt", pointLabel(NarouOrder.NEW, workSummary(points = workPoints(global = 999))))
        assertNull(pointLabel(NarouOrder.WEEKLY, workSummary(points = workPoints(global = 999))))
    }
}
