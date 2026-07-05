#!/usr/bin/env python3
"""agy(Antigravity) 用 PreToolUse ガード — AGENTS.md の禁止事項を機械的に強制する二次防衛線。

なぜ: AGENTS.md の禁止（git 書き込み系・実機 adb・connectedAndroidTest）は
「お願いベース」の規約で、モデルが忘れる/誤解するリスクが残る。PreToolUse hook なら
run_command の実行前に決定論的に deny でき、監督(Claude Code)のレビュー前に
リポジトリや実機の状態が壊れる事故を塞げる（Claude Code 側 guard_commit_branch.py と同じ思想）。

設計:
- キー名の揺れに強いよう、toolCall.args 内の「全文字列値」を再帰的に集めて検査する
  （公式 docs の例は args.CommandLine だが、実装差異・改名に備える）。
- 判定不能・例外時は allow に倒す(fail-open)。ここは二次防衛線であり（一次は
  AGENTS.md 規約＋監督レビュー）、hook の不具合で全委譲が止まる方が実害が大きいため。
"""
import json
import re
import sys

FORBIDDEN = [
    # (正規表現, deny 理由 — agy に返り、代替行動を促す文面にする)
    (r"(^|[;&|(]\s*|\s)git(\.exe)?\s+(commit|push|reset|merge|rebase|revert|clean|restore|switch|checkout|stash|cherry-pick|branch\s+-[DdMm]|tag\s+\S)",
     "git の履歴・ワークツリー操作は監督(Claude Code)が行う。ファイル編集は編集ツールで直接行い、"
     "バージョン管理には触れないこと（現在ブランチの確認は `git branch --show-current` が使える）"),
    (r"connected(Debug|Release)?AndroidTest",
     "connectedAndroidTest は実機の蔵書DBを破壊する禁忌。単体テストは testDebugUnitTest のみ実行してよい"),
    (r"(^|[;&|(]\s*|\s|/)adb(\.exe)?\s",
     "実機(adb)操作は監督(Claude Code)が行う。必要なら最終報告で監督に依頼せよ"),
    (r"\bpm\s+(uninstall|clear)\b",
     "アプリデータ破壊コマンドは禁止"),
    (r"(^|[;&|(]\s*|\s)sudo\s",
     "sudo は対話パスワード必須のこのマシンでは使えない。$HOME 配下で完結させるか、監督に報告せよ"),
]


def strings(v):
    """ネストした JSON から文字列値だけを再帰的に取り出す。"""
    if isinstance(v, str):
        yield v
    elif isinstance(v, dict):
        for x in v.values():
            yield from strings(x)
    elif isinstance(v, list):
        for x in v:
            yield from strings(x)


def main():
    payload = json.load(sys.stdin)
    blob = "\n".join(strings(payload.get("toolCall", {}).get("args", {})))
    # 観測用: 実ペイロードのキー構造が docs と違って blob が空のときに気づけるようにする
    print("[guard_forbidden] inspected %d chars" % len(blob), file=sys.stderr)
    for pat, reason in FORBIDDEN:
        if re.search(pat, blob):
            print(json.dumps(
                {"decision": "deny", "reason": "[guard_forbidden] " + reason},
                ensure_ascii=False))
            return
    print(json.dumps({"decision": "allow"}))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # fail-open: 二次防衛線のため hook 不具合で委譲を止めない
        print("[guard_forbidden] error (fail-open): %r" % e, file=sys.stderr)
        print(json.dumps({"decision": "allow"}))
