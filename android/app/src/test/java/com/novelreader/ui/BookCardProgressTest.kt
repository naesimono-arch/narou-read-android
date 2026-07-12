package com.novelreader.ui

// progressFractionFor は BookCard から viewmodel 層へ移設された（読書状態フィルタと共有するため）。
import com.novelreader.viewmodel.progressFractionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * progressFractionFor（F-N）の単体テスト。
 * 本棚カードの進捗割合が「最終章を開いた瞬間100%」の嘘を出さないことを固定する。
 */
@RunWith(JUnit4::class)
class BookCardProgressTest {

    @Test
    fun `未読(章番号null)は null`() {
        assertNull(progressFractionFor(chapNum = null, totalChaps = 10, scrollIndex = 0, scrollOffset = 0))
    }

    @Test
    fun `総章数0は null（0除算を避ける）`() {
        assertNull(progressFractionFor(chapNum = 1, totalChaps = 0, scrollIndex = 0, scrollOffset = 0))
    }

    @Test
    fun `中間章は 章番号割る総数`() {
        assertEquals(0.3f, progressFractionFor(3, 10, 0, 0)!!, 1e-6f)
    }

    @Test
    fun `最終章の先頭(未スクロール)は100パーではなく N-1 割る N`() {
        // 最終章(=10)を開いただけ・未スクロール → 0.9（あと1章ぶん未読）。100%の嘘を出さない。
        assertEquals(0.9f, progressFractionFor(10, 10, 0, 0)!!, 1e-6f)
    }

    @Test
    fun `最終章をスクロール済みでも100パーにはせず N-半章 割る N(index)`() {
        // ssot Major: 1行スクロール＝100% の嘘を消す。末尾到達は判定できないため <1f に留める。
        assertEquals(0.95f, progressFractionFor(10, 10, 2, 0)!!, 1e-6f)
    }

    @Test
    fun `最終章をスクロール済みでも100パーにはせず N-半章 割る N(offsetのみ)`() {
        assertEquals(0.95f, progressFractionFor(10, 10, 0, 50)!!, 1e-6f)
    }

    @Test
    fun `最終章スクロール中は常に1f未満（読了の嘘を出さない境界）`() {
        // 章数が変わっても (N-0.5)/N は厳密に <1f であることを固定する（FINISHED へ落ちない不変条件）。
        assertTrue(progressFractionFor(10, 10, 1, 0)!! < 1f)
        assertTrue(progressFractionFor(3, 3, 0, 1)!! < 1f)
        assertTrue(progressFractionFor(1, 1, 5, 0)!! < 1f)
        // かつ未スクロール時の (N-1)/N より大きい（読み進めた分だけ増える単調性）。
        assertTrue(progressFractionFor(10, 10, 1, 0)!! > progressFractionFor(10, 10, 0, 0)!!)
    }

    @Test
    fun `単一章の本は 先頭0パー・スクロール済みは0-5パー（100パーにしない）`() {
        assertEquals(0f, progressFractionFor(1, 1, 0, 0)!!, 1e-6f)
        // (1-0.5)/1 = 0.5。単一章でもスクロールだけで読了(1f)にはしない。
        assertEquals(0.5f, progressFractionFor(1, 1, 1, 0)!!, 1e-6f)
    }

    @Test
    fun `章番号が総数を超えても最終章扱いで安全に丸める`() {
        // 防御的: 想定外に chapNum > totalChaps でも 100%上限で破綻しない。
        assertEquals(0.9f, progressFractionFor(11, 10, 0, 0)!!, 1e-6f)
    }
}
