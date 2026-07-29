package com.novelreader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** 表紙(1ページ目)から得た書籍メタ情報。 */
data class BookMeta(val title: String, val author: String)

/**
 * PDFBox-android の CID→Unicode 出力を pdfminer（移植のオラクル）に揃える 1 文字正規化。
 *
 * なぜ: PDFBox-android は一部グリフを Adobe-Japan1/pdfminer と別コードポイントへ写す。放置すると
 * title・本文・章題が実機とオラクルでズレ、ゴールデン回帰がグリフ差だけで不一致になる。1:1 で対応が
 * 付くものをオラクル側へ寄せる（N6169DZ 章題ドリフト・task_diary #35）。写像:
 *   - FF5E FULLWIDTH TILDE → 301C WAVE DASH（有名な「波ダッシュ問題」の CMap 版。なろうでは波ダッシュが
 *     正で FF5E の正当用例はほぼ無く低リスク）
 *   - FF0D FULLWIDTH HYPHEN-MINUS → 2212 MINUS SIGN（章題6件）
 *   - 2191/2193 UP/DOWN ARROW → 2190/2192 LEFT/RIGHT ARROW（PDFBox が矢印を 90° 回転誤読するのを補正・章題3件）
 *
 * ⚠ FF0D→2212 は body にも同グリフが出れば正規化され、短中編の body_sha256（現状 pdfminer と完全一致）を
 *   破壊しうる＝pdfminer が本文では FF0D のまま出す証拠になる。実機ゲート(PdfExtractorDeviceSpikeTest)で
 *   検証し、短中編 body_sha256 が壊れたら FF0D→2212 は取り下げる（golden から離れる写像は入れない）。
 *   矢印は本文に出にくく低リスク。
 *
 * 各写像は個別 indexOf ガードで包み、対象を含まない大多数のグリフでは新規文字列を確保しない
 * （processTextPosition は 1 グリフ毎＝超長編で数百万回走るホットパス。PdfExtractorTest の assertSame 契約）。
 * 見た目が酷似する文字が多いため取り違え防止にエスケープで明示する。
 */
internal fun normalizeGlyphUnicode(s: String): String {
    var r = s
    if (r.indexOf('\uFF5E') >= 0) r = r.replace('\uFF5E', '\u301C')  // FULLWIDTH TILDE → WAVE DASH
    if (r.indexOf('\uFF0D') >= 0) r = r.replace('\uFF0D', '\u2212')  // FULLWIDTH HYPHEN-MINUS → MINUS SIGN
    if (r.indexOf('\u2191') >= 0) r = r.replace('\u2191', '\u2190')  // UPWARDS → LEFTWARDS ARROW
    if (r.indexOf('\u2193') >= 0) r = r.replace('\u2193', '\u2192')  // DOWNWARDS → RIGHTWARDS ARROW
    return r
}

/**
 * PDFTextStripper をカスタマイズし、processTextPosition で 1 文字ずつ座標付きで収集する。
 *
 * pdfminer 版の座標変換（top = page_height - y1, bottom = page_height - y0）に合わせ、
 * PDFBox の上原点座標から以下のように対応付ける：
 *   - x0     = getXDirAdj()                    （文字左端）
 *   - bottom = getYDirAdj()                    （上端からの距離＝文字下端）
 *   - top    = getYDirAdj() - getHeightDir()   （文字上端）
 *
 * sortByPosition は無効（既定）。並びは TextProcessor 側で座標から再構成する。
 *
 * 移植元 submission-B GlyphStripper と同一。import のみ apache→tom_roush へ差し替え
 * （TextPosition.{unicode,xDirAdj,yDirAdj,heightDir,font,fontSizeInPt} は 2.0.x 系で同名同義）。
 */
class GlyphStripper(
    // ページ開始ごとに「開始済みページ数(1始まり)」を通知する省略可のフック。
    // なぜ: 本文グリフ抽出は getText(doc) の単一走査で、そのままでは進捗を出さない。
    // load フェーズの進捗バー連動に使う（未指定＝通知なし＝オラクル/1ページ抽出など通知不要な用途）。
    private val onPageStart: ((loaded: Int) -> Unit)? = null,
) : PDFTextStripper() {

    val pages: MutableList<MutableList<CharBox>> = mutableListOf()
    private var current: MutableList<CharBox> = mutableListOf()

    override fun startPage(page: PDPage) {
        current = mutableListOf()
        pages.add(current)
        // pages.size ＝ 開始済みページ数。getText の全ページ単一走査中にページ毎に発火するため、
        // 支配的コストの本文抽出中も進捗バーを前進させられる（handover の UX ギャップ対策）。
        onPageStart?.invoke(pages.size)
        super.startPage(page)
    }

    override fun processTextPosition(text: TextPosition) {
        val raw = text.unicode
        if (raw.isNullOrEmpty()) return
        // PDFBox-android の CID→Unicode を pdfminer(オラクル)へ揃える（波ダッシュ等・task_diary #35）。
        val s = normalizeGlyphUnicode(raw)

        val bottom = text.yDirAdj.toDouble()
        val top = bottom - text.heightDir.toDouble()
        current.add(
            CharBox(
                text = s,
                fontName = text.font?.name,
                size = text.fontSizeInPt.toDouble(),
                x0 = text.xDirAdj.toDouble(),
                top = top,
                bottom = bottom,
            )
        )
    }
}

object PdfExtractor {

    /**
     * 全ページの文字を取得する（list[list[CharBox]]）。
     * onPageLoaded はページ開始ごとに (開始済みページ数, 総ページ数) を通知する（既定＝無通知）。
     */
    fun loadPages(
        doc: PDDocument,
        onPageLoaded: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): List<List<CharBox>> {
        val total = doc.numberOfPages
        val stripper = GlyphStripper(onPageStart = { loaded -> onPageLoaded(loaded, total) }).apply {
            sortByPosition = false
            startPage = 1
            endPage = Int.MAX_VALUE
        }
        stripper.getText(doc)
        return stripper.pages
    }

    /** 1 ページ目だけの文字を取得する（タイトル・著者抽出用）。 */
    private fun loadFirstPage(doc: PDDocument): List<CharBox> {
        if (doc.numberOfPages == 0) return emptyList()
        val stripper = GlyphStripper().apply {
            sortByPosition = false
            startPage = 1
            endPage = 1
        }
        stripper.getText(doc)
        return stripper.pages.firstOrNull() ?: emptyList()
    }

    // ==========================================================
    // 【Phase 00】 表紙からタイトル・著者を抽出
    // ==========================================================

    /**
     * タイトルと著者を「1 ページ目を 1 回だけストリップして」両方算出する。
     * 旧実装は title/author が個別に 1 ページ目を再ストリップしており表紙のパースが 2 回走っていた。
     * 短編ではこの重複が抽出時間の無視できない割合を占めるため 1 パスに統合する
     * （本文抽出の loadPages とは別パス＝著者/タイトルは表紙のみで完結するため）。
     */
    fun extractBookMeta(doc: PDDocument): BookMeta {
        val chars = loadFirstPage(doc)
        // 表紙フッター帯をページ高さ相対で出すため、実ページ高さを mediaBox から直接取る
        // （GlyphStripper/CharBox の型は変えない＝変更面最小化の設計判断）。ページ0が無ければ
        // 基準高さへフォールバック（この後 chars も空なので著者は空文字になる）。
        val pageHeight =
            if (doc.numberOfPages > 0) doc.getPage(0).mediaBox.height.toDouble()
            else ParserRules.COVER_PAGE_HEIGHT
        return BookMeta(titleFromChars(chars), authorFromChars(chars, pageHeight))
    }

    /**
     * 表紙文字列から「最大フォントサイズの文字を top→x0 順で結合」してタイトルを得る。
     * 移植元 extract_book_title と同一。座標計算を伴わない純関数なのでユニットテスト可能。
     */
    fun titleFromChars(chars: List<CharBox>): String {
        if (chars.isEmpty()) return "不明なタイトル"
        val maxSize = chars.maxOf { it.size }
        val title = chars
            .filter { ParserRules.isClose(it.size, maxSize, absTol = 0.1) }
            .sortedWith(compareBy({ it.top }, { it.x0 }))
            .joinToString("") { it.text }
            .trim()
        return if (title.isNotEmpty()) title else "無題の作品"
    }

    /**
     * 表紙文字列から著者（タイトル未満の最大サイズ群・フッター除外）を結合して得る。
     * 移植元 extract_book_author の思想を相対化: サイズは絶対 12pt 固定でなく「表紙内の最大サイズ
     * ＝タイトル、より小さい最大サイズ＝著者」と実配置から選ぶ（検出できなければ FONT_SIZE_AUTHOR へ退避）。
     * フッター帯はページ高さ相対（実高さ×COVER_FOOTER_Y/COVER_PAGE_HEIGHT・幅±COVER_FOOTER_Y_TOL）で除く。
     * フッターを除くのは、ページ番号/シリーズ名（発行元表記）が著者と同サイズで下部に出るため。
     */
    fun authorFromChars(chars: List<CharBox>, pageHeight: Double = ParserRules.COVER_PAGE_HEIGHT): String {
        if (chars.isEmpty()) return ""
        val authorSize = detectAuthorSize(chars)
        // フッター帯の中心をページ高さ相対で求める。golden 高さ(595.28)では 500.0 と数値等価になり現行挙動保存。
        val footerY = pageHeight * (ParserRules.COVER_FOOTER_Y / ParserRules.COVER_PAGE_HEIGHT)
        return chars
            .filter {
                ParserRules.isClose(it.size, authorSize) &&
                    !ParserRules.isClose(it.top, footerY, absTol = ParserRules.COVER_FOOTER_Y_TOL)
            }
            .sortedWith(compareBy({ Math.round(it.top) }, { it.x0 }))
            .joinToString("") { it.text }
            .trim()
    }

    /**
     * 表紙の著者サイズ＝「最大サイズ(タイトル)未満の最大サイズ群」。タイトル1種しかない表紙では
     * 検出不能なので FONT_SIZE_AUTHOR(=12.0) へフォールバックする。
     * なぜ最頻でなく最大: 著者はタイトル直下の見出し格で、惹句(より小さいサイズ)より必ず大きいという
     * 現行レイアウト事実に基づく（惹句 11pt 等を誤って拾わないため）。
     */
    private fun detectAuthorSize(chars: List<CharBox>): Double {
        val maxSize = chars.maxOf { it.size }
        val below = chars.map { it.size }.filter { !ParserRules.isClose(it, maxSize) }
        return below.maxOrNull() ?: ParserRules.FONT_SIZE_AUTHOR
    }

    // ==========================================================
    // 【Phase 01-02】 本文抽出エンジン
    // ==========================================================

    /**
     * 全ページを読み込み TextProcessor で段落列（章マーカー・ルビマーカー入り）へ変換する。
     *
     * onProgress は load(全ページのグリフ抽出＝超長編の支配的コスト)と process(段落化)を [EnginePhase]
     * で区別して通知する。facade(PdfBookExtractor) が両フェーズを重み合成し、load 中も進捗バーを前進
     * させるために渡す（未指定＝null なら通知しない＝オラクル/テスト用途）。
     */
    fun runFinalEngine(
        doc: PDDocument,
        onProgress: ((phase: EnginePhase, current: Int, total: Int) -> Unit)? = null,
    ): List<String> {
        val totalPages = doc.numberOfPages
        val charListsByPage = loadPages(doc) { loaded, total ->
            onProgress?.invoke(EnginePhase.LOAD, loaded, total)
        }
        // 本文処理の前に、この文書の実配置から解析パラメータを検出する（検出不能な項目は FALLBACK＝
        // 現行実測値へ退避）。生成側が同形状のまま寸法を微調整しても追随できるようにするため。
        val rules = DetectedRules.detect(charListsByPage)
        // processPages が出す pct(10-60) は元々未使用のため捨て、(processed, bodyTotal) のみ前送りする。
        return TextProcessor.processPages(charListsByPage, totalPages, rules) { _, processed, bodyTotal ->
            onProgress?.invoke(EnginePhase.PROCESS, processed, bodyTotal)
        }
    }
}
