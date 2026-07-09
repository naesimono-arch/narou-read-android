#!/usr/bin/env python3
"""コミット系フックの共通定義（検知正規表現・stdin/stdout ボイラープレート）。

なぜ共有モジュール化したか（2026-07-07。旧設計は「意図的複製＋test_hooks.py の一致テスト」）:
  同一の検知正規表現が 6 フックに複製され、変更のたびに全ファイル更新が要る運用だった。
  一致テストで乖離は検知できても「更新漏れ→テスト失敗→再更新」の往復コストが残るため、
  定義を単一化して乖離を構造的に不可能にする。test_hooks.py は「各フックがこの共有定義を
  実際に使っていること（ローカル再定義していないこと）」を identity で回帰固定する。

import が成立する理由:
  フックは `python <hooks_dir>/<hook>.py` で起動され sys.path[0] がスクリプトのディレクトリ＝
  本ディレクトリになるため、追加設定なしで import できる（Windows/Linux 共通）。
既知の制約:
  フック単体ファイルだけを他ブランチへコピーすると本モジュールが無く ImportError でフックごと
  落ちる（git 上は常に同居するため通常運用では起きない。ADR 0004 の「配線は起動時固定・実ファイルは
  ブランチ追従」問題とは別系統）。
"""
import io
import json
import re
import subprocess
import sys


def wrap_stdio():
    """stdout/stderr を UTF-8 へ張り替える（Windows cp932 文字化け対策＝task_diary #26）。"""
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


def read_payload():
    """stdin のフック JSON を読む。壊れていれば None（呼び出し側は exit 0 の fail-open に倒す）。"""
    try:
        return json.load(sys.stdin)
    except (json.JSONDecodeError, EOFError, ValueError):
        return None


def make_sha_verifier(cwd):
    """
    「SHA がリポジトリにコミットとして実在するか」の照合 callable を返す
    （git repo でなければ None＝照合なし）。Stop フックと CLI アナライザで共用。
    なぜ必要か: system prompt の gitStatus（Recent commits）由来の実在 SHA への言及は
    transcript に証拠が構造的に残らず、検知器 Tier A2 が偽陽性化する
    （2026-07-09 Stop ライブ実測・bcd69bb6）。実在照合は検知エンジン外（ここ）に置き、
    detect_fabricated_execution_core は純ロジック（subprocess なし）を維持する。
    """
    try:
        r = subprocess.run(["git", "rev-parse", "--git-dir"], cwd=cwd,
                           capture_output=True, timeout=5)
        if r.returncode != 0:
            return None
    except (OSError, subprocess.SubprocessError):
        return None

    cache = {}

    def verify(sha):
        if sha in cache:
            return cache[sha]
        try:
            # ^{commit} 付き: blob/tree の hex に誤ヒットさせず「コミットとして実在」に限定
            r2 = subprocess.run(["git", "cat-file", "-e", f"{sha}^{{commit}}"],
                                cwd=cwd, capture_output=True, timeout=5)
            ok = r2.returncode == 0
        except (OSError, subprocess.SubprocessError):
            # 照合失敗は「実在しない」側（＝検知維持）。誤降格より安全で従来動作と同じ。
            ok = False
        cache[sha] = ok
        return ok

    return verify


# 実行コマンドとしての `git commit` を検知する正規表現（コミット系フック全体の単一定義）。
# なぜコマンド境界に限定するか:
#   guard 系は exit 2 で実際にブロックするため精度が要る。素朴な \bgit\s+commit\b だと
#   `echo '...git commit...'`（クォート内の単なる言及）まで誤ブロックする（実際に検証中に巻き込まれた）。
# なぜ境界に改行 \n を含めるか:
#   `git add -A`⏎`git commit ...` のような複数行コマンド（heredoc 等）では commit が行頭に来る。
#   改行を境界に含めないと、この最頻パターンの直接コミットを取りこぼす（監査で実証）。
# なぜ git と commit の間にグローバルオプションを許容するか:
#   `git -C <path> commit` / `git -c k=v commit` / `git --git-dir=… commit` も実コミット。
#   オプションを許容しないと取りこぼす（監査で実証）。引数・メッセージ内の言及は引き続き無視する。
COMMIT_CMD_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    r"\s+commit\b"
)

# コミットを生成する merge/rebase/cherry-pick の検知
# （guard のブロック／consume の消費／granularity のテストゲートの3フックで共有）。
# なぜ: 競合しない `git merge <branch>` やマージ/リベースの `--continue` は "commit" トークンを
# 含まずに保護ブランチの HEAD を進める＝リテラル git commit 検知だけでは素通りしていた
# （2026-07-06 の feat+kotlin 統合で実地に露呈＝handover hooks/fix ②）。
# --abort/--quit はコミットを生成しない回復コマンドのため除外（誤ブロックすると main 上での
# マージ中断すらセンチネルが要る本末転倒になる）。--no-commit も生成しない（後続の明示
# git commit が COMMIT_CMD_RE で捕まる）ため除外。
COMMIT_GENERATING_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    # (?!-) は merge-base / merge-file 等の読み取り系サブコマンドへの誤発火防止
    r"\s+(?:merge|rebase|cherry-pick)\b(?!-)(?![^\n;|&]*--(?:abort|quit|no-commit)\b)"
)
