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

- 対象サイトの個別規約照合は各アダプタ着手時（設計ラウンドでは全数検証しない）。アルファポリス/ハーメルンの裁定は保留（★裁定待ち②）。

## 追記（2026-07-20・P3〜P5 実装で確定した追加裁定）

P3（パイプライン接続）〜P5（発見層の脱なろう）は着地済み（完了の正本＝git log）。実装時に確定した設計裁定と Why-not:

1. **Room 版衝突は v21 退避＋19_20 複製で解決**（ユーザー裁定）: `feat/delete-source-pdf` が v20/sourceUri を先着消費
   → 当基盤は v21 へ退避し `MIGRATION_19_20` を同一SQLで複製・schemas/20.json も複製配置（task_diary #39 定石）。
   provenance 列は**統合せず独立2列**（`sourceUrl`=Web作品URL・`sourceSite`=アダプタキー。`sourceUri`=PDF削除用 content:// と
   意味差を近傍コメントで明示）。**マージ統合時に 19_20 の二重定義を一方へ寄せる**こと。
2. **Web源は pending_jobs 非対象**: PDF の再開キューは content:// URI＋FGS 前提。Web取込の失敗は即時通知・リトライは
   ユーザーの再共有で足りる（低頻度・軽量）。Why not キュー共通化＝URI 主キーと FGS 配線の改修コストに見合う頻度がない。
3. **ACTION_VIEW は対応ホスト限定・ACTION_SEND が任意サイトの受け口**: 全 http/https の VIEW filter は「全リンクのブラウザ候補に
   アプリが出る」UX破壊＋Blocked サイトのリンク横取りになるため却下。共有（SEND）経由なら任意URLを受け、Registry 3値で案内。
4. **取込時の構造疑い検知は保守的な床値**（ScrapeIntegrity 3条件・合計20字床）: 過検知（実在作の取込拒否）回避を最優先し、
   見逃しは fixture ゴールデン（本ADR §3）と全章空チェックが補完する二段構え。
5. **WorkSummary は UI 消費の全数調査（15プロパティ）から設計**: novelType/end はマッパ内で `serialState` 意味論enumへ吸収・
   `ncode`/`genreCode` はなろう固有の残置例外（KDoc 明記）・allcount センチネルは持ち込まない。`NarouNovel` は Moshi DTO として
   narou/ に閉じ込め（境界規則: main で NarouNovel 型参照可は narou/ のみ・機械 grep で検証可能）。
   `novelDetailsBulk` のみ NarouNovel 据置＝of=t-n-ga で writer 欠落＝WorkSummary 写像すると新着検知が全滅するため narou 内部限定。
   Why not 発見検索語彙（DiscoveryQuery/NarouNovelType）の同時汎用化＝D5 初期スコープ外（発見はなろうAPIのまま価値先出し）。

## 追記（2026-07-23・汎用アダプタ G1＝設定表駆動エンジン。ユーザー裁定「汎用アダプタで GO」）

一次情報＝`.claude/plans/generic-adapter-design-2026-07-23.md`。実装＝git log（`scrape/generic/`）。

1. **汎用機は「1プロファイル=1アダプタインスタンス」**（`GenericSiteAdapter(profile, http)`・siteKey=profile.siteKey）。
   Why not「1アダプタ=多サイト」＝sourceSite 列・HealthProbe・AdapterHealthCheck・ScrapeIntegrity が全て siteKey 単位の
   既存契約であり、多サイト集約はこの全てに分岐を強いる。表の各行を第一級アダプタにすれば**無改修で適合**する。
2. **設定表は Kotlin 定数（`SiteProfiles`）**。Why not 外部リソース/リモート設定＝コンパイル時型検査の喪失・
   fixture golden（siteKey=fixture ディレクトリ名で1対1）とのズレが実行時まで検知不能・「破損は golden で機械検知」
   （本ADR §3）の自動更新なし方針と矛盾。手動同期点（Manifest VIEW ホスト列挙）は `SiteProfilesTest` が機械照合。
3. **JSON/AJAX 系サイトは表に押し込まない（非対称戦略）**: カクヨム（`__NEXT_DATA__`）は専用アダプタ温存。
   CSS セレクタ表は SSR 静的 HTML 専用（暁が初号・競合実装解析の「重要は厚く・他は薄く」を踏襲）。
4. **pendingHosts ゲート**: 規約裁定待ちサイト（ハーメルン/アルファポリス/Pixiv/野いちご/ベリーズカフェ）は
   blockedHosts 直後の明示リストで公式送りへ。Why not Unsupported のまま放置＝将来 catch-all（G2）導入時に
   裁定待ちサイトへ誤発動する構造穴。裁定確定後は行削除だけで解放できる（登録ゲート＝本ADR §2 の運用と同型）。
5. **G2（ヒューリスティック本文自動検出）と Wayback は見送り＝要否から別裁定**。G3 recon（2026-07-23）で
   OK側の表駆動候補は暁で打ち止めと判明（ナノ/エムペ！/ALICE+ は HP レンタル型＝ユーザー毎構造で表不成立・
   エムペは bot 403 遮断・Arcadia は TLS 失効で検証不能）＝個人サイト系を拾う実質手段が G2 のみ、が次の裁定材料。
