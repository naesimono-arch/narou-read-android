# CLAUDE.md

## 概要

日本語Web小説（なろう系）のPDFを、ふりがな対応のHTMLに変換する **Androidアプリ**。
Jetpack Compose + Chaquopy (Python 3.12)。
ビルド設定: Chaquopy 15.0.1 / Python 3.12 / pdfminer.six / minSdk 26 / targetSdk 34

## 環境セットアップ

Claude Code のシェルには Java が通っていないため、Gradle 実行前に以下を設定すること：

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

## 開発コマンド

```bash
./gradlew assembleDebug       # デバッグAPKビルド
./gradlew installDebug        # インストール
./gradlew compileDebugKotlin  # Kotlinコンパイル確認

# Python PDFロジックの単体テスト（Android実機不要）
cd android/app/src/main/python && python -m unittest test_logic -v
```

## 開発ルール

- **思考の構造化**: コードを書く前に `<thinking>` タグで「要求の分解」「アプローチと理由」「副作用」を言語化すること。
- **コードダンプ禁止**: チャットへのコード出力は10行以内。全体確認は `code <ファイルパス>` でエディターを開く。
- **Atomic Commit**: 1論理的変更＝1コミット。形式は `fix/feat/refactor: 要約（日本語可）`。`git commit` 前に変更内容を提示して人間の承認を得ること。
- **UIコメントは日本語**
- **自己検証必須**: PDF処理ロジック（`pdf_extractor.py` / `chapter_processor.py` 等）を変更した場合は必ず `python -m unittest test_logic -v` を実行してからコミット計画を提示すること。
- **「なぜ」コメントの義務付け**: 自明でないロジックには必ず「なぜそうしているか」をコメントで残すこと。「何をしているか」（what）はコードを読めば分かる。コメントに書くべきは「なぜそうしなければならないか」（why）。特に以下の場合は必須：
  - バグ修正：なぜそのバグが起きていたか、この修正がなぜ効くか
  - 非直感的な処理：一見おかしく見えるが意図的な実装
  - 根本原因が未特定の防御的コード：「〇〇が原因と推定されるが未確定のため防御的に対処」と明記
  - 将来の自分やAIが「なぜこうなっているか」を誤解して壊しそうな箇所
  - **【絶対禁止】**: `// alpha=0 にする` のような what コメントだけを書いて終わること・why コメントなしにバグ修正・防御的コードを追加すること

## ドメイン知識

- アーキテクチャ全体像 → `/architecture` スキルを参照
- DBスキーマ変更手順 → `/db-migration` スキルを参照
- PDF解析の定数・ルール → `android/app/src/main/python/pdf_rules.py` を直接参照
- OPPO/ColorOS 固有動作 → `task_diary.md` を参照
