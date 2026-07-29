package com.novelreader.pdf

import java.io.File
import java.util.Locale

/**
 * 整形済み本文を Compose 側（ChapterHtmlParser）が解釈する静的 HTML
 * （index.html / chap_N.html）へ書き出す（移植元 html_exporter.py）。
 * ※ python/ は 2026-07-05 の純 Kotlin 化で撤去済み＝「移植元」は git 履歴上の出自を指すだけで、
 *   追従すべき現物は存在しない（現在の受入基準は下記ゴールデン）。
 *
 * 読書画面のナビ（前/次/目次/本棚）は Compose ネイティブ UI が担うため、HTML 側の
 * nav-footer リンクは実描画には使われない（旧 WebView 版の名残＝視覚的フォールバックとして温存）。
 *
 * **バイト等価が受入条件**: 先頭改行・各行のインデント・末尾の空白（末尾改行なし）まで
 * `src/test/resources/golden_html/`（index.html / chap_1.html / chap_2.html）と 1 バイト一致させる。
 * 比較対象は現ゴールデンの実ファイルであり、`HtmlExporterGoldenTest` が testDebugUnitTest で守る。
 * テンプレート文字列を安易に再整形しないこと（整形するならゴールデン更新と同じコミットで）。
 *
 * さらに `<div class="content">` は [com.novelreader.parser.ChapterHtmlParser] が
 * `selectFirst("div.content")` で本文抽出の起点にする live な契約＝この class 名を変えると本文が空になる。
 */
object HtmlExporter {

    // ゴールデン（golden_html/index.html・chap_N.html）と 1 バイトも違えない共通スタイル。
    // 先頭 "\n" と末尾 4 スペース（末尾改行なし）・CSS 各行の 8 スペース字下げまで全て load-bearing
    // ＝ゴールデンに含まれるため、コード側の見た目を整える目的で字下げを変えるとテストが落ちる。
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
     * 出力先の特定は [outputDir] だけで完結する（書籍 id は呼び出し側が outputDir へ畳み込み済み）。
     * @param progressCallback (pct, message) を各章生成後に通知（88〜99%）。null で無効。
     */
    fun exportToMobileHtml(
        finalChapters: List<ProcessedChapter>,
        outputDir: File,
        bookTitle: String? = null,
        progressCallback: ((Int, String) -> Unit)? = null,
    ) {
        if (!outputDir.exists()) outputDir.mkdirs()

        // タイトルは PDF 抽出由来で < > & を含みうるため HTML エスケープする
        // （本文 body は ChapterProcessor 側でエスケープ済みのため二重エスケープしない）。
        // trim は PDF 表紙由来のタイトルに前後空白が混ざるため（見出しの字下がりを防ぐ）。
        val indexHeading = htmlEscape((if (bookTitle.isNullOrEmpty()) "作品目次" else bookTitle).trim())
        val indexPageTitle =
            if (bookTitle.isNullOrEmpty()) "小説リーダー - 目次" else "$indexHeading - 目次"

        // index.html は必ず全章を書き終えた後に最後に書き出す。なぜ順序が load-bearing か＝
        // BookEntity（`BookEntity.kt` の欠落判定）が index.html の実在を「一式が揃った」代表点に使うため、
        // 先に index を置くと途中失敗した書きかけ（torn）が「揃っている」と誤判定される。まず header を組む。
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
                // pct = 88 + ((i+1)/total*11) の 0 方向切り捨て（toInt）＝末章でちょうど 99% に着く。
                // 呼び出し側 PdfBookExtractor が (pct-88)/12 で step3 のローカル進捗へ戻すため範囲は変えない。
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

    /**
     * exportToMobileHtml への薄いラッパー（移植元 export_to_pwa）。
     *
     * かつて bookId を受けていたが、出力先は [outputDir] だけで決まる（呼び出し側が
     * `filesDir/novels/{bookId}` を解決済みで渡す）ため一度も使われていなかった＝2026-07-30 に撤去。
     */
    fun exportToPwa(
        finalChapters: List<ProcessedChapter>,
        realTitle: String?,
        outputDir: File,
        progressCallback: ((Int, String) -> Unit)? = null,
    ) = exportToMobileHtml(
        finalChapters,
        outputDir = outputDir,
        bookTitle = realTitle,
        progressCallback = progressCallback,
    )

    // chap_N.html 本文（移植元 chapter_html）。8 スペース基準の字下げ・空行・末尾 8 スペース
    // （末尾改行なし）まで golden_html/chap_1.html・chap_2.html と 1 バイト一致させる。
    // body は content 直下へ改行付きで挿入（ChapterHtmlParser が div.content 内をそのまま走査する）。
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

    // 3 桁区切りカンマ。端末ロケールで区切り文字が「.」等へ化けないよう US 固定にする
    // （進捗文言に出る数字＝ロケール依存にすると端末ごとに表示が割れる）。
    private fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
