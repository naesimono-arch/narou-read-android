---
name: architecture
description: アプリ全体のアーキテクチャを説明するスキル。PDF処理パイプライン・UI層・Service層・DB・ファイル構造を網羅する。
triggers:
  - "アーキテクチャを教えて"
  - "全体構成を確認したい"
  - "どのファイルがどの役割か"
---

# アーキテクチャ概要

日本語Web小説（なろう系）のPDFを、ふりがな対応HTMLに変換する Androidアプリ。

## PDF処理パイプライン

Kotlin ネイティブ実装（PDFBox-Android `com.tom-roush:pdfbox-android`）。配置は
`android/app/src/main/java/com/novelreader/pdf/`。進捗は4ステップ（step 0〜3）で通知される。
（旧 Chaquopy(Python 3.12)+pdfminer 経路は **2026-07-05 Phase 5 で完全撤去**。移植の経緯・A/B評価は STATUS.md／
`.claude/plans/kotrin-branch-python-kotrin-graceful-flute.md` 参照。精度オラクルの双子 `ab-review/submission-B` は残置）

```
BookRepository.kt（Kotlin）
  └─ PdfBookExtractor.process(pdf, bookId, outputDir, onProgress)   ← facade
        step0: PdfExtractor.extractBookTitle() / extractBookAuthor()   タイトル・著者を抽出
        step1: PdfExtractor.runFinalEngine()                          本文抽出
                 文字座標・フォント情報から縦書きPDFを解析
                 （PDDocument.load 前に PDFBoxResourceLoader.init(context) 必須＝CID→Unicode 解決／task_diary #31。
                   波ダッシュは pdfminer に揃えて正規化 U+FF5E→U+301C／#35）
        step2: ChapterProcessor.splitIntoChapters()          【題名】マーカーで章分割
               ChapterProcessor.processForewordAfterword()   前書き・後書き処理、
                                                             |base《ruby》 → <ruby> HTML変換
        step3: HtmlExporter.export()   index.html + chap_N.html を生成（旧 Python 出力とバイト等価）
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
  └─ NavHost（2ルート: "bookshelf" と "reading/{bookId}/{startFile}"）
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

- 進捗は `BookRepository.ProgressListener` 経由でUIに通知
- **多重起動制御は `ReentrantLock` + `ArrayDeque<Uri>` のキュー方式**（`65abfe4` で導入）。
  処理中に別PDFが追加されてもキューに積まれ、ループが順次処理する（無音破棄しない）。
  「キュー追加+ループ起動判定」と「取り出し+終了判定」を1つの lock でアトミックに保護。
- OPPO のバックグラウンド強制停止対策として処理ループ中は `PARTIAL_WAKE_LOCK` を保持
- **全体停止**は `ACTION_STOP`（通知/本棚バナーの「停止」）→ キュー待ちを破棄し停止フラグ `isStopping` を立てる。
  停止ボタンのキャンセル粒度は現状 **PDF境界**（処理中の1冊は完走し、ループ次周回が空キューを検知して `stopSelf`）。
  ※ 純 Kotlin 化（Phase 3 の NonCancellable 緩和）で `processPages` が本文ページ毎に `ensureActive()` を呼ぶため、
    本文抽出中の割り込み中断**自体は可能**になった（旧 Chaquopy/JNI では原理的に不可能だった）。
    停止ボタンをページ境界の即中断へ再配線するのは別タスク（handover 参照）。

## データベース（Room）

```
AppDatabase（versionはAppDatabase.ktを直接参照）
  ├─ BookDao    → books テーブル
  └─ ProgressDao → progress テーブル（bookId, lastReadFilename）
```

現在のversion・カラム定義・Migrationリストは `AppDatabase.kt` と各 Entity ファイルが正典。
スキーマ変更手順 → `/db-migration` スキルを参照。

DB操作はすべて IO Dispatcher（Coroutines）で実行。

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
