#!/usr/bin/env python3
"""
PreToolUse hook: 委譲（サブエージェント）の編集が「行を消す」形になったときだけ、
消える行を本人へ提示する（ブロックしない）。
matcher: Edit|Write|MultiEdit ／ 対象: agent_id を持つ呼び出し＝サブエージェントのみ

なぜ作ったか:
  既知バグレジストリ `delegation-deletes-out-of-scope`＝「委譲した結果、指示範囲外の機能が
  黙って消える」（条件シートの『今月』『先月』チップが委譲バッチで消えた実例）。防御は
  CLAUDE.md の「削除行込み diff 全量レビュー」という**人間側の規律だけ**で、機械は何も止めて
  いなかった。追加行だけ読んで承認する失敗は規律では反復するので、削除が起きる瞬間に
  「何が消えるか」を本文つきで突きつける側へ移す。

なぜ子へ通告するのか（監督へ上げないのか）:
  子は自分の指示範囲を知っている唯一の当事者で、スコープ外削除の大半は「ブロックを書き直す際に
  項目を落とす」事故＝本人が気づけば直せる。監督側へ届く経路は SubagentStop の decision のみで、
  それは子を再走行させる意味になり通告には使えない（項目17 の対応表・Stop 系は
  additionalContext を持たない）。PreToolUse の hookSpecificOutput.additionalContext は
  本リポジトリで実測済みの経路（2026-07-07 probe・check_sequence_id_collision.py と同型）。

なぜ PreToolUse か（PostToolUse ではなく）:
  Write は全置換で、実行後には旧内容が消えて「何行消えたか」を復元できない。PreToolUse なら
  ディスク上の旧内容と tool_input の新内容を突き合わせて正確に差分が取れる（Edit も同じ経路で
  扱えるので入口を1つにできる）。ブロックはしない＝編集はそのまま実行される。

閾値の較正（推測せず実測。~/.claude/projects/*/*/subagents/agent-*.jsonl の 1955 本、
サブエージェントの Edit 5303 件・エージェント 108 体の分布を集計。2026-07-29 実測）:
  - 削除行数は p50=1 / p90=7 / p95=14 / p99=42。**15 行以上は 3.17%**。
  - 「追加0行の純削除」は 7.69% だが、その大半（231/408）は1行だけの微修正。
    **純削除2行以上は 3.34%** で、これがスコープ外削除の形（機能の塊が replacement 無しに消える）。
  - 採用条件〈純削除≥2 ∨ 削除≥15〉の発火率は **5.94%**＝子1体あたり平均 2.9 回
    （エージェント平均 49 Edit）。count_delegation_turns の 30 回ごと通告と同程度の頻度に収まる。
  純削除1行を外したのは、含めると発火率が 10.3% へ跳ね上がり（+4.4pt が1行削除）
  通告が背景ノイズ化するため。1行の取りこぼしは監督の diff レビューが引き続き受け持つ。

fail-open（常に exit 0・例外を握る）:
  レビュー補助の故障が編集を妨げてはならない。無症状で死ぬ代償（task_diary #44）は
  test_warn_delegated_deletions.py の陽性コントロールで担保する。
"""
import collections
import json
import os
import sys

from hooks_common import read_payload, wrap_stdio

# 通告の発火条件（上の docstring に実測根拠。変更時は docstring の数値も更新すること）。
PURE_DELETE_MIN = 2   # 追加が1行も無い純削除でこの行数以上
DELETE_MIN = 15       # 追加を伴っていても削除がこの行数以上
SAMPLE_LINES = 5      # 通告に載せる削除行の見本数
SAMPLE_WIDTH = 100    # 1行あたりの表示幅（長大な行で通告が肥大するのを防ぐ）


def line_bag(text):
    """行の多重集合。空行を捨て左右空白を落とす。

    なぜ正規化するか: インデントの付け替え・末尾空白の掃除まで「削除」と数えると、
    ブロックの再インデントだけで閾値を超えて通告が空振りする（実測で最も多い偽陽性の形）。
    """
    return collections.Counter(l.strip() for l in text.splitlines() if l.strip())


def diff_counts(old, new):
    """(消える行のリスト, 追加行数)。消える行＝old にあって new に残らない行。"""
    removed = line_bag(old) - line_bag(new)
    added = line_bag(new) - line_bag(old)
    return list(removed.elements()), sum(added.values())


def read_current(path):
    """編集前のディスク内容。読めない・存在しないなら None（新規作成＝削除ゼロ）。"""
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


def analyze(tool_name, tool_input):
    """(消える行, 追加行数, 対象ファイル) を返す。対象外なら None。"""
    path = tool_input.get("file_path", "") or ""
    if tool_name == "Edit":
        return (*diff_counts(tool_input.get("old_string", "") or "",
                             tool_input.get("new_string", "") or ""), path)
    if tool_name == "MultiEdit":
        removed, added = [], 0
        for e in tool_input.get("edits", []) or []:
            if not isinstance(e, dict):
                continue
            r, a = diff_counts(e.get("old_string", "") or "", e.get("new_string", "") or "")
            removed += r
            added += a
        return removed, added, path
    if tool_name == "Write":
        old = read_current(path)
        if old is None:
            return [], 0, path  # 新規ファイル＝削除は起こり得ない
        return (*diff_counts(old, tool_input.get("content", "") or ""), path)
    return None


def should_warn(removed, added):
    """実測較正した発火条件（純削除≥2 ∨ 削除≥15）。"""
    n = len(removed)
    if n == 0:
        return False
    return (added == 0 and n >= PURE_DELETE_MIN) or n >= DELETE_MIN


def build_message(removed, added, path):
    sample = [l[:SAMPLE_WIDTH] for l in removed[:SAMPLE_LINES]]
    more = len(removed) - len(sample)
    body = " / ".join(sample) + (f" …ほか{more}行" if more > 0 else "")
    kind = "純削除（置き換え無し）" if added == 0 else f"削除{len(removed)}行・追加{added}行"
    return (
        f"[委譲スコープ] この編集で {os.path.basename(path) or path} から "
        f"{len(removed)} 行が消える（{kind}）。消える行: {body}\n"
        "指示範囲外の機能・選択肢まで消えていないか確認すること。範囲外なら復元し、"
        "意図的な削除なら報告に理由を明記する（CLAUDE.md「削除行込み diff 全量レビュー」の機械化。"
        "正当な削除ならこの通告は無視してよい）。"
    )


def main():
    wrap_stdio()
    data = read_payload()
    if data is None:
        return
    # サブエージェント判別は agent_id の有無（公式 hooks doc。agent_type は `--agent` 起動の
    # メインセッションにも付くため判別に使わない＝count_delegation_turns.py と同じ根拠）。
    if not data.get("agent_id"):
        return
    result = analyze(data.get("tool_name", ""), data.get("tool_input", {}) or {})
    if result is None:
        return
    removed, added, path = result
    if not should_warn(removed, added):
        return
    print(json.dumps(
        {"hookSpecificOutput": {"hookEventName": "PreToolUse",
                                "additionalContext": build_message(removed, added, path)}},
        ensure_ascii=False,
    ))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # fail-open: レビュー補助の故障で編集を止めない（無症状化は自己テストの陽性コントロールで担保）
        pass
    sys.exit(0)
