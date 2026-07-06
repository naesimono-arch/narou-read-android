#!/usr/bin/env python3
"""
PreToolUse hook: git commit 前に最新プランのコミット計画とステージ済みファイルを提示する。
対象ツール: Bash

出力方式: ブロック理由は exit 2 + stderr、情報提示は hookSpecificOutput.additionalContext。
plain stdout はどちらの用途でもモデルに届かない（task_diary #28）。
"""
import glob
import json
import os
import re
import subprocess
import sys

# git commit／コミットを生成する merge/rebase/cherry-pick の検知は hooks_common.py の
# 単一定義を共有する（定義と設計理由は同ファイル参照。共有を identity で固定するのは test_hooks.py）。
# 本フック固有の背景: 旧・緩い検知（\bgit\s+commit\b）はクォート内言及にも誤発火し、
# Kotlin ステージ×センチネル不在の状況では exit 2 の誤ブロックまで到達しえた（2026-07-06 指摘）。
from hooks_common import COMMIT_CMD_RE, COMMIT_GENERATING_RE, read_payload, wrap_stdio

wrap_stdio()

data = read_payload()
if data is None:
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
# なぜ OS で案内コマンドを分けるか: Linux/WSL では gradlew が CRLF 改行で直接実行できず、
# 非対話シェルは .bashrc 非ロードで java も PATH に無い。従来の "./gradlew" 案内は
# ブロック時にモデルが実行不能なコマンドへ誘導されて空回りする（AGENTS.md の実証済み手順に揃える）。
if os.name == "nt":
    KOTLIN_TEST_CMD = "cd android && ./gradlew testDebugUnitTest"
else:
    KOTLIN_TEST_CMD = (
        'export JAVA_HOME=$HOME/opt/jdk-17 ANDROID_HOME=$HOME/Android/Sdk && cd android && '
        '"$JAVA_HOME/bin/java" -classpath gradle/wrapper/gradle-wrapper.jar '
        "org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain testDebugUnitTest"
    )

kotlin_staged = [
    f for f in gate_targets
    if f.endswith(".kt") and f.startswith(KOTLIN_GATE_DIRS)
]

if kotlin_staged:
    if not os.path.exists(KOTLIN_SENTINEL):
        # なぜ stderr か: PreToolUse の exit 2 でモデルに届くのは stderr のみ（task_diary #28）。
        # stdout だと「理由の無いブロック」になり、どのテストを実行すべきかが伝わらない。
        print("[Kotlinテスト未実行] コミットをブロックします", file=sys.stderr)
        print("以下のKotlinファイルがステージされています:", file=sys.stderr)
        for f in kotlin_staged:
            print(f"  - {f}", file=sys.stderr)
        print("\n先に実行してください:", file=sys.stderr)
        print(f"  {KOTLIN_TEST_CMD}", file=sys.stderr)
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
        # stderr の理由は上の未実行ブロックと同じ（task_diary #28）。
        print("[Kotlinテスト古い] コミットをブロックします", file=sys.stderr)
        print("センチネルより新しいKotlinファイルがあります:", file=sys.stderr)
        for f in kotlin_stale:
            print(f"  - {f}", file=sys.stderr)
        print("\n再度テストを実行してください:", file=sys.stderr)
        print(f"  {KOTLIN_TEST_CMD}", file=sys.stderr)
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

# 出力: PreToolUse の plain stdout はデバッグログ止まりでモデルに届かない（task_diary #28）ため、
# 本フックの主機能（ステージ内容↔コミット計画の突合をモデルに促す）は
# hookSpecificOutput.additionalContext(JSON) で注入する。旧実装は plain print だったため
# この提示が一度もモデルに届いていなかった（2026-07-07 横展開監査で顕在化）。
out = ["[コミット粒度チェック]"]
if staged:
    out.append(f"ステージ済みファイル ({len(staged)}件):")
    out.extend(f"  - {f}" for f in staged)
else:
    out.append("ステージ済みファイル: なし")

if plan_name:
    out.append(f"\nアクティブプラン: {plan_name}")
    if commit_section:
        out.append("コミット計画:")
        out.append(commit_section)
    else:
        out.append("（このプランにコミット計画セクションはありません）")

out.append("\nこのステージ内容はコミット計画の何番に対応しますか？")
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": "\n".join(out),
    }
}, ensure_ascii=False))
sys.exit(0)
