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
     * このサイトへ課す最低リクエスト間隔（ミリ秒）。[ScrapeHttpClient] が per-host スロットルで下限に使う。
     * 既定 2500ms＝robots に Crawl-delay 宣言が無いサイトでも相手網へ十分優しい保守値（章連続 DL の礼儀）。
     * サイト固有に robots 等で緩め/厳しめが判れば override する。グローバル床（全ホスト横断 1000ms）は別途常に効く。
     */
    val crawlDelayMs: Long get() = 2500L

    /**
     * 任意の URL（作品トップ・話ページのいずれでも）を当該サイトの**作品トップ正規 URL**へ解決する。
     * このサイトの URL でなければ null（＝`matches` 兼用）。
     */
    fun canonicalWorkUrl(inputUrl: String): String?

    /** 作品トップ URL から目次（メタ＋順序付き章参照）を取得する（ネットワーク）。 */
    suspend fun fetchToc(workUrl: String): ScrapedToc

    /** 章参照から本文を取得する（ネットワーク）。body は中間ルビ記法 `|base《ruby》`。 */
    suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter

    /**
     * 自己診断の宣言（破損監視・層3＝debug ヘルスボード）。安定既知の作品 URL と抽出結果に期待する最小値。
     * これを [AdapterHealthCheck] が **debug の手動実行時にだけ** 実取得して緑/赤を判定する。
     *
     * なぜ既定実装を持たせず抽象のままにするか（IF 変更の判断）: 「安定既知の作品＋期待最小値」は本質的に
     * サイト固有で、汎用の既定値が存在しない（＝既定は必ず無意味）。全登録アダプタに自己診断の宣言を
     * コンパイル時に強制する方が D4 の設計意図（各アダプタが健全性を宣言）に合う。実装コストは probe 1個の宣言のみ。
     */
    val healthProbe: HealthProbe
}

/**
 * アダプタの自己診断宣言（破損監視・層3）。安定して存在する作品を1件指し、抽出結果に期待する最小値を持つ。
 * @param workUrl 期待が安定している既知作品の正規トップ URL（回帰 fixture の元作品を使うと期待値の二重管理を避けられる）。
 * @param minChapters 目次に期待する章数の下限（これ未満なら赤＝構造破損の疑い）。本文非空は [AdapterHealthCheck] が別途検査する。
 */
data class HealthProbe(
    val workUrl: String,
    val minChapters: Int,
)

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
