package com.novelreader.scrape.generic

import com.novelreader.scrape.HealthProbe

/**
 * 汎用アダプタ（[GenericSiteAdapter]）の設定表。1 行 = 1 サイト = 1 アダプタインスタンス。
 * 行を足すたびに (1) 対応する fixture golden（`scrape_fixtures/<siteKey>/`）と (2) AndroidManifest の
 * ACTION_VIEW ホスト列挙を同期する必要があり、そのズレは SiteProfilesTest が機械検知する（手動同期点の封鎖）。
 */
object SiteProfiles {

    /**
     * 暁〜小説投稿サイト〜（akatsuki-novels.com）。旧・暁専用アダプタ実装（退役済み）をこのプロファイルへ
     * 移植した 1 行目（挙動差ゼロが移植条件。回帰は AkatsukiGoldenTest／AkatsukiProfileUnitTest が担保）。
     *
     * 構造の正本＝保存済み fixture（`scrape_fixtures/akatsuki/`・2026-07-23 取得スナップショット）:
     * - 目次は素の DOM。作品名＝最初の `<h3>`・著者＝`/users/view/{uid}` リンク・話一覧＝`table.list` 内で
     *   `/stories/view/` を持つアンカーのみ（章見出し行は colspan の `<b>…</b>` でリンクを持たず除外）。
     * - 本文は複数ありうる `div.body-novel`。前書き/後書きも同タグで、直前の `<div><b>前書き|後書き</b></div>`
     *   マーカーで判別し本文から除外する。段落は `<br>` 区切り（[ParagraphMode.BR]）。
     * - titleSelectors は h3→h2 の連鎖: 目次ページは h3（作品名）のみ・章ページは h2（話題）のみを持つため、
     *   同じ連鎖が両ページで正しく解決する（章ページの作品名 h1 は選ばない）。
     * - workUrlRe は作品トップ（index 形）・話ページ（view 形）双方が持つ `novel_id~(\d+)` を capture し、
     *   非 www/http もテンプレートで www/https の index 形 canonical へ畳む。
     */
    val AKATSUKI = SiteProfile(
        siteKey = "akatsuki",
        displayName = "暁",
        hosts = listOf("akatsuki-novels.com"),
        workUrlRe = Regex("""novel_id~(\d+)"""),
        workUrlTemplate = "https://www.akatsuki-novels.com/stories/index/novel_id~{1}",
        tocLinkSelector = "table.list a[href]",
        episodeUrlRe = Regex("""/stories/view/\d+/novel_id~\d+"""),
        titleSelectors = listOf("h3", "h2"),
        authorSelector = "a[href*=/users/view/]",
        bodySelectors = listOf("div.body-novel"),
        paragraphMode = ParagraphMode.BR,
        // robots に Crawl-delay 宣言は無いが、個人運営サイトへの配慮で既定 2500ms より厚い 3000ms を明示する。
        crawlDelayMs = 3000L,
        // 破損監視（層3）: fixture ゴールデンの元作品を使う。minChapters=30 は撮影時 66 話を大きく下回る保守値
        // （著者の整理でも割りにくく、セレクタ破損＝話数激減だけを赤にする）。
        healthProbe = HealthProbe(workUrl = "https://www.akatsuki-novels.com/stories/index/novel_id~4679", minChapters = 30),
    )

    /** 全プロファイル（Registry が [GenericSiteAdapter] を量産する順。破損監視・siteKey 一意検査もこの並びを走る）。 */
    val ALL: List<SiteProfile> = listOf(AKATSUKI)
}
