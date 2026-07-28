#!/usr/bin/env python3
"""
statusLine コマンド: 1行目=ブランチ／worktree、2行目=モデル・コスト・利用枠・コンテキスト。

なぜこのフックが要るか（1行目）:
  Claude Code の auto-memory と session history はディレクトリパスに紐付き、ブランチを
  考慮しない。長時間セッションの圧縮で「今どのブランチか」が要約から脱落し、誤ったブランチ
  （特に main）へ直接コミットする事故が起こりうる。statusLine は会話コンテキストの「外」で
  常時表示されるため、圧縮の影響を受けずブランチ識別を生存させられる。

なぜ2行目を公式 JSON から描くか（2026-07-29 に ccusage から移行）:
  従来は外部ツール ccusage の statusline 出力を ¥換算・和訳していたが、Claude Code が
  stdin JSON で cost.total_cost_usd・context_window.used_percentage・rate_limits
  （5h/7d の消費率とリセット時刻）を公式に渡すようになった。外部集計はトランスクリプトの
  読み取りに失敗すると黙って 0 を出し続ける（実測で「¥0 セッション / 🧠 0 (0%)」が常時表示）
  のに対し、公式値は Claude Code 自身が持つ実値。かつ定額サブスクでは推定 API 額より
  「利用枠の残り」のほうが実用的なため、公式の rate_limits を主軸に据える。
  ccusage に残る役目は当月累計のみ（公式はセッション単位しか持たない）＝ --month で外から渡す。

なぜ git 呼び出しを1回にまとめ、import を絞るか:
  statusLine は入力待受やツール実行の前後で頻繁に呼ばれる。Windows はプロセス生成が遅いため、
  python 起動＋git サブプロセスを毎回走らせると体感遅延になる。git は1回（複数値を一括取得）に
  徹し、re や argparse など重い／不要なモジュールは import しない。
"""
import json
import os
import subprocess
import sys
import time
import unicodedata

# Windows の既定コンソールは cp932 で、絵文字(📂🌿)を encode できず UnicodeEncodeError になる。
# 追加 import を避けつつ stdout を UTF-8 に再構成する（reconfigure は Python 3.7+ で利用可）。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


def parse_args(argv):
    """--rate <USD→JPY 換算> と --month <当月累計の整形済み文字列> を拾う（argparse は起動コスト回避で不使用）。"""
    rate, month = 150.0, ""
    for i, a in enumerate(argv):
        if a == "--rate" and i + 1 < len(argv):
            try:
                rate = float(argv[i + 1])
            except ValueError:
                pass
        elif a == "--month" and i + 1 < len(argv):
            month = argv[i + 1]
    return rate, month


def git_line():
    """1行目: プロジェクト名・ブランチ・worktree 種別。"""
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
        return "📂 (git unavailable)"

    if out.returncode != 0:
        return "📂 (not a git repo)"

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

    return f"📂 {project} | 🌿 {branch} | wt:{wt}"


def fmt_tokens(n):
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M".replace(".0M", "M")
    if n >= 1000:
        return f"{n // 1000}k"
    return str(n)


def fmt_left(resets_at, prefix=True):
    """リセットまでの残りを日本語で。過ぎている／不明なら空文字（区画ごと出さない）。"""
    if not resets_at:
        return ""
    sec = int(resets_at) - int(time.time())
    if sec <= 0:
        return ""
    d, rem = divmod(sec, 86400)
    h, m = divmod(rem, 3600)
    m //= 60
    head = "残り" if prefix else ""
    if d:
        return f"{head}{d}日{h}時間"
    if h:
        return f"{head}{h}時間{m}分"
    return f"{head}{m}分"


def disp_width(s):
    """端末表示幅の概算（全角・絵文字＝2桁）。
    なぜ len() では駄目か: この行は絵文字と日本語が主体で、文字数と占有桁数が倍近くずれる。
    ずれたまま判定すると縮退が効かず見切れる。"""
    w = 0
    for ch in s:
        o = ord(ch)
        if o == 0xFE0F or unicodedata.combining(ch):
            continue  # 異体字セレクタ・結合文字は幅を持たない
        if 0x1F300 <= o <= 0x1FAFF or 0x2600 <= o <= 0x27BF:
            w += 2
        elif unicodedata.east_asian_width(ch) in ("W", "F"):
            w += 2
        else:
            w += 1
    return w


def status_line(data, rate, month, columns=0):
    """2行目を組み立てる。欠けているフィールドの区画は丸ごと省く（null 埋めの "--" を出さない）。

    なぜ段階縮退するか（2026-07-29 ユーザー報告）:
      全部入りは実測で 100 桁を超え、末尾の当月累計から順に見切れて読めなくなっていた。
      端末が切り捨てる位置は制御できないので、こちらで優先度の低い情報から先に落とす。
      落とす順＝①ラベル語とコンテキスト内訳 ②利用枠のリセット残り ③当月累計 ④枠ラベル。
      COLUMNS は Claude Code が実行前に設定する（v2.1.153+）。無ければ縮退しない。
    """
    cw = data.get("context_window") or {}
    size = cw.get("context_window_size")
    model = (data.get("model") or {}).get("display_name") or ""
    # display_name 自身が「Opus 5 (1M context)」と拡張枠を自称することがある＝二重表示を避ける。
    # 拡張枠は context_window_size でしか確実に判別できないため、名乗っていないときだけ添える。
    model = model.replace("(1M context)", "(1M)")
    if size and size >= 1_000_000 and "1M" not in model:
        model += " (1M)"

    cost = (data.get("cost") or {}).get("total_cost_usd")
    rl = data.get("rate_limits") or {}
    pct = cw.get("used_percentage")
    used = cw.get("total_input_tokens")

    def render(level):
        parts = []
        if model:
            parts.append(f"🤖 {model}")

        if cost is not None:
            parts.append(f"💰 ¥{round(cost * rate):,}" + (" セッション" if level < 1 else ""))

        # プラン利用枠（Pro/Max のみ・最初の API レスポンス後に現れる）。ccusage の「5hブロック」推定の代替。
        # ラベルを和文にするのは、同じ値を見せる組み込みの /usage が英語表示で変更できないため
        # ——「英語で分かりにくい」を埋めるのがこの常時表示の役目（2026-07-29 ユーザー指摘）。
        segs = []
        for key, label in (("five_hour", "5時間"), ("seven_day", "週")):
            win = rl.get(key) or {}
            wpct = win.get("used_percentage")
            if wpct is None:
                continue
            if level >= 4:
                segs.append(f"{wpct:.0f}%")
                continue
            seg = f"{label} {wpct:.0f}%"
            if level < 2:
                left = fmt_left(win.get("resets_at"), prefix=(level < 1))
                if left:
                    seg += f"({left})"
            segs.append(seg)
        if segs:
            parts.append("🔋 " + ("/".join(segs) if level >= 4 else " / ".join(segs)))

        if pct is not None:
            detail = f" ({fmt_tokens(used)}/{fmt_tokens(size)})" if level < 1 and used and size else ""
            parts.append(f"🧠 {pct:.0f}%{detail}")

        if month and level < 3:
            parts.append(f"📅 ¥{month}" + (" 今月" if level < 1 else ""))

        return " | ".join(parts)

    line = render(0)
    if not columns:
        return line
    for level in range(5):
        line = render(level)
        # 右端には MCP エラー等の通知が同居しうるので数桁の余白を残す
        if disp_width(line) <= columns - 2:
            return line
    return line


def main():
    rate, month = parse_args(sys.argv[1:])

    # stdin が JSON でない（/dev/null 等）場合は2行目を出さずブランチ行だけで縮退する。
    try:
        data = json.load(sys.stdin)
    except (json.JSONDecodeError, EOFError, ValueError):
        data = None

    print(git_line())

    if isinstance(data, dict):
        # COLUMNS は Claude Code が実行前に現在の端末幅で設定する（tput cols はパイプ越しで効かない）
        try:
            columns = int(os.environ.get("COLUMNS", "0"))
        except ValueError:
            columns = 0
        line = status_line(data, rate, month, columns)
        if line:
            print(line)


if __name__ == "__main__":
    main()
