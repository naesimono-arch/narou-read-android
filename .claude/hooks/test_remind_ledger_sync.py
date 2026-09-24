#!/usr/bin/env python3
"""remind_ledger_sync.py の回帰テスト（stale-check 項目12 が回収する）。

想起系フックの故障はサイレント（出力方式か検知条件を誤ると誰にも届かず、しかもエラーにならない）。
実際 remind_task_diary.py は `-m` しか見ていなかったため -F ヒアドキュメント運用では終日発火せず、
その事実は 2026-08-07 に実測されるまで誰にも見えていなかった。同じ轍を踏まないよう、
**発火する条件と黙る条件の両方**を陽性コントロールとして固定する。
"""
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import remind_ledger_sync as mod  # noqa: E402


def payload(cmd="git commit -F - <<MSG\nfix: 何か\nMSG"):
    return json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}})


class LedgerSyncCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        for args in (["init", "-q"], ["config", "user.email", "t@e"], ["config", "user.name", "t"]):
            subprocess.run(["git"] + args, cwd=self.root, capture_output=True)
        self._orig_root = mod.repo_root
        mod.repo_root = lambda: self.root
        self.addCleanup(self._restore)

    def _restore(self):
        mod.repo_root = self._orig_root
        self.tmp.cleanup()

    def write(self, rel, body):
        p = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(body)
        return p

    def stage(self, *rels):
        subprocess.run(["git", "add"] + list(rels), cwd=self.root, capture_output=True)

    def run_hook(self, cmd=None):
        old_stdin, old_stdout = sys.stdin, sys.stdout
        sys.stdin = io.StringIO(payload(cmd) if cmd else payload())
        sys.stdout = io.StringIO()
        try:
            mod.main()
            return sys.stdout.getvalue().strip()
        finally:
            sys.stdin, sys.stdout = old_stdin, old_stdout

    # --- 発火すべき場合 ---

    def test_fires_when_ledger_mentions_staged_file(self):
        self.write("handover.md", "- **[バグ]** `ui/skins/k/TocK.kt:399` のチップが幅を先取り\n")
        self.write("ui/skins/k/TocK.kt", "class A\n")
        self.stage("ui/skins/k/TocK.kt")
        out = self.run_hook()
        self.assertTrue(out, "台帳が言及しているのに黙った")
        ctx = json.loads(out)["hookSpecificOutput"]["additionalContext"]
        self.assertIn("handover.md:1", ctx, "行番号が出ていない＝どこを直すか一意に決まらない")
        self.assertIn("TocK.kt", ctx)

    def test_fires_for_heredoc_commit(self):
        """-F ヒアドキュメント形式でも発火する（remind_task_diary が踏んだ穴の再発防止）。"""
        self.write("STATUS.md", "現況: `PdfProcessingService.kt` は onTimeout が dead\n")
        self.write("PdfProcessingService.kt", "class S\n")
        self.stage("PdfProcessingService.kt")
        self.assertTrue(self.run_hook("git commit -q -F - <<'MSG'\nfix: 直す\nMSG"))

    def test_reports_pre_tool_use_event(self):
        """PreToolUse の additionalContext で返すこと（plain stdout はモデルに届かない）。"""
        self.write("handover.md", "`Foo.kt` が壊れている\n")
        self.write("Foo.kt", "x\n")
        self.stage("Foo.kt")
        out = json.loads(self.run_hook())["hookSpecificOutput"]
        self.assertEqual(out["hookEventName"], "PreToolUse")

    def test_fires_when_staged_ledger_holds_completion_line(self):
        """台帳を触っている瞬間こそ、消す判断が最も安い＝削除側の合図を出す。

        追記側（言及突合）にだけ合図があり削除側に無いことが台帳肥大の機序だった。
        """
        self.write("STATUS.md", "- 本棚のフィルタ行フェードは実装済み（ADR 0036）\n")
        self.write("Foo.kt", "x\n")
        self.stage("Foo.kt", "STATUS.md")
        out = self.run_hook()
        self.assertTrue(out, "完了記述が台帳に残っているのに黙った＝削除側の合図が死んでいる")
        ctx = json.loads(out)["hookSpecificOutput"]["additionalContext"]
        self.assertIn("STATUS.md:1", ctx, "行番号が出ていない＝どこを消すか一意に決まらない")

    def test_fires_when_staged_ledger_near_limit(self):
        """上限の9割で字数を出す（超えてから言うと「縮めて収める」誘惑が働く）。"""
        self.write("handover.md", "- やること\n" + "あ" * 7300)
        self.stage("handover.md")
        ctx = json.loads(self.run_hook())["hookSpecificOutput"]["additionalContext"]
        self.assertIn("上限 8000字", ctx)

    # --- 黙るべき場合 ---

    def test_silent_when_staged_ledger_is_clean(self):
        """台帳が staged でも、消す候補も字数の逼迫も無ければ黙る。

        ここが緩むと毎コミット同じ小言になり、読み飛ばされて合図として死ぬ。
        """
        self.write("handover.md", "`Foo.kt` が壊れている\n")
        self.write("Foo.kt", "x\n")
        self.stage("Foo.kt", "handover.md")
        self.assertEqual(self.run_hook(), "", "消す理由が無いのに想起が出た＝毎回出る小言になる")

    def test_silent_when_staged_ledger_is_current_value_only(self):
        """「未実装」「〜待ち」は完了ではなく現在値＝消す対象ではない。"""
        self.write("STATUS.md", "- 書き出しUIは未実装（別ラウンド）\n- 実機目視待ちが2件\n")
        self.stage("STATUS.md")
        self.assertEqual(self.run_hook(), "", "現在値の記述を消す候補として出した＝誤検知")

    def test_silent_when_ledger_does_not_mention(self):
        self.write("handover.md", "- 無関係なやること\n")
        self.write("Foo.kt", "x\n")
        self.stage("Foo.kt")
        self.assertEqual(self.run_hook(), "")

    def test_silent_when_nothing_staged(self):
        self.write("handover.md", "`Foo.kt` が壊れている\n")
        self.assertEqual(self.run_hook(), "")

    def test_silent_for_non_commit_command(self):
        self.write("handover.md", "`Foo.kt` が壊れている\n")
        self.write("Foo.kt", "x\n")
        self.stage("Foo.kt")
        self.assertEqual(self.run_hook("echo 'git commit という文字列'"), "")

    def test_silent_on_malformed_stdin(self):
        """stdin が壊れていても落ちない（フックの例外はコミットを妨げる）。"""
        old_stdin, old_stdout = sys.stdin, sys.stdout
        sys.stdin, sys.stdout = io.StringIO("not json"), io.StringIO()
        try:
            self.assertEqual(mod.main(), 0)
        finally:
            sys.stdin, sys.stdout = old_stdin, old_stdout


if __name__ == "__main__":
    unittest.main()
