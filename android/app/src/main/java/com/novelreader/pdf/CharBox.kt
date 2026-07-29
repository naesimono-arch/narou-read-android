package com.novelreader.pdf

/**
 * 抽出した 1 文字分の情報（移植元 pdf_extractor.py の char dict と等価）。
 * top は「ページ上端から文字上端までの距離」、bottom は「ページ上端から文字下端までの距離」。
 * rubyText は _associate_ruby で親文字へ後付けされるルビ（初期 null）。
 */
data class CharBox(
    val text: String,
    val fontName: String?,
    val size: Double,
    val x0: Double,
    val top: Double,
    val bottom: Double,
    var rubyText: String? = null,
)

/** 章分割の中間表現（移植元 chapter_processor.py の {"title","body"} 相当）。 */
data class RawChapter(
    val title: String,
    val body: MutableList<String>,
)
