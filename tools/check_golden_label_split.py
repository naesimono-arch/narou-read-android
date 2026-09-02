#!/usr/bin/env python3
"""走査(c): fontScale 2.0 でラベルが「1行1文字」の縦積みに崩れていたら赤。

なぜこの検査が要るか（監査 2026-08-06 第1部 G-5 / G-6 と KBottomNav の既知破綻）: 固定幅・maxLines 無しの
ラベルは拡大すると桁や文字が縦へ積まれ、「10」が「1」「0」に、「PDFを追加」が「P」「D」「F」…に割れる。
順位や CTA は**割れた絵のまま読めてしまう**ため semantics アサーションは通り、golden も初回記録で正解として
焼き付く＝どのゲートも赤くならない。

判定の署名（何を「割れ」と呼ぶか）:
  ①狭い（画面幅の [MAX_WIDTH_RATIO] 以下）インク塊が、②x 範囲を [MIN_X_OVERLAP] 以上共有し幅も高さも
  同程度の塊を真下に持ち（間隔は上の塊の高さの [MAX_V_GAP_RATIO] 倍以内＝1行分まで）、③**その行に
  自分しか居ない**（同じ y 帯の最近傍インクが字高の [ISOLATION_RATIO] 倍以上離れている）、
  ④**塊が1文字の形をしている**（幅が自身の高さの [MAX_ASPECT] 倍以下）、
  ⑤**[GRANULARITIES] のどの粒度で測っても 2.0 の本数が 1.0 を上回る**。
  ③が本検査の要＝1行1文字に潰れた列と、複数字が並ぶ**正常な折返し**を分ける唯一の軸。

なぜ ③ が要るのか（2026-08-06 の再較正・初版の署名は成立していなかった）: 初版は「縦の間隔が字送り相当
（字高の 0.55 倍以下）なら割れ」としていたが、実測でこの軸は**両群を分けない**——
  ・正常な CJK 折返し（さがすK の気分カード「今夜の一/気読み」）は 0.48 倍＝割れと判定される
  ・真の縦積み（本棚K 空棚の P/D、順位の 1/0）は 0.6〜0.94 倍＝**割れでないと判定される**
両群が逆転していた理由は、1行1文字では「字送り」がそのまま**行送り**（字高の 1 倍前後）になるのに対し、
CJK の折返しは字高が全角ベタ組みに近く行間が相対的に狭く見えるため。結果、初版の 17件の赤は
**1件も本物の縦積みではなく**（ContinuationCard は太字「8」の左右分割、目次K と順位は「台」「完」の
字内の横空き、設定K・気分カード・章見出しは CJK 折返し）、捕まえるはずだった G-5 の順位割れと
G-6 の P/D/F は逆に取り逃していた。③（行内孤立）と縦間隔の緩和を入れ替えると、40組の比較で
**本物2件（順位・空棚CTA）だけが赤・偽陽性ゼロ**になる（0.4 と 0.25 の間に明確な谷がある＝
折返しの隣字は字高の 0.02〜0.25 倍、真の縦積みの最近傍は 0.65〜1.3 倍）。

なぜ ④ が要るのか（2026-09-02 の追加・下の正規化が dp 固定要素で裏目に出る穴を塞ぐ）: まとめ幅を
fontScale 比で伸ばす下の正規化は「インクは全て sp 追従で拡大する」を前提にしている。ところが golden には
**dp 由来の寸法で描かれ 1.0 と 2.0 で画素が変わらないインク**が混ざる（作品詳細の栞書影カードの縦組み題字＝
2.0 の絵と 1.0 の絵で当該領域は実質同一画素）。この種のインクに 2.0 側だけ 2 倍のまとめ幅を当てると、
1.0 では別々だった**隣り合う縦列が横に融合して 1 つの塊になる**（実測＝列の間隔が 6px ちょうどで、
1.0 の gap=3 では割れ、2.0 の gap=6 でつながる）。融合塊は本物の隣を自分の中へ取り込んでしまうため
③の行内孤立を素通りし、真下の融合塊と対になって「2.0 で生えた連なり」に化ける。同じ PNG を
1.0 の粒度で走査すると 5、2.0 の粒度で走査すると 6 になる＝レンダリング差ではなく判定側の産物と確定した。
④は緩和ではなく**署名の明文化**＝融合塊は幅だけが 2 倍になり高さは変わらないので、「1行1文字」を
名乗る以上あり得ない縦横比になる。実測でも真の1文字は最大 1.22、融合塊は最小 2.20 と谷が空いている。
なぜ正規化そのものを止めないか: 粒度を 1.0 に固定して測り直すと corpus の赤は 6→12 件へ増える
（2.0 の sp 文字が細かく砕けて別の偽陽性が出る）＝下の正規化の理由は今も成立している。

なぜ ⑤ が要るのか（2026-09-02・④と同じ穴の一般解）: ④は融合が「横に広い塊」として現れた場合しか
落とせない。根にあるのは**1.0 の絵を粒度1.0 で、2.0 の絵を粒度2.0 で測った本数を突き合わせている**ことで、
dp 固定インクにはこの土俵合わせが不公平に働く（絵は同じなのに測り方だけ変わる）。そこで両方の粒度で
測り、**どちらでも増えている**ことを赤の条件にする。本物の 1行1文字は字の周りが大きく空くので粒度を
変えても連なりとして残る——実測でも是正前の実物2件（空棚 CTA・4桁話数ラベル）は両粒度で 0→1 / 0→3 と
赤のままで、既知の産物4件は緑へ落ちた。⚠️ **これで corpus の赤が減った分だけ感度が落ちていないことは
`test_check_golden_label_split.py` が真陽性 fixture で固定している**＝緩めるなら必ずそちらも通すこと。

なぜ塊の粒度をスケールで正規化するか: 塊のまとめ幅を絶対 px（3px）で固定すると、2.0 の絵だけが
細かく砕けて「1.0 より塊が増えた」が構造的に起きる（同じ UI を2倍で描いただけで字間も2倍になるため）。
比較の土俵を揃えるため、まとめ幅・最小画素・最小高さは fontScale 比で伸ばす。

なぜ 1.0 との差で見るか: 縦の連なり自体は等倍でも成立しうる（縦書きの書影題字＝D/K のグリッドは
1.0 で十数件ある）。「等倍では起きていなかった連なりが拡大で生えた」ことだけが fontScale 破綻の証拠。

限界（取りこぼしを黙って0件にしないため明記する）:
  ・**複数字が1行に残る折返し**は原理的に取れない（③で落とすため）。目次Kの現在地バーが「全4／話・／
    読了／率／25%」と5行へ膨らむ形＝監査 G-1 はこの署名では赤にならない。あれは走査(a)(b) が拾う。
  ・表示設定シートのスクロール欠落（G-2）もこの署名の対象外＝現状どの走査も機械では捕まえていない。
  ・**1.0 側に既に在る割れ**（監査 G-4＝4桁目次の「第1028」/「話」が等倍で2行に割れている）は、
    1.0 と 2.0 の差で見る以上どの閾値でも出ない。等倍の絵の正しさは別の軸（採寸テスト）で担保する。
  ・連なりが下端で切れて上段ごと消えている形（KBottomNav 2.0）は縦の連なりとして残らないので取れない。
  ・④は**幅の広い融合**しか落とせない。半角数字のように元から細い字が2つ融合すると縦横比が 1 前後に
    収まるので、dp 固定要素での偽陽性が原理的に消えるわけではない（現 corpus では発生していない）。
  そのため 1.0 側の連なりが 2.0 で**減った** case も参考として出す（＝拡大で塊ごと消えた兆候）。

実行: python3 tools/check_golden_label_split.py [png-dir]   （新規の割れを検出したら exit 1）
"""
import sys

import golden_png as gp

# 較正値（現行 golden 104枚＝40組での実測に基づく）:
INK_THRESHOLD = 32
#  H_GAP_BASE / MIN_PIXELS_BASE / MIN_HEIGHT_BASE — fontScale 1.0 での粒度。実際に使う値は fontScale 比で
#                  伸ばす（面積系は2乗）。3px は 1.0 の字間実測 2〜3px をつないで語をまとめる値。
H_GAP_BASE = 3
MIN_PIXELS_BASE = 20
MIN_HEIGHT_BASE = 8
#  MAX_WIDTH_RATIO — 画面幅に対する「狭い塊」の上限。0.10（720px なら 72px）。本文行を除外しつつ、
#                  順位セル 34dp・ボトムナビのラベル列・全角1文字（2.0 で約52px）を含む値。
MAX_WIDTH_RATIO = 0.10
#  MIN_X_OVERLAP / SIZE_RATIO — 同一 x 範囲・同程度の大きさ。同じ文字列が段に割れたのか、
#                  別要素がたまたま縦に並んだのかを分ける。
MIN_X_OVERLAP = 0.7
SIZE_RATIO = (0.7, 1.43)
#  MAX_V_GAP_RATIO — 1行1文字では字送り＝行送りになる。実測は空棚CTA の P/D が 1.03、順位の 1/0 が 0.94。
#                  1.3 に置く（1.6 まで広げても検出集合は変わらない＝谷の上に乗っている）。
MAX_V_GAP_RATIO = 1.3
#  ISOLATION_RATIO — 「その行に自分しか居ない」の閾。実測は折返しの隣字が字高の 0.02〜0.25 倍、
#                  真の縦積みの最近傍が 0.65〜1.3 倍。0.4 をその谷に置く（0.25 まで下げると
#                  D 書影の縦書き題字と字内の横空きを拾い始める）。
ISOLATION_RATIO = 0.4
#  MAX_ASPECT — 「1行1文字」を名乗る塊の幅の上限（自身の高さ比）。現行 golden の連なり構成塊 424 個の
#                  実測分布は 1.22 以下に 415 個・次が 1.80 で、間に谷がある。1.4 をその谷に置く
#                  （1.22＝D 書影の縦組み題字の実塊、2.20＝隣接2列が融合した偽の塊）。
#                  ⚠️ 上げると dp 固定要素の融合を拾い直し、下げると全角1文字を落とす。
MAX_ASPECT = 1.4
#  GRANULARITIES — 判定に使う粒度。1.0 と 2.0 の両方で増えていることを赤の条件にする（下の⑤）。
GRANULARITIES = (1.0, 2.0)


def _isolated(blob, all_blobs):
    """[blob] と同じ y 帯に、字高の [ISOLATION_RATIO] 倍より近い別のインクが無いか。

    x 範囲が重なる塊（枠線・下地・自分を含む親要素）は「隣の字」ではないので距離を測らない。
    """
    need = ISOLATION_RATIO * (blob[3] - blob[1] + 1)
    for other in all_blobs:
        if other is blob or other[3] < blob[1] or other[1] > blob[3]:
            continue
        if other[2] < blob[0]:
            distance = blob[0] - other[2] - 1
        elif other[0] > blob[2]:
            distance = other[0] - blob[2] - 1
        else:
            continue
        if distance < need:
            return False
    return True


def vertical_stacks(path, scale):
    """1行1文字へ崩れた連なり [(x1, y1, x2, y2), ...]（上の塊の左上と下の塊の右下）。

    [scale] は塊の粒度に使う比。塊の粒度をこの比で伸ばして 1.0 と 2.0 の土俵を揃える。
    """
    return stacks_in(ink_of(path), scale)


def ink_of(path):
    """PNG を1回だけ復号してインクマスク (width, height, mask) にする。

    同じ絵を複数の粒度で測るため（case_verdict）、復号とマスク化を粒度ループの外へ出してある。
    """
    width, height, rows = gp.decode_rgba(path)
    bg = gp.background_color(width, height, rows)
    return width, height, gp.ink_mask(width, height, rows, bg, INK_THRESHOLD)


def stacks_in(ink, scale):
    """[ink_of] の結果に対する [vertical_stacks] 本体。"""
    width, height, mask = ink
    all_blobs = gp.components(
        width, height, mask,
        gap=max(1, round(H_GAP_BASE * scale)),
        v_gap=0,  # 縦に接していない限りつなげない＝「段に割れた」事実を保つ（縦は正規化しない）。
        min_pixels=round(MIN_PIXELS_BASE * scale * scale),
    )
    # 連なりの構成要素になれるのは「1文字の形をした塊」だけ。all_blobs 側は絞らない＝融合塊も
    # インクとしては存在するので、③の行内孤立を測るときの隣人としては残す必要がある。
    blobs = [
        c for c in all_blobs
        if (c[2] - c[0] + 1) <= MAX_WIDTH_RATIO * width
        and (c[3] - c[1] + 1) >= round(MIN_HEIGHT_BASE * scale)
        and (c[2] - c[0] + 1) <= MAX_ASPECT * (c[3] - c[1] + 1)
    ]
    found = []
    for upper in blobs:
        ux1, uy1, ux2, uy2, _ = upper
        uw, uh = ux2 - ux1 + 1, uy2 - uy1 + 1
        for lower in blobs:
            if lower is upper or lower[1] <= uy1:
                continue
            lx1, ly1, lx2, ly2, _ = lower
            lw, lh = lx2 - lx1 + 1, ly2 - ly1 + 1
            overlap = min(ux2, lx2) - max(ux1, lx1) + 1
            if overlap < MIN_X_OVERLAP * min(uw, lw):
                continue
            if not (SIZE_RATIO[0] <= lw / uw <= SIZE_RATIO[1]):
                continue
            if not (SIZE_RATIO[0] <= lh / uh <= SIZE_RATIO[1]):
                continue
            spacing = ly1 - uy2 - 1
            if spacing < 0 or spacing > MAX_V_GAP_RATIO * uh:
                continue
            if not (_isolated(upper, all_blobs) and _isolated(lower, all_blobs)):
                continue  # 行に隣の字が居る＝正常な折返し。
            found.append((ux1, uy1, lx2, ly2))
            break  # 1つの塊につき直下の1件だけ数える（3段以上を件数で二重計上しない）
    return found


def case_verdict(files):
    """1組（fontScale 1.0/2.0 のペア）の判定 ("red"|"vanished"|"green", 1.0の本数, 2.0の連なり)。

    **判定の唯一の入口**＝CI が叩く main も回帰テスト（test_check_golden_label_split.py）もここを通す。
    テスト側で述語を組み直すと「テストは緑だが本番の判定は変わっている」が成立してしまうため。
    """
    ink_small, ink_large = ink_of(files["1.0"]), ink_of(files["2.0"])
    # 各粒度の内側で 1.0 と 2.0 を突き合わせる（粒度をまたいだ本数を比べない）。
    per_granularity = [
        (stacks_in(ink_small, g), stacks_in(ink_large, g)) for g in GRANULARITIES
    ]
    # 表示は従来どおり「各絵を自分の fontScale の粒度で測った本数」＝人が絵と照合しやすい読み。
    small = per_granularity[0][0]
    large = per_granularity[-1][1]
    if all(len(b) > len(a) for a, b in per_granularity):
        return "red", len(small), large
    if len(small) > len(large) and small:
        return "vanished", len(small), large
    return "green", len(small), large


def main(argv):
    png_dir = gp.resolve_dir(argv)
    pairs = gp.scale_pairs(png_dir)
    skipped = len(list(png_dir.glob("*.png"))) - sum(len(v) for v in pairs.values())
    failures = []
    vanished = []
    for case, files in pairs.items():
        verdict, small_n, large = case_verdict(files)
        if verdict == "red":
            failures.append((files["2.0"].name, small_n, large))
        elif verdict == "vanished":
            vanished.append((case, small_n, len(large)))
    print(f"走査(c) ラベルの段割れ: {len(pairs)} 組を比較（scale ペアを持たない {skipped} 枚は対象外）")
    for name, before, stacks in failures:
        boxes = ", ".join(f"({a},{b})-({c},{d})" for a, b, c, d in stacks[:3])
        more = f" ほか{len(stacks) - 3}件" if len(stacks) > 3 else ""
        print(f"  [赤] {name}: 縦の連なり 1.0={before} → 2.0={len(stacks)}  {boxes}{more}")
    for case, before, after in vanished:
        print(f"  [参考] {case}: 縦の連なりが 1.0={before} → 2.0={after} と減少（拡大で塊ごと消えた可能性）")
    if failures:
        print(
            f"\n{len(failures)} 件で fontScale 2.0 のラベルが1行1文字へ崩れている。"
            "幅固定・maxLines 無しの Text が疑わしい（器の幅を sp 追従にするか maxLines=1+softWrap=false へ）。"
            "実装を直してから再記録すること。",
        )
        return 1
    print("  新規の段割れなし")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
