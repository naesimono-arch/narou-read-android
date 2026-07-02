package com.novelreader.pdf

/**
 * 段落リストを話数・前書き・後書きに分割/整形する（移植元 python/chapter_processor.py の HTML 版）。
 *
 * submission-B の ChapterProcessor(plain/Node 版) ではなく本番 Python の HTML 版を移植元とする（設計判断2）:
 * 読み戻し経路（Jsoup/ChapterHtmlParser/TextSegment/RubyText）を無改修で温存するため、
 * 章本文は HTML 中間表現のまま生成する。
 */
object ChapterProcessor {

    /**
     * 段落列を「【題名】プレフィックス」で章に分割する（移植元 split_into_chapters と 1:1）。
     *
     * 本文のない章（題名直後に本文が無い＝currentBody が空）はサイレントにドロップする仕様。
     * 後書きの特殊処理はここでは行わない（processForewordAfterword が後書きタイトルを処理するため、
     * ここで畳み込むと二重処理になる）。
     */
    fun splitIntoChapters(paragraphs: List<String>): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        var currentTitle = "作品情報・プロローグ"
        var currentBody = mutableListOf<String>()

        for (p in paragraphs) {
            if (p.startsWith("【題名】")) {
                if (currentBody.isNotEmpty()) {
                    chapters.add(RawChapter(currentTitle, currentBody))
                }
                currentTitle = p.replace("【題名】", "").trim()
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
}
