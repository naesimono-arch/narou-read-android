package com.novelreader.scrape

import kotlinx.coroutines.CancellationException

/**
 * 全登録アダプタの自己診断（[NovelSiteAdapter.healthProbe]）を実行し緑/赤＋理由を返す（破損監視・層3）。
 *
 * 実ネットワークを張るのは [runAll] を呼んだときだけ＝debug ヘルスボードの手動実行時に限る
 * （取得はアダプタ内蔵の [ScrapeHttpClient] 経由＝Crawl-delay も守る）。結果は永続化しない（その場診断・P4 スコープ）。
 * 各アダプタの probe は独立に try/catch し、1件が落ちても他アダプタの緑/赤判定は続行する。
 */
class AdapterHealthCheck(
    private val adapters: List<NovelSiteAdapter>,
) {
    /** 1アダプタの診断結果。[healthy]=緑/赤・[detail]=緑の要約 or 赤の失敗理由（UI にそのまま出す）。 */
    data class Report(
        val siteKey: String,
        val displayName: String,
        val healthy: Boolean,
        val detail: String,
    )

    /** 全アダプタを順に診断する（各 probe はネットワークを1〜2回叩く＝Crawl-delay の分だけ時間がかかる）。 */
    suspend fun runAll(): List<Report> = adapters.map { probe(it) }

    private suspend fun probe(adapter: NovelSiteAdapter): Report {
        val p = adapter.healthProbe
        return try {
            val toc = adapter.fetchToc(p.workUrl)
            when {
                // 章数が期待下限を割った＝セレクタ破損等で目次がほぼ辿れていない疑い（構造ドリフトの主センサ）。
                toc.chapters.size < p.minChapters ->
                    red(adapter, "章数 ${toc.chapters.size} < 期待下限 ${p.minChapters}（構造破損の疑い）")
                else -> {
                    // 目次は取れても本文セレクタだけ壊れる破損があるため、先頭章を1件取得して本文非空も確認する。
                    val first = adapter.fetchChapter(toc.chapters.first())
                    val chars = realCharCount(first.body)
                    if (chars == 0) red(adapter, "先頭章の本文が空（本文セレクタ不一致の疑い）")
                    else green(adapter, "章数 ${toc.chapters.size}・先頭章 ${chars}字")
                }
            }
        } catch (e: CancellationException) {
            // ダイアログを閉じる等で診断コルーチンがキャンセルされたら握り潰さず素通しする（構造化ログ握り潰し禁止）。
            throw e
        } catch (e: Exception) {
            // 通信/解析/構造疑い（ScrapeException 系含む）はすべて赤として集約する（1件の失敗で全体を落とさない）。
            red(adapter, e.message ?: e.toString())
        }
    }

    private fun green(a: NovelSiteAdapter, detail: String) = Report(a.siteKey, a.displayName, true, detail)
    private fun red(a: NovelSiteAdapter, detail: String) = Report(a.siteKey, a.displayName, false, detail)
}
