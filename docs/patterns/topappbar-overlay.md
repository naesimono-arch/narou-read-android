# TopAppBar オーバーレイ化 + NestedScrollConnection 非消費パターン  ★★

> 旧 `task_diary.md` §24（本アプリ固有の実装パターン）
> ここは **コードが正本**。「なぜこのパターンか」に絞る。

`enterAlwaysScrollBehavior` をそのまま `Scaffold` に渡すと、スクロールを横取りして
LazyColumn の `contentPadding` が再計算され本文が揺れる問題がある。

**解決パターン**: `Scaffold` の外側の `Box` に TopAppBar をオーバーレイで重ね、
バーの動きは `graphicsLayer { translationY }` で制御する。

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(nonStealingConnection),
        // TopAppBar は Scaffold の topBar に渡さない
    ) { ... }

    TopAppBar(
        modifier = Modifier.graphicsLayer {
            translationY = topAppBarState.heightOffset
        },
        scrollBehavior = scrollBehavior, // heightOffsetLimit 計測のために維持
    )
}
```

**NestedScrollConnection の実装方針**:
- `onPreScroll`: 下スクロール時にバーを追従させるが `Offset.Zero` を返して消費しない
- `onPostScroll`: 上スクロール時は本文が実際に動いた分だけバーを復元
- `onPostFling`: 慣性終了後に `settleTopBar()` を呼んで全表示/全非表示へスナップ

標準の snap は消費戦略と一体化しているため自前実装が必要。
`scrollBehavior = null` にすると `heightOffsetLimit` が測定されず追従計算が壊れるため、
`scrollBehavior` は引き続きバーに渡し続けること。

**ボトムバーの没入退避（045da9f で拡張）**:
ボトムバーを `Scaffold` の `bottomBar` スロットに置いたままだと、`graphicsLayer { translationY }` で
画面外へスライドさせても **Scaffold が確保する `innerPadding.bottom`（バー高＋ナビバーインセット）は消えず**、
退避しても下端に空白が残り本文が最下部まで届かない。**バーをスロットから外し TopAppBar と同じく
全画面 `Box` のオーバーレイへ移す**ことで退避時に本文が全画面を使える。退避量は固定値でなく
`onSizeChanged` で実測したバー高（ナビバー実高込み）× `topAppBarState.collapsedFraction` で算出する
（ボタン式/ジェスチャー式でナビバー高が異なるため）。本文側は `Scaffold(contentWindowInsets = WindowInsets(0))`
＋ LazyColumn の `contentPadding.bottom`（ナビバー実高＋バー高）でクリアランスを確保（旧 trailing `Spacer(80dp)` から移行）。

**中央タップトグルは実オフセットから反転（045da9f）**:
タップ表示切替を `var barsVisible: Boolean` で持つと、スクロール退避で既にバーが隠れている状態でも
`true` のままになり「隠れているものを隠す」空打ちで2回タップが必要になる。**真偽値を持たず
`topAppBarState.collapsedFraction`（実退避割合）から現在状態を判定して反転先を決める**ことで1タップで
必ず切り替わる（`settleTopBar(state, target)` に目標を渡す形へ一般化して流用）。

コード: `NativeReadingScreen.kt`（8a27999, 2662bf6 で導入。045da9f で没入退避＋タップトグルに拡張）
