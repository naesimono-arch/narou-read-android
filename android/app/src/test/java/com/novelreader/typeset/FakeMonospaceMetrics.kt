package com.novelreader.typeset

/**
 * テスト用の等幅フェイク。
 * verticalAdvance は fontSizePx 固定（1ユニット＝1マス）、horizontalAdvance は fontSizePx×文字数。
 *
 * なぜ実測でなく等幅か: 純組版ロジック（折返し・禁則・按分・座標）の検証には決定的で単純な
 * 寸法源が要る。実測 advance の書体差（P0-1 で serif 小書き仮名 64→65）は FontMetricsProvider
 * 境界の存在意義そのもので、その差の吸収は P2 の実 Paint 実装＝PaintFontMetricsTest＋golden で担保する
 * （golden だけに委ねない: 初回記録が壊れていると golden は修正を阻む側に回る＝2026-08-06 監査 G-3）。
 */
class FakeMonospaceMetrics : FontMetricsProvider {
    // charClass は意図的に使わない: 等幅世界では向きによる寸法差が存在しない（全ユニット em）。
    // 「向きで縦送りが変わる」実測差の回帰（単独半角の正立＝em / 回転字＝半角幅）は
    // 実 Paint を使う PaintFontMetricsTest 側が担う。
    override fun verticalAdvance(unitText: String, charClass: CharClass, fontSizePx: Float): Float =
        // 結合リーダー run（……等）だけは実装（PaintFontMetrics）と同じく「横幅＝字数ぶん」を返す
        // ＝折返し・座標テストが結合ユニットの実寸法（Nマス）を前提にできるように規則を鏡写しにする。
        if (unitText.length > 1 && unitText.all { it == unitText[0] && it in LeaderJoin.CHARS }) {
            fontSizePx * unitText.length
        } else {
            fontSizePx
        }

    override fun horizontalAdvance(text: String, fontSizePx: Float): Float = fontSizePx * text.length
}
