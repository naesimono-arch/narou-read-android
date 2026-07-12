#!/usr/bin/env python3
"""モックCSS変数 ⇄ Compose トークンの一致検査（デザイン正本の drift 検出）。

なぜこの形か: 正本の層構造（ADR 0014 §A）で「値の正本＝theme/ のトークン」「姿の正本＝HTMLモック」
と宣言したが、両者は人力写経で同期しており drift が実測されていた（ヘアライン2値の無記録混在）。
tokens.json＋Style Dictionary の生成自動化は現段階では過剰（KB 03 §2・ADR 0014 Alternatives）のため、
「宣言の突合」だけを機械化する現実解を採る。

検査の意味論:
  - 各期待は「ファイル内に `--var:#HEX` の宣言が（テーマスコープ問わず）存在するか」で判定する。
    テーマスコープ（.tl/.ts 等）の CSS を構文解析せず宣言の存在で見る＝モック側でライト値を
    変えれば宣言が消えて検知でき、セピア宣言が偽陽性にならない、の両立。
  - reading-D のみ 3 テーマ（.t-light/.t-sepia/.t-dark の順に宣言が並ぶ）を順序前提で全数照合する。
  - 変数がファイルに無い場合は SKIP（モック改版でレイアウトごと消えた場合はこの表を保守する）。

実行: python3 tools/check_design_tokens.py   （リポジトリルートから。不一致で exit 1）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME_DIR = ROOT / "android/app/src/main/java/com/novelreader/ui/theme"
MOCK_DIR = ROOT / "docs/design-candidates"

# ---- Compose 側トークンの読み取り -------------------------------------------------

def parse_color_kt() -> dict[str, str]:
    """Color.kt の `val Name = Color(0xFFRRGGBB)` を {Name: 'RRGGBB'} で返す。"""
    text = (THEME_DIR / "Color.kt").read_text(encoding="utf-8")
    return {
        m.group(1): m.group(2).upper()
        for m in re.finditer(r"val\s+(\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", text)
    }

def parse_font_tokens() -> dict[str, float]:
    """Typography.kt の役割別スロット `val FontXxx = N.sp` を {Name: N} で返す（ADR 0014 §A）。"""
    text = (THEME_DIR / "Typography.kt").read_text(encoding="utf-8")
    return {
        m.group(1): float(m.group(2))
        for m in re.finditer(r"val\s+(Font\w+)\s*=\s*([0-9.]+)\.sp", text)
    }

def parse_reading_colors() -> dict[str, dict[str, str]]:
    """Theme.kt の ReadingColors 3 テーマを {テーマ: {フィールド: 'RRGGBB'}} で返す。"""
    text = (THEME_DIR / "Theme.kt").read_text(encoding="utf-8")
    themes: dict[str, dict[str, str]] = {}
    for tm in re.finditer(r"ReadingTheme\.(LIGHT|SEPIA|DARK)\s*->\s*ReadingColors\((.*?)\n\s*\)", text, re.S):
        fields = {
            f.group(1): f.group(2).upper()
            for f in re.finditer(r"(\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", tm.group(2))
        }
        themes[tm.group(1)] = fields
    return themes

# ---- 期待表（保守対象。モック改版・トークン改名時はここを更新する） ---------------

# 標準変数セット（発見系・UI-n 4画面等の :root）→ Color.kt ライトトークン。
# 変数がファイルに存在するときのみ照合（--soft/--ink-soft の揺れは両方マップ）。
STANDARD_VARS = {
    "--base": "BackgroundLight",
    "--bg": "BackgroundLight",
    "--ink": "OnBackgroundLight",
    "--ink-soft": "OnSurfaceVariantLight",
    "--soft": "OnSurfaceVariantLight",
    "--line": "OutlineVariantLight",
    "--ai": "PrimaryLight",
    "--seiji": "SecondaryLight",
    "--seiji-ink": "UnreadSeiji",
    "--sj-ink": "UnreadSeiji",
}

# 標準変数セットで照合する正本モック（探索・歴史記録のモックは対象外＝drift 検査の意味がない）。
STANDARD_FILES = [
    "discovery/discovery-home-D.html",
    "discovery/discovery-search-D.html",
    "discovery/discovery-genre-D.html",
    "discovery/discovery-detail-D.html",
    "discovery/reading-continuation-D.html",
    "discovery/bookshelf-fusion-D.html",
    "bookshelf-D.html",
    "toc-D.html",
    "settings-D.html",
    "bookshelf-shiori-grid-D.html",
]

# 本棚系（目録・栞整合）の家系トークン（--hl/--track のライト値。ADR 0014 適用裁定）。
SHELF_VARS = {
    "--hl": "ShelfHairlineLight",
    "--track": "ShelfHairlineLight",
    "--seiji-ink": "UnreadSeiji",
    "--sj-ink": "UnreadSeiji",
}
SHELF_FILES = [
    "bookshelf-mokuroku-D.html",
    "bookshelf-shiori-consistency-D.html",
]

# reading-D の 3 テーマ宣言（.t-light → .t-sepia → .t-dark の出現順が前提）→ ReadingColors。
READING_VARS = {
    "--bg": "background",
    "--ink": "text",
    "--soft": "textSecondary",
    "--ruby": "ruby",
    "--blk-bg": "blockBackground",
    "--blk-bd": "blockBorder",
    "--bar": "navBackground",
    "--bar-line": "divider",
    "--accent": "accent",
    "--rule": "rule",  # 章見出しルール（DARK のみ accent と乖離＝独立トークン ReadingColors.rule）
}
READING_FILE = "reading-D.html"
READING_ORDER = ["LIGHT", "SEPIA", "DARK"]

# ---- 照合 -------------------------------------------------------------------------

def find_decls(text: str, var: str) -> list[str]:
    return [m.upper() for m in re.findall(rf"{re.escape(var)}\s*:\s*#([0-9A-Fa-f]{{6}})\b", text)]

def main() -> int:
    tokens = parse_color_kt()
    reading = parse_reading_colors()
    ok = ng = skip = 0
    failures: list[str] = []

    def check(label: str, actual: list[str], expected: str | None, token_name: str) -> None:
        nonlocal ok, ng, skip
        if expected is None:
            failures.append(f"[NG] {label}: トークン {token_name} が Color.kt に見つからない")
            ng += 1
        elif not actual:
            skip += 1
        elif expected in actual:
            ok += 1
        else:
            failures.append(f"[NG] {label}: モック宣言 {actual} ⇄ {token_name}=#{expected}")
            ng += 1

    for rel in STANDARD_FILES + SHELF_FILES:
        path = MOCK_DIR / rel
        if not path.exists():
            failures.append(f"[NG] 正本モックが見つからない: {rel}（移設したなら本表を更新）")
            ng += 1
            continue
        text = path.read_text(encoding="utf-8")
        var_map = STANDARD_VARS if rel in STANDARD_FILES else SHELF_VARS
        for var, token_name in var_map.items():
            check(f"{rel} {var}", find_decls(text, var), tokens.get(token_name), token_name)

    # reading-D: 3 テーマ順序照合
    rpath = MOCK_DIR / READING_FILE
    if not rpath.exists():
        failures.append(f"[NG] 正本モックが見つからない: {READING_FILE}")
        ng += 1
    else:
        text = rpath.read_text(encoding="utf-8")
        for var, field in READING_VARS.items():
            decls = find_decls(text, var)
            if len(decls) != 3:
                failures.append(f"[NG] {READING_FILE} {var}: 3テーマ宣言のはずが {len(decls)} 件（順序前提が崩れた＝要保守）")
                ng += 1
                continue
            for theme, decl in zip(READING_ORDER, decls):
                expected = reading.get(theme, {}).get(field)
                if expected is None:
                    failures.append(f"[NG] {READING_FILE} {var}({theme}): ReadingColors.{field} を Theme.kt から抽出できない")
                    ng += 1
                elif decl == expected:
                    ok += 1
                else:
                    failures.append(f"[NG] {READING_FILE} {var}({theme}): モック #{decl} ⇄ ReadingColors.{field}=#{expected}")
                    ng += 1

    # 字面スロット（Font*）: 各スロット値が正本モック群の font-size px 集合に実在するか（drift 検出）。
    # なぜ「集合への実在」照合か: sp 値は全てモック px の写経（2026-07-12 全数調査で 1:1 対応を確認）で、
    # 色のような変数名対応がモック側に無い（font-size は直値）。値がどのモックからも消えたら
    # 「モックだけ字面を変えた／トークンだけ変えた」の drift として検知できる最小の機械検査。
    mock_px: set[float] = set()
    for path in MOCK_DIR.rglob("*.html"):
        mock_px |= {float(v) for v in re.findall(r"font-size\s*:\s*([0-9.]+)px", path.read_text(encoding="utf-8"))}
    for name, sp in parse_font_tokens().items():
        if sp in mock_px:
            ok += 1
        else:
            failures.append(f"[NG] Typography.kt {name}={sp}sp: モック群の font-size px に不在（drift）")
            ng += 1

    print(f"design token check: OK={ok} NG={ng} SKIP={skip}")
    for line in failures:
        print(line)
    return 1 if ng else 0

if __name__ == "__main__":
    sys.exit(main())
