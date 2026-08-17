#!/usr/bin/env python3
"""「調査 Read」を Read の実行時点で機械判定できるか——できないことを数字で示す。

背景: 調査 Read（読んだきりで編集しなかった Read）は Read 実効寄与の約 4 割を占め、
サブへ丸ごと出せば main のコンテキストから消える（内訳＝docs/knowledge/context-cost-breakdown-2026-08-10.md）。
そこで「フックで機械的に誘導できないか」が問われた。

しかし measure_read_kind.py の正解ラベルは **後ろ向き**（その Read より後に同じ path が編集されたか）で、
PreToolUse フックはその未来を見られない。本スクリプトは、決定時点で実際に見える特徴
（トップディレクトリ・拡張子・部分読みか・当該セッションで既に編集済みか）だけを使った
**最良の規則の上限**を出す。上限は同じデータに当てはめた楽観値（ホールドアウトではない）＝
実運用の精度はこれより必ず低い。それでも上限が低ければ「不可能」の証明として十分に効く。
"""
import json, glob, os
from collections import Counter, defaultdict

BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
    "/home/qingj/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid",
]
CH2TOK = 4.0
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}

# WSL から見ると BASES の 1 番目と 3 番目は同一ディレクトリに解決される。
# realpath で畳まないと同じ jsonl を二重に数え、件数だけが倍化する（比率は変わらないので気づきにくい）。
files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
    if "/subagents/" not in p.replace("\\", "/")
})

rows = []                              # (label, ext, topdir, partial, edited_before, eff)
path_labels = defaultdict(Counter)     # path -> {label: n}

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
    reads, edited = [], {}
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
                if isinstance(content, list):
                    for b in content:
                        if not isinstance(b, dict) or b.get("type") != "tool_use":
                            continue
                        nm, inp = b.get("name"), (b.get("input") or {})
                        name_by_id[b.get("id")] = nm
                        p = inp.get("file_path")
                        if p:
                            path_by_id[b.get("id")] = p
                            if nm in EDIT_TOOLS and p not in edited:
                                edited[p] = t
                        if nm == "Read":
                            partial_by_id[b.get("id")] = bool(inp.get("offset") or inp.get("limit"))
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
                reads.append((t, path_by_id.get(b.get("tool_use_id"), "?"), size,
                              partial_by_id.get(b.get("tool_use_id"), False)))

    for (rt, p, size, partial) in reads:
        eff = (size / CH2TOK) * (turns - rt)
        label = "edit" if (p in edited and edited[p] >= rt) else "probe"
        pn = p.replace("\\", "/")
        parts = [x for x in pn.split("/") if x]
        topdir = next((x for x in parts
                       if x in ("docs", "android", "tools", ".claude", "scripts")), "(other)")
        rows.append((label, os.path.splitext(pn)[1].lower() or "(none)", topdir,
                     partial, p in edited and edited[p] < rt, eff))
        path_labels[pn][label] += 1

n = len(rows)
if not n:
    raise SystemExit("対象セッションが見つからない（BASES のスラッグを確認）")
probe_n = sum(1 for r in rows if r[0] == "probe")
tot_eff = sum(r[5] for r in rows)
probe_eff = sum(r[5] for r in rows if r[0] == "probe")
print(f"Read {n:,} 件 / 調査 {probe_n:,} 件 ({100*probe_n/n:.1f}%) / "
      f"実効寄与ベースの調査 {100*probe_eff/tot_eff:.1f}%")
print(f"何も見ずに全部『調査』と決め打つ規則の正解率 = {100*probe_n/n:.1f}%（基準線）\n")

# パス自体が signal になっていないことの直接証拠。
both = [p for p, c in path_labels.items() if c["probe"] and c["edit"]]
amb = sum(sum(path_labels[p].values()) for p in both)
print("=== パスの曖昧さ ===")
print(f"  ユニークパス {len(path_labels):,} 本のうち、同じパスが調査と編集起点の両方で現れる: {len(both):,} 本")
print(f"  その両義パスが占める Read: {amb:,} 件 = 全体の {100*amb/n:.1f}%\n")


def purity(keyfn, title, top=12):
    g = defaultdict(Counter)
    for r in rows:
        g[keyfn(r)][r[0]] += 1
    print(f"=== {title} ===")
    for k, c in sorted(g.items(), key=lambda kv: -sum(kv[1].values()))[:top]:
        tot = sum(c.values())
        print(f"  {str(k):<36}{tot:>6}件  調査 {100*c['probe']/tot:>5.1f}%")
    # 各グループで多数派に賭けた場合＝この特徴で到達できる正解率の上限（楽観値）
    ub = sum(max(c["probe"], c["edit"]) for c in g.values())
    print(f"  → この特徴で到達できる正解率の上限: {100*ub/n:.1f}%\n")


purity(lambda r: r[1], "拡張子別")
purity(lambda r: r[2], "トップディレクトリ別")
purity(lambda r: (r[2], r[1], r[3], r[4]), "決定時点で見える全特徴の組合せ（dir×ext×部分読み×既編集）")
