#!/usr/bin/env python3
"""main の Read を「委譲で消せるか」で二分する。

CLAUDE.md の委譲規約では〈編集起点ファイルの読み〉は委譲しない＝main が抱えるしかない。
逆に、読んだきり編集しなかった Read は調査であり、サブ/agy へ丸ごと出せば main の
コンテキストから消える。どちらが実効寄与を作っているかで打ち手が変わる。

判定: 同一セッション内で、その Read より後（同ターン含む）に同じ file_path への
Edit/Write/MultiEdit があれば「編集起点」、無ければ「調査」。
併せて offset/limit 付き（部分読み）の比率と、台帳ファイルの読み方も出す。
"""
import json, glob, os, sys, io
from collections import Counter

# --json の意図は measure_read_residency.py の同名ブロックを参照（表を parse させない）。
JSON_MODE = "--json" in sys.argv
_real_stdout = sys.stdout
if JSON_MODE:
    sys.stdout = io.StringIO()

# Windows 側ユーザー名の移行（qingj→naesimono, 2026-08-23）で project slug が分岐した。
# 過去の実測値は旧 slug 側にしか無いので、新旧どちらも走査する。
BASES = [
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-naesimono-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"),
    os.path.expanduser("~/.claude/projects/C--Users-qingj-Desktop-project-novel-reader-andloid"),
]
CH2TOK = 4.0
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}

files = sorted({
    os.path.realpath(p)
    for b in BASES
    for p in glob.glob(b + "/**/*.jsonl", recursive=True)
    if "/subagents/" not in p.replace("\\", "/")
})

eff_kind = Counter()      # 編集起点 / 調査
cnt_kind = Counter()
eff_partial = Counter()   # 全量 / 部分（offset|limit あり）
cnt_partial = Counter()
eff_ledger = Counter()    # 台帳(STATUS/handover/awaiting) / その他
cnt_ledger = Counter()
big_reads = []            # (実効寄与, 文字数, 種別, basename)

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
    reads = []            # (turn, path, size, partial)
    edited = {}           # path -> 最初に編集したターン
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
                        if isinstance(b, dict) and b.get("type") == "tool_use":
                            nm = b.get("name")
                            inp = b.get("input") or {}
                            name_by_id[b.get("id")] = nm
                            p = inp.get("file_path")
                            if p:
                                path_by_id[b.get("id")] = p
                                if nm in EDIT_TOOLS and p not in edited:
                                    edited[p] = t
                            if nm == "Read":
                                partial_by_id[b.get("id")] = bool(
                                    inp.get("offset") or inp.get("limit"))
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
                reads.append((t, path_by_id.get(b.get("tool_use_id"), "(不明)"),
                              size, partial_by_id.get(b.get("tool_use_id"), False)))

    for (rt, p, size, partial) in reads:
        e = (size / CH2TOK) * (turns - rt)
        kind = "編集起点" if p in edited and edited[p] >= rt else "調査（読んだきり）"
        eff_kind[kind] += e
        cnt_kind[kind] += 1
        pk = "部分読み(offset/limit)" if partial else "全量読み"
        eff_partial[pk] += e
        cnt_partial[pk] += 1
        base = os.path.basename(p)
        lk = "台帳(STATUS/handover/awaiting)" if base in (
            "STATUS.md", "handover.md", "awaiting-human.md") else "その他"
        eff_ledger[lk] += e
        cnt_ledger[lk] += 1
        big_reads.append((e, size, kind, base))

tot = sum(eff_kind.values())
print(f"=== main の Read 実効寄与 {tot/1e6:.0f}M の二分 ===")
for k, v in eff_kind.most_common():
    print(f"  {k:<22}{v/1e6:>7.0f}M  {100*v/tot:>5.1f}%   {cnt_kind[k]:>5}回")
print("\n=== 読み方 ===")
for k, v in eff_partial.most_common():
    print(f"  {k:<22}{v/1e6:>7.0f}M  {100*v/tot:>5.1f}%   {cnt_partial[k]:>5}回")
print("\n=== 対象 ===")
for k, v in eff_ledger.most_common():
    print(f"  {k:<30}{v/1e6:>7.0f}M  {100*v/tot:>5.1f}%   {cnt_ledger[k]:>5}回")

big_reads.sort(reverse=True)
print("\n=== 実効寄与の大きい単発 Read 上位10 ===")
for e, size, kind, base in big_reads[:10]:
    print(f"  {e/1e6:>6.2f}M  {size:>7,}文字  {kind:<18}{base}")

if JSON_MODE:
    sys.stdout = _real_stdout
    print(json.dumps({
        "read_total_eff": tot,
        "kind_pct": {k: 100 * v / tot for k, v in eff_kind.most_common()},
        "partial_pct": {k: 100 * v / tot for k, v in eff_partial.most_common()},
        "target_pct_of_read": {k: 100 * v / tot for k, v in eff_ledger.most_common()},
    }, ensure_ascii=False, indent=1))
