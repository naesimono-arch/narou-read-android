#!/usr/bin/env python3
r"""⚠️ 凍結中（2026-09-01 ユーザー裁定）＝**このフックは配線されていない**。

`.claude/settings.json` に登録が無いので、ここに在るだけで**一度も実行されない**
（テストだけは `python .claude/hooks/test_check_context_budget.py` で単体で回る）。

なぜ凍結したか: **ユーザー裁定**（201k 到達の通告を受けて「settings.json で恒久的に無効化」）。
  ⚠️ **裁定の理由は記録されていない**＝ここに推測を書かない。判っているのは
  **否定されたのが「自動通告」であって閾値の算出でも検知の実装でもない**ことだけで、
  175k の根拠（下の設計メモ）は `session-relay` skill の判断基準として今も生きている。
  凍結の記録＝`docs/backlog-frozen.md`「コンテキスト予算の Stop 通告フック」。

解凍条件＝**ユーザーが自動通告を再び求めたとき**。技術的な減点で凍結したのではないので
  Claude 側の判断だけでは戻さない。戻すなら閾値は
  `docs/knowledge/context-cost-optimization-levers-measured.md` を測り直してから決めること。

解凍手順: `.claude/settings.json` の `hooks.Stop` にある唯一のブロックの `hooks` 配列へ
  **次の要素を足すだけ**（コード側の変更は不要）。凍結前は `stop_guard_fabrication.py` の直後に置いていた。
  併せて `session-relay` skill の frontmatter description と「通告は来ない」節も戻すこと
  （凍結時に書き換えてある＝戻し忘れると skill が嘘を言う）。

          {
            "type": "command",
            "command": "python \"${CLAUDE_PROJECT_DIR}/.claude/hooks/check_context_budget.py\""
          }

==== 以下は凍結前の設計メモ（当時のまま） ====

Stop: コンテキスト長が閾値を超えたら、引き継いで新セッションへ移るよう通告する。

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

# ⚠️ 凍結中（2026-09-01 ユーザー裁定）。settings.json の配線は既に外してあるが、
# **配線を外しても、外す前から走っているセッションには反映されない**（起動時スナップショット＝
# memory `hook-implementation-facts` §3・2026-09-02 実測）。実際 09-02 のセッションで通告が再発した。
# 本体の変更は即時反映されるので、旧配線を掴んだセッションを黙らせる手はここしかない。
# 解凍するときは settings.json へ配線を戻したうえで、この定数を False にする。
FROZEN = True
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


def already_notified(transcript_path):
    """このセッションで既に通告したか。**user メッセージの本文として届いた分だけ**を数える。

    なぜ transcript の全文検索ではだめか（2026-08-26 に実際に踏んだ）:
      SENTINEL はこのファイルとテストに literal で載っている。つまりフックを Write / Read した
      だけで、その内容が tool_use / tool_result として transcript に現れる。全文 grep だと
      **フックを書いたセッションと、コードを読んだだけのセッションが以後永久に通告されない**。
      通告は decision:block の reason＝user 側の text として届くので、そこだけを見る。
    """
    try:
        with open(transcript_path, encoding="utf-8", errors="replace") as f:
            for line in f:
                if SENTINEL not in line:
                    continue
                try:
                    ev = json.loads(line)
                except (json.JSONDecodeError, ValueError):
                    continue
                if ev.get("type") != "user":
                    continue
                c = (ev.get("message") or {}).get("content")
                if isinstance(c, str):
                    if SENTINEL in c:
                        return True
                elif isinstance(c, list):
                    for b in c:
                        if (isinstance(b, dict) and b.get("type") == "text"
                                and SENTINEL in (b.get("text") or "")):
                            return True
    except OSError:
        return True      # 読めないなら通告しない側へ倒す（うるさくするより黙る）
    return False


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

    if already_notified(tpath):
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
    if FROZEN:
        sys.exit(0)
    try:
        sys.exit(main())
    except Exception as e:      # 計測系フックはユーザーを絶対に止めない（fail-open）
        sys.stderr.write(f"check_context_budget: {e}\n")
        sys.exit(0)
