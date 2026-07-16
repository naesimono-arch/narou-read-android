package com.novelreader.typeset.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.novelreader.typeset.CharClass
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [VertGlyphRenderer] が共有 Paint を汚染しないことの回帰。
 *
 * なぜこのテストか: paint は VerticalParagraph と全グリフで使い回すため、vert フィーチャや
 * textScaleX を一時変更したまま戻し忘れると、後続グリフに縦字形圧縮が漏れて版面が崩れる。
 * 実描画（Bitmap への drawText）を通したうえで、描画前後で paint 状態が一致することを assert する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class VertGlyphRendererTest {

    private val renderer = VertGlyphRenderer()

    private fun newCanvas(): Canvas =
        Canvas(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888))

    private fun bodyPaint(): Paint = Paint().apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    @Test
    fun punctRepositionRestoresFontFeatureSettings() {
        val paint = bodyPaint().apply { fontFeatureSettings = "kern" }
        renderer.drawGlyph(newCanvas(), "、", CharClass.PUNCT_REPOSITION, 48f, 0f, 48f, paint)
        // vert を一時適用したあと、元の "kern" に戻っていること。
        assertEquals("kern", paint.fontFeatureSettings)
    }

    @Test
    fun rotateVertPathRestoresFontFeatureSettings() {
        // 「「」は vert に任せる ROTATE（MANUAL_ROTATE_REQUIRED ではない）＝vert パスを通る。
        val paint = bodyPaint()
        val before = paint.fontFeatureSettings
        renderer.drawGlyph(newCanvas(), "「", CharClass.ROTATE, 48f, 0f, 48f, paint)
        assertEquals(before, paint.fontFeatureSettings)
    }

    @Test
    fun manualRotateLeavesTextScaleXAndAlignUntouched() {
        // 「…」は MANUAL_ROTATE_REQUIRED＝自前回転パス。textScaleX/textAlign を汚さないこと。
        val paint = bodyPaint().apply { textScaleX = 1f }
        renderer.drawGlyph(newCanvas(), "…", CharClass.ROTATE, 48f, 0f, 48f, paint)
        assertEquals(1f, paint.textScaleX, 0.0001f)
        assertEquals(Paint.Align.CENTER, paint.textAlign)
    }

    @Test
    fun tateChuYokoRestoresTextScaleX() {
        // "12" はセル幅を超えるため textScaleX で圧縮されるが、描画後は元の 1.0 へ戻ること。
        val paint = bodyPaint().apply { textScaleX = 1f }
        renderer.drawGlyph(newCanvas(), "12", CharClass.TATE_CHU_YOKO, 24f, 0f, 48f, paint)
        assertEquals(1f, paint.textScaleX, 0.0001f)
    }

    @Test
    fun uprightDoesNotSetVertFeature() {
        // 正立は vert を使わない。null のまま維持されること（漏れて後続に vert が乗らない担保）。
        val paint = bodyPaint()
        renderer.drawGlyph(newCanvas(), "亜", CharClass.UPRIGHT, 48f, 0f, 48f, paint)
        assertEquals(null, paint.fontFeatureSettings)
    }
}
