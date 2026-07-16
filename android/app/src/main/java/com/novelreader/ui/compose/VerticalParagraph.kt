package com.novelreader.ui.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.novelreader.typeset.CharClassifier
import com.novelreader.typeset.ParagraphLayout
import com.novelreader.typeset.render.GlyphRenderer
import com.novelreader.typeset.render.VertGlyphRenderer

/**
 * 1 段落の [ParagraphLayout]（純組版結果）を Canvas に描くだけの薄い葉（P2 描画層）。
 *
 * 意匠判断はしない: 色・書体は引数で受けるだけ（/visual-language の翻訳＝配色/トークン適用は P3 側で行う）。
 * どの字を回す・寄せるかの決定も純層が済ませ済みで、ここは座標へ描くだけ。
 *
 * @param layout 組版済みデータ（DefaultVerticalTypesetter + PaintFontMetrics の出力）。
 * @param fontSizePx 本文セルの縦送り基準 px（layout の advance と一致する値を渡す契約）。
 * @param rubyFontSizePx ルビ 1 書記素の縦送り px。
 * @param textColor 本文・ルビの色。
 * @param typeface 描画書体（既定 SERIF＝明朝系。組版時 PaintFontMetrics と一致させる）。
 * @param renderer グリフ描画方式（既定 [VertGlyphRenderer]）。
 */
@Composable
fun VerticalParagraph(
    layout: ParagraphLayout,
    fontSizePx: Float,
    rubyFontSizePx: Float,
    textColor: Color,
    modifier: Modifier = Modifier,
    typeface: Typeface = Typeface.SERIF,
    renderer: GlyphRenderer = remember { VertGlyphRenderer() },
) {
    val density = LocalDensity.current

    // なぜ remember 化するか: Canvas 再描画（スクロール・再コンポーズ）ごとに Paint を new すると
    // 可視段落分のアロケーションが走る。見た目を決める入力が変わらない限り再利用する。
    val bodyPaint = remember(textColor, fontSizePx, typeface) {
        Paint().apply {
            color = textColor.toArgb()
            textSize = fontSizePx
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }
    val rubyPaint = remember(textColor, rubyFontSizePx, typeface) {
        Paint().apply {
            color = textColor.toArgb()
            textSize = rubyFontSizePx
            this.typeface = typeface
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    val widthDp = with(density) { layout.widthPx.toDp() }
    val heightDp = with(density) { layout.heightPx.toDp() }

    // a11y: セマンティクスは意図的に付けない。読み上げ順（右→左・列内上→下）と当て字の著者読み置換
    //（RubyText.kt:132-133,238-248）は、P3 の VerticalChapterContent 配線で clearAndSetSemantics を
    // 縦書き経路へ移植する設計。葉である本 Composable に付けると段落を跨いだ読み上げ順の統制ができない。
    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        drawIntoCanvas { canvas ->
            drawParagraphLayout(canvas.nativeCanvas, layout, rubyFontSizePx, renderer, bodyPaint, rubyPaint)
        }
    }
}

/**
 * 描画本体（Compose 非依存の内部関数）。
 * なぜ抽出するか: Robolectric では captureToImage の再描画待ちが刺さり Compose 経由の draw を
 * テストから駆動できない＝「ルビが分類器つきで renderer を通る」回帰をこの関数の直接呼び出しで固定する。
 */
internal fun drawParagraphLayout(
    nc: android.graphics.Canvas,
    layout: ParagraphLayout,
    rubyFontSizePx: Float,
    renderer: GlyphRenderer,
    bodyPaint: Paint,
    rubyPaint: Paint,
) {
    // 本文: 各グリフを純層が決めた CharClass どおりに描く（cellAdvancePx=そのユニットの縦送り）。
    for (g in layout.glyphs) {
        renderer.drawGlyph(nc, g.text, g.charClass, g.x, g.y, g.advancePx, bodyPaint)
    }
    // ルビも本文と同じ分類器＋renderer 経由で描く。読みには「ー」（伸ばし棒＝要回転）や
    // 小書き仮名（要位置替え）が普通に含まれる——「ルビ＝仮名だから正立だけで足りる」は
    // 誤った前提だった（2026-07-17 実機フィードバック「ルビの伸ばし棒が横向き」の真因）。
    for (r in layout.rubies) {
        var cellTop = r.y
        for (grapheme in RubyLayoutHelper.splitGraphemes(r.text)) {
            renderer.drawGlyph(
                nc, grapheme, CharClassifier.classify(grapheme),
                r.x, cellTop, rubyFontSizePx, rubyPaint,
            )
            cellTop += rubyFontSizePx
        }
    }
}
