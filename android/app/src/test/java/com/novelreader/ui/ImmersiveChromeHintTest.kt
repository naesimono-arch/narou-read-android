package com.novelreader.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 没入ヒントの「見られた」判定（[awaitImmersiveHintSeen]）を固定する。
 *
 * 固定したい穴（2026-08-21 の真因）: 旧実装はクロームが退避した瞬間に永続フラグを立てていた。
 * 退避を起こすのは中央タップだけでなく**入場時の自動初期退避**もあるため、ユーザーが何もしていない
 * 数フレームでアプリ通算1回きりの教示機会が焼き切れ、しかもその 2600ms の途中で章送り・離脱・
 * バックグラウンド化が起きればピルは誰にも見られずに消えていた。ここでは
 * 「規定尺のあいだ途切れず可視だった回だけが消費に値する」を仮想時間で直接固定する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmersiveChromeHintTest {

    private val required = IMMERSIVE_HINT_VISIBLE_MS

    @Test
    fun `没入へ入った瞬間では消費しない（旧実装の真因＝表示に踏み切った時点で焼いていた）`() = runTest {
        val canBeSeen = MutableStateFlow(false)
        val visibleLog = mutableListOf<Boolean>()
        var seen = false
        val job = launch {
            awaitImmersiveHintSeen(canBeSeen, required) { visibleLog += it }
            seen = true
        }
        runCurrent()

        // 入場時の自動初期退避で没入に入った瞬間（ユーザーは何も操作していない）。
        canBeSeen.value = true
        runCurrent()

        assertTrue("ピル自体は出る", visibleLog.last())
        assertFalse("出した瞬間に通算1回を焼いてはならない", seen)
        job.cancel()
    }

    @Test
    fun `出し切る前に途切れたら消費しない（章送り・メニュー復帰・離脱で消えた回は焼かない）`() = runTest {
        val canBeSeen = MutableStateFlow(false)
        var seen = false
        val job = launch {
            awaitImmersiveHintSeen(canBeSeen, required) {}
            seen = true
        }
        runCurrent()

        canBeSeen.value = true
        advanceTimeBy(required - 1) // あと 1ms で出し切る、という手前まで
        runCurrent()
        assertFalse("尺の手前で既に焼かれている", seen)

        canBeSeen.value = false // ここで消える＝この回は最後まで見られていない
        advanceTimeBy(required * 10)
        runCurrent()

        assertFalse("出し切っていない回で通算1回が焼かれた", seen)
        job.cancel()
    }

    @Test
    fun `規定尺のあいだ途切れず可視だったときに初めて消費する`() = runTest {
        val canBeSeen = MutableStateFlow(false)
        val visibleLog = mutableListOf<Boolean>()
        var seen = false
        val job = launch {
            awaitImmersiveHintSeen(canBeSeen, required) { visibleLog += it }
            seen = true
        }
        runCurrent()

        canBeSeen.value = true
        advanceTimeBy(required - 1)
        runCurrent()
        assertFalse("規定尺に 1ms 足りない時点ではまだ消費しない", seen)

        advanceTimeBy(1)
        runCurrent()
        assertTrue("出し切ったら消費する", seen)
        assertEquals(listOf(false, true), visibleLog)
        job.join()
    }

    @Test
    fun `一度途切れても やり直して出し切れば消費できる（教示の機会は失われない）`() = runTest {
        val canBeSeen = MutableStateFlow(false)
        var seen = false
        val job = launch {
            awaitImmersiveHintSeen(canBeSeen, required) {}
            seen = true
        }
        runCurrent()

        // 1回目: 尺の手前で途切れる。
        canBeSeen.value = true
        advanceTimeBy(required - 1)
        runCurrent()
        canBeSeen.value = false
        runCurrent()

        // 2回目: 再び没入へ。タイマーは 0 から数え直す（前回の経過を持ち越さない）。
        canBeSeen.value = true
        advanceTimeBy(required - 1)
        runCurrent()
        assertFalse("やり直しは 0 から数え直すはず（前回分を持ち越して早期に焼いた）", seen)

        advanceTimeBy(1)
        runCurrent()
        assertTrue("やり直して出し切れば消費できる", seen)
        job.join()
    }

    // ── 前面判定を含む実ゲート（本番と同じ combine 式）──────────────────────────────
    // 本番は snapshotFlow(クローム退避) と Lifecycle.currentStateFlow を combine して canBeSeen にする。
    // ここでは実物の LifecycleRegistry を使い、「前面でない時間は積まれないか」を仮想時間で直接見る。

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `前面でない間は尺を積まない（バックグラウンドで溶けてフラグだけ残るのを防ぐ）`() = runTest {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        val chromeHidden = MutableStateFlow(true)
        var seen = false
        val job = launch {
            awaitImmersiveHintSeen(
                combine(chromeHidden, owner.lifecycle.currentStateFlow) { hidden, state ->
                    hidden && state.isAtLeast(Lifecycle.State.RESUMED)
                },
                required,
            ) {}
            seen = true
        }
        runCurrent()

        advanceTimeBy(required - 1)
        runCurrent()
        assertFalse(seen)

        // ホームへ抜けた（画面が目の前から消えた）。ここから先は何時間経っても積まれない。
        owner.registry.currentState = Lifecycle.State.CREATED
        advanceTimeBy(required * 10)
        runCurrent()
        assertFalse("バックグラウンドで尺が溶け、見られないまま焼かれた", seen)

        // 戻ってきたら 0 から出し直し、そこで出し切って初めて消費する。
        owner.registry.currentState = Lifecycle.State.RESUMED
        advanceTimeBy(required - 1)
        runCurrent()
        assertFalse("復帰後もやり直しは 0 から", seen)
        advanceTimeBy(1)
        runCurrent()
        assertTrue("前面に戻って出し切れば消費できる", seen)
        job.join()
    }
}
