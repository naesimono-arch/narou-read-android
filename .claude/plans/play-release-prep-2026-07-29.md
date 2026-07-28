# 公開準備 — SDK 36 移行 → リリース署名・AAB（2026-07-29 起票）

**対象ブランチ**: `release/play-prep`（worktree: `~/wt/release-play-prep`・base=main `b635b08`）
**このファイルの役割**: canonical セッションで済ませた下ごしらえと実測の引き渡し。実作業はこの worktree 内のセッションで行う。

## スコープ（2026-07-29 ユーザー裁定）

- **やる**: ①targetSdk/compileSdk 36 移行 ②リリース署名の整備と AAB 検証 — 順に同じブランチで通す。
- **やらない**: applicationId 変更・ストア素材・タイトル・商標チェック — **ブランド名が未定**のため一式を別便へ。
  初回アップロード前でありさえすれば間に合うので急がない（公開後は applicationId が永久不変）。
- プライバシーポリシー／Data safety／In-App Review は本便の外（コードに触らない準備として別途）。

## 現状の実測（2026-07-29・`handover.md` の 07-19 記述からの差分に注意）

handover の「AGP 8.6.1 / compileSdk 34」前提は**部分的に古い**。実測は以下。

| 項目 | 現在値 | 出典 |
|---|---|---|
| compileSdk | **35**（2026-07-27 裁定で移行済み） | `android/app/build.gradle:38` |
| targetSdk | **34** | 同 `:43` |
| minSdk | 26 | 同 `:42` |
| AGP | **8.6.1**（→ compileSdk 36 には 8.9.1+ が要る） | `android/settings.gradle:8` |
| Gradle | **8.9**（→ AGP 8.9.1 には 8.11.1+ が要る） | `gradle/wrapper/gradle-wrapper.properties:3` |
| Kotlin | 1.9.22（据え置き可＝compileSdk 非依存） | `settings.gradle:10` |
| release 署名 | **なし**（`signingConfig` 未設定。benchmark だけが debug 署名を借りている） | `app/build.gradle` buildTypes |
| versionCode/Name | 1 / "1.0" | 同 `:44-45` |

**移行後の検証必須3点のうち、2点はコード上すでにクリア**（実機掃引が要るのは①だけ）:

- ②predictive back（targetSdk 36 で既定 ON）→ `src/main` に **`onBackPressed` の残存ゼロ**。既に `OnBackPressedDispatcher` 系。
- ③大画面での向き固定・リサイズ不可の無効化 → Manifest に **`screenOrientation`・`resizeableActivity` の指定なし**＝影響を受ける宣言が存在しない。
- ①**edge-to-edge 強制だけが残る**。targetSdk 34→36 は 35 の変更も一度に受けるため、全画面＋没入モード＋IME の insets を実機で掃引する必要がある。

**16KB ページ要件は「対象だが充足済み」**（2026-07-29 の実測でこの起票時記述を訂正）: 自前の `jniLibs` は無いが、
release APK には**推移依存由来の `.so` が 4ABI × 2 本**入る（`libandroidx.graphics.path.so`＝Compose 由来・
`libdatastore_shared_counter.so`＝datastore-preferences 1.1.1 由来）。「PDFBox-Android は純 Java」は事実だが、
**他の依存が持ち込む分を見落としていた**のが起票時の誤り。実測結果は ELF の PT_LOAD が全て `p_align=0x4000`(16KB)、
ZIP 側も `zipalign -c -v -P 16 4` が Verification successful ＝**要件は既に満たしている**（対処不要）。
検査手順: `assembleRelease` → APK 内 `lib/**/*.so` の PT_LOAD `p_align` と zipalign の2点。依存バンプ時は再確認が要る。

**SDK は導入済み**（2026-07-29 canonical で実施・`~/Android/Sdk`）: `platforms;android-36`・`build-tools;36.0.0`。
JDK は Temurin 17.0.19 据え置き（AGP 8.9 の要件を満たす）。

## 手順 A — SDK 36 移行

1. Gradle wrapper を 8.11.1+ へ（`gradle-wrapper.properties` の `distributionUrl`）。
2. AGP を 8.9.1 へ（`settings.gradle` の `com.android.application` version）。**先に Gradle を上げること**（逆順だと AGP が要件エラーで止まる）。
3. `compileSdk 36` / `targetSdk 36`（`app/build.gradle`）。コメントに残っている「旧 compileSdk 34/35 天井」由来の依存固定（WorkManager 2.9.1・lifecycle 2.6.2・Compose BOM・profileinstaller 1.2.0）は**この便では動かさない**——バンプは動作検証と別便、が 07-27 の裁定。
4. `gw testDebugUnitTest` → `gw :app:lintDebug` → `python3 tools/check_design_tokens.py`。
5. 実機で edge-to-edge 掃引（①）。**adb を触る前に一度ユーザーへ確認**（memory `feedback-ask-before-device-testing`）。

### 手順 A の実施結果（2026-07-29）

ビルド構成の移行は完了し JVM ゲートは全緑。**実機掃引①だけが残る**。以下は実測で分かったことのみ（完了の記録自体はコミットが正本）。

- 移行値: Gradle 8.9→**8.11.1**・AGP 8.6.1→**8.9.1**・compileSdk 35→**36**・targetSdk 34→**36**。
  `:macrobenchmark` も 36 へ追従（テストAPK と計測対象アプリで実行時の前提がズレるのを避けるため）。
- 依存は**全て据え置きのまま通った**（Kotlin 1.9.22・Compose Compiler 1.5.10・BOM 2025.02.00・lifecycle 2.6.2・
  WorkManager 2.9.1・Roborazzi 1.30.1・Robolectric 4.11.1・JDK 17）。Roborazzi 1.30.1 は AGP 8.6.1 でビルドされた版だが、
  AGP 8.9.1 でも全スクリーンショットテストが通ることを実測。
- ゲート結果: `testDebugUnitTest` **943件 skipped=0 failed=0** ／ `:app:lintDebug` **エラー0**（警告75・SDK 36 由来の新規エラーなし）
  ／ `check_design_tokens.py` **OK=192 NG=0** ／ `:macrobenchmark:assembleBenchmark` 成功
  ／ `:app:assembleRelease`（R8 収縮＋難読化＋リソース削減）成功＝**SDK 36 化で R8 構成は無傷**（unsigned APK 8.5MB）。
- **Robolectric 4.11.1（サポート上限 SDK 34）が targetSdk 36 で落ちなかった理由**——偽 GREEN ではない:
  Robolectric テスト45ファイルの**全て**が `@Config(sdk = [34])` を明示しており、既定の「merged manifest の targetSdk を
  実行時 SDK にする」経路に乗らない。**裏返せば JVM テストは SDK 34 の挙動しか見ておらず、targetSdk 35/36 固有の変化は
  原理的に捕まらない**＝下記①の実機掃引が唯一の検証手段（テストが緑でも移行の安全性は何も担保していない）。

## 手順 B — リリース署名・AAB

1. upload keystore を生成（有効期限 25年+）。**鍵とパスワードの保管先を先に決める**——紛失時は Play App Signing でリセット可能だが、保管設計なしに生成だけ先行すると事故る。
2. `signingConfigs.release` を配線。認証情報は `local.properties` or 環境変数から読み、**リポジトリに入れない**。
3. `bundleRelease` で AAB を生成し、`bundletool` で実機インストール検証（R8 収縮済み構成での欠落確認を兼ねる）。
4. 実機は debug 署名の APK と別署名＝**既存の蔵書データは引き継がれない**。検証は捨て本で（memory `device-verify-delegation-no-destructive-on-real-library`）。

## ゲート・作法

- ここは **ext4 worktree** ＝ `--init-script` 不要。`cd android && gw testDebugUnitTest` が in-tree・ネイティブ速度で通る。
- コミットはこの worktree 内で起動したセッションから（ブランチガードはセッション cwd で判定）。
- 台帳（`handover.md` の「Google Play 公開準備」節）の更新は、原因となった論理変更と同じコミットに同梱する。
  上表の「実測との差分」は本ファイルが一次情報なので、handover 側へ再掲しない。
