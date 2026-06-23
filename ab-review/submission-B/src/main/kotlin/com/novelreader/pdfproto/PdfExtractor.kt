package com.novelreader.pdfproto

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/** PDF 解析時のユーザー向け分類例外（移植元 app.py のカスタム例外に対応）。 */
class EncryptedPdfError(message: String, cause: Throwable? = null) : Exception(message, cause)
class CorruptedPdfError(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 表紙(1ページ目)から得た書籍メタ情報。 */
data class BookMeta(val title: String, val author: String)

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
 */
class GlyphStripper : PDFTextStripper() {

    val pages: MutableList<MutableList<CharBox>> = mutableListOf()
    private var current: MutableList<CharBox> = mutableListOf()

    override fun startPage(page: PDPage) {
        current = mutableListOf()
        pages.add(current)
        super.startPage(page)
    }

    override fun processTextPosition(text: TextPosition) {
        val s = text.unicode
        if (s.isNullOrEmpty()) return

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

    /** 全ページの文字 dict を取得する（list[list[CharBox]]）。 */
    fun loadPages(doc: PDDocument): List<List<CharBox>> {
        val stripper = GlyphStripper().apply {
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
     * 旧実装は extractBookTitle / extractBookAuthor が個別に 1 ページ目を再ストリップしており、
     * 表紙のパースが 2 回走っていた。短編ではこの重複が抽出時間の無視できない割合を占めるため、
     * 1 パスに統合する（本文抽出の loadPages とは別パス＝著者/タイトルは表紙のみで完結するため）。
     */
    fun extractBookMeta(doc: PDDocument): BookMeta {
        val chars = loadFirstPage(doc)
        return BookMeta(titleFromChars(chars), authorFromChars(chars))
    }

    /**
     * 表紙文字列から「最大フォントサイズの文字を X 左→右(top→x0)で結合」してタイトルを得る。
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
     * 表紙文字列から著者(12pt・フッター除外)を結合して得る。移植元 extract_book_author と同一。
     * フッター(COVER_FOOTER_Y 付近)を除くのは、ページ番号/シリーズ名が著者と同サイズ(12pt)のため。
     */
    fun authorFromChars(chars: List<CharBox>): String =
        chars
            .filter {
                ParserRules.isClose(it.size, ParserRules.FONT_SIZE_AUTHOR) &&
                    !ParserRules.isClose(it.top, ParserRules.COVER_FOOTER_Y, absTol = ParserRules.COVER_FOOTER_Y_TOL)
            }
            .sortedWith(compareBy({ Math.round(it.top) }, { it.x0 }))
            .joinToString("") { it.text }
            .trim()

    // ==========================================================
    // 【Phase 01-02】 本文抽出エンジン
    // ==========================================================
    fun runFinalEngine(doc: PDDocument): List<String> {
        val totalPages = doc.numberOfPages
        val charListsByPage = loadPages(doc)
        return TextProcessor.processPages(charListsByPage, totalPages)
    }
}
