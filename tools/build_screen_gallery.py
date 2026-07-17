#!/usr/bin/env python3
"""実装対象スキン×各画面モックの「大きめ（等倍）」ギャラリー HTML を生成する。

なぜ等倍・srcdoc 埋め込みか:
  ユーザー要望「それぞれを大きな画面で見たい」＝縮小タイルでなく等倍で縦に並べる。
  プレビューは mockview（単一ファイルコピー）経由が必須のため相対パス iframe は不可
  ＝各モックを HTML エスケープして srcdoc に埋め込み1ファイルで完結させる。
  iframe は操作可（pointer-events を殺さない）＝モック内の横並びフェーズもその場でスクロールできる。

使い方: python3 tools/build_screen_gallery.py [スキンID…]
  引数なし＝全スキン → _screens-gallery.html
  例 `M` ＝そのスキンのみ → _screens-M.html（1スキン=1ページで都度目視する運用・認知負荷対策）
"""

import html
import sys
from pathlib import Path

SKINSDIR = Path(__file__).resolve().parent.parent / "docs" / "design-candidates" / "skins"
CAND = SKINSDIR / "candidates"

# 収蔵済み（skins/直下）=M/P/J。保留（candidates/）=Q。ディレクトリはこの表で解決する
SKINS = [("M", "星図"), ("P", "カートリッジ"), ("Q", "読書の庭"), ("J", "ポータル・デッキ")]
SKIN_DIR = {"M": SKINSDIR, "P": SKINSDIR, "J": SKINSDIR, "Q": CAND}
SCREENS = [("bookshelf", "本棚"), ("reading", "読書"), ("discovery", "発見"), ("toc", "目次"), ("settings", "設定")]

CSS = """
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#141416;font-family:"Yu Gothic","Noto Sans JP",sans-serif;color:#E8E6E0;padding:24px 16px 80px}
  h1{font-size:19px;letter-spacing:.1em;margin-bottom:4px}
  .sub{font-size:12px;color:#9B98A0;margin-bottom:18px;line-height:1.7}
  nav{position:sticky;top:0;z-index:9;background:#141416ee;padding:10px 0;margin-bottom:20px;
    display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid #2E2D33}
  nav a{color:#C9C6D2;text-decoration:none;font-size:12px;padding:6px 12px;border:1px solid #3A3941;border-radius:999px}
  nav a:hover{background:#26252B}
  h2{font-size:16px;letter-spacing:.08em;margin:36px 0 6px;padding-top:12px}
  h2 small{color:#8B8894;font-weight:400;margin-left:10px;font-size:12px}
  .cell{margin:14px 0 34px}
  .cell .cap{font-size:12px;color:#A5A2AD;margin-bottom:8px;display:flex;gap:12px;align-items:baseline}
  .cell .cap b{color:#E8E6E0;font-size:13px}
  .cell .cap code{color:#7E7B86;font-size:11px}
  iframe{width:100%;height:990px;border:1px solid #2E2D33;border-radius:10px;background:#0E0E10}
"""


def build(only: list[str] | None = None) -> None:
    skins = [s for s in SKINS if not only or s[0] in only]
    out = CAND / (f"_screens-{'-'.join(only)}.html" if only else "_screens-gallery.html")
    parts = []
    nav = []
    for sid, sname in skins:
        nav.append(f'<a href="#skin-{sid}">{sid} {html.escape(sname)}</a>')
        parts.append(f'<h2 id="skin-{sid}">{sid}. {html.escape(sname)}<small>5画面（本棚→読書→発見→目次→設定）</small></h2>')
        for key, label in SCREENS:
            path = SKIN_DIR[sid] / f"{key}-{sid}.html"
            if not path.exists():
                parts.append(f'<div class="cell"><div class="cap"><b>{label}</b><code>{path.name}（未生成）</code></div></div>')
                continue
            content = path.read_text(encoding="utf-8")
            parts.append(
                f'<div class="cell"><div class="cap"><b>{sid} · {label}</b><code>{path.name}</code></div>'
                f'<iframe srcdoc="{html.escape(content, quote=True)}" title="{sid}-{key}"></iframe></div>'
            )

    doc = f"""<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<title>実装対象スキン 画面別ギャラリー（等倍）</title>
<style>{CSS}</style>
</head>
<body>
  <h1>実装対象スキン 画面別ギャラリー</h1>
  <p class="sub">M 星図・P カートリッジ・Q 読書の庭・J ポータル・デッキ × 本棚/読書/発見/目次/設定。
    等倍表示・各枠内はそのままスクロール可。生成: tools/build_screen_gallery.py</p>
  <nav>{"".join(nav)}</nav>
  {"".join(parts)}
</body>
</html>
"""
    out.write_text(doc, encoding="utf-8")
    print(f"OK: {out} ({out.stat().st_size:,} bytes)")


if __name__ == "__main__":
    build(sys.argv[1:] or None)
