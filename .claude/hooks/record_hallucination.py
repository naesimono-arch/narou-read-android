#!/usr/bin/env python3
"""
UserPromptSubmit フック: ユーザーが `/hallucination` と打った**その瞬間**のセッション JSONL を
機械的に保全し、正解データ台帳の未確定キューへ自動記載する。

なぜフックか（スキル内の Claude にやらせないか）:
  ハルシネーション直後のセッションでは Claude 自身が信用できない（台帳L＝検知器修正の完了報告を
  丸ごと捏造した実例）。証拠の確保（スナップショット＋台帳記載)はモデル推論を介さず
  ハーネス側で決定的に行い、Claude の役割は事後の分類・確定（/hallucination スキル）に限定する。

なぜ UserPromptSubmit か:
  ペイロードに prompt / transcript_path / session_id が揃って渡る＝現行セッションの特定が
  ノンス等の迂回なしに確定する。スナップショットは「プロンプト送信時点まで」の transcript
  ＝ユーザーが幻覚を目撃した瞬間の不変証拠になる。

設計:
  - `/hallucination` 以外のプロンプトは即 exit 0（無干渉）。
  - スナップショット先は ext4 の ~/.claude/hallucination-archive/（/mnt/c の EPERM 系を回避、
    かつ git 管理外＝巨大 JSONL をリポジトリに入れない）。
  - 台帳へは「未確定キュー」として追記のみ（分類・確度はスキル側で人間承認のうえ確定する。
    open(..., "a") の追記は drvfs でも安全＝sed -i の in-place パーミッション複製とは別物）。
  - fail-open（exit 0 固定）だが、キャプチャ失敗は stdout（UserPromptSubmit の stdout は
    コンテキスト注入される）で必ず可視化する（サイレント失敗クラスにしない＝ADR 0004 の教訓）。
"""
import json
import os
import re
import shutil
import sys
from datetime import datetime

HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
if HOOKS_DIR not in sys.path:
    sys.path.insert(0, HOOKS_DIR)

import hooks_common

TRIGGER_RE = re.compile(r"^\s*/hallucination\b(.*)", re.DOTALL)
ARCHIVE_DIR = os.path.expanduser("~/.claude/hallucination-archive")
QUEUE_HEADER = "## ⏳ 未確定キュー（/hallucination 自動キャプチャ・確定後にレター事象へ昇格して行を消す）"


def ledger_path() -> str:
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(HOOKS_DIR))
    return os.path.join(root, "docs", "reference", "hallucination-ground-truth.md")


def main() -> int:
    hooks_common.wrap_stdio()
    data = hooks_common.read_payload()
    if not data:
        return 0
    m = TRIGGER_RE.match(data.get("prompt") or "")
    if not m:
        return 0

    args = m.group(1).strip()
    tpath = data.get("transcript_path") or ""
    sid = data.get("session_id") or (os.path.basename(tpath)[:-6] if tpath.endswith(".jsonl") else "unknown")
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 1) スナップショット保全（最優先。台帳追記が失敗しても証拠は残す）
    snapshot = ""
    lines = -1
    err = []
    try:
        if tpath and os.path.exists(tpath):
            os.makedirs(ARCHIVE_DIR, exist_ok=True)
            snapshot = os.path.join(
                ARCHIVE_DIR, f"{sid}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.jsonl")
            shutil.copy2(tpath, snapshot)
            with open(snapshot, encoding="utf-8", errors="replace") as f:
                lines = sum(1 for _ in f)
        else:
            err.append(f"transcript_path が解決できない: {tpath!r}")
    except OSError as e:
        err.append(f"スナップショット失敗: {e}")
        snapshot = ""

    # 2) 台帳の未確定キューへ機械的追記（分類はしない＝スキル側の仕事）
    lpath = ledger_path()
    try:
        if os.path.exists(lpath):
            with open(lpath, encoding="utf-8", errors="replace") as f:
                has_queue = QUEUE_HEADER in f.read()
            with open(lpath, "a", encoding="utf-8") as f:
                if not has_queue:
                    f.write(f"\n---\n\n{QUEUE_HEADER}\n\n")
                f.write(f"- [ ] {ts} session=`{sid}` "
                        f"snapshot=`{snapshot or '取得失敗'}`（{lines}行時点） "
                        f"live=`{tpath}`"
                        + (f" ユーザーメモ: {args}" if args else "") + "\n")
        else:
            err.append(f"台帳が見つからない: {lpath}")
    except OSError as e:
        err.append(f"台帳追記失敗: {e}")

    # 3) 結果をコンテキストへ注入（成功でも失敗でも可視化する）
    if err:
        print("[/hallucination 自動キャプチャ・一部失敗] " + " / ".join(err)
              + (f"（スナップショットは取得済み: {snapshot}）" if snapshot else
                 " 手動で transcript の保全から実施すること。"))
    else:
        print(f"[/hallucination 自動キャプチャ完了] snapshot={snapshot}（{lines}行時点）を"
              f"台帳の未確定キューへ記載済み。以後の行番号確定はこのスナップショット基準で行うこと。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
