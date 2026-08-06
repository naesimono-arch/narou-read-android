package com.novelreader.typeset

/**
 * 文字の実測寸法を返す境界。実装は P2 の Android Paint ラッパ、テストは等幅フェイク。
 *
 * なぜ interface で切るか（＝等幅前提を置かない）: P0-1 実測で serif の小書き仮名は
 * advance が 64→65px に変わる書体があった。縦送りを「fontSizePx 一律」と決め打つと
 * 書体差で版面がずれる。実測 advance を返す境界で吸収し、純組版層は寸法源を抽象に依存させる。
 */
interface FontMetricsProvider {
    /**
     * 縦組み1ユニット（書記素 or 縦中横 run）の縦送り px。
     *
     * なぜ charClass を受けるか: 縦の占有はユニットの「向き」で決まる。同じ「3」でも
     * 単独ラン＝UPRIGHT（正立＝em マス）と4字以上ランの1字＝ROTATE（横倒し＝半角幅）で
     * 縦送りが異なり、文字列だけでは判別できない（判別せず回転前提の半角幅を返していたのが
     * 「3日」等で字面が接触した真因＝golden 監査 2026-08-06 G-3）。向きの決定は純層
     * （CharClassifier / VerticalTypesetter のラン文脈上書き）が済ませ、寸法側はそれに従う。
     *
     * @param unitText 1書記素、または縦中横として1マスに収める部分文字列
     * @param charClass 純層が確定したこのユニットの向き
     */
    fun verticalAdvance(unitText: String, charClass: CharClass, fontSizePx: Float): Float

    /**
     * 横方向の実測幅 px（縦中横の収まり判定・ルビ幅見積り用）。
     */
    fun horizontalAdvance(text: String, fontSizePx: Float): Float
}
