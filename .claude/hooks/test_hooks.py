#!/usr/bin/env python3
"""
ブランチガード系フックのコマンド検知正規表現 COMMIT_CMD_RE を回帰固定するテスト。
対象: guard_commit_branch.py（PreToolUse ブロック）/ consume_protected_sentinel.py（PostToolUse 消費）

なぜ subprocess（exit code）ではなく正規表現を直接検証するか:
  guard の exit code はカレントブランチ（main か否か）とセンチネル有無に依存し環境的で不安定。
  一方バグの実体は「どのコマンド文字列を git commit と見なすか」＝正規表現にある。ここを固定すれば
  監査で実証した偽陰性（改行区切りの `git add⏎git commit` / グローバルオプション付き `git -C … commit`）
  の再発を、ブランチに依存せず確実に捕捉できる。

なぜ実ファイルを exec して定数を回収するか:
  フックは import 時に stdin を読む実行スクリプト（`if __name__` ガード無し）のため通常の import では使えない。
  正規表現をテストに複製すると本体との乖離を検出できなくなる。実ファイルを exec し、stdin 読込で
  sys.exit(0) する前に定義済みの COMMIT_CMD_RE を回収することで「本物」を検証する。
  フックが行う sys.stdout/stderr の張替えは、捨て先 BytesIO を渡して実 stdout を保護する。
"""
import io
import os
import sys
import unittest

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))


def load_constant(filename, name):
    """フック実ファイルを exec し、stdin 読込で SystemExit する前に定義された定数を回収する。"""
    path = os.path.join(HOOKS_DIR, filename)
    with open(path, encoding="utf-8") as f:
        src = f.read()
    ns = {}
    saved = (sys.stdin, sys.stdout, sys.stderr)
    # stdin=有効な空JSON（直後の tool_name チェックで sys.exit(0) する）。
    # stdout/stderr=捨て先 BytesIO 裏付けの TextIOWrapper。フックがこれらを .buffer ごと
    # 張り替えても実 stdout には触れず、GC で閉じられるのも throwaway な BytesIO のみ。
    sys.stdin = io.StringIO("{}")
    sys.stdout = io.TextIOWrapper(io.BytesIO())
    sys.stderr = io.TextIOWrapper(io.BytesIO())
    try:
        exec(compile(src, path, "exec"), ns)
    except SystemExit:
        pass  # 定数は stdin 読込より前に定義済み＝ ns に残る
    finally:
        sys.stdin, sys.stdout, sys.stderr = saved
    return ns[name]


GUARD_RE = load_constant("guard_commit_branch.py", "COMMIT_CMD_RE")
CONSUME_RE = load_constant("consume_protected_sentinel.py", "COMMIT_CMD_RE")

# COMMIT_CMD_RE を定義する全フック（2026-07-06 stale-check で緩い検知〈\bgit\s+commit\b・
# 単純部分文字列〉の誤発火を解消し、厳密版を全フックへ複製統一した）。
# 意図的複製（共有 util 化しない設計）のため、乖離はこの一致テストで検出する。
COMMIT_RE_HOOKS = [
    "guard_commit_branch.py",
    "consume_protected_sentinel.py",
    "check_commit_granularity.py",
    "check_schema_change.py",
    "check_lint_on_commit.py",
    "remind_task_diary.py",
]
# COMMIT_GENERATING_RE（merge/rebase/cherry-pick 検知）を定義するフック
# （handover hooks/fix ②: マージ完了経路の素通し対策。ブロック/消費/テストゲートの3本のみ）。
GENERATING_RE_HOOKS = [
    "guard_commit_branch.py",
    "consume_protected_sentinel.py",
    "check_commit_granularity.py",
]
GENERATING_RE = load_constant("guard_commit_branch.py", "COMMIT_GENERATING_RE")

# 実行コマンドとしての git commit ＝検知すべき（block / consume 対象）
SHOULD_MATCH = [
    "git commit -m x",
    "cd foo && git commit -m x",
    "git add -A; git commit -m x",
    "git add -A | cat; git commit -m x",
    "git add -A\ngit commit -m x",            # 改行区切り（監査で実証した偽陰性）
    "git add -A\n  git commit -m x",          # 改行＋インデント
    'git commit -m "$(cat <<EOF\nmsg\nEOF\n)"',
    "git   commit --amend",
    "git -C . commit -m x",                   # -C <path>（監査で実証した偽陰性）
    "git -c user.name=x commit -m x",         # -c <kv>
    "git --git-dir=/r/.git commit -m x",      # --opt=val
    "git -C /a/b -c k=v commit -m x",         # 複合
]
# 言及・非コミット＝検知してはならない（偽陽性で通常作業を阻害しない）
SHOULD_NOT_MATCH = [
    "echo '...git commit...'",                # クォート内の言及
    "echo git commit && ls",                  # echo の引数（先頭は echo）
    "grep 'git commit' file.txt",
    "git status",
    "git log --oneline",
    "git add -A",
]


# コミットを生成する merge/rebase/cherry-pick ＝検知すべき
GENERATING_SHOULD_MATCH = [
    "git merge feature/x",
    "git merge --continue",                   # マージ完了経路（handover hooks/fix ②の主対象）
    "git rebase --continue",
    "git rebase main",
    "git cherry-pick abc1234",
    "git cherry-pick --continue",
    "git -C . merge topic",                   # グローバルオプション付き
    "git add -A\ngit merge topic",            # 改行区切り
    "git fetch && git merge origin/main",
]
# コミットを生成しない・言及＝検知してはならない
GENERATING_SHOULD_NOT_MATCH = [
    "git merge --abort",                      # 回復コマンド（ブロックすると中断すら不能になる）
    "git rebase --abort",
    "git cherry-pick --abort",
    "git rebase --quit",
    "git merge --no-commit topic",            # 後続の明示 git commit が COMMIT_CMD_RE で捕まる
    "git merge-base main HEAD",               # 読み取り系サブコマンド
    "echo 'git merge'",                       # クォート内の言及
    "grep 'git rebase' file.txt",
    "git status",
]


class CommitDetectRegex(unittest.TestCase):
    def test_guard_and_consume_identical(self):
        # 検知整合: 片方だけ直す事故を防ぐため両フックの定義は完全一致であること
        self.assertEqual(GUARD_RE.pattern, CONSUME_RE.pattern)

    def test_commit_re_identical_across_all_hooks(self):
        # 統一先の全フックで COMMIT_CMD_RE が完全一致であること（意図的複製の乖離検出）
        for filename in COMMIT_RE_HOOKS:
            with self.subTest(hook=filename):
                self.assertEqual(
                    GUARD_RE.pattern,
                    load_constant(filename, "COMMIT_CMD_RE").pattern,
                    f"COMMIT_CMD_RE が guard と乖離: {filename}",
                )

    def test_should_match(self):
        for cmd in SHOULD_MATCH:
            with self.subTest(cmd=cmd):
                self.assertTrue(GUARD_RE.search(cmd), f"検知漏れ（偽陰性）: {cmd!r}")

    def test_should_not_match(self):
        for cmd in SHOULD_NOT_MATCH:
            with self.subTest(cmd=cmd):
                self.assertIsNone(GUARD_RE.search(cmd), f"誤検知（偽陽性）: {cmd!r}")


class CommitGeneratingRegex(unittest.TestCase):
    def test_identical_across_hooks(self):
        for filename in GENERATING_RE_HOOKS:
            with self.subTest(hook=filename):
                self.assertEqual(
                    GENERATING_RE.pattern,
                    load_constant(filename, "COMMIT_GENERATING_RE").pattern,
                    f"COMMIT_GENERATING_RE が guard と乖離: {filename}",
                )

    def test_should_match(self):
        for cmd in GENERATING_SHOULD_MATCH:
            with self.subTest(cmd=cmd):
                self.assertTrue(GENERATING_RE.search(cmd), f"検知漏れ（偽陰性）: {cmd!r}")

    def test_should_not_match(self):
        for cmd in GENERATING_SHOULD_NOT_MATCH:
            with self.subTest(cmd=cmd):
                self.assertIsNone(GENERATING_RE.search(cmd), f"誤検知（偽陽性）: {cmd!r}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
