package com.novelreader.ui

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

// 「最上部へ」ピルの器の較正値（正本 docs/design-candidates/reading-backtotop-D.html `.toppill` の写経）。
// 影 = box-shadow 0 2px 10px rgba(0,0,0,.08)＝offsetY 2 / blur 10 / 黒 8%。px→dp は既存モック規約の 1:1 写像。
private val ChromePillShadowOffsetY = 2.dp
private val ChromePillShadowBlur = 10.dp

/**
 * 影の黒み。正本は D（明面）の値しか持たない。
 *
 * ⚠️ 比較モック `candidates/reading-toppill-translucency-candidates.html` は J ダーク列に `.45` を使っているが、
 * あれは**比較用の頁**で、`skins/reading-J.html` は `.toppill` を定義していない＝暗面の影は**正本を持たない**。
 * 正本の無い値を実装へ持ち込むと「モックが正本」の順序が逆転するので、全スキンで D の `.08` を敷く。
 * 暗面ではこの黒みはほぼ見えないが、器の輪郭は 1px ヘアライン枠が担うので欠けは出ない
 * （影は輪郭の**補助**という位置づけ＝正本の why を参照）。暗面専用の影が要るなら先にモックを起こすこと。
 */
private const val CHROME_PILL_SHADOW_ALPHA = 0.08f

/** 落ち影と打ち抜き用の Paint。draw 毎の生成を避けて Modifier 構築時に1度だけ作る（settingsPeekRow と同型）。 */
internal class ReadingChromePillPaints {
    val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    /** ピル形をレイヤーから消すための Paint。色は無関係（CLEAR は宛先を透明にするだけ）。 */
    val punch = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
}

/**
 * 「最上部へ」ピルの地を〈**外側だけの落ち影** → 半透明の面〉の順で描く。
 *
 * ⚠️ **この関数の存在理由＝影が自分の地を濁さないこと**。CSS の `box-shadow`（非 inset）は border-box の
 * **外側にしか描かれず**、要素自身の背景を暗くしない。ところが Compose の `Modifier.shadow` /
 * `graphicsLayer.shadowElevation` も、素朴な `drawBehind { ぼかし矩形; 面 }` も、影を**面の真下**へ敷く。
 * 面が不透明なら見えないので誰も気づかないが、2026-09-04 裁定でこのピルは **α.78 の半透明**になった＝
 * 影が面を透けて器の内側を暗くする。それは見た目の粗ではなく**裁定の根拠を壊す**:
 * α.78 は「ADR 0014-D の 4.5:1 を全テーマで割らない最も透けた値」（明 5.02:1）として選ばれており、
 * その計算は**影の項を含んでいない**。内側が暗くなればピル字 #4A4F58（中間色）の側が先に沈む。
 * だから影は別レイヤーへ描き、ピル形を [PorterDuff.Mode.CLEAR] で打ち抜いてから合成する。
 *
 * ⚠️ ここを `Modifier.shadow` や `shadowElevation` へ「簡単にした」書き換えをすると、**見えが少し暗くなるだけで
 * テストは緑のまま通る**（描画結果の話で semantics には出ない）。それを防ぐのが
 * `ReadingChromePillSurfaceTest`＝実際に画素へ描いて〈内側は濁らない／外側には影が在る〉を両方向で見る。
 *
 * ⚠️ 退き方: 実機で見てなお濁る・描画が破綻するなら、**影を落として枠だけ**にする（α.78 は据え置き）。
 * 影は輪郭の補助で、輪郭の主は 1px ヘアライン枠＝落としても裁定の主要部は残る（正本の why と同じ退き方）。
 *
 * @param color ピルの地色（スキントークン `navBackground`）。
 * @param faceAlpha 面に載せる α（2026-09-04 裁定＝[com.novelreader.ui.theme.ChromeTopPillAlpha]）。
 */
internal fun Modifier.readingChromePillSurface(
    color: Color,
    faceAlpha: Float,
): Modifier {
    val paints = ReadingChromePillPaints()
    return drawBehind {
        drawReadingChromePill(
            paints = paints,
            color = color,
            faceAlpha = faceAlpha,
            shadowAlpha = CHROME_PILL_SHADOW_ALPHA,
            shadowBlurPx = ChromePillShadowBlur.toPx(),
            shadowOffsetYPx = ChromePillShadowOffsetY.toPx(),
            // RoundedCornerShape(50)＝高さの半分＝丸ピル。器の形は枠・ripple と1つに揃える。
            cornerRadiusPx = size.height / 2f,
        )
    }
}

/**
 * [readingChromePillSurface] の実描画。`Modifier` から切り離してあるのは、実際に画素へ描いて
 * 「内側が濁らない」を検証できるようにするため（`ReadingChromePillSurfaceTest`）。
 */
internal fun DrawScope.drawReadingChromePill(
    paints: ReadingChromePillPaints,
    color: Color,
    faceAlpha: Float,
    shadowAlpha: Float,
    shadowBlurPx: Float,
    shadowOffsetYPx: Float,
    cornerRadiusPx: Float,
) {
    if (shadowAlpha > 0f && shadowBlurPx > 0f) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // 影を別レイヤーへ隔離する。ここで saveLayer を挟まないと CLEAR が背後の本文まで消してしまう
            //（CLEAR は「重ねる」ではなく「宛先を透明にする」ため、対象を影だけに限る必要がある）。
            val layer = native.saveLayer(null, null)
            paints.shadow.color = Color.Black.copy(alpha = shadowAlpha).toArgb()
            paints.shadow.maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
            native.drawRoundRect(
                0f,
                shadowOffsetYPx,
                size.width,
                size.height + shadowOffsetYPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paints.shadow,
            )
            // ピル形を抜く＝CSS の box-shadow が border-box の外側にしか出ないことの再現（この関数の要）。
            native.drawRoundRect(0f, 0f, size.width, size.height, cornerRadiusPx, cornerRadiusPx, paints.punch)
            native.restoreToCount(layer)
        }
    }
    // 面（読書地色 × 裁定 α）。影を抜いた後に載せるので、器の内側には背後の本文しか透けない。
    drawRoundRect(
        color = color.copy(alpha = faceAlpha),
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(cornerRadiusPx),
    )
}
