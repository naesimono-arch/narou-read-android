#!/usr/bin/env python3
"""オーケストレーションがどこで漏れているかを実測する。

問い: セッションを「切る」代わりに委譲で main のコンテキストを伸ばさない設計にしたのに、
main の cache_read が平均 186k/ターンある。何が伸ばしているのか。

分けて測る:
  1. tool_result を side 別に集計 — main が自分で Read/Bash しているなら委譲の漏れ
  2. 各セッション初回 assistant ターンのコンテキスト長 — 常設注入（システム＋CLAUDE.md＋memory）
     ＝「切っても減らない固定費」。切る判断の損得に直結する
  3. セッション内のターン位置別 cache_read — 伸び方の形（線形か階段か）
     ＋ 長さが線形なら累積は O(n^2) になるので、切っていた場合の反実仮想まで出す
     （2026-08-26 追加。知見＝docs/knowledge/context-cost-superlinear-in-session-length.md）
  4. Agent の tool_result サイズ — digest が効いているか（返却が肥大していないか）
"""
import json, glob, os, sys
from collections import Counter, defaultdict

# Windows 側ユーザー名の移行（qingj→naesimono, 2026-08-23）で project slug が分岐した。
# 過去の実測値は旧 slug 側にしか無いので、新旧どちらも走査する。
BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
]
files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
})

name_by_id = {}
res_by_side = {"main": Counter(), "sub": Counter()}
cnt_by_side = {"main": Counter(), "sub": Counter()}
first_ctx = []
main_seqs = []   # main セッションごとの [ctx per turn]
POS = [(1, 10), (11, 50), (51, 100), (101, 200), (201, 400), (401, 10**9)]
pos_sum = defaultdict(int)
pos_n = defaultdict(int)

for fp in files:
    side = "sub" if "/subagents/" in fp else "main"
    turn = 0
    seq = []
    with open(fp, encoding="utf-8", errors="replace") as f:
        for line in f:
            if '"tool_' not in line and '"usage"' not in line:
                continue
            try:
                ev = json.loads(line)
            except Exception:
                continue
            msg = ev.get("message") or {}
            if ev.get("type") == "assistant":
                turn += 1
                u = msg.get("usage") or {}
                ctx = ((u.get("input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)
                       + (u.get("cache_creation_input_tokens") or 0))
                if turn == 1 and side == "main":
                    first_ctx.append(ctx)
                if side == "main":
                    if ctx > 0:
                        seq.append(ctx)
                    for lo, hi in POS:
                        if lo <= turn <= hi:
                            pos_sum[(lo, hi)] += ctx
                            pos_n[(lo, hi)] += 1
                            break
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            for b in content:
                if not isinstance(b, dict):
                    continue
                if b.get("type") == "tool_use":
                    name_by_id[b.get("id")] = b.get("name")
                elif b.get("type") == "tool_result":
                    c = b.get("content")
                    size = (sum(len(x.get("text") or "") for x in c if isinstance(x, dict))
                            if isinstance(c, list) else len(c or ""))
                    nm = name_by_id.get(b.get("tool_use_id"), "(不明)")
                    res_by_side[side][nm] += size
                    cnt_by_side[side][nm] += 1
    if side == "main" and len(seq) >= 5:
        main_seqs.append(seq)

print("=== tool_result を side 別に（コンテキストへ居座る素材の出所）===")
for side in ("main", "sub"):
    tot = sum(res_by_side[side].values())
    print(f"\n[{side}] 合計 {tot/1e6:.1f}M 文字")
    for nm, v in res_by_side[side].most_common(6):
        print(f"    {nm:<20}{v/1e6:>7.1f}M  ({cnt_by_side[side][nm]:>6}回・平均{v//max(cnt_by_side[side][nm],1):>6,}) "
              f"{100*v/max(tot,1):>5.1f}%")
m, s = sum(res_by_side["main"].values()), sum(res_by_side["sub"].values())
print(f"\n  → main が抱えた素材は全体の {100*m/max(m+s,1):.1f}%（委譲できていれば sub 側に寄るはず）")

if first_ctx:
    first_ctx.sort()
    md = first_ctx[len(first_ctx)//2]
    print(f"\n=== 常設注入（各セッション初回ターンのコンテキスト長）===")
    print(f"  中央値 {md/1000:.1f}k / 最小 {first_ctx[0]/1000:.1f}k / 最大 {first_ctx[-1]/1000:.1f}k"
          f"（n={len(first_ctx)}）")
    print(f"  ※ これは「切っても減らない固定費」。27,068 ターン全体で ≒ {md*27068/1e6:.0f}M トークン相当")

print(f"\n=== main のターン位置別 平均コンテキスト長（伸び方の形）===")
for lo, hi in POS:
    if pos_n[(lo, hi)]:
        label = f"{lo}-{hi if hi < 10**9 else '∞'}"
        print(f"  {label:>9} ターン目: 平均 {pos_sum[(lo,hi)]//pos_n[(lo,hi)]/1000:>7.1f}k "
              f"（n={pos_n[(lo,hi)]:>6}）  この帯の総計 {pos_sum[(lo,hi)]/1e6:>7.0f}M")
tot_pos = sum(pos_sum.values())
late = sum(v for k, v in pos_sum.items() if k[0] >= 101)
print(f"  → 101 ターン目以降が main コンテキスト総量の {100*late/max(tot_pos,1):.1f}%")

ag = res_by_side["main"]["Agent"], cnt_by_side["main"]["Agent"]
print(f"\n=== 委譲の返却サイズ（digest が効いているか）===")
print(f"  Agent tool_result: {ag[0]/1e6:.2f}M 文字 / {ag[1]}回 = 平均 {ag[0]//max(ag[1],1):,} 文字")

# --- セッション長の軸（2026-08-26 追加）---------------------------------
# 上の「ターン位置別」は帯が粗く、傾きが出ないので線形か逓増かを判別できない。
# 「101 ターン目以降が 76.4%」だけでは超線形の証拠にならない（ターン数の分布次第で
# 長さが一定でも同じ数字が出る）。証拠になるのは傾きが正で一定であること。
print("\n=== 伸び方の傾き（線形なら累積は O(n^2)）===")
BANDS = [(1,25),(26,50),(51,75),(76,100),(101,150),(151,200),(201,300),(301,400),(401,600),(601,10**9)]
prev = None
for lo, hi in BANDS:
    vals = [s_[i] for s_ in main_seqs for i in range(lo-1, min(hi, len(s_)))]
    if not vals:
        continue
    avg = sum(vals)/len(vals)
    mid = (lo + min(hi, lo + (hi-lo)))/2 if hi < 10**9 else lo + 100
    slope = f"  傾き {(avg-prev[1])/(mid-prev[0]):>6.0f} トークン/ターン" if prev else ""
    print(f"  {lo:>4}-{hi if hi<10**9 else '∞':>4} : 平均 {avg/1000:>7.1f}k (n={len(vals):>5}){slope}")
    prev = (mid, avg)

print("\n=== セッション長 n 別の『1ターン単価』（同じ仕事が長いセッションでは何倍高いか）===")
for lo, hi in [(5,25),(26,50),(51,100),(101,200),(201,400),(401,10**9)]:
    grp = [s_ for s_ in main_seqs if lo <= len(s_) <= hi]
    if not grp:
        continue
    tot, turns = sum(sum(s_) for s_ in grp), sum(len(s_) for s_ in grp)
    print(f"  n={lo:>4}-{hi if hi<10**9 else '∞':<5} 本数 {len(grp):>3}  総量 {tot/1e6:>7.0f}M  "
          f"1ターン単価 {tot/turns/1000:>6.1f}k")

drops = sum(1 for s_ in main_seqs for a, b in zip(s_, s_[1:]) if b < a * 0.7 and a > 100_000)
print(f"\n=== 途中リセット（100k 超から 30%以上の縮小）= {drops} 箇所 / 全 {len(main_seqs)} 本 ===")
print("  ※ ほぼ発火しない＝1M コンテキストでは自動 compact に頼れず、切るのは判断依存")

# 反実仮想: 総ターン数を変えずに cap ターンで切り直していたら総量はどうなったか。
# ⚠️ 「切った後も実測の序盤カーブに従う」＝引き継ぎ Read で序盤が重くならない、という仮定つき。
curve = defaultdict(list)
for s_ in main_seqs:
    for i, v in enumerate(s_, 1):
        curve[i].append(v)
avg_at = {t: sum(v)/len(v) for t, v in curve.items() if len(v) >= 3}
if avg_at:
    actual = sum(sum(s_) for s_ in main_seqs)
    print("\n=== 反実仮想: 総ターン数を変えず N ターンで切っていたら ===")
    for cap in (60, 100, 150, 200):
        sim = sum(avg_at.get(i % cap + 1, avg_at[max(avg_at)]) for s_ in main_seqs for i in range(len(s_)))
        print(f"  {cap:>3} ターン刻み: 総量 {sim/1e6:>6.0f}M（実測 {actual/1e6:.0f}M の {100*sim/actual:.0f}%）")
