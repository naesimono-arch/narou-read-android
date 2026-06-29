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


class CommitDetectRegex(unittest.TestCase):
    def test_guard_and_consume_identical(self):
        # 検知整合: 片方だけ直す事故を防ぐため両フックの定義は完全一致であること
        self.assertEqual(GUARD_RE.pattern, CONSUME_RE.pattern)

    def test_should_match(self):
        for cmd in SHOULD_MATCH:
            with self.subTest(cmd=cmd):
                self.assertTrue(GUARD_RE.search(cmd), f"検知漏れ（偽陰性）: {cmd!r}")

    def test_should_not_match(self):
        for cmd in SHOULD_NOT_MATCH:
            with self.subTest(cmd=cmd):
                self.assertIsNone(GUARD_RE.search(cmd), f"誤検知（偽陽性）: {cmd!r}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
