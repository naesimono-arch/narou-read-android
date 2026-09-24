package com.novelreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.MotionDurationEdgeFade
import com.novelreader.ui.theme.MotionEasingEdgeFade

/**
 * 横スクロール行の「まだ続く」を示す端フェード（正本モック `skins/bookshelf-K.html` /
 * `skins/bookshelf-D.html` の `.chipsrow .fade`・2026-08-20 ユーザー裁定①「強度＝中」）。
 *
 * 直す症状: fontScale 2.0 で状態フィルタの「読了」が右へ隠れ、**横へ送れること自体に気づけない**
 * （送る手段は K/D とも既にあり、欠けているのは手掛かりだけ）。
 *
 * 採用の制約がそのまま実装契約になっている:
 *  ・点灯条件は [ScrollState.canScrollBackward] / [ScrollState.canScrollForward] だけ
 *    （モック末尾の JS も `scrollLeft` と `scrollWidth-clientWidth` で同じ問いを立てている）。
 *    **溢れていなければ両方 false ＝ 1枚も描かない＝ fontScale 1.0 の版面は 1px も動かない。**
 *  ・幅は [EdgeFadeWidth] の固定 dp。フォントスケールで伸ばさない——伸ばすと 2.0 で溶ける量が増え、
 *    隠れているチップ（＝手掛かりを与えたい当の相手）まで食ってしまう。
 *
 * 版面を動かさないための構造: レイアウトノードを1つも足さず、draw だけで乗せる。
 * 呼び出しは **`horizontalScroll` の直前**（＝この修飾子のノード寸法＝スクロールの可視域）に置く。
 * [drawWithCache] を使うのは、点灯条件（フレームレートで動く state）の読み取りを draw フェーズへ
 * 遅延させ、ブラシ生成だけを寸法・色でキャッシュするため（スクロール中の再コンポーズを起こさない）。
 *
 * @param baseColor 溶かし込む地色。透明側も RGB はこの色のまま alpha だけ 0 にする
 *   （[Color.Transparent]＝黒の透明を混ぜると端が灰ばむ＝モックの同注意書きと同じ理由）。
 * @param bottomInset 行が自分の内側に持つ下パディング。フェードは**チップの帯だけ**を溶かし
 *   下の余白には掛けないので、その高さぶんを band から除く（下パディングをスクロール器の外側で
 *   受けている呼び出し側は 0＝除くべき高さを持っていない）。
 */
@Composable
internal fun Modifier.horizontalScrollEdgeFade(
    scrollState: ScrollState,
    baseColor: Color,
    bottomInset: Dp = 0.dp,
): Modifier {
    // derivedStateOf で「境界を跨いだ瞬間」だけに畳む。canScrollBackward は value の直読みなので、
    // 素で composition から読むとスクロール毎フレーム再コンポーズしてしまう（点灯の可否は変わらないのに）。
    val leadingVisible = remember(scrollState) { derivedStateOf { scrollState.canScrollBackward } }
    val trailingVisible = remember(scrollState) { derivedStateOf { scrollState.canScrollForward } }
    // モック `.fade{transition:opacity .18s ease}` の翻訳。State のまま持ち回り draw で読む
    // （`by` で composition から読むとアニメの毎フレームが再コンポーズになる）。
    val leadingAlpha = animateFloatAsState(
        targetValue = if (leadingVisible.value) 1f else 0f,
        animationSpec = tween(MotionDurationEdgeFade, easing = MotionEasingEdgeFade),
        label = "scrollEdgeFadeLeading",
    )
    val trailingAlpha = animateFloatAsState(
        targetValue = if (trailingVisible.value) 1f else 0f,
        animationSpec = tween(MotionDurationEdgeFade, easing = MotionEasingEdgeFade),
        label = "scrollEdgeFadeTrailing",
    )
    return this.drawWithCache {
        val fadeWidth = minOf(EdgeFadeWidth.toPx(), size.width)
        val bandHeight = size.height - bottomInset.toPx()
        // 地色 100% から端へ向かって抜くカーブ（正本の `--fade-stops`）。K/D で同一＝ここが唯一の在処。
        val stops = arrayOf(
            0f to baseColor,
            EdgeFadeSolidStop to baseColor,
            0.38f to baseColor.copy(alpha = 0.86f),
            0.60f to baseColor.copy(alpha = 0.55f),
            0.80f to baseColor.copy(alpha = 0.22f),
            1f to baseColor.copy(alpha = 0f),
        )
        // 右端は同じ停止点を右→左へ向けて張る（startX>endX＝配列を反転して持たない＝値の二重管理を避ける）。
        val leadingBrush = Brush.horizontalGradient(*stops, startX = 0f, endX = fadeWidth)
        val trailingBrush =
            Brush.horizontalGradient(*stops, startX = size.width, endX = size.width - fadeWidth)
        onDrawWithContent {
            drawContent()
            if (bandHeight <= 0f || fadeWidth <= 0f) return@onDrawWithContent
            if (leadingAlpha.value > 0f) {
                drawRect(
                    brush = leadingBrush,
                    topLeft = Offset.Zero,
                    size = Size(fadeWidth, bandHeight),
                    alpha = leadingAlpha.value,
                )
            }
            if (trailingAlpha.value > 0f) {
                drawRect(
                    brush = trailingBrush,
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, bandHeight),
                    alpha = trailingAlpha.value,
                )
            }
        }
    }
}

/**
 * 端フェードの帯幅（正本 `--fade-w:56px`＝モックの phone 幅 360px は実機実効 360dp）。
 * 案「弱」32px は端で一気に抜けて「薄い帯が乗っている」としか読めず、案「強」80px は溶け際が伸びて
 * 目立ちすぎた＝3案の目視比較の結論（2026-08-20 裁定）。fontScale に追従させてはいけない値。
 */
private val EdgeFadeWidth = 56.dp

/** 帯の内側 18%（≈10dp）は地色のまま保持する（正本 `--fade-stops` の第2停止点）。ここが「中」たる所以で、
 *  線形（弱）との差は幅でなくこの平坦区間＝スクロール器のクリップ端をこの区間が覆い隠す。 */
private const val EdgeFadeSolidStop = 0.18f
