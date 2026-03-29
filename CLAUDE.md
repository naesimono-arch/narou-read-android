# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Claude への行動規則

### 1. コードのチャット出力禁止（ダンプ禁止）

- **10行を超えるコードブロックをチャットに出力してはならない。**
- ファイル全体の表示も禁止。代わりに `code <ファイルパス>` を実行してエディタで開くこと。
  ```bash
  code android/app/src/main/java/com/novelreader/BookshelfScreen.kt
  ```
- 変更点の説明は「何を・なぜ変えたか」を簡潔に文章で述べるだけでよい。

### 2. Write-then-Open サイクルの強制

- Write ツールまたは Edit ツールでファイルを作成・編集した直後に、必ず該当ファイルを VS Code で開くこと。
  ```bash
  code <編集したファイルパス>
  ```
- 複数ファイルを編集した場合はすべてのファイルを開く。

### 3. 修正後のコミット必須

- ファイルを修正・作成した後は、必ず `git add` → `git commit` を実行すること。
- コミットメッセージは以下の形式に従うこと：
  ```
  fix/feat/refactor: 変更内容の要約（日本語可）

  - 変更点1
  - 変更点2
  ```
- ユーザーに確認を求めず、実装完了後に自動でコミットまで行う。

### 3-1. コミットの粒度とルール

- **「1論理的変更＝1コミット（Atomic Commit）」を厳守すること。**
- 単一の機能追加、単一のバグ修正、または単一のリファクタリング単位でコミットを分ける。
- 複数ファイルにまたがる変更であっても、それが1つの論理的機能を構成する場合は1つのコミットにまとめて良い。
- **禁止事項（Negative Prompting）**:
  - 全く無関係な複数の修正（例：UI修正とバグ修正）を1つのコミットに含めること。
  - 修正内容と無関係な「ついで」のリファクタリングを含めること。
- **Human-in-the-loop**:
  - コミットメッセージの生成までは自律的に行い、ステージング（git add）まで完了させる。
  - 最終的な `git commit` および `git push` の実行前に、必ず変更内容の要約を提示し、人間の最終承認を得ること。

### 4. 実装前の思考プロセス構造化

- コードを書き始める前に、必ず `<thinking>` タグ内で以下を言語化すること：
  1. 要求の分解（何を実現するか）
  2. 採用するアプローチとその理由
  3. 考慮すべきエッジケースや副作用

---

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のインタラクティブなHTML読書体験に変換する **Androidアプリ**。
Jetpack Compose（UI）+ Chaquopy（Python 3.12 統合）で構成。

## 開発コマンド

```bash
# デバッグAPKをビルド
./gradlew assembleDebug

# 接続デバイス／エミュレーターへインストール
./gradlew installDebug

# Kotlinビルドのみ確認
./gradlew compileDebugKotlin
```

ビルド設定: Chaquopy 15.0.1 / Python 3.12 / pdfminer.six / minSdk 26 / targetSdk 34

## アーキテクチャ

### PDF処理パイプライン

```
BookRepository.kt（Kotlin）
  └─ Chaquopy → python/app.py
        Phase 00-02: pdf_extractor.py       → タイトル抽出・本文テキスト抽出（文字座標・フォント情報から）
        Phase 03:    chapter_processor.py   → 【題名】マーカーで章に分割
        Phase 03B:   chapter_processor.py   → 前書き・後書き処理、ルビマーカーをHTMLに変換
        Phase 04:    html_exporter.py        → index.html + chap_N.html を生成
```

Pythonファイルはすべて `android/app/src/main/python/` に配置。

### UI層（Jetpack Compose）

```
MainActivity
  └─ NavHost
       ├─ BookshelfScreen  — 書籍一覧、PDF選択、処理進捗表示
       └─ ReadingScreen    — WebViewでローカルHTML表示、章遷移・進捗保存
BookshelfViewModel
  └─ BookRepository        — データアクセス層（Room + Chaquopy呼び出し）
```

### Service層

```
PdfProcessingService（Foreground Service）
  └─ BookRepository.addBook()
       └─ Chaquopy → python/app.py → HTML生成
```

進捗はコールバック（`fun interface ProgressCallback`）経由でUIに通知。

### データベース（Room）

```
AppDatabase
  ├─ BookDao       → books テーブル（id, title, htmlDirPath）
  └─ ProgressDao   → progress テーブル（bookId, lastReadFilename）
```

DB操作はすべて IO Dispatcher（Coroutines）で実行。

### ファイル保存先

```
context.filesDir/novels/{bookId}/
  ├─ index.html    — 目次
  ├─ chap_1.html
  ├─ chap_2.html
  └─ ...
```

### PDF解析のポイント（pdf_rules.py）

なろう系縦書きPDFに特化した定数群：
- `FONT_SIZE_BODY_TITLE` = 14.0pt（本文・章タイトル）
- `FONT_SIZE_RUBY` = 7.0pt（ルビ文字）
- `RUBY_OFFSET_X` = 14.84（ルビのX軸オフセット）
- `LINE_STEP_X` = 22.68（行間隔）
- Boldフォント → 章タイトルとして認識

ルビのマーカー形式：`|base_char《ruby_text》` → HTML変換後：`<ruby>base<rt>ruby</rt></ruby>`

## 注意事項

- Pythonロジックの唯一の場所は `android/app/src/main/python/`（Web版は削除済み）
- UIコメントはすべて日本語
- index.html（目次ページ）閲覧時は読書進捗を上書きしない制御が入っている
- ForegroundService の多重起動ガードに `AtomicBoolean` を使用
- OPPO/ColorOS 固有の動作については `task.md` を参照
