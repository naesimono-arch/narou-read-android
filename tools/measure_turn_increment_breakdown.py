#!/usr/bin/env python3
"""コスト最適化の打ち手を選ぶための分解。

累積 cache_read ≈ Σ_t ctx(t) = n·F + Σ_t Σ_{i<t} inc(i) ≈ **n·F + (n²/2)·avg_inc**
  F      = 常設注入（システム＋CLAUDE.md＋memory）＝切っても各ターンが必ず払う固定費
  inc(i) = 1ターンごとの増分（assistant 出力＋tool_use 引数＋tool_result＋user 入力）

打ち手は3つの項にしか当たらない:
  ① n を減らす（線形＋2次の両方）② F を減らす（線形のみ）③ inc を減らす（2次のみ）
  ④ セッションを切る＝n をリセットして 2次項を n²/m にする

どれがいくら効くかは F と inc の内訳で決まるので、それを実測する。
inc の中身は「そのターンの直前に何が積まれたか」＝assistant のテキスト・tool_use 引数・
tool_result・user メッセージに分解できる。文字→トークンの換算比も実測から逆算する。
"""
import json, glob, os
from collections import defaultdict

TARGETS = ["/mnt/c/Users/naesimono/.claude/projects/C--Users-naesimono-Desktop-project-nuru",
           os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
           os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid")]

def blocks(msg):
    """(text 文字数, tool_use 引数文字数, tool_result 文字数) を返す。"""
    t = tu = tr = 0
    c = msg.get("content")
    if isinstance(c, str):
        return len(c), 0, 0
    if not isinstance(c, list):
        return 0, 0, 0
    for b in c:
        if not isinstance(b, dict):
            continue
        ty = b.get("type")
        if ty == "text":
            t += len(b.get("text") or "")
        elif ty == "tool_use":
            tu += len(json.dumps(b.get("input") or {}, ensure_ascii=False))
        elif ty == "tool_result":
            x = b.get("content")
            tr += (sum(len(y.get("text") or "") for y in x if isinstance(y, dict))
                   if isinstance(x, list) else len(x or ""))
    return t, tu, tr

first_ctx, incs = [], []
acc = defaultdict(int)     # 種別 → 文字数
n_turns = 0
tot_ctx = 0

seen = set()
for base in TARGETS:
    for p in glob.glob(base + "/**/*.jsonl", recursive=True):
        rp = os.path.realpath(p)
        if rp in seen or "/subagents/" in rp:
            continue
        seen.add(rp)
        prev_ctx = None
        pend = defaultdict(int)     # 次の assistant ターンまでに積まれた文字
        turns_here = 0
        try:
            f = open(rp, encoding="utf-8", errors="replace")
        except OSError:
            continue
        with f:
            for line in f:
                if '"message"' not in line:
                    continue
                try:
                    ev = json.loads(line)
                except Exception:
                    continue
                msg = ev.get("message") or {}
                ty = ev.get("type")
                if ty == "user":
                    t, tu, tr = blocks(msg)
                    pend["tool_result"] += tr
                    pend["user 入力"] += t
                elif ty == "assistant":
                    u = msg.get("usage") or {}
                    ctx = ((u.get("input_tokens") or 0) + (u.get("cache_read_input_tokens") or 0)
                           + (u.get("cache_creation_input_tokens") or 0))
                    if ctx <= 0:
                        continue
                    turns_here += 1
                    n_turns += 1
                    tot_ctx += ctx
                    if turns_here == 1:
                        first_ctx.append(ctx)
                    elif prev_ctx is not None and ctx > prev_ctx:
                        incs.append(ctx - prev_ctx)
                        for k, v in pend.items():
                            acc[k] += v
                    prev_ctx = ctx
                    pend.clear()
                    t, tu, tr = blocks(msg)
                    pend["assistant 本文"] += t
                    pend["tool_use 引数"] += tu

first_ctx.sort()
F = first_ctx[len(first_ctx)//2]
avg_inc = sum(incs)/len(incs)
print(f"main {len(seen):,} ファイル / {n_turns:,} ターン / 総コンテキスト {tot_ctx/1e6:,.0f}M トークン\n")

print("=== 総額の分解 ===")
fixed = F * n_turns
print(f"  固定費 n·F      {fixed/1e6:>8,.0f}M  ({100*fixed/tot_ctx:>5.1f}%)   F(中央値) = {F/1000:.1f}k/ターン")
print(f"  可変（累積増分） {(tot_ctx-fixed)/1e6:>8,.0f}M  ({100*(tot_ctx-fixed)/tot_ctx:>5.1f}%)"
      f"   平均増分 = {avg_inc:,.0f} トークン/ターン")

print("\n=== 1ターン増分の中身（何が積まれているか）===")
tot_chars = sum(acc.values())
n_inc = len(incs)
ratio = sum(incs) / max(tot_chars, 1)
print(f"  実測の文字→トークン換算比 = {ratio:.3f}（増分トークン合計 ÷ 積まれた文字合計）")
print(f"  {'':<16}{'字/ターン':>10}{'占有':>8}{'トークン/ターン':>16}")
for k, v in sorted(acc.items(), key=lambda kv: -kv[1]):
    print(f"  {k:<16}{v/n_inc:>10,.0f}{100*v/tot_chars:>7.1f}%{v/n_inc*ratio:>15,.0f}")

print("\n=== 打ち手ごとの効き（この母数での試算）===")
print("  ※ 総額 ≈ n·F + (n²/2)·avg_inc。n は総ターン数、m は分割数。")
for label, f2, inc2, m in [
        ("① ターン数を 20% 減らす", 1.0, 1.0, 1.0),
        ("② 固定費 F を 30% 削る", 0.7, 1.0, 1.0),
        ("③ 増分 inc を 30% 削る", 1.0, 0.7, 1.0),
        ("④ 100 ターンで切る", 1.0, 1.0, None)]:
    if label.startswith("①"):
        # n を 0.8 倍: 固定費は 0.8 倍、可変は 0.64 倍
        v = fixed*0.8 + (tot_ctx-fixed)*0.64
    elif label.startswith("④"):
        v = None
    else:
        v = fixed*f2 + (tot_ctx-fixed)*inc2
    if v is not None:
        print(f"  {label:<24} → {v/1e6:>8,.0f}M（{100*v/tot_ctx:>5.1f}%・削減 {100-100*v/tot_ctx:>4.1f}pt）")
