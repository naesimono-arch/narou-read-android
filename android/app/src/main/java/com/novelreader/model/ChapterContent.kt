package com.novelreader.model

/** 章の構造化データ（1つのchap_N.htmlに対応） */
data class ChapterContent(
    val title: String,
    val segments: List<TextSegment>,
)

/** テキストセグメント（パーサー出力の最小単位） */
sealed class TextSegment {
    data class Plain(val text: String) : TextSegment()
    data class Ruby(val base: String, val reading: String) : TextSegment()
    data object LineBreak : TextSegment()
    data object HorizontalRule : TextSegment()
    /** 前書き・後書きブロック（背景色付き領域） */
    data class StyledBlock(val label: String, val segments: List<TextSegment>) : TextSegment()
}

/** 目次の1エントリ（index.html の <li><a> に対応） */
data class TocEntry(val title: String, val fileName: String)
