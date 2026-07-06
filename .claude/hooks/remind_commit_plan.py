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

# なぜ stderr か: PostToolUse の exit 2 でモデルに届くのは stderr のみで、stdout は
# デバッグログ止まり（公式仕様・task_diary #28）。stdout に出すと「理由の無いブロック信号」
# だけが返り、何を直せばよいかがモデルに伝わらない（B6 アンチパターン）。
print(
    f"[コミット計画チェック] `## コミット計画` セクションが {os.path.basename(normalized)} にありません。追加してください。",
    file=sys.stderr,
)
sys.exit(2)
