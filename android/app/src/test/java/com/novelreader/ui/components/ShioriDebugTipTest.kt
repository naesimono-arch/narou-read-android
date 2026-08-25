package com.novelreader.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 栞先端 tip の固定（debug 限定の観察器＝[ShioriDebugTip]）の契約。
 *
 * 守りたいのは2点。①**指定した番号が生成結果へ実際に載る／解除で書影ごとの値へ戻る**（観察器としての本体）。
 * ②**release へ漏れない**——ここは BuildConfig を直読みできない（JVM テストは debug の値しか見ない）ため、
 * 解決の純関数 [shioriDebugTipFor] に isDebug を引数で与えて release 側を固定する（ADR 0027 決定4 と同じ手）。
 */
class ShioriDebugTipTest {

    @After
    fun tearDown() {
        // プロセス内 global なので、他テストへ固定状態を持ち越さない。
        ShioriDebugTip.set(null)
    }

    @Test
    fun `release では固定できない（isDebug=false は常に null）`() {
        assertNull(shioriDebugTipFor(isDebug = false, storedIndex = 3))
        assertNull(shioriDebugTipFor(isDebug = false, storedIndex = 0))
        // debug では保存値が生きる（この対比が「ガードが本当に効いている」ことの証拠）。
        assertEquals(3, shioriDebugTipFor(isDebug = true, storedIndex = 3))
    }

    @Test
    fun `保存値の正規化＝範囲外とキー不在(-1)は固定しない`() {
        assertNull(sanitizeShioriDebugTip(null))
        assertNull(sanitizeShioriDebugTip(-1))
        assertNull(sanitizeShioriDebugTip(SHIORI_TIP_COUNT))
        assertEquals(0, sanitizeShioriDebugTip(0))
        assertEquals(SHIORI_TIP_COUNT - 1, sanitizeShioriDebugTip(SHIORI_TIP_COUNT - 1))
    }

    @Test
    fun `ステッパーは解除を含めて巡回する（行き止まりを作らない）`() {
        assertEquals(0, shioriDebugTipStep(null, 1)) // 解除 → tip 0
        assertEquals(1, shioriDebugTipStep(0, 1))
        assertNull(shioriDebugTipStep(SHIORI_TIP_COUNT - 1, 1)) // 末尾 → 解除
        assertEquals(SHIORI_TIP_COUNT - 1, shioriDebugTipStep(null, -1)) // 解除 ← 末尾へ巻き戻る
        assertNull(shioriDebugTipStep(0, -1)) // tip 0 → 解除
        assertEquals(9, shioriDebugTipStep(null, 10)) // ±10 は「解除」1つぶんを含んで飛ぶ
        assertEquals(10, shioriDebugTipStep(0, 10))
    }

    @Test
    fun `固定した番号が生成結果の tipIndex になる（永続値より強い）`() {
        val title = "星降る夜のパン屋と魔法使い"
        val natural = shioriParams(title, SHIORI_TIP_COUNT)
        val persisted = shioriParams(title, SHIORI_TIP_COUNT, persistedTipIndex = 42)

        // 観察器の固定は、書影由来の値にも取込時の永続値にも勝つ（＝棚の全冊が同じ先端になる）。
        for (fixed in 0..8) {
            assertEquals(fixed, shioriParams(title, SHIORI_TIP_COUNT, debugFixedTipIndex = fixed).tipIndex)
            assertEquals(
                fixed,
                shioriParams(title, SHIORI_TIP_COUNT, persistedTipIndex = 42, debugFixedTipIndex = fixed).tipIndex,
            )
        }
        // 対象外パラメータは1ビットも動かない（rng 消費順序を崩していない証拠）。
        val fixedParams = shioriParams(title, SHIORI_TIP_COUNT, debugFixedTipIndex = 5)
        assertEquals(natural.hue, fixedParams.hue)
        assertEquals(natural.xFrac, fixedParams.xFrac, 0f)
        assertEquals(natural.lenFrac, fixedParams.lenFrac, 0f)

        // 解除（null）＝従来の書影依存へ完全復帰（永続値ありも同様）。
        assertEquals(natural, shioriParams(title, SHIORI_TIP_COUNT, debugFixedTipIndex = null))
        assertEquals(42, shioriParams(title, SHIORI_TIP_COUNT, persistedTipIndex = 42, debugFixedTipIndex = null).tipIndex)
        assertEquals(persisted, shioriParams(title, SHIORI_TIP_COUNT, persistedTipIndex = 42, debugFixedTipIndex = null))
    }

    @Test
    fun `set-解除でプロセス内の状態が往復する`() {
        assertNull(ShioriDebugTip.fixedIndex)
        ShioriDebugTip.set(7)
        assertEquals(7, ShioriDebugTip.fixedIndex)
        ShioriDebugTip.set(SHIORI_TIP_COUNT) // 範囲外は解除扱い（壊れた保存値で固定され続けない）
        assertNull(ShioriDebugTip.fixedIndex)
        ShioriDebugTip.set(0)
        assertEquals(0, ShioriDebugTip.fixedIndex)
        ShioriDebugTip.set(null)
        assertNull(ShioriDebugTip.fixedIndex)
    }
}
