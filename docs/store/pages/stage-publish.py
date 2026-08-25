#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""プライバシーポリシー公開の「機械でできる分」を一発で済ませる（2026-08-25 追加）。

README「公開の手順（人間の作業）」のうち、**GitHub のアカウント操作以外**をまとめて実行する:
  1. `../privacy-policy-draft.md` の公開本文にある `【公開日】` を実行日（または --date）で置換
  2. `build.py` で `privacy.html` を再生成
  3. 公開用の4ファイルを公開リポジトリ用ディレクトリへ平置きコピー（既定＝`<project>/yosari-pages`）
  4. そこで git commit（リポジトリが無ければ init）
  5. 残る人間の作業（GitHub でリポジトリ作成 → push → Pages 有効化）を実コマンドで表示

⚠️ **実行するのは「実際に公開する当日」**。制定日は公開日と一致していなければならず、
   仮の日付を先に入れないことが README の裁定（前倒しの日付は事実と食い違う）。
⚠️ 置換は `privacy-policy-draft.md` 本体を書き換える（正本が md のため。README の手順どおり）。
⚠️ 置換対象は**公開本文区間の中だけ**。md の前書きにも `【公開日】` を説明する行があり、
   全文置換すると注意書きが日付に化ける（2026-08-25 の空実行で実際に踏んだ）。

使い方:
    python stage-publish.py                 # 今日の日付で
    python stage-publish.py --date 2026年9月1日
    python stage-publish.py --dry-run       # 何をするかだけ表示
"""
from __future__ import annotations

import argparse
import datetime
import shutil
import subprocess
import sys
from pathlib import Path

NL = chr(10)
HERE = Path(__file__).resolve().parent
MD = HERE.parent / "privacy-policy-draft.md"
PLACEHOLDER = "【公開日】"
BEGIN = "▼公開本文▼"
END = "▲公開本文ここまで▲"
FILES = ("privacy.html", "index.html", "style.css", ".nojekyll")
DEFAULT_PUB = HERE.parents[3] / "yosari-pages"   # <project>/yosari-pages
REPO_SLUG = "naesimono-arch/yosari"
SITE = "https://naesimono-arch.github.io/yosari/"


def jp_today() -> str:
    d = datetime.date.today()
    return "%d年%d月%d日" % (d.year, d.month, d.day)


def run(cmd, cwd=None, check=True):
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and r.returncode != 0:
        sys.exit("失敗: %s%s%s%s" % (" ".join(cmd), NL, r.stdout, r.stderr))
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", default=jp_today(), help="制定日（例 2026年9月1日）。既定＝今日")
    ap.add_argument("--out", default=str(DEFAULT_PUB), help="公開リポジトリ用ディレクトリ")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    out = Path(a.out)

    md = MD.read_text(encoding="utf-8")
    lines = md.split(NL)
    try:
        b = next(i for i, t in enumerate(lines) if t.strip() == BEGIN)
        e = next(i for i, t in enumerate(lines) if t.strip() == END)
    except StopIteration:
        sys.exit("公開本文マーカーが見つからない（%s / %s）" % (BEGIN, END))
    body = NL.join(lines[b + 1:e])
    n = body.count(PLACEHOLDER)
    if n == 0:
        print("※ 公開本文に %s は残っていない＝置換済みとみなす（制定日を目で確認すること）" % PLACEHOLDER)
    else:
        print("1. %s を %s に置換（公開本文の %d 箇所のみ）" % (PLACEHOLDER, a.date, n))
        if not a.dry_run:
            lines[b + 1:e] = body.replace(PLACEHOLDER, a.date).split(NL)
            MD.write_text(NL.join(lines), encoding="utf-8", newline=NL)

    print("2. build.py で privacy.html を再生成")
    if not a.dry_run:
        run([sys.executable, str(HERE / "build.py")])

    print("3. 4ファイルを %s へ平置きコピー" % out)
    if not a.dry_run:
        out.mkdir(parents=True, exist_ok=True)
        for f in FILES:
            src = HERE / f
            if not src.exists():
                sys.exit("公開ファイルが無い: %s" % src)
            shutil.copyfile(src, out / f)

    print("4. commit")
    if not a.dry_run:
        if not (out / ".git").exists():
            run(["git", "init", "-b", "main"], cwd=out)
        run(["git", "add", "-A"], cwd=out)
        st = run(["git", "status", "--porcelain"], cwd=out)
        if st.stdout.strip():
            run(["git", "commit", "-m", "プライバシーポリシーを %s 制定として公開する" % a.date], cwd=out)
        else:
            print("   変更なし＝commit しない")

    print(NL.join([
        "",
        "─" * 60,
        "ここから先は GitHub のアカウント操作＝人間の作業（ログインが要る）",
        "",
        "  ① https://github.com/new で public リポジトリ `%s` を作る" % REPO_SLUG,
        "     （README/.gitignore/ライセンスは付けない＝空で作る）",
        "  ② 下を実行して push",
        '       cd "%s"' % out,
        "       git remote add origin https://github.com/%s.git" % REPO_SLUG,
        "       git push -u origin main",
        "  ③ Settings → Pages → Source = Deploy from a branch / main / (root) → Save",
        "  ④ 数分後に開いて確認  %sprivacy.html" % SITE,
        "     ・404 でない ・文字が組まれている（素の白地なら CSS が 404）",
        "     ・制定日が %s になっている ・連絡先が colophon.apps@gmail.com" % a.date,
        "  ⑤ Play Console へ登録（アプリのコンテンツ → プライバシーポリシー）",
        "─" * 60,
    ]))


if __name__ == "__main__":
    main()
