package com.novelreader.ui.discovery

import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.workPoints
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.junit.After
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
    fun `pointLabel - orderに対応するptがカンマ区切りで返り、値なしはnullになること`() {
        assertEquals("週間 12,345pt", pointLabel(NarouOrder.WEEKLY, workSummary(points = workPoints(weekly = 12345))))
        assertEquals("日間 100pt", pointLabel(NarouOrder.DAILY, workSummary(points = workPoints(daily = 100))))
        assertEquals("月間 7pt", pointLabel(NarouOrder.MONTHLY, workSummary(points = workPoints(monthly = 7))))
        assertEquals("四半期 20pt", pointLabel(NarouOrder.QUARTER, workSummary(points = workPoints(quarter = 20))))
        assertEquals("累計 999pt", pointLabel(NarouOrder.TOTAL, workSummary(points = workPoints(global = 999))))
        assertNull(pointLabel(NarouOrder.WEEKLY, workSummary(points = workPoints(global = 999))))
    }

    @Test
    fun `pointLabel - 各orderは自分の並び順を決めた期間のptだけを読み、他期間のptには反応しないこと`() {
        // 固定する不変条件: 順位数字の隣に出る数値は「その並びを決めた指標」でなければならない
        // （読み手はそれを順位の根拠として読むため）。全期間のptが揃った作品を与えても、
        // 各 order は自分の期間の値だけを拾う＝他期間の値が紛れ込まない。
        val all = workPoints(global = 1, daily = 2, weekly = 3, monthly = 4, quarter = 5)
        assertEquals("累計 1pt", pointLabel(NarouOrder.TOTAL, workSummary(points = all)))
        assertEquals("日間 2pt", pointLabel(NarouOrder.DAILY, workSummary(points = all)))
        assertEquals("週間 3pt", pointLabel(NarouOrder.WEEKLY, workSummary(points = all)))
        assertEquals("月間 4pt", pointLabel(NarouOrder.MONTHLY, workSummary(points = all)))
        assertEquals("四半期 5pt", pointLabel(NarouOrder.QUARTER, workSummary(points = all)))
        // 新着は novelupdated_at 降順＝ptが並びに関与しない。よって「どのptも根拠にならない」＝出さない。
        assertNull(pointLabel(NarouOrder.NEW, workSummary(points = all)))
    }

    @Test
    fun `pointLabel - 新着タブは累計ptを出さないこと（並び順と無関係な数値で順位が壊れて見えた回帰）`() {
        // 2026-07-30 実機: 新着タブが「1位 22pt・2位 27,031pt・3位 0pt」と並び、更新日時順という
        // 正しい並びが壊れて見えた。値の大小・0・欠損のいずれでも pt を出さないことを固定する。
        assertNull(pointLabel(NarouOrder.NEW, workSummary(points = workPoints(global = 22))))
        assertNull(pointLabel(NarouOrder.NEW, workSummary(points = workPoints(global = 27_031))))
        assertNull(pointLabel(NarouOrder.NEW, workSummary(points = workPoints(global = 0))))
        assertNull(pointLabel(NarouOrder.NEW, workSummary(points = null)))
    }

    // ── 更新日時ラベル（新着順の「順位の根拠」）─────────────────────────────

    /** 端末TZを弄るテストがあるため、各テスト後に必ず戻す（他テストへの汚染防止）。 */
    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(null)
    }

    @Test
    fun `orderMetricLabel - 新着は更新日時・他の期間はptを出し、両者は排他であること`() {
        // 本命の不変条件: 順位数字の隣に出るのは「その並びを決めた指標」ひとつだけ。
        val work = workSummary(points = workPoints(global = 999, weekly = 12345), updatedAt = "2026-07-31 05:45:59")
        // 新着＝更新日時順ゆえ更新日時。ptを持っていても pt は出さない。
        assertEquals("05:45 更新", orderMetricLabel(NarouOrder.NEW, work, NOW))
        // pt で並ぶ期間は pt。更新日時を持っていても更新日時は出さない。
        assertEquals("週間 12,345pt", orderMetricLabel(NarouOrder.WEEKLY, work, NOW))
        assertEquals("累計 999pt", orderMetricLabel(NarouOrder.TOTAL, work, NOW))
    }

    @Test
    fun `updatedAtLabel - 同じ暦日なら時刻表記になること（当日00時ちょうど・ほぼ24時間差も含む）`() {
        assertEquals("05:45 更新", updatedAtLabel("2026-07-31 05:45:59", NOW))
        // 当日の 00:00 ちょうど＝暦日の下端。時刻は0埋め2桁（作品詳細の HH:mm と同書式）。
        assertEquals("00:00 更新", updatedAtLabel("2026-07-31 00:00:00", jst("2026-07-31 12:00:00")))
        // 同じ暦日なら経過がほぼ24時間でも「今日」＝時刻表記。経過時間窓ではなく暦日で判定している証拠。
        assertEquals("00:00 更新", updatedAtLabel("2026-07-31 00:00:10", jst("2026-07-31 23:59:50")))
    }

    @Test
    fun `updatedAtLabel - 暦日が違えば経過が数分でも日付相対になること（24時間窓との差）`() {
        // 昨日の 23:59。
        assertEquals("昨日 更新", updatedAtLabel("2026-07-30 23:59:00", jst("2026-07-31 12:00:00")))
        // 日付が変わった直後: 経過はわずか1分だが暦日は昨日。relativeReadLabel へ素の経過ミリ秒を渡すと
        // 24時間窓に入って「今日」に化ける（＝暦日判定と矛盾する）。暦日の00:00同士を渡す実装がこれを防ぐ。
        assertEquals("昨日 更新", updatedAtLabel("2026-07-30 23:59:30", jst("2026-07-31 00:00:30")))
    }

    @Test
    fun `updatedAtLabel - 日付相対の語彙はrelativeReadLabelのまま（日・週・月の境界）`() {
        val now = jst("2026-07-31 12:00:00")
        assertEquals("3日前 更新", updatedAtLabel("2026-07-28 10:00:00", now))
        assertEquals("6日前 更新", updatedAtLabel("2026-07-25 10:00:00", now)) // 日表記の上端
        assertEquals("1週間前 更新", updatedAtLabel("2026-07-24 10:00:00", now)) // 7日目で週表記へ
        assertEquals("1ヶ月前 更新", updatedAtLabel("2026-07-01 10:00:00", now)) // 30日目で月表記へ
    }

    @Test
    fun `updatedAtLabel - 欠損・書式外・未来は何も出さないこと`() {
        // WorkSummary.updatedAt の約束: 根拠が無いことを空文字や現在時刻で埋めない＝null を返す。
        assertNull(updatedAtLabel(null, NOW))
        assertNull(updatedAtLabel("", NOW))
        assertNull(updatedAtLabel("2026-07-31", NOW))            // 時刻部が無い
        assertNull(updatedAtLabel("2026/07/31 05:45:59", NOW))   // 区切り違い
        assertNull(updatedAtLabel("いつか", NOW))
        // 端末時計のズレ等で未来になった場合も出さない（relativeReadLabel の未来防御を引き継ぐ）。
        assertNull(updatedAtLabel("2026-08-01 00:00:00", jst("2026-07-31 12:00:00")))
    }

    @Test
    fun `updatedAtLabel - 判定はJST固定で端末タイムゾーンに影響されないこと`() {
        // JST 08:00 は UTC では前日 23:00・米西海岸では前日 16:00。端末TZ基準で判定していると
        // 同じ入力が「昨日 更新」に化ける。なろうが発行しなろうのページが表示するのは JST なので、
        // どのTZの端末でも JST の暦日で判定し JST の壁時計を出す（作品詳細 formatLastupLabel と同基準）。
        val raw = "2026-07-31 08:00:00"
        val now = jst("2026-07-31 12:00:00")
        listOf("Asia/Tokyo", "UTC", "America/Los_Angeles", "Pacific/Kiritimati").forEach { id ->
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            assertEquals("端末TZ=$id で判定が揺れた", "08:00 更新", updatedAtLabel(raw, now))
        }
    }

    private companion object {
        val JST: ZoneId = ZoneId.of("Asia/Tokyo")

        /** JST の壁時計文字列をエポックミリへ（テストの「今」を読める形で書くため）。 */
        fun jst(text: String): Long =
            LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(JST).toInstant().toEpochMilli()

        /** 実 API 実測（2026-07-31 05:49 に order=new を取得、上位は4〜20分前に密集）に合わせた「今」。 */
        val NOW: Long = jst("2026-07-31 05:49:00")
    }
}
