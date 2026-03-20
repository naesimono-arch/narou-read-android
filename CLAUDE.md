# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Claude への行動規則

### 1. コードのチャット出力禁止（ダンプ禁止）

- **10行を超えるコードブロックをチャットに出力してはならない。**
- ファイル全体の表示も禁止。代わりに `code <ファイルパス>` を実行してエディタで開くこと。
  ```bash
  code src/bookshelf.py
  ```
- 変更点の説明は「何を・なぜ変えたか」を簡潔に文章で述べるだけでよい。

### 2. Write-then-Open サイクルの強制

- Write ツールまたは Edit ツールでファイルを作成・編集した直後に、必ず該当ファイルを VS Code で開くこと。
  ```bash
  code <編集したファイルパス>
  ```
- 複数ファイルを編集した場合はすべてのファイルを開く。

### 3. 実装前の思考プロセス構造化

- コードを書き始める前に、必ず `<thinking>` タグ内で以下を言語化すること：
  1. 要求の分解（何を実現するか）
  2. 採用するアプローチとその理由
  3. 考慮すべきエッジケースや副作用

---

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のインタラクティブなHTML読書体験に変換するWebアプリ。FlaskバックエンドとバニラJavaScriptフロントエンドで構成。

## 起動・開発コマンド

```bash
# 依存関係のインストール
pip install -r requirements.txt

# サーバー起動（デフォルト: http://0.0.0.0:5000）
python bookshelf.py

# ポート変更
PORT=8000 python bookshelf.py

# デバッグ・テスト用
python tools/main.py
python tools/check_font.py     # フォントデバッグ
python tools/pdf_conversion.py # PDF解析
```

## アーキテクチャ

### PDF処理パイプライン（app.py が統括）

```
app.process_pdf(pdf_path, book_id)
  Phase 00-02: pdf_extractor.py  → タイトル抽出・本文テキスト抽出（文字座標・フォント情報から）
  Phase 03:    chapter_processor.py → 【題名】マーカーで章に分割
  Phase 03B:   chapter_processor.py → 前書き・後書き処理、ルビマーカーをHTMLに変換
  Phase 04:    html_exporter.py   → index.html + chap_N.html を生成
```

### Flaskサーバー（bookshelf.py）

主要APIエンドポイント：
- `POST /api/add` — PDF受け取り → app.py で処理 → books.json に登録
- `GET /api/books` — 書籍一覧＋読書進捗を返す
- `POST /api/progress` — 読書進捗（どの章まで読んだか）を保存
- `POST /api/rename` / `POST /api/delete` — 書籍管理
- `GET /book/<book_id>/...` — 生成済みHTMLを配信

### データ構造

- `library/books.json` — 書籍メタデータ（id, title, path）
- `library/progress.json` — 読書進捗（book_id → chap_N.html）
- `novel_app/<book_id>/` — 生成されたHTML（index.html + chap_N.html）

### PDF解析のポイント（pdf_rules.py）

なろう系縦書きPDFに特化した定数群：
- `FONT_SIZE_BODY_TITLE` = 14.0pt（本文・章タイトル）
- `FONT_SIZE_RUBY` = 7.0pt（ルビ文字）
- `RUBY_OFFSET_X` = 14.84（ルビのX軸オフセット）
- `LINE_STEP_X` = 22.68（行間隔）
- Boldフォント → 章タイトルとして認識

ルビのマーカー形式：`|base_char《ruby_text》` → HTML変換後：`<ruby>base<rt>ruby</rt></ruby>`

### 並行アクセス対策

books.json と progress.json への書き込みはスレッドロック（`threading.Lock`）で保護。

## 注意事項

- `novel_app/` と `library/` は .gitignore 対象（生成物・ユーザーデータ）
- フロントエンドはフレームワーク不使用（バニラJS）
- UIコメントはすべて日本語
- ブラウザバック（bfcache）からの復帰時は `pageshow` イベントで書籍一覧を再取得
- index.html（目次ページ）閲覧時は読書進捗を上書きしない制御が入っている
