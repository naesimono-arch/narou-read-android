package com.novelreader.typeset

import com.novelreader.ui.compose.RubyLayoutHelper

/**
 * ルビ（振り仮名）を各列の右側に配置する（純ロジック）。
 *
 * 縦書きのルビは本文列の右隣に、親文字スパンの縦中央へ来る。親文字が複数列へ跨いだ場合は
 * 列ごとの親文字数に比例してルビを按分する（横書き RubyText と同じ分割規則を
 * RubyLayoutHelper.splitRubyReading の流用で保つ）。
 */
object RubyPlacer {

    /**
     * @param columns 列割り当て済み単位列（ルビ親文字ユニットに segmentIndex が付いている）
     * @param columnCenterX 各列の中心 x（本文列の中心。列右側ルビ x の基準）
     * @param rubyReadings segmentIndex → ルビ読み全体
     * @param fontSizePx 本文フォントサイズ px
     * @param rubyFontSizePx ルビフォントサイズ px
     * @param metrics ルビ部分文字列の縦長を実測して縦中央合わせに使う
     */
    fun place(
        columns: List<Column>,
        columnCenterX: List<Float>,
        rubyReadings: Map<Int, String>,
        fontSizePx: Float,
        rubyFontSizePx: Float,
        metrics: FontMetricsProvider,
    ): List<PositionedRuby> {
        // segmentIndex ごとに「どの列に親文字が何個いるか」を列順（＝読み順）で集約。
        // LinkedHashMap で挿入順（列0→列1…＝読み順）を保つ＝splitRubyReading の charCountsPerLine 順序に一致。
        val bySegment = LinkedHashMap<Int, LinkedHashMap<Int, MutableList<PlacedUnit>>>()
        columns.forEachIndexed { columnIndex, column ->
            for (placed in column.units) {
                val u = placed.unit
                if (u.isRubyBase && u.segmentIndex >= 0) {
                    bySegment.getOrPut(u.segmentIndex) { LinkedHashMap() }
                        .getOrPut(columnIndex) { ArrayList() }
                        .add(placed)
                }
            }
        }

        val result = ArrayList<PositionedRuby>()
        for ((segmentIndex, perColumn) in bySegment) {
            val reading = rubyReadings[segmentIndex] ?: continue
            val columnIndices = perColumn.keys.toList()
            val charCountsPerLine = columnIndices.map { perColumn.getValue(it).size }
            // 横書きと同じ按分規則を流用（列順＝読み順で親文字数に比例配分）。
            val parts = RubyLayoutHelper.splitRubyReading(reading, charCountsPerLine)

            columnIndices.forEachIndexed { k, columnIndex ->
                val part = parts[k]
                if (part.isEmpty()) return@forEachIndexed
                val placedUnits = perColumn.getValue(columnIndex)
                // 親文字スパン＝先頭ユニットの天〜末尾ユニットの底。
                val spanTop = placedUnits.first().yTop
                val last = placedUnits.last()
                val spanBottom = last.yTop + last.advance
                val spanCenter = (spanTop + spanBottom) / 2f

                // ルビ部分文字列の縦長を書記素ごとに実測して合算（等幅前提を置かない）。
                val rubyLength = RubyLayoutHelper.splitGraphemes(part)
                    .fold(0f) { acc, g -> acc + metrics.verticalAdvance(g, rubyFontSizePx) }
                // 縦中央合わせ。ルビ長 > 親文字スパンなら y が spanTop より上（負方向）に出る＝
                // 上下はみ出しを許容（圧縮は後続フェーズ。親プラン P6 の版面較正で扱う）。
                val y = spanCenter - rubyLength / 2f
                // x は列の右側＝本文列中心 + 本文半幅 + ルビ半幅。
                val x = columnCenterX[columnIndex] + fontSizePx / 2f + rubyFontSizePx / 2f

                result.add(PositionedRuby(part, columnIndex, x, y))
            }
        }
        return result
    }
}
