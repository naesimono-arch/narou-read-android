# Compose: content ラムダ内の早期 `return@Box` はグループスタックを壊してクラッシュする

**症状**: 実行時（テストでは Robolectric）に `ArrayIndexOutOfBoundsException: Index -2 out of bounds for length 160`（`IntStack.peek2`）。コンパイルは通る。

**真因**: `Box { … if (cond) return@Box … }` のように **content ラムダ内から早期 return** すると、既にコンポジション開始済みの子グループを飛ばして終了するため、Compose のグループスタックが不整合になる（2026-07-17 DiscoveryPortalJ の ctx=null 経路で実測）。

**対処**: 条件分岐は content ラムダの**外**（Composable 関数のトップレベル）へ出し、トップレベルの早期 `return` にする（こちらは Compose が対応済み）。または early-return をやめ if/else で描き分ける。

**見つけ方**: 症状のスタックに UI コードが出ず composer 内部だけが並ぶため grep しづらい。`return@` を content ラムダ内で使っている箇所を疑う。

一次情報: `ui/skins/j/DiscoveryPortalJ.kt` の当該箇所コメント（ui/skin-framework ブランチ）。
