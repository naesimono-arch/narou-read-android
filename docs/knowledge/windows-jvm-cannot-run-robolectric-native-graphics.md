# Windows セッションからは Robolectric/Roborazzi 系テストが全滅する＝ゲートは WSL で回すしかない

**重要度 ★★★／2026-08-17 実測（sweep/handover-sweep-2026-08-17）／関連＝`/build` skill「Windows」節**

1行要約: Windows の JVM で `testDebugUnitTest` を回すと、描画を伴うテストが
**`java.lang.UnsatisfiedLinkError at RenderNodeNatives.java:-2`** で一斉に落ちる。
`/build` skill の Windows 節が `assembleDebug`/`installDebug`/`compileDebugKotlin` しか
挙げていないのは**書き漏らしではなく、単体テストがそもそも通らないから**だった。

## 実測（同一 worktree・同一コミットで比較）

| 実行環境 | `:app:lintDebug` | `testDebugUnitTest` |
|---|---|---|
| Windows（`android\gradlew`） | **0 errors**（通る） | **1436 中 210 失敗**・全て `UnsatisfiedLinkError` |
| WSL（ext4 worktree・素の wrapper 起動） | 未計測 | **BUILD SUCCESSFUL** |

失敗した 210 件の内訳は**全てスクリーンショット系と版面計測系**——`*ScreenshotTest`
に加えて `SettingsRowWidthLayoutTest`・`TocResumeAffordanceLayoutTest`・
`VertGlyphRendererTest`・`PaintFontMetricsTest`・`BookshelfKFabTest` のように
`Paint`/`RenderNode` を踏む計測テストも巻き込まれる。**ロジック系の赤は0件**
＝赤の数を見て「実装が壊れた」と読み違えないこと。

## 何ができなくなるか

- `verifyRoborazziDebug`（golden 画像照合）も `recordRoborazziDebug`（golden 再記録）も**不可**。
  ⇒ **見た目を変えた変更を Windows セッションだけで完結させられない**。
- CLAUDE.md「自己検証必須」の `testDebugUnitTest` を Windows で満たしたことにはならない。

## 対処

WSL 側の登録済み worktree で回す。`gw` 関数は非対話シェルでは未定義なので素の起動列を使う
（`/build` skill「Bash ツールから回すとき」と同じ形。**ext4 上の worktree なので
`--init-script` は不要**＝AAPT2 の EPERM は `/mnt/c` 固有）。

```bash
cd <ext4 worktree>/android
export JAVA_HOME="$HOME/opt/jdk-17" ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk"
"$JAVA_HOME/bin/java" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --no-daemon --console=plain <task>
```

## ⚠️ 併せて踏んだ罠: `| tail` が exit code を握り潰す

`./gradlew … 2>&1 | tail -40` で回すと、**パイプの終了コードは `tail` のもの**になるため
`BUILD FAILED` でも**シェルは exit 0 を返し、ハーネスの完了通知は「成功」と表示する**。
実際にこれで一度「ゲート GREEN」と読み違えかけた。**出力を絞るなら `PIPESTATUS` を見るか、
tail を挟まず末尾だけ後から読む**こと。ログ量を減らす目的なら `--console=plain` で足りる。
