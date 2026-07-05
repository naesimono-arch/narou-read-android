#!/usr/bin/env python3
"""
PostToolUse hook: 保護ブランチへの明示コミットが「実際に成功した後」だけ上書きセンチネルを消費する。
対象ツール: Bash

なぜ PreToolUse(guard) ではなくここで消費するか:
  PreToolUse がブロック(exit 2)するとコマンドは実行されず PostToolUse は発火しない。逆に本フックが
  発火するのは「全 PreToolUse を通過しコマンドが実行された」場合のみ。さらに出力でコミット成功を
  確認してから削除するため、git レベルで失敗（nothing to commit 等）したケースでもセンチネルは残る。
  これにより「コミット失敗なのにセンチネルだけ消える」穴を塞ぐ。
"""
import io
import json
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# 実行コマンドとしての `git commit` を検知する正規表現。
# 【重要】guard_commit_branch.py と同一定義（検知整合のため）。詳細な理由は同ファイルのコメント参照。
# 改行区切り・グローバルオプション付き（git -C/-c … commit）も対象にし、guard が許可・実行した
# コミットを consume 側でも確実に検知して、上書きセンチネルを消費できるようにする。
COMMIT_CMD_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    r"\s+commit\b"
)

# コミットを生成する merge/rebase/cherry-pick も消費対象にする。
# なぜ: guard 側が同コマンドをブロック対象に加えたため、センチネルで許可されたマージ完了後にも
# 消費が走らないと「1回限りの上書き」が使い回せてしまう。
# 【重要】guard_commit_branch.py / check_commit_granularity.py と同一定義。詳細は guard のコメント参照。
COMMIT_GENERATING_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    # (?!-) は merge-base / merge-file 等の読み取り系サブコマンドへの誤発火防止
    r"\s+(?:merge|rebase|cherry-pick)\b(?!-)(?![^\n;|&]*--(?:abort|quit|no-commit)\b)"
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

# tool_response の出力を取得。
# なぜ複数形式に対応するか: Claude Code の Bash は環境により {"stdout","stderr"} / {"output"} /
# 文字列のいずれかで渡す（mark_kotlin_tests_passed.py と同じ後方互換方針）。
tool_response = data.get("tool_response", {})
if isinstance(tool_response, str):
    output = tool_response
elif isinstance(tool_response, dict):
    raw = tool_response.get("output", "")
    if not isinstance(raw, str):
        raw = ""
    output = (raw + tool_response.get("stdout", "") + tool_response.get("stderr", ""))
else:
    output = ""

# コミット成功の判定: "[<branch/desc> <短縮hash>]" 形式が出力に現れる（commit / cherry-pick）。
# 例) "[main 1a2b3c4] message" / "[lab (root-commit) 1a2b3c4] ...".
# merge / rebase は同形式を出力しないため、成功時の定型文でも判定する
# （merge: "Merge made by …" / "Fast-forward" ／ rebase: "Successfully rebased …"）。
# 既知の限界: `git commit --quiet` は本形式を出力しないためここで消費されずセンチネルが残る。
# ただし本フックはブランチ非依存（PROTECTED 判定をしない）ため、次に成功する任意のコミットが
# 必ずセンチネルを消費する＝1コミット以内に自己修復する。よって個別対処はせず挙動として明記する。
if not (re.search(r"\[[^\]]+\s[0-9a-f]{7,}\]", output)
        or re.search(r"(?m)^\s*(?:Merge made by|Fast-forward\b|Successfully rebased)", output)):
    sys.exit(0)

hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".allow_protected_commit")

if os.path.exists(sentinel):
    try:
        os.remove(sentinel)
        print("[ブランチガード] 保護ブランチへのコミット成功を確認。上書きセンチネルを消費しました。")
    except OSError as e:
        print(f"[ブランチガード] 警告: センチネル削除に失敗: {e}", file=sys.stderr)

sys.exit(0)
