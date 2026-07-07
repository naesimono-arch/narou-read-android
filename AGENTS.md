# AGENTS.md — Antigravity (agy) 実行者向けブリーフィング

Claude Code（監督）から委譲されたサブタスクをこのリポジトリで実行するときの規約。
普遍規約（git 操作禁止・検証偽装禁止・報告様式・WSL 環境）はユーザーグローバルの
`~/.gemini/AGENTS.md` にある。ここには本リポジトリ固有のことだけを書く。

## このリポジトリ

日本語 Web 小説（なろう系）の PDF を、ふりがな対応 HTML に変換する **Android アプリ**。
Jetpack Compose + Kotlin。PDF 抽出パイプラインは **純 Kotlin（PDFBox-Android）ネイティブ実装**
（旧 Chaquopy/Python + pdfminer は 2026-07-05 に完全撤去。旧 `kotlin` ブランチは 2026-07-06 に main へ統合済み）。
あわせて **なろう公式APIによる作品の発見・検索** を第2の柱として持つ（`narou/`・`ui/discovery/`）。
今いるブランチの実態は `STATUS.md`（現況台帳）と `android/app/build.gradle` で確認すること。

## リポジトリ地図

| パス | 中身 |
|---|---|
| `android/` | Android プロジェクト本体（Gradle） |
| `android/app/src/main/java/com/novelreader/` | Kotlin ソース（ui=Compose ／ pdf=抽出パイプライン ／ data=Room ／ narou=なろうAPI連携〔Room非依存の別系統〕／ ui/discovery=発見・検索の画面群。層構造の地図は `/architecture` スキル） |
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
- **呼び出し側との境界不整合を勝手に埋めない**: 監督が編集する側とシグネチャが合わなくても、deprecated オーバーロードや互換シムを自作して整合させないこと。**コンパイルエラーのまま残す**（配線は監督がやる）。善意の互換コードは指示外＝手戻りになる。
- 監督が **plan モードで委譲**したとき（タスク文に「plan モード」「read-only」の明示がある場合を含む）は **read-only 徹底＝ファイルの新規作成/変更/削除を一切せず read/digest のみ返す**。**なぜ**: plan モードの read-only 保証は Claude Code から agy プロセスへ伝播しない（`--yolo` を渡されなければ元々書けないが、契約でも二重に守る）。

## ビルド・テスト（WSL 固有の罠あり — この手順以外で Gradle を起動しない）

```bash
export JAVA_HOME=/home/qingj/opt/jdk-17 ANDROID_HOME=/home/qingj/Android/Sdk
cd android
# local.properties に Windows 側の sdk.dir が残っていると WSL で壊れるため除去（gitignore 済みで安全）。
# ※ /mnt/c(drvfs) では sed -i が EPERM で失敗するため、一時ファイル経由で書き換える（2026-07-05 実証済み）
sed '/^sdk\.dir=/d' local.properties > local.properties.tmp && cat local.properties.tmp > local.properties && rm local.properties.tmp
# gradlew は CRLF 改行のため直接実行不可 → ラッパー jar を直接起動する。
# java は PATH に無い（非対話シェルは ~/.bashrc を読まない）ため JAVA_HOME 経由のフルパスで起動する。
# --init-script は必須（/mnt/c 上では AAPT2 が EPERM で死ぬため build/ を ext4 へ退避している）
"$JAVA_HOME/bin/java" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --no-daemon --console=plain \
  --init-script /home/qingj/ext-build/novel-reader-init.gradle \
  testDebugUnitTest
```

- 単体テストは `testDebugUnitTest` のみ実行してよい。**`connectedAndroidTest` と実機 adb 操作は絶対にしない**（実機の蔵書 DB を消す禁忌があるため監督が行う）。
- このリポジトリは `/mnt/c`（Windows マウント）上にあり I/O が遅い。広い走査は対象を絞ってから。
- **作業後に `git status` を確認し `local.properties.tmp` が残っていたら削除する**（上の sed 回避手順の副産物。gitignore 外で差分に紛れるため）。
