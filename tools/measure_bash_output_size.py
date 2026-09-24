#!/usr/bin/env python3
"""Bash/PowerShell の出力長の分布と、切り詰め閾値ごとの削減見込みを出す。

なぜ収録するか: 「閾値をいくつにするか」は勘ではなく分布で決める値で、後から再導出できないと
検証不能な数字が規約に居座る（応答様式ルールの「69%」が元計算を再現できず誤りと判明した前例
＝docs/knowledge/context-cost-breakdown-2026-08-10.md）。閾値を動かすときは必ずこれを回すこと。

数え方は measure_read_residency.py と同じ「実効寄与 = トークン数 × そのターン以降に残るターン数」。
素の量ではなく居座り時間込みで見ないと、序盤の長い出力の高さを取り逃す。
"""
import json, glob, os
from collections import Counter

# Windows 側ユーザー名の移行（qingj→naesimono, 2026-08-23）で project slug が分岐した。
# 過去の実測値は旧 slug 側にしか無いので、新旧どちらも走査する。
BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
]
CH2TOK = 4.0
TOOLS = ("Bash", "PowerShell")

# 同一実体へ解決される slug が混じるので realpath で畳む（畳まないと同じ jsonl を二重に数え、件数だけが倍化する）。
files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
    if "/subagents/" not in p.replace("\\", "/")
})

lens = []          # (文字数, 残ターン, コマンド先頭)
eff_total = 0.0    # 占有率の分母（全カテゴリの実効寄与）
bash_eff = 0.0
persisted_n = 0

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

    name_by_id, cmd_by_id = {}, {}
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
                if isinstance(content, list):
                    for b in content:
                        if not isinstance(b, dict):
                            continue
                        if b.get("type") == "text":
                            eff_total += len(b.get("text") or "") / CH2TOK * rest
                        elif b.get("type") == "thinking":
                            eff_total += len(b.get("thinking") or "") / CH2TOK * rest
                        elif b.get("type") == "tool_use":
                            name_by_id[b.get("id")] = b.get("name")
                            inp = b.get("input") or {}
                            if b.get("name") in TOOLS:
                                cmd_by_id[b.get("id")] = (inp.get("command") or "")[:120]
                            eff_total += len(json.dumps(inp, ensure_ascii=False)) / CH2TOK * rest
                continue
            if not isinstance(content, list):
                continue
            rest = turns - t
            tur = ev.get("toolUseResult")
            if isinstance(tur, dict) and tur.get("persistedOutputSize"):
                persisted_n += 1
            for b in content:
                if not isinstance(b, dict) or b.get("type") != "tool_result":
                    continue
                c = b.get("content")
                size = (sum(len(x.get("text") or "") for x in c if isinstance(x, dict))
                        if isinstance(c, list) else len(c or ""))
                eff_total += size / CH2TOK * rest
                if name_by_id.get(b.get("tool_use_id")) in TOOLS:
                    bash_eff += size / CH2TOK * rest
                    lens.append((size, rest, cmd_by_id.get(b.get("tool_use_id"), "")))

if not lens:
    raise SystemExit("対象セッションが見つからない（BASES のスラッグを確認）")

lens.sort(key=lambda x: x[0])
n = len(lens)
print(f"Bash/PowerShell の tool_result: {n:,} 件 / セッション {len(files)} 本")
print(f"実効寄与 {bash_eff/1e6:.0f}M（全体 {eff_total/1e6:.0f}M の {100*bash_eff/eff_total:.1f}%）")
# ハーネス自身が超巨大出力を退避する分（persistedOutputSize）。この帯はフックが触る必要がない。
print(f"ハーネスがオフロード済み: {persisted_n} 件\n")

print("=== 出力長パーセンタイル（文字）===")
for p in (50, 75, 90, 95, 97, 98, 99, 99.5, 100):
    print(f"  p{p:<6}{lens[min(n - 1, int(n * p / 100))][0]:>8,}")
print(f"  平均 {sum(x[0] for x in lens)/n:,.0f}\n")

print("=== 閾値ごと（介入率と削減見込み）===")
print(f"{'閾値':>8}{'該当':>7}{'介入率':>8}{'その帯の寄与':>13}{'削減される寄与':>15}")
for th in (1000, 2000, 3000, 4000, 5000, 6000, 8000, 10000, 15000, 20000):
    over = [x for x in lens if x[0] > th]
    if not over:
        continue
    o_eff = sum(x[0] / CH2TOK * x[1] for x in over)
    kept = sum((th + 200) / CH2TOK * x[1] for x in over)   # +200 は中略バナーぶん
    print(f"{th:>8,}{len(over):>7,}{100*len(over)/n:>7.1f}%"
          f"{100*o_eff/bash_eff:>12.1f}%{100*(o_eff-kept)/bash_eff:>14.1f}%")

print("\n=== 実効寄与の大きい出力 上位15（コマンド先頭）===")
for size, rest, cmd in sorted(lens, key=lambda x: -x[0] / CH2TOK * x[1])[:15]:
    print(f"  {size:>7,}字 x残{rest:>4}  {' '.join(cmd.split())[:84]}")
