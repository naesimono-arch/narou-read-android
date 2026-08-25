#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ストアスクショ用「デモ蔵書」を生成してエミュレータへ流し込む。

なぜ実蔵書で撮らないか（この便の最重要判断・2026-08-25）:
エミュに入っていた蔵書9冊は **実在の小説家になろう作品（著作権存続中・作者名つき）** で、
本文HTMLも全文が入っている。ストアのスクリーンショットは *公開の宣伝物* なので、
そのまま撮ると

  ・読書画面 = 他人の著作物の本文1ページを、非公式アプリの広告として複製・公衆送信する
  ・本棚画面 = 9作品の題名（うち2件は他社の販促文字列「【書籍３巻発売】」等を含む）を
               作者の許諾なく自社アプリの装飾に使う＝黙示の推薦と読まれうる
  ・目次画面 = 同上（話タイトルの一覧）

になる。listing-draft.md §0 が置いている自衛線（「公式を装わない」「規約線に触れない」・
競合が実際に停止を食らっている＝ADR 0011/0024）と正面から衝突するため、
**掲載素材に写る文字列はすべて自作のデモ内容に差し替える**。UI・組版・ルビ描画は
本物のまま（アプリの挙動は何も偽っていない）で、写っている作品だけが架空、という状態を作る。

生成物はエクスポータ HtmlExporter.kt と同じ形（chap_N.html / index.html・
`<div class="content">`・`<ruby>親<rt>ふりがな</rt></ruby>`）＝アプリから見て実蔵書と区別がつかない。

使い方:
    python3 seed-demo-library.py --serial emulator-5564 [--push]

--push なしなら ./_demo-library/ に書き出すだけ（中身の確認用）。
--push で adb root 経由のインストール（DB の books 行の入れ替えまで）を行う。
⚠️ 実機では絶対に流さない（--serial が emulator- で始まらないと停止する）。
撮り終えたら DB はバックアップから戻す（restore は shoot 手順側＝README 参照）。
"""

import argparse
import html
import os
import shutil
import subprocess
import sys

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
PKG = "com.novelreader"
NOVELS_DIR = f"/data/user/0/{PKG}/files/novels"
DB = f"/data/data/{PKG}/databases/novel_reader_db"

STYLE = (
    "\n"
    "    <style>\n"
    '        body { background-color: #fcfaf2; color: #333; font-family: "MS Mincho", "Hiragino Mincho ProN", serif; line-height: 1.8; margin: 0; padding: 0; -webkit-text-size-adjust: 100%; }\n'
    "        .container { max-width: 600px; margin: 0 auto; padding: 20px 15px 80px 15px; background-color: #ffffff; min-height: 100vh; }\n"
    "        h1 { font-size: 1.4em; border-bottom: 2px solid #e0dcd0; padding-bottom: 10px; color: #111; }\n"
    "        .content { font-size: 1.15em; white-space: pre-wrap; word-wrap: break-word; }\n"
    "        .nav-footer { position: fixed; bottom: 0; left: 0; width: 100%; background: rgba(252, 250, 242, 0.95); border-top: 1px solid #ddd; display: flex; justify-content: space-around; padding: 15px 0; backdrop-filter: blur(5px); }\n"
    "        a { color: #8b4513; text-decoration: none; font-weight: bold; }\n"
    "        ruby rt { font-size: 0.55em; color: #777; ruby-position: over; }\n"
    "        ruby { ruby-align: center; }\n"
    "        hr { border: 0; border-top: 1px dashed #ccc; margin: 30px 0; }\n"
    "        .index-list { list-style: none; padding: 0; }\n"
    "        .index-list li { padding: 15px 0; border-bottom: 1px solid #eee; }\n"
    "    </style>\n"
    "    "
)


def r(base, yomi):
    """ふりがな（ルビ）。読書画面の TextSegment.Ruby へそのまま落ちる形。"""
    return f"<ruby>{base}<rt>{yomi}</rt></ruby>"


# ============================================================
# 本文（すべて書き下ろし＝この便で新規に書いた架空の文章。既存作品の引用は一切ない）
# ふりがなは「縦書きでルビが見える」ことを1枚目で示すため、意図的に密度を上げてある。
# ============================================================

HERO_CHAPTERS = [
    (
        "第一話　閉館のあとで",
        [
            f"　閉館の{r('鐘','かね')}が鳴ってから、この図書館はようやく本当の顔を見せる。",
            f"　わたしは{r('司書','ししょ')}のエプロンを外し、代わりに{r('燭台','しょくだい')}を手に取った。{r('灯','あか')}りはひとつで足りる。ここの書架は、月の光をよく通すように{r('設計','せっけい')}されているからだ。",
            f"　三階まで{r('階段','かいだん')}を上がると、{r('硝子','ガラス')}の天井から降りてきた光が、床に細長い川をつくっていた。その川の真ん中に、一冊だけ本が落ちている。",
            "　……また、増えている。",
            f"　{r('拾','ひろ')}い上げて表紙を{r('確','たし')}かめた。題名の部分だけが{r('掠','かす')}れて読めない。持ち主のいない本は、決まってそうなる。",
            "「今日で三冊目ですよ」",
            f"　背後から声がした。振り向かなくても分かる。{r('閲覧室','えつらんしつ')}の{r('隅','すみ')}にいつも座っている、あの子だ。",
            "「数えていたの」",
            "「{暇}ですから」".replace("{暇}", r("暇", "ひま")),
            f"　彼女は足音を立てずに近づいてきて、わたしの手元をのぞき込む。{r('薄暗','うすぐら')}がりでも、その{r('瞳','ひとみ')}の色だけははっきり見えた。",
            "「焼くんですか」",
            "「焼くわ」",
            f"　{r('躊躇','ためら')}わずに答えた。読まれることのなくなった本は、ここでは{r('朽','く')}ちるより先に燃やすことになっている。それが、この図書館にたったひとつだけある{r('規則','きそく')}だ。",
        ],
    ),
    (
        "第二話　名前のない背表紙",
        [
            f"　{r('炉','ろ')}に火を入れるのは、いつもわたしの役目だった。",
            f"　地下へ降りる{r('螺旋','らせん')}階段は、三十七段ある。数えなくても足が{r('憶','おぼ')}えている。降りきったところに、{r('煉瓦','れんが')}を{r('積','つ')}んだ古い炉が{r('据','す')}えられていた。",
            f"「その本、{r('抵抗','ていこう')}しませんね」",
            "　うしろからついてきた彼女が言う。",
            "「抵抗する本もあるの」",
            f"「ありますよ。{r('頁','ページ')}を{r('閉','と')}じさせなかったり、火の中で{r('題名','だいめい')}だけ残したり」",
            f"　わたしは手のなかの本を見下ろした。{r('確','たし')}かに、これはおとなしい。まるで、もう{r('充分','じゅうぶん')}に読まれたと言いたげだった。",
            f"「……{r('待','ま')}って」",
            f"　背表紙に、{r('薄','うす')}く{r('文字','もじ')}が{r('浮','う')}かびはじめている。",
        ],
    ),
    (
        "第三話　読まれなかった一行",
        [
            f"　{r('浮','う')}かび上がった文字は、たった一行だった。",
            f"　わたしはその行を{r('三','み')}度読み返し、それから炉の{r('扉','とびら')}を閉めた。火は入れなかった。",
            "「焼かないんですか」",
            f"「{r('今夜','こんや')}は」",
            f"　彼女は{r('納得','なっとく')}したようなしていないような顔で、{r('肩','かた')}をすくめた。",
            f"　階段を{r('昇','のぼ')}りながら、わたしはその一行を{r('頭','あたま')}のなかで{r('繰','く')}り返していた。読まれなかった本にも、{r('宛先','あてさき')}はある。ただ、届く前に{r('忘','わす')}れられただけだ。",
            f"　窓の外では、月が{r('中天','ちゅうてん')}にかかっていた。夜はまだ長い。",
        ],
    ),
]

# 以降の章（本文の中身は棚・目次の見栄えにしか写らないので短め・ただし全て書き下ろし）
FILLER = [
    [
        f"　その{r('報','しら')}せが届いたのは、{r('朝','あさ')}の{r('霧','きり')}がまだ{r('晴','は')}れないうちだった。",
        f"　わたしは{r('手紙','てがみ')}を{r('二','ふた')}つに{r('折','お')}り、{r('上着','うわぎ')}の内{r('側','がわ')}へしまった。",
        "「行くんですね」",
        "「行くわ」",
    ],
    [
        f"　{r('街道','かいどう')}を外れると、{r('景色','けしき')}は急に{r('静','しず')}かになった。",
        f"　{r('轍','わだち')}の跡が{r('途切','とぎ')}れ、代わりに{r('背','せ')}の高い草が{r('道','みち')}を{r('覆','おお')}っている。",
        f"　わたしは{r('地図','ちず')}をしまった。ここから先は、{r('憶','おぼ')}えのある道だ。",
    ],
    [
        "「それで、どうするつもりですか」",
        f"　{r('彼','かれ')}の問いに、わたしはすぐには答えられなかった。",
        f"　{r('答','こた')}えを{r('持','も')}っていなかったからではない。持っている答えが、{r('気','き')}に入らなかったからだ。",
    ],
    [
        f"　{r('炎','ほのお')}は{r('思','おも')}ったより{r('静','しず')}かに{r('燃','も')}えた。",
        f"　{r('灰','はい')}になるまでのあいだ、わたしたちはひと{r('言','こと')}も{r('喋','しゃべ')}らなかった。",
        f"　{r('終','お')}わってから、彼女が小さく「{r('御疲','おつか')}れさま」と言った。",
    ],
    [
        f"　{r('冬','ふゆ')}が{r('近','ちか')}い。",
        f"　{r('書架','しょか')}の{r('隙間','すきま')}から{r('風','かぜ')}が{r('入','はい')}るようになり、わたしは{r('毛布','もうふ')}を一枚{r('増','ふ')}やした。",
        f"　こういう夜は、なぜか{r('客','きゃく')}が{r('多','おお')}い。",
    ],
    [
        f"　{r('約束','やくそく')}は{r('果','は')}たされた。",
        f"　ただ、{r('果','は')}たされ方が、わたしの{r('想像','そうぞう')}とは{r('少','すこ')}しだけ{r('違','ちが')}っていた。",
        f"　それでも{r('構','かま')}わない、と{r('思','おも')}えるようになるまでに、また{r('幾','いく')}つかの{r('季節','きせつ')}が{r('要','い')}った。",
    ],
]


def chapter_body(book_idx, chap_idx):
    """章本文。1冊目の最初の3章だけ書き下ろし本編、以降は共通の書き下ろし断片を回す。"""
    if book_idx == 0 and chap_idx < len(HERO_CHAPTERS):
        return "\n".join(HERO_CHAPTERS[chap_idx][1])
    block = FILLER[(book_idx * 3 + chap_idx) % len(FILLER)]
    return "\n".join(block)


def chapter_title(book_idx, chap_idx, book):
    if book_idx == 0 and chap_idx < len(HERO_CHAPTERS):
        return HERO_CHAPTERS[chap_idx][0]
    return book["chapfmt"].format(n=chap_idx + 1)


def chapter_html(safe_title, body, prev_page, next_page):
    return (
        "\n"
        "        <!DOCTYPE html>\n"
        '        <html lang="ja">\n'
        "        <head>\n"
        '            <meta charset="UTF-8">\n'
        '            <meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
        f"            <title>{safe_title}</title>\n"
        f"            {STYLE}\n"
        "        </head>\n"
        "        <body>\n"
        '            <div class="container">\n'
        f"                <h1>{safe_title}</h1>\n"
        '                <div class="content">\n'
        f"{body}\n"
        "                </div>\n"
        "            </div>\n"
        "\n"
        '            <div class="nav-footer">\n'
        f'                <a href="{prev_page}">← 前へ</a>\n'
        '                <a href="index.html">目次</a>\n'
        f'                <a href="{next_page}">次へ →</a>\n'
        "            </div>\n"
        "\n"
        "        </body>\n"
        "        </html>\n"
        "        "
    )


def export_book(out_root, book_idx, book):
    d = os.path.join(out_root, book["id"])
    os.makedirs(d, exist_ok=True)
    heading = html.escape(book["title"])
    index = [
        "\n"
        "    <!DOCTYPE html>\n"
        '    <html lang="ja">\n'
        "    <head>\n"
        '        <meta charset="UTF-8">\n'
        '        <meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
        f"        <title>{heading} - 目次</title>\n"
        f"        {STYLE}\n"
        "    </head>\n"
        "    <body>\n"
        '        <div class="container">\n'
        f"            <h1>{heading}</h1>\n"
        '            <ul class="index-list">\n'
        "    "
    ]
    n = book["chapters"]
    for i in range(n):
        fn = f"chap_{i + 1}.html"
        t = html.escape(chapter_title(book_idx, i, book))
        index.append(f'<li><a href="{fn}">{t}</a></li>')
        prev_page = f"chap_{i}.html" if i > 0 else "index.html"
        next_page = f"chap_{i + 2}.html" if i < n - 1 else "index.html"
        with open(os.path.join(d, fn), "w", encoding="utf-8") as f:
            f.write(chapter_html(t, chapter_body(book_idx, i), prev_page, next_page))
    index.append(
        "\n"
        "            </ul>\n"
        "        </div>\n"
        "    </body>\n"
        "    </html>\n"
        "    "
    )
    with open(os.path.join(d, "index.html"), "w", encoding="utf-8") as f:
        f.write("".join(index))
    return d


# ============================================================
# デモ蔵書の一覧（題名・作者名とも架空＝この便の書き下ろし）
# addedAt / progress は「棚が生きて見える」状態を作るために配る。
# shioriTipIndex / shioriLenFrac は書影の栞意匠（SHIORI_TIPS）＝見た目を散らすために別々の値にする。
# ============================================================
BOOKS = [
    dict(id="demo01", title="月光の図書館で、司書は今日も本を焼く",
         author="東雲　あかり", chapters=24, chapfmt="第{n}話　夜のつづき", tip=3, frac=0.62),
    dict(id="demo02", title="北の砦の料理番　〜冷めないスープの作り方〜",
         author="白瀬　とおる", chapters=18, chapfmt="{n}皿目　today's soup", tip=11, frac=0.48),
    dict(id="demo03", title="雨の日だけ開く古書店の話",
         author="三上　ゆき", chapters=12, chapfmt="第{n}話", tip=27, frac=0.71),
    dict(id="demo04", title="わたしの担当編集は幽霊です",
         author="森野　かなで", chapters=9, chapfmt="第{n}回　締切まであと少し", tip=42, frac=0.35),
    dict(id="demo05", title="星をひろう仕事　〜夜間巡回員の記録〜",
         author="高梨　いつき", chapters=31, chapfmt="記録{n}　巡回の夜", tip=8, frac=0.80),
    dict(id="demo06", title="三年後、私は約束の駅で待っている",
         author="南雲　ひかる", chapters=7, chapfmt="第{n}話", tip=19, frac=0.55),
    dict(id="demo07", title="転生したら辺境の茶屋でした　〜お茶を淹れるだけで英雄が集まってくる〜",
         author="秋月　そら", chapters=42, chapfmt="第{n}話　本日も営業中", tip=33, frac=0.44),
    dict(id="demo08", title="霧の街のスケッチブック",
         author="深沢　まこと", chapters=15, chapfmt="{n}枚目のスケッチ", tip=51, frac=0.67),
    dict(id="demo09", title="さよならの手前で、もう一度きみに会う方法",
         author="柊木　れん", chapters=20, chapfmt="第{n}話", tip=24, frac=0.58),
]


def sh(serial, cmd, check=True):
    p = subprocess.run([ADB, "-s", serial, "shell", cmd],
                       capture_output=True, text=True)
    if check and p.returncode != 0:
        sys.exit(f"adb shell failed: {cmd}\n{p.stderr}")
    return p.stdout.strip()


def sql(serial, statement):
    esc = statement.replace('"', '\\"')
    return sh(serial, f'sqlite3 {DB} "{esc}"')


def push(serial, out_root):
    if not serial.startswith("emulator-"):
        sys.exit("実機には流さない（--serial は emulator-XXXX のみ）")
    subprocess.run([ADB, "-s", serial, "root"], capture_output=True, text=True)
    subprocess.run(["sleep", "1"])
    uid = sh(serial, f"stat -c '%u:%g' {NOVELS_DIR}")
    ctx = sh(serial, f"ls -Zd {NOVELS_DIR}").split()[0]
    print(f"  owner={uid} secontext={ctx}")

    sh(serial, "rm -rf /data/local/tmp/demolib")
    sh(serial, "mkdir -p /data/local/tmp/demolib")
    for b in BOOKS:
        subprocess.run([ADB, "-s", serial, "push", os.path.join(out_root, b["id"]),
                        "/data/local/tmp/demolib/"], capture_output=True, text=True)
    for b in BOOKS:
        sh(serial, f"rm -rf {NOVELS_DIR}/{b['id']}")
        sh(serial, f"cp -r /data/local/tmp/demolib/{b['id']} {NOVELS_DIR}/{b['id']}")
    sh(serial, f"chown -R {uid} {NOVELS_DIR}")
    sh(serial, f"restorecon -R {NOVELS_DIR}")
    sh(serial, "rm -rf /data/local/tmp/demolib")

    # 実蔵書（実在作品）の行を books から外し、デモ9冊だけが棚に出る状態にする。
    # HTML 実体は消さない＝DB をバックアップから戻せば元通り（この便は非破壊）。
    sql(serial, "DELETE FROM progress;")
    sql(serial, "DELETE FROM books;")
    base = 1756000000000
    for i, b in enumerate(BOOKS):
        t = b["title"].replace("'", "''")
        a = b["author"].replace("'", "''")
        sql(serial,
            "INSERT INTO books (id,title,htmlDirPath,author,addedAt,ncode,contentSha256,"
            f"shioriTipIndex,shioriLenFrac,sourceUri,sourceUrl,sourceSite) VALUES "
            f"('{b['id']}','{t}','{NOVELS_DIR}/{b['id']}','{a}',{base + i * 3600000},"
            f"NULL,NULL,{b['tip']},{b['frac']},NULL,NULL,NULL);")
    print(f"  books={sql(serial, 'SELECT COUNT(*) FROM books;')}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--push", action="store_true")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "_demo-library"))
    a = ap.parse_args()

    if os.path.isdir(a.out):
        shutil.rmtree(a.out)
    os.makedirs(a.out, exist_ok=True)
    for i, b in enumerate(BOOKS):
        export_book(a.out, i, b)
        print(f"  {b['id']}: {b['chapters']}章  {b['title'][:28]}")
    print(f"generated -> {a.out}")

    if a.push:
        push(a.serial, a.out)
        print("pushed.")


if __name__ == "__main__":
    main()
