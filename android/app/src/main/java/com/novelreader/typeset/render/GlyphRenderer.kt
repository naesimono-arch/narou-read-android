package com.novelreader.typeset.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.novelreader.typeset.CharClass
import com.novelreader.typeset.VertFeatureCoverage

/**
 * 縦組み 1 ユニット（書記素 or 縦中横 1 マス）を Canvas のセルへ描く境界。
 *
 * セル定義: xCenter（列中心 x）・yTop（字の天）・cellAdvancePx（このユニットの縦送り＝正方セルなら幅も同値）。
 * paint は呼び出し側が textSize/typeface/色を設定済みで渡す。
 */
interface GlyphRenderer {
    fun drawGlyph(
        canvas: Canvas,
        text: String,
        charClass: CharClass,
        xCenter: Float,
        yTop: Float,
        cellAdvancePx: Float,
        paint: Paint,
    )
}

/**
 * fontFeatureSettings="vert" を第一候補に、効かない字は Canvas 回転で補う縦組み描画実装。
 *
 * 「どの字を回すか・寄せるかは純層（CharClass / VertFeatureCoverage）が決定・ここは実行のみ」。
 * vert の効きは書体依存で不安定（P0-1 実測で「‥」が default/serif で書体割れ）だが、その判断は
 * すべて純組版層に集約済み＝この描画層は CharClass に従って座標変換を実行するだけにする分業。
 *
 * paint は VerticalParagraph と共有されうるため、fontFeatureSettings / textScaleX / textAlign を
 * 一時変更したら finally で必ず元へ戻す（状態リーク防止）。例外を握り潰す try/catch は置かない
 * （復帰の finally のみ）。
 */
class VertGlyphRenderer : GlyphRenderer {

    override fun drawGlyph(
        canvas: Canvas,
        text: String,
        charClass: CharClass,
        xCenter: Float,
        yTop: Float,
        cellAdvancePx: Float,
        paint: Paint,
    ) {
        when (charClass) {
            // 正立: vert を使わずそのまま縦に置く（漢字・仮名・全角英数・約物「？！・」など）。
            CharClass.UPRIGHT ->
                drawCentered(canvas, text, xCenter, yTop, cellAdvancePx, paint, useVert = false)

            // 位置替え: 句読点・小書き仮名は vert で右上寄せ等の縦字形が出る（P0-1 実測で有効）。
            CharClass.PUNCT_REPOSITION ->
                drawCentered(canvas, text, xCenter, yTop, cellAdvancePx, paint, useVert = true)

            // 回転: vert が回してくれる括弧・長音・波などは vert に任せ、vert が効かない実測字
            //（…‥；−）と半角 ASCII 1 字（vert は欧文回転を保証しない）は自前 90 度回転で補う。
            CharClass.ROTATE ->
                if (isManualRotate(text)) {
                    drawManualRotate(canvas, text, xCenter, yTop, cellAdvancePx, paint)
                } else {
                    drawCentered(canvas, text, xCenter, yTop, cellAdvancePx, paint, useVert = true)
                }

            // 縦中横: 横並びの小組みをセル幅に収める（超過分は textScaleX で横圧縮）。
            CharClass.TATE_CHU_YOKO ->
                drawTateChuYoko(canvas, text, xCenter, yTop, cellAdvancePx, paint)
        }
    }

    /** MANUAL_ROTATE_REQUIRED の実測字、または半角 ASCII 1 字は自前回転が必要。 */
    private fun isManualRotate(text: String): Boolean =
        text in VertFeatureCoverage.MANUAL_ROTATE_REQUIRED ||
            (text.length == 1 && isHalfWidthAscii(text[0]))

    /**
     * セル縦中央にベースラインを合わせて正立描画する（useVert 時のみ vert フィーチャを一時適用）。
     * ベースライン＝天 yTop からセル中央まで下げ、フォントメトリクスの上下中央 (ascent+descent)/2 を補正。
     */
    private fun drawCentered(
        canvas: Canvas,
        text: String,
        xCenter: Float,
        yTop: Float,
        cellAdvancePx: Float,
        paint: Paint,
        useVert: Boolean,
    ) {
        val prevAlign = paint.textAlign
        val prevFeatures = paint.fontFeatureSettings
        try {
            paint.textAlign = Paint.Align.CENTER
            if (useVert) paint.fontFeatureSettings = "vert"
            val fm = paint.fontMetrics
            val baseline = yTop + cellAdvancePx / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, xCenter, baseline, paint)
        } finally {
            paint.textAlign = prevAlign
            paint.fontFeatureSettings = prevFeatures
        }
    }

    /**
     * セル中心 (xCenter, yTop+cellAdvancePx/2) を pivot に 90 度時計回りへ回して描く。
     * 回転後は横書き＝ベースラインを pivot の縦中央に合わせ、字面がセル中央に来るようにする。
     */
    private fun drawManualRotate(
        canvas: Canvas,
        text: String,
        xCenter: Float,
        yTop: Float,
        cellAdvancePx: Float,
        paint: Paint,
    ) {
        val prevAlign = paint.textAlign
        canvas.save()
        try {
            paint.textAlign = Paint.Align.CENTER
            val cy = yTop + cellAdvancePx / 2f
            // Android は y 下向き＝正の角度が時計回り。pivot をセル中心に取り座標系ごと回す。
            canvas.rotate(90f, xCenter, cy)
            // なぜ em 中央（(ascent+descent)/2）でなくインク中央か: 自前回転の対象（…‥；―−）は
            // ベースライン際にインクが偏る約物で、em 中央合わせだと回転後に「emの下端＝左」へ
            // 字面が寄る（実機で「……」が列中心から左にずれて見えたバグの真因）。
            // getTextBounds の字面ボックス中心をセル中心へ合わせると回転後も列中心に乗る。
            val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
            val baseline = if (bounds.isEmpty) {
                cy - (paint.fontMetrics.let { (it.ascent + it.descent) / 2f }) // 空グリフ（空白等）は em 中央へ倒す防御
            } else {
                cy - bounds.exactCenterY()
            }
            canvas.drawText(text, xCenter, baseline, paint)
        } finally {
            canvas.restore()
            paint.textAlign = prevAlign
        }
    }

    /**
     * 縦中横: 横組みのままセル中央へ。横幅がセル幅（＝cellAdvancePx。縦中横セルは正方＝縦送りと同値）を
     * 超えるなら textScaleX で横方向だけ圧縮する。
     */
    private fun drawTateChuYoko(
        canvas: Canvas,
        text: String,
        xCenter: Float,
        yTop: Float,
        cellAdvancePx: Float,
        paint: Paint,
    ) {
        val prevAlign = paint.textAlign
        val prevScaleX = paint.textScaleX
        try {
            paint.textAlign = Paint.Align.CENTER
            // 縦中横 1 マスは正方セル（FontMetricsProvider が縦送り＝fontSizePx を返す契約）＝幅も cellAdvancePx。
            val width = paint.measureText(text)
            if (width > cellAdvancePx) {
                paint.textScaleX = cellAdvancePx / width
            }
            val fm = paint.fontMetrics
            val baseline = yTop + cellAdvancePx / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, xCenter, baseline, paint)
        } finally {
            paint.textAlign = prevAlign
            paint.textScaleX = prevScaleX
        }
    }
}
