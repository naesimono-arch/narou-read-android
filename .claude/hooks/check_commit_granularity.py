#!/usr/bin/env python3
"""
PreToolUse hook: git commit 前に最新プランのコミット計画とステージ済みファイルを提示する。
対象ツール: Bash
"""
import glob
import io
import json
import os
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

# ステージ済みファイル一覧を取得
try:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only"],
        capture_output=True, text=True, timeout=10
    )
    staged = [f for f in result.stdout.strip().splitlines() if f]
except Exception:
    staged = []

# ──── Pythonテスト強制チェック ────
PYTHON_DIR = "android/app/src/main/python/"
SENTINEL = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    ".python_tests_passed"
)

python_staged = [f for f in staged if f.startswith(PYTHON_DIR) and f.endswith(".py")]

if python_staged:
    if not os.path.exists(SENTINEL):
        print("[Pythonテスト未実行] コミットをブロックします")
        print("以下のPythonファイルがステージされています:")
        for f in python_staged:
            print(f"  - {f}")
        print("\n先に実行してください:")
        print("  cd android/app/src/main/python && python -m unittest test_logic -v")
        sys.exit(2)

    sentinel_mtime = os.path.getmtime(SENTINEL)
    try:
        repo_root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=10
        ).stdout.strip()
    except Exception:
        repo_root = ""

    if not repo_root:
        repo_root = os.getcwd()

    stale = [
        f for f in python_staged
        if os.path.exists(os.path.join(repo_root, f))
        and os.path.getmtime(os.path.join(repo_root, f)) > sentinel_mtime
    ]
    if stale:
        print("[Pythonテスト古い] コミットをブロックします")
        print("センチネルより新しいPythonファイルがあります:")
        for f in stale:
            print(f"  - {f}")
        print("\n再度テストを実行してください:")
        print("  cd android/app/src/main/python && python -m unittest test_logic -v")
        sys.exit(2)
# ──── ここまで ────

# 最新プランファイルを取得
plans_dir = os.path.expanduser("~/.claude/plans")
plan_name = None
commit_section = None

plan_files = glob.glob(os.path.join(plans_dir, "*.md"))
if plan_files:
    latest = max(plan_files, key=os.path.getmtime)
    plan_name = os.path.basename(latest)
    try:
        with open(latest, encoding="utf-8") as f:
            plan_content = f.read()
        # H2見出し行に "コミット" or "commit" を含むセクションを抽出
        # 行単位で処理してDOTALLによる誤マッチを防ぐ
        # コードブロック内の偽H2を避けるため最後のマッチを使う（計画末尾に置く慣習）
        lines = plan_content.splitlines()
        start = None
        for i, line in enumerate(lines):
            if re.match(r"^##\s+.*?(コミット|commit)", line, re.IGNORECASE):
                start = i  # 最後のマッチで上書き
        if start is not None:
            section_lines = [lines[start]]
            for line in lines[start + 1:]:
                if re.match(r"^##\s+", line):
                    break
                section_lines.append(line)
            commit_section = "\n".join(section_lines).strip()
    except OSError:
        pass

# 出力
print("[コミット粒度チェック]")
if staged:
    print(f"ステージ済みファイル ({len(staged)}件):")
    for f in staged:
        print(f"  - {f}")
else:
    print("ステージ済みファイル: なし")

if plan_name:
    print(f"\nアクティブプラン: {plan_name}")
    if commit_section:
        print("コミット計画:")
        print(commit_section)
    else:
        print("（このプランにコミット計画セクションはありません）")

print("\nこのステージ内容はコミット計画の何番に対応しますか？")
sys.exit(0)
