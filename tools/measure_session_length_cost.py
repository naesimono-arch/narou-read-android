#!/usr/bin/env python3
"""全ログ横断（Windows/WSL 両側・全プロジェクト）でセッション長とコストの関係を測る。

`measure_orchestration_leak.py` は novel-reader の特定 slug に固定されている（過去実測との
比較可能性を守るため）。こちらは母数を広げて別の問いに答える:

  1. セッション長の超線形性は novel-reader 固有か、プロジェクト非依存の構造か
  2. Windows セッションと WSL セッションで作法・単価が違うか
  3. main と sub の規模比（本数・ターン数）

⚠️ **ここの「ターン」は assistant イベント数**。/orchestration §0 の「谷 ~30」の較正値は
   **tool_uses（ツール呼び出し回数）**で、別の指標である。1 ターンに複数ツールを呼ぶぶん
   こちらが系統的に大きく出るので、**両者を突き合わせてはいけない**（2026-08-26 に実際に踏み、
   「谷を大きく超えている」という逆の結論を出しかけた）。子の走行長の較正は
   `tools/measure_subagent_run_length.py`（データ源＝count_delegation_turns.py の delegation-stats.jsonl）。

⚠️ worktree 用の slug（`-home-qingj-wt-*`）は canonical への symlink＝realpath で重複排除しないと
   同じログを何重にも数える（実際に踏んだ: 見かけ上4つの slug が同数 396 で並ぶ）。
⚠️ Windows 側の `C:\\Users\\qingj` も `naesimono` への symlink（2026-08-23 のユーザー名移行）。
"""
import json, glob, os, sys
from collections import defaultdict

BASES = [("LNX", os.path.expanduser("~/.claude/projects")),
         ("WIN", "/mnt/c/Users/naesimono/.claude/projects")]

def collect():
    """realpath で重複排除しつつ (side, project, is_sub, path) を返す。"""
    seen, out = set(), []
    for side, b in BASES:
        for p in glob.glob(b + "/**/*.jsonl", recursive=True):
            rp = os.path.realpath(p)
            if rp in seen:
                continue
            seen.add(rp)
            after = rp.split("/projects/")
            proj = after[1].split("/")[0] if len(after) > 1 else "?"
            out.append((side, proj, "/subagents/" in rp, rp))
    return out

def ctx_seq(path):
    """assistant ターンごとの総コンテキスト長（input + cache_read + cache_creation）。"""
    seq = []
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                if '"usage"' not in line:
                    continue
                try:
                    ev = json.loads(line)
                except Exception:
                    continue
                if ev.get("type") != "assistant":
                    continue
                u = (ev.get("message") or {}).get("usage") or {}
                c = ((u.get("input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)
                     + (u.get("cache_creation_input_tokens") or 0))
                if c > 0:
                    seq.append(c)
    except OSError:
        pass
    return seq

def pct(vals, q):
    if not vals:
        return 0
    v = sorted(vals)
    return v[min(len(v) - 1, int(len(v) * q))]

def unit_table(groups, title, min_sessions=3):
    """(ラベル → [seq,...]) を『1ターン単価』表にする。"""
    print(f"\n=== {title} ===")
    print(f"  {'':<34}{'本数':>5}{'ターン':>8}{'総量':>9}{'1ターン単価':>12}{'中央長':>9}")
    for label, seqs in sorted(groups.items(), key=lambda kv: -sum(len(s) for s in kv[1])):
        if len(seqs) < min_sessions:
            continue
        turns = sum(len(s) for s in seqs)
        tot = sum(sum(s) for s in seqs)
        print(f"  {label[:34]:<34}{len(seqs):>5}{turns:>8,}{tot/1e6:>8.0f}M"
              f"{tot/max(turns,1)/1000:>11.1f}k{pct([len(s) for s in seqs],0.5):>8}")

files = collect()
print(f"走査対象: {len(files):,} ファイル（realpath 重複排除後）")

main_by_side, main_by_proj, sub_lens, sub_by_side = defaultdict(list), defaultdict(list), [], defaultdict(list)
all_main = []
for side, proj, is_sub, rp in files:
    seq = ctx_seq(rp)
    if len(seq) < 5:
        continue
    if is_sub:
        sub_lens.append(len(seq))
        sub_by_side[side].append(seq)
    else:
        all_main.append(seq)
        main_by_side[side].append(seq)
        main_by_proj[proj].append(seq)

print(f"  → main {len(all_main):,} 本 / {sum(len(s) for s in all_main):,} ターン、"
      f"sub {len(sub_lens):,} 本 / {sum(sub_lens):,} ターン（5ターン未満は除外）")

# --- 1. 超線形性は構造か（セッション長 n 別の 1ターン単価）---
buckets = [(5, 25), (26, 50), (51, 100), (101, 200), (201, 400), (401, 10**9)]
grp = {}
for lo, hi in buckets:
    lbl = f"n={lo}-{hi if hi < 10**9 else '∞'}"
    grp[lbl] = [s for s in all_main if lo <= len(s) <= hi]
print("\n=== セッション長 n 別の『1ターン単価』（全プロジェクト横断）===")
for lo, hi in buckets:
    g = [s for s in all_main if lo <= len(s) <= hi]
    if not g:
        continue
    turns, tot = sum(len(s) for s in g), sum(sum(s) for s in g)
    print(f"  n={lo:>4}-{hi if hi<10**9 else '∞':<5} 本数 {len(g):>4}  総量 {tot/1e6:>7.0f}M  "
          f"1ターン単価 {tot/turns/1000:>6.1f}k")

# --- 2. 伸び方の傾き（線形なら累積は O(n^2)）---
print("\n=== 伸び方の傾き（正で一定なら線形＝累積 O(n^2)）===")
prev = None
for lo, hi in [(1,25),(26,50),(51,100),(101,200),(201,400),(401,800),(801,10**9)]:
    vals = [s[i] for s in all_main for i in range(lo-1, min(hi, len(s)))]
    if not vals:
        continue
    avg = sum(vals) / len(vals)
    mid = (lo + min(hi, lo + (hi - lo))) / 2 if hi < 10**9 else lo + 200
    slope = f"  傾き {(avg-prev[1])/(mid-prev[0]):>6.0f} トークン/ターン" if prev else ""
    print(f"  {lo:>4}-{hi if hi<10**9 else '∞':>4} : 平均 {avg/1000:>7.1f}k (n={len(vals):>6}){slope}")
    prev = (mid, avg)

# --- 3. OS 別・プロジェクト別 ---
unit_table({k: v for k, v in main_by_side.items()}, "OS 別（main セッション）")
unit_table({k: v for k, v in main_by_proj.items()}, "プロジェクト別（main・3本以上）")

# --- 4. サブの規模（⚠️ 谷 ~30 の較正には使えない。冒頭の注意を参照）---
if sub_lens:
    print(f"\n=== サブエージェントの assistant ターン数（規模の把握用）===")
    print(f"  n={len(sub_lens):,}  中央値 {pct(sub_lens,0.5)}  p75 {pct(sub_lens,0.75)}  "
          f"p90 {pct(sub_lens,0.90)}  max {max(sub_lens)}")
    print(f"  ⚠️ これは tool_uses ではない＝/orchestration §0 の谷 ~30 と比較しないこと")
    print(f"     （較正は tools/measure_subagent_run_length.py）")
    for side, seqs in sorted(sub_by_side.items()):
        L = [len(s) for s in seqs]
        print(f"  [{side}] n={len(L):,} 中央値 {pct(L,0.5)} p90 {pct(L,0.90)} max {max(L)}")

# --- 5. 反実仮想 ---
curve = defaultdict(list)
for s in all_main:
    for i, v in enumerate(s, 1):
        curve[i].append(v)
avg_at = {t: sum(v)/len(v) for t, v in curve.items() if len(v) >= 3}
if avg_at:
    actual = sum(sum(s) for s in all_main)
    print("\n=== 反実仮想: 総ターン数を変えず N ターンで切っていたら ===")
    print("  ⚠️ 『切った後も実測の序盤カーブに従う』＝引き継ぎ Read で序盤が重くならない、という仮定つき")
    for cap in (60, 100, 150, 200):
        sim = sum(avg_at.get(i % cap + 1, avg_at[max(avg_at)]) for s in all_main for i in range(len(s)))
        print(f"  {cap:>3} ターン刻み: 総量 {sim/1e6:>6.0f}M（実測 {actual/1e6:.0f}M の {100*sim/actual:.0f}%）")
