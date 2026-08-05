#!/usr/bin/env python3
"""栞 高負荷アニメ正本モックの2層検証（/shiori-anim スキルの機械照合・自己申告 GREEN の代替）。
A) 装飾層 t0〜t8: keyframes名/プロパティ集合/周期/イージングの9種ペア全比較で重複ゼロ＋既製イージング禁止。
   ※bf0〜bf8（線追従層＝共通物理規則）と hl-*（旧参考語彙）は対象外＝モック冒頭コメントの規則どおり。
B) 線追従層 bf0〜bf8: 全9 tip に存在・周期が各 tip の主周期と一致・遅相 BF_LAG 配布・拡大/in-situ 両ステージ配線。
使い方: python3 verify_anim_layers.py [モックパス]（省略時は自己相対で正本を解決）
⚠️ tip 抽出は現状1桁（t\\d/bf\\d）前提＝9〜173 を作り込む拡大時は多桁対応へ改修が要る。"""
import os, re, sys, itertools

# 正本の既定パスは自己相対で解決（worktree/canonical どちらでも走る＝絶対パス焼き込み禁止）
_DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "..", "..", "..", "docs", "design-candidates", "bookshelf-shiori-highload-K.html")
PATH = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(_DEFAULT)
src = open(PATH, encoding="utf-8").read()

kf = {}
for m in re.finditer(r'@keyframes\s+([\w-]+)\s*\{', src):
    name, i = m.group(1), m.end()
    depth, j = 1, m.end()
    while depth and j < len(src):
        if src[j] == '{': depth += 1
        elif src[j] == '}': depth -= 1
        j += 1
    kf[name] = src[i:j-1]

deco_tip = lambda n: int(re.match(r't(\d)', n).group(1)) if re.match(r't\d', n) else None
bf_tip   = lambda n: int(re.match(r'bf(\d)$', n).group(1)) if re.match(r'bf\d$', n) else None

names = {t: set() for t in range(9)}
props = {t: set() for t in range(9)}
bf_props = {}
for n, body in kf.items():
    t = deco_tip(n)
    b = bf_tip(n)
    tgt = props[t] if t is not None else (bf_props.setdefault(b, set()) if b is not None else None)
    if t is not None: names[t].add(n)
    if tgt is None: continue  # hl-*（旧参考語彙）は両層の対象外
    for pm in re.finditer(r'([\w-]+)\s*:\s*([^;}]+)', body):
        p, v = pm.group(1), pm.group(2)
        if p == 'transform':
            tgt |= set(re.findall(r'(rotate|translateX|translateY|scaleX|scaleY|scale|skewX)\(', v))
        else:
            tgt.add(p)

durs = {t: set() for t in range(9)}
eases = {t: set() for t in range(9)}
bf_durs = {}
keyword_ease = []
for m in re.finditer(r'animation:([^;}]+)', src):
    for em in re.finditer(r'([\w-]+)\s+([\d.]+)s\s+(cubic-bezier\([^)]*\)|[a-z-]+)\s+infinite', m.group(1)):
        n, d, e = em.group(1), float(em.group(2)), em.group(3)
        t, b = deco_tip(n), bf_tip(n)
        if t is not None:
            durs[t].add(d); eases[t].add(e)
            if not e.startswith('cubic-bezier'): keyword_ease.append((n, e))
        elif b is not None:
            bf_durs.setdefault(b, set()).add(d)

for t in range(9):
    assert durs[t] and eases[t], f"tip{t} 装飾層の animation 宣言が抽出できていない（偽GREEN防止アサート）"

fails = []
pairs = list(itertools.combinations(range(9), 2))
for a, b in pairs:
    if names[a] & names[b]: fails.append(f"[A]keyframes共有 {a}-{b}: {names[a]&names[b]}")
    if props[a] == props[b]: fails.append(f"[A]プロパティ集合同一 {a}-{b}: {props[a]}")
    if durs[a] & durs[b]:   fails.append(f"[A]周期共有 {a}-{b}: {durs[a]&durs[b]}")
    if eases[a] & eases[b]: fails.append(f"[A]イージング共有 {a}-{b}: {eases[a]&eases[b]}")
if keyword_ease: fails.append(f"[A]既製イージング使用: {keyword_ease}")

# B) 線追従層: 全 tip に bf が存在し、周期が装飾層の周期のいずれかと一致・.flw 適用と BF_LAG も確認
lag = dict(re.findall(r'(\d):\s*\.(\d+)', re.search(r'BF_LAG=\{([^}]*)\}', src).group(1))) if re.search(r'BF_LAG=\{([^}]*)\}', src) else {}
insitu = set(int(x) for x in re.findall(r'class="flw bf-(\d)"', src))
stage_dyn = bool(re.search(r"flw bf-'\+f\.i", src))
print("B) 線追従層の適用一覧:")
for t in range(9):
    p = sorted(bf_props.get(t, []))
    d = sorted(bf_durs.get(t, []))
    ok_dur = bool(d) and d[0] in durs[t]
    kind = "最小追従(縦張力)" if p == ['scaleY'] else ("傾ぎ＋縦張力" if 'scaleY' in p else "傾ぎ(rotate)")
    print(f"  bf{t}: props={p} dur={d} tip周期と一致={ok_dur} 遅相={'.'+lag.get(str(t),'?')}s 種別={kind}")
    if not bf_props.get(t): fails.append(f"[B]bf{t} なし")
    if not ok_dur: fails.append(f"[B]bf{t} 周期不一致: {d} vs {sorted(durs[t])}")
    if t not in insitu: fails.append(f"[B]in-situ 棚に bf-{t} ラッパなし")
if not stage_dyn: fails.append("[B]拡大ステージ生成に bf-N ラッパなし")
if len(lag) != 9: fails.append(f"[B]BF_LAG が9件でない: {lag}")

ext = re.findall(r'(?:src|href)\s*=\s*["\']https?://|url\(\s*["\']?https?://', src)
if ext: fails.append(f"外部参照あり: {ext}")

print("\nA) 装飾層（照合対象）:")
for t in range(9):
    print(f"  tip{t}: props={sorted(props[t])} dur={sorted(durs[t])} ease={len(eases[t])}種")
print(f"\nペア全比較: {len(pairs)}ペア × 4軸")
if fails:
    print("NG:\n" + "\n".join(fails)); sys.exit(1)
print("OK: [A]装飾層は全36ペア重複ゼロ・既製イージングなし [B]線追従層は9 tip 全適用（周期一致・遅相配布・両ステージ）・外部参照なし")
