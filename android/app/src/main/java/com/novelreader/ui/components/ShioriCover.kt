package com.novelreader.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.novelreader.typeset.CharClassifier
import com.novelreader.typeset.render.VertGlyphRenderer
import com.novelreader.ui.theme.BackgroundSepia
import com.novelreader.ui.theme.ShioriCoverInkDark
import com.novelreader.ui.theme.ShioriCoverPaperDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================
// ShioriCover — 「栞」書影の Compose 描画（意匠正本 bookshelf-shiori-final-D.html の翻訳）。
//
// 紙地に「天から引いた色の細棒＋その先端のワンポイント意匠（174種から title 種で1つ）」を置く。
// 決定論パラメータ（色相・x・長さ・先端）は ShioriGenerator（純ロジック・テスト済み）が算出。
// 本ファイルはその描画のみ＝棒=drawLine／先端=drawPath/drawArc/drawCircle／題字=nativeCanvas。
//
// 先端は「配列に1つ足すだけ」で拡張できる（正本の TIPS 設計を踏襲）＝オーナー要望「都度増やす」を
// 構造で担保。先端を足すと選択分布が変わるが「同じ本＝同じ絵」は保たれる（tipCount 依存の決定論）。
// ============================================================

// 先端意匠の描画。(x=棒先端のx, y=棒の長さ位置, accent=識別色, paper=紙色〔削り出し用〕)。
// paper は半月・勾玉が"欠け"を作るのに使う（正本と同じ）。
private typealias ShioriTip = DrawScope.(x: Float, y: Float, accent: Color, paper: Color) -> Unit

private fun strokeR(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
private fun deg(rad: Double): Float = Math.toDegrees(rad).toFloat()

private fun starPath(cx: Float, cy: Float, rO: Float, rI: Float, n: Int, rotRad: Double): Path {
    val p = Path()
    for (i in 0 until n * 2) {
        val r = if (i % 2 == 1) rI else rO
        val ang = rotRad + i * PI / n
        val px = cx + (r * cos(ang)).toFloat()
        val py = cy + (r * sin(ang)).toFloat()
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    return p
}

/**
 * 先端ワンポイント174種（正本 TIPS＋増補バッチの faithful 移植）。ここに1つ足すだけで拡張できる。
 * 既存31種＝結び房系[魚尾/一粒/結び玉/二又房/三又房/総角/蝶結び/玉と尾/数珠/括り]／
 * 輪幾何系[小輪/二重丸/菱/逆三角/小四角/星/十字/三点/雫/半月/矢尻]／
 * 和意匠系[巴/勾玉/鈴/瓢箪/短冊/蔵書印/梅/木の葉/木の実/分銅]。
 * 増補143種＝結び房・輪幾何・和意匠の追補＋文様系／植物天体系／陰陽・呪術系／武具系／
 * 家紋系／鳥獣系／花・季系／文・書斎系（index 31〜173＝各 entries.js の正順）。
 */
internal val SHIORI_TIPS: List<ShioriTip> = listOf(
    // 0 魚尾
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x - 6f, y + 12f), 2.6f, StrokeCap.Round)
        drawLine(a, Offset(x, y), Offset(x + 6f, y + 12f), 2.6f, StrokeCap.Round)
    },
    // 1 一粒
    { x, y, a, _ -> drawCircle(a, 4f, Offset(x, y + 7f)) },
    // 2 結び玉
    { x, y, a, _ -> drawCircle(a, 6f, Offset(x, y + 6f)) },
    // 3 二又房
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x - 5f, y + 18f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x, y), Offset(x + 5f, y + 18f), 2.4f, StrokeCap.Round)
    },
    // 4 三又房
    { x, y, a, _ ->
        drawCircle(a, 3.2f, Offset(x, y + 3f))
        drawLine(a, Offset(x, y + 5f), Offset(x - 6f, y + 22f), 2f, StrokeCap.Round)
        drawLine(a, Offset(x, y + 5f), Offset(x, y + 23f), 2f, StrokeCap.Round)
        drawLine(a, Offset(x, y + 5f), Offset(x + 6f, y + 22f), 2f, StrokeCap.Round)
    },
    // 5 総角
    { x, y, a, _ ->
        drawCircle(a, 4f, Offset(x, y + 4f))
        for (k in -2..2) drawLine(a, Offset(x, y + 7f), Offset(x + k * 4f, y + 24f), 1.6f, StrokeCap.Round)
    },
    // 6 蝶結び
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x, y + 4f); quadraticTo(x - 11f, y - 2f, x - 9f, y + 4f); quadraticTo(x - 11f, y + 10f, x, y + 4f)
            moveTo(x, y + 4f); quadraticTo(x + 11f, y - 2f, x + 9f, y + 4f); quadraticTo(x + 11f, y + 10f, x, y + 4f)
        }
        drawPath(p, a, style = strokeR(1.8f))
        drawCircle(a, 2f, Offset(x, y + 4f))
    },
    // 7 玉と尾
    { x, y, a, _ ->
        drawCircle(a, 4f, Offset(x, y + 5f))
        drawLine(a, Offset(x, y + 9f), Offset(x, y + 20f), 2f, StrokeCap.Round)
    },
    // 8 数珠
    { x, y, a, _ -> for (d in intArrayOf(6, 13, 20)) drawCircle(a, 2.6f, Offset(x, y + d)) },
    // 9 括り
    { x, y, a, _ -> drawLine(a, Offset(x - 7f, y + 1f), Offset(x + 7f, y + 1f), 3f, StrokeCap.Round) },
    // 10 小輪
    { x, y, a, _ -> drawCircle(a, 5f, Offset(x, y + 7f), style = strokeR(2f)) },
    // 11 二重丸
    { x, y, a, _ ->
        drawCircle(a, 8f, Offset(x, y + 9f), style = strokeR(1.6f))
        drawCircle(a, 4f, Offset(x, y + 9f), style = strokeR(1.6f))
    },
    // 12 菱
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x, y + 1f); lineTo(x + 6f, y + 9f); lineTo(x, y + 17f); lineTo(x - 6f, y + 9f); close() }
        drawPath(p, a, style = strokeR(1.8f))
    },
    // 13 逆三角
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x - 7f, y + 1f); lineTo(x + 7f, y + 1f); lineTo(x, y + 13f); close() }
        drawPath(p, a, style = strokeR(1.8f))
    },
    // 14 小四角
    { x, y, a, _ -> drawRect(a, Offset(x - 6f, y + 2f), Size(12f, 12f), style = strokeR(1.8f)) },
    // 15 星
    { x, y, a, _ -> drawPath(starPath(x, y + 10f, 7f, 3f, 5, -PI / 2), a, style = strokeR(1.6f)) },
    // 16 十字
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 2f), Offset(x, y + 16f), 2f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 9f), Offset(x + 7f, y + 9f), 2f, StrokeCap.Round)
    },
    // 17 三点
    { x, y, a, _ ->
        for (o in listOf(Offset(0f, 5f), Offset(-5f, 13f), Offset(5f, 13f))) drawCircle(a, 2.2f, Offset(x + o.x, y + o.y))
    },
    // 18 雫
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x, y); quadraticTo(x + 6f, y + 8f, x, y + 15f); quadraticTo(x - 6f, y + 8f, x, y) }
        drawPath(p, a)
    },
    // 19 半月
    { x, y, a, paper ->
        drawCircle(a, 7f, Offset(x, y + 9f))
        drawCircle(paper, 7f, Offset(x + 3f, y + 7f))
    },
    // 20 矢尻
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x - 6f, y + 1f); lineTo(x + 6f, y + 1f); lineTo(x, y + 11f); close() }
        drawPath(p, a)
    },
    // 21 巴
    { x, y, a, _ ->
        drawArc(a, deg(-0.4), deg(PI * 1.4 + 0.4), false, Offset(x - 6f, y + 3f), Size(12f, 12f), style = strokeR(1.8f))
        drawCircle(a, 2.4f, Offset(x + 4f, y + 5f))
    },
    // 22 勾玉
    { x, y, a, paper ->
        val p = Path().apply {
            arcTo(Rect(Offset(x - 6f, y + 2f), Size(12f, 12f)), deg(-0.2), deg(PI * 1.5 + 0.2), true)
            quadraticTo(x - 3f, y + 13f, x + 1f, y + 10f); close()
        }
        drawPath(p, a)
        drawCircle(paper, 1.6f, Offset(x + 2f, y + 5f))
    },
    // 23 鈴
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x - 6f, y + 11f); quadraticTo(x - 6f, y + 1f, x, y + 1f); quadraticTo(x + 6f, y + 1f, x + 6f, y + 11f); close()
        }
        drawPath(p, a, style = strokeR(1.6f))
        drawCircle(a, 1.8f, Offset(x, y + 14f))
    },
    // 24 瓢箪
    { x, y, a, _ ->
        drawCircle(a, 5f, Offset(x, y + 13f), style = strokeR(1.6f))
        drawCircle(a, 3.2f, Offset(x, y + 5f), style = strokeR(1.6f))
    },
    // 25 短冊
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 4f), 1.4f, StrokeCap.Round)
        drawRect(a, Offset(x - 4f, y + 4f), Size(8f, 16f), style = strokeR(1.4f))
        drawCircle(a, 1f, Offset(x, y + 7f), style = strokeR(1.4f))
    },
    // 26 蔵書印
    { x, y, a, _ ->
        rotate(deg(0.06), pivot = Offset(x, y + 9f)) {
            drawRect(a, Offset(x - 7f, y + 2f), Size(14f, 14f), style = strokeR(1.4f))
            drawCircle(a, 1.8f, Offset(x, y + 9f))
        }
    },
    // 27 梅
    { x, y, a, _ ->
        for (k in 0..4) {
            val an = -PI / 2 + k * 2 * PI / 5
            drawCircle(a, 3f, Offset(x + (6 * cos(an)).toFloat(), y + 9f + (6 * sin(an)).toFloat()), style = strokeR(1.4f))
        }
        drawCircle(a, 1.8f, Offset(x, y + 9f))
    },
    // 28 木の葉
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x, y); quadraticTo(x + 6f, y + 8f, x, y + 16f); quadraticTo(x - 6f, y + 8f, x, y) }
        drawPath(p, a, style = strokeR(1.5f))
        drawLine(a, Offset(x, y + 2f), Offset(x, y + 14f), 1.5f, StrokeCap.Round)
    },
    // 29 木の実
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 5f), 1.4f, StrokeCap.Round)
        drawCircle(a, 4f, Offset(x, y + 9f))
    },
    // 30 分銅
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x - 6f, y + 2f); quadraticTo(x, y + 7f, x + 6f, y + 2f); quadraticTo(x + 1f, y + 9f, x + 6f, y + 16f)
            quadraticTo(x, y + 11f, x - 6f, y + 16f); quadraticTo(x - 1f, y + 9f, x - 6f, y + 2f); close()
        }
        drawPath(p, a, style = strokeR(1.6f))
    },
    // 31 淡路結び
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x, y + 2f); quadraticTo(x - 9f, y + 3f, x - 5f, y + 10f); quadraticTo(x - 2f, y + 14f, x + 2f, y + 11f)
            moveTo(x, y + 2f); quadraticTo(x + 9f, y + 3f, x + 5f, y + 10f); quadraticTo(x + 2f, y + 14f, x - 2f, y + 11f)
        }
        drawPath(p, a, style = strokeR(1.8f))
        drawLine(a, Offset(x - 2f, y + 12f), Offset(x - 3f, y + 20f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x + 2f, y + 12f), Offset(x + 3f, y + 20f), 1.8f, StrokeCap.Round)
    },
    // 32 片流し房
    { x, y, a, _ ->
        drawCircle(a, 3f, Offset(x, y + 3f))
        drawLine(a, Offset(x, y + 5f), Offset(x - 7f, y + 21f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x, y + 5f), Offset(x - 2f, y + 23f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x, y + 5f), Offset(x + 3f, y + 20f), 1.8f, StrokeCap.Round)
    },
    // 33 撚り房
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x, y); quadraticTo(x + 5f, y + 6f, x, y + 12f); quadraticTo(x - 5f, y + 18f, x, y + 23f) }
        drawPath(p, a, style = strokeR(2.2f))
    },
    // 34 二重括り
    { x, y, a, _ ->
        drawLine(a, Offset(x - 6f, y + 3f), Offset(x + 6f, y + 3f), 2.6f, StrokeCap.Round)
        drawLine(a, Offset(x - 6f, y + 9f), Offset(x + 6f, y + 9f), 2.6f, StrokeCap.Round)
    },
    // 35 巻き結び
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 22f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 4f, y + 6f), Offset(x + 4f, y + 9f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 4f, y + 11f), Offset(x + 4f, y + 14f), 1.8f, StrokeCap.Round)
    },
    // 36 輪結び
    { x, y, a, _ ->
        drawCircle(a, 5f, Offset(x, y + 6f), style = strokeR(1.8f))
        drawLine(a, Offset(x, y + 11f), Offset(x, y + 23f), 1.8f, StrokeCap.Round)
    },
    // 37 総締め
    { x, y, a, _ ->
        drawRect(a, Offset(x - 4f, y + 2f), Size(8f, 4f))
        drawLine(a, Offset(x - 3f, y + 6f), Offset(x - 4f, y + 20f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x, y + 6f), Offset(x, y + 21f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 6f), Offset(x + 4f, y + 20f), 1.6f, StrokeCap.Round)
    },
    // 38 六角
    { x, y, a, _ ->
        val p = Path().apply {
            for (k in 0 until 6) {
                val an = -PI / 2 + k * PI / 3
                val px = x + (7 * cos(an)).toFloat(); val py = y + 9f + (7 * sin(an)).toFloat()
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(p, a, style = strokeR(1.8f))
    },
    // 39 五角
    { x, y, a, _ ->
        val p = Path().apply {
            for (k in 0 until 5) {
                val an = -PI / 2 + k * 2 * PI / 5
                val px = x + (7 * cos(an)).toFloat(); val py = y + 8f + (7 * sin(an)).toFloat()
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(p, a, style = strokeR(1.8f))
    },
    // 40 環に点
    { x, y, a, _ ->
        drawCircle(a, 7f, Offset(x, y + 9f), style = strokeR(1.8f))
        drawCircle(a, 2f, Offset(x, y + 9f))
    },
    // 41 半円
    { x, y, a, _ -> drawArc(a, deg(0.0), deg(PI), true, Offset(x - 7f, y - 3f), Size(14f, 14f)) },
    // 42 扇形
    { x, y, a, _ -> drawArc(a, deg(PI * 0.30), deg(PI * 0.40), true, Offset(x - 11f, y - 9f), Size(22f, 22f)) },
    // 43 山形
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x - 7f, y + 13f); lineTo(x, y + 2f); lineTo(x + 7f, y + 13f) }
        drawPath(p, a, style = strokeR(2f))
    },
    // 44 重ね菱
    { x, y, a, _ ->
        val outer = Path().apply { moveTo(x, y + 1f); lineTo(x + 7f, y + 9f); lineTo(x, y + 17f); lineTo(x - 7f, y + 9f); close() }
        drawPath(outer, a, style = strokeR(1.6f))
        val inner = Path().apply { moveTo(x, y + 5f); lineTo(x + 3.5f, y + 9f); lineTo(x, y + 13f); lineTo(x - 3.5f, y + 9f); close() }
        drawPath(inner, a, style = strokeR(1.6f))
    },
    // 45 交差
    { x, y, a, _ ->
        drawLine(a, Offset(x - 6f, y + 3f), Offset(x + 6f, y + 15f), 2f, StrokeCap.Round)
        drawLine(a, Offset(x + 6f, y + 3f), Offset(x - 6f, y + 15f), 2f, StrokeCap.Round)
    },
    // 46 扇
    { x, y, a, _ ->
        drawArc(a, deg(PI * 0.28), deg(PI * 0.44), true, Offset(x - 12f, y - 9f), Size(24f, 24f), style = strokeR(1.5f))
        for (t in listOf(0.36, 0.5, 0.64)) {
            drawLine(a, Offset(x, y + 3f), Offset(x + (12 * cos(PI * t)).toFloat(), y + 3f + (12 * sin(PI * t)).toFloat()), 1.5f, StrokeCap.Round)
        }
        drawCircle(a, 1.6f, Offset(x, y + 3f))
    },
    // 47 千鳥
    { x, y, a, paper ->
        val p = Path().apply {
            moveTo(x, y + 5f); quadraticTo(x - 3f, y + 1f, x - 7f, y + 5f); quadraticTo(x - 6f, y + 11f, x, y + 12f)
            quadraticTo(x + 6f, y + 11f, x + 7f, y + 5f); quadraticTo(x + 3f, y + 1f, x, y + 5f); close()
        }
        drawPath(p, a)
        drawCircle(paper, 1.4f, Offset(x, y + 7f))
    },
    // 48 富士
    { x, y, a, paper ->
        val mt = Path().apply { moveTo(x - 8f, y + 16f); lineTo(x - 3f, y + 4f); quadraticTo(x, y + 3f, x + 3f, y + 4f); lineTo(x + 8f, y + 16f); close() }
        drawPath(mt, a)
        val snow = Path().apply {
            moveTo(x - 3f, y + 4f); lineTo(x + 3f, y + 4f); lineTo(x + 2.5f, y + 7f); lineTo(x + 1.2f, y + 5f)
            lineTo(x, y + 7.5f); lineTo(x - 1.2f, y + 5f); lineTo(x - 2.5f, y + 7f); close()
        }
        drawPath(snow, paper)
    },
    // 49 鳥居
    { x, y, a, _ ->
        drawLine(a, Offset(x - 5f, y + 4f), Offset(x - 5f, y + 20f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x + 5f, y + 4f), Offset(x + 5f, y + 20f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 6f, y + 9f), Offset(x + 6f, y + 9f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 4f), Offset(x + 7f, y + 4f), 2.2f, StrokeCap.Round)
    },
    // 50 独楽
    { x, y, a, paper ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 5f), 1.6f, StrokeCap.Round)
        val body = Path().apply { moveTo(x - 6f, y + 6f); quadraticTo(x, y + 4f, x + 6f, y + 6f); lineTo(x, y + 18f); close() }
        drawPath(body, a)
        val band = Path().apply { moveTo(x - 4f, y + 9f); lineTo(x + 4f, y + 9f); lineTo(x + 2.6f, y + 12f); lineTo(x - 2.6f, y + 12f); close() }
        drawPath(band, paper)
    },
    // 51 帆掛
    { x, y, a, _ ->
        val hull = Path().apply { moveTo(x - 7f, y + 16f); quadraticTo(x, y + 22f, x + 7f, y + 16f) }
        drawPath(hull, a, style = strokeR(1.6f))
        drawLine(a, Offset(x, y + 2f), Offset(x, y + 16f), 1.6f, StrokeCap.Round)
        val sail = Path().apply { moveTo(x + 1f, y + 3f); lineTo(x + 7f, y + 14f); lineTo(x + 1f, y + 14f); close() }
        drawPath(sail, a)
    },
    // 52 徳利
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x - 2f, y + 2f); lineTo(x - 2f, y + 7f)
            quadraticTo(x - 8f, y + 9f, x - 6f, y + 15f); quadraticTo(x - 4f, y + 21f, x, y + 21f)
            quadraticTo(x + 4f, y + 21f, x + 6f, y + 15f); quadraticTo(x + 8f, y + 9f, x + 2f, y + 7f); lineTo(x + 2f, y + 2f)
            moveTo(x - 2.6f, y + 2f); lineTo(x + 2.6f, y + 2f)
        }
        drawPath(p, a, style = strokeR(1.6f))
    },
    // 53 御守
    { x, y, a, _ ->
        val body = Path().apply {
            moveTo(x - 5f, y + 8f); lineTo(x - 5f, y + 20f); lineTo(x + 5f, y + 20f); lineTo(x + 5f, y + 8f)
            quadraticTo(x + 5f, y + 5f, x, y + 5f); quadraticTo(x - 5f, y + 5f, x - 5f, y + 8f); close()
        }
        drawPath(body, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 5f, y + 9f), Offset(x + 5f, y + 9f), 1.6f, StrokeCap.Round)
        val cord = Path().apply { moveTo(x - 2f, y + 5f); quadraticTo(x, y, x + 2f, y + 5f) }
        drawPath(cord, a, style = strokeR(1.6f))
    },
    // 54 井桁
    { x, y, a, _ ->
        drawLine(a, Offset(x - 3f, y + 2f), Offset(x - 3f, y + 16f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 2f), Offset(x + 3f, y + 16f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 6f), Offset(x + 7f, y + 6f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 12f), Offset(x + 7f, y + 12f), 1.8f, StrokeCap.Round)
    },
    // 55 麻の葉
    { x, y, a, _ ->
        val cy = y + 9f; val r = 8f
        val hex = Path().apply {
            for (k in 0 until 6) {
                val an = -PI / 2 + k * PI / 3
                val px = x + (r * cos(an)).toFloat(); val py = cy + (r * sin(an)).toFloat()
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(hex, a, style = strokeR(1.4f))
        for (k in 0 until 3) {
            val an = -PI / 2 + k * PI / 3
            drawLine(a, Offset(x + (r * cos(an)).toFloat(), cy + (r * sin(an)).toFloat()), Offset(x - (r * cos(an)).toFloat(), cy - (r * sin(an)).toFloat()), 1.4f, StrokeCap.Round)
        }
    },
    // 56 矢羽根
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 22f), 1.5f, StrokeCap.Round)
        val chevrons = Path().apply {
            for (d in intArrayOf(6, 12, 18)) {
                moveTo(x - 6f, y + d); lineTo(x, y + d - 4f); lineTo(x + 6f, y + d)
            }
        }
        drawPath(chevrons, a, style = strokeR(1.5f))
    },
    // 57 立涌
    { x, y, a, _ ->
        val left = Path().apply { moveTo(x - 2f, y + 1f); quadraticTo(x - 8f, y + 7f, x - 2f, y + 12f); quadraticTo(x - 8f, y + 18f, x - 2f, y + 23f) }
        drawPath(left, a, style = strokeR(1.6f))
        val right = Path().apply { moveTo(x + 2f, y + 1f); quadraticTo(x + 8f, y + 7f, x + 2f, y + 12f); quadraticTo(x + 8f, y + 18f, x + 2f, y + 23f) }
        drawPath(right, a, style = strokeR(1.6f))
    },
    // 58 青海波
    { x, y, a, _ ->
        for (r in listOf(8f, 5.5f, 3f)) {
            drawArc(a, deg(PI), deg(PI), false, Offset(x - r, y + 13f - r), Size(2f * r, 2f * r), style = strokeR(1.5f))
        }
    },
    // 59 雷文
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x - 7f, y + 3f); lineTo(x + 7f, y + 3f); lineTo(x + 7f, y + 16f); lineTo(x - 4f, y + 16f)
            lineTo(x - 4f, y + 7f); lineTo(x + 3f, y + 7f); lineTo(x + 3f, y + 12f)
        }
        drawPath(p, a, style = strokeR(1.6f))
    },
    // 60 割菱
    { x, y, a, _ ->
        for (o in listOf(Offset(-3.5f, 5f), Offset(3.5f, 5f), Offset(-3.5f, 13f), Offset(3.5f, 13f))) {
            val cx = x + o.x; val cy = y + o.y; val r = 3.5f
            val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
            drawPath(p, a, style = strokeR(1.4f))
        }
    },
    // 61 三つ巴
    { x, y, a, _ ->
        for (k in 0 until 3) {
            rotate(deg(k * 2 * PI / 3), pivot = Offset(x, y + 11f)) {
                drawArc(a, deg(-PI / 2), deg(PI * 0.4 + PI / 2), false, Offset(x - 6f, y + 5f), Size(12f, 12f), style = strokeR(1.6f))
                drawCircle(a, 2.3f, Offset(x, y + 5f))
            }
        }
    },
    // 62 七宝
    { x, y, a, _ ->
        val cy = y + 10f; val r = 4f
        for (o in listOf(Offset(0f, -r), Offset(r, 0f), Offset(0f, r), Offset(-r, 0f))) {
            drawCircle(a, r, Offset(x + o.x, cy + o.y), style = strokeR(1.4f))
        }
    },
    // 63 双葉
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 8f), Offset(x, y + 22f), 1.6f, StrokeCap.Round)
        val left = Path().apply { moveTo(x, y + 8f); quadraticTo(x - 8f, y + 4f, x - 6f, y + 11f); quadraticTo(x - 3f, y + 12f, x, y + 8f); close() }
        drawPath(left, a)
        val right = Path().apply { moveTo(x, y + 8f); quadraticTo(x + 8f, y + 4f, x + 6f, y + 11f); quadraticTo(x + 3f, y + 12f, x, y + 8f); close() }
        drawPath(right, a)
    },
    // 64 稲穂
    { x, y, a, _ ->
        val stalk = Path().apply { moveTo(x - 1f, y + 1f); quadraticTo(x + 3f, y + 11f, x - 2f, y + 22f) }
        drawPath(stalk, a, style = strokeR(1.5f))
        for (p in listOf(Offset(1f, 7f), Offset(2.2f, 10f), Offset(2.2f, 13f), Offset(1f, 16f))) {
            drawLine(a, Offset(x + p.x, y + p.y), Offset(x + p.x + 3f, y + p.y - 2f), 1.4f, StrokeCap.Round)
        }
    },
    // 65 竹
    { x, y, a, _ ->
        drawLine(a, Offset(x - 3f, y + 2f), Offset(x - 3f, y + 22f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 2f), Offset(x + 3f, y + 22f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 3f, y + 9f), Offset(x + 3f, y + 9f), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 3f, y + 16f), Offset(x + 3f, y + 16f), 1.8f, StrokeCap.Round)
        val leaf = Path().apply { moveTo(x + 3f, y + 5f); quadraticTo(x + 8f, y + 2f, x + 7f, y + 7f); quadraticTo(x + 5f, y + 7f, x + 3f, y + 5f); close() }
        drawPath(leaf, a)
    },
    // 66 蕨
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x, y + 22f); lineTo(x, y + 10f); quadraticTo(x - 5f, y + 8f, x - 4f, y + 4f)
            quadraticTo(x - 3f, y + 1f, x, y + 2f); quadraticTo(x + 3f, y + 3f, x + 2f, y + 6f); quadraticTo(x + 1f, y + 7f, x, y + 6f)
        }
        drawPath(p, a, style = strokeR(1.8f))
    },
    // 67 唐草
    { x, y, a, _ ->
        val vine = Path().apply { moveTo(x, y + 1f); quadraticTo(x + 7f, y + 6f, x, y + 12f); quadraticTo(x - 7f, y + 18f, x, y + 23f) }
        drawPath(vine, a, style = strokeR(1.6f))
        val tendrils = Path().apply {
            moveTo(x, y + 1f); quadraticTo(x - 4f, y + 1f, x - 3f, y + 4f)
            moveTo(x, y + 23f); quadraticTo(x + 4f, y + 23f, x + 3f, y + 20f)
        }
        drawPath(tendrils, a, style = strokeR(1.6f))
    },
    // 68 北斗
    { x, y, a, _ ->
        val pts = listOf(Offset(x - 6f, y + 5f), Offset(x - 6f, y + 11f), Offset(x - 1f, y + 12f), Offset(x - 1f, y + 6f), Offset(x + 3f, y + 4f), Offset(x + 6f, y + 9f))
        val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(line, a, style = strokeR(1.2f))
        for (p in pts) drawCircle(a, 1.7f, p)
    },
    // 69 日輪
    { x, y, a, _ ->
        drawCircle(a, 4f, Offset(x, y + 10f))
        for (k in 0 until 8) {
            val an = k * PI / 4
            drawLine(a, Offset(x + (6 * cos(an)).toFloat(), y + 10f + (6 * sin(an)).toFloat()), Offset(x + (8 * cos(an)).toFloat(), y + 10f + (8 * sin(an)).toFloat()), 1.6f, StrokeCap.Round)
        }
    },
    // 70 雪華
    { x, y, a, _ ->
        for (k in 0 until 6) {
            rotate(deg(k * PI / 3), pivot = Offset(x, y + 11f)) {
                drawLine(a, Offset(x, y + 11f), Offset(x, y + 3f), 1.4f, StrokeCap.Round)
                drawLine(a, Offset(x, y + 6f), Offset(x - 2.5f, y + 4f), 1.4f, StrokeCap.Round)
                drawLine(a, Offset(x, y + 6f), Offset(x + 2.5f, y + 4f), 1.4f, StrokeCap.Round)
            }
        }
    },
    // 71 太極
    { x, y, a, paper ->
        val cy = y + 9f; val r = 7f
        drawCircle(a, r, Offset(x, cy))
        val yin = Path().apply {
            arcTo(Rect(Offset(x - r, cy - r), Size(2f * r, 2f * r)), -90f, 180f, true)
            arcTo(Rect(Offset(x - r / 2, cy), Size(r, r)), 90f, -180f, false)
            arcTo(Rect(Offset(x - r / 2, cy - r), Size(r, r)), 90f, 180f, false)
            close()
        }
        drawPath(yin, paper)
        drawCircle(paper, 1.5f, Offset(x, cy - r / 2))
        drawCircle(a, 1.5f, Offset(x, cy + r / 2))
    },
    // 72 晴明桔梗
    { x, y, a, _ ->
        val cy = y + 10f; val r = 8f
        val pts = ArrayList<Offset>(5)
        for (k in 0 until 5) {
            val an = -PI / 2 + k * 2 * PI / 5
            pts.add(Offset(x + (r * cos(an)).toFloat(), cy + (r * sin(an)).toFloat()))
        }
        val order = intArrayOf(0, 2, 4, 1, 3)
        val p = Path().apply {
            order.forEachIndexed { i, idx -> if (i == 0) moveTo(pts[idx].x, pts[idx].y) else lineTo(pts[idx].x, pts[idx].y) }
            close()
        }
        drawPath(p, a, style = strokeR(1.4f))
    },
    // 73 籠目
    { x, y, a, _ ->
        val cy = y + 10f; val r = 8f
        for (rot in listOf(-PI / 2, PI / 2)) {
            val p = Path().apply {
                for (k in 0 until 3) {
                    val an = rot + k * 2 * PI / 3
                    val px = x + (r * cos(an)).toFloat(); val py = cy + (r * sin(an)).toFloat()
                    if (k == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(p, a, style = strokeR(1.4f))
        }
    },
    // 74 九字
    { x, y, a, _ ->
        for (dx in intArrayOf(-6, -2, 2, 6)) drawLine(a, Offset(x + dx, y + 2f), Offset(x + dx, y + 20f), 1.3f, StrokeCap.Round)
        for (dy in intArrayOf(3, 7, 11, 15, 19)) drawLine(a, Offset(x - 7f, y + dy), Offset(x + 7f, y + dy), 1.3f, StrokeCap.Round)
    },
    // 75 三爻
    { x, y, a, _ ->
        drawLine(a, Offset(x - 7f, y + 4f), Offset(x - 1.5f, y + 4f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 1.5f, y + 4f), Offset(x + 7f, y + 4f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 11f), Offset(x + 7f, y + 11f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 18f), Offset(x - 1.5f, y + 18f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 1.5f, y + 18f), Offset(x + 7f, y + 18f), 2.4f, StrokeCap.Round)
    },
    // 76 方位盤
    { x, y, a, _ ->
        val cy = y + 10f; val r = 7.5f
        drawCircle(a, r, Offset(x, cy), style = strokeR(1.3f))
        drawLine(a, Offset(x, cy - r), Offset(x, cy + r), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x - r, cy), Offset(x + r, cy), 1.3f, StrokeCap.Round)
        for (o in listOf(Offset(0f, -r), Offset(r, 0f), Offset(0f, r), Offset(-r, 0f))) {
            drawCircle(a, 1.6f, Offset(x + o.x, cy + o.y))
        }
    },
    // 77 護符
    { x, y, a, _ ->
        val p = Path().apply { moveTo(x, y); lineTo(x + 4f, y + 4f); lineTo(x + 4f, y + 22f); lineTo(x - 4f, y + 22f); lineTo(x - 4f, y + 4f); close() }
        drawPath(p, a, style = strokeR(1.5f))
        for (dy in intArrayOf(8, 13, 18)) drawCircle(a, 1.4f, Offset(x, y + dy))
    },
    // 78 形代
    { x, y, a, _ ->
        drawCircle(a, 2.4f, Offset(x, y + 4f), style = strokeR(1.6f))
        drawLine(a, Offset(x - 7f, y + 9f), Offset(x + 7f, y + 9f), 1.6f, StrokeCap.Round)
        val body = Path().apply { moveTo(x - 3f, y + 7f); lineTo(x - 5f, y + 21f); lineTo(x + 5f, y + 21f); lineTo(x + 3f, y + 7f) }
        drawPath(body, a, style = strokeR(1.6f))
    },
    // 79 結界
    { x, y, a, _ ->
        drawRect(a, Offset(x - 6f, y + 4f), Size(12f, 12f), style = strokeR(1.5f))
        for (o in listOf(Offset(-6f, 4f), Offset(6f, 4f), Offset(-6f, 16f), Offset(6f, 16f))) {
            drawCircle(a, 1.8f, Offset(x + o.x, y + o.y))
        }
    },
    // 80 御幣
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 23f), 1.6f, StrokeCap.Round)
        val shide = Path().apply {
            moveTo(x, y + 3f); lineTo(x - 6f, y + 5f); lineTo(x - 2f, y + 7f); lineTo(x - 7f, y + 10f); lineTo(x - 2f, y + 12f)
            moveTo(x, y + 3f); lineTo(x + 6f, y + 5f); lineTo(x + 2f, y + 7f); lineTo(x + 7f, y + 10f); lineTo(x + 2f, y + 12f)
        }
        drawPath(shide, a, style = strokeR(1.3f))
    },
    // 81 宝珠
    { x, y, a, paper ->
        val jewel = Path().apply {
            moveTo(x, y + 2f); quadraticTo(x + 7f, y + 7f, x + 6f, y + 14f); quadraticTo(x + 6f, y + 21f, x, y + 21f)
            quadraticTo(x - 6f, y + 21f, x - 6f, y + 14f); quadraticTo(x - 7f, y + 7f, x, y + 2f); close()
        }
        drawPath(jewel, a)
        drawCircle(paper, 1.4f, Offset(x - 1.5f, y + 10f))
        drawLine(a, Offset(x - 4f, y + 23f), Offset(x + 4f, y + 23f), 1.4f, StrokeCap.Round)
    },
    // 82 宝剣
    { x, y, a, _ ->
        drawCircle(a, 2f, Offset(x, y + 2f), style = strokeR(1.6f))
        drawLine(a, Offset(x, y + 4f), Offset(x, y + 6f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x - 6f, y + 6f), Offset(x + 6f, y + 6f), 2f, StrokeCap.Round)
        val blade = Path().apply { moveTo(x - 2.2f, y + 6f); lineTo(x + 2.2f, y + 6f); lineTo(x + 2.2f, y + 16f); lineTo(x, y + 22f); lineTo(x - 2.2f, y + 16f); close() }
        drawPath(blade, a)
    },
    // 83 金剛杵
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 3f), Offset(x, y + 21f), 1.6f, StrokeCap.Round)
        drawCircle(a, 2.4f, Offset(x, y + 12f))
        val top = Path().apply {
            moveTo(x, y + 3f); lineTo(x, y)
            moveTo(x - 4f, y + 6f); quadraticTo(x - 4f, y + 1f, x, y + 2f)
            moveTo(x + 4f, y + 6f); quadraticTo(x + 4f, y + 1f, x, y + 2f)
        }
        drawPath(top, a, style = strokeR(1.6f))
        val bottom = Path().apply {
            moveTo(x, y + 21f); lineTo(x, y + 24f)
            moveTo(x - 4f, y + 18f); quadraticTo(x - 4f, y + 23f, x, y + 22f)
            moveTo(x + 4f, y + 18f); quadraticTo(x + 4f, y + 23f, x, y + 22f)
        }
        drawPath(bottom, a, style = strokeR(1.6f))
    },
    // 84 呪眼
    { x, y, a, paper ->
        val cy = y + 10f
        val eye = Path().apply { moveTo(x - 8f, cy); quadraticTo(x, cy - 5f, x + 8f, cy); quadraticTo(x, cy + 5f, x - 8f, cy); close() }
        drawPath(eye, a, style = strokeR(1.5f))
        drawCircle(a, 2.6f, Offset(x, cy))
        drawCircle(paper, 0.9f, Offset(x + 0.8f, cy - 0.8f))
    },
    // 85 亀甲
    { x, y, a, _ ->
        val cy = y + 10f; val r = 8f
        val hex = Path().apply {
            for (k in 0 until 6) {
                val an = -PI / 2 + k * PI / 3
                val px = x + (r * cos(an)).toFloat(); val py = cy + (r * sin(an)).toFloat()
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(hex, a, style = strokeR(1.4f))
        drawLine(a, Offset(x, cy), Offset(x, cy - 4f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x, cy), Offset(x - 3.5f, cy + 2.5f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x, cy), Offset(x + 3.5f, cy + 2.5f), 1.4f, StrokeCap.Round)
    },
    // 86 封印
    { x, y, a, _ ->
        val cy = y + 10f
        drawCircle(a, 8f, Offset(x, cy), style = strokeR(1.5f))
        drawCircle(a, 4.5f, Offset(x, cy), style = strokeR(1.5f))
        drawCircle(a, 1.8f, Offset(x, cy))
    },
    // 87 錫杖
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 7f), Offset(x, y + 23f), 1.6f, StrokeCap.Round)
        drawCircle(a, 4f, Offset(x, y + 4f), style = strokeR(1.4f))
        drawCircle(a, 1.6f, Offset(x - 4f, y + 3f), style = strokeR(1.4f))
        drawCircle(a, 1.6f, Offset(x + 4f, y + 3f), style = strokeR(1.4f))
    },
    // 88 太刀
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 3f), 1.6f, StrokeCap.Round)
        val blade = Path().apply { moveTo(x - 1f, y + 3f); quadraticTo(x - 4f, y + 14f, x + 2f, y + 23f); quadraticTo(x + 2f, y + 13f, x + 2f, y + 4f); close() }
        drawPath(blade, a)
    },
    // 89 懐剣
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 4f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x - 4f, y + 5f), Offset(x + 4f, y + 5f), 1.6f, StrokeCap.Round)
        val blade = Path().apply { moveTo(x - 2f, y + 6f); lineTo(x + 2f, y + 6f); lineTo(x, y + 22f); close() }
        drawPath(blade, a)
    },
    // 90 槍
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 13f), 1.8f, StrokeCap.Round)
        val head = Path().apply { moveTo(x, y + 11f); lineTo(x + 3f, y + 16f); lineTo(x, y + 24f); lineTo(x - 3f, y + 16f); close() }
        drawPath(head, a)
    },
    // 91 薙刀
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 12f), 1.8f, StrokeCap.Round)
        val blade = Path().apply { moveTo(x, y + 11f); quadraticTo(x + 8f, y + 13f, x + 6f, y + 23f); quadraticTo(x + 2f, y + 18f, x, y + 12f); close() }
        drawPath(blade, a)
    },
    // 92 鎌
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 4f), Offset(x, y + 23f), 1.8f, StrokeCap.Round)
        val blade = Path().apply { moveTo(x, y + 3f); quadraticTo(x + 8f, y + 1f, x + 7f, y + 10f); quadraticTo(x + 5f, y + 5f, x, y + 6f); close() }
        drawPath(blade, a)
    },
    // 93 鉞
    { x, y, a, _ ->
        drawLine(a, Offset(x, y), Offset(x, y + 23f), 1.8f, StrokeCap.Round)
        val head = Path().apply { moveTo(x, y + 2f); lineTo(x + 7f, y + 4f); quadraticTo(x + 8f, y + 9f, x + 6f, y + 13f); lineTo(x, y + 11f); close() }
        drawPath(head, a)
    },
    // 94 金棒
    { x, y, a, paper ->
        val club = Path().apply { moveTo(x - 2f, y + 1f); lineTo(x + 2f, y + 1f); lineTo(x + 4f, y + 22f); lineTo(x - 4f, y + 22f); close() }
        drawPath(club, a)
        for (o in listOf(Offset(0f, 6f), Offset(-2f, 12f), Offset(2f, 12f), Offset(-1.5f, 18f), Offset(1.5f, 18f))) {
            drawCircle(paper, 1f, Offset(x + o.x, y + o.y))
        }
    },
    // 95 十手
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 21f), 2f, StrokeCap.Round)
        val hook = Path().apply { moveTo(x, y + 6f); lineTo(x + 5f, y + 6f); lineTo(x + 5f, y + 2f) }
        drawPath(hook, a, style = strokeR(1.6f))
        drawCircle(a, 1.8f, Offset(x, y + 22f))
    },
    // 96 独鈷
    { x, y, a, _ ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 23f), 1.8f, StrokeCap.Round)
        drawCircle(a, 2.2f, Offset(x, y + 12f), style = strokeR(1.8f))
        val prongs = Path().apply {
            moveTo(x - 3f, y + 5f); quadraticTo(x, y + 3f, x + 3f, y + 5f)
            moveTo(x - 3f, y + 19f); quadraticTo(x, y + 21f, x + 3f, y + 19f)
        }
        drawPath(prongs, a, style = strokeR(1.5f))
    },
    // 97 手裏剣
    { x, y, a, paper ->
        drawPath(starPath(x, y + 12f, 8f, 3f, 4, -PI / 2), a)
        drawCircle(paper, 1.6f, Offset(x, y + 12f))
    },
    // 98 弓
    { x, y, a, _ ->
        val bow = Path().apply { moveTo(x, y + 1f); quadraticTo(x - 8f, y + 12f, x, y + 23f) }
        drawPath(bow, a, style = strokeR(1.8f))
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 23f), 1.3f, StrokeCap.Round)
        drawCircle(a, 1.5f, Offset(x, y + 12f))
    },
    // 99 鍔
    { x, y, a, paper ->
        drawCircle(a, 8f, Offset(x, y + 11f))
        drawRect(paper, Offset(x - 1.3f, y + 4f), Size(2.6f, 14f))
        drawRect(paper, Offset(x - 7f, y + 9.7f), Size(14f, 2.6f))
    },
    // 100 鞘
    { x, y, a, _ ->
        val body = Path().apply { moveTo(x - 3f, y + 1f); lineTo(x - 2.5f, y + 20f); quadraticTo(x, y + 24f, x + 2.5f, y + 20f); lineTo(x + 3f, y + 1f); close() }
        drawPath(body, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 3f, y + 4f), Offset(x + 3f, y + 4f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 8f), Offset(x + 6f, y + 9f), 1.6f, StrokeCap.Round)
    },
    // 101 兜
    { x, y, a, paper ->
        val dome = Path().apply { moveTo(x - 7f, y + 15f); quadraticTo(x - 7f, y + 4f, x, y + 4f); quadraticTo(x + 7f, y + 4f, x + 7f, y + 15f); close() }
        drawPath(dome, a)
        val brim = Path().apply { moveTo(x - 8f, y + 15f); lineTo(x + 8f, y + 15f); lineTo(x + 6f, y + 18f); lineTo(x - 6f, y + 18f); close() }
        drawPath(brim, a)
        drawCircle(paper, 1.4f, Offset(x, y + 7f))
    },
    // 102 鍬形
    { x, y, a, _ ->
        val horns = Path().apply {
            moveTo(x - 2f, y + 20f); quadraticTo(x - 8f, y + 14f, x - 6f, y + 2f)
            moveTo(x + 2f, y + 20f); quadraticTo(x + 8f, y + 14f, x + 6f, y + 2f)
        }
        drawPath(horns, a, style = strokeR(2f))
        drawCircle(a, 2f, Offset(x, y + 20f))
    },
    // 103 陣笠
    { x, y, a, _ ->
        val hat = Path().apply { moveTo(x, y + 3f); lineTo(x + 8f, y + 14f); quadraticTo(x, y + 16f, x - 8f, y + 14f); close() }
        drawPath(hat, a)
        drawLine(a, Offset(x, y + 3f), Offset(x, y), 1.8f, StrokeCap.Round)
        drawLine(a, Offset(x - 5f, y + 14f), Offset(x - 4f, y + 21f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 5f, y + 14f), Offset(x + 4f, y + 21f), 1.3f, StrokeCap.Round)
    },
    // 104 盾
    { x, y, a, _ ->
        val shield = Path().apply {
            moveTo(x - 7f, y + 2f); lineTo(x + 7f, y + 2f); lineTo(x + 7f, y + 12f)
            quadraticTo(x + 7f, y + 20f, x, y + 23f); quadraticTo(x - 7f, y + 20f, x - 7f, y + 12f); close()
        }
        drawPath(shield, a, style = strokeR(1.8f))
        drawLine(a, Offset(x, y + 2f), Offset(x, y + 23f), 1.8f, StrokeCap.Round)
    },
    // 105 軍配
    { x, y, a, paper ->
        drawLine(a, Offset(x, y + 1f), Offset(x, y + 8f), 2f, StrokeCap.Round)
        val fan = Path().apply {
            moveTo(x, y + 7f); quadraticTo(x - 8f, y + 9f, x - 6f, y + 18f); quadraticTo(x - 3f, y + 24f, x, y + 23f)
            quadraticTo(x + 3f, y + 24f, x + 6f, y + 18f); quadraticTo(x + 8f, y + 9f, x, y + 7f); close()
        }
        drawPath(fan, a)
        drawCircle(paper, 1.6f, Offset(x, y + 15f))
    },
    // 106 蛇の目
    { x, y, a, paper ->
        drawCircle(a, 8f, Offset(x, y + 10f))
        drawCircle(paper, 4f, Offset(x, y + 10f))
    },
    // 107 銭
    { x, y, a, paper ->
        drawCircle(a, 8f, Offset(x, y + 10f))
        drawRect(paper, Offset(x - 3f, y + 7f), Size(6f, 6f))
    },
    // 108 木瓜
    { x, y, a, _ ->
        val cy = y + 10f
        val p = Path().apply {
            moveTo(x - 4.4f, cy - 4.4f); quadraticTo(x, cy - 8.4f, x + 4.4f, cy - 4.4f); quadraticTo(x + 8.4f, cy, x + 4.4f, cy + 4.4f)
            quadraticTo(x, cy + 8.4f, x - 4.4f, cy + 4.4f); quadraticTo(x - 8.4f, cy, x - 4.4f, cy - 4.4f); close()
        }
        drawPath(p, a, style = strokeR(1.5f))
        drawCircle(a, 2.4f, Offset(x, cy), style = strokeR(1.5f))
    },
    // 109 花菱
    { x, y, a, _ ->
        val cy = y + 10f; val rx = 6.5f; val ry = 8.5f
        val dia = Path().apply { moveTo(x, cy - ry); lineTo(x + rx, cy); lineTo(x, cy + ry); lineTo(x - rx, cy); close() }
        drawPath(dia, a, style = strokeR(1.4f))
        for (e in listOf(Offset(0f, -ry * 0.8f), Offset(rx * 0.8f, 0f), Offset(0f, ry * 0.8f), Offset(-rx * 0.8f, 0f))) {
            val ex = e.x; val ey = e.y
            val petal = Path().apply {
                moveTo(x, cy)
                quadraticTo(x + ex * 0.5f - ey * 0.3f, cy + ey * 0.5f + ex * 0.3f, x + ex, cy + ey)
                quadraticTo(x + ex * 0.5f + ey * 0.3f, cy + ey * 0.5f - ex * 0.3f, x, cy)
            }
            drawPath(petal, a, style = strokeR(1.4f))
        }
    },
    // 110 州浜
    { x, y, a, _ ->
        for (p in listOf(Triple(0f, -4f, 4.5f), Triple(-5f, 3f, 4.5f), Triple(5f, 3f, 4.5f))) {
            drawCircle(a, p.third, Offset(x + p.first, y + 11f + p.second))
        }
    },
    // 111 三つ鱗
    { x, y, a, _ ->
        for (t in listOf(Offset(0f, 9f), Offset(-4.5f, 18f), Offset(4.5f, 18f))) {
            val cx = x + t.x; val by = y + t.y; val w = 4f; val h = 7f
            val p = Path().apply { moveTo(cx, by - h); lineTo(cx + w, by); lineTo(cx - w, by); close() }
            drawPath(p, a)
        }
    },
    // 112 四つ目
    { x, y, a, _ ->
        for (p in listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(-1f, 1f), Offset(1f, 1f))) {
            drawRect(a, Offset(x + p.x * 3.2f - 2.5f, y + 10f + p.y * 3.2f - 2.5f), Size(5f, 5f))
        }
    },
    // 113 違い鷹の羽
    { x, y, a, paper ->
        val cy = y + 11f
        for (rot in listOf(-0.32, 0.32)) {
            rotate(deg(rot), pivot = Offset(x, cy)) {
                val feather = Path().apply {
                    moveTo(x, cy - 9f); quadraticTo(x + 2.6f, cy - 2f, x + 1.6f, cy + 9f); quadraticTo(x, cy + 10f, x - 1.6f, cy + 9f)
                    quadraticTo(x - 2.6f, cy - 2f, x, cy - 9f); close()
                }
                drawPath(feather, a)
                drawLine(paper, Offset(x, cy - 6f), Offset(x, cy + 8f), 1f, StrokeCap.Round)
            }
        }
    },
    // 114 丸に一つ引
    { x, y, a, _ ->
        drawCircle(a, 8f, Offset(x, y + 10f), style = strokeR(1.6f))
        drawLine(a, Offset(x - 5.5f, y + 10f), Offset(x + 5.5f, y + 10f), 3f, StrokeCap.Round)
    },
    // 115 五三桐
    { x, y, a, _ ->
        val by = y + 19f
        for (b in listOf(Triple(x, 15f, 3f), Triple(x - 5.5f, 11f, 2.6f), Triple(x + 5.5f, 11f, 2.6f))) {
            val sx = b.first; val h = b.second; val w = b.third
            val p = Path().apply { moveTo(sx, by); quadraticTo(sx - w, by - h * 0.5f, sx, by - h); quadraticTo(sx + w, by - h * 0.5f, sx, by); close() }
            drawPath(p, a)
        }
    },
    // 116 橘
    { x, y, a, paper ->
        val cy = y + 13f
        drawCircle(a, 6f, Offset(x, cy))
        val left = Path().apply { moveTo(x, cy - 6f); quadraticTo(x - 6f, cy - 9f, x - 4f, cy - 13f); quadraticTo(x - 1f, cy - 10f, x, cy - 6f); close() }
        drawPath(left, a)
        val right = Path().apply { moveTo(x, cy - 6f); quadraticTo(x + 6f, cy - 9f, x + 4f, cy - 13f); quadraticTo(x + 1f, cy - 10f, x, cy - 6f); close() }
        drawPath(right, a)
        drawCircle(paper, 1.8f, Offset(x, cy))
    },
    // 117 沢瀉
    { x, y, a, _ ->
        val cy = y + 8f
        val leaf = Path().apply {
            moveTo(x, cy - 7f); quadraticTo(x + 7f, cy - 1f, x + 5f, cy + 5f); quadraticTo(x + 2f, cy + 3f, x, cy + 1f)
            quadraticTo(x - 2f, cy + 3f, x - 5f, cy + 5f); quadraticTo(x - 7f, cy - 1f, x, cy - 7f); close()
        }
        drawPath(leaf, a)
        drawLine(a, Offset(x, cy + 1f), Offset(x, cy + 14f), 1.5f, StrokeCap.Round)
    },
    // 118 片喰
    { x, y, a, _ ->
        val cy = y + 10f
        for (k in 0 until 3) {
            rotate(deg(k * 2 * PI / 3), pivot = Offset(x, cy)) {
                val leaf = Path().apply {
                    moveTo(x, cy)
                    quadraticTo(x - 1.3f, cy - 3.5f, x - 3.4f, cy - 5.2f); quadraticTo(x - 5f, cy - 6.6f, x - 2.6f, cy - 7.8f)
                    quadraticTo(x - 0.9f, cy - 8.2f, x, cy - 6.4f); quadraticTo(x + 0.9f, cy - 8.2f, x + 2.6f, cy - 7.8f)
                    quadraticTo(x + 5f, cy - 6.6f, x + 3.4f, cy - 5.2f); quadraticTo(x + 1.3f, cy - 3.5f, x, cy); close()
                }
                drawPath(leaf, a)
            }
        }
    },
    // 119 抱き茗荷
    { x, y, a, _ ->
        val by = y + 21f
        for (sgn in listOf(-1f, 1f)) {
            val outer = Path().apply { moveTo(x + sgn, by); quadraticTo(x + sgn * 7f, by - 6f, x + sgn * 5f, by - 13f); quadraticTo(x + sgn * 3f, by - 18f, x, by - 19f) }
            drawPath(outer, a, style = strokeR(1.4f))
            val inner = Path().apply { moveTo(x + sgn * 1.5f, by - 3f); quadraticTo(x + sgn * 4f, by - 8f, x + sgn * 3f, by - 13f) }
            drawPath(inner, a, style = strokeR(1.4f))
        }
    },
    // 120 桔梗紋
    { x, y, a, paper ->
        drawPath(starPath(x, y + 10f, 8f, 4.2f, 5, -PI / 2), a)
        drawCircle(paper, 2f, Offset(x, y + 10f))
    },
    // 121 松皮菱
    { x, y, a, _ ->
        for (d in listOf(Triple(y + 11f, 7f, 6f), Triple(y + 3f, 4f, 3f), Triple(y + 19f, 4f, 3f))) {
            val cy = d.first; val w = d.second; val h = d.third
            val p = Path().apply { moveTo(x, cy - h); lineTo(x + w, cy); lineTo(x, cy + h); lineTo(x - w, cy); close() }
            drawPath(p, a, style = strokeR(1.4f))
        }
    },
    // 122 蔦
    { x, y, a, _ ->
        val cy = y + 7f
        val leaf = Path().apply {
            moveTo(x, cy - 6f); quadraticTo(x + 3f, cy - 4f, x + 7f, cy - 4f); quadraticTo(x + 4f, cy, x + 7f, cy + 4f)
            quadraticTo(x + 3f, cy + 5f, x + 1f, cy + 8f); lineTo(x - 1f, cy + 8f); quadraticTo(x - 3f, cy + 5f, x - 7f, cy + 4f)
            quadraticTo(x - 4f, cy, x - 7f, cy - 4f); quadraticTo(x - 3f, cy - 4f, x, cy - 6f); close()
        }
        drawPath(leaf, a)
        drawLine(a, Offset(x, cy + 8f), Offset(x, cy + 15f), 1.5f, StrokeCap.Round)
    },
    // 123 鶴
    { x, y, a, _ ->
        val body = Path().apply {
            moveTo(x - 2f, y + 10f); quadraticTo(x + 4f, y + 8f, x + 8f, y + 12f); quadraticTo(x + 5f, y + 15f, x + 1f, y + 15f)
            quadraticTo(x - 3f, y + 15f, x - 2f, y + 10f); close()
        }
        drawPath(body, a)
        val neck = Path().apply { moveTo(x - 1f, y + 11f); quadraticTo(x - 6f, y + 9f, x - 6f, y + 3f) }
        drawPath(neck, a, style = strokeR(1.6f))
        drawLine(a, Offset(x, y + 15f), Offset(x - 1f, y + 23f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 15f), Offset(x + 3f, y + 23f), 1.6f, StrokeCap.Round)
        drawCircle(a, 1.7f, Offset(x - 6f, y + 2f))
        drawLine(a, Offset(x - 7f, y + 2f), Offset(x - 8f, y + 1f), 1.3f, StrokeCap.Round)
    },
    // 124 燕
    { x, y, a, _ ->
        val p = Path().apply {
            moveTo(x, y + 5f); quadraticTo(x - 4f, y + 6f, x - 8f, y + 4f); quadraticTo(x - 4f, y + 9f, x - 2.5f, y + 11f)
            lineTo(x - 4f, y + 22f); lineTo(x, y + 15f); lineTo(x + 4f, y + 22f); lineTo(x + 2.5f, y + 11f)
            quadraticTo(x + 4f, y + 9f, x + 8f, y + 4f); quadraticTo(x + 4f, y + 6f, x, y + 5f); close()
        }
        drawPath(p, a)
    },
    // 125 雀
    { x, y, a, paper ->
        drawCircle(a, 6f, Offset(x + 1f, y + 13f))
        drawCircle(a, 3.4f, Offset(x - 3f, y + 6f))
        val beak = Path().apply { moveTo(x - 6f, y + 5f); lineTo(x - 8f, y + 5.5f); lineTo(x - 6f, y + 7f); close() }
        drawPath(beak, a)
        val tail = Path().apply { moveTo(x + 5f, y + 10f); lineTo(x + 8f, y + 7f); lineTo(x + 7f, y + 13f); close() }
        drawPath(tail, a)
        drawLine(a, Offset(x - 1f, y + 19f), Offset(x - 1f, y + 22f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 2f, y + 19f), Offset(x + 2f, y + 22f), 1.3f, StrokeCap.Round)
        drawCircle(paper, 0.9f, Offset(x - 3.6f, y + 5.4f))
    },
    // 126 兎
    { x, y, a, paper ->
        val leftEar = Path().apply { moveTo(x - 1f, y + 9f); quadraticTo(x - 5f, y + 4f, x - 3f, y); quadraticTo(x - 1.5f, y + 3f, x - 0.3f, y + 9f); close() }
        drawPath(leftEar, a)
        val rightEar = Path().apply { moveTo(x + 1f, y + 9f); quadraticTo(x + 5f, y + 4f, x + 3f, y); quadraticTo(x + 1.5f, y + 3f, x + 0.3f, y + 9f); close() }
        drawPath(rightEar, a)
        drawCircle(a, 4.2f, Offset(x, y + 11f))
        val body = Path().apply { moveTo(x - 6f, y + 22f); quadraticTo(x - 7f, y + 13f, x - 1f, y + 13f); quadraticTo(x + 7f, y + 13f, x + 6f, y + 22f); close() }
        drawPath(body, a)
        drawCircle(a, 1.6f, Offset(x + 6f, y + 18f))
        drawCircle(paper, 0.9f, Offset(x - 1.4f, y + 10.5f))
        drawCircle(paper, 0.9f, Offset(x + 1.4f, y + 10.5f))
    },
    // 127 狐
    { x, y, a, paper ->
        val face = Path().apply { moveTo(x - 6f, y + 7f); lineTo(x + 6f, y + 7f); lineTo(x, y + 21f); close() }
        drawPath(face, a)
        val leftEar = Path().apply { moveTo(x - 6f, y + 7f); lineTo(x - 7f, y + 1f); lineTo(x - 2f, y + 6f); close() }
        drawPath(leftEar, a)
        val rightEar = Path().apply { moveTo(x + 6f, y + 7f); lineTo(x + 7f, y + 1f); lineTo(x + 2f, y + 6f); close() }
        drawPath(rightEar, a)
        val leftEye = Path().apply { moveTo(x - 4.5f, y + 10f); lineTo(x - 1.5f, y + 11f); lineTo(x - 4.5f, y + 12f); close() }
        drawPath(leftEye, paper)
        val rightEye = Path().apply { moveTo(x + 4.5f, y + 10f); lineTo(x + 1.5f, y + 11f); lineTo(x + 4.5f, y + 12f); close() }
        drawPath(rightEye, paper)
        drawCircle(paper, 0.9f, Offset(x, y + 17f))
    },
    // 128 鹿
    { x, y, a, paper ->
        val face = Path().apply {
            moveTo(x, y + 8f); quadraticTo(x + 3f, y + 9f, x + 2.6f, y + 15f); quadraticTo(x + 1.6f, y + 20f, x, y + 21f)
            quadraticTo(x - 1.6f, y + 20f, x - 2.6f, y + 15f); quadraticTo(x - 3f, y + 9f, x, y + 8f); close()
        }
        drawPath(face, a)
        val leftEar = Path().apply { moveTo(x - 2.6f, y + 9f); quadraticTo(x - 6f, y + 7f, x - 6f, y + 10f); quadraticTo(x - 4f, y + 10.5f, x - 2.6f, y + 10f); close() }
        drawPath(leftEar, a)
        val rightEar = Path().apply { moveTo(x + 2.6f, y + 9f); quadraticTo(x + 6f, y + 7f, x + 6f, y + 10f); quadraticTo(x + 4f, y + 10.5f, x + 2.6f, y + 10f); close() }
        drawPath(rightEar, a)
        drawLine(a, Offset(x - 1.6f, y + 8f), Offset(x - 4f, y + 2f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x - 3.4f, y + 3.5f), Offset(x - 6.5f, y + 2f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 1.6f, y + 8f), Offset(x + 4f, y + 2f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 3.4f, y + 3.5f), Offset(x + 6.5f, y + 2f), 1.3f, StrokeCap.Round)
        drawCircle(paper, 0.8f, Offset(x - 1.3f, y + 12f))
        drawCircle(paper, 0.8f, Offset(x + 1.3f, y + 12f))
    },
    // 129 猫
    { x, y, a, paper ->
        val body = Path().apply { moveTo(x - 5f, y + 22f); quadraticTo(x - 6f, y + 12f, x - 1f, y + 12f); quadraticTo(x + 6f, y + 12f, x + 5f, y + 22f); close() }
        drawPath(body, a)
        drawCircle(a, 4.5f, Offset(x, y + 9f))
        val leftEar = Path().apply { moveTo(x - 4.4f, y + 6f); lineTo(x - 5.6f, y + 1f); lineTo(x - 1.4f, y + 5f); close() }
        drawPath(leftEar, a)
        val rightEar = Path().apply { moveTo(x + 4.4f, y + 6f); lineTo(x + 5.6f, y + 1f); lineTo(x + 1.4f, y + 5f); close() }
        drawPath(rightEar, a)
        val tail = Path().apply { moveTo(x + 5f, y + 20f); quadraticTo(x + 8f, y + 18f, x + 6.5f, y + 14f) }
        drawPath(tail, a, style = strokeR(2f))
        drawCircle(paper, 0.9f, Offset(x - 1.7f, y + 9f))
        drawCircle(paper, 0.9f, Offset(x + 1.7f, y + 9f))
    },
    // 130 亀
    { x, y, a, paper ->
        for (s in listOf(Offset(-4.6f, -4.6f), Offset(4.6f, -4.6f), Offset(-4.6f, 4.6f), Offset(4.6f, 4.6f))) {
            drawCircle(a, 2f, Offset(x + s.x, y + 13f + s.y))
        }
        val head = Path().apply { moveTo(x - 1.6f, y + 7f); lineTo(x, y + 3.5f); lineTo(x + 1.6f, y + 7f); close() }
        drawPath(head, a)
        drawCircle(a, 6.4f, Offset(x, y + 13f))
        drawCircle(a, 2.1f, Offset(x, y + 21.5f))
        val hex = Path().apply {
            for (k in 0 until 6) {
                val an = k * PI / 3 - PI / 2
                val px = x + (3.4 * cos(an)).toFloat(); val py = y + 13f + (3.4 * sin(an)).toFloat()
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(hex, paper, style = strokeR(1f))
    },
    // 131 鯉
    { x, y, a, paper ->
        val body = Path().apply {
            moveTo(x, y + 2f); quadraticTo(x - 6f, y + 3f, x - 5f, y + 9f); quadraticTo(x - 4f, y + 14f, x - 3f, y + 17f)
            lineTo(x - 6f, y + 22f); lineTo(x, y + 18f); lineTo(x + 6f, y + 22f); lineTo(x + 3f, y + 17f)
            quadraticTo(x + 4f, y + 14f, x + 5f, y + 9f); quadraticTo(x + 6f, y + 3f, x, y + 2f); close()
        }
        drawPath(body, a)
        drawLine(a, Offset(x - 4f, y + 10f), Offset(x - 7f, y + 12f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 4f, y + 10f), Offset(x + 7f, y + 12f), 1.3f, StrokeCap.Round)
        drawCircle(paper, 1.2f, Offset(x - 2f, y + 6f))
        drawCircle(paper, 1.2f, Offset(x + 2f, y + 6f))
    },
    // 132 揚羽
    { x, y, a, _ ->
        val ul = Path().apply { moveTo(x, y + 8f); quadraticTo(x - 8f, y + 1f, x - 6f, y + 9f); quadraticTo(x - 3f, y + 10f, x, y + 8f); close() }
        drawPath(ul, a)
        val ur = Path().apply { moveTo(x, y + 8f); quadraticTo(x + 8f, y + 1f, x + 6f, y + 9f); quadraticTo(x + 3f, y + 10f, x, y + 8f); close() }
        drawPath(ur, a)
        val ll = Path().apply { moveTo(x, y + 9f); quadraticTo(x - 7f, y + 12f, x - 5f, y + 18f); lineTo(x - 6f, y + 21f); quadraticTo(x - 2f, y + 15f, x, y + 13f); close() }
        drawPath(ll, a)
        val lr = Path().apply { moveTo(x, y + 9f); quadraticTo(x + 7f, y + 12f, x + 5f, y + 18f); lineTo(x + 6f, y + 21f); quadraticTo(x + 2f, y + 15f, x, y + 13f); close() }
        drawPath(lr, a)
        val bodyP = Path().apply { moveTo(x - 0.8f, y + 6f); lineTo(x - 0.8f, y + 16f); lineTo(x + 0.8f, y + 16f); lineTo(x + 0.8f, y + 6f); close() }
        drawPath(bodyP, a)
        val ant = Path().apply {
            moveTo(x, y + 6f); quadraticTo(x - 3f, y + 2f, x - 4f, y)
            moveTo(x, y + 6f); quadraticTo(x + 3f, y + 2f, x + 4f, y)
        }
        drawPath(ant, a, style = strokeR(1.1f))
    },
    // 133 蜻蛉
    { x, y, a, _ ->
        for (w in listOf(Offset(-8f, 4f), Offset(8f, 4f), Offset(-8f, 11f), Offset(8f, 11f))) {
            val tx = x + w.x; val ty = y + w.y
            val wing = Path().apply {
                moveTo(x, y + 7.5f); quadraticTo((x + tx) / 2f, ty - 1.8f, tx, ty); quadraticTo((x + tx) / 2f, ty + 1.8f, x, y + 7.5f); close()
            }
            drawPath(wing, a)
        }
        drawLine(a, Offset(x, y + 6f), Offset(x, y + 23f), 1.9f, StrokeCap.Round)
        drawCircle(a, 2.6f, Offset(x, y + 3.2f))
    },
    // 134 蛍
    { x, y, a, paper ->
        drawCircle(a, 1.8f, Offset(x, y + 4f))
        val wing = Path().apply { moveTo(x, y + 5f); quadraticTo(x - 3.6f, y + 8f, x - 2f, y + 13f); quadraticTo(x, y + 15f, x + 2f, y + 13f); quadraticTo(x + 3.6f, y + 8f, x, y + 5f); close() }
        drawPath(wing, a)
        drawCircle(a, 2.4f, Offset(x, y + 18f))
        for (k in 0 until 8) {
            val an = k * PI / 4
            drawLine(a, Offset(x + (3.4 * cos(an)).toFloat(), y + 18f + (3.4 * sin(an)).toFloat()), Offset(x + (5 * cos(an)).toFloat(), y + 18f + (5 * sin(an)).toFloat()), 1f, StrokeCap.Round)
        }
        drawCircle(paper, 1f, Offset(x, y + 18f))
    },
    // 135 蜘蛛
    { x, y, a, _ ->
        for (s in intArrayOf(-1, 1)) {
            for (L in listOf(intArrayOf(6, 5, 7, 7), intArrayOf(7, 9, 8, 11), intArrayOf(7, 12, 8, 15), intArrayOf(6, 15, 6, 18))) {
                val leg = Path().apply {
                    moveTo(x + s * 1.5f, y + 9f); lineTo(x + s * L[0], y + L[1]); lineTo(x + s * L[2], y + L[3])
                }
                drawPath(leg, a, style = strokeR(1.3f))
            }
        }
        drawCircle(a, 3.5f, Offset(x, y + 13f))
        drawCircle(a, 2.3f, Offset(x, y + 8.5f))
    },
    // 136 蛙
    { x, y, a, paper ->
        val body = Path().apply { moveTo(x - 7f, y + 16f); quadraticTo(x - 8f, y + 8f, x, y + 8f); quadraticTo(x + 8f, y + 8f, x + 7f, y + 16f); quadraticTo(x, y + 19f, x - 7f, y + 16f); close() }
        drawPath(body, a)
        drawCircle(a, 2.6f, Offset(x - 3.6f, y + 7f))
        drawCircle(a, 2.6f, Offset(x + 3.6f, y + 7f))
        val front = Path().apply {
            moveTo(x - 6f, y + 14f); lineTo(x - 8f, y + 18f); lineTo(x - 5f, y + 18f)
            moveTo(x + 6f, y + 14f); lineTo(x + 8f, y + 18f); lineTo(x + 5f, y + 18f)
        }
        drawPath(front, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 3f, y + 18f), Offset(x - 4f, y + 21f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 18f), Offset(x + 4f, y + 21f), 1.6f, StrokeCap.Round)
        drawCircle(paper, 1f, Offset(x - 3.6f, y + 7f))
        drawCircle(paper, 1f, Offset(x + 3.6f, y + 7f))
    },
    // 137 蝸牛
    { x, y, a, paper ->
        val body = Path().apply {
            moveTo(x - 7f, y + 20f); quadraticTo(x - 8f, y + 14f, x - 3f, y + 13f); lineTo(x + 4f, y + 13f)
            quadraticTo(x + 8f, y + 13.5f, x + 8f, y + 17f); lineTo(x + 7f, y + 20f); close()
        }
        drawPath(body, a)
        drawLine(a, Offset(x + 6f, y + 14f), Offset(x + 7f, y + 9f), 1.1f, StrokeCap.Round)
        drawLine(a, Offset(x + 3.6f, y + 14f), Offset(x + 3.6f, y + 9f), 1.1f, StrokeCap.Round)
        drawCircle(a, 1f, Offset(x + 7f, y + 8.5f))
        drawCircle(a, 1f, Offset(x + 3.6f, y + 8.5f))
        drawCircle(a, 6f, Offset(x - 2f, y + 9f))
        drawArc(paper, deg(0.5), deg(PI * 1.9 - 0.5), false, Offset(x - 6f, y + 5f), Size(8f, 8f), style = strokeR(1.1f))
        drawArc(paper, deg(0.5), deg(PI * 1.9 - 0.5), false, Offset(x - 3.2f, y + 7.4f), Size(4f, 4f), style = strokeR(1.1f))
        drawCircle(paper, 0.7f, Offset(x - 0.8f, y + 9.6f))
    },
    // 138 蝙蝠
    { x, y, a, _ ->
        val lw = Path().apply {
            moveTo(x - 1.5f, y + 8f); quadraticTo(x - 6f, y + 6f, x - 8f, y + 9f); quadraticTo(x - 7f, y + 12f, x - 5.5f, y + 11f)
            quadraticTo(x - 4.5f, y + 15f, x - 3.5f, y + 13f); quadraticTo(x - 2.5f, y + 16f, x - 1.5f, y + 14f); close()
        }
        drawPath(lw, a)
        val rw = Path().apply {
            moveTo(x + 1.5f, y + 8f); quadraticTo(x + 6f, y + 6f, x + 8f, y + 9f); quadraticTo(x + 7f, y + 12f, x + 5.5f, y + 11f)
            quadraticTo(x + 4.5f, y + 15f, x + 3.5f, y + 13f); quadraticTo(x + 2.5f, y + 16f, x + 1.5f, y + 14f); close()
        }
        drawPath(rw, a)
        drawCircle(a, 3f, Offset(x, y + 9f))
        val le = Path().apply { moveTo(x - 1.8f, y + 7f); lineTo(x - 3f, y + 2f); lineTo(x - 0.4f, y + 6f); close() }
        drawPath(le, a)
        val re = Path().apply { moveTo(x + 1.8f, y + 7f); lineTo(x + 3f, y + 2f); lineTo(x + 0.4f, y + 6f); close() }
        drawPath(re, a)
    },
    // 139 蜂
    { x, y, a, paper ->
        val lw = Path().apply { moveTo(x - 2f, y + 8f); quadraticTo(x - 8f, y + 6f, x - 7f, y + 11f); quadraticTo(x - 4f, y + 11f, x - 2f, y + 9f); close() }
        drawPath(lw, a)
        val rw = Path().apply { moveTo(x + 2f, y + 8f); quadraticTo(x + 8f, y + 6f, x + 7f, y + 11f); quadraticTo(x + 4f, y + 11f, x + 2f, y + 9f); close() }
        drawPath(rw, a)
        drawCircle(a, 2.2f, Offset(x, y + 4f))
        val bodyP = Path().apply {
            moveTo(x, y + 6f); quadraticTo(x - 4.5f, y + 8f, x - 4f, y + 15f); quadraticTo(x - 3f, y + 21f, x, y + 22f)
            quadraticTo(x + 3f, y + 21f, x + 4f, y + 15f); quadraticTo(x + 4.5f, y + 8f, x, y + 6f); close()
        }
        drawPath(bodyP, a)
        drawLine(paper, Offset(x - 4f, y + 11f), Offset(x + 4f, y + 11f), 1.4f, StrokeCap.Round)
        drawLine(paper, Offset(x - 3.7f, y + 15f), Offset(x + 3.7f, y + 15f), 1.4f, StrokeCap.Round)
        drawLine(paper, Offset(x - 2.7f, y + 18.5f), Offset(x + 2.7f, y + 18.5f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x - 0.8f, y + 2.5f), Offset(x - 2.5f, y), 1f, StrokeCap.Round)
        drawLine(a, Offset(x + 0.8f, y + 2.5f), Offset(x + 2.5f, y), 1f, StrokeCap.Round)
    },
    // 140 桜
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 5) {
            rotate(deg(k * 2 * PI / 5), pivot = Offset(x, cy)) {
                val petal = Path().apply {
                    moveTo(x, cy - 2.5f); quadraticTo(x - 3.8f, cy - 4.5f, x - 1.6f, cy - 7.5f); quadraticTo(x - 0.7f, cy - 6.4f, x, cy - 7f)
                    quadraticTo(x + 0.7f, cy - 6.4f, x + 1.6f, cy - 7.5f); quadraticTo(x + 3.8f, cy - 4.5f, x, cy - 2.5f); close()
                }
                drawPath(petal, a)
            }
        }
        drawCircle(paper, 1.6f, Offset(x, cy))
    },
    // 141 椿
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 5) {
            val an = -PI / 2 + k * 2 * PI / 5
            drawCircle(a, 3.4f, Offset(x + (4.5 * cos(an)).toFloat(), cy + (4.5 * sin(an)).toFloat()))
        }
        drawCircle(paper, 2.6f, Offset(x, cy))
        for (k in 0 until 5) {
            val an = -PI / 2 + k * 2 * PI / 5
            drawCircle(a, 0.9f, Offset(x + (1.5 * cos(an)).toFloat(), cy + (1.5 * sin(an)).toFloat()))
        }
    },
    // 142 牡丹
    { x, y, a, paper ->
        val cy = y + 11f
        drawCircle(a, 5f, Offset(x, cy))
        for (k in 0 until 8) {
            val an = k * PI / 4
            drawCircle(a, 3f, Offset(x + (5 * cos(an)).toFloat(), cy + (5 * sin(an)).toFloat()))
        }
        drawCircle(paper, 5f, Offset(x, cy), style = strokeR(1.1f))
        for (k in 0 until 6) {
            val an = -PI / 2 + k * PI / 3
            drawLine(paper, Offset(x, cy), Offset(x + (4.5 * cos(an)).toFloat(), cy + (4.5 * sin(an)).toFloat()), 1.1f, StrokeCap.Round)
        }
        drawCircle(paper, 1.6f, Offset(x, cy))
    },
    // 143 菊
    { x, y, a, _ ->
        val cy = y + 11f
        for (k in 0 until 16) {
            val an = k * PI / 8
            drawLine(a, Offset(x + (2.6 * cos(an)).toFloat(), cy + (2.6 * sin(an)).toFloat()), Offset(x + (8 * cos(an)).toFloat(), cy + (8 * sin(an)).toFloat()), 1.4f, StrokeCap.Round)
        }
        for (k in 0 until 16) {
            val an = (k + 0.5) * PI / 8
            drawLine(a, Offset(x + (2 * cos(an)).toFloat(), cy + (2 * sin(an)).toFloat()), Offset(x + (5.4 * cos(an)).toFloat(), cy + (5.4 * sin(an)).toFloat()), 1.4f, StrokeCap.Round)
        }
        drawCircle(a, 2.4f, Offset(x, cy))
    },
    // 144 桔梗
    { x, y, a, paper ->
        val cy = y + 11f
        drawPath(starPath(x, cy, 8f, 3.4f, 5, -PI / 2), a)
        for (k in 0 until 5) {
            val an = -PI / 2 + k * 2 * PI / 5
            drawLine(paper, Offset(x, cy), Offset(x + (7 * cos(an)).toFloat(), cy + (7 * sin(an)).toFloat()), 1f, StrokeCap.Round)
        }
        drawCircle(paper, 1.8f, Offset(x, cy))
    },
    // 145 撫子
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 5) {
            rotate(deg(k * 2 * PI / 5), pivot = Offset(x, cy)) {
                val petal = Path().apply {
                    moveTo(x, cy - 2.2f); lineTo(x - 3.2f, cy - 6.2f); lineTo(x - 2.4f, cy - 7.3f); lineTo(x - 1.6f, cy - 6.4f); lineTo(x - 0.8f, cy - 7.6f)
                    lineTo(x, cy - 6.6f); lineTo(x + 0.8f, cy - 7.6f); lineTo(x + 1.6f, cy - 6.4f); lineTo(x + 2.4f, cy - 7.3f); lineTo(x + 3.2f, cy - 6.2f); close()
                }
                drawPath(petal, a)
            }
        }
        drawCircle(paper, 1.5f, Offset(x, cy))
    },
    // 146 秋桜
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 8) {
            rotate(deg(k * PI / 4), pivot = Offset(x, cy)) {
                val petal = Path().apply {
                    moveTo(x, cy - 2f); quadraticTo(x - 3.3f, cy - 5.8f, x - 1.5f, cy - 7.8f); quadraticTo(x, cy - 6.8f, x + 1.5f, cy - 7.8f)
                    quadraticTo(x + 3.3f, cy - 5.8f, x, cy - 2f); close()
                }
                drawPath(petal, a)
            }
        }
        drawCircle(paper, 2.2f, Offset(x, cy))
    },
    // 147 菖蒲
    { x, y, a, _ ->
        val cy = y + 13f
        val top = Path().apply { moveTo(x, cy); quadraticTo(x - 2f, cy - 8f, x, cy - 12f); quadraticTo(x + 2f, cy - 8f, x, cy); close() }
        drawPath(top, a)
        val left = Path().apply { moveTo(x, cy - 2f); quadraticTo(x - 8f, cy - 6f, x - 6f, cy + 3f); quadraticTo(x - 4f, cy - 1f, x, cy - 2f); close() }
        drawPath(left, a)
        val right = Path().apply { moveTo(x, cy - 2f); quadraticTo(x + 8f, cy - 6f, x + 6f, cy + 3f); quadraticTo(x + 4f, cy - 1f, x, cy - 2f); close() }
        drawPath(right, a)
        drawLine(a, Offset(x - 3f, cy), Offset(x + 3f, cy), 1.8f, StrokeCap.Round)
    },
    // 148 蓮
    { x, y, a, _ ->
        val by = y + 20f
        for (pt in listOf(Pair(-PI / 2, 14.0), Pair(-PI / 2 - 0.62, 12.0), Pair(-PI / 2 + 0.62, 12.0), Pair(-PI / 2 - 1.0, 8.5), Pair(-PI / 2 + 1.0, 8.5))) {
            val ang = pt.first; val len = pt.second
            val nx = cos(ang + PI / 2); val ny = sin(ang + PI / 2); val w = 2.6
            val mx = x + (len * 0.5 * cos(ang)).toFloat(); val my = by + (len * 0.5 * sin(ang)).toFloat()
            val tx = x + (len * cos(ang)).toFloat(); val ty = by + (len * sin(ang)).toFloat()
            val petal = Path().apply {
                moveTo(x, by)
                quadraticTo((mx + w * nx).toFloat(), (my + w * ny).toFloat(), tx, ty)
                quadraticTo((mx - w * nx).toFloat(), (my - w * ny).toFloat(), x, by)
                close()
            }
            drawPath(petal, a)
        }
    },
    // 149 朝顔
    { x, y, a, paper ->
        val cy = y + 10f
        drawCircle(a, 8f, Offset(x, cy))
        for (k in 0 until 5) {
            val an = -PI / 2 + k * 2 * PI / 5
            drawLine(paper, Offset(x, cy), Offset(x + (8 * cos(an)).toFloat(), cy + (8 * sin(an)).toFloat()), 1.2f, StrokeCap.Round)
        }
        drawCircle(paper, 2f, Offset(x, cy))
        val stem = Path().apply { moveTo(x - 2f, cy + 7f); lineTo(x + 2f, cy + 7f); lineTo(x + 1.4f, cy + 13f); lineTo(x - 1.4f, cy + 13f); close() }
        drawPath(stem, a)
    },
    // 150 水仙
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 6) {
            val an = -PI / 2 + k * PI / 3
            rotate(deg(an + PI / 2), pivot = Offset(x, cy)) {
                val petal = Path().apply {
                    moveTo(x, cy - 3f); quadraticTo(x - 2.8f, cy - 6f, x, cy - 8.6f); quadraticTo(x + 2.8f, cy - 6f, x, cy - 3f); close()
                }
                drawPath(petal, a)
            }
        }
        drawCircle(a, 3.4f, Offset(x, cy))
        drawCircle(paper, 2f, Offset(x, cy))
    },
    // 151 向日葵
    { x, y, a, paper ->
        val cy = y + 11f
        for (k in 0 until 12) {
            val an = k * PI / 6
            val petal = Path().apply {
                moveTo(x + (3.6 * cos(an)).toFloat(), cy + (3.6 * sin(an)).toFloat())
                lineTo(x + (8 * cos(an - 0.12)).toFloat(), cy + (8 * sin(an - 0.12)).toFloat())
                lineTo(x + (8 * cos(an + 0.12)).toFloat(), cy + (8 * sin(an + 0.12)).toFloat())
                close()
            }
            drawPath(petal, a)
        }
        drawCircle(a, 4f, Offset(x, cy))
        drawCircle(paper, 3f, Offset(x, cy))
        drawCircle(a, 1f, Offset(x, cy))
    },
    // 152 藤
    { x, y, a, _ ->
        for (p in listOf(Triple(3.5f, 3f, 2.6f), Triple(3f, 6.5f, 2.3f), Triple(2.4f, 10f, 2f), Triple(1.8f, 13.5f, 1.7f), Triple(1.1f, 17f, 1.3f), Triple(0.4f, 20f, 1f))) {
            drawCircle(a, p.third, Offset(x - p.first, y + p.second))
            drawCircle(a, p.third, Offset(x + p.first, y + p.second))
        }
    },
    // 153 紫陽花
    { x, y, a, _ ->
        for (f in listOf(Offset(x, y + 6f), Offset(x - 4.2f, y + 10f), Offset(x + 4.2f, y + 10f), Offset(x - 2.5f, y + 15f), Offset(x + 2.5f, y + 15f))) {
            for (k in 0 until 4) {
                val an = k * PI / 2 + PI / 4
                drawCircle(a, 1.6f, Offset(f.x + (2 * cos(an)).toFloat(), f.y + (2 * sin(an)).toFloat()))
            }
        }
    },
    // 154 紅葉
    { x, y, a, _ ->
        val cy = y + 11f
        for (L in listOf(Pair(270.0, 9.0), Pair(212.0, 8.0), Pair(328.0, 8.0), Pair(150.0, 6.5), Pair(30.0, 6.5))) {
            val an = L.first * PI / 180; val nx = cos(an + PI / 2); val ny = sin(an + PI / 2); val w = 2.2; val len = L.second
            val lobe = Path().apply {
                moveTo(x + (w * nx).toFloat(), cy + (w * ny).toFloat())
                lineTo(x + (len * cos(an)).toFloat(), cy + (len * sin(an)).toFloat())
                lineTo(x - (w * nx).toFloat(), cy - (w * ny).toFloat())
                close()
            }
            drawPath(lobe, a)
        }
        drawCircle(a, 2.6f, Offset(x, cy))
        drawLine(a, Offset(x, cy + 2f), Offset(x, cy + 9f), 1.4f, StrokeCap.Round)
    },
    // 155 銀杏
    { x, y, a, paper ->
        val by = y + 20f
        val leaf = Path().apply {
            moveTo(x, by); quadraticTo(x - 8f, by - 9f, x - 6f, by - 13f); quadraticTo(x - 3f, by - 15f, x, by - 12f)
            quadraticTo(x + 3f, by - 15f, x + 6f, by - 13f); quadraticTo(x + 8f, by - 9f, x, by); close()
        }
        drawPath(leaf, a)
        for (dx in listOf(-5f, -2.5f, 0f, 2.5f, 5f)) {
            drawLine(paper, Offset(x, by), Offset(x + dx, by - 11.5f), 0.9f, StrokeCap.Round)
        }
        drawLine(a, Offset(x, by), Offset(x, by + 3f), 1.5f, StrokeCap.Round)
    },
    // 156 松笠
    { x, y, a, paper ->
        val cone = Path().apply {
            moveTo(x, y + 2f); quadraticTo(x - 6f, y + 3f, x - 6f, y + 11f); quadraticTo(x - 6f, y + 21f, x, y + 23f)
            quadraticTo(x + 6f, y + 21f, x + 6f, y + 11f); quadraticTo(x + 6f, y + 3f, x, y + 2f); close()
        }
        drawPath(cone, a)
        val scales = Path().apply {
            for (i in 0 until 4) {
                val yy = y + 6f + i * 4.5f
                moveTo(x - 5f, yy); lineTo(x, yy + 3f); lineTo(x + 5f, yy)
            }
        }
        drawPath(scales, paper, style = strokeR(1f))
        drawLine(a, Offset(x, y + 2f), Offset(x, y), 1.6f, StrokeCap.Round)
    },
    // 157 筆
    { x, y, a, _ ->
        drawCircle(a, 2f, Offset(x, y + 3f), style = strokeR(1.5f))
        drawLine(a, Offset(x - 2f, y + 5f), Offset(x - 2f, y + 13f), 1.5f, StrokeCap.Round)
        drawLine(a, Offset(x + 2f, y + 5f), Offset(x + 2f, y + 13f), 1.5f, StrokeCap.Round)
        drawLine(a, Offset(x - 2f, y + 5f), Offset(x + 2f, y + 5f), 1.5f, StrokeCap.Round)
        val tip = Path().apply { moveTo(x - 3f, y + 13f); quadraticTo(x - 2f, y + 20f, x, y + 23f); quadraticTo(x + 2f, y + 20f, x + 3f, y + 13f); close() }
        drawPath(tip, a)
    },
    // 158 筆立
    { x, y, a, _ ->
        drawLine(a, Offset(x - 3f, y + 13f), Offset(x - 4f, y + 4f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 3f, y + 13f), Offset(x + 5f, y + 4f), 1.6f, StrokeCap.Round)
        drawCircle(a, 1.6f, Offset(x - 4f, y + 3f))
        drawCircle(a, 1.6f, Offset(x + 5f, y + 3f))
        val holder = Path().apply { moveTo(x - 6f, y + 13f); lineTo(x - 5f, y + 23f); lineTo(x + 5f, y + 23f); lineTo(x + 6f, y + 13f); close() }
        drawPath(holder, a, style = strokeR(1.6f))
    },
    // 159 硯
    { x, y, a, paper ->
        val body = Path().apply {
            moveTo(x - 6f, y + 6f); lineTo(x + 6f, y + 6f); quadraticTo(x + 8f, y + 6f, x + 8f, y + 8f); lineTo(x + 8f, y + 17f)
            quadraticTo(x + 8f, y + 19f, x + 6f, y + 19f); lineTo(x - 6f, y + 19f); quadraticTo(x - 8f, y + 19f, x - 8f, y + 17f); lineTo(x - 8f, y + 8f)
            quadraticTo(x - 8f, y + 6f, x - 6f, y + 6f); close()
        }
        drawPath(body, a)
        drawArc(paper, deg(0.0), deg(PI), true, Offset(x - 4.5f, y + 5.5f), Size(9f, 9f))
    },
    // 160 墨
    { x, y, a, paper ->
        val body = Path().apply {
            moveTo(x - 4f, y + 3f); lineTo(x + 4f, y + 3f); lineTo(x + 4f, y + 21f); quadraticTo(x + 4f, y + 22f, x + 3f, y + 22f)
            lineTo(x - 3f, y + 22f); quadraticTo(x - 4f, y + 22f, x - 4f, y + 21f); close()
        }
        drawPath(body, a)
        drawCircle(paper, 1.8f, Offset(x, y + 8f))
        drawLine(paper, Offset(x - 2.5f, y + 13f), Offset(x + 2.5f, y + 13f), 1.4f, StrokeCap.Round)
    },
    // 161 水滴
    { x, y, a, paper ->
        drawCircle(a, 6f, Offset(x, y + 15f))
        val spout = Path().apply { moveTo(x + 4f, y + 11f); lineTo(x + 8f, y + 9f); lineTo(x + 6f, y + 13f); close() }
        drawPath(spout, a)
        drawCircle(paper, 1.6f, Offset(x - 1f, y + 11f))
    },
    // 162 巻物
    { x, y, a, _ ->
        drawLine(a, Offset(x - 5f, y + 6f), Offset(x - 5f, y + 18f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 5f, y + 6f), Offset(x + 5f, y + 18f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x - 1f, y + 9f), Offset(x - 1f, y + 15f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 2f, y + 9f), Offset(x + 2f, y + 14f), 1.4f, StrokeCap.Round)
        drawRect(a, Offset(x - 5f, y + 3f), Size(10f, 3f))
        drawRect(a, Offset(x - 5f, y + 18f), Size(10f, 3f))
        drawCircle(a, 1.7f, Offset(x - 6f, y + 19.5f))
        drawCircle(a, 1.7f, Offset(x + 6f, y + 19.5f))
    },
    // 163 冊子
    { x, y, a, _ ->
        val cover = Path().apply { moveTo(x - 6f, y + 3f); lineTo(x + 6f, y + 3f); lineTo(x + 6f, y + 22f); lineTo(x - 6f, y + 22f); close() }
        drawPath(cover, a, style = strokeR(1.5f))
        drawLine(a, Offset(x - 3f, y + 3f), Offset(x - 3f, y + 22f), 1.5f, StrokeCap.Round)
        for (dd in intArrayOf(7, 12, 17)) drawLine(a, Offset(x - 5f, y + dd), Offset(x - 1f, y + dd), 1.4f, StrokeCap.Round)
    },
    // 164 折本
    { x, y, a, _ ->
        val zig = Path().apply { moveTo(x - 8f, y + 7f); lineTo(x - 4f, y + 17f); lineTo(x, y + 7f); lineTo(x + 4f, y + 17f); lineTo(x + 8f, y + 7f) }
        drawPath(zig, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 8f, y + 7f), Offset(x - 8f, y + 17f), 2.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 8f, y + 7f), Offset(x + 8f, y + 17f), 2.4f, StrokeCap.Round)
    },
    // 165 料紙
    { x, y, a, _ ->
        val sheet = Path().apply { moveTo(x - 6f, y + 3f); lineTo(x + 3f, y + 3f); lineTo(x + 6f, y + 6f); lineTo(x + 6f, y + 22f); lineTo(x - 6f, y + 22f); close() }
        drawPath(sheet, a, style = strokeR(1.5f))
        val ear = Path().apply { moveTo(x + 3f, y + 3f); lineTo(x + 3f, y + 6f); lineTo(x + 6f, y + 6f) }
        drawPath(ear, a, style = strokeR(1.5f))
        drawLine(a, Offset(x - 2f, y + 10f), Offset(x - 2f, y + 18f), 1.3f, StrokeCap.Round)
        drawLine(a, Offset(x + 1f, y + 10f), Offset(x + 1f, y + 16f), 1.3f, StrokeCap.Round)
    },
    // 166 落款印
    { x, y, a, paper ->
        drawRect(a, Offset(x - 6f, y + 5f), Size(12f, 14f))
        drawLine(paper, Offset(x, y + 7.5f), Offset(x, y + 16.5f), 1.8f, StrokeCap.Round)
        drawLine(paper, Offset(x - 3.5f, y + 9.5f), Offset(x + 3.5f, y + 9.5f), 1.8f, StrokeCap.Round)
        drawLine(paper, Offset(x - 3.5f, y + 14.5f), Offset(x + 3.5f, y + 14.5f), 1.8f, StrokeCap.Round)
    },
    // 167 矢立
    { x, y, a, paper ->
        drawRect(a, Offset(x - 2f, y + 5f), Size(8f, 4f))
        drawCircle(a, 2f, Offset(x + 6f, y + 7f))
        drawCircle(a, 5f, Offset(x - 3f, y + 14f))
        drawCircle(paper, 2f, Offset(x - 3f, y + 14f), style = strokeR(1.4f))
    },
    // 168 文箱
    { x, y, a, paper ->
        drawRect(a, Offset(x - 6f, y + 12f), Size(12f, 8f))
        drawRect(a, Offset(x - 7f, y + 8f), Size(14f, 5f))
        drawCircle(paper, 1.5f, Offset(x, y + 10f))
        drawLine(paper, Offset(x, y + 13f), Offset(x, y + 15f), 1.2f, StrokeCap.Round)
    },
    // 169 文鎮
    { x, y, a, _ ->
        val body = Path().apply {
            moveTo(x - 5f, y + 12f); lineTo(x + 5f, y + 12f)
            arcTo(Rect(Offset(x + 5f - 3f, y + 15f - 3f), Size(6f, 6f)), -90f, 180f, false)
            lineTo(x - 5f, y + 18f)
            arcTo(Rect(Offset(x - 5f - 3f, y + 15f - 3f), Size(6f, 6f)), 90f, 180f, false)
            close()
        }
        drawPath(body, a)
        val handle = Path().apply { moveTo(x - 3f, y + 12f); quadraticTo(x, y + 5f, x + 3f, y + 12f) }
        drawPath(handle, a, style = strokeR(1.8f))
    },
    // 170 筆架
    { x, y, a, _ ->
        val rest = Path().apply {
            moveTo(x - 8f, y + 18f); lineTo(x - 8f, y + 14f); quadraticTo(x - 6f, y + 8f, x - 4f, y + 13f); quadraticTo(x - 2f, y + 16f, x, y + 11f)
            quadraticTo(x + 2f, y + 16f, x + 4f, y + 13f); quadraticTo(x + 6f, y + 8f, x + 8f, y + 14f); lineTo(x + 8f, y + 18f); close()
        }
        drawPath(rest, a)
    },
    // 171 眼鏡
    { x, y, a, _ ->
        drawCircle(a, 4f, Offset(x - 4f, y + 12f), style = strokeR(1.6f))
        drawCircle(a, 4f, Offset(x + 4f, y + 12f), style = strokeR(1.6f))
        val bridge = Path().apply { moveTo(x - 1f, y + 11f); quadraticTo(x, y + 9f, x + 1f, y + 11f) }
        drawPath(bridge, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 8f, y + 11f), Offset(x - 8f, y + 8f), 1.6f, StrokeCap.Round)
        drawLine(a, Offset(x + 8f, y + 11f), Offset(x + 8f, y + 8f), 1.6f, StrokeCap.Round)
    },
    // 172 湯呑
    { x, y, a, paper ->
        val cup = Path().apply { moveTo(x - 5f, y + 8f); lineTo(x + 5f, y + 8f); lineTo(x + 4f, y + 19f); lineTo(x - 4f, y + 19f); close() }
        drawPath(cup, a)
        drawLine(paper, Offset(x - 4f, y + 11f), Offset(x + 4f, y + 11f), 1.4f, StrokeCap.Round)
        val saucer = Path().apply { moveTo(x - 7f, y + 21f); quadraticTo(x, y + 24f, x + 7f, y + 21f) }
        drawPath(saucer, a, style = strokeR(1.5f))
    },
    // 173 行灯
    { x, y, a, _ ->
        val frame = Path().apply { moveTo(x - 6f, y + 6f); lineTo(x + 6f, y + 6f); lineTo(x + 6f, y + 17f); lineTo(x - 6f, y + 17f); close() }
        drawPath(frame, a, style = strokeR(1.6f))
        drawLine(a, Offset(x - 2f, y + 6f), Offset(x - 2f, y + 17f), 1.2f, StrokeCap.Round)
        drawLine(a, Offset(x + 2f, y + 6f), Offset(x + 2f, y + 17f), 1.2f, StrokeCap.Round)
        drawLine(a, Offset(x - 7f, y + 4f), Offset(x + 7f, y + 4f), 2f, StrokeCap.Round)
        drawLine(a, Offset(x - 4f, y + 17f), Offset(x - 4f, y + 21f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x + 4f, y + 17f), Offset(x + 4f, y + 21f), 1.4f, StrokeCap.Round)
        drawLine(a, Offset(x - 6f, y + 21f), Offset(x + 6f, y + 21f), 1.4f, StrokeCap.Round)
    },
)

/**
 * 栞アクセント色の共有ヘルパー（純関数）。書架の栞の棒／先端色と、目録リストの左端色帯を
 * この1関数へ集約し「同じ本＝同じ色相・同じ明度」を書架⇔目録で保証する（整合の要）。
 *
 * 明度はテーマ3値（正本 consistency-D の THEMES.accL）: ライト L=0.52／セピア L=0.48／ダーク L=0.62。
 * 彩度 S=0.48 固定。
 *
 * なぜ surface からテーマを判定するか: 純関数ゆえ colorScheme を直接持てず、呼び出し側が渡す surface で
 * 識別する。セピア判定に `surface == BackgroundSepia` を使う理由: SepiaColorScheme は
 * LightColorScheme.copy(...) で作られ、surface だけがセピア固有値(#F2E7CE)を持つ唯一確実な識別子だから
 * （他トークンはライトと共有され得るため surface 以外では判別できない）。ダーク判定は既存 ShioriCover と
 * 同じく luminance()<0.5 を維持する。
 */
internal fun shioriAccentFor(hue: Int, surface: Color): Color {
    val l = when {
        surface == BackgroundSepia -> 0.48f
        surface.luminance() < 0.5f -> 0.62f
        else -> 0.52f
    }
    return hslToColor(hue.toFloat(), 0.48f, l)
}

// 栞表紙の内枠（正本の 5% 罫）の不透明度。紙のふちを表紙の墨/白で微かに締めるだけの飾り罫。
// 旧値 0x0D（=13/255≈0.051）を明示定数化し、色は生 ARGB でなく表紙の墨 ink から生成する。
private const val ShioriInnerBorderAlpha = 0.05f

/**
 * 栞書影。title から決定論生成した「棒＋先端＋縦組み明朝題字」を紙地に描く。
 *
 * @param accentOverride 識別色を差し替える（Web由来・未取込カードの青磁署名など・将来用）。null=title 生成色。
 * @param persistedTipIndex 取込時に抽選し BookEntity に永続化した先端種。null=未抽選（旧蔵書）＝title 由来の決定論値へフォールバック。
 * @param persistedLenFrac 取込時に抽選し永続化した棒長。null=同上。両者とも「非 null なら固定・null なら従来の見た目」。
 */
@Composable
internal fun ShioriCover(
    title: String,
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
    persistedTipIndex: Int? = null,
    persistedLenFrac: Float? = null,
) {
    val cs = MaterialTheme.colorScheme
    // ダーク判定＝surface の輝度。ライト #FBFAF8／セピア #F2E7CE は高輝度、ダーク #14171C は低輝度。
    val isDark = remember(cs.surface) { cs.surface.luminance() < 0.5f }
    // 紙／墨: ライト・セピアは surface/onSurface がモック値と一致。ダークだけ cover 専用トークン（Color.kt）。
    val paper = if (isDark) ShioriCoverPaperDark else cs.surface
    val ink = if (isDark) ShioriCoverInkDark else cs.onSurface
    // 永続値も remember キーに含める（null→非 null の差し替え時に確実に再計算させる）。
    val params = remember(title, persistedTipIndex, persistedLenFrac) {
        shioriParams(title, SHIORI_TIPS.size, persistedTipIndex, persistedLenFrac)
    }
    // 棒・先端の識別色＝生成色。共有ヘルパー shioriAccentFor に集約し、目録リストの色帯と同一色にする
    // （S=0.48・L はライト0.52/セピア0.48/ダーク0.62＝surface からテーマ判定）。
    val computedAccent = remember(params.hue, cs.surface) {
        shioriAccentFor(params.hue, cs.surface)
    }
    val accent = accentOverride ?: computedAccent
    // 内枠（正本の 5% 罫）＝紙のふちを微かに締める。
    // なぜ生 ARGB でなく ink 由来か: 旧 Color(0x0DFFFFFF)/Color(0x0D1C1F26) はテーマ改訂でこの罫だけ
    // 取り残される（ADR 0014 charter(a) theme/外の生 Color 禁止）。表紙の墨 ink（ライト/セピア=onSurface・
    // ダーク=ShioriCoverInkDark）を名前付き alpha で薄め、isDark の2分岐を1式へ畳む。
    val borderColor = ink.copy(alpha = ShioriInnerBorderAlpha)

    // 題字は Canvas 描画で text ノードを持たないため、表紙自体に contentDescription=題名 を与える。
    // なぜ必須か: これが無いとスクリーンリーダーがグリッドの作品名を読めない（a11y 退行）。
    Canvas(modifier.semantics { contentDescription = title }) {
        val w = size.width
        val h = size.height
        drawRect(paper) // 紙地
        drawRect(borderColor, topLeft = Offset(0.5f, 0.5f), size = Size(w - 1f, h - 1f), style = Stroke(1f))
        // 固定px意匠のスケール補正。なぜ必要か: 正本 grid-D の canvas は「幅150pxのカバー」を基準に
        // した固定px座標で棒・先端を描く（Node検証時 clientWidth=0→フォールバック150 で描画＝これが
        // 設計基準）。実機は density 倍の device px（例 592px＝density4）で同じ数値をそのまま描くと、
        // w比例の題字は正しくても、固定pxの棒の太さ・先端が 1/density に潰れ、先端31種の意匠がほぼ
        // 消える（実測: density4 で先端が針の点大に縮小）。基準150に対する実幅比 s を固定px意匠に掛け、
        // 相対サイズをモックと一致させる（棒位置 barX・長さ barLen は w/h 比例なので s を掛けない）。
        val s = w / 150f
        // 棒＝天から色の細線。cap=Butt（正本 lineCap:'butt'＝天端を角で切る）。太さは固定px→s倍。
        val barX = (w * params.xFrac).roundToInt() + 0.5f
        val barLen = (h * params.lenFrac).roundToInt().toFloat()
        drawLine(accent, Offset(barX, 0f), Offset(barX, barLen), 2.5f * s, StrokeCap.Butt)
        // 先端＝種で1つ。棒先端(barX,barLen)を pivot に s 倍拡大し、モックの相対サイズを再現する
        // （先端は固定px座標で描かれるため scale 変換で一括拡大＝各座標を書き換えず正本ロジックを保つ）。
        // coerceIn は防御（tipCount とインデックスの不整合が起きても落とさない）。
        scale(s, s, pivot = Offset(barX, barLen)) {
            SHIORI_TIPS[params.tipIndex.coerceIn(0, SHIORI_TIPS.size - 1)](this, barX, barLen, accent, paper)
        }
        drawShioriTitle(title, w, h, ink)
    }
}

/**
 * 縦組み題字の 1 字描画に使う共有レンダラ。読書画面の縦書き（P1〜P3）と同一の分類→描画経路に通し、
 * 「（）」「～」「ー」等が正立のまま死ぬ既存バグ（全字 drawText 直描画）を解消する。
 * なぜ関数外の private val か: drawShioriTitle は DrawScope 拡張で remember が使えず、毎回 new すると
 * 描画のたびにインスタンスを捨てる。状態を持たない純描画実装なので単一インスタンスを使い回す。
 */
private val shioriTitleGlyphRenderer = VertGlyphRenderer()

/**
 * 表紙内の縦組み明朝題字（右列から左へ最大3列・溢れは末尾を ⋮）。
 * なぜ nativeCanvas + Serif Typeface で px 直描画か: 正本 canvas の `ctx.fillText`（px 指定・
 * textBaseline='middle'）を最も忠実に翻訳でき、かつフォントスケールに依存しない固定構図の
 * 表紙グラフィックにするため（sp 換算だと端末の文字拡大で題字だけ崩れる）。Serif は当端末で
 * Noto Serif CJK（＝明朝）へ解決＝Typography の MinchoFamily(FontFamily.Serif) と同系。
 *
 * 1 字は CharClassifier.classify → VertGlyphRenderer.drawGlyph 経由で置く（読書画面と同一部品）。
 * なぜ連続リーダー結合（LeaderJoin）を使わないか: 題字は固定グリッド（1字=1セル）で ⋮省略と列割りが
 * セル数を前提にするため、複数字を1ユニットへ束ねる結合は列の勘定を壊す。1字=1セルのまま分類だけ通す。
 */
private fun DrawScope.drawShioriTitle(text: String, w: Float, h: Float, ink: Color) {
    val fs = (w * 0.088f).roundToInt().toFloat()
    if (fs < 1f || text.isEmpty()) return
    val lineGap = fs * 1.16f
    val colGap = (w * 0.108f).roundToInt().toFloat()
    val yTop = fs + 5f
    val yBottom = h - 11f
    val x0 = w - fs - 3f
    val maxCols = 3
    val perCol = floor((yBottom - yTop) / lineGap).toInt()
    if (perCol < 1) return
    // 正本の [...text] 相当＝コードポイント単位（サロゲート対応）。
    val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            color = ink.toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) // 明朝・太字（正本 font-weight 600 相当）
            textSize = fs
            textAlign = Paint.Align.CENTER
        }
        for (col in 0 until maxCols) {
            val start = col * perCol
            if (start >= chars.size) break
            val cxp = x0 - col * colGap
            for (i in 0 until perCol) {
                val idx = start + i
                if (idx >= chars.size) break
                // 3列目末尾でまだ続くなら ⋮ で省略（正本と同じ）。⋮ も classify を通す（UPRIGHT で正しい）。
                val ch = if (col == maxCols - 1 && i == perCol - 1 && idx < chars.size - 1) "⋮" else chars[idx]
                // セルの天 y = 従来のセル中心(cy) − lineGap/2。renderer が中心合わせ（旧 baseAdj 手計算）を担う。
                val cellTop = yTop + i * lineGap
                val cls = CharClassifier.classify(ch)
                shioriTitleGlyphRenderer.drawGlyph(canvas.nativeCanvas, ch, cls, cxp, cellTop, lineGap, paint)
            }
        }
    }
}
