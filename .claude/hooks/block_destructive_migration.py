#!/usr/bin/env python3
import json, re, sys

try:
    data = json.load(sys.stdin)
except (json.JSONDecodeError, EOFError):
    sys.exit(0)

tool_name = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

# 断片ペア判定用。TARGET の連続一致だけだと `$(printf '%s' 断片)` のような
# シェル内文字列組立で契機を分断されてすり抜けるため、両断片の同時出現でも検知する。
# なぜ隣接リテラル連結（"A" "B"）か: 本ファイル自身が Bash（heredoc/tee）で書かれると
# 下記クォート除去派生が自ソースにも適用され、`"A" + "B"` 形は除去後に連続一致が
# 復元されて自己ブロックする。隣接リテラル間の空白は除去後も残るため復元されない
# （コメントにも連続形は書かない）。
FRAG_A = "fallback" "To"
FRAG_B = "Destructive" "Migration"
TARGET = FRAG_A + FRAG_B


def is_kotlin(path):
    return isinstance(path, str) and path.endswith(".kt")


FILE_WRITE_PATTERN = re.compile(r"(>>?|tee\s|sed\s+-i|perl\s+-i)")

# env-prefix 代入（EXT=kt など）の収集用。値は空白・; | & の手前まで
# （制御演算子を値に巻き込むと展開が壊れるため除外）。
ENV_ASSIGN_RE = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)=([^\s;|&]+)")


def normalized_variants(command):
    """素朴な部分文字列一致を崩すシェル小細工（env-prefix 変数・クォート分断）を
    展開した派生テキスト群を返す。先頭は必ず原文＝従来挙動を厳密に保存する。
    なぜ実行せず文字列展開か: フックはコマンドを走らせられないため、
    `EXT=kt … Db.$EXT` の類は代入値の静的置換でしか .kt を復元できない。"""
    variants = [command]
    assigns = ENV_ASSIGN_RE.findall(command)
    if assigns:
        expanded = command
        # 長い変数名から置換（$A が $AB の先頭部分を先に食う誤置換の防止）
        for name, value in sorted(assigns, key=lambda a: -len(a[0])):
            value = value.strip("'\"")
            expanded = expanded.replace("${%s}" % name, value).replace("$" + name, value)
        if expanded != command:
            variants.append(expanded)
    # クォート除去: 'fallbackTo''Destructive…' のような隣接クォート連結は
    # 実行時に結合されるため、除去後テキストで連続一致が復元される。
    for text in list(variants):
        stripped = text.replace("'", "").replace('"', "")
        if stripped != text:
            variants.append(stripped)
    return variants


def is_destructive_bash(command):
    # 3条件（危険トークン・ファイル書込み・.kt）は各派生テキスト単位で評価する。
    # 断片ペアは TARGET 連続一致の上位互換（TARGET ⊃ 両断片）＝従来検知は全て維持。
    # 書込みパターン＋.kt を必須のまま残すのは誤ブロック回避のため
    # （grep 等の読み取り調査で TARGET に言及するのは正常運用）。
    for text in normalized_variants(command):
        if (
            FRAG_A in text
            and FRAG_B in text
            and FILE_WRITE_PATTERN.search(text)
            and ".kt" in text
        ):
            return True
    return False


found = False
if tool_name == "Bash":
    found = is_destructive_bash(tool_input.get("command", ""))
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
