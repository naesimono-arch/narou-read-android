#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow"]
# ///
"""
撮影した候補を1枚に並べた「コンタクトシート」を作る（人間の採否ゲート用）。

なぜ必要か: スクショは**候補であって完成品ではない**（2026-08-25 ユーザー裁定＝ストア系は人間ゲート必須）。
10枚をバラバラに置くと採否が判断できないので、**訴求の意図・検算結果・論点を添えて1画面で見比べられる形**にする。

⚠️ 画像は data URI で埋め込む（`mockview` は HTML 1ファイルだけを .mock-preview へコピーするため、
相対パス参照だと画像が全部切れる）。原寸は各カードのリンクから開く。

    uv run --no-project build-contact-sheet.py   # Pillow をその場で解決する（推奨）
    python3 build-contact-sheet.py               # システムに Pillow がある環境のみ
    mockview docs/store/assets/screenshots/contact-sheet.html
"""
import base64, io, os, struct, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WINDIR = "C:/Users/naesimono/Desktop/project/novel-reader_andloid/docs/store/assets/screenshots"

SHOTS = [
    ("phone-1-vertical-ruby.png", "1", "ふりがな付きのまま、縦書きで",
     "最大の差別化を1枚目に置く。ルビが実際に読める段を選んである。"),
    ("phone-2-shelf.png", "2", "取り込んだ作品は、端末の本棚に",
     "主張＝端末に置く／圏外で読める。先頭行に「よみかけ(第1/24話)」と「読了」を並べた。"),
    ("phone-3-reading-settings.png", "3", "文字の大きさも行間も、自分好みに",
     "読書アプリの評価軸。テーマ4種・向き・文字サイズ・行間・余白が1画面に出る。"),
    ("phone-4-toc.png", "4", "どこまで読んだか、ひと目で",
     "現在地チップ・全31話/読了率61%・既読✓・再開ボタン。長編を読む人の実利。"),
    ("phone-5-discover.png", "5", "なろうの検索とランキングから",
     "発見導線があることを示す＝取込専用ツールではない。対応サイト注記の置き場候補。"),
    ("phone-6-horizontal.png", "6", "縦書きでも、横書きでも",
     "横書き派を切り落とさない安心材料。§4初版の「没入読書」は1枚目と同じ絵になるため差し替えた。"),
]
TABLETS = [
    ("tablet-1-shelf.png", "T1", "大画面の本棚（5列＋ナビレール）", "先頭行に 未読・よみかけ・読了 が揃う。"),
    ("tablet-2-vertical-ruby.png", "T2", "大画面の縦書き＋ふりがな", "1話がほぼ全部見える＝タブレットの利点が出る。"),
    ("tablet-3-discover.png", "T3", "大画面のさがす", "作品が一切写らない画面。"),
    ("tablet-4-toc.png", "T4", "大画面の目次", "読了率61%・既読✓。"),
]
ALTS = [
    ("alt-shelf-list.png", "代", "本棚＝リスト表示",
     "2枚目の代案。作者名と状態（第1/24話・読了・未読）が全冊ぶん読めるが、**書影が消える**。"),
    ("alt-shelf-scrolled.png", "代", "本棚＝グリッドを送った状態",
     "2枚目の別案。状態は見えるが上段の書影が切れる。採用版はこれを使わずに済むよう並び順で解決した。"),
]

# 埋め込み解像度は「そのカードが実際に表示される CSS 幅」に合わせて決める（一律 300px にしない）。
# なぜ: スマホ(1080x1920 縦)とタブレット(2560x1600 横)を同じ 300px へ落とすと縮小率が 3.6倍 と 8.5倍 に割れ、
# タブレットのジャンルチップ(原寸 214x78px・文字 145x24px)が 25x9px／文字 2.8px まで潰れて判読不能になる。
# 2026-08-26 にこれで「素材の欠陥」と誤診が出た（原本 PNG も実機も無傷だった）＝レビュー用の道具側の欠陥。
EMBED_PHONE,  Q_PHONE  = 640,  76   # 複数列グリッド（表示 ~250px）＋ブラウザ拡大ぶんの余裕
EMBED_TABLET, Q_TABLET = 2560, 82   # 1枚1行で表示（~1800px）＝原寸のまま埋めて細部を読ませる

def png_info(p):
    with open(p,"rb") as f:
        f.read(16); w,h,d,c = struct.unpack(">IIBB", f.read(10))
    return w,h,d,c

def thumb(path, w, q):
    im = Image.open(path).convert("RGB")
    im.thumbnail((w, w*3), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "JPEG", quality=q, optimize=True)
    return base64.b64encode(b.getvalue()).decode()

def card(fn, num, caption, note, embed_w=EMBED_PHONE, q=Q_PHONE):  # w は下で原寸に使うので別名
    p = os.path.join(HERE, fn)
    if not os.path.exists(p): return f'<div class="card missing">未撮影: {fn}</div>'
    w,h,d,c = png_info(p)
    ratio = max(w,h)/min(w,h)
    ok = (c==2 and d==8 and 320<=min(w,h) and max(w,h)<=3840 and ratio<=2)
    return f"""<figure class="card">
  <a href="file:///{WINDIR}/{fn}" target="_blank"><img src="data:image/jpeg;base64,{thumb(p, embed_w, q)}" alt="{fn}"></a>
  <figcaption>
    <div class="num">{num}</div>
    <div class="cap">{caption}</div>
    <p class="note">{note}</p>
    <div class="spec {'ok' if ok else 'ng'}">{w}×{h}・比{ratio:.2f}・{'24bit PNG(αなし)' if c==2 else f'color type {c}'} — {'Play要件OK' if ok else '要件NG'}</div>
    <div class="fn">{fn}</div>
  </figcaption>
</figure>"""

html = f"""<!doctype html><html lang="ja"><head><meta charset="utf-8">
<title>ストアスクショ候補 — 採否レビュー</title>
<style>
 :root{{--ink:#1b2733;--sub:#5b6b7a;--line:#dfe5ea;--bg:#f7f8f9;--ok:#1f7a5a;--ng:#b3261e;--accent:#1C3D5A}}
 *{{box-sizing:border-box}}
 body{{margin:0;padding:32px 28px 64px;background:var(--bg);color:var(--ink);
   font-family:"Hiragino Kaku Gothic ProN","Noto Sans JP",system-ui,sans-serif;line-height:1.7}}
 h1{{font-size:22px;margin:0 0 4px}}
 .lead{{color:var(--sub);font-size:13px;margin:0 0 22px}}
 .banner{{background:#fff5e6;border:1px solid #e8c98a;border-left:4px solid #d9a441;
   padding:14px 16px;border-radius:6px;margin:0 0 22px;font-size:13px}}
 .banner b{{color:#7a5a12}}
 h2{{font-size:15px;margin:30px 0 12px;padding-bottom:6px;border-bottom:1px solid var(--line)}}
 .grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:20px}}
 .grid.wide{{grid-template-columns:1fr}}
 .hint{{font-weight:400;color:var(--sub);font-size:12px}}
 .card{{margin:0;background:#fff;border:1px solid var(--line);border-radius:8px;overflow:hidden;
   display:flex;flex-direction:column}}
 .card img{{width:100%;display:block;border-bottom:1px solid var(--line);background:#eee}}
 figcaption{{padding:11px 13px 13px}}
 .num{{display:inline-block;background:var(--accent);color:#fff;font-size:11px;font-weight:700;
   padding:1px 8px;border-radius:10px;margin-bottom:6px}}
 .cap{{font-weight:700;font-size:13.5px}}
 .note{{color:var(--sub);font-size:12px;margin:6px 0 8px}}
 .spec{{font-size:11px;font-variant-numeric:tabular-nums}}
 .spec.ok{{color:var(--ok)}} .spec.ng{{color:var(--ng);font-weight:700}}
 .fn{{color:#9aa7b3;font-size:10.5px;margin-top:3px;word-break:break-all}}
 ol{{font-size:13.5px;padding-left:22px}} ol li{{margin-bottom:9px}}
 .q{{background:#fff;border:1px solid var(--line);border-radius:8px;padding:16px 20px}}
</style></head><body>
<h1>ストア掲載スクリーンショット — <span style="color:#b3261e">候補</span>（採否は人間が決める）</h1>
<p class="lead">Yosari / Google Play・release APK（明快K）で撮影・{len(SHOTS)}枚＋タブレット{len(TABLETS)}枚。画像をクリックすると原寸が開く。</p>

<div class="banner">
<b>⚠️ これは完成した掲載素材ではない。</b> 寸法・形式は機械検算済みだが、<b>「これで提出してよいか」は未判断</b>。<br>
<b>⚠️ 写っている作品はすべて架空</b>（題名・作者名・本文とも書き下ろし）。エミュに入っていた実在のなろう作品9冊は、
宣伝物に他人の著作物と作者名を写すことになるため<b>意図的に全て差し替えた</b>。UI・組版・ルビ描画は本物のまま。
</div>

<h2>決めてほしいこと</h2>
<div class="q"><ol>
<li><b>1枚目に何を置くか</b>——現案は「縦書き＋ふりがな」（差別化が一番強い絵）。
    ただし 2026-08-25 の見直しで<b>主張は「端末に取り込んで圏外でも読める」</b>に移っている。
    主張どおり<b>2の本棚を1枚目</b>にする案もある。</li>
<li><b>2枚目の本棚はグリッドかリストか</b>——採用案はグリッド（書影が主役）。
    リスト代案は作者名と読書状態が全冊ぶん読める代わりに<b>書影が消える</b>。</li>
<li><b>6枚目に横書きを入れるか</b>——「広告なし」は<i>不在</i>なので撮れず文言側へ回した。
    横書きは可視の能力だが、1枚枠を使う価値があるか。</li>
<li><b>デモ蔵書の題名・作者名・装丁が宣伝物として妥当か</b>——なろうの語感に寄せた架空題名。
    実在作品と紛れないか／品位は保てているか。</li>
<li><b>目次の話タイトルの繰り返し</b>——「記録21 巡回の夜／記録22 巡回の夜…」と機械生成の繰り返しが見える。
    気になるなら生成器の章題を多様化して撮り直す（要デモ蔵書の再投入）。</li>
<li><b>キャプション文字を画像に焼くか</b>——listing-draft §4 は画像上にキャプションを載せる設計。
    現物は<b>素のスクショ</b>で、文字は未合成。</li>
</ol></div>

<h2>スマートフォン（1080×1920・Play は2〜8枚）</h2>
<div class="grid">{''.join(card(*s) for s in SHOTS)}</div>

<h2>タブレット（2560×1600・任意／推奨）<span class="hint">— 1枚1行・原寸埋め込み。ジャンルチップ等の細部はここで読める（縮小しても判読できる必要があるため）</span></h2>
<div class="grid wide">{''.join(card(*s, embed_w=EMBED_TABLET, q=Q_TABLET) for s in TABLETS)}</div>

<h2>代案（採否の比較用・提出候補ではない）</h2>
<div class="grid">{''.join(card(*s) for s in ALTS)}</div>
</body></html>"""

out = os.path.join(HERE, "contact-sheet.html")
open(out,"w",encoding="utf-8").write(html)
print(f"wrote {out}  ({os.path.getsize(out)/1024:.0f} KB)")
