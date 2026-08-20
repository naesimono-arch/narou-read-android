---
name: build
description: ビルド・テストの実行コマンドと環境セットアップ。「ビルドしたい」「gradleを実行したい」「APKをビルド」「テストを実行したい」「環境セットアップ」等の依頼で使う。
---

# ビルド環境セットアップ

**環境そのもの（JDK・SDK の在処・`gw` の中身・`--init-script` の理由）はグローバル
`~/.claude/CLAUDE.md` が正本**（常時ロード＝ここへ複製しない）。以下はコマンド帳と固有の罠のみ。

JAVA_HOME は設定済み（Linux/WSL は `~/.bashrc`、Windows は環境変数）で通常は追加設定不要。
`java: command not found` のときだけ手動で通す（OS で JDK の在処が異なる）:

```bash
export JAVA_HOME="$HOME/opt/jdk-17"                             # Linux/WSL（Temurin 17＝AGP 8.6.1 に合わせる）
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"  # Windows（Git Bash 等）
export PATH="$JAVA_HOME/bin:$PATH"
```

# 開発コマンド

> gradlew の実体は `android/` 配下。プロジェクトルートからは `cd android` してから実行する。

## Linux / WSL（このマシンの正本）

`/mnt/c`(canonical) では `gw` と `--init-script` の2点が**必須**（`--init-script` の理由＝AAPT2 の EPERM。
機序はグローバル CLAUDE.md）。**ext4 の worktree では `--init-script` は不要**＝素の `gw <task>` でよい。
`gw` の実益＝wrapper jar 直起動＋`local.properties` の sdk.dir 自己修復。**CRLF は理由ではない**
（gradlew は LF＝`ci.yml` の chmod ステップが file コマンド確認済みと明記。`./gradlew` 直接実行を阻む実態は
git index 100644 の実行ビット欠落＝ext4 worktree では Permission denied。drvfs の canonical は 777 扱いで実行自体は可）。

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
- 旧記述「`run_in_background` だとコミットゲートのセンチネルが生成されない→前景で」は**失効**
  （センチネル生成側は 2026-07-12 撤去＝ADR 0017・消費側判定も 2026-07-25 退役。根拠にしていた
  memory `background-gradle-test-skips-sentinel-hook` も現存しない）。前景で回す義務は無い。

## Windows

※このマシンでは Linux/WSL が正本。グローバル CLAUDE.md も Linux 版のため、**Windows 手順の記録はここだけ**。

```bash
cd android && ./gradlew assembleDebug       # デバッグAPKビルド
cd android && ./gradlew installDebug        # インストール
cd android && ./gradlew compileDebugKotlin  # Kotlinコンパイル確認
cd android && ./gradlew :app:lintDebug      # lint は Windows でも通る（基準 0 errors）
```

⚠️ **`testDebugUnitTest` をここに書いていないのは書き漏らしではない**——Windows の JVM では
描画を伴うテストが `UnsatisfiedLinkError`（`RenderNodeNatives`）で一斉に落ち、**golden の
verify も record もできない**（2026-08-17 実測＝1436 中 210 失敗・全て同一原因・ロジック系の赤は0）。
**見た目を変えた変更を Windows セッションだけで完結させられない**＝ゲートは WSL 側の worktree で回すこと。
機序・実測・併発する「`| tail` が exit code を握り潰す」罠＝
**`docs/knowledge/windows-jvm-cannot-run-robolectric-native-graphics.md` が正本**。

## CI と同じゲートをローカルで回す

CI（`.github/workflows/ci.yml`）が毎 push で回すのは次の6つ。**日常の自己検証は
`testDebugUnitTest` だけでよく**（CLAUDE.md「自己検証必須」）、以下は push 前に赤を前倒しで拾いたいときや、
該当領域を触ったときに個別で回す。ext4 worktree なら `gw <task>` がそのまま通る（`--init-script` 不要）。

```bash
cd android
gw --continue :app:ktlintCheck    # 未使用 import 検知（2026-08-05 ブロッキング復帰）。基準 0 件
gw :app:verifyRoborazziDebug      # 単体テスト全件＋golden の画像比較（testDebugUnitTest を内包する1パス）
                                  # 枚数は増えるので書かない＝実数は `ls android/app/src/test/screenshots/*.png | wc -l`
gw :app:assembleDebugAndroidTest  # androidTest の「ビルド」だけ（実行は端末必須＝/device-verify）
gw :app:lintDebug                 # 基準 0 errors
gw :app:assembleRelease           # release の R8 収縮が通るか（鍵不在でも未署名で通る）
python3 tools/check_design_tokens.py
```

- **worktree 作業は冒頭で `gw :app:lintDebug` を回す**（基準＝**0 errors**・warnings は非ブロックの参考値）。
  ローカルの自動コミットゲートは現存せず CI が毎 push で担保するので、役目は**push 前に赤を見つける**前倒し検知。

- **ktlint の残数を数えるときは必ず `--continue` を付ける**——無しだと最初に落ちたソースセットで止まり、
  他ソースセットの report が生成されないまま「あと N 件」と読み違える（`ci.yml` のコメントが一次情報＝実際に踏んだ）。

- **`verifyRoborazziDebug` は `testDebugUnitTest` を内包する**（同じ test タスクを `roborazzi.test.verify=true`
  付きで実行する形）。両方を別々に回すとテストが丸ごと2回走るだけなので、golden も見たいときは verify 側だけでよい。
- **見た目を変えたときは従来どおり `recordRoborazziDebug` で再記録**してから verify（`/visual-language`）。
- **落とし穴**: golden PNG は test タスクの宣言済み入力ではないため、**golden だけ**を差し替えた直後の
  `verifyRoborazziDebug` は UP-TO-DATE で素通りする（実測）。golden を手で差し替えて照合し直したいときは
  `gw :app:verifyRoborazziDebug :app:testDebugUnitTest --tests "*XxxScreenshotTest*"` のようにテストフィルタを
  付けて強制再実行する（フィルタ自体が入力差分になる）。実装を変えた場合は入力が変わるので普通に再実行される。
- `assembleDebugAndroidTest` は**端末不要**。androidTest は既定ゲートでコンパイルされず、本番のシグネチャ変更に
  追従しないまま壊れて潜伏した実績が2回あるためゲート化してある（`docs/known-bugs-registry.md`）。

- ⚠️ **寸法アサートを持つ Robolectric テストは `@GraphicsMode(GraphicsMode.Mode.NATIVE)` を明示する**
  （幅・高さ・位置・折り返し／溢れの有無＝いずれも文字送りから決まる値すべて）。既定の LEGACY では
  **文字幅が「文字数」の定数**になり、fontScale を上げても幅が1px も動かない＝そのテストは
  **検出力ゼロのまま緑で通り続ける**（アサート自体は自明に真になるので、赤くならず気づけない）。
  メソッド単位で付けられるので、遅くなるのを嫌ってクラス全体を LEGACY のまま置かないこと。
  機序・NATIVE 側の裏付け・現況の点検コマンド＝
  `docs/knowledge/robolectric-legacy-graphicsmode-text-width-is-char-count.md`。
  **この規約を knowledge 側にだけ書いていた間は守られず、違反が放置されていた**（2026-08-21 判明）
  ＝テストを回すこの導線に置いてある理由。

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
