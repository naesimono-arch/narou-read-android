---
name: build
description: ビルドコマンドと環境セットアップ。Gradle実行・Python単体テストのコマンドを確認したいときに使う。
triggers:
  - "ビルドしたい"
  - "gradleを実行したい"
  - "APKをビルド"
  - "テストを実行したい"
  - "環境セットアップ"
---

# ビルド環境セットアップ

JAVA_HOME は `~/.bashrc` に設定済みのため、通常は追加設定不要。
もし `java: command not found` が出た場合は手動で通す：

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

# 開発コマンド

```bash
./gradlew assembleDebug       # デバッグAPKビルド
./gradlew installDebug        # インストール
./gradlew compileDebugKotlin  # Kotlinコンパイル確認

# Python PDFロジックの単体テスト（Android実機不要）
cd android/app/src/main/python && python -m unittest test_logic -v
```
