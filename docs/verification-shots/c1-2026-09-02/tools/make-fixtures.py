#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""エミュに「実機では作れない前提」を作るための蔵書ファブリケータ（2026-08-26・便B2）。

seed-demo-library.py と同じ HtmlExporter 互換の形（index.html + chap_N.html）を吐き、
books 行を直接 INSERT する。実機の実蔵書では踏めない発火条件を作るのが目的:

  --chapters 1240   4桁話ラベル（目次 .ep 列の折り返し）を出す本
  --missing         生成後に index.html を消す（本文欠落＝reimportPlans に載る）
  --sha <hex>       contentSha256 を任意値で入れる（フォルダ走査の照合キー）
  --source-uri なし = PickPdfNoRecord ＝ 3ボタン縦積みの復旧ダイアログ／走査対象

usage: make-fixtures.py --id ep4d --title '…' --author '…' --chapters 1240 [--missing] [--sha <hex>]
"""
import argparse, html, os, shutil, subprocess, sys, tarfile, tempfile

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
DEV = os.environ.get("NR_SERIAL", "emulator-5554")
PKG = "com.novelreader"
NOVELS = f"/data/data/{PKG}/files/novels"
DB = f"/data/data/{PKG}/databases/novel_reader_db"

STYLE = ('<style>body{background:#fcfaf2;color:#333;font-family:serif;line-height:1.8;margin:0}'
         '.container{max-width:600px;margin:0 auto;padding:20px 15px 80px}'
         '.content{font-size:1.15em;white-space:pre-wrap}</style>')


def sh(cmd):
    p = subprocess.run([ADB, "-s", DEV, "shell", cmd], capture_output=True, text=True)
    return p.stdout.strip()


def sql(stmt):
    esc = stmt.replace('"', '\\"')
    return sh(f'sqlite3 {DB} "{esc}"')


def build(out, book_id, title, author, n, chapfmt):
    d = os.path.join(out, book_id)
    os.makedirs(d, exist_ok=True)
    items = []
    for i in range(1, n + 1):
        t = html.escape(chapfmt.format(n=i))
        items.append(f'<li><a href="chap_{i}.html">{t}</a></li>')
        prev = f"chap_{i-1}.html" if i > 1 else "index.html"
        nxt = f"chap_{i+1}.html" if i < n else "index.html"
        body = (f"<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\">"
                f"<title>{t}</title>{STYLE}</head><body><div class=\"container\"><h1>{t}</h1>"
                f"<div class=\"content\">これは検証用の書き下ろしダミー本文です（第{i}節）。"
                f"<ruby>検証<rt>けんしょう</rt></ruby>のための文字が並んでいるだけで、意味はありません。</div>"
                f"<div class=\"nav-footer\"><a href=\"{prev}\">前へ</a><a href=\"index.html\">目次</a>"
                f"<a href=\"{nxt}\">次へ</a></div></div></body></html>")
        open(os.path.join(d, f"chap_{i}.html"), "w", encoding="utf-8").write(body)
    idx = (f"<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\">"
           f"<title>{html.escape(title)} - 目次</title>{STYLE}</head><body><div class=\"container\">"
           f"<h1>{html.escape(title)}</h1><ul class=\"index-list\">{''.join(items)}</ul></div></body></html>")
    open(os.path.join(d, "index.html"), "w", encoding="utf-8").write(idx)
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--author", default="検証　太郎")
    ap.add_argument("--chapters", type=int, default=12)
    ap.add_argument("--chapfmt", default="第{n}話")
    ap.add_argument("--missing", action="store_true", help="index.html を消して本文欠落にする")
    ap.add_argument("--sha", default=None, help="contentSha256（走査の照合キー）")
    ap.add_argument("--added-at", type=int, default=1756000000000)
    a = ap.parse_args()
    if not DEV.startswith("emulator-"):
        sys.exit("実機には流さない")

    subprocess.run([ADB, "-s", DEV, "root"], capture_output=True, text=True)
    tmp = tempfile.mkdtemp()
    d = build(tmp, a.id, a.title, a.author, a.chapters, a.chapfmt)
    tarpath = os.path.join(tmp, "fx.tar")
    with tarfile.open(tarpath, "w") as tf:
        tf.add(d, arcname=a.id)
    subprocess.run([ADB, "-s", DEV, "push", tarpath, "/data/local/tmp/fx.tar"], capture_output=True)
    # ★ SELinux: 展開の前に「正しいラベルと uid」を novels ディレクトリから控える（skill §3）
    ctx = sh(f"ls -Zd {NOVELS}").split()[0]
    uid = sh(f"stat -c %u:%g {NOVELS}")
    sh(f"rm -rf {NOVELS}/{a.id}")
    sh(f"cd {NOVELS} && tar xf /data/local/tmp/fx.tar && chown -R {uid} {a.id} && chcon -R '{ctx}' {a.id}")
    sh("rm -f /data/local/tmp/fx.tar")
    if a.missing:
        sh(f"rm -f {NOVELS}/{a.id}/index.html")

    t = a.title.replace("'", "''")
    au = a.author.replace("'", "''")
    sha = f"'{a.sha}'" if a.sha else "NULL"
    sql(f"DELETE FROM books WHERE id='{a.id}';")
    sql("INSERT INTO books (id,title,htmlDirPath,author,addedAt,ncode,contentSha256,"
        f"shioriTipIndex,shioriLenFrac,sourceUri,sourceUrl,sourceSite) VALUES "
        f"('{a.id}','{t}','{NOVELS}/{a.id}','{au}',{a.added_at},NULL,{sha},7,0.5,NULL,NULL,NULL);")
    print(f"{a.id}: chapters={a.chapters} missing={a.missing} sha={a.sha} ctx={ctx} uid={uid}")
    print("books=", sql("SELECT COUNT(*) FROM books;"))
    shutil.rmtree(tmp)


if __name__ == "__main__":
    main()
