#!/usr/bin/env python3
"""
guard_sentinel_creation.py の振る舞い（block=exit 2 / pass=exit 0）を回帰固定するテスト。

なぜ subprocess で exit code を検証するか（test_hooks.py の正規表現直検証とは方式が異なる理由）:
  本フックの判定は tool_name と file_path/command のみに依存し、git ブランチやセンチネル有無に
  依存しない＝環境非依存で exit code が安定する。よって「どの入力でブロックし、どの入力で通すか」を
  実プロセスの exit code で直接検証するのが最も本質に近い（正規表現を持たないため定数回収もしない）。
"""
import json
import os
import subprocess
import sys
import unittest

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HOOKS_DIR, "guard_sentinel_creation.py")
BLOCK = 2  # PreToolUse で exit 2＝ブロック
PASS = 0


def run_hook(payload):
    """stdin に JSON を流してフックを実行し returncode を返す。"""
    proc = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(payload),
        capture_output=True, text=True, timeout=10,
    )
    return proc.returncode


class GuardSentinelCreation(unittest.TestCase):
    # --- ブロックすべき: AI がツール経由でセンチネルを生成・改変 ---
    def test_bash_redirect_relative_blocked(self):
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "Bash", "tool_input": {"command": "echo > .claude/.allow_protected_commit"}}))

    def test_bash_touch_absolute_blocked(self):
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "Bash",
             "tool_input": {"command": "touch /repo/.claude/.allow_protected_commit"}}))

    def test_bash_readonly_still_blocked(self):
        # 一律ブロック方針: 読取(cat)でもファイル名が出れば止める（module docstring 参照）
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "Bash", "tool_input": {"command": "cat .claude/.allow_protected_commit"}}))

    def test_write_sentinel_blocked(self):
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "Write",
             "tool_input": {"file_path": "/repo/.claude/.allow_protected_commit", "content": ""}}))

    def test_edit_sentinel_blocked(self):
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "Edit",
             "tool_input": {"file_path": ".claude/.allow_protected_commit"}}))

    def test_multiedit_sentinel_blocked(self):
        self.assertEqual(BLOCK, run_hook(
            {"tool_name": "MultiEdit",
             "tool_input": {"file_path": "/repo/.claude/.allow_protected_commit"}}))

    # --- 通すべき: 無関係な操作・別ファイル・別ツール ---
    def test_bash_unrelated_passes(self):
        self.assertEqual(PASS, run_hook(
            {"tool_name": "Bash", "tool_input": {"command": "git commit -m x"}}))

    def test_write_other_file_passes(self):
        self.assertEqual(PASS, run_hook(
            {"tool_name": "Write",
             "tool_input": {"file_path": "/repo/.claude/settings.json", "content": "{}"}}))

    def test_write_similar_but_different_basename_passes(self):
        # 部分一致ではなく basename 完全一致で判定する（誤爆防止）
        self.assertEqual(PASS, run_hook(
            {"tool_name": "Write",
             "tool_input": {"file_path": "/repo/.allow_protected_commit.bak", "content": ""}}))

    def test_other_tool_passes(self):
        self.assertEqual(PASS, run_hook(
            {"tool_name": "Read",
             "tool_input": {"file_path": "/repo/.claude/.allow_protected_commit"}}))

    def test_broken_stdin_passes(self):
        # 壊れた入力でフックが誤ってブロックし全操作を止めないこと（フェイルオープン）
        proc = subprocess.run(
            [sys.executable, HOOK], input="not-json",
            capture_output=True, text=True, timeout=10)
        self.assertEqual(PASS, proc.returncode)


if __name__ == "__main__":
    unittest.main(verbosity=2)
