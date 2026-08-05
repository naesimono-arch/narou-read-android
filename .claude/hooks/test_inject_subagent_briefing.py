#!/usr/bin/env python3
"""inject_subagent_briefing.py（SubagentStart 定型規律の自動注入）の回帰固定テスト。

なぜ subprocess で end-to-end 検証するか（test_hooks.py の正規表現直検証とは方式が異なる理由）:
  本フックの本質は「agent_type に応じて何を additionalContext として stdout へ吐くか」＝
  stdin→stdout の実挙動にある。判定は agent_type と環境変数/cwd のみに依存し git ブランチや
  センチネルに依存しない＝環境非依存で安定するため、実プロセスの returncode と stdout(JSON) を
  そのまま突けるのが最も本質に近い（test_check_sequence_id_collision.py と同方式）。

なぜ fail-open を必須テストにするか:
  SubagentStart はブロック目的でない注入専用フック。壊れた stdin で例外死・非ゼロ終了すると
  サブエージェント起動そのものを妨げうる（module docstring の「失敗は常に fail-open」）。
  沈黙故障（task_diary #44）を回帰で捕まえるため陰性コントロールを固定する。

なぜゲートコマンドと CLAUDE.md を突合するか（handover「hooks・テスト宿題」の "なお良い" 要件）:
  注入文中のビルドゲートは CLAUDE.md の自己検証手順の複製（Gradle タスク testDebugUnitTest）。
  片方だけ変わるとサブエージェントに古い/誤ったゲートを配り続けるが無症状で気づけない。
  CLAUDE.md 側の `./gradlew <task>` からタスクを機械抽出し、注入文がそのタスクを含むことを
  固定する＝どちらが動いても乖離時に落ちる（下の GateCommandMatchesClaudeMd 参照）。
"""
import json
import os
import re
import subprocess
import sys
import unittest

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HOOKS_DIR, "inject_subagent_briefing.py")
# ROOT = リポジトリ直下（.claude/hooks から3つ上）。CLAUDE.md 突合に使う。
ROOT = os.path.dirname(os.path.dirname(HOOKS_DIR))

# 注入文を種別判定するための安定部分文字列（本文全文ではなく骨格語に依存＝文言微修正に強い）。
IMPLEMENTER_MARK = "プロジェクト定型規律"
RESEARCH_MARK = "調査報告の定型"
DEVICE_MARK = "実機検証の禁忌"


def run_hook(payload, project_dir_env=None):
    """stdin に payload(JSON) を流してフックを実行し proc を返す。

    project_dir_env=None なら CLAUDE_PROJECT_DIR を環境から除去（cwd/デフォルト解決を試験可能に）、
    文字列なら当該値を設定する。親プロセスの CLAUDE_PROJECT_DIR 混入で試験が揺れるのを防ぐ。
    """
    env = dict(os.environ)
    if project_dir_env is None:
        env.pop("CLAUDE_PROJECT_DIR", None)
    else:
        env["CLAUDE_PROJECT_DIR"] = project_dir_env
    return subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True, timeout=15, env=env,
    )


def run_raw(stdin_bytes):
    """壊れた/空 stdin をそのまま流す（JSON 化しない）fail-open 試験用。"""
    env = dict(os.environ)
    env.pop("CLAUDE_PROJECT_DIR", None)
    return subprocess.run(
        [sys.executable, HOOK],
        input=stdin_bytes, capture_output=True, timeout=15, env=env,
    )


def context_of(proc):
    """stdout(JSON) から additionalContext を取り出す（出力形式も同時に検証する）。"""
    out = json.loads(proc.stdout.decode("utf-8"))
    return out["hookSpecificOutput"]["additionalContext"]


class AgentTypeRouting(unittest.TestCase):
    """(a) agent_type 分岐: 実装系→実装briefing・調査系→調査briefing・対象外/空/欠落→注入なし。"""

    def _assert_implementer(self, agent_type):
        proc = run_hook({"agent_type": agent_type})
        self.assertEqual(proc.returncode, 0)
        self.assertIn(IMPLEMENTER_MARK, context_of(proc))

    def _assert_research(self, agent_type):
        proc = run_hook({"agent_type": agent_type})
        self.assertEqual(proc.returncode, 0)
        ctx = context_of(proc)
        self.assertIn(RESEARCH_MARK, ctx)
        # 調査系には重量級の実装規律を混ぜない（軽注入である回帰の固定）。
        self.assertNotIn(IMPLEMENTER_MARK, ctx)

    def _assert_no_injection(self, payload):
        proc = run_hook(payload)
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_general_purpose_is_implementer(self):
        self._assert_implementer("general-purpose")

    def test_claude_is_implementer(self):
        self._assert_implementer("claude")

    def test_explore_is_research(self):
        self._assert_research("explore")

    def test_plan_is_research(self):
        self._assert_research("plan")

    def test_agent_type_is_case_insensitive(self):
        # 実装は .lower() で正規化する＝呼出側が "General-Purpose"/"Explore"/"Plan" を渡しても
        # 同じ分岐に落ちること（大小差でサイレントに注入漏れしない保証）。
        self.assertIn(IMPLEMENTER_MARK, context_of(run_hook({"agent_type": "General-Purpose"})))
        self.assertIn(RESEARCH_MARK, context_of(run_hook({"agent_type": "Explore"})))
        self.assertIn(RESEARCH_MARK, context_of(run_hook({"agent_type": "Plan"})))

    def test_foreground_wait_rule_reaches_long_runners(self):
        # 「完了通知待ち」駐機事故（2026-08-06 実害2件＝docs/knowledge/subagent-idle-stop-parks-forever.md）の
        # 焼き込みが長走行を打ちうる両種別（実装系・実機系）に届くことを固定。骨格語＝「完了扱いで駐機」。
        for t in ("general-purpose", "device-verify"):
            self.assertIn("完了扱いで駐機", context_of(run_hook({"agent_type": t})))

    def test_device_verify_gets_device_rules(self):
        # 実機系は「共通規律＋実機固有の禁忌」の両方を受け取る。
        # なぜ中身まで固定するか: 本分岐の目的が「監督がブリーフへ手で転記していた禁忌の
        # 転記漏れを機械で塞ぐ」ことなので、項目が黙って抜けたら分岐の意味が消えるため。
        proc = run_hook({"agent_type": "device-verify"})
        self.assertEqual(proc.returncode, 0)
        ctx = context_of(proc)
        self.assertIn(IMPLEMENTER_MARK, ctx)
        self.assertIn(DEVICE_MARK, ctx)
        for must in ("実蔵書を絶対に消さない", "connectedAndroidTest", "取り違え", "platform-tools"):
            with self.subTest(rule=must):
                self.assertIn(must, ctx)

    def test_device_rules_do_not_leak_into_other_types(self):
        # 実機禁忌が実装系・調査系へ混入しないこと（注入の肥大と、実機を触ってよいという誤解の防止）。
        for t in ("general-purpose", "claude", "explore", "plan"):
            with self.subTest(agent_type=t):
                self.assertNotIn(DEVICE_MARK, context_of(run_hook({"agent_type": t})))

    def test_out_of_scope_agent_types_no_injection(self):
        # 外部調査・設定系（module docstring の対象外種別）は一切注入しない。
        for t in ("claude-code-guide", "statusline-setup", "antigravity-delegate", "unknown-x"):
            with self.subTest(agent_type=t):
                self._assert_no_injection({"agent_type": t})

    def test_empty_agent_type_no_injection(self):
        self._assert_no_injection({"agent_type": ""})

    def test_missing_agent_type_no_injection(self):
        # agent_type キー欠落（None 相当）でも例外死せず素通し。
        self._assert_no_injection({"cwd": "/somewhere"})


class FailOpen(unittest.TestCase):
    """(b) 壊れた stdin での fail-open: ブロックせず exit 0・何も注入しない。"""

    def test_non_json_stdin_fail_open(self):
        proc = run_raw(b"not-json-garbage")
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")

    def test_empty_stdin_fail_open(self):
        proc = run_raw(b"")
        self.assertEqual(proc.returncode, 0)
        self.assertEqual(proc.stdout.decode("utf-8").strip(), "")


class OutputFormat(unittest.TestCase):
    """(c) JSON 出力形式: hookSpecificOutput.hookEventName / additionalContext の構造固定。"""

    def _assert_shape(self, agent_type):
        proc = run_hook({"agent_type": agent_type})
        # stdout 全体が単一の JSON（余計な行を吐いていない）ことも同時に固定する。
        out = json.loads(proc.stdout.decode("utf-8"))
        self.assertEqual(list(out.keys()), ["hookSpecificOutput"])
        hso = out["hookSpecificOutput"]
        self.assertEqual(hso["hookEventName"], "SubagentStart")
        self.assertIsInstance(hso["additionalContext"], str)
        self.assertTrue(hso["additionalContext"].strip())

    def test_implementer_output_shape(self):
        self._assert_shape("general-purpose")

    def test_research_output_shape(self):
        self._assert_shape("explore")


class ProjectDirResolution(unittest.TestCase):
    """project_dir 解決順（env CLAUDE_PROJECT_DIR > payload.cwd > "."）が注入ゲートに反映される。"""

    def _gate_line(self, ctx):
        return next(l for l in ctx.split("\n") if l.strip().startswith("cd "))

    def test_env_wins_over_cwd(self):
        proc = run_hook({"agent_type": "claude", "cwd": "/from-cwd"}, project_dir_env="/from-env")
        line = self._gate_line(context_of(proc))
        self.assertIn("cd /from-env/android", line)

    def test_cwd_fallback_when_no_env(self):
        proc = run_hook({"agent_type": "claude", "cwd": "/from-cwd"})  # env は run_hook が除去
        self.assertIn("cd /from-cwd/android", self._gate_line(context_of(proc)))

    def test_default_dot_when_no_env_no_cwd(self):
        proc = run_hook({"agent_type": "claude"})
        self.assertIn("cd ./android", self._gate_line(context_of(proc)))


class GateCommandMatchesClaudeMd(unittest.TestCase):
    """(d) 注入ゲートの Gradle タスクが CLAUDE.md のビルド手順と乖離したら落ちる突合。"""

    def _claude_md_gradle_tasks(self):
        """CLAUDE.md の `./gradlew <task>` からタスク名を機械抽出（自己検証ゲートの正本）。"""
        with open(os.path.join(ROOT, "CLAUDE.md"), encoding="utf-8") as f:
            text = f.read()
        tasks = re.findall(r"\./gradlew\s+([A-Za-z0-9:_.-]+)", text)
        # 前提: CLAUDE.md はビルドゲートを `./gradlew …` 形で明記している。
        # ここが空になる＝手順の記法が変わった＝それ自体が突合の前提崩れなので気づけるよう固定。
        self.assertTrue(tasks, "CLAUDE.md に `./gradlew <task>` 形のビルド手順が見つからない")
        return tasks

    def test_injected_gate_contains_claude_md_task(self):
        # 実装系注入文（general-purpose）が CLAUDE.md の全 gradlew タスクを含むこと。
        # 片方だけ testDebugUnitTest を改名/削除すると、この包含が破れて落ちる（双方向の drift 検知）。
        ctx = context_of(run_hook({"agent_type": "general-purpose"}))
        for task in self._claude_md_gradle_tasks():
            with self.subTest(task=task):
                self.assertIn(task, ctx,
                              f"注入ゲートが CLAUDE.md のタスク {task!r} を含まない（手順乖離）")


if __name__ == "__main__":
    unittest.main(verbosity=2)
