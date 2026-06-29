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

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")
if not re.search(r"\bgit\s+commit\b", command):
    sys.exit(0)

# tool_response の出力を取得。
# なぜ複数形式に対応するか: Claude Code の Bash は環境により {"stdout","stderr"} / {"output"} /
# 文字列のいずれかで渡す（既存 mark_python_tests_passed.py と同じ後方互換方針）。
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

# コミット成功の判定: "[<branch/desc> <短縮hash>]" 形式が出力に現れる。
# 例) "[main 1a2b3c4] message" / "[lab (root-commit) 1a2b3c4] ...".
if not re.search(r"\[[^\]]+\s[0-9a-f]{7,}\]", output):
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
