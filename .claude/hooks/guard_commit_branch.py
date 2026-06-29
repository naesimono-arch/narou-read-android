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

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")
# なぜ「コマンド先頭 or 区切り直後の git commit」に限定するか:
#   本フックは exit 2 で実際にブロックするため精度が重要。素朴な \bgit\s+commit\b だと
#   `echo '...git commit...'` のような単なる言及（クォート内の文字列）まで誤ブロックする
#   （実際に検証中に巻き込まれた）。コマンド境界（行頭/&&/;/|）の直後に来る実行コマンドとしての
#   git commit のみを対象にし、引数やメッセージ内の言及は無視する。
if not re.search(r"(?:^|&&|\|\||[;|&])\s*git\s+commit\b", command):
    sys.exit(0)

try:
    branch = subprocess.run(
        ["git", "branch", "--show-current"],
        capture_output=True, text=True, timeout=5,
    ).stdout.strip()
except Exception:
    branch = ""

# detached HEAD（空文字）や非保護ブランチは通す
if branch not in PROTECTED:
    sys.exit(0)

# .claude/.allow_protected_commit があれば許可（ただし削除しない＝消費は PostToolUse 側）
hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".allow_protected_commit")

if os.path.exists(sentinel):
    print(f"[ブランチガード] 保護ブランチ '{branch}' への明示コミットを許可（センチネル検出）。"
          "コミット成功時に自動消費されます。")
    sys.exit(0)

print(f"[ブランチガード] 保護ブランチ '{branch}' への直接コミットをブロックします。", file=sys.stderr)
print("作業ブランチ（lab / UI-* など）へ切替えてコミットしてください。", file=sys.stderr)
print("意図的に main へコミットする場合のみ:", file=sys.stderr)
print(f"  echo > \"{sentinel}\"   # を作成してから再実行（1回の成功コミットで自動消費）", file=sys.stderr)
sys.exit(2)
