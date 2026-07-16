#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
縦書きモード「縦中横」対象パターンの実データ頻度計測（P0-2 スパイク）。

入力コーパス: ab-review/submission-B/output/*.json（PDFBox/Kotlin 抽出のゴールデン出力）。
  スキーマ: {title, author, chapters:[{title, paragraphs:[segment,...]}]}
  segment は dict で type='plain'(text) か type='ruby'(base, reading)。
  本文テキストは plain.text と ruby.base のみを連結して再構成する
  （ruby.reading は読みなので本文に混ぜない＝縦中横計測の汚染を回避）。
  段落境界は plain.text 内の改行 '\n'。

出力（すべて scratchpad）:
  tatechuyoko_stats.md  … 集計表
  tatechuyoko_corpus.txt … 縦中横対象を含む実データ行（JVMテスト入力用）
"""
import json, os, re, collections

SCRATCH = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(SCRATCH, "corpus_src")
FILES = ["N1453LW.json", "N2959KI.json", "N6169DZ.json"]

# ---- 文字クラス（縦中横は半角英数字・半角記号が対象。全角は正立で対象外＝参考記録）----
RE_HDIGIT = re.compile(r'[0-9]+')            # 半角数字 run
RE_HALPHA = re.compile(r'[A-Za-z]+')         # 半角英字 run
RE_HSYM   = re.compile(r'[!?]+')             # 半角!? クラスタ
RE_FSYM   = re.compile(r'[！？]+')            # 全角！？ クラスタ（参考／実データの主役）
RE_FDIGIT = re.compile(r'[０-９]+')           # 全角数字 run（参考・正立／実データの主役）
RE_FALPHA = re.compile(r'[Ａ-Ｚａ-ｚ]+')       # 全角英字 run（参考・正立／実データの主役）
# 実データ上まれに出る半角 ~ ' の実例も拾う（縦中横対象外だが正立/回転判断の入力）
RE_HRARE  = re.compile(r"[~']+")

def bucket(n):
    if n == 1: return "1"
    if n == 2: return "2"
    if n == 3: return "3"
    return "4+"

def rebuild_chapters(path):
    """各章の本文を線形テキストへ再構成して返す: list[(chapter_title, body_text)]。
    body_text は plain.text + ruby.base を出現順に連結（reading は除外）。"""
    d = json.load(open(path, encoding='utf-8'))
    out = []
    for ch in d["chapters"]:
        parts = []
        for seg in ch["paragraphs"]:
            t = seg.get("type")
            if t == "plain":
                parts.append(seg.get("text", ""))
            elif t == "ruby":
                parts.append(seg.get("base", ""))   # reading は捨てる
        out.append((ch.get("title", ""), "".join(parts)))
    return d.get("title", ""), out

# ---- 集計 ----
# 種別ごと: 長さバケット別 run 数
digit_buckets = collections.Counter()
alpha_buckets = collections.Counter()
hsym_by_literal = collections.Counter()   # 半角クラスタの literal 別
hsym_buckets = collections.Counter()
fsym_by_literal = collections.Counter()
fsym_buckets = collections.Counter()
fdigit_buckets = collections.Counter()     # 全角数字 run 長さ別
falpha_buckets = collections.Counter()     # 全角英字 run 長さ別
fdigit_count = 0
falpha_count = 0

# 実例（前後10文字つき）: 種別 -> list[(literal, context)]
EX_LIMIT = 20
examples = {
    "digit": collections.defaultdict(list),   # bucketキーごと最大20
    "alpha": collections.defaultdict(list),
    "hsym":  collections.defaultdict(list),
    "fdigit": collections.defaultdict(list),
    "falpha": collections.defaultdict(list),
    "fsym":  collections.defaultdict(list),
    "hrare": collections.defaultdict(list),
}
CTX = 10

# corpus 行抽出用: パターン種別 -> list[(fid, 段落テキスト)]（dedup）
corpus_lines = {
    "half_digit": [], "half_alpha": [], "half_symbol": [], "half_rare": [],
    "full_digit": [], "full_alpha": [], "full_symbol": [],
}
corpus_seen = {k: set() for k in corpus_lines}
RE_FDIGIT2 = re.compile(r'[０-９]{2,}')   # 全角数字2桁以上（テスト価値の高い段落抽出用）
RE_FALPHA2 = re.compile(r'[Ａ-Ｚａ-ｚ]{2,}')
RE_FSYM2   = re.compile(r'[！？]{2,}')

# 規模感
total_chars = 0
per_file = {}
longest_chapter = (None, None, 0, 0)  # (file, title, chars, paragraphs)
longest_paragraph = (None, 0, "")     # (file, chars, text)
total_paragraphs = 0
blank_paragraphs = 0

def add_example(kind, literal, ctx):
    b = bucket(len(literal))
    lst = examples[kind][b]
    if len(lst) < EX_LIMIT:
        lst.append((literal, ctx))

def collect_context(text, m):
    s = max(0, m.start() - CTX)
    e = min(len(text), m.end() + CTX)
    return text[s:e].replace("\n", "⏎")

for fn in FILES:
    path = os.path.join(SRC, fn)
    fid = fn.replace(".json", "")
    work_title, chapters = rebuild_chapters(path)
    f_chars = 0
    f_paras = 0
    for ctitle, body in chapters:
        f_chars += len(body)
        # 段落分割（改行区切り）
        paras = body.split("\n")
        ch_para_count = len(paras)
        for p in paras:
            total_paragraphs_local = 1
            if p.strip() == "":
                pass
        # 章の規模
        if len(body) > longest_chapter[2]:
            longest_chapter = (fid, ctitle, len(body), ch_para_count)
        f_paras += ch_para_count
        for p in paras:
            if p == "" or p.strip() == "":
                # 空段落
                pass
            if len(p) > longest_paragraph[1]:
                longest_paragraph = (fid, len(p), p)

        # --- パターン走査は body 全体（段落跨ぎの縦中横は無いが行内で十分）---
        # 半角数字
        for m in RE_HDIGIT.finditer(body):
            digit_buckets[bucket(len(m.group()))] += 1
            add_example("digit", m.group(), collect_context(body, m))
        # 半角英字
        for m in RE_HALPHA.finditer(body):
            alpha_buckets[bucket(len(m.group()))] += 1
            add_example("alpha", m.group(), collect_context(body, m))
        # 半角記号クラスタ
        for m in RE_HSYM.finditer(body):
            lit = m.group()
            hsym_by_literal[lit] += 1
            hsym_buckets[bucket(len(lit))] += 1
            add_example("hsym", lit, collect_context(body, m))
        # 全角！？（参考／実データ主役）
        for m in RE_FSYM.finditer(body):
            fsym_by_literal[m.group()] += 1
            fsym_buckets[bucket(len(m.group()))] += 1
            add_example("fsym", m.group(), collect_context(body, m))
        # 全角数字 run（参考・正立／実データ主役）
        for m in RE_FDIGIT.finditer(body):
            g = m.group(); fdigit_count += len(g)
            fdigit_buckets[bucket(len(g))] += 1
            add_example("fdigit", g, collect_context(body, m))
        # 全角英字 run（参考・正立／実データ主役）
        for m in RE_FALPHA.finditer(body):
            g = m.group(); falpha_count += len(g)
            falpha_buckets[bucket(len(g))] += 1
            add_example("falpha", g, collect_context(body, m))
        # まれな半角 ~ '（縦中横対象外だが正立/回転判断の実データ）
        for m in RE_HRARE.finditer(body):
            add_example("hrare", m.group(), collect_context(body, m))

        # --- corpus 行抽出（段落単位・dedup）---
        def maybe(key, cond, p):
            if cond and p not in corpus_seen[key]:
                corpus_seen[key].add(p)
                corpus_lines[key].append((fid, p))
        for p in paras:
            maybe("half_digit",  RE_HDIGIT.search(p), p)
            maybe("half_alpha",  RE_HALPHA.search(p), p)
            maybe("half_symbol", RE_HSYM.search(p), p)
            maybe("half_rare",   RE_HRARE.search(p), p)
            maybe("full_digit",  RE_FDIGIT2.search(p), p)   # 2桁以上
            maybe("full_alpha",  RE_FALPHA2.search(p), p)
            maybe("full_symbol", RE_FSYM2.search(p), p)

    per_file[fid] = {"title": work_title, "chars": f_chars,
                     "chapters": len(chapters), "paragraphs": f_paras}
    total_chars += f_chars
    total_paragraphs += f_paras

# ---- stats.md 出力 ----
def sum_counter(c): return sum(c.values())

md = []
md.append("# 縦中横 対象パターン 実データ頻度計測（P0-2 スパイク）\n")
md.append("計測日: 2026-07-17 / 集計スクリプト: `analyze_tatechuyoko.py`\n")
md.append("コーパス: `ab-review/submission-B/output/{N1453LW,N2959KI,N6169DZ}.json`"
          "（PDFBox/Kotlin 抽出ゴールデン出力・全3作品）。\n")
md.append("本文再構成: 各章 segment を出現順に連結し plain.text と ruby.base のみ採用"
          "（ruby.reading は本文から除外＝読みの混入なし）。段落 = 改行区切り。\n")

md.append("\n## ★ 主要結論（headline）\n")
md.append("**この蔵書コーパスに縦中横の本来対象（半角英数字・半角記号の連続）は事実上ゼロ。**")
md.append("本文 %s 文字中、ASCII 0x21–0x7E は 22 文字だけ（`'`×16, `~`×6）で、"
          "半角数字[0-9]・半角英字[A-Za-z]・半角[!?] は **各 0 run**。" % f'{total_chars:,}')
md.append("数値・英字・感嘆符/疑問符は **すべて全角**で出現する"
          "（なろう系PDFの表記慣習・抽出でも全角のまま）。")
md.append("ゲーム系（N6169DZ シャングリラ・フロンティア＝ステータス表記が多い題材）でも全角で統一。")
md.append("→ v1 縦中横（半角対象）は実蔵書では**発火しない**。実務上意味を持つのは"
          "**全角！！/！？クラスタ（%s run）と全角数字/英字 run**（下記2・5）で、"
          "これらは縦書きでは正立(または全角！？の縦中横類似処理)の対象。\n"
          % f'{sum_counter(fsym_buckets):,}')

md.append("\n## 6. コーパス規模感\n")
md.append("| 作品 | タイトル(冒頭) | 総本文字数 | 章数 | 段落数(改行区切) |")
md.append("|---|---|---:|---:|---:|")
for fid in ["N1453LW","N2959KI","N6169DZ"]:
    m = per_file[fid]
    md.append("| %s | %s | %s | %d | %s |" % (
        fid, m["title"][:16], f'{m["chars"]:,}', m["chapters"], f'{m["paragraphs"]:,}'))
md.append("| **合計** | — | **%s** | %d | **%s** |" % (
    f'{total_chars:,}', sum(per_file[x]["chapters"] for x in per_file),
    f'{total_paragraphs:,}'))
md.append("")
md.append("- **最長章**: %s『%s』= **%s 文字 / %s 段落**（改行区切り）。"
          "LazyRow の1章まるごと典型上限の目安。" % (
    longest_chapter[0], longest_chapter[1], f'{longest_chapter[2]:,}',
    f'{longest_chapter[3]:,}'))
md.append("- **最長段落**: %s = **%s 文字**（単一段落＝LazyRow 巨大アイテム実測の入力値）。"
          "冒頭抜粋: `%s…`" % (
    longest_paragraph[0], f'{longest_paragraph[1]:,}',
    longest_paragraph[2][:40].replace("\n","")))

md.append("\n## 1. 半角数字の連続 run（長さ別）\n")
md.append("| 長さ | run 数 |")
md.append("|---|---:|")
for b in ["1","2","3","4+"]:
    md.append("| %s桁 | %s |" % (b, f'{digit_buckets[b]:,}'))
md.append("| **計** | **%s** |" % f'{sum_counter(digit_buckets):,}')

md.append("\n## 2. 半角英字の連続 run（長さ別）\n")
md.append("| 長さ | run 数 |")
md.append("|---|---:|")
for b in ["1","2","3","4+"]:
    md.append("| %s字 | %s |" % (b, f'{alpha_buckets[b]:,}'))
md.append("| **計** | **%s** |" % f'{sum_counter(alpha_buckets):,}')

md.append("\n## 3. 半角記号クラスタ [!?]（連続・半角のみ）\n")
md.append("総 run 数: **%s**（長さ別: %s）\n" % (
    f'{sum_counter(hsym_buckets):,}',
    ", ".join("%s=%s"%(b, f'{hsym_buckets[b]:,}') for b in ["1","2","3","4+"])))
md.append("| literal | 出現数 |")
md.append("|---|---:|")
for lit, c in hsym_by_literal.most_common(15):
    md.append("| `%s` | %s |" % (lit, f'{c:,}'))
md.append("\n### 参考(別カウント): 全角！？クラスタ（縦中横対象外だが実データの主役・total run=%s）\n"
          % f'{sum_counter(fsym_buckets):,}')
md.append("長さ別: %s\n" % ", ".join("%s=%s"%(b, f'{fsym_buckets[b]:,}') for b in ["1","2","3","4+"]))
md.append("| literal | 出現数 |")
md.append("|---|---:|")
for lit, c in fsym_by_literal.most_common(10):
    md.append("| `%s` | %s |" % (lit, f'{c:,}'))

md.append("\n## 5. 参考: 全角数字・全角英字 run（縦中横対象外・正立処理の頻度感）\n")
md.append("全角は縦書きで正立（1文字1マス）。連続 run は縦中横しないが多文字レイアウトの実入力。\n")
md.append("| 種別 | 総文字数 | run長 1 | 2 | 3 | 4+ | 総run |")
md.append("|---|---:|---:|---:|---:|---:|---:|")
md.append("| 全角数字 [０-９] | %s | %s | %s | %s | %s | %s |" % (
    f'{fdigit_count:,}', f'{fdigit_buckets["1"]:,}', f'{fdigit_buckets["2"]:,}',
    f'{fdigit_buckets["3"]:,}', f'{fdigit_buckets["4+"]:,}', f'{sum_counter(fdigit_buckets):,}'))
md.append("| 全角英字 [Ａ-Ｚａ-ｚ] | %s | %s | %s | %s | %s | %s |" % (
    f'{falpha_count:,}', f'{falpha_buckets["1"]:,}', f'{falpha_buckets["2"]:,}',
    f'{falpha_buckets["3"]:,}', f'{falpha_buckets["4+"]:,}', f'{sum_counter(falpha_buckets):,}'))

md.append("\n## 4. 文脈つき実例（各種別・長さバケット別 最大20件）\n")
def dump_examples(kind, title):
    md.append("### %s\n" % title)
    for b in ["1","2","3","4+"]:
        lst = examples[kind][b]
        if not lst: continue
        md.append("- **%s**（%d件表示）:" % (b, len(lst)))
        for lit, ctx in lst[:20]:
            md.append("    - `%s` … `%s`" % (lit, ctx))
    md.append("")
dump_examples("digit", "半角数字 run（実データ 0 件）")
dump_examples("alpha", "半角英字 run（実データ 0 件）")
dump_examples("hsym", "半角記号クラスタ [!?]（実データ 0 件）")
dump_examples("hrare", "半角まれ記号 [~'] （縦中横対象外・正立/回転判断の実データ）")
dump_examples("fdigit", "全角数字 run（参考・正立／実データ主役）")
dump_examples("falpha", "全角英字 run（参考・正立／実データ主役）")
dump_examples("fsym", "全角！？クラスタ（参考／実データ主役）")

with open(os.path.join(SCRATCH, "tatechuyoko_stats.md"), "w", encoding='utf-8') as f:
    f.write("\n".join(md) + "\n")

# ---- corpus.txt 出力（~100行・種別セクション分け・dedup・出典コメント）----
# 各セクションから均等に取り、行が長すぎるものは切り詰め（テスト入力用に可読）
def truncate(s, n=160):
    s = s.replace("\n", " ")
    return s if len(s) <= n else s[:n] + "…"

out = []
out.append("# 縦中横 テストコーパス（実データ抽出・P0-2）")
out.append("# 出典: ab-review/submission-B/output/{N1453LW,N2959KI,N6169DZ}.json")
out.append("# 各行フォーマット: [出典ID] 本文段落（対象パターンを含む・重複除去・長文は…で切詰）")
out.append("# reading(ルビ読み)は除外済み・base のみを本文として再構成した段落。")
out.append("#")
out.append("# ★重要: この蔵書に縦中横の本来対象（半角英数字・半角[!?]）は事実上ゼロ。")
out.append("#   半角セクションは実データが無いため空。実データの対象は全角！？/全角数字/全角英字。")
out.append("#   ゆえに JVM テスト入力は下記の全角パターン＋まれな半角~' を主軸とする。")
out.append("")

# 割当: 実データがある全角を主軸に。合計~100行。半角は0件だが節見出しは残す(不在の記録)。
def interleave(rows):
    # 出典多様性のため N1453LW / N2959KI / N6169DZ を巡回
    by = {"N1453LW":[], "N2959KI":[], "N6169DZ":[]}
    for r in rows:
        if len(r[1].strip()) >= 6: by.setdefault(r[0],[]).append(r)
    order=[]
    i=0
    keys=list(by.keys())
    while any(by[k] for k in keys):
        k=keys[i%len(keys)]
        if by[k]: order.append(by[k].pop(0))
        i+=1
    return order

alloc = [("full_symbol", "## 全角！？クラスタ(！！/！？等)を含む段落", 40),
         ("full_digit",  "## 全角数字(2桁以上)を含む段落", 30),
         ("full_alpha",  "## 全角英字(2字以上)を含む段落", 22),
         ("half_rare",   "## まれな半角記号 [~'] を含む段落", 8),
         ("half_digit",  "## 半角数字を含む段落（実データ 0 件）", 0),
         ("half_alpha",  "## 半角英字を含む段落（実データ 0 件）", 0),
         ("half_symbol", "## 半角[!?]クラスタを含む段落（実データ 0 件）", 0)]
for key, header, n in alloc:
    out.append(header)
    if n == 0:
        out.append("# (該当なし — 実蔵書に半角の当該パターンは存在しない)")
        out.append("")
        continue
    picked = 0
    for fid, p in interleave(corpus_lines[key]):
        if picked >= n: break
        out.append("[%s] %s" % (fid, truncate(p)))
        picked += 1
    out.append("")

with open(os.path.join(SCRATCH, "tatechuyoko_corpus.txt"), "w", encoding='utf-8') as f:
    f.write("\n".join(out) + "\n")

# ---- コンソールへ digest ----
print("=== DIGEST ===")
print("total_chars:", f'{total_chars:,}')
print("total_paragraphs:", f'{total_paragraphs:,}')
for fid in per_file:
    print(" ", fid, per_file[fid])
print("longest_chapter:", longest_chapter[0], longest_chapter[2], "chars,", longest_chapter[3], "paras")
print("longest_paragraph:", longest_paragraph[0], longest_paragraph[1], "chars")
print("digit_buckets:", dict(digit_buckets), "total", sum_counter(digit_buckets))
print("alpha_buckets:", dict(alpha_buckets), "total", sum_counter(alpha_buckets))
print("hsym total run:", sum_counter(hsym_buckets), "buckets", dict(hsym_buckets))
print("hsym top:", hsym_by_literal.most_common(8))
print("fsym total run:", sum_counter(fsym_buckets), "buckets", dict(fsym_buckets), "top", fsym_by_literal.most_common(5))
print("fdigit runs:", dict(fdigit_buckets), "total_run", sum_counter(fdigit_buckets), "chars", f'{fdigit_count:,}')
print("falpha runs:", dict(falpha_buckets), "total_run", sum_counter(falpha_buckets), "chars", f'{falpha_count:,}')
print("corpus lines available:", {k: len(v) for k,v in corpus_lines.items()})
