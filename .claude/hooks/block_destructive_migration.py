#!/usr/bin/env python3
"""
PreToolUse hook: fallbackToDestructiveMigration() の使用を検出・ブロックする。
既存ユーザーの Room DB データが消えるため絶対禁止。
対象ツール: Edit, Write, MultiEdit, Bash
"""
import json
import sys

data = json.load(sys.stdin)
tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

TARGET = "fallbackToDestructiveMigration"

found = False
if tool_name == "Bash":
    found = TARGET in tool_input.get("command", "")
elif tool_name in ("Edit", "Write"):
    found = (
        TARGET in tool_input.get("new_string", "")
        or TARGET in tool_input.get("content", "")
    )
elif tool_name == "MultiEdit":
    for edit in tool_input.get("edits", []):
        if TARGET in edit.get("new_string", ""):
            found = True
            break

if found:
    print(
        "🛑 BLOCK: `fallbackToDestructiveMigration` は使用禁止です。\n"
        "既存ユーザーの Room DB データ（書籍・読書進捗）が全て消えます。\n"
        "正しい Migration オブジェクトを作成してください（/db-migration スキル参照）。",
        file=sys.stderr,
    )
    sys.exit(2)

sys.exit(0)
