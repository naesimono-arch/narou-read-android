package com.novelreader.pdf

/** 前後書き整形後の章（body は HTML 文字列。移植元 process_foreword_afterword 出力の {title, body:str} 相当）。 */
data class ProcessedChapter(val title: String, var body: String)

/**
 * 章タイトルの出自。[ChapterProcessor.processForewordAfterword] が構造マーカー判定を
 * 掛けてよいかを決める（＝タイトル文字列を「機械が付けた構造情報」と読むか
 * 「著者が書いた自由文」と読むかの宣言）。
 *
 * なぜ出自を型で持つか（2026-08-06 監査 A1 の真因対処）: 同関数を PDF 取込と Web 取込が共有しており、
 * 両者でタイトル文字列の意味論が正反対だった。文字列だけを見る限りどんな判定式を選んでも
 * 「著者が偶然マーカー語を書いた話」を構造マーカーと誤読する余地が残る（誤読の代償は章の消失）。
 * 呼び出し側は自分が渡すタイトルの出自を必ず知っているので、そこで一度宣言させて曖昧さを断つ。
 */
enum class ChapterTitleSource {
    /**
     * なろう PDF 生成物の Bold 見出し（[TextProcessor.processPages] が "【題名】" 付きで出す）。
     * 生成器が付ける構造マーカーを含みうる＝マーカー判定の対象。
     */
    PDF_GENERATED,

    /**
     * サイト目次由来＝著者が書いた自由文（[com.novelreader.repository.WebBookImporter] 経路）。
     * 構造上ここにマーカーは現れない: 汎用アダプタが SiteProfile.forewordMarkers で
     * 前書き/後書きブロックを HTML パース時点に本文から除外済みで、章タイトルは常に話の題そのもの。
     * ＝マーカー判定を一切掛けない（掛ける利得がゼロで、誤爆の損失だけがある）。
     */
    AUTHOR_WRITTEN,
}

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

    /** [TextProcessor.processPages] が Bold 見出し段落へ付ける接頭辞。 */
    private const val TITLE_MARKER = "【題名】"

    /**
     * その【題名】段落が「実在の章見出し」か（＝構造マーカー見出しでないか）。
     *
     * なぜ区別が要るか（2026-09-04 の真因）: なろうの縦書きPDF 生成器は、前書き/後書きブロックにも
     * Bold 見出しを打つ。連載作品では〈話タイトル〉＋「（前書き）」/「（後書き）」の形になるが、
     * **単話（話タイトルが存在しない作品）では話タイトル部が空になり、裸の「（前書き）」「（後書き）」
     * だけの見出しが出る**（実測: N0089HK は「（後書き）」1件のみ・N7668GF は「（前書き）」「（後書き）」の
     * 2件のみで、本文側の見出しは1件も無い）。つまり単話でも【題名】の総数は 0 にならない。
     * 「章見出しの有無」をマーカー総数で測ると、この2つを章見出しと数えて単話判定が外れる。
     */
    private fun isRealChapterHeading(paragraph: String): Boolean =
        paragraph.startsWith(TITLE_MARKER) &&
            structuralMarkerOf(paragraph.removePrefix(TITLE_MARKER)) == null

    /**
     * 段落列を「【題名】プレフィックス」で章に分割する（移植元 split_into_chapters と 1:1）。
     * 呼び手は PDF 経路のみ（Web 取込は [processForewordAfterword] だけを使う）＝ここで
     * PDF 生成器の構造マーカーを解釈してよい。
     *
     * 本文のない章（題名直後に本文が無い＝currentBody が空）はサイレントにドロップする仕様。
     * 後書きの特殊処理はここでは行わない（processForewordAfterword が後書きタイトルを処理するため、
     * ここで畳み込むと二重処理になる）。
     *
     * @param noTitleFallback 文書に**実在の章見出し**が1件も無いとき（＝単話）の単一章タイトル。
     *   なぜ引数化するか＝単話では全段落が既定タイトル「作品情報・プロローグ」の単一章に流れ込み、
     *   目次と読書画面に実在しない嘘見出しが出る。本パラメータ（本番は表紙由来の作品タイトル）を
     *   初期タイトルへ流用してこれを防ぐ。既定値は従来値のため、引数を省く既存呼び出し・テストは挙動不変。
     *
     *   ⚠️ **判定条件は 2026-09-04 に訂正した**。旧条件は「【題名】マーカーが1件も無いとき」で、
     *   単話でも前書き/後書きの見出しだけは付く（[isRealChapterHeading] の実測）ため**発火しなかった**
     *   ＝後書き付き単話が丸ごと「作品情報・プロローグ」章になる実機症状の真因。
     */
    fun splitIntoChapters(
        paragraphs: List<String>,
        noTitleFallback: String = "作品情報・プロローグ",
    ): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        // 実在の章見出しが1件でも在れば従来値、皆無（＝単話）のときのみ fallback を初期タイトルにする。
        // なぜ事前走査か＝先頭本文を読み始める前に初期タイトルを確定する必要があるため（後から遡って
        // 差し替えると「作品情報・プロローグ」が既に確定済みの章へ混入しうる）。
        val hasRealChapterHeading = paragraphs.any { isRealChapterHeading(it) }
        var currentTitle = if (hasRealChapterHeading) "作品情報・プロローグ" else noTitleFallback
        var currentBody = mutableListOf<String>()

        for (p in paragraphs) {
            if (p.startsWith(TITLE_MARKER)) {
                val title = p.replace(TITLE_MARKER, "").trim()
                // 単話の「（前書き）」見出しだけは章の区切りにしない。
                // なぜ＝[processForewordAfterword] の前書きは「次の通常章の先頭へ前置」する向きだが、
                // 単話には本文側の見出しが存在しない（生成器が空タイトルの見出しを出さない）ため
                // 畳み込む先の通常章が最後まで現れず、**前書き以降の本文が丸ごと落ちて章数 0 になる**
                // （実測: N7668GF が finalChapters=0）。ここで区切らなければ前書きと本文が単一章に
                // 収まり、内容も順序も落ちない。
                // ⚠️ 代償＝前書きが装飾枠（（前書き）ボックス）を持たず本文冒頭に地の文として並ぶ。
                // 枠を保つには前書きと本文の境界が要るが、その境界はグリフ列に現れず（見出しが無く
                // ページ送りだけで区切られる）、段落列へページ境界を通す構造変更が要る＝別裁定。
                if (!hasRealChapterHeading && structuralMarkerOf(title) == StructuralMarker.FOREWORD) {
                    continue
                }
                if (currentBody.isNotEmpty()) {
                    chapters.add(RawChapter(currentTitle, currentBody))
                }
                currentTitle = title
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

    /** PDF 生成器が付ける構造マーカーの種別（本文の畳み込み先が前後で逆になるので区別する）。 */
    private enum class StructuralMarker { FOREWORD, AFTERWORD }

    /**
     * PDF 生成見出しが構造マーカーを名乗っているかを判定する（[ChapterTitleSource.PDF_GENERATED] 専用）。
     *
     * 判定形の根拠＝golden corpus 4 本（sample_pdfs/）の Bold 見出しを全数抽出した実測:
     * マーカー見出しは **1444 件すべてが〈話タイトル〉＋末尾「（前書き）」/「（後書き）」** の形で、
     * 裸の「前書き」「後書き」も半角括弧形も 0 件だった
     * （例: 「９　手を焼いてるよ（後書き）」。内訳 N2959KI 102 件・N6169DZ 1342 件・他 2 本は 0 件）。
     * よって「末尾の全角括弧付きマーカー」にアンカーする。
     *
     * 却下した案:
     * - 部分一致（"後書き" in title・移植元 process_foreword_afterword の元実装）＝監査 A1 の穴。
     *   Web の著者記述題「後書きにかえて」等を誤爆し、その話が前後章へ畳み込まれて章ごと消える
     *   （先頭章なら本文が丸ごと失われ、例外もログも出ない）。
     * - trim 後の完全一致のみ（先行の A1 修正）＝上記の実測どおり PDF 側に該当が 1 件も無く、
     *   本来の畳み込みが全滅した（JvmGoldenRegressionTest N2959KI の章数が 131→233 へ増加して赤）。
     * - 「末尾が全角括弧で閉じていれば構造マーカー」＝括弧付きの実在話題を誤爆する
     *   （同 corpus に「１２９　罪人の独白（前編）」「（後編）」が実在）。マーカー語まで含めて突き合わせる。
     * - 出自分岐だけで文字列判定は元のまま＝PDF 内部の誤爆（本文見出しにマーカー語を含む題）が残る。
     *   出自分岐は Web を守るが PDF 側の同定精度は上げないので、両方を重ねる。
     *
     * ⚠️ **corpus 4 本の実測に穴があった（2026-09-04 追記）**: 上の「1444 件すべてが〈話タイトル〉＋末尾
     * マーカー」は**連載作品しか含まない標本**の話で、単話（話タイトルが存在しない作品）では
     * 話タイトル部が空になり **「（前書き）」「（後書き）」だけの見出し**が出る（実測 N0089HK・N7668GF）。
     * 末尾アンカーなので判定自体はそのまま当たるが、「単話には【題名】が付かない」という前提は誤り。
     * 依存していた [splitIntoChapters] の単話判定を [isRealChapterHeading] へ差し替えてある。
     *
     * 裸の完全一致も残すのは、移植元 python 実装が保証していた最小契約（生成器が話タイトル無しの
     * 「後書き」単独見出しを出す版）を落とさないため。判定が PDF 経路限定になった今、
     * この枝が誤爆できる相手（著者記述題）は原理的に届かない＝残しても A1 は再発しない。
     * 半角括弧「(後書き)」は実測 0 件のため意図的に対象外にする（未実測の形へ判定を広げると、
     * 広げた分だけ誤爆＝章消失の面が増える。逆に取りこぼした場合は章数が増えるだけで本文は失われず、
     * ゴールデンの chapter_count が即座に赤くなる＝安全側に倒れる）。
     */
    private fun structuralMarkerOf(title: String): StructuralMarker? {
        // 生成器の見出しは前後に空白が付きうるため trim で吸収してから形を見る。
        val t = title.trim()
        return when {
            t == "前書き" || t.endsWith("（前書き）") -> StructuralMarker.FOREWORD
            t == "後書き" || t.endsWith("（後書き）") -> StructuralMarker.AFTERWORD
            else -> null
        }
    }

    /**
     * 章列の前書き/後書きを畳み込み HTML 本文へ整形する（移植元 process_foreword_afterword 相当。
     * ただしマーカー判定のみ移植元の部分一致から〈出自分岐＋末尾マーカーへのアンカー〉へ意図的に変更
     * ＝2026-08-06 監査 A1、根拠は [structuralMarkerOf] の KDoc）。
     *
     * @param titleSource 渡す章タイトルの出自。既定は本パッケージ本来の PDF 経路。
     *   **著者が書いたタイトル（Web 取込等）を渡すときは必ず [ChapterTitleSource.AUTHOR_WRITTEN] を明示する**
     *   ——既定のままだとマーカー判定が掛かり、万一形が一致した話が畳み込まれて章ごと消えるため。
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
    fun processForewordAfterword(
        chaptersData: List<RawChapter>,
        titleSource: ChapterTitleSource = ChapterTitleSource.PDF_GENERATED,
    ): List<ProcessedChapter> {
        val finalChapters = mutableListOf<ProcessedChapter>()
        var tempForeword = ""

        for (chap in chaptersData) {
            val title = chap.title
            // 著者記述タイトル（Web）には構造マーカーが存在しない＝判定自体を掛けない（監査 A1 の面を閉じる）。
            // PDF 生成見出しのみ [structuralMarkerOf] で同定する（判定形の根拠・却下案は同関数の KDoc）。
            val marker = if (titleSource == ChapterTitleSource.PDF_GENERATED) structuralMarkerOf(title) else null
            val bodyText = applyRuby(htmlEscape(chap.body.joinToString("\n")))

            if (marker == StructuralMarker.FOREWORD) {
                tempForeword = "<div style=\"background-color: #f9f9f9; padding: 15px; " +
                    "border: 1px solid #eee; margin-bottom: 20px;\">" +
                    "<b>（前書き）</b><br>$bodyText</div><hr>"
                continue
            }

            if (marker == StructuralMarker.AFTERWORD) {
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
