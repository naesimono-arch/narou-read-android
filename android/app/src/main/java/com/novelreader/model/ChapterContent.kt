package com.novelreader.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/** 章の構造化データ（1つのchap_N.htmlに対応） */
data class ChapterContent(
    val title: String,
    val segments: ImmutableList<TextSegment>,
)

/** テキストセグメント（パーサー出力の最小単位） */
@Immutable
// なぜ注釈が必要か: StyledBlock が ImmutableList<TextSegment> を内包し stability 推論が自己参照で循環→コンパイラが不安定側へ倒すため、注釈で不変契約を宣言して循環を断つ（全サブクラスは実際に不変）
sealed class TextSegment {
    data class Plain(val text: String) : TextSegment()
    data class Ruby(val base: String, val reading: String) : TextSegment()
    data object LineBreak : TextSegment()
    data object HorizontalRule : TextSegment()
    /** 前書き・後書きブロック（背景色付き領域） */
    data class StyledBlock(val label: String, val segments: ImmutableList<TextSegment>) : TextSegment()
}

/** 目次の1エントリ（index.html の <li><a> に対応） */
data class TocEntry(val title: String, val fileName: String)
