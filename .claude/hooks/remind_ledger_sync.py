#!/usr/bin/env python3
"""
PreToolUse hook: コミット直前に「台帳がこの変更に言及している」ことを行番号つきで想起させる。

なぜ PostToolUse ではなく PreToolUse か:
  CLAUDE.md は「台帳（STATUS/handover）の更新は原因となった論理変更と**同じコミット**に同梱」と
  定めている。コミット後に想起しても `git commit --amend` を強いることになり規約と噛み合わない。
  knowledge/ADR/patterns は別便で足せるので remind_task_diary.py（PostToolUse）でよいが、
  台帳だけは commit の**前**に言う必要がある。
  PreToolUse でも `hookSpecificOutput.additionalContext` は有効（task_diary #28 追補で実測済み）＝
  「ブロックせず情報だけモデルへ渡す」唯一の手段。

なぜ「台帳も見て」の平文ではなく**言及パス突合**か:
  平文の追加は毎コミット同じ小言になり読み飛ばされる。代わりに
  「staged したファイルの basename を台帳が実際に書いているか」を見ると精度が出る。
  2026-08-07 に実データ（あるラウンドの 18 コミット）で測ったところ、この条件で 7 件が該当し
  **うち 5 件が完了項目の消し忘れ**だった（残り 2 件は台帳項目でない改善＝正常な無視）。
  実際この検査で handover に残っていた完了済み 8 項目を発見・削除している。

なぜブロックしないか:
  台帳項目でない改善（フック追加など）は正常に存在するため、必ず出る想起を強制にすると
  「毎回 --no-verify 相当で無視する」運用に化ける。exit 0 の想起までに留める。
"""
import json
import os
import re
import subprocess
import sys

from hooks_common import COMMIT_CMD_RE, read_payload, wrap_stdio

wrap_stdio()

LEDGERS = ("handover.md", "STATUS.md", "docs/known-bugs-registry.md")
MAX_HITS = 6


def git(args, cwd):
    try:
        r = subprocess.run(["git"] + args, cwd=cwd, capture_output=True, text=True, timeout=5)
        return r.stdout if r.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def repo_root():
    # cwd 依存にしない（サブディレクトリ起動での誤判定回避＝inject_branch_context.py と同じ罠）。
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(os.path.dirname(script_dir))


def main():
    data = read_payload()
    if data is None:
        return 0
    if data.get("tool_name", "") != "Bash":
        return 0
    command = data.get("tool_input", {}).get("command", "")
    if not COMMIT_CMD_RE.search(command):
        return 0

    root = repo_root()
    staged = [p for p in git(["diff", "--cached", "--name-only"], root).split("\n") if p]
    if not staged:
        return 0

    # 台帳自身が staged なら、書き手は既に台帳を見ている＝想起は不要。
    if any(led in staged for led in LEDGERS):
        return 0

    # 台帳が「今まさに変更しているファイル」に言及しているかを行番号つきで拾う。
    # basename で見るのは、台帳の記述が `ui/skins/k/TocK.kt:275` のようにパス途中から始まることが
    # 多く、リポジトリ相対パス完全一致では取りこぼすため。
    targets = {os.path.basename(p) for p in staged if p.endswith((".kt", ".pro", ".py", ".yml"))}
    if not targets:
        return 0

    hits = []
    for led in LEDGERS:
        path = os.path.join(root, led)
        try:
            with open(path, encoding="utf-8") as f:
                lines = f.read().split("\n")
        except OSError:
            continue
        for i, line in enumerate(lines, 1):
            for name in sorted(targets):
                if name in line:
                    excerpt = re.sub(r"\s+", " ", line.strip())[:70]
                    hits.append(f"  {led}:{i} … {excerpt}")
                    break
            if len(hits) >= MAX_HITS:
                break
        if len(hits) >= MAX_HITS:
            break

    if not hits:
        return 0

    msg = (
        "[台帳の同梱 想起] このコミットが触るファイルを台帳が言及している。"
        "**台帳の更新は原因となった論理変更と同じコミットに同梱する**（CLAUDE.md）ので、"
        "必要なら今 `git add` してからコミットすること:\n"
        + "\n".join(hits)
        + "\n  ・消化したなら該当行を**消す**（打ち消し線で残さない）。"
        "現在値が変わったなら STATUS を直す。まだ残っているなら何もしなくてよい。"
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": msg,
        }
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
