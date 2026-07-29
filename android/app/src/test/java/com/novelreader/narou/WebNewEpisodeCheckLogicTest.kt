package com.novelreader.narou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1 Web 蔵書パス（既読話数統合・2026-07-29）の契約テスト。
 * 正本＝NewEpisodeCheckLogic.kt の shouldCheckWebBookNow／computeWebNewEpisodeAlerts／webNewEpisodeMarkKey。
 * なろうパス（computeNewEpisodeAlerts）の契約は NewEpisodeCheckLogicTest が別途持つ。
 */
class WebNewEpisodeCheckLogicTest {

    private fun state(
        bookId: String = "b1",
        title: String = "Web本",
        device: Int = 10,
        lastRead: Int = 10,
    ) = WebBookCheckState(
        bookId = bookId,
        bookTitle = title,
        sourceUrl = "https://kakuyomu.jp/works/1",
        deviceChapterCount = device,
        lastReadChapterNumber = lastRead,
    )

    // ---- 照会ゲート（既読話数の統合点）----

    @Test
    fun `最終章を開いた（既読が取込済み章数に到達）本だけが照会対象になる`() {
        assertTrue(shouldCheckWebBookNow(state(device = 10, lastRead = 10)))
        // 読み残しがある間は端末内に続きが既にある＝サイト照会しない（規約礼儀と通知ノイズの双方を抑える）。
        assertFalse(shouldCheckWebBookNow(state(device = 10, lastRead = 9)))
        assertFalse(shouldCheckWebBookNow(state(device = 10, lastRead = 0)))
    }

    @Test
    fun `取込済み章数ゼロ（実体欠損・抽出異常）は照会しない`() {
        assertFalse(shouldCheckWebBookNow(state(device = 0, lastRead = 0)))
    }

    @Test
    fun `既読が取込済み章数を超えていても照会対象（進捗が章より先行する異常系の防御）`() {
        assertTrue(shouldCheckWebBookNow(state(device = 10, lastRead = 12)))
    }

    // ---- 差分判定 ----

    @Test
    fun `初回（基準値なし）は取込済み章数を基準に即通知する`() {
        // なろうパスの無音初期化と意図的に異なる: Web 取込は目次全章を落とすため取込時点で
        // 端末章数＝サイト総話数が保証され、差分＝取込後に実際に増えた話数（誤報にならない）。
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(device = 100, lastRead = 100)),
            marks = emptyMap(),
            siteTotals = mapOf("b1" to 103),
        )
        assertEquals(1, alerts.size)
        assertEquals(NewEpisodeAlert("web:b1", "b1", "Web本", newCount = 3, totalAllNo = 103), alerts[0])
        assertEquals(mapOf("web:b1" to 103), marks)
    }

    @Test
    fun `初回で増分ゼロなら通知せず基準値だけ初期化する`() {
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(device = 100, lastRead = 100)),
            marks = emptyMap(),
            siteTotals = mapOf("b1" to 100),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("web:b1" to 100), marks)
    }

    @Test
    fun `基準値ありは基準値からの増分だけ通知する（取込済み章数からではない）`() {
        // 前回 103 まで通知済み→今回 105: 通知は +2（105-100=5 ではない＝同じ話を二重通知しない礼儀）。
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(device = 100, lastRead = 100)),
            marks = mapOf("web:b1" to 103),
            siteTotals = mapOf("b1" to 105),
        )
        assertEquals(1, alerts.size)
        assertEquals(2, alerts[0].newCount)
        assertEquals(105, alerts[0].totalAllNo)
        assertEquals(mapOf("web:b1" to 105), marks)
    }

    @Test
    fun `変化なしは通知しない（毎日同じ通知の再送を防ぐ）`() {
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(device = 100, lastRead = 100)),
            marks = mapOf("web:b1" to 105),
            siteTotals = mapOf("b1" to 105),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("web:b1" to 105), marks)
    }

    @Test
    fun `サイト側の話数減少は通知せず基準値を現在値へ追従させる`() {
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(device = 100, lastRead = 100)),
            marks = mapOf("web:b1" to 105),
            siteTotals = mapOf("b1" to 102),
        )
        assertTrue(alerts.isEmpty())
        assertEquals(mapOf("web:b1" to 102), marks)
    }

    @Test
    fun `フェッチ結果が無い本（照会対象外・取得失敗）は通知も基準値更新もしない`() {
        // siteTotals 非搭載＝据え置き契約: 誤った前進（取りこぼし）・巻き戻し（二重通知）の双方を防ぐ。
        val (alerts, marks) = computeWebNewEpisodeAlerts(
            listOf(state(bookId = "b1"), state(bookId = "b2", title = "Web本2", device = 50, lastRead = 50)),
            marks = mapOf("web:b1" to 10, "web:b2" to 50),
            siteTotals = mapOf("b2" to 52),
        )
        assertEquals(listOf("web:b2"), alerts.map { it.ncode })
        assertEquals(mapOf("web:b2" to 52), marks)
    }

    @Test
    fun `複数の本の同時更新はそれぞれ通知される`() {
        val (alerts, _) = computeWebNewEpisodeAlerts(
            listOf(state(bookId = "b1"), state(bookId = "b2", title = "Web本2", device = 50, lastRead = 50)),
            marks = mapOf("web:b1" to 10, "web:b2" to 50),
            siteTotals = mapOf("b1" to 13, "b2" to 51),
        )
        assertEquals(listOf("web:b1" to 3, "web:b2" to 1), alerts.map { it.ncode to it.newCount })
    }

    // ---- 基準値キーの名前空間 ----

    @Test
    fun `基準値キーは web 接頭辞つきで正規化 ncode と衝突しない`() {
        assertEquals("web:b1", webNewEpisodeMarkKey("b1"))
        // 正規化 ncode（trim+大文字の英数のみ）にコロンは現れない＝同一テーブル同居でも名前空間が機械的に分かれる。
        assertTrue(webNewEpisodeMarkKey("b1").contains(":"))
    }
}
