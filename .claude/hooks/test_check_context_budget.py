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


def transcript(*ctxs, extra_text=None, tool_use_text=None):
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
        if tool_use_text:
            # フックのソースやテストを Write/Read したときに transcript へ載る形
            f.write(json.dumps({"type": "assistant",
                                "message": {"content": [{"type": "tool_use", "id": "x", "name": "Write",
                                                         "input": {"content": tool_use_text}}]}},
                               ensure_ascii=False) + "\n")
    return path


# 凍結ゲート（本体の FROZEN）を外した状態の __main__ と同じ入口。
# なぜテストが本体をスクリプトとして起動しないか（2026-09-05）:
#   2026-09-02 の凍結は FROZEN=True で __main__ を即 exit させる形で入ったが、
#   **テスト側が追従しなかった**ため通告側 2 件が落ちたまま 3 日走っていた。
#   単に skip すると素通し側まで「凍結だから空」で通る＝解凍した瞬間に何も守られていない。
#   凍結が否定したのは「自動通告」であって検知ロジックでも 175k の閾値でもなく
#   （閾値は session-relay skill の判断基準として現役）、解凍は settings.json の
#   配線戻し＋FROZEN=False だけで起きる。∴ ロジックは main() を直接呼んで常に検査し、
#   凍結ゲート自体は test_凍結ゲートが効いている で別に押さえる。
DRIVER = (
    "import sys; sys.path.insert(0, {!r}); import check_context_budget as m\n"
    "try:\n"
    "    sys.exit(m.main())\n"
    "except Exception as e:\n"          # fail-open も本体 __main__ と揃える
    "    sys.stderr.write(str(e)); sys.exit(0)\n"
).format(HOOKS_DIR)


def frozen_flag():
    """本体の FROZEN を別プロセスで読む（import すると sys.stdout を差し替えるので同居させない）。"""
    r = subprocess.run(
        [sys.executable, "-c",
         "import sys; sys.path.insert(0, {!r}); "
         "import check_context_budget as m; print(m.FROZEN)".format(HOOKS_DIR)],
        capture_output=True, text=True, timeout=30,
    )
    return r.stdout.strip() == "True"


FROZEN = frozen_flag()


def run_hook(stdin_obj):
    """凍結ゲートを介さず本体ロジックだけを走らせる（検知の中身を検査する側）。"""
    proc = subprocess.run(
        [sys.executable, "-c", DRIVER],
        input=json.dumps(stdin_obj, ensure_ascii=False).encode("utf-8"),
        capture_output=True, timeout=30,
    )
    return proc.returncode, proc.stdout.decode("utf-8", errors="replace")


def run_script(stdin_obj):
    """実際のフックと同じくスクリプトとして起動する＝凍結ゲート込み。"""
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

    def test_ソースを書いただけでは通告を止めない(self):
        """回帰: SENTINEL はフック自身とテストに literal で載る。全文 grep で判定していた版は、
        フックを Write / Read しただけのセッションが以後永久に通告されなかった（2026-08-26 に実際に踏んだ）。
        通告は user 本文として届くので、tool_use に載っただけの分は無視されねばならない。"""
        p = self._t(200_000, tool_use_text="SENTINEL = \"CTXBUDGET_NOTICE_V1\"")
        code, out = run_hook({"transcript_path": p})
        self.assertEqual(json.loads(out)["decision"], "block")

    def test_stop_hook_active時は素通し(self):
        code, out = run_hook({"transcript_path": self._t(200_000), "stop_hook_active": True})
        self.assertEqual(out.strip(), "")

    def test_transcript欠落でも落ちない(self):
        code, out = run_hook({"transcript_path": "/nonexistent/xx.jsonl"})
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "")

    def test_壊れた入力でも落ちない(self):
        proc = subprocess.run([sys.executable, "-c", DRIVER], input=b"not json",
                              capture_output=True, timeout=30)
        self.assertEqual(proc.returncode, 0)

    def test_凍結ゲートが効いている(self):
        """凍結中はスクリプト起動が閾値超過でも黙る（解凍後は通告する）。

        本体の FROZEN は「配線を外す前から走っているセッション」を黙らせる唯一の手で
        （起動時スナップショット＝memory hook-implementation-facts §3）、settings.json の
        配線外しと二段になっている。どちらの状態でも正しいことを主張するので、
        解凍時にこのテストを書き換える必要はない。
        """
        code, out = run_script({"transcript_path": self._t(200_000)})
        self.assertEqual(code, 0)
        if FROZEN:
            self.assertEqual(out.strip(), "", "FROZEN=True の間は通告してはならない")
        else:
            self.assertEqual(json.loads(out)["decision"], "block")


if __name__ == "__main__":
    unittest.main(verbosity=2)
