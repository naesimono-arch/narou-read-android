#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
`adb exec-out screencap -p` の生 PNG を Play 受入形式へ整えて検算する。

なぜ変換が要るか: screencap の出力は **RGBA（PNG color type 6）** で、Play のスクリーンショット要件は
「JPEG または **24bit PNG（αなし）**」＝そのまま上げると弾かれる。ここで α を落として
color type 2（truecolor）へ焼き直し、ついでに寸法・辺比・色深度を機械で確認する。

Play の要件（2026-08-25 時点・出典 support.google.com/googleplay/android-developer/answer/9866151）:
  ・各辺 320〜3840px
  ・長辺 <= 短辺 * 2
  ・JPEG または 24bit PNG（α なし）
  ・スマホ 2〜8枚／タブレットは推奨

使い方:
    python3 finalize-screenshots.py            # このディレクトリの _raw/*.png を変換して検算
    python3 finalize-screenshots.py --check     # 変換せず、既存の *.png を検算するだけ
"""

import argparse
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "_raw")

MIN_SIDE, MAX_SIDE = 320, 3840


def png_info(path):
    """PNG のヘッダから (幅, 高さ, bit深度, color type) を読む。Pillow なしで検算できるようにする。"""
    with open(path, "rb") as f:
        sig = f.read(8)
        if sig != b"\x89PNG\r\n\x1a\n":
            return None
        f.read(4)                      # IHDR length
        if f.read(4) != b"IHDR":
            return None
        w, h, depth, ctype = struct.unpack(">IIBB", f.read(10))
    return w, h, depth, ctype


COLOR_TYPES = {0: "grayscale", 2: "truecolor(24bit)", 3: "indexed",
               4: "grayscale+alpha", 6: "truecolor+alpha(32bit)"}


def check(path):
    info = png_info(path)
    if info is None:
        return [f"{os.path.basename(path)}: PNG として読めない"], None
    w, h, depth, ctype = info
    problems = []
    long_side, short_side = max(w, h), min(w, h)
    if not (MIN_SIDE <= short_side and long_side <= MAX_SIDE):
        problems.append(f"辺が範囲外 ({w}x{h}・許容 {MIN_SIDE}〜{MAX_SIDE})")
    if long_side > short_side * 2:
        problems.append(f"長辺が短辺の2倍超 ({long_side}/{short_side}={long_side / short_side:.2f})")
    if ctype != 2:
        problems.append(f"24bit PNG でない (color type {ctype}={COLOR_TYPES.get(ctype, '?')})")
    if depth != 8:
        problems.append(f"bit深度が8でない ({depth})")
    return problems, (w, h, depth, ctype)


def convert(src, dst):
    from PIL import Image
    im = Image.open(src)
    # α を落として 24bit truecolor にする。透過部は Play 側で黒く出る事故があるので白で合成せず、
    # そもそも screencap の α は全面不透明＝RGB へ捨てるだけで絵は変わらない。
    if im.mode != "RGB":
        im = im.convert("RGB")
    im.save(dst, "PNG", optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="変換せず検算のみ")
    a = ap.parse_args()

    if not a.check:
        if not os.path.isdir(RAW):
            sys.exit(f"生スクショの置き場がない: {RAW}")
        for name in sorted(os.listdir(RAW)):
            if name.endswith(".png"):
                convert(os.path.join(RAW, name), os.path.join(HERE, name))
                print(f"  変換 {name}")

    targets = sorted(n for n in os.listdir(HERE)
                     if n.endswith(".png") and not n.startswith("_"))
    if not targets:
        sys.exit("検算対象の PNG が無い")

    ng = 0
    phone = tablet = 0
    print(f"\n{'ファイル':44s} {'寸法':>12s} {'比':>6s}  形式")
    for name in targets:
        problems, info = check(os.path.join(HERE, name))
        w, h, depth, ctype = info
        ratio = max(w, h) / min(w, h)
        mark = "OK " if not problems else "NG "
        print(f"{mark}{name:41s} {w}x{h:<6d} {ratio:5.2f}  {COLOR_TYPES.get(ctype)}")
        for p in problems:
            print(f"     - {p}")
            ng += 1
        if name.startswith("phone"):
            phone += 1
        elif name.startswith("tablet"):
            tablet += 1

    print(f"\nスマホ {phone}枚 / タブレット {tablet}枚")
    if not (2 <= phone <= 8):
        print(f"  ⚠️ スマホは 2〜8枚（現在 {phone}）")
        ng += 1
    if tablet and not (2 <= tablet <= 8):
        print(f"  ⚠️ タブレットも 2〜8枚（現在 {tablet}）")
        ng += 1
    print("判定:", "全て Play 要件を満たす" if ng == 0 else f"{ng} 件の不適合")
    return 1 if ng else 0


if __name__ == "__main__":
    sys.exit(main())
