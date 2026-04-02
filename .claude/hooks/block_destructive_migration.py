#!/usr/bin/env python3
import json, re, sys

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

TARGET = "fallbackTo" + "DestructiveMigration"


def is_kotlin(path):
    return isinstance(path, str) and path.endswith(".kt")


FILE_WRITE_PATTERN = re.compile(r"(>>?|tee\s|sed\s+-i|perl\s+-i)")

found = False
if tool_name == "Bash":
    command = tool_input.get("command", "")
    found = TARGET in command and bool(FILE_WRITE_PATTERN.search(command)) and ".kt" in command
elif tool_name in ("Edit", "Write"):
    if is_kotlin(tool_input.get("file_path", "")):
        found = TARGET in tool_input.get("new_string", "") or TARGET in tool_input.get("content", "")
elif tool_name == "MultiEdit":
    for edit in tool_input.get("edits", []):
        if is_kotlin(edit.get("file_path", "")) and TARGET in edit.get("new_string", ""):
            found = True
            break

if found:
    print("BLOCK: dangerous migration call detected in .kt file.", file=sys.stderr)
    print("Existing user Room DB data will be erased. Use proper Migration objects.", file=sys.stderr)
    sys.exit(2)

sys.exit(0)