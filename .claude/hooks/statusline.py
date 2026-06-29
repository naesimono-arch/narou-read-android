#!/usr/bin/env python3
"""
statusLine コマンド: 現在のブランチ／worktree を1行で常時表示する。

なぜこのフックが要るか:
  Claude Code の auto-memory と session history はディレクトリパスに紐付き、ブランチを
  考慮しない。長時間セッションの圧縮で「今どのブランチか」が要約から脱落し、誤ったブランチ
  （特に main）へ直接コミットする事故が起こりうる。statusLine は会話コンテキストの「外」で
  常時表示されるため、圧縮の影響を受けずブランチ識別を生存させられる。

なぜ git 呼び出しを1回にまとめ、import を絞るか:
  statusLine は入力待受やツール実行の前後で頻繁に呼ばれる。Windows はプロセス生成が遅いため、
  python 起動＋git サブプロセスを毎回走らせると体感遅延になる。git は1回（複数値を一括取得）に
  徹し、re など重い／不要なモジュールは import しない。
"""
import json
import subprocess
import sys

# Windows の既定コンソールは cp932 で、絵文字(📂🌿)を encode できず UnicodeEncodeError になる。
# 追加 import を避けつつ stdout を UTF-8 に再構成する（reconfigure は Python 3.7+ で利用可）。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


def main():
    # stdin の JSON は将来利用に備えて読むだけ（現状は git の事実のみで描画する）
    try:
        json.load(sys.stdin)
    except (json.JSONDecodeError, EOFError, ValueError):
        pass

    # 1回の git 呼び出しで「ブランチ名・作業ツリールート・common dir」を一括取得する。
    # --git-common-dir は全 worktree で共通の .git を指し、--show-toplevel は当該 worktree の
    # ルートを指す。リンク worktree では両者がずれるため、これでメイン/リンクを判定できる。
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD",
             "--show-toplevel", "--git-common-dir", "--git-dir"],
            capture_output=True, text=True, timeout=5,
        )
    except Exception:
        print("📂 (git unavailable)")
        return

    if out.returncode != 0:
        print("📂 (not a git repo)")
        return

    lines = [ln for ln in out.stdout.splitlines() if ln.strip() != ""]
    branch = lines[0] if len(lines) > 0 else "?"
    toplevel = lines[1] if len(lines) > 1 else ""
    common_dir = lines[2] if len(lines) > 2 else ""
    git_dir = lines[3] if len(lines) > 3 else ""

    # プロジェクト名は作業ツリールートのベース名
    project = toplevel.replace("\\", "/").rstrip("/").rsplit("/", 1)[-1] if toplevel else "?"

    # detached HEAD のとき git は "HEAD" を返す。ブランチ未所属である事実をそのまま見せる。
    if branch == "HEAD":
        branch = "(detached)"

    # worktree 判定: common dir が当該 worktree 直下の ".git" ならメイン、そうでなければリンク。
    # 例) メイン: common_dir="<top>/.git" / リンク: common_dir="<main>/.git"（別ツリーを指す）
    norm_common = common_dir.replace("\\", "/").rstrip("/")
    norm_main_git = (toplevel.replace("\\", "/").rstrip("/") + "/.git") if toplevel else ""
    if norm_common in ("", ".git", norm_main_git):
        wt = "main"
    else:
        # リンク worktree。--git-dir は <main>/.git/worktrees/<name> を指すのでその末尾名を採る。
        # （--git-common-dir は共通 .git を指し worktree 名を含まないため、名前付けには使えない）
        wt = git_dir.replace("\\", "/").rstrip("/").rsplit("/", 1)[-1] or "linked"

    print(f"📂 {project} | 🌿 {branch} | wt:{wt}")


if __name__ == "__main__":
    main()
