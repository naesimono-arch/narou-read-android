# Compose draggable: onDelta を launch{Animatable.snapTo(value+delta)} で積むと追従が死ぬ（同一フレーム内の delta 潰し合い）

**重要度**: ★★
**確定日**: 2026-07-16（PGEM10 実機・スワイプ章送りの引っ張り追従で実測）

## 症状

`rememberDraggableState { delta -> scope.launch { anim.snapTo(anim.value + delta) } }` の形で
ドラッグ追従を書くと、**ゆっくりしたドラッグで視覚追従がほぼゼロ**になる（速いフリックは動いたように見える）。

## 真因

onDelta は同一フレーム内に複数回呼ばれるが、`scope.launch` の本体はイベントディスパッチが
main を明け渡すまで実行されない。**全 delta が同じ古い `anim.value` から次値を計算**して launch を積むため、
最後の1個しか効かず「1フレームにつき delta 1個分」しか進まない。

## 対処

追従は **素の `mutableFloatStateOf` へ同期加算**（`offset = (offset + delta).coerceIn(...)`）し、
graphicsLayer 等の draw 段 deferred read で描く。滑らかさが要るのは指を離した後だけ＝
確定/キャンセルの settle を `animate(initial, target) { v, _ -> offset = v }` で張る
（本リポの settleTopBar と同型）。settle 中の再ドラッグは Job を持って `onDragStarted` で cancel。
実装＝`NativeReadingScreen.kt` の `dragOffsetPx`／`settleSwipe`。
