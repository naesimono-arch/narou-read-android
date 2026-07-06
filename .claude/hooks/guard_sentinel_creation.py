#!/usr/bin/env python3
"""
PreToolUse hook: 保護ブランチ用センチネル `.claude/.allow_protected_commit` を
AI（ツール経由）が生成・改変する操作をブロックする。
対象ツール: Write / Edit / MultiEdit / Bash

なぜこのフックが要るか（設計の核心）:
  `.allow_protected_commit` は「main への明示コミットを許可する認可トークン」。だが従来は AI 自身が
  `echo > .claude/.allow_protected_commit` 等で自己発行できた＝「被認可者が認可トークンを自分で
  発行できる」設計崩壊（confused deputy 的な穴）で、ガードとしての意味が骨抜きだった。
  そこで発行主体を人間に固定する: ツール経由の生成を塞ぎ、ユーザーが入力欄で `!`(bash mode) から
  叩いた時だけ作れるようにする。**ユーザーの `!` 実行は PreToolUse フックを一切通らない**ことを
  2026-07-06 に実測で確定済み（AI の Bash ツール実行のみ tool_name="Bash" でフックを発火する）。
  この非対称性を利用して「AI は作れない・人間は `!` で作れる」を機構的に成立させる。

限界（ADR 0004 のソフト防御思想を踏襲＝完全防止ではない）:
  - Bash 経由の難読化（変数展開・`cd` して相対パス・base64・printf/tee/dd/`python -c` 等で
    リテラル `.allow_protected_commit` を出さない形）は文字列検知を回避しうる。
  - matcher に無い PowerShell ツール経由も対象外（既存コミット系フックと同じ制約）。
  よって本フックが確実に止めるのは「素直な自己発行（echo/touch/Write）」＝暴走・うっかりでの
  自己認可であり、意図的な難読化回避までは防げない。最終防壁は ADR 0004 同様
  「作業ブランチ運用 ＋ 未 push の main コミットは可逆」に置く。

なぜ Bash では読取(cat/ls 等)まで巻き込む一律ブロックにするか:
  「書き込み文脈だけ」を正規表現で正確に切り分けるのは >/tee/touch/cp/dd/`:` リダイレクト等の
  多様さで漏れやすい。一方 AI がこのファイル名をコマンドに書く正当な必要はほぼ無い（存在確認は
  guard_commit_branch.py が担う）。過剰側に倒しても実害が小さいため、単純で漏れにくい一律禁止を採る。
"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# 【重要】guard_commit_branch.py / consume_protected_sentinel.py と同じセンチネル名。
# ここは basename のみで判定する（AI が相対でも絶対でも同名ファイルを作れば捕捉するため）。
SENTINEL_BASENAME = ".allow_protected_commit"

# 案内メッセージ用の絶対パス（cwd 非依存）。`!`(bash mode) のシェル cwd はリポジトリルートである
# 保証がなく、相対 `.claude/…` はサブディレクトリから叩くと "No such file or directory" で失敗する
# （2026-07-07 実地）。手順は必ず絶対パスで出す。判定は上記 basename、案内はこの絶対パスと役割が別。
SENTINEL_ABS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    SENTINEL_BASENAME,
)


def block() -> None:
    # なぜ stderr か: PreToolUse の plain stdout はモデルに届かないが、exit 2 でブロックした際の
    # stderr は Claude にフィードバックされる（task_diary #28）。AI に理由と正しい手順を返すため。
    print(f"[センチネル保護] '{SENTINEL_BASENAME}' は AI が作成・改変できません。", file=sys.stderr)
    print("これは main への明示コミット許可を『人間だけが発行する』ための仕組みです。", file=sys.stderr)
    print("コミット内容をユーザーに提示し、承認を得たうえで、ユーザーに入力欄で次を", file=sys.stderr)
    print("先頭の `!` ごと実行してもらってください（`!`=bash mode はこのフックを迂回します）:", file=sys.stderr)
    print(f"  ! echo > {SENTINEL_ABS}", file=sys.stderr)
    sys.exit(2)


try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError, ValueError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

# Write / Edit / MultiEdit: 書き込み先 file_path の basename がセンチネルなら生成・改変とみなす。
if tool_name in ("Write", "Edit", "MultiEdit"):
    file_path = tool_input.get("file_path", "") or ""
    if os.path.basename(file_path) == SENTINEL_BASENAME:
        block()
    sys.exit(0)

# Bash: コマンド文字列にセンチネル名が現れたら一律ブロック（理由は module docstring 参照）。
if tool_name == "Bash":
    command = tool_input.get("command", "") or ""
    if SENTINEL_BASENAME in command:
        block()
    sys.exit(0)

sys.exit(0)
