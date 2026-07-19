# 0024: 汎用Web小説DL基盤＝サイトアダプタ抽象・規約ゲート・既存HTML契約への合流

- 日付: 2026-07-20
- 状態: 承認（handover 最優先B 2026-07-20 転換／設計判断は監督・実装フェーズ順は plan）
- 関連: 0010（なろう＝本文機械取得は規約NG）・0011/0012（なろうは取込WebView/閲覧WebView）・
  一次情報＝`.claude/plans/scraping-foundation-design-2026-07-20.md`（設計判断 D1〜D6・調査ダイジェスト・カクヨム実構造）

## 決定

1. **サイトごと抽出器を `scrape/` に分離する（`NovelSiteAdapter` IF）**。各サイト1実装。出力は既存 PDF 抽出と同じ
   `pdf.RawChapter`（本文段落は**中間ルビ記法 `|base《ruby》`**）とし、`ChapterProcessor.processForewordAfterword`→
   `HtmlExporter.exportToPwa` に合流させる。これで index.html/chap_N.html が **PDF蔵書とバイト同契約**で生成され、
   読書画面（ChapterHtmlParser/RubyText）を無改修で流用できる＝ルビ・HTMLエスケープ・目次生成を新経路で二重実装しない。

2. **規約線はコード上の「登録ゲート」で表現する（`SiteAdapterRegistry` の3値解決）**:
   - `Supported`＝利用規約が自前取得を禁じないサイト（アダプタ登録済み）。
   - `Blocked`＝本文の機械取得が規約違反のサイト（なろうグループ＝ADR 0010/0012）。**アダプタを持たず**公式サイト/API へ逃がす。
   - `Unsupported`＝未知サイト（アダプタ未整備）。公式サイト直行を案内。
   「非登録＝黙って無視」でなく明示3値にするのは、なろうURLを貼ったユーザーに「未対応」でなく
   「これは公式API＋WebViewで読む対象」と正しく案内するため（＝どのサイトにも公式直行の逃げ道を併設＝handover 確定事項③）。

3. **破損は fixture ゴールデンで機械検知する**。スクレイピングはサイトのHTML変更で壊れる前提（handover 確定事項④）。
   保存した実HTMLスナップショット（`test/resources/scrape_fixtures/<site>/`）に対する抽出結果を `testDebugUnitTest` で
   常時突き合わせ、構造変更を赤で検知する（既存 `JvmGoldenRegressionTest` の PDF golden と同流儀）。復旧＝fixture 撮り直し＋差分確認。

4. **パース（純関数・テスト対象）と取得（ネットワーク）を分離**し、Crawl-delay は `ScrapeHttpClient` が直列化で担保する
   （カクヨム robots の Crawl-delay:1 準拠・低頻度アクセス）。`narou/network` は api.syosetu.com 固定Retrofitで任意URL不可＝別系統。

## 背景（Why）

なろう限定では求心力が足りず（第三者評価）、ハーメルン/カクヨム等を見据えた汎用オフラインDL基盤が最優先Bに昇格。
受け皿調査で「抽出出力HTMLの2ファイル契約に合わせれば読書画面は無改修流用可」「addBook の PDF固有部は extractBook 境界に
局在」「発見層は NarouNovel が UI 全域へ素通し漏出」が判明。取込元ごとに合法性が分岐する（なろう＝閲覧のみ／他サイト＝規約次第）
のが設計前提のため、規約分岐を Registry に集約した。

## Why not（不採用の選択肢）

- **新しい読書経路・HTML表現をWeb用に作る**: 却下。ルビ/エスケープ/目次の二重実装と翻訳劣化を生む。既存の中間記法へ合流が最小差分。
- **一律スクレイピング（全サイト同一扱い）**: 却下（ユーザー裁定）。規約で自動取得を禁じるサイトは除外＝Registry の Blocked。
- **サイト検出を暗黙の「非対応で無視」にする**: 却下。なろうURLに対し公式路（API＋WebView）へ導けず体験を損なう。
- **fixture を持たずライブ疎通テストで担保**: 却下。ネットワーク依存でCIが不安定・破損点の特定も遅い。スナップショット差分が最速の保守。

## スコープ外・後続（plan/handover が正本）

- パイプライン接続（P3）・破損監視の実行時層（P4）・発見層の WorkSummary 化（P5）は本ADRの機構の上に段階実装。
- Room スキーマ（取込元URL/サイト種別の永続化）は `feat/delete-source-pdf` の v20 と版衝突中＝**マージ順序と列統合の裁定待ち**
  （handover「汎用DL基盤 実装トラック」★裁定待ち①）。本ADRの scrape 層はスキーマ非依存で先行着地している。
- 対象サイトの個別規約照合は各アダプタ着手時（設計ラウンドでは全数検証しない）。アルファポリス/ハーメルンの裁定は保留（★裁定待ち②）。
