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
     * @param unitText 1書記素、または縦中横として1マスに収める部分文字列
     */
    fun verticalAdvance(unitText: String, fontSizePx: Float): Float

    /**
     * 横方向の実測幅 px（縦中横の収まり判定・ルビ幅見積り用）。
     */
    fun horizontalAdvance(text: String, fontSizePx: Float): Float
}
