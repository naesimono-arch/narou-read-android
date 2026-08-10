#!/usr/bin/env python3
"""main の cache_read に「誰が何トークン居座らせたか」を実効値で測る。

素材の総量（tool_result の文字数）は cache_read への寄与を表さない——寄与は
  実効寄与 = トークン数 × そのターン以降に残るターン数
で決まる（cache_read = Σ 各ターンのコンテキスト長）。序盤の Read は末尾まで
何百回も読み直され、終盤の Read はほぼ効かない。ADR 0031 が assistant 出力に
対してやった計算を、ツール別（特に Read）へ広げる。

さらに Read はファイル別に「同一セッション内の重複読み」を出す——重複は委譲でなく
読み方の問題で、対策がまったく違うため。
"""
import json, glob, os
from collections import Counter, defaultdict

BASE = os.path.expanduser(
    "~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid")
CH2TOK = 4.0  # 文字→トークン概算（既存ツールと揃える）

files = [p for p in sorted(glob.glob(BASE + "/**/*.jsonl", recursive=True))
         if "/subagents/" not in p]

eff = Counter()          # カテゴリ別 実効寄与（トークン×残ターン）
raw = Counter()          # カテゴリ別 素の量（トークン）
eff_file = Counter()     # Read のファイル別 実効寄与
cnt_file = Counter()     # Read のファイル別 回数
dup_eff = 0              # 重複読み（同一セッション内2回目以降）の実効寄与
dup_cnt = 0
read_total_eff = 0
total_turns = 0

for fp in files:
    # 1パス目: ターン総数
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
    total_turns += turns

    name_by_id, path_by_id = {}, {}
    seen_paths = set()
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
                            n = len(b.get("text") or "") / CH2TOK
                            eff["(assistant 出力)"] += n * rest
                            raw["(assistant 出力)"] += n
                        elif b.get("type") == "thinking":
                            n = len(b.get("thinking") or "") / CH2TOK
                            eff["(thinking)"] += n * rest
                            raw["(thinking)"] += n
                        elif b.get("type") == "tool_use":
                            name_by_id[b.get("id")] = b.get("name")
                            fpth = (b.get("input") or {}).get("file_path")
                            if fpth:
                                path_by_id[b.get("id")] = fpth
                            n = len(json.dumps(b.get("input") or {}, ensure_ascii=False)) / CH2TOK
                            eff["(tool_use 引数)"] += n * rest
                            raw["(tool_use 引数)"] += n
                continue
            if not isinstance(content, list):
                continue
            rest = turns - t
            for b in content:
                if not isinstance(b, dict) or b.get("type") != "tool_result":
                    continue
                c = b.get("content")
                size = (sum(len(x.get("text") or "") for x in c if isinstance(x, dict))
                        if isinstance(c, list) else len(c or ""))
                n = size / CH2TOK
                nm = name_by_id.get(b.get("tool_use_id"), "(不明)")
                eff[nm] += n * rest
                raw[nm] += n
                if nm == "Read":
                    read_total_eff += n * rest
                    p = path_by_id.get(b.get("tool_use_id"), "(不明)")
                    eff_file[p] += n * rest
                    cnt_file[p] += 1
                    if p in seen_paths:
                        dup_eff += n * rest
                        dup_cnt += 1
                    seen_paths.add(p)

tot_eff = sum(eff.values())
print(f"=== main の cache_read 実効寄与（トークン×残ターン）: {tot_eff/1e6:.0f}M ===")
print(f"{'出所':<22}{'実効寄与':>10}{'占有':>8}{'素の量':>10}{'平均倍率':>9}")
for nm, v in eff.most_common(10):
    mult = v / max(raw[nm], 1)
    print(f"{nm:<22}{v/1e6:>9.0f}M{100*v/tot_eff:>7.1f}%{raw[nm]/1e6:>9.2f}M{mult:>8.0f}x")

print(f"\n=== Read の内訳（実効寄与 {read_total_eff/1e6:.0f}M・全体の {100*read_total_eff/tot_eff:.1f}%）===")
print(f"  同一セッションでの再読が {100*dup_eff/max(read_total_eff,1):.1f}%（{dup_cnt}回ぶん）")
print(f"\n  実効寄与の大きいファイル上位12:")
for p, v in eff_file.most_common(12):
    print(f"    {v/1e6:>7.1f}M  {cnt_file[p]:>4}回  {os.path.basename(p)}")
print(f"\n  （main 総ターン {total_turns:,}）")
