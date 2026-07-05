package com.novelreader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ParserRules.checkIsTitle の判定テスト。
 * 移植元: submission-B CheckIsTitleTest / python test_logic.py TestCheckIsTitle。
 * Bold フォント名を含み かつ 本文題名サイズ(14.0pt・±0.1) の文字のみ題名と判定する。
 */
class ParserRulesTest {
    @Test fun boldCorrectSize() = assertTrue(ParserRules.checkIsTitle("HogeB Bold", 14.0))

    // 許容誤差 TOLERANCE=0.1 内なら題名扱い（13.95 は 14.0 と isClose）
    @Test fun boldWithinTolerance() = assertTrue(ParserRules.checkIsTitle("NotoSerifCJK Bold", 13.95))

    @Test fun nonBold() = assertFalse(ParserRules.checkIsTitle("NotoSerifCJK Regular", 14.0))

    @Test fun wrongSize() = assertFalse(ParserRules.checkIsTitle("HogeB Bold", 7.0))

    // fontName が null の char（LTAnno 相当）でも落ちないこと
    @Test fun nullFontName() = assertFalse(ParserRules.checkIsTitle(null, 14.0))
}
