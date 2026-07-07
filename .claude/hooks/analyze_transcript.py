#!/usr/bin/env python3
"""
実行捏造検知の CLI 事後アナライザ（アダプタ1・非ブロック）。

セッション・トランスクリプト JSONL を静的解析し、実行の捏造・未検証の完了主張
（対応する tool_use/tool_result ペアが無い実行報告）を列挙する。フックではない＝
一切ブロックしない。実データで精度を検証・調整するための第一デリバラブル。

使い方:
  python analyze_transcript.py <session.jsonl>                # 単一ファイル
  python analyze_transcript.py --slug -mnt-c-...-novel-reader-andloid   # slug 配下を全走査
  python analyze_transcript.py <f.jsonl> --format json        # JSON 出力
  python analyze_transcript.py <f.jsonl> --tier B --min-confidence 0.8  # 絞り込み

終了コード: 降格されていない（active な）finding が min-confidence 以上あれば 1、無ければ 0。
"""
import argparse
import glob
import io
import json
import os
import sys

import detect_fabricated_execution_core as core

# 非ASCIIパス・日本語出力の文字化けを防ぐ（既存フックの定型）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

PROJECTS_DIR = os.path.expanduser("~/.claude/projects")

# TTY のときだけ着色（パイプ/リダイレクト時はプレーン）
_COLOR = sys.stdout.isatty()
_C = {"red": "\033[31m", "yellow": "\033[33m", "dim": "\033[2m",
      "cyan": "\033[36m", "bold": "\033[1m", "reset": "\033[0m"}


def paint(s, color):
    if not _COLOR:
        return s
    return f"{_C.get(color, '')}{s}{_C['reset']}"


def resolve_targets(args) -> list:
    if args.path:
        return [args.path]
    if args.slug:
        d = os.path.join(PROJECTS_DIR, args.slug)
        files = sorted(glob.glob(os.path.join(d, "*.jsonl")))
        if not files:
            print(paint(f"[エラー] {d} に *.jsonl が見つかりません", "red"), file=sys.stderr)
        return files
    print(paint("[エラー] <path.jsonl> か --slug のどちらかを指定してください", "red"), file=sys.stderr)
    return []


def read_file(path) -> str:
    # トランスクリプトは日本語を含むため UTF-8 明示（task_diary #26 同趣旨）
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def finding_line(f) -> str:
    if f.suppressed_reason:
        head = paint(f"[降格 {f.rule} ({f.suppressed_reason})]", "dim")
    else:
        color = "red" if f.confidence >= 0.8 else "yellow"
        head = paint(f"[{f.tier} {f.rule} conf={f.confidence:.2f}]", color)
    parts = [head]
    if f.missing_token:
        parts.append(paint(f"missing={f.missing_token}", "cyan"))
    excerpt = f.claim_excerpt.replace("\n", " ⏎ ")
    parts.append(f'"{excerpt}"')
    return "  " + " ".join(parts)


def print_human(path, report, args) -> int:
    active = [f for f in report.findings
              if f.suppressed_reason is None and f.confidence >= args.min_confidence]
    shown_suppressed = [f for f in report.findings if f.suppressed_reason is not None]

    if not active and not (args.include_suppressed and shown_suppressed) and not args.verbose:
        return len(active)

    print(paint(f"── {os.path.basename(path)}", "bold")
          + paint(f"  (発話 {report.scanned} 件を検査)", "dim"))
    if active:
        for f in active:
            print(finding_line(f))
    else:
        print(paint("  active な捏造の疑いなし", "dim"))
    if args.include_suppressed:
        for f in shown_suppressed:
            print(finding_line(f))
    for b in report.blind_spots:
        print(paint(f"  ⚠ 盲点: {b}", "yellow"))
    return len(active)


def main() -> int:
    ap = argparse.ArgumentParser(description="実行捏造検知の事後アナライザ（非ブロック）")
    ap.add_argument("path", nargs="?", help="解析対象の <session.jsonl>")
    ap.add_argument("--slug", help="~/.claude/projects/<slug>/*.jsonl を全走査")
    ap.add_argument("--format", choices=["human", "json"], default="human")
    ap.add_argument("--tier", default="ABC", help="検査する Tier（例 'B' / 'AB' / 'ABC'。C=misread型）")
    ap.add_argument("--scope", choices=["all", "last_turn"], default="all")
    ap.add_argument("--sentinel-dir", default=None,
                    help="センチネル(.python_tests_passed 等)のあるディレクトリ（live 裏取り用）")
    ap.add_argument("--min-confidence", type=float, default=0.0)
    ap.add_argument("--include-suppressed", action="store_true", help="降格 finding も表示")
    ap.add_argument("--verbose", action="store_true", help="finding が無いファイルも表示")
    # なぜサイズ上限があるか: analyze() はファイル全文をメモリ展開し、証拠コーパスを
    # 連結・正規化（＝大きなコピー）する。長大トランスクリプト（数十MB級。特に多数の
    # サブエージェントを含むもの）を全文処理すると WSL/drvfs で CPU・メモリを爆食いする
    # 実害があったため、既定で上限超を「スキップ＋明示警告」する（サイレント切り捨て禁止）。
    # 大物を敢えて解析したいときは単一ファイル指定＋--max-mb を上げる。
    ap.add_argument("--max-mb", type=float, default=8.0,
                    help="この MB を超えるファイルはスキップ（既定8.0・0で無制限）")
    args = ap.parse_args()

    targets = resolve_targets(args)
    if not targets:
        return 2

    reports = []
    skipped_big = []
    total_active = 0
    for path in targets:
        try:
            size_mb = os.path.getsize(path) / 1e6
        except OSError:
            size_mb = 0.0
        if args.max_mb > 0 and size_mb > args.max_mb:
            skipped_big.append((path, size_mb))
            print(paint(f"[スキップ] {os.path.basename(path)}: "
                        f"{size_mb:.1f}MB > 上限{args.max_mb}MB（--max-mb で調整）", "yellow"),
                  file=sys.stderr)
            continue
        try:
            text = read_file(path)
        except OSError as e:
            print(paint(f"[スキップ] {path}: {e}", "yellow"), file=sys.stderr)
            continue
        report = core.analyze(text, transcript_path=path, scope=args.scope,
                              sentinel_dir=args.sentinel_dir, tiers=args.tier)
        reports.append((path, report))

    if args.format == "json":
        out = [dict(path=p, **r.to_dict()) for p, r in reports]
        print(json.dumps(out, ensure_ascii=False, indent=2))
        total_active = sum(
            1 for _, r in reports for f in r.findings
            if f.suppressed_reason is None and f.confidence >= args.min_confidence
        )
    else:
        for path, report in reports:
            total_active += print_human(path, report, args)
        summary = f"\n合計: active な捏造の疑い {total_active} 件（{len(reports)} ファイル走査）"
        if skipped_big:
            summary += f" / サイズ上限でスキップ {len(skipped_big)} 件"
        print(paint(summary, "bold"))

    # active finding があれば exit 1（CI 化できるように）
    return 1 if total_active > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
