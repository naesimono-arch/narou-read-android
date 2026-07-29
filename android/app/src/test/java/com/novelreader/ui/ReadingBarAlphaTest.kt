package com.novelreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [readingBarAlpha]（読書クローム上下バーの描画 alpha）の合成規則の固定。
 * 案3ライブプレビュー退避（表示設定スライダー押下中はバーも完全透明・2026-07-29 裁定）と、
 * 既存の初期実測待ち不可視化（barsVisualReady=false は常に 0）が両立することを JVM で担保する。
 * graphicsLayer 内の視覚結果そのものは semantics に出ないため、規則を純関数へ切り出してここで固定する。
 */
class ReadingBarAlphaTest {

    @Test
    fun `通常時（実測済み・非調整）は不透明`() {
        assertEquals(1f, readingBarAlpha(barsVisualReady = true, settingsPeek = 0f), 0f)
    }

    @Test
    fun `スライダー押下中（退避1）は完全透明`() {
        assertEquals(0f, readingBarAlpha(barsVisualReady = true, settingsPeek = 1f), 0f)
    }

    @Test
    fun `退避遷移中は割合の線形補間`() {
        assertEquals(0.6f, readingBarAlpha(barsVisualReady = true, settingsPeek = 0.4f), 1e-6f)
    }

    @Test
    fun `初期実測待ちは退避割合に関わらず不可視（既存挙動の維持）`() {
        assertEquals(0f, readingBarAlpha(barsVisualReady = false, settingsPeek = 0f), 0f)
        assertEquals(0f, readingBarAlpha(barsVisualReady = false, settingsPeek = 1f), 0f)
    }
}
