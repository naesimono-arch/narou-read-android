package com.novelreader.ui.compose

import androidx.compose.ui.text.TextLayoutResult
import java.text.BreakIterator
import java.util.Locale

/**
 * ルビの描画位置情報（1行分）
 * @param line 行インデックス
 * @param centerX ルビテキストの中央X座標
 * @param baselineY ルビのベースライン Y 座標（親文字行の上端）
 * @param rubyText この行に描画するルビ部分文字列
 */
data class RubyDrawInfo(
    val line: Int,
    val centerX: Float,
    val baselineY: Float,
    val rubyText: String,
)

/**
 * ルビ描画位置の計算ヘルパー。
 * 純粋な文字列分割ロジックとCompose依存の位置計算を分離し、
 * 文字列分割部分はJVMユニットテストで検証可能にしている。
 */
object RubyLayoutHelper {

    /**
     * 文字列を書記素クラスタ単位で分割する。
     * なぜ String.toList() でなく BreakIterator か:
     * サロゲートペア（𠮷など）や結合文字（異体字セレクタ付き）を
     * 正しく1単位として扱うため。toList() は UTF-16 の Char 単位で
     * 分割するため、これらの文字が破壊される。
     */
    fun splitGraphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val iterator = BreakIterator.getCharacterInstance(Locale.JAPANESE)
        iterator.setText(text)
        var start = 0
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }

    /**
     * ルビ読みを、各行に含まれる親文字数に比例して分割する。
     * @param fullReading ルビ全体（例: "ものがたり"）
     * @param charCountsPerLine 各行の親文字数（例: [2, 1] = 1行目に2文字、2行目に1文字）
     * @return 各行に割り当てるルビ部分文字列のリスト
     */
    fun splitRubyReading(
        fullReading: String,
        charCountsPerLine: List<Int>,
    ): List<String> {
        if (charCountsPerLine.isEmpty()) return emptyList()
        if (fullReading.isEmpty()) return charCountsPerLine.map { "" }

        val graphemes = splitGraphemes(fullReading)
        val totalBaseChars = charCountsPerLine.sum()

        if (totalBaseChars == 0) return charCountsPerLine.map { "" }

        val result = mutableListOf<String>()
        var graphemeOffset = 0

        for ((index, count) in charCountsPerLine.withIndex()) {
            if (index == charCountsPerLine.lastIndex) {
                // 最終行は残りすべてを割り当て（端数吸収）
                result.add(graphemes.drop(graphemeOffset).joinToString(""))
            } else {
                // 親文字数に比例して書記素を割り当て
                val allocatedGraphemes = (graphemes.size.toLong() * count / totalBaseChars).toInt()
                val endOffset = (graphemeOffset + allocatedGraphemes).coerceAtMost(graphemes.size)
                result.add(graphemes.subList(graphemeOffset, endOffset).joinToString(""))
                graphemeOffset = endOffset
            }
        }

        return result
    }

    /**
     * TextLayoutResult からルビの描画位置を計算する。
     * @param layout Compose の TextLayoutResult（onTextLayout で取得）
     * @param start ルビ対象の親文字の開始オフセット（AnnotatedString 内）
     * @param end ルビ対象の親文字の終了オフセット（排他的）
     * @param fullReading ルビ読み文字列全体
     * @return 行ごとの描画情報リスト（通常は1要素、行またぎ時は複数）
     */
    fun calculateRubyPositions(
        layout: TextLayoutResult,
        start: Int,
        end: Int,
        fullReading: String,
    ): List<RubyDrawInfo> {
        if (fullReading.isEmpty() || start >= end) return emptyList()

        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end - 1)

        if (startLine == endLine) {
            // 同一行: 親文字範囲の中央にルビを配置
            val startBox = layout.getBoundingBox(start)
            val endBox = layout.getBoundingBox(end - 1)
            val centerX = (startBox.left + endBox.right) / 2f
            val baselineY = layout.getLineTop(startLine)
            return listOf(RubyDrawInfo(startLine, centerX, baselineY, fullReading))
        }

        // 行またぎ: 各行の親文字数に比例してルビを分割
        val charCountsPerLine = mutableListOf<Int>()
        for (line in startLine..endLine) {
            val lineStart = if (line == startLine) start else layout.getLineStart(line)
            val lineEnd = if (line == endLine) end else layout.getLineEnd(line)
            charCountsPerLine.add((lineEnd - lineStart).coerceAtLeast(0))
        }

        val rubyParts = splitRubyReading(fullReading, charCountsPerLine)

        return rubyParts.mapIndexed { index, rubyText ->
            val line = startLine + index
            val lineStart = if (line == startLine) start else layout.getLineStart(line)
            val lineEnd = if (line == endLine) end else layout.getLineEnd(line)
            val safeLineEnd = (lineEnd - 1).coerceAtLeast(lineStart)

            val startBox = layout.getBoundingBox(lineStart)
            val endBox = layout.getBoundingBox(safeLineEnd)
            val centerX = (startBox.left + endBox.right) / 2f
            val baselineY = layout.getLineTop(line)

            RubyDrawInfo(line, centerX, baselineY, rubyText)
        }
    }
}
