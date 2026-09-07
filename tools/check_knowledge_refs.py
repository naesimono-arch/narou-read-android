#!/usr/bin/env python3
"""docs/knowledge/*.md のバッククォート内ファイル参照が今も解決するかを機械照合する。

用途: `/stale-check` 横断検査3 の「knowledge の全数照合はしない」という判断を、
汚染率が跳ねていないことの確認だけで支え続けるための、費用ゼロで再現できる腐り検査。
意味の腐り（主張が実態と食い違う）は検出しない——**参照パスの死活だけ**を見る。

なぜ「末尾一致」で解決するか（素朴な os.path.exists では測れない）:
  knowledge は正本パスを省略形で書くのが常態で、`pdf/DetectedRules.kt` や
  `android/.../ui/skins/j/BookshelfPortalJ.kt` のように途中を省く。repo ルート相対の実在確認
  だけで判定すると、生きた参照が大量に「壊れている」と出る（2026-09-07 の実測では素朴版が
  70.4%、basename 索引版でも 30.6% の偽陽性を出し、末尾一致まで実装して 7.1% に落ちた）。
  ∴ セグメント境界での末尾一致を正とする。

残る7本相当は非追跡が設計どおりのもの（ビルド生成物・端末上のファイル・auto-memory）なので、
既定では EXPECTED_UNTRACKED で除外して 0 件を期待値にする。除外は「腐りではない」と判断した
記録を兼ねるため、増やすときは理由を添えること。
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 非追跡が設計どおり＝参照が生きていても git には無いもの。腐りとして数えない。
EXPECTED_UNTRACKED = {
    "app_debug-module.json": "Compose コンパイラのビルド生成物",
    "benchmarkData.json": "Macrobenchmark の実行成果物",
    "app_prefs.xml": "端末上の SharedPreferences",
    "shared_prefs/app_prefs.xml": "端末上の SharedPreferences",
    "chap_1028.html": "端末上に取り込まれた蔵書データ",
    "MEMORY.md": "auto-memory（リポジトリ外）",
    "legacy-python-artifacts.md": "auto-memory（リポジトリ外）",
}

EXT = (".md", ".kt", ".py", ".js", ".json", ".gradle", ".html", ".tsv", ".sh", ".yml", ".xml", ".kts")
BACKTICK = re.compile(r"`([^`\n]+)`")
LINESUFFIX = re.compile(r":\d+(-\d+)?$")


def tracked_paths():
    out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True)
    return [p for p in out.stdout.split("\n") if p]


def normalize(token):
    """バッククォート内トークンから参照パス候補を取り出す（対象外なら None）。"""
    t = token.strip().split()[0].split("#")[0].rstrip("/,。）)、")
    t = LINESUFFIX.sub("", t)                      # `path:12` / `path:12-20` の行番号を剥がす
    t = t.replace("…/", "").replace(".../", "")    # 省略記号を畳む
    if not t.endswith(EXT) or "*" in t or "<" in t:
        return None
    if t.startswith(("~", "/", "C:", "http")):     # 絶対パス・URL は対象外（環境依存）
        return None
    if os.path.basename(t).startswith("."):        # 素の拡張子（`.kt` 等）は参照ではない
        return None
    return t


def main():
    tracked = tracked_paths()
    knowledge = sorted(p for p in tracked
                       if p.startswith("docs/knowledge/") and p.endswith(".md")
                       and not p.endswith("README.md"))

    def resolves(tok):
        if os.path.exists(os.path.join(ROOT, tok)):
            return True
        # セグメント境界での末尾一致＝省略形の参照を生きているとみなす
        return any(t == tok or t.endswith("/" + tok) for t in tracked)

    broken = {}
    for rel in knowledge:
        with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
            text = f.read()
        bad = set()
        for raw in BACKTICK.findall(text):
            tok = normalize(raw)
            if tok and tok not in EXPECTED_UNTRACKED and not resolves(tok):
                bad.add(tok)
        if bad:
            broken[rel] = sorted(bad)

    print(f"knowledge {len(knowledge)} 本を照合（README 除く）")
    if not broken:
        print("解決できない参照: 0 本 — OK")
        return 0
    pct = 100 * len(broken) / len(knowledge)
    print(f"解決できない参照を持つ: {len(broken)} 本 ({pct:.1f}%)")
    for rel, toks in broken.items():
        print(f"  {rel} -> {', '.join(toks)}")
    print("\n非追跡が設計どおりのものなら EXPECTED_UNTRACKED へ理由つきで登録する。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
