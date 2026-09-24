package com.novelreader.typeset.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.novelreader.typeset.CharClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun manualRotateCentersInkOnColumnCenter() {
        // 「…」はインクがベースライン際に偏る約物＝em中央合わせだと回転後に列中心から左へ寄る
        //（実機バグ 2026-07-17）。描画結果のインク重心（不透明画素の x 範囲の中央）がセル中心 xCenter に
        // 一致することをピクセルで固定する（±2px＝アンチエイリアスの揺れ幅）。
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        renderer.drawGlyph(canvas, "…", CharClass.ROTATE, 48f, 0f, 96f, bodyPaint())
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        for (y in 0 until 96) {
            for (x in 0 until 96) {
                if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        check(minX <= maxX) { "インクが描かれていること（フォント環境の前提）" }
        assertEquals(48f, (minX + maxX + 1) / 2f, 2f)
    }

    @Test
    fun uprightSingleAsciiInkStaysWithinEmCell() {
        // G-3（golden 監査 2026-08-06）の物理側: 正立で描く単独半角数字の字面が em セル
        // [yTop, yTop+cellAdvance] に収まること。寸法側は PaintFontMetricsTest が
        // 「UPRIGHT の半角1字＝em マス」を固定しており、両者が揃って初めて
        // 「前後の字と接触しない」不変条件が閉じる。
        val bmp = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
        val yTop = 48
        renderer.drawGlyph(Canvas(bmp), "3", CharClass.UPRIGHT, 48f, yTop.toFloat(), 48f, bodyPaint())
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until 144) {
            for (x in 0 until 96) {
                if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 0) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        check(minY <= maxY) { "インクが描かれていること（フォント環境の前提）" }
        // ±2px はアンチエイリアスの揺れ幅（manualRotateCentersInkOnColumnCenter と同基準）。
        assertTrue("字面の天がセル上端を越えた: $minY", minY >= yTop - 2)
        assertTrue("字面の底がセル下端を越えた: $maxY", maxY <= yTop + 48 + 2)
    }

    @Test
    fun uprightDoesNotSetVertFeature() {
        // 正立は vert を使わない。null のまま維持されること（漏れて後続に vert が乗らない担保）。
        val paint = bodyPaint()
        renderer.drawGlyph(newCanvas(), "亜", CharClass.UPRIGHT, 48f, 0f, 48f, paint)
        assertEquals(null, paint.fontFeatureSettings)
    }

    /**
     * fontFeatureSettings への代入を記録する Paint。
     *
     * なぜ絵でなく代入を見るか: Robolectric の環境フォントは日本語の vert 縦字形を持たないため、
     * 「vert を適用した」ことをピクセルで証明できない（適用してもしなくても同じ絵になる）。
     * 描画層に残る唯一の観測点が「Paint へ "vert" を渡したか」なので、そこを直接記録する。
     */
    private class RecordingPaint : Paint() {
        val featureLog = mutableListOf<String?>()

        override fun setFontFeatureSettings(settings: String?) {
            featureLog.add(settings)
            super.setFontFeatureSettings(settings)
        }
    }

    @Test
    fun uprightMacronAppliesVertFeatureAndRestoresIt() {
        // 2026-09-07 裁定 (b): ￣U+FFE3 は正立クラスのまま vert の縦字形を使う唯一の字
        //（UAX#50 Tr・PGEM10 実測で横棒 64×4 → 右端の縦棒 3×66）。vert を渡さないと横棒のまま描かれ、
        // 縦組み本文で漢数字「一」と紛らわしくなる＝UPRIGHT 分岐を useVert=false へ戻す退行を捕まえる。
        val paint = RecordingPaint().apply { textSize = 48f; isAntiAlias = true; fontFeatureSettings = "kern" }
        paint.featureLog.clear()
        renderer.drawGlyph(newCanvas(), "￣", CharClass.UPRIGHT, 48f, 0f, 48f, paint)
        assertTrue("￣ の描画で vert を適用していない: ${paint.featureLog}", paint.featureLog.contains("vert"))
        assertEquals("復帰漏れ（共有 Paint に vert が残る）", "kern", paint.fontFeatureSettings)
    }

    @Test
    fun uprightRepeatMarksDoNotApplyVertFeature() {
        // 2026-09-07 裁定 (b): ゝヽヾ は UAX#50 が U（正立）と宣言し、実測の bounds 差も最大 3px＝
        // 縦字形ではなくヒンティング差。JSONL の changed=true だけを根拠にここへ足す退行を捕まえる。
        for (ch in listOf("ゝ", "ヽ", "ヾ")) {
            val paint = RecordingPaint().apply { textSize = 48f; isAntiAlias = true }
            paint.featureLog.clear()
            renderer.drawGlyph(newCanvas(), ch, CharClass.UPRIGHT, 48f, 0f, 48f, paint)
            assertTrue("'$ch' に vert を適用してはいけない: ${paint.featureLog}", !paint.featureLog.contains("vert"))
        }
    }
}
