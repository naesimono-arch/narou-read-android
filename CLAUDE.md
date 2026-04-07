# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のHTMLに変換する **Androidアプリ**。
Jetpack Compose + Chaquopy (Python 3.12)。
ビルド設定: Chaquopy 15.0.1 / Python 3.12 / pdfminer.six / minSdk 26 / targetSdk 34

## 開発ルール

- **思考の構造化**: コードを書く前に `<thinking>` タグで「要求の分解」「アプローチと理由」「副作用」を言語化すること。
- **コードダンプ禁止**: チャットへのコード出力は10行以内。全体確認は `code <ファイルパス>` でエディターを開く。
- **Atomic Commit**: 1論理的変更＝1コミット。形式は `fix/feat/refactor: 要約（日本語可）`。`git commit` 前に変更内容を提示して人間の承認を得ること。
- **UIコメントは日本語**
- **自己検証必須**: PDF処理ロジック（`pdf_extractor.py` / `chapter_processor.py` 等）を変更した場合は必ず `python -m unittest test_logic -v` を実行してからコミット計画を提示すること。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには必ず「なぜそうしているか」をコメントで残すこと。whatはコードを読めば分かる。根本原因が未確定の場合は「〇〇が原因と推定されるが未確定のため防御的に対処」と明記。**【絶対禁止】** what コメントのみ・why なしのバグ修正・防御的コード追加。

## ドメイン知識

- ビルドコマンド・環境セットアップ → `/build` スキルを参照
- アーキテクチャ全体像 → `/architecture` スキルを参照
- DBスキーマ変更手順 → `/db-migration` スキルを参照
- PDF解析の定数・ルール → `android/app/src/main/python/pdf_rules.py` を直接参照
- OPPO/ColorOS 固有動作 → `task_diary.md` を参照
- **ホットスポット分析**（頻繁変更ファイルの特定）:
  ```bash
  # ファイル別変更回数ランキング（上位20件）
  git log --name-only --format="" | sort | uniq -c | sort -rn | head -20
  # 特定ファイルの変更回数
  git log --oneline -- <file_path> | wc -l
  ```
  AIへの提示例: 「上記コマンドの結果を渡して、なぜ頻繁に変更されるのか・設計上の問題がないかを分析させる」
