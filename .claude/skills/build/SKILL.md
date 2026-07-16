---
name: build
description: ビルドコマンドと環境セットアップ。Gradle実行・単体テストのコマンドを確認したいときに使う。
when_to_use: 「ビルドしたい」／「gradleを実行したい」／「APKをビルド」／「テストを実行したい」／「環境セットアップ」 などの依頼で使う。
---

# ビルド環境セットアップ

JAVA_HOME は設定済み（Linux/WSL は `~/.bashrc`、Windows は環境変数）のため通常は追加設定不要。
もし `java: command not found` が出た場合は手動で通す（OS で JDK の在処が異なる）：

```bash
# Linux / WSL（このマシンの正本。グローバル ~/.claude/CLAUDE.md と一致）
export JAVA_HOME="$HOME/opt/jdk-17"   # Temurin 17。AGP 8.6.1 に合わせ 17
export PATH="$JAVA_HOME/bin:$PATH"

# Windows（Git Bash 等）
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

# 開発コマンド

> gradlew の実体は `android/` 配下。プロジェクトルートからは `cd android` してから実行する。

## Linux / WSL（このマシンの正本）

`/mnt/c` 上では2つの WSL 固有ワークアラウンドが**必須**:
- `gradlew` は CRLF 改行で `./gradlew` が直接実行できない → ラッパー jar を直接起動する `gw` 関数を使う
  （`~/.bashrc` 定義の薄いラッパー。`gradle-wrapper.jar` を `--no-daemon --console=plain` で起動し、
  ビルド直前に `local.properties` の `sdk.dir` 行を除去して Linux SDK(`ANDROID_HOME`)へ自動フォールバックさせる）。
- `/mnt/c`(drvfs) で AAPT2 が EPERM で落ちる → 成果物を ext4 へ逃がす `--init-script` を**必ず**付ける。

```bash
cd android
gw --init-script /home/qingj/ext-build/novel-reader-init.gradle testDebugUnitTest   # Kotlin単体テスト
gw --init-script /home/qingj/ext-build/novel-reader-init.gradle assembleDebug       # デバッグAPKビルド
gw --init-script /home/qingj/ext-build/novel-reader-init.gradle compileDebugKotlin  # Kotlinコンパイル確認
```

### Claude Code の Bash ツール（非対話シェル）から回すとき

Bash ツールは `~/.bashrc` を読まない（非対話 early-return）＝ **`gw` 関数も JAVA_HOME/ANDROID_HOME も
未定義**で、上のコマンドはそのままでは `command not found` になる。素の起動列を使うこと
（正本: task_diary #32・memory `bash-tool-no-bashrc-gradle-env`）:

```bash
cd android
export JAVA_HOME="$HOME/opt/jdk-17" ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk"
sed -i '/^sdk\.dir/d' local.properties   # Android Studio が書き戻す Windows sdk.dir を除去（gw 内蔵の自己修復と同じ）
"$JAVA_HOME/bin/java" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --no-daemon --console=plain --init-script /home/qingj/ext-build/novel-reader-init.gradle <task>
```

- `sed -i` は `/mnt/c`(drvfs) で permissions 警告を出すが**置換自体は成功する**（無害・`2>/dev/null` で抑制可）。
- `testDebugUnitTest` を `run_in_background` で回すとコミットゲートのセンチネルが生成されない
  （memory `background-gradle-test-skips-sentinel-hook`）→ テストはフォアグラウンドで。

## Windows

```bash
cd android && ./gradlew assembleDebug       # デバッグAPKビルド
cd android && ./gradlew installDebug        # インストール
cd android && ./gradlew compileDebugKotlin  # Kotlinコンパイル確認
```

## PDF 抽出ロジックのテスト

PDF 抽出（縦書き列復元・ルビ紐付け・章分割・HTML 出力）は Kotlin ネイティブ実装
（`java/com/novelreader/pdf/`・PDFBox-Android）で、上記 `testDebugUnitTest` が正本の単体テスト。
**旧 Chaquopy(Python)+pdfminer 経路と `python/test_logic.py` は 2026-07-05 Phase 5 で撤去済み**
（`uv run … unittest test_logic` は現存しない。移植の経緯は `/architecture` スキル・STATUS.md 参照）。
実機での精度回帰は `PdfExtractorDeviceSpikeTest`（`/device-verify` スキル）。

## 注意: プロジェクトパスは ASCII のみ

配置パスに日本語等の非ASCII文字（例: `Desktop\開発\...`）が含まれると、AGP がコンパイル開始前に
`Your project path contains non-ASCII characters` で BUILD FAILED になる（Gradle テストワーカーが
ClassNotFoundException で全滅する症状も出る）。build / test / run は必ず ASCII のみのパス
（例: `Desktop\project\...`）から実行すること。詳細は `task_diary.md` の非ASCII文字の項を参照。
