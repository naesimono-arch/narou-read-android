#!/usr/bin/env python3
"""count_delegation_turns.py（委譲ターン計測）の回帰固定テスト。

なぜ subprocess で end-to-end 検証するか: 本フックの本質は「stdin(JSON)→状態ファイル更新→
stdout(additionalContext)/JSONL 追記」の実挙動にあり、実プロセスの returncode と入出力を
そのまま突くのが最も本質に近い（test_inject_subagent_briefing.py と同方式）。

なぜ fail-open と exit 0 を全ケースで固定するか: 計測フックは全ツール呼出（PostToolUse "*"）で
走る補助機能であり、非ゼロ終了や例外死は本務のツールフローを汚染する。壊れた stdin・
メインセッション素通りを陰性コントロールとして固定し、無症状故障（task_diary #44）は
「30回目で通告が出る」陽性コントロール側で捕まえる。

置き場の隔離: 実運用の記録先（~/.claude/projects/... 配下）を汚さないよう、
全テストは DELEGATION_METER_DIR 環境変数で一時ディレクトリへ差し替えて走らせる。
"""
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HOOKS_DIR, "count_delegation_turns.py")

# 通告文の種別判定に使う安定部分文字列（全文一致でなく骨格語＝文言微修正に強い）。
NOTICE_MARK = "回に到達"


class MeterTestCase(unittest.TestCase):
    """一時ディレクトリへ隔離した共通土台。"""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory(prefix="delegation-meter-test-")
        self.base = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)

    def run_hook(self, payload_bytes):
        env = dict(os.environ)
        env["DELEGATION_METER_DIR"] = str(self.base)
        return subprocess.run(
            [sys.executable, HOOK],
            input=payload_bytes, capture_output=True, timeout=15, env=env,
        )

    def post_tool_use(self, agent_id="agentA", session_id="sess1",
                      agent_type="general-purpose"):
        proc = self.run_hook(json.dumps({
            "hook_event_name": "PostToolUse",
            "session_id": session_id,
            "agent_id": agent_id,
            "agent_type": agent_type,
            "tool_name": "Bash",
            "tool_input": {"command": "echo x"},
        }).encode("utf-8"))
        # どの経路でも exit 0（ブロック厳禁）を全呼出で固定する
        self.assertEqual(proc.returncode, 0, proc.stderr.decode("utf-8", "replace"))
        return proc

    def subagent_stop(self, agent_id="agentA", session_id="sess1",
                      agent_type="general-purpose"):
        proc = self.run_hook(json.dumps({
            "hook_event_name": "SubagentStop",
            "session_id": session_id,
            "agent_id": agent_id,
            "agent_type": agent_type,
        }).encode("utf-8"))
        self.assertEqual(proc.returncode, 0, proc.stderr.decode("utf-8", "replace"))
        return proc

    def state_file(self, agent_id="agentA", session_id="sess1"):
        return self.base / "delegation-state" / f"{session_id}--{agent_id}.json"

    def seed_state(self, count, agent_id="agentA", session_id="sess1",
                   agent_type="general-purpose"):
        """カウンタを直接仕込む（30回分の subprocess 起動を省き試験を速く保つ）。"""
        p = self.state_file(agent_id, session_id)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps({
            "tool_uses": count, "agent_type": agent_type,
            "session_id": session_id, "agent_id": agent_id,
        }), encoding="utf-8")
        return p

    def stats_lines(self):
        f = self.base / "delegation-stats.jsonl"
        if not f.exists():
            return []
        return [json.loads(l) for l in f.read_text(encoding="utf-8").splitlines() if l]


class ThresholdNotification(MeterTestCase):
    """(1)(2) 30回目で通告・31回目は沈黙・60回目で再通告（陽性コントロール）。"""

    def _context_of(self, proc):
        out = json.loads(proc.stdout.decode("utf-8"))
        hso = out["hookSpecificOutput"]
        # 出力形式の固定: PostToolUse として additionalContext を注入する（task_diary #28 の形）
        self.assertEqual(hso["hookEventName"], "PostToolUse")
        return hso["additionalContext"]

    def test_counts_from_scratch(self):
        # 状態ファイル無しから3回呼ぶ→カウンタ3・通告なし（増分経路そのものの検証）
        for _ in range(3):
            proc = self.post_tool_use()
            self.assertEqual(proc.stdout.decode("utf-8").strip(), "")
        state = json.loads(self.state_file().read_text(encoding="utf-8"))
        self.assertEqual(state["tool_uses"], 3)
        self.assertEqual(state["agent_type"], "general-purpose")

    def test_notice_at_30_silent_at_31_notice_at_60(self):
        self.seed_state(29)
        ctx = self._context_of(self.post_tool_use())          # 30回目
        self.assertIn("30", ctx)
        self.assertIn(NOTICE_MARK, ctx)
        self.assertIn("再スコープ", ctx)                        # 再考トリガー文言の骨格
        proc = self.post_tool_use()                            # 31回目
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")
        self.seed_state(59)
        ctx = self._context_of(self.post_tool_use())          # 60回目
        self.assertIn("60", ctx)
        self.assertIn(NOTICE_MARK, ctx)

    def test_agents_are_counted_independently(self):
        # セッション×エージェント単位のキー分離: 片方のカウントが他方へ漏れない
        self.seed_state(29, agent_id="agentA")
        self.assertIn(NOTICE_MARK,
                      self._context_of(self.post_tool_use(agent_id="agentA")))
        proc = self.post_tool_use(agent_id="agentB")
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")


class ScopeAndFailOpen(MeterTestCase):
    """(3) 壊れた stdin で exit 0／メインセッション（agent_id 無し）は計測対象外。"""

    def test_broken_json_fail_open(self):
        proc = self.run_hook(b"{{{ not-json")
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_empty_stdin_fail_open(self):
        proc = self.run_hook(b"")
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_main_session_not_counted(self):
        # agent_id 欠落＝メインセッション（公式 doc の判別キー）: 状態を一切作らない
        proc = self.run_hook(json.dumps({
            "hook_event_name": "PostToolUse", "session_id": "sess1",
            "tool_name": "Bash",
        }).encode("utf-8"))
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")
        self.assertFalse((self.base / "delegation-state").exists())


class CompletionRecord(MeterTestCase):
    """(4) SubagentStop で JSONL 追記が正しい・状態ファイルが片付く・TTL 掃除が回る。"""

    def test_stop_appends_record_and_removes_state(self):
        self.seed_state(42, agent_type="explore")
        proc = self.subagent_stop(agent_type="explore")
        # Stop 系で decision を返さない（会話継続に干渉しない）ことも固定
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")
        lines = self.stats_lines()
        self.assertEqual(len(lines), 1)
        rec = lines[0]
        self.assertEqual(rec["tool_uses"], 42)
        self.assertEqual(rec["agent_type"], "explore")
        self.assertEqual(rec["agent_id"], "agentA")
        self.assertIn("ts", rec)
        self.assertFalse(self.state_file().exists())

    def test_stop_without_state_records_zero(self):
        # ツールを一度も使わず完走した子＝真に0回として分布へ残す
        self.subagent_stop(agent_id="agentZ")
        lines = self.stats_lines()
        self.assertEqual(len(lines), 1)
        self.assertEqual(lines[0]["tool_uses"], 0)

    def test_stale_state_cleanup_on_stop(self):
        old = self.seed_state(5, agent_id="deadAgent", session_id="oldSess")
        eight_days_ago = time.time() - 8 * 86400
        os.utime(old, (eight_days_ago, eight_days_ago))
        fresh = self.seed_state(5, agent_id="liveAgent")
        self.subagent_stop(agent_id="agentA")
        self.assertFalse(old.exists(), "TTL(7日)超の状態ファイルが掃除されていない")
        self.assertTrue(fresh.exists(), "TTL 内の状態ファイルまで消している")


if __name__ == "__main__":
    unittest.main(verbosity=2)
