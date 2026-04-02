#!/usr/bin/env python3
"""
PreToolUse hook: git commit 前に Room スキーマ変更を検知して確認を促す。
対象ツール: Bash (git commit)

検知パターン:
  A) schemas/*.json がステージ済み → Migrationオブジェクト・バージョン番号の確認を促す
  B) *Entity.kt がステージ済みだが schemas/ がステージされていない
     → ビルド前にスキーマが再生成されていない可能性を警告
"""
import io
import json
import re
import subprocess
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

if tool_name != "Bash":
    sys.exit(0)

command = tool_input.get("command", "")
if not re.search(r"\bgit\s+commit\b", command):
    sys.exit(0)

# ステージ済みファイルを取得
try:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only"],
        capture_output=True, text=True, timeout=10
    )
    staged = result.stdout.strip().splitlines()
except Exception:
    sys.exit(0)

schema_files = [f for f in staged if "schemas/" in f and f.endswith(".json")]
entity_files = [f for f in staged if f.endswith("Entity.kt")]

if schema_files:
    print("[Room スキーマ変更検知]")
    print("以下のスキーマJSONがステージされています:")
    for f in schema_files:
        print(f"  - {f}")
    print()
    print("コミット前に以下を確認してください:")
    print("  1. AppDatabase.kt の version が +1 されているか")
    print("  2. 対応する Migration オブジェクト（MIGRATION_N_M）が追加されているか")
    print("  3. AppDatabase.kt の migrations リストに登録されているか")
    print("  → 詳細手順: /db-migration スキルを参照")
    sys.exit(0)

if entity_files and not schema_files:
    print("[Room スキーマ未更新の疑い]")
    print("以下の Entity ファイルがステージされていますが、schemas/*.json が含まれていません:")
    for f in entity_files:
        print(f"  - {f}")
    print()
    print("スキーマJSONはビルド時にKSPが自動生成します。")
    print("  ./gradlew compileDebugKotlin を実行して schemas/ を再生成してからステージしてください。")
    sys.exit(0)

sys.exit(0)
