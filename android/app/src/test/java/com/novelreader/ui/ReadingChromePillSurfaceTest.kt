package com.novelreader.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「最上部へ」ピルの地の不変条件を**実際に画素へ描いて**固定する（2026-09-04 裁定・案D の器）。
 *
 * 守るのは1つだけ＝**落ち影が器の内側を濁さないこと**。
 *
 * なぜソース走査でも semantics でもなく画素か: これは純粋に**描画の重なり順**の話で、意味論には一切出ない。
 * CSS の `box-shadow`（非 inset）は border-box の**外側にしか描かれない**のに対し、Compose の
 * `Modifier.shadow` / `graphicsLayer.shadowElevation` も素朴な `drawBehind { ぼかし矩形; 面 }` も影を
 * **面の真下**へ敷く。面が不透明なら差は出ないが、この器は α.78 の**半透明**なので影が透けて内側が暗くなる。
 * そして α.78 は「ADR 0014-D の 4.5:1 を全テーマで割らない最も透けた値」（明 5.02:1）という**計算**で
 * 選ばれた値で、その計算に影の項は入っていない＝内側が濁ると裁定の根拠ごと崩れる。
 * それでいて**見た目は「少し暗い」だけ**なので、人の目視でもコントラスト以外のテストでも捕まらない。
 *
 * ⚠️ 影の α は本番値（.08）ではなく**わざと濃い値**で測る。.08 のままだと打ち抜きを外しても中心の差は
 * 1%未満で、検知器が「壊れても緑」になる。ここで確かめたいのは値ではなく**重なり順の機構**なので、
 * 機構が壊れたら必ず落ちる感度まで濃くする。
 *
 * ⚠️ 各検査に**陽性確認**を対で置く（[打ち抜かない素朴な形は内側が濁る]）。打ち抜かない形を同じ経路で
 * 描いて「確かに濁る」ことを毎回機械確認する＝検知器が死んでいるのにテストは緑、を防ぐ
 *（このリポジトリの検知器はすべてこの流儀＝NavigationBarBandContractTest と同じ）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReadingChromePillSurfaceTest {

    @Test
    fun `落ち影は器の内側を濁さない`() {
        val map = render(punchOut = true)
        // 内側＝面（黒 α.78）が白地に載っただけの色。影の項が入っていなければこの値と一致する。
        assertEquals(
            "ピル中心が期待合成色と違う＝影が面の下へ入り込んで内側を暗くしている" +
                "（CSS の box-shadow は要素自身の地を濁さない＝α.78 の 4.5:1 計算の前提）",
            expectedInside(BACKDROP, FACE),
            map.inside().red,
            TOLERANCE,
        )
    }

    @Test
    fun `落ち影は器の外側には出ている`() {
        val map = render(punchOut = true)
        // 内側だけを見ると「影を一度も描いていない」実装でも緑になる。外側の暗さを対で見て、
        // 打ち抜きが「影ごと消す」形に退行していないことを押さえる。
        assertTrue(
            "ピルの真下に影が出ていない＝打ち抜きが影そのものを消している（器が地から浮かない）",
            map.outside().red < BACKDROP.red - MIN_SHADOW_DARKENING,
        )
    }

    @Test
    fun `陽性確認 — 打ち抜かない素朴な形は内側が濁る`() {
        val expected = expectedInside(BACKDROP, FACE)
        val naive = render(punchOut = false).inside().red
        assertTrue(
            "打ち抜きを外しても内側の色が変わらない＝この検査は死んでいる（実測 $naive / 期待 $expected）",
            expected - naive > MIN_DETECTABLE_TINT,
        )
    }

    // ── 暗面（J ダーク相当）＝同じ機構を反対の極性でも見る ──

    @Test
    fun `暗面でも落ち影は器の内側を濁さない`() {
        val map = render(punchOut = true, backdrop = DARK_BACKDROP, face = DARK_FACE)
        assertEquals(
            "暗面のピル中心が期待合成色と違う＝影が面の下へ入り込んでいる",
            expectedInside(DARK_BACKDROP, DARK_FACE),
            map.inside().red,
            TOLERANCE,
        )
    }

    @Test
    fun `暗面でも落ち影は器の外側には出ている`() {
        val map = render(punchOut = true, backdrop = DARK_BACKDROP, face = DARK_FACE)
        assertTrue(
            "暗面でピルの真下に影が出ていない＝打ち抜きが影そのものを消している",
            map.outside().red < DARK_BACKDROP.red - MIN_SHADOW_DARKENING,
        )
    }

    // ⚠️ **暗面には陽性確認を置かない**（置けない）。黒い影で既に暗い地をさらに暗くしても合成色はほとんど動かず、
    // 打ち抜きを外したときの内側の差は実測 0.013 程度＝8bit 量子化とアンチエイリアスのぶれに埋もれる。
    // ここで無理に閾値を下げると「ぶれで落ちたり通ったりする検査」になり、検知器としてはむしろ死ぬ。
    // これは手抜きではなく**この不変条件のリスクが明面に偏っている**ことの現れで、裁定が実測した
    //「限界は暗色でなく明色側が先に来る（ピル字 #4A4F58 が中間色のため）」と同じ非対称さである。
    // だから陽性確認は明面（上の3本目）が担い、暗面は「同じ機構が両極性で成立する」ことだけを見る。

    // ────────────────────────────────────────────────────────
    // 描画（本番と同じ関数を通す。陽性確認だけ「打ち抜かない形」を本文でなくここで組む
    //   ＝本番コードにテスト専用の分岐を持ち込まないため）
    // ────────────────────────────────────────────────────────

    private fun PixelMap.inside(): Color = this[PAD + PILL_W / 2, PAD + PILL_H / 2]

    /** ピル下端の少し外＝ぼかしの penumbra 内。offsetY ぶん下へずれるのでそこを見る。 */
    private fun PixelMap.outside(): Color = this[PAD + PILL_W / 2, PAD + PILL_H + 4]

    /** 面（α.78）が地へ載っただけの色＝影の項を含まない期待値。 */
    private fun expectedInside(backdrop: Color, face: Color): Float =
        face.red * FACE_ALPHA + backdrop.red * (1f - FACE_ALPHA)

    private fun render(
        punchOut: Boolean,
        backdrop: Color = BACKDROP,
        face: Color = FACE,
    ): PixelMap {
        val w = PILL_W + PAD * 2
        val h = PILL_H + PAD * 2
        val bitmap = ImageBitmap(w, h)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val density = Density(1f) // px=dp＝較正値をそのまま px として読めるようにする
        val paints = ReadingChromePillPaints()

        scope.draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawRect(backdrop)
        }
        canvas.save()
        canvas.translate(PAD.toFloat(), PAD.toFloat())
        scope.draw(density, LayoutDirection.Ltr, canvas, Size(PILL_W.toFloat(), PILL_H.toFloat())) {
            if (punchOut) {
                drawReadingChromePill(
                    paints = paints,
                    color = face,
                    faceAlpha = FACE_ALPHA,
                    shadowAlpha = SHADOW_ALPHA,
                    shadowBlurPx = BLUR_PX,
                    shadowOffsetYPx = OFFSET_Y_PX,
                    cornerRadiusPx = PILL_H / 2f,
                )
            } else {
                drawNaive(paints, face)
            }
        }
        canvas.restore()
        return bitmap.toPixelMap()
    }

    /** 打ち抜きを持たない素朴な形＝影を面の真下へ敷く（`Modifier.shadow` 相当の重なり順）。 */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNaive(
        paints: ReadingChromePillPaints,
        face: Color,
    ) {
        drawIntoCanvas { canvas ->
            paints.shadow.color = Color.Black.copy(alpha = SHADOW_ALPHA).toArgb()
            paints.shadow.maskFilter = BlurMaskFilter(BLUR_PX, BlurMaskFilter.Blur.NORMAL)
            canvas.nativeCanvas.drawRoundRect(
                0f, OFFSET_Y_PX, size.width, size.height + OFFSET_Y_PX,
                PILL_H / 2f, PILL_H / 2f, paints.shadow,
            )
        }
        drawRoundRect(
            color = face.copy(alpha = FACE_ALPHA),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(PILL_H / 2f),
        )
    }

    private companion object {
        const val PAD = 40
        const val PILL_W = 160
        const val PILL_H = 48

        val BACKDROP = Color.White
        val FACE = Color.Black
        const val FACE_ALPHA = 0.78f

        /** J ダーク相当（`--bg` #0F1712 / ピル字 #C8D2C4 の明度域）。値そのものでなく極性を見るための地。 */
        val DARK_BACKDROP = Color(0xFF0F1712)
        val DARK_FACE = Color(0xFFC8D2C4)

        /** 本番は .08。機構の検査なので、壊れたら必ず落ちる感度まで濃くする（KDoc の why 参照）。 */
        const val SHADOW_ALPHA = 0.80f
        const val BLUR_PX = 10f
        const val OFFSET_Y_PX = 2f

        /** 8bit 量子化とアンチエイリアスのぶれ（±2/255）。 */
        const val TOLERANCE = 0.008f

        /** 外側に影が「在る」と言える最小の暗さ（4/255 相当）。 */
        const val MIN_SHADOW_DARKENING = 0.016f

        /** 打ち抜きを外したとき内側が沈むべき最小量。理論値は .8×.22＝.176 なのでその半分を下限に取る。 */
        const val MIN_DETECTABLE_TINT = 0.08f
    }
}
