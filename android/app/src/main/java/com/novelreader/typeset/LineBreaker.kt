package com.novelreader.typeset

/**
 * 組版の最小単位。1書記素、または縦中横として1マスに収める部分文字列（"12" 等）。
 * @param text 表示文字列
 * @param charClass この単位の向き
 * @param isRubyBase ルビ親文字か（RubyPlacer が親文字スパンを求めるのに使う）
 * @param segmentIndex Ruby セグメント由来ならその通し番号。非ルビは -1
 */
data class TypesetUnit(
    val text: String,
    val charClass: CharClass,
    val isRubyBase: Boolean,
    val segmentIndex: Int,
)

/**
 * 列内に配置済みの単位。yTop は列内でのこの字の天（上端）オフセット px。
 */
data class PlacedUnit(
    val unit: TypesetUnit,
    val yTop: Float,
    val advance: Float,
)

/** 1列（縦の1行分）。単位は上から下（yTop 昇順）に並ぶ。 */
data class Column(val units: List<PlacedUnit>)

/**
 * 禁則つきで単位列を縦の列へ折る（純ロジック）。
 *
 * 実装済みの禁則（ぶら下げは後続フェーズ＝未実装で良い旨、親プラン P6）:
 * - 行頭禁則（列頭に置けない字）: 前列へ「追い込み」＝容量超過を許して押し込む。連続する行頭禁則は続けて追い込む。
 * - 行末禁則（列末に置けない開き括弧）: 次列へ「追い出し」。
 * - 段落頭インデント: 有効時、最初の列の先頭に fontSizePx×1 の空きを入れる。
 * 縦中横 run・ルビ親文字も1ユニットとして通常どおり折る（親文字列の分断は許容＝按分は RubyPlacer の仕事）。
 */
object LineBreaker {

    /** 行頭禁則（列頭に来てはいけない）＝約物・小書き仮名・閉じ括弧・繰返し記号。 */
    private val LINE_HEAD_FORBIDDEN: Set<Char> = (
        "、。，．・：；？！ー…‥ゝゞ々" +
            "ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ" +
            "」』）〕］｝〉》】"
        ).toSet()

    /** 行末禁則（列末に来てはいけない）＝開き括弧。 */
    private val LINE_END_FORBIDDEN: Set<Char> = "「『（〔［｛〈《【".toSet()

    private fun isLineHeadForbidden(text: String): Boolean =
        text.length == 1 && text[0] in LINE_HEAD_FORBIDDEN

    private fun isLineEndForbidden(text: String): Boolean =
        text.length == 1 && text[0] in LINE_END_FORBIDDEN

    /**
     * @param units 展開済み単位列（読み順）
     * @param columnHeightPx 1列の縦容量 px
     * @param indentFirstColumn 段落頭インデントを最初の列に入れるか
     * @return 列ごとの配置結果（各単位に列内 y を確定）
     */
    fun breakIntoColumns(
        units: List<TypesetUnit>,
        columnHeightPx: Float,
        metrics: FontMetricsProvider,
        fontSizePx: Float,
        indentFirstColumn: Boolean,
    ): List<Column> {
        val columns = ArrayList<Column>()
        var current = ArrayList<PlacedUnit>()
        // 段落頭インデントは最初の列にだけ効かせる（改列時は y=0 に戻すので自然に1列目限定になる）。
        var y = if (indentFirstColumn) fontSizePx else 0f

        for (unit in units) {
            val adv = metrics.verticalAdvance(unit.text, fontSizePx)
            var needBreak = current.isNotEmpty() && (y + adv > columnHeightPx)

            // 行頭禁則: 改列するとこの字が次列の頭に来てしまう→改列せず前列へ追い込む（容量超過を許容）。
            if (needBreak && isLineHeadForbidden(unit.text)) {
                needBreak = false
            }

            if (needBreak) {
                // 行末禁則の追い出し: 閉じる直前の列末が開き括弧なら次列先頭へ移す。
                // なぜ size>1 条件か: 列が開き括弧だけになる場合は追い出し不能（空列化を避け、そのまま残す）。
                val carried = ArrayList<PlacedUnit>()
                while (current.size > 1 && isLineEndForbidden(current.last().unit.text)) {
                    carried.add(0, current.removeAt(current.size - 1))
                }
                columns.add(Column(current.toList()))
                current = ArrayList()
                y = 0f
                for (c in carried) {
                    current.add(PlacedUnit(c.unit, y, c.advance))
                    y += c.advance
                }
            }

            current.add(PlacedUnit(unit, y, adv))
            y += adv
        }

        if (current.isNotEmpty()) columns.add(Column(current.toList()))
        return columns
    }
}
