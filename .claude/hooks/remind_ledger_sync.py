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

# CLAUDE.md が定める字数上限（known-bugs-registry には上限の定めが無いので載せない）。
LEDGER_LIMITS = {"handover.md": 8000, "STATUS.md": 6000}
MAX_DONE_HITS = 4
# 完了語から行末までに許す文字数（末尾の括弧注記ぶん）。広げると従属節の完了を拾い誤検知が増える。
DONE_TAIL_SLACK = 12

# 「完了の履歴＝git log が正本」（CLAUDE.md）に反して台帳へ残りがちな断定表現。
DONE_RE = re.compile(r"(実装済|導入済|統合済|採用済|除去済|確認済|対応済|解消済|完了|済み)")
# 現在値として正しい記述を落とすための除外。「未実装」「〜待ち」「〜禁止」「残るのは〜」は
# 完了ではなく現況そのもの＝語が含まれても消す対象ではない。
NOT_DONE_RE = re.compile(r"(未|待ち|禁止|不在|できて|していない|残る|残り|残す)")
# 走査から外す台帳。known-bugs-registry は「修正済みバグの機序」を主題にする文書で、
# 完了語が本文のいたる所に現れる＝走査すると誤検知しか出ない（実測 4/4 が誤検知）。
DONE_SCAN_SKIP = ("docs/known-bugs-registry.md",)


def emit(msg):
    # PreToolUse で additionalContext を返す＝ブロックせずモデルへ渡す唯一の手段
    # （素の stdout はモデルに届かない＝task_diary #28 追補の実測）。
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": msg,
        }
    }, ensure_ascii=False))


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


def digest_notes(root, staged_ledgers):
    """staged された台帳について「消化の合図」を作る。

    なぜ全行の列挙でなく候補提示に留めるか:
      機械判定の上限は実測 76.6%（docs/knowledge/context-cost-hook-levers-measured-limits.md）で
      誤検知は必ず出る。門にすると「毎回無視する」運用へ化けるので、判断は読み手に残す。
    """
    notes = []
    for led in staged_ledgers:
        try:
            with open(os.path.join(root, led), encoding="utf-8") as f:
                text = f.read()
        except OSError:
            continue

        limit = LEDGER_LIMITS.get(led)
        if limit:
            chars = len(text)  # wc -m と同じ文字数カウント
            # 9割で言う。超えてから言うと「縮めて収める」誘惑が働くため、消化の余地が
            # あるうちに出す（上限は圧縮の合図ではなく消化の合図＝CLAUDE.md）。
            if chars >= limit * 0.9:
                notes.append(f"  {led}: {chars}字 / 上限 {limit}字")

        if led in DONE_SCAN_SKIP:
            continue

        hits = []
        for i, line in enumerate(text.split("\n"), 1):
            s = line.strip()
            if not s or s.startswith(("#", ">", "|")):
                continue
            if NOT_DONE_RE.search(s):
                continue
            # 完了語が**行末側**に来る行だけを見る。「〜は解消済み。だが〜が残る」のように
            # 従属節で完了を述べて項目自体は開いている行が誤検知の主因で、実測では
            # この距離条件だけで候補が 6件→2件へ落ちた（末尾の括弧注記ぶんを 12 文字で許す）。
            last = None
            for mo in DONE_RE.finditer(s):
                last = mo
            if last is None or len(s) - last.end() > DONE_TAIL_SLACK:
                continue
            hits.append(f"  {led}:{i} … {re.sub(r'[ \t]+', ' ', s)[:70]}")
            if len(hits) >= MAX_DONE_HITS:
                break
        notes.extend(hits)
    return notes


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

    # 台帳自身が staged＝書き手がいま台帳を開いている。ここは黙る場所ではなく、
    # **消す判断が最も安い唯一の瞬間**。追記側にだけ合図があり削除側に無いことが
    # 台帳肥大の機序だった（実測: STATUS.md は 2026-07-23 に上限の1.4倍へ達し、圧縮で
    # 収めたあと1か月で再び上限へ戻った＝規約の言い換えだけでは機構が変わらなかった）。
    staged_ledgers = [led for led in LEDGERS if led in staged]
    if staged_ledgers:
        notes = digest_notes(root, staged_ledgers)
        if notes:
            emit(
                "[台帳の消化 想起] 台帳を触っている今が、消す判断の最も安い瞬間。"
                "**上限は圧縮の合図ではなく消化の合図**（CLAUDE.md）＝縮めて収めない:\n"
                + "\n".join(notes)
                + "\n  ・完了は git log が正本＝「〜済み」で終わる記述は台帳から消す。"
                "・やることは消化して消す。・**誤検知込みの候補**なので、現在値なら無視してよい。"
            )
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
    emit(msg)
    return 0


if __name__ == "__main__":
    sys.exit(main())
