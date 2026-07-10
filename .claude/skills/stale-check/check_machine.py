#!/usr/bin/env python3
"""
stale-check の「機械チェック実体」。

ドキュメント／設定が主張する内容と、実態（コード・ビルド設定・DBスキーマ・git）の
ズレを決定的に検出する。読み取り専用で副作用は無い。

なぜ単体スクリプトに切り出すか:
  軽量モードを「日常的に叩けるコスト」にするため。Claude が個別に grep/read する代わりに
  全機械チェックを一括実行し結果だけ受け取れば、トークンが桁違いに安く・結果が再現的になる。
  意味的整合（アーキ記述とコードの乖離など機械化困難なもの）は SKILL.md 側で
  Claude／並列エージェントが担当する。

使い方:
  python .claude/skills/stale-check/check_machine.py          # 人間可読サマリ
  python .claude/skills/stale-check/check_machine.py --json    # 機械可読(JSON)
  python .claude/skills/stale-check/check_machine.py --full    # 互換のため受理（出力は同じ＝機械チェックは常に全件）

終了コード: 確度高(severity=high)の陳腐化が1件以上あれば 1、無ければ 0。
  ※ これはレポート用途であり hook ではない。コミット等はブロックしない。
"""
import ast
import collections
import datetime
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# Windows のコンソール既定コードページでの文字化けを防ぐ（既存 hook と同じ作法）。
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# このスクリプトは <root>/.claude/skills/stale-check/check_machine.py に置かれる。
# parents[3] がプロジェクトルート。CWD に依存させない（どこから呼ばれても安定）ため __file__ 基準。
ROOT = Path(__file__).resolve().parents[3]
STATE_FILE = ROOT / ".claude/.stale_check_state.json"

# 軽量モードで「前回チェック以降に変わった管理ファイル」を絞り込むための対象プレフィックス。
# これに該当する差分だけ Claude が意味確認すればよい（コア12チェックは常に全件実行）。
MANAGED_PREFIXES = (
    "CLAUDE.md", "STATUS.md", "handover.md", "task_diary.md",
    ".claude/skills/", ".claude/hooks/", ".claude/settings", ".mcp.json", "docs/",
)

findings = []  # 各要素: {"check", "status", "severity", "detail"}


def add(check, status, severity, detail):
    findings.append({"check": check, "status": status, "severity": severity, "detail": detail})


def read_text(rel):
    """ルート相対パスを utf-8 で読む。読めなければ None。"""
    try:
        return (ROOT / rel).read_text(encoding="utf-8")
    except OSError:
        return None


# ── 1. 版数照合: CLAUDE.md の宣言 ↔ gradle の実値 ──────────────────────────
def check_versions():
    claude = read_text("CLAUDE.md")
    settings_gradle = read_text("android/settings.gradle")
    app_gradle = read_text("android/app/build.gradle")
    if not (claude and settings_gradle and app_gradle):
        add("versions", "error", "info", "CLAUDE.md / gradle のいずれかが読めず版数照合をスキップ")
        return

    # Phase 5 (2026-07-05) で Chaquopy/Python を撤去したため版数照合対象から外した
    # （settings.gradle に chaquo plugin・build.gradle に python{} ブロックが無くなり抽出不能になる）。
    actual = {}
    m = re.search(r"\bminSdk\s+(\d+)", app_gradle)
    if m:
        actual["minSdk"] = m.group(1)
    m = re.search(r"\btargetSdk\s+(\d+)", app_gradle)
    if m:
        actual["targetSdk"] = m.group(1)

    declared = {}
    for key, pat in (
        ("minSdk", r"minSdk\s+(\d+)"),
        ("targetSdk", r"targetSdk\s+(\d+)"),
    ):
        m = re.search(pat, claude)
        declared[key] = m.group(1) if m else None

    for key in ("minSdk", "targetSdk"):
        a, d = actual.get(key), declared.get(key)
        if a and d and a != d:
            add("versions", "stale", "high", f"{key}: CLAUDE.md='{d}' ↔ gradle実値='{a}'")
        elif a is None or d is None:
            add("versions", "warn", "info", f"{key}: 値の抽出に失敗 (actual={a}, declared={d})")


# ── 2. DB整合: version ↔ schemas ↔ addMigrations ↔ skill履歴 ───────────────
def check_db():
    appdb = read_text("android/app/src/main/java/com/novelreader/data/AppDatabase.kt")
    if not appdb:
        add("db", "error", "info", "AppDatabase.kt が読めず DB 整合チェックをスキップ")
        return

    mver = re.search(r"version\s*=\s*(\d+)", appdb)
    version = int(mver.group(1)) if mver else None

    pairs = sorted({(int(a), int(b)) for a, b in re.findall(r"MIGRATION_(\d+)_(\d+)", appdb)})

    schemas_dir = ROOT / "android/app/schemas/com.novelreader.data.AppDatabase"
    schema_versions = [int(f.stem) for f in schemas_dir.glob("*.json") if f.stem.isdigit()] \
        if schemas_dir.is_dir() else []
    max_schema = max(schema_versions) if schema_versions else None

    if version and max_schema and version != max_schema:
        add("db", "stale", "high",
            f"AppDatabase version={version} だが schemas 最大={max_schema}（スキーマ未export の疑い）")

    if version and pairs:
        max_to = max(b for _, b in pairs)
        if max_to != version:
            add("db", "stale", "high", f"最大 Migration の to=v{max_to} ↔ AppDatabase version={version} 不一致")
        for a, b in pairs:
            if b != a + 1:
                add("db", "warn", "info", f"MIGRATION_{a}_{b} が +1 の連続でない")

    skill = read_text(".claude/skills/db-migration/SKILL.md")
    if skill and version:
        sv = re.findall(r"v(\d+)\s*→\s*v(\d+)", skill)
        if sv:
            max_skill = max(int(b) for _, b in sv)
            if max_skill != version:
                add("db", "stale", "info",
                    f"db-migration skill の履歴表 最大=v{max_skill} ↔ 実 version=v{version}（skill 追記漏れ?）")


# ── 3. hook 双方向照合: settings の参照 ↔ 実ファイル ───────────────────────
def _registered_hooks():
    registered = set()
    for sf in (".claude/settings.json", ".claude/settings.local.json"):
        txt = read_text(sf)
        if not txt:
            continue
        try:
            data = json.loads(txt)
        except json.JSONDecodeError:
            add("hooks", "warn", "info", f"{sf} が JSON としてパースできない")
            continue
        # command 文字列群を走査して hooks/<name>.py を拾う。
        for m in re.finditer(r"hooks/([\w.-]+\.py)", json.dumps(data)):
            registered.add(m.group(1))
    return registered


def _actual_hooks():
    hooks_dir = ROOT / ".claude/hooks"
    return {p.name for p in hooks_dir.glob("*.py")} if hooks_dir.is_dir() else set()


def check_hooks_registration():
    actual = _actual_hooks()
    registered = _registered_hooks()
    for r in sorted(registered - actual):
        add("hooks", "stale", "high", f"settings が参照する '{r}' が .claude/hooks/ に実在しない（壊れた参照）")
    # hook ではない同居ライブラリ/CLI（ADR 0006: 捏造検知は「純ロジックのエンジン＋薄いアダプタ」構成で、
    # エンジンは Stop フックと CLI が import して使い、CLI は配線ゼロが設計）。settings 未登録が正。
    # hooks_common.py はコミット系フックが import する共有定義モジュール（ADR 0008）＝これも配線ゼロが正。
    non_hook_libs = {"detect_fabricated_execution_core.py", "analyze_transcript.py", "hooks_common.py"}
    for a in sorted(actual - registered):
        # test_*.py は hook 本体ではなく回帰テスト（guard/consume の正規表現整合を守る test_hooks.py 等）。
        # settings に登録しないのが正なので死 hook 判定から除外する（誤検知回避）。
        if a.startswith("test_") or a in non_hook_libs:
            continue
        add("hooks", "warn", "info", f"'{a}' は実在するが settings に未登録（動いていない死 hook の可能性）")


# ── 4. hook の git 追跡: 実ファイル ↔ git ls-files ─────────────────────────
def check_hook_git_tracked():
    actual = _actual_hooks()
    if not actual:
        return
    try:
        out = subprocess.run(
            ["git", "ls-files", ".claude/hooks"],
            cwd=ROOT, capture_output=True, text=True, timeout=10
        ).stdout
    except Exception:
        add("hook-git", "warn", "info", "git ls-files を実行できず追跡チェックをスキップ")
        return
    tracked = {Path(line).name for line in out.splitlines() if line.endswith(".py")}
    registered = _registered_hooks()
    for a in sorted(actual - tracked):
        # 登録済みなのに未追跡＝別環境で hook が file-not-found で壊れる。重大。
        sev = "high" if a in registered else "info"
        add("hook-git", "stale", sev, f"'{a}' が git 未追跡（settings 登録 hook ならコミット漏れ）")


# ── 5. コンフリクトマーカー検知 ──────────────────────────────────────────
def check_conflict_markers():
    targets = ["CLAUDE.md", "STATUS.md", "handover.md", "task_diary.md"]
    skills_dir = ROOT / ".claude/skills"
    if skills_dir.is_dir():
        targets += [str(p.relative_to(ROOT)).replace("\\", "/") for p in skills_dir.glob("*/SKILL.md")]
    # git の衝突マーカー。`=======` は setext 見出しの誤検知を避けるため行全体一致に限定。
    pat = re.compile(r"^(<{7}|>{7}|={7}\s*$)", re.MULTILINE)
    for t in targets:
        txt = read_text(t)
        if txt and pat.search(txt):
            add("conflict", "stale", "high", f"{t} に未解決のコンフリクトマーカーが残存")


# ── 6. 参照ファイルの実在 ────────────────────────────────────────────────
def _ref_exists(ref, doc):
    """参照ファイルが実在するか。パス付きは相対で厳密確認、ファイル名のみは主要ツリーを basename 検索。

    なぜ basename 検索するか: ドキュメントは `PdfBookExtractor.kt` のようにファイル名だけで言及することが多く、
    実体は android/app/src/ の深い階層にある。ルート直下だけ見ると実在ファイルを
    「参照切れ」と誤検知する。生成物 build/ は無関係かつ重いので検索対象から外す。
    """
    if "/" in ref:
        return (ROOT / ref).exists() or ((ROOT / doc).parent / ref).exists()
    if (ROOT / ref).exists():
        return True
    for base in (ROOT / "android/app/src", ROOT / ".claude", ROOT / "docs", ROOT / "ab-review"):
        if base.is_dir() and next(base.rglob(ref), None) is not None:
            return True
    return False


def check_referenced_files():
    # PDF 抽出ロジックは Phase 5 (2026-07-05) で Kotlin(pdf/) へ一本化し python/ を撤去した。
    must = [
        "android/gradlew",
        "android/app/src/main/java/com/novelreader/data/AppDatabase.kt",
        "android/app/src/main/java/com/novelreader/pdf/PdfBookExtractor.kt",
        "android/app/src/main/java/com/novelreader/pdf/PdfExtractor.kt",
        "android/app/src/main/java/com/novelreader/pdf/ChapterProcessor.kt",
        "android/app/src/main/java/com/novelreader/pdf/HtmlExporter.kt",
        "android/app/src/main/java/com/novelreader/pdf/ParserRules.kt",
    ]
    for rel in must:
        if not (ROOT / rel).exists():
            add("ref", "stale", "high", f"主要ファイルが存在しない: {rel}")

    # ドキュメントがバッククォートで名指しする *.md / *.py の相対参照が実在するか。
    for d in ("STATUS.md", "handover.md", "task_diary.md", "CLAUDE.md"):
        txt = read_text(d)
        if not txt:
            continue
        for m in re.finditer(r"`([\w./-]+\.(?:md|py))`", txt):
            ref = m.group(1)
            # URL・絶対・plans 配下・ワイルドカード的記述は対象外（誤検知回避）。
            # MEMORY.md は auto-memory の索引で ~/.claude 配下の外部ファイル（リポジトリ内に無いのが正）。
            if ref.startswith(("http", "/")) or ".claude/plans" in ref or "*" in ref or ref == "MEMORY.md":
                continue
            if not _ref_exists(ref, d):
                add("ref", "warn", "info", f"{d} が参照する '{ref}' が見つからない（参照切れの疑い）")


# ── 7. テストコマンドの一貫性 ────────────────────────────────────────────
def check_test_commands():
    # Phase 5 (2026-07-05) で Python 経路撤去。unittest test_logic の照合は廃止し testDebugUnitTest のみ。
    claude = read_text("CLAUDE.md") or ""
    for needle, label in (
        ("testDebugUnitTest", "Kotlin 単体テスト (testDebugUnitTest)"),
    ):
        if needle not in claude:
            add("cmd", "warn", "info", f"CLAUDE.md に {label} コマンドが見当たらない（記述ずれの疑い）")


# ── 8. gradlew パス健全性（build skill）──────────────────────────────────
def check_gradlew_path():
    build = read_text(".claude/skills/build/SKILL.md")
    if not build:
        return
    # gradlew の実体は android/ 配下。'./gradlew' を cd android 無しで書くとルートからは動かない。
    for i, line in enumerate(build.splitlines(), 1):
        if re.match(r"^\./gradlew\b", line.strip()):
            add("gradlew", "warn", "info",
                f"build skill L{i}: '{line.strip()}' は cd android を伴わない（gradlew 実体は android/ 配下）")


# ── 9. skill frontmatter 妥当性 ──────────────────────────────────────────
def check_skill_frontmatter():
    skills_dir = ROOT / ".claude/skills"
    if not skills_dir.is_dir():
        return
    for skill_md in skills_dir.glob("*/SKILL.md"):
        dir_name = skill_md.parent.name
        txt = skill_md.read_text(encoding="utf-8", errors="replace")
        fm = re.match(r"^---\s*\n(.*?)\n---", txt, re.DOTALL)
        if not fm:
            add("frontmatter", "stale", "high", f"{dir_name}/SKILL.md に frontmatter が無い")
            continue
        block = fm.group(1)
        mn = re.search(r"^name:\s*(\S+)", block, re.MULTILINE)
        if not mn:
            add("frontmatter", "stale", "high", f"{dir_name}/SKILL.md に name: が無い")
        elif mn.group(1) != dir_name:
            add("frontmatter", "warn", "info",
                f"{dir_name}/SKILL.md: name='{mn.group(1)}' がディレクトリ名と不一致")
        if not re.search(r"^description:\s*\S", block, re.MULTILINE):
            add("frontmatter", "warn", "info", f"{dir_name}/SKILL.md に description: が無い")


# ── 10. plans 参照の実在（管理md・skill が名指しする .claude/plans/*.md）────
def check_plans_references():
    """なぜ専用チェックか: check_referenced_files は .claude/plans を誤検知回避のため除外しており、
    2026-07-06 のフル照合で architecture skill の plans 参照切れを機械が見逃した実績がある。
    plans はアーカイブでも「存在しないファイルを指す台帳は読者を誤誘導する」（CLAUDE.md の
    一時ファイル規約）ため、参照の実在だけは機械で担保する。"""
    docs = ["CLAUDE.md", "STATUS.md", "handover.md", "task_diary.md"]
    docs_dir = ROOT / "docs"
    if docs_dir.is_dir():
        docs += [str(p.relative_to(ROOT)).replace("\\", "/") for p in docs_dir.rglob("*.md")]
    skills_dir = ROOT / ".claude/skills"
    if skills_dir.is_dir():
        docs += [str(p.relative_to(ROOT)).replace("\\", "/") for p in skills_dir.glob("*/SKILL.md")]
    # (?<!~/) で `~/.claude/plans/…`（ホーム側 active plan・リポジトリ外）を除外する。
    # ホーム側はこのマシンにしか無くリポジトリ実在チェックの対象にできない（マシン間で結果が揺れる）。
    pat = re.compile(r"(?<!~/)\.claude/plans/[\w.-]+\.md")
    for d in docs:
        txt = read_text(d)
        if not txt:
            continue
        # HTML コメントは除外: 「この参照は削除した」という経緯説明が旧パス文字列を含むのは正当
        # （STATUS.md の lab-verification 注記で実証済みの誤検知パターン）。
        txt = re.sub(r"<!--.*?-->", "", txt, flags=re.DOTALL)
        for ref in sorted(set(pat.findall(txt))):
            if not (ROOT / ref).exists():
                add("plans-ref", "stale", "info",
                    f"{d} が参照する '{ref}' が存在しない（plans 参照切れ）")


# ── 11. permissions が指すパスの実在 ─────────────────────────────────────
def check_permission_paths():
    """settings の allow/deny ルール内のパス様文字列が実在するかを確認する。
    なぜ: Phase 5 の Python 撤去後も test_logic 系の死 permission が残存し、
    2026-07-06 のフル照合まで機械では検出できなかった（穴の実証）。
    限界: `cd *python*` のようにワイルドカードで始まる断片はパスとして再構成できないため対象外
    （そうした残骸は意味チェック側で拾う）。"""
    token_pat = re.compile(r"[A-Za-z0-9_.~-]+(?:/[A-Za-z0-9_.*~-]+)+")
    for sf in (".claude/settings.json", ".claude/settings.local.json"):
        txt = read_text(sf)
        if not txt:
            continue
        try:
            perms = json.loads(txt).get("permissions", {})
        except json.JSONDecodeError:
            continue  # JSON 破損は check_hooks_registration 側で既に警告される
        rules = [r for v in perms.values() if isinstance(v, list)
                 for r in v if isinstance(r, str)]
        for rule in rules:
            for tok in sorted(set(token_pat.findall(rule))):
                prefix = tok.split("*", 1)[0].rstrip("/")
                # ホーム相対(~)・URL 断片・スラッシュが残らない断片は再構成不能のため対象外
                if "/" not in prefix or prefix.startswith(("http", "~")):
                    continue
                if not (ROOT / prefix).exists():
                    add("perm-path", "stale", "info",
                        f"{sf} の許可ルール '{rule}' が指す '{prefix}' が存在しない（死 permission の疑い）")


# ── 12. hook 動作点検（構文＋自己テスト）────────────────────────────────
def check_hook_smoke():
    """全 hook の構文チェック（ast.parse＝副作用なし）と、hooks の自己テスト
    （test_*.py を unittest で実行）を行う。
    なぜ: 参照整合だけでは「登録されているが起動時に落ちる hook」（構文エラー・リファクタの
    取り残し）を検出できない。hook はサイレント失敗クラス（task_diary #26/#28）のため、
    動作レベルの点検を機械チェックに組み込む（2026-07-06 大規模マージ後点検でのユーザー要望）。
    py_compile でなく ast.parse を使うのは __pycache__ を生成しない（本スクリプトの
    「読み取り専用・副作用なし」を守る）ため。"""
    hooks_dir = ROOT / ".claude/hooks"
    if not hooks_dir.is_dir():
        return
    for p in sorted(hooks_dir.glob("*.py")):
        try:
            ast.parse(p.read_text(encoding="utf-8", errors="replace"), filename=str(p))
        except SyntaxError as e:
            add("hook-smoke", "stale", "high",
                f"{p.name} が構文エラーで起動不能: L{e.lineno}: {e.msg}")
        except OSError:
            add("hook-smoke", "error", "info", f"{p.name} を読めず構文チェックをスキップ")
    tests = sorted(t.stem for t in hooks_dir.glob("test_*.py"))
    if not tests:
        return
    try:
        r = subprocess.run(
            [sys.executable, "-m", "unittest", *tests],
            cwd=hooks_dir, capture_output=True, text=True, timeout=120,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},  # 副作用（__pycache__）を作らない
        )
        if r.returncode != 0:
            out = (r.stderr or r.stdout).strip()
            tail = out.splitlines()[-1] if out else "(出力なし)"
            add("hook-smoke", "stale", "high",
                f"hook 自己テスト失敗（{' '.join(tests)}）: {tail}")
    except subprocess.TimeoutExpired:
        add("hook-smoke", "error", "info", "hook 自己テストがタイムアウト（120秒）")
    except Exception as e:
        add("hook-smoke", "error", "info", f"hook 自己テストを実行できない: {e}")


# ── 状態管理（軽量モードの差分起点）─────────────────────────────────────
def _git(args):
    try:
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        return ""


def load_state():
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def changed_since(last_commit):
    """前回チェックのコミット以降に変わった/未追跡のファイル集合。初回(last_commit無し)は None。"""
    if not last_commit:
        return None
    files = set()
    for line in _git(["diff", "--name-only", f"{last_commit}..HEAD"]).splitlines():
        if line.strip():
            files.add(line.strip())
    # まだコミットしていない作業ツリーの変更・未追跡も拾う（porcelain の3文字目以降がパス）。
    for line in _git(["status", "--porcelain"]).splitlines():
        path = line[3:].strip()
        if path:
            files.add(path)
    return files


def is_managed(path):
    p = path.replace("\\", "/")
    return any(p == m or p.startswith(m) for m in MANAGED_PREFIXES)


def update_state(high, total):
    """状態ファイルを現在の HEAD・日時・結果サマリで更新する（管理対象ファイルには触れない）。"""
    try:
        STATE_FILE.write_text(json.dumps({
            "last_checked_commit": _git(["rev-parse", "HEAD"]),
            "last_checked_at": datetime.datetime.now().isoformat(timespec="seconds"),
            "high": high,
            "total": total,
        }, ensure_ascii=False, indent=2), encoding="utf-8")
    except OSError:
        pass


# ── 13. task_diary 固定IDの一意性 ─────────────────────────────────────────
def check_diary_id_unique():
    """task_diary.md のエントリID（`#N.` 見出し）が重複していないか。

    なぜ要るか: エントリ番号は固定IDで、他ドキュメントが `#N` で参照する規約
    （リナンバー禁止）。だが並行ブランチが各自「次の番号」を採番するとマージで
    衝突し、参照がどちらを指すか曖昧になる（2026-07-07 に #30 の二重採番を実際に
    検出・解消済み）。リナンバー禁止ゆえ事後修正は移設マッピング表込みで高コスト
    ＝早期の機械検知だけが実効的な防御になる。
    """
    txt = read_text("task_diary.md")
    if txt is None:
        add("diary_id", "error", "info", "task_diary.md が読めず ID 一意性チェックをスキップ")
        return
    # fenced code block 内の見出し例（フック解説の ```md 等）を ID と誤認しないよう先に除去する。
    prose = re.sub(r"(?ms)^```.*?^```", "", txt)
    # 見出しは H3/H4 混在（`### 19.` と `#### 30.` が実在）のため両方拾う。
    # int 化するのは `030` と `30` のような表記揺れを同一 ID として突合するため（str のままだと見逃す）。
    ids = [int(i) for i in re.findall(r"^#{3,4}\s+(\d+)\.", prose, re.MULTILINE)]
    dupes = sorted(i for i, c in collections.Counter(ids).items() if c > 1)
    if dupes:
        add(
            "diary_id", "stale", "high",
            "task_diary.md のエントリIDが重複: " + ", ".join(f"#{i}" for i in dupes)
            + "（固定ID規約と衝突。後発側を未使用IDへ移し、末尾の移設マッピング表へ記録すること）",
        )


CHECKS = [
    check_versions,
    check_db,
    check_hooks_registration,
    check_hook_git_tracked,
    check_conflict_markers,
    check_referenced_files,
    check_test_commands,
    check_gradlew_path,
    check_skill_frontmatter,
    check_plans_references,
    check_permission_paths,
    check_hook_smoke,
    check_diary_id_unique,
]


def main():
    as_json = "--json" in sys.argv
    full = "--full" in sys.argv
    state = load_state()
    changed = None if full else changed_since(state.get("last_checked_commit"))

    for c in CHECKS:
        try:
            c()
        except Exception as e:  # 1チェックの例外で全体を落とさない
            add(c.__name__, "error", "info", f"チェック中に例外: {e}")

    high = [f for f in findings if f["severity"] == "high" and f["status"] != "ok"]
    info = [f for f in findings if f not in high]

    if as_json:
        out = {"high": len(high), "findings": findings}
        if not full:
            out["changed_managed"] = (
                None if changed is None else sorted(f for f in changed if is_managed(f))
            )
        print(json.dumps(out, ensure_ascii=False, indent=2))
    else:
        print(f"=== stale-check 機械チェック結果（検出 {len(findings)} 件 / 確度高 {len(high)} 件）===")
        # 軽量モードの差分セクション: ここに出た管理ファイルだけ意味確認すればよい。
        if not full:
            print("\n■ 前回チェック以降に変わった管理ファイル")
            if changed is None:
                print("  （状態記録なし → 初回フォールバック: 全体を対象に意味確認すること）")
            else:
                managed = sorted(f for f in changed if is_managed(f))
                if managed:
                    for f in managed:
                        print(f"  ~ {f}")
                else:
                    print("  （管理ファイルの変更なし → 機械チェック結果のみで可）")
        print(f"\n■ 確度高 / 要対応（{len(high)} 件）")
        for f in high:
            print(f"  ✗ [{f['check']}] {f['detail']}")
        if not high:
            print("  （なし）")
        print(f"\n■ 要確認 / 情報（{len(info)} 件）")
        for f in info:
            print(f"  - [{f['check']}] {f['detail']}")
        if not info:
            print("  （なし）")

    # 状態ファイルを更新（--no-update で抑制可）。管理対象ファイルには触れない。
    if "--no-update" not in sys.argv:
        update_state(len(high), len(findings))

    sys.exit(1 if high else 0)


if __name__ == "__main__":
    main()
