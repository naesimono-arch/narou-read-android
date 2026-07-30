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
import tokenize
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
    "CLAUDE.md", "STATUS.md", "handover.md", "awaiting-human.md", "task_diary.md",
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
        elif a is None:
            add("versions", "warn", "info", f"{key}: gradle 実値の抽出に失敗 (actual={a})")
        # declared=None は正常: 2026-07-12 の CLAUDE.md 痩身でビルド値の宣言をやめ
        # build.gradle を唯一の正本にした（宣言があるときだけ食い違いを検査する）。


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
    targets = ["CLAUDE.md", "STATUS.md", "handover.md", "awaiting-human.md", "task_diary.md"]
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
# 「その参照先はもう無い（無いのが正しい）」と文書が明言している印になる語彙。
# 過去形・完了形に限定する: `削除` 単独を入れると「削除機能」を説明する散文まで
# 抑止してしまい、検査を骨抜きにする（抑止則は狭く保つほど検査の信用が残る）。
_GONE_RE = re.compile(
    r"撤去|廃止|退役|消滅|削除済み|削除された|現存しない|存在しない|残っていない"
    r"|含まれない|非収蔵|収蔵していない|使い捨て|破棄"
    # 「アーカイブへ移した」も同義（旧パスを歴史として書き残す ADR 0005 の書き方）。
    # 素の `移設` は入れない: 移設を告げながら旧パスを指したままの記述こそ検出したい対象だから。
    r"|アーカイブ移設|移設済み|移設ずみ"
)
# メタ変数記法を含む参照は実パスではない（`NNNN-kebab-case.md` の採番テンプレート・`{bookId}` 等）。
_PLACEHOLDER_RE = re.compile(r"NNN|[{}<>*]")
_HEADING_RE = re.compile(r"^#{1,6}\s")
# 新しい箇条書き項目の開始（＝直前の項目の継続行ではない）。字下げされた `- ` は継続。
_NEW_ITEM_RE = re.compile(r"^(?:[-*+]\s|\d+[.)]\s)")


def _gone_scope(lines):
    """行番号 → その行に在る参照へ『もう無い』注記が効いているか、を先に解く。

    なぜ行単位の判定では足りないか: 注記は参照と同じ行に書けるとは限らない。
    このリポジトリで実際に使われている断り書きは3通りあり、どれも読み手には自明だが
    機械には見えない形をしている（2026-07-30 の参照全数棚卸しで確認した実在の書き方）:

      (1) 同じ行に併記          … 従来から効いていた唯一の形
      (2) 直後の継続行に注記    … `※…` や字下げの続き。参照は行末、注記は次行というのが常態
      (3) 前置きの引用ブロック  … `> …` の断り書き。文書前置き（最初の `##` より前）なら
                                   文書全体に、節の中なら次の見出しまでに効く

    取りこぼすとどうなるか: 正しく注記された参照が毎回ノイズとして出続け、検査そのものが
    信用されなくなる（狼少年化）。対象ドキュメントを広げる前にここを先に固めるのはそのため。
    """
    n = len(lines)
    scoped = [False] * n

    # ── (3) 引用ブロックの断り書き ──
    # 文書前置き＝最初の `##` 以降の見出しより前。タイトル行（`# …`）の直後に置かれた
    # 前置き引用は文書全体の断り書きとして使われている（docs/reference/04-06 の収蔵注記が実例）。
    first_section = next((i for i, ln in enumerate(lines)
                          if re.match(r"^#{2,6}\s", ln)), n)
    for i, ln in enumerate(lines):
        if not ln.lstrip().startswith(">") or not _GONE_RE.search(ln):
            continue
        if i < first_section:
            end = n                      # 前置き＝文書全体
        else:
            end = next((j for j in range(i + 1, n) if _HEADING_RE.match(lines[j])), n)
        for j in range(i, end):
            scoped[j] = True

    # ── (1)(2) 同一行 + 直後の継続行 ──
    # 継続行＝空行でも見出しでも新しい箇条書き項目でもない行。段落境界を越えないので
    # 「無関係な後続の記述がたまたま撤去に言及していた」で誤って抑止することがない。
    for i in range(n):
        if scoped[i]:
            continue
        if _GONE_RE.search(lines[i]):
            scoped[i] = True
            continue
        for j in range(i + 1, min(i + 4, n)):   # 直後3行まで
            nxt = lines[j]
            if not nxt.strip() or _HEADING_RE.match(nxt) or _NEW_ITEM_RE.match(nxt):
                break
            if _GONE_RE.search(nxt):
                scoped[i] = True
                break
    return scoped


def _gone_basenames(lines):
    """文書のどこかで「この名前のファイルはもう無い」と名指しされたファイル名の集合。

    なぜ位置に依らない判定を別に持つか: 冒頭で「本文中の X は当時のファイルで現存しない」と
    まとめて断り、本文の離れた箇所で X に言及する書き方があるため（ADR 0004 が実例）。
    名前の一致を要求するので、引用ブロックの一括抑止より狭く効く＝誤抑止が起きにくい。
    """
    names = set()
    for ln in lines:
        if not _GONE_RE.search(ln):
            continue
        for m in re.finditer(r"`?([\w.\-]+\.(?:md|py|js|sh|kt|html))`?", ln):
            names.add(m.group(1))
    return names


# 実行時に filesDir へ生成される HTML＝リポジトリに無いのが正（`chap_N.html` は N がメタ変数）。
_RUNTIME_HTML_RE = re.compile(r"^(?:index|chap_[\w.]*)\.html$")

# 参照解決で走査するツリー。build/ 生成物は無関係かつ重いので入れない。
# `android/macrobenchmark/src` は 2026-07-30 追加: app モジュールしか見ておらず、
# 実在する `StartupBudget.kt`（計測モジュール）を参照切れと誤検知していた（対象文書を
# 広げて初めて露見した既存の穴）。android/ を丸ごと走査しないのは build/ を踏むため。
_SEARCH_ROOTS = (
    ROOT / "android/app/src",
    ROOT / "android/macrobenchmark/src",
    ROOT / ".claude",
    ROOT / "docs",
    ROOT / "tools",
)


def _mock_ref_exists(ref, doc):
    """モック HTML の実在。True=在る / False=無い（＝報告対象） / None=そもそも検査対象外。

    なぜ解決先を docs/design-candidates に限るか: HTML 参照には〈正本モック〉と〈実行時生成物〉と
    〈競合アプリ調査のローカル成果〉が混在していて、リポジトリ全体を探すと後2者を軒並み
    参照切れと誤検知する。検出したいのは「正本モックが消えたのに文書が指し続けている」ケース
    だけなので、モック置き場に限って突合し、それ以外は判定を放棄する（None）。
    """
    base = ref.rsplit("/", 1)[-1]
    if _RUNTIME_HTML_RE.match(base):
        return None
    mocks = ROOT / "docs/design-candidates"
    if not mocks.is_dir():
        return None
    if (ROOT / ref).exists() or ((ROOT / doc).parent / ref).exists():
        return True
    suffix = "/" + ref
    if any(p.as_posix().endswith(suffix) or p.name == ref for p in mocks.rglob(base)):
        return True
    # モック置き場のどこにも同名が無い。モック名かどうかの判定材料が無いので、
    # 「モックを名指しているのに消えている」と断定できるのは docs/design-candidates を
    # 明示的に指している参照だけ。裸のファイル名は判定を放棄する（誤検知を作らない）。
    return False if "design-candidates" in ref else None


def _ref_exists(ref, doc):
    """参照ファイルが実在するか。パス付きは相対で厳密確認、ファイル名のみは主要ツリーを基準検索。

    なぜ basename 検索するか: ドキュメントは `PdfBookExtractor.kt` のようにファイル名だけで言及することが多く、
    実体は android/app/src/ の深い階層にある。ルート直下だけ見ると実在ファイルを
    「参照切れ」と誤検知する。生成物 build/ は無関係かつ重いので検索対象から外す。
    """
    if "/" in ref:
        if (ROOT / ref).exists() or ((ROOT / doc).parent / ref).exists():
            return True
        # Kotlin のパスは com/novelreader/ 起点の相対で書く慣行（architecture skill が冒頭で明示宣言し、
        # new-screen skill の実例ポインタも踏襲）。ルート相対だけ見ると実在ファイルを参照切れと誤検知する。
        for base in (ROOT / "android/app/src/main/java/com/novelreader",
                     ROOT / "android/app/src/test/java/com/novelreader"):
            if (base / ref).exists():
                return True
        # 末尾一致のフォールバック: ドキュメントは文脈で自明な上位を省いた部分パスで名指しする
        # （`theme/Color.kt` の実体は ui/theme/・`network/NarouApiService.kt` は narou/network/ 配下）。
        # 厳密な相対解決だけだと、実在するファイルを軒並み参照切れと誤検知する（2026-07-29 実測で7件全部が誤検知）。
        suffix = "/" + ref
        for base in _SEARCH_ROOTS:
            if base.is_dir() and any(p.as_posix().endswith(suffix) for p in base.rglob(ref.rsplit("/", 1)[-1])):
                return True
        return False
    if (ROOT / ref).exists():
        return True
    for base in _SEARCH_ROOTS + (ROOT / "ab-review",):
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

    # ドキュメント・skill がバッククォートで名指しする相対参照が実在するか。
    # task_diary.md は対象に含めない: 凍結アーカイブ＝当時の事実の記録であり、そこに出る
    # ファイル名が現在消えているのはむしろ正しい（撤去済みフックの実例記述などが毎回ノイズになる）。
    # skill も対象に含める: shiori-tips の `tools/*.js` のような cwd 依存パスは、
    # 拡張子と走査対象の両方から漏れて機械チェックが原理的に検出できなかった（2026-07-25）。
    targets = ["STATUS.md", "handover.md", "awaiting-human.md", "CLAUDE.md"]
    targets += sorted(str(p.relative_to(ROOT)) for p in (ROOT / ".claude/skills").rglob("SKILL.md"))
    # 2026-07-30 拡張: docs/** と .claude/plans 直下も対象へ。
    # なぜ必要だったか: 本チェックの対象は STATUS/handover/CLAUDE と skill だけで、一方の
    # 項目10（plans 参照）は対象文書こそ広いが探すパターンが `.claude/plans/*.md` 参照だけ。
    # 両者のカバレッジが直交していたため、**ADR・knowledge・patterns・reference・plans の
    # 一般ファイル参照はどちらの検査も通っていなかった**。2026-07-30 の全数棚卸しで、その空白域に
    # 移設ずみ・削除ずみを指す参照が実際に溜まっていた（`viewmodel/SearchDraft.kt`＝domain/ へ移設、
    # ADR 0022 が「candidates/ に残置」と書いたまま実体だけ別コミットで消滅、等）ため塞ぐ。
    docs_dir = ROOT / "docs"
    if docs_dir.is_dir():
        targets += sorted(str(p.relative_to(ROOT)).replace("\\", "/") for p in docs_dir.rglob("*.md"))
    # .claude/plans は直下のみ（glob）＝ archive/ を自然に外す。archive は凍結された当時の記述で、
    # 参照が現ツリーと合わないのが正しい（まとめての断りは archive/README.md が持つ）。
    plans_dir = ROOT / ".claude/plans"
    if plans_dir.is_dir():
        targets += sorted(str(p.relative_to(ROOT)).replace("\\", "/") for p in plans_dir.glob("*.md"))
    for d in targets:
        txt = read_text(d)
        if not txt:
            continue
        # HTML コメントは除外（項目10 と同じ理由）: 「この参照は消した」という経緯説明が
        # 旧パス文字列を含むのは正当。行番号がずれないよう改行数だけ残して落とす。
        txt = re.sub(r"<!--.*?-->", lambda m: "\n" * m.group(0).count("\n"), txt, flags=re.DOTALL)
        lines = txt.splitlines()
        scoped = _gone_scope(lines)
        gone_names = _gone_basenames(lines)
        for i, line in enumerate(lines):
            # 行内に外部の絶対パス／ホーム参照が在れば、同じ行の裸ファイル名はその外部ディレクトリ
            # 配下を指す＝リポジトリ内に無いのが正。誤検知の最多パターンだった
            # （例: `/mnt/c/…/アプリ公開戦略/`（`外部リサーチ実査結果_….md`）という列挙）。
            if re.search(r"`[~/][^`]*`", line):
                continue
            # 「もう無い」と文書が断っている参照は対象外。同一行だけでなく直後の注記行・
            # 前置きの引用ブロック・冒頭での名指し宣言まで見る（_gone_scope の docstring）。
            if scoped[i]:
                continue
            # .kt も対象（2026-07-29 追加）: skill の「コピー元の実例」ポインタ（new-screen §5 等）と
            # architecture skill の所在表は Kotlin ファイルを名指しするのに、拡張子表から漏れて
            # 改廃を検出できなかった。行番号サフィックス（`Foo.kt:120-140`）はパス部分だけ見る。
            # .html は 2026-07-30 追加（モック正本の消滅検出）＝解決先を design-candidates に限る。
            for m in re.finditer(r"`([\w./-]+\.(?:md|py|js|sh|kt|html))(?::\d+(?:-\d+)?)?`", line):
                ref = m.group(1)
                # URL・絶対・plans 配下・ワイルドカード的記述は対象外（誤検知回避）。
                # MEMORY.md は auto-memory の索引で ~/.claude 配下の外部ファイル（リポジトリ内に無いのが正）。
                # "..." は中略記号（`android/.../ui/components/Foo.kt`）＝実パスではない。
                if (ref.startswith(("http", "/")) or ".claude/plans" in ref
                        or _PLACEHOLDER_RE.search(ref)
                        or "..." in ref or ref == "MEMORY.md"):
                    continue
                if ref.rsplit("/", 1)[-1] in gone_names:
                    continue
                if ref.endswith(".html"):
                    if _mock_ref_exists(ref, d) is not False:
                        continue
                    add("ref", "warn", "info",
                        f"{d} が参照するモック '{ref}' が docs/design-candidates 配下に無い（正本モック消滅の疑い）")
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
    """なぜ専用チェックか: check_referenced_files は `.claude/plans/…` を**指す**参照を除外しており
    （項目6 の ref スキップ条件）、2026-07-06 のフル照合で architecture skill の plans 参照切れを
    機械が見逃した実績がある。plans はアーカイブでも「存在しないファイルを指す台帳は読者を誤誘導する」
    （CLAUDE.md の一時ファイル規約）ため、参照の実在だけは機械で担保する。

    2026-07-30 追記: 項目6 の**対象文書**は docs/** と .claude/plans 直下まで広がったが、
    上記のとおり除外しているのは「参照先が plans であること」なので本チェックの役割は変わらない。
    加えて本チェックは task_diary.md を発信元に含む（項目6 は凍結アーカイブとして除外）＝ここも差分。"""
    docs = ["CLAUDE.md", "STATUS.md", "handover.md", "awaiting-human.md", "task_diary.md"]
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


# ── 14. 台帳・CLAUDE.md のサイズ予算 ─────────────────────────────────────
def check_size_budgets():
    """台帳が「現況のみ/やることのみ」の規約から再肥大していないかの番人。

    なぜ要るか: 2026-07-12 の台帳大掃除で STATUS=現在値のみ（目安60行）・handover=
    完了打ち消し線ゼロ・CLAUDE.md=ルーター（毎セッション固定費）と再定義した。
    この種の規約は宣言だけだと数週間で崩れる（大掃除前: STATUS 181行/91KB・
    docs コミットが全体の35%）＝機械の番人だけが実効的な防御になる。
    しきい値は「目安」の1.5倍程度を high とし、通常運用の揺らぎでは鳴らさない。
    """
    txt = read_text("STATUS.md")
    if txt is not None:
        n = txt.count("\n") + 1
        if n > 90:
            add("size_budget", "stale", "high",
                f"STATUS.md が {n} 行（目安60行）。完了ログ・git導出値の混入を疑い刈り込むこと（完了履歴は git log が正本）")
        elif n > 60:
            add("size_budget", "warn", "info", f"STATUS.md が {n} 行（目安60行超）。肥大の兆候")

    # 打ち消し線＝完了項目の残置。ADR 0028 で台帳を二分したので awaiting-human.md も同じ規約の下にある。
    for ledger in ("handover.md", "awaiting-human.md"):
        txt = read_text(ledger)
        if txt is None:
            continue
        strikes = len(re.findall(r"~~.+?~~", txt))
        if strikes:
            add("size_budget", "stale", "high",
                f"{ledger} に打ち消し線（完了項目の残置）が {strikes} 件。規約は「完了したら消す」＝行ごと削除すること")

        # なぜ打ち消し線だけでは足りないか: 2026-07-30 の分離作業で判明した実際の肥大は、打ち消し線を
        # 一切使わず「実装済み。残＝実機目視のみ」の形で完了経緯を本文に残す型だった（打ち消し線検査は
        # 0件のまま通り、handover が 42,000 字まで膨らんだ）。完了は git log が正本なので削り、人間待ちが
        # 残るなら awaiting-human.md へ移すのが ADR 0028 の運用。
        # 箇条書き行に限り、かつ「残＝／残は」の形に絞る＝引用ブロックの運用注記（「完了したら消す」）や
        # 「残る注意点」のような通常表現を誤検知しないため（絞る前は誤検知4:真陽性1だった）。
        leftovers = [
            ln for ln in txt.splitlines()
            if re.match(r"^\s*-\s", ln)
            and re.search(r"実装済み|全フェーズ完了|実機PASS|PASS 済|目視OK", ln)
            and re.search(r"残[＝=は]", ln)
        ]
        if leftovers:
            add("size_budget", "stale", "info",
                f"{ledger} に「完了＋残＝…」型の項目が {len(leftovers)} 件。完了経緯は git log が正本＝削り、"
                f"残りが人間待ちなら awaiting-human.md へ（ADR 0028）。先頭: {leftovers[0].strip()[:60]}")

    # 台帳2枚の**合計**文字数。ADR 0028 の宿題（対象リストへの awaiting-human.md 追加）は済んでいたが、
    # handover/awaiting-human には STATUS(60行)・CLAUDE(16KB) のような**数値予算が1つも無かった**
    # ＝上の2検査（打ち消し線・「完了＋残＝」型）を素通りする書き方で膨らめば無防備のまま。
    # なぜ per-file でなく合計が主判定か: 二分は**移し替え**であって痩身ではない。片方の溢れをもう片方へ
    # 移すと per-file 予算は両方とも緑になり、「session 冒頭で開く総量」（ADR 0028 症状3＝トークンコスト）は
    # 1文字も減っていないのに番人が黙る。合計で見ればその抜け道が原理的に塞がる。
    # なぜ 42,000 字か: 発明値ではなく ADR 0028 が「肥大の実測」として記録した分割前 handover の実サイズ。
    # 「分割したのに分割前の総量へ戻った」は最も反論しにくい肥大の事実なので、そこを high の線に採る。
    # なぜバイトでなく文字数か: 記録された 42,000 が「字」だから。日本語主体の台帳はバイトが約2倍に出る
    # （実測 handover 24,776 字 = 49,563 バイト）ため、単位を取り違えると予算が黙って2倍に緩む。
    LEDGER_TOTAL_BUDGET = 42000
    ledger_chars = {rel: len(read_text(rel) or "") for rel in ("handover.md", "awaiting-human.md")}
    total = sum(ledger_chars.values())
    breakdown = "・".join(f"{k} {v:,}字" for k, v in ledger_chars.items())
    if total > LEDGER_TOTAL_BUDGET:
        add("size_budget", "stale", "high",
            f"台帳2枚の合計が {total:,} 字（ADR 0028 が記録した分割前 handover の肥大値 {LEDGER_TOTAL_BUDGET:,} 字に到達）"
            f"＝二分の効果が消えている。完了経緯は git log が正本＝行ごと削ること（内訳: {breakdown}）")
    elif total > LEDGER_TOTAL_BUDGET * 3 // 4:
        add("size_budget", "warn", "info",
            f"台帳2枚の合計が {total:,} 字（肥大値 {LEDGER_TOTAL_BUDGET:,} 字の3/4超）。肥大の兆候（内訳: {breakdown}）")
    # per-file は「二分の釣り合い」の兆候として info のみ。片方が肥大値の半分を超える＝もう一方の台帳が
    # 事実上機能していない（＝二分前の1枚台帳へ戻りつつある）合図。awaiting-human も同じ線で見る:
    # 人間待ちは積むのが自然だが、積むべきは「待ち1件＝何を目視するか」の短い行で、
    # 経緯を抱えたまま積むのは handover と同じ病（ADR 0028 症状1）だから緩める理由が無い。
    for rel, n in ledger_chars.items():
        if n > LEDGER_TOTAL_BUDGET // 2:
            add("size_budget", "warn", "info",
                f"{rel} が {n:,} 字（台帳1枚あたりの目安 {LEDGER_TOTAL_BUDGET // 2:,} 字超）。"
                f"完了経緯の残留・1項目あたりの長文化を疑うこと")

    txt = read_text("CLAUDE.md")
    if txt is not None:
        size = len(txt.encode("utf-8"))
        if size > 16000:
            add("size_budget", "stale", "high",
                f"CLAUDE.md が {size} バイト（毎セッション固定費）。手順の skill 追い出し・フック重複の1行化で痩身すること（上限の考え方は claude-bestpractice/claude-md/knowledge/01）")
        elif size > 10000:
            add("size_budget", "warn", "info", f"CLAUDE.md が {size} バイト。肥大の兆候（痩身直後は約8KB）")


# ── 15. 委譲ターン計測フックの整合 ───────────────────────────────────────
def check_delegation_meter():
    """count_delegation_turns.py の配線・記録先・閾値記述の整合。

    なぜ要るか: 本フックは PostToolUse（計測・通告）と SubagentStop（完走記録）の2イベント
    配線で1機能を成す。片方だけ配線が消えると「通告は出るが記録されない」等の半死に状態が
    fail-open ゆえ無症状で続く（task_diary #44 と同じサイレント失敗クラス）。項目3の双方向
    照合は「ファイル実在」しか見ないため、イベント被覆はここで見る。
    """
    hook_name = "count_delegation_turns.py"
    src = read_text(f".claude/hooks/{hook_name}")
    settings = read_text(".claude/settings.json")
    if src is None or settings is None:
        add("delegation-meter", "error", "info",
            f"{hook_name} か settings.json が読めず点検をスキップ")
        return
    try:
        cfg = json.loads(settings)
    except json.JSONDecodeError:
        return  # settings 全体の破損は項目3側が露呈させる（ここで重複報告しない）
    for event in ("PostToolUse", "SubagentStop"):
        cmds = [h.get("command", "")
                for grp in cfg.get("hooks", {}).get(event, [])
                for h in grp.get("hooks", [])]
        if not any(hook_name in c for c in cmds):
            add("delegation-meter", "stale", "high",
                f"{hook_name} が {event} に未配線（計測/記録の片肺運転）")
    if "delegation-stats.jsonl" not in src:
        add("delegation-meter", "stale", "high",
            f"{hook_name} の記録先ファイル名が delegation-stats.jsonl から変わった/消えた"
            "（較正データの追記先が分裂している可能性）")
    m = re.search(r"NOTIFY_INTERVAL\s*=\s*(\d+)", src)
    if not m:
        add("delegation-meter", "stale", "high",
            f"{hook_name} に NOTIFY_INTERVAL 定数が見つからない（通告間隔の正本が不明化）")
    else:
        # docstring の「N 回ごと」記述と定数の乖離（片方だけ較正し直した見落とし）を検知
        for doc_n in re.findall(r"(\d+)\s*回ごと", src):
            if doc_n != m.group(1):
                add("delegation-meter", "stale", "high",
                    f"{hook_name} の通告間隔が不一致: 定数 NOTIFY_INTERVAL={m.group(1)} ↔ "
                    f"docstring 記述「{doc_n} 回ごと」（較正時の片側更新）")
                break


# ── 16. 既知バグレジストリ（L4）の名指し実在照合 ─────────────────────────
# 状態列の語彙（レジストリ冒頭の凡例が正本。ここは機械側の写しではなく「受理する値の定義」）。
_CI_WORKFLOW = ".github/workflows/ci.yml"
_REGISTRY_STATES = {"[!] なし", "[!] 知見のみ", "[~] 部分", "[o] 固定"}
# 検知ありを主張する状態＝検証可能な名指しを1つ以上持たねばならない。
_REGISTRY_DEFENDED = {"[~] 部分", "[o] 固定"}


def check_known_bugs_registry():
    """docs/known-bugs-registry.md が名指しする検知手段・参照が実在するかを照合する。

    なぜ要るか: L4 の台帳は「どのバグ型が無防備か」を一目で示すのが唯一の効用で、
    その判断材料は〈テストクラス名／機械チェック名／knowledge のパス〉という**外部への名指し**でできている。
    名指し先がリネーム・削除で消えても Markdown は何事もなく緑のまま読めてしまい、
    「守られている」という嘘が残る——このリポジトリには恒久 dead 化した判定が 13 日間気づかれなかった
    実例があり、台帳側にも同じ穴が開く。実在照合だけは機械で担保する。

    fail-open にしない工夫: レジストリが読めない／行が1件も取れない／名指しが1件も抽出できない場合は
    「検知器そのものが死んでいる」とみなして必ず落とす（黙って全通過させない）。
    """
    rel = "docs/known-bugs-registry.md"
    txt = read_text(rel)
    if txt is None:
        add("known-bugs", "stale", "high",
            f"{rel} が読めない（L4 レジストリの消失／移動。参照する SKILL.md・handover と併せて追随させること）")
        return

    # データ行＝状態セルがコードスパンで始まる 7 列の行。凡例（2 列）やヘッダは自然に外れる。
    rows = [ln for ln in txt.splitlines() if ln.startswith("| `[") and ln.count("|") == 8]
    if not rows:
        add("known-bugs", "stale", "high",
            f"{rel} から表の行を1件も抽出できない（表の列数が変わった＝この照合が恒久 dead 化している）")
        return

    test_root = ROOT / "android/app/src/test"
    check_names = {fn.__name__ for fn, _label in CHECKS}
    refs_seen = 0

    for ln in rows:
        cells = [c.strip() for c in ln.split("|")[1:-1]]
        state = cells[0].strip("`")
        bug_id = cells[1].strip("`")
        if state not in _REGISTRY_STATES:
            add("known-bugs", "stale", "high",
                f"{bug_id}: 状態 '{state}' が語彙外（受理値: {' / '.join(sorted(_REGISTRY_STATES))}）")
            continue

        # 検知手段セルと関連 knowledge セルのコードスパンだけを照合対象にする
        # （症状・機序セルの `items` のような語り中の識別子まで拾うと偽陽性になる）。
        # verifiable は**検知手段セルの分だけ**数える: knowledge セルの参照を数えてしまうと
        # 「検知手段は空文だが関連 knowledge が在るので防御あり」を通してしまい、
        # まさにこの台帳が禁じている「知見＝防御」の誤読を機械側で再現することになる。
        verifiable_here = 0
        for cell_i, cell in ((5, cells[5]), (6, cells[6])):
            for tok in re.findall(r"`([^`]+)`", cell):
                counts = cell_i == 5
                refs_seen += 1
                if tok.startswith("lint:"):
                    # lint ルールの実在はオフラインで確認できない。黙殺すると「語彙外を素通し」に
                    # なるため info で必ず表に出す（未検証であること自体を可視化する）。
                    add("known-bugs", "warn", "info",
                        f"{bug_id}: '{tok}' は lint ルール名＝機械照合の対象外（人間が有効性を確認すること）")
                    verifiable_here += counts
                elif re.fullmatch(r"check_\w+", tok):
                    if tok in check_names:
                        verifiable_here += counts
                    else:
                        add("known-bugs", "stale", "high",
                            f"{bug_id}: 機械チェック '{tok}' が CHECKS に存在しない")
                elif re.fullmatch(r"[A-Z]\w*Test", tok):
                    if test_root.is_dir() and next(test_root.rglob(f"{tok}.kt"), None) is not None:
                        verifiable_here += counts
                    else:
                        add("known-bugs", "stale", "high",
                            f"{bug_id}: テストクラス '{tok}' が android/app/src/test に存在しない"
                            "（削除・リネームなら状態列も無防備側へ戻すこと）")
                elif "/" in tok and tok.endswith((".md", ".py", ".sh", ".kt")):
                    if (ROOT / tok).exists():
                        verifiable_here += counts
                    else:
                        add("known-bugs", "stale", "high",
                            f"{bug_id}: 参照 '{tok}' が存在しない")
                else:
                    add("known-bugs", "warn", "info",
                        f"{bug_id}: '{tok}' はどの照合パターンにも当たらない"
                        "（テストクラス名は `XxxTest`・機械チェックは `check_xxx`・参照はパスで書くこと）")

        # CI ゲートの主張（検知手段セルの素テキスト "CI: <Gradleタスク名>"）を workflow と突合する。
        # なぜコードスパンで書かせないか: Gradle タスク名は上のどの照合パターン（XxxTest / check_xxx /
        # パス）にも当たらず info を出すだけになるため、台帳側では素のテキストで書く規約にしてある。
        # なぜ存在照合まで要るか: ワークフローのステップは1行消せば消え、台帳は緑のまま「CI が守っている」と
        # 嘘をつく——このリポジトリが13日間踏んだ恒久 dead 化と同じ形。パスの実在確認だけでは足りないので
        # タスク名そのものを workflow 本文に対して照合する。
        for task in re.findall(r"CI:\s*([A-Za-z][\w:.]*)", cells[5]):
            wf = read_text(_CI_WORKFLOW)
            if wf is None:
                add("known-bugs", "stale", "high",
                    f"{bug_id}: CI ゲートを主張しているが {_CI_WORKFLOW} が読めない（移動・削除）")
            elif task not in wf:
                add("known-bugs", "stale", "high",
                    f"{bug_id}: CI ゲート '{task}' が {_CI_WORKFLOW} に無い"
                    "（ステップを外したなら状態列も無防備側へ戻すこと）")
            else:
                verifiable_here += 1

        if state in _REGISTRY_DEFENDED and verifiable_here == 0:
            add("known-bugs", "stale", "high",
                f"{bug_id}: 状態 '{state}' は検知ありを主張しているのに、"
                "検証可能な名指し（テストクラス名／check_xxx ／パス／lint: ／CI: タスク名）が1つも無い")

    if refs_seen == 0:
        add("known-bugs", "stale", "high",
            f"{rel} から名指しを1件も抽出できない（コードスパン記法が失われた＝照合が恒久 dead 化している）")


# ── 17. hook の出力経路照合（イベント × モデルに届く出力方法） ────────────
# **この表が本チェックの中核**。Claude Code の外部仕様なので推測で書かず、確定できたセルだけを
# 断定し、裏の取れていないセルは presumed に列挙して指摘の重み（high→info）を落とす。
#
# 確定の出典（すべてリポジトリ内の一次情報）:
#   - plain stdout がモデルのコンテキストへ入るのは UserPromptSubmit / UserPromptExpansion /
#     SessionStart のみ（公式仕様。task_diary #28 が引用し、PostToolUse で踏んだ実例つき）。
#   - additionalContext: PostToolUse（#28 実測）／PreToolUse（#28 追補・2026-07-07 の live probe）／
#     SessionStart（inject_branch_context.py が稼働）／SubagentStart（公式 doc ＋ 2026-07-19 実測＝
#     auto-memory `hook-subagent-start-injection`）。
#   - Stop は additionalContext を持たない＝`{"decision":"block","reason":…}` で返す
#     （#28／stop_guard_fabrication.py。test_stop_guard_fabrication.py の陽性コントロールが回帰固定）。
#   - PreToolUse の permission decision はホスト側でマージされる公式仕様（ADR 0008 の事実確認）。
#   - exit 2 の stderr が届くのは PreToolUse（guard 群のブロック実績）／PostToolUse
#     （remind_commit_plan.py・#28 記述）。**exit 0 の stderr はどのイベントでもデバッグログ止まり**。
#
# presumed に入れたセル＝本リポジトリに実配線も実測も無く公式記述の類推で埋めた推測。
# SubagentStop は行ごと推測（Stop と同系と仮定）。UserPromptExpansion は #28 の列挙以外は不明。
_EVENT_DELIVERY = {
    "UserPromptSubmit": {"stdout": True, "additionalContext": True, "decision": True, "exit2": True,
                         "presumed": {"additionalContext", "decision", "exit2"}},
    "UserPromptExpansion": {"stdout": True, "additionalContext": True, "decision": False, "exit2": False,
                            "presumed": {"additionalContext", "decision", "exit2"}},
    "SessionStart": {"stdout": True, "additionalContext": True, "decision": False, "exit2": False,
                     "presumed": {"decision", "exit2"}},
    "SubagentStart": {"stdout": False, "additionalContext": True, "decision": False, "exit2": False,
                      "presumed": {"stdout", "decision", "exit2"}},
    "PreToolUse": {"stdout": False, "additionalContext": True, "decision": True, "exit2": True,
                   "presumed": set()},
    "PostToolUse": {"stdout": False, "additionalContext": True, "decision": True, "exit2": True,
                    "presumed": {"decision"}},
    "Stop": {"stdout": False, "additionalContext": False, "decision": True, "exit2": True,
             "presumed": {"exit2"}},
    "SubagentStop": {"stdout": False, "additionalContext": False, "decision": True, "exit2": True,
                     "presumed": {"stdout", "additionalContext", "decision", "exit2"}},
}


def _hook_events_by_file():
    """settings の `hooks` ブロックから {hook.py: {イベント名,…}} を作る。

    項目3の `_registered_hooks()` と分ける理由: あちらは JSON 全文の正規表現で拾うため
    statusLine の `statusline.py`（フックではない）まで含む。出力経路はイベント別の性質なので、
    ここは hooks ブロックだけを構造的に辿る。
    """
    mapping = collections.defaultdict(set)
    for sf in (".claude/settings.json", ".claude/settings.local.json"):
        txt = read_text(sf)
        if not txt:
            continue
        try:
            cfg = json.loads(txt)
        except json.JSONDecodeError:
            continue  # settings 破損は項目3が露呈させる（重複報告しない）
        for event, groups in (cfg.get("hooks") or {}).items():
            for grp in groups or []:
                for h in (grp.get("hooks") or []):
                    for m in re.finditer(r"hooks/([\w.-]+\.py)", h.get("command", "") or ""):
                        mapping[m.group(1)].add(event)
    return mapping


def _dict_value(node, key):
    """ast.Dict からリテラルキー key の値ノードを取る（無ければ None）。"""
    if not isinstance(node, ast.Dict):
        return None
    for k, v in zip(node.keys, node.values):
        if isinstance(k, ast.Constant) and k.value == key:
            return v
    return None


def _classify_print(call):
    """print() 1件を (channel, ac_event, reason) に分類する。

    channel: "stdout"（素の標準出力）/ "stderr" / "additionalContext" / "decision" / None（判定不能）。
    ac_event: additionalContext を出す場合に埋め込まれた hookEventName リテラル（無ければ None）。
    """
    dest = "stdout"
    for kw in call.keywords:
        if kw.arg == "file":
            t = kw.value
            if isinstance(t, ast.Attribute) and isinstance(t.value, ast.Name) and t.value.id == "sys":
                dest = t.attr
            else:
                return None, None, "print(file=…) の出力先が静的に決まらない"
    if dest == "stderr":
        return "stderr", None, None
    if dest != "stdout":
        return None, None, f"print(file=sys.{dest}) を解釈できない"
    if not call.args:
        return "stdout", None, None  # print() の空行も素の stdout
    arg = call.args[0]
    if not (isinstance(arg, ast.Call) and isinstance(arg.func, ast.Attribute) and arg.func.attr == "dumps"):
        return "stdout", None, None  # 文字列・f-string・連結＝素の stdout
    if not arg.args or not isinstance(arg.args[0], ast.Dict):
        return None, None, "json.dumps(<非リテラル>) で出力形式を静的判定できない"
    payload = arg.args[0]
    hso = _dict_value(payload, "hookSpecificOutput")
    if hso is not None:
        if not isinstance(hso, ast.Dict):
            return None, None, "hookSpecificOutput がリテラル dict でない"
        ev = _dict_value(hso, "hookEventName")
        ev_name = ev.value if isinstance(ev, ast.Constant) and isinstance(ev.value, str) else None
        if _dict_value(hso, "additionalContext") is not None:
            return "additionalContext", ev_name, (
                None if ev_name else "hookEventName がリテラルでなく配線イベントと突合できない")
        if _dict_value(hso, "permissionDecision") is not None:
            return "decision", ev_name, None
        return None, None, "hookSpecificOutput に additionalContext も permissionDecision も無い"
    if _dict_value(payload, "decision") is not None:
        return "decision", None, None
    return None, None, "json.dumps した dict が additionalContext/decision のどちらでもない"


def _analyze_hook_output(src, filename):
    """hook ソースから「どの経路へ何を出しているか」を抽出する。構文エラーなら None。"""
    try:
        tree = ast.parse(src, filename=filename)
    except SyntaxError:
        return None  # 項目12（構文チェック）が high で露呈させるのでここでは黙る
    # except ブロック配下のノード集合。fail-open の診断ログ（意図的に届かない stderr）と
    # 本流の通告を区別するために使う。
    in_except = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.ExceptHandler):
            for d in ast.walk(node):
                in_except.add(id(d))
    res = {"channels": set(), "ac_events": set(), "exit2": False,
           "stderr_outside_except": False, "reasons": []}
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "print":
            ch, ev, reason = _classify_print(node)
            if reason:
                res["reasons"].append(f"L{node.lineno}: {reason}")
            if ch:
                res["channels"].add(ch)
                if ch == "stderr" and id(node) not in in_except:
                    res["stderr_outside_except"] = True
            if ev:
                res["ac_events"].add(ev)
        # exit 2（＝stderr をモデルへ届ける唯一の手段）を使う経路があるか。
        # bool は int の subclass なので True(==1) を 2 と誤認しないよう型で弾く。
        val = None
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
                and node.func.attr == "exit" and node.args:
            val = node.args[0]
        elif isinstance(node, ast.Return):
            val = node.value
        if isinstance(val, ast.Constant) and isinstance(val.value, int) \
                and not isinstance(val.value, bool) and val.value == 2:
            res["exit2"] = True
    return res


def check_hook_output_channel():
    """「配線イベント → モデルに届く出力経路 → コードが実際に使っている経路」を機械照合する。

    なぜ要るか: 既知バグレジストリで `hook-output-not-delivered` は**採録中の最多再発（7回）**
    でありながら検知手段が無かった。症状は「フックは動いているのに通告がモデルに一切届かない」で、
    機序は**イベントごとに届く出力先が違う（stdout / stderr / additionalContext）のを取り違える**こと。
    7件中5件は「素の stdout を PreToolUse/PostToolUse で出していた」＝この照合1本で機械化できる形だった。
    項目12（hook 動作点検）は構文と自己テストしか見ないので、経路の正しさは誰も見ていなかった。

    fail-open にしない工夫: 静的に判定できない出力（json.dumps(<変数>) 等）は黙って通さず
    「判定不能」として件数と理由を必ず出す。対象が0本になった場合（hooks ブロックの構造変更）は
    「この検査が恒久 dead 化した」とみなして high で落とす。
    """
    events_by_file = _hook_events_by_file()
    if not events_by_file:
        add("hook-output", "stale", "high",
            "settings の hooks ブロックから hook を1本も抽出できない"
            "（配線の構造が変わった＝出力経路検査が恒久 dead 化している）")
        return
    ok_n = bad_n = und_n = 0
    for name in sorted(events_by_file):
        events = events_by_file[name]
        src = read_text(f".claude/hooks/{name}")
        if src is None:
            und_n += 1  # 実ファイル欠落そのものは項目3が high で報告する
            add("hook-output", "warn", "info",
                f"{name}: 実ファイルが読めず出力経路を判定不能（配線は {', '.join(sorted(events))}）")
            continue
        res = _analyze_hook_output(src, name)
        if res is None:
            und_n += 1
            add("hook-output", "warn", "info", f"{name}: 構文エラーで出力経路を判定不能")
            continue
        known = {e for e in events if e in _EVENT_DELIVERY}
        violated = undecided = False
        for e in sorted(events - known):
            undecided = True
            add("hook-output", "warn", "info",
                f"{name}: イベント '{e}' が対応表に無い（新イベント＝表を一次情報で更新すること）")

        def _sev(channel):
            """確定セルだけを根拠にできるなら high、推測セルが混じるなら info へ落とす。"""
            return "high" if all(channel not in _EVENT_DELIVERY[e]["presumed"] for e in known) else "info"

        if known:
            # ① 素の stdout: 届くイベントが配線に1つも無ければ違反（7件中5件がこの形）。
            if "stdout" in res["channels"]:
                deliver = {e for e in known if _EVENT_DELIVERY[e]["stdout"]}
                if not deliver:
                    sev = _sev("stdout")
                    if sev == "high":
                        violated = True
                    else:
                        undecided = True
                    add("hook-output", "stale", sev,
                        f"{name}: 素の stdout へ出力しているが、配線イベント "
                        f"{', '.join(sorted(known))} は stdout をモデルへ届けない"
                        "（additionalContext か stderr+exit 2 へ移すこと・task_diary #28）")
                elif deliver != known:
                    undecided = True
                    add("hook-output", "warn", "info",
                        f"{name}: 素の stdout が {', '.join(sorted(deliver))} でのみ届き "
                        f"{', '.join(sorted(known - deliver))} では届かない（イベント分岐の確認が要る）")
            # ② additionalContext: 支持しないイベントだけに配線されていないか。
            if "additionalContext" in res["channels"]:
                deliver = {e for e in known if _EVENT_DELIVERY[e]["additionalContext"]}
                if not deliver:
                    sev = _sev("additionalContext")
                    if sev == "high":
                        violated = True
                    else:
                        undecided = True
                    add("hook-output", "stale", sev,
                        f"{name}: additionalContext を出しているが、配線イベント "
                        f"{', '.join(sorted(known))} はこれを受け取らない（Stop 系なら decision/reason で返す）")
            # ③ hookEventName リテラルと配線イベントの突合（雛形コピーの取り残し）。
            #    ホストはこの値で注入先を解決するため、配線外の値だと無音で捨てられる。
            for ev in sorted(res["ac_events"]):
                if ev not in known:
                    violated = True
                    add("hook-output", "stale", "high",
                        f"{name}: hookEventName='{ev}' が配線イベント {', '.join(sorted(known))} に無い"
                        "（雛形コピーの取り残し＝注入が無音で捨てられる）")
            # ④ decision JSON を受け取らないイベントだけに配線されていないか。
            if "decision" in res["channels"]:
                deliver = {e for e in known if _EVENT_DELIVERY[e]["decision"]}
                if not deliver:
                    sev = _sev("decision")
                    if sev == "high":
                        violated = True
                    else:
                        undecided = True
                    add("hook-output", "stale", sev,
                        f"{name}: decision JSON を出しているが、配線イベント "
                        f"{', '.join(sorted(known))} はこれを解釈しない")
            # ⑤ stderr は exit 2 とセットでのみ届く。exit 2 の経路が無い stderr は
            #    どのイベントでもデバッグログ止まり＝本流の通告なら違反、except 内の
            #    fail-open 診断ログなら設計どおり（info で可視化だけする）。
            if "stderr" in res["channels"] and not res["exit2"]:
                if res["stderr_outside_except"]:
                    violated = True
                    add("hook-output", "stale", "high",
                        f"{name}: stderr へ通告しているが exit 2 の経路が無い"
                        "（exit 0 の stderr はデバッグログ止まりでモデルに届かない）")
                else:
                    undecided = True
                    add("hook-output", "warn", "info",
                        f"{name}: except 内の stderr のみ（exit 2 なし）＝デバッグログ止まり。"
                        "モデルに知らせるべき状態遷移ならこれでは届かない")
            elif res["exit2"]:
                deliver = {e for e in known if _EVENT_DELIVERY[e]["exit2"]}
                if not deliver:
                    sev = _sev("exit2")
                    if sev == "high":
                        violated = True
                    else:
                        undecided = True
                    add("hook-output", "stale", sev,
                        f"{name}: exit 2 を使うが、配線イベント {', '.join(sorted(known))} は"
                        "exit 2 の stderr をモデルへ渡さない")
        for r in res["reasons"]:
            undecided = True
            add("hook-output", "warn", "info", f"{name}: 出力形式を静的判定できない — {r}")
        if violated:
            bad_n += 1
        elif undecided:
            und_n += 1
        else:
            ok_n += 1
    # 常に1行出す: 「0件だから健全」と「検査自体が空振り」を外形で区別できるようにする
    # （この検査が沈黙したら本末転倒＝task_diary #44 の無症状故障対策）。
    add("hook-output", "ok" if not bad_n else "stale", "info",
        f"出力経路検査: 対象 {len(events_by_file)} 本 = 適合 {ok_n} / 違反 {bad_n} / 判定不能 {und_n}")


# ── 18. 撤去フックの残存参照（dead consumer）照合 ─────────────────────────
# CLAUDE.md「フックの撤去は『参照する側』まで含めて1セット」の機械化。手順（撤去したフック名で
# リポジトリ全体を grep し、他フックのロジック・コメント・docstring・.gitignore・skill の記述に
# 残骸が無いことを確認する）をそのまま検査へ移す。
#
# なぜ「生成物」まで追うか: 2026-07-12 の実例（既知バグ `removed-hook-leaves-dead-consumer`）で
# 恒久 dead 化したのは**フック名の参照ではなく生成物の参照**だった——mark_kotlin_tests_passed.py を
# 撤去した後、センチネル `.kotlin_tests_passed` の mtime を見る判定だけが残り、生成者が居ないので
# 「必ず古い」＝判定が恒久 False のまま13日間テストは緑で通り続けた。名前だけ照合しても
# あの事故は捕まらないので、**撤去されたフックの旧ソースから生成物リテラルを取り出して**
# 現ツリーの参照と突合する（旧ソースが「何を作っていたか」の一次情報＝推測が要らない）。
#
# 撤去の判定に履歴を使う理由: 「いま .claude/hooks/ に無い名前が他所から名指しされている」だけなら
# 現在の状態で足りるが、それでは生成物の名前を知る術が無い（生成物は名前から導けない）。
# `git log --diff-filter=D` は「撤去された事実」と「旧ソース」を同時にくれる唯一の一次情報。
_ARTIFACT_DENY = {".claude", ".git", ".github", ".gitignore", ".vscode", ".idea", ".gradle", ".env"}
_ARTIFACT_EXT = (".json", ".jsonl", ".txt", ".log", ".flag", ".lock", ".state")
# 生成物を「書いている」形の粗い指標（生成者が別ファイルへ引き継がれた場合の降格判定に使う）。
_WRITE_HINT_RE = re.compile(r"open\s*\(|write_text|writelines|\.write\(|touch|mkdir|dump\(|>>")


def _looks_like_artifact(s):
    """文字列リテラルが「フックの生成物ファイル名」らしいか。

    偽陽性が怖いのは名前が汎用すぎるとき（'.claude' のような語は現ツリー中どこにでも当たる）。
    ドット始まり（センチネル）か既知の成果物拡張子に限り、リポジトリの構造ディレクトリは除外する。
    """
    if not s or len(s) < 6 or any(c.isspace() for c in s) or "." not in s:
        return False
    if s in _ARTIFACT_DENY or s.endswith((".py", ".md", ".kt", ".kts", ".gradle", ".java")):
        return False
    return s.startswith(".") or s.endswith(_ARTIFACT_EXT)


def _removed_hooks():
    """git 履歴から「削除され、いま実在しない」フック → 直近の削除 (sha, path)。

    --no-renames を付けるのは、改名を「旧名の撤去」として扱うため（旧名の参照が残っていれば
    それも dead consumer）。取得できなければ None（環境要因＝呼び出し側で info 扱い）。
    """
    out = _git(["log", "--diff-filter=D", "--no-renames", "--name-only",
                "--pretty=format:%H", "--", ".claude/hooks"])
    if not out:
        return None
    actual = _actual_hooks()
    removed, sha = {}, None
    for line in out.splitlines():
        line = line.strip()
        if re.fullmatch(r"[0-9a-f]{40}", line):
            sha = line
        elif line.endswith(".py") and sha:
            name = Path(line).name
            # 直近の削除だけ残す（log は新しい順＝先に入った方が新しい）。再追加された名前は対象外。
            if name not in actual and name not in removed:
                removed[name] = (sha, line)
    return removed


def _tracked_basenames():
    """git 追跡下にあるファイルの basename 集合（生成物候補の足切りに使う）。"""
    return {Path(p).name for p in _git(["ls-files"]).splitlines() if p}


def _removed_hook_artifacts(sha, path, tracked):
    """撤去直前のソースから生成物リテラルを取り出す（読めなければ空集合）。

    追跡下に実在する名前（settings.json 等）を落とすのはなぜか: 撤去フックが**読んでいただけ**の
    共有ファイルまで「生成物」と見なすと、リポジトリ中の全言及が鳴って検知が使い物にならない
    （実測: settings.json だけで 20 件の偽陽性）。生成物として意味があるのは「生成者が消えた今、
    誰も作らないファイル」＝リポジトリに実体を持たないものだけ。
    """
    src = _git(["show", f"{sha}^:{path}"])
    if not src:
        return set()
    try:
        tree = ast.parse(src)
        lits = {n.value for n in ast.walk(tree)
                if isinstance(n, ast.Constant) and isinstance(n.value, str)}
    except SyntaxError:
        lits = set(re.findall(r"[\"']([^\"'\n]+)[\"']", src))
    return {s for s in lits if _looks_like_artifact(s) and Path(s).name not in tracked}


def _py_prose_spans(src):
    """Python ソースの「散文」領域（コメント／docstring）を {行: [(開始列, 終了列), …]} で返す。

    行単位でなく列まで見るのは、`code  # …撤去済みフック名…` の行末コメントで実コードの参照まで
    散文扱いに落ちるのを防ぐため（見逃しは偽陰性＝この検査の存在意義を削る）。
    """
    spans = collections.defaultdict(list)
    try:
        for t in tokenize.generate_tokens(io.StringIO(src).readline):
            if t.type == tokenize.COMMENT:
                spans[t.start[0]].append((t.start[1], 10 ** 6))
    except (tokenize.TokenError, SyntaxError, IndentationError, ValueError):
        return None
    try:
        tree = ast.parse(src)
    except SyntaxError:
        return spans
    for node in ast.walk(tree):
        if isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant) \
                and isinstance(node.value.value, str):
            for ln in range(node.lineno, (node.end_lineno or node.lineno) + 1):
                spans[ln].append((0, 10 ** 6))
    return spans


def _classify_reference(rel, lineno, line, col, term, prose_cache):
    """参照1件を ('live'|'prose', 位置の説明) に分類する。live＝機械が実際に食っている位置。"""
    if rel.endswith(".py"):
        if rel not in prose_cache:
            src = read_text(rel)
            prose_cache[rel] = _py_prose_spans(src) if src is not None else None
        spans = prose_cache[rel]
        if spans is None:
            return "live", "Python（構文解析できず散文判定不能＝安全側で live）"
        if any(a <= col < b for a, b in spans.get(lineno, ())):
            return "prose", "コメント／docstring"
        if Path(rel).name.startswith("test_"):
            # 自己テストは歴史的ペイロードを固定文言で再現するのが仕事＝残っていて正しい。
            return "prose", "自己テストの固定文言"
        return "live", "Python の実コード"
    if Path(rel).name == ".gitignore":
        return ("prose", "コメント") if line.lstrip().startswith("#") else ("live", ".gitignore の実エントリ")
    if rel.startswith(".claude/settings") and rel.endswith(".json"):
        return "live", "settings の配線（項目3も別角度で報告する）"
    if rel.endswith(".md"):
        # live 扱いは「その名前をインタプリタが実行する形」に限る。当初は行内に python/sh と .py が
        # 在れば live としたが、撤去フックを語る文章は必ず両方を含む（実測で4件すべて偽陽性）。
        # 判定は名前そのものが実行対象の位置に来ているかで行う。
        cmd = re.search(r"(?:python3?|bash|sh)\s+[^\s`'\"]*" + re.escape(term), line)
        return ("live", "文書中の実行コマンド") if cmd else ("prose", "文書中の記述")
    return "prose", "分類対象外の位置（人手で確認すること）"


def check_removed_hook_references():
    """撤去済みフックの名前・生成物が、現ツリーの「機械が食う位置」に残っていないか照合する。

    報告の向き: 高＝live（settings の配線・Python の実コード・.gitignore の実エントリ・文書中の
    実行コマンド）。info＝prose（コメント／docstring／文書の記述／自己テストの固定文言）。
    散文の言及は歴史の記述として正当なので落とさないが、CLAUDE.md の手順が「コメント・docstring も
    確認対象」と書いている以上、件数は必ず表に出す。

    fail-open にしない工夫: 履歴から撤去フックを1本も抽出できなければ「パス指定が実態と合わなく
    なった＝この照合が恒久 dead 化」とみなして落とす。git 自体が使えない場合だけ info でスキップ。
    """
    removed = _removed_hooks()
    if removed is None:
        add("removed-hook", "warn", "info", "git log を実行できず撤去フック照合をスキップ")
        return
    if not removed:
        add("removed-hook", "stale", "high",
            "git 履歴から撤去済みフックを1本も抽出できない"
            "（.claude/hooks のパス指定が実態と合っていない＝この照合が恒久 dead 化している）")
        return

    # 検索語 → (種別, 由来フック)。名前は拡張子込み／拡張子抜きの両方を拾う（CLAUDE.md の手順どおり）。
    terms = {}
    tracked = _tracked_basenames()
    for name, (sha, path) in removed.items():
        stem = name[:-3]
        terms[stem] = ("名前", name)  # stem で grep すれば "<stem>.py" 表記も同時に当たる
        for art in _removed_hook_artifacts(sha, path, tracked):
            terms.setdefault(art, ("生成物", name))

    args = ["grep", "-n", "-I", "-F"]
    for t in terms:
        args += ["-e", t]
    hits_raw = _git(args)  # 0 件なら空文字（git grep は無ヒットで exit 1＝_git は空を返す）

    prose_cache, live_n, prose_n = {}, 0, 0
    per_term = collections.defaultdict(list)
    for hit in hits_raw.splitlines():
        m = re.match(r"^([^:]+):(\d+):(.*)$", hit)
        if not m:
            continue
        rel, lineno, line = m.group(1), int(m.group(2)), m.group(3)
        if rel == "docs/known-bugs-registry.md":
            continue  # 台帳は「この事故が在った」と書くのが仕事＝自己言及で毎回鳴らせない
        for term, (kind, origin) in terms.items():
            col = line.find(term)
            if col < 0:
                continue
            per_term[term].append((kind, origin, rel, lineno, line, col))

    for term, occurrences in sorted(per_term.items()):
        kind, origin = occurrences[0][0], occurrences[0][1]
        classified = []
        for _k, _o, rel, lineno, line, col in occurrences:
            cls, where = _classify_reference(rel, lineno, line, col, term, prose_cache)
            classified.append((cls, where, rel, lineno, line))
        # 生成物が現存ファイルの live な書き込み側にも現れるなら、生成者が引き継がれている＝dead ではない。
        producer_alive = kind == "生成物" and any(
            cls == "live" and _WRITE_HINT_RE.search(line) for cls, _w, _r, _l, line in classified)
        # live は1件ずつ高で挙げる（直すべき対象）。散文の言及は語ごとに1行へ畳む——
        # 撤去の経緯を書いた文書・コメントは正当に何十件も在り、1件1行にすると
        # 情報欄が撤去フックの話題で埋まって他の指摘が読まれなくなる（実測 30 行）。
        # 件数と代表箇所は残す＝CLAUDE.md の手順（コメント・docstring まで確認）を人が辿れる。
        prose_hits = []
        for cls, where, rel, lineno, _line in classified:
            if cls == "live" and not producer_alive:
                live_n += 1
                add("removed-hook", "stale", "high",
                    f"{rel}:{lineno} が撤去済みフック {origin} の{kind} '{term}' を参照（{where}）。"
                    "生成者が居ない参照は恒久 dead 化する＝参照ごと消すか生成者を戻すこと")
            else:
                prose_n += 1
                prose_hits.append(f"{rel}:{lineno}")
        if prose_hits:
            head = "／".join(prose_hits[:3]) + (f" ほか{len(prose_hits) - 3}件" if len(prose_hits) > 3 else "")
            note = "（生成者は別ファイルへ引き継がれている模様）" if producer_alive else ""
            add("removed-hook", "warn", "info",
                f"撤去済み {origin} の{kind} '{term}' への言及 {len(prose_hits)} 件"
                f"（コメント／文書の記述＝経緯の記録として正当）{note}: {head}")
    # 常に1行出す: 「0件だから健全」と「検査が空振り」を外形で区別する（項目17 と同じ理由）。
    arts = sum(1 for k, _o in terms.values() if k == "生成物")
    add("removed-hook", "ok" if not live_n else "stale", "info",
        f"撤去フック照合: 履歴の撤去 {len(removed)} 本 / 生成物リテラル {arts} 件 / "
        f"現ツリーの残存参照 {live_n + prose_n} 件（live {live_n} / 散文 {prose_n}）")


# ── 項目一覧（この表が正本＝`--list` で出力する） ──────────────────────────
# なぜ説明文をここへ同居させるか: 以前は SKILL.md 側に項目表を複製し、件数の一致を
# check_self_item_table で見張っていた。しかしそれは二重管理を前提にした対症療法で、
# 件数しか照合できず内容のズレは素通しだった（2026-07-12 に check_size_budgets を足した際、
# SKILL.md の列挙が 13 項目のまま 13 日間放置されたのが発端）。複製を無くせば腐る対象自体が
# 消えるため、説明を CHECKS に同居させ SKILL.md からは `--list` を案内するだけにした。
# 新しいチェックを足すときは関数と説明をこの表に1行で追加する（タプルなので説明の書き忘れは
# 実行時に必ず落ちる＝黙って列挙が欠けることがない）。
# ── 19. 抑止則の自己テスト ───────────────────────────────────────────────
def check_suppression_selftest():
    """項目6 の抑止則が、意図した形だけを抑止し・それ以外は素通しすることを毎回確かめる。

    なぜ検査器の中に自己テストを持つか: 抑止則は「出さない」方向の仕掛けなので、壊れても
    出力が静かになるだけで気づけない（既知バグ `stale-check-false-positive` そのもの）。
    語彙を1語足した／継続行の条件を触った、で黙って検出力が消えるのを止める。
    外部フィクスチャを置くと本体と一緒に腐るので、期待値はここにインラインで持つ。
    """
    # (本文, 抑止されるべきか) — 参照は常に1行目の末尾に置く。
    cases = [
        ("`a.md`\n", False),                                  # 素の参照＝抑止しない
        ("`a.md` は撤去済み\n", True),                         # (1) 同一行
        ("`a.md`\n  ※現存しない。\n", True),                   # (2) 直後の注記行
        ("`a.md`\n\n  ※現存しない。\n", False),                # 空行で切れる＝別の段落
        ("`a.md`\n## 次の節\n※現存しない。\n", False),          # 見出しで切れる
        ("`a.md`\n- 別項目は撤去済み\n", False),                # 新しい箇条書きで切れる
        ("# T\n> 本書の参照は非収蔵。\n\n## 節\n`a.md`\n", True),  # (3) 前置き引用＝文書全体
        ("# T\n## 節1\n> ここは撤去済み。\n`a.md`\n", True),      # (3) 節内引用＝節に効く
        ("# T\n## 節1\n> ここは撤去済み。\n## 節2\n`a.md`\n", False),  # 次の見出しで切れる
        ("`a.md` を移設する予定\n", False),                     # 素の「移設」は効かない
    ]
    for i, (text, want) in enumerate(cases):
        lines = text.splitlines()
        got = _gone_scope(lines)[next(j for j, l in enumerate(lines) if "`a.md`" in l)]
        if got != want:
            add("selftest", "stale", "high",
                f"抑止則が期待と違う（ケース{i}: 期待={'抑止' if want else '素通し'} / 実際={'抑止' if got else '素通し'}）")
    # (4) 名指し宣言は文書全体へ効き、名前が違えば効かない。
    named = _gone_basenames(["冒頭: 本文中の `gone.py` は現存しない。"])
    if "gone.py" not in named:
        add("selftest", "stale", "high", "名指しの廃止宣言を拾えていない（項目6 の抑止(4)）")
    if "other.py" in named:
        add("selftest", "stale", "high", "名指ししていないファイル名まで抑止対象にしている（抑止過剰）")
    # 実行時生成 HTML とメタ変数記法は、検出の対象外であり続けること。
    for ref in ("chap_N.html", "index.html"):
        if _mock_ref_exists(ref, "docs/knowledge/README.md") is not None:
            add("selftest", "stale", "high", f"実行時生成 HTML '{ref}' をモック実在チェックに掛けている")
    if not _PLACEHOLDER_RE.search("NNNN-kebab-case.md"):
        add("selftest", "stale", "high", "採番テンプレート記法をメタ変数として除外できていない")


CHECKS = [
    (check_versions, "版数照合（CLAUDE.md ↔ gradle: minSdk / targetSdk）"),
    (check_db, "DB整合（AppDatabase.kt の version ↔ schemas 最大 ↔ MIGRATION 連番 ↔ db-migration の履歴表）"),
    (check_hooks_registration, "hook 双方向照合（settings 参照 ↔ 実ファイル: 壊れた参照／未登録の死hook）"),
    (check_hook_git_tracked, "hook の git 追跡（実ファイル ↔ git ls-files: コミット漏れ）"),
    (check_conflict_markers, "コンフリクトマーカー残存"),
    (check_referenced_files, "参照ファイルの実在（CLAUDE/STATUS/handover・skill・docs/**・.claude/plans 直下が名指しする .md/.py/.js/.sh/.kt と、design-candidates のモック .html。『撤去済み』等の断り書きが同一行・直後の注記行・前置き引用ブロック・冒頭の名指し宣言のいずれかに在れば対象外）"),
    (check_test_commands, "テストコマンドの一貫性"),
    (check_gradlew_path, "gradlew パス健全性（build skill）"),
    (check_skill_frontmatter, "skill frontmatter 妥当性（name ↔ ディレクトリ名）"),
    (check_plans_references, "plans 参照の実在（`.claude/plans/*.md` を名指す参照＝項目6 が参照側で除外しているのと、task_diary.md を発信元に含むのが差分）"),
    (check_permission_paths, "permissions パス実在（settings の allow/deny が指すパスの消滅＝死 permission）"),
    (check_hook_smoke, "hook 動作点検（全 hook の構文チェック＋test_*.py 自己テストの実行）"),
    (check_diary_id_unique, "task_diary エントリID の一意性（#N 見出しの重複採番検知・自動リネームはしない）"),
    (check_size_budgets, "台帳のサイズ番人（STATUS=現況のみ・目安60行／handover=やることのみ）"),
    (check_delegation_meter, "委譲ターン計測フックの整合（count_delegation_turns.py の PostToolUse/SubagentStop 両配線・記録先・通告間隔）"),
    (check_known_bugs_registry, "既知バグレジストリ（L4）の名指し実在照合（docs/known-bugs-registry.md のテストクラス名・check_xxx・参照パスが実在するか／検知ありを主張する行に名指しがあるか）"),
    (check_hook_output_channel, "hook の出力経路照合（配線イベント × モデルに届く経路: 素の stdout 不達・hookEventName の取り残し・exit 2 無しの stderr。判定不能も件数を出す）"),
    (check_suppression_selftest, "抑止則の自己テスト（項目6 の『もう無い』注記判定＝同一行/直後の注記行/引用ブロック/名指し宣言が、意図した形だけを抑止し段落・見出し境界を越えないこと）"),
    (check_removed_hook_references, "撤去フックの残存参照（git 履歴の撤去フック名＋旧ソースが作っていた生成物を現ツリーと突合。settings/実コード/.gitignore/実行コマンドは高・コメントや文書の言及は info）"),
]


def main():
    # --list: 項目一覧の照会。SKILL.md に列挙を複製させないための出口（上の CHECKS 冒頭コメント参照）。
    if "--list" in sys.argv:
        print(f"=== stale-check の機械チェック項目（全 {len(CHECKS)} 種）===")
        for i, (fn, label) in enumerate(CHECKS, 1):
            print(f"{i:2d}. {label}  [{fn.__name__}]")
        return

    as_json = "--json" in sys.argv
    full = "--full" in sys.argv
    state = load_state()
    changed = None if full else changed_since(state.get("last_checked_commit"))

    for c, _label in CHECKS:
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
