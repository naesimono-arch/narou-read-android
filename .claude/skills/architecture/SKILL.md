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
  ├─ processingState: MutableStateFlow<ProcessingState?>
  └─ errorState:      MutableStateFlow<String?>
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
  （`chap_` で始まり `.html` で終わるファイルのみ進捗保存）
- OPPO/ColorOS 固有の動作については `task_diary.md` を参照
- Python ロジックの唯一の場所は `android/app/src/main/python/`（Web版は削除済み）
