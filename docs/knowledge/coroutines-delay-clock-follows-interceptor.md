# `delay` の時計は「そのコルーチンの ContinuationInterceptor」が決める——ディスパッチャを差し替えた瞬間、テストの仮想時間から外れる

**重要度**: ★★★
**確定日**: 2026-08-21（没入ヒントの修正で踏み、kotlinx-coroutines 1.8.1 / lifecycle 2.8.7 の bytecode 実査＋単体プローブの実測で機序確定）

1行要約: **仮想時間で動くのは「interceptor が仮想時間ディスパッチャのまま」の区間だけ**。
`withContext` / `flowOn`、およびそれを内部に隠し持つ API（`repeatOnLifecycle` など）をまたぐと、
その中の `delay` は差し替え先の時計（実時間 or looper）で待つ——**テストは「時間だけが進まない」形で落ちる**。

## 症状の見え方（次に踏んだ人が検索で辿り着けるように）

- テストの assert は正しいのに **`waitUntil` が全部タイムアウト**する。`advanceTimeBy` を何倍積んでも変わらない。
- 「タイマーが 0 のまま」「フラグが一生立たない」——実装は健全なので、実装側を疑って延々と溶ける
  （実際、Gradle を4回まわして切り分けた）。
- 本番（実機）では正しく動く。**テストだけが進まない**のがこの型の指紋。

## 機序（bytecode 実査）

`delay` は待ち先を **`context[ContinuationInterceptor] as? Delay ?: DefaultDelay`** で選ぶ
（`kotlinx/coroutines/DelayKt.getDelay` を逆アセンブルして確認：ContinuationInterceptor.Key で引き、
`Delay` なら採用、でなければ `DefaultExecutor` の実時間 Delay）。

- `runTest` / Compose の `mainClock` の仮想時間が効くのは、**interceptor が `TestDispatcher`（＝`Delay` を実装し
  `TestCoroutineScheduler` を持つ）だから**。
- ディスパッチャを差し替えると interceptor ごと入れ替わる＝**仮想時間の `Delay` を失う**。
  差し替えても `MonotonicFrameClock` などの他の context 要素は残る（`withContext` は親 context にマージするだけ）ので、
  「フレームクロックが消える」というより **時計が2系統に割れる**と捉えるのが正確。

## 引き金は `repeatOnLifecycle` ではない（一般化・実測）

`kotlinx-coroutines-test` 1.8.1 上で `runTest { ... }` の中の `delay(1000)` を、壁時計と `currentTime`（仮想）の
両方で測った（JVM 単体プローブ・Gradle 不使用。再現は「素の JVM に stdlib+coroutines-core+coroutines-test を通し、
下表の5パターンで wall と `currentTime` を出力する」だけで足りる）:

| 書き方 | 壁時計 | 仮想時間 | 判定 |
|---|---|---|---|
| `delay(1000)`（差し替えなし） | 0ms | 1000ms | 仮想時間 |
| `withContext(Dispatchers.Default) { delay(1000) }` | 1005ms | 0ms | **外れる** |
| `flow { delay(1000); … }.flowOn(Dispatchers.Default)` | 1012ms | 0ms | **外れる** |
| `withContext(Dispatchers.Unconfined) { delay(1000) }` | 1001ms | 0ms | **外れる** |
| `Dispatchers.setMain(StandardTestDispatcher())` 下の `withContext(Dispatchers.Main.immediate)` | 0ms | 1000ms | 保たれる |

読み取れること:

1. **`withContext` でも `flowOn` でも同じ**＝API 名ではなく「interceptor が入れ替わるか」が引き金。
2. **`Unconfined` でも外れる**＝スレッドを跨ぐかどうかは無関係。呼び出し元スレッドで走り続けても仮想時間は失う。
   「別スレッドに飛ばしていないから安全」は誤り。
3. **差し替え先が仮想時間なら保たれる**＝`Dispatchers.setMain(StandardTestDispatcher())` を張ってあれば
   `Main.immediate` への切り替えは無害。つまり是正手段は「切り替えを消す」だけではない。

## ディスパッチャ切り替えを隠し持つ API（lifecycle 2.8.7 の bytecode で確認）

呼び名から切り替えが読めないものがある。実際に確認できたもの:

- **`Lifecycle.repeatOnLifecycle`** = `coroutineScope { withContext(Dispatchers.Main.immediate) { … } }`
  （`RepeatOnLifecycleKt$repeatOnLifecycle$3` に `Dispatchers.getMain().getImmediate()` → `BuildersKt.withContext`）。
- **`withResumed` / `withStarted` / `withCreated` / `withStateAtLeast` 系**（`WithLifecycleStateKt`）も同じく
  `Main.immediate` を掴む（同ファイルに複数箇所）。
- **`Flow.flowWithLifecycle`** は内部で `repeatOnLifecycle` を使う＝同じ性質を継ぐ。

見分け方は「その API の実装で `Dispatchers.…` と `withContext` が出るか」を bytecode かソースで見るだけ。
**suspend API がディスパッチャを指定していないとは限らない**、と疑うのが早い。

## 対処の型

1. **時計を1系統に保つ**（今回採った道）。lifecycle を「ゲートとして畳む」——`repeatOnLifecycle` で
   コルーチンを包むのではなく、`lifecycle.currentStateFlow` を**値の flow**として他の条件と `combine` する。
   状態機械は元の（仮想時間の）コルーチンに残り、前面判定だけが足される。
   実物＝`android/app/src/main/java/com/novelreader/ui/ImmersiveChromeHint.kt`。
2. **時間に依存する Compose 副作用は、判定を suspend 関数へ切り出して純 coroutine テストで固定する**。
   `LaunchedEffect` の中の `delay` を Robolectric の compose ハーネスで進めるのは**断念した**（下記）。
   切り出した `awaitImmersiveHintSeen` は `runTest` の仮想時間で素直に固定できた
   （`android/app/src/test/java/com/novelreader/ui/ImmersiveChromeHintTest.kt`）。
3. どうしても本番側で切り替えが要るなら、**テスト側で差し替え先を仮想時間にする**
   （`Dispatchers.setMain(StandardTestDispatcher(testScheduler))`）。上表5行目で成立を確認。
   ただし Compose のテストハーネスと併用したときの挙動は**未確認**（プローブは素の `runTest` で測った）。

## ⚠️ 未確定（推測で埋めないこと）

Compose 側で踏んだとき、**`mainClock.advanceTimeBy` でも `ShadowLooper.idleFor` でも進まなかった**。
前者は既知（`robolectric-compose-clock-capture-pitfalls.md` ②＝`advanceTimeBy` は TestCoroutineScheduler を
進めるだけで looper を汲まない）だが、**後者がなぜ効かなかったかは切り分けていない**——Gradle 4回を費やした時点で
「時計を1つに保つ」方針へ切り替えたため。**両方の時計を汲もうとする戦い方に賭けない**、が実務上の結論。
