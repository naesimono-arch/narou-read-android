package com.novelreader.typeset

import com.novelreader.model.TextSegment
import com.novelreader.ui.compose.RubyLayoutHelper

/**
 * 縦組みの制約。列送り（columnAdvancePx）はルビ帯込みの列中心間隔で、P3 で行間設定から算出する。
 */
data class TypesetConstraints(
    val columnHeightPx: Float,
    val fontSizePx: Float,
    val rubyFontSizePx: Float,
    /** 列送り＝本文列中心の間隔（ルビ帯込み）。 */
    val columnAdvancePx: Float,
    val indentFirstColumn: Boolean = true,
)

/**
 * 配置済みの1字（または縦中横1マス）。
 * @param x 列中心（アイテム左上原点・右→左に列が進む）
 * @param y 字の天（上端）
 */
data class PositionedGlyph(
    val text: String,
    val charClass: CharClass,
    val columnIndex: Int,
    val x: Float,
    val y: Float,
    val advancePx: Float,
)

/**
 * 配置済みのルビ部分文字列。
 * @param x ルビ帯中心 x（列の右側）
 * @param y ルビの天（上端）
 */
data class PositionedRuby(
    val text: String,
    val columnIndex: Int,
    val x: Float,
    val y: Float,
)

/** 1段落の組版結果（純データ）。 */
data class ParagraphLayout(
    val glyphs: List<PositionedGlyph>,
    val rubies: List<PositionedRuby>,
    val columnCount: Int,
    val widthPx: Float,
    val heightPx: Float,
)

/**
 * 縦組み器の境界。
 * なぜ interface で切るか: 公式の text-vertical（Compose の縦書き対応）が成熟したら
 * 自前組版から差し替えるためのつなぎ。ADR 0020（自前Compose組版＝つなぎ）が正本。
 */
interface VerticalTypesetter {
    fun typeset(segments: List<TextSegment>, constraints: TypesetConstraints): ParagraphLayout
}

/**
 * 既定の自前組版器。
 * 処理順: segments を書記素ユニット列へ展開 → LineBreaker で列へ折る →
 * 列ごとに x（列0＝最右）を確定 → RubyPlacer → ParagraphLayout。
 */
class DefaultVerticalTypesetter(private val metrics: FontMetricsProvider) : VerticalTypesetter {

    override fun typeset(segments: List<TextSegment>, constraints: TypesetConstraints): ParagraphLayout {
        val units = ArrayList<TypesetUnit>()
        val rubyReadings = HashMap<Int, String>()
        var rubySegmentCounter = 0

        for (segment in segments) {
            when (segment) {
                is TextSegment.Plain -> units += expandPlain(segment.text)
                is TextSegment.Ruby -> {
                    val idx = rubySegmentCounter++
                    units += expandRubyBase(segment.base, idx)
                    rubyReadings[idx] = segment.reading
                }
                // 枠のクロームは Compose 側・中身は呼び出し側が segments を展開して渡す契約のため、
                // StyledBlock をこの純層で受けるのは契約違反＝早期に落とす。
                is TextSegment.StyledBlock ->
                    throw IllegalArgumentException(
                        "StyledBlock は VerticalTypesetter が受け付けない（枠は Compose 側・中身は呼び出し側で展開して渡す契約）",
                    )
                // LineBreak/HorizontalRule は段落分割で消費される構造マーカー＝段落内容には来ない想定。
                // 万一来ても字面を持たないためスキップ（防御的・落とすほどの契約違反ではない）。
                TextSegment.LineBreak, TextSegment.HorizontalRule -> Unit
            }
        }

        val columns = LineBreaker.breakIntoColumns(
            units = units,
            columnHeightPx = constraints.columnHeightPx,
            metrics = metrics,
            fontSizePx = constraints.fontSizePx,
            indentFirstColumn = constraints.indentFirstColumn,
        )

        val columnCount = columns.size
        val widthPx = columnCount * constraints.columnAdvancePx
        // 列0＝最右。x = 右端 widthPx から左へ列送りの半分ずつ寄せた中心。
        val columnCenterX = (0 until columnCount).map { i ->
            widthPx - constraints.columnAdvancePx * (i + 0.5f)
        }

        val glyphs = ArrayList<PositionedGlyph>()
        var heightPx = 0f
        columns.forEachIndexed { columnIndex, column ->
            for (placed in column.units) {
                glyphs.add(
                    PositionedGlyph(
                        text = placed.unit.text,
                        charClass = placed.unit.charClass,
                        columnIndex = columnIndex,
                        x = columnCenterX[columnIndex],
                        y = placed.yTop,
                        advancePx = placed.advance,
                    ),
                )
                // 追い込みで容量超過しうるため、実測の列底を最大値で拾う。
                heightPx = maxOf(heightPx, placed.yTop + placed.advance)
            }
        }

        val rubies = RubyPlacer.place(
            columns = columns,
            columnCenterX = columnCenterX,
            rubyReadings = rubyReadings,
            fontSizePx = constraints.fontSizePx,
            rubyFontSizePx = constraints.rubyFontSizePx,
            metrics = metrics,
        )

        return ParagraphLayout(glyphs, rubies, columnCount, widthPx, heightPx)
    }

    /**
     * プレーン文字列を単位列へ展開する。半角数字/!? の極大ラン文脈で向きを確定する:
     * 2〜3桁→縦中横1ユニット / 1桁→正立 / 4桁以上→各字回転 / 半角英字→各字回転。
     * それ以外（漢字・仮名・約物…）は書記素分割して CharClassifier に委ねる。
     */
    private fun expandPlain(text: String): List<TypesetUnit> {
        val units = ArrayList<TypesetUnit>()
        val runs = maximalHalfWidthRuns(text)
        var pos = 0
        for (run in runs) {
            // ラン手前の非半角テキストは書記素分割して分類（半角英数字/!? は含まれない＝ROTATE誤判定なし）。
            if (run.start > pos) {
                appendClassifiedGraphemes(units, text.substring(pos, run.start))
            }
            val runText = text.substring(run.start, run.endExclusive)
            val len = run.endExclusive - run.start
            val isDigitOrBang = run.kind == HalfWidthKind.DIGIT || run.kind == HalfWidthKind.EXCLAM_QUEST
            when {
                // 2〜3桁の数字/!? は1マスの縦中横。
                isDigitOrBang && len in 2..3 ->
                    units.add(TypesetUnit(runText, CharClass.TATE_CHU_YOKO, isRubyBase = false, segmentIndex = -1))
                // 1桁の数字/!? は正立が慣行（分類器の文脈非依存 ROTATE をラン文脈で上書き）。
                isDigitOrBang && len == 1 ->
                    units.add(TypesetUnit(runText, CharClass.UPRIGHT, isRubyBase = false, segmentIndex = -1))
                // 4桁以上の数字/!?、および半角英字ランは各字を回転（欧文横倒し）。
                else -> for (c in runText) {
                    units.add(TypesetUnit(c.toString(), CharClass.ROTATE, isRubyBase = false, segmentIndex = -1))
                }
            }
            pos = run.endExclusive
        }
        if (pos < text.length) {
            appendClassifiedGraphemes(units, text.substring(pos))
        }
        return units
    }

    private fun appendClassifiedGraphemes(units: MutableList<TypesetUnit>, text: String) {
        for (g in RubyLayoutHelper.splitGraphemes(text)) {
            units.add(TypesetUnit(g, CharClassifier.classify(g), isRubyBase = false, segmentIndex = -1))
        }
    }

    /**
     * ルビ親文字を書記素分割し、各字に segmentIndex を付ける（按分は RubyPlacer が担う）。
     * 親文字は漢字が主で縦中横判定は不要のため、書記素ごとに素直に分類する。
     */
    private fun expandRubyBase(base: String, segmentIndex: Int): List<TypesetUnit> =
        RubyLayoutHelper.splitGraphemes(base).map { g ->
            TypesetUnit(g, CharClassifier.classify(g), isRubyBase = true, segmentIndex = segmentIndex)
        }
}
