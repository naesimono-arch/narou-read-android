#!/usr/bin/env python3
"""check_sequence_id_collision.py の陽性/陰性コントロール。

なぜ必須か: fail-open 設計のフックは壊れていても「全通し」で無症状（task_diary #44）。
わざと衝突状態を作って発火を固定し、回帰で沈黙故障を検出する。

なぜ実リポジトリの固定ID（task_diary #5・ADR 0001）を代表に使うか:
固定IDはリナンバーしない規約（CLAUDE.md）のため恒久に安定し、フィクスチャ複製より
「現物レジストリを読めていること」まで一度に検証できる（repo_root の dirname 回数
バグ＝#44 の実例も、この現物照合が守備範囲に入れる）。
"""
import json
import os
import subprocess
import sys
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check_sequence_id_collision.py")
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def run_hook(payload):
    return subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=30,
    )


def payload(tool_name, **tool_input):
    return {"tool_name": tool_name, "tool_input": tool_input}


class SequenceIdCollisionTest(unittest.TestCase):
    def context_of(self, proc):
        out = json.loads(proc.stdout.decode("utf-8"))
        return out["hookSpecificOutput"]["additionalContext"]

    def test_diary_collision_warns(self):
        """陽性: 使用済みの固定ID #5 を新規見出しとして追加する Edit は警告される。"""
        proc = run_hook(payload(
            "Edit",
            file_path=os.path.join(ROOT, "task_diary.md"),
            old_string="（アンカー行）",
            new_string="（アンカー行）\n#### 5. 重複させる見出し\n",
        ))
        self.assertEqual(proc.returncode, 0)
        self.assertIn("#5", self.context_of(proc))

    def test_diary_fresh_id_silent(self):
        """陰性: どのレーンにも無い大番号の新規採番は無警告。"""
        proc = run_hook(payload(
            "Edit",
            file_path=os.path.join(ROOT, "task_diary.md"),
            old_string="x",
            new_string="x\n#### 99999. 新規見出し\n",
        ))
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_diary_title_edit_silent(self):
        """陰性: old/new 両方に同番号（既存エントリのタイトル編集）は新規採番でない。"""
        proc = run_hook(payload(
            "Edit",
            file_path=os.path.join(ROOT, "task_diary.md"),
            old_string="#### 5. 旧タイトル",
            new_string="#### 5. 新タイトル",
        ))
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_adr_collision_warns(self):
        """陽性: 使用済み番号 0001 の新規 ADR ファイル Write は警告される。"""
        proc = run_hook(payload(
            "Write",
            file_path=os.path.join(ROOT, "docs", "decisions", "0001-duplicate-probe.md"),
            content="# probe",
        ))
        self.assertEqual(proc.returncode, 0)
        self.assertIn("0001", self.context_of(proc))

    def test_adr_fresh_number_silent(self):
        """陰性: 未使用番号の新規 ADR は無警告。"""
        proc = run_hook(payload(
            "Write",
            file_path=os.path.join(ROOT, "docs", "decisions", "9999-fresh-probe.md"),
            content="# probe",
        ))
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_adr_existing_file_overwrite_silent(self):
        """陰性: 既存 ADR ファイルへの Write（増補・上書き）は採番でないため無警告。"""
        proc = run_hook(payload(
            "Write",
            file_path=os.path.join(ROOT, "docs", "decisions", "0001-no-hilt.md"),
            content="# probe",
        ))
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_unrelated_file_silent(self):
        """陰性: 対象外ファイルは見出し形の文字列があっても無警告。"""
        proc = run_hook(payload(
            "Edit",
            file_path=os.path.join(ROOT, "STATUS.md"),
            old_string="a",
            new_string="a\n#### 5. x\n",
        ))
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_broken_stdin_fail_open(self):
        """fail-open: 壊れた stdin でも exit 0（編集を妨げない）。"""
        proc = subprocess.run([sys.executable, HOOK], input=b"not json", capture_output=True, timeout=30)
        self.assertEqual(proc.returncode, 0)


if __name__ == "__main__":
    unittest.main()
