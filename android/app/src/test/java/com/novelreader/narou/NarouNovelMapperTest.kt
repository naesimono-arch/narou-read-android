package com.novelreader.narou

import com.novelreader.discovery.model.SerialState
import com.novelreader.narou.model.NarouNovel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [NarouNovel.toWorkSummary] の翻訳規約テスト。
 * なろうJSONの都合（novelType 二重キー・end 逆転意味論・allcount センチネル）がマッパ内側で
 * 吸収され、サイト非依存の [com.novelreader.discovery.model.WorkSummary] に正しく写ることを担保する。
 */
class NarouNovelMapperTest {

    /** 全フィールドが揃った作品を全数写像する（正常系）。 */
    @Test
    fun toWorkSummary_mapsAllFields() {
        val novel = NarouNovel(
            title = "テスト作品",
            ncode = "N1234AB",
            writer = "作者名",
            globalPoint = 1000,
            generalAllNo = 42,
            end = 1,
            length = 84000,
            noveltypeCompact = 1,
            genre = 201,
            time = 168,
            dailyPoint = 10,
            weeklyPoint = 20,
            monthlyPoint = 30,
            quarterPoint = 40,
            novelupdatedAt = "2026-07-31 05:45:59",
        )

        val summary = novel.toWorkSummary()
        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals("テスト作品", summary.title)
        assertEquals("作者名", summary.author)
        assertEquals("narou", summary.sourceSite)
        // 公式 URL は narouWorkUrl 経由＝trim+lowercase の正規化が効く。
        assertEquals("https://ncode.syosetu.com/n1234ab/", summary.workUrl)
        assertEquals("N1234AB", summary.ncode)
        assertEquals(42, summary.chapterCount)
        assertEquals(SerialState.ONGOING, summary.serialState)
        assertEquals(84000, summary.lengthChars)
        assertEquals(168, summary.readMinutes)
        assertEquals(201, summary.genreCode)
        val points = requireNotNull(summary.points)
        assertEquals(1000, points.global)
        assertEquals(10, points.daily)
        assertEquals(20, points.weekly)
        assertEquals(30, points.monthly)
        assertEquals(40, points.quarter)
        // 新着順の並びを決める値。生文字列のまま素通しする（解釈は表示層の責務＝WorkSummary.updatedAt の KDoc）。
        assertEquals("2026-07-31 05:45:59", summary.updatedAt)
    }

    /**
     * updatedAt は novelupdated_at 由来であり general_lastup（最終掲載日）とは別物であること。
     *
     * なぜ固定するか: なろうは似た日時項目を複数返し（nu=作品更新日時／gl=最終掲載日）、新着順(order=new)の
     * 並びを決めるのは前者だけ。取り違えると一覧の「更新日時」が並び順を説明しなくなり、
     * ポイントを出していた頃と同じ「並びが壊れて見える」表示へ静かに戻る。実データでも両者は数十秒ずれる
     * （2026-07-31 実測: nu=05:41:45 / gl=05:41:04 の作品が存在）。
     */
    @Test
    fun toWorkSummary_updatedAtComesFromNovelupdatedAtNotGeneralLastup() {
        val novel = NarouNovel(
            title = "a",
            writer = "w",
            novelupdatedAt = "2026-07-31 05:41:45",
            generalLastup = "2026-07-31 05:41:04",
        )
        assertEquals("2026-07-31 05:41:45", novel.toWorkSummary()?.updatedAt)
    }

    /**
     * novelupdated_at 欠損時は updatedAt が null のまま運ばれること（現在時刻や general_lastup で代用しない）。
     * 順位の根拠として読まれる値ゆえ、根拠が無いことを別の値で埋めると誤情報になるため（WorkSummary の約束）。
     */
    @Test
    fun toWorkSummary_updatedAtStaysNullWhenAbsent() {
        val novel = NarouNovel(title = "a", writer = "w", generalLastup = "2026-07-31 05:41:04")
        assertNull(novel.toWorkSummary()?.updatedAt)
    }

    /** novelType の二重キー（of 指定=noveltype / of 無指定=novel_type）どちらでも連載状態が定まる。 */
    @Test
    fun toWorkSummary_resolvesNovelTypeFromEitherKey() {
        // compact キー（of 指定経路）で短編。
        val viaCompact = NarouNovel(title = "a", writer = "w", noveltypeCompact = 2)
        assertEquals(SerialState.SHORT, viaCompact.toWorkSummary()?.serialState)

        // full キー（of 無指定=novelDetail 経路）で短編。同じ結果になること。
        val viaFull = NarouNovel(title = "a", writer = "w", novelTypeFull = 2)
        assertEquals(SerialState.SHORT, viaFull.toWorkSummary()?.serialState)
    }

    /** end/novelType から SerialState の3値（SHORT/ONGOING/COMPLETED）が導けること。 */
    @Test
    fun toWorkSummary_serialStateThreeValues() {
        // novelType=2 → 短編（end の値に依らず SHORT が優先）。
        val short = NarouNovel(title = "a", writer = "w", noveltypeCompact = 2, end = 0)
        assertEquals(SerialState.SHORT, short.toWorkSummary()?.serialState)

        // novelType=1 かつ end=0 → 完結（end の意味は直感と逆）。
        val completed = NarouNovel(title = "a", writer = "w", noveltypeCompact = 1, end = 0)
        assertEquals(SerialState.COMPLETED, completed.toWorkSummary()?.serialState)

        // novelType=1 かつ end=1 → 連載中。
        val ongoing = NarouNovel(title = "a", writer = "w", noveltypeCompact = 1, end = 1)
        assertEquals(SerialState.ONGOING, ongoing.toWorkSummary()?.serialState)
    }

    /** title 欠落・writer 欠落はいずれも null 返し（呼び出し側でフィルタする設計）。 */
    @Test
    fun toWorkSummary_skipsWhenTitleOrWriterNull() {
        assertNull(NarouNovel(title = null, writer = "w").toWorkSummary())
        assertNull(NarouNovel(title = "a", writer = null).toWorkSummary())
    }

    /**
     * allcount センチネル（先頭要素＝allcount のみで title/writer が無い）は null に落ち、
     * WorkSummary へ混入しない（allcount 概念の分離）。
     */
    @Test
    fun toWorkSummary_dropsAllcountSentinel() {
        val sentinel = NarouNovel(allcount = 12345)
        assertNull(sentinel.toWorkSummary())
    }

    /** 全ポイントが欠損なら points は null に集約される。 */
    @Test
    fun toWorkSummary_pointsNullWhenAllAbsent() {
        val novel = NarouNovel(title = "a", writer = "w")
        assertNull(novel.toWorkSummary()?.points)
    }

    /** 1つでもポイントがあれば WorkPoints を返し、欠損分はフィールド null として保つ。 */
    @Test
    fun toWorkSummary_pointsKeptWhenAnyPresent() {
        val novel = NarouNovel(title = "a", writer = "w", weeklyPoint = 500)
        val points = requireNotNull(novel.toWorkSummary()?.points)
        assertEquals(500, points.weekly)
        assertNull(points.global)
        assertNull(points.daily)
        assertNull(points.monthly)
        assertNull(points.quarter)
    }

    /** ncode 欠損時は workUrl も導出せず null。 */
    @Test
    fun toWorkSummary_workUrlNullWhenNcodeAbsent() {
        val novel = NarouNovel(title = "a", writer = "w", ncode = null)
        val summary = requireNotNull(novel.toWorkSummary())
        assertNull(summary.workUrl)
        assertNull(summary.ncode)
    }
}
