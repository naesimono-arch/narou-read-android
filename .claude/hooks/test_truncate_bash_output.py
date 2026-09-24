#!/usr/bin/env python3
"""⚠️ 凍結中（2026-08-17 ユーザー裁定）＝対象フックは配線されていない（＝この回帰も本番を守っていない）。

凍結の理由（削減が全体の約 2.6%・介入率 3.6% にすぎないのに、未文書化の内部フィールド
`hookSpecificOutput.updatedToolOutput` へ依存し、監督が見る出力を加工する監視パイプラインの改変になる）と、
**解凍条件（`updatedToolOutput` が公式 docs に載ること）・解凍手順（settings.json へ足す JSON 現物）は
`truncate_bash_output.py` 冒頭の凍結注記が正本**——二重に書くと片方だけ腐るのでここには写さない。
実測の正本＝`docs/knowledge/context-cost-hook-levers-measured-limits.md`。
このファイル単体は `python .claude/hooks/test_truncate_bash_output.py` でいつでも回る（解凍時の最初のゲート）。

truncate_bash_output.py の「切り詰める／絶対に触らない」を回帰固定する。

なぜこのテストが要るか（task_diary #44 のクラス）:
  本フックは fail-open（何かあれば差し替えないだけ）なので、壊れても Bash は正常に見える。
  逆に SKIP 条件が壊れると、スタックトレースやテスト失敗の詳細を**黙って中略する**という
  最悪の形で機能してしまう。発火側・沈黙側の両方を陽性コントロールとして固定する。
"""
import json
import os
import subprocess
import sys
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "truncate_bash_output.py")
BASE = {"stderr": "", "interrupted": False, "isImage": False, "noOutputExpected": False}

LONG_PLAIN = "\n".join(f"line {i} " + "x" * 60 for i in range(200))
LONG_GREP = "\n".join(f"src/a{i}.kt:{i}:  hit " + "y" * 60 for i in range(200))


def run(tool_response, tool_name="Bash"):
    """フックを実行し、差し替えがあれば新 stdout を、無ければ None を返す。"""
    r = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps({"tool_name": tool_name, "tool_input": {"command": "x"},
                          "tool_response": tool_response}),
        capture_output=True, text=True, timeout=20, encoding="utf-8",
    )
    assert r.returncode == 0, r.stderr
    if not (r.stdout or "").strip():
        return None
    d = json.loads(r.stdout)
    return d["hookSpecificOutput"]["updatedToolOutput"]


class TruncateCase(unittest.TestCase):
    def test_truncates_long_plain_output(self):
        got = run({**BASE, "stdout": LONG_PLAIN})
        self.assertIsNotNone(got, "長い通常出力は切り詰められるべき")
        self.assertLess(len(got["stdout"]), len(LONG_PLAIN))
        self.assertIn("[truncate_bash_output] 中略", got["stdout"])

    def test_keeps_both_ends(self):
        """両端残し。末尾を捨てるとビルド／テストの結論が消える。"""
        got = run({**BASE, "stdout": LONG_PLAIN})
        self.assertTrue(got["stdout"].startswith("line 0 "))
        self.assertTrue(got["stdout"].rstrip().endswith("x" * 60))

    def test_preserves_output_shape(self):
        """outputSchema 検証に落ちると差し替えごと無効化される＝元 dict のキーを保つ。"""
        got = run({**BASE, "stdout": LONG_PLAIN, "gitOperation": {"a": 1}})
        self.assertEqual(sorted(got), sorted({**BASE, "stdout": "", "gitOperation": {}}))

    def test_short_output_untouched(self):
        self.assertIsNone(run({**BASE, "stdout": "ok" * 10}))

    # ---- 触ってはいけない出力（ここが壊れると調査が黙って壊れる）----
    def test_skips_stack_trace(self):
        s = LONG_PLAIN + "\nTraceback (most recent call last)\n  File \"a.py\", line 3"
        self.assertIsNone(run({**BASE, "stdout": s}))

    def test_skips_gradle_failure(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN + "\nFAILURE: Build failed"}))

    def test_skips_kotlin_compile_error(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN + "\ne: /a/B.kt:3:1 unresolved"}))

    def test_skips_when_stderr_present(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN, "stderr": "warning"}))

    def test_skips_when_interrupted(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN, "interrupted": True}))

    def test_skips_already_offloaded(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN, "persistedOutputPath": "/x"}))

    def test_skips_other_tools(self):
        self.assertIsNone(run({**BASE, "stdout": LONG_PLAIN}, tool_name="Read"))

    def test_skips_string_response(self):
        """失敗時の tool_response は str で来る＝触らない。"""
        self.assertIsNone(run("Exit code 1\n" + LONG_PLAIN))

    # ---- 回復可能性・向きの制御 ----
    def test_stashes_full_text_and_cites_path(self):
        got = run({**BASE, "stdout": LONG_PLAIN})
        line = [l for l in got["stdout"].split("\n") if l.strip().startswith("全文:")][0]
        path = line.split("全文:", 1)[1].strip()
        with open(path, encoding="utf-8") as f:
            self.assertEqual(f.read(), LONG_PLAIN)

    def test_listing_output_keeps_head_heavy(self):
        """grep -n 形式は末尾に結論が無いので先頭を厚く残す。"""
        plain = run({**BASE, "stdout": LONG_PLAIN})["stdout"]
        grep = run({**BASE, "stdout": LONG_GREP})["stdout"]
        head_plain = plain.split("=====")[0]
        head_grep = grep.split("=====")[0]
        self.assertGreater(len(head_grep), len(head_plain))

    def test_reports_dropped_line_count(self):
        """「一部しか見ていない」と分かること自体が grep 結果の早合点を止める歯止め。"""
        got = run({**BASE, "stdout": LONG_GREP})
        self.assertRegex(got["stdout"], r"約 \d+ 行")

    def test_malformed_stdin_is_fail_open(self):
        r = subprocess.run([sys.executable, HOOK], input="not json",
                           capture_output=True, text=True, timeout=20)
        self.assertEqual(r.returncode, 0)
        self.assertEqual(r.stdout.strip(), "")


if __name__ == "__main__":
    unittest.main()
