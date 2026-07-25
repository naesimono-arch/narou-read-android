package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 目次の読了率導出（tocReadProgressPercent）の単体テスト。
 * D/M/P/J の現在地バー・HUD が共有する純関数で、可視の既読マーク数（現在章より前の行）を単一真実源に
 * 読了率（％）を返す（捏造せず画面の状態と一致させる）。0 除算・端数丸め・境界を固定する。
 */
class TocReadProgressPercentTest {

    @Test
    fun `既読0（未読・現在章が先頭）は0パーセント`() {
        assertEquals(0, tocReadProgressPercent(readCount = 0, total = 10))
    }

    @Test
    fun `全話読了なら100パーセント`() {
        assertEquals(100, tocReadProgressPercent(readCount = 10, total = 10))
    }

    @Test
    fun `端数は四捨五入する（1÷3＝33パーセント）`() {
        assertEquals(33, tocReadProgressPercent(readCount = 1, total = 3))
    }

    @Test
    fun `端数は四捨五入する（2÷3＝67パーセント）`() {
        // 66.66… は四捨五入で 67（切り捨ての 66 でない）。
        assertEquals(67, tocReadProgressPercent(readCount = 2, total = 3))
    }

    @Test
    fun `total0は0除算せず0パーセント`() {
        assertEquals(0, tocReadProgressPercent(readCount = 0, total = 0))
    }

    @Test
    fun `total負値でも0パーセント（防御）`() {
        // 想定外入力でも 0 除算・負値%を返さない防御（分母は必ず正のときだけ割る）。
        assertEquals(0, tocReadProgressPercent(readCount = 3, total = -1))
    }

    @Test
    fun `途中まで読了は既読数÷全話数の四捨五入`() {
        // 340話中127話目が現在＝読了126話 → 126÷340 = 37.05… → 37。
        assertEquals(37, tocReadProgressPercent(readCount = 126, total = 340))
    }
}
