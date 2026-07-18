package com.novelreader.ui.skins.m

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常駐 backdrop の「空の表示・z2 演出抑止」状態機の純ロジック担保（透過の天の川・2026-07-19 裁定）。
 *
 * 固定するもの:
 *  1) 読書M本文（index.html 以外）＝空は見せる（hidden=false）が z2 流星は止める（meteorSuppressed=true・読書Mモーションゼロ）
 *  2) 読書M目次（index.html）＝透過の構造画面＝空も流星も見せる（両フラグ false）
 *  3) 非M（backdrop 無し）＝両フラグ false（空を消さず流星も抑止しない・呼出側は M のときだけ controller が non-null）
 */
class SkyBackdropReadingStateTest {

    @Test
    fun `読書M本文は空を見せz2流星のみ抑止する`() {
        val st = skyBackdropReadingState(isSeizu = true, isIndex = false)
        assertFalse("読書M本文でも空（backdrop）は見せる＝透過の天の川", st.hidden)
        assertTrue("読書M本文は z2 流星を止める＝読書Mモーションゼロ", st.meteorSuppressed)
    }

    @Test
    fun `読書M目次は空も流星も見せる`() {
        val st = skyBackdropReadingState(isSeizu = true, isIndex = true)
        assertFalse(st.hidden)
        assertFalse("目次は透過の構造画面＝流星を止めない", st.meteorSuppressed)
    }

    @Test
    fun `非Mは両フラグとも常にfalse`() {
        assertEquals(SkyBackdropReadingState(hidden = false, meteorSuppressed = false),
            skyBackdropReadingState(isSeizu = false, isIndex = false))
        assertEquals(SkyBackdropReadingState(hidden = false, meteorSuppressed = false),
            skyBackdropReadingState(isSeizu = false, isIndex = true))
    }
}
