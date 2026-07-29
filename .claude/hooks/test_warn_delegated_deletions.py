#!/usr/bin/env python3
"""warn_delegated_deletions.py の陽性/陰性コントロールと配線の固定。

なぜ必須か: fail-open 設計のフックは壊れても「全通し」で無症状（task_diary #44）。
削除警告は「出ないこと」が正常状態と区別できないので、陽性コントロールが唯一の生存証明になる。

配線まで固定するのはなぜか: 検知手段を既知バグレジストリへ登録する以上、settings から配線を
外した瞬間に落ちないと台帳が「守られている」と嘘をつく（撤去フックの残骸が13日間 dead だった
のと同じ形＝`removed-hook-leaves-dead-consumer`）。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HERE, "warn_delegated_deletions.py")
ROOT = os.path.dirname(os.path.dirname(HERE))


def run_hook(payload):
    return subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=30,
    )


def sub_payload(tool_name, **tool_input):
    """サブエージェント発の PreToolUse ペイロード（判別キーは agent_id の有無）。"""
    return {"hook_event_name": "PreToolUse", "agent_id": "agent-test-0001",
            "session_id": "s-test", "tool_name": tool_name, "tool_input": tool_input}


class WarnDelegatedDeletionsTest(unittest.TestCase):
    def context_of(self, proc):
        return json.loads(proc.stdout.decode("utf-8"))["hookSpecificOutput"]["additionalContext"]

    def assert_silent(self, proc):
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_pure_deletion_warns(self):
        """陽性: 置き換え無しで2項目消える Edit（スコープ外削除の実例の形＝チップ2個の消失）。"""
        proc = run_hook(sub_payload(
            "Edit", file_path="/x/FilterSheet.kt",
            old_string='Chip("今月")\nChip("先月")\nChip("全期間")\n',
            new_string='Chip("全期間")\n',
        ))
        self.assertEqual(proc.returncode, 0)
        ctx = self.context_of(proc)
        self.assertIn("2 行が消える", ctx)
        self.assertIn("今月", ctx)

    def test_single_line_pure_deletion_silent(self):
        """陰性: 1行だけの純削除は通告しない（実測 4.4%＝含めると通告が背景ノイズ化する）。"""
        self.assert_silent(run_hook(sub_payload(
            "Edit", file_path="/x/a.kt", old_string="val a = 1\nval b = 2\n", new_string="val b = 2\n")))

    def test_reindent_only_silent(self):
        """陰性: インデント付け替えだけの編集は削除0扱い（行の正規化が効いていること）。"""
        self.assert_silent(run_hook(sub_payload(
            "Edit", file_path="/x/a.kt",
            old_string="fun f() {\nval a = 1\nval b = 2\n}\n",
            new_string="fun f() {\n    val a = 1\n    val b = 2\n}\n")))

    def test_large_rewrite_warns(self):
        """陽性: 追加を伴っても削除が閾値（15行）以上なら通告する。"""
        old = "".join(f"line{i}\n" for i in range(20))
        new = "".join(f"new{i}\n" for i in range(20))
        proc = run_hook(sub_payload("Edit", file_path="/x/a.kt", old_string=old, new_string=new))
        self.assertIn("20 行が消える", self.context_of(proc))

    def test_main_session_not_watched(self):
        """陰性: agent_id 無し＝メインセッションは対象外（監督の編集は人間がレビューする）。"""
        p = {"hook_event_name": "PreToolUse", "tool_name": "Edit",
             "tool_input": {"file_path": "/x/a.kt", "old_string": "a\nb\nc\n", "new_string": "c\n"}}
        self.assert_silent(run_hook(p))

    def test_write_shrink_warns_against_disk_content(self):
        """陽性: Write の全置換は、ディスク上の旧内容と突き合わせて削除を数える。"""
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "list.kt")
            with open(path, "w", encoding="utf-8") as f:
                f.write("keep\nitemA\nitemB\n")
            proc = run_hook(sub_payload("Write", file_path=path, content="keep\n"))
            ctx = self.context_of(proc)
            self.assertIn("2 行が消える", ctx)
            self.assertIn("itemA", ctx)

    def test_write_new_file_silent(self):
        """陰性: 新規ファイルの Write は削除が起こり得ない。"""
        with tempfile.TemporaryDirectory() as d:
            self.assert_silent(run_hook(sub_payload(
                "Write", file_path=os.path.join(d, "new.kt"), content="a\nb\nc\n")))

    def test_broken_payload_fails_open(self):
        """陰性: 壊れた入力でも exit 0・無出力（fail-open が編集を止めない）。"""
        proc = subprocess.run([sys.executable, HOOK], input=b"{ not json",
                              capture_output=True, timeout=30)
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_wired_as_pretooluse_edit_write(self):
        """配線固定: settings.json の PreToolUse に Edit/Write を含む matcher で登録されていること。"""
        with open(os.path.join(ROOT, ".claude/settings.json"), encoding="utf-8") as f:
            cfg = json.load(f)
        matchers = [g.get("matcher", "") for g in cfg["hooks"]["PreToolUse"]
                    for h in (g.get("hooks") or [])
                    if "warn_delegated_deletions.py" in h.get("command", "")]
        self.assertTrue(matchers, "warn_delegated_deletions.py が PreToolUse に配線されていない")
        for m in matchers:
            self.assertIn("Edit", m)
            self.assertIn("Write", m)


if __name__ == "__main__":
    unittest.main()
