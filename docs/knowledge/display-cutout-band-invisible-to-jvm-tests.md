# 切り欠き帯への食い込みは JVM テスト・golden の網から原理的に漏れる（実機/エミュの座標判定しか番人がいない）

**重要度**: ★★
**確定日**: 2026-08-25（API 36 エミュ・cutout emulation `tall` で実測）
**一行要約**: Robolectric も golden も cutout を模擬しないため `WindowInsets.displayCutout` は常に 0 で解決される
＝帯を避ける実装を**丸ごと外しても全緑のまま通る**。番人は `tools/cutout_probe.py` の座標判定だけ。

## 症状

横向きにすると、戻る矢印・下部バー最左のボタン・目次行・本棚レールといった**画面端 UI が切り欠きの下へ潜り込む**。
2026-08-25 の実測では横向きで `inset.left=126px` が立っているのに、それらの要素が x=0〜35 に置かれていた。
にもかかわらず `testDebugUnitTest` も golden も全緑。

## 真因（二段）

1. **アプリ側**: `layoutInDisplayCutoutMode=ALWAYS`（切り欠きの下まで描く宣言）に対して、
   その裏返しの義務＝**cutout inset を消費する側が存在しなかった**。各画面の
   `statusBarsPadding` / `navigationBarsPadding` も読書画面の `systemBarsIgnoringVisibility` も
   **`displayCutout` を含まない**ので、横方向の実効値が 0 のままだった。
2. **検査側**: Robolectric は display cutout を模擬しない。`WindowInsets.displayCutout` は常に
   `Insets.NONE` へ解決されるため、`windowInsetsPadding(displayCutout…)` を消しても**測れる差が出ない**。
   golden も同じ理由で通る——描かれる絵の中に帯という概念が無いから。
   ⇒ **この破綻クラスに対して JVM 側のゲートは構造的に無力**。緑は「無事」の証拠にならない。

## 対処

- 実装: 宣言と同じ所有者（`MainActivity` の画面ルート `Column`）で
  `WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)` を消費する。
  - `safeDrawing` を採らない: ime と systemBars を巻き込み、かつ**可視性に追従する**ので
    バー出没のたびに幾何が動く＝`ALWAYS` を選んだ目的（没入トグルでレイアウトを跳ねさせない）を自ら壊す。
  - `Horizontal` 限定: 縦向きの切り欠きは上端でステータスバーと同帯＝各画面が処理済み。
    縦も足すと二重に下がる（**縦向きでは横 inset が実測 0** なので、この padding は縦では無効化される）。
  - `Horizontal` は左右**両方**を覆う＝landscape(左) と seascape(右) の両方に効く（両向きとも実測で確認済み）。
- **NavHost の外に重ねる層は別途自前で持つ**（教示オーバーレイが該当）。根の Column の padding は届かない。
  スクリムは全面のまま、カードだけを安全帯へ寄せる。
- 検査: `python3 tools/cutout_probe.py setup` → 面を出して `check <名前>` → `restore`。
  帯幅はハードコードせず `dumpsys window` の `DisplayCutout{insets=…}` を毎回読む。

## なぜそうなるか（ハマりどころ3点）

- **`dumpsys display` の cutout は回転しない**。物理（回転 0 基準）で固定なので、横向きにしても
  `Rect(0, 126 - 0, 0)`（＝top）に見え、「横方向は 0 だから無関係」と誤読する。
  回転に追従した実効値は **`dumpsys window` の `DisplayCutout`** 側（seascape なら `right=126` と出る）。
- **AVD は overlay を全部切っても切り欠きを持つことがある**。`com.android.internal.emulation.pixel_7` 等の
  端末プロファイル overlay が既定で有効で、実測 136px の cutout を供給していた。
  「overlay 無効＝切り欠き無し」ではないので、**基準値は必ず実測してから比較する**。
- **見えない semantics ノードを食い込みと誤診しない**。`alpha=0` や負オフセットの部品は
  AccessibilityNodeInfo に残ることがある（`compose-offscreen-nodes-pruned-from-a11y-tree.md` の裏返し）。
  実例: さがす画面に `[63,86][106,212]` の clickable ノードが出るが、実描画は素の背景色
  `(251,250,248)` でタップも無反応＝ランキング期間ページャの隣ページ隙間
  （`RankingPageSpacing = Spacing.S24` = 63px ぶん左）の器で、破綻ではない。
  `cutout_probe.py check --pixels` が実描画色を採るのはこの切り分けのため。
