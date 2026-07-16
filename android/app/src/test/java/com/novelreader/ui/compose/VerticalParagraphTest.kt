package com.novelreader.ui.compose

import android.graphics.Canvas
import android.graphics.Paint
import com.novelreader.model.TextSegment
import com.novelreader.typeset.CharClass
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.FakeMonospaceMetrics
import com.novelreader.typeset.TypesetConstraints
import com.novelreader.typeset.render.GlyphRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ルビの書記素が本文と同じ分類器＋GlyphRenderer 経由で描かれることの回帰。
 *
 * なぜこのテストか: 「ルビ＝仮名だから正立だけで足りる」という誤前提で素の drawText をしていた
 * 時期があり、読みに含まれる「ー」が実機で横向きのままになった（2026-07-17 実機フィードバック）。
 * ー の縦字形は vert フィーチャ由来＝Robolectric では no-op で golden に写らないため、
 * 「renderer に正しい CharClass で届くこと」を drawParagraphLayout（Compose 非依存の描画本体）の
 * 直接呼び出し＋記録フェイクで固定する（captureToImage は Robolectric で再描画待ちが刺さり使えない）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerticalParagraphTest {

    private class RecordingRenderer : GlyphRenderer {
        val calls = mutableListOf<Pair<String, CharClass>>()
        override fun drawGlyph(
            canvas: Canvas,
            text: String,
            charClass: CharClass,
            xCenter: Float,
            yTop: Float,
            cellAdvancePx: Float,
            paint: Paint,
        ) {
            calls += text to charClass
        }
    }

    @Test
    fun rubyGraphemesAreRoutedThroughRendererWithClassification() {
        val layout = DefaultVerticalTypesetter(FakeMonospaceMetrics()).typeset(
            listOf(TextSegment.Ruby("剣", "そーど")),
            TypesetConstraints(
                columnHeightPx = 200f,
                fontSizePx = 20f,
                rubyFontSizePx = 10f,
                columnAdvancePx = 40f,
                indentFirstColumn = false,
            ),
        )
        val recorder = RecordingRenderer()
        drawParagraphLayout(
            nc = Canvas(),
            layout = layout,
            rubyFontSizePx = 10f,
            renderer = recorder,
            bodyPaint = Paint(),
            rubyPaint = Paint(),
        )

        // 本文「剣」とルビ「そ」「ー」「ど」の全書記素が renderer を通ること。
        val texts = recorder.calls.map { it.first }
        assertTrue("ルビ書記素が renderer に届く: $texts", texts.containsAll(listOf("剣", "そ", "ー", "ど")))
        // 肝: ルビの「ー」が ROTATE として届く（素の drawText だと横向きのままになる回帰の芯）。
        assertEquals(
            CharClass.ROTATE,
            recorder.calls.first { it.first == "ー" }.second,
        )
    }
}
