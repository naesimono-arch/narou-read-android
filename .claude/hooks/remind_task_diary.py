#!/usr/bin/env python3
"""
PostToolUse hook: `fix:` コミット時に task_diary.md への追記要否を「想起」させる。
対象ツール: Bash

設計意図（なぜブロックせず additionalContext だけか）:
  CLAUDE.md の既存ルールは「追記が必要か確認／重複なら不要」。大半の fix は追記不要で、
  追記を強制すると空エントリが量産され task_diary が再び雑多化する（3パート再編の意図を壊す）。
  そのため「考慮の想起」までに留め、コミットは決して妨げない（常に exit 0・例外を投げない）。

出力方式（重要）:
  PostToolUse の plain stdout(exit 0) は **デバッグログ止まりでモデルのコンテキストに入らない**
  （公式仕様。コンテキスト注入されるのは UserPromptSubmit/SessionStart 等の stdout か exit 2 の
  stderr のみ）。素の print では「想起」がモデルに届かず無意味になるため、
  PostToolUse がサポートする `hookSpecificOutput.additionalContext`(JSON) で注入する。

発火条件:
  - tool_name == "Bash"
  - コマンドが `git commit` を含み、コミットメッセージが `fix:`（または `feat:`）で始まる
  - `docs:`/`refactor:`/`chore:`/`style:`/`test:` 等はスルー（ノイズを出さない）
"""
import io
import json
import re
import sys

# 文字化け対策（既存 hook と同じ作法）。
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")

# `git commit` でなければ無関係。
if "git commit" not in command:
    sys.exit(0)

# コミットメッセージ（-m "..." / -m '...'）を抽出して接頭辞を判定する。
# なぜ message を見るか: コミット種別（fix:/feat: 等）で対象を絞り、
# docs:/refactor: 等の「知見が生まれにくい」コミットでは想起文を出さないため。
# heredoc 等で -m が取れない場合は判定不能 → 安全側（無出力）に倒す。
messages = re.findall(r"-m\s+(['\"])(.*?)\1", command, re.DOTALL)
prefixes_to_remind = ("fix:", "feat:")
should_remind = any(
    msg.strip().startswith(prefixes_to_remind) for _q, msg in messages
)

if not should_remind:
    sys.exit(0)

reminder = (
    "[task_diary 想起] この変更に、コードコメントでは伝わらない知見はあるか？\n"
    "  ・根本原因／OEM固有動作／将来はまりやすいパターン → あれば記録。\n"
    "  ・置き場: 外部プラットフォームの事実→task_diary.md / 実装パターン→docs/patterns/ /"
    " 設計判断・Why-not→docs/decisions/(ADR)。\n"
    "  ・既存エントリと重複、または自明なら追記不要。"
)

# PostToolUse の additionalContext でモデルのコンテキストへ注入する（plain stdout は届かない）。
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": reminder,
    }
}, ensure_ascii=False))
sys.exit(0)
