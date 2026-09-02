#!/usr/bin/env python3
"""web 原文アンカー・オラクル fixture の生成器（系譜外オラクルの出所を機械で残すため）。

期待値の出所は現行実装でも旧 Python 実装でもなく、独立再実装 `~/naro-pdf-engine/`（第二実装）の出力。
第二実装はクリーンセッションで要件書のみから書かれ（アルゴリズム・閾値・期待値の受け渡しなし）、
その出力は ncode.syosetu.com の公開原文 10 話と突き合わせて 8/10 が完全一致・残り2話も
行境界のみの差で文字は保存、と検証済み（`~/naro-pdf-engine/verification.md` §3）。
＝現行 golden（旧 Python 実装の出力複製＝系譜内）とは独立した突合先になる。

⚠️ 生成の再実行には第二実装の成果物が要る（裁定待ち・`docs/backlog-frozen.md`）。
fixture 側は自己完結させてあるのでテスト実行には不要。
"""
import json, glob, os, re, sys

TH = os.path.expanduser('~/naro-pdf-engine/work/out')
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '../android/app/src/test/resources/pdf_oracle')
# git 追跡下の PDF だけを対象にする（他6本は .gitignore 済み＝CI で実行できない）。
DOCS = ['N1453LW', 'N2959KI', 'N5368ML', 'N6169DZ']
BOUTEN_MARKS = {'・', '﹅', '﹆', '●', '○'}


def merge(runs):
    """隣接ルビ run を1件へ畳む。分割粒度の差（S4-2）は裁定未了の方針差なので、
    親範囲(S4a)・傍点(S4b)の判定に混入させないため両側を同じ土俵へ載せる。"""
    out = []
    for st, base, rd in runs:
        if out and out[-1][0] + len(out[-1][1]) == st:
            p = out[-1]
            out[-1] = (p[0], p[1] + base, p[2] + rd)
        else:
            out.append((st, base, rd))
    return [(b, r) for _, b, r in out]


# S2b（字種写像）の監視対象コードポイント。**0 件のものも必ず載せる**＝「出るはずのない字が出た」も
# 「出るはずの字が消えた」も同じ表で赤くするため（片側だけだと撤去漏れ・過剰写像のどちらかを見逃す）。
# 6 系統＝波ダッシュ / 二重引用符 / 縦書き括弧 / ダッシュ / 矢印 / マイナス
# （`docs/knowledge/extraction-charmap-diverges-from-web-source.md` の表と 1:1）。
S2B_WATCH = [
    0xFF5E, 0x301C,                    # 波ダッシュ（PDF は両方を撃ち分けて保持している）
    0x301D, 0x301E, 0x301F,            # 二重引用符（開き 301D・閉じ 301F。301E は ToUnicode 逆引きの誤り）
    0xFF3B, 0xFF3D, 0xFE47, 0xFE48,    # 縦書き括弧（FE47/FE48 は縦書き表示形＝原文には現れない）
    0x2014, 0x2015, 0x0336,            # ダッシュ（0336 は結合長打消線＝独立字ですらない）
    0x2190, 0x2191, 0x2192, 0x2193,    # 矢印
    0xFF0D, 0x2212, 0x002D,            # マイナス（生成器が半角 - を全角 FF0D 化する。2212 は写像の産物）
    0x203E, 0xFFE3,                    # 上線（203E は ToUnicode 逆引きの誤り・原文は全角マクロン FFE3）
]


def s2b_counts(flow):
    c = {'U+%04X' % cp: 0 for cp in S2B_WATCH}
    watch = {chr(cp): 'U+%04X' % cp for cp in S2B_WATCH}
    for ch in flow:
        k = watch.get(ch)
        if k:
            c[k] += 1
    return c


def line_shape(lines):
    """行構造の要約（非空行数・空行数）。ADR 0041 決定2 で抽出の出力単位が「原文の行」になった検証用。

    ⚠️ 長編は行の完全一致では比較しない。列→行の復元には原理的に曖昧な境界が在り
    （`~/naro-pdf-engine/design.md` §1.3）、一致率は帯で見るのが正しい。位置まで見る厳密な比較は
    小さい ep57 fixture 側（`build_s3_fixture.py` の s3_line_lengths）が持つ。
    """
    return {
        's3_line_count': sum(1 for t in lines if t != ''),
        's3_blank_count': sum(1 for t in lines if t == ''),
    }


def read_doc(n):
    flow, runs, lines_all = [], [], []
    for f in sorted(glob.glob(f'{TH}/{n}/episodes/*.json')):
        d = json.load(open(f))
        for sec in ('foreword', 'body', 'afterword'):
            s = d.get(sec)
            if not s:
                continue
            for ln in s['lines']:
                t = ln if isinstance(ln, str) else ln['t']
                # 空文字の行だけを「空行」として落とす。⚠️ `not t.strip()` で落とすと
                # **半角スペースだけの行**まで消え、S1 の期待値が実際より小さくなる
                # （実測 N6169DZ で 1 個・N8809BK で 4 個ぶん過少になっていた）。
                lines_all.append(t)
                if t == '':
                    continue
                if not isinstance(ln, str):
                    runs += merge([(o, t[o:o + l], r) for o, l, r in ln.get('r', [])])
                flow.append(t)
    return ''.join(flow), runs, lines_all


def build(n):
    flow, runs, lines_all = read_doc(n)
    # S7 は半角スペース除去後の flow で見る＝S1（空白脱落）と赤の原因を分離するため。
    stripped = flow.replace(' ', '')
    apos = [stripped[max(0, i - 12):i + 12]
            for i, c in enumerate(stripped) if c == "'"]
    # S4a アンカー: 読みが文書内で一意な run だけを鍵にする（現行実装を一切参照せずに選ぶ＝
    # 「今ズレている所」だけを拾う選択バイアスを避ける）。
    seen = {}
    for b, r in runs:
        seen.setdefault(r, []).append(b)
    anchors = {r: bs[0] for r, bs in sorted(seen.items()) if len(bs) == 1}
    return {
        'ncode': n,
        'oracle_source': 'naro-pdf-engine (独立再実装) work/out/%s — web 原文検証済み' % n,
        's1_body_space_count': flow.count(' '),
        's2a_replacement_char_count': flow.count('�'),
        's2b_charmap_counts': s2b_counts(flow),
        **line_shape(lines_all),
        's4b_bouten_ruby_count': sum(1 for _, r in runs if r and set(r) <= BOUTEN_MARKS),
        's4a_ruby_base_anchors': anchors,
        's7_apostrophe_contexts': apos,
    }


if __name__ == '__main__':
    if not os.path.isdir(TH):
        sys.exit('第二実装の出力が無い: %s（裁定待ち資産。fixture 再生成にのみ必要）' % TH)
    for n in DOCS:
        o = build(n)
        p = os.path.join(OUT, f'{n}.oracle.json')
        with open(p, 'w') as f:
            json.dump(o, f, ensure_ascii=False, indent=1, sort_keys=True)
        print(f"{n}: space={o['s1_body_space_count']} lines={o['s3_line_count']} "
              f"blank={o['s3_blank_count']} fffd={o['s2a_replacement_char_count']} "
              f"bouten={o['s4b_bouten_ruby_count']} anchors={len(o['s4a_ruby_base_anchors'])} "
              f"apos={len(o['s7_apostrophe_contexts'])}  -> {os.path.getsize(p)//1024}KB")
