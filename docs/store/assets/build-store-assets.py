# -*- coding: utf-8 -*-
"""Google Play ストア掲載画像の書き出し（アイコン 512² / フィーチャーグラフィック 1024×500）。

裁定済みの意匠だけを書き出す:
  ・アイコン           = 案A「栞書影」  （`docs/design-candidates/store/store-icon-candidates.html` #ic-a）
  ・フィーチャーグラフィック = 案A「夜の帯」（同 store-feature-graphic-candidates.html #fgA）
裁定の出所＝`awaiting-human.md`「ストア掲載素材の作成」（2026-08-20）。

やっていること: 候補カタログと同じ数値・同じトークンで**書き出し専用の最小HTML**を起こし、
headless Chrome で実寸スクリーンショットを撮る。候補カタログ側の裁定用ガイド
（セーフエリアの破線・縮小プレビュー・見出し）は含めない。

⚠️ 意匠を変えるときは**候補カタログ側を直してからここへ写す**（モック先行＝`/visual-language`）。
    この2枚は「カタログの案Aを、原寸で、装飾なしに出したもの」以上の意味を持たせない。

使い方: python build-store-assets.py
"""
import os
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
]

# 明快K のトークン（候補カタログ :root と同一。ここだけで完結させる）
TOKENS = """
  --ai:#1C3D5A; --navy:#0E2030; --ink:#1C1F26; --paper:#FBFAF8;
  --seiji:#A9C2BB; --shu:#A1573F; --shu-lt:#CC8B73;
  --mincho:"Hiragino Mincho ProN","Yu Mincho","YuMincho","Noto Serif JP","MS PMincho",serif;
  --gothic:"Yu Gothic","Hiragino Kaku Gothic ProN","Noto Sans JP","Meiryo",sans-serif;
"""

# ── アイコン 案A「栞書影」 ────────────────────────────────────────────
# store-icon-candidates.html の <defs> gNight / fCard ＋ symbol#ic-a をそのまま展開したもの。
ICON_HTML = """<!DOCTYPE html><html lang="ja"><head><meta charset="utf-8"><style>
html,body{margin:0;padding:0;width:512px;height:512px;overflow:hidden;background:#0E2030}
svg{display:block;width:512px;height:512px}
</style></head><body>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="gNight" x1="0" y1="0" x2="0.35" y2="1">
      <stop offset="0" stop-color="#1C3D5A"/><stop offset="1" stop-color="#0E2030"/>
    </linearGradient>
    <filter id="fCard" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="10" stdDeviation="14" flood-color="#000" flood-opacity="0.34"/>
    </filter>
  </defs>
  <rect width="512" height="512" fill="url(#gNight)"/>
  <g filter="url(#fCard)">
    <rect x="128" y="86" width="256" height="341" rx="7" fill="#FBFAF8"/>
  </g>
  <g stroke="#1C1F26" stroke-opacity="0.13" stroke-width="4">
    <line x1="300" y1="118" x2="300" y2="395"/>
    <line x1="326" y1="118" x2="326" y2="395"/>
    <line x1="352" y1="118" x2="352" y2="395"/>
  </g>
  <rect x="194" y="86" width="27" height="157" fill="#A1573F"/>
  <circle cx="207.5" cy="263" r="21" fill="#A1573F"/>
</svg>
</body></html>
"""

# ── フィーチャーグラフィック 案A「夜の帯」 ────────────────────────────
FG_HTML = """<!DOCTYPE html><html lang="ja"><head><meta charset="utf-8"><style>
:root{%(tokens)s}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1024px;height:500px;overflow:hidden;background:#0E2030;-webkit-font-smoothing:antialiased}
.fg{position:relative;width:1024px;height:500px;overflow:hidden;font-family:var(--gothic)}
.cv{position:relative;background:var(--paper);border-radius:4px;box-shadow:0 8px 22px rgba(0,0,0,.34);overflow:hidden}
.cv .bar{position:absolute;top:0;left:26%%;width:5px;background:var(--band)}
.cv .bar::after{content:"";position:absolute;left:50%%;top:100%%;transform:translate(-50%%,-1px);
  width:13px;height:13px;border-radius:50%%;background:var(--band)}
.cv .vt{position:absolute;top:16px;right:13px;bottom:16px;max-width:64%%;overflow:hidden;
  writing-mode:vertical-rl;font-family:var(--mincho);font-size:14px;font-weight:600;
  color:var(--ink);line-height:1.65;letter-spacing:.06em}
.logo{font-family:var(--mincho);letter-spacing:.06em;line-height:1}
.logo .en{font-size:82px;font-weight:600;display:block}
.logo .ja{font-size:19px;letter-spacing:.5em;display:block;margin-top:16px;opacity:.82}
.copy{font-family:var(--mincho);font-size:27px;letter-spacing:.11em;line-height:1.6;margin-top:30px}
.sub2{font-family:var(--gothic);font-size:14.5px;letter-spacing:.09em;margin-top:16px;line-height:1.85}
.rule{width:64px;height:2px;margin-top:26px}
</style></head><body>
<div class="fg" style="background:linear-gradient(118deg,#24435F 0%%,#1C3D5A 42%%,#0E2030 100%%)">
  <div style="position:absolute;left:-60px;top:-120px;width:420px;height:420px;border-radius:50%%;
    background:radial-gradient(circle,rgba(143,179,212,.16) 0%%,rgba(143,179,212,0) 68%%)"></div>
  <div style="position:absolute;left:0;top:0;right:0;bottom:0;opacity:.09;
    background:repeating-linear-gradient(90deg,#FBFAF8 0 2px,transparent 2px 34px)"></div>

  <div style="position:absolute;left:160px;top:96px;color:#FBFAF8">
    <div class="logo"><span class="en">Yosari</span><span class="ja">よ さ り</span></div>
    <div class="rule" style="background:#CC8B73"></div>
    <div class="copy" style="color:#FBFAF8">縦書きで読む、Web小説。</div>
    <div class="sub2" style="color:#A9C2BB">ふりがなを保ったまま。広告なし・登録不要。</div>
  </div>

  <div style="position:absolute;left:706px;top:56px;transform:rotate(-7deg)">
    <div class="cv" style="width:150px;height:200px;--band:#A1573F">
      <div class="bar" style="height:46%%"></div><div class="vt">転生した先で本を読む</div>
    </div>
  </div>
  <div style="position:absolute;left:822px;top:150px;transform:rotate(4deg)">
    <div class="cv" style="width:162px;height:216px;--band:#1C3D5A">
      <div class="bar" style="height:52%%"></div><div class="vt">夜明けの塔にて</div>
    </div>
  </div>
  <div style="position:absolute;left:690px;top:276px;transform:rotate(3deg)">
    <div class="cv" style="width:140px;height:187px;--band:#50685C">
      <div class="bar" style="height:40%%"></div><div class="vt">辺境暮らしの記録</div>
    </div>
  </div>
</div>
</body></html>
""" % {"tokens": TOKENS}

JOBS = [
    ("_icon-512.html", "icon-512.png", 512, 512, ICON_HTML),
    ("_feature-graphic-1024x500.html", "feature-graphic-1024x500.png", 1024, 500, FG_HTML),
]


def find_chrome():
    for p in CHROME_CANDIDATES:
        if os.path.exists(p):
            return p
    p = shutil.which("chrome") or shutil.which("chrome.exe")
    if p:
        return p
    sys.exit("chrome.exe が見つからない。CHROME_CANDIDATES に実パスを足すこと。")


def png_size(path):
    """PIL 無しで PNG の実寸を読む（IHDR の 8 バイト）。"""
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def main():
    chrome = find_chrome()
    profile = tempfile.mkdtemp(prefix="yosari-shot-")
    ok = True
    try:
        for html_name, png_name, w, h, html in JOBS:
            html_path = os.path.join(HERE, html_name)
            png_path = os.path.join(HERE, png_name)
            with open(html_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(html)
            if os.path.exists(png_path):
                os.remove(png_path)
            cmd = [
                chrome,
                "--headless=new",
                "--disable-gpu",
                "--hide-scrollbars",
                "--force-device-scale-factor=1",
                "--default-background-color=00000000",
                "--user-data-dir=" + profile,
                "--window-size=%d,%d" % (w, h),
                "--screenshot=" + png_path,
                "file:///" + html_path.replace("\\", "/"),
            ]
            subprocess.run(cmd, capture_output=True, timeout=120)
            size = png_size(png_path) if os.path.exists(png_path) else None
            if size == (w, h):
                print("OK   %-32s %dx%d  %d bytes" % (png_name, w, h, os.path.getsize(png_path)))
            else:
                ok = False
                print("FAIL %-32s expected %dx%d got %r" % (png_name, w, h, size))
    finally:
        shutil.rmtree(profile, ignore_errors=True)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
