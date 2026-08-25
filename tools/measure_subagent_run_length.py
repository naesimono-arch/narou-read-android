#!/usr/bin/env python3
"""サブエージェントの走行長分布 — /orchestration §0「谷 ~30」の較正材料。

データ源は `.claude/hooks/count_delegation_turns.py` が SubagentStop で追記する
`delegation-stats.jsonl`（{ts, agent_type, tool_uses, session_id, agent_id}）。

⚠️ **指標を取り違えないこと**。規範の較正値は **tool_uses（ツール呼び出し回数）**であって、
セッション jsonl の assistant ターン数ではない。後者で測ると 1 ターンに複数ツールを呼ぶぶん
系統的にずれ、「谷を大きく超えている」という誤った結論が出る（2026-08-26 に実際に踏んだ）。

⚠️ 同一 `agent_id` の行が重複する（〜08-06 で 193 行 / 169 id）。最終到達点＝最大値を採る。
⚠️ `tool_uses == 0`（agent_type 空）の行が全体の 8 割を占める。分布からは除外する。
"""
import json, os, sys
from collections import defaultdict

PATH = os.environ.get("DELEGATION_STATS") or os.path.expanduser(
    "~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid/delegation-stats.jsonl")

def load():
    rows = []
    with open(PATH, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                rows.append(json.loads(line))
            except Exception:
                pass
    return rows

def dist(rows, start, end, label, agent_type=None):
    """agent_id 重複除去つきの分布。返り値はソート済み list。"""
    u = {}
    for r in rows:
        d = r["ts"][:10]
        if not r.get("tool_uses") or not (start <= d <= end):
            continue
        if agent_type and r.get("agent_type") != agent_type:
            continue
        k = r.get("agent_id")
        u[k] = max(u.get(k, 0), r["tool_uses"])
    v = sorted(u.values())
    if not v:
        return []
    med = v[len(v)//2] if len(v) % 2 else (v[len(v)//2-1] + v[len(v)//2]) / 2
    over = 100 * sum(1 for x in v if x > 30) / len(v)
    print(f"  {label:<24} n={len(v):>4}  中央値 {med:>5}  p75 {v[int(len(v)*.75)]:>4}  "
          f"p90 {v[int(len(v)*.90)]:>4}  max {max(v):>4}   30超 {over:>5.1f}%")
    return v

rows = load()
if not rows:
    sys.exit(f"データなし: {PATH}")
days = sorted(r["ts"][:10] for r in rows)
print(f"{PATH}\n行 {len(rows):,}  期間 {days[0]} 〜 {days[-1]}\n")

print("=== 期間別 ===")
dist(rows, "0000-00-00", "2026-08-06", "較正時点まで")
dist(rows, "2026-08-07", "9999-99-99", "較正後")
allv = dist(rows, "0000-00-00", "9999-99-99", "全期間（累計）")
print("\n  skill 記載の較正値: n=96・中央値45.5・p90 103・max 199（導入前=中央値43・p90 138）")
print("  ※ 同期間・同方法で再現すると n=169（中央値47・p90 97・max 199）＝**n だけ再現しない**。")
print("     max 199 の一致から同じデータではある。母数の数え方が不明なので n は引用しないこと。")
print(f"  再較正条件『分布が倍増（~200件）』: 累計 n={len(allv)} → {'満たす' if len(allv) >= 200 else '未達'}")

print("\n=== agent_type 別（較正後・5件以上）===")
types = defaultdict(int)
for r in rows:
    if r.get("tool_uses") and r["ts"][:10] > "2026-08-06":
        types[r.get("agent_type") or "(空)"] += 1
for t, n in sorted(types.items(), key=lambda kv: -kv[1]):
    if n >= 5:
        dist(rows, "2026-08-07", "9999-99-99", t, agent_type=None if t == "(空)" else t)

print("\n=== 週別の推移（運用が走行長を縮めているか）===")
weeks = sorted({r["ts"][:10] for r in rows})
if weeks:
    import datetime
    d0 = datetime.date.fromisoformat(weeks[0])
    d1 = datetime.date.fromisoformat(weeks[-1])
    cur = d0
    while cur <= d1:
        nxt = cur + datetime.timedelta(days=6)
        dist(rows, cur.isoformat(), nxt.isoformat(), f"{cur.strftime('%m-%d')}〜{nxt.strftime('%m-%d')}")
        cur = nxt + datetime.timedelta(days=1)
