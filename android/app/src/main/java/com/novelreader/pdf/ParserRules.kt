package com.novelreader.pdf

import kotlin.math.abs
import kotlin.math.max

/**
 * なろう縦書き PDF 解析の判定ルール（移植元 pdf_rules.py と 1:1。
 * 「移植元」の意味は PdfBookExtractor の注記を参照＝python/ は現存しない）。
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
 * 移植元との差分（Why-not の記録）: pdf_rules.py の START_Y_BODY / START_Y_TITLE は定義のみで
 * 本文処理から一度も参照されないデッド定数だったため移植していない（移植時に移植元を grep して確認済み）。
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

    /**
     * 1 ページの本文列グリッドの横幅＝右端列 x0 −左端列 x0（フォールバック実測値）。
     *
     * なぜ要るか（S3 ページ境界の空行）: ページ末尾と次ページ先頭の空行数は
     * 「前ページで余った列数 ＋ 次ページで空いた列数」で、これは
     * (COLUMN_SPAN_X + 前ページ最終列 x0 − 次ページ先頭列 x0) / LINE_STEP_X に等しい
     * （導出は [com.novelreader.pdf.TextProcessor.LineStreamer] の KDoc）。
     * 個々の右端/左端の絶対座標は要らず、**両端の差**だけで足りるのが要点。
     *
     * 値は sample_pdfs 全 10 文書で一致した実測（741.35 − 83.71＝29 列ぶん＝1 ページ 30 列）。
     * 文書ごとの検出は [DetectedRules.detect] が行い、統計不足のときだけここへ退避する。
     */
    const val COLUMN_SPAN_X = 657.64

    /**
     * 1 列に流し込める本文の文字数（＝原文の行が折り返される幅）。
     *
     * 縦書き CID フォントは W2 配列を持たないため縦送りは**全文字 1em**（半角スペース・ASCII も同じ）＝
     * 列の容量は文字数でそのまま数えられる。列がこの数ちょうどなら「行が続いている可能性」があり、
     * これ未満なら**そこで原文の行が終わっている**（[TextProcessor.LineStreamer] の行復元規則）。
     * 行頭禁則のぶら下がりと widow 回避だけが +1 字（＝31 字）を作る。
     *
     * なぜ [DetectedRules] で文書ごとに検出しないか: **揺れが実測されていない項目**を検出機構へ足すと、
     * 検出が外した文書で行境界が丸ごと壊れる面を新設することになる（[DetectedRules] の KDoc＝予防的検出が
     * 固定値より悪化した実例あり）。代わりに固定前提＋**コーパス全数の機械検証**で守る＝
     * `TextProcessorColumnCapacityTest` が sample_pdfs の追跡 4 本について「最長列＝容量+1」かつ
     * 「最頻列長＝容量」を毎回確かめる（生成器が列高を変えたらそこが赤くなる）。
     *
     * 値の根拠（実測）: 第二実装のトレース 10 文書・467,192 列で**最長列は全文書 31 字**、
     * 30 字ちょうどの列が最頻（例 N6169DZ 145,052 列中 71,132 列＝49%）。
     */
    const val COLUMN_CAPACITY = 30

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
     * CPython 標準ライブラリ math.isclose(a, b, rel_tol=1e-9, abs_tol=absTol) と同じ判定式
     * （外部仕様への参照＝撤去された python/ ではなく現存する言語仕様を指す）。
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
