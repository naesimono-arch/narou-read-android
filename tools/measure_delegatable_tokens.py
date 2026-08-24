#!/usr/bin/env python3
"""過去セッションから「agy へ逃がせた Claude 出力トークン」の上限を実測する。

前提（ユーザー指示 2026-08-09）: agy 側はゼロコストと見なす。よって節約効果＝
Claude 側の output_tokens がどれだけ消えるか、だけを測ればよい。

測り方: assistant ターンのうち Write/Edit/MultiEdit を含むもの＝「生成ターン」の
output_tokens を積む。これが委譲で消せる量の上限（実際に委譲できるのは break-even を
超える塊だけなので、閾値別にも出す）。

isSidechain でメイン/サブを分ける: サブが生成している分は既に司令塔のコンテキスト外だが
Claude トークンは同じ枠を食うので、三層化すればこちらも削減対象になる。
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
EDIT_TOOLS = {"Write", "Edit", "MultiEdit", "NotebookEdit"}

tot_out = {"main": 0, "sub": 0}
gen_out = {"main": 0, "sub": 0}
gen_turns = {"main": 0, "sub": 0}
tot_turns = {"main": 0, "sub": 0}
chars = {"Write": 0, "Edit": 0, "MultiEdit": 0, "NotebookEdit": 0}
tools = Counter()
# 生成ターンを「そのターンで書いた文字数」で層別（break-even の当たりを付ける）
buckets = {"<500": [0, 0], "500-2k": [0, 0], "2k-8k": [0, 0], ">=8k": [0, 0]}  # [ターン数, output_tokens]
cache_read = 0
# 「agy はゼロコスト」前提では、サブへ出した仕事を丸ごと agy へ移せる＝サブ側の全消費が
# 削減候補になる。output だけでなく cache_read も side 別に積む（コストの主体はこちら）。
cr = {"main": 0, "sub": 0}
ci = {"main": 0, "sub": 0}   # 通常 input
cw = {"main": 0, "sub": 0}   # cache 書き込み
# サブエージェントのログは <session-id>/subagents/*.jsonl に別置き＝再帰で拾う。
# main/sub の判別は isSidechain ではなくパスで行う（サブ側 jsonl には当該フラグが無い）。
files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
})
main_files = [p for p in files if "/subagents/" not in p]

for fp in files:
    with open(fp, encoding="utf-8", errors="replace") as f:
        for line in f:
            if '"assistant"' not in line:
                continue
            try:
                ev = json.loads(line)
            except Exception:
                continue
            if ev.get("type") != "assistant":
                continue
            msg = ev.get("message") or {}
            usage = msg.get("usage") or {}
            out = usage.get("output_tokens") or 0
            cache_read += usage.get("cache_read_input_tokens") or 0
            side = "sub" if "/subagents/" in fp else "main"
            cr[side] += usage.get("cache_read_input_tokens") or 0
            ci[side] += usage.get("input_tokens") or 0
            cw[side] += usage.get("cache_creation_input_tokens") or 0
            tot_out[side] += out
            tot_turns[side] += 1
            turn_chars = 0
            for b in msg.get("content") or []:
                if not isinstance(b, dict) or b.get("type") != "tool_use":
                    continue
                name = b.get("name")
                tools[name] += 1
                if name not in EDIT_TOOLS:
                    continue
                inp = b.get("input") or {}
                if name == "Write":
                    c = len(inp.get("content") or "")
                elif name == "MultiEdit":
                    c = sum(len(e.get("new_string") or "") for e in (inp.get("edits") or []))
                else:
                    c = len(inp.get("new_string") or inp.get("new_source") or "")
                chars[name] += c
                turn_chars += c
            if turn_chars:
                gen_out[side] += out
                gen_turns[side] += 1
                k = ("<500" if turn_chars < 500 else "500-2k" if turn_chars < 2000
                     else "2k-8k" if turn_chars < 8000 else ">=8k")
                buckets[k][0] += 1
                buckets[k][1] += out

M = 1_000_000
print(f"対象: メインセッション {len(main_files)} 本 / jsonl 総数 {len(files)}（サブ含む）")
print(f"\n=== Claude 出力トークン（実測・usage.output_tokens） ===")
for s in ("main", "sub"):
    print(f"  {s:4s}: 総 {tot_out[s]/M:8.2f}M  / 生成ターン {gen_out[s]/M:7.2f}M "
          f"({100*gen_out[s]/max(tot_out[s],1):5.1f}%)  ターン {gen_turns[s]}/{tot_turns[s]}")
t_all, g_all = sum(tot_out.values()), sum(gen_out.values())
print(f"  合計: 総 {t_all/M:.2f}M / 生成ターン {g_all/M:.2f}M ({100*g_all/max(t_all,1):.1f}%)")
print(f"  参考 cache_read 累計: {cache_read/M:.1f}M")

# 金額換算（prices.json のプレースホルダ単価。Opus 5 の実単価ではない＝相対比較にのみ使う）
P_IN, P_OUT, P_CR, P_CW = 5.0, 25.0, 5.0 * 0.10, 5.0 * 1.25
print(f"\n=== 消費の内訳（side 別・単位 M トークン / $ は prices.json のプレースホルダ換算） ===")
for s in ("main", "sub"):
    cost = (ci[s] * P_IN + tot_out[s] * P_OUT + cr[s] * P_CR + cw[s] * P_CW) / M
    print(f"  {s:4s}: in {ci[s]/M:7.1f}  out {tot_out[s]/M:6.2f}  cache_w {cw[s]/M:7.1f}  "
          f"cache_r {cr[s]/M:8.1f}   ≒ ${cost:8.2f}")
tot_cost = sum((ci[s] * P_IN + tot_out[s] * P_OUT + cr[s] * P_CR + cw[s] * P_CW) for s in ci) / M
sub_cost = (ci["sub"] * P_IN + tot_out["sub"] * P_OUT + cr["sub"] * P_CR + cw["sub"] * P_CW) / M
print(f"  総計 ≒ ${tot_cost:.2f}   うちサブ側 ${sub_cost:.2f} = {100*sub_cost/max(tot_cost,1e-9):.1f}%")
print(f"  → 「agy ゼロコスト」前提の最大削減＝サブ側を丸ごと移した場合の {100*sub_cost/max(tot_cost,1e-9):.1f}%")

print(f"\n=== 生成ターンの層別（そのターンで書いた文字数） ===")
for k, (n, o) in buckets.items():
    print(f"  {k:>7s}: {n:5d} ターン  output {o/M:6.2f}M  ({100*o/max(g_all,1):5.1f}% of 生成)")

print(f"\n=== 書き込み文字数の内訳 ===")
for k, v in chars.items():
    print(f"  {k:12s}: {v:10,d} 文字  ({tools[k]} 回)")
print(f"\n=== ツール呼び出し上位 ===")
for k, v in tools.most_common(12):
    print(f"  {k:20s}: {v}")
