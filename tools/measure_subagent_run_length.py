#!/usr/bin/env python3
"""サブエージェントの走行長分布 — /orchestration §0「谷 ~30」の較正材料。

データ源は `.claude/hooks/count_delegation_turns.py` が SubagentStop で追記する
`delegation-stats.jsonl`（{ts, agent_type, tool_uses, session_id, agent_id}）。
正規の置き場は `~/.claude/delegation-meter/`（2026-09-07 以降。それ以前は project slug 配下で、
リポジトリが動くたびに分裂した＝フック側 base_dir の docstring）。

⚠️ **本スクリプトは「測る」だけで、規範（谷の値・再較正の要否）は持たない**。
   結論の正本は `/orchestration` §0 側だけに置く。2026-09-07 まではここに較正値の要約文を
   直書きしていたため、skill 側の再較正（2026-08-26）に追従できず、**閾値が skill「~550」対
   ここ「~200」で食い違ったまま**残った。同じ数を2箇所に書かない。

⚠️ **指標を取り違えないこと**。規範の較正値は **tool_uses（ツール呼び出し回数）**であって、
セッション jsonl の assistant ターン数ではない。後者で測ると 1 ターンに複数ツールを呼ぶぶん
系統的にずれ、「谷を大きく超えている」という誤った結論が出る（2026-08-26 に実際に踏んだ）。

⚠️ 同一 `agent_id` の行が重複しうる。最終到達点＝最大値を採る。
⚠️ `tool_uses == 0`（agent_type 空）の行が大半を占める。分布からは除外する。
"""
import datetime
import glob
import json
import os
import sys
from collections import defaultdict

# 正規の置き場を先頭に、過去に使われた slug 配下も拾う（フックが slug へ退行しても取りこぼさない）。
# realpath で畳むので、worktree slug の symlink を経由した重複は数えない。
def stats_files():
    env = os.environ.get("DELEGATION_STATS")
    if env:
        return [env]
    cands = [os.path.expanduser("~/.claude/delegation-meter/delegation-stats.jsonl")]
    cands += sorted(glob.glob(os.path.expanduser("~/.claude/projects/*/delegation-stats.jsonl")))
    out, seen = [], set()
    for p in cands:
        if not os.path.exists(p):
            continue
        rp = os.path.realpath(p)
        if rp in seen:
            continue
        seen.add(rp)
        out.append(p)
    return out


def load(paths):
    rows = []
    for p in paths:
        with open(p, encoding="utf-8", errors="replace") as f:
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
        d = r.get("ts", "")[:10]
        if not r.get("tool_uses") or not (start <= d <= end):
            continue
        if agent_type and r.get("agent_type") != agent_type:
            continue
        k = r.get("agent_id")
        u[k] = max(u.get(k, 0), r["tool_uses"])
    v = sorted(u.values())
    if not v:
        return []
    med = v[len(v) // 2] if len(v) % 2 else (v[len(v) // 2 - 1] + v[len(v) // 2]) / 2
    over = 100 * sum(1 for x in v if x > 30) / len(v)
    print(f"  {label:<26} n={len(v):>4}  中央値 {med:>6}  p75 {v[int(len(v)*.75)]:>4}  "
          f"p90 {v[int(len(v)*.90)]:>4}  max {max(v):>4}   30超 {over:>5.1f}%")
    return v


paths = stats_files()
rows = load(paths)
if not rows:
    sys.exit(f"データなし: {paths}")
days = sorted(r["ts"][:10] for r in rows if r.get("ts"))
print("読み込み: " + "\n           ".join(paths))
print(f"行 {len(rows):,}  期間 {days[0]} 〜 {days[-1]}\n")

print("=== 期間別 ===")
dist(rows, "0000-00-00", "2026-08-06", "初回較正まで(〜08-06)")
dist(rows, "2026-08-07", "2026-08-25", "2回目較正の窓(08-07〜08-25)")
dist(rows, "2026-08-26", "9999-99-99", "その後(08-26〜)")
allv = dist(rows, "0000-00-00", "9999-99-99", "全期間（累計）")
print(f"\n  累計 n={len(allv)}。再較正の要否は /orchestration §0 が正本（そちらの閾値と突き合わせる）。")

print("\n=== agent_type 別（08-26 以降・5件以上）===")
types = defaultdict(int)
for r in rows:
    if r.get("tool_uses") and r.get("ts", "")[:10] > "2026-08-25":
        types[r.get("agent_type") or "(空)"] += 1
for t, n in sorted(types.items(), key=lambda kv: -kv[1]):
    if n >= 5:
        dist(rows, "2026-08-26", "9999-99-99", t, agent_type=None if t == "(空)" else t)

print("\n=== 週別の推移（運用が走行長を縮めているか）===")
weeks = sorted({r["ts"][:10] for r in rows if r.get("ts")})
if weeks:
    cur = datetime.date.fromisoformat(weeks[0])
    last = datetime.date.fromisoformat(weeks[-1])
    while cur <= last:
        nxt = cur + datetime.timedelta(days=6)
        dist(rows, cur.isoformat(), nxt.isoformat(),
             f"{cur.strftime('%m-%d')}〜{nxt.strftime('%m-%d')}")
        cur = nxt + datetime.timedelta(days=1)
