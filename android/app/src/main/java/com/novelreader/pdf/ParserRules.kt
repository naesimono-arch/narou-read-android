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
 * 【定数群の位置づけ】下記のサイズ・座標・ピッチは「現行 PDF 形状の実測値」であり、
 * 文書ごとの自動検出（[DetectedRules.detect]）が統計を立てられないときのフォールバック正本を兼ねる。
 * 本文処理は原則 [DetectedRules] 経由で相対値を使い、検出不能な項目だけここへ退避する
 * （＝生成側が同じ形状のまま寸法を微調整しても、検出が追随して破綻しないようにするため）。
 *
 * 移植元との差分: pdf_rules.py の START_Y_BODY / START_Y_TITLE は定義のみで本文処理から
 * 一度も参照されないデッド定数のため移植しない（Python 側 grep で未参照を確認済み）。
 */
object ParserRules {
    // 1. フォントサイズ / フォント名（検出不能時のフォールバック実測値）
    const val FONT_SIZE_BODY_TITLE = 14.0 // 題名と本文
    const val FONT_SIZE_RUBY = 7.0        // ルビ
    const val FONT_SIZE_PAGE = 12.0       // ページ数
    const val FONT_MARKER_TITLE = "Bold"  // 題名判定（フォント名に含まれるか。サイズ検出とは独立の固定マーカー）

    // 2. ページ数の座標（フォールバック実測値）
    const val PAGE_NUM_Y = 528.98

    // 3. ルビ（フォールバック実測値）
    const val RUBY_OFFSET_X = 14.84 // 親文字 x0 に対するルビ x0 のズレ(+)

    // 4. 行間（フォールバック実測値）
    const val LINE_STEP_X = 22.68 // 1 行あたりの x 移動量（空行計算用）

    // 5. 表紙の著者名
    const val FONT_SIZE_AUTHOR = 12.0    // FONT_SIZE_PAGE と同値 → フッター除外で区別
    const val COVER_FOOTER_Y = 500.0
    const val COVER_FOOTER_Y_TOL = 30.0
    // フッター座標を相対化する基準ページ高さ（golden 表紙の mediaBox 高さ実測＝595.28pt）。
    // 表紙パスでは実ページ高さ×(COVER_FOOTER_Y / COVER_PAGE_HEIGHT) でフッター帯を出す。
    // golden 高さ 595.28 では COVER_FOOTER_Y=500.0 と数値等価になり現行挙動を保存する。
    const val COVER_PAGE_HEIGHT = 595.28

    // 数値比較の許容誤差
    const val TOLERANCE = 0.1

    /**
     * Python の math.isclose(a, b, rel_tol=1e-9, abs_tol=absTol) と等価。
     * abs(a-b) <= max(rel_tol*max(|a|,|b|), abs_tol)
     */
    fun isClose(a: Double, b: Double, absTol: Double = TOLERANCE, relTol: Double = 1e-9): Boolean =
        abs(a - b) <= max(relTol * max(abs(a), abs(b)), absTol)

    /**
     * Bold フォント かつ 本文題名サイズなら題名とみなす。
     * bodySize は検出した本文サイズを注入する（既定はフォールバック実測値）。Bold マーカーは
     * サイズ検出と独立の固定判定のため引数化しない。
     */
    fun checkIsTitle(fontName: String?, fontSize: Double, bodySize: Double = FONT_SIZE_BODY_TITLE): Boolean =
        fontName != null &&
            fontName.contains(FONT_MARKER_TITLE) &&
            isClose(fontSize, bodySize)
}
