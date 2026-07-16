package com.novelreader.typeset

/**
 * 読書位置（段落index＋段落内 fraction 0..1）。
 * モード切替（横書き⇔縦書き）跨ぎでも保持する正本の位置表現。
 */
data class ParagraphPosition(val paragraphIndex: Int, val fraction: Float)

/**
 * スクロール状態（LazyList の index/offset）と ParagraphPosition の相互変換（純関数）。
 *
 * なぜ横書き LazyColumn と縦書き LazyRow(reverseLayout=true) の両方に同じ式が使えるか:
 * P0-4 実測で reverseLayout でも (index,offset) は「#0 が右端・scrollBy(+) で読み進め」＝
 * 現行 (scrollIndex,scrollOffset) と完全同型だった。よって位置保存は同型のまま両モードで成立する。
 *
 * header アイテム（index < headerItemCount＝章タイトル等）は段落ではないため
 * paragraphIndex=0, fraction=0 に丸める。
 */
object ReadingPositionMapper {

    // fraction は [0,1) に収める契約。1f 未満の最大 float を上限に使う。
    private val MAX_FRACTION: Float = Math.nextDown(1.0f)

    /**
     * @param firstVisibleItemSizePx 現在先頭アイテムの高さ/幅 px（<=0 は fraction=0 に倒す＝ゼロ除算防御）
     * @param headerItemCount 段落アイテムの前に置くヘッダアイテム数（既定1）
     */
    fun fromScroll(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        firstVisibleItemSizePx: Int,
        headerItemCount: Int = 1,
    ): ParagraphPosition {
        // ヘッダ上にいる間は最初の段落の先頭に丸める（ヘッダは段落ではない）。
        if (firstVisibleItemIndex < headerItemCount) {
            return ParagraphPosition(paragraphIndex = 0, fraction = 0f)
        }
        val paragraphIndex = firstVisibleItemIndex - headerItemCount
        // アイテム寸法が未確定（0以下）のときは fraction を出せない＝先頭に倒す（防御）。
        val fraction = if (firstVisibleItemSizePx <= 0) {
            0f
        } else {
            (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSizePx).coerceIn(0f, MAX_FRACTION)
        }
        return ParagraphPosition(paragraphIndex, fraction)
    }

    /**
     * @param itemSizePx 復元先アイテムの寸法 px（<=0 なら offset=0）
     * @return (index, offset)
     */
    fun toScroll(
        pos: ParagraphPosition,
        itemSizePx: Int,
        headerItemCount: Int = 1,
    ): Pair<Int, Int> {
        val index = pos.paragraphIndex + headerItemCount
        // なぜ round か: fromScroll の除算（offset/size）を掛け戻して元の offset を復元するとき、
        // 単純な toInt() の切り捨てだと浮動小数誤差で1px ずれて往復同一性が崩れる。round で吸収する。
        val offset = if (itemSizePx <= 0) 0 else Math.round(pos.fraction * itemSizePx)
        return index to offset
    }
}
