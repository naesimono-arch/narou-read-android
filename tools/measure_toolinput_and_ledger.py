#!/usr/bin/env python3
"""(1) tool_use 引数 289M のうち agy へ出せる「生成物」はどれだけか。
   (2) 台帳 118M の内訳（ファイル別・セッションあたり読み直し回数・全量/部分）。

agy が消せるのは Write/Edit の content/new_string（＝Claude が書いた本文）だけ。
Bash の command や Agent の prompt は委譲の指示そのもので、出しても main に残る。
そこを分けないと「agy でどれだけ減るか」を誤る。
"""
import json, glob, os
from collections import Counter, defaultdict

# Windows 側ユーザー名の移行（qingj→naesimono, 2026-08-23）で project slug が分岐した。
# 過去の実測値は旧 slug 側にしか無いので、新旧どちらも走査する。
BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
]
CH2TOK = 4.0
LEDGERS = ("STATUS.md", "handover.md", "awaiting-human.md")

files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
    if "/subagents/" not in p.replace("\\", "/")
})

eff_in = Counter()        # tool_use 引数を「中身の種類」別に
cnt_in = Counter()
eff_led = Counter()       # 台帳ファイル別 Read 実効寄与
cnt_led = Counter()
size_led = Counter()      # 台帳ファイル別 総文字数
partial_led = Counter()   # 台帳ファイル別 部分読み回数
per_sess_reads = defaultdict(list)   # 台帳: セッションごとの読み回数

for fp in files:
    turns = 0
    with open(fp, encoding="utf-8", errors="replace") as f:
        for line in f:
            if '"assistant"' in line:
                try:
                    if json.loads(line).get("type") == "assistant":
                        turns += 1
                except Exception:
                    pass
    if turns < 5:
        continue

    name_by_id, path_by_id, partial_by_id = {}, {}, {}
    reads = []
    sess_led = Counter()
    t = 0
    with open(fp, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                ev = json.loads(line)
            except Exception:
                continue
            msg = ev.get("message") or {}
            content = msg.get("content")
            if ev.get("type") == "assistant":
                t += 1
                rest = turns - t
                if not isinstance(content, list):
                    continue
                for b in content:
                    if not isinstance(b, dict) or b.get("type") != "tool_use":
                        continue
                    nm = b.get("name")
                    inp = b.get("input") or {}
                    name_by_id[b.get("id")] = nm
                    p = inp.get("file_path")
                    if p:
                        path_by_id[b.get("id")] = p
                    if nm == "Read":
                        partial_by_id[b.get("id")] = bool(inp.get("offset") or inp.get("limit"))
                    total = len(json.dumps(inp, ensure_ascii=False)) / CH2TOK
                    # 生成本文（agy へ出せる）とそれ以外を分離
                    body = 0
                    if nm in ("Write",):
                        body = len(inp.get("content") or "") / CH2TOK
                    elif nm in ("Edit",):
                        body = (len(inp.get("new_string") or "")
                                + len(inp.get("old_string") or "")) / CH2TOK
                    elif nm == "MultiEdit":
                        for e in (inp.get("edits") or []):
                            if isinstance(e, dict):
                                body += (len(e.get("new_string") or "")
                                         + len(e.get("old_string") or "")) / CH2TOK
                    if body:
                        eff_in[f"{nm} 本文（agy 射程）"] += body * rest
                        cnt_in[f"{nm} 本文（agy 射程）"] += 1
                        eff_in["(その他の引数)"] += max(total - body, 0) * rest
                    elif nm == "Bash":
                        eff_in["Bash command"] += total * rest
                        cnt_in["Bash command"] += 1
                    elif nm == "Agent":
                        eff_in["Agent prompt（委譲指示）"] += total * rest
                        cnt_in["Agent prompt（委譲指示）"] += 1
                    else:
                        eff_in["(その他の引数)"] += total * rest
                        cnt_in["(その他の引数)"] += 1
                continue
            if not isinstance(content, list):
                continue
            for b in content:
                if not isinstance(b, dict) or b.get("type") != "tool_result":
                    continue
                if name_by_id.get(b.get("tool_use_id")) != "Read":
                    continue
                c = b.get("content")
                size = (sum(len(x.get("text") or "") for x in c if isinstance(x, dict))
                        if isinstance(c, list) else len(c or ""))
                p = path_by_id.get(b.get("tool_use_id"), "")
                base = os.path.basename(p)
                if base in LEDGERS:
                    e = (size / CH2TOK) * (turns - t)
                    eff_led[base] += e
                    cnt_led[base] += 1
                    size_led[base] += size
                    if partial_by_id.get(b.get("tool_use_id")):
                        partial_led[base] += 1
                    sess_led[base] += 1
    for base, n in sess_led.items():
        per_sess_reads[base].append(n)

tot_in = sum(eff_in.values())
print(f"=== tool_use 引数 {tot_in/1e6:.0f}M の内訳（agy の射程はどこか）===")
for k, v in eff_in.most_common():
    print(f"  {k:<26}{v/1e6:>7.0f}M  {100*v/tot_in:>5.1f}%   {cnt_in[k]:>5}回")

print(f"\n=== 台帳 Read の内訳 ===")
print(f"{'ファイル':<20}{'実効寄与':>9}{'回数':>7}{'平均文字':>9}{'部分読み':>8}{'/セッション':>11}")
for base, v in eff_led.most_common():
    n = cnt_led[base]
    avg = size_led[base] // max(n, 1)
    ps = per_sess_reads[base]
    med = sorted(ps)[len(ps)//2] if ps else 0
    print(f"  {base:<18}{v/1e6:>8.0f}M{n:>7}{avg:>9,}{100*partial_led[base]/max(n,1):>7.0f}%"
          f"{med:>7}回(中央値・n={len(ps)})")
