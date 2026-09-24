#!/usr/bin/env python3
"""走査(a): 同一 case の fontScale 1.0 に在った全幅罫線が 2.0 で減っていたら赤。

なぜこの検査が要るか（監査 2026-08-06 第1部 G-1）: golden は「退行」しか止めないため、初回記録の
時点で壊れていた絵はそのまま正解として焼き付く。実例＝目次の現在地バーが 2.0 で章一覧を押し出し、
区切り罫線が 1.0 の 7本 → 2.0 の 1本 まで消えた絵が 7枚固定されている。人間には「章が消えた」と
一目で分かるが、Roborazzi の verify は同名 golden との一致しか見ないので永久に緑になる。

なぜ「全幅罫線の本数」を指標にするか: 一覧（目次・設定・リスト）の行数はほぼ全て区切り罫線の本数と
一致し、罫線は「幅いっぱいに一定の明度差で走る 1〜数 px の帯」という機械的に堅い特徴を持つ。文字は
幅を占めないので cover 条件で自然に落ちる。「2.0 で行が減る」＝拡大したら一覧が消えた、という
レイアウト破綻の中核症状を、画面ごとの知識なしに 1 本の述語で捕まえられる。

限界（許容する理由つき）: 罫線を持たない画面（カード型・キャンバス直描き）は原理的に無反応で、
scale ペアの無い golden（テーマのみの縦書き系）は対象外。取りこぼしは走査(b)(c) と網羅テストが別軸で
補う。

**画素だけでは分けられない一群がある**（2026-08-06 の追加走査で実証・当初の「偽陽性は起きない」は誤り）:
スクロール可能な面では 2.0 で行が高くなり**可視行が減るのが正常**（設定K は 7本→2本 になるが本体 Column は
verticalScroll を持ち全項目に到達できる）。Roborazzi は最初のビューポート1枚しか撮らないため、
「押し出されて到達不能」と「畳の外へ流れただけ」は同じ絵になる。器の有無は実装にしか無い情報なので、
人が1件ずつ読んで判断した case だけを [golden_png.SCROLLING_SURFACES] で [参考] へ落とす（根拠字句が
実装から消えたら除外は自動失効＝赤へ戻る）。器が在っても高さを奪われて 0 になる形（目次K＝監査 G-1）は
除外に載せない＝赤のまま。

実行: python3 tools/check_golden_rule_loss.py [png-dir]   （減少を検出したら exit 1）
"""
import sys

import golden_png as gp

# 較正値（すべて現行 golden 104枚での実測に基づく）:
#  CONTRAST — 区切り罫線の実測明度差は 17（例: TocK_current_light_1.0 の全罫線）。アンチエイリアスで
#             薄まる分を見込んで 10 に置く。8 以下にするとカード影のグラデーションを拾い始める。
CONTRAST = 10
#  COVER — 罫線は左右の余白を除いて幅いっぱいに走る。実測は cover=1.00 だが、インセット付き区切り線
#          （左右 16dp 空け＝幅の 91%）も拾えるよう 0.80。0.6 まで下げると本文の行が混じる。
COVER = 0.80
#  MAX_THICKNESS — 1dp 罫線は xhdpi で 2px。太い帯（ヘッダ地・選択行の塗り）を罫線と数えないための上限。
MAX_THICKNESS = 6
#  PROBE — 罫線判定は「2px 上下の行との差」で見る。隣接行だとアンチエイリアスの中間色と比べてしまい
#          差が出ない（1px 罫線が実質 2px に滲むため）。
PROBE = 2


def full_width_rules(path):
    """全幅罫線の y 帯 [(y_start, y_end), ...]。

    2段構えで走査する: まず行平均（`sum(bytes)`＝C 実装）が上下から離れる行を候補に絞り、候補行だけ
    列ごとの被覆率を測る。全画素を Python で舐めると 104枚で分オーダーになるため。
    全幅罫線は全列に同じ差を与えるので、行平均の差は必ず CONTRAST 近くまで動く＝候補漏れは無い。
    """
    width, height, rows = gp.decode_rgba(path)
    # 行平均は緑チャネルで代表させる（罫線は無彩色寄りの明度差＝R/G/B が揃って動くため、
    # 3チャネル舐めても候補集合は変わらず、コストだけ 3 倍になる）。
    means = [sum(bytes(rows[y][1::4])) / width for y in range(height)]
    candidates = [
        y for y in range(PROBE, height - PROBE)
        if abs(means[y] - means[y - PROBE]) > CONTRAST * COVER * 0.5
        and abs(means[y] - means[y + PROBE]) > CONTRAST * COVER * 0.5
    ]
    rule_rows = []
    for y in candidates:
        up, me, dn = rows[y - PROBE], rows[y], rows[y + PROBE]
        gu, gm, gd = bytes(up[1::4]), bytes(me[1::4]), bytes(dn[1::4])
        covered = sum(
            1 for x in range(width)
            if abs(gm[x] - gu[x]) > CONTRAST and abs(gm[x] - gd[x]) > CONTRAST
        )
        if covered >= COVER * width:
            rule_rows.append(y)
    bands = []
    for y in rule_rows:
        if bands and y - bands[-1][1] <= 1:
            bands[-1][1] = y
        else:
            bands.append([y, y])
    return [(a, b) for a, b in bands if b - a + 1 <= MAX_THICKNESS]


def main(argv):
    png_dir = gp.resolve_dir(argv)
    pairs = gp.scale_pairs(png_dir)
    skipped = len(list(png_dir.glob("*.png"))) - sum(len(v) for v in pairs.values())
    failures = []
    exempted = []
    for case, files in pairs.items():
        small = full_width_rules(files["1.0"])
        large = full_width_rules(files["2.0"])
        if len(large) < len(small):
            exemption = gp.scroll_exemption(case)
            if exemption and exemption[0] == "ok":
                exempted.append((files["2.0"].name, len(small), len(large), exemption[1]))
            else:
                stale = f"  ※除外が失効: {exemption[1]}" if exemption else ""
                failures.append((case, small, large, files["2.0"].name, stale))
    print(f"走査(a) 全幅罫線の消失: {len(pairs)} 組を比較（scale ペアを持たない {skipped} 枚は対象外）")
    for case, small, large, name, stale in failures:
        print(f"  [赤] {name}: 1.0={len(small)}本 → 2.0={len(large)}本  （1.0 の y帯 {small}）{stale}")
    for name, before, after, reason in exempted:
        # 黙って捨てると「見たが正常と判断した」ことが消えるので、理由つきで必ず出す。
        print(f"  [参考] {name}: 1.0={before}本 → 2.0={after}本 ＝スクロール面の正常な縮退。{reason}")
    if failures:
        print(
            f"\n{len(failures)} 件で fontScale 2.0 の一覧行が減っている。"
            "実装（進捗行や見出しが weight/maxLines を持たず一覧の高さを奪う形）を直してから再記録すること。"
            "先に recordRoborazzi を打つと今の破綻が新しい正解として固定される"
            "（docs/knowledge/golden-record-bakes-in-regressions.md）。",
        )
        return 1
    print("  減少なし")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
