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

## Python PDFロジックの単体テスト（Android実機不要・素早い検証）

> kotlin ブランチでは PDF ロジックの Kotlin+PDFBox 移植（`java/com/novelreader/pdf/`・依存
> `com.tom-roush:pdfbox-android`）が **Chaquopy と併存中**。Kotlin 側 PDF コードのテストは
> 上記 `testDebugUnitTest` でカバーされる。Python 側を変えたら以下、Kotlin 側を変えたら Gradle、
> 両方変えたら両方を回すこと（二重構造の詳細は `/architecture` スキル）。

依存(pdfminer.six)と Python 3.12 固定が要るため **uv 経由**で実行する（Chaquopy が Python 3.12 前提のため版を合わせる。
hook の `python` シムは 3.14 を指すので、PDF テストはこの uv コマンドで明示的に 3.12 に固定すること）：

```bash
cd android/app/src/main/python
uv run --no-project --python 3.12 --with pdfminer.six python -m unittest test_logic -v
```

## 注意: プロジェクトパスは ASCII のみ

配置パスに日本語等の非ASCII文字（例: `Desktop\開発\...`）が含まれると、AGP がコンパイル開始前に
`Your project path contains non-ASCII characters` で BUILD FAILED になる（Gradle テストワーカーが
ClassNotFoundException で全滅する症状も出る）。build / test / run は必ず ASCII のみのパス
（例: `Desktop\project\...`）から実行すること。詳細は `task_diary.md` の非ASCII文字の項を参照。
