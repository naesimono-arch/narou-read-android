#!/usr/bin/env python3
"""
PostToolUse hook: unittest が成功した際にセンチネルファイルをtouchする。
対象ツール: Bash
条件（両方を満たす場合のみ）:
  主判定: output.rstrip().endswith("\nOK") — 最終非空行が OK 単独行
  補助判定: "Ran N test(s) in" パターンが存在する — unittest 出力であることを確認
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
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")
if "unittest" not in command:
    sys.exit(0)

# tool_response の出力を取得。
# なぜトップレベルの stdout/stderr を読むか:
# Claude Code の Bash は tool_response を {"stdout":..., "stderr":...} 形式で渡す。
# 旧実装は "output" キー前提だったため値が空になり、テスト成功(OK)を検出できず
# センチネルが一度も更新されない不具合があった。旧 {"output": ...} 形式にも後方互換で対応する。
tool_response = data.get("tool_response", {})
if isinstance(tool_response, str):
    output = tool_response
elif isinstance(tool_response, dict):
    raw = tool_response.get("output", "")
    if not isinstance(raw, str):
        raw = raw.get("stdout", "") + raw.get("stderr", "")
    output = raw or (tool_response.get("stdout", "") + tool_response.get("stderr", ""))
else:
    output = ""

# 主判定: 最終非空行が "OK"
primary = output.rstrip().endswith("\nOK")
# 補助判定: "Ran N test(s) in" が含まれる（unittest 出力であることを確認）
auxiliary = bool(re.search(r"Ran \d+ tests? in", output))

if not (primary and auxiliary):
    sys.exit(0)

# センチネルファイルをtouch
hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".python_tests_passed")

try:
    with open(sentinel, "a", encoding="utf-8"):
        pass
    os.utime(sentinel, None)
    print(f"[Pythonテスト成功] センチネルを更新しました: {sentinel}")
except OSError as e:
    print(f"[Pythonテスト成功] 警告: センチネルの更新に失敗しました: {e}", file=sys.stderr)

sys.exit(0)
