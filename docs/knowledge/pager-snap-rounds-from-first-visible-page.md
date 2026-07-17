# HorizontalPager: 覗き(contentPadding)構図では既定snapの丸め基準が視覚中央から1枚ズレる

**症状**: 左右に隣カードを覗かせる `HorizontalPager`（`contentPadding` 使用）で、高速フリングすると目的の1枚先を越えて2枚先へ着地することがある。

**機序**（foundation 1.7.8 / compose-bom 2025.02.00・bytecode 逆アセンブルと公式docsで確定）:
- flingBehavior 未指定でも既定は `PagerSnapDistance.atMost(1)`＝「1ページ制限」自体は効いている。
- しかし `calculateApproachOffset` の丸め基準は **`firstVisiblePage`**（velocity 符号で±1補正）。覗き構図では `firstVisiblePage` は視覚的中央カード（`currentPage`）の**1つ手前＝左の覗きカード**なので、基準点が1枚ズレ、一方向の高速フリングで中央から2枚先へ着地しうる。
- 疑わしく見える負の `pageSpacing` は無関係（snap step は `pageSize + pageSpacing` で正しく計算される）。

**対処**: 速度制限ハックではなく丸め基準の是正＝カスタム `PagerSnapDistance` を `PagerDefaults.flingBehavior` に注入し、着地を `currentPage ± 1` へ厳密制限（decay/snap の質感は既定のまま）。実装と純関数テスト＝`ui/WardrobeScreen.kt` / `WardrobeFlingTargetTest.kt`（2026-07-17）。

**適用条件**: 覗きカード構図の Pager 全般。覗きの無い全幅ページでは `firstVisiblePage`≒`currentPage` なので顕在化しない。
