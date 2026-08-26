#!/usr/bin/env python3
"""check_context_budget.py（Stop フック）のテスト。

判定は stdout（フックは全経路 exit 0 で、通告/素通しを stdout で表現する）:
  通告   = json.loads(stdout)["decision"] == "block"
  素通し = stdout.strip() == ""

なぜ「閾値ちょうど」と「センチネル既出」を必ず見るか: この2つが壊れると、
黙って毎ターン通告し続ける（うるさくて無視されるようになる＝ゲートが死ぬ）か、
一度も通告しない（存在しないのと同じ）かのどちらかになる。どちらも無症状で進む。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HOOKS_DIR, "check_context_budget.py")


def transcript(*ctxs, extra_text=None):
    """assistant ターンの usage 列を持つ transcript を書いて、そのパスを返す。"""
    fd, path = tempfile.mkstemp(suffix=".jsonl")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        for c in ctxs:
            f.write(json.dumps({
                "type": "assistant",
                "message": {"usage": {"input_tokens": 10,
                                      "cache_read_input_tokens": c - 10,
                                      "cache_creation_input_tokens": 0}},
            }, ensure_ascii=False) + "\n")
        if extra_text:
            f.write(json.dumps({"type": "user",
                                "message": {"content": [{"type": "text", "text": extra_text}]}},
                               ensure_ascii=False) + "\n")
    return path


def run_hook(stdin_obj):
    proc = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(stdin_obj, ensure_ascii=False).encode("utf-8"),
        capture_output=True, timeout=30,
    )
    return proc.returncode, proc.stdout.decode("utf-8", errors="replace")


class TestContextBudget(unittest.TestCase):
    def tearDown(self):
        for p in getattr(self, "_paths", []):
            try:
                os.unlink(p)
            except OSError:
                pass

    def _t(self, *a, **kw):
        p = transcript(*a, **kw)
        self._paths = getattr(self, "_paths", []) + [p]
        return p

    def test_閾値未満は素通し(self):
        code, out = run_hook({"transcript_path": self._t(50_000, 174_999)})
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "")

    def test_閾値超過で通告(self):
        code, out = run_hook({"transcript_path": self._t(50_000, 180_000)})
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("180k", json.loads(out)["reason"])

    def test_判定は最新ターンで行う(self):
        """途中で閾値を超えても、最新が下回っていれば通告しない（compact 後など）。"""
        code, out = run_hook({"transcript_path": self._t(300_000, 40_000)})
        self.assertEqual(out.strip(), "")

    def test_センチネル既出なら再通告しない(self):
        p = self._t(200_000, extra_text="前に [CTXBUDGET_NOTICE_V1] を出した")
        code, out = run_hook({"transcript_path": p})
        self.assertEqual(out.strip(), "")

    def test_stop_hook_active時は素通し(self):
        code, out = run_hook({"transcript_path": self._t(200_000), "stop_hook_active": True})
        self.assertEqual(out.strip(), "")

    def test_transcript欠落でも落ちない(self):
        code, out = run_hook({"transcript_path": "/nonexistent/xx.jsonl"})
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "")

    def test_壊れた入力でも落ちない(self):
        proc = subprocess.run([sys.executable, HOOK], input=b"not json",
                              capture_output=True, timeout=30)
        self.assertEqual(proc.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
