#!/usr/bin/env python3
"""agy 能力検査の採点機（変異テスト方式）。

表面的な網羅率ではなく「本番コードに仕込んだバグを、生成されたテストが赤で捕まえるか」で数える。
採点基準はタスク投入の前にここで固定してある＝仕様を書いた本人の主観が入る余地を無くすため
（ADR 0031 決定2-a を3回踏んだことへの構造的な回答）。

使い方:
    python3 score.py --worktree ~/wt/agy-probe-L0 --label agy-L0
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

TARGET = "android/app/src/main/java/com/novelreader/domain/ShelfItems.kt"
TEST_DIR = "android/app/src/test/java/com/novelreader/domain"

# 変異体＝「この行をこう書き換えると、正しいテストなら赤になるはず」。
# id, 説明, 対象ファイル, 元の文字列, 変異後の文字列
MUTANTS = [
    (
        "M1-recency-boundary",
        "recencyKeyOf の未読境界を壊す（lastReadAt==0 も触った本扱いへ）",
        TARGET,
        "if (lastReadAt > 0L) RecencyKey(tier = 0, value = lastReadAt)",
        "if (lastReadAt >= 0L) RecencyKey(tier = 0, value = lastReadAt)",
    ),
    (
        "M2-recency-tier-swap",
        "recencyKeyOf の層を反転する（ADR 0016 改訂前の並びへ逆走）",
        TARGET,
        "if (lastReadAt > 0L) RecencyKey(tier = 0, value = lastReadAt)\n    else RecencyKey(tier = 1, value = addedAt)",
        "if (lastReadAt > 0L) RecencyKey(tier = 1, value = lastReadAt)\n    else RecencyKey(tier = 0, value = addedAt)",
    ),
    (
        "M3-recency-value",
        "recencyKeyOf の未読 value を addedAt から lastReadAt へ差し替える",
        TARGET,
        "else RecencyKey(tier = 1, value = addedAt)",
        "else RecencyKey(tier = 1, value = lastReadAt)",
    ),
    (
        "M4-web-tier-privilege",
        "webRecencyKeyOf に tier 特権を戻す（2026-07-26 実機報告の実バグを復活）",
        TARGET,
        "RecencyKey(tier = 0, value = if (lastReadAt > 0L) lastReadAt else addedAt)",
        "RecencyKey(tier = if (lastReadAt > 0L) 0 else 1, value = if (lastReadAt > 0L) lastReadAt else addedAt)",
    ),
    (
        "M5-web-value",
        "webRecencyKeyOf の value を常に addedAt にする",
        TARGET,
        "RecencyKey(tier = 0, value = if (lastReadAt > 0L) lastReadAt else addedAt)",
        "RecencyKey(tier = 0, value = addedAt)",
    ),
    (
        "M6-ncode-normalize",
        "importedNcodeKeys の保存キー正規化（trim+大文字）を外す",
        TARGET,
        "books.mapNotNull { b -> b.ncode?.let { Ncode(it).storageKey } }.toSet()",
        "books.mapNotNull { b -> b.ncode }.toSet()",
    ),
    (
        "M7-compare-tier-ignored",
        "RecencyKey.compareTo で tier 優先を捨て value だけで比べる",
        TARGET,
        "val t = tier.compareTo(other.tier)\n        return if (t != 0) t else value.compareTo(other.value)",
        "return value.compareTo(other.value)",
    ),
]


def run(cmd: str, cwd: Path, timeout: int = 1800) -> tuple[int, str]:
    """bash -ic で回す＝Gradle は bashrc の gw 関数（ラッパー jar 直起動）に依存するため。"""
    p = subprocess.run(
        ["bash", "-ic", cmd],
        cwd=cwd,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return p.returncode, (p.stdout + p.stderr)


def generated_tests(wt: Path) -> list[Path]:
    """この試行で生えた／書き換えられたテストファイル。

    新規ファイルだけを見ると取りこぼす——L0 の実測で、agy は新規ファイルを作らず
    既存の `ShelfItemsTest.kt` へ追記した（既存テストの置き場を自力で見つけた）。
    """
    _, out = run("git status --porcelain --untracked-files=all", wt)
    found = []
    for line in out.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip().strip('"')
        if path.endswith(".kt") and "src/test" in path:
            found.append(wt / path)
    return found


def test_classes(files: list[Path]) -> list[str]:
    names = []
    for f in files:
        if f.exists():
            m = re.search(r"^\s*class\s+([A-Za-z0-9_]+)", f.read_text(encoding="utf-8"), re.M)
            if m:
                names.append(m.group(1))
    return names


def gate(wt: Path, filt: list[str] | None) -> tuple[bool, str]:
    cmd = "cd android && gw testDebugUnitTest"
    if filt:
        cmd += "".join(f" --tests '*{c}'" for c in filt)
    code, out = run(cmd, wt)
    return code == 0, out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--worktree", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--brief", help="使ったブリーフのパス（文字数をコストとして記録する）")
    ap.add_argument(
        "--classes",
        help="テストクラス名をカンマ区切りで明示。ベースライン測定用＝生成物を外した状態でも"
        "既存テストだけで変異を回せるようにする（既存テストが対象関数を間接的に呼ぶため、"
        "これを測らないと agy の寄与を分離できない）",
    )
    args = ap.parse_args()

    wt = Path(args.worktree).expanduser()
    result: dict = {"label": args.label, "worktree": str(wt)}

    if args.brief:
        result["brief_chars"] = len(Path(args.brief).read_text(encoding="utf-8"))

    # --- 副作用の検査: src/main を触っていないか / 既存テストを改変していないか ---
    _, diff = run("git diff --stat", wt)
    result["touched_tracked_files"] = [
        l.split("|")[0].strip() for l in diff.splitlines() if "|" in l
    ]
    result["touched_main"] = any(
        "src/main" in f for f in result["touched_tracked_files"]
    )
    result["modified_existing_tests"] = [
        f for f in result["touched_tracked_files"] if "src/test" in f
    ]

    # --- 生成物 ---
    files = generated_tests(wt)
    result["generated_files"] = [str(f.relative_to(wt)) for f in files]
    classes = [c.strip() for c in args.classes.split(",")] if args.classes else test_classes(files)
    result["test_classes"] = classes

    body = "\n".join(f.read_text(encoding="utf-8") for f in files if f.exists())
    result["functions_mentioned"] = [
        fn
        for fn in ("recencyKeyOf", "webRecencyKeyOf", "importedNcodeKeys")
        if fn in body
    ]

    if not classes:
        result["green"] = False
        result["note"] = "生成テストが見つからない＝採点不能"
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1

    # --- ゲート1: そもそも緑か ---
    green, _ = gate(wt, None)
    result["green"] = green

    # --- ゲート2の前提: 変異を当てる前に、対象クラスが「変異なしで緑」であること ---
    # これを確かめずに変異を当てると、元から赤いテストが1件あるだけで Gradle は毎回 FAILED を返し、
    # 全変異を「殺した」と誤読する（2026-08-11 の agy L0 実測で実際に 7/7 の偽陽性を出した）。
    target_green, _ = gate(wt, classes)
    result["target_class_green"] = target_green
    if not target_green:
        result["mutation_score"] = "測定不能"
        result["note"] = (
            "変異なしで対象クラスが既に赤い＝全変異が偽陽性になるため測定を中止した。"
            "赤いテストを取り除いてから測り直すこと。"
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1

    # --- ゲート2: 変異を殺せるか ---
    target = wt / TARGET
    original = target.read_text(encoding="utf-8")
    killed, survived, inapplicable = [], [], []

    for mid, desc, _rel, before, after in MUTANTS:
        if before not in original:
            inapplicable.append({"id": mid, "why": "変異の当て先が本番コードに無い"})
            continue
        target.write_text(original.replace(before, after, 1), encoding="utf-8")
        try:
            passed, _ = gate(wt, classes)
            (survived if passed else killed).append({"id": mid, "desc": desc})
        finally:
            target.write_text(original, encoding="utf-8")

    result["mutants_killed"] = killed
    result["mutants_survived"] = survived
    result["mutants_inapplicable"] = inapplicable
    total = len(killed) + len(survived)
    result["mutation_score"] = f"{len(killed)}/{total}" if total else "0/0"

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
