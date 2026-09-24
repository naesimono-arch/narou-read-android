#!/usr/bin/env python3
"""横向き＋切り欠き（display cutout）で画面端 UI が帯へ潜り込んでいないかを座標で判定する。

## なぜ機械テストでなくこの形（実機/エミュを操作者が運ぶ半自動）なのか

この破綻クラスは **Robolectric の網から原理的に漏れる**。Robolectric は cutout を模擬せず
`WindowInsets.displayCutout` は常に 0 で解決されるため、`windowInsetsPadding` を丸ごと外しても
JVM テストは全緑のまま通る（golden も同じ理由で通る＝絵の中に帯が無い）。
`layoutInDisplayCutoutMode=ALWAYS`（AndroidManifest/MainActivity）を宣言している以上、
「帯を避ける側」が生きているかを確かめられるのは **実際に cutout を持つ画面を持つ端末だけ**。

## 使いかた（1行）

    python3 tools/cutout_probe.py setup      # 切り欠きを立てて横向きに固定
    python3 tools/cutout_probe.py check 本棚  # ← 画面を出しておいて、その面を判定（何度でも）
    python3 tools/cutout_probe.py restore    # 台を既定へ戻す（必ず最後に実行）

`check` は帯へ食い込む要素があれば **exit 1**。巡回すべき面は下の CHECKLIST を参照。

## 判定の作法（当てずっぽうを避けるための3点）

- **帯幅はハードコードしない**。`dumpsys window` の `DisplayCutout{insets=...}` を毎回読む＝
  回転に追従した実効値（横向きは left か right、縦向きは top）をそのまま使う。
  縦向きは横方向の inset が 0 になる＝この検査は「横向きでだけ意味を持つ」ことが数値で自明になる。
- **全幅コンテナは食い込みに数えない**。背景・スクリム・画面ルートは帯の下まで敷き詰めるのが正しい
  （教示オーバーレイのスクリムがまさにこれ＝カードだけを安全帯へ寄せる）。
  内容幅いっぱいに広がるノードだけを除外し、それ未満の「部品」を見る。
- **本文段落・縦書き面は `text` を持たない**ので名前は content-desc / class で補う
  （`docs/knowledge/body-paragraphs-have-no-text-node-only-contentdescription.md`）。

⚠️ 見えないだけの semantics ノードは食い込みではない。`alpha=0` や画面外へ寄せた部品は
AccessibilityNodeInfo に残ることがある（`docs/knowledge/compose-offscreen-nodes-pruned-from-a11y-tree.md`）。
疑わしいノードは `--pixels` で実際に何か描かれているかを確かめてから破綻と呼ぶこと。
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# 横向き＋切り欠きで巡回する面（2026-08-25 時点で全数合格を実測済み）。
# 深い画面は NavHost 配下＝MainActivity の Column が一括で帯を避けるので、
# 新しい面を足したときに確かめるべきは「その Column の外に重ねていないか」だけ。
CHECKLIST = [
    "本棚（グリッド／リスト）",
    "本棚 選択モードの下端バー",
    "本文欠落スイープバナー",
    "さがす（ランキングまでスクロール）",
    "設定",
    "作品詳細",
    "目次・読書（下部バー最左）",
    "教示カード（NavHost の外に重なる層）",
    "削除確認／復旧（3ボタン縦積み）／一括再取込 の各ダイアログ",
]

CUTOUT_OVERLAY = "com.android.internal.display.cutout.emulation.tall"


def adb_bin() -> str:
    """adb の実体を自前で解決する。

    なぜ素の `adb` に頼らないか: フック・サブエージェント・CI から呼ばれる非対話 Bash は
    `.bashrc` を読まない＝PATH に platform-tools も `~/.local/bin` も載っていない
    （`docs/knowledge/` 相当の実測＝auto-memory `bash-tool-no-bashrc-gradle-env`）。
    ここで解決しておかないと「使いたい場面でだけ動かない」道具になる。
    `~/.local/bin/adb`（承認済み鍵を提示する WSL ラッパ）を素の platform-tools より優先する。
    """
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    for cand in (Path.home() / ".local/bin/adb",
                 Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb"):
        if cand.exists():
            return str(cand)
    found = shutil.which("adb")
    if not found:
        sys.exit("adb が見つからない。ADB=<パス> で明示するか ANDROID_HOME を設定すること。")
    return found


def adb(serial: str | None, *args: str, binary: bool = False):
    cmd = [adb_bin()] + (["-s", serial] if serial else []) + list(args)
    p = subprocess.run(cmd, capture_output=True)
    if p.returncode != 0:
        sys.exit(f"adb 失敗: {' '.join(cmd)}\n{p.stderr.decode(errors='replace')}")
    return p.stdout if binary else p.stdout.decode(errors="replace")


def cutout_insets(serial: str | None) -> dict[str, int]:
    """回転に追従した実効 cutout inset を読む。

    `dumpsys display` 側の値は**物理（回転 0 基準）で固定**なので使わない——横向きにしても
    top=126 のままに見え、「横方向は 0」と誤読する。`dumpsys window` の DisplayCutout は
    現在の回転で解決済み（seascape なら right=126 と出る）。
    Rect の短縮表記は `Rect(left, top - right, bottom)`。
    """
    out = adb(serial, "shell", "dumpsys", "window")
    m = re.search(r"DisplayCutout\{insets=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)", out)
    if not m:
        return {"left": 0, "top": 0, "right": 0, "bottom": 0}
    left, top, right, bottom = (int(g) for g in m.groups())
    return {"left": left, "top": top, "right": right, "bottom": bottom}


def dump_nodes(serial: str | None):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/_cutout_probe.xml")
    xml = adb(serial, "shell", "cat", "/sdcard/_cutout_probe.xml")
    adb(serial, "shell", "rm", "-f", "/sdcard/_cutout_probe.xml")
    root = ET.fromstring(xml[xml.index("<?xml") :])
    nodes = []
    for n in root.iter("node"):
        m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        x1, y1, x2, y2 = (int(g) for g in m.groups())
        if x2 <= x1 or y2 <= y1:  # 面積 0＝描かれていない
            continue
        name = (n.get("text") or n.get("content-desc") or n.get("resource-id") or "").strip()
        nodes.append({
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "name": name, "cls": (n.get("class") or "").split(".")[-1],
            "clickable": n.get("clickable") == "true",
        })
    return nodes


def sample_pixel(serial: str | None, x: int, y: int):
    raw = adb(serial, "exec-out", "screencap", binary=True)
    w, h, _fmt = struct.unpack("<III", raw[:12])
    header = next((o for o in (12, 16) if w * h * 4 + o == len(raw)), 16)
    i = header + (y * w + x) * 4
    return tuple(raw[i : i + 4])


def check(serial: str | None, label: str, show_pixels: bool) -> int:
    ins = cutout_insets(serial)
    nodes = dump_nodes(serial)
    screen_w = max(n["x2"] for n in nodes)
    screen_h = max(n["y2"] for n in nodes)
    left, right = ins["left"], ins["right"]

    print(f"### {label}")
    print(f"    画面 {screen_w}x{screen_h} / cutout inset {ins}")
    if left == 0 and right == 0:
        print("    横方向の cutout が 0＝この向きでは検査対象なし（縦向きの帯は上端＝statusBars 側が担当）。")
        print("    横向きへ回してから測ること: adb shell settings put system user_rotation 1")
        return 0

    # 内容幅いっぱいに広がるノード＝背景・スクリム・画面ルート。帯を跨ぐのが正しいので除外する。
    content_w = screen_w - left - right
    intruders = []
    for n in nodes:
        if n["x2"] - n["x1"] >= content_w:
            continue
        if left and n["x1"] < left:
            intruders.append((n, "左", left - n["x1"]))
        elif right and n["x2"] > screen_w - right:
            intruders.append((n, "右", n["x2"] - (screen_w - right)))

    parts = [n for n in nodes if n["x2"] - n["x1"] < content_w]
    if left:
        print(f"    左帯 0..{left} / 部品の最小 x = {min(n['x1'] for n in parts)}（安全は {left} 以上）")
    if right:
        edge = screen_w - right
        print(f"    右帯 {edge}..{screen_w} / 部品の最大 x2 = {max(n['x2'] for n in parts)}（安全は {edge} 以下）")

    if not intruders:
        print(f"    ✅ 食い込み 0（部品 {len(parts)} 個を判定）")
        return 0

    print(f"    ❌ 食い込み {len(intruders)} 件")
    for n, side, depth in sorted(intruders, key=lambda t: -t[2])[:15]:
        px = ""
        if show_pixels:
            cx = (n["x1"] + min(n["x2"], left)) // 2 if side == "左" else (max(n["x1"], screen_w - right) + n["x2"]) // 2
            px = f" pixel={sample_pixel(serial, cx, (n['y1'] + n['y2']) // 2)}"
        flag = " clickable" if n["clickable"] else ""
        print(f"      {side}へ {depth}px  [{n['x1']},{n['y1']}][{n['x2']},{n['y2']}]"
              f"  {n['name'][:40]!r} {n['cls']}{flag}{px}")
    if not show_pixels:
        print("    ※ 見えない semantics ノードの可能性を切るには --pixels を付けて再実行")
    return 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("action", choices=["setup", "check", "restore", "checklist"])
    ap.add_argument("label", nargs="?", default="現在の画面", help="check の見出し（面の名前）")
    ap.add_argument("-s", "--serial", help="adb -s に渡す端末（複数台つながっているときは必須）")
    ap.add_argument("--seascape", action="store_true", help="setup を rotation 270（切り欠きが右）にする")
    ap.add_argument("--pixels", action="store_true", help="食い込みノードの実描画色も採る（見えない幽霊ノードの切り分け）")
    a = ap.parse_args()

    if a.action == "checklist":
        print("横向き＋切り欠きで巡回する面:")
        for i, s in enumerate(CHECKLIST, 1):
            print(f"  {i}. {s}")
        return 0

    if a.action == "setup":
        adb(a.serial, "shell", "cmd", "overlay", "enable", CUTOUT_OVERLAY)
        adb(a.serial, "shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb(a.serial, "shell", "settings", "put", "system", "user_rotation", "3" if a.seascape else "1")
        print(f"切り欠きを立てて{'seascape(右)' if a.seascape else 'landscape(左)'}へ固定した。")
        print("画面を出したら: python3 tools/cutout_probe.py check <面の名前>")
        print("⚠️ 終わったら必ず restore（台は共有）")
        return 0

    if a.action == "restore":
        adb(a.serial, "shell", "cmd", "overlay", "disable", CUTOUT_OVERLAY)
        adb(a.serial, "shell", "settings", "put", "system", "user_rotation", "0")
        print(f"既定へ戻した: cutout overlay 無効 / rotation 0 / {cutout_insets(a.serial)}")
        return 0

    return check(a.serial, a.label, a.pixels)


if __name__ == "__main__":
    sys.exit(main())
