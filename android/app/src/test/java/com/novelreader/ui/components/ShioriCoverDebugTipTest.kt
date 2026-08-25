package com.novelreader.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 観察器（[ShioriDebugTip]）の固定が**実際に描かれるパラメータ**へ載ることの固定。
 *
 * なぜ画素でなく [rememberShioriParams] を見るか: [ShioriCover] は生成をこの1関数からしか得ておらず、
 * 「棒の位置・先端の種類」はここで確定する＝ここに載っていれば Canvas には必ず載る。画素比較にすると
 * Robolectric のフォント・描画差に判定が引きずられ、観察器の配線とは無関係な理由で赤くなる。
 *
 * 併せて**再コンポーズで届くこと**（設定画面で番号を送った瞬間に棚の書影が引き直される）も固定する。
 * remember のキーに固定 tip を入れ忘れると、値は変わったのに古い params が残る＝実機では
 * 「送っても絵が変わらない」という、観察器として致命的だが単体の純関数テストでは絶対に捕まらない欠陥になる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ShioriCoverDebugTipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        ShioriDebugTip.set(null) // プロセス内 global＝他テストへ固定を持ち越さない
    }

    private val title = "星降る夜のパン屋と魔法使い"

    @Test
    fun `固定中は永続値の本でも指定 tip が描画パラメータになる`() {
        ShioriDebugTip.set(6)
        var observed: ShioriParams? = null
        composeTestRule.setContent {
            observed = rememberShioriParams(title, persistedTipIndex = 42, persistedLenFrac = null)
        }
        composeTestRule.waitForIdle()
        assertEquals(6, observed?.tipIndex)
        // 先端だけが差し替わる（色相・棒位置・棒長は書影のまま＝観察中も棚の見え方が別物にならない）。
        val natural = shioriParams(title, SHIORI_TIP_COUNT, persistedTipIndex = 42)
        assertEquals(natural.hue, observed?.hue)
        assertEquals(natural.xFrac, observed?.xFrac)
        assertEquals(natural.lenFrac, observed?.lenFrac)
    }

    @Test
    fun `番号の送り・解除が再コンポーズで届く`() {
        val results = mutableListOf<Int>()
        composeTestRule.setContent {
            results += rememberShioriParams(title, persistedTipIndex = 42, persistedLenFrac = null).tipIndex
        }
        composeTestRule.waitForIdle()
        assertEquals(42, results.last()) // 固定なし＝取込時の永続値

        // 状態の書き換えは runOnIdle（UI スレッド）で行う＝snapshot の変更通知が確実に配られる。
        composeTestRule.runOnIdle { ShioriDebugTip.set(0) }
        composeTestRule.waitForIdle()
        assertEquals(0, results.last())

        composeTestRule.runOnIdle { ShioriDebugTip.set(8) }
        composeTestRule.waitForIdle()
        assertEquals(8, results.last())

        composeTestRule.runOnIdle { ShioriDebugTip.set(null) } // 解除＝書影ごとの値（ここでは永続値）へ戻る
        composeTestRule.waitForIdle()
        assertEquals(42, results.last())
    }
}
