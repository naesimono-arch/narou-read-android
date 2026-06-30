#!/usr/bin/env python3
"""
PostToolUse hook: ファイル編集後に VS Code で該当ファイルを自動的に開く。
対象ツール: Edit, Write, MultiEdit
"""
import json
import sys
import shutil
import subprocess

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)
tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

paths = set()

if tool_name in ("Edit", "Write"):
    fp = tool_input.get("file_path", "")
    if fp:
        paths.add(fp)
elif tool_name == "MultiEdit":
    for edit in tool_input.get("edits", []):
        fp = edit.get("file_path", "")
        if fp:
            paths.add(fp)

for path in paths:
    open_path = path
    # WSL から Windows版 VS Code(code ラッパー)を呼ぶ場合、Linux パス(/mnt/c/...)のままだと
    # Windows 側がファイルを解決できず「開いても中身が出ない」状態になる(Linux 移行後の実害)。
    # wslpath が在る環境(=WSL)でのみ Windows パスへ変換する。Windows ネイティブでは wslpath 不在かつ
    # file_path が既に C:\... 形式のため変換不要(本ファイルは /mnt/c 共有なので両 OS で安全に分岐させる)。
    # -w(バックスラッシュ)でなく -m(フォワードスラッシュ C:/...)を使うのは、shell=True 下で
    # バックスラッシュが誤エスケープされるのを避けるため(VS Code はどちらの形式も受理する)。
    if shutil.which("wslpath"):
        try:
            win = subprocess.run(
                ["wslpath", "-m", path],
                capture_output=True, text=True, timeout=5,
            ).stdout.strip()
            if win:
                open_path = win
        except Exception:
            pass  # 変換不能なら元パスで試行(最悪 Windows で開けないだけで操作は止めない)
    try:
        # Windows では code.cmd なので shell=True が必要
        # shell=False だと FileNotFoundError になり静かに失敗する
        subprocess.Popen(f'code "{open_path}"', shell=True)
    except Exception:
        pass

sys.exit(0)
