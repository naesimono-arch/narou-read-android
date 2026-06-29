---
name: stale-check
description: md・skill・hooks・settings の陳腐化を検出する。軽量(差分ベース)とフル(並列全網羅)の2モード。実態とドキュメントのズレを確度別に報告し、修正案を提示する。
triggers:
  - "陳腐化チェック"
  - "ドキュメントやskillが古くないか確認したい"
  - "stale check"
  - "管理ファイルの整合性を点検したい"
---

# stale-check — 管理ファイル陳腐化チェック

CLAUDE.md / STATUS.md / handover.md / task_diary.md / `docs/decisions/` / `.claude/skills/` / `.claude/hooks/` / settings / `.mcp.json` が
実態（コード・ビルド設定・DBスキーマ・git）とズレていないかを点検する。

検出で終わらせず **確度高 / 要確認** に分類して報告し、**修正案（diff）を提示**する。
適用とコミットは常に人間承認（CLAUDE.md のフローに従う）。

## 2つのモード

| 呼び出し | モード | 用途 |
|----------|--------|------|
| `/stale-check` | 軽量（既定） | 前回チェック以降の差分だけ意味確認。日常的に回す。単一エージェント。 |
| `/stale-check full`（`all` / `--full` / 「全網羅」「フル」でも可） | フル | 並列エージェントで全ファイルを意味レベルまで全面照合。 |

どちらも最初に機械チェック `check_machine.py` を実行する（決定的・高速）。
モードの差は「意味チェックを差分に絞るか／全面でやるか」だけ。

## 軽量モード（既定）の手順

1. 機械チェックを実行する:
   ```
   python .claude/skills/stale-check/check_machine.py
   ```
   - 9種の機械チェックを**全件**実行し、「前回チェック以降に変わった管理ファイル」一覧の提示と、
     状態ファイル `.claude/.stale_check_state.json` の自動更新まで行う。
   - 状態記録が無い初回は「初回フォールバック」と表示される → その場合は全管理ファイルを意味確認の対象にする。
2. 出力の「前回チェック以降に変わった管理ファイル」に挙がったものだけ、下記**意味チェック観点**で精読する
   （差分が無ければ機械チェック結果のみで完了）。
3. 機械チェックの「確度高」と意味チェックで見つけたズレを統合し、**確度高 / 要確認** で報告する。
4. 各指摘に修正案（diff）を添える。承認を得てから適用・コミットする。

## フルモード（明示時）の手順

1. 機械チェックを全件実行:
   ```
   python .claude/skills/stale-check/check_machine.py --full
   ```
2. **Explore エージェントを3つ並列起動**して意味レベルまで全面照合する（差分に絞らない）:
   - (a) 管理md系: CLAUDE.md / STATUS.md / handover.md / task_diary.md / `docs/decisions/` の主張 ↔ 実コード・git
   - (b) skill系: `.claude/skills/**/SKILL.md` ↔ 実構成・DBスキーマ・コマンド
   - (c) hooks/settings系: `.claude/hooks/**` ↔ settings 登録・参照パス・git追跡
   - 各エージェントに「主張(file:line) / 実態 / 推奨アクション」を確度別で報告させる。
3. 機械チェックと3エージェントの結果を統合して報告＋修正提案する。

## check_machine.py が見る項目（機械的に確定）

1. 版数照合（CLAUDE.md ↔ gradle: Chaquopy / Python / minSdk / targetSdk）
2. DB整合（AppDatabase.kt の version ↔ schemas 最大 ↔ MIGRATION 連番 ↔ db-migration skill 履歴表）
3. hook 双方向照合（settings 参照 ↔ 実ファイル: 壊れた参照／未登録の死hook）
4. hook の git 追跡（実ファイル ↔ git ls-files: コミット漏れ）
5. コンフリクトマーカー残存
6. 参照ファイルの実在（ドキュメントが名指しする .md / .py）
7. テストコマンドの一貫性
8. gradlew パス健全性（build skill）
9. skill frontmatter 妥当性（name ↔ ディレクトリ名）

機械チェックを足したくなったら、このスクリプトに関数を追加して `CHECKS` に登録する。

## 意味チェック観点（Claude／エージェントが担当・機械化困難）

- architecture skill の記述 ↔ 実コード構成（Service名・Composable・**描画方式**。WebView→Compose のような構造変化の追従漏れ）
- skill 内部の論理矛盾（「版数は書かない」と言いつつ版数を明記、等）
- STATUS / task_diary / handover の個別エントリで状況が変わった・解決済みの記述
- `docs/decisions/` の ADR ↔ 実装（恒久決定が実装とズレていないか。例: hook 仕様変更が ADR に未反映）
- `docs/reference/frontend-design/SKILL.md` などがプロジェクト用途に対し有効か
- CLAUDE.md のルール ↔ 現行 hook / skill の実運用の整合

## 出力フォーマット

```
【類型】対象(file:line) → 主張 / 実態 / 推奨アクション
```
を「確度高（実態と明確に矛盾）」「要確認（疑わしいが断定不可）」に分類して提示する。

## 注意（Windows 環境）

- `python` 実行は PowerShell ツール / Bash ツールどちらでも可（分類器不調時は PowerShell が通りやすい）。
- 状態ファイル `.claude/.stale_check_state.json` は `.gitignore` 済みのローカル状態。コミットしない。
- リナンバー禁止: task_diary.md のエントリ番号（#N）は固定IDなので、重複を見つけても自動リネームせず報告に留める。
