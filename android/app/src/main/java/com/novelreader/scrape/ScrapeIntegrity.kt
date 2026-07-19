package com.novelreader.scrape

import com.novelreader.pdf.RawChapter

/**
 * サイト構造変更の疑い（＝抽出結果が構造的に壊れている）を表す例外（破損監視・層1）。
 *
 * [ScrapeException] の派生: 通信・解析の一般失敗（HTTP エラー等）と区別し、呼び出し側（ViewModel）が
 * この型だけを判別して「サイト構造が変わった可能性があります＋公式サイトで読む」フォールバック導線へ
 * 落とせるようにする（層2＝逃げ道が保険の実体）。派生ゆえ既存の `is ScrapeException` 捕捉も従来どおり成立する。
 */
class ScrapeStructureException(message: String) : ScrapeException(message)

/**
 * 取込結果が構造的に妥当かを検査する共通層（アダプタ非依存・破損監視・層1）。
 *
 * なぜアダプタでなく共通層か（handover 確定事項・層1）: セレクタ不一致等の破損は全アダプタ共通の症状
 * （空 TOC・空本文・異常に短い本文）で現れる。判定を1か所へ集約し `addWebBook` の取得直後に適用すれば、
 * 各アダプタへ検査ロジックを二重実装せず、repository は例外型で分岐するだけで済む（重い検査を持たせない）。
 *
 * この実行時検知は粗い安全網であり、精密な構造ドリフト検知は fixture ゴールデン（KakuyomuGoldenTest）が担う
 * （D4 の役割分担）。ここでは「ユーザーの取込がゴミ HTML で成立してしまう」ことだけを実行時に堰き止める。
 */
object ScrapeIntegrity {

    /**
     * 本文合計の下限（非空白文字数）。これ未満（かつ 0 超）は「実質空＝構造変更の疑い」と見なす。
     *
     * なぜ 20 か（保守的な床値・短編の誤検知を避ける）: カクヨム等には掌編（超短編）が実在するが、実作の
     * 1エピソードは最低でも一文（日本語で優に 20 字超）を持つ。一方セレクタが外れて本文以外（ナビ断片・記号）
     * だけが数文字漏れた場合はこの床を割る。全体崩壊（0 字）は [verify] の条件②が先に捕捉するため、ここは
     * 「わずかに漏れたが実質空」の中間状態だけを対象にする。過検知（実在作の取込拒否）を最も重く見て、床は
     * 実作の最短をさらに下回る保守値に置く（見逃しは条件②と fixture ゴールデンが補うため床を上げない）。
     */
    const val MIN_TOTAL_CONTENT_CHARS = 20

    /**
     * 取込直後の [toc]（目次）と取得済み [chapters]（全章の生本文）を検査する。
     * 構造変更の疑いがあれば [ScrapeStructureException] を投げる（正常なら何もしない）。
     */
    fun verify(toc: ScrapedToc, chapters: List<RawChapter>) {
        // 条件①: 目次にエピソードが1件も無い（fetchToc の章数 0）＝作品構造を1件も辿れていない。
        if (toc.chapters.isEmpty()) {
            throw ScrapeStructureException("目次にエピソードが1件も無い（構造変更の疑い）")
        }
        // 条件②: 全章の本文が実文字 0（全行 blank）＝セレクタは当たったが中身が全く取れていない。
        val total = chapters.sumOf { realCharCount(it.body) }
        if (total == 0) {
            throw ScrapeStructureException("本文が全章で空（セレクタ不一致 or 構造変更の疑い）")
        }
        // 条件③: 本文合計が異常に短い（床値未満）＝わずかにゴミが漏れただけで実質空のケースを弾く。
        if (total < MIN_TOTAL_CONTENT_CHARS) {
            throw ScrapeStructureException(
                "本文が異常に短い（合計 ${total}字 < 床 ${MIN_TOTAL_CONTENT_CHARS}字・構造変更の疑い）",
            )
        }
    }
}

/**
 * 本文段落列の非空白文字数（破損検知・健全性診断で共有する純関数）。
 * 全角字下げ U+3000 等の空白は [Char.isWhitespace] が true を返すため数えない（字下げで水増しされない）。
 */
internal fun realCharCount(paragraphs: List<String>): Int =
    paragraphs.sumOf { line -> line.count { !it.isWhitespace() } }
