#!/usr/bin/env python3
"""
PostToolUse hook: プランファイル書き込み後、コミット計画セクションがなければ追記を促す。
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

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

# プランファイルかどうか判定
file_path = tool_input.get("file_path", "")
plans_dir = os.path.normpath(os.path.expanduser("~/.claude/plans"))
normalized = os.path.normpath(file_path)

if not (normalized.startswith(plans_dir) and normalized.endswith(".md")):
    sys.exit(0)

# ファイル全体を読んでコミット計画セクションの有無を確認
try:
    with open(normalized, encoding="utf-8") as f:
        content = f.read()
except OSError:
    sys.exit(0)

# H2見出しに "コミット" または "commit" が含まれるか
if re.search(r"^##\s+.*?(コミット|commit)", content, re.MULTILINE | re.IGNORECASE):
    sys.exit(0)

print("[コミット計画チェック]")
print(f"プランファイル {os.path.basename(normalized)} にコミット計画セクションがありません。")
print("「## 検証方法」の後に以下の形式で追加してください:\n")
print("## コミット計画\n")
print("| # | 内容 | 対象ファイル |")
print("|---|------|------------|")
print("| 1 | feat: ... | foo.kt |")
print("| 2 | fix: ...  | bar.py |")
sys.exit(0)
