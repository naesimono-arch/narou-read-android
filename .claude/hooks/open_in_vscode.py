#!/usr/bin/env python3
"""
PostToolUse hook: ファイル編集後に VS Code で該当ファイルを自動的に開く。
対象ツール: Edit, Write, MultiEdit
"""
import json
import sys
import subprocess

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

paths = set()

if tool_name in ("Edit", "Write"):
    fp = tool_input.get("file_path", "")
    if fp:
        paths.add(fp)
elif tool_name == "MultiEdit":
    for edit in tool_input.get("edits", []):
        fp = edit.get("file_path", "")
        if fp:
            paths.add(fp)

for path in paths:
    try:
        subprocess.Popen(["code", path], shell=False)
    except Exception:
        pass

sys.exit(0)
