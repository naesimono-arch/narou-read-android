package com.novelreader.pdf

import kotlin.math.abs
import kotlin.math.max

/**
 * なろう縦書き PDF 解析の判定ルール（移植元 python/pdf_rules.py と 1:1）。
 *
 * 座標系: pdfminer 版は top = page_height - y1 で上原点へ変換していた。
 * PDFBox-Android の TextPosition も上原点（y は下方向が正）で単位は PDF point のため、
 * 下記の絶対座標定数（PAGE_NUM_Y 等）はそのまま流用できる前提。
 * getYDirAdj() の基準差が出る場合に備え、実機スパイク(Phase 0)で座標をキャリブレーションする。
 *
 * 移植元との差分: pdf_rules.py の START_Y_BODY / START_Y_TITLE は定義のみで本文処理から
 * 一度も参照されないデッド定数のため移植しない（Python 側 grep で未参照を確認済み）。
 */
object ParserRules {
    // 1. フォントサイズ / フォント名
    const val FONT_SIZE_BODY_TITLE = 14.0 // 題名と本文
    const val FONT_SIZE_RUBY = 7.0        // ルビ
    const val FONT_SIZE_PAGE = 12.0       // ページ数
    const val FONT_MARKER_TITLE = "Bold"  // 題名判定（フォント名に含まれるか）

    // 2. ページ数の座標
    const val PAGE_NUM_Y = 528.98

    // 3. ルビ
    const val RUBY_OFFSET_X = 14.84 // 親文字 x0 に対するルビ x0 のズレ(+)

    // 4. 行間
    const val LINE_STEP_X = 22.68 // 1 行あたりの x 移動量（空行計算用）

    // 5. 表紙の著者名
    const val FONT_SIZE_AUTHOR = 12.0    // FONT_SIZE_PAGE と同値 → フッター除外で区別
    const val COVER_FOOTER_Y = 500.0
    const val COVER_FOOTER_Y_TOL = 30.0

    // 数値比較の許容誤差
    const val TOLERANCE = 0.1

    /**
     * Python の math.isclose(a, b, rel_tol=1e-9, abs_tol=absTol) と等価。
     * abs(a-b) <= max(rel_tol*max(|a|,|b|), abs_tol)
     */
    fun isClose(a: Double, b: Double, absTol: Double = TOLERANCE, relTol: Double = 1e-9): Boolean =
        abs(a - b) <= max(relTol * max(abs(a), abs(b)), absTol)

    /** Bold フォント かつ 本文題名サイズ(14.0pt) なら題名とみなす。 */
    fun checkIsTitle(fontName: String?, fontSize: Double): Boolean =
        fontName != null &&
            fontName.contains(FONT_MARKER_TITLE) &&
            isClose(fontSize, FONT_SIZE_BODY_TITLE)
}
