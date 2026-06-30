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

コード: `NativeReadingScreen.kt`（8a27999, 2662bf6 で導入）
