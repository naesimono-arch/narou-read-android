---
name: architecture
description: アプリ全体のアーキテクチャを説明するスキル。PDF処理パイプライン・発見/検索層（なろうAPI）・UI層・Service層・DB・ファイル構造を網羅する。
triggers:
  - "アーキテクチャを教えて"
  - "全体構成を確認したい"
  - "どのファイルがどの役割か"
---

# アーキテクチャ概要

日本語Web小説（なろう系）のPDFを、ふりがな対応HTMLに変換する Androidアプリ。
あわせて、**なろう公式APIによる作品の発見・検索**を第2の柱として持つ（下記「発見・検索層（なろうAPI）」）。

## PDF処理パイプライン

Kotlin ネイティブ実装（PDFBox-Android `com.tom-roush:pdfbox-android`）。配置は
`android/app/src/main/java/com/novelreader/pdf/`。進捗は4ステップ（step 0〜3）で通知される。
（旧 Chaquopy(Python 3.12)+pdfminer 経路は **2026-07-05 Phase 5 で完全撤去**。移植の経緯・A/B評価は STATUS.md と
`handover.md` §D 参照。精度オラクルの双子 `ab-review/submission-B` は残置）

```
BookRepository.kt（Kotlin）
  └─ PdfBookExtractor.process(pdf, bookId, outputDir, onProgress)   ← facade
        step0: PdfExtractor.extractBookMeta()                          タイトル・著者を一括抽出（BookMeta）
        step1: PdfExtractor.runFinalEngine()                          本文抽出
                 文字座標・フォント情報から縦書きPDFを解析
                 （PDDocument.load 前に PDFBoxResourceLoader.init(context) 必須＝CID→Unicode 解決／task_diary #31。
                   波ダッシュは pdfminer に揃えて正規化 U+FF5E→U+301C／#35）
        step2: ChapterProcessor.splitIntoChapters()          【題名】マーカーで章分割
               ChapterProcessor.processForewordAfterword()   前書き・後書き処理、
                                                             |base《ruby》 → <ruby> HTML変換
        step3: HtmlExporter.exportToPwa()   index.html + chap_N.html を生成（旧 Python 出力とバイト等価）
        return BookMeta（title, author）
```

構成ファイル（`java/com/novelreader/pdf/`）:
- `PdfBookExtractor.kt` — facade。4ステップ進捗（typealias PdfProgress）＋例外分類（classifyPdfError）
- `PdfExtractor.kt` — PDFBox で文字座標抽出（CharBox）。`PDFBoxResourceLoader.init` は
  `NovelReaderApplication.onCreate` で1回（Service が Activity 無しでも走るため Application で先行初期化）
- `TextProcessor.kt` — 本文抽出コア（縦書き列復元・ルビ紐付け・ページ進捗）
- `ChapterProcessor.kt` — 章分割・前後書き HTML 整形
- `HtmlExporter.kt` — HTML 出力（バイト等価ゴールデン = `src/test/resources/golden_html/`）
- `CharBox / ParserRules / HtmlEscape / PdfExtractionException`（sealed 3型）

- 実機テスト harness は `androidTest/…/pdf/PdfExtractorDeviceSpikeTest.kt`（精度回帰ゲート）・`PdfPipelineDeviceTest.kt`。
  実行作法は `/device-verify` スキル参照（`connectedAndroidTest` 直叩きは蔵書DB消失＝task_diary #36）。

## UI層（Jetpack Compose）

```
MainActivity
  └─ NavHost（本棚・読書の "bookshelf" ／ "reading/{bookId}/{startFile}" ＋ 発見系5ルート
       "discovery"・"discovery/search"・"discovery/genre"・"discovery/result"・"discovery/detail/{ncode}"
       ＝画面詳細は別節「発見・検索層（なろうAPI）」）
       ├─ ui/BookshelfScreen.kt       — 書籍一覧、PDF選択（2026-07-02 分割:
       │    カード= ui/BookCard.kt ／ 処理バナー・空状態= ui/ProcessingBanner.kt）
       └─ ui/NativeReadingScreen.kt   — 読書画面（公開Composable名は ReadingScreen。2026-07-02 分割:
            │    本文描画= ui/ChapterContent.kt ／ 設定シート= ui/ReadingSettingsSheet.kt ／
            │    エラー画面= ui/ReadingErrorScreen.kt）
            ├─ Compose ネイティブ描画（WebViewではない）:
            │    ChapterHtmlParser で HTML をパース → LazyColumn + RubyText でルビ描画
            └─ index.html を開いたときは ui/NativeTableOfContentsScreen（目次）を表示
viewmodel/BookshelfViewModel
  └─ repository/BookRepository   — データアクセス層（Room + PdfBookExtractor 呼び出し）
NovelReaderApplication
  ├─ repository（シングルトン）   — Service/ViewModel 共用
  ├─ processingState: StateFlow<ProcessingState?>（書き込みは updateProcessingState() のみ）
  └─ errorEvents:     Flow<String> — Channel ベースの one-shot イベント（emitError() で送出）。
       StateFlow だと画面回転で再表示・複数購読で重複するため Channel（受信時に消費・clearError 不要）
```

**重要**: 読書画面は WebView ではなく **Compose ネイティブ描画**（`e82df4a` で WebView 版を削除し
`NativeReadingScreen` に一本化）。ルビ描画は `ui/compose/RubyText.kt`、HTML解析は
`parser/ChapterHtmlParser.kt`。目次は NavHost のルートではなく ReadingScreen 内から表示される。

**見た目の正本 ＝ /design の HTMLモック**: 配色・タイポ・余白・レイアウトといった静的視覚は、
claude.ai `/design`（HTMLデザインシステム）で作った HTMLモック（`ui-n-phase0/*-D.html`）が正本で、
上記 Compose 実装はその**翻訳**にすぎない。**見た目を変えるときは Compose 単独で決めず、
設計判断 `docs/decisions/0005-ui-n-visual-language-D.md` とモックを先に見る**こと。
色は `theme/Color.kt`、明朝は `theme/Typography.kt` の `MinchoFamily` 経由（トークン直書き禁止）。
モック現物は**リポジトリ内には無い**＝claude.ai/design プロジェクト `Novel Reader UI` の `ui-n-phase0/` 配下にあり
`DesignSync: get_file` で取得（入口は `handover.md`）。
（操作感・組版・アニメ・没入クロームはこのワークフローのスコープ外＝実機フィードバックで後詰め。ADR 0005 §B）

## Service層

```
PdfProcessingService（Foreground Service）
  └─ 処理ループ → BookRepository.addBook()
       └─ PdfBookExtractor.process → HTML生成（純 Kotlin / PDFBox）
```

- 進捗は `onProgress` ラムダ（`PdfBookExtractor.process` → `BookRepository`）→ `NovelReaderApplication.updateProcessingState()` → `processingState: StateFlow` 経由でUIに通知（`ProgressListener` 型は Phase 3 の直結化で廃止済み）
- **多重起動制御は `ReentrantLock` + `ArrayDeque<Uri>` のキュー方式**（`65abfe4` で導入）。
  処理中に別PDFが追加されてもキューに積まれ、ループが順次処理する（無音破棄しない）。
  「キュー追加+ループ起動判定」と「取り出し+終了判定」を1つの lock でアトミックに保護。
- OPPO のバックグラウンド強制停止対策として処理ループ中は `PARTIAL_WAKE_LOCK` を保持
- **全体停止**は `ACTION_STOP`（通知/本棚バナーの「停止」）→ キュー待ちを破棄し停止フラグ `isStopping` を立てる。
  停止ボタンのキャンセル粒度は現状 **PDF境界**（処理中の1冊は完走し、ループ次周回が空キューを検知して `stopSelf`）。
  ※ 純 Kotlin 化（Phase 3 の NonCancellable 緩和）で `processPages` のページ毎進捗を受けた
    `BookRepository` 側の onProgress が `ensureActive()` を呼ぶため（TextProcessor 自体は coroutines 非依存）、
    本文抽出中の割り込み中断**自体は可能**になった（旧 Chaquopy/JNI では原理的に不可能だった）。
    停止ボタンをページ境界の即中断へ再配線するのは別タスク（handover 参照）。

## データベース（Room）

```
AppDatabase（versionはAppDatabase.ktを直接参照）
  ├─ BookDao    → books テーブル（Phase 3 で ncode 列を追加＝PDF↔Web継続読書のなろう作品紐付け。定義は data/BookEntity.kt）
  └─ ProgressDao → progress テーブル（bookId, lastReadFilename）
```

現在のversion・カラム定義・Migrationリストは `AppDatabase.kt` と各 Entity ファイルが正典。
スキーマ変更手順 → `/db-migration` スキルを参照。

DB操作はすべて IO Dispatcher（Coroutines）で実行。

※ 発見機能の**検索履歴**は Room ではなく DataStore の別系統（`narou_search_history`）に持つ（次節）。

## 発見・検索層（なろうAPI）

第2の柱＝作品**発見**機能（テキスト検索はその一部）。100% なろう公式API（`https://api.syosetu.com/`）の
メタデータ取得のみで、**本文は取得しない**（キーレス・案A）。蔵書の Room とは**別系統**で Room には触れない
（検索履歴だけ DataStore `narou_search_history` に永続）。
**命名の非対称に注意**: 傘の機能名は Discovery だがテキスト検索の部分だけ Search（リポジトリのメソッドは `discover()`、
ネットワーク層は `search()`）＝`search` だけで grep すると Home/Genre/Result/Detail を取りこぼす。
（ブランチ名の "ai" は生成AIではなく api-lab の意）

**API層**（`java/com/novelreader/narou/`＝実質「API層」。`network`/`remote` 等の一般名ディレクトリは無い）:
- `network/NarouNetwork.kt` — Retrofit+OkHttp+Moshi(codegen) 配線・`BASE_URL`(api.syosetu.com)・UAインターセプタ(`NovelReader-Android/1.0`)
- `network/NarouApiService.kt` — 唯一のエンドポイント `@GET("novelapi/api/")` の `search(...)`。一覧も詳細もこの1本を引数で呼び分ける
- `NovelApiRepository.kt` — API層の中核。`discover()`/`novelDetail()`・6h TTL インメモリキャッシュ(上限50)・
  例外正規化(`NarouApiException`)・パラメータ組立・SHORT+RENSAI の2クエリマージ
- `model/DiscoveryQuery.kt` — 検索条件 DTO＋enum4種(`NarouOrder`/`NarouNovelType`/`NarouLastup`/`NarouAttr`)＋
  変換関数(`typeApiParam`/`lastupApiParam`)を**同一ファイルに同居**
- `model/`（他）— `NarouNovel`(APIレスポンス1件)・`DiscoveryResult`・`NarouGenres`(ジャンルコード表)・`NarouCuratedKeywords`(公式おすすめ語)
- `SearchHistoryStore.kt` — 検索履歴＋ピン留めの DataStore(`narou_search_history`)。合成は純関数
- `ContinuationLogic.kt` — なろう外部URL生成(`narouWorkUrl`/`narouEpisodeUrl`)＋PDF↔Web話数突合(`computeContinuation`)の純関数。
  ※`narou/` に在るが**突合本体は継続読書フロー(NativeReadingScreen/BookCard)側**で使う。発見側からは NovelDetailScreen が `narouWorkUrl`（Webで読む導線）のみ使用

**VM/State層**（`viewmodel/`）:
- `DiscoveryViewModel` — ホーム／結果一覧／検索ドラフト／履歴を**単一VMで共有**（着地の共通コンテキスト `ResultContext`・`ResultSource` は同ファイル内）
- `NovelDetailViewModel` — 作品詳細（ncode）用に独立
- `SearchDraft` — 検索条件の下書き（`SearchFilters`/`SearchRange`＋トグル/レンジ合成の純関数群を同居）
- `MoodPreset` — 「気分で探す」プリセット→Query

**UI層**（`ui/discovery/`＝7ファイル）:
- `DiscoveryHomeScreen`（order 切替タブ）／`DiscoverySearchScreen`（条件シート）／`DiscoveryGenreScreen`（唯一VM非依存・静的 `NarouGenres` 依存）
- `DiscoveryResultScreen`（検索/ジャンル/気分の**共通着地**）／`NovelDetailScreen`（作品カード詳細）
- `DiscoveryCommon`（Loading/Empty/Error 共通部品）／`DiscoveryQueryLabels`（条件チップ文言）
- ※本棚↔なろう紐付けシート `ui/NcodeLinkSheet.kt` は `ui/discovery/` ではなく `ui/` 直下＝**発見層ではなく読書画面(NativeReadingScreen)の継続読書フロー部品**

制御フロー（複数の別入口 → 同一の終着を1本で）:

```
検索実行 / ジャンル / 気分プリセット / 詳細キーワードタップ
  └─ すべて ResultContext を作り
       DiscoveryViewModel.openResult() → loadResult() → fetch(query)
         └─ NovelApiRepository.discover(query)   ← キャッシュ判定→パラメータ組立
              └─ NarouApiService.search(...)      ← Retrofit→なろうAPI→List<NarouNovel>
                   （先頭要素の allcount を list.drop(1) で分離）
作品詳細: NovelDetailViewModel.load(ncode) / BookCard / NativeReadingScreen
  └─ NovelApiRepository.novelDetail(ncode) → search(ncode=, lim=1) 同経路
```

**why 注記**（地図が無いと踏む罠）:
- 検索下書き(`SearchDraft`)は画面 `remember` でなく **VM 保持**＝画面を離れても条件が残る（意図的挙動）
- API制約由来の非自明ロジック（length/time 併用不可・lastup の連続レンジ合成・SHORT+RENSAI の2クエリマージ・属性 istt=OR）は `SearchDraft`/`DiscoveryQuery` に集中
- レスポンスの `allcount` は配列**先頭要素専用**（本体は `drop(1)`）／作品種別は `of` 指定有無で `noveltype`↔`novel_type` の**二重キー**（`NarouNovel.novelType` 合流アクセサで吸収）
- インメモリキャッシュは「全呼び出しが Main dispatcher」の暗黙不変条件に依存（Worker 化すると壊れる）

詳細の正本（この節は所在の地図に徹し、churny な現況・仕様はこれらを見る）:
- 現況・進捗 → `STATUS-api-lab.md`
- API仕様 → `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）／機能検討(案A) → `03-api-feature-analysis.md`
- 検索UX設計原則 → `docs/decisions/0007-search-ux-three-principles.md`
- なろうAPI固有の落とし穴 → `task_diary.md`「なろう小説API（検索パラメータ）」節（#42 type の OR サイレント無視・#44 noveltype↔novel_type キー名）

## ファイル保存先

```
context.filesDir/novels/{bookId}/
  ├─ index.html    — 目次
  ├─ chap_1.html
  ├─ chap_2.html
  └─ ...
```

## 特記事項

- `index.html`（目次ページ）閲覧時は読書進捗を上書きしない制御が `NativeReadingScreen.kt` に入っている
  （実装は `fileName != "index.html"` のブロックリスト方式。`chap_` 接頭辞の許可リスト判定ではない）
- OPPO/ColorOS 固有の動作については `/device-verify` スキル経由で `task_diary.md` を参照
- PDF抽出ロジックは `java/com/novelreader/pdf/` の Kotlin 実装が**唯一の正本**（旧 Python `src/main/python/` は
  2026-07-05 Phase 5 で撤去し二重構造を解消）。精度基準の一次情報は `ab-review/golden_regression/`＋
  実機ゲート `PdfExtractorDeviceSpikeTest`／HTML バイト等価ゴールデンは `src/test/resources/golden_html/`。
