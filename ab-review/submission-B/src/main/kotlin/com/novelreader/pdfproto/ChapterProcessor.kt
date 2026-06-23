package com.novelreader.pdfproto

/**
 * 段落リストを話数・前書き・後書きに分割／整形する。
 * 移植元 chapter_processor.py の split_into_chapters / process_foreword_afterword を踏襲。
 *
 * 移植元は最終的に HTML を生成するが、本プロトタイプは JSON 出力のため
 * 「どのテキストがどの章に属すか」という構造だけを忠実に再現し、
 * ルビは <ruby> タグではなく Node.Ruby(base, reading) へ変換する。
 */
object ChapterProcessor {

    // |親文字《よみ》 マーカー（chapter_processor._RUBY_PATTERN と同一）
    private val RUBY_PATTERN = Regex("""\|([^《]+)《([^》]+)》""")

    fun splitIntoChapters(paragraphs: List<String>): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        var currentTitle = "作品情報・プロローグ"
        var currentBody = mutableListOf<String>()

        for (p in paragraphs) {
            if (p.startsWith("【題名】")) {
                if (currentBody.isNotEmpty()) {
                    chapters.add(RawChapter(currentTitle, currentBody))
                }
                currentTitle = p.removePrefix("【題名】").trim()
                currentBody = mutableListOf()
            } else {
                currentBody.add(p)
            }
        }
        if (currentBody.isNotEmpty()) {
            chapters.add(RawChapter(currentTitle, currentBody))
        }
        return chapters
    }

    /**
     * 前書き → 次の通常章の先頭へ、後書き → 直前の章末へ畳み込む。
     * 移植元 HTML 版の構造（temp_foreword 保持、後書きは直前章へ付与）を文字列レベルで再現。
     */
    fun processForewordAfterword(chapters: List<RawChapter>): List<RawChapter> {
        val finalChapters = mutableListOf<RawChapter>()
        var tempForeword: MutableList<String>? = null

        for (chap in chapters) {
            val title = chap.title
            when {
                title.contains("前書き") -> {
                    tempForeword = mutableListOf<String>().apply {
                        add("（前書き）")
                        addAll(chap.body)
                    }
                }
                title.contains("後書き") -> {
                    if (finalChapters.isNotEmpty()) {
                        val last = finalChapters.last()
                        last.body.add("（後書き）")
                        last.body.addAll(chap.body)
                    }
                }
                else -> {
                    val body = mutableListOf<String>()
                    tempForeword?.let { body.addAll(it) }
                    body.addAll(chap.body)
                    finalChapters.add(RawChapter(title, body))
                    tempForeword = null
                }
            }
        }
        return finalChapters
    }

    /** 整形済み章リストを JSON モデル（List<Chapter>）へ変換する。 */
    fun buildChapters(finalChapters: List<RawChapter>): List<Chapter> =
        finalChapters.map { Chapter(title = it.title, paragraphs = parseNodes(it.body.joinToString("\n"))) }

    /** 「|親《よみ》」マーカーを含む本文を plain / ruby ノード列へ分解する。 */
    internal fun parseNodes(text: String): List<Node> {
        val nodes = mutableListOf<Node>()
        var lastEnd = 0
        for (m in RUBY_PATTERN.findAll(text)) {
            if (m.range.first > lastEnd) {
                val plain = text.substring(lastEnd, m.range.first)
                if (plain.isNotEmpty()) nodes.add(Node.Plain(plain))
            }
            nodes.add(Node.Ruby(base = m.groupValues[1], reading = m.groupValues[2]))
            lastEnd = m.range.last + 1
        }
        if (lastEnd < text.length) {
            val plain = text.substring(lastEnd)
            if (plain.isNotEmpty()) nodes.add(Node.Plain(plain))
        }
        return nodes
    }
}
