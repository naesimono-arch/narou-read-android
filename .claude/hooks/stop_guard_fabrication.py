#!/usr/bin/env python3
"""
Stop フック: ターン終了時に、直近の assistant 発話の「実行捏造」を検知したらブロックして
自己修正を促す（アダプタ2・ライブゲート）。エンジンは detect_fabricated_execution_core。

設計（ADR 0006。Tier C と A2 昇格は 2026-07-07 の misread 型対応＝正解データ事象F）:
  - Stop フックは additionalContext を持てない（task_diary #28）ため「ブロック or 素通し」の二択。
  - ブロックは高精度シグナルのみ:
      Tier B unverified_test_claim（成功実行なし ∧ センチネル不在/古い ∧ 非降格・conf≥0.8）
      Tier A3 fabricated_harness_block（ハーネス/ツール構文の地の文化・非降格）
      Tier A2 fabricated_concrete_token（存在しない SHA の断言。証拠の result 層化・エコーバック
        除外・git 文脈語拡張で精度が上がり、全セッション走査で偽陽性ゼロを確認して昇格）
      Tier C 全4ルール（misread 型: ブロック済みコミットの完了報告／出力シグネチャ捏造／
        書き込み完了の捏造／ブランチ削除の捏造。同走査で偽陽性ゼロ・正解データFの5事象を検知）
      Tier D4 phantom_turn_marker（入力側捏造: assistant 自身の text ブロックにハーネス割込
        マーカー `[Request interrupted by user]` を自己生成＝幻のユーザーターン。正解データK。
        マーカーリテラルの特異性で conf 0.9・コーパス全走査で active は K のみ＝偽陽性ゼロ実測。
        **D1〜D3 は従来どおり非ブロック**＝段階導入の維持。判断は ADR 0006 増補3）。
  - A1 は構造レビュー向きで自己修正に不向きなのでブロックしない（CLI に委ねる）。
  - 偽陽性・例外・transcript 不在は必ず exit 0（ユーザー作業を妨げない）。

なぜ scope=current_turn か（旧 last_turn からの変更・事象L）: 最後の生ユーザープロンプト以降の
全 assistant 発話を対象にする。last_turn（最終発話のみ）だと、捏造報告の直後に AskUserQuestion 等で
ターンが継続すると検査窓から捏造発話が抜け落ちて素通りした（多ツールターン内捏造の穴）。証拠・成功
実行はセッション全域から集める。センチネル(.kotlin_tests_passed)は現行セッションの実行痕跡なので
裏取りに使える。
"""
import io
import json
import os
import sys

# 非ASCII出力の文字化け防止（既存フックの定型）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# cwd 非依存で同ディレクトリのエンジンを import する
HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))
if HOOKS_DIR not in sys.path:
    sys.path.insert(0, HOOKS_DIR)

try:
    import detect_fabricated_execution_core as core
except ImportError:
    # エンジンが無いブランチへ切り替えた等 → 妨げない
    sys.exit(0)


def main() -> int:
    # transcript は日本語を含むため UTF-8 明示デコード（task_diary #26）
    try:
        raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
        data = json.loads(raw)
    except (json.JSONDecodeError, EOFError, ValueError):
        return 0

    # 再発火ループ防止: 前回この Stop フックがブロックして再度 Stop に来た場合は素通し
    if data.get("stop_hook_active"):
        return 0

    tpath = data.get("transcript_path")
    if not tpath or not os.path.exists(tpath):
        return 0
    try:
        with open(tpath, encoding="utf-8", errors="replace") as f:
            text = f.read()
    except OSError:
        return 0

    # センチネルは .claude/ 直下（hooks の親）
    claude_dir = os.path.dirname(HOOKS_DIR)

    try:
        # scope=current_turn: 最後の生ユーザープロンプト以降の全 assistant 発話を検査する。
        # last_turn（最終発話のみ）だと、捏造報告の直後に AskUserQuestion 等でターンが継続すると
        # 検査窓から捏造発話が抜け落ちて素通りした（正解データ事象L＝多ツールターン内捏造の穴）。
        report = core.analyze(text, transcript_path=tpath, scope="current_turn",
                              sentinel_dir=claude_dir, tiers="ABCD")
    except Exception:
        # 解析中の想定外例外でユーザーを止めない（非妨害の原則）
        return 0

    # misread 型（Tier C）＝ペアは在るが報告が実結果と食い違うルール群（正解データ事象F）
    tier_c_rules = {"completion_after_blocked_commit", "fabricated_output_signature",
                    "unverified_write_claim", "unverified_branch_delete_claim"}
    blockers = [
        f for f in report.findings
        if f.suppressed_reason is None and (
            (f.rule == "unverified_test_claim" and f.confidence >= 0.8)
            or f.rule == "fabricated_harness_block"
            or (f.rule == "fabricated_concrete_token" and f.confidence >= 0.8)
            or (f.rule in tier_c_rules and f.confidence >= 0.8)
            # Tier D4: 幻の割込ターンの自己生成（正解データK）。D1〜D3 は非ブロックのまま
            # ＝マーカーリテラルの特異性で D4 だけを段階昇格する（ADR 0006 増補3）。
            or (f.rule == "phantom_turn_marker" and f.confidence >= 0.8)
        )
    ]
    if not blockers:
        return 0

    lines = ["[実行捏造の疑い] 直近の応答に、実ツール記録で裏付けられない・"
             "または実際のツール結果と食い違う実行報告があります:"]
    for f in blockers[:4]:
        lines.append(f"  ・{f.rule}: 「{f.claim_excerpt[:80]}」")
    lines.append("直近の tool_result を読み直し、実際の結果に基づいて報告を訂正してください。"
                 "未実行なら該当コマンドを実行し、その tool_result で確認してから完了報告してください。")
    reason = "\n".join(lines)

    # decision:block は Stop 停止を差し止め、reason をモデルに渡して続行（自己修正）させる
    print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
