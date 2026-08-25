package com.novelreader.ui.intro

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.ImmersiveChromeHintEffect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **今回いちばん壊しやすい 1 点**を固定する（正本モック tutorial-onboarding-K.html §8 の★）。
 *
 * 何が壊れるか: 教示の組B 3 枚目「メニューを出す　本文のどこでもタップ。」は、既存の没入ヒント（ピル）の
 * 「画面をタップでメニュー」と**同じことを、同じ瞬間に**言う。組B は本文初回、ピルは入場時の自動初期退避で
 * 発火するので、本文を初めて開いた 1 回で両方が成立してしまう。
 *
 * なぜ prefs へ書くだけでは防げないか: ChapterScreen の `chromeHintConsumed` は
 * `remember { mutableStateOf(prefs.getBoolean(…)) }` で**入場時に 1 度読むだけ**。同一セッションで
 * prefs を書いても state は false のまま効果が走り続け、**カードを閉じた直後にピルが出てくる**。
 * この 2 本目のテストが、その失敗モードを実際に再現して見せている（＝この検知器が死んでいないことの証拠）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntroPillDoubleShowTest {

    @get:Rule
    val rule = createComposeRule()

    /** クロームが退避したまま＝ピルが出る前提（collapsedFraction = heightOffset / limit = 1.0）。 */
    private fun collapsedBarState() = TopAppBarState(-100f, -100f, 0f)

    @Test
    fun `教示の組B を出したら、閉じた直後もピルは出ない（現行の結線）`() {
        val store = FakeIntroFlagStore()
        val controller = IntroController(store)
        // 入場時に prefs から読んだ値。ChapterScreen の chromeHintConsumed 初期値そのもの（未消費）。
        val persistedAtEntry = false
        var pillVisible = false

        rule.setContent {
            val barState = remember { collapsedBarState() }
            ImmersiveChromeHintEffect(
                topAppBarState = barState,
                barsVisualReady = true,
                deferHeavyContent = false,
                // ChapterScreen と同じ合成（永続フラグ ∨ 同一セッションの黙らせ）。
                consumed = immersiveHintConsumed(persistedAtEntry, controller.chromeHintSilenced),
                onVisibleChange = { pillVisible = it },
                onConsumed = {},
            )
        }
        rule.waitForIdle()
        assertTrue("前提が崩れている: 教示が無ければピルは出るはず", pillVisible)

        // 本文へ着いて組B が出た。
        controller.requestAuto(IntroGroup.READING)
        rule.waitForIdle()
        assertFalse("カードが出ている間にピルが残っている", pillVisible)

        // 3 枚目 → 4 枚目 → 閉じる。ここが従来ピルが顔を出していた瞬間。
        controller.next()
        controller.dismiss()
        rule.waitForIdle()
        assertFalse("カードを閉じた直後にピルが出た（二重表示）", pillVisible)
    }

    @Test
    fun `prefs へ書くだけの結線ではカードを閉じた直後にピルが出る（この検知器が生きている証拠）`() {
        val store = FakeIntroFlagStore()
        val controller = IntroController(store)
        val persistedAtEntry = false // 入場時に 1 度読んだきり＝以後 prefs を書いても変わらない
        var pillVisible = false

        rule.setContent {
            val barState = remember { collapsedBarState() }
            ImmersiveChromeHintEffect(
                topAppBarState = barState,
                barsVisualReady = true,
                deferHeavyContent = false,
                // ⚠️ わざと旧来の結線（永続フラグだけ）にする。実装はこちらに戻してはならない。
                consumed = persistedAtEntry,
                onVisibleChange = { pillVisible = it },
                onConsumed = {},
            )
        }
        rule.waitForIdle()

        controller.requestAuto(IntroGroup.READING)
        controller.next()
        controller.dismiss()
        rule.waitForIdle()

        assertTrue("prefs は書かれている（が、それだけでは効かない）", store.immersiveHintShown)
        assertTrue("旧結線ならカードを閉じた直後にピルが出たまま＝これが塞いだ穴", pillVisible)
    }
}
