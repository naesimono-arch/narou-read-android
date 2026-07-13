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

def parse_spacing_scale() -> set[int]:
    """Spacing.kt の `val S<N> = <N>.dp` からスケール {N} を返す（ADR 0014 §C）。
    
    なぜ単一情報源か: 実装とテストで別々に {4, 8...} を持つと将来の改訂で必ず乖離する。
    正本（theme/Spacing.kt）から直接パースして検査基準とする。
    """
    text = (THEME_DIR / "Spacing.kt").read_text(encoding="utf-8")
    scale = {int(m.group(1)) for m in re.finditer(r"val\s+S(\d+)\s*=\s*\d+\.dp", text)}
    assert scale, "Spacing scale must not be empty (failed to parse Spacing.kt)"
    return scale

def parse_insets_values() -> set[int]:
    """Spacing.kt の `object Insets { ... = <N>.dp }` から構造インセット {N} を返す。
    
    なぜ離散スケールと分けるか: インセットは他要素からの制約から決まり、
    余白のリズム（スケール）とは別種の値だから。
    """
    text = (THEME_DIR / "Spacing.kt").read_text(encoding="utf-8")
    m = re.search(r"object Insets\s*\{([^}]+)\}", text)
    if not m:
        return set()
    return {int(v) for v in re.findall(r"val\s+\w+\s*=\s*(\d+)\.dp", m.group(1))}

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

# ---- 派生モック突合表（opt-in・drift 検出） ---------------------------------------
# なぜこの検査か: 派生モック（比較・試作系）は正本モックの値を複製して作られ、正本の更新で
# サイレントに陳腐化する（実測＝F比較モックの本文タイポ drift・2026-07-13 ユーザー2度指摘で発覚）。
# なぜ opt-in 表か: 「全派生モックにメタ必須」の opt-out 方式は並行レーンの新規カタログ収蔵を
# 軒並み NG にする。突合するのは「正本の忠実再現を自称する値」（派生モック内コメントの主張）だけで、
# それをこの表に列挙する。意図的に旧値を保存する歴史記録モック（例: spacing-scale-compare-D の
# 「現状」側）は表に載せず、@dsCard 隣の @derives コメントに frozen を宣言する。
# コミットゲート hook への配線は不採用（発生頻度1回のリスクに常設ガードは過剰＝人間センチネル
# 撤去と同じ判断。本検査は既存ゲート運用で習慣的に回るため走らせ忘れリスクは実測上ない）。
DERIVED_SYNC: list[dict] = [
    # reading-gear-alt-D（C① 試作）: 「ライトテーマ（reading-D t-light）」を自称する色変数の複製。
    # 正本 reading-D は 3 テーマ順序宣言（LIGHT が先頭）＝先頭宣言同士を突合する。
    {
        "derived": "reading-gear-alt-D.html",
        "source": "reading-D.html",
        "vars_first_decl": ["--bg", "--ink", "--soft", "--ruby", "--rule",
                            "--blk-bg", "--blk-bd", "--bar", "--bar-line", "--accent"],
    },
    # reading-gear-alt-D: 「表示設定シートは settings-D の .sheet をそのまま再利用（中身は不変）」
    # を自称するシート系クラスタの複製（セレクタ×プロパティ単位で実値突合）。
    {
        "derived": "reading-gear-alt-D.html",
        "source": "settings-D.html",
        "css_props": [
            (".sheet", "padding"),
            (".grab", "margin"),
            (".sheet h2", "margin-bottom"),
            (".sheet h2", "font-size"),
            (".lbl", "margin-bottom"),
            (".sec", "margin-bottom"),
            (".chips", "gap"),
            (".chip", "padding"),
            (".slider", "gap"),
            (".row-lbl", "margin-bottom"),
        ],
    },
]

def css_prop_value(text: str, selector: str, prop: str) -> str | None:
    """`selector{...}` ブロック内の `prop:値` を返す（無ければ None）。

    なぜ簡易パースで足りるか: 対象モックの CSS は「1ルール=1行頭開始」の圧縮記法。
    行頭アンカーでセレクタ全体を特定する（素の `.lbl` 検索だと複合セレクタ
    `.bb2-set .lbl` に先にマッチする誤検出があった＝実測）。
    prop 側は `(?<![-\\w])` で `margin-bottom` への `margin` 誤マッチを防ぐ。
    """
    m = re.search(r"(?m)^\s*" + re.escape(selector) + r"\s*\{(.*?)\}", text, re.S)
    if not m:
        return None
    pm = re.search(rf"(?<![-\w]){re.escape(prop)}\s*:\s*([^;}}]+)", m.group(1))
    return re.sub(r"\s+", " ", pm.group(1).strip()) if pm else None

# これは再翻訳(Spacing/Insets参照化)完了ごとに行を消していくラチェット。空になったら余白トークン移行完了。
# 拡張7段スケール再翻訳の残債ラチェット。2026-07-13 に全19ファイルを再翻訳し空へ到達
# （＝以後 spacing-context の直書き .dp は WARN でなく NG＝逆走を止める）。履歴は git log 参照。
GRACE_FILES: set[str] = set()

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

    # ---- 派生モック ⇄ 正本モックの実値突合（DERIVED_SYNC 表） ----
    for entry in DERIVED_SYNC:
        dpath = MOCK_DIR / entry["derived"]
        spath = MOCK_DIR / entry["source"]
        if not dpath.exists() or not spath.exists():
            failures.append(f"[NG] DERIVED_SYNC: {entry['derived']} / {entry['source']} が見つからない（移設・収蔵完了なら本表を更新）")
            ng += 1
            continue
        dtext = dpath.read_text(encoding="utf-8")
        stext = spath.read_text(encoding="utf-8")
        for var in entry.get("vars_first_decl", []):
            dd, sd = find_decls(dtext, var), find_decls(stext, var)
            label = f"{entry['derived']} {var} ⇄ {entry['source']}"
            if not dd or not sd:
                failures.append(f"[NG] {label}: 宣言が見つからない（派生={len(dd)}件/正本={len(sd)}件＝複製構造が変わった＝要保守）")
                ng += 1
            elif dd[0] == sd[0]:
                ok += 1
            else:
                failures.append(f"[NG] {label}: 派生 #{dd[0]} ⇄ 正本 #{sd[0]}（drift）")
                ng += 1
        for selector, prop in entry.get("css_props", []):
            dv = css_prop_value(dtext, selector, prop)
            sv = css_prop_value(stext, selector, prop)
            label = f"{entry['derived']} {selector}{{{prop}}} ⇄ {entry['source']}"
            if dv is None or sv is None:
                failures.append(f"[NG] {label}: 宣言が見つからない（派生={dv!r}/正本={sv!r}＝複製構造が変わった＝要保守）")
                ng += 1
            elif dv == sv:
                ok += 1
            else:
                failures.append(f"[NG] {label}: 派生 '{dv}' ⇄ 正本 '{sv}'（drift）")
                ng += 1

    # ---- Spacing lint (Phase A): mock margin check ----
    scale_set = parse_spacing_scale()
    insets_set = parse_insets_values()
    # モック特有の構造インセット allowlist（Compose側の Insets と同種＝他要素の寸法から決まる値で丸め対象外）:
    # 92=reading-D スクロール下端・210=discovery-detail floating panel クリアランス・90=目録のスクロール下端
    mock_insets = {90, 92, 210}
    allowed_mock_px = scale_set | {0} | mock_insets | insets_set
    
    # 探索・歴史記録以外の正本モックすべて（reading-D含む）を走査
    for rel in STANDARD_FILES + SHELF_FILES + [READING_FILE]:
        path = MOCK_DIR / rel
        if not path.exists(): continue
        
        text = path.read_text(encoding="utf-8")
        
        # なぜ /*==harness==*/ を除外するか:
        # モック上では検証用・コンテナ表現など実際のアプリレイアウトには現れない
        # （または別管理される）余白が存在するため。これらは off-scale でもノイズ。
        text = re.sub(r"/\*==harness==\*/.*?/\*==/harness==\*/", "", text, flags=re.DOTALL)
        
        for m in re.finditer(r"(?<!-)\b(padding(?:-(?:top|right|bottom|left))?|margin(?:-(?:top|right|bottom|left))?|gap|row-gap|column-gap)\s*:\s*([^;\"\}]+)", text):
            prop = m.group(1)
            val_str = m.group(2)
            
            for px_m in re.finditer(r"(-?[0-9.]+)\s*px\b", val_str):
                px_val = float(px_m.group(1))
                if px_val < 0: continue
                px_int = int(px_val) if px_val.is_integer() else px_val
                
                # なぜ gap:1px を特別扱いするか:
                # これはリストセパレータのヘアライン表現（borderの代用）であり、
                # 空間を広げる意図の余白ではないためスケール外で正しい。
                if prop == 'gap' and px_int == 1:
                    continue
                    
                if px_int not in allowed_mock_px:
                    failures.append(f"[NG] {rel} {prop}:{px_int}px")
                    ng += 1

    # ---- Spacing lint (Phase B): Compose reference lint ----
    # 意図: 単なる off-scale 検出ではなく「余白トークンの参照（Spacing.S*）」を強要する。
    # そのため、オン/オフスケール問わず、文脈内の .dp リテラル直書きはすべて対象とする。
    # （※事前の audit 調査は off-scale 数のみ数えたため、このチェックの方が厳格になる）
    
    def get_enclosing_calls(text: str, pos: int) -> list[str]:
        calls = []
        idx = pos - 1
        parens = 0
        while idx >= 0 and len(calls) < 2:
            c = text[idx]
            if c == ')': parens += 1
            elif c == '(':
                parens -= 1
                if parens < 0:
                    parens = 0
                    c_match = re.search(r"([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\s*$", text[:idx])
                    if c_match: calls.append(c_match.group(1))
                    else: calls.append("UNKNOWN")
            elif c in '}]': parens += 1
            elif c in '{[':
                parens -= 1
                if parens < 0: parens = 0
            idx -= 1
        return calls

    def is_spacing_context(calls: list[str]) -> bool:
        if not calls: return False
        c0 = calls[0]
        
        # 除外対象: これらは明確に除外（包含ルールだけでも実質除外されるが念のため）
        if "border" in c0.lower() or "stroke" in c0.lower() or "elevation" in c0.lower() or "RoundedCornerShape" in c0:
            return False
            
        # 包含対象: 指定された文脈のみをチェックする
        if c0.endswith("padding") or c0.endswith("PaddingValues") or c0.endswith("spacedBy") or c0.endswith("spacedByWithFooter"):
            return True
            
        # Spacer(Modifier.height/width) は対象。単なる width/height(Modifier.height等) は非対象。
        if c0.endswith("height") or c0.endswith("width"):
            if len(calls) > 1 and calls[1].endswith("Spacer"):
                return True
                
        return False

    COMPOSE_DIR = ROOT / "android/app/src/main/java/com/novelreader/ui"
    compose_ng = 0
    compose_warn = 0
    grace_warns: dict[str, int] = {}
    for path in COMPOSE_DIR.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        rel_path = path.relative_to(ROOT).as_posix()
        is_grace = rel_path in GRACE_FILES
        
        for m in re.finditer(r"\b([0-9.]+)\.dp\b", text):
            val = float(m.group(1))
            if val == 0: continue
            
            calls = get_enclosing_calls(text, m.start())
            if is_spacing_context(calls):
                line_no = text.count('\n', 0, m.start()) + 1
                msg = f"{rel_path}:{line_no} Numeric .dp literal in spacing context ({m.group(0)})"
                if is_grace:
                    # WARN は全行出力せずファイル別に集計（248行の洪水でゲート出力が読めなくなるため。
                    # 個別行が要るときは再翻訳作業時に対象ファイルへ grep すれば足りる）
                    grace_warns[rel_path] = grace_warns.get(rel_path, 0) + 1
                    compose_warn += 1
                else:
                    failures.append(f"[NG] {msg}")
                    compose_ng += 1
                    ng += 1
    for grace_path, count in sorted(grace_warns.items()):
        failures.append(f"[WARN] {grace_path}: 余白リテラル残 {count} 件（GRACE_FILES ラチェット＝再翻訳待ち）")

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
    print(f"spacing phase(b) check: NG={compose_ng} WARN={compose_warn}")
    for line in failures:
        print(line)
    return 1 if ng else 0

if __name__ == "__main__":
    sys.exit(main())
