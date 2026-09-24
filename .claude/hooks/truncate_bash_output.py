#!/usr/bin/env python3
r"""⚠️ 凍結中（2026-08-17 ユーザー裁定）＝**このフックは配線されていない**。

`.claude/settings.json` に登録が無いので、ここに在るだけで**一度も実行されない**
（テストだけは `python .claude/hooks/test_truncate_bash_output.py` で単体で回る）。

なぜ凍結したか: 削減が**全体の約 2.6%**（閾値 4,000 字で介入率 3.6%）にすぎないのに対し、
  ① **未文書化の内部フィールド `hookSpecificOutput.updatedToolOutput` に依存**する
  ② **監督が見る出力そのものを加工する監視パイプラインの改変**である
     （ハーネスがこの変更に self-modification のセキュリティ警告を出した）
  ＝効果と危うさが釣り合わない。実測の正本＝`docs/knowledge/context-cost-hook-levers-measured-limits.md`／
  凍結の記録＝`docs/backlog-frozen.md`「Bash 出力の自動切り詰めフック」。

解凍条件: **`hookSpecificOutput.updatedToolOutput` が公式 docs に記載されたとき**（未文書化 API 依存という減点材料が消えるため）。

解凍手順: `.claude/settings.json` の `hooks.PostToolUse` 配列へ**次のブロックを足すだけ**（コード側の変更は不要）。
  凍結前は `"matcher": "*"`（count_delegation_turns）のブロックの直前に置いていた。
  ⚠️ 閾値を変えるなら先に `python tools/measure_bash_output_size.py` を回し、分布から決め直すこと。

      {
        "matcher": "Bash|PowerShell",
        "hooks": [
          {
            "type": "command",
            "command": "python \"${CLAUDE_PROJECT_DIR}/.claude/hooks/truncate_bash_output.py\""
          }
        ]
      },

==== 以下は凍結前の設計メモ（当時のまま） ====

PostToolUse hook: 長すぎる Bash/PowerShell の stdout を「両端残し＋全文退避」で切り詰める。

対象ツール: Bash / PowerShell

なぜ必要か（コスト構造）:
  cache_read の実効寄与（トークン×残ターン）は読み込み側に偏り、Bash 出力が全体の約 20〜22% を占める
  （実測の正本＝docs/knowledge/context-cost-breakdown-2026-08-10.md）。長い出力は 1 回読み捨てても
  以降の全ターンで再送され続けるため、素の量より遥かに高くつく。

なぜ「更新」できるのか（この機構の一次確認・2026-08-17 / Claude Code 2.1.233）:
  PostToolUse の plain stdout はモデルに届かない（task_diary #28）。**しかし本体バイナリには
  `hookSpecificOutput.updatedToolOutput` が実装されており**（説明文字列「Replaces the tool output
  before it is sent to the model」・パース箇所で `u.updatedToolOutput = e.hookSpecificOutput.updatedToolOutput`）、
  これはモデルへ渡る tool_result 本体を差し替える。公式 docs の同梱テキストには未記載＝
  docs だけを根拠にすると「不可能」と誤断する（実際に一次確認前の調査では不可能と結論が出た）。

なぜサイレント失敗にならないか（3 重）:
  1. ホスト側の型検査 — 返した値は当該ツールの outputSchema で safeParse され、合わなければ
     **元の出力を使い**、かつ `hook_error_during_execution` メッセージが表示される（fail-safe）。
  2. 本フックの例外は握り潰さず「無出力 exit 0」に倒す＝差し替えが起きない＝元の出力が残る。
  3. 切り詰めたときは stdout の中央に明示バナーを差し込む。**黙って消える経路が無い**。

切り詰めが有害なケースを先に決め、そこは触らない（下の SKIP 条件）。
何より、省略部分は捨てずに一時ファイルへ全文退避し、そのパスをバナーに書く＝**回復可能**にする。

撤去するときの 1 セット（名前が散らない設計）:
  本ファイル / `test_truncate_bash_output.py` / `.claude/settings.json` の配線 の 3 箇所だけ。
  退避先は OS の一時ディレクトリ配下（リポジトリ外）なので `.gitignore` へフック名が漏れない。
  他フック・skill・docstring から本フックを参照しないこと（参照した瞬間に撤去が 1 セットで済まなくなる）。
"""
import io
import json
import os
import re
import sys
import tempfile
import time

# ---- 較正値（すべて実測分布から決めた。根拠はコメント参照）----------------------------

# 閾値 4,000 字（≒1,000 トークン）。
# 実測分布（過去 119 セッション・Bash/PowerShell の tool_result 3,889 件。
# 再導出は `python tools/measure_bash_output_size.py`。閾値を動かすなら必ず回し直すこと）:
#   p50=356 / p90=2,141 / p95=3,239 / p97=4,344 / p99=7,882 / 最大=25,245 字
# 4,000 字は p97 付近＝**28 回に 1 回しか介入しない**（該当 3.6%）一方で、
# Bash 出力の実効寄与の 29.4% を捕まえ、うち 11.5%（全体コストの約 2.6%）を削る。
# 2,000 字まで下げると削減は倍（全体の約 5.4%）になるが介入率が 10.8%＝9 回に 1 回へ跳ね上がり、
# p90 規模の「普通の出力」まで触り始める。誤切り詰めの損失（調査のやり直し）は削減幅より高くつくと
# 判断し、介入頻度の低い側へ寄せた。
LIMIT = 4000

# 省略量がこれ未満なら切り詰めない（バナー自体が数百字あるので、削減が割に合わない）。
MIN_DROP = 800

# 既定は末尾寄り（60%）。ビルド／テスト／長時間コマンドの結論は末尾に出るため。
TAIL_RATIO_DEFAULT = 0.60
# grep -n 形式のような「行が独立した列挙」は末尾に結論が無いので先頭寄り（25%）にする。
TAIL_RATIO_LISTING = 0.25

# 退避ファイルの保持時間（これを超えたものは次回起動時に掃除する）。
RETENTION_SEC = 24 * 3600

# ---- 切り詰めてはいけない出力の判定 ------------------------------------------------

# 失敗の詳細（スタックトレース・アサーション差分・コンパイルエラー）は中略した瞬間に価値が消える。
# 「因果の鎖」なので中央が抜けると復元できない＝ここは削減より情報保全を優先し、まるごと素通しする。
# 判定は広めに取る（＝切り詰めない側へ倒す）。偽陽性のコストは「削れないだけ」だが、
# 偽陰性のコストは「調査が壊れる」で非対称。
FAILURE_MARKERS = re.compile(
    r"Traceback \(most recent call last\)"
    r"|^\s*File \"[^\"]+\", line \d+"          # Python スタックフレーム
    r"|^\s*at [\w.$]+\([\w$]+\.(?:java|kt):\d+\)"  # JVM スタックフレーム
    r"|^FAILURE: "                              # Gradle
    r"|BUILD FAILED"
    r"|^\s*\d+ tests? completed, \d+ failed"    # Gradle テストサマリ
    r"|^Exit code [1-9]"
    r"|AssertionError|ComparisonFailure|AssertionFailedError"
    r"|^e: "                                    # Kotlin コンパイルエラー
    r"|^[^\s:]+:\d+:\d+: error: ",              # 一般的なコンパイラのエラー行
    re.MULTILINE,
)

# `path:12:マッチ行` 形式（grep -n / rg -n）。行が互いに独立で、末尾に結論が無い出力の目印。
GREP_LINE = re.compile(r"^[^\s:]+:\d+:")


def _looks_like_listing(text):
    """行独立の列挙（grep -n 等）かを判定する。切り詰める向き（先頭寄り／末尾寄り）を決めるだけ。"""
    lines = [ln for ln in text.split("\n") if ln.strip()]
    if len(lines) < 10:
        return False
    hits = sum(1 for ln in lines if GREP_LINE.match(ln))
    return hits / len(lines) >= 0.5


def _stash(text):
    """省略部分を失わないよう全文を一時ファイルへ退避し、パスを返す（失敗したら None）。

    なぜリポジトリ外（OS の一時ディレクトリ）か: リポジトリ内に置くと `.gitignore` に
    本フック由来の名前が増え、撤去が 1 セットで済まなくなる（CLAUDE.md のドメイン知識節）。
    """
    try:
        d = os.path.join(tempfile.gettempdir(), "claude-bash-output")
        os.makedirs(d, exist_ok=True)
        now = time.time()
        # 溜め込み防止。掃除の失敗は本題ではないので個別に無視する。
        for name in os.listdir(d):
            p = os.path.join(d, name)
            try:
                if now - os.path.getmtime(p) > RETENTION_SEC:
                    os.remove(p)
            except OSError:
                pass
        path = os.path.join(d, f"out-{int(now)}-{os.getpid()}.txt")
        with open(path, "w", encoding="utf-8", errors="replace") as f:
            f.write(text)
        return path
    except OSError:
        return None


def _truncate(text):
    """両端を残して中央を省略した文字列を返す。切り詰め不要なら None。"""
    total = len(text)
    if total <= LIMIT:
        return None

    tail_ratio = TAIL_RATIO_LISTING if _looks_like_listing(text) else TAIL_RATIO_DEFAULT
    tail_n = int(LIMIT * tail_ratio)
    head_n = LIMIT - tail_n
    dropped = total - LIMIT
    if dropped < MIN_DROP:
        return None

    head, tail = text[:head_n], text[total - tail_n:]
    # 省略された行数を出す。「N 行あるうち一部しか見ていない」とモデルが認識できることが、
    # grep 結果を早合点で結論づけない唯一の歯止めになる。
    dropped_lines = text.count("\n") - head.count("\n") - tail.count("\n")
    stash = _stash(text)

    banner = (
        "\n\n"
        f"===== [truncate_bash_output] 中略: {dropped:,} 字 / 約 {max(dropped_lines, 0):,} 行 "
        f"（全 {total:,} 字）=====\n"
        + (f"  全文: {stash}\n" if stash
           else "  ⚠ 全文の退避に失敗したため、この中略部分は失われている。\n")
        + "  この出力は一部しか見えていない。件数や網羅性を判断するなら上記全文を Read するか、\n"
        + "  コマンドを絞って再実行すること。\n"
        "===== ここまで中略 =====\n\n"
    )
    return head + banner + tail


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    # stdin は生バイトで受けて UTF-8 明示デコードする（Windows の cp932 既定対策＝task_diary #26）。
    try:
        raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
        data = json.loads(raw)
    except (json.JSONDecodeError, ValueError, OSError):
        return  # fail-open: 差し替えないので元の出力がそのまま残る

    if data.get("tool_name") not in ("Bash", "PowerShell"):
        return

    resp = data.get("tool_response")
    if not isinstance(resp, dict):
        return  # 失敗時などは str で来る＝触らない

    stdout = resp.get("stdout")
    if not isinstance(stdout, str) or len(stdout) <= LIMIT:
        return

    # ---- SKIP 条件: 切り詰めが有害な出力は触らない ----
    if resp.get("stderr"):
        return  # 何か言っている＝失敗か警告の詳細。丸ごと残す
    if resp.get("interrupted"):
        return  # 中断時は途中経過そのものが手掛かり
    if resp.get("persistedOutputPath"):
        return  # ハーネスが既にオフロード済み。二重処理しない
    if FAILURE_MARKERS.search(stdout):
        return  # スタックトレース・テスト失敗・コンパイルエラー

    new_stdout = _truncate(stdout)
    if new_stdout is None:
        return

    # 元の dict を保ったまま stdout だけ差し替える。
    # なぜ全キーを引き継ぐか: 返り値はツールの outputSchema で検証され、合わなければ
    # 元の出力へ差し戻される（＝削減が黙って効かなくなる）。差分を最小にするのが最も安全。
    updated = dict(resp)
    updated["stdout"] = new_stdout

    print(json.dumps({
        "systemMessage": f"[truncate_bash_output] {len(stdout):,}字 → {len(new_stdout):,}字 に中略",
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "updatedToolOutput": updated,
        },
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # フックの不具合で Bash を壊さない。無出力 exit 0＝差し替え無し＝元の出力が届く。
        # ただし fail-open は故障が無症状になる（task_diary #44）ので、stderr に痕跡だけ残す
        # （exit 0 の stderr はブロック扱いにならず、デバッグ時に /hooks で見える）。
        import traceback
        traceback.print_exc(file=sys.stderr)
    sys.exit(0)
