package com.novelreader.ui.discovery

import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 一覧行のラベル導出純関数のテスト。
 * なぜ固定するか: end の意味は直感と逆（0=短編・完結済／1=連載中）という API 仕様の罠を
 * 実装している一等地であり、リファクタでサイレントに壊れると一覧全行の誤表示になるため。
 */
class DiscoveryCommonLabelsTest {

    @Test
    fun `novelStatusLabel - endの逆転仕様どおり 0は完結・1は連載中・novelType2は短編になること`() {
        assertEquals("短編", novelStatusLabel(NarouNovel(noveltypeCompact = 2, end = 0, generalAllNo = 1)))
        assertEquals("完結 88話", novelStatusLabel(NarouNovel(noveltypeCompact = 1, end = 0, generalAllNo = 88)))
        assertEquals("連載中 127話", novelStatusLabel(NarouNovel(noveltypeCompact = 1, end = 1, generalAllNo = 127)))
        // 話数欠損はフォールバック 1話（of で general_all_no を外した場合の保険）
        assertEquals("連載中 1話", novelStatusLabel(NarouNovel(noveltypeCompact = 1, end = 1)))
    }

    @Test
    fun `novelStatusLabel - of無指定(詳細)経路の novel_type キーでも短編判定が効くこと`() {
        // なぜ: 詳細取得は of 無指定でキーが novel_type になる（noveltype しかマップしないと
        // 短編が「完結 1話」に化ける実回帰があった）。合流アクセサ経由で判定されることを固定する。
        assertEquals("短編", novelStatusLabel(NarouNovel(novelTypeFull = 2, end = 0, generalAllNo = 1)))
    }

    @Test
    fun `readTimeLabel - 60分未満は分表記・60分以上は四捨五入の時間表記になること`() {
        assertEquals("約12分", readTimeLabel(NarouNovel(time = 12)))
        assertEquals("約59分", readTimeLabel(NarouNovel(time = 59)))
        assertEquals("約1時間", readTimeLabel(NarouNovel(time = 60)))
        assertEquals("約1時間", readTimeLabel(NarouNovel(time = 89)))  // 119/60=1（切り捨て側の境界）
        assertEquals("約2時間", readTimeLabel(NarouNovel(time = 90)))  // 120/60=2（繰り上げ側の境界）
    }

    @Test
    fun `readTimeLabel - time欠損時はlengthから500字毎切り上げで導出され両方欠損はnullになること`() {
        assertEquals("約2分", readTimeLabel(NarouNovel(length = 501)))  // (501+499)/500=2
        assertEquals("約1分", readTimeLabel(NarouNovel(length = 500)))
        assertNull(readTimeLabel(NarouNovel()))
    }

    @Test
    fun `pointLabel - orderに対応するptがカンマ区切りで返り、NEWは累計へ倒れ、値なしはnullになること`() {
        assertEquals("週間 12,345pt", pointLabel(NarouOrder.WEEKLY, NarouNovel(weeklyPoint = 12345)))
        assertEquals("日間 100pt", pointLabel(NarouOrder.DAILY, NarouNovel(dailyPoint = 100)))
        // NEW に対応する期間ptが無いため累計で代用する仕様
        assertEquals("累計 999pt", pointLabel(NarouOrder.NEW, NarouNovel(globalPoint = 999)))
        assertNull(pointLabel(NarouOrder.WEEKLY, NarouNovel(globalPoint = 999)))
    }
}
