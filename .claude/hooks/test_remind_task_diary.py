#!/usr/bin/env python3
"""remind_task_diary.py（知見の置き場 想起）の発火条件を回帰固定する。

なぜこのテストが要るか（2026-08-07 に実測で判明した実害）:
  旧実装はコミットメッセージを `-m "..."` からしか取り出さず、**`git commit -F - <<'MSG'` の
  ヒアドキュメント形式では一度も発火しなかった**。長い日本語メッセージを書くほどヒアドキュメントを
  選ぶため「実運用ほど沈黙する」逆相関になっており、あるラウンドの 18 コミット全てで想起が死んでいた。
  エラーも出ず、誰も気づけないサイレント失敗クラス＝発火・沈黙の両方を機械で固定する。
"""
import json
import os
import subprocess
import sys
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "remind_task_diary.py")


def fires(command):
    r = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps({"tool_name": "Bash", "tool_input": {"command": command}}),
        capture_output=True, text=True, timeout=20,
    )
    assert r.returncode == 0, r.stderr
    return bool(r.stdout.strip())


class RemindTaskDiaryCase(unittest.TestCase):
    def test_fires_for_dash_m(self):
        self.assertTrue(fires('git commit -m "fix: 直す"'))

    def test_fires_for_heredoc_file_message(self):
        """-F - ヒアドキュメント（実運用の主形式）。ここが旧実装の穴だった。"""
        self.assertTrue(fires("git commit -q -F - <<MSG\nfix: 直す\n\n本文\nMSG"))

    def test_fires_for_quoted_heredoc_delimiter(self):
        self.assertTrue(fires("git commit -F - <<'MSG'\nfeat: 足す\nMSG"))

    def test_fires_for_refactor(self):
        """Why-not は refactor に乗ることが多い（ADR 0008 が実際そうだった）。"""
        self.assertTrue(fires("git commit -F - <<MSG\nrefactor: 作り直す\nMSG"))

    def test_silent_for_docs_prefix(self):
        self.assertFalse(fires("git commit -q -F - <<MSG\ndocs: 台帳更新\nMSG"))

    def test_silent_for_test_prefix(self):
        self.assertFalse(fires("git commit -F - <<MSG\ntest: テスト追加\nMSG"))

    def test_silent_for_non_commit_mention(self):
        """クォート内の言及で誤発火しない（COMMIT_CMD_RE の境界判定）。"""
        self.assertFalse(fires("echo 'git commit -m \"fix: これは言及\"'"))

    def test_output_is_post_tool_use_additional_context(self):
        r = subprocess.run(
            [sys.executable, HOOK],
            input=json.dumps({"tool_name": "Bash",
                              "tool_input": {"command": 'git commit -m "fix: x"'}}),
            capture_output=True, text=True, timeout=20,
        )
        out = json.loads(r.stdout)["hookSpecificOutput"]
        self.assertEqual(out["hookEventName"], "PostToolUse")
        self.assertIn("docs/knowledge/", out["additionalContext"])


if __name__ == "__main__":
    unittest.main()
