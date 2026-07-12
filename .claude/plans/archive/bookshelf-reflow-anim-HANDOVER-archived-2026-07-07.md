# 案B: 本棚 詰め直しアニメ復活 — 実行ハンドオーバー

> **対象ブランチ**: `feat/bookshelf-reflow-anim`（base: `main` e0d885f）
> **宛先**: このworktree(`~/wt/feat-bookshelf-reflow-anim`)で実装する別セッションの Claude（＝私）。最初にこれを読む想定。
> **作成**: main セッションで文脈を実コードに接地して作成。下記の file:line は本ハンドオーバー作成時（e0d885f 時点）の値。ズレていたら周辺の理由コメントで再同定すること。
> **出典**: `handover.md:55`（B節）＋ `BookshelfScreen.kt` / `build.gradle` 実読。

---

## ★次はここから（最小読みセット）

1. `android/app/build.gradle:60-95`（dependencies・BOM 2箇所）
2. `android/app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:287-346`（グリッド/リストの `items{}` と削除跡の理由コメント）
3. `handover.md:55`（B節・タスクの正本記述）
4. 案Aで `animateItemPlacement()` を削除した経緯 → `git log -p -- android/app/.../BookshelfScreen.kt` で該当コミット確認（重なりバグの症状把握）

## タスク（1行）

本棚の**削除時の詰め直しアニメを復活**させる。案Aで重なりバグ回避のため削除した `animateItemPlacement()` を、Compose BOM を上げて新API `Modifier.animateItem()` で置換する。

## 背景（案A → 案B の経緯）

- **案A（現状）**: `animateItemPlacement()` を使っていたが、**Foundation 1.6系(BOM 2024.04.01) に「高速フリング中にカバーが画面外の古い位置から補間され重なる」既知不具合**があり、これを回避するため削除した。重なりバグは解消したが、**代償で削除時の詰め直しアニメが失われた**。
- **案B（このタスク）**: BOM を `2024.09+`（Foundation 1.7系＝`animateItem()` が stable）へ上げ、`animateItem()` で置換して詰め直しアニメを復活させる。**かつフリング重なりバグを再発させないこと**が受入条件。

## 変更点（接地済み・すべて実コードで確認済み）

### 1. BOM を `2024.04.01` → `2024.09+` に上げる【2箇所・両方必須】
- `android/app/build.gradle:64` — `implementation platform('androidx.compose:compose-bom:2024.04.01')`
- `android/app/build.gradle:91` — `androidTestImplementation platform('androidx.compose:compose-bom:2024.04.01')`
- **なぜ両方**: BOM は全 Compose モジュール(ui/foundation/material3/…)の版を**一括決定**する。main と androidTest で版が食い違うと解決が壊れる。片方だけ上げない。
- バージョン選定: `animateItem()` が stable なのは Foundation **1.7**系＝BOM **2024.09.xx 以降**。まず 2024.09 系の安定版を採り、ビルド/回帰が通ることを確認してから必要に応じ調整。

### 2. `animateItemPlacement()` 相当を `animateItem()` で復活【2箇所】
- **グリッド**: `BookshelfScreen.kt:313-316` の理由コメント直下、`GridBookCard(..., modifier = Modifier)` → `modifier = Modifier.animateItem()`
- **リスト**: `BookshelfScreen.kt:341-342` の理由コメント直下、`ListBookCard(..., modifier = Modifier)` → `modifier = Modifier.animateItem()`
- `animateItem()` は **`LazyItemScope` 拡張**なので、`items{}` ラムダ内（＝現在の呼び出し位置）で使う。カード内部でなくここで付けるのが正しい。
- **前提OK**: `items(books, key = { it.id })` と **key 指定済み**（`animateItem()` はアイテムkeyが必須。これを満たしている）。
- 置換後は**理由コメントを実態へ更新**（現行「案B(…)で別タスク復活予定」→ 復活済みの説明へ）。「なぜ animateItem を使うか／元の重なりバグは版更新で解消した」を why として残す（CLAUDE.md「なぜコメント義務」）。

## リスク / 受入条件

- **リスク大＝全画面回帰必須**: BOM一括更新で material3/foundation/ui の挙動が全画面へ波及しうる。回帰対象: 本棚(list/grid) ／ 読書3テーマ(ライト/セピア/ダーク) ／ 目次 ／ 設定シート ／ 処理中バナーの slideIn/fade ／ テーマ切替追従。
- **受入条件**:
  1. 削除時に残りカードが**詰め直しアニメで動く**（復活の主目的）。
  2. **高速フリング中にカバーが重ならない**（案Aで消した元バグを**再発させない**＝このタスクの肝）。
  3. 他画面のレイアウト/アニメに退行なし。

## 検証ゲート

- **JVM単体**: `cd android && gw testDebugUnitTest`
  - このworktreeは **ext4 なので `--init-script` 不要**・in-tree でネイティブ速度（AAPT2 EPERM は /mnt/c 固有で消える。memory `workflow-parallel-worktrees`）。
  - ⚠ **Bashツールでは `.bashrc` 非ロード**で `gw`/`JAVA_HOME`/`ANDROID_HOME` が全滅する（memory `bash-tool-no-bashrc-gradle-env`）。env明示 export＋ `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain testDebugUnitTest` で回すか、ユーザーの `!` 実行を使う。
- **実機目視（このタスクの本丸）**: アニメ挙動は JVM では見えない。`assembleDebug` → `install -r` → 本棚で「削除して残りが詰まる」「高速フリング中に重ならない」を目視。
  - 実機を触る前に **`adb-bridge` 一発**（WSLはadbが端末を直接見ない。memory `workflow-autonomous-device-verification` ／ 入口は `/device-verify` スキル）。
  - WSLビルドの**上書きinstallは Windows `debug.keystore` 共有が必要**（蔵書DB保持。memory `wsl-debug-keystore-share-for-install`）。
- **コミット**: 必ず**このworktree内で起動したセッションから**（`guard_commit_branch` はセッションcwdのブランチで判定。main セッションからは誤ブロック）。Atomic に「BOM上げ」「animateItem置換＋コメント更新」を分けるのが素直（build.gradle と BookshelfScreen.kt で論理単位が違うため）。

## スコープ境界（触らないもの）

- **見た目トークンは触らない**: 色は `theme/Color.kt`、字面は `theme/Typography.kt` 経由。詰め直しは**操作感/アニメ層**でADR `docs/decisions/0005-ui-n-visual-language-D.md` §B「実機フィードバックで後詰め」の領域。意匠の自己判断はしない（CLAUDE.md「UIの見た目は /design モックが正本」）。
- **削除UI方式トグル**（`BookshelfScreen.kt:234-242` の開発用 deleteUiMode）はこのタスクと無関係。触らない。

## 参照

- `handover.md:55`（B節）／ `CLAUDE.md`（開発ルール・自己検証・なぜコメント）
- 関連 memory: `workflow-parallel-worktrees`（ext4 worktree運用）／ `bash-tool-no-bashrc-gradle-env` ／ `wsl-debug-keystore-share-for-install` ／ `workflow-autonomous-device-verification`
