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
    override fun verticalAdvance(unitText: String, fontSizePx: Float): Float =
        // 結合リーダー run（……等）だけは実装（PaintFontMetrics）と同じく「横幅＝字数ぶん」を返す
        // ＝折返し・座標テストが結合ユニットの実寸法（Nマス）を前提にできるように規則を鏡写しにする。
        if (unitText.length > 1 && unitText.all { it == unitText[0] && it in LeaderJoin.CHARS }) {
            fontSizePx * unitText.length
        } else {
            fontSizePx
        }

    override fun horizontalAdvance(text: String, fontSizePx: Float): Float = fontSizePx * text.length
}
