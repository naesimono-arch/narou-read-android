#!/usr/bin/env python3
"""
PreToolUse hook: 保護ブランチ（main）への直接コミットをブロックする。検査のみ・センチネルは消さない。
対象ツール: Bash

なぜ「検査のみ」で消費しないか（消費は consume_protected_sentinel.py の PostToolUse 側）:
  Claude Code の PreToolUse はいずれかのフックが exit 2 でブロックすると Bash コマンド自体が
  実行されず、PostToolUse は発火しない。もし本フックで上書きセンチネルを削除すると、後続フック
  （テスト未実行・コミット粒度等）がブロックした場合に「コミットは失敗したのにセンチネルだけ消える」
  という穴が生じ、毎回作り直しが要る。よって検査と消費を分離し、消費は「コミットが実際に走って
  成功した後」に限定する。

なぜ detached HEAD は対象外か:
  本ガードの目的は「main ブランチ名そのものへの直接コミット事故」防止。detached HEAD では
  `git branch --show-current` が空を返し PROTECTED に一致しないため、ここではブロックしない。
  防御的に広げないのは意図的（過剰ブロックで通常作業を阻害しないため）。
"""
import io
import json
import os
import re
import subprocess
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

PROTECTED = {"main"}

# 実行コマンドとしての `git commit` を検知する正規表現。
# 【重要】consume_protected_sentinel.py と同一定義（検知整合のため）。変更時は両ファイルを更新すること。
# なぜコマンド境界に限定するか:
#   本フックは exit 2 で実際にブロックするため精度が要る。素朴な \bgit\s+commit\b だと
#   `echo '...git commit...'`（クォート内の単なる言及）まで誤ブロックする（実際に検証中に巻き込まれた）。
# なぜ境界に改行 \n を含めるか:
#   `git add -A`⏎`git commit ...` のような複数行コマンド（heredoc 等）では commit が行頭に来る。
#   改行を境界に含めないと、この最頻パターンの直接コミットを取りこぼす（監査で実証）。
# なぜ git と commit の間にグローバルオプションを許容するか:
#   `git -C <path> commit` / `git -c k=v commit` / `git --git-dir=… commit` も実コミット。
#   オプションを許容しないと取りこぼす（監査で実証）。引数・メッセージ内の言及は引き続き無視する。
COMMIT_CMD_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    r"\s+commit\b"
)

# コミットを生成する merge/rebase/cherry-pick も保護対象にする。
# なぜ: 競合しない `git merge <branch>` やマージ/リベースの `--continue` は "commit" トークンを
# 含まずに保護ブランチの HEAD を進める＝リテラル git commit 検知だけでは branch guard を素通り
# していた（2026-07-06 の feat+kotlin 統合で実地に露呈＝handover hooks/fix ②）。
# --abort/--quit はコミットを生成しない回復コマンドのため除外（誤ブロックすると main 上での
# マージ中断すらセンチネルが要る本末転倒になる）。--no-commit も生成しない（後続の明示
# git commit が COMMIT_CMD_RE で捕まる）ため除外。
# 【重要】consume_protected_sentinel.py / check_commit_granularity.py と同一定義。
COMMIT_GENERATING_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    # (?!-) は merge-base / merge-file 等の読み取り系サブコマンドへの誤発火防止
    r"\s+(?:merge|rebase|cherry-pick)\b(?!-)(?![^\n;|&]*--(?:abort|quit|no-commit)\b)"
)

# コマンド文字列内で保護ブランチへ `switch`/`checkout` する箇所を検知する（実効ブランチ判定用）。
# なぜ要るか（2026-07-07 実地で判明）: `git switch main && git merge …` のように switch と
# commit/merge を1つの Bash コマンドにまとめると、PreToolUse 時点の `git branch --show-current` は
# まだ切替前の（非保護）ブランチを返すため、カレントブランチだけの判定ではガードを素通りする。
# コマンド内で保護ブランチへ移動していれば実効的なコミット先は保護ブランチなので、それも保護扱いにする。
# 設計メモ:
#  - `switch` はブランチ専用（pathspec を取らない）ため常に安全に検知できる。`checkout` は
#    `checkout <branch> -- <path>`（ファイル復元＝ブランチ移動でない）があるため、直後が ` -- ` の
#    形だけ除外する（それ以外の稀な誤検知は「センチネルを一度要求するだけ」で実害小＝guard は
#    過剰側に倒す方針。guard_sentinel_creation.py の docstring と同じ思想）。
#  - ブランチ名は完全トークン一致（直後が空白/終端/区切り）に限定し、`main~1`/`main.foo`/
#    `feature/main` 等のリビジョン・別ブランチ名を誤検知しない。
_PROTECTED_ALT = "|".join(re.escape(b) for b in sorted(PROTECTED))
SWITCH_TO_PROTECTED_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"   # git のグローバルオプション
    r"\s+(?:switch|checkout)\b"
    r"(?:\s+-[\w-]+(?:=\S+)?)*"                          # switch/checkout のオプション（-q/-c/-b 等）
    r"\s+(" + _PROTECTED_ALT + r")"                      # 移動先の保護ブランチ名（group 1）
    r"(?=\s|$|[;&|])"                                    # 完全トークン境界（~^. 等が続く rev は弾く）
    r"(?!\s+--(?:\s|$))"                                 # 直後が ` -- <path>` の checkout はブランチ移動でない
)

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")
if not (COMMIT_CMD_RE.search(command) or COMMIT_GENERATING_RE.search(command)):
    sys.exit(0)

try:
    branch = subprocess.run(
        ["git", "branch", "--show-current"],
        capture_output=True, text=True, timeout=5,
    ).stdout.strip()
except Exception:
    branch = ""

# 実効ブランチ判定: カレントが保護対象、または「コマンド内で保護ブランチへ switch/checkout する」なら
# 保護扱い。後者は `git switch main && git merge …` 型の複合コマンド対策（2026-07-07 実地の素通しを塞ぐ。
# PreToolUse 時点のカレントは切替前で非保護に見えるため、コマンド文字列から実効コミット先を補う）。
switch_match = SWITCH_TO_PROTECTED_RE.search(command)
if branch in PROTECTED:
    effective_branch = branch
elif switch_match:
    effective_branch = switch_match.group(1)
else:
    # detached HEAD（空文字）や非保護ブランチで、コマンド内の保護ブランチ移動も無ければ通す
    sys.exit(0)

# .claude/.allow_protected_commit があれば許可（ただし削除しない＝消費は PostToolUse 側）
hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".allow_protected_commit")

if os.path.exists(sentinel):
    # PreToolUse の plain stdout はモデルに届かない（task_diary #28）。許可の事実と「成功時に
    # 自動消費される」ことは次の行動判断に要るため additionalContext で注入する
    # （PreToolUse でも additionalContext が届くことは 2026-07-07 の probe で実測済み）。
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": f"[ブランチガード] 保護ブランチ '{effective_branch}' への明示コミットを許可"
                             "（センチネル検出）。コミット成功時に自動消費されます。",
    }}, ensure_ascii=False))
    sys.exit(0)

print(f"[ブランチガード] 保護ブランチ '{effective_branch}' への直接コミット"
      "（またはコミットを生成する merge/rebase/cherry-pick）をブロックします。", file=sys.stderr)
print("作業ブランチ（lab / UI-* など）へ切替えてコミットしてください。", file=sys.stderr)
print("意図的に main へコミットする場合のみ（センチネルは AI では作成できません）:", file=sys.stderr)
print("  → コミット内容をユーザーに提示して承認を得たうえで、ユーザーに入力欄で", file=sys.stderr)
print("    次を先頭の `!` ごと実行してもらう（`!`=bash mode だけがフックを迂回する）:", file=sys.stderr)
# cwd 非依存の絶対パスで案内する: `!`(bash mode) のシェル cwd はリポジトリルートである保証がなく、
# サブディレクトリから相対 `.claude/…` を叩くと "No such file or directory" で失敗する（2026-07-07 実地）。
# sentinel は上で算出済みの絶対パス（`.claude/.allow_protected_commit`）。
print(f"      ! echo > {sentinel}", file=sys.stderr)
print("  （guard_sentinel_creation.py が AI のツール経由作成を塞ぐ。作成後に再度コミットを実行）", file=sys.stderr)
sys.exit(2)
