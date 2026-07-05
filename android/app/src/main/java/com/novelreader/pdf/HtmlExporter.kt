package com.novelreader.pdf

import java.io.File
import java.util.Locale

/**
 * 整形済み本文を Compose 側（ChapterHtmlParser）が解釈する静的 HTML
 * （index.html / chap_N.html）へ書き出す（移植元 python/html_exporter.py の 1:1 移植）。
 *
 * 読書画面のナビ（前/次/目次/本棚）は Compose ネイティブ UI が担うため、HTML 側の
 * nav-footer リンクは実描画には使われない（旧 WebView 版の名残＝視覚的フォールバックとして温存）。
 *
 * **バイト等価が受入条件**（Task 7 穴1）: Python f-string の先頭改行・各行のインデント・
 * 末尾の空白（末尾改行なし）まで完全一致させる。テンプレート文字列を安易に再整形しないこと。
 */
object HtmlExporter {

    // 移植元 style（f-string）と 1 バイトも違えない共通スタイル。
    // 先頭 "\n" と末尾 4 スペース（末尾改行なし）は f-string の `\n    <style> … </style>\n    ` を再現する。
    // CSS 各行の 8 スペース字下げも load-bearing（ゴールデンに含まれる）＝コード側の字下げとは無関係に固定。
    private val STYLE =
        "\n" +
        "    <style>\n" +
        "        body { background-color: #fcfaf2; color: #333; font-family: \"MS Mincho\", \"Hiragino Mincho ProN\", serif; line-height: 1.8; margin: 0; padding: 0; -webkit-text-size-adjust: 100%; }\n" +
        "        .container { max-width: 600px; margin: 0 auto; padding: 20px 15px 80px 15px; background-color: #ffffff; min-height: 100vh; }\n" +
        "        h1 { font-size: 1.4em; border-bottom: 2px solid #e0dcd0; padding-bottom: 10px; color: #111; }\n" +
        "        .content { font-size: 1.15em; white-space: pre-wrap; word-wrap: break-word; }\n" +
        "        .nav-footer { position: fixed; bottom: 0; left: 0; width: 100%; background: rgba(252, 250, 242, 0.95); border-top: 1px solid #ddd; display: flex; justify-content: space-around; padding: 15px 0; backdrop-filter: blur(5px); }\n" +
        "        a { color: #8b4513; text-decoration: none; font-weight: bold; }\n" +
        "        ruby rt { font-size: 0.55em; color: #777; ruby-position: over; }\n" +
        "        ruby { ruby-align: center; }\n" +
        "        hr { border: 0; border-top: 1px dashed #ccc; margin: 30px 0; }\n" +
        "        .index-list { list-style: none; padding: 0; }\n" +
        "        .index-list li { padding: 15px 0; border-bottom: 1px solid #eee; }\n" +
        "    </style>\n" +
        "    "

    /**
     * 章列を index.html / chap_N.html へ書き出す（移植元 export_to_mobile_html）。
     *
     * @param bookId Python 引数との対応保持のため受けるが本文では未使用（export_to_pwa 経由の呼び分けのみ）。
     * @param progressCallback (pct, message) を各章生成後に通知（88〜99%）。null で無効。
     */
    @Suppress("UNUSED_PARAMETER") // bookId は Python シグネチャ parity のため保持＝意図的に未使用
    fun exportToMobileHtml(
        finalChapters: List<ProcessedChapter>,
        outputDir: File,
        bookTitle: String? = null,
        bookId: String? = null,
        progressCallback: ((Int, String) -> Unit)? = null,
    ) {
        if (!outputDir.exists()) outputDir.mkdirs()

        // タイトルは PDF 抽出由来で < > & を含みうるため HTML エスケープする
        // （本文 body は ChapterProcessor 側でエスケープ済みのため二重エスケープしない）。
        // Python: html.escape((book_title if book_title else "作品目次").strip()) ＝ strip してから escape。
        val indexHeading = htmlEscape((if (bookTitle.isNullOrEmpty()) "作品目次" else bookTitle).trim())
        val indexPageTitle =
            if (bookTitle.isNullOrEmpty()) "小説リーダー - 目次" else "$indexHeading - 目次"

        // index.html は全章を書き終えた後に最後に書き出す（Python と同順）。まず header を組む。
        val indexHtml = StringBuilder()
        indexHtml.append(
            "\n" +
            "    <!DOCTYPE html>\n" +
            "    <html lang=\"ja\">\n" +
            "    <head>\n" +
            "        <meta charset=\"UTF-8\">\n" +
            "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "        <title>$indexPageTitle</title>\n" +
            "        $STYLE\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <div class=\"container\">\n" +
            "            <h1>$indexHeading</h1>\n" +
            "            <ul class=\"index-list\">\n" +
            "    ",
        )

        val totalChapters = finalChapters.size
        finalChapters.forEachIndexed { i, chap ->
            val filename = "chap_${i + 1}.html"
            // 章タイトルも PDF 抽出由来のため目次リンク・<title>・<h1> でエスケープする。
            val safeTitle = htmlEscape(chap.title)
            indexHtml.append("<li><a href=\"$filename\">$safeTitle</a></li>")

            val prevPage = if (i > 0) "chap_$i.html" else "index.html"
            val nextPage = if (i < finalChapters.size - 1) "chap_${i + 2}.html" else "index.html"

            File(outputDir, filename).writeText(
                chapterHtml(safeTitle, chap.body, prevPage, nextPage),
                Charsets.UTF_8,
            )

            if (progressCallback != null) {
                // Python: pct = 88 + int((i+1)/total*11)。int() は 0 方向切り捨て＝Double.toInt() と同義。
                val pct = 88 + ((i + 1).toDouble() / totalChapters * 11).toInt()
                progressCallback(
                    pct,
                    "HTMLを生成しています… (${grouped(i + 1)}/${grouped(totalChapters)}章)",
                )
            }
        }

        indexHtml.append(
            "\n" +
            "            </ul>\n" +
            "        </div>\n" +
            "    </body>\n" +
            "    </html>\n" +
            "    ",
        )

        File(outputDir, "index.html").writeText(indexHtml.toString(), Charsets.UTF_8)
    }

    /** export_to_mobile_html への薄いラッパー（移植元 export_to_pwa）。 */
    fun exportToPwa(
        finalChapters: List<ProcessedChapter>,
        bookId: String?,
        realTitle: String?,
        outputDir: File,
        progressCallback: ((Int, String) -> Unit)? = null,
    ) = exportToMobileHtml(
        finalChapters,
        outputDir = outputDir,
        bookTitle = realTitle,
        bookId = bookId,
        progressCallback = progressCallback,
    )

    // chap_N.html 本文（移植元 chapter_html f-string）。8 スペース基準の字下げ・空行・末尾 8 スペース
    // （末尾改行なし）まで Python と 1 バイト一致させる。body は content 直下へ改行付きで挿入。
    private fun chapterHtml(
        safeTitle: String,
        body: String,
        prevPage: String,
        nextPage: String,
    ): String =
        "\n" +
        "        <!DOCTYPE html>\n" +
        "        <html lang=\"ja\">\n" +
        "        <head>\n" +
        "            <meta charset=\"UTF-8\">\n" +
        "            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
        "            <title>$safeTitle</title>\n" +
        "            $STYLE\n" +
        "        </head>\n" +
        "        <body>\n" +
        "            <div class=\"container\">\n" +
        "                <h1>$safeTitle</h1>\n" +
        "                <div class=\"content\">\n" +
        "$body\n" +
        "                </div>\n" +
        "            </div>\n" +
        "\n" +
        "            <div class=\"nav-footer\">\n" +
        "                <a href=\"$prevPage\">← 前へ</a>\n" +
        "                <a href=\"index.html\">目次</a>\n" +
        "                <a href=\"$nextPage\">次へ →</a>\n" +
        "            </div>\n" +
        "\n" +
        "        </body>\n" +
        "        </html>\n" +
        "        "

    // Python f"{n:,}" 相当（3 桁区切りカンマ）。ロケール非依存にするため US 固定（Python の {:,} は常にカンマ）。
    private fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
