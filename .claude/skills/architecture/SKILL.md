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

```
BookRepository.kt（Kotlin）
  └─ Chaquopy → python/app.py: process_pdf(pdf_path, book_id, output_dir, progress_callback)
        step0: pdf_extractor.extract_book_title() + extract_book_author()
                 タイトル・著者を抽出
        step1: pdf_extractor.run_final_engine()
                 文字座標・フォント情報から縦書きPDFを解析（本文抽出）
        step2: chapter_processor.split_into_chapters()       【題名】マーカーで章分割
               chapter_processor.process_foreword_afterword() 前書き・後書き処理、
                                                              |base《ruby》 → <ruby> HTML変換
        step3: html_exporter.export_to_pwa()  index.html + chap_N.html を生成
        return [real_title, real_author]      ← タイトルと著者を返す
```

Pythonファイルはすべて `android/app/src/main/python/` に配置。
進捗は4ステップ（step 0〜3）で通知される。

## Kotlin+PDFBox 移植パイプライン（進行中・Chaquopy と併存）

上記 Python パイプラインの **Kotlin への忠実移植**が `java/com/novelreader/pdf/` に併存する
（依存: PDFBox-Android `com.tom-roush:pdfbox-android`。何がどこまで完了したかは **STATUS.md が正本**＝
このスキルには進捗状態を書かない）。

```
pdf/PdfBookExtractor.kt    — facade。app.py: process_pdf と同形の4ステップ進捗（typealias PdfProgress）
  ├─ PdfExtractor.kt       — PDFBoxで文字座標抽出（pdf_extractor.py 相当）。
  │     PDDocument.load 前に PDFBoxResourceLoader.init(context) 必須（task_diary #31）。
  │     波ダッシュは pdfminer に揃えて正規化 U+FF5E→U+301C（#35）
  ├─ TextProcessor.kt      — 本文抽出コア（run_final_engine 相当）
  ├─ ChapterProcessor.kt   — 章分割・前後書き処理（chapter_processor.py 相当）
  ├─ HtmlExporter.kt       — HTML出力（html_exporter.py 相当・Python出力とバイト等価ゴールデン）
  └─ CharBox / ParserRules / HtmlEscape / PdfExtractionException（sealed 3型＋classifyPdfError）
```

- **ランタイムで実際に動くのは現状 Chaquopy（Python）側**。Phase 3 で `BookRepository` を
  `PdfBookExtractor.process` 直呼へ切替予定（切替済みかは STATUS.md で確認すること）。
- 実機テスト harness は `androidTest/…/pdf/PdfExtractorDeviceSpikeTest.kt`・`PdfPipelineDeviceTest.kt`。
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
  └─ repository/BookRepository   — データアクセス層（Room + Chaquopy呼び出し）
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
       └─ Chaquopy → python/app.py → HTML生成
```

- 進捗は `BookRepository.ProgressCallback`（`fun interface`）経由でUIに通知
- **多重起動制御は `ReentrantLock` + `ArrayDeque<Uri>` のキュー方式**（`65abfe4` で導入）。
  処理中に別PDFが追加されてもキューに積まれ、ループが順次処理する（無音破棄しない）。
  「キュー追加+ループ起動判定」と「取り出し+終了判定」を1つの lock でアトミックに保護。
- OPPO のバックグラウンド強制停止対策として処理ループ中は `PARTIAL_WAKE_LOCK` を保持
- **全体停止**は `ACTION_STOP`（通知/本棚バナーの「停止」）→ キュー待ちを破棄し停止フラグ `isStopping` を立てる。
  処理中の1冊は Python(JNI)が中断不能のため完走し、ループ次周回が空キューを検知して `stopSelf`。
  ＝ キャンセル粒度は **PDF境界のみ**（割り込み停止は不可）。

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
- Python ロジックの置き場は `android/app/src/main/python/`（Web版は削除済み）。ただし抽出/章分割/HTML出力の
  ロジックは `java/com/novelreader/pdf/` にも忠実移植済み（上記「Kotlin+PDFBox 移植」参照）＝現在は二重構造。
  **Python 側ロジックを直すときは Kotlin 側への反映要否も必ず確認**（逆も同様）
