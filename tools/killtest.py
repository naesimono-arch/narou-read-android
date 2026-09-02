#!/usr/bin/env python3
"""killtest＝「番人が本当に鳴くか」を、壊した別世界で毎走measる。

思想（出所＝docs/reference/nuru-exchange-2026-09-02.md 第III部(B)）:
  検算器・ガードは **黙って緑を返す形で腐る**（語彙が古い／アンカーが動いた／配線が消えた）。
  当リポジトリには実例が2件ある——センチネル照合が 13 日間 dead だった件と、
  台帳のサイズ番人が個別上限を1本も実装しないまま緑を返していた件。どちらも
  「入力を壊すテスト」は在ったのに「番人が黙る」型のテストが1本も無かった。
  ∴ 実体の複製に 1 箇所ずつ壊れ方を注入し、**鳴るべきものだけが鳴る**ことを見る。

合格条件は終了コードだけではない:
  各変異は「どの番人を・どの文言で鳴らすはずか」を宣言し、宣言した文言が実際に出たことまで見る。
  別の番人が偶然鳴っただけを成立扱いにしない。

変異アンカーは構造で指す（値のリテラルで焼かない）:
  台帳・設定・コードは同期のたびに数字が動く。値を焼くと同期のたびに脚が一斉に外れ、
  **腐っているのはキルテストの方**という最悪の形になる（nuru で 20 脚中 7 脚が一斉脱落した実例）。
  ∴ 「version 行を探して +1」「最初のフック配線の .py 名を差し替える」のように形だけを焼き、
  値は実行時に現物から読む。期待文言も現物から組み立てる。
  アンカーが当たらなかったときは「鳴らなかった」ではなく **キルテスト自身の FAIL** にする。

判定は差分で採る:
  無傷の複製（対照）で出る指摘を先に採り、変異後に**新しく増えた**指摘だけを見る。
  複製の baseline が緑である必要をなくす（実リポジトリは常時 info を抱えている）。

射程:
  〈射程内〉①対照が再現すること ②各変異が宣言した番人・宣言した文言で鳴ること
  〈射程外〉変異の網羅性（列挙は閉じない）／本物のツリーでの挙動／番人が数える「意味」の正しさ／
           check_machine が git 履歴に依存する検査は、複製の履歴が合成である範囲でしか見ていない

使い方（所要が2桁違うので用途で使い分ける）:
  python3 tools/killtest.py --suite hooks   # フック16本＝**1分未満**。編集の合間に回す方
  python3 tools/killtest.py --suite checks  # 検査19本＝**20〜40分**。締めの前・CI に置く方
  python3 tools/killtest.py                 # 全走
  python3 tools/killtest.py --list
  python3 tools/killtest.py --keep          # 別世界を消さずに残す（調査用）

なぜ checks が重いか（実測・省略すると「遅いから回さない」で棚上げされるので明記する）:
  変異 1 本につき check_machine を 1 走させ、その中の `check_hook_smoke` が
  フックの unittest 14 モジュールを毎回走らせる＝1 走 10 秒〜2 分（他便と CPU を取り合うと後者）。
  ∴ 速い方（hooks）と重い方（checks）を分けてある。**重い方を軽くするために対照を省かない**
  ——対照を落とすと「増えた指摘」が採れず、判定が baseline の緑に依存してしまう。

⚠️ 別世界は必ず ext4（/tmp 配下）に作る。/mnt/c（drvfs）では check_machine 1 走が
   実測 8分23秒かかり、ext4 の複製では 9.9 秒だった（同一コミット・同一入力での実測）。
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECK_MACHINE = ".claude/skills/stale-check/check_machine.py"
# 撤去フック検査に「履歴に在って現ツリーに無いフック」を与えるための合成。
# 複製は tracked ファイルの1コミットしか持たないので、履歴側は自分で作る。
GHOST_HOOK = ".claude/hooks/_killtest_ghost_hook.py"


class AnchorMissing(Exception):
    """変異アンカーが現物に当たらなかった＝キルテスト自身の FAIL。"""


# ── 別世界の構築 ──────────────────────────────────────────────────────────
def sh(args, cwd, check=True, env=None):
    r = subprocess.run(args, cwd=str(cwd), capture_output=True, text=True, env=env)
    if check and r.returncode != 0:
        raise RuntimeError(f"{args} failed: {r.stderr[-300:]}")
    return r


# 別世界へ「作業ツリーの現物」を持ち込むパス。ここに該当しない未コミット変更は持ち込まない。
# なぜ全部を持ち込まないか: このリポジトリは複数エージェントが同一ツリーで並走する運用で、
# 作業ツリーは測定中も動き続ける（実測: git が追跡しているのにディスクに無い一瞬があり tar が中断した）。
# HEAD のオブジェクトから複製すれば対照が他便の編集に汚染されない＝nuru の「判定汚染」と同型の対処。
OVERLAY_GLOBS = ("CLAUDE.md", "docs/requirements.md", "docs/known-bugs-registry.md",
                 "tools/*.py", "tools/*.tsv", "tools/*.json", ".claude/hooks/*.py")


def build_world(dest):
    dest = Path(dest)
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    # HEAD のオブジェクトから複製する（作業ツリーを読まない＝並走する他便の編集に汚染されない）。
    arch = subprocess.Popen(["git", "archive", "HEAD"], cwd=str(ROOT), stdout=subprocess.PIPE)
    untar = subprocess.Popen(["tar", "-xf", "-", "-C", str(dest)], stdin=arch.stdout)
    arch.stdout.close()
    untar.communicate()
    if arch.wait() != 0 or untar.returncode != 0:
        raise RuntimeError("git archive による複製に失敗した")
    # 本便が所有するファイルだけは作業ツリーの現物を重ねる（自分の変更を検査対象に含めるため）。
    for pat in OVERLAY_GLOBS:
        for src_p in ROOT.glob(pat):
            if not src_p.is_file():
                continue
            dst = dest / src_p.relative_to(ROOT)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src_p, dst)

    env = dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")
    sh(["git", "init", "-q", "-b", WORLD_BRANCH], dest, env=env)
    sh(["git", "config", "user.email", "killtest@local"], dest, env=env)
    sh(["git", "config", "user.name", "killtest"], dest, env=env)
    # 履歴側にだけ存在するフックを1本作って消す＝撤去フック検査に食わせる合成履歴。
    (dest / GHOST_HOOK).write_text("# killtest 用の合成フック（履歴にのみ存在させる）\n", encoding="utf-8")
    sh(["git", "add", "-A"], dest, env=env)
    sh(["git", "commit", "-q", "-m", "world (with ghost hook)"], dest, env=env)
    (dest / GHOST_HOOK).unlink()
    sh(["git", "add", "-A"], dest, env=env)
    sh(["git", "commit", "-q", "-m", "world (ghost hook removed)"], dest, env=env)
    return dest


WORLD_BRANCH = "main"


def git_restore(world):
    """次の変異のために別世界を初期状態へ戻す。

    ⚠️ ブランチも戻すこと。`reset --hard` + `clean` はファイルしか戻さないので、
    ある対照が `checkout -b` でブランチを変えると、以降の全ケースがそのブランチ上で走り、
    ブランチ依存のガードが「正しく黙る」＝偽の SILENT になる（実測で guard_commit_branch が
    これで落ちた）。ケース間の状態漏れは番人の合否を静かに汚染する。
    """
    env = dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")
    sh(["git", "checkout", "-q", "--force", WORLD_BRANCH], world, env=env)
    sh(["git", "reset", "-q", "--hard", "HEAD"], world, env=env)
    sh(["git", "clean", "-qfd"], world, env=env)


# ── 変異のための構造ヘルパ（値は実行時に現物から読む）───────────────────────
def edit(world, rel, fn):
    p = Path(world) / rel
    if not p.exists():
        raise AnchorMissing(f"{rel} が複製に無い")
    old = p.read_text(encoding="utf-8")
    new = fn(old)
    if new == old:
        raise AnchorMissing(f"{rel}: 変異が何も変えなかった（アンカーが外れている）")
    p.write_text(new, encoding="utf-8")


def sub_once(text, pattern, repl, what):
    new, n = re.subn(pattern, repl, text, count=1)
    if n != 1:
        raise AnchorMissing(f"{what}: パターンが {n} 箇所（1 箇所であること）")
    return new


def const_str(source, name):
    """モジュールの文字列定数を AST で解く（`A = "x" "y"` / `B = A + C` の連結を含む）。

    なぜ import しないか: フックは module 直下で stdin を読むため import すると走ってしまう。
    なぜ正規表現でないか: 定義が断片連結のとき値がソースに連続形で現れない。
    """
    import ast
    env = {}
    for node in ast.parse(source).body:
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        t = node.targets[0]
        if not isinstance(t, ast.Name):
            continue
        try:
            env[t.id] = ast.literal_eval(node.value)
        except (ValueError, SyntaxError):
            v = node.value
            if (isinstance(v, ast.BinOp) and isinstance(v.op, ast.Add)
                    and isinstance(v.left, ast.Name) and isinstance(v.right, ast.Name)
                    and v.left.id in env and v.right.id in env):
                env[t.id] = env[v.left.id] + env[v.right.id]
    if name not in env or not isinstance(env[name], str):
        raise AnchorMissing(f"{name} を文字列定数として解けない")
    return env[name]


def read_num(world, rel, pattern, what):
    m = re.search(pattern, (Path(world) / rel).read_text(encoding="utf-8"))
    if not m:
        raise AnchorMissing(f"{what}: {rel} で数値が取れない")
    return int(m.group(1))


# ── 変異の定義（19 検査ぶん）────────────────────────────────────────────
def m_versions(w):
    # CLAUDE.md は既定でビルド値を宣言しない＝宣言があるときだけ照合される。
    # ∴ gradle の実値を読み、それと食い違う宣言を CLAUDE.md へ植えて番人を起こす。
    n = read_num(w, "android/app/build.gradle", r"\bminSdk\s+(\d+)", "minSdk")
    edit(w, "CLAUDE.md", lambda t: t + f"\n<!-- killtest -->\nminSdk {n + 1}\n")
    return rf"minSdk: CLAUDE\.md='{n + 1}' ↔ gradle実値='{n}'"


def m_db(w):
    v = read_num(w, "android/app/src/main/java/com/novelreader/data/AppDatabase.kt",
                 r"version\s*=\s*(\d+)", "Room version")
    edit(w, "android/app/src/main/java/com/novelreader/data/AppDatabase.kt",
         lambda t: sub_once(t, r"version\s*=\s*\d+", f"version = {v + 1}", "Room version"))
    return r"version"


def m_hooks_registration(w):
    # 最初のフック配線が指す .py を実在しない名前へ差し替える＝壊れた参照。
    edit(w, ".claude/settings.json",
         lambda t: sub_once(t, r"/\.claude/hooks/(\w+)\.py", r"/.claude/hooks/\1_killtest_absent.py",
                            "最初のフック配線"))
    return r"_killtest_absent\.py"


def m_hook_git_tracked(w):
    (Path(w) / ".claude/hooks/_killtest_untracked.py").write_text("import sys\n", encoding="utf-8")
    return r"_killtest_untracked\.py"


def m_conflict(w):
    edit(w, "STATUS.md", lambda t: t + "\n<<<<<<< HEAD\nkilltest\n=======\nkilltest\n>>>>>>> other\n")
    return r"STATUS\.md"


def m_ref(w):
    edit(w, "docs/knowledge/README.md",
         lambda t: t + "\n- killtest: `tools/_killtest_absent_ref.py` を参照する行\n")
    return r"_killtest_absent_ref\.py"


def m_test_commands(w):
    edit(w, "CLAUDE.md", lambda t: t.replace("testDebugUnitTest", "testDebugUnitTes_KILLTEST"))
    return r"testDebugUnitTest"


def m_gradlew(w):
    edit(w, ".claude/skills/build/SKILL.md",
         lambda t: t + "\n./gradlew :app:killtestTask\n")
    return r"cd android を伴わない"


def m_frontmatter(w):
    # skill の frontmatter name をディレクトリ名と食い違わせる。
    p = Path(w) / ".claude/skills/build/SKILL.md"
    edit(w, ".claude/skills/build/SKILL.md",
         lambda t: sub_once(t, r"(?m)^name:\s*\S+", "name: killtest-renamed", "build skill の name"))
    assert p.exists()
    return r"killtest-renamed"


def m_plans_ref(w):
    edit(w, "task_diary.md",
         lambda t: t + "\n- killtest: `.claude/plans/_killtest_absent_plan.md` を参照する行\n")
    return r"_killtest_absent_plan\.md"


def m_perm_path(w):
    def f(t):
        d = json.loads(t)
        d.setdefault("permissions", {}).setdefault("allow", []).insert(
            0, "Read(//mnt/c/_killtest_absent_dir/**)")
        return json.dumps(d, ensure_ascii=False, indent=2)
    edit(w, ".claude/settings.json", f)
    return r"_killtest_absent_dir"


def m_hook_smoke(w):
    edit(w, ".claude/hooks/guard_commit_branch.py",
         lambda t: t + "\ndef killtest(:\n")            # 構文エラー
    return r"guard_commit_branch"


def m_diary_id(w):
    p = Path(w) / "task_diary.md"
    m = re.search(r"(?m)^(#{3,4})\s+(\d+)\.", p.read_text(encoding="utf-8"))
    if not m:
        raise AnchorMissing("task_diary.md に `#### N.` 見出しが無い")
    edit(w, "task_diary.md", lambda t: t + f"\n\n{m.group(1)} {m.group(2)}. killtest 重複採番\n")
    return rf"#{m.group(2)}"


def m_size_budget(w):
    # 上限に対して最も余裕のある台帳を実行時に選び、上限を超えるまで水増しする
    # （どの台帳が今どれだけ膨らんでいるかは日々動くので、対象をリテラルで焼かない）。
    budgets = {"STATUS.md": 2500, "handover.md": 8000, "awaiting-human.md": 12000}
    best = max(budgets, key=lambda k: budgets[k] - len((Path(w) / k).read_text(encoding="utf-8")))
    need = budgets[best] - len((Path(w) / best).read_text(encoding="utf-8")) + 100
    edit(w, best, lambda t: t + "\n" + ("killtest 水増し。" * (max(need, 1) // 12 + 2)))
    return re.escape(best) + r" が [\d,]+ 字"


def m_delegation_meter(w):
    # SubagentStop 側の配線だけを抜く＝「通告は出るが完走が記録されない」片肺運転。
    def f(t):
        d = json.loads(t)
        d["hooks"].pop("SubagentStop", None)
        return json.dumps(d, ensure_ascii=False, indent=2)
    edit(w, ".claude/settings.json", f)
    return r"SubagentStop に未配線"


def m_known_bugs(w):
    # ⚠️ 台帳**冒頭の凡例**には `check_xxx` というメタ変数（プレースホルダ）が書かれている。
    # 素朴に「最初の `check_...`」を掴むとそれに当たり、実在照合の対象でないので番人は正しく黙る
    # ＝キルテスト側が偽の SILENT を出す（実測で踏んだ）。∴ 表の行（`|` 始まり）に現れ、
    # かつメタ変数でない名指しだけを狙う。
    p = Path(w) / "docs/known-bugs-registry.md"
    m = None
    for line in p.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        m = re.search(r"`(check_(?!xxx\b)[a-z_]+)`", line)
        if m:
            break
    if m is None:
        raise AnchorMissing("known-bugs-registry.md の表に実在の `check_*` 名指しが無い")
    edit(w, "docs/known-bugs-registry.md",
         lambda t: t.replace(f"`{m.group(1)}`", f"`{m.group(1)}_killtest_absent`", 1))
    return rf"{m.group(1)}_killtest_absent"


def m_hook_output(w):
    # PreToolUse 配線のフックに素の stdout 出力を足す（実障害 7 件中 5 件がこの形）。
    edit(w, ".claude/hooks/guard_commit_branch.py",
         lambda t: t.replace("sys.exit(2)", 'print("killtest plain stdout")\nsys.exit(2)', 1))
    return r"素の stdout"


def m_removed_hook(w):
    # 履歴にだけ在るフック名を settings から参照する＝撤去したのに配線が残った形。
    ghost = Path(GHOST_HOOK).stem
    def f(t):
        d = json.loads(t)
        d["hooks"]["Stop"][0]["hooks"].append(
            {"type": "command", "command": f'python "${{CLAUDE_PROJECT_DIR}}/{GHOST_HOOK}"'})
        return json.dumps(d, ensure_ascii=False, indent=2)
    edit(w, ".claude/settings.json", f)
    return re.escape(ghost)


def m_suppression_selftest(w):
    # 抑止則を「常に抑止する」へ倒す＝黙る方向の腐り。selftest が素通しケースで鳴るはず。
    edit(w, CHECK_MACHINE,
         lambda t: sub_once(t, r"(?m)^(def _gone_scope\(([^)]*)\):\n)",
                            r"\1    return {i: True for i in range(len(\2))}\n",
                            "_gone_scope の定義"))
    return r"抑止則が期待と違う"


# killtest が発火試験を持つ結線フックの一覧（roster の静的照合対象）。
# なぜ module 直下に別途置くか: hook_specs() は別世界を引数に取る＝走らせないと顔ぶれが判らず、
# 員数照合のために毎回 killtest を全走させることになる。∴ 一覧は静的に持ち、
# hook_specs() の末尾で「実際に返した集合と一致すること」を検査して二重帳簿化を塞ぐ。
HOOK_SPEC_NAMES = (
    "inject_branch_context.py", "inject_handover_digest.py", "inject_subagent_briefing.py",
    "remind_commit_plan.py", "remind_task_diary.py", "consume_protected_sentinel.py",
    "count_delegation_turns.py", "block_destructive_migration.py", "guard_sentinel_creation.py",
    "check_sequence_id_collision.py", "warn_delegated_deletions.py", "check_schema_change.py",
    "guard_commit_branch.py", "remind_ledger_sync.py", "stop_guard_fabrication.py",
    "record_hallucination.py",
)


CHECK_MUTANTS = [
    ("versions", m_versions, "CLAUDE.md が gradle と食い違うビルド値を宣言する"),
    ("db", m_db, "Room の version だけを進めて schemas/Migration と割る"),
    ("hooks", m_hooks_registration, "フック配線が実在しない .py を指す"),
    ("hook-git", m_hook_git_tracked, "フック実体を git へ追加し忘れる"),
    ("conflict", m_conflict, "コンフリクトマーカーを残す"),
    ("ref", m_ref, "docs が実在しない .py を名指す"),
    ("cmd", m_test_commands, "CLAUDE.md からテストコマンド名が消える"),
    ("gradlew", m_gradlew, "build skill が cd android 無しの ./gradlew を書く"),
    ("frontmatter", m_frontmatter, "skill の frontmatter name がディレクトリ名と割れる"),
    ("plans-ref", m_plans_ref, "実在しない .claude/plans/*.md を名指す"),
    ("perm-path", m_perm_path, "permissions が消えたパスを指す"),
    ("hook-smoke", m_hook_smoke, "フックに構文エラーを入れる"),
    ("diary_id", m_diary_id, "task_diary の #N を重複採番する"),
    ("size_budget", m_size_budget, "台帳を上限超えまで膨らませる"),
    ("delegation-meter", m_delegation_meter, "委譲計測の SubagentStop 配線だけを抜く"),
    ("known-bugs", m_known_bugs, "既知バグ台帳が実在しない検知手段を名乗る"),
    ("hook-output", m_hook_output, "PreToolUse フックが素の stdout へ出す"),
    ("removed-hook", m_removed_hook, "撤去済みフックへの配線が残る"),
    ("selftest", m_suppression_selftest, "抑止則を常に抑止する側へ倒す"),
]


# ── check_machine の実行と差分 ────────────────────────────────────────────
def run_check_machine(world):
    r = subprocess.run([sys.executable, CHECK_MACHINE, "--json", "--no-update"],
                       cwd=str(world), capture_output=True, text=True, timeout=1800)
    try:
        data = json.loads(r.stdout)
    except json.JSONDecodeError:
        raise RuntimeError(f"check_machine の JSON が読めない: {r.stdout[-300:]} / {r.stderr[-300:]}")
    return {(f["check"], f["severity"], f["detail"]) for f in data["findings"]}


def suite_checks(world, verbose, only=None):
    """変異を 1 本ずつ当てて、宣言した番人が宣言した文言で鳴くかを見る。

    only を渡すと対照＋その変異だけを走らせる（1 本の確認に全走 40 分を払わないため）。
    ⚠️ only は**確認用の絞り込みであって合否ではない**——絞った走行の結果を
    「キルテストが通った」と報告しない（全走の結果だけがその主張を支えられる）。
    """
    targets = [m for m in CHECK_MUTANTS if only is None or m[0] in only]
    if only:
        unknown = set(only) - {m[0] for m in CHECK_MUTANTS}
        if unknown:
            raise SystemExit(f"--only に未知の変異名: {sorted(unknown)}")
    results = []
    print("  対照（無傷の複製）を採取中…", flush=True)
    base = run_check_machine(world)
    print(f"  対照 = {len(base)} 指摘", flush=True)
    for name, mutate, desc in targets:
        git_restore(world)
        try:
            expect = mutate(world)
        except AnchorMissing as e:
            results.append((name, "ANCHOR", f"変異アンカーが外れた: {e}"))
            continue
        try:
            after = run_check_machine(world)
        except Exception as e:
            results.append((name, "ERROR", str(e)))
            continue
        new = after - base
        by_name = [d for (c, _s, d) in new if c == name]
        if not by_name:
            others = sorted({c for (c, _s, _d) in new})
            results.append((name, "SILENT",
                            f"{desc} → {name} は鳴かなかった（増えたのは {others or 'なし'}）"))
        elif not any(re.search(expect, d) for d in by_name):
            results.append((name, "WRONGMSG",
                            f"{name} は鳴いたが宣言文言 /{expect}/ が出ない: {by_name[0][:110]}"))
        else:
            results.append((name, "OK", desc))
        if verbose:
            print(f"    {results[-1][1]:<9} {name}", flush=True)
    git_restore(world)
    return results


# ── フック 16 本の killtest ───────────────────────────────────────────────
def hook_specs(world):
    """(フック名, 配線イベント, 発火させる payload, 期待文言, 準備関数) を返す。

    期待文言は可能なかぎり現物から組み立てる（定数を焼かない）。
    """
    w = Path(world)
    hooks = w / ".claude/hooks"

    def src(name):
        return (hooks / name).read_text(encoding="utf-8")

    # 現物から読む定数（リテラルを焼かないため）
    notify = re.search(r"NOTIFY_INTERVAL\s*=\s*(\d+)", src("count_delegation_turns.py"))
    if not notify:
        raise AnchorMissing("count_delegation_turns.py に NOTIFY_INTERVAL が無い")
    notify_n = int(notify.group(1))
    # TARGET は断片の連結で定義されている（フック自身が自分のソースで自己ブロックしないための作法）。
    # ∴ 正規表現でなく AST で解いて現物から組み立てる。ここに連続形のリテラルを書くと
    # このファイル自身が block_destructive_migration の検知対象になってしまう。
    target = const_str(src("block_destructive_migration.py"), "TARGET")
    sent = re.search(r'SENTINEL_BASENAME\s*=\s*"([^"]+)"', src("guard_sentinel_creation.py"))
    if not sent:
        raise AnchorMissing("guard_sentinel_creation.py に SENTINEL_BASENAME が無い")
    sentinel = sent.group(1)
    branch = sh(["git", "branch", "--show-current"], w).stdout.strip()
    if not branch:
        raise AnchorMissing("複製のブランチ名が取れない")
    hv = (w / "handover.md").read_text(encoding="utf-8")
    hm = re.search(r"(?m)^##+\s+(\S.*?)\s*$", hv)
    if not hm:
        raise AnchorMissing("handover.md に節見出しが無い")
    digest_anchor = hm.group(1)[:12]

    def ctrl_branch(home):
        sh(["git", "checkout", "-q", "-b", "killtest-other"], w,
           env=dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null"))

    def ctrl_handover(home):
        (w / "handover.md").write_text("# handover\n\n（killtest: 中身を空にした）\n", encoding="utf-8")

    diary_id = re.search(r"(?m)^#{3,4}\s+(\d+)\.", (w / "task_diary.md").read_text(encoding="utf-8"))
    if not diary_id:
        raise AnchorMissing("task_diary.md に `#### N.` 見出しが無い")

    def stage(paths):
        def _p(home):
            env = dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")
            sh(["git", "add", "--"] + list(paths), w, env=env)
        return _p

    def prep_sentinel(home):
        # 実体は `.claude/` 直下（フックが dirname(dirname(__file__)) で解決する）。
        (w / ".claude" / sentinel).write_text("", encoding="utf-8")

    def prep_plan(home):
        d = Path(home) / ".claude/plans"
        d.mkdir(parents=True, exist_ok=True)
        (d / "_killtest_plan.md").write_text("# plan\n本文だけでコミット計画の節が無い\n", encoding="utf-8")

    def prep_ledger(home):
        p = w / "handover.md"
        p.write_text(p.read_text(encoding="utf-8") + "\n- killtest 項目は対応済み。\n", encoding="utf-8")
        stage(["handover.md"])(home)

    def prep_schema(home):
        s = next(w.glob("android/app/schemas/**/*.json"), None)
        if s is None:
            raise AnchorMissing("schemas/*.json が複製に無い")
        s.write_text(s.read_text(encoding="utf-8") + "\n", encoding="utf-8")
        sh(["git", "add", "-f", "--", str(s.relative_to(w))], w,
           env=dict(os.environ, GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null"))

    def prep_transcript(home):
        # 実ツール記録の裏付けが無い「テストを実行し全緑」報告だけの transcript。
        tp = Path(home) / "t.jsonl"
        rows = [{"type": "user", "message": {"role": "user", "content": "テストして"}},
                {"type": "assistant", "message": {"role": "assistant", "content": [
                    {"type": "text",
                     "text": "./gradlew testDebugUnitTest を実行しました。"
                             "全 307 件が緑（BUILD SUCCESSFUL）です。"}]}}]
        tp.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n",
                      encoding="utf-8")

    long_old = "\n".join(f"削除される行 {i}" for i in range(20))

    def prep_delete_target(home):
        (w / "docs/_killtest_target.md").write_text(long_old + "\n", encoding="utf-8")

    specs = [
        # 無条件に注入するフックは「文言が出るか」では死を検知できない（何を入力しても出る）。
        # ∴ 期待文言を**現物から**組み立て、現物を変えたら注入も変わることを対照で見る。
        ("inject_branch_context.py", "SessionStart", {},
         rf"現在ブランチ: {re.escape(branch)}", None, ctrl_branch),
        ("inject_handover_digest.py", "SessionStart", {},
         re.escape(digest_anchor), None, ctrl_handover),
        ("inject_subagent_briefing.py", "SubagentStart",
         {"agent_type": "general-purpose", "agent_id": "kt1", "cwd": str(w)},
         r"定型規律", None),
        ("remind_commit_plan.py", "PostToolUse",
         {"tool_name": "Write", "tool_input": {"file_path": "__HOME__/.claude/plans/_killtest_plan.md"}},
         r"コミット計画", prep_plan),
        ("remind_task_diary.py", "PostToolUse",
         {"tool_name": "Bash", "tool_input": {"command": 'git commit -m "fix: killtest の変更"'}},
         r"知見の置き場 想起", None),
        ("consume_protected_sentinel.py", "PostToolUse",
         {"tool_name": "Bash", "tool_input": {"command": 'git commit -m "x"'},
          "tool_response": {"stdout": "[main abc1234] x\n 1 file changed", "stderr": ""}},
         r"ブランチガード", prep_sentinel),
        ("count_delegation_turns.py", "PostToolUse",
         {"tool_name": "Bash", "tool_input": {"command": "ls"},
          "session_id": "killtest", "agent_id": "kt", "agent_type": "general-purpose",
          "__repeat__": notify_n},
         rf"{notify_n} 回に到達", None),
        ("block_destructive_migration.py", "PreToolUse",
         {"tool_name": "Edit", "tool_input": {"file_path": "/x/AppDatabase.kt",
                                              "new_string": f"builder.{target}()"}},
         r"BLOCK: dangerous migration", None),
        ("guard_sentinel_creation.py", "PreToolUse",
         {"tool_name": "Write", "tool_input": {"file_path": f"/x/{sentinel}", "content": ""}},
         r"センチネル保護", None),
        ("check_sequence_id_collision.py", "PreToolUse",
         {"tool_name": "Edit", "tool_input": {
             "file_path": str(w / "task_diary.md"), "old_string": "",
             "new_string": f"#### {diary_id.group(1)}. killtest 重複"}},
         r"連番ID衝突", None),
        ("warn_delegated_deletions.py", "PreToolUse",
         {"tool_name": "Edit", "agent_id": "kt", "tool_input": {
             "file_path": str(w / "docs/_killtest_target.md"),
             "old_string": long_old, "new_string": ""}},
         r"委譲スコープ", prep_delete_target),
        ("check_schema_change.py", "PreToolUse",
         {"tool_name": "Bash", "tool_input": {"command": 'git commit -m "x"'}},
         r"Room スキーマ", prep_schema),
        ("guard_commit_branch.py", "PreToolUse",
         {"tool_name": "Bash", "tool_input": {"command": 'git commit -m "x"'}},
         r"保護ブランチ", None),
        ("remind_ledger_sync.py", "PreToolUse",
         {"tool_name": "Bash", "tool_input": {"command": 'git commit -m "x"'}},
         r"台帳の消化 想起", prep_ledger),
        ("stop_guard_fabrication.py", "Stop",
         {"transcript_path": "__HOME__/t.jsonl", "session_id": "killtest", "cwd": str(w)},
         r"実行捏造の疑い", prep_transcript),
        ("record_hallucination.py", "UserPromptSubmit",
         {"prompt": "/hallucination killtest", "session_id": "killtest",
          "transcript_path": "__HOME__/t.jsonl", "cwd": str(w)},
         r"hallucination 自動キャプチャ", prep_transcript),
    ]
    got = {s[0] for s in specs}
    if got != set(HOOK_SPEC_NAMES):
        raise AnchorMissing(
            f"HOOK_SPEC_NAMES と実体が割れている（一覧のみ={sorted(set(HOOK_SPEC_NAMES) - got)} "
            f"/ 実体のみ={sorted(got - set(HOOK_SPEC_NAMES))}）")
    return specs


def run_hook(world, home, name, payload):
    env = dict(os.environ, HOME=str(home), CLAUDE_PROJECT_DIR=str(world),
               GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")
    r = subprocess.run([sys.executable, str(Path(world) / ".claude/hooks" / name)],
                       cwd=str(world), input=json.dumps(payload, ensure_ascii=False),
                       capture_output=True, text=True, env=env, timeout=120)
    return r


def hook_text(r):
    """モデルが実際に受け取る文字列。

    なぜ stdout の生文字列だけでは足りないか: フックが `json.dumps` を既定
    （ensure_ascii=True）で出すと日本語がバックスラッシュ u の数値エスケープへ逃げ、
    生文字列には宣言文言が 1 文字も現れない（実測: inject_branch_context が偽の SILENT になった）。
    モデルへ渡るのは復号後の additionalContext なので、復号できたぶんを照合対象へ足す。
    """
    parts = [r.stdout or "", r.stderr or ""]
    try:
        parts.append(json.dumps(json.loads(r.stdout or ""), ensure_ascii=False))
    except (json.JSONDecodeError, TypeError):
        pass
    return "\n".join(parts)


def suite_hooks(world, verbose):
    results = []
    home = Path(tempfile.mkdtemp(prefix="killtest-home-"))
    try:
        specs = hook_specs(world)
        for spec in specs:
            name, event, payload, expect, prep = spec[:5]
            control_prep = spec[5] if len(spec) > 5 else None
            git_restore(world)
            try:
                if prep:
                    prep(home)
            except AnchorMissing as e:
                results.append((name, "ANCHOR", str(e)))
                continue
            body = json.loads(json.dumps(payload).replace("__HOME__", str(home)))
            repeat = body.pop("__repeat__", 1)
            body["hook_event_name"] = event
            r = None
            for _ in range(repeat):
                r = run_hook(world, home, name, body)
            out = hook_text(r)
            # 対照＝「鳴らないはずの世界では鳴らない」こと。番人が何にでも鳴くのを合格にしない。
            # 無条件注入フック（SessionStart 系）は payload では黙らないので、代わりに
            # **世界の側**を変えて、注入内容が現物に追随していることを見る。
            if control_prep:
                git_restore(world)
                if prep:
                    prep(home)
                control_prep(home)
                rb = run_hook(world, home, name, body)
            else:
                benign = {"hook_event_name": event, "tool_name": "__killtest_none__",
                          "tool_input": {}, "prompt": "無関係な発話", "session_id": "killtest"}
                rb = run_hook(world, home, name, benign)
            benign_out = hook_text(rb)
            if not re.search(expect, out):
                results.append((name, "SILENT",
                                f"{event} で発火 payload を与えたが /{expect}/ が出ない "
                                f"(exit={r.returncode}) {out.strip()[:110]}"))
            elif re.search(expect, benign_out):
                results.append((name, "ALWAYS",
                                f"無関係な payload でも /{expect}/ が出る＝番人が何にでも鳴いている"))
            else:
                results.append((name, "OK", f"{event}: exit={r.returncode}"))
            if verbose:
                print(f"    {results[-1][1]:<9} {name}", flush=True)
    finally:
        shutil.rmtree(home, ignore_errors=True)
        git_restore(world)
    return results


# ── エントリポイント ──────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--suite", choices=["checks", "hooks", "all"], default="all")
    ap.add_argument("--world", default=None, help="別世界の置き場（既定: /tmp 配下の一時ディレクトリ）")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--only", default=None,
                    help="checks の変異名をカンマ区切りで絞る（確認用。合否の主張には使わない）")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("-q", "--quiet", action="store_true")
    a = ap.parse_args()

    if a.list:
        print(f"=== killtest 変異一覧 ===\n■ check_machine の検査（{len(CHECK_MUTANTS)} 本）")
        for n, _f, d in CHECK_MUTANTS:
            print(f"  {n:<18} {d}")
        print(f"■ 結線フックの発火試験（{len(HOOK_SPEC_NAMES)} 本）")
        for n in HOOK_SPEC_NAMES:
            print(f"  {n}")
        print("（発火 payload と期待文言は別世界の現物から組み立てるので --suite hooks で確定する）")
        return 0

    dest = Path(a.world) if a.world else Path(tempfile.mkdtemp(prefix="killtest-world-"))
    if not str(dest.resolve()).startswith("/tmp") and a.world is None:
        print("⚠️ 別世界は /tmp（ext4）に作ること。drvfs では 1 走 8 分超になる。")
    print(f"=== killtest: 別世界を {dest} に構築中… ===", flush=True)
    build_world(dest)
    results = {}
    try:
        if a.suite in ("hooks", "all"):
            print("■ 結線フック", flush=True)
            results["hooks"] = suite_hooks(dest, not a.quiet)
        if a.suite in ("checks", "all"):
            print("■ check_machine の検査", flush=True)
            only = [s.strip() for s in a.only.split(",")] if a.only else None
            results["checks"] = suite_checks(dest, not a.quiet, only)
    finally:
        if not a.keep:
            shutil.rmtree(dest, ignore_errors=True)
        else:
            print(f"（別世界を残した: {dest}）")

    bad = 0
    print("\n=== killtest 結果 ===")
    for suite, rows in results.items():
        ok = sum(1 for _n, s, _d in rows if s == "OK")
        print(f"■ {suite}: {ok}/{len(rows)} 本が「宣言どおりに鳴った」")
        for n, s, d in rows:
            if s != "OK":
                bad += 1
                print(f"  ✗ [{s}] {n}: {d}")
    print("（全て鳴いた）" if not bad else f"（鳴かなかった/誤って鳴いた: {bad} 件）")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
