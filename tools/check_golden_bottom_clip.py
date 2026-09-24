#!/usr/bin/env python3
"""走査(b): fontScale 2.0 でキャンバス最終行にインクが残る＝下端クリップなら赤。

なぜこの検査が要るか（監査 2026-08-06 第1部 G-2 / G-6）: 「拡大したら要素が画面の下から溢れて切れる」
は fontScale 破綻の典型で、しかも golden の絵では**切れた断面がそのまま正解として**焼き付く。実例＝
K の空棚 CTA が 1文字ずつ縦積みになりキャンバス最終行 y1279 まで続いて切断されている絵、および
設定画面が拡大で下端まで詰まった絵。verify は同名 golden との一致しか見ないので永久に緑になる。

なぜ「最終行のインク」で下端クリップと言えるか: レイアウトが健全なら最下部の要素の下には必ず余白
（または部品境界）が入る。最終行に地の色でない画素が残るのは「まだ続く絵が canvas の縁で断ち切られた」
ときに限られる。ただし**部品単体の撮影**（KBottomNav のように部品の高さ＝キャンバス高）は下端に
要素が接しているのが正常なので、単体の絶対値では判定できない。

したがって判定は必ず**同一 case の 1.0 との差**で行う: 「1.0 では下端に触れていなかったものが 2.0 で
触れる」＝拡大によって溢れた、と言い切れる。1.0 から既に接している部品（KBottomNav・D グリッドの
スクロール途中）は正常/別軸として除外され、その種の破綻は走査(c) が拾う。

**それでも分けられない一群がある**（2026-08-06 の追加走査で実証）: スクロール可能な面は 1.0 で全内容が
収まり 2.0 で溢れることが普通にあり、その溢れは**スクロールすれば届く**＝破綻ではない（設定K の
最終行インク 0px→32px は、畳の外へ出た「通知」見出しが縁で切れているだけ）。「溢れて到達不能」と
「溢れたが到達可能」を分ける情報は実装（スクロール器の有無と、その器に高さが渡っているか）にしか無い。
人が1件ずつ読んで判断した case だけを [golden_png.SCROLLING_SURFACES] で [参考] へ落とす。

実行: python3 tools/check_golden_bottom_clip.py [png-dir]   （新規の下端接触を検出したら exit 1）
"""
import sys

import golden_png as gp

# 較正値:
#  INK_THRESHOLD — 背景色からのチャネル最大差。実測で 32 は地の紙色のわずかなグラデーション
#                  （sepia の紙目）を拾わず、薄い罫線（差 17）と文字（差 100 超）を拾う。
INK_THRESHOLD = 32
#  BASELINE_PX / CLIPPED_PX — 1.0 側の許容インク量と、2.0 側で赤とみなす量。アンチエイリアスの
#                  裾や 1px の枠線が数画素残ることがあるため 0 では判定できない。720px 幅に対し
#                  「8px 以下＝縁の滲み」「9px 以上＝実体が切れている」を境にする（実測分布は
#                  0/6/8px と 18/32/79/302px に割れており、この間に自然な谷がある）。
BASELINE_PX = 8
CLIPPED_PX = 8


def bottom_row(path):
    """キャンバス最終行（と幅）。"""
    width, height, rows = gp.decode_rgba(path)
    return rows[height - 1], width


def bottom_row_ink(row, width, bg):
    """最終行のインク画素数。地の色 [bg] は呼び出し側が**1.0 側から1つ**決めて両方へ渡す。

    絵ごとに地を取り直すと、ダイアログ golden で scrim とダイアログ面が 1.0/2.0 で入れ替わり
    下端の量が比較不能になる（機序＝[gp.row_background] の docstring）。
    """
    return gp.ink_count(gp.ink_mask_row(row, width, bg, INK_THRESHOLD))


def main(argv):
    png_dir = gp.resolve_dir(argv)
    pairs = gp.scale_pairs(png_dir)
    skipped = len(list(png_dir.glob("*.png"))) - sum(len(v) for v in pairs.values())
    failures = []
    preexisting = []
    for case, files in pairs.items():
        row_small, width = bottom_row(files["1.0"])
        row_large, _ = bottom_row(files["2.0"])
        # 地は「1.0 の下端行の最頻色」＝版面が健全な側で下端に在る色。両スケールをこの1色で測る。
        bg = gp.row_background(row_small, width)
        small = bottom_row_ink(row_small, width, bg)
        large = bottom_row_ink(row_large, width, bg)
        if large > CLIPPED_PX and small <= BASELINE_PX:
            exemption = gp.scroll_exemption(case)
            if exemption and exemption[0] == "ok":
                preexisting.append((files["2.0"].name, small, large, f"スクロール面の折り返し地点＝正常。{exemption[1]}"))
            else:
                stale = f"  ※除外が失効: {exemption[1]}" if exemption else ""
                failures.append((files["2.0"].name, small, large, width, stale))
        elif large > CLIPPED_PX:
            # 1.0 から接している＝この検査の守備範囲外（部品境界か、等倍から既に切れている）。
            # 黙って捨てると「見たが判定不能だった」ことが消えるので、参考として必ず出す。
            preexisting.append((files["2.0"].name, small, large, "1.0 から下端に接触＝部品境界か等倍から破綻"))
    print(f"走査(b) 下端クリップ: {len(pairs)} 組を比較（scale ペアを持たない {skipped} 枚は対象外）")
    for name, small, large, width, stale in failures:
        print(f"  [赤] {name}: 最終行インク 1.0={small}px → 2.0={large}px / 幅{width}px{stale}")
    for name, small, large, note in preexisting:
        print(f"  [参考] {name}: 最終行インク 1.0={small}px / 2.0={large}px ＝{note}")
    if failures:
        print(
            f"\n{len(failures)} 件で fontScale 2.0 が下端で切れている。"
            "スクロール不在（verticalScroll を持たない Column）か、折返しで縦に伸びた要素が"
            "有界領域を超えている。実装を直してから再記録すること。",
        )
        return 1
    print("  新規の下端接触なし")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
