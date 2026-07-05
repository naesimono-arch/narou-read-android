# AGENTS.md — Antigravity (agy) 実行者向けブリーフィング

Claude Code（監督）から委譲されたサブタスクをこのリポジトリで実行するときの規約。
普遍規約（git 操作禁止・検証偽装禁止・報告様式・WSL 環境）はユーザーグローバルの
`~/.gemini/AGENTS.md` にある。ここには本リポジトリ固有のことだけを書く。

## このリポジトリ

日本語 Web 小説（なろう系）の PDF を、ふりがな対応 HTML に変換する **Android アプリ**。
Jetpack Compose + Kotlin。**PDF 抽出パイプラインの実装はブランチで異なる**
（main = Chaquopy/Python + pdfminer ／ kotlin = PDFBox-Android ネイティブ）。
今いるブランチの実態は `STATUS.md`（現況台帳）と `android/app/build.gradle` で確認すること。

## リポジトリ地図

| パス | 中身 |
|---|---|
| `android/` | Android プロジェクト本体（Gradle） |
| `android/app/src/main/java/com/novelreader/` | Kotlin ソース（ui=Compose / pdf=抽出パイプライン / data=Room） |
| `STATUS.md` | 現況台帳（今どうなっているか）＝正本 |
| `handover.md` | やること台帳（backlog） |
| `task_diary.md` | 外部プラットフォーム知見（OPPO/ColorOS の罠など） |
| `docs/decisions/` | 設計判断 ADR ／ `docs/patterns/` 実装パターン |
| `sample_pdfs/` | テスト用 PDF 実体 |

## コーディング規約（must）

- UI コメントは日本語。自明でないロジック・バグ修正・防御的コードには「なぜ」コメント必須（what だけのコメントは禁止）。
- UI の色・タイポは `theme/Color.kt` / `theme/Typography.kt` のトークン経由（直書き禁止）。見た目の意匠を自己判断で変えない（HTML モックが正本という体制のため）。
- Room DB の Entity/スキーマは勝手に変えない（Migration 手順が別管理のため、指示された場合のみ指示の範囲で）。
- 管理ドキュメント（`STATUS.md` / `handover.md` / `task_diary.md` / `docs/`）へは指示がない限り書き込まない。
- 一時ファイルを作ったら作業終了時に削除する。

## ビルド・テスト（WSL 固有の罠あり — この手順以外で Gradle を起動しない）

```bash
export JAVA_HOME=/home/qingj/opt/jdk-17 ANDROID_HOME=/home/qingj/Android/Sdk
cd android
# local.properties に Windows 側の sdk.dir が残っていると WSL で壊れるため除去（gitignore 済みで安全）
sed -i '/^sdk\.dir=/d' local.properties
# gradlew は CRLF 改行のため直接実行不可 → ラッパー jar を直接起動する。
# --init-script は必須（/mnt/c 上では AAPT2 が EPERM で死ぬため build/ を ext4 へ退避している）
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --no-daemon --console=plain \
  --init-script /home/qingj/ext-build/novel-reader-init.gradle \
  testDebugUnitTest
```

- 単体テストは `testDebugUnitTest` のみ実行してよい。**`connectedAndroidTest` と実機 adb 操作は絶対にしない**（実機の蔵書 DB を消す禁忌があるため監督が行う）。
- このリポジトリは `/mnt/c`（Windows マウント）上にあり I/O が遅い。広い走査は対象を絞ってから。
