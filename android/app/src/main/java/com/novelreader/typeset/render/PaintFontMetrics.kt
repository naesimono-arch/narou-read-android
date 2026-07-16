package com.novelreader.typeset.render

import android.graphics.Paint
import android.graphics.Typeface
import com.novelreader.typeset.FontMetricsProvider

/**
 * 半角 ASCII（可視域 0x20〜0x7E）の 1 文字か。
 *
 * なぜ 0x20〜0x7E か: 縦組みで欧文横倒し（回転）や縦中横の対象になるのは半角の英数字・約物で、
 * これらは可視 ASCII 域に収まる。全角（U+FF01 以降）や仮名・漢字は含めない（正立扱い）。
 * PaintFontMetrics（縦送りの寸法）と VertGlyphRenderer（回転判定）が同じ定義を共有する。
 */
internal fun isHalfWidthAscii(c: Char): Boolean = c.code in 0x20..0x7E

/**
 * [FontMetricsProvider] の Android [Paint] 実装（P2 描画層）。
 *
 * 縦送りの寸法源。純組版層（LineBreaker / RubyPlacer）はこの境界越しに実測 advance を受け取り、
 * 等幅前提を置かない（P0-1 実測で serif の小書き仮名 advance が 64→65px に割れた根拠）。
 *
 * @param typeface 計測に使う書体（既定 SERIF＝明朝系。描画側 VerticalParagraph と一致させる契約）。
 */
class PaintFontMetrics(typeface: Typeface = Typeface.SERIF) : FontMetricsProvider {

    // なぜインスタンスで Paint を 1 つ使い回すか: verticalAdvance/horizontalAdvance は
    // 組版 1 回で 1 段落の全書記素分（最長 1,518 字＝P0-2）呼ばれる。都度 new すると
    // 計測のたびに Paint アロケーションが走るため、1 個を保持し textSize だけ都度設定する。
    private val paint = Paint().apply {
        this.typeface = typeface
        isAntiAlias = true
    }

    override fun verticalAdvance(unitText: String, fontSizePx: Float): Float {
        if (unitText.isEmpty()) return fontSizePx
        // 単一の半角 ASCII 文字: 90 度回転して置くため、横幅（measureText）がそのまま縦の占有になる。
        if (unitText.length == 1 && isHalfWidthAscii(unitText[0])) {
            paint.textSize = fontSizePx
            return paint.measureText(unitText)
        }
        // 半角のみからなる複数文字（縦中横 run）: 1 マス（正方セル）に収める契約＝縦送りは fontSizePx。
        if (unitText.length > 1 && unitText.all { isHalfWidthAscii(it) }) {
            return fontSizePx
        }
        // それ以外（全角・書記素）: 全角の縦送り＝em マス。P2 は em 送りで開始し、実測が必要になったら
        // P6 版面較正で precise 化する（フォント縦メトリクスからの実 advance 化はここに差し込む）。
        return fontSizePx
    }

    override fun horizontalAdvance(text: String, fontSizePx: Float): Float {
        paint.textSize = fontSizePx
        return paint.measureText(text)
    }
}
