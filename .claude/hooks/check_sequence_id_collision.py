#!/usr/bin/env python3
"""
PreToolUse hook: 連番資源（task_diary #N・ADR 番号）の新規採番が、全レーン
（canonical＋~/wt/* worktree）の既存番号と衝突していないかを検査し、衝突時のみ
additionalContext で警告する（ブロックしない）。
matcher: Edit|Write|MultiEdit

なぜ作ったか:
  並列 worktree レーンが同じ連番を別内容で採番する事故が反復した（task_diary の
  二重採番は #28/#30/#42/#44/#39 の計5件を 2026-07-07〜08 に解消、ADR も 0001・0007 で
  二度発生）。Room version には /db-migration の「全 worktree grep」予防が確立済みなのに、
  同じ衝突クラスの task_diary #N・ADR 番号には機械網が無い非対称を塞ぐ。
  第一防衛は CLAUDE.md task_diary ルールの「採番前の全レーン grep」＝本フックは
  見落とし時のセーフティネット。

なぜブロック（deny）せず警告か:
  既存エントリの移動・張り替えや見出しの引用でも「old_string に無い見出しが
  new_string に現れる」形は正当に起こり得て、deny は誤爆が痛い。採番ミスは可逆
  （コミット前に直すだけ）なので情報提供で足りる。PreToolUse の
  hookSpecificOutput.additionalContext は task_diary #28 追補で有効と実測済み
  （「ブロックせずに情報だけモデルへ渡す」唯一の手段）。

fail-open（常に exit 0・例外を握る）:
  採番ガードの故障が編集を妨げてはならない。無症状化の代償（task_diary #44）は
  test_check_sequence_id_collision.py の陽性コントロールで担保する。
"""
import glob
import json
import os
import re
import sys

# stdin/stdout ボイラープレートは hooks_common の単一定義を共有（ADR 0008（旧0007））。
from hooks_common import read_payload, wrap_stdio

wrap_stdio()

# task_diary の固定ID見出し（`#### 39. タイトル`）。行頭アンカーで本文中の引用を除外する。
HEADING_RE = re.compile(r"(?:^|\n)####\s+(\d+)\.")
# 移設マッピング表の旧ID（`| #30（…` / `| §20 |`）。旧IDは再利用禁止のため使用済み扱いにする。
LEGACY_ID_RE = re.compile(r"(?:^|\n)\|\s*[#§](\d+)")
ADR_FILENAME_RE = re.compile(r"^(\d{4})-[^/\\]*\.md$")


def repo_root():
    # <root>/.claude/hooks/<this>.py → 3階層上が root。
    # dirname 回数の取り違えで導入以来一度も動かなかった前例あり（task_diary #44）＝テストで固定。
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def lanes():
    """canonical＋全 worktree のルート一覧。~/wt が無い環境（Windows 等）では canonical のみ。"""
    roots = [repo_root()]
    roots += sorted(glob.glob(os.path.join(os.path.expanduser("~"), "wt", "*")))
    return [r for r in roots if os.path.isdir(r)]


def diary_ids(text):
    ids = set(int(m) for m in HEADING_RE.findall(text))
    ids |= set(int(m) for m in LEGACY_ID_RE.findall(text))
    return ids


def collect_diary_registry():
    """レーンの task_diary.md → 使用済み番号集合。読めないレーンは黙って飛ばす（fail-open）。"""
    registry = {}
    for lane in lanes():
        path = os.path.join(lane, "task_diary.md")
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                ids = diary_ids(f.read())
        except OSError:
            continue
        if ids:
            registry[path] = ids
    return registry


def collect_adr_registry():
    """番号 → 全レーンの該当 ADR ファイルパス一覧。"""
    registry = {}
    for lane in lanes():
        d = os.path.join(lane, "docs", "decisions")
        try:
            names = os.listdir(d)
        except OSError:
            continue
        for name in names:
            m = ADR_FILENAME_RE.match(name)
            if m:
                registry.setdefault(int(m.group(1)), []).append(os.path.join(d, name))
    return registry


def added_diary_ids(tool_name, tool_input, target_path):
    """この編集で「新規に増える」見出し番号（new にあって old に無いもの）。"""
    if tool_name == "Write":
        new_ids = diary_ids(tool_input.get("content", "") or "")
        try:
            with open(target_path, encoding="utf-8", errors="replace") as f:
                old_ids = diary_ids(f.read())
        except OSError:
            old_ids = set()
        return new_ids - old_ids
    edits = tool_input.get("edits") if tool_name == "MultiEdit" else [tool_input]
    added = set()
    for e in edits or []:
        added |= diary_ids(e.get("new_string", "") or "") - diary_ids(e.get("old_string", "") or "")
    return added


def build_warnings(tool_name, tool_input):
    path = tool_input.get("file_path", "") or ""
    basename = os.path.basename(path)
    warnings = []

    if basename == "task_diary.md":
        added = added_diary_ids(tool_name, tool_input, path)
        if added:
            registry = collect_diary_registry()
            all_ids = set().union(*registry.values()) if registry else set()
            for n in sorted(added):
                hits = sorted(p for p, ids in registry.items() if n in ids)
                if hits:
                    warnings.append(f"task_diary の見出し番号 #{n} は使用済み: {', '.join(hits)}")
            if warnings and all_ids:
                warnings.append(
                    f"全レーンの最大は #{max(all_ids)} ＝ 新規採番は #{max(all_ids) + 1} を使うこと"
                )
        return warnings

    m = ADR_FILENAME_RE.match(basename)
    parts = os.path.normpath(path).replace("\\", "/").split("/")
    # ADR は「新規ファイルの Write」だけが採番＝既存ファイルの上書き・Edit（増補）は対象外。
    if (
        tool_name == "Write"
        and m
        and len(parts) >= 3
        and parts[-2] == "decisions"
        and parts[-3] == "docs"
        and not os.path.exists(path)
    ):
        n = int(m.group(1))
        registry = collect_adr_registry()
        hits = registry.get(n, [])
        if hits:
            mx = max(registry)
            warnings.append(
                f"ADR 番号 {n:04d} は使用済み: {', '.join(sorted(hits))} "
                f"／ 全レーンの最大は {mx:04d} ＝ 新規は {mx + 1:04d} を使うこと"
            )
    return warnings


def main():
    data = read_payload()
    if data is None:
        return
    tool_name = data.get("tool_name", "")
    if tool_name not in ("Edit", "Write", "MultiEdit"):
        return
    warnings = build_warnings(tool_name, data.get("tool_input", {}) or {})
    if warnings:
        msg = (
            "[連番ID衝突の疑い] "
            + " ／ ".join(warnings)
            + "（採番前の全レーン確認は CLAUDE.md task_diary ルール。"
            "既存エントリの移動・張り替えによる誤検知なら無視してよい）"
        )
        print(
            json.dumps(
                {"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": msg}},
                ensure_ascii=False,
            )
        )


try:
    main()
except Exception:
    # fail-open: 採番ガードの故障が編集を妨げてはならない（無症状化はテストの陽性コントロールで担保）
    pass
sys.exit(0)
