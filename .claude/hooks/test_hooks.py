#!/usr/bin/env python3
"""
ブランチガード系フックのコマンド検知正規表現を回帰固定するテスト。
対象: hooks_common.py（COMMIT_CMD_RE / COMMIT_GENERATING_RE の単一定義）と、
      それを共有する各コミット系フック（正本は下の COMMIT_RE_HOOKS / GENERATING_RE_HOOKS
      ＝ここに名前を再掲すると撤去時に乖離するため列挙しない）

なぜ subprocess（exit code）ではなく正規表現を直接検証するか:
  guard の exit code はカレントブランチ（main か否か）とセンチネル有無に依存し環境的で不安定。
  一方バグの実体は「どのコマンド文字列を git commit と見なすか」＝正規表現にある。ここを固定すれば
  監査で実証した偽陰性（改行区切りの `git add⏎git commit` / グローバルオプション付き `git -C … commit`）
  の再発を、ブランチに依存せず確実に捕捉できる。

なぜ「pattern 一致テスト」でなく「identity テスト」か（2026-07-07 共有モジュール化に伴い変更）:
  旧設計は同一正規表現を全フックへ意図的に複製し、pattern 文字列の一致で乖離を検知していた。
  hooks_common への単一化後に守るべき性質は「各フックがローカル再定義せず共有定義を実際に
  使っていること」であり、実ファイルを exec して回収したオブジェクトが hooks_common のものと
  同一（is）であることを固定すれば、再定義・別定義の混入を構造的に検出できる。

なぜ実ファイルを exec して定数を回収するか:
  フックは import 時に stdin を読む実行スクリプト（`if __name__` ガード無し）のため通常の import では使えない。
  実ファイルを exec し、stdin 読込で sys.exit(0) する前に定義済みの定数を回収することで「本物」を検証する。
  フックが行う sys.stdout/stderr の張替えは、捨て先 BytesIO を渡して実 stdout を保護する。
"""
import io
import os
import sys
import unittest

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
# フック内の `from hooks_common import …` を exec 時に確実に解決できるよう、フックディレクトリを
# import パスへ載せる（pytest 等、sys.path[0] がここ以外になる起動でも identity 検証を成立させる）。
sys.path.insert(0, HOOKS_DIR)
import hooks_common  # noqa: E402（HOOKS_DIR の path 挿入が先に必要）


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


GUARD_RE = hooks_common.COMMIT_CMD_RE
GENERATING_RE = hooks_common.COMMIT_GENERATING_RE

# COMMIT_CMD_RE を共有すべき全フック（2026-07-07 に「全フック複製＋一致テスト」から
# hooks_common の単一定義へ移行。identity テストでローカル再定義の混入を検出する）。
COMMIT_RE_HOOKS = [
    "guard_commit_branch.py",
    "consume_protected_sentinel.py",
    "check_schema_change.py",
    "remind_task_diary.py",
]
# COMMIT_GENERATING_RE（merge/rebase/cherry-pick 検知）を共有すべきフック
# （handover hooks/fix ②: マージ完了経路の素通し対策。ブロック/消費の2本。
#   check_commit_granularity / check_lint_on_commit / mark_kotlin_tests_passed は
#   2026-07-12 に撤去＝コミットは可逆・テストは再実行可能で ROI 基準を満たさない裁定）。
GENERATING_RE_HOOKS = [
    "guard_commit_branch.py",
    "consume_protected_sentinel.py",
]

# 保護ブランチへの in-command switch/checkout 検知（guard_commit_branch.py 固有＝共有しない）。
# `git switch main && git merge …` 型の複合コマンドがブランチガードを素通りした穴への対処
# （2026-07-07 実地）。この正規表現は他フックに複製しないため一致テストの対象外。
SWITCH_TO_PROTECTED_RE = load_constant("guard_commit_branch.py", "SWITCH_TO_PROTECTED_RE")

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


# コマンド内で保護ブランチ(main)へ switch/checkout する＝実効コミット先が main ＝検知すべき
SWITCH_SHOULD_MATCH = [
    "git switch main && git merge x",         # 実地で素通しした型（handover の穴）
    "git checkout main && git commit -m x",
    "git switch -q main && git commit -m x",  # switch のオプション
    "git switch -c main && git commit",       # -c で main を作成＆切替
    "git checkout -b main && git merge x",     # -b で main を作成＆切替
    "git -C /repo switch main && git commit",  # git グローバルオプション付き
    "git switch main\ngit commit -m x",       # 改行区切り
    "cd x; git checkout main && git commit -m x",
]
# ブランチ移動でない・別ブランチ・言及＝検知してはならない（偽陽性で通常作業を阻害しない）
SWITCH_SHOULD_NOT_MATCH = [
    "git switch feature && git commit",       # 非保護ブランチへの切替
    "git checkout main -- file.txt",          # ファイル復元（ブランチ移動でない）
    "git checkout main~1 -- file",            # リビジョン指定（ブランチ名一致でない）
    "git merge main",                         # main は「マージ元」＝ switch/checkout ではない
    "git switch feature/main",                # 'main' を含むが別ブランチ
    "git checkout mainline",                  # 'main' で始まる別ブランチ
    "git log main",                           # 読み取り系
    "echo 'git switch main'",                 # クォート内の言及（コマンド境界に git が来ない）
]


class CommitDetectRegex(unittest.TestCase):
    def test_commit_re_shared_across_all_hooks(self):
        # 各フックが hooks_common の単一定義を「実際に」使っていること（ローカル再定義の混入検出）。
        # is（同一オブジェクト）で固定するため、pattern が偶然一致する別定義も弾ける。
        for filename in COMMIT_RE_HOOKS:
            with self.subTest(hook=filename):
                self.assertIs(
                    load_constant(filename, "COMMIT_CMD_RE"),
                    hooks_common.COMMIT_CMD_RE,
                    f"COMMIT_CMD_RE が hooks_common の共有定義でない: {filename}",
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
    def test_shared_across_hooks(self):
        # COMMIT_CMD_RE と同様、共有定義の identity で回帰固定する。
        for filename in GENERATING_RE_HOOKS:
            with self.subTest(hook=filename):
                self.assertIs(
                    load_constant(filename, "COMMIT_GENERATING_RE"),
                    hooks_common.COMMIT_GENERATING_RE,
                    f"COMMIT_GENERATING_RE が hooks_common の共有定義でない: {filename}",
                )

    def test_should_match(self):
        for cmd in GENERATING_SHOULD_MATCH:
            with self.subTest(cmd=cmd):
                self.assertTrue(GENERATING_RE.search(cmd), f"検知漏れ（偽陰性）: {cmd!r}")

    def test_should_not_match(self):
        for cmd in GENERATING_SHOULD_NOT_MATCH:
            with self.subTest(cmd=cmd):
                self.assertIsNone(GENERATING_RE.search(cmd), f"誤検知（偽陽性）: {cmd!r}")


class SwitchToProtectedRegex(unittest.TestCase):
    """`git switch/checkout main` の in-command 検知（複合コマンドによるガード素通し対策）。"""

    def test_should_match(self):
        for cmd in SWITCH_SHOULD_MATCH:
            with self.subTest(cmd=cmd):
                self.assertTrue(SWITCH_TO_PROTECTED_RE.search(cmd), f"検知漏れ（偽陰性）: {cmd!r}")

    def test_should_not_match(self):
        for cmd in SWITCH_SHOULD_NOT_MATCH:
            with self.subTest(cmd=cmd):
                self.assertIsNone(SWITCH_TO_PROTECTED_RE.search(cmd), f"誤検知（偽陽性）: {cmd!r}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
