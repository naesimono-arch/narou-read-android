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
// 紙地に「天から引いた色の細棒＋その先端のワンポイント意匠（31種から title 種で1つ）」を置く。
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
 * 先端ワンポイント31種（正本 TIPS の faithful 移植）。ここに1つ足すだけで拡張できる。
 * 結び房系[魚尾/一粒/結び玉/二又房/三又房/総角/蝶結び/玉と尾/数珠/括り]／
 * 輪幾何系[小輪/二重丸/菱/逆三角/小四角/星/十字/三点/雫/半月/矢尻]／
 * 和意匠系[巴/勾玉/鈴/瓢箪/短冊/蔵書印/梅/木の葉/木の実/分銅]。
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
 */
@Composable
internal fun ShioriCover(
    title: String,
    modifier: Modifier = Modifier,
    accentOverride: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    // ダーク判定＝surface の輝度。ライト #FBFAF8／セピア #F2E7CE は高輝度、ダーク #14171C は低輝度。
    val isDark = remember(cs.surface) { cs.surface.luminance() < 0.5f }
    // 紙／墨: ライト・セピアは surface/onSurface がモック値と一致。ダークだけ cover 専用トークン（Color.kt）。
    val paper = if (isDark) ShioriCoverPaperDark else cs.surface
    val ink = if (isDark) ShioriCoverInkDark else cs.onSurface
    val params = remember(title) { shioriParams(title, SHIORI_TIPS.size) }
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
 * 表紙内の縦組み明朝題字（右列から左へ最大3列・溢れは末尾を ⋮）。
 * なぜ nativeCanvas + Serif Typeface で px 直描画か: 正本 canvas の `ctx.fillText`（px 指定・
 * textBaseline='middle'）を最も忠実に翻訳でき、かつフォントスケールに依存しない固定構図の
 * 表紙グラフィックにするため（sp 換算だと端末の文字拡大で題字だけ崩れる）。Serif は当端末で
 * Noto Serif CJK（＝明朝）へ解決＝Typography の MinchoFamily(FontFamily.Serif) と同系。
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
        val fm = paint.fontMetrics
        val baseAdj = (fm.ascent + fm.descent) / 2f // textBaseline='middle' 相当（中心→ベースライン補正）
        for (col in 0 until maxCols) {
            val start = col * perCol
            if (start >= chars.size) break
            val cxp = x0 - col * colGap
            for (i in 0 until perCol) {
                val idx = start + i
                if (idx >= chars.size) break
                // 3列目末尾でまだ続くなら ⋮ で省略（正本と同じ）。
                val ch = if (col == maxCols - 1 && i == perCol - 1 && idx < chars.size - 1) "⋮" else chars[idx]
                val cy = yTop + i * lineGap + lineGap / 2f
                canvas.nativeCanvas.drawText(ch, cxp, cy - baseAdj, paint)
            }
        }
    }
}
