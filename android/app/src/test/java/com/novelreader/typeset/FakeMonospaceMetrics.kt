package com.novelreader.typeset

/**
 * テスト用の等幅フェイク。
 * verticalAdvance は fontSizePx 固定（1ユニット＝1マス）、horizontalAdvance は fontSizePx×文字数。
 *
 * なぜ実測でなく等幅か: 純組版ロジック（折返し・禁則・按分・座標）の検証には決定的で単純な
 * 寸法源が要る。実測 advance の書体差（P0-1 で serif 小書き仮名 64→65）は FontMetricsProvider
 * 境界の存在意義そのもので、その差の吸収は P2 の実 Paint 実装＋golden で担保する。
 */
class FakeMonospaceMetrics : FontMetricsProvider {
    override fun verticalAdvance(unitText: String, fontSizePx: Float): Float = fontSizePx
    override fun horizontalAdvance(text: String, fontSizePx: Float): Float = fontSizePx * text.length
}
