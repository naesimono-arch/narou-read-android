package com.novelreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 没入中の「層①ピル → 層②取っ手」の受け渡し規則（[immersiveHandleVisible]）を固定する。
 *
 * 固定したい穴（2026-09-03 の実機実測で判明した真因）: 取っ手のゲートを入力 boolean `showChromeHint` で
 * 切ると、false は**退場アニメの開始**を指すだけなので、ピルがまだ 98% 不透明のフレームで取っ手が
 * 0→満値へ跳んだ（約180ms 並んで見えた）。ピルの実寿命＝遷移の currentState / isIdle を見れば、
 * 「最後の1フレームが描かれ切ってから取っ手が出る」が構造的に決まる。
 *
 * 正本 `tutorial-onboarding-K.html` §6「ピルが出て自分で消える。そのあと取っ手だけが残る」＝順次であって
 * 同時ではない、が固定したい振る舞い。
 */
class ImmersiveHandleHandoffTest {

    @Test
    fun `ピルが居ない定常の没入中は取っ手を出す`() {
        assertTrue(
            immersiveHandleVisible(barsVisualReady = true, hintIdle = true, hintCurrentlyShown = false)
        )
    }

    @Test
    fun `ピル表示中は取っ手を出さない`() {
        assertFalse(
            immersiveHandleVisible(barsVisualReady = true, hintIdle = true, hintCurrentlyShown = true)
        )
    }

    @Test
    fun `ピルの退場アニメ中は取っ手を出さない（本件の回帰点）`() {
        // 退場開始〜完了までの各フレーム＝isIdle:false / currentState:true。
        // 旧実装（!showChromeHint）はこの区間で true を返し、消えかけのピルの真下へ取っ手を出していた。
        assertFalse(
            immersiveHandleVisible(barsVisualReady = true, hintIdle = false, hintCurrentlyShown = true)
        )
    }

    @Test
    fun `ピルの入場アニメ中も取っ手を出さない（入場方向の維持）`() {
        // 入場開始のフレーム＝isIdle:false / currentState:false。ここで false を返すことが
        // 「取っ手が先に消えてからピルが出る」既存の正しい順序（実機で正常と確認済み）の担保。
        assertFalse(
            immersiveHandleVisible(barsVisualReady = true, hintIdle = false, hintCurrentlyShown = false)
        )
    }

    @Test
    fun `バーの初期退避が未確定の間は何があっても出さない`() {
        assertFalse(
            immersiveHandleVisible(barsVisualReady = false, hintIdle = true, hintCurrentlyShown = false)
        )
    }

    @Test
    fun `退場の全フレーム列で重なりが一度も起きない`() {
        // MutableTransitionState が退場中に取る値の列（開始→…→完了）。
        // 完了フレームだけが取っ手の出番＝ピルが描かれているどのフレームとも重ならない。
        val exitFrames = listOf(
            false to true,   // 退場開始（ピルはまだほぼ不透明）
            false to true,   // 退場中
            false to true,   // 退場終盤
            true to false,   // 退場完了＝ピルは1フレームも描かれない
        )
        val visible = exitFrames.map { (idle, shown) ->
            immersiveHandleVisible(barsVisualReady = true, hintIdle = idle, hintCurrentlyShown = shown)
        }
        assertTrue("取っ手が出てよいのは退場完了フレームだけ", visible == listOf(false, false, false, true))
    }
}
