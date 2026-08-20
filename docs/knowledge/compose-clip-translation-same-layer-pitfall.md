# Compose: clip と変換（translation / scale / rotation）を同一 graphicsLayer に同居させると、動く前にクリップされる

**症状の初出**: 2タイル記録した無限スクロール背景（星図Mの縦トーラス空）で、画面最上部に背景が出ず、
スクロールすると周期境界で全面に「バッと」復帰する（2026-07-19 実機で発覚）。

**機序（プラットフォーム事実）**: RenderNode の `clipToBounds` は**レイヤのローカル座標** [0,w]×[0,h] で先に効き、
その後で**レイヤの変換行列**が、クリップ済みの結果を親座標へ写す。
`graphicsLayer { clip = true; translationY = -offset }` と書くと、ローカル [0,h] の外
（2枚目タイル [h,2h]）は**移動する前に切り落とされ**、タイル連結が無効化される。

**引き金は translationY ではなく「clip と変換の同居」**——`clip` も `translationX/Y` も `scaleX/Y` も
`rotationX/Y/Z` も `transformOrigin` も、**同一の RenderNode の**プロパティである
（Compose 1.7.8/`RenderNodeApi29` の bytecode で確認: `setClipToBounds` / `setTranslationX` / `setScaleX` /
`setRotationZ` はすべて同じ `renderNode` フィールドへ委譲）。したがって順序（クリップ→変換）は
**どの変換プロパティでも同じ**で、translationY はその1つの現れ方にすぎない。

## 同じ機序の別の現れ方

- **scale**: ピンチズームで `scaleX/scaleY` を上げても、元のレイヤ境界の外にあった内容は**拡大前に切られている**。
  「寄ったのに周辺が出てこない・端が欠ける」として出る。
- **rotation**: 回転で枠内へ入ってくるはずの四隅が出ず、回転した内容の角が切り落とされたように見える。
- **translationX**: 横トーラス・横方向の無限カルーセルで同上（初出は縦だっただけ）。
- **言い換えると**: 同一レイヤの `clip` は「固定された窓」ではなく**内容と一緒に動く（拡大する・回る）枠**。
  窓として使いたいなら、クリップは**変換しない祖先**に置くしかない。

## 対処パターン

クリップと変換を別レイヤへ分離する——外側 Box に `clipToBounds()`（画面座標・不動）、
内側 Box に `graphicsLayer { translationY = ...; clip = false }`（変換のみ）。
同じことを modifier チェーンで書く場合、**チェーンの先に書いた方が外側**なので
`Modifier.clipToBounds().graphicsLayer { ... }` の順（＝クリップが外）にする。
1つの `graphicsLayer { }` ブロックの中に `clip = true` と変換を並べた瞬間に同居＝罠。

回帰は純関数化した可視窓カバレッジ検証（offset 全周期掃引で記録域が可視域を包含）で固定
（`SkyParallaxControllerTest` / SkyBackdropM.kt 参照）。

## 検知の勘所

「内容が出てこない／端が欠ける／境界で一気に復帰する」で、まず
`rg -n 'clip\s*=\s*true' -g '*.kt'` の各ヒットについて**同じ `graphicsLayer` ブロック内に変換プロパティが
書かれていないか**を見る。翻訳ミスではなく、書いた瞬間に必ず起きる構造の罠。

関連: 一枚化アーキテクチャ＝ADR 0019 追記（M の fade 遷移例外）・ADR 0023。
