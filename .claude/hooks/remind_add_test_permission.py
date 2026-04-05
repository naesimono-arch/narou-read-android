#!/usr/bin/env python3
"""
PostToolUse hook: テストファイル(test_*.py)が新規作成されたとき、
settings.json の permissions.allow に対応するunittestコマンドを追加するよう Claude に促す。
対象ツール: Write, Edit
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

tool_input = data.get("tool_input", {})
file_path = tool_input.get("file_path", "")

# test_*.py パターンのファイルのみ対象
basename = os.path.basename(file_path)
if not re.match(r"^test_.+\.py$", basename):
    sys.exit(0)

# モジュール名（拡張子なし）を取得
module_name = os.path.splitext(basename)[0]

# すでに permissions.allow に登録済みか確認
settings_path = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "settings.json"
)
try:
    with open(settings_path, encoding="utf-8") as f:
        settings = json.load(f)
    allow_list = settings.get("permissions", {}).get("allow", [])
except (OSError, json.JSONDecodeError):
    allow_list = []

permission_entry = f"Bash(python -m unittest {module_name}*)"
if permission_entry in allow_list:
    sys.exit(0)

# Claude へのコンテキスト挿入
print(f"[テスト権限チェック] 新しいテストファイル '{basename}' が作成されました。")
print(f".claude/settings.json の permissions.allow に以下を追加してください:")
print(f'  "{permission_entry}"')
sys.exit(0)
