#!/usr/bin/env python3
"""
stop_guard_fabrication.py（Stop フックのアダプタ層）の陽性コントロールテスト。

なぜこのテストが要るか: エンジン detect_fabricated_execution_core は
test_detect_fabricated_execution.py の 36 テストで被覆済みだが、アダプタ層＝
「stdin JSON → core.analyze(scope=last_turn) → blockers 抽出 → decision:block を
stdout JSON 出力／fail-open 系は空 stdout」の経路には自動テストが無かった
（handover「Stop アダプタの陽性コントロールテストが無い」）。ここを subprocess で
フックを実起動して被覆する。手動スモークだけでは回帰で守れないため。

判定は stdout の JSON（フックは全経路 exit 0 で、block/素通しを stdout で表現する）:
  block   = json.loads(stdout)["decision"] == "block"
  素通し  = stdout.strip() == ""

フィクスチャ・ビルダ（asst_text/asst_tool/tool_result）は
test_detect_fabricated_execution から import して借用する（同一 JSONL 形式）。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

# フックとビルダは同ディレクトリ。cwd 非依存でパス解決する。
HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HOOKS_DIR, "stop_guard_fabrication.py")
if HOOKS_DIR not in sys.path:
    sys.path.insert(0, HOOKS_DIR)

# エンジンテストのビルダを流用（合成 JSONL レコード生成）。
from test_detect_fabricated_execution import asst_text, asst_tool, tool_result, GRADLE_OK

# Tier B の block を決定化するための遠未来タイムスタンプ。
# なぜ: フックは sentinel_dir を実 .claude/（HOOKS_DIR の親）に固定算出する＝stdin で
# 差し替え不可。実 .kotlin_tests_passed が存在すると、その mtime が主張時刻以降のとき
# _sentinel_state が fresh 判定し、Tier B の confidence を 0.8→0.6 へ減衰させる。すると
# アダプタの block 条件（unverified_test_claim ∧ conf≥0.8）を外れて block 漏れする。
# 主張 ts を遠未来に置けば mtime < 主張時刻となり fresh=False＝conf 0.8 を維持でき、
# センチネルの有無に依らず block を決定化できる（ビルダ既定の TS=2026 のままだと不安定）。
FUTURE_TS = "2099-01-01T00:00:00Z"


def write_jsonl(records):
    """records を一時 JSONL ファイルへ書き、そのパスを返す（呼び手が削除する）。"""
    fd, path = tempfile.mkstemp(suffix=".jsonl")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write("\n".join(json.dumps(r, ensure_ascii=False) for r in records))
    return path


def run_hook(stdin_obj):
    """フックを subprocess 起動し (returncode, stdout) を返す。"""
    proc = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(stdin_obj),
        capture_output=True,
        text=True,
    )
    return proc.returncode, proc.stdout


class StopGuardAdapter(unittest.TestCase):
    def _run_with_transcript(self, records, stop_hook_active=False):
        path = write_jsonl(records)
        try:
            return run_hook({"transcript_path": path, "stop_hook_active": stop_hook_active})
        finally:
            os.remove(path)

    def test_a3_harness_block_is_blocked(self):
        # A3: 生の <task-id> ハーネスブロックを地の文化（センチネル非依存・conf 0.9）→ block。
        rc, out = self._run_with_transcript(
            [asst_text("m1", "結果です。\n<task-id>abc123def</task-id>\n完了しました。")]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("fabricated_harness_block", out)

    def test_tier_b_unverified_claim_is_blocked(self):
        # Tier B: 裏取り無しのテスト成功断言（成功実行ゼロ）→ conf 0.8 で block。
        # 主張 ts を遠未来にしてセンチネル fresh 減衰を無効化する（FUTURE_TS の理由参照）。
        rec = asst_text("m1", "テストは全部通りました。問題ありません。")
        rec["timestamp"] = FUTURE_TS
        rc, out = self._run_with_transcript([rec])
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("unverified_test_claim", out)

    def test_clean_grounded_claim_passes(self):
        # 成功 gradle 実行の tool_result で裏取り済み → blockers 空 → 素通し（空 stdout）。
        rc, out = self._run_with_transcript(
            [
                asst_tool("m1", "toolu_1", "Bash",
                          {"command": "cd android && ./gradlew testDebugUnitTest"}),
                tool_result("toolu_1", GRADLE_OK, structured={"stdout": GRADLE_OK, "stderr": ""}),
                asst_text("m2", "単体テストが成功しました。"),
            ]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(out.strip(), "")

    def test_stop_hook_active_short_circuits(self):
        # 再発火ループ防止: stop_hook_active:true なら block 相当の transcript でも即素通し
        # （transcript を解析する前に short-circuit する）。
        rc, out = self._run_with_transcript(
            [asst_text("m1", "結果です。\n<task-id>abc123def</task-id>\n完了しました。")],
            stop_hook_active=True,
        )
        self.assertEqual(rc, 0)
        self.assertEqual(out.strip(), "")

    def test_missing_transcript_fails_open(self):
        # transcript_path が存在しない → fail-open（空 stdout・ユーザー作業を止めない）。
        rc, out = run_hook({"transcript_path": "/nonexistent/does-not-exist.jsonl"})
        self.assertEqual(rc, 0)
        self.assertEqual(out.strip(), "")

    # ─── Tier C（misread 型・2026-07-07）＋ A2 昇格の陽性コントロール ───

    def test_c1_blocked_commit_completion_is_blocked(self):
        # C1: コミットがフックでブロックされたのに「マージ完了」（事象F①型）→ block。
        # センチネル非依存（conf 0.85 固定）なので FUTURE_TS は不要。
        blocked = ("PreToolUse:Bash hook error: [python check_commit_granularity.py]: "
                   "[Kotlinテスト古い] コミットをブロックします")
        rc, out = self._run_with_transcript(
            [
                asst_tool("m1", "toolu_1", "Bash", {"command": "git commit -m 'merge x'"}),
                tool_result("toolu_1", blocked, is_error=True),
                asst_text("m2", "resilience マージ完了（clean）です。"),
            ]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("completion_after_blocked_commit", out)

    def test_c3_unverified_write_claim_is_blocked(self):
        # C3: Read しかしていないのに「memory 本体を更新しました」（事象F⑤型）→ block。
        rc, out = self._run_with_transcript(
            [
                asst_tool("m1", "toolu_1", "Read",
                          {"file_path": "/home/u/.claude/projects/p/memory/git-guard.md"}),
                tool_result("toolu_1", "---\nname: git-guard\n---\n本文"),
                asst_text("m2", "memory 本体を更新しました。"),
            ]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("unverified_write_claim", out)

    def test_a2_fabricated_sha_is_blocked(self):
        # A2 昇格: 実出力に存在しない SHA の断言（証拠の result 層化後・conf 0.8）→ block。
        rc, out = self._run_with_transcript(
            [asst_text("m1", "コミット abc1234 を作成しました。")]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("fabricated_concrete_token", out)

    def test_d4_phantom_turn_marker_is_blocked(self):
        # D4 昇格（正解データK・L320）: 自分の text ブロックにハーネス割込マーカーを自己生成
        # ＝幻のユーザーターン → block。マーカーリテラルの特異性で conf 0.9・センチネル非依存
        # （FUTURE_TS 不要）。D1〜D3 は非ブロックのまま（tiers=ABCD だが blockers は D4 のみ）。
        rc, out = self._run_with_transcript(
            [asst_text("m1", "確認します。\n\nuser[Request interrupted by user]\n\n"
                             "勝手にすすめないで！何をどう変更したのか説明を求めます")]
        )
        self.assertEqual(rc, 0)
        self.assertEqual(json.loads(out)["decision"], "block")
        self.assertIn("phantom_turn_marker", out)


if __name__ == "__main__":
    unittest.main()
