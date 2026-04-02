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
  └─ Chaquopy → android/app/src/main/python/app.py
        Phase 00-02: pdf_extractor.py   → extract_book_title() + run_final_engine()
                                           文字座標・フォント情報から縦書きPDFを解析
        Phase 03:    chapter_processor.py → split_into_chapters()
                                           【題名】マーカーで章に分割
        Phase 03B:   chapter_processor.py → process_foreword_afterword()
                                           前書き・後書き処理、|base《ruby》 → <ruby> HTML変換
        Phase 04:    html_exporter.py    → export_to_pwa() → export_to_mobile_html()
                                           index.html + chap_N.html を生成
```

Pythonファイルはすべて `android/app/src/main/python/` に配置。

## UI層（Jetpack Compose）

```
MainActivity
  └─ NavHost
       ├─ ui/BookshelfScreen     — 書籍一覧、PDF選択、処理進捗表示
       └─ ui/ReadingScreen       — WebViewでローカルHTML表示、章遷移・進捗保存
viewmodel/BookshelfViewModel
  └─ repository/BookRepository   — データアクセス層（Room + Chaquopy呼び出し）
NovelReaderApplication
  ├─ repository（シングルトン）   — Service/ViewModel 共用
  ├─ processingState: MutableStateFlow<ProcessingState?>
  └─ errorState:      MutableStateFlow<String?>
```

## Service層

```
PdfProcessingService（Foreground Service）
  └─ BookRepository.addBook()
       └─ Chaquopy → python/app.py → HTML生成
```

- 進捗はコールバック（`fun interface ProgressCallback`）経由でUIに通知
- 多重起動ガードに `AtomicBoolean` を使用

## データベース（Room）

```
AppDatabase（現在 version = 4）
  ├─ BookDao    → books テーブル（id, title, htmlDirPath）
  └─ ProgressDao → progress テーブル（bookId, lastReadFilename）
```

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

- `index.html`（目次ページ）閲覧時は読書進捗を上書きしない制御が ReadingScreen.kt に入っている
  （`chap_` で始まり `.html` で終わるファイルのみ進捗保存）
- OPPO/ColorOS 固有の動作については `task_diary.md` を参照
- Python ロジックの唯一の場所は `android/app/src/main/python/`（Web版は削除済み）
