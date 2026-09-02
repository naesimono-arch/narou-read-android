#!/usr/bin/env python3
"""員数宣言（roster）と実体を突き合わせる番人。

なぜ「本数を宣言しておく」必要があるか:
  検知の表は **1 行消せば 1 本の検知が消え、消えたことを誰も見ていない** のが唯一の失敗様式で、
  終了コードにも出力にも現れない（消えた番人は何も言わずに黙るだけ）。
  当リポジトリには実例が 2 件ある——センチネル照合が 13 日間 dead だった件と、
  台帳のサイズ番人が個別上限を 1 本も実装しないまま緑を返していた件。
  `len(実体)` を期待値にすると「実装が自分自身を検査する」自己参照になり、行が消えれば
  期待値も一緒に減って**必ず一致する**。∴ 期待値は実体とは別のファイル（tools/roster.tsv）に置く。

照合は 6 点（1〜5 は nuru の設計、6 は当リポジトリで追加した双方向カバレッジ）:
  1. 宣言 ↔ 実体の顔ぶれ（settings.json の配線 / check_machine の CHECKS）
  2. 宣言 ↔ **引数まで**（イベント・matcher・スクリプト名の三つ組。args は文言でなく実行対象で、
     配線行を消さずに指す先だけ差し替えれば検知は死ぬ）
  3. 宣言の員数 ↔ 実数（数え方の定義は roster.tsv 冒頭に書いてある）
  4. エントリポイントが実在ファイル／実在関数か
  5. 表が**静的**であること（実行時に増減しない＝環境で顔ぶれが変わらない）
  6. killtest の双方向カバレッジ（宣言された番人には必ず変異が在り、変異には必ず宣言が在る）

なぜ実体を import せず AST で読むか:
  check_machine.py は import すると走り出す構造ではないが、フック群は module 直下で stdin を読む。
  「読むだけで走る」ものを員数照合のために起動するのは筋が悪く、5（静的であること）の判定も
  import では原理的にできない（走った後の姿しか見えない）。∴ 一貫して構文木から読む。

終了コード: ズレが 1 件でもあれば 1。
"""
import ast
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROSTER = ROOT / "tools/roster.tsv"
SETTINGS = ROOT / ".claude/settings.json"
CHECK_MACHINE = ROOT / ".claude/skills/stale-check/check_machine.py"
KILLTEST = ROOT / "tools/killtest.py"
HOOKS_DIR = ROOT / ".claude/hooks"

problems = []


def bad(msg):
    problems.append(msg)


def load_roster():
    counts, hooks, checks = {}, [], []
    for raw in ROSTER.read_text(encoding="utf-8").split("\n"):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        f = raw.split("\t")
        if f[0] == "count":
            counts[f[1]] = int(f[2])
        elif f[0] == "hook":
            hooks.append((f[1], f[2], f[3]))
        elif f[0] == "check":
            checks.append((f[1], f[2]))
        else:
            bad(f"roster.tsv に未知の kind: {f[0]!r}")
    return counts, hooks, checks


def actual_hooks():
    """settings.json の配線を (イベント, matcher, スクリプト名) の三つ組で読む。"""
    cfg = json.loads(SETTINGS.read_text(encoding="utf-8"))
    out = []
    for event, groups in cfg.get("hooks", {}).items():
        for g in groups:
            for h in g.get("hooks", []):
                cmd = h.get("command", "")
                # 5: コマンドが「${CLAUDE_PROJECT_DIR}/.claude/hooks/<name>.py」の素の形であること。
                # 別スクリプトで包む・シェル展開を挟む形は、配線を残したまま中身を差し替えられる
                # ＝roster が名前だけ見て緑を返す穴になる（実測知見: スクリプト包みは両ガードを素通り）。
                if ".claude/hooks/" not in cmd or not cmd.rstrip('"').endswith(".py"):
                    bad(f"[5-静的] {event} の配線が素のフック起動でない: {cmd[:90]}")
                out.append((event, g.get("matcher", "") or "-", cmd.split("/")[-1].rstrip('"')))
    return out


def _module_list(path, name):
    """module 直下の `<name> = [...]` を、静的なリテラル代入としてのみ受理する。"""
    tree = ast.parse(path.read_text(encoding="utf-8"))
    found = None
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(
                isinstance(t, ast.Name) and t.id == name for t in node.targets):
            if found is not None:
                bad(f"[5-静的] {path.name} の {name} が複数回代入されている")
            found = node.value
    if found is None:
        bad(f"[4-実在] {path.name} に {name} が無い")
        return None
    if not isinstance(found, (ast.List, ast.Tuple)):
        bad(f"[5-静的] {path.name} の {name} がリテラルのリスト/タプルでない")
        return None
    # 実行時の増減（append/extend/+=）が無いこと＝顔ぶれが環境で変わらない。
    for node in ast.walk(tree):
        if isinstance(node, ast.AugAssign) and getattr(node.target, "id", "") == name:
            bad(f"[5-静的] {path.name} の {name} が実行時に += されている")
        if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                and getattr(node.func.value, "id", "") == name
                and node.func.attr in ("append", "extend", "insert", "pop", "clear")):
            bad(f"[5-静的] {path.name} の {name} が実行時に {node.func.attr}() されている")
    return found


def actual_checks():
    node = _module_list(CHECK_MACHINE, "CHECKS")
    if node is None:
        return []
    names = []
    for el in node.elts:
        if not isinstance(el, ast.Tuple) or not isinstance(el.elts[0], ast.Name):
            bad("[5-静的] CHECKS の要素が (関数, ラベル) のリテラル対でない")
            continue
        names.append(el.elts[0].id)
    return names


def defined_functions(path):
    return {n.name for n in ast.parse(path.read_text(encoding="utf-8")).body
            if isinstance(n, ast.FunctionDef)}


def killtest_coverage():
    """killtest.py が持つ変異の顔ぶれを AST で読む（走らせない）。"""
    node = _module_list(KILLTEST, "CHECK_MUTANTS")
    check_side = []
    if node is not None:
        for el in node.elts:
            if isinstance(el, ast.Tuple) and isinstance(el.elts[0], ast.Constant):
                check_side.append(el.elts[0].value)
    node2 = _module_list(KILLTEST, "HOOK_SPEC_NAMES")
    hook_side = [e.value for e in node2.elts
                 if isinstance(e, ast.Constant)] if node2 is not None else []
    return check_side, hook_side


def main():
    counts, r_hooks, r_checks = load_roster()
    a_hooks = actual_hooks()
    a_checks = actual_checks()
    kt_checks, kt_hooks = killtest_coverage()

    # 1+2: 顔ぶれと引数（三つ組）の集合一致
    for extra in sorted(set(a_hooks) - set(r_hooks)):
        bad(f"[1-顔ぶれ] settings.json に在るが roster に宣言が無い配線: {extra}")
    for missing in sorted(set(r_hooks) - set(a_hooks)):
        bad(f"[1-顔ぶれ] roster が宣言しているのに settings.json に無い配線: {missing}"
            "（配線が消えた＝その番人は黙っている）")
    if [c for c, _n in r_checks] != a_checks:
        for extra in sorted(set(a_checks) - {c for c, _n in r_checks}):
            bad(f"[1-顔ぶれ] CHECKS に在るが roster に宣言が無い検査: {extra}")
        for missing in sorted({c for c, _n in r_checks} - set(a_checks)):
            bad(f"[1-顔ぶれ] roster が宣言しているのに CHECKS に無い検査: {missing}"
                "（検査が消えた＝その番人は黙っている）")
        if sorted(a_checks) == sorted(c for c, _n in r_checks):
            bad("[2-引数] CHECKS の顔ぶれは同じだが**順序**が roster と違う")

    # 3: 員数コメント ↔ 実数
    actual_counts = {
        "hook_wirings": len(a_hooks),
        "hook_files": len({s for _e, _m, s in a_hooks}),
        "checks": len(a_checks),
        "killtest_mutants": len(kt_checks) + len(kt_hooks),
    }
    for key, want in counts.items():
        got = actual_counts.get(key)
        if got is None:
            bad(f"[3-員数] roster が数え方の定義に無い名前を宣言している: {key}")
        elif got != want:
            bad(f"[3-員数] {key}: 宣言 {want} ↔ 実数 {got}")
    for key in actual_counts:
        if key not in counts:
            bad(f"[3-員数] {key} の員数宣言が roster に無い")

    # 4: エントリポイントの実在
    for _e, _m, script in r_hooks:
        if not (HOOKS_DIR / script).is_file():
            bad(f"[4-実在] 宣言された {script} が .claude/hooks/ に無い")
    defined = defined_functions(CHECK_MACHINE)
    for fn, _n in r_checks:
        if fn not in defined:
            bad(f"[4-実在] 宣言された {fn}() が check_machine.py に定義されていない")

    # 6: killtest の双方向カバレッジ
    r_finding_names = {n for _c, n in r_checks}
    for missing in sorted(r_finding_names - set(kt_checks)):
        bad(f"[6-被覆] 検査 '{missing}' に killtest の変異が無い（鳴るか誰も確かめていない）")
    for orphan in sorted(set(kt_checks) - r_finding_names):
        bad(f"[6-被覆] killtest の変異 '{orphan}' に対応する検査が roster に無い")
    r_hook_files = {s for _e, _m, s in r_hooks}
    for missing in sorted(r_hook_files - set(kt_hooks)):
        bad(f"[6-被覆] フック '{missing}' に killtest の発火試験が無い")
    for orphan in sorted(set(kt_hooks) - r_hook_files):
        bad(f"[6-被覆] killtest の発火試験 '{orphan}' は配線されていないフックを見ている")

    print(f"=== roster 照合（配線 {len(a_hooks)} / 検査 {len(a_checks)} / "
          f"killtest 変異 {len(kt_checks) + len(kt_hooks)}）===")
    if problems:
        print(f"■ ズレ {len(problems)} 件")
        for p in problems:
            print("  ✗ " + p)
    else:
        print("■ ズレなし（宣言と実体が 6 点すべてで一致）")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
