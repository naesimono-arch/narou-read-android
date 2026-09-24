#!/usr/bin/env python3
"""S3 fixture 生成の中身（起動は tools/build_s3_fixture.sh から。単体では使わない）。

sub-command:
  cut    ... 全文版の抽出結果から第 N 話のページ範囲を割り出し、ページ抜き PDF を書く（pypdf 必須）
  verify ... ページ抜き版の抽出が全文版と一致することを検証し、オラクル fixture を書く
"""
import json
import os
import re
import sys

RUBY = re.compile(r'\|([^《]+)《([^》]+)》')
# 前書き/後書きの見出しは PDF 生成器が話タイトルの末尾に付けるマーカー（ChapterProcessor と同じ判定形）。
FORE, AFTER = '（前書き）', '（後書き）'


def episode_spans(paras, emit_page, total_pages):
    """段落列から話ごとの (段落 index 範囲, ページ範囲) を割り出す。

    1 話は PDF 上で〈前書き見出し→前書き→本編見出し→本編→後書き見出し→後書き〉の順に並ぶ。
    見出しは必ずページ先頭で emit されるので、話の始まりは「前書き見出し（無ければ本編見出し）」の段落。
    """
    tit = [i for i, p in enumerate(paras) if p.startswith('【題名】')]

    def kind(i):
        t = paras[i][4:]
        return 'F' if t.endswith(FORE) else ('A' if t.endswith(AFTER) else 'T')

    starts = []
    for n, i in enumerate(tit):
        if kind(i) != 'T':
            continue
        prev = tit[n - 1] if n > 0 else None
        starts.append(prev if prev is not None and kind(prev) == 'F' else i)
    out = []
    for k, s in enumerate(starts):
        nxt = starts[k + 1] if k + 1 < len(starts) else None
        end = nxt - 1 if nxt is not None else len(paras) - 1
        pa = emit_page[s]
        pb = (emit_page[nxt] - 1) if nxt is not None else total_pages - 2
        out.append(dict(k=k + 1, pi=(s, end), pg=(pa, pb)))
    return out


def blank_runs(lines):
    """行列 → (空行を除いた本文 flow, [(flow 上の文字オフセット, 連続空行数)])。

    なぜ「段落 index」でなく「文字オフセット」で位置を表すか: 現行実装は行を段落へ結合し、
    第二実装は原文の行を保つ（段落結合の方針差＝裁定未了）。単位が違うので段落番号では突き合わせられないが、
    **文字は両者で保存されている**ので、文字オフセットなら実装非依存に比較できる。
    """
    flow, n, out, run = [], 0, [], 0
    for t in lines:
        if t == '':
            run += 1
            continue
        if run:
            out.append([n, run])
            run = 0
        flow.append(t)
        n += len(t)
    if run:
        out.append([n, run])
    return ''.join(flow), out


def oracle_lines(third, ep):
    f = os.path.join(third, 'episodes', '%04d.json' % ep)
    o = json.load(open(f))
    assert o['index'] == ep, f'話番号が合わない: {f}'
    lines = []
    for sec in ('foreword', 'body', 'afterword'):
        s = o.get(sec)
        if s:
            lines += [(l if isinstance(l, str) else l['t']) for l in s['lines']]
    return lines


def cmd_cut(work, ep, src, outdir):
    import pypdf
    from pypdf.generic import DecodedStreamObject, NameObject
    full = json.load(open(os.path.join(work, 'full.json')))
    span = [e for e in episode_spans(full['paras'], full['emitPage'], full['pages']) if e['k'] == ep]
    assert span, f'第 {ep} 話が見つからない'
    a, b = span[0]['pg']
    r = pypdf.PdfReader(src)
    w = pypdf.PdfWriter()
    mb = r.pages[a].mediabox

    def pad():
        # 捨てページの padding。なぜ「白紙」でなく最小の内容ストリーム（q Q）を入れるか＝
        # PDFBox は内容ストリームを持たないページを**走査対象から外す**ため、真の白紙を挟むと
        # loadPages が返すリストがページ数より短くなり、「先頭3ページ・最終ページを捨てる」の
        # index が本文ページへずれる（実測: 白紙 padding では本文 11p のうち先頭 3p が消えた）。
        p = w.add_blank_page(width=mb.width, height=mb.height)
        s = DecodedStreamObject()
        s.set_data(b'q Q\n')
        p[NameObject('/Contents')] = w._add_object(s)

    # 本文処理は先頭3ページ（表紙・注意事項）と最終ページ（クレジット）を捨てる設計なので、
    # その4枚は**中身のない padding** で埋める＝著作物のページを1枚も余分に持ち込まない。
    for _ in range(3):
        pad()
    for i in range(a, b + 1):
        w.add_page(r.pages[i])
    pad()
    out = os.path.join(outdir, 'N0833HI_ep%d.pdf' % ep)
    with open(out, 'wb') as f:
        w.write(f)
    json.dump(dict(ep=ep, pages=[a, b], pi=span[0]['pi']), open(os.path.join(work, 'span.json'), 'w'))
    print('  ページ %d..%d（%d ページ）を抜いた: %s (%d bytes)'
          % (a, b, b - a + 1, out, os.path.getsize(out)))


def cmd_verify(work, ep, third, outdir):
    full = json.load(open(os.path.join(work, 'full.json')))
    sub = json.load(open(os.path.join(work, 'sub.json')))
    span = json.load(open(os.path.join(work, 'span.json')))
    s, e = span['pi']
    sliced = full['paras'][s:e + 1]
    # ── 検証①: ページ抜き版の抽出＝全文版の当該話の抽出（段落列が完全一致）
    if sliced != sub['paras']:
        for i, (x, y) in enumerate(zip(sliced, sub['paras'])):
            if x != y:
                sys.exit('不一致: 段落 %d（全文版 %d 字 / 抜き版 %d 字）。fixture 化を中止する'
                         % (i, len(x), len(y)))
        sys.exit('不一致: 段落数（全文版 %d / 抜き版 %d）。fixture 化を中止する'
                 % (len(sliced), len(sub['paras'])))
    # ── 検証②: 検出パラメータ。rubyOffsetX だけはバケット内中央値で標本が違うと末尾桁が動くが、
    #    段落列が完全一致している＝出力に効いていないことが示せているので警告に留める。
    for k, v in full['rules'].items():
        if sub['rules'][k] != v and k != 'rubyOffsetX':
            sys.exit('検出パラメータ %s が全文版と違う（全文 %s / 抜き %s）' % (k, v, sub['rules'][k]))
    if sub['rules']['rubyOffsetX'] != full['rules']['rubyOffsetX']:
        print('  [注記] rubyOffsetX 全文=%r 抜き=%r（差は %.1e・段落列は完全一致＝出力に無影響）'
              % (full['rules']['rubyOffsetX'], sub['rules']['rubyOffsetX'],
                 abs(full['rules']['rubyOffsetX'] - sub['rules']['rubyOffsetX'])))

    ours = [RUBY.sub(lambda m: m.group(1), p) for p in sub['paras'] if not p.startswith('【題名】')]
    of, ob = blank_runs(ours)
    nf, nb = blank_runs(oracle_lines(third, ep))
    assert len(of) == len(nf), '文字数が合わない（%d/%d）＝S3 以前に字が落ちている' % (len(of), len(nf))
    so, sn = {tuple(x) for x in ob}, {tuple(x) for x in nb}
    missing = sorted(sn - so)

    # ── 既知の穴の真因確定: 欠けた空行が「ページの切れ目」に在るかを、行（列）単位の素データで裏取りする。
    bounds, n = set(), 0
    for pg in sub['pageLines']:
        bounds.add(n)
        for ent in pg:
            n += len(RUBY.sub(lambda m: m.group(1), ent['s']))
    at_boundary = all(o in bounds for o, _ in missing)

    json.dump({
        'ncode': 'N0833HI',
        'episode_index': ep,
        'fixture_pdf': 'N0833HI_ep%d.pdf' % ep,
        'source_pages': 'sample_pdfs/N0833HI.pdf の 0 始まり %d..%d' % tuple(span['pages']),
        'oracle_source': 'naro-pdf-engine (独立再実装) work/out/N0833HI/episodes/%04d.json — web 原文検証済み' % ep,
        's3_flow_char_count': len(nf),
        's3_blank_runs': nb,
        # 行構造そのもの（0＝空行）。ADR 0041 決定2 で抽出の出力単位が「原文の行」になったため、
        # 空行の位置だけでなく**行境界の位置まで**この小さい fixture で厳密に見張る
        # （長編は曖昧境界の影響で完全一致にならない＝帯で見るのが正しく、oracle.json 側の
        #  s3_line_count/s3_blank_count が担当する）。
        's3_line_lengths': [len(t) for t in oracle_lines(third, ep)],
    }, open(os.path.join(outdir, 'N0833HI_ep%d.oracle.json' % ep), 'w'),
        ensure_ascii=False, indent=1, sort_keys=True)

    json.dump({
        'why': 'ページ境界の空行は復元できない（列間 X の比較がページ内で閉じるため、'
               'ページ末尾とページ先頭の間隔は測れない）。全 66 話でも欠落 654 件は全件がページ境界で、'
               '誤検出・位置ずれは 0 件だった＝この表が塞がる/開くのは真因側の変化を意味する。',
        'missing_blank_runs': [list(x) for x in missing],
        'spurious_blank_runs': [list(x) for x in sorted(so - sn)],
        'all_missing_at_page_boundary': at_boundary,
    }, open(os.path.join(outdir, 'N0833HI_ep%d.s3_known_gaps.json' % ep), 'w'),
        ensure_ascii=False, indent=1, sort_keys=True)
    print('  一致 OK（段落 %d 本）。空行 実測 %d / オラクル %d・欠落 %d（全件ページ境界=%s）・誤検出 %d'
          % (len(sliced), sum(r for _, r in ob), sum(r for _, r in nb),
             len(missing), at_boundary, len(so - sn)))


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'cut':
        cmd_cut(sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5])
    elif cmd == 'verify':
        cmd_verify(sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5])
    else:
        sys.exit('unknown: %s' % cmd)
