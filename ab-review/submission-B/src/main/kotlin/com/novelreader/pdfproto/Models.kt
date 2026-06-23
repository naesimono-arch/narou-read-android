package com.novelreader.pdfproto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 抽出した 1 文字分の情報。
 * pdfminer 版の char dict（text/fontname/size/x0/top/bottom）と等価。
 * top は「ページ上端から文字上端までの距離」、bottom は「ページ上端から文字下端までの距離」。
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

/** 章分割の中間表現（Python: chapter_processor の {"title","body"} 相当）。 */
data class RawChapter(
    val title: String,
    val body: MutableList<String>,
)

// ---------------------------------------------------------------------------
// JSON 出力モデル（kotlinx.serialization）
// ---------------------------------------------------------------------------

@Serializable
data class Book(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
)

@Serializable
data class Chapter(
    val title: String,
    val paragraphs: List<Node>,
)

/**
 * 段落を構成する内容ノード。
 * sealed class なので JSON では既定の discriminator "type" が付与される
 *   → {"type":"plain","text":...} / {"type":"ruby","base":...,"reading":...}
 */
@Serializable
sealed class Node {
    @Serializable
    @SerialName("plain")
    data class Plain(val text: String) : Node()

    @Serializable
    @SerialName("ruby")
    data class Ruby(val base: String, val reading: String) : Node()
}
