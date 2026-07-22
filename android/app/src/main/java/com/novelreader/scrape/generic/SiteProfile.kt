package com.novelreader.scrape.generic

import com.novelreader.scrape.HealthProbe

/**
 * 段落の区切り方式。サイトの本文 HTML がどう段落を表すかで選ぶ。
 * - [BR]: 1つの本文コンテナ内を `<br>` で区切る旧来型（例: 暁）。連続 `<br>` は空行として保持。
 * - [P]: 段落ごとに `<p>` を並べる型（例: カクヨム構造）。`<p class="blank">` を空行として扱う。
 */
enum class ParagraphMode { BR, P }

/**
 * 「1プロファイル=1アダプタインスタンス」の設定表 1 行（設計正本
 * `.claude/plans/generic-adapter-design-2026-07-23.md`）。[GenericSiteAdapter] がこの行を受け取り、
 * `siteKey=profile.siteKey` の独立アダプタとして振る舞う。これにより BookEntity.sourceSite・HealthProbe・
 * AdapterHealthCheck・ScrapeIntegrity が**無改修**で効く（1アダプタ=多サイトにしないのが要）。
 *
 * なぜ外部リソース/リモート設定にせず Kotlin 定数表か（Why-not）: コンパイル時の型検査を失い、fixture golden
 * （siteKey=fixture ディレクトリ名で 1 対 1）との対応が実行時までズレ検知不能になる。「破損は golden で機械検知」
 * 方針（ADR 0024 §3）と矛盾するため、設定は型付き定数として持つ。
 *
 * @param hosts 対象ホスト。判定は「完全一致 ＋ `.host` サフィックス」（`akatsuki-novels.com` は `www.` も拾う）。
 * @param workUrlRe 作品トップ・話ページのいずれからも正規化に要る値を capture する正規表現。
 * @param workUrlTemplate [workUrlRe] の capture group を `{1}`/`{2}`… で埋めた作品トップ正規 URL の雛形。
 * @param tocLinkSelector 目次ページで話リンク候補の `<a>` を選ぶ CSS セレクタ（非話リンクは [episodeUrlRe] で弾く）。
 * @param episodeUrlRe href がこれに一致した `<a>` のみを話として数える（章見出し行・ヘッダ等の除外フィルタ）。
 * @param titleSelectors 作品名／話題を先頭から試すフォールバック連鎖（目次では作品名・章では話題に解決する）。
 * @param authorSelector 著者名の CSS セレクタ（無いサイトは null）。
 * @param bodySelectors 本文コンテナのフォールバック連鎖（先頭で取れなければ次へ＝競合 Alphapolis `novelBoby ?? novelBody` 式）。
 * @param forewordMarkers 本文ブロック直前の見出しがこれなら前書き/後書きとみなし本文から除外する。
 * @param crawlDelayMs このサイトへ課す最低リクエスト間隔（既定 3000ms＝個人運営サイト配慮の保守値）。
 * @param healthProbe 破損監視（層3）の自己診断宣言。回帰 golden の元作品を使い期待値の二重管理を避ける。
 */
data class SiteProfile(
    val siteKey: String,
    val displayName: String,
    val hosts: List<String>,
    val workUrlRe: Regex,
    val workUrlTemplate: String,
    val tocLinkSelector: String,
    val episodeUrlRe: Regex,
    val titleSelectors: List<String>,
    val authorSelector: String?,
    val bodySelectors: List<String>,
    val paragraphMode: ParagraphMode,
    val forewordMarkers: List<String> = listOf("前書き", "後書き"),
    val crawlDelayMs: Long = 3000L,
    val healthProbe: HealthProbe,
)
