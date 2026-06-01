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

# tool_response の出力を取得（文字列 or {"output": ...} の両形式に対応）
tool_response = data.get("tool_response", {})
if isinstance(tool_response, dict):
    raw = tool_response.get("output", "")
    output = raw if isinstance(raw, str) else (raw.get("stdout", "") + raw.get("stderr", ""))
elif isinstance(tool_response, str):
    output = tool_response
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
    print(f"[Kotlinテスト成功] センチネルを更新しました: {sentinel}")
except OSError as e:
    print(f"[Kotlinテスト成功] 警告: センチネルの更新に失敗しました: {e}", file=sys.stderr)

sys.exit(0)
