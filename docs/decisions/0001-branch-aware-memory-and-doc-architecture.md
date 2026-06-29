# 0001. ブランチ非依存な auto-memory への対処（branch-aware-memory）と doc アーキテクチャ

- ステータス: Accepted
- 日付: 2026-06-29
- 関連実装: `.claude/hooks/{statusline,inject_branch_context,guard_commit_branch,consume_protected_sentinel}.py` / `.claude/settings.json`
- 関連コミット: `cb5078f`（infra 追加）, `9247b76` / `96c8f14` / `96a9061` / `280038a` / `7416c85`

## Context（背景）

Claude Code の auto-memory（`MEMORY.md`・`~/.claude` 配下）と session history は、**作業ツリーのディレクトリパスに紐付いて全ブランチで共有**される。ブランチを切り替えても同じメモリ・履歴が読まれる＝**ブランチを区別しない**。

このため、ブランチ固有の状態（Room スキーマ版・不具合の解決状況・CP進捗など）を auto-memory に書くと、別ブランチへ切り替えた後もそれが「現況」として残り、誤誘導の温床になる。さらに長時間セッションのコンテキスト圧縮で「今どのブランチか」が要約から脱落すると、誤ったブランチ（特に `main`）へ直接コミットする事故が起こりうる。

メモリ側を「ブランチ不変情報のみ」に律しても、それは規律頼みで、圧縮や切替えの度に破綻しうる。**規律ではなく機構でブランチ識別を担保する**必要がある。

## Decision（決定）

### A. ドキュメントの役割分担（何をどこに書くか）

| 置き場所 | 役割 | ブランチ性 |
|---|---|---|
| auto-memory（`MEMORY.md`・`~/.claude`） | **ブランチ不変**の知見のみ（ユーザー嗜好・環境・ワークフロー・汎用知見） | 全共有 |
| `STATUS.md` | そのブランチの**現況**（状態・完了済み・既知不具合） | lab 等の長期ブランチに存在＝正本 |
| `handover.md` | **やること台帳**（active backlog ＋ 思いつき・取りこぼし） | 追従 |
| `.claude/plans/` の active plan | ephemeral な feature ブランチの現況 | 追従 |
| `docs/decisions/`（本ディレクトリ） | **恒久的な設計判断**（ADR・Why-not） | 非依存 |

ブランチ種別 → 現況の正本: lab → `STATUS.md` ／ ephemeral feature → `.claude/plans/` の plan ／ `main` → `handover.md`。

### B. ブランチ識別を機構で担保する4フック

1. **`statusline.py`（statusLine）** — 現在ブランチ／worktree を1行で常時表示する。statusLine は会話コンテキストの**外**で描画されるため、圧縮の影響を受けずブランチ識別が生存する。`git rev-parse` 1回で branch / toplevel / common-dir / git-dir を一括取得し、リンク worktree 名は `--git-dir` 末尾から採る（`--git-common-dir` は共通 `.git` を指し worktree 名を含まないため）。

2. **`inject_branch_context.py`（SessionStart）** — セッション開始（圧縮復帰を含む）時に、現在ブランチ・作業ツリー・`STATUS.md` の在処・現況正本の方針を `additionalContext` として注入する。`STATUS.md` 探索は cwd 非依存（スクリプト位置の2つ上＝リポジトリルート基準）で、サブディレクトリ起動時の「存在しない」誤判定を防ぐ。

3. **`guard_commit_branch.py`（PreToolUse / matcher:"Bash"）** — `main` への直接 `git commit` をコマンド境界に限定した正規表現で検知し `exit 2` でブロックする。**検査のみ**でセンチネルは消費しない。改行区切り・グローバルオプション付き（`git -C/-c … commit`）も検知し、クォート内の単なる言及は誤ブロックしない。

4. **`consume_protected_sentinel.py`（PostToolUse / matcher:"Bash"）** — 上書きセンチネル `.claude/.allow_protected_commit` を、コミットが**実際に成功した後にのみ**消費する。検査(Pre)と消費(Post)を分離する理由: PreToolUse がブロックするとコマンド自体が走らず PostToolUse は発火しない。もし Pre 側で消費すると、後続フック（テスト未実行・粒度違反等）のブロック時に「コミットは失敗したのにセンチネルだけ消える」穴が生じる。消費を「成功後」に限定してこれを塞ぐ。

## Consequences（結果と制約）

- `main` 直コミット事故が減り、SessionStart で毎回ブランチ文脈が明示される。
- **これはソフトな防御網であり完全な防止ではない**:
  - matcher が `"Bash"` のみ＝**PowerShell ツール経由・難読化形は対象外**。
  - detached HEAD は対象外（`git branch --show-current` が空＝過剰ブロック回避のため意図的）。
  - `git commit --quiet` は成功形を出力しないため当該コミットでは消費されないが、ブランチ非依存の consume が次の任意コミットで自己修復する。
- 最終防壁はガードではなく「**作業ブランチ運用 ＋ 未 push の `main` コミットは可逆**」という事実に置く。

### 既知の副作用: セッション内ブランチ跨ぎで hook が壊れる（2026-06-30 追記）

`settings.json`（hook 配線）は**セッション起動時に読み込まれ、以後ブランチ追従しない**。一方 hook 実ファイルは作業ツリー＝ブランチ追従する。infra hook を持つブランチ（feat/main）で起動したセッションから、infra hook を**持たない**ブランチ（lab 等）へ `git switch` すると、配線は存在しない hook を呼び **file-not-found** になる:

- 該当するのは PostToolUse の `consume_protected_sentinel.py` と PreToolUse の `guard_commit_branch.py`。
- `git switch <infra無しブランチ>` 自体では、PreToolUse は switch 前（infra 有りブランチ）で走るためエラーが出ず、PostToolUse のみ switch 後に file-not-found になる（非対称）。
- だが switch 後にそのブランチで `git commit` 等を実行すると、今度は **PreToolUse の `guard_commit_branch.py` も file-not-found となり、コマンド自体をブロックしうる**。
- 当座の回避: そのブランチでのコミットを **PowerShell ツール**で行えば Bash matcher の hook 自体が発火せず迂回できる（本 ADR の追記コミットも feat 経由で main 直コミットを避けつつ実施した）。
- 皮肉だが、ブランチ意識のための infra が「1セッション内のブランチ跨ぎ」で破綻する。

**根本対処（未了・今後の課題）**:
- (a) `settings.json` の各 hook コマンドを**ファイル不在に耐性化**する（存在すれば実行するラッパー化）。1セッション内のブランチ跨ぎに強く、波及も小さいため第一候補。
- (b) infra hook を全作業ブランチへ行き渡らせる（各ブランチが運用で main から取り込む）。

## Alternatives（採用しなかった案と理由）

- **worktree 分離**（ブランチ毎に別パスの worktree を割り当て、auto-memory のパス紐付けを逆手に取って分離する）: パスは分離できるが、worktree 越境で `@import` 等のパスが誤解決する既知問題があり運用が複雑化する。不採用。
- **memory 名前空間分割**（ブランチ名でメモリを名前空間化）: Claude Code の auto-memory はパス単位でしか分離できず、ブランチ名前空間の概念を持たない。実現困難で不採用。
- **PowerShell の実ゲート化**（PowerShell ツール経由のコミットもブロック）: 既存のコミット系フック（`check_commit_granularity` / `check_schema_change` / `check_lint_on_commit` / `guard_commit_branch`）が**すべて matcher `"Bash"` で配線**されており、PowerShell を実ゲート化するのは波及の大きい別タスク。現時点では据え置き、最終防壁（可逆性）に委ねる。
