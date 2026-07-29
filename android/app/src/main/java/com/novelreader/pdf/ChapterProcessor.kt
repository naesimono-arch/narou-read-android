package com.novelreader.pdf

/** 前後書き整形後の章（body は HTML 文字列。移植元 process_foreword_afterword 出力の {title, body:str} 相当）。 */
data class ProcessedChapter(val title: String, var body: String)

/**
 * 段落リストを話数・前書き・後書きに分割/整形する（移植元 chapter_processor.py の HTML 版。
 * 「移植元」の意味は PdfBookExtractor の注記を参照＝python/ は現存しない）。
 *
 * plain/Node 版でなく HTML 中間表現を採る理由（設計判断2）は今も生きている:
 * 読み戻し経路（Jsoup/ChapterHtmlParser/TextSegment/RubyText）を無改修で温存するため、
 * 章本文は HTML 文字列のまま生成する。
 */
object ChapterProcessor {

    // モジュールロード時にコンパイルしておく（移植元 _RUBY_PATTERN と同一パターン）。
    // 量指定子は貪欲だが、親文字側 `[^《]+`・ルビ側 `[^》]+` が区切り文字を除外しているため
    // 隣接する 2 つのルビ記法をまたいで飲み込むことはない（貪欲/非貪欲で結果は変わらない）。
    private val RUBY_PATTERN = Regex("""\|([^《]+)《([^》]+)》""")

    /**
     * 段落列を「【題名】プレフィックス」で章に分割する（移植元 split_into_chapters と 1:1）。
     *
     * 本文のない章（題名直後に本文が無い＝currentBody が空）はサイレントにドロップする仕様。
     * 後書きの特殊処理はここでは行わない（processForewordAfterword が後書きタイトルを処理するため、
     * ここで畳み込むと二重処理になる）。
     *
     * @param noTitleFallback 文書全体に【題名】マーカーが1件も無いときの単一章タイトル。
     *   なぜ引数化するか＝単話（章見出しグリフが皆無で【題名】が1件も付かない作品）では、
     *   全段落が既定タイトル「作品情報・プロローグ」の単一章に流れ込み、目次と読書画面に
     *   実在しない嘘見出しが出る。マーカー皆無時に限り本パラメータ（本番は表紙由来の作品タイトル）を
     *   初期タイトルへ流用してこれを防ぐ。既定値は従来値のため、引数を省く既存呼び出し・テストは挙動不変。
     *   マーカーが1件でもあれば従来どおり先頭本文群は「作品情報・プロローグ」章となり本パラメータは無効。
     */
    fun splitIntoChapters(
        paragraphs: List<String>,
        noTitleFallback: String = "作品情報・プロローグ",
    ): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        // マーカーが1件でも在れば従来値、皆無のときのみ fallback を初期タイトルにする。
        // なぜ事前走査か＝先頭本文を読み始める前に初期タイトルを確定する必要があるため（後から遡って
        // 差し替えると「作品情報・プロローグ」が既に確定済みの章へ混入しうる）。
        val hasAnyTitleMarker = paragraphs.any { it.startsWith("【題名】") }
        var currentTitle = if (hasAnyTitleMarker) "作品情報・プロローグ" else noTitleFallback
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
     * length/zip は Kotlin では Char（UTF-16 単位）で数えるため、非 BMP のサロゲートペアを含む親文字では
     * 1 文字ずつの紐付けが崩れる。なろう本文は日本語 BMP のみで実害が無いと判断して対象外にしている
     * （＝将来 emoji 等を含む本文を扱うならここが最初に壊れる箇所）。
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
     *
     * ここで埋める `<hr>` は装飾でなく **読み戻し側との契約**: ChapterHtmlParser が
     * `"hr" -> TextSegment.HorizontalRule` として拾い、各スキンの場面転換線（SceneDividerM/P/J 等）を描く。
     * タグを変えると場面転換線が無音で消える（ゴールデンは本文 sha256 までしか見ておらず検出できない）。
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
