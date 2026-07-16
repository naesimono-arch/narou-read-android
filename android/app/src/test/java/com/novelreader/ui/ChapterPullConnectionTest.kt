package com.novelreader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 縦書き章送りの終端デルタ捕捉 [ChapterPullConnection] の純ロジック退行固定（P4）。
 *
 * コンポーザブル状態はラムダ注入で分離済みのため Android/Compose ランタイム不要（Offset/Velocity/
 * NestedScrollSource はいずれも JVM 上の value class）＝Robolectric なしで単体テストできる。
 *
 * 座標系（P0 実測）: reverseLayout でも available.x は画面座標＝章末の右ドラッグが +x で漏れる。
 * よって「+x で offset 増＝次章／-x で offset 減＝前章」を検証する（横書きの鏡像）。
 */
class ChapterPullConnectionTest {

    private val bodyWidth = 1000f
    private val userInput = NestedScrollSource.UserInput

    /** 注入ラムダを差せる被験オブジェクト。offset は可変・pullStart/settle 呼び出しを記録する。 */
    private class Harness(
        var enabled: Boolean = true,
        var offset: Float = 0f,
        var bounds: ClosedFloatingPointRange<Float> = -1000f..1000f,
    ) {
        var pullStartCount = 0
        var settledVelocity: Float? = null

        val connection = ChapterPullConnection(
            enabled = { enabled },
            dragOffset = { offset },
            onDragOffset = { offset = it },
            bounds = { bounds },
            onPullStart = { pullStartCount++ },
            onSettle = { v -> settledVelocity = v },
        )
    }

    @Test
    fun `無効時は何も消費せずoffsetも動かさない`() {
        val h = Harness(enabled = false, offset = 0f)
        val pre = h.connection.onPreScroll(Offset(80f, 0f), userInput)
        val post = h.connection.onPostScroll(Offset.Zero, Offset(80f, 0f), userInput)
        assertEquals(Offset.Zero, pre)
        assertEquals(Offset.Zero, post)
        assertEquals(0f, h.offset, 0f)
        assertEquals(0, h.pullStartCount)
    }

    @Test
    fun `ドラッグ以外のsourceは無視する`() {
        val h = Harness(offset = 0f)
        // fling 由来（SideEffect）の余りは引っ張りに使わない＝素通し。
        val post = h.connection.onPostScroll(Offset.Zero, Offset(80f, 0f), NestedScrollSource.SideEffect)
        assertEquals(Offset.Zero, post)
        assertEquals(0f, h.offset, 0f)
    }

    @Test
    fun `章末の正デルタ漏れでoffsetが増え消費量が返る`() {
        val h = Harness(offset = 0f, bounds = -bodyWidth..bodyWidth)
        val consumed = h.connection.onPostScroll(Offset.Zero, Offset(120f, 0f), userInput)
        // +120 を全量積む（次章の引っ張り）。消費として同量を返しリストへは漏らさない。
        assertEquals(120f, h.offset, 0f)
        assertEquals(Offset(120f, 0f), consumed)
    }

    @Test
    fun `boundsで上限にclampされ超過分は消費しない`() {
        val h = Harness(offset = 950f, bounds = -bodyWidth..bodyWidth)
        val consumed = h.connection.onPostScroll(Offset.Zero, Offset(120f, 0f), userInput)
        // 950+120=1070 は上限 1000 で頭打ち＝実際に積めた 50 だけ消費。
        assertEquals(1000f, h.offset, 0f)
        assertEquals(Offset(50f, 0f), consumed)
    }

    @Test
    fun `進めない端章は上限0へ潰れ正デルタを積まない`() {
        // canGoNext=false 相当＝上限 0。章末でさらに右へ引いても引っ張りは生じない。
        val h = Harness(offset = 0f, bounds = -bodyWidth..0f)
        val consumed = h.connection.onPostScroll(Offset.Zero, Offset(120f, 0f), userInput)
        assertEquals(0f, h.offset, 0f)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `章頭の負デルタ漏れでoffsetが減る（前章の引っ張り）`() {
        val h = Harness(offset = 0f, bounds = -bodyWidth..bodyWidth)
        val consumed = h.connection.onPostScroll(Offset.Zero, Offset(-120f, 0f), userInput)
        assertEquals(-120f, h.offset, 0f)
        assertEquals(Offset(-120f, 0f), consumed)
    }

    @Test
    fun `引き戻しは0へ巻き取り消費して返す（preScroll逆符号）`() {
        // 次章を引いた状態(offset>0)で逆向き(-x)に引く＝preScroll で 0 へ近づく分を消費。
        val h = Harness(offset = 200f, bounds = -bodyWidth..bodyWidth)
        val consumed = h.connection.onPreScroll(Offset(-80f, 0f), userInput)
        assertEquals(120f, h.offset, 0f)
        assertEquals(Offset(-80f, 0f), consumed)
        // 引っ張り操作＝settle アニメ中断のため onPullStart が呼ばれる。
        assertEquals(1, h.pullStartCount)
    }

    @Test
    fun `引き戻しは0を跨がず超過分は未消費でリストへ流す`() {
        val h = Harness(offset = 50f, bounds = -bodyWidth..bodyWidth)
        // -80 のうち 0 までの 50 だけ消費し、残り -30 は未消費のまま LazyRow のスクロールへ流す。
        val consumed = h.connection.onPreScroll(Offset(-80f, 0f), userInput)
        assertEquals(0f, h.offset, 0f)
        assertEquals(Offset(-50f, 0f), consumed)
    }

    @Test
    fun `同符号の伸長はpreScrollで消費せずリストへ流す`() {
        // 既に次章を引いた状態(offset>0)でさらに +x＝伸長は preScroll では触らず（postScroll で bounds 込み処理）。
        val h = Harness(offset = 200f, bounds = -bodyWidth..bodyWidth)
        val consumed = h.connection.onPreScroll(Offset(60f, 0f), userInput)
        assertEquals(200f, h.offset, 0f)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `preFlingはoffsetがあればsettleを起動し全速度を消費する`() = runBlocking {
        val h = Harness(offset = 200f)
        val consumed = h.connection.onPreFling(Velocity(1500f, 0f))
        assertEquals(1500f, h.settledVelocity)
        assertEquals(Velocity(1500f, 0f), consumed)
    }

    @Test
    fun `preFlingはoffsetが0なら素通ししsettleを呼ばない`() = runBlocking {
        val h = Harness(offset = 0f)
        val consumed = h.connection.onPreFling(Velocity(1500f, 0f))
        assertNull(h.settledVelocity)
        assertEquals(Velocity.Zero, consumed)
    }
}
