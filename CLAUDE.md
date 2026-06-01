# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のHTMLに変換する **Androidアプリ**。
Jetpack Compose + Chaquopy (Python 3.12)。
ビルド設定: Chaquopy 15.0.1 / Python 3.12 / pdfminer.six / minSdk 26 / targetSdk 34

## 開発ルール

- **思考の構造化**: コードを書く前に `<thinking>` タグで「要求の分解」「アプローチと理由」「副作用」を言語化すること。
- **コードダンプ禁止**: チャットへのコード出力は10行以内。全体確認は `code <ファイルパス>` でエディターを開く。
- **Atomic Commit**: 1論理的変更＝1コミット。形式は `fix/feat/refactor: 要約（日本語可）`。`git commit` 前に変更内容を提示して人間の承認を得ること。`Co-Authored-By` トレーラーは付けないこと。
- **UIコメントは日本語**
- **自己検証必須**: PDF処理ロジック（`pdf_extractor.py` / `chapter_processor.py` 等）を変更した場合は必ず `python -m unittest test_logic -v` を実行してからコミット計画を提示すること。Kotlin の `src/main` または `src/test` を変更した場合は必ず `cd android && ./gradlew testDebugUnitTest` を実行してからコミット計画を提示すること（`androidTest` は端末必須のため対象外）。
- **「なぜ」コメントの義務付け**: 自明でないロジック・バグ修正・防御的コードには必ず「なぜそうしているか」をコメントで残すこと。whatはコードを読めば分かる。根本原因が未確定の場合は「〇〇が原因と推定されるが未確定のため防御的に対処」と明記。**【絶対禁止】** what コメントのみ・why なしのバグ修正・防御的コード追加。
- **task_diary自動更新**: `fix:` コミット後は同じターン内で `task_diary.md` への追記が必要か確認すること。コードコメントだけでは伝わらない根本原因・OEM固有動作・将来はまりやすいパターンがある場合のみ追記。既存エントリと重複なら不要。
- **スキル陳腐化チェック**: 構成・描画方式・モジュール間の制御フローを変えるリファクタ（例: WebView→Composeネイティブ移行、多重起動ガード方式の変更）をコミットした後は、同じターン内で `.claude/skills/`（特に `architecture`・`db-migration`）の記述が陳腐化していないか確認すること。スキルは久々に触る際の最初の参照先のため、実態とズレると誤誘導の温床になる。

## ドメイン知識

**【必須】以下の場面では自力調査・コード探索より先に必ず該当スキルを実行すること。スキル実行を省略して試行錯誤することは禁止。**

- **ビルド・環境セットアップ・Gradleに関する作業が発生したら → 必ず `/build` スキルを最初に実行すること**
- **アーキテクチャ・構成・モジュール間の関係を把握する必要があれば → 必ず `/architecture` スキルを最初に実行すること**
- **Room DBのスキーマ・Entityを変更するときは → 必ず `/db-migration` スキルを最初に実行すること**
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
