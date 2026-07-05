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

# 実行コマンドとしての `git commit` を検知する正規表現。
# 【重要】guard_commit_branch.py / consume_protected_sentinel.py と同一定義（検知整合のため）。
# 変更時は全ファイルを更新すること（test_hooks.py が一致を回帰固定）。
# なぜ緩い \bgit\s+commit\b から置き換えたか: `echo '...git commit...'` 等のクォート内言及にも
# 誤発火し、Kotlin ステージ×センチネル不在の状況では exit 2 の誤ブロックまで到達しうるため
# （2026-07-06 stale-check フル照合で指摘）。
# なぜ stdin 読込より前に定義するか: test_hooks.py が実ファイルを exec して定数を回収する設計のため。
COMMIT_CMD_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    r"\s+commit\b"
)
# コミットを生成する merge/rebase/cherry-pick も対象にする（--abort/--quit/--no-commit は
# コミットを生成しないため除外）。
# なぜ: 競合解決後の `git merge --continue` 等は "commit" トークンを含まずにコミットを生成し、
# リテラル git commit 検知だけではテストゲートを素通りしていた
# （2026-07-06 の feat+kotlin 統合で実地に露呈＝handover hooks/fix ②）。
# 【重要】guard_commit_branch.py / consume_protected_sentinel.py と同一定義。
COMMIT_GENERATING_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    # (?!-) は merge-base / merge-file 等の読み取り系サブコマンドへの誤発火防止
    r"\s+(?:merge|rebase|cherry-pick)\b(?!-)(?![^\n;|&]*--(?:abort|quit|no-commit)\b)"
)

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

if tool_name != "Bash":
    sys.exit(0)

command = tool_input.get("command", "")

if not (COMMIT_CMD_RE.search(command) or COMMIT_GENERATING_RE.search(command)):
    sys.exit(0)

# ステージ済みファイル一覧を取得（--name-status で削除を区別する）。
# なぜ D（削除）をテストゲート対象から除外するか: 削除ファイルはテスト実行対象が存在せず、
# センチネル存在チェックが「削除済みで実行不能なファイルのテスト」を要求してコミット不能になる
# （kotlin マージの Python 全撤去で実地に露呈＝handover hooks/fix ①）。表示用一覧には削除も含める。
try:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-status"],
        capture_output=True, text=True, timeout=10
    )
    staged_entries = []
    for line in result.stdout.strip().splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        # R/C（rename/copy）は "R100\t旧\t新" 形式＝新パス（末尾要素）を採用する
        staged_entries.append((parts[0], parts[-1]))
except Exception:
    staged_entries = []

staged = [p for _s, p in staged_entries]  # 表示用（削除含む）
gate_targets = [p for s, p in staged_entries if not s.startswith("D")]

# ──── Kotlinテスト強制チェック ────
# なぜ src/main と src/test のみ対象で androidTest を除外するか:
# androidTest（計器テスト）は実機/エミュレータが無いとコミット時に自動実行できないため、
# JVM 単体テスト(testDebugUnitTest)で回せる src/main・src/test の .kt のみをゲート対象とする。
KOTLIN_GATE_DIRS = ("android/app/src/main/", "android/app/src/test/")
KOTLIN_SENTINEL = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    ".kotlin_tests_passed"
)
KOTLIN_TEST_CMD = "cd android && ./gradlew testDebugUnitTest"

kotlin_staged = [
    f for f in gate_targets
    if f.endswith(".kt") and f.startswith(KOTLIN_GATE_DIRS)
]

if kotlin_staged:
    if not os.path.exists(KOTLIN_SENTINEL):
        print("[Kotlinテスト未実行] コミットをブロックします")
        print("以下のKotlinファイルがステージされています:")
        for f in kotlin_staged:
            print(f"  - {f}")
        print("\n先に実行してください:")
        print(f"  {KOTLIN_TEST_CMD}")
        sys.exit(2)

    kotlin_sentinel_mtime = os.path.getmtime(KOTLIN_SENTINEL)
    try:
        repo_root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, timeout=10
        ).stdout.strip()
    except Exception:
        repo_root = ""

    kotlin_stale = [
        f for f in kotlin_staged
        if os.path.exists(os.path.join(repo_root, f))
        and os.path.getmtime(os.path.join(repo_root, f)) > kotlin_sentinel_mtime
    ]
    if kotlin_stale:
        print("[Kotlinテスト古い] コミットをブロックします")
        print("センチネルより新しいKotlinファイルがあります:")
        for f in kotlin_stale:
            print(f"  - {f}")
        print("\n再度テストを実行してください:")
        print(f"  {KOTLIN_TEST_CMD}")
        sys.exit(2)
# ──── ここまで ────

# 最新プランファイルを取得
plans_dir = os.path.expanduser("~/.claude/plans")
plan_name = None
commit_section = None

plan_files = glob.glob(os.path.join(plans_dir, "*.md"))
if not plan_files:
    sys.exit(0)

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
