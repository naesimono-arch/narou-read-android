#!/usr/bin/env python3
"""PostToolUse + SubagentStop: サブエージェントのツール使用数を計測する（委譲ターン計測）。

目的（2026-07-25 監督決定）:
  1. 中間通告: 子エージェントのツール使用が 30 回ごと（30/60/90…）に到達したら、その子自身の
     コンテキストへ additionalContext で1行通告し、スコープ再考（部分成果で監督へ戻る選択肢）を
     促す。ブロック・kill は絶対にしない＝閾値は上限規約ではなく再考トリガー（/orchestration §0）。
  2. 記録: 子の完走時（SubagentStop）に {ts, agent_type, tool_uses} を JSONL へ追記し、
     通告間隔（谷=30）の妥当性を実測分布で較正する材料にする。

設計根拠（雛形コピーでなく裏取りした点）:
  - サブエージェント判別は **agent_id の有無**（公式 hooks doc: subagent 内で発火した hook のみに
    付与され main/subagent の判別キーと明記。agent_type は `--agent` セッションでも付くため
    判別には使わない）。agent_id 無し＝メインセッション＝計測しない。
  - 通告は PostToolUse の hookSpecificOutput.additionalContext（task_diary #28 実測＋公式 doc。
    「ツール結果の隣」に注入される＝子のツール呼出なら子の文脈に届く。plain stdout は不達）。
  - 状態・記録の置き場は auto-memory と同じプロジェクト領域（ブランチ不変・リポジトリ外）。
    /tmp はフック実行のサンドボックスで主セッションから見えない（memory hook-tmp-writes-sandboxed）
    ため使わない。~ 配下への hook 書込みは record_hallucination.py（~/.claude/hallucination-archive）
    で実績あり。
  - fail-open を明示設計: あらゆる例外で exit 0＝ツールフローを絶対に妨げない。ただし黙って
    死なない（task_diary #44 の無症状故障対策）＝例外時は stderr へ1行（exit 0 の stderr は
    デバッグログ行き。exit 2 は「ブロック」を意味するため計測フックでは絶対に使わない）。
"""
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

from hooks_common import wrap_stdio

# 通告間隔（初回30・以後30ごと）。較正は delegation-stats.jsonl の実測分布で見直す。
# 変えるときは本 docstring の「30 回ごと」も更新する（stale-check 項目16が乖離を検知する）。
NOTIFY_INTERVAL = 30
# 完走前に死んだ子の状態ファイルを掃除する猶予（子の走行が日単位に及ぶことはない）。
STATE_TTL_DAYS = 7


def base_dir():
    """状態・記録の置き場。

    なぜ CLAUDE_PROJECT_DIR でなく固定 slug か: 計測はブランチ・worktree 不変の較正データで、
    全 worktree 分を1箇所に集計したい（worktree ごとの project slug に分散すると分布にならない）。
    auto-memory と同じ canonical スラッグ配下に置く。テストは DELEGATION_METER_DIR で差し替える。
    """
    env = os.environ.get("DELEGATION_METER_DIR")
    if env:
        return Path(env)
    return Path.home() / ".claude/projects/-mnt-c-Users-qingj-Desktop-project-novel-reader-andloid"


def _read_payload_utf8():
    """stdin を生バイト→UTF-8 明示デコードで読む（task_diary #26 の入力側対策）。

    なぜ hooks_common.read_payload を使わないか: あちらは json.load(sys.stdin)＝Windows では
    cp932 デコードになり、日本語を含む agent_type が JSONL 記録へ化けて残る／
    UnicodeDecodeError で fail-open 空振りする。本フックは値を「記録」するため入力側から潰す。
    """
    try:
        raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
        data = json.loads(raw)
        return data if isinstance(data, dict) else None
    except (ValueError, OSError):
        return None


def _sanitize(value):
    """session_id/agent_id をファイル名に安全な形へ（形式が非公開のため防御的に丸める）。"""
    return re.sub(r"[^A-Za-z0-9._-]", "_", str(value or ""))[:80]


def _state_path(payload, agent_id):
    return base_dir() / "delegation-state" / (
        f"{_sanitize(payload.get('session_id'))}--{_sanitize(agent_id)}.json"
    )


def _load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _save_json(path, data):
    """一時ファイル→os.replace の原子的置換で書く。

    なぜ: 並列ツール呼出では同一子の PostToolUse フックが同時に走りうる。カウントの取りこぼし
    （read-modify-write の競合で1回分欠落）は較正用途では許容するが、書きかけ JSON での
    状態破損だけは防ぐ（破損すると以後のカウントが0リセットされ分布が大きく歪む）。
    """
    tmp = path.with_name(path.name + f".tmp{os.getpid()}")
    tmp.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, path)


def handle_tool_use(payload, agent_id):
    """PostToolUse: カウンタを進め、NOTIFY_INTERVAL の倍数ちょうどでのみ通告を注入する。"""
    path = _state_path(payload, agent_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    state = _load_json(path) or {}
    count = int(state.get("tool_uses", 0)) + 1
    state.update({
        "tool_uses": count,
        # agent_type は毎回来る想定だが、欠けた回があっても既知値を保持する（記録の欠損防止）
        "agent_type": payload.get("agent_type") or state.get("agent_type") or "",
        "session_id": payload.get("session_id", ""),
        "agent_id": agent_id,
    })
    _save_json(path, state)

    if count % NOTIFY_INTERVAL == 0:
        # 警告のみ・ブロックしない。decision/exit 2 は使わない（上限でなく再考トリガー）。
        msg = (
            f"⏱ ツール使用が {count} 回に到達。スコープが想定を超えているなら、"
            "一旦部分成果を報告様式で返して監督の再スコープを仰ぐことを検討"
            "（健全な長走行なら続行してよい）"
        )
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": msg,
        }}, ensure_ascii=False))


def handle_stop(payload, agent_id):
    """SubagentStop: 完走記録を JSONL へ1行追記し、状態ファイルを片付ける。"""
    path = _state_path(payload, agent_id)
    state = _load_json(path) or {}
    record = {
        "ts": datetime.now().astimezone().isoformat(timespec="seconds"),
        # 状態側を優先: PostToolUse 30+回分の多数決的に安定した値。無ければ Stop payload から。
        "agent_type": state.get("agent_type") or payload.get("agent_type") or "",
        # 状態ファイル無し＝ツールを一度も使わず完走した子（真に0回。TTL内に状態が消える経路は無い）
        "tool_uses": int(state.get("tool_uses", 0)),
        "session_id": payload.get("session_id", ""),
        "agent_id": agent_id,
    }
    stats = base_dir() / "delegation-stats.jsonl"
    stats.parent.mkdir(parents=True, exist_ok=True)
    # O_APPEND の1行 write は実用上原子的（4KB未満）＝並列 Stop でも行が混ざらない
    with open(stats, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")
    try:
        path.unlink()
    except OSError:
        pass
    _cleanup_stale_states(path.parent)


def _cleanup_stale_states(state_dir):
    """TTL 超の状態ファイルを掃除する（完走せず殺された子の残骸が無限に溜まるのを防ぐ）。

    なぜ SubagentStop 側だけで走らせるか: PostToolUse は全ツール呼出で発火する高頻度経路で、
    毎回のディレクトリ走査はレイテンシ税になる。Stop は子1体につき1回＝掃除には十分な頻度。
    """
    cutoff = time.time() - STATE_TTL_DAYS * 86400
    try:
        for p in state_dir.glob("*.json"):
            try:
                if p.stat().st_mtime < cutoff:
                    p.unlink()
            except OSError:
                continue  # 個別の消し損ねは次回に回す（掃除で本務を落とさない）
    except OSError:
        pass


def main():
    wrap_stdio()
    payload = _read_payload_utf8()
    if payload is None:
        return  # fail-open: 入力が壊れていても何もしない
    agent_id = payload.get("agent_id")
    if not agent_id:
        return  # メインセッションは計測しない（agent_id の有無が公式の判別キー）
    event = payload.get("hook_event_name", "")
    if event == "PostToolUse":
        handle_tool_use(payload, agent_id)
    elif event == "SubagentStop":
        handle_stop(payload, agent_id)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # fail-open: 計測は補助機能＝どんな故障でもツールフローを妨げない
        # 黙って死なない工夫: 1行だけ stderr へ（exit 0 の stderr はデバッグログで観測可能）
        print(f"count_delegation_turns fail-open: {type(e).__name__}: {e}", file=sys.stderr)
    sys.exit(0)
