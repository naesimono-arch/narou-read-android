package com.novelreader.pdf

/** 前後書き整形後の章（body は HTML 文字列。移植元 process_foreword_afterword 出力の {title, body:str} 相当）。 */
data class ProcessedChapter(val title: String, var body: String)

/**
 * 段落リストを話数・前書き・後書きに分割/整形する（移植元 python/chapter_processor.py の HTML 版）。
 *
 * submission-B の ChapterProcessor(plain/Node 版) ではなく本番 Python の HTML 版を移植元とする（設計判断2）:
 * 読み戻し経路（Jsoup/ChapterHtmlParser/TextSegment/RubyText）を無改修で温存するため、
 * 章本文は HTML 中間表現のまま生成する。
 */
object ChapterProcessor {

    // モジュールロード時にコンパイルしておく（移植元 _RUBY_PATTERN と同一パターン）。
    // Kotlin Regex も既定で貪欲＝Python re と同一マッチ挙動。
    private val RUBY_PATTERN = Regex("""\|([^《]+)《([^》]+)》""")

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

    /**
     * |base《ruby》 マーカーを <ruby> タグへ変換する（移植元 _apply_ruby と 1:1）。
     * 親文字とルビの長さが一致する場合は 1 文字ずつ紐付ける（zip）。長さが異なる場合はまとめて 1 つの ruby に。
     *
     * length/zip は Kotlin では Char 単位＝Python len()/zip の code point 単位と BMP 文字では一致する
     * （なろう本文は日本語 BMP のため実害なし。非 BMP のサロゲートペアのみ差が出るが対象外）。
     */
    private fun applyRuby(text: String): String =
        RUBY_PATTERN.replace(text) { m ->
            val base = m.groupValues[1]
            val ruby = m.groupValues[2]
            if (base.length == ruby.length) {
                base.zip(ruby).joinToString("") { (b, r) -> "<ruby>$b<rt>$r</rt></ruby>" }
            } else {
                "<ruby>$base<rt>$ruby</rt></ruby>"
            }
        }

    // htmlEscape は HtmlExporter（タイトル）と共有するため HtmlEscape.kt のトップレベル関数へ集約した。
    // 同一パッケージのため下記 processForewordAfterword 内の htmlEscape(...) はそのまま解決される。

    /**
     * 章列の前書き/後書きを畳み込み HTML 本文へ整形する（移植元 process_foreword_afterword と 1:1）。
     *
     * 本文は「先に htmlEscape → 後に applyRuby」の順で処理する。なぜこの順か:
     * 抽出本文の生 < > & をそのまま HTML へ流すと Jsoup パースで本文欠落/タグ崩壊が起きるため先に無害化し、
     * その後ルビマーカー(| 《 》＝escape 対象外)を <ruby> へ変換する。順序を守れば両者は共存できる。
     *
     * 前書き: 次の通常章の先頭へ前置。後書き: 直前の章末へ追記（前章が無ければドロップ）。
     * div/hr の HTML 文字列は Python f-string とバイト等価に揃える（Task 7 のゴールデンの前提）。
     */
    fun processForewordAfterword(chaptersData: List<RawChapter>): List<ProcessedChapter> {
        val finalChapters = mutableListOf<ProcessedChapter>()
        var tempForeword = ""

        for (chap in chaptersData) {
            val title = chap.title
            val bodyText = applyRuby(htmlEscape(chap.body.joinToString("\n")))

            if ("前書き" in title) {
                tempForeword = "<div style=\"background-color: #f9f9f9; padding: 15px; " +
                    "border: 1px solid #eee; margin-bottom: 20px;\">" +
                    "<b>（前書き）</b><br>$bodyText</div><hr>"
                continue
            }

            if ("後書き" in title) {
                if (finalChapters.isNotEmpty()) {
                    val afterwordHtml = "<hr><div style=\"background-color: #f9f9f9; padding: 15px; " +
                        "border: 1px solid #eee; margin-top: 20px;\">" +
                        "<b>（後書き）</b><br>$bodyText</div>"
                    finalChapters.last().body += afterwordHtml
                }
                continue
            }

            val fullBody = tempForeword + bodyText
            finalChapters.add(ProcessedChapter(title, fullBody))
            tempForeword = ""
        }

        return finalChapters
    }
}
