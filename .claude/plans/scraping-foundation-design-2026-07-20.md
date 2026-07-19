# 汎用オフラインDL基盤（最優先B）設計ラウンド — 一次情報（2026-07-20）

**対象ブランチ: `feat/scraping-prep`**（worktree `/home/qingj/wt/feat-scraping-prep`）
**状態: 調査中・設計案は未確定（ユーザー最終裁定前）**

## 確定事項（ユーザー裁定済み・handover 2026-07-20 転換より）

- 利用規約で自動取得が禁止のサイトは除外・別対応（一律スクレイピングしない）
- なろうは公式API優先・公式サイト直行の逃げ道をどのサイトにも併設
- スクレイピングはサイトHTML変更で壊れる前提で設計（脆さ織り込み・公式直行が保険）
- オフラインDL＝手元の蔵書コレクションの位置づけ（サイトごとに抽出器を分離できる設計）

## 対象サイトの裁定リスト（2026-07-20 ユーザー提供・一次入力）

**スクレイピング問題なし（OK側）**:
カクヨム／Arcadia／暁／Pixiv（※注1）／アルファポリス（大量ダウンロードに制限あり ※注2）／
ベリーズカフェ／エブリスタ／フォレストページ／魔法のiらんど／エムペ！／ナノ／BLove／
無料で読める大人のケータイ官能小説／ちょっと大人のケータイ小説／ポケットBLノベルクラブ／
Feard／野いちご／シルフェニア／ALICE+／
個人サイト全般（目次と本文で構成される、又は、次ページへのリンクが一定の形で存在するサイト）／
Wayback Machine（ウェブアーカイブ）

**ダメ（除外側）**: 小説家になろうグループ／マグネット！／ノベルピ

- ※注1・注2 の注記本文は未受領（Pixiv の条件・アルファポリスの制限の詳細は要確認）。
- **ハーメルンは OK 側に載っていない**（handover 最優先B の当初例示には登場）→ 規約調査（調査B）の
  一次ソース照合で裏取りし、除外なら handover の例示も直す。
- なろうグループ除外は ADR 0010/0012 の既決（本文の機械的取得＝違反）と整合。

## 調査A: 受け皿アーキ（Explore 委譲・2026-07-20 受領）

**核心制約**: ADR 0010/0012 は「本文の機械的取得＝違反」を**なろうに限って**確定済み
（`docs/decisions/0010:20,22,38`・0012）。→ 汎用DL基盤は**なろうには適用不可**・
なろうは恒久的に「メタ発見＋加工なし送客/WebView閲覧」。基盤は**取込元ごとに合法性が分岐する**設計を強いられる
（上のユーザー裁定リストがその分岐表の初期値）。

1. **蔵書1冊の定義**（`data/BookEntity.kt:8-38`）: 必須は `id/title/htmlDirPath` のみ。
   PDF前提列＝`contentSha256`（PDFバイト指紋 :29）・なろう固有＝`ncode`（:23 nullable）。
   取込元URL/サイト種別の列は**無い**。HTML実体は `filesDir/novels/<bookId>` 決定規約（:60）。
   → Web由来本もこの表に入る（contentSha256 は本文ハッシュ流用可・source URL 列の一般化が要る＝調査員の推測）。
2. **抽出出力HTML契約**（`pdf/HtmlExporter.kt`＋`parser/ChapterHtmlParser.kt`）:
   `index.html`＝`ul.index-list > li > a[href=chap_N.html]`（Exporter:76-85 / Parser:49-54）、
   `chap_N.html`＝`h1`＋`div.content`（Exporter:150-153 / Parser:25,73-123）。
   ルビ＝`<ruby>漢<rt>かん</rt></ruby>`（`ChapterProcessor.kt:76-78`）。中間表現＝`|親《よみ》`
   （`TextProcessor.kt:11`→`ChapterProcessor.kt:17` の `\|([^《]+)《([^》]+)》`）。メタはHTMLに埋めず Room 保持。
   → **Web本文をこの2ファイル契約で吐けば読書画面（ChapterHtmlParser/RubyText）は無改修流用可**。
3. **addBook の切れ目**（`repository/DefaultBookRepository.kt`）: PDF固有＝抽出関数注入
   `extractBook`（:72-77・呼出 :251-256）とその前段（PDFコピー :189-203・SHA256 :213・容量見積り :230-235）。
   下流（outputDir 規約 :193・title+author べき等 :271・insert :296-302・pending_jobs/権限 :308）は汎用。
   → **境界は extractBook ラムダ**。Web源の「取得→同契約HTML生成」実装へ差し替えれば骨格再利用可（推測）。
4. **発見層のなろう結合**: `NarouNovel`（なろうAPI JSON 直写し70+列）が `ui/discovery/*`・
   `ui/BookCard.kt`・`ui/skins/{j,m,p}/*` まで**広範に漏出**。`ResultContext`（`viewmodel/DiscoveryViewModel.kt:66`）
   も `DiscoveryQuery`（なろう検索語彙）を運ぶ。→ 新サイト追加には UI⇄API 間に**サイト非依存の作品モデル**の挿入が必須。
5. **ADR制約の要点**: 0010＝加工（見た目改変）＋本文機械取得がなろう違反・意匠は自前権利のPDF面に集中。
   0012＝なろう閲覧は JS注入ゼロ・URL観測のみ WebView。0011/0013＝縦書きPDF取込のWebView限定導線。
   ※2026-07-19 のユーザー裁定（handover「Google Play 公開準備」節）＝なろう公式PDF の手動取込→端末内整形再表示は
   「自動化された手段」に非該当との解釈で受容（現行設計の守り3点を維持）。

## 調査B: 対象サイト規約・API（general-purpose 委譲・Web一次ソース2点照合・2026-07-20 受領）

一次ソース（規約＋robots.txt／規約＋公式ヘルプ）で照合。要点：
- **なろう／なろうR18**: 公式APIはメタのみ（本文返さない）。規約14条23号で「API以外の自動アクセス・データ収集」明示禁止・
  ヘルプで「本文を機械的取得しアプリ表示/DL」を違反明記。→ 本文自前取得は不可＝ADR 0010/0012 と一致。robots は Crawl-delay:1・Meta系bot全Disallow。
- **カクヨム**（kakuyomu.jp）: **グレー＝自前取得を許容しうる**。規約14条の禁止対象は「サイト/アプリ（ソフト）の複製・改変・結合」で
  本文スクレイピングを名指す条項なし。robots は本文正規URL `/works/{id}/episodes/{id}` を Disallow していない（許容）。公式API/DL無し。
  → **初号機の最有力**（低頻度・Crawl-delay遵守・再配布しないが前提）。
- **ハーメルン**（syosetu.org）: グレー。自動取得の明文なし・robots本文許容だが「無断転載固くお断り」明示・公式API無し。`/conv/pdf/` は Disallow。
- **アルファポリス**（alphapolis.co.jp）: **除外寄りグレー**。規約**第10条3項**「配信コンテンツをいかなる方法でも複製・送信・翻案等できない」
  ＝本文DL/オフライン保存に抵触の恐れ。robots は本文 Disallow なし（Googlebot のみpage系制限）。公式API無し。→ 調査員は「除外・サイト直行推奨」。
- **エブリスタ／ノベルアップ＋**: 規約本文が 403 で一次到達不能＝**未確認・現時点は除外扱い**（手動で規約全文確認まで実装に組み込まない）。
- 一次ソースURL群は general-purpose 報告に収載（必要時 SendMessage で再照会可）。

### ユーザー裁定リストとの齟齬（要再裁定・実装が該当サイトに及ぶ前に提示）
1. **アルファポリス**: ユーザーOK側（注2「大量DL制限あり」）だが規約10条3項は複製を広範に禁止。ユーザーは制限を認識済みの可能性大だが、
   規約条項の存在を提示して最終確認したい（本文DL＝10条3項抵触の恐れ）。
2. **ハーメルン**: ユーザーOK側リストに**無い**（handover 当初例示にはあった）。除外意図か記載漏れか要確認。
3. 未確認サイト（エブリスタ/ノベルアップ＋/その他リスト多数）は各アダプタ着手時に個別規約照合（設計ラウンドでは全数検証しない）。
→ **初号機＝カクヨム**はこれら齟齬と無関係・OK側筆頭・規約調査も整合＝先行して安全に実装できる。

## 設計判断（監督の確定事項・2026-07-20）

> これらは監督（Claude）の設計判断。Plan/実装エージェントには「確定事項」として外給する。
> ユーザーの最終裁定は plan 提示時に仰ぐ（特に Room 移行・アダプタ初号機の対象サイト）。

### D1. データモデル（Room v19→v20 移行が必要＝着手時 `/db-migration` 必須）
- `BookEntity` に **`sourceUrl: String?`**（取込元の作品URL・PDFはnull）と **`sourceSite: String?`**
  （アダプタキー例 `"kakuyomu"`・PDFはnull）を追加。既存行は NULL＝「PDF由来／取込元不明」を意味させる。
- `contentSha256`（現・PDFバイト指紋）は**Web本文の連結ハッシュへ流用**（列名は据え置き＝意味を「取込内容の指紋」に一般化）。
- これにより handover の宿題「本削除時にPDF本体も削除するか」やWeb版「続きから」も同じ源泉列で扱える下地になる。

### D2. サイトアダプタ抽象（`narou/` の隣に新パッケージ `scrape/` を新設・サイトごと1ファイル）
- IF `NovelSiteAdapter`: `siteKey` / `displayName` / `matches(url): Boolean` /
  `resolveWork(url): WorkRef`（作品トップURL正規化） / `fetchToc(workUrl): List<ChapterRef>` /
  `fetchChapter(chapterUrl): RawChapter`（title＋本文を**中間ルビ記法 `|親《よみ》` で返す**＝既存 ChapterProcessor に合流）。
- レジストリ `SiteAdapterRegistry`（URL→アダプタ解決・OK/NGリストのゲートをここに集約）。
- **規約ガード**: NG サイト（なろうグループ／マグネット！／ノベルピ）は**アダプタを登録しない**＝URL一致しても
  「このサイトは公式サイトで読む」導線（＝逃げ道）のみ提示。なろうは既存 WebView 読書（ADR 0012）へ送客。

### D3. パイプライン接続（`extractBook` 境界で差し替え＝addBook 骨格を再利用）
- `DefaultBookRepository` の `extractBook` ラムダ（:72-77）を「源泉抽象」に一般化。
  PDF源＝現 `PdfBookExtractor.process`、Web源＝`WebNovelExtractor`（アダプタで TOC/章を取得→
  既存 `HtmlExporter` で `index.html`＋`chap_N.html` を同契約で吐く）。**下流（outputDir規約・insert・pending_jobs・権限）は不変**。
- Web源では PDFコピー/容量見積り経路（:189-235）を通らない別入口が要る＝`addBook` を「源泉種別で前段分岐・後段共通」へ薄く割る。

### D4. 破損監視システム（ユーザー要件「急な変化の監視」＝スクレイピングの脆さの保険）
- **セレクタ契約の自己診断**: 各アダプタが「安定既知の作品URL＋期待する最小抽出結果（章数下限・本文非空）」を宣言し、
  `AdapterHealthCheck` が実行時（章取得のたび）に**抽出結果が異常に空/短い**ことを検知して構造化ログ＋ユーザーへ
  「取得に失敗した可能性・公式サイトで読む」フォールバック提示（＝逃げ道が保険の実体）。
- **回帰の作り置き**: 各アダプタの HTML パースを**固定 fixture（保存した実HTMLスナップショット）に対する JVM ゴールデン**で
  `testDebugUnitTest` に載せる（ネットワーク非依存で常時緑・PDF golden と同じ流儀）。サイトHTML変更は fixture 更新＋
  差分で検知＝「保守を極限まで楽に」の中核。fixture 取得手順を docs 化。
- **開発用ヘルスボード**（debug ビルド限定・高負荷モードトグルと同じ節）: 全アダプタの自己診断を一括実行して緑/赤表示。

### D5. 発見層の脱なろう（最大の refactor・段階導入）
- UI⇄API 間に**サイト非依存の作品要約 `WorkSummary`**（title/author/synopsis/chapterCount/sourceSite/workUrl/coverや象徴）を挟む。
  `NarouNovel`→`WorkSummary` のマッパを `NovelApiRepository` に置き、`ui/discovery/*`・`BookCard`・`ui/skins/*` は
  `WorkSummary` のみ参照へ移す（NarouNovel 直参照を消す）。**この段は独立コミット群**（大きいので基盤成立後に着手）。
- 初期スコープでは「発見＝なろうAPIのまま・他サイトはURL貼付/共有インテントから直DL」で価値を先出しし、
  他サイトの検索統合（アダプタ `search`）は D5 完了後の後続とする（段階リリース）。

### 実装フェーズ順（loop の反復単位＝各フェーズ末で testDebugUnitTest 緑→コミット提案）
1. **P1 データ基盤**: Room v20 移行（`/db-migration`）＋ MigrationTest。※スキーマ変更＝要ユーザー確認。
2. **P2 アダプタ抽象＋初号機1サイト**: `scrape/` IF＋Registry＋規約ゲート＋最初の1アダプタ（対象は要裁定＝カクヨム or アルファポリス）。
   fixture ゴールデンを同時整備（D4）。
3. **P3 パイプライン接続**: `extractBook` 一般化＋Web源 addBook 入口＋取込導線（URL貼付/共有インテント受け）。
4. **P4 破損監視**: 実行時ヘルスチェック＋フォールバックUI＋開発ヘルスボード。
5. **P5 発見層 refactor（D5）**: WorkSummary 挿入（段階・大）。
6. **P6 後始末**: STATUS/handover/ADR 更新・/stale-check・不要コードやTODO掃除・fixture更新手順の docs 化。

## カクヨム実構造の確定（2026-07-20 ライブ recon・アダプタ実装の正本）

> robots 再確認: `/works/{id}/episodes/{id}` は許容・`/works/*/episodes/*/read$` のみ Disallow・Crawl-delay:1 尊重。
> ページは Next.js＝`<script id="__NEXT_DATA__" type="application/json">` に Apollo 正規化ストアが埋まる。

- **TOC 取得（正本＝JSON。DOM アンカーは6件しか出ず不可）**: `__NEXT_DATA__` を JSON パース→
  「値に `__typename==Episode` を持つ dict」＝Apollo ストアを探す（キー名 `apolloState` 固定ではないので値で探索）。
  順序は `Work:{workId}.tableOfContentsV2[]`（`{__ref:"TableOfContentsChapter:ID"}` の順序配列）→
  各 `TableOfContentsChapter.episodeUnions[]`（`{__ref:"Episode:ID"}` の順序配列）→ `Episode:{id,title}` を平坦化。
  ※ストアには関連作品の Work/Episode が混在（Work 31・Episode 593）＝**必ず URL の workId で `Work:{workId}` を選ぶ**。
- **作品タイトル**: `Work:{workId}.title`（og:title は " - カクヨム" 接尾つき＝代替）。
- **章本文（DOM）**: コンテナ `.widget-episodeBody.js-episode-body`＞直下 `<p id="pN">`。空行は `<p class="blank"><br/></p>`。
  章題は `.widget-episodeTitle`。ルビは**著者任意**（人気作でも不使用が多い＝14章走査で0）。出現時は標準 `<ruby>base<rt>reading</rt></ruby>`。
- **パイプライン接続**: アダプタは `RawChapter(title, body:List<String>)`（本文は**中間ルビ記法 `|base《ruby》`**＝ASCII パイプ）を返す→
  既存 `ChapterProcessor.processForewordAfterword`（`htmlEscape`→`applyRuby` の順・`|《》`はエスケープ生存）→`HtmlExporter.exportToPwa`
  で index/chap HTML を**PDF蔵書とバイト同契約**で生成＝読書画面(ChapterHtmlParser/RubyText)無改修。RawChapter は `pdf/CharBox.kt:19`。
- **依存**: jsoup 1.17.2・okhttp 4.12.0・mockwebserver 4.12.0（test）既存＝新規ゼロ。JSON は org.json（Robolectric/実機とも可）。

## 設計案の詳細化（Plan 委譲・2026-07-20 受領）＋監督の裁定

### ★★ ブロッカー（要ユーザー裁定・不可逆スキーマ／ブランチ横断）: Room 版衝突
- **事実（機械確認済み）**: 並列 worktree `feat/delete-source-pdf`（コミット `3782bec`・**main 未マージ**）が
  **v20 を消費済み**＝`books.sourceUri TEXT`（取込元PDFの content:// URI を削除機能用に永続化）を追加。
  私のブランチ（feat/scraping-prep）は現 **v19**。→ D1 の「v19→v20」は成立しない。
- **意味の非対称**: `sourceUri`（削除用・ローカルPDFの content:// URI）≠ 私の `sourceUrl`（Web作品の https:// URL）
  ＋`sourceSite`（アダプタキー）。列名が酷似し混同リスク大。
- **要裁定（統合順に依存・自律不可）**:
  (a) 2ブランチのマージ順序＝どちらが先に main へ入るか（先着が v20 を保持・後着は v21 へ退避）。
  (b) 「取込元 provenance」を1概念に統合するか（`sourceUri` を汎用化して Web/PDF 両対応にするか、独立2列で行くか）。
  → **P2/P3 の実装は進めつつ、この裁定が付くまで migration の版番号を確定・コミットしない**（P1 を後ろへ）。
  推奨たたき台: 独立2列を維持（機能が別・結合はマージ時の debt を増やす）・私のブランチは後着前提で v20 の sourceUri を
  複製してブリッジ→自分の2列を v21 で追加、を暫定設計にしておく（Plan 提案どおり）。マージ順が逆なら退避不要に単純化。

### フェーズ再編（P2 先行・スキーマ非依存を先に価値化）
- **新P1 = 旧P2**: `scrape/` アダプタ抽象＋カクヨムアダプタ＋fixtureゴールデン（スキーマ非依存・commit可）。← **今ここ**
- **新P2 = 旧P4 の一部**: 破損監視（fixtureゴールデン＝保守の中核・上と同時）。
- **新P3 = 旧P1**: Room 版（★裁定後）。
- 以降 旧P3（パイプライン接続）→ 旧P5（発見層 refactor）→ 旧P6（後始末）。

### Plan の実装知見（採用）
- **依存OK**: `app/build.gradle` に `jsoup:1.17.2`＋`okhttp:4.12.0` 既存＝**新規依存ゼロ**。ChapterHtmlParser も jsoup。
- **HTTP経路**: `narou/network/NarouNetwork.kt` は BASE_URL=api.syosetu.com 固定＝流用不可。`scrape/` 専用の別 OkHttpClient
  （UA＋**Crawl-delay:1**）を新設。Crawl-delay は Registry/Extractor 層で章取得間に実装。
- **fixtureゴールデン**: 既存 `test/java/com/novelreader/pdf/JvmGoldenRegressionTest.kt`（Robolectric・resolveRepoRoot）流儀。
  置き場 `test/resources/scrape_fixtures/kakuyomu/<id>.html`＋期待JSON。新規 `test/java/com/novelreader/scrape/KakuyomuGoldenTest.kt`。
- **P3 補足（Plan 指摘の穴）**: `DefaultBookRepository.addBook(:177)` 引数が `pdfUri:Uri` 固定＝**BookRepository IF に
  Web源メソッド追加が必要**（extractBook 差替だけでは足りない）。contentSha256 の Web本文連結ハッシュ流用も insert 経路(:296)に
  計算地点の新設が要る（PDFは:213）。
- **P5 規模（実測）**: `NarouNovel` 直参照＝main **26ファイル・93出現**（マッパ設置先 `narou/NovelApiRepository.kt:16`）。
  `WorkSummary` 挿入で touch は UI側15前後＋`ResultContext`(`viewmodel/DiscoveryViewModel.kt:66-71`) の Parcelable 連鎖。大規模＝独立コミット群。
