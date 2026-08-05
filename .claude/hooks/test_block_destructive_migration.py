#!/usr/bin/env python3
"""block_destructive_migration.py を stdin 経由で直接叩くテストベクタ。
期待値: BLOCK=exit 2 / ALLOW=exit 0"""
import json, os, subprocess, sys

# worktree/canonical のどちらからでも走るよう自ファイル基準で解決（絶対パス焼き込み禁止）
HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "block_destructive_migration.py")
T = "fallbackTo" + "DestructiveMigration"

def bash(cmd):
    return {"tool_name": "Bash", "tool_input": {"command": cmd}}

CASES = [
    # --- 従来の陽性コントロール（現行挙動の維持確認） ---
    ("BLOCK", "pos-plain-append", bash("echo '%s()' >> app/src/main/java/Db.kt" % T)),
    ("BLOCK", "pos-sed-i", bash("sed -i 's/x/%s()/' android/app/src/main/java/Db.kt" % T)),
    ("BLOCK", "pos-edit-kt", {"tool_name": "Edit", "tool_input": {"file_path": "/x/Db.kt", "new_string": T + "()"}}),
    # --- すり抜け型1: env-prefix ---
    ("BLOCK", "bypass1a-env-hides-ext", bash("EXT=kt sh -c 'echo \"%s()\" >> DbModule.$EXT'" % T)),
    ("BLOCK", "bypass1b-env-splits-target", bash("A=fallbackTo B=DestructiveMigration sh -c 'echo $A$B\"()\" >> Db.kt'")),
    # --- すり抜け型2: コマンド置換 $(…) 包み ---
    ("BLOCK", "bypass2a-printf-in-subst", bash("echo \"$(printf 'fallbackTo%s' 'DestructiveMigration')()\" >> app/src/main/java/Db.kt")),
    ("BLOCK", "bypass2b-quote-concat", bash("echo 'fallbackTo''DestructiveMigration()' >> Db.kt")),
    # --- 正常系（誤ブロックゼロ確認・代表） ---
    ("ALLOW", "ok-git-status", bash("git status && git log --oneline -5")),
    ("ALLOW", "ok-git-commit", bash("git commit -m 'fix: 章遷移の栞位置を修正'")),
    ("ALLOW", "ok-gradle", bash("cd android && java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest")),
    ("ALLOW", "ok-adb", bash("adb shell dumpsys activity top")),
    ("ALLOW", "ok-ls", bash("ls -la app/src/main/java/com/novelreader/")),
    ("ALLOW", "ok-echo-log", bash("echo done >> /tmp/progress.log")),
    ("ALLOW", "ok-grep-target-readonly", bash("grep -rn %s android/app/src --include='*.kt'" % T)),
    ("ALLOW", "ok-sed-non-kt", bash("sed -i 's/old/new/' README.md")),
    ("ALLOW", "ok-write-non-kt", {"tool_name": "Write", "tool_input": {"file_path": "/x/notes.md", "content": T}}),
]

fail = 0
for expect, name, payload in CASES:
    p = subprocess.run([sys.executable, HOOK], input=json.dumps(payload), capture_output=True, text=True)
    got = "BLOCK" if p.returncode == 2 else "ALLOW" if p.returncode == 0 else "ERR(%d)" % p.returncode
    ok = got == expect
    fail += 0 if ok else 1
    print("%-4s %-28s expect=%-5s got=%-5s %s" % ("PASS" if ok else "FAIL", name, expect, got, p.stderr.splitlines()[0] if (p.stderr and not ok) else ""))

print("\n%d/%d passed" % (len(CASES) - fail, len(CASES)))
sys.exit(1 if fail else 0)
