#!/usr/bin/env python3
"""Stop: コンテキスト長が閾値を超えたら、引き継いで新セッションへ移るよう通告する。

なぜ「検知だけ」で実行までしないか:
  引き継ぎ文書の作成は「何が終わって何が途中か・次の一手は何か」の要約＝**判断**であり、
  フック（コード）には書けない。中身の無い引き継ぎで新セッションを立てても意味がない。
  ADR 0031 の線引き（事実の照合＝機械が強い／判断の代行＝機械が弱い）に従い、
  フックは「閾値を超えた」という**事実**だけを出し、実行は Claude に委ねる。

閾値 175k の根拠（docs/knowledge/context-cost-optimization-levers-measured.md）:
  累積 cache_read ≈ n·F + (n²/2)·avg_inc で、1ターンの限界費用は**その時点のコンテキスト長そのもの**。
  175k は実測で 100 ターン刻みに相当し、総量を 54%（-46pt）へ落とす水準。
  ⚠️ 1M 枠では 17% で「まだ空いている」ように見えるが、**枠の余裕と費用は別**。

出力の約束（既存 Stop フックと同じ）: 素通し＝空 stdout ／ 通告＝stdout JSON・全経路 exit 0。

なぜ再通告しないか:
  transcript にセンチネルが残っているかで判定する（状態ファイルを持たない）。
  フックのファイル書込は /tmp がサンドボックス化される（memory hook-implementation-facts §4）ため、
  状態を外に置くと環境差で黙って壊れる。transcript は必ず渡ってくるので、そこだけを見る。
"""
import io
import json
import os
import subprocess
import sys

# 通告文は日本語＝Windows の既定コードページで出すと化ける（task_diary #26 の出力側対策）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 通告済みマーカー。transcript に残るので、これを見て 1 セッション 1 回に絞る。
SENTINEL = "CTXBUDGET_NOTICE_V1"
THRESHOLD = 175_000


def latest_context_len(transcript_path):
    """transcript の最後の assistant ターンのコンテキスト長を返す（取れなければ None）。

    ctx = input + cache_read + cache_creation。statusLine の 🧠 が出しているのと同じ量で、
    次の 1 ターンで実際に cache_read として払う額＝限界費用そのもの。
    """
    last = None
    try:
        with open(transcript_path, encoding="utf-8", errors="replace") as f:
            for line in f:
                if '"usage"' not in line:
                    continue
                try:
                    ev = json.loads(line)
                except (json.JSONDecodeError, ValueError):
                    continue
                if ev.get("type") != "assistant":
                    continue
                u = (ev.get("message") or {}).get("usage") or {}
                c = ((u.get("input_tokens") or 0)
                     + (u.get("cache_read_input_tokens") or 0)
                     + (u.get("cache_creation_input_tokens") or 0))
                if c > 0:
                    last = c
    except OSError:
        return None
    return last


def dirty_worktree(cwd):
    """未コミットの変更があるか。判定できなければ False（＝通告文で触れない）。"""
    try:
        r = subprocess.run(["git", "status", "--porcelain"], cwd=cwd,
                           capture_output=True, text=True, timeout=5)
    except (OSError, subprocess.SubprocessError):
        return False
    return r.returncode == 0 and bool(r.stdout.strip())


def main():
    try:
        raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
        data = json.loads(raw)
    except (json.JSONDecodeError, EOFError, ValueError):
        return 0

    # 前回この Stop フックがブロックして再度来た場合は素通し（再発火ループ防止）
    if data.get("stop_hook_active"):
        return 0

    tpath = data.get("transcript_path")
    if not tpath or not os.path.exists(tpath):
        return 0

    ctx = latest_context_len(tpath)
    if ctx is None or ctx < THRESHOLD:
        return 0

    # 既に通告済みなら黙る。transcript 全文にセンチネルが載っているかだけで判る。
    try:
        with open(tpath, encoding="utf-8", errors="replace") as f:
            if SENTINEL in f.read():
                return 0
    except OSError:
        return 0

    cwd = data.get("cwd") or os.getcwd()
    lines = [
        f"[{SENTINEL}] コンテキストが {ctx/1000:.0f}k に達した"
        f"（閾値 {THRESHOLD/1000:.0f}k）。ここから先は 1 ターンごとに {ctx/1000:.0f}k を払い続ける。",
        "",
        "**/session-relay を実行して引き継ぎ、新しいセッションへ移ること。**",
        "",
        "⚠️ この通告は 1 セッションに 1 回だけ出る。無視して続行してもよいが、"
        "その場合コストは 2 次で伸び続ける（同じ仕事が 3 倍高くなる領域に入っている）。",
    ]
    if dirty_worktree(cwd):
        lines.insert(2, "⚠️ 未コミットの変更がある。**移行の前にコミットを済ませること**"
                        "（引き継ぎ先は別プロセスなので、作業ツリーの状態は引き継がれない）。")
    # decision:block は Stop 停止を差し止め、reason をモデルへ渡して続行させる。
    # 既存 Stop フック（stop_guard_fabrication.py）と同じく**全経路 exit 0**で、
    # 素通しは空 stdout・通告は stdout JSON で表現する（exit 2 はエラーと紛れるため使わない）。
    print(json.dumps({"decision": "block", "reason": "\n".join(lines)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:      # 計測系フックはユーザーを絶対に止めない（fail-open）
        sys.stderr.write(f"check_context_budget: {e}\n")
        sys.exit(0)
