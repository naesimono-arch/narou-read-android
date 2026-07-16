package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DetectedRules.detect] の単体テストと、本リファクタの目的（形状不変の一律シフト耐性）の直接担保。
 *
 * 合成 CharBox で組んだ複数ページ文書で各項目の検出値・フォールバック発動を検証し、さらに
 * 「全数値を一律シフトした同型文書」でも processPages の出力テキストが完全一致することを実証する
 * （＝生成側が同じレイアウトのまま寸法だけ動かしても抽出が破綻しないことの証明）。
 */
class DetectedRulesTest {

    private fun cb(text: String, size: Double, x0: Double, top: Double, font: String = "R") =
        CharBox(text, font, size, x0, top, top + size)

    // 現行 PDF 形状に相当する寸法群と、それを一律にずらした同型の寸法群。
    private data class Shape(
        val bodySize: Double,
        val rubySize: Double,
        val pageNumSize: Double,
        val pageNumY: Double,
        val step: Double,
        val rubyOffset: Double,
    )

    private val ref = Shape(14.0, 7.0, 12.0, 528.98, 22.68, 14.84)
    private val shifted = Shape(13.8, 6.9, 11.8, 568.98, 21.0, 13.5)

    /**
     * 8 ページ文書を生成する。index 0-2=前付（除外）・3-6=本文・7=末尾（除外）。
     * 全ページにページ番号を置き（検出の再出率を上げる）、本文ページは
     * 一様ステップの本文列 5 本＋2*step のギャップ段落 1 本、ルビ 3 個、先頭ページに題名を持つ。
     */
    private fun buildDoc(s: Shape): List<List<CharBox>> {
        val baseX = 400.0
        fun pageNum() = cb("9", s.pageNumSize, 200.0, s.pageNumY)
        fun bodyCol(text: String, col: Int, ruby: Boolean): List<CharBox> {
            val x0 = baseX - col * s.step
            val out = mutableListOf(cb(text, s.bodySize, x0, 100.0))
            // ルビは親 x0 + rubyOffset（associateRuby の逆算対象）。親と同 top で最近傍紐付け。
            if (ruby) out.add(cb("ル", s.rubySize, x0 + s.rubyOffset, 100.0))
            return out
        }
        fun contentPage(withTitle: Boolean): List<CharBox> {
            val cs = mutableListOf<CharBox>()
            cs += pageNum()
            if (withTitle) cs += cb("章", s.bodySize, baseX + s.step, 60.0, font = "NotoSerif Bold")
            cs += bodyCol("森", 0, ruby = true)
            cs += bodyCol("川", 1, ruby = false)
            cs += bodyCol("海", 2, ruby = true)
            cs += bodyCol("空", 3, ruby = false)
            cs += bodyCol("雲", 4, ruby = true)
            // 段落切れ＝col4 から 2*step 空けた列（開き括弧始まり）。空行 1 行を伴う。
            cs += cb("「", s.bodySize, baseX - 6 * s.step, 100.0)
            return cs
        }
        return listOf(
            listOf(pageNum()), listOf(pageNum()), listOf(pageNum()),  // 0-2 前付（除外）
            contentPage(withTitle = true),                            // 3
            contentPage(false), contentPage(false), contentPage(false), // 4-6
            listOf(pageNum()),                                        // 7 末尾（除外）
        )
    }

    // --- 検出値 ---

    @Test fun detectsReferenceShape() {
        val r = DetectedRules.detect(buildDoc(ref))
        assertEquals(14.0, r.bodySize, 1e-9)
        assertEquals(7.0, r.rubySize, 1e-9)          // 本文×0.5
        assertEquals(12.0, r.pageNumSize, 1e-9)
        assertEquals(529.0, r.pageNumY, 1e-9)        // Math.round(528.98)
        assertEquals(22.68, r.lineStepX, 1e-6)       // 最頻バケット内中央値
        assertEquals(14.84, r.rubyOffsetX, 1e-6)
    }

    @Test fun detectsShiftedShape() {
        val r = DetectedRules.detect(buildDoc(shifted))
        assertEquals(13.8, r.bodySize, 1e-9)
        assertEquals(6.9, r.rubySize, 1e-9)
        assertEquals(11.8, r.pageNumSize, 1e-9)
        assertEquals(569.0, r.pageNumY, 1e-9)        // Math.round(568.98)
        assertEquals(21.0, r.lineStepX, 1e-6)
        assertEquals(13.5, r.rubyOffsetX, 1e-6)
    }

    @Test fun detectsBodySizeFromDominantFont() {
        // 本文16 が支配的 → bodySize=16, rubySize=8（少数の別サイズに引っ張られない）
        val page = (0 until 20).map { cb("あ", 16.0, 100.0 + it, 100.0) } + cb("小", 10.0, 50.0, 50.0)
        val r = DetectedRules.detect(listOf(page))
        assertEquals(16.0, r.bodySize, 1e-9)
        assertEquals(8.0, r.rubySize, 1e-9)
    }

    // --- フォールバック ---

    @Test fun emptyInputFallsBackEntirely() {
        assertEquals(DetectedRules.FALLBACK, DetectedRules.detect(emptyList()))
    }

    @Test fun fewPagesPageNumFallsBack() {
        // 総ページ 3（>3 を満たさない）＝ページ番号シグネチャがあっても採用せずフォールバック。
        val py = 528.98
        fun pg() = listOf(
            cb("9", 12.0, 200.0, py),
            cb("あ", 14.0, 300.0, 100.0), cb("い", 14.0, 277.32, 100.0),
        )
        val r = DetectedRules.detect(listOf(pg(), pg(), pg()))
        assertEquals(ParserRules.PAGE_NUM_Y, r.pageNumY, 1e-9)
        assertEquals(ParserRules.FONT_SIZE_PAGE, r.pageNumSize, 1e-9)
    }

    @Test fun fewLineStepSamplesFallsBack() {
        // 列ステップのサンプルが <10 → 実測 30.0 でなくフォールバック 22.68 を返す。
        val page = listOf(
            cb("あ", 14.0, 400.0, 100.0), cb("い", 14.0, 370.0, 100.0),
            cb("う", 14.0, 340.0, 100.0), cb("え", 14.0, 310.0, 100.0),
        )
        val r = DetectedRules.detect(listOf(page))
        assertEquals(ParserRules.LINE_STEP_X, r.lineStepX, 1e-9)
    }

    @Test fun fewRubyOffsetSamplesFallsBack() {
        // ルビが少数（<10）→ 実測でなくフォールバック 14.84。
        val page = listOf(
            cb("森", 14.0, 400.0, 100.0), cb("ル", 7.0, 400.0 + 9.9, 100.0), // 1 個だけ
            cb("川", 14.0, 377.32, 100.0),
        )
        val r = DetectedRules.detect(listOf(page))
        assertEquals(ParserRules.RUBY_OFFSET_X, r.rubyOffsetX, 1e-9)
    }

    // --- 目的の直接担保: 一律シフト不変性 ---

    @Test fun uniformShiftProducesIdenticalOutput() {
        val refDoc = buildDoc(ref)
        val shiftedDoc = buildDoc(shifted)
        val refOut = TextProcessor.processPages(refDoc, refDoc.size, DetectedRules.detect(refDoc))
        val shiftedOut =
            TextProcessor.processPages(shiftedDoc, shiftedDoc.size, DetectedRules.detect(shiftedDoc))

        // 寸法を一律にずらしても抽出テキストは完全一致（本リファクタの目的）。
        assertEquals(refOut, shiftedOut)

        // 検出が実際に効き、かつ非自明な出力である（題名・ルビ・段落間空行を含みページ番号は除外）ことの担保。
        assertTrue("題名マーカーが無い", refOut.any { it.startsWith("【題名】") })
        assertTrue("ルビが無い", refOut.any { it.contains("《") })
        assertTrue("段落間の空行が無い", refOut.contains(""))
        assertFalse("ページ番号が混入", refOut.joinToString("").contains("9"))
    }
}
