package com.novelreader.pdf

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文字座標から縦書きの列を再構成し、ルビを紐付けて段落文字列を組み立てる。
 * 移植元 pdf_extractor.py の _group_chars_by_line / _associate_ruby /
 * _build_line_str / _process_pages（「移植元」の意味は PdfBookExtractor の注記を参照）。
 *
 * ルビは中間表現として「|親文字《よみ》」マーカーで段落文字列に埋め込み、後段の
 * [ChapterProcessor] が `<ruby>` タグへ変換する。**この記法は今も live な内部契約**で、
 * 二重に守られている:
 * - `JvmGoldenRegressionTest` がこの段落文字列そのものを body_sha256 と 《 の出現数で固定する
 * - Web 取込側 [com.novelreader.scrape.NovelSiteAdapter] も同じ記法で本文を返し、同じ変換段を通る
 * ＝記法を変えるなら PDF/Web 両経路とゴールデンを同時に更新すること。
 */
object TextProcessor {

    // 移植元 pdf_extractor.py の空白集合 [" ", "\n", "\r", "\t", "\xa0"] と同一。
    // 末尾は NBSP(U+00A0=\xa0)。見た目で半角空白と区別できないため明示エスケープで書く。
    private val WHITESPACE = setOf(" ", "\n", "\r", "\t", "\u00a0")

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

    /**
     * ルビ文字を最近傍の親文字へ rubyText として紐付ける（in-place）。
     * rubyOffsetX は検出したルビ横オフセットを注入する（既定はフォールバック実測値）。
     */
    internal fun associateRuby(
        linesDict: LinkedHashMap<Double, MutableList<CharBox>>,
        rubiesAll: List<CharBox>,
        rubyOffsetX: Double = ParserRules.RUBY_OFFSET_X,
    ) {
        for (r in rubiesAll) {
            // ルビの x0 は親文字 x0 + rubyOffsetX。逆算して親の列を最近傍で探す。
            val targetX = r.x0 - rubyOffsetX
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
                // ルビ付き文字が連続する範囲をまとめて 1 つの |base《ruby》 に合成する。
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
     *
     * 中身は [ParagraphStreamer] へ 1 ページずつ流すだけ（実装は一本＝全ページ版とストリーミング版で
     * 段落の縫合規則が食い違わないようにするため）。全ページを同時に持てる呼び出し側（テスト・
     * オラクル・ヒープに余裕のある端末）はこちらを使ってよい。
     *
     * progressCallback: 有効ページの処理開始ごとに (pct, processed, bodyTotal) を通知する。
     *   pct は 10〜60% にマップ。本文抽出(step1)の進捗バーをページ単位でライブ更新するために使う。
     *   null なら通知しない。
     */
    fun processPages(
        charListsByPage: List<List<CharBox>>,
        totalPages: Int,
        rules: DetectedRules = DetectedRules.FALLBACK,
        progressCallback: ((pct: Int, processed: Int, bodyTotal: Int) -> Unit)? = null,
    ): List<String> {
        val out = mutableListOf<String>()
        val streamer = ParagraphStreamer(totalPages, rules, progressCallback) { out.add(it) }
        for ((pageNum, chars) in charListsByPage.withIndex()) streamer.addPage(pageNum, chars)
        streamer.finish()
        return out
    }

    /**
     * ページを 1 枚ずつ受け取り、確定した段落を [emit] へ吐き出す逐次処理器。
     *
     * なぜ逐次か（OOM の真因対処）: 段落化はページ内で閉じる処理で、ページを跨いで要る状態は
     * **組み立て中の段落 1 本だけ**（[currentParagraph]）。にもかかわらず旧経路は全ページ分の
     * CharBox を先に materialize してから回していたため、保持量がページ数に比例した。
     * 1 ページ受け取るたびに使い切って捨てれば、保持量はページ数に依存しない。
     *
     * ⚠️ [addPage] に渡された `chars` は復帰後に破棄されてよい（参照を持ち越さない）。
     * ⚠️ ページは**昇順**に渡すこと（段落の縫合と先頭/末尾ページのトリムが順序に依存する）。
     */
    class ParagraphStreamer(
        private val totalPages: Int,
        private val rules: DetectedRules = DetectedRules.FALLBACK,
        private val progressCallback: ((pct: Int, processed: Int, bodyTotal: Int) -> Unit)? = null,
        private val emit: (String) -> Unit,
    ) {
        private var currentParagraph = StringBuilder()

        // 本文ページ総数（先頭3＋末尾1 を除いた数）。0除算回避で最小1。
        private val bodyTotal = maxOf(totalPages - 4, 1)

        /**
         * 確定した段落を整形して外へ出す。
         * クリーンアップ規則は全ページ版と同一＝空行は "" のまま保持、それ以外は trim して空なら捨てる。
         * 段落ごとに閉じた規則なので、全部溜めてから一括で掛けても 1 本ずつ掛けても結果は同じ。
         */
        private fun emitParagraph(p: String) {
            if (p.isEmpty()) {
                emit("")
            } else {
                val cleaned = p.trim(' ', '\t', '\n', '\r')
                if (cleaned.isNotEmpty()) emit(cleaned)
            }
        }

        /** 1 ページ分の文字を処理する（[pageNum] は 0 始まりの通しページ番号）。 */
        fun addPage(pageNum: Int, chars: List<CharBox>) {
            // 先頭3ページ（表紙・注意事項）と最終ページ（クレジット）を除外
            if (pageNum < 3 || pageNum >= totalPages - 1) return

            // 進捗通知（10〜60%）
            if (progressCallback != null) {
                val processed = pageNum - 3
                val pct = 10 + (processed.toDouble() / bodyTotal * 50).toInt()
                progressCallback.invoke(pct, processed, bodyTotal)
            }

            val titlesAll = mutableListOf<CharBox>()
            val bodiesAll = mutableListOf<CharBox>()
            val rubiesAll = mutableListOf<CharBox>()

            for (c in chars) {
                val fontName = c.fontName
                val fontSize = c.size
                val yPos = c.top

                // ① ページ数の除外（ページ番号サイズ かつ ページ番号 Y 付近。窓は現行と同形＝±5.0）
                if (ParserRules.isClose(fontSize, rules.pageNumSize)) {
                    if (ParserRules.isClose(yPos, rules.pageNumY, absTol = 5.0) ||
                        ParserRules.isClose(c.bottom, rules.pageNumY, absTol = 5.0)
                    ) {
                        continue
                    }
                }

                // ② 題名（Bold 判定・本文サイズ基準）
                if (ParserRules.checkIsTitle(fontName, fontSize, rules.bodySize)) {
                    titlesAll.add(c)
                }
                // ③ 本文
                else if (ParserRules.isClose(fontSize, rules.bodySize)) {
                    bodiesAll.add(c)
                }
                // ④ ルビ
                else if (ParserRules.isClose(fontSize, rules.rubySize)) {
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
                        emitParagraph(currentParagraph.toString())
                        currentParagraph = StringBuilder()
                    }
                    emitParagraph("【題名】$titleText")
                }
            }

            // 本文ソート（X 降順・Y 昇順）
            val bodiesSorted = bodiesAll.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })

            val linesDict = groupCharsByLine(bodiesSorted)
            associateRuby(linesDict, rubiesAll, rules.rubyOffsetX)

            // 右の列から順にテキスト化＆段落の縫合
            val linesSortedX = linesDict.keys.sortedDescending()
            // 列間 X の比較はページ内で閉じる（ページを跨いだ列位置の比較には意味が無い）。
            var prevX: Double? = null

            for (x in linesSortedX) {
                val lineBodies = linesDict[x]!!.sortedBy { it.top }
                val lineStr = buildLineStr(lineBodies)
                if (lineStr.isEmpty()) continue

                var isNewParagraph = false
                var blankLineCount = 0

                // 行頭が字下げ/開き括弧なら新段落
                if (lineStr.startsWith("　") || lineStr.startsWith("「") ||
                    lineStr.startsWith("『") || lineStr.startsWith("（")
                ) {
                    isNewParagraph = true
                }

                // 列間 X が 1 行ステップの 1.5 倍超なら段落切れ＋空行挿入
                if (prevX != null) {
                    val diffX = prevX - x
                    if (diffX > rules.lineStepX * 1.5) {
                        isNewParagraph = true
                        // 空行数 = round(diffX/lineStepX) - 1。厳密に .5 のとき roundToInt は上へ丸めるが、
                        // 実測 PDF で diffX/lineStepX がちょうど .5 になる例は確認されておらず、
                        // 丸め方向は結果に効いていない。
                        blankLineCount = (diffX / rules.lineStepX).roundToInt() - 1
                    }
                }

                if (isNewParagraph) {
                    if (currentParagraph.isNotEmpty()) {
                        emitParagraph(currentParagraph.toString())
                    }
                    repeat(maxOf(0, blankLineCount)) { emitParagraph("") }
                    currentParagraph = StringBuilder(lineStr)
                } else {
                    currentParagraph.append(lineStr)
                }

                prevX = x
            }
        }

        /** 全ページを渡し終えた後に必ず呼ぶ（組み立て途中の最後の段落を吐き出す）。 */
        fun finish() {
            if (currentParagraph.isNotEmpty()) {
                emitParagraph(currentParagraph.toString())
                currentParagraph = StringBuilder()
            }
        }
    }
}
