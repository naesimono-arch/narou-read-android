#!/usr/bin/env python3
"""スキン候補モック10種を1枚に並べた比較ギャラリー HTML を生成する。

なぜ自己完結型（iframe srcdoc 埋め込み）か:
  プレビューは mockview（単一ファイルのスナップショットコピー）経由が必須のため、
  相対パス iframe はコピー先で切れる。各モックを HTML エスケープして srcdoc に
  埋め込めば1ファイルで完結し、mockview の一意名コピーでも壊れない。

使い方: python3 tools/build_skin_gallery.py
出力:   docs/design-candidates/skins/candidates/_gallery-all.html
差し戻しループで候補モックを更新したら再実行して再生成する。
"""

import html
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "docs" / "design-candidates"
CAND = ROOT / "skins" / "candidates"
OUT = CAND / "_gallery-all.html"

# 表示順。src は CURRENT.md 索引に従う（D の現行見た目＝栞・最終形モック）。
# 経緯: 旧A/B/E/F/G/H/I 差し戻し→撤去、K絵巻はテーマごと退役。新規7＋後継2を生成し
# 全案にデザインダイジェスト（.claude/plans/skin-design-digest-2026-07-17.md）の磨き込みパスを適用。
# 2026-07-17 最終判定（ユーザー「qpm」＋J追加）: M/P/Q/J=実装対象・L/N/O/R/S=保留（モックは保全・凍結）。
# status: adopted=採用済み / impl=実装対象 / kept=候補残留（実装対象外） / hold=保留
SKINS = [
    ("D", "和モダン・余白(現行既定)", "栞・最終形＝現行の見た目の正本（CURRENT.md）。比較の基準", "adopted",
     ROOT / "bookshelf-shiori-grid-D.html"),
    ("C", "夜行", "深炭×温白の没入。採用済み（色層実装済み・構造/演出層は別タスク）", "adopted",
     ROOT / "skins" / "bookshelf-C.html"),
    ("M", "星図", "読んだ分だけ夜空に星が灯る。作品＝星座・結線＝進捗", "impl",
     ROOT / "skins" / "bookshelf-M.html"),
    ("P", "カートリッジ", "物語＝ゲームカセット。彩度を律したレトロポップ（目次＝はっちゃけ採用）", "impl",
     ROOT / "skins" / "bookshelf-P.html"),
    ("Q", "読書の庭", "作品＝庭に育つ草木。2026-07-17 差し戻し＆保留（指摘多数）", "hold",
     CAND / "bookshelf-Q.html"),
    ("J", "ポータル・デッキ", "1作1画面の没入扉を横スワイプ", "impl",
     CAND.parent / "bookshelf-J.html"),
    ("L", "発車標", "本棚＝駅の発車案内板。続きから＝まもなく発車", "hold",
     CAND / "bookshelf-L.html"),
    ("N", "文机", "本棚でなく「読みかけの机の上」俯瞰。伏せた本＝読書中", "hold",
     CAND / "bookshelf-N.html"),
    ("O", "短冊", "縦書き短冊のタイポ主軸。進捗＝短冊の染まり", "hold",
     CAND / "bookshelf-O.html"),
    ("R", "文（ふみ）", "連載＝作者から届く長い手紙。未読＝封蝋の封筒", "hold",
     CAND / "bookshelf-R.html"),
    ("S", "映写室", "物語＝上映を待つフィルム。進捗＝巻き取られたリール比", "hold",
     CAND / "bookshelf-S.html"),
]

STATUS_LABEL = {
    "adopted": ("採用済み", "st-adopted"),
    "impl": ("実装対象", "st-adopted"),
    "kept": ("候補残留", "st-cand"),
    "hold": ("保留", "st-re"),
}

CSS = """
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#1A1A1E;font-family:"Yu Gothic","Noto Sans JP",sans-serif;padding:28px 24px 60px;color:#E8E6E0}
  h1{font-size:20px;letter-spacing:.12em;font-weight:600;margin-bottom:6px}
  .sub{font-size:12px;color:#9B98A0;margin-bottom:26px;line-height:1.7}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(400px,1fr));gap:26px}
  .cell{background:#242429;border:1px solid #37363E;border-radius:14px;overflow:hidden}
  .head{padding:12px 14px;display:flex;align-items:baseline;gap:10px;border-bottom:1px solid #37363E}
  .head .id{font-size:22px;font-weight:800;color:#fff;min-width:26px}
  .head .nm{font-size:14px;font-weight:700}
  .head .st{margin-left:auto;font-size:10px;padding:3px 9px;border-radius:999px;white-space:nowrap}
  .st-adopted{background:#2E4A3A;color:#9FD8B0}
  .st-cand{background:#3A3A52;color:#B9B9EE}
  .st-re{background:#52402E;color:#EECFA0}
  .desc{padding:8px 14px;font-size:11.5px;color:#B5B2BC;line-height:1.6;border-bottom:1px solid #37363E;min-height:46px}
  .frame{height:430px;overflow:hidden;position:relative;background:#111}
  .frame iframe{width:880px;height:960px;border:0;transform:scale(0.448);transform-origin:0 0;pointer-events:none}
"""


def build() -> None:
    cells = []
    for sid, name, desc, status, path in SKINS:
        content = path.read_text(encoding="utf-8")
        label, cls = STATUS_LABEL[status]
        cells.append(f"""
    <div class="cell">
      <div class="head"><span class="id">{sid}</span><span class="nm">{html.escape(name)}</span>
        <span class="st {cls}">{label}</span></div>
      <div class="desc">{html.escape(desc)}<br><small>{path.relative_to(ROOT.parent.parent)}</small></div>
      <div class="frame"><iframe srcdoc="{html.escape(content, quote=True)}" scrolling="no" title="{sid}"></iframe></div>
    </div>""")

    doc = f"""<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<title>UIスキン候補 全種ギャラリー（A〜J）</title>
<style>{CSS}</style>
</head>
<body>
  <h1>UIスキン候補 全種ギャラリー</h1>
  <p class="sub">最終判定済み（2026-07-17）: <b>M 星図・P カートリッジ・Q 読書の庭・J ポータル・デッキ＝実装対象</b>。
    L/N/O/R/S＝保留（モック保全・凍結）。<br>
    生成: tools/build_skin_gallery.py（候補モック更新後に再実行）</p>
  <div class="grid">{"".join(cells)}
  </div>
</body>
</html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"OK: {OUT} ({OUT.stat().st_size:,} bytes, {len(SKINS)} skins)")


if __name__ == "__main__":
    build()
