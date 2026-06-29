#!/usr/bin/env python3
"""
SessionStart hook: セッション開始（圧縮復帰を含む）時に「現在ブランチ文脈」をコンテキスト注入する。

なぜこのフックが要るか:
  auto-memory はパス紐付けで全ブランチ共有のため、ブランチ固有の状態（STATUS.md の有無など）を
  正しく案内できない。長時間セッションの圧縮で現在ブランチが要約から脱落すると、誤ったブランチへ
  コミットしたり、存在しない STATUS.md を正本扱いしたりする。SessionStart で毎回ブランチの事実を
  コンテキストへ注入し、規律に頼らず自動でオリエンテーションさせる。
"""
import io
import json
import os
import subprocess
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    pass


def git(args):
    try:
        r = subprocess.run(["git"] + args, capture_output=True, text=True, timeout=5)
        return r.stdout.strip() if r.returncode == 0 else ""
    except Exception:
        return ""


# 現在ブランチ（detached HEAD では空文字になる）
branch = git(["branch", "--show-current"])
toplevel = git(["rev-parse", "--show-toplevel"])

# STATUS.md の探索はカレントディレクトリ依存にしない。
# なぜスクリプト位置から逆算するか:
#   android/ などサブディレクトリから Claude を起動した場合、cwd 基準で探すと STATUS.md を
#   「存在しない」と誤判定する。.claude/hooks/ の2つ上＝リポジトリルートを基準にする。
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(os.path.dirname(script_dir))
status_path = os.path.join(project_root, "STATUS.md")
status_exists = os.path.exists(status_path)

branch_label = branch if branch else "(detached HEAD)"
status_line = (
    "STATUS.md は **このブランチに存在する** → 現況の正本として参照してよい。"
    if status_exists
    else "STATUS.md は **このブランチに存在しない** → 正本扱いしないこと（lab 系作業ブランチにのみ存在）。"
)

context = (
    "【ブランチ文脈（SessionStart 自動注入）】\n"
    f"- 現在ブランチ: {branch_label}\n"
    f"- 作業ツリー: {project_root}\n"
    f"- {status_line}\n"
    "- auto-memory はブランチ不変情報のみ。ブランチ固有の状態は git 管理の STATUS.md / handover.md が正本。\n"
    "- コード変更・コミット前に必ず `git branch --show-current` を再確認し、active plan 冒頭に対象ブランチを記録すること。"
)

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": context,
    }
}))
sys.exit(0)
