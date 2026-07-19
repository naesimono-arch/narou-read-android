package com.novelreader.scrape

import com.novelreader.pdf.RawChapter

/**
 * Web小説サイト1つ分の抽出器（サイトごとに1実装）。
 *
 * 設計の核（why）:
 * - 出力は既存 PDF 抽出と同じ [RawChapter]（title＋本文段落列）。本文段落は **中間ルビ記法 `|base《ruby》`**
 *   （ASCII パイプ）で返す。これを `pdf.ChapterProcessor.processForewordAfterword` → `pdf.HtmlExporter.exportToPwa`
 *   に通すと index.html/chap_N.html が **PDF 蔵書とバイト同契約**で生成され、読書画面（ChapterHtmlParser/RubyText）を
 *   無改修で流用できる。ルビ・HTML エスケープ・目次生成は既存経路に集約＝新規経路で二重実装しない。
 * - パース（純関数・テスト対象＝fixture ゴールデン）と取得（ネットワーク）を分離する。サイトの HTML は
 *   壊れやすい前提（handover 確定事項④）なので、壊れたら fixture ゴールデンの差分で機械検知できる形にする。
 * - 規約線: 実装するのは「利用規約で自動取得が禁止でない」サイトのみ（`SiteAdapterRegistry` が登録ゲート）。
 *   なろうグループ等 NG サイトはアダプタを持たず「公式サイトで読む」導線へ逃がす。
 */
interface NovelSiteAdapter {
    /** 永続化・ログ用の安定キー（例 "kakuyomu"）。BookEntity.sourceSite に入る。 */
    val siteKey: String

    /** UI 表示名（例 "カクヨム"）。 */
    val displayName: String

    /**
     * 任意の URL（作品トップ・話ページのいずれでも）を当該サイトの**作品トップ正規 URL**へ解決する。
     * このサイトの URL でなければ null（＝`matches` 兼用）。
     */
    fun canonicalWorkUrl(inputUrl: String): String?

    /** 作品トップ URL から目次（メタ＋順序付き章参照）を取得する（ネットワーク）。 */
    suspend fun fetchToc(workUrl: String): ScrapedToc

    /** 章参照から本文を取得する（ネットワーク）。body は中間ルビ記法 `|base《ruby》`。 */
    suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter
}

/** DL 時に確定する最小の作品メタ（発見層の WorkSummary とは別レイヤ）。 */
data class ScrapedWorkMeta(
    val title: String,
    val author: String?,
    /** 作品トップの正規 URL（BookEntity.sourceUrl に入る）。 */
    val workUrl: String,
)

/** 目次の1エントリ（読了順は [ScrapedToc.chapters] の並びが正）。 */
data class ScrapedChapterRef(
    val title: String,
    val chapterUrl: String,
)

/** 取得した目次（メタ＋順序付き章参照）。 */
data class ScrapedToc(
    val meta: ScrapedWorkMeta,
    val chapters: List<ScrapedChapterRef>,
)
