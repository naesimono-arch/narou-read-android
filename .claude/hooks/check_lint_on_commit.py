#!/usr/bin/env python3
"""
PreToolUse hook: git commit 前に Android Lint を実行し、
エラー数がベースラインより増えていたらコミットをブロックする。
対象ツール: Bash (git commit コマンドのみ)

警告増加は通過させるが、エラー増加はブロックする。
理由: 警告はレガシーが多く false positive があるが、エラーは実際の問題を示すため。
"""
import io
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# 実行コマンドとしての `git commit` を検知する正規表現。
# 【重要】guard_commit_branch.py と同一定義（test_hooks.py が一致を回帰固定）。
# なぜ緩い \bgit\s+commit\b から置き換えたか: クォート内言及（echo/grep 等）にも誤発火し、
# 本フックは Lint 実行（最大5分）まで走るため誤発火コストがとりわけ大きい。
# なぜ stdin 読込より前に定義するか: test_hooks.py が実ファイルを exec して定数を回収する設計のため。
COMMIT_CMD_RE = re.compile(
    r"(?:^|\n|&&|\|\||[;|&])\s*git"
    r"(?:\s+(?:-[Cc]\s+\S+|-{1,2}[\w.-]+(?:=\S+)?))*"
    r"\s+commit\b"
)

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

if tool_name != "Bash":
    sys.exit(0)

command = tool_input.get("command", "")
if not COMMIT_CMD_RE.search(command):
    sys.exit(0)

# Kotlin/Java/XMLファイルがステージされているときだけ実行（高コストなので限定）
try:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only"],
        capture_output=True, text=True, timeout=10
    )
    staged = result.stdout.strip().splitlines()
except Exception:
    sys.exit(0)

ANDROID_EXTS = {".kt", ".java", ".xml"}
android_staged = [f for f in staged if os.path.splitext(f)[1] in ANDROID_EXTS]
if not android_staged:
    sys.exit(0)

# ── Lint 実行 ──
# __file__ は <root>/.claude/hooks/check_lint_on_commit.py ＝ルートまで dirname 3回（hooks → .claude → root）。
# 旧実装は2回で PROJECT_DIR が <root>/.claude を指し、存在しない cwd への subprocess 起動が
# OSError → fail-open となり「全 OS で一度も Lint が走らない」サイレント無効化だった（2026-07-07 e2e で顕在化）。
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ANDROID_DIR = os.path.join(PROJECT_DIR, "android")
REPORT_XML = os.path.join(ANDROID_DIR, "app", "build", "reports", "lint-results-debug.xml")
BASELINE_FILE = os.path.join(PROJECT_DIR, ".claude", "lint_baseline.json")

print("[Android Lint] Kotlin/XMLファイルがステージされているため Lint を実行中...")
# なぜ OS／ファイルシステムで起動方式を分けるか:
# - Windows: Unix シェルスクリプト ./gradlew は subprocess から直接起動できず
#   WinError 193（FileNotFoundError で捕捉不能＝未捕捉クラッシュだった）→ gradlew.bat で解決。
# - WSL/Linux: 旧実装の "./gradlew" は (a) gradlew が CRLF 改行で実行不能、(b) 非対話シェルは
#   .bashrc 非ロードで java が PATH に無い、の二重理由で常に OSError → サイレントスキップとなり、
#   「Linux が正本」の現環境で Lint ゲートが一度も機能していなかった（2026-07-07 顕在化）。
#   CLAUDE.md の gw と同じく「JAVA_HOME フルパスの java でラッパー jar を直接起動」する。
#   ただし /mnt/*（drvfs）上は AAPT2 が EPERM で落ち、init-script 退避だとレポートも ext4 側へ
#   出て解析不能のため、実行せず「明示スキップ」に倒す（サイレント→文書化されたスキップへ）。
if os.name == "nt":
    lint_cmd = ["gradlew.bat", "lintDebug", "-q", "--no-daemon"]
    lint_env = None
else:
    # ドライブレター1文字（/mnt/c/ 等）に限定して drvfs と判定する。/mnt/data のような
    # ext4 マウントまで startswith("/mnt/") で巻き込むとゲートを不必要に無効化するため。
    if re.match(r"/mnt/[a-z]/", PROJECT_DIR):
        print("[Android Lint] drvfs(/mnt/<drive>/) 上のリポジトリでは Lint を実行できません"
              "（AAPT2 EPERM・既知）。スキップします。ext4 上の worktree ではゲートが有効です。")
        sys.exit(0)
    java_home = os.environ.get("JAVA_HOME") or os.path.expanduser("~/opt/jdk-17")
    java_bin = os.path.join(java_home, "bin", "java")
    if not os.path.exists(java_bin):
        print(f"[Android Lint] java が見つかりません（{java_bin}）。スキップします。")
        sys.exit(0)
    lint_env = {
        **os.environ,
        "JAVA_HOME": java_home,
        "ANDROID_HOME": os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk")),
    }
    lint_cmd = [
        java_bin, "-classpath", "gradle/wrapper/gradle-wrapper.jar",
        "org.gradle.wrapper.GradleWrapperMain",
        "lintDebug", "-q", "--no-daemon", "--console=plain",
    ]
try:
    subprocess.run(
        lint_cmd,
        cwd=ANDROID_DIR,
        timeout=300,
        check=False,
        env=lint_env,
    )
except subprocess.TimeoutExpired:
    print("[Android Lint] タイムアウト（5分）。スキップします。")
    sys.exit(0)
except OSError as e:
    # FileNotFoundError / WinError193 等いずれも Lint をスキップ（コミットは妨げない）。
    # ここで sys.exit(2) するとビルド未整備の端末でコミット不能になるため握り潰す。
    print(f"[Android Lint] gradlew を起動できません（{e}）。スキップします。")
    sys.exit(0)

# ── レポート解析 ──
if not os.path.exists(REPORT_XML):
    print(f"[Android Lint] レポートが見つかりません: {REPORT_XML}")
    sys.exit(0)

try:
    tree = ET.parse(REPORT_XML)
    root = tree.getroot()
    issues = root.findall("issue")
    errors = sum(1 for i in issues if i.get("severity") == "Error")
    warnings = sum(1 for i in issues if i.get("severity") == "Warning")
    total = errors + warnings
except Exception as e:
    print(f"[Android Lint] レポート解析失敗: {e}")
    sys.exit(0)

# ── ベースラインと比較 ──
baseline: dict = {}
if os.path.exists(BASELINE_FILE):
    try:
        with open(BASELINE_FILE, encoding="utf-8") as f:
            baseline = json.load(f)
    except Exception:
        pass

prev_errors = baseline.get("errors", errors)  # 初回はエラーなし扱いにしない
prev_total = baseline.get("total", total)
first_run = not baseline  # ベースラインファイルが存在しなかった場合

print(
    f"[Android Lint] Errors: {errors} (前回: {prev_errors})  "
    f"Warnings: {warnings}  Total: {total} (前回: {prev_total})"
)

if first_run:
    print("[Android Lint] 初回実行のためベースラインを記録しました。次回から回帰を検知します。")
elif errors > prev_errors:
    diff = errors - prev_errors
    # なぜ stderr か: PreToolUse の exit 2 でモデルに届くのは stderr のみ（task_diary #28）。
    # stdout だと「理由の無いブロック」になり、何を直せばよいか伝わらない。
    print(
        f"[Android Lint] エラーが {diff} 件増加しました（{prev_errors}→{errors}）。コミットをブロックします。\n"
        "詳細: android/app/build/reports/lint-results-debug.html",
        file=sys.stderr,
    )
    sys.exit(2)
elif total > prev_total:
    diff = total - prev_total
    print(f"[Android Lint] 警告が {diff} 件増加しました（エラーなし）。続行しますが確認を推奨します。")

# ベースラインを更新（改善または初回）
try:
    with open(BASELINE_FILE, "w", encoding="utf-8") as f:
        json.dump(
            {"errors": errors, "warnings": warnings, "total": total},
            f,
            ensure_ascii=False,
            indent=2,
        )
except Exception:
    pass

sys.exit(0)
