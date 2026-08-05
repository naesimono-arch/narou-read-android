package com.novelreader.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.tan

// ============================================================
// ShioriHighLoadTips — 栞アニメ高負荷モードの1フレーム描画（2026-08-06 裁定＝モック全体GO）。
//
// 値の出所はすべて ShioriHighLoadChoreo（モック t0〜t8／bf0〜bf8 の写し）＝本ファイルは
// 「部品分解した先端を、トラックから引いた値の変形で描く」だけ。部品の座標・線幅・半径は
// ShioriTips.kt の静的 tip 0〜8 と同値（アタッチ点(16,4)↔(x,y) の平行移動＝モック svgTip と同座標）。
//
// CSS transform との等価性:
//  ・transform リストは translate→rotate→scale の順で並ぶ（モック全 tip）。純平行移動は origin 移動と
//    可換なので、〈plain translate → rotate(pivot=origin) → scale(pivot=origin)〉の合成は
//    CSS の translate(O)·list·translate(-O) と厳密一致する。
//  ・skewX だけは DrawScope に直接 API が無いため Matrix（translate(O)·skew·translate(-O)）で写す。
//  ・静止区間はすべてのトラックが静止値（0/1/2.6/…）を返し、identity 変形＋静的描画と同一プリミティブ
//    列になる＝静止表示は静的 tip と1pxも変わらない（モックの「静止意匠不変」規則の Compose 側保証）。
// ============================================================

private fun strokeRound(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * skewX(deg) を origin(ox,oy) まわりで行う行列（CSS の transform-origin 付き skewX の等価）。
 * Matrix.values[SkewX] は「y を x' へ混ぜる」係数＝x' = x + tanθ·y（CSS skewX と同定義）。
 */
private fun skewXAround(ox: Float, oy: Float, deg: Float): Matrix {
    val m = Matrix()
    m.translate(ox, oy)
    val sk = Matrix()
    sk.values[Matrix.SkewX] = tan(Math.toRadians(deg.toDouble())).toFloat()
    m *= sk
    m.translate(-ox, -oy)
    return m
}

/**
 * 高負荷モードの〈線追従層＋棒＋先端〉を1フレーム描く（ShioriCover のアニメ経路専用）。
 *
 * @param barX 棒中心 x（cover px）／@param barLen 棒の長さ＝先端アタッチ点 y（cover px）
 * @param coverScale 基準150幅に対する実幅比 s（静的経路と同じ固定px意匠のスケール補正）
 * @param hue,accentLightness tip5 の filter 等価（HSL 再計算）に使う生成色の素材
 * @param tSec カード位相込みのアニメ時刻（秒）
 */
internal fun DrawScope.drawShioriHighLoad(
    tipIndex: Int,
    barX: Float,
    barLen: Float,
    coverScale: Float,
    accent: Color,
    hue: Int,
    accentLightness: Float,
    tSec: Float,
) {
    val choreo = SHIORI_HIGHLOAD_CHOREOS[tipIndex]
    val root = Offset(barX, 0f) // 付け根（本への挿し込み点）＝線追従の支点（恒久規則 2026-08-06）
    withTransform({
        // 線追従層 .flw: 〈棒＋先端〉を束ねて傾がせる。棒だけ回すと棒先端と装飾のアタッチ点が
        // 離れて見えるため（モック注記）。ROTATE=傾ぎ／SCALE=縦張力の微伸び（最小追従 bf5/6/8）。
        for (tr in choreo.follow) when (tr.prop) {
            ShioriChoreoProp.ROTATE -> rotate(tr.aAt(tSec), root)
            ShioriChoreoProp.SCALE -> scale(1f, tr.aAt(tSec), root)
            else -> Unit // 線追従は rotate/scaleY のみ（データ側の契約）
        }
    }) {
        // 棒。tip2 のみ「先端のみ可動」緩和＝締まりの張力 t2bar が scaleY で伝う（origin=付け根）。
        // 静止値 1f では素の drawLine と同一（identity scale を挟まない＝静止時の描画列を静的経路と揃える）。
        val barSy = if (tipIndex == 2) ShioriT2.BAR_SY.aAt(tSec) else 1f
        if (barSy == 1f) {
            drawLine(accent, Offset(barX, 0f), Offset(barX, barLen), 2.5f * coverScale, StrokeCap.Butt)
        } else {
            scale(1f, barSy, root) {
                drawLine(accent, Offset(barX, 0f), Offset(barX, barLen), 2.5f * coverScale, StrokeCap.Butt)
            }
        }
        // 先端＝静的経路と同じ「棒先端 pivot の s 倍」空間で部品ごとの振り付けを描く。
        scale(coverScale, coverScale, pivot = Offset(barX, barLen)) {
            when (tipIndex) {
                0 -> drawTip0(barX, barLen, accent, tSec)
                1 -> drawTip1(barX, barLen, accent, tSec)
                2 -> drawTip2(barX, barLen, accent, tSec)
                3 -> drawTip3(barX, barLen, accent, tSec)
                4 -> drawTip4(barX, barLen, accent, tSec)
                5 -> drawTip5(barX, barLen, hue, accentLightness, tSec)
                6 -> drawTip6(barX, barLen, accent, tSec)
                7 -> drawTip7(barX, barLen, accent, tSec)
                8 -> drawTip8(barX, barLen, accent, tSec)
            }
        }
    }
}

/** tip0 魚尾「水中の一閃」: 左右刃の逆相交差（t0-L/R）＋刃の撓み stroke-width（t0-flex・.09s 遅れ）＋反動の浮き（t0-G）。 */
private fun DrawScope.drawTip0(x: Float, y: Float, a: Color, t: Float) {
    val w = ShioriT0.FLEX.aAt(t) // 撓み: 打ち出しで太く（水を押す）→戻りで細く（抜ける）
    translate(0f, ShioriT0.GROUP_TY.aAt(t)) {
        rotate(ShioriT0.ROT_L.aAt(t), Offset(x, y)) {
            drawLine(a, Offset(x, y), Offset(x - 6f, y + 12f), w, StrokeCap.Round)
        }
        rotate(ShioriT0.ROT_R.aAt(t), Offset(x, y)) {
            drawLine(a, Offset(x, y), Offset(x + 6f, y + 12f), w, StrokeCap.Round)
        }
    }
}

/** tip1 一粒「振り子の余韻」: 減衰振り子（t1・支点=アタッチ点）＋最下点での縦つぶれ（t1-w・玉中心 pivot）。 */
private fun DrawScope.drawTip1(x: Float, y: Float, a: Color, t: Float) {
    rotate(ShioriT1.ROT.aAt(t), Offset(x, y)) {
        // 入れ子にする理由（モック注記）: 回転と潰しが同じ transform を奪い合わないため。
        scale(ShioriT1.SQUASH.aAt(t), ShioriT1.SQUASH.bAt(t), Offset(x, y + 7f)) {
            drawCircle(a, 4f, Offset(x, y + 7f))
        }
    }
}

/** tip2 結び玉「締まり直す結び」: 引き込み translateY＋座り直し rotate＋潰れ scale（t2・玉中心 pivot）。棒連動は呼び出し側。 */
private fun DrawScope.drawTip2(x: Float, y: Float, a: Color, t: Float) {
    translate(0f, ShioriT2.TY.aAt(t)) {
        rotate(ShioriT2.ROT.aAt(t), Offset(x, y + 6f)) {
            scale(ShioriT2.SC.aAt(t), ShioriT2.SC.bAt(t), Offset(x, y + 6f)) {
                drawCircle(a, 6f, Offset(x, y + 6f))
            }
        }
    }
}

/** tip3 二又房「すれ違う二本」: 左右で別周期の rotate（t3-a 3.9s／t3-b 5.3s）＋遅れて追う撓み skewX（t3-as/bs）。 */
private fun DrawScope.drawTip3(x: Float, y: Float, a: Color, t: Float) {
    rotate(ShioriT3.ROT_L.aAt(t), Offset(x, y)) {
        withTransform({ transform(skewXAround(x, y, ShioriT3.SKEW_L.aAt(t))) }) {
            drawLine(a, Offset(x, y), Offset(x - 5f, y + 18f), 2.4f, StrokeCap.Round)
        }
    }
    rotate(ShioriT3.ROT_R.aAt(t), Offset(x, y)) {
        withTransform({ transform(skewXAround(x, y, ShioriT3.SKEW_R.aAt(t))) }) {
            drawLine(a, Offset(x, y), Offset(x + 5f, y + 18f), 2.4f, StrokeCap.Round)
        }
    }
}

/** tip4 三又房「波が渡る」: 3本の rotate 位相カスケード（t4-s＋doff 0/.16/.32）＋頭玉の逆相（t4-b）＋全体の横流れ（t4-g）。 */
private fun DrawScope.drawTip4(x: Float, y: Float, a: Color, t: Float) {
    translate(ShioriT4.GROUP_TX.aAt(t), 0f) {
        rotate(ShioriT4.HEAD.aAt(t), Offset(x, y)) {
            drawCircle(a, 3.2f, Offset(x, y + 3f))
        }
        val ends = arrayOf(Offset(x - 6f, y + 22f), Offset(x, y + 23f), Offset(x + 6f, y + 22f))
        for (i in 0..2) {
            // 位相差は t をずらして与える（track 側 delay との合成＝CSS animation-delay の加算と等価）。
            rotate(ShioriT4.STRAND.aAt(t - ShioriT4.STRAND_DOFFS[i]), Offset(x, y + 5f)) {
                drawLine(a, Offset(x, y + 5f), ends[i], 2f, StrokeCap.Round)
            }
        }
    }
}

/** tip5 総角「息を吸う房」: 五本が --k 比例で扇に開く（t5-s）＋頭玉の浮き（t5-b）＋墨の濃淡（t5-g）。
 *  filter: brightness/saturate は Canvas に等価物が無いため、HSL で色そのものを毎フレーム算出して渡す
 *  （モック申し送り⑦: brightness→明度×・saturate→彩度×。「それらしい別の動き」への差し替えではない）。 */
private fun DrawScope.drawTip5(x: Float, y: Float, hue: Int, accentLightness: Float, t: Float) {
    val bright = ShioriT5.FILTER.aAt(t)
    val sat = ShioriT5.FILTER.bAt(t)
    // 生成色の正本＝shioriAccentFor（S=0.48・L=accentLightness）。その S/L に濃淡係数を掛ける。
    val col = hslToColor(
        hue.toFloat(),
        (0.48f * sat).coerceIn(0f, 1f),
        (accentLightness * bright).coerceIn(0f, 1f),
    )
    translate(0f, ShioriT5.HEAD_TY.aAt(t)) {
        drawCircle(col, 4f, Offset(x, y + 4f))
    }
    for (j in 0..4) {
        val kf = j - 2 // --k: -2..2（外側ほど大きく開く）
        rotate(kf * ShioriT5.STRAND.aAt(t - ShioriT5.STRAND_DOFFS[j]), Offset(x, y + 7f)) {
            drawLine(col, Offset(x, y + 7f), Offset(x + kf * 4f, y + 24f), 1.6f, StrokeCap.Round)
        }
    }
}

/** tip6 蝶結び「眠る蝶の呼吸」: 左右の羽の交互 scale（t6-L/R）＋張った羽の墨が薄らぐ opacity（t6-oL/oR・.28s 遅れ）。芯は不動。 */
private fun DrawScope.drawTip6(x: Float, y: Float, a: Color, t: Float) {
    val wingL = Path().apply {
        moveTo(x, y + 4f); quadraticTo(x - 11f, y - 2f, x - 9f, y + 4f); quadraticTo(x - 11f, y + 10f, x, y + 4f)
    }
    val wingR = Path().apply {
        moveTo(x, y + 4f); quadraticTo(x + 11f, y - 2f, x + 9f, y + 4f); quadraticTo(x + 11f, y + 10f, x, y + 4f)
    }
    scale(ShioriT6.WING_L.aAt(t), ShioriT6.WING_L.bAt(t), Offset(x, y + 4f)) {
        drawPath(wingL, a.copy(alpha = a.alpha * ShioriT6.ALPHA_L.aAt(t)), style = strokeRound(1.8f))
    }
    scale(ShioriT6.WING_R.aAt(t), ShioriT6.WING_R.bAt(t), Offset(x, y + 4f)) {
        drawPath(wingR, a.copy(alpha = a.alpha * ShioriT6.ALPHA_R.aAt(t)), style = strokeRound(1.8f))
    }
    drawCircle(a, 2f, Offset(x, y + 4f)) // 芯の玉は不動（世界の支点＝モック注記）
}

/** tip7 玉と尾「気まぐれの尾」: 尾の非対称 rotate（t7-t）＋払い際の墨切れ dashoffset（t7-ink・.12s 遅れ）＋玉の沈み（t7-b）。 */
private fun DrawScope.drawTip7(x: Float, y: Float, a: Color, t: Float) {
    translate(0f, ShioriT7.BALL_TY.aAt(t)) {
        drawCircle(a, 4f, Offset(x, y + 5f))
    }
    rotate(ShioriT7.TAIL.aAt(t), Offset(x, y + 9f)) {
        val phase = ShioriT7.INK.aAt(t)
        // dasharray 11 11 は尾の路長（y+9→y+20＝11）ちょうど＝phase 0 の静止時は隙間ゼロで意匠不変。
        // phase>0 で穂先側から墨が切れる（CSS stroke-dashoffset と Skia phase は同方向＝先端が紙を離れる）。
        drawLine(
            a, Offset(x, y + 9f), Offset(x, y + 20f), 2f, StrokeCap.Round,
            pathEffect = if (phase == 0f) null else PathEffect.dashPathEffect(floatArrayOf(11f, 11f), phase),
        )
    }
}

/** tip8 数珠「繰る手」: 3珠の沈み＋膨らみの順送り（t8＋doff 0/.22/.44・上→下）＋紐全体の遅れ沈み（t8-g）。 */
private fun DrawScope.drawTip8(x: Float, y: Float, a: Color, t: Float) {
    translate(0f, ShioriT8.GROUP_TY.aAt(t)) {
        val cys = floatArrayOf(y + 6f, y + 13f, y + 20f)
        for (i in 0..2) {
            val td = t - ShioriT8.BEAD_DOFFS[i]
            translate(0f, ShioriT8.BEAD_TY.aAt(td)) {
                val sc = ShioriT8.BEAD_SC.aAt(td)
                scale(sc, sc, Offset(x, cys[i])) {
                    drawCircle(a, 2.6f, Offset(x, cys[i]))
                }
            }
        }
    }
}
