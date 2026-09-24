#!/usr/bin/env python3
"""app_prefs.xml のキーを書き換える（アプリを止めてから／pull→編集→push）。
usage: setpref.py <key> <string|boolean|int|long> <value> [<key> <type> <value> ...]"""
import os, re, subprocess, sys
A = os.path.expanduser("~/Android/Sdk/platform-tools/adb"); DEV = "emulator-5554"
P = "/data/data/com.novelreader/shared_prefs/app_prefs.xml"
TMP = "/tmp/app_prefs.xml"
def sh(*a): return subprocess.run([A, "-s", DEV, *a], capture_output=True, text=True).stdout
sh("shell", "am", "force-stop", "com.novelreader")
sh("shell", f"cp {P} /data/local/tmp/pp.xml || true")
sh("pull", "/data/local/tmp/pp.xml", TMP)
try:
    xml = open(TMP, encoding="utf-8").read()
except FileNotFoundError:
    xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
args = sys.argv[1:]
for i in range(0, len(args), 3):
    k, t, v = args[i], args[i+1], args[i+2]
    xml = re.sub(rf'\s*<\w+ name="{re.escape(k)}"[^>]*/>', '', xml)
    xml = re.sub(rf'\s*<string name="{re.escape(k)}">.*?</string>', '', xml, flags=re.S)
    if v != "__DELETE__":
        line = f'    <string name="{k}">{v}</string>' if t == "string" else f'    <{t} name="{k}" value="{v}" />'
        xml = xml.replace("</map>", line + "\n</map>")
open(TMP, "w", encoding="utf-8").write(xml)
sh("push", TMP, "/data/local/tmp/pp.xml")
sh("shell", f"su root sh -c 'cp /data/local/tmp/pp.xml {P}; chown $(stat -c %u:%g /data/data/com.novelreader) {P}; chmod 660 {P}'")
print(sh("shell", f"cat {P}"))
