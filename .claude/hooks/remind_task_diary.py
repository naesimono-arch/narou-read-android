#!/usr/bin/env python3
"""
PostToolUse hook: fix:/feat:/refactor: コミット時に「知見の置き場」（task_diary / ADR / patterns）
への記録要否を「想起」させる。
対象ツール: Bash
※ ファイル名は歴史的に remind_task_diary のまま（settings.json の配線は起動時固定＝改名は
  再起動まで反映されないため据え置き。役割は task_diary 限定から置き場ルーティング全般へ拡張済み）。

設計意図（なぜブロックせず additionalContext だけか）:
  CLAUDE.md の既存ルールは「追記が必要か確認／重複なら不要」。大半の fix は追記不要で、
  追記を強制すると空エントリが量産され task_diary が再び雑多化する（3パート再編の意図を壊す）。
  そのため「考慮の想起」までに留め、コミットは決して妨げない（常に exit 0・例外を投げない）。

なぜ refactor: も対象か（2026-07-07 追加）:
  「不採用にした代替案（Why-not）」はコミットを生まないか refactor: に乗ることが多く、
  fix:/feat: 限定では想起が一度も発火しない＝ADR 化の取りこぼしが実地で発生した
  （フック単一ディスパッチャ不採用の判断が refactor コミット e4ff7f7 に乗り、
  人間レビューで初めて ADR 0008（旧0007）として回収された）。docs:/chore:/style:/test: は
  知見が生まれにくくノイズ源になるため引き続き対象外。

出力方式（重要）:
  PostToolUse の plain stdout(exit 0) は **デバッグログ止まりでモデルのコンテキストに入らない**
  （公式仕様。コンテキスト注入されるのは UserPromptSubmit/SessionStart 等の stdout か exit 2 の
  stderr のみ）。素の print では「想起」がモデルに届かず無意味になるため、
  PostToolUse がサポートする `hookSpecificOutput.additionalContext`(JSON) で注入する。

発火条件:
  - tool_name == "Bash"
  - コマンドが `git commit` を含み、コミットメッセージが `fix:`/`feat:`/`refactor:` で始まる
  - `docs:`/`chore:`/`style:`/`test:` 等はスルー（ノイズを出さない）
"""
import json
import re
import sys

# git commit の検知は hooks_common.py の単一定義を共有する
# （定義と設計理由は同ファイル参照。共有を identity で固定するのは test_hooks.py）。
# 本フック固有の背景: 旧・単純部分文字列判定はクォート内言及にも誤発火し、
# -m 抽出が echo 内の疑似メッセージを拾うノイズ源だった。
from hooks_common import COMMIT_CMD_RE, read_payload, wrap_stdio

wrap_stdio()

data = read_payload()
if data is None:
    sys.exit(0)

if data.get("tool_name", "") != "Bash":
    sys.exit(0)

command = data.get("tool_input", {}).get("command", "")

# 実行コマンドとしての `git commit` でなければ無関係。
if not COMMIT_CMD_RE.search(command):
    sys.exit(0)

# コミットメッセージ（-m "..." / -m '...'）を抽出して接頭辞を判定する。
# なぜ message を見るか: コミット種別で対象を絞り、docs: 等の「知見が生まれにくい」コミットでは
# 想起文を出さないため。heredoc 等で -m が取れない場合は判定不能 → 安全側（無出力）に倒す。
messages = re.findall(r"-m\s+(['\"])(.*?)\1", command, re.DOTALL)
prefixes_to_remind = ("fix:", "feat:", "refactor:")
should_remind = any(
    msg.strip().startswith(prefixes_to_remind) for _q, msg in messages
)

if not should_remind:
    sys.exit(0)

# task_diary.md は 2026-07-12 に凍結（既存 #N 参照は有効・新規追記はしない）。
# 新規の外部事実は docs/knowledge/ に1知見=1ファイル（採番衝突クラスを構造的に消すため）。
reminder = (
    "[知見の置き場 想起] この変更に、コードコメントでは伝わらない知見・判断はあるか？\n"
    "  ・外部プラットフォームの事実・落とし穴（根本原因/OEM固有動作） → docs/knowledge/ に1知見=1ファイル\n"
    "  ・不採用にした代替案・方式転換（Why-not） → docs/decisions/(ADR)\n"
    "  ・本アプリの実装パターン（コードが正本＝whyに絞る） → docs/patterns/\n"
    "  ・既存エントリ（task_diary #N 含む）と重複、または自明なら追記不要。"
)

# PostToolUse の additionalContext でモデルのコンテキストへ注入する（plain stdout は届かない）。
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": reminder,
    }
}, ensure_ascii=False))
sys.exit(0)
