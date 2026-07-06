#!/usr/bin/env python3
"""
PostToolUse hook: gradle の JVM 単体テスト(testDebugUnitTest)が成功した際に
センチネルファイルをtouchする。
対象ツール: Bash
条件（両方を満たす場合のみ）:
  主判定: コマンドが gradle テストタスク(testDebugUnitTest 等)を含む
  補助判定: 出力に "BUILD SUCCESSFUL" が含まれる（失敗時は "BUILD FAILED" になるため）

なぜ "BUILD SUCCESSFUL" を成功判定にするか:
  テストタスクが UP-TO-DATE（前回から変更なしでキャッシュ）でも gradle は
  "BUILD SUCCESSFUL" を出す＝実際にはテストが走らない場合がある。だが
  「ソース変更後の再実行強制」は check_commit_granularity.py 側の mtime 比較
  （センチネルより新しい .kt があれば再テスト要求）が担保するため、ここでは
  ビルド成功＝直近のテスト結果が green とみなして許容する。
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
# gradle の JVM 単体テストタスク呼び出しのみ対象（assembleDebug 等のビルドは除外）
if not re.search(r"test\w*UnitTest", command):
    sys.exit(0)

# tool_response の出力を取得。
# なぜトップレベルの stdout/stderr を読むか:
# Claude Code の Bash は tool_response を {"stdout":..., "stderr":...} 形式で渡す。
# 旧実装は "output" キー前提だったため値が空になり、BUILD SUCCESSFUL を検出できず
# センチネルが一度も更新されない不具合があった。旧 {"output": ...} 形式にも後方互換で対応する。
tool_response = data.get("tool_response", {})
if isinstance(tool_response, str):
    output = tool_response
elif isinstance(tool_response, dict):
    raw = tool_response.get("output", "")
    if isinstance(raw, dict):
        raw = raw.get("stdout", "") + raw.get("stderr", "")
    elif not isinstance(raw, str):
        # None 等の想定外型だと raw.get() が AttributeError でフックごと落ち、fail-open 設計が
        # 崩れるため防御（実ペイロードでは未観測＝推定リスク。consume_protected_sentinel と同方針）。
        raw = ""
    output = raw or (tool_response.get("stdout", "") + tool_response.get("stderr", ""))
else:
    output = ""

# 成功判定: gradle は成功で "BUILD SUCCESSFUL"、失敗で "BUILD FAILED" を出す
if "BUILD SUCCESSFUL" not in output:
    sys.exit(0)

# センチネルファイルをtouch
hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".kotlin_tests_passed")

try:
    with open(sentinel, "a", encoding="utf-8"):
        pass
    os.utime(sentinel, None)
    # PostToolUse の plain stdout はモデルに届かない（task_diary #28）。「テストゲート通過済み＝
    # .kt のコミットが可能」という状態は次の行動判断に直結するため additionalContext で注入する。
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": "[Kotlinテスト成功] コミットゲートのセンチネルを更新しました（.kt のコミットが可能です）。",
    }}, ensure_ascii=False))
except OSError as e:
    print(f"[Kotlinテスト成功] 警告: センチネルの更新に失敗しました: {e}", file=sys.stderr)

sys.exit(0)
