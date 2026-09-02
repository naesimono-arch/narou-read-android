package com.novelreader.typeset

/**
 * テスト用の**非等幅**フェイク。advance が (unitText, charClass, fontSizePx) の3つすべてに依存する。
 *
 * なぜ [FakeMonospaceMetrics] と別に要るか: 等幅フェイクは全ユニットが em で返るため、
 * advance キャッシュ（[CachingFontMetrics]）がキーの一部を取り違えても**値が同じ**になってしまい、
 * 「版面がキャッシュ有無で一致した」が何も証明しない。キーの3成分に実際に反応する寸法源で初めて、
 * 取り違え（例: 向きを無視して "3" の UPRIGHT の値を ROTATE へ配る＝golden 監査 2026-08-06 G-3 の形）が
 * 版面の差として現れる。書体差そのものの再現ではなく、**キーの取り違えを可視化するための道具**。
 */
class VariableFontMetrics : FontMetricsProvider {

    /** delegate が実際に呼ばれた回数＝キャッシュの外れ回数の観測に使う。 */
    var verticalCalls = 0
        private set

    var horizontalCalls = 0
        private set

    override fun verticalAdvance(unitText: String, charClass: CharClass, fontSizePx: Float): Float {
        verticalCalls++
        return advanceOf(unitText, charClass, fontSizePx)
    }

    override fun horizontalAdvance(text: String, fontSizePx: Float): Float {
        horizontalCalls++
        return fontSizePx * text.length
    }

    companion object {
        /**
         * 期待値の正本（テスト側からも直に引ける純関数）。
         * 実装（PaintFontMetrics）と同じく結合リーダー run だけ字数ぶんのマスを占め、
         * それ以外は em の 1.0〜1.13 倍に散らす＝字と向きで値が必ず割れる。
         */
        fun advanceOf(unitText: String, charClass: CharClass, fontSizePx: Float): Float {
            val cells = if (unitText.length > 1 && unitText.all { it == unitText[0] && it in LeaderJoin.CHARS }) {
                unitText.length
            } else {
                1
            }
            val codeSum = unitText.fold(0) { acc, c -> acc + c.code }
            val jitter = (codeSum + charClass.ordinal * 3) % 11
            return fontSizePx * cells * (1f + jitter * 0.013f)
        }
    }
}
