#!/usr/bin/env python3
"""コンテキスト肥大の内訳を実測する（cache_read が消費の主体だと分かったための追跡調査）。

cache_read = Σ(各ターンのコンテキスト長) なので、削るには「何がコンテキストに居座るか」を
知る必要がある。居座るものの正体はほぼ tool_result（ツールの出力本文）なので、
ツール別の総量・単発の巨大出力・セッション別の膨張を出す。

使い方: python3 tools/measure_context_bloat.py [上位N=15]
"""
import json, glob, os, sys
from collections import Counter

# Windows 側ユーザー名の移行（qingj→naesimono, 2026-08-23）で project slug が分岐した。
# 過去の実測値は旧 slug 側にしか無いので、新旧どちらも走査する。
BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
]
TOPN = int(sys.argv[1]) if len(sys.argv) > 1 else 15

files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
})
name_by_id = {}
res_chars, res_count = Counter(), Counter()
biggest = []          # (size, tool, file)
per_session = []      # (cache_read, turns, name) — main のみ

for fp in files:
    is_sub = "/subagents/" in fp
    cr = turns = 0
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
                turns += 1
                cr += (msg.get("usage") or {}).get("cache_read_input_tokens") or 0
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
                    if isinstance(c, list):
                        size = sum(len(x.get("text") or "") for x in c if isinstance(x, dict))
                    else:
                        size = len(c or "")
                    nm = name_by_id.get(b.get("tool_use_id"), "(不明)")
                    res_chars[nm] += size
                    res_count[nm] += 1
                    if size >= 50_000:
                        biggest.append((size, nm, os.path.basename(os.path.dirname(fp))[:8] if is_sub else os.path.basename(fp)[:8]))
    if not is_sub and turns:
        per_session.append((cr, turns, os.path.basename(fp)[:8]))

total = sum(res_chars.values())
print(f"=== tool_result の総量: {total/1e6:.1f}M 文字（≒ {total/2.5/1e6:.1f}M トークン相当）===")
print(f"{'ツール':<18}{'総文字数':>14}{'回数':>8}{'平均':>9}{'占有':>7}")
for nm, v in res_chars.most_common(TOPN):
    n = res_count[nm]
    print(f"{nm:<18}{v:>14,}{n:>8}{v//max(n,1):>9,}{100*v/max(total,1):>6.1f}%")

print(f"\n=== 単発 50k 文字超の tool_result: {len(biggest)} 件 ===")
for size, nm, sid in sorted(biggest, reverse=True)[:12]:
    print(f"  {size:>9,} 文字  {nm:<16} {sid}")

per_session.sort(reverse=True)
print(f"\n=== cache_read の大きいメインセッション上位10（ターン数と1ターン平均）===")
for cr, turns, sid in per_session[:10]:
    print(f"  {cr/1e6:>8.1f}M  {turns:>5} ターン  平均 {cr//max(turns,1)/1000:>6.1f}k/ターン  {sid}")
tot_cr = sum(c for c, _, _ in per_session)
tot_t = sum(t for _, t, _ in per_session)
print(f"  --- main 全体: {tot_cr/1e6:.1f}M / {tot_t} ターン = 平均 {tot_cr//max(tot_t,1)/1000:.1f}k/ターン")
print(f"  上位10が main cache_read の {100*sum(c for c,_,_ in per_session[:10])/max(tot_cr,1):.1f}% を占める")
