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

a11y コントラスト検査（2026-07-30 追加。既知バグ `a11y-contrast-below-aa` の検知手段）:
  - 「意味を運ぶ文字は WCAG 4.5:1」（ADR 0014-D の審級）を機械で測る。詳細は下の
    「a11y コントラスト」節の設計コメントを参照。

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

def parse_reading_colors(kt_rel: str) -> dict[str, dict[str, str]]:
    """スキンの reading トークン（`ReadingTheme.X -> ReadingColors(...)` の when）を
    {テーマ: {フィールド: 'RRGGBB'}} で返す。kt_rel は THEME_DIR 起点の相対パス（例 skins/SkinD.kt）。

    なぜファイルを引数化したか: P1 でスキン骨格を導入し、D の ReadingColors 値は Theme.kt から
    skins/SkinD.kt へ移設された（Theme.kt の getter は SkinD へ委譲する薄い D 固定アクセサに縮退）。
    スキンごとに reading 表の所在が変わるため、SKIN_READING 表からファイルを差し込む。
    字面 `ReadingTheme.LIGHT -> ReadingColors(` は移設後も維持されており正規表現はそのまま引ける。
    """
    text = (THEME_DIR / kt_rel).read_text(encoding="utf-8")
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
# 表の単位は「1 論理トークン = 別名の集合」。
# なぜ別名を集合で持つか（2026-07-30 の SKIP 棚卸しで是正）: モックは世代で命名が揺れており
# （--base/--bg・--ink-soft/--soft・--seiji-ink/--sj-ink）、これを平坦な dict で別エントリとして
# 持つと「使われていない方の別名」が毎ファイル SKIP として計上される。実測 SKIP 42 件のうち 32 件が
# これで、内訳は「別名の片方が不在なだけ」の空振り 22 件＋「同じ役割の不在を別名の数だけ二重計上」10 件。
# どちらも照合できていない実体ではないのに SKIP_BASELINE ラチェットの雑音になり、本物の照合漏れを埋没させる。
# 判定: 別名のうち宣言されているものを全て照合する（両方宣言されていれば両方＝カバレッジは減らさない）。
# 1 つも宣言が無いときだけ 1 件の SKIP を出し、理由を EXPECTED_SKIPS に明記させる。
STANDARD_VAR_GROUPS: list[tuple[tuple[str, ...], str]] = [
    (("--base", "--bg"), "BackgroundLight"),
    (("--ink",), "OnBackgroundLight"),
    (("--ink-soft", "--soft"), "OnSurfaceVariantLight"),
    (("--line",), "OutlineVariantLight"),
    (("--ai",), "PrimaryLight"),
    (("--seiji",), "SecondaryLight"),
    (("--seiji-ink", "--sj-ink"), "UnreadSeiji"),
]

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
# --hl と --track は同じ ShelfHairlineLight を指すが「別名」ではなく別役割（強調線／進捗の溝）で、
# どちらのモックも両方を宣言している＝両方の宣言を要求する（別エントリのまま置く）。
SHELF_VAR_GROUPS: list[tuple[tuple[str, ...], str]] = [
    (("--hl",), "ShelfHairlineLight"),
    (("--track",), "ShelfHairlineLight"),
    (("--seiji-ink", "--sj-ink"), "UnreadSeiji"),
]
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

# reading-C（夜行）の 1 テーマ宣言（:root 単一スコープ＝固定1変種）→ SkinC.reading の ReadingColors。
# reading-C の CSS 変数名は D と異なる（--text/--text-dim/--slate 系）ため C 専用の対応表を持つ。
# blockBackground/rule/navBackground 等の導出値はモックに CSS 変数として現れない（rgba 直値・opacity 合成）
# ため照合対象外＝モック変数と 1:1 対応する骨格色だけを突合する（D と同じ意味論）。
READING_VARS_C = {
    "--bg": "background",
    "--text": "text",
    "--text-dim": "textSecondary",
    "--ruby": "ruby",
    "--line": "divider",
    "--slate": "accent",
}
READING_ORDER_C = ["DARK"]  # C は固定1変種（:root 単一宣言＝order 長 1）

# reading-M（星図）の 1 テーマ宣言（:root 単一スコープ＝固定1変種 DARK）→ SkinM.reading。
# hex 宣言される :root 変数のみ照合（--line は rgba(150,168,214,.20) で checker が拾えないため除外・
# background #0B1330 はグラデ直値で変数化されていないため除外）。
READING_VARS_M = {
    "--text": "text",
    "--dim": "textSecondary",
    "--ruby": "ruby",
    "--star": "accent",
}
READING_ORDER_M = ["DARK"]

# reading-P（カートリッジ）の 3 テーマ宣言（.t-light → .t-sepia → .t-dark の出現順が前提）→ SkinP.reading。
# 追補ドラフト reading-P-themes-draft.html を承認して3テーマ化（ADR 0022 §2 追記・2026-07-17）。
# 照合するのは各 .t-* に3回宣言される「読書面の骨格色」（hex）のみ。chrome（--line=divider・--lcd=accent）は
# :root 単一宣言のテーマ不変色で .t-* に3回現れない＝順序照合に載せられないため除外する（M が rgba --line を
# 除外したのと同型＝ordered per-theme 照合は3宣言を要する）。派生値（--rd-block-bg=blockBackground 等・rgba）も
# checker が hex を拾えないため除外（SkinP.reading 側で焼き込み算式コメント併記）。
READING_VARS_P = {
    "--screen": "background",
    "--screen-lo": "blockBorder",
    "--rd-ink": "text",
    "--rd-soft": "textSecondary",
    "--rd-ruby": "ruby",
}
READING_ORDER_P = ["LIGHT", "SEPIA", "DARK"]  # reading-P の .t-* 出現順（light→sepia→dark）

# reading-J（ポータル）の 3 テーマ宣言（.t-dark → .t-light → .t-sepia の出現順が前提）→ SkinJ.reading。
# .t-* の hex 変数のみ照合（--amb1/--amb2/--glyph は rgba ambient で checker が拾えず・構造画面用のため除外）。
READING_VARS_J = {
    "--bg": "background",
    "--ink": "text",
    "--soft": "textSecondary",
    "--ruby": "ruby",
    "--accent": "accent",
    "--rule": "rule",
    "--panel": "blockBackground",
    "--panel-bd": "blockBorder",
    "--bar": "navBackground",
    "--bar-line": "divider",
}
READING_ORDER_J = ["DARK", "LIGHT", "SEPIA"]  # reading-J の .t-* 出現順（dark→light→sepia）

# スキン別の reading 期待表（表駆動）。P1 でスキン骨格を導入し、reading トークンは 1 スキン=1 ファイルへ
# 移設された。ここに 1 行足せば新スキン（例 C 夜行＝skins/SkinC.kt / reading-C.html）を同じ照合ロジックで
# 検査できる（C 用の行追加は SkinC 実装と同時＝P3 の前提）。D の検査は移設前と完全同値（30 件 OK）。
SKIN_READING: dict[str, dict] = {
    "D": {
        "kt_file": "skins/SkinD.kt",   # THEME_DIR 起点。D の ReadingColors 値の正本
        "mock": READING_FILE,          # reading-D.html（3 テーマ順序宣言）
        "vars": READING_VARS,
        "order": READING_ORDER,
    },
    "C": {
        "kt_file": "skins/SkinC.kt",       # THEME_DIR 起点。C の ReadingColors 値の正本
        "mock": "skins/reading-C.html",    # MOCK_DIR 起点＝docs/design-candidates/skins/ 配下（1 テーマ宣言）
        "vars": READING_VARS_C,
        "order": READING_ORDER_C,
    },
    "M": {
        "kt_file": "skins/SkinM.kt",       # 星図＝固定1変種 DARK
        "mock": "skins/reading-M.html",
        "vars": READING_VARS_M,
        "order": READING_ORDER_M,
    },
    "P": {
        "kt_file": "skins/SkinP.kt",       # カートリッジ＝読書のみ 3 テーマ（.t-light/.t-sepia/.t-dark）
        "mock": "skins/reading-P.html",
        "vars": READING_VARS_P,
        "order": READING_ORDER_P,
    },
    "J": {
        "kt_file": "skins/SkinJ.kt",       # ポータル＝reading のみ 3 テーマ（.t-dark/.t-light/.t-sepia）
        "mock": "skins/reading-J.html",
        "vars": READING_VARS_J,
        "order": READING_ORDER_J,
    },
}

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

# ---- SKIP ベースライン（「全滅 SKIP でも緑」の盲点封鎖） ---------------------------
# なぜこの機構か: SKIP は「その変数宣言がモックに無い＝照合対象外」で、ファイルごとに使う変数が
# 違う以上 SKIP 自体は正常。だが判定が「宣言が在るときだけ照合」のため、モック側で変数名を一斉
# リネームすると全照合が NG でなく SKIP へ落ち、OK が減っても exit 0 の緑のまま通る盲点があった。
# そこで実測 SKIP 数を定数として焼き込み、超過（＝照合が SKIP へ漏れ始めた合図）で exit 1 にする。
#
# なぜ「数」だけでは足りず EXPECTED_SKIPS（下）を併置するか（2026-07-30 の棚卸しで追加）:
# 数のラチェットは「A の照合が消えて B の照合が復活した」を素通しする（差し引き 0 で緑のまま）。
# また 42 件の SKIP が匿名の塊で、どれが構造上の対象外でどれが本物の追従漏れかを誰も言えず、
# 「照合できていない 18%」が固定化していた。SKIP を鍵付きで列挙し理由を必須にすることで、
#   - 未知の SKIP が生まれたら NG（＝新しい照合漏れを名指しで検知）
#   - 表にある SKIP が消えたら INFO/NG（＝復活したので締め直す／表が drift した）
# となり、SKIP の総数ではなく「SKIP の中身」がラチェットになる。
#
# 【現在の内訳】SKIP=10（別名グループ化で雑音 32 件＝別名の空振り 22・二重計上 10 を解消した後の実測値。
# 照合できていた OK は 193 件のまま増減なし＝カバレッジを削って SKIP を減らしたのではない）。
#   - モック追従待ち 3 件 = fusion-D / shiori-grid-D / shiori-consistency-D。未読・未取込ラベルが
#     装飾色のままで濃青磁 UnreadSeiji へ追従しておらず、対応する CSS 変数自体が無い（要意匠裁定）。
#   - 役割不在 7 件 = 発見系 5 画面・toc-D・settings-D。未読ラベルという役割がその画面に無い。
#   - 構造的に照合不能な変数（rgba 宣言・テーマ別 3 宣言が揃わない等）は SKIP ではなく
#     READING_VARS_* の期待表から除外済み＝各表の why コメントが理由の正本（C の導出値・M の
#     rgba --line・P の chrome 単一宣言・J の ambient）。
# ベースライン更新手順: モック改版・期待表の増減で SKIP が意図的に変わったら、
#   python3 tools/check_design_tokens.py を実行 → [SKIP] 一覧が全件意図どおりか目視 →
#   EXPECTED_SKIPS を増減し、本定数を新しい実測値へ書き換える。
SKIP_BASELINE = 10

# SKIP 1 件ごとの理由（鍵 = 上の [SKIP] ラベルと同一文字列。別名グループは "--a|--b" で表す）。
# 「モックの意匠を機械が直すのは禁止」（CLAUDE.md /visual-language）なので、追従漏れは隠さず
# ここへ理由付きで可視化したまま置き、人間の裁定で解消したら行を消す＝CONTRAST_BASELINE と同じ思想。
_SKIP_NO_UNREAD_ROLE = (
    "(c) 役割不在: この画面は未読/未取込の状態ラベルを持たない＝UnreadSeiji に対応する CSS 変数が"
    "存在しないのが正しい。宣言が現れたら照合が始まる（そのとき本行を削除して締め直す）")
_SKIP_MOCK_LAGS_UNREAD = (
    "(b) モック追従待ち【要裁定】: 未読/未取込ラベルはあるが装飾色のままで、濃青磁 UnreadSeiji "
    "#50685C（素地 5.79:1）へ追従していないため対応変数が無い。ADR 0014-D『意味を運ぶ文字は "
    "WCAG 4.5:1 ＞ 美学』の裁定は実装側（BookCard.kt・SkinD.shelf）にだけ入り、モックが取り残された。"
    "モックの色は意匠＝人間の裁定領域のため機械では直さない")
EXPECTED_SKIPS: dict[str, str] = {
    # --- (c) 未読ラベルという役割がその画面に無い ---
    "discovery/discovery-home-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    "discovery/discovery-search-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    "discovery/discovery-genre-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    # 詳細画面の「未読」は説明キャプションの地の文だけで、状態ラベルとしては描かれない
    "discovery/discovery-detail-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    "discovery/reading-continuation-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    "toc-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    "settings-D.html --seiji-ink|--sj-ink": _SKIP_NO_UNREAD_ROLE,
    # --- (b) 役割はあるが装飾色のまま＝モック側の追従待ち ---
    "discovery/bookshelf-fusion-D.html --seiji-ink|--sj-ink":
        _SKIP_MOCK_LAGS_UNREAD + "。実箇所 `.bk-card.narou-unimported .meta-row`（なろう・未取込）が "
        "--seiji #9CB3A8＝素地 2.14:1",
    "bookshelf-shiori-grid-D.html --seiji-ink|--sj-ink":
        _SKIP_MOCK_LAGS_UNREAD + "。実箇所 `.bk .mr`（なろう・未取込）が --seiji #9CB3A8＝2.14:1、"
        "`.bk .sr`（短編・未読）が --ink-soft #7C808B＝3.79:1",
    "bookshelf-shiori-consistency-D.html --seiji-ink|--sj-ink":
        _SKIP_MOCK_LAGS_UNREAD + "。実箇所 `.li .m .u`（未読）が --sj #9CB3A8＝2.14:1、"
        "`.bk .sr`（短編・未読）が --sub #7C808B＝3.79:1。同世代の bookshelf-mokuroku-D は "
        "64c52da で --sj-ink へ追従済み＝本ファイルだけ取り残された取りこぼし",
}

def find_decls(text: str, var: str) -> list[str]:
    return [m.upper() for m in re.findall(rf"{re.escape(var)}\s*:\s*#([0-9A-Fa-f]{{6}})\b", text)]

# ====================================================================================
# a11y コントラスト検査（WCAG 2.x 相対輝度・既知バグ `a11y-contrast-below-aa` の検知手段）
# ====================================================================================
# なぜ必要か: ADR 0014-D は「意味を運ぶ文字は WCAG 4.5:1 ＞ 美学」を審級として宣言し、
# ルビ3色・発見系メタ6箇所・未読ラベルを実際に AA へ引き上げた。だがその判定は毎回手計算で、
# 「直した箇所だけ検算し、他は未検査のまま」だった（既知バグレジストリの機序欄＝「コントラストを
# 測る機械が無い」）。スキンが D/C/M/P/J/K の6種へ増えた今、手計算での全面担保は破綻している。
#
# 【設計の核心＝前景×面の対応をどう確定したか】
# 全組合せの総当たりは雑音（載らない前景×背景の比に意味は無い）。ここでは「対応が型として宣言
# されている組」だけを測る＝ソースの構造そのものが対応表になっているものに限定する:
#   (1) ReadingColors（Theme.kt の data class）: 1 つの struct が面（background/blockBackground/
#       topBarBackground/navBackground）と前景（text/infoText/ruby/topBarTitle/topBarIcon/accent）を
#       同時に宣言する＝同一 struct 内の組は「その画面でその面の上に載る」ことが型で確定している。
#       各スキンの why コメントが実際に「素地 5.14:1／ブロック地 4.70:1」等と主張しており、
#       本検査はその主張の検算にもなる。
#   (2) ShelfColors（本棚系の家系トークン）× 同スキン同テーマの Material 面: ADR 0014 の適用裁定が
#       まさに「ライト素地 5.79:1・カード 5.30:1」と 素地(background)／カード(surfaceVariant) の
#       2 面で測っており、実装も BookCard.kt が colorScheme.background の上に infoText を描く
#       （`.miss` バッジ）ことを確認済み＝この2面が対応。
#   (3) Material3 ColorScheme の onX ⇄ X: Material の規約そのもの（onSurface は surface の上に描く）。
#       対応の出所が外部規約＝推測が入らない。
# 逆に「どの面に載るか実コードから一意に決まらない」前景（accent の面／線用途・signatureAccent・
# 構造画面専用パレット・ambient の α 付き色）は測らない。ただし黙って落とさず件数と理由を出す
# （SKIP_BASELINE と同じ fail-open 忌避の思想）。
#
# 【検査する軸＝スキン × テーマ（実測に基づく決定）】
# 実測: K は `object SkinK : SkinTokens by SkinD`＝D へ全委譲で値が同一（測っても D の重複）。
# C/M は supportedThemes=[DARK] の固定1変種。D/P/J は3変種だが P/J は material/shelf が theme 非依存。
# よって「各スキンの supportedThemes を実際にパースして、その組合せだけ回す」＝存在しない変種を
# 測らないので偽陽性が構造的に出ない。K のみ委譲検出で対象外（理由付きで件数計上）。
#
# 【閾値と例外の裁定】
#   - 文字 4.5:1（WCAG 1.4.3 AA・通常サイズ）。
#   - アイコン等の非テキスト 3:1（WCAG 1.4.11）。topBarIcon のみ該当。
#   - **大きな文字の 3:1 例外は採らない**。理由: トークン層はフォントサイズを持たず、同じ
#     `text`/`accent` トークンが本文（最小サイズ）から見出しまで共用される。大きい方に合わせて
#     緩めると最小サイズの用途が無検査になる＝厳しい側（4.5:1）で一律に測るのが安全側。
#   - 装飾テキスト（意味を運ばない）は WCAG 対象外＝測らない。どのロールが装飾かは Theme.kt の
#     KDoc が明示している（textSecondary＝「装飾的補助テキスト…意味を運ばない」、placeholder＝
#     「例示/不活性…WCAG 概ね対象外」）。この宣言をそのまま対象外表 READING_OUT_OF_SCOPE にする。
#   - 線（divider/hr/blockBorder/outline）はテキストでもUI操作部品でもない区切り＝対象外
#     （WCAG 1.4.11 は「状態や境界の識別に必須な UI 部品」を対象とし、装飾的ヘアラインは含まない）。

def _rel_luminance(hex6: str) -> float:
    """WCAG 2.x 相対輝度。sRGB を線形化して ITU-R BT.709 係数で合成する。"""
    def lin(v: int) -> float:
        c = v / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hex6[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

def contrast_ratio(fg: str, bg: str) -> float:
    """WCAG 2.x コントラスト比 (L1+0.05)/(L2+0.05)。"""
    l1, l2 = sorted((_rel_luminance(fg), _rel_luminance(bg)), reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)

# --- Kotlin の軽量パース（既存の regex 流儀を踏襲。AST は持たない） -------------------

def _strip_comments(text: str) -> str:
    """`//` 行コメントと `/* */` ブロックを空白化する。
    なぜ必要か: 値のコメントに `--rd-block-bg=rgba(...)` のような `名前=値` の字面があり、
    引数パーサが偽の引数として拾ってしまう（実測）。値の解釈にコメントは要らないので落とす。
    """
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)

def _balanced(text: str, open_idx: int, op: str = "(", cl: str = ")") -> tuple[str, int]:
    """open_idx（開き括弧の位置）から対応する閉じ括弧までの内側と、閉じ括弧の次位置を返す。"""
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == op:
            depth += 1
        elif text[i] == cl:
            depth -= 1
            if depth == 0:
                return text[open_idx + 1:i], i + 1
    return "", len(text)

_HEX_ARG = re.compile(r"(\w+)\s*=\s*(Color\(0x[0-9A-Fa-f]{8}\)|[A-Za-z_]\w*)")

def _resolve(expr: str, tokens: dict[str, str]) -> str | None:
    """`Color(0xFFRRGGBB)` か Color.kt の val 名を 'RRGGBB' へ解決する。
    α付き（0xAARRGGBB で AA != FF）は「単一の面へ焼き込めない透過色」＝測れないので None。"""
    m = re.fullmatch(r"Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)", expr)
    if m:
        return m.group(2).upper() if m.group(1).upper() == "FF" else None
    return tokens.get(expr)

def _named_args(inner: str, tokens: dict[str, str]) -> tuple[dict[str, str], list[str]]:
    """`name = 値` の並びを {name: RRGGBB} と、解決できなかった name のリストで返す。"""
    got: dict[str, str] = {}
    unresolved: list[str] = []
    for m in _HEX_ARG.finditer(inner):
        if m.group(2) in ("true", "false", "null"):
            continue  # ReadingColors.isLight 等の非色フィールド＝コントラストの対象ではない
        v = _resolve(m.group(2), tokens)
        if v:
            got[m.group(1)] = v
        else:
            unresolved.append(m.group(1))
    return got, unresolved

_BRANCH = re.compile(r"((?:ReadingTheme\.(?:LIGHT|SEPIA|DARK)\s*,\s*)*ReadingTheme\.(?:LIGHT|SEPIA|DARK))\s*->")

def _member_body(text: str, decl_re: str) -> str | None:
    """`override fun x(...) = <本体>` の本体テキストを返す（when ブロックなら中身、単式なら行末まで）。"""
    m = re.search(decl_re, text)
    if not m:
        return None
    rest = text[m.end():]
    stripped = rest.lstrip()
    if stripped.startswith("when"):
        brace = rest.index("{", rest.index("when"))
        return _balanced(rest, brace, "{", "}")[0]
    return rest.split("\n", 1)[0]

def _theme_dispatch(body: str, supported: list[str]) -> dict[str, tuple[str, int]]:
    """when 本体（または単式）から {テーマ: (本体テキスト, 分岐開始オフセット)} を作る。
    when を持たない（theme 非依存の固定値を返す）実装では全テーマが同じ式を指す。"""
    branches = list(_BRANCH.finditer(body))
    if not branches:
        return {t: (body, 0) for t in supported}
    out: dict[str, tuple[str, int]] = {}
    for i, m in enumerate(branches):
        end = branches[i + 1].start() if i + 1 < len(branches) else len(body)
        for t in re.findall(r"ReadingTheme\.(LIGHT|SEPIA|DARK)", m.group(1)):
            out[t] = (body[m.end():end], 0)
    return out

_TIER_CALL = re.compile(r"\s*\.\s*withSkinContainerTiers\s*\(\s*\)")

def parse_container_tiers() -> dict[str, str]:
    """`SkinContainerTiers.kt` の `copy(surfaceContainerHigh = surface, ...)` を {束ね先: 参照元} で返す。

    なぜ Python 側へ写経せず字面から引くか: この検査が **ダイアログ面を1件も測っていなかった真因**が
    ここにある。`surfaceContainer` 4 段は `lightColorScheme(...)` の名前付き引数に現れず
    `.withSkinContainerTiers()` という**関数呼び出しの中**で束ねられるため、名前付き引数だけを読む
    パーサからは 4 段が丸ごと存在しないスロットに見えていた（対応表に組を足しても
    「トークン未定義で組が作れない」の SKIP に落ちるだけで検査は形骸化する）。
    束ね直しの表を Python 側へ複製すると Kotlin 改訂で静かに乖離するので、Kotlin の唯一の正本を読む。
    """
    text = _strip_comments((THEME_DIR / "skins/SkinContainerTiers.kt").read_text(encoding="utf-8"))
    m = re.search(r"withSkinContainerTiers\(\)\s*:\s*ColorScheme\s*=\s*copy\s*\(", text)
    if not m:
        return {}
    inner = _balanced(text, m.end() - 1)[0]
    return dict(re.findall(r"(\w+)\s*=\s*(\w+)", inner))

def _apply_container_tiers(scheme: dict[str, str], tiers: dict[str, str],
                           text: str, after_idx: int) -> dict[str, str]:
    """定義の直後に `.withSkinContainerTiers()` が続くときだけ、Kotlin と同じ束ね直しを適用する。

    適用順序も Kotlin と一致させる（`Base.copy(surface = ...)` の後に呼ぶ D セピアは、copy で
    差し替えた新しい surface を基準に 4 段が再束ねされる＝基底から引き継いだ寒色ライト値は残らない）。
    """
    if not _TIER_CALL.match(text, after_idx):
        return scheme
    out = dict(scheme)
    for slot, src in tiers.items():
        if src in out:
            out[slot] = out[src]
    return out

def parse_skin(kt_rel: str, tokens: dict[str, str], tiers: dict[str, str] | None = None) -> dict:
    """1 スキンファイルから supportedThemes / reading / shelf / material を解決値で取り出す。

    委譲スキン（`object SkinK : SkinTokens by SkinD`）は {"delegate": "SkinD"} を返す。
    """
    tiers = tiers or {}
    raw = (THEME_DIR / kt_rel).read_text(encoding="utf-8")
    text = _strip_comments(raw)
    dm = re.search(r"object\s+(\w+)\s*:\s*SkinTokens\s+by\s+(\w+)", text)
    if dm:
        return {"delegate": dm.group(2)}

    sm = re.search(r"supportedThemes[^=]*=\s*listOf\(([^)]*)\)", text, re.S)
    supported = re.findall(r"ReadingTheme\.(LIGHT|SEPIA|DARK)", sm.group(1)) if sm else []

    unresolved: list[str] = []

    # --- Material ColorScheme: 名前付きスキーム定義 → material(theme) のディスパッチ ---
    schemes: dict[str, dict[str, str]] = {}
    for m in re.finditer(r"val\s+(\w+)\s*=\s*(?:light|dark)ColorScheme\s*\(", text):
        inner, after = _balanced(text, m.end() - 1)
        got, un = _named_args(inner, tokens)
        schemes[m.group(1)] = _apply_container_tiers(got, tiers, text, after)
        unresolved += [f"{kt_rel}:{m.group(1)}.{n}" for n in un]
    # `Base.copy(...)` 派生（D の SepiaColorScheme）。定義順にファイルへ現れる前提（実測どおり）。
    for m in re.finditer(r"val\s+(\w+)\s*=\s*(\w+)\.copy\s*\(", text):
        base = schemes.get(m.group(2))
        if base is None:
            continue
        inner, after = _balanced(text, m.end() - 1)
        got, un = _named_args(inner, tokens)
        schemes[m.group(1)] = _apply_container_tiers({**base, **got}, tiers, text, after)
        unresolved += [f"{kt_rel}:{m.group(1)}.{n}" for n in un]

    material: dict[str, dict[str, str]] = {}
    body = _member_body(text, r"override fun material\([^)]*\)\s*:\s*ColorScheme\s*=\s*")
    if body is not None:
        for theme, (expr, _) in _theme_dispatch(body, supported).items():
            name = re.search(r"\b(\w+)\b", expr)
            if name and name.group(1) in schemes:
                material[theme] = schemes[name.group(1)]

    # --- ReadingColors: when 分岐ごとに ReadingColors(...) の名前付き引数を解決 ---
    reading: dict[str, dict[str, str]] = {}
    body = _member_body(text, r"override fun reading\([^)]*\)\s*:\s*ReadingColors\s*=\s*")
    if body is not None:
        for theme, (expr, _) in _theme_dispatch(body, supported).items():
            cm = re.search(r"ReadingColors\s*\(", expr)
            if not cm:
                continue
            got, un = _named_args(_balanced(expr, cm.end() - 1)[0], tokens)
            reading[theme] = got
            unresolved += [f"{kt_rel}:reading({theme}).{n}" for n in un]

    # --- ShelfColors: 位置引数 (hairline, unreadLabel, infoText)。単一 val 定義の場合も辿る ---
    shelf: dict[str, dict[str, str]] = {}
    body = _member_body(text, r"override fun shelf\([^)]*\)\s*:\s*ShelfColors\s*=\s*")
    if body is not None:
        for theme, (expr, _) in _theme_dispatch(body, supported).items():
            cm = re.search(r"ShelfColors\s*\(", expr)
            if not cm:  # `= NightShelf` 形式＝private val の定義側を引く
                name = re.search(r"\b(\w+)\b", expr)
                vm = re.search(rf"val\s+{name.group(1)}\s*=\s*ShelfColors\s*\(", text) if name else None
                if not vm:
                    continue
                inner = _balanced(text, vm.end() - 1)[0]
            else:
                inner = _balanced(expr, cm.end() - 1)[0]
            args = [a.strip() for a in inner.split(",")]
            fields = ("hairline", "unreadLabel", "infoText")
            vals = {}
            for f, a in zip(fields, args):
                v = _resolve(a, tokens)
                if v:
                    vals[f] = v
                else:
                    unresolved.append(f"{kt_rel}:shelf({theme}).{f}")
            shelf[theme] = vals

    return {"supported": supported, "material": material, "reading": reading,
            "shelf": shelf, "unresolved": unresolved}

# --- 検査するスキン（1 スキン=1 ファイル。SKIN_READING と同じ流儀の表駆動） ----------
CONTRAST_SKINS = {
    "D": "skins/SkinD.kt", "C": "skins/SkinC.kt", "M": "skins/SkinM.kt",
    "P": "skins/SkinP.kt", "J": "skins/SkinJ.kt", "K": "skins/SkinK.kt",
}

# ReadingColors 内の (前景, 面, 最低比, 役割)。対応は同一 struct の型宣言＋実描画で確認済み。
READING_PAIRS = [
    ("text", "background", 4.5, "本文"),
    ("text", "blockBackground", 4.5, "前書き/後書きブロック内の本文"),
    ("infoText", "background", 4.5, "意味を運ぶ補助テキスト（エラー本文・空状態説明・目次メタ）"),
    ("ruby", "background", 4.5, "ルビ＝著者指定の読み（意味搬送小文字）"),
    ("ruby", "blockBackground", 4.5, "ブロック内のルビ（ChapterContent の Surface 内 RubyText）"),
    ("topBarTitle", "topBarBackground", 4.5, "トップバーのタイトル文字"),
    ("topBarIcon", "topBarBackground", 3.0, "トップバーのアイコン（非テキスト 3:1）"),
    ("topBarIcon", "navBackground", 3.0, "ナビバーのアイコン（非テキスト 3:1）"),
    # accent は面/線にも使う多義トークンだが、「文字として載る面」は実コードで2つに確定できる:
    #   ChapterContent.kt: block.label を colors.accent で blockBackground の Surface 上に描く。
    #   NativeTableOfContentsScreen.kt: 現在章の章題を colors.accent で描く（行地は accent の 6% 淡色
    #   ＝素地とほぼ同輝度。合成せず素地 background を代表面として測る）。
    ("accent", "blockBackground", 4.5, "前書き/後書きのラベル文字"),
    ("accent", "background", 4.5, "目次の現在章タイトル"),
]

# ReadingColors のうち「面でも前景でもない／WCAG 対象外」と Theme.kt の KDoc が宣言するロール。
# 黙って落とさず件数と理由を出すためにデータとして持つ（fail-open 忌避）。
READING_OUT_OF_SCOPE = [
    ("textSecondary", "装飾的補助テキスト＝意味を運ばない（Theme.kt KDoc の宣言）"),
    ("placeholder", "例示/不活性テキスト＝WCAG 概ね対象外（Theme.kt KDoc の宣言）"),
    ("hr", "本文中の区切り線＝テキストでもUI部品でもない"),
    ("divider", "目次の区切り線＝同上"),
    ("blockBorder", "ブロック枠線＝同上"),
    ("rule", "章見出しの装飾ルール＝同上（各スキンが装飾線と明記）"),
]

# ShelfColors の意味色 × 同スキン同テーマの Material 面（棚地＝background・カード面＝surfaceVariant・
# ダイアログ面＝surfaceContainerHigh）。
SHELF_PAIRS = [
    ("unreadLabel", "background", 4.5, "未読ラベル（棚地の上）"),
    ("unreadLabel", "surfaceVariant", 4.5, "未読ラベル（カード面の上）"),
    ("infoText", "background", 4.5, "情報メタ（棚地の上）"),
    ("infoText", "surfaceVariant", 4.5, "情報メタ（カード面の上）"),
    # ダイアログ本文。対応の出所は theme/NovelReaderAlertDialog.kt の既定引数
    # `textContentColor = LocalShelfColors.current.infoText`＝ソースが型として宣言している組
    #（M3 既定の onSurfaceVariant は ADR 0014-D で装飾専用スロットへ縮退済みのため使えない）。
    # 現状は全スキンで surfaceContainerHigh == surface == background ゆえ「情報メタ（棚地の上）」と
    # 同値だが、SkinContainerTiers.kt の TODO どおり各スキンのダイアログ意匠が起こされて面の実色が
    # 分かれた瞬間に、この組だけが正しい面を測り続ける（別組として持つ理由）。
    ("infoText", "surfaceContainerHigh", 4.5, "ダイアログ本文（NovelReaderAlertDialog の既定色）"),
]

# Material3 の onX ⇄ X（規約上「X の面に onX を描く」）。inversePrimary は inverseSurface 上のアクセント。
MATERIAL_PAIRS = [
    ("onBackground", "background"), ("onSurface", "surface"),
    ("onSurfaceVariant", "surfaceVariant"),
    ("onPrimary", "primary"), ("onPrimaryContainer", "primaryContainer"),
    ("onSecondary", "secondary"), ("onSecondaryContainer", "secondaryContainer"),
    ("onTertiary", "tertiary"), ("onTertiaryContainer", "tertiaryContainer"),
    ("onError", "error"), ("onErrorContainer", "errorContainer"),
    ("inverseOnSurface", "inverseSurface"), ("inversePrimary", "inverseSurface"),
]

# ダイアログ面（`DialogTokens.ContainerColor` = SurfaceContainerHigh）に載る M3 既定の前景。
# なぜ MATERIAL_PAIRS と別表か: 上は「onX ⇄ X」という Material の命名規約で対応が決まる組で、
# こちらは**部品トークンの配線**（DialogTokens / TextButtonTokens）で対応が決まる組＝出所が違う。
# 対応は material3 1.3.2 のバイトコードで確認（DialogTokens: ContainerColor=SurfaceContainerHigh・
# HeadlineColor=OnSurface・SupportingTextColor=OnSurfaceVariant／TextButtonTokens: LabelTextColor=Primary）。
# 本文（SupportingTextColor）をここに入れない理由: アプリは NovelReaderAlertDialog で本文色を
# InfoText 系へ差し替える＝実際に載るのは onSurfaceVariant ではない（その組は SHELF_PAIRS 側で測る）。
# 迂回して raw AlertDialog を直呼びする流入は、値でなく呼び出し側の問題なので下の lint が止める。
# 他の 3 段（Lowest/Low/Highest）は組を作らない: SkinContainerTiers.kt の実測どおり、それらを引く
# 部品（Card/ModalBottomSheet/Switch トラック）は呼び出し側が containerColor を明示しているか
# 非テキストで、載る前景が一意に決まらないため（雑音を足すと検査が形骸化する）。
DIALOG_MATERIAL_PAIRS = [
    ("onSurface", "surfaceContainerHigh", 4.5, "ダイアログ題字（DialogTokens.HeadlineColor）"),
    ("primary", "surfaceContainerHigh", 4.5, "ダイアログ操作ラベル（TextButtonTokens.LabelTextColor）"),
]

# ---- 既知の意図的例外（ベースライン） ----------------------------------------------
# なぜベースラインを置くか: 「症状を隠す」ためではない。意匠の裁定は人間が持つ（CLAUDE.md
# /visual-language）ので、機械は「未達である事実」を消さずに可視化したまま緑に保ち、裁定が
# 済んだものだけ理由付きでここへ載せる。閾値を下げて黙らせるのは禁止（それは検知手段の破壊）。
# 保守則:
#   - ここの項目が是正されて合格したら [INFO] が出る → 行を削除して締め直す。
#   - ここの項目が検査対象に現れなくなったら [NG]（対応表かトークン名が drift した合図）。
# 【重要】ここには2種類が載る。混同しないこと:
#   (a) 裁定済みの意図的例外 — ADR/トークンコメントに根拠がある。原則そのまま。
#   (b) 【要裁定】— 初回計測（2026-07-30）で見つかった未裁定の実違反。色を機械が勝手に決め直すのは
#       禁止（意匠の裁定は人間・CLAUDE.md /visual-language）なので、赤を消さずに可視化したまま
#       裁定待ちで置く。裁定が済んだら「色を直して行を消す」が正しい終わり方で、
#       (a) へ格下げして永住させるのは原則否。
# key = "スキン/テーマ/前景⇄面"
_DECO_ON_SV = ("(a) 裁定済: onSurfaceVariant は『装飾的補助（著者名・キャプション）』専用スロットで "
               "意味を運ぶ用途は InfoText 系へ役割分離済み（ADR 0014-D 適用裁定・Color.kt の why）。"
               "装飾は WCAG 対象外のため未達を許容する")
_UNUSED_ON_SECONDARY = ("(a) 裁定済: secondary は装飾・面・署名の補助色で、対の onSecondary は "
                        "UI 実装から1箇所も参照されない（実測 0 件）。Material 契約上の充足値であり "
                        "実際に文字が載る面ではない")
_P_ACCENT_TEXT = ("【要裁定】P の accent=液晶グリーン #A4AF80 は Color.kt が『装飾/面用途に限る』と "
                  "宣言しているのに、共通読書エンジンが accent を文字色として使う"
                  "（ChapterContent の前書き/後書きラベル・目次の現在章タイトル）。明面スクリーンでは "
                  "1.4〜1.9:1 で完全に読めない。P 専用の文字用アクセントを切るか、ラベル/現在章の "
                  "色役割を accent から分離するかの意匠裁定が要る")
_P_SHELF_ON_PANEL = ("【要裁定】P の --ink-mid #5A574C は素地 plastic 上 4.98:1 だけを検算しており "
                     "カード面 --panel #CFCABB 上を測っていなかった（4.42:1）。ADR 0014 の先例"
                     "（UnreadSeiji/InfoText は素地・カードの両面で AA を満たす最小暗化）に照らすと "
                     "同型の最小暗化が要る")
_P_DIALOG_ACTION = ("【要裁定】P の primary＝退色レッド #B5564E がダイアログ面（筐体面 #DBD6C8）上 3.28:1。"
                    "確認/やめる の操作ラベルは TextButton の既定色（TextButtonTokens.LabelTextColor="
                    "Primary）で描かれる＝削除確認の主操作という意味を運ぶ文字。ラベル色の決定は意匠の"
                    "裁定（人間・CLAUDE.md /visual-language）なので機械では直さず可視化のまま置く。"
                    "候補: --red-lo #8D4139 は同面 4.91:1（正本モック側のボタン実色との突合が要る）")
_P_RUBY_ON_BLOCK = ("【要裁定】P DARK のルビは読書面 --screen 上 4.63:1 だけを検算しており、"
                    "前書き/後書きブロック地 #33352C 上を測っていなかった（4.12:1）。"
                    "D はルビを素地・ブロック地の両面で検算済み＝P だけ片面検算の取り残し")
CONTRAST_BASELINE: dict[str, str] = {
    "D/LIGHT/material:onSurfaceVariant⇄surfaceVariant": _DECO_ON_SV,
    "D/SEPIA/material:onSurfaceVariant⇄surfaceVariant": _DECO_ON_SV,
    "P/LIGHT/material:onSurfaceVariant⇄surfaceVariant": _DECO_ON_SV,
    "P/SEPIA/material:onSurfaceVariant⇄surfaceVariant": _DECO_ON_SV,
    "P/DARK/material:onSurfaceVariant⇄surfaceVariant": _DECO_ON_SV,
    "D/LIGHT/material:onSecondary⇄secondary": _UNUSED_ON_SECONDARY,
    "D/SEPIA/material:onSecondary⇄secondary": _UNUSED_ON_SECONDARY,
    "P/LIGHT/material:onSecondary⇄secondary": _UNUSED_ON_SECONDARY,
    "P/SEPIA/material:onSecondary⇄secondary": _UNUSED_ON_SECONDARY,
    "P/DARK/material:onSecondary⇄secondary": _UNUSED_ON_SECONDARY,
    "P/LIGHT/reading:accent⇄blockBackground": _P_ACCENT_TEXT,
    "P/LIGHT/reading:accent⇄background": _P_ACCENT_TEXT,
    "P/SEPIA/reading:accent⇄blockBackground": _P_ACCENT_TEXT,
    "P/SEPIA/reading:accent⇄background": _P_ACCENT_TEXT,
    "P/LIGHT/shelf:unreadLabel⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/LIGHT/shelf:infoText⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/SEPIA/shelf:unreadLabel⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/SEPIA/shelf:infoText⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/DARK/shelf:unreadLabel⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/DARK/shelf:infoText⇄surfaceVariant": _P_SHELF_ON_PANEL,
    "P/DARK/reading:ruby⇄blockBackground": _P_RUBY_ON_BLOCK,
    "P/LIGHT/material:primary⇄surfaceContainerHigh": _P_DIALOG_ACTION,
    "P/SEPIA/material:primary⇄surfaceContainerHigh": _P_DIALOG_ACTION,
    "P/DARK/material:primary⇄surfaceContainerHigh": _P_DIALOG_ACTION,
}

# 実測の検査ペア数。regex が壊れて対象が静かに消えても緑のまま通る盲点を塞ぐラチェット
# （SKIP_BASELINE と同じ思想）。増える分には止めず [INFO] で締め直しを促す。
CONTRAST_PAIRS_BASELINE = 330

def check_contrast(failures: list[str], notes: list[str]) -> tuple[int, int, int, int, int]:
    """コントラスト検査。(合格, 違反, ベースライン済, 対象外, 検査ペア総数) を返す。"""
    tokens = parse_color_kt()
    # surfaceContainer 4 段は Kotlin 側の束ね直し関数の中にしか無い＝パースできないと
    # ダイアログ面の組が全部 SKIP へ落ちて静かに無検査になる（この検査が件1を見逃した真因そのもの）。
    # 落ちたら黙らず fail-closed にする。
    tiers = parse_container_tiers()
    ok = ng = based = skipped = 0
    if not tiers:
        failures.append("[NG] コントラスト: SkinContainerTiers.kt の束ね直し表を抽出できない"
                        "（パース前提が崩れた＝要保守。surfaceContainer 4 段が無検査になる）")
        ng += 1
    seen_keys: set[str] = set()
    skip_reasons: dict[str, int] = {}

    def note_skip(reason: str, n: int = 1) -> None:
        nonlocal skipped
        skipped += n
        skip_reasons[reason] = skip_reasons.get(reason, 0) + n

    for skin_id, kt_rel in CONTRAST_SKINS.items():
        spec = parse_skin(kt_rel, tokens, tiers)
        if "delegate" in spec:
            note_skip(f"スキン{skin_id}: {spec['delegate']} へトークン束を全委譲＝値が同一（委譲元で検査済み）")
            continue
        if not spec["supported"]:
            failures.append(f"[NG] コントラスト: {kt_rel} の supportedThemes を抽出できない（パース前提が崩れた＝要保守）")
            ng += 1
            continue
        for name in spec["unresolved"]:
            note_skip(f"値を単色へ解決できない（α付き Color 等・要素 {name}）")

        for theme in spec["supported"]:
            groups = [
                ("reading", spec["reading"].get(theme, {}), spec["reading"].get(theme, {}), READING_PAIRS),
                # shelf だけ前景と面の出所が別（家系トークン × Material 面）＝2 引数を分けて渡す形が要る
                ("shelf", spec["shelf"].get(theme, {}), spec["material"].get(theme, {}), SHELF_PAIRS),
                ("material", spec["material"].get(theme, {}), spec["material"].get(theme, {}),
                 [(f, b, 4.5, "Material3 の onX ⇄ X") for f, b in MATERIAL_PAIRS] + DIALOG_MATERIAL_PAIRS),
            ]
            for gname, fg_src, bg_src, pairs in groups:
                for fg, bg, minimum, role in pairs:
                    # 群名を鍵に含める: reading.infoText⇄background と shelf.infoText⇄background は
                    # 別の組（前景の出所が違う）だが名前が衝突する。1つのベースライン行が両方を
                    # 黙って覆う fail-open を防ぐため群で分ける。
                    key = f"{skin_id}/{theme}/{gname}:{fg}⇄{bg}"
                    fg_hex, bg_hex = fg_src.get(fg), bg_src.get(bg)
                    if not fg_hex or not bg_hex:
                        note_skip(f"{gname}: トークン未定義で組が作れない（{key}）")
                        continue
                    seen_keys.add(key)
                    ratio = contrast_ratio(fg_hex, bg_hex)
                    if ratio >= minimum:
                        ok += 1
                        if key in CONTRAST_BASELINE:
                            notes.append(f"[INFO] コントラスト例外 {key} は {ratio:.2f}:1 で合格＝是正済み。"
                                         f"CONTRAST_BASELINE から削除して締め直す")
                    elif key in CONTRAST_BASELINE:
                        based += 1
                        notes.append(f"[BASELINE] {key} #{fg_hex} on #{bg_hex} = {ratio:.2f}:1 "
                                     f"(< {minimum}) 既知の意図的例外: {CONTRAST_BASELINE[key]}")
                    else:
                        failures.append(f"[NG] コントラスト {key}: #{fg_hex} on #{bg_hex} = "
                                        f"{ratio:.2f}:1 < {minimum}:1 ({role})")
                        ng += 1
        # ReadingColors の WCAG 対象外ロール（宣言由来）を件数として計上する
        for field, reason in READING_OUT_OF_SCOPE:
            n = sum(1 for t in spec["supported"] if field in spec["reading"].get(t, {}))
            if n:
                note_skip(f"reading.{field}: {reason}", n)

    for key in sorted(set(CONTRAST_BASELINE) - seen_keys):
        failures.append(f"[NG] CONTRAST_BASELINE の項目 {key} が検査対象に現れない"
                        f"（対応表かトークン名の drift＝ベースラインが死んでいる）")
        ng += 1

    total = ok + ng + based
    for reason, n in sorted(skip_reasons.items()):
        notes.append(f"[OUT-OF-SCOPE] x{n} {reason}")
    if total < CONTRAST_PAIRS_BASELINE:
        failures.append(f"[NG] 検査ペア数 {total} がベースライン {CONTRAST_PAIRS_BASELINE} を下回った"
                        f"（パース破損等で対象が静かに消えた疑い）")
        ng += 1
    elif total > CONTRAST_PAIRS_BASELINE:
        notes.append(f"[INFO] 検査ペア数 {total} > ベースライン {CONTRAST_PAIRS_BASELINE}: "
                     f"CONTRAST_PAIRS_BASELINE を {total} へ締め直し推奨")
    return ok, ng, based, skipped, total

# ====================================================================================
# raw AlertDialog 直呼びの検出（ダイアログ本文色の迂回）
# ====================================================================================
# なぜトークン検査に呼び出し側の lint を置くか: 本文色の是正は theme/NovelReaderAlertDialog.kt に
# 集約したが、M3 の `AlertDialog` を直に呼べば既定（onSurfaceVariant＝装飾専用スロット）へ落ちる。
# 「以後は必ずラッパを使う」は宣言だけのルールで数週間で崩れる（ADR 0017 決定5）＝番人とセットにする。
# 判定は import 行でなく**呼び出しの字面**。BookshelfScreen.kt が `material3.*` のワイルドカード
# import で 10 箇所を直呼びしており（実測）、import 判定では丸ごと素通りしたため。
# 直前が語構成文字なら除外＝`NovelReaderAlertDialog(`（ラッパ自身の呼び出し）を拾わない。
# `.` は除外しない＝完全修飾 `androidx.compose.material3.AlertDialog(` も捕まえる。
UI_SRC_DIR = ROOT / "android/app/src/main/java/com/novelreader/ui"
RAW_ALERT_DIALOG_WRAPPER = "theme/NovelReaderAlertDialog.kt"   # UI_SRC_DIR 起点。唯一の直呼び許可箇所
_RAW_ALERT_DIALOG_CALL = re.compile(r"(?<!\w)AlertDialog\s*\(")

# 移行台帳（2026-07-31 時点で raw を残しているファイル＝ラッパ新設と同便では差し替えない）。
# なぜ台帳方式か: 差し替えは呼び出し側の所有権（別レーンが同ファイルを触っている）で、同便に混ぜられない。
# 台帳に無いファイルの新規流入は即 NG＝「次に同じものが入ってきたら止まる」を今日から効かせつつ、
# 残作業は機械が数え続ける（報告文に書くだけだと消える）。保守則は CONTRAST_BASELINE と同じ:
#   - 差し替え済みで raw が消えた → [INFO]。行を削除して締め直す。
#   - ファイルごと消えた/移動した → [NG]（台帳の行が死んでいる）。
# 2026-07-31: 16 箇所 7 ファイルの差し替えが完了したので**空へ締め直した**（保守則どおり）。
# 空が既定の姿＝raw 直呼びは1件も許されない。行を足してよいのは「同便で差し替えられない移行が
# 実際に発生したとき」だけで、足した行は差し替え完了と同時に消す（[INFO] が締め直しを促す）。
PENDING_RAW_ALERT_DIALOG: dict[str, str] = {}

def check_raw_alert_dialog(failures: list[str], notes: list[str]) -> tuple[int, int, int]:
    """(ラッパ経由=OK, 新規流入=NG, 移行待ち) を返す。"""
    ok = ng = pending = 0
    found: set[str] = set()
    for path in sorted(UI_SRC_DIR.rglob("*.kt")):
        rel = path.relative_to(UI_SRC_DIR).as_posix()
        if rel == RAW_ALERT_DIALOG_WRAPPER:
            continue
        # コメント（「構造は Material AlertDialog をそのまま使う」等の説明文）を先に落とす＝偽陽性回避。
        hits = len(_RAW_ALERT_DIALOG_CALL.findall(_strip_comments(path.read_text(encoding="utf-8"))))
        if not hits:
            continue
        found.add(rel)
        if rel in PENDING_RAW_ALERT_DIALOG:
            pending += hits
            notes.append(f"[PENDING] {rel}: raw AlertDialog 直呼び {hits} 件"
                         f"（{PENDING_RAW_ALERT_DIALOG[rel]}）＝NovelReaderAlertDialog へ差し替え待ち")
        else:
            failures.append(f"[NG] {rel}: raw AlertDialog を直呼びしている（{hits} 件）。本文が M3 既定の "
                            f"onSurfaceVariant（装飾専用スロット・ADR 0014-D）で描かれ AA を割る＝"
                            f"theme/NovelReaderAlertDialog.kt を使うこと")
            ng += 1
    for rel in sorted(PENDING_RAW_ALERT_DIALOG):
        if rel in found:
            continue
        if (UI_SRC_DIR / rel).exists():
            notes.append(f"[INFO] 移行台帳の {rel} は raw 直呼びが消えた＝差し替え済み。行を削除して締め直す")
        else:
            failures.append(f"[NG] 移行台帳の {rel} が存在しない（ファイル移動・改名＝台帳の行が死んでいる）")
            ng += 1
    # 差し替え済みの件数（ラッパ定義ファイル自身は数えない）＝移行の進捗が数字で見える。
    ok = sum(len(re.findall(r"NovelReaderAlertDialog\s*\(", _strip_comments(p.read_text(encoding="utf-8"))))
             for p in UI_SRC_DIR.rglob("*.kt")
             if p.relative_to(UI_SRC_DIR).as_posix() != RAW_ALERT_DIALOG_WRAPPER)
    return ok, ng, pending

def main() -> int:
    tokens = parse_color_kt()
    ok = ng = skip = 0
    failures: list[str] = []
    # SKIP の内訳（識別子＋理由）。総数だけだと「どの照合が対象外へ落ちたか」が追えず、
    # ベースライン超過時の原因特定も目視更新（手順は SKIP_BASELINE コメント）もできないため列挙する。
    skip_details: list[str] = []
    observed_skips: set[str] = set()   # 実際に SKIP になった別名グループのラベル
    group_labels: set[str] = set()     # 評価した別名グループのラベル（SKIP に落ちたかは問わない）
    stale_skip_notes: list[str] = []   # EXPECTED_SKIPS の締め直し案内

    def check(label: str, actual: list[str], expected: str, token_name: str) -> None:
        nonlocal ok, ng
        if expected in actual:
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
        groups = STANDARD_VAR_GROUPS if rel in STANDARD_FILES else SHELF_VAR_GROUPS
        for names, token_name in groups:
            glabel = f"{rel} {'|'.join(names)}"
            group_labels.add(glabel)
            expected = tokens.get(token_name)
            if expected is None:
                failures.append(f"[NG] {glabel}: トークン {token_name} が Color.kt に見つからない")
                ng += 1
                continue
            # 別名は「どれか 1 つが在れば足りる」ではなく「在るものは全部照合する」。
            # 片方だけ見て打ち切ると、両方宣言されたモックで残りが無検査になるため。
            declared = [(n, d) for n in names if (d := find_decls(text, n))]
            if not declared:
                skip += 1
                observed_skips.add(glabel)
                reason = EXPECTED_SKIPS.get(glabel)
                if reason is None:
                    failures.append(
                        f"[NG] 未文書化の SKIP: {glabel}（対応トークン {token_name}）。"
                        f"モックの変数リネーム等で照合が静かに落ちた疑い＝原因を確かめ、"
                        f"意図的なら理由を EXPECTED_SKIPS へ登録する")
                    ng += 1
                else:
                    skip_details.append(f"[SKIP] {glabel}（対応トークン {token_name}）: {reason}")
                continue
            for name, decls in declared:
                check(f"{rel} {name}", decls, expected, token_name)

    # 各スキンの reading: テーマ順序照合（SKIN_READING 表駆動。D は移設前と完全同値）
    for skin_id, spec in SKIN_READING.items():
        reading = parse_reading_colors(spec["kt_file"])
        mock_file = spec["mock"]
        order = spec["order"]
        rpath = MOCK_DIR / mock_file
        if not rpath.exists():
            failures.append(f"[NG] 正本モックが見つからない: {mock_file}（スキン {skin_id}）")
            ng += 1
            continue
        text = rpath.read_text(encoding="utf-8")
        for var, field in spec["vars"].items():
            decls = find_decls(text, var)
            if len(decls) != len(order):
                failures.append(f"[NG] {mock_file} {var}: {len(order)}テーマ宣言のはずが {len(decls)} 件（順序前提が崩れた＝要保守）")
                ng += 1
                continue
            for theme, decl in zip(order, decls):
                expected = reading.get(theme, {}).get(field)
                if expected is None:
                    failures.append(f"[NG] {mock_file} {var}({theme}): ReadingColors.{field} を {spec['kt_file']} から抽出できない")
                    ng += 1
                elif decl == expected:
                    ok += 1
                else:
                    failures.append(f"[NG] {mock_file} {var}({theme}): モック #{decl} ⇄ ReadingColors.{field}=#{expected}")
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

    # EXPECTED_SKIPS の逆照合（CONTRAST_BASELINE と同じ保守則）。
    #   - 表にあるのに SKIP しなくなった＝照合が復活した → INFO（良化なので止めず締め直しを促す）
    #   - そもそも評価対象に現れない＝ファイル名や別名グループが drift した → NG（表が死んでいる）
    for glabel in sorted(EXPECTED_SKIPS):
        if glabel in observed_skips:
            continue
        if glabel in group_labels:
            stale_skip_notes.append(f"[INFO] EXPECTED_SKIPS の {glabel} は照合が復活した"
                                    f"（変数宣言がモックに現れた）。行を削除して締め直す")
        else:
            failures.append(f"[NG] EXPECTED_SKIPS の項目 {glabel} が検査対象に現れない"
                            f"（ファイル表・別名グループの drift＝この行が死んでいる）")
            ng += 1

    # a11y コントラスト（WCAG 4.5:1）。既存の failures へ [NG] を積み、内訳は notes へ。
    contrast_notes: list[str] = []
    c_ok, contrast_ng, c_base, c_skip, c_total = check_contrast(failures, contrast_notes)
    ng += contrast_ng

    # ダイアログ本文色の迂回（raw AlertDialog 直呼び）。値でなく呼び出し側の規律なので別集計。
    d_ok, dialog_ng, d_pending = check_raw_alert_dialog(failures, contrast_notes)
    ng += dialog_ng

    print(f"design token check: OK={ok} NG={ng} SKIP={skip}")
    print(f"dialog wrapper lint: WRAPPED={d_ok} NG={dialog_ng} PENDING={d_pending}")
    print(f"spacing phase(b) check: NG={compose_ng} WARN={compose_warn}")
    print(f"a11y contrast check: PASS={c_ok} NG={contrast_ng} BASELINE={c_base} "
          f"OUT-OF-SCOPE={c_skip} (pairs={c_total})")
    for line in failures:
        print(line)
    for line in contrast_notes:
        print(line)
    for line in stale_skip_notes:
        print(line)
    for line in skip_details:
        print(line)

    # SKIP ベースライン照合（機序・更新手順は SKIP_BASELINE の定義コメント）。
    # 超過のみ fail: 減少は照合カバレッジの増加＝良化なので止めず、締め直しを促すだけにする。
    baseline_breach = skip > SKIP_BASELINE
    if baseline_breach:
        print(f"[NG] SKIP={skip} がベースライン {SKIP_BASELINE} を超過: 照合が SKIP へ漏れた"
              f"（モックの変数リネーム等）疑い。上の [SKIP] 一覧を精査し、意図的なら SKIP_BASELINE を更新")
    elif skip < SKIP_BASELINE:
        print(f"[INFO] SKIP={skip} < ベースライン {SKIP_BASELINE}: 照合カバレッジが増えた。"
              f"SKIP_BASELINE を {skip} へ締め直し推奨")
    return 1 if (ng or baseline_breach) else 0

if __name__ == "__main__":
    sys.exit(main())
