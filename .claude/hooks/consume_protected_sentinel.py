#!/usr/bin/env python3
"""
PostToolUse hook: 保護ブランチへの明示コミットが「実際に成功した後」だけ上書きセンチネルを消費する。
対象ツール: Bash

なぜ PreToolUse(guard) ではなくここで消費するか:
  PreToolUse がブロック(exit 2)するとコマンドは実行されず PostToolUse は発火しない。逆に本フックが
  発火するのは「全 PreToolUse を通過しコマンドが実行された」場合のみ。さらに出力でコミット成功を
  確認してから削除するため、git レベルで失敗（nothing to commit 等）したケースでもセンチネルは残る。
  これにより「コミット失敗なのにセンチネルだけ消える」穴を塞ぐ。
"""
import json
import os
import re
import sys

# git commit／コミットを生成する merge/rebase/cherry-pick の検知は hooks_common.py の
# 単一定義を共有する（定義と設計理由は同ファイル参照。共有を identity で固定するのは test_hooks.py）。
# guard(Pre) と consume(Post) が同一定義であることは「guard が許可・実行したコミットを consume 側でも
# 確実に検知してセンチネルを消費できる」ための整合条件＝共有化でズレようがなくなった。
from hooks_common import COMMIT_CMD_RE, COMMIT_GENERATING_RE, read_payload, wrap_stdio

wrap_stdio()

data = read_payload()
if data is None:
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")
if not (COMMIT_CMD_RE.search(command) or COMMIT_GENERATING_RE.search(command)):
    sys.exit(0)

# tool_response の出力を取得。
# なぜ複数形式に対応するか: Claude Code の Bash は環境により {"stdout","stderr"} / {"output"} /
# 文字列のいずれかで渡す（mark_kotlin_tests_passed.py と同じ後方互換方針）。
tool_response = data.get("tool_response", {})
if isinstance(tool_response, str):
    output = tool_response
elif isinstance(tool_response, dict):
    raw = tool_response.get("output", "")
    if not isinstance(raw, str):
        raw = ""
    output = (raw + tool_response.get("stdout", "") + tool_response.get("stderr", ""))
else:
    output = ""

# コミット成功の判定: "[<branch/desc> <短縮hash>]" 形式が出力に現れる（commit / cherry-pick）。
# 例) "[main 1a2b3c4] message" / "[lab (root-commit) 1a2b3c4] ...".
# merge / rebase は同形式を出力しないため、成功時の定型文でも判定する
# （merge: "Merge made by …" / "Fast-forward" ／ rebase: "Successfully rebased …"）。
# 既知の限界: `git commit --quiet` は本形式を出力しないためここで消費されずセンチネルが残る。
# ただし本フックはブランチ非依存（PROTECTED 判定をしない）ため、次に成功する任意のコミットが
# 必ずセンチネルを消費する＝1コミット以内に自己修復する。よって個別対処はせず挙動として明記する。
if not (re.search(r"\[[^\]]+\s[0-9a-f]{7,}\]", output)
        or re.search(r"(?m)^\s*(?:Merge made by|Fast-forward\b|Successfully rebased)", output)):
    sys.exit(0)

hooks_dir = os.path.dirname(os.path.abspath(__file__))
claude_dir = os.path.dirname(hooks_dir)
sentinel = os.path.join(claude_dir, ".allow_protected_commit")

if os.path.exists(sentinel):
    try:
        os.remove(sentinel)
        # PostToolUse の plain stdout はモデルに届かない（task_diary #28）。「センチネルは消費済み＝
        # 次の保護ブランチコミットには人間の再発行が要る」という状態遷移を additionalContext で知らせる。
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": "[ブランチガード] 保護ブランチへのコミット成功を確認し、上書きセンチネルを消費しました（次の保護ブランチコミットにはユーザーの再発行が必要）。",
        }}, ensure_ascii=False))
    except OSError as e:
        # ここも additionalContext で返す。なぜ stderr ではダメか: 本フックは exit 0 固定
        # （PostToolUse をブロックしない）で、exit 0 の stderr はデバッグログ止まり＝モデルに
        # 一切届かない（task_diary #28）。しかも削除失敗は「センチネルが残ったまま＝次の保護
        # ブランチコミットが人間の再認可なしに通る」という認可状態の異常であり、成功通知より
        # 届ける必要が高い。届かない経路へ出していたのは既知バグ型 hook-output-not-delivered。
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": f"[ブランチガード] 警告: 上書きセンチネルの削除に失敗しました（{e}）。"
                                 f"センチネル {sentinel} が残存＝次の保護ブランチコミットが再認可なしに通ります。"
                                 "ユーザーに削除を依頼してください。",
        }}, ensure_ascii=False))

sys.exit(0)
