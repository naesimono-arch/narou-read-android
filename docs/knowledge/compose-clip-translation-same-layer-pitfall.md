# Compose: clip と translation を同一 graphicsLayer に同居させると移動前にクリップされる

**症状**: 2タイル記録した無限スクロール背景（星図Mの縦トーラス空）で、画面最上部に背景が出ず、
スクロールすると周期境界で全面に「バッと」復帰する（2026-07-19 実機で発覚）。

**機序（プラットフォーム事実）**: RenderNode の `clipToBounds` は**レイヤのローカル座標** [0,h] で先に効き、
その後クリップ枠ごと `translationY` で平行移動する。つまり `graphicsLayer { clip = true; translationY = -offset }`
と書くと、ローカル [0,h] の外（2枚目タイル [h,2h]）は**移動する前に切り落とされ**、タイル連結が無効化される。

**対処パターン**: クリップと移動を別レイヤへ分離する——外側 Box に `clipToBounds()`（画面座標・不動）、
内側 Box に `graphicsLayer { translationY = ...; clip = false }`（移動のみ）。
回帰は純関数化した可視窓カバレッジ検証（offset 全周期掃引で記録域が可視域を包含）で固定
（`SkyParallaxControllerTest` / SkyBackdropM.kt 参照）。

関連: 一枚化アーキテクチャ＝ADR 0019 追記（M の fade 遷移例外）・ADR 0023。
