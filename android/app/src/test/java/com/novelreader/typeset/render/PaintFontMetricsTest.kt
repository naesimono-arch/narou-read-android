package com.novelreader.typeset.render

import android.graphics.Paint
import android.graphics.Typeface
import com.novelreader.model.TextSegment
import com.novelreader.typeset.CharClass
import com.novelreader.typeset.DefaultVerticalTypesetter
import com.novelreader.typeset.TypesetConstraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PaintFontMetrics] の縦送りが「向き（CharClass）」で決まることの回帰
 * （golden 監査 2026-08-06 G-3: 正立で描かれる単独半角数字に回転前提の半角幅を返し、
 * 「3日」「1人」で字面が接触していた）。
 *
 * なぜ FakeMonospaceMetrics で書かないか: 等幅フェイクは全ユニットに em を返すため
 * 「半角と全角の実寸差」が存在せず、このバグを構造的に検出できない。
 * 実 Paint（Robolectric NATIVE）で実寸差を持ち込んで固定する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PaintFontMetricsTest {

    private val metrics = PaintFontMetrics()
    private val fontSize = 48f

    /** PaintFontMetrics 内部と同条件の Paint（SERIF）。期待値の実測に使う。 */
    private fun referencePaint(): Paint = Paint().apply {
        typeface = Typeface.SERIF
        textSize = fontSize
        isAntiAlias = true
    }

    @Test
    fun uprightSingleAsciiOccupiesFullEmCell() {
        // 単独ランの「3」は正立で描かれ、字面が em 全高に及ぶ＝縦の占有も em マス。
        // 回転前提の半角幅（約0.5em）を返すと前後の字と接触する（G-3 の破綻条件そのもの）。
        assertEquals(fontSize, metrics.verticalAdvance("3", CharClass.UPRIGHT, fontSize), 0.0001f)
    }

    @Test
    fun rotatedSingleAsciiOccupiesMeasuredWidth() {
        // 4字以上ランの各字（ROTATE）は 90 度回転＝回転前の横幅がそのまま縦の占有（従来どおり）。
        // em 化すると欧文横倒しが間延びする退行になるため、こちら側も固定する。
        val adv = metrics.verticalAdvance("3", CharClass.ROTATE, fontSize)
        assertEquals(referencePaint().measureText("3"), adv, 0.0001f)
        assertTrue("半角数字の横幅は em より狭い前提（フォント環境の確認）: $adv", adv < fontSize)
    }

    @Test
    fun tateChuYokoRunKeepsSquareEmCell() {
        // 縦中横 run は 1 マス（正方セル）に収める契約＝縦送りは em（従来どおり）。
        assertEquals(fontSize, metrics.verticalAdvance("12", CharClass.TATE_CHU_YOKO, fontSize), 0.0001f)
    }

    @Test
    fun typesetKeepsEmCellForSingleDigitBetweenKanji() {
        // 「月3日」: 単独数字は UPRIGHT のまま em セルを占有し、各セルは連接（前セル底＝次セル天）。
        // 正立の字面は em セル内に収まる（VertGlyphRendererTest 側で画素固定）ので、
        // セルが em でありさえすれば「月」「3」「日」の字面接触は起きない。
        val layout = DefaultVerticalTypesetter(metrics).typeset(
            listOf(TextSegment.Plain("月3日")),
            TypesetConstraints(
                columnHeightPx = 480f,
                fontSizePx = fontSize,
                rubyFontSizePx = fontSize / 2f,
                columnAdvancePx = fontSize * 1.5f,
                indentFirstColumn = false,
            ),
        )
        assertEquals(3, layout.glyphs.size)
        val digit = layout.glyphs[1]
        assertEquals("3", digit.text)
        assertEquals(CharClass.UPRIGHT, digit.charClass)
        assertEquals(fontSize, digit.advancePx, 0.0001f)
        assertEquals(layout.glyphs[0].y + layout.glyphs[0].advancePx, digit.y, 0.0001f)
        assertEquals(digit.y + digit.advancePx, layout.glyphs[2].y, 0.0001f)
    }

    @Test
    fun typesetKeepsHalfWidthCellsForLongAsciiRun() {
        // 「2026」（4字以上ラン）は各字 ROTATE＝半角幅セルのまま。単独ラン修正の巻き添えで
        // em 化していないことを固定する（横倒しの数字列が間延びする退行の防止）。
        val layout = DefaultVerticalTypesetter(metrics).typeset(
            listOf(TextSegment.Plain("2026")),
            TypesetConstraints(
                columnHeightPx = 480f,
                fontSizePx = fontSize,
                rubyFontSizePx = fontSize / 2f,
                columnAdvancePx = fontSize * 1.5f,
                indentFirstColumn = false,
            ),
        )
        assertEquals(4, layout.glyphs.size)
        for (g in layout.glyphs) {
            assertEquals(CharClass.ROTATE, g.charClass)
            assertTrue("回転字は半角幅セルのまま: ${g.advancePx}", g.advancePx < fontSize)
        }
    }
}
