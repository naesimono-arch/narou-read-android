# 汎用サイトアダプタ（GenericSiteAdapter）設計 — 2026-07-23

> 対象ブランチ: `feat/scraping-prep`。ユーザー裁定（2026-07-23）＝「汎用アダプタで GO・規約グレー勢のまとめ裁定は保留」。
> 設計調査＝Plan 体ダイジェスト（競合解析 07-competitor-scraping-techniques.md の「汎用継承＋セレクタ表」型を既存契約へ翻訳）。
> 監督裁定＝G1 のみ着手・G2 見送り・実証は暁のプロファイル移植で行う。

## 核の設計: 1プロファイル=1アダプタインスタンス

`GenericSiteAdapter(profile: SiteProfile, http)` を設定表の行ごとに生成し、`siteKey=profile.siteKey` とする。
これにより `BookEntity.sourceSite`（独立2列・ADR 0024）・`HealthProbe`・`AdapterHealthCheck`（全アダプタ走査）・
`ScrapeIntegrity.verify`（アダプタ非依存）が**すべて無改修**で効く。「1アダプタ=多サイト」にしないのが要。

## SiteProfile スキーマ（Kotlin 定数表）

```kotlin
data class SiteProfile(
  val siteKey: String, val displayName: String,
  val hosts: List<String>,                          // 完全一致＋".host" サフィックス
  val workUrlRe: Regex, val workUrlTemplate: String, // canonicalWorkUrl（regex capture → template）
  val tocLinkSelector: String, val episodeUrlRe: Regex,
  val titleSelectors: List<String>, val authorSelector: String?,
  val bodySelectors: List<String>,                  // フォールバック連鎖 a ?? b（競合 Alphapolis novelBoby??novelBody 式）
  val paragraphMode: ParagraphMode,                 // P | BR
  val forewordMarkers: List<String> = listOf("前書き","後書き"),
  val crawlDelayMs: Long = 3000L, val healthProbe: HealthProbe,
)
```

**Why-not 外部リソース/リモート設定**: コンパイル時型検査の喪失・fixture golden（siteKey=fixture ディレクトリ名で1対1）
との対応が実行時までズレ検知不能・「破損は golden で機械検知」方針（ADR 0024 §3）と矛盾。

## フェーズ

- **G1（着手）**: 新規 `scrape/generic/SiteProfile.kt`・`GenericSiteAdapter.kt`・`SiteProfiles.kt`。
  parseToc/parseChapter は暁実装から純関数抽出・convertRuby は共有ヘルパ化。**暁を profile 1行目へ移植し
  AkatsukiAdapter（専用実装）を退役**＝既存 AkatsukiGoldenTest がそのままエンジンの回帰になる。
  カクヨムは JSON（`__NEXT_DATA__`）系＝CSS 表で表現不能のため**専用のまま温存**（競合の非対称戦略「重要は厚く・他は薄く」）。
  あわせて **pendingHosts ゲート**を Registry の blockedHosts 直後に新設: 裁定待ち5サイト
  （ハーメルン/アルファポリス/Pixiv/野いちご/ベリーズカフェ）を公式送り（Blocked 相当 UX）へ。現状は Unsupported に
  落ちる＝将来 catch-all が誤発動しうる構造穴の先回り封鎖。裁定が下りたら行を外すだけで解放。
- **G2（見送り・別裁定）**: readability 類似の本文自動検出（設定に無いホスト・ACTION_SEND 限定・pending/blocked
  通過後のみ発動）。個人サイト需要が見えたら要否から再判断。
- **G3（後続・サイトごと recon）**: 初期収載候補＝Arcadia／ナノ／エムペ！／ALICE+（着手時規約照合＋fixture 撮影が前提。
  「旧来型サーバサイド HTML」評価は recon で実証してから）。**BLove 除外**（アプリ専・匿名 GET 不可）。
  **Wayback は G1 表で扱えない特殊形**（host が任意の原サイトを包む）＝G2/専用処理へ後回し。

## テスト計画

1. 表エントリごと fixture golden 必須（toc 件数・話順・ルビ・前後書き除外の固定値照合）＝破損監視の核
2. `GenericSiteAdapterUnitTest`: ParagraphMode P/BR・bodySelectors フォールバック連鎖・episodeUrlRe 判定
3. `SiteProfilesTest`: 全 profile の hosts ⊆ Manifest ACTION_VIEW・siteKey 一意・fixture ディレクトリ実在（手動同期点の機械封鎖）
4. pendingHosts 回帰: 裁定待ち5サイトの URL が Supported にならないことを assert

## リスク

- Manifest VIEW ホスト列挙は表追加時の唯一の手動同期点 → テスト3で機械検知
- 表の表現力不足（JSON/AJAX 系サイト）→ 専用アダプタ併存で吸収（統合しない）
