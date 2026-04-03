#!/usr/bin/env python3
"""
PostToolUse hook: unittest が成功したらセンチネルファイルを touch する。
"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_input = data.get("tool_input", {})
tool_response = data.get("tool_response", {})

command = tool_input.get("command", "")
output = tool_response.get("output", "")

if "unittest" not in command:
    sys.exit(0)

if "\nOK" not in output:
    sys.exit(0)

sentinel = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    ".python_tests_passed"
)
try:
    with open(sentinel, "w") as f:
        f.write("")
    print(f"[Pythonテスト合格] センチネルを更新しました: {sentinel}")
except OSError as e:
    print(f"[警告] センチネルの書き込みに失敗しました: {e}", file=sys.stderr)

sys.exit(0)
