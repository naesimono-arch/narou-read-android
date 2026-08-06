#!/usr/bin/env python3
"""inject_handover_digest.py の回帰テスト（stale-check 項目12 が回収する）。

このフックの故障はサイレント（出力方式を誤ると誰にも届かず、しかもエラーにならない）。
よって陽性コントロール＝「実際に発火させて期待どおりの中身が出ること」を固定する。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "inject_handover_digest.py")


def run_hook(cwd=None):
    r = subprocess.run(
        [sys.executable, HOOK],
        input="{}", capture_output=True, text=True, timeout=20, cwd=cwd,
    )
    return r


class TestInjectHandoverDigest(unittest.TestCase):
    def test_outputs_session_start_additional_context(self):
        """plain stdout ではなく additionalContext で返すこと（task_diary #28 の型）。"""
        r = run_hook()
        self.assertEqual(r.returncode, 0, r.stderr)
        payload = json.loads(r.stdout)
        out = payload["hookSpecificOutput"]
        self.assertEqual(out["hookEventName"], "SessionStart")
        self.assertIn("additionalContext", out)

    def test_injects_handover_outline_with_line_numbers(self):
        """handover.md の節見出しを行番号つきで載せること（全文読み回避の要）。"""
        ctx = json.loads(run_hook().stdout)["hookSpecificOutput"]["additionalContext"]
        self.assertIn("handover.md", ctx)
        self.assertRegex(ctx, r"L\d+: ")
        self.assertIn("Read(offset/limit)", ctx)

    def test_does_not_inject_body_text(self):
        """本文は載せない（常時コンテキストを太らせない）＝サイズ上限で機械的に縛る。"""
        ctx = json.loads(run_hook().stdout)["hookSpecificOutput"]["additionalContext"]
        self.assertLess(len(ctx), 4000, "地図のはずが本文を載せている疑い")

    def test_survives_when_cwd_is_subdirectory(self):
        """サブディレクトリ起動でも同じ結果（cwd 依存にしない＝inject_branch_context.py と同じ罠）。"""
        root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        sub = os.path.join(root, "android")
        if not os.path.isdir(sub):
            self.skipTest("android/ が無い")
        a = json.loads(run_hook(cwd=root).stdout)["hookSpecificOutput"]["additionalContext"]
        b = json.loads(run_hook(cwd=sub).stdout)["hookSpecificOutput"]["additionalContext"]
        self.assertEqual(a.split("- `~/.claude/plans/`")[0], b.split("- `~/.claude/plans/`")[0])

    def test_handles_malformed_stdin(self):
        """stdin が JSON でなくても落ちない（フックの例外はセッション開始を壊す）。"""
        r = subprocess.run(
            [sys.executable, HOOK],
            input="not json", capture_output=True, text=True, timeout=20,
        )
        self.assertEqual(r.returncode, 0, r.stderr)
        json.loads(r.stdout)

    def test_outline_counts_top_level_bullets(self):
        """件数は見出し直下のトップレベル箇条書きだけを数える（入れ子を二重計上しない）。"""
        sys.path.insert(0, os.path.dirname(HOOK))
        import inject_handover_digest as mod

        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "sample.md")
            with open(p, "w", encoding="utf-8") as f:
                f.write("## A\n- one\n  - nested\n- two\n\n## B\n本文のみ\n")
            heads, total = mod.outline(p)
        self.assertEqual([(h[2], h[3]) for h in heads], [("A", 2), ("B", 0)])
        self.assertGreater(total, 0)


if __name__ == "__main__":
    unittest.main()
