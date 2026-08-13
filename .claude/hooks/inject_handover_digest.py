#!/usr/bin/env python3
"""
SessionStart hook: 台帳（handover.md）の「地図」と active plan 候補をコンテキスト注入する。

なぜこのフックが要るか:
  毎セッション、モデルは「次に何をやるか」を知るために handover.md を探して読み直す。
  ファイルは 300 行規模で、実際に必要なのはそのうち1〜2節だけ＝全文読みは丸損。
  ここで **節見出し＋行番号＋件数の地図**を先に渡せば、モデルは Read(offset/limit) で
  必要な節だけを取りに行ける。探索と全文読みの往復が消える。

設計上の判断（サイレント失敗クラスを避けるため）:
  - **本文は注入しない・地図だけ注入する**。全文を毎回注入すると今度は常時コンテキストが太る
    （auto-memory の index を1行フックに留めているのと同じ理由）。
  - active plan は `~/.claude/plans/` にあるが、どれが「今の」plan かはファイル名からは決まらない
    （plan 名は自動生成語＝ブランチ名と無関係）。よって **mtime 最新かつ N 日以内のものを「候補」
    として提示するに留め、「active とは限らない」と明示**する。誤った plan を active と断定して
    注入するのは、何も注入しないより有害（inject_branch_context.py が STATUS.md の不在を
    「正本扱いするな」と明示するのと同じ思想）。
  - 出力は `hookSpecificOutput.additionalContext`（plain stdout はモデルに届かない＝task_diary #28）。

配線: `.claude/settings.json` の SessionStart 配列に追加（ディスパッチャは作らない＝ADR 0008）。
      **配線の変更はセッション起動時固定＝反映は次セッションから**（本体 .py の編集は即反映）。
"""
import io
import json
import os
import re
import subprocess
import sys
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    pass

PLAN_FRESH_DAYS = 7
MAX_SECTION_TITLE = 60


def repo_root():
    # cwd 依存にしない理由は inject_branch_context.py と同じ（サブディレクトリ起動での誤判定回避）。
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(os.path.dirname(script_dir))


def outline(path):
    """md の見出しを (行番号, レベル, タイトル, 直下の箇条書き件数) で返す。"""
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().split("\n")
    except OSError:
        return [], 0
    heads = []
    for i, line in enumerate(lines):
        m = re.match(r"^(#{2,3}) +(.*)$", line)
        if m:
            heads.append([i + 1, len(m.group(1)), m.group(2).strip(), 0])
    # 各見出しの直下（次の見出しまで）にあるトップレベル箇条書きを数える。
    # 「やること台帳に何件残っているか」を件数だけで伝えるのが目的で、本文は載せない。
    for idx, h in enumerate(heads):
        start = h[0]
        end = heads[idx + 1][0] - 1 if idx + 1 < len(heads) else len(lines)
        h[3] = sum(1 for line in lines[start:end] if re.match(r"^- ", line))
    return heads, len(lines)


def current_branch():
    try:
        r = subprocess.run(["git", "branch", "--show-current"],
                           capture_output=True, text=True, timeout=5)
        return r.stdout.strip() if r.returncode == 0 else ""
    except Exception:
        return ""


def plan_candidate():
    """~/.claude/plans/ の中で mtime 最新かつ PLAN_FRESH_DAYS 以内のものを1つ返す。"""
    plans_dir = os.path.join(os.path.expanduser("~"), ".claude", "plans")
    if not os.path.isdir(plans_dir):
        return None
    newest, newest_mtime = None, 0.0
    for name in os.listdir(plans_dir):
        if not name.endswith(".md"):
            continue
        p = os.path.join(plans_dir, name)
        try:
            mtime = os.path.getmtime(p)
        except OSError:
            continue
        if mtime > newest_mtime:
            newest, newest_mtime = p, mtime
    if newest is None:
        return None
    age_days = (time.time() - newest_mtime) / 86400.0
    if age_days > PLAN_FRESH_DAYS:
        return None
    return newest, age_days


def render_outline(heads, limit=24):
    out = []
    for line_no, level, title, bullets in heads[:limit]:
        if len(title) > MAX_SECTION_TITLE:
            title = title[:MAX_SECTION_TITLE] + "…"
        indent = "  " * (level - 2)
        suffix = f"（{bullets}件）" if bullets else ""
        out.append(f"  {indent}L{line_no}: {title}{suffix}")
    if len(heads) > limit:
        out.append(f"  …ほか {len(heads) - limit} 節")
    return out


def main():
    root = repo_root()
    parts = ["【台帳の地図（SessionStart 自動注入）】"]

    handover = os.path.join(root, "handover.md")
    if os.path.exists(handover):
        heads, total = outline(handover)
        parts.append(
            f"- やること台帳 `handover.md`（全{total}行）。**全文を読まず、下の行番号で必要な節だけ "
            f"Read(offset/limit) すること**:"
        )
        parts.extend(render_outline(heads))
    else:
        parts.append(
            "- `handover.md` はこのブランチに無い → やることの正本は active plan 側。"
        )

    cand = plan_candidate()
    if cand:
        path, age = cand
        heads, total = outline(path)
        parts.append(
            f"- active plan の候補: `{path}`（最終更新 {age:.1f} 日前・全{total}行）。"
            f"⚠️ plan 名はブランチ名と無関係な自動生成語のため、**これが現在の plan とは限らない**。"
            f"内容が今のブランチ（{current_branch() or 'detached'}）と噛み合わなければ無視すること。"
        )
        parts.extend(render_outline(heads, limit=12))
    else:
        parts.append(
            f"- `~/.claude/plans/` に直近{PLAN_FRESH_DAYS}日以内の plan は無い"
            "（＝進行中の plan セッションは無いと見てよい）。"
        )

    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": "\n".join(parts),
        }
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
