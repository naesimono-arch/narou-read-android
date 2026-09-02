#!/usr/bin/env python3
"""走査(c) の判定器の回帰テスト＝感度（本物を赤にする）と特異度（産物を緑にする）を同時に固定する。

なぜ要るか: 走査(c) は偽陽性を消すたびに述語と閾値が足される。真陽性のサンプルを持たないまま緩めると
**「常に緑を返すだけの部品」に化けても CI は緑のまま**で、誰も気づけない（同型の事故＝2026-07-12 の
テスト強制3点撤去でセンチネル照合が13日間死んでいた）。赤を 0 にすること自体は目的ではなく、
「赤が出るべきときに出る」を保ったまま産物だけを消すのが目的なので、両方を assert する。

fixture の出所（tools/testdata/golden_label_split/ ＝ corpus 本体とは別管理。他便の再記録で
勝手に変わらないよう、是正前の実物を凍結してある）:
  ・BookshelfK_empty_light_{1.0,2.0}.png … コミット d99d99d（是正コミット 9e935c6「fontScale 2.0 で
    機能が失われる器を直す（目次4スキン・設定シート・ナビ帯・空棚）」の親）から取得。
    空棚 CTA が 2.0 で P/D/F の縦積みへ割れた実物。
  ・TocK_ep4digits_light_{1.0,2.0}.png … コミット 3567f2c（是正コミット 20f1f01「目次の現在地バーが
    fontScale 2.0 で章題を1行1文字に潰し進捗も読めない」の親）から取得。4桁話数ラベルが割れた実物。
  ・残り4組 … 現行 corpus から凍結した既知の偽陽性（いずれも「同じ PNG を別の粒度で測ると結果が変わる」
    ことを実測して判定器の産物と確定したもの）。

実行: python3 tools/test_check_golden_label_split.py   （期待と1件でも違えば exit 1）
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import check_golden_label_split as split  # noqa: E402
import golden_png as gp  # noqa: E402

FIXTURES = Path(os.path.dirname(os.path.abspath(__file__))) / "testdata" / "golden_label_split"

# (case, 真の姿, 現時点で判定器が返す値, 出所と根拠)
#   真の姿 … "red"=実際に 1行1文字へ割れている / "green"=画素は割れておらず判定器の産物
#   3列目が真の姿と食い違う行が「既知の穴」。穴を塞ぐと assert が落ちて表の更新を強制するので、
#   **改善を黙って通さない**（緩めた結果あちこち緑になったのを見逃さないための仕掛け）。
EXPECTED = [
    ("BookshelfK_empty_light", "red", "red",
     "d99d99d＝是正前の実物。空棚 CTA が P/D/F へ縦積み（感度の担保）"),
    ("TocK_ep4digits_light", "red", "red",
     "3567f2c＝是正前の実物。4桁話数ラベルが縦積み（感度の担保）"),
    ("NovelDetailScreen_content_light", "green", "green",
     "栞書影の縦組み題字が dp 固定で、2.0 側だけ広いまとめ幅により隣接2列が融合した産物"),
    ("BookshelfK_list_mixed_light", "green", "green",
     "同上の融合（塊 36x16px＝縦横比 2.25）"),
    ("BookshelfK_grid_mixed_light", "green", "green",
     "粒度1.0で14/11・粒度2.0で18/15＝どちらの粒度でも増えていない（⑤で解消）"),
    ("IntroOverlayK_about_intro_light", "green", "green",
     "粒度1.0で0/0・粒度2.0で0/1＝粒度2.0でしか増えない（⑤で解消）"),
]


def main():
    pairs = gp.scale_pairs(FIXTURES)
    bad = []
    gaps = []
    for case, truth, current, why in EXPECTED:
        if case not in pairs:
            bad.append(f"{case}: fixture が無い（{FIXTURES} を確認）")
            continue
        verdict, small_n, large = split.case_verdict(pairs[case])
        # "vanished"（1.0 より減った）は赤ではない＝参考出力なので緑側として扱う。
        got = "red" if verdict == "red" else "green"
        if got != current:
            bad.append(f"{case}: 期待 {current} だが {got}（1.0={small_n}本 → 2.0={len(large)}本）。{why}")
        elif current != truth:
            gaps.append(f"{case}: 既知の穴＝真の姿は {truth} だが判定器は {current}。{why}")
    print(f"走査(c) 判定器の回帰: {len(EXPECTED)} 組を検査")
    for line in gaps:
        # 黙って通すと「穴が在ること」が消えるので、緑でも必ず出す。
        print(f"  [既知の穴] {line}")
    for line in bad:
        print(f"  [失敗] {line}")
    if bad:
        print(
            f"\n{len(bad)} 件が期待と違う。判定器を緩めた直後なら、真の姿と一致する方向へ動いたか"
            "（＝EXPECTED の3列目を更新してよいか）を確かめること。感度側（BookshelfK_empty /"
            "TocK_ep4digits）が緑へ倒れた場合は緩めすぎ＝差し戻す。",
        )
        return 1
    print("  期待どおり")
    return 0


if __name__ == "__main__":
    sys.exit(main())
