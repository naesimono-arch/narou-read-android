#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""privacy-policy-draft.md の「公開本文」区間から privacy.html を起こす。

なぜ生成にするか: 公開する法的文書を md と html で二重管理すると、片方だけ直したときに
Data safety 申告との整合チェック（乖離はアプリ停止事由）が md 側しか見ない。
本文の正本を md 1 本に固定し、html は毎回捨てて作り直す。

使い方:
    python3 docs/store/pages/build.py          # privacy.html を再生成
    python3 docs/store/pages/build.py --check  # 再生成せず、既存 html が最新かだけ判定（差分あれば exit 1）

対応する Markdown は本文が実際に使っている記法だけ（見出し #/##・段落・- 箇条書き・表・**強調**・`code`）。
未対応の記法を本文へ足したときは、ここも足すか記法を避ける。黙って崩れないよう未知の行頭記号は例外にする。
"""
from __future__ import annotations

import argparse
import html
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE.parent / "privacy-policy-draft.md"
OUT = HERE / "privacy.html"

BEGIN = "▼公開本文▼"
END = "▲公開本文ここまで▲"

# 公開本文が使ってよい行頭記号。ここに無い記法（引用・番号付き・コードブロック等）は
# 変換されずに素通りする事故を避けるため、明示的に落として気づけるようにする。
ALLOWED_PREFIXES = ("#", "-", "|")


def extract_body(md: str) -> str:
    """▼〜▲ の間だけを取り出す。

    マーカーは**その行がマーカーだけの行**であることで判定する。
    md の前書きが「『▼公開本文▼』〜」と地の文でマーカー名に言及しており、
    素朴な部分一致だと前書き側を拾って本文を取り違えるため（実際に踏んだ）。
    """
    lines = md.split("\n")
    begins = [n for n, s in enumerate(lines) if s.strip() == BEGIN]
    ends = [n for n, s in enumerate(lines) if s.strip() == END]
    if len(begins) != 1 or len(ends) != 1 or begins[0] >= ends[0]:
        sys.exit(f"公開本文マーカー行が 1 対でない: {BEGIN}={begins} {END}={ends}")
    return "\n".join(lines[begins[0] + 1:ends[0]]).strip("\n")


def inline(text: str) -> str:
    """行内記法。エスケープしてから **強調** と `code` を戻す（順序を逆にすると属性を壊す）。"""
    s = html.escape(text, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    return s


def split_row(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def to_html(body: str) -> str:
    lines = body.split("\n")
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if not line:
            i += 1
            continue

        # 表: ヘッダ行 + 区切り行 + データ行
        if line.startswith("|") and i + 1 < len(lines) and re.fullmatch(r"\|[\s|:-]+\|", lines[i + 1].strip()):
            head = split_row(line)
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            out.append('<div class="tw"><table>')
            out.append("<thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr></thead>")
            out.append("<tbody>")
            for r in rows:
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            out.append("</tbody></table></div>")
            continue

        # 箇条書き
        if line.startswith("- "):
            out.append("<ul>")
            while i < len(lines) and lines[i].startswith("- "):
                out.append(f"<li>{inline(lines[i][2:])}</li>")
                i += 1
            out.append("</ul>")
            continue

        # 見出し
        m = re.match(r"^(#{1,3})\s+(.*)$", line)
        if m:
            lv = len(m.group(1))
            out.append(f"<h{lv}>{inline(m.group(2))}</h{lv}>")
            i += 1
            continue

        if line[0] in ALLOWED_PREFIXES:
            sys.exit(f"未対応の記法（{i + 1} 行目）: {line[:40]}")

        # 段落（連続する非空行は 1 段落にまとめ、行の区切りは <br> で保つ）
        # なぜ <br> か: 本文は 1 段落 1 行で書く運用で、改行が現れるのは
        # 「制定日: …／提供者: …」のように**別項目を並べている**ときだけ。
        # 素の連結にすると 2 項目が 1 行に潰れる（実際に潰れた）。
        buf = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and lines[i][0] not in ALLOWED_PREFIXES:
            buf.append(lines[i].rstrip())
            i += 1
        out.append("<p>" + "<br>".join(inline(b) for b in buf) + "</p>")

    return "\n".join(out)


TEMPLATE = """<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Yosari プライバシーポリシー</title>
<meta name="description" content="Android アプリ Yosari（夜さり）のプライバシーポリシー。提供者 Colophon。">
<meta name="robots" content="index, follow">
<link rel="stylesheet" href="style.css">
</head>
<body>
<main>
{body}
<footer><a href="index.html">Yosari</a> ／ 提供者 Colophon</footer>
</main>
</body>
</html>
"""


def render() -> str:
    return TEMPLATE.format(body=to_html(extract_body(SRC.read_text(encoding="utf-8"))))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="生成せず、既存 privacy.html が md と一致するかだけ見る")
    args = ap.parse_args()

    rendered = render()
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if current != rendered:
            print("privacy.html が privacy-policy-draft.md と食い違っている（build.py を実行して再生成する）")
            return 1
        print("privacy.html は最新")
        return 0

    OUT.write_text(rendered, encoding="utf-8")
    print(f"wrote {OUT} ({len(rendered)} bytes)")
    if "【公開日】" in rendered:
        print("注意: 【公開日】が未置換のまま。公開当日に置換すること（README 手順3）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
