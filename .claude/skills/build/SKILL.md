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

> gradlew の実体は `android/` 配下。プロジェクトルートからは `cd android` してから実行する。

```bash
cd android && ./gradlew assembleDebug       # デバッグAPKビルド
cd android && ./gradlew installDebug        # インストール
cd android && ./gradlew compileDebugKotlin  # Kotlinコンパイル確認

# Python PDFロジックの単体テスト（Android実機不要）
cd android/app/src/main/python && python -m unittest test_logic -v
```

## 注意: プロジェクトパスは ASCII のみ

配置パスに日本語等の非ASCII文字（例: `Desktop\開発\...`）が含まれると、AGP がコンパイル開始前に
`Your project path contains non-ASCII characters` で BUILD FAILED になる（Gradle テストワーカーが
ClassNotFoundException で全滅する症状も出る）。build / test / run は必ず ASCII のみのパス
（例: `Desktop\project\...`）から実行すること。詳細は `task_diary.md` の非ASCII文字の項を参照。
