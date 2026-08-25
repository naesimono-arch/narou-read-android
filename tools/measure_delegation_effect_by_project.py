#!/usr/bin/env python3
"""「Read すら委譲する」運用の効果を、プロジェクト間・時期別に比較する。

nuru は最近 Read まで委譲する運用へ振っている（2026-08-26 ユーザー申告）。novel-reader は
編集起点の Read を main が持つ運用。両者を並べれば自然実験になる。

効果の核心は **main のコンテキスト成長率**（1ターンあたり何トークン伸びるか）。
Read を外へ出せば main に居座る素材が減り、傾きが寝るはず——それが出ているかを見る。
セッション長で正規化するため、量は「100 ターンあたり」で出す。

⚠️ realpath 重複排除は必須（worktree slug と旧ユーザー名がどちらも symlink）。
⚠️ ここの「ターン」は assistant イベント数。/orchestration §0 の谷（tool_uses）とは別指標。
"""
import json, glob, os
from collections import defaultdict

TARGETS = {
    "nuru": ["/mnt/c/Users/naesimono/.claude/projects/C--Users-naesimono-Desktop-project-nuru"],
    "novel-reader": [
        os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
        os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
        "/mnt/c/Users/naesimono/.claude/projects/C--Users-naesimono-Desktop-novel-reader-andloid",
        "/mnt/c/Users/naesimono/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid",
    ],
}

def scan(path):
    """1 セッションを走査して (日付, ctx列, main側 tool_result 内訳, tool_use 回数) を返す。"""
    day, seq = None, []
    res = defaultdict(int)      # tool 名 → tool_result 文字数
    cnt = defaultdict(int)      # tool 名 → tool_result 件数
    uses = defaultdict(int)     # tool 名 → tool_use 件数
    names = {}
    try:
        f = open(path, encoding="utf-8", errors="replace")
    except OSError:
        return None
    with f:
        for line in f:
            if '"timestamp"' not in line and '"usage"' not in line and '"tool_' not in line:
                continue
            try:
                ev = json.loads(line)
            except Exception:
                continue
            if day is None and ev.get("timestamp"):
                day = ev["timestamp"][:10]
            msg = ev.get("message") or {}
            if ev.get("type") == "assistant":
                u = msg.get("usage") or {}
                c = ((u.get("input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)
                     + (u.get("cache_creation_input_tokens") or 0))
                if c > 0:
                    seq.append(c)
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            for b in content:
                if not isinstance(b, dict):
                    continue
                if b.get("type") == "tool_use":
                    names[b.get("id")] = b.get("name")
                    uses[b.get("name")] += 1
                elif b.get("type") == "tool_result":
                    c_ = b.get("content")
                    size = (sum(len(x.get("text") or "") for x in c_ if isinstance(x, dict))
                            if isinstance(c_, list) else len(c_ or ""))
                    nm = names.get(b.get("tool_use_id"), "(不明)")
                    res[nm] += size
                    cnt[nm] += 1
    return day, seq, res, cnt, uses

def week_of(day):
    import datetime
    d = datetime.date.fromisoformat(day)
    return (d - datetime.timedelta(days=d.weekday())).isoformat()

data = {}    # proj → {"main":[...], "sub_chars":int, "sub_n":int}
for proj, bases in TARGETS.items():
    seen, mains, sub_chars, sub_n = set(), [], 0, 0
    for b in bases:
        for p in glob.glob(b + "/**/*.jsonl", recursive=True):
            rp = os.path.realpath(p)
            if rp in seen:
                continue
            seen.add(rp)
            r = scan(rp)
            if not r:
                continue
            day, seq, res, cnt, uses = r
            if "/subagents/" in rp:
                sub_chars += sum(res.values()); sub_n += 1
                continue
            if len(seq) >= 20 and day:      # 20 ターン未満は傾きが安定しないので除外
                mains.append({"day": day, "seq": seq, "res": res, "cnt": cnt, "uses": uses})
    data[proj] = {"main": mains, "sub_chars": sub_chars, "sub_n": sub_n}

def agg(sessions):
    if not sessions:
        return None
    turns = sum(len(s["seq"]) for s in sessions)
    tot = sum(sum(s["seq"]) for s in sessions)
    rd_c = sum(s["cnt"].get("Read", 0) for s in sessions)
    rd_b = sum(s["res"].get("Read", 0) for s in sessions)
    ag_c = sum(s["uses"].get("Agent", 0) for s in sessions)
    bash_b = sum(s["res"].get("Bash", 0) for s in sessions)
    allres = sum(sum(s["res"].values()) for s in sessions)
    return dict(n=len(sessions), turns=turns, unit=tot/turns/1000,
                read_per100=100*rd_c/turns, read_chars_per_turn=rd_b/turns,
                agent_per100=100*ag_c/turns, read_share=100*rd_b/max(allres,1),
                bash_share=100*bash_b/max(allres,1))

H = f"  {'':<14}{'本':>4}{'ターン':>8}{'単価':>8}{'Read/100T':>11}{'Read字/T':>10}{'Agent/100T':>12}{'Read占':>8}"
print("=== プロジェクト全体（main セッション・20 ターン以上）===")
print(H)
for proj, d in data.items():
    a = agg(d["main"])
    if a:
        print(f"  {proj:<14}{a['n']:>4}{a['turns']:>8,}{a['unit']:>7.1f}k{a['read_per100']:>11.1f}"
              f"{a['read_chars_per_turn']:>10,.0f}{a['agent_per100']:>12.1f}{a['read_share']:>7.1f}%")
    main_chars = sum(sum(s["res"].values()) for s in d["main"])
    tot = main_chars + d["sub_chars"]
    print(f"    └ tool_result の main:sub = {100*main_chars/max(tot,1):.1f}% : {100*d['sub_chars']/max(tot,1):.1f}%"
          f"（sub {d['sub_n']} 本）")

print("\n=== ターン位置別の平均コンテキスト長（成長率の直接比較）===")
print(f"  {'':<14}" + "".join(f"{f'{lo}-{hi}':>12}" for lo, hi in [(1,25),(26,50),(51,100),(101,200),(201,400)]))
for proj, d in data.items():
    row = f"  {proj:<14}"
    for lo, hi in [(1,25),(26,50),(51,100),(101,200),(201,400)]:
        vals = [s["seq"][i] for s in d["main"] for i in range(lo-1, min(hi, len(s["seq"])))]
        row += f"{(sum(vals)/len(vals)/1000 if vals else 0):>11.1f}k"
    print(row)

print("\n=== 週別の推移（変化点を探す）===")
for proj, d in data.items():
    print(f"\n[{proj}]")
    print(H.replace("  ", "  ", 1))
    by = defaultdict(list)
    for s in d["main"]:
        by[week_of(s["day"])].append(s)
    for wk in sorted(by):
        a = agg(by[wk])
        print(f"  {wk:<14}{a['n']:>4}{a['turns']:>8,}{a['unit']:>7.1f}k{a['read_per100']:>11.1f}"
              f"{a['read_chars_per_turn']:>10,.0f}{a['agent_per100']:>12.1f}{a['read_share']:>7.1f}%")
