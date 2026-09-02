// カード上部の線画（正本モックの `.ocard .fig svg`＝viewBox 0 0 120 84・stroke 1.5・round cap/join）。
// 絵は意匠なので発明しない＝正本の path をそのまま座標で写経し、色だけトークン経由にする。
package com.novelreader.ui.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** どのカードにどの線画が付くかは正本の 1:1 対応。 */
internal enum class IntroFigure { SHELF, ORIENTATION, TWO_WAYS, TAP, VERTICAL, SEARCH }

/** 正本 svg の viewBox 幅・高さ（この単位系で座標を書き、描画時に実寸へ写す）。 */
private const val FIG_W = 120f
private const val FIG_H = 84f

/** 正本 `.ocard .fig svg{stroke-width:1.5}`。 */
private const val FIG_STROKE = 1.5f

/** 正本 `.ocard .fig svg .soft{opacity:.55}`。 */
private const val SOFT_ALPHA = 0.55f

@Composable
internal fun IntroFigureArt(figure: IntroFigure, modifier: Modifier = Modifier) {
    // 主線は藍アクセント＝primary。soft は装飾専用の補助線なので onSurfaceVariant（意味は運ばない）。
    val accent = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(FIG_W.dp, FIG_H.dp)) {
        val u = size.width / FIG_W // viewBox 単位 → px
        val art = FigureScope(this, u, accent, soft)
        when (figure) {
            IntroFigure.SHELF -> art.shelf()
            IntroFigure.ORIENTATION -> art.orientation()
            IntroFigure.TWO_WAYS -> art.twoWays()
            IntroFigure.TAP -> art.tap()
            IntroFigure.VERTICAL -> art.vertical()
            IntroFigure.SEARCH -> art.search()
        }
    }
}

/**
 * viewBox 座標で書けるようにする薄いラッパ。素の DrawScope へ毎回 `* u` を書くと
 * 写経の突き合わせ（正本の path 座標と 1:1 か）が読めなくなるため。
 */
private class FigureScope(
    private val scope: DrawScope,
    private val u: Float,
    private val accent: Color,
    private val soft: Color,
) {
    private fun stroke(dash: Pair<Float, Float>? = null) = Stroke(
        width = FIG_STROKE * u,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(it.first * u, it.second * u)) },
    )

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, alpha: Float = 1f) {
        scope.drawLine(
            color = color, alpha = alpha,
            start = Offset(x1 * u, y1 * u), end = Offset(x2 * u, y2 * u),
            strokeWidth = FIG_STROKE * u, cap = StrokeCap.Round,
        )
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, dash: Pair<Float, Float>? = null) {
        scope.drawRoundRect(
            color = color,
            topLeft = Offset(x * u, y * u),
            size = Size(w * u, h * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * u, r * u),
            style = stroke(dash),
        )
    }

    private fun circle(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f) {
        scope.drawCircle(color = color, alpha = alpha, radius = r * u, center = Offset(cx * u, cy * u), style = stroke())
    }

    /** 1 枚目「はじめに」＝棚に並ぶ 4 冊（高さ違い）と棚板。 */
    fun shelf() {
        rect(10f, 14f, 22f, 48f, 2f, accent)
        rect(36f, 20f, 22f, 42f, 2f, accent)
        rect(62f, 10f, 22f, 52f, 2f, accent)
        rect(88f, 24f, 22f, 38f, 2f, accent)
        line(6f, 68f, 114f, 68f, soft, SOFT_ALPHA)
        line(21f, 26f, 21f, 36f, soft, SOFT_ALPHA)
        line(47f, 30f, 47f, 40f, soft, SOFT_ALPHA)
        line(73f, 22f, 73f, 32f, soft, SOFT_ALPHA)
        line(99f, 34f, 99f, 44f, soft, SOFT_ALPHA)
    }

    /**
     * 2 枚目「どちらで読みますか」＝横罫の面（横書き）と縦罫の面（縦書き）。
     *
     * **枠は 2 枚とも実線**にする——[twoWays] は片方を破線にして〈アプリ／なろう〉という
     * **届く/届かない**を描き分けているが、こちらは<b>どちらも等しく選べる</b>のが意味なので、
     * 同じ破線語彙を持ち込むと「縦書きは劣った側」と読める。罫の向きだけで差を出す。
     */
    fun orientation() {
        rect(8f, 14f, 46f, 56f, 5f, accent)
        line(16f, 28f, 46f, 28f, soft, SOFT_ALPHA)
        line(16f, 38f, 46f, 38f, soft, SOFT_ALPHA)
        line(16f, 48f, 38f, 48f, soft, SOFT_ALPHA)
        line(16f, 58f, 46f, 58f, soft, SOFT_ALPHA)
        rect(66f, 14f, 46f, 56f, 5f, accent)
        line(104f, 22f, 104f, 62f, soft, SOFT_ALPHA)
        line(96f, 22f, 96f, 62f, soft, SOFT_ALPHA)
        line(88f, 22f, 88f, 54f, soft, SOFT_ALPHA)
        line(80f, 22f, 80f, 62f, soft, SOFT_ALPHA)
    }

    /** 3 枚目「読みかたは、2 通り」＝実線の面（アプリ）と破線の面（なろう）。 */
    fun twoWays() {
        rect(8f, 14f, 46f, 56f, 5f, accent)
        line(16f, 28f, 46f, 28f, soft, SOFT_ALPHA)
        line(16f, 38f, 46f, 38f, soft, SOFT_ALPHA)
        line(16f, 48f, 36f, 48f, soft, SOFT_ALPHA)
        line(22f, 60f, 40f, 60f, accent)
        rect(66f, 14f, 46f, 56f, 5f, accent, dash = 5f to 4f)
        dashedLine(74f, 24f, 104f, 24f, 4f to 3f)
        dashedLine(74f, 34f, 104f, 34f, 5f to 4f)
        dashedLine(74f, 44f, 92f, 44f, 5f to 4f)
        dashedLine(86f, 60f, 100f, 60f, 4f to 3f)
    }

    private fun dashedLine(x1: Float, y1: Float, x2: Float, y2: Float, dash: Pair<Float, Float>) {
        scope.drawLine(
            color = soft, alpha = SOFT_ALPHA,
            start = Offset(x1 * u, y1 * u), end = Offset(x2 * u, y2 * u),
            strokeWidth = FIG_STROKE * u, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.first * u, dash.second * u)),
        )
    }

    /** 4 枚目「横書きで読む」＝画面の中央に置かれたタップの波紋。 */
    fun tap() {
        rect(30f, 4f, 60f, 76f, 7f, accent)
        line(34f, 16f, 86f, 16f, soft, SOFT_ALPHA)
        line(34f, 68f, 86f, 68f, soft, SOFT_ALPHA)
        circle(60f, 42f, 17f, accent, alpha = 0.45f)
        circle(60f, 42f, 9f, accent)
    }

    /** 5 枚目「縦書きで読む」＝右から左へ並ぶ行と、左向きの読み進め矢印。 */
    fun vertical() {
        line(96f, 12f, 96f, 70f, soft, SOFT_ALPHA)
        line(84f, 12f, 84f, 70f, soft, SOFT_ALPHA)
        line(72f, 12f, 72f, 70f, soft, SOFT_ALPHA)
        line(60f, 20f, 60f, 62f, accent)
        line(48f, 20f, 48f, 62f, accent)
        line(40f, 76f, 18f, 76f, accent)
        line(25f, 69f, 18f, 76f, accent)
        line(18f, 76f, 25f, 83f, accent)
        line(14f, 12f, 110f, 12f, soft, alpha = 0.35f)
    }

    /** 6 枚目「さがして、本棚に入れる」＝虫めがねと、選ばれた検索範囲のチップ。 */
    fun search() {
        circle(40f, 34f, 17f, accent)
        line(52f, 46f, 65f, 59f, accent)
        rect(74f, 16f, 34f, 10f, 5f, soft.copy(alpha = SOFT_ALPHA))
        rect(74f, 32f, 34f, 10f, 5f, accent)
        rect(74f, 48f, 34f, 10f, 5f, soft.copy(alpha = SOFT_ALPHA))
        line(18f, 70f, 102f, 70f, soft, alpha = 0.5f)
    }
}
