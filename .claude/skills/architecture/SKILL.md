---
name: architecture
description: アプリ全体構成の入口。タスク→場所→罠の早見表と「コードから読み取れない設計判断・罠」だけを持つ（構造の詳細はコード/KDocが正本）。
when_to_use: 「アーキテクチャを教えて」／「全体構成を確認したい」／「どのファイルがどの役割か」 などの依頼で使う。
---

# アーキテクチャ早見表（WHERE-TO-LOOK）

日本語Web小説（なろう系）のPDFを、ふりがな対応HTMLに変換する Androidアプリ
（Jetpack Compose + 純 Kotlin PDF 抽出＝PDFBox-Android。旧 Chaquopy/Python 経路は 2026-07-05 Phase 5 で完全撤去。
精度オラクルの双子 `ab-review/submission-B` は残置）。
あわせて、**なろう公式APIによる作品の発見・検索**を第2の柱として持つ（下記「発見・検索層（なろうAPI）」）。

**構造の詳細（パイプラインのステップ構成・クラス関係）はコード/KDoc が正本**。
このスキルは「どこを見るか」と「コードから読み取れない罠・設計判断」だけを持つ。

## タスク → 場所 → 罠

| タスク | 場所 | 罠・注意 |
|---|---|---|
| PDF抽出ロジック | `android/app/src/main/java/com/novelreader/pdf/`（入口は facade `PdfBookExtractor.kt`＝4ステップ進捗・例外分類。ステップ構成は同ファイル KDoc） | `PDDocument.load` 前に `PDFBoxResourceLoader.init` 必須＝CID→Unicode 解決（`NovelReaderApplication.onCreate` で配線済み・task_diary #31）。グリフ正規化（波ダッシュ等）は `PdfExtractor` の `normalizeGlyphUnicode`（#35/#38） |
| 抽出のルール（文書ごと自動検出） | `pdf/DetectedRules.kt`（フォールバック定数＝`pdf/ParserRules.kt`） | 検出値は文書内実測＝定数直参照で挙動を推論しない |
| 精度の基準・回帰 | `ab-review/golden_regression/`＋実機ゲート `androidTest/…/pdf/PdfExtractorDeviceSpikeTest.kt`／HTMLバイト等価ゴールデン `src/test/resources/golden_html/` | 実機テストは `/device-verify` スキル必読（`connectedAndroidTest` 直叩きは蔵書DB消失＝task_diary #36） |
| UI（本棚/読書/目次） | `ui/BookshelfScreen.kt`（カード=`BookCard.kt`・バナー=`ProcessingBanner.kt`）／`ui/NativeReadingScreen.kt`（公開名 ReadingScreen。本文=`ChapterContent.kt`・設定=`ReadingSettingsSheet.kt`・エラー=`ReadingErrorScreen.kt`）。NavHost は本棚・読書の2ルート（"bookshelf"・"reading/{bookId}/{startFile}"）＋発見系（"discovery"・"discovery/search"・"discovery/genre"・"discovery/result"・"discovery/detail/{ncode}"・取り込み "discovery/detail/{ncode}/import"・**なろうWebView読書 "web-reader/{ncode}/{startEpisode}"**） | 読書画面（PDF蔵書）は **WebView ではなく Compose ネイティブ**（HTML解析=`parser/ChapterHtmlParser.kt`・ルビ=`ui/compose/RubyText.kt`）。目次 `NativeTableOfContentsScreen` は NavHost ルートでなく ReadingScreen 内から表示。⚠️**なろう作品の"閲覧"だけは例外的に WebView**（`WebReaderScreen`・ADR 0012＝加工なし・URL 観測のみ・JS 注入ゼロ。読書位置 `web_reading_progress` を URL から自動記録し続きから再開） |
| 見た目（配色・タイポ・余白）の変更 | まず `docs/decisions/0005-ui-n-visual-language-D.md`＋claude.ai/design のモック現物（`ui-n-phase0/*-D.html`・取得は `DesignSync: get_file`・入口は handover.md） | **HTMLモックが正本・Compose は翻訳**＝Compose 側で意匠を自己判断しない。色=`theme/Color.kt`・明朝=`theme/Typography.kt` の `MinchoFamily` 経由（直書き禁止） |
| 変換サービス | `PdfProcessingService`（Foreground）→ `BookRepository.addBook` → `PdfBookExtractor.process` | 下記「コードから読み取りにくい設計判断」 |
| データアクセス | `repository/BookRepository.kt`（Room + 抽出呼び出し。`NovelReaderApplication` がシングルトン保持し Service/ViewModel 共用） | DB操作は IO Dispatcher |
| DBスキーマ | `AppDatabase.kt`＋各 Entity が正典（version・Migration 含む） | 変更は必ず `/db-migration` スキルを先に実行 |
| 生成物の保存先 | `context.filesDir/novels/{bookId}/`（`index.html`＋`chap_N.html`） | — |
| 発見・検索（なろうAPI） | API層=`narou/`／VM=`viewmodel/Discovery*` 等／UI=`ui/discovery/`（詳細は下記「発見・検索層（なろうAPI）」） | 命名の非対称（傘は Discovery・テキスト検索部分だけ Search）＝`search` だけの grep は取りこぼす。検索履歴は Room でなく DataStore 別系統 |

## コードから読み取りにくい設計判断・罠

- **進捗/エラーのUI通知**: `NovelReaderApplication.processingState: StateFlow`（書き込みは `updateProcessingState()` のみ）＋ `errorEvents: Flow<String>`＝**Channel ベースの one-shot**。StateFlow だと画面回転で再表示・複数購読で重複するため Channel（受信時に消費・clearError 不要）。
- **多重起動制御**: `ReentrantLock` + `ArrayDeque<Uri>` のキュー方式（処理中の追加PDFは無音破棄せずキューへ）。「キュー追加+ループ起動判定」と「取り出し+終了判定」を1つの lock でアトミックに保護。
- **停止（ACTION_STOP）はページ境界で即中断**（2026-07-07 再配線）: キュー待ちは破棄し、処理中の1冊も子 Job（`currentBookJob`）を cancel → `BookRepository.addBook` の進捗コールバック内 `ensureActive()`（TextProcessor 自体は coroutines 非依存）が次のページ境界で中断する。ループ Job ごと cancel しないのは cancel〜finally 間に来た ACTION_START を取りこぼすレース回避（ループは生かし次周回の空キュー検知で `stopSelf`）。処理ループ中は `PARTIAL_WAKE_LOCK` 保持（OPPO のバックグラウンド強制停止対策）。
- **強制終了（OEM kill/OOM/onTimeout）からの再開**（2026-07-07 導入）: enqueue 時に `pending_jobs`（Room v8・`PendingJobDao`）へ記帳し成否確定で削除（明示停止は全消し＝再開しない）。次回起動時 `NovelReaderApplication.runStartupRecoveryOnce()`（MainActivity.onCreate トリガー・プロセス毎1回・Service 非稼働時のみ）が孤立HTML掃除（books に無い `novels/<id>/` を削除）→ 未完了ジョブを snackbar 通知＋権限が生きる分を FGS 再投入。プロセス跨ぎ読取のため `BookshelfViewModel.addBook` が `takePersistableUriPermission` 取得（解放は記帳削除時）・記帳の insert/全消しは `DefaultBookRepository.pendingJobMutex`（DAO 呼び出し完了までロック保持）で直列化＝「追加直後に停止」でも破棄済みジョブが復活しない（2026-07-11 `cccb4dc`。旧 `pendingJobDispatcher`＝並列度1は Room 再ディスパッチで直列化が不成立だったため撤去＝task_diary #55）。
- **読書進捗の上書き防止**: `index.html`（目次）閲覧時は進捗を上書きしない制御が `NativeReadingScreen.kt` にある。実装は `fileName != "index.html"` の**ブロックリスト方式**（`chap_` 接頭辞の許可リスト判定ではない）。
- OPPO/ColorOS 固有動作 → `/device-verify` スキル（症状→対処表）経由で `task_diary.md` を参照。

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
- `NovelApiRepository.kt` — API層の中核。`discover()`/`discoverPage()`(結果一覧のページング＝st/lim上限の検出込み)/`novelDetail()`・6h TTL インメモリキャッシュ(上限50・offset込みキー)・
  例外正規化(`NarouApiException`)・パラメータ組立・SHORT+RENSAI の2クエリマージ。ncode はドメイン/VM/UI層では `model/Ncode.kt`（@JvmInline value class）で受け渡し（Room/Moshi/Retrofit 境界のみ生 String）
- `model/DiscoveryQuery.kt` — 検索条件 DTO＋enum4種(`NarouOrder`/`NarouNovelType`/`NarouLastup`/`NarouAttr`)＋
  変換関数(`typeApiParam`/`lastupApiParam`)を**同一ファイルに同居**
- `model/`（他）— `NarouNovel`(APIレスポンス1件)・`DiscoveryResult`・`NarouGenres`(ジャンルコード表)・`NarouCuratedKeywords`(公式おすすめ語)
- `SearchHistoryStore.kt` — 検索履歴＋ピン留めの DataStore(`narou_search_history`)。合成は純関数
- `ContinuationLogic.kt` — なろうURL生成(`narouWorkUrl`/`narouEpisodeUrl`)＋PDF↔Web話数突合(`computeContinuation`)＋話ページURL→話数抽出(`parseNarouEpisodeNumber`＝機能②の WebView 読書位置観測)の純関数。
  ※`narou/` に在るが**突合本体は継続読書フロー(NativeReadingScreen/BookCard)側**で使う。発見側の作品詳細・本棚 Web カードの「なろうで読む/続きから読む」は `WebReaderScreen`（ADR 0012・URL 観測で `web_reading_progress` 記録）へ着地。※`NativeReadingScreen` の PDF 蔵書継続カードは当面 Custom Tabs 送客のまま（0012 スコープ外）

**VM/State層**（`viewmodel/`）:
- `DiscoveryViewModel` — ホーム／結果一覧／検索ドラフト／履歴を**単一VMで共有**（着地の共通コンテキスト `ResultContext`・`ResultSource` は同ファイル内）
- `NovelDetailViewModel` — 作品詳細（ncode）用に独立
- `SearchDraft` — 検索条件の下書き（`SearchFilters`/`SearchRange`＋トグル/レンジ合成の純関数群を同居）
- `MoodPreset` — 「気分で探す」プリセット→Query

**UI層**（`ui/discovery/`＝8ファイル。VM直結画面は route(VM結線)/Content(stateless描画) の2層分割＝`BookshelfScreen`/`BookshelfContent` と同型・Content は Robolectric でテスト＝ADR 0009）:
- `DiscoveryHomeScreen`（order 切替タブ）／`DiscoverySearchScreen`（条件シートは `SearchConditionSheet.kt` へ分離済み）／`DiscoveryGenreScreen`（唯一VM非依存・静的 `NarouGenres` 依存）
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
- 現況・進捗 → `STATUS.md`（発見・検索を含む全体の現況）／次アクション・技術的負債 → `handover.md`／実装 why → `docs/patterns/narou-api-discovery.md`
- API仕様 → `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）／機能検討(案A) → `03-api-feature-analysis.md`
- 検索UX設計原則 → `docs/decisions/0007-search-ux-three-principles.md`
- なろうAPI固有の落とし穴 → `task_diary.md`「なろう小説API（検索パラメータ）」節（#46 type の OR サイレント無視・#47 noveltype↔novel_type キー名）
