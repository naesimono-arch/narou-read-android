package com.novelreader.pdfproto

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文字座標から縦書きの列を再構成し、ルビを紐付けて段落文字列を組み立てる。
 * 移植元 pdf_extractor.py の _group_chars_by_line / _associate_ruby /
 * _build_line_str / _process_pages を Kotlin で再現する。
 *
 * ルビは中間表現として「|親文字《よみ》」マーカーで段落文字列に埋め込む（Python 踏襲）。
 */
object TextProcessor {

    private val WHITESPACE = setOf(" ", "\n", "\r", "\t", " ")

    /** 本文文字を x0（縦列）でグループ化する。挿入順を保持する LinkedHashMap。 */
    internal fun groupCharsByLine(bodiesAll: List<CharBox>): LinkedHashMap<Double, MutableList<CharBox>> {
        val linesDict = LinkedHashMap<Double, MutableList<CharBox>>()
        for (c in bodiesAll) {
            val xVal = c.x0
            var matchedKey: Double? = null
            for (k in linesDict.keys) {
                if (ParserRules.isClose(xVal, k)) {
                    matchedKey = k
                    break
                }
            }
            if (matchedKey != null) {
                linesDict[matchedKey]!!.add(c)
            } else {
                linesDict[xVal] = mutableListOf(c)
            }
        }
        return linesDict
    }

    /** ルビ文字を最近傍の親文字へ rubyText として紐付ける（in-place）。 */
    internal fun associateRuby(
        linesDict: LinkedHashMap<Double, MutableList<CharBox>>,
        rubiesAll: List<CharBox>,
    ) {
        for (r in rubiesAll) {
            val targetX = r.x0 - ParserRules.RUBY_OFFSET_X
            var matchedKey: Double? = null
            for (xKey in linesDict.keys) {
                if (ParserRules.isClose(xKey, targetX)) {
                    matchedKey = xKey
                    break
                }
            }
            if (matchedKey != null) {
                val targetLine = linesDict[matchedKey]!!
                var best: CharBox? = null
                var minDist = Double.POSITIVE_INFINITY
                for (bc in targetLine) {
                    val dist = abs(bc.top - r.top)
                    if (dist < minDist) {
                        minDist = dist
                        best = bc
                    }
                }
                if (best != null) {
                    best.rubyText = (best.rubyText ?: "") + r.text
                }
            }
        }
    }

    /** Y 昇順ソート済みの文字列から「|親《よみ》」付き文字列を組み立てる。 */
    internal fun buildLineStr(lineBodies: List<CharBox>): String {
        val sb = StringBuilder()
        var j = 0
        while (j < lineBodies.size) {
            val bc = lineBodies[j]
            val charText = bc.text
            if (charText in WHITESPACE) {
                j++
                continue
            }

            val rubyText = bc.rubyText
            if (!rubyText.isNullOrEmpty()) {
                val baseRun = StringBuilder()
                val rubyRun = StringBuilder()
                while (j < lineBodies.size) {
                    val bc2 = lineBodies[j]
                    val t2 = bc2.text
                    if (t2 in WHITESPACE) {
                        j++
                        continue
                    }
                    val r2 = bc2.rubyText
                    if (r2.isNullOrEmpty()) break
                    baseRun.append(t2)
                    rubyRun.append(r2)
                    j++
                }
                if (baseRun.isNotEmpty() && rubyRun.isNotEmpty()) {
                    sb.append("|").append(baseRun).append("《").append(rubyRun).append("》")
                } else {
                    sb.append(charText)
                    j++
                }
            } else {
                sb.append(charText)
                j++
            }
        }
        return sb.toString()
    }

    /**
     * 本文抽出コア。ページごとの文字リストから段落文字列のリストを返す。
     * 題名は "【題名】..." プレフィックス付きの段落として混在させる（章分割で利用）。
     */
    fun processPages(charListsByPage: List<List<CharBox>>, totalPages: Int): List<String> {
        val allParagraphs = mutableListOf<String>()
        var currentParagraph = StringBuilder()

        for ((pageNum, chars) in charListsByPage.withIndex()) {
            // 先頭3ページ（表紙・注意事項）と最終ページ（クレジット）を除外
            if (pageNum < 3 || pageNum >= totalPages - 1) continue

            val titlesAll = mutableListOf<CharBox>()
            val bodiesAll = mutableListOf<CharBox>()
            val rubiesAll = mutableListOf<CharBox>()

            for (c in chars) {
                val fontName = c.fontName
                val fontSize = c.size
                val yPos = c.top

                // ① ページ数の除外
                if (ParserRules.isClose(fontSize, ParserRules.FONT_SIZE_PAGE)) {
                    if (ParserRules.isClose(yPos, ParserRules.PAGE_NUM_Y, absTol = 5.0) ||
                        ParserRules.isClose(c.bottom, ParserRules.PAGE_NUM_Y, absTol = 5.0)
                    ) {
                        continue
                    }
                }

                // ② 題名（Bold 判定）
                if (ParserRules.checkIsTitle(fontName, fontSize)) {
                    titlesAll.add(c)
                }
                // ③ 本文
                else if (ParserRules.isClose(fontSize, ParserRules.FONT_SIZE_BODY_TITLE)) {
                    bodiesAll.add(c)
                }
                // ④ ルビ
                else if (ParserRules.isClose(fontSize, ParserRules.FONT_SIZE_RUBY)) {
                    rubiesAll.add(c)
                }
            }

            // 題名のテキスト化（X 降順・Y 昇順）
            if (titlesAll.isNotEmpty()) {
                val sorted = titlesAll.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })
                val titleText = sorted
                    .filter { it.text != " " && it.text != "\n" && it.text != "\r" }
                    .joinToString("") { it.text }
                if (titleText.isNotEmpty()) {
                    if (currentParagraph.isNotEmpty()) {
                        allParagraphs.add(currentParagraph.toString())
                        currentParagraph = StringBuilder()
                    }
                    allParagraphs.add("【題名】$titleText")
                }
            }

            // 本文ソート（X 降順・Y 昇順）
            val bodiesSorted = bodiesAll.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })

            val linesDict = groupCharsByLine(bodiesSorted)
            associateRuby(linesDict, rubiesAll)

            // 右の列から順にテキスト化＆段落の縫合
            val linesSortedX = linesDict.keys.sortedDescending()
            var prevX: Double? = null

            for (x in linesSortedX) {
                val lineBodies = linesDict[x]!!.sortedBy { it.top }
                val lineStr = buildLineStr(lineBodies)
                if (lineStr.isEmpty()) continue

                var isNewParagraph = false
                var blankLineCount = 0

                if (lineStr.startsWith("　") || lineStr.startsWith("「") ||
                    lineStr.startsWith("『") || lineStr.startsWith("（")
                ) {
                    isNewParagraph = true
                }

                if (prevX != null) {
                    val diffX = prevX - x
                    if (diffX > ParserRules.LINE_STEP_X * 1.5) {
                        isNewParagraph = true
                        blankLineCount = (diffX / ParserRules.LINE_STEP_X).roundToInt() - 1
                    }
                }

                if (isNewParagraph) {
                    if (currentParagraph.isNotEmpty()) {
                        allParagraphs.add(currentParagraph.toString())
                    }
                    repeat(maxOf(0, blankLineCount)) { allParagraphs.add("") }
                    currentParagraph = StringBuilder(lineStr)
                } else {
                    currentParagraph.append(lineStr)
                }

                prevX = x
            }
        }

        if (currentParagraph.isNotEmpty()) {
            allParagraphs.add(currentParagraph.toString())
        }

        // クリーンアップ：空行は "" のまま保持、それ以外は trim
        val finalOutput = mutableListOf<String>()
        for (p in allParagraphs) {
            if (p.isEmpty()) {
                finalOutput.add("")
            } else {
                val cleaned = p.trim(' ', '\t', '\n', '\r')
                if (cleaned.isNotEmpty()) finalOutput.add(cleaned)
            }
        }
        return finalOutput
    }
}
