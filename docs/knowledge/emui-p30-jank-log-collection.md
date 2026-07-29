# EMUI/Huawei P30 実機の jank 回収 — logcat は2分で流れ、累積 gfxinfo は「どの画面か」を教えない

**重要度**: ★★
**確定日**: 2026-07-29（ELE-L29 / Android 10 (SDK 29) / EMUI 10.1 / 1080×2340 **60Hz固定**・実測）
**位置づけ**: 検証端末が PGEM10（ColorOS）以外だった初のケース。`/device-verify` の症状表は ColorOS 固有で、
以下は **EMUI 側の事実**。テスター端末から「もっさり」の証拠を回収する手順としても使う。

## 症状

アプリを使ってくれた人の端末で体感のもっさりが出た。実機を繋いで原因ログを回収しようとしたが、
**logcat には直近2分弱しか残っていない**（`--------- beginning of crash` の直後が既に接続時刻）。
実使用中の `Choreographer: Skipped N frames` 等はすべて流出済みだった。

## 実測された事実

### 1. jank ベースライン（debug ビルド・プロセス稼働 1h45m30s の累積）

条件: 既定スキン K（`app_prefs.xml` にスキンキー無し）・`reading_font_size=14`・主戦場は読書画面。

```
Total frames rendered: 1207    Janky frames: 122 (10.11%)
50th 6ms / 90th 16ms / 95th 22ms / 99th 61ms
Missed Vsync 7 / Slow UI thread 49 / Frame deadline missed 50 / Slow bitmap uploads 0
HISTOGRAM 末尾: 101ms=1 109ms=1 400ms=1 450ms=1 500ms=2   ← 0.4〜0.5秒級の停止が4回
Layout Cache: 1817/5000 entries, hit ratio 0.9097
```

フレーム内訳（`framestats` の有効10フレーム・中央値 23.6ms ≒ 42fps 相当）:

| フェーズ | 平均 |
|---|---|
| traversal（measure/layout） | **0.06ms**（＝レイアウトは無罪） |
| draw（描画コマンド記録） | 8.14ms |
| GPU コマンド発行 | 11.29ms |
| SwapBuffers 待ち | 2.00ms |

メモリは TOTAL 108MB / Graphics 6.2MB / Views 10 で健全。**dropbox に crash・ANR は1件も無い**。

### 2. 濡れ衣に注意

logcat に出た `Choreographer: Skipped 132 frames!` は PID が `com.huawei.phoneservice` のもので、
当アプリとは無関係だった。**Skipped frames は必ず PID を引き当ててから読む。**

## 対処（実機を触る順序）

1. **`adb logcat -G 16M` を最初に打つ**（EMUI 既定は小さく、実測で2分弱しか保持しない）。
   `-G` は永続せず端末再起動で既定へ戻る＝調査のたびに打つ。
2. **`dumpsys gfxinfo <pkg>` を先に、非破壊で吸う**。累積統計はプロセスが死ぬと消えるため、
   `force-stop`・再インストール・アプリ再起動より前に取る（`--reset` は破壊的なので回収前に使わない）。
3. crash/ANR の有無は `dumpsys dropbox` で確認する（**永続**＝logcat と違い数日残る）。
   `/data/anr/` の trace は system 所有で shell からは読めない。
4. **他人の端末では、触る前に使用中かを確認する**:
   `adb shell dumpsys activity activities | grep mResumedActivity`。
   `install -r` はアプリを前面に出さないので比較的安全だが、**起動（monkey/am）は相手の操作に割り込む**
   （ColorOS で 2026-07-07 に「ゲーム中にアプリが勝手に展開」として体感された実害と同型）。

## なぜそうなるか / 何が分からないか

- `gfxinfo` の累積統計は ThreadedRenderer がプロセス生存中に積むもので、**時刻もアクティビティ名も持たない**。
  そのため「0.5秒フリーズがどの画面のどの操作で起きたか」は原理的に取り出せない。
  画面ごとに切り分けるなら `dumpsys gfxinfo <pkg> reset` を画面遷移の境目で打つしかない。
- 今回それを補ったのが**アプリ自身の診断機構**だった: `shared_prefs/app_prefs.xml` の
  `diag_last_screen` が `reading/{bookId}/{startFile}` を保持しており、主戦場が読書画面だと特定できた
  （`PrefKeys.DIAG_LAST_SCREEN`）。**自前の診断値は実機調査で効く**という実例。
  ただし読めるのは debuggable ビルドのときだけ（`run-as`）。release を入れた後は読めない。

## 含意（次にこれを見る人へ）

- **読書画面の描画コードに決め打ちで手を入れない**。`RubyText.kt` は Paint の remember 化・
  `RubyPositionCache` による位置計算キャッシュ・ascent の事前算出まで済んでおり、`ChapterContent.kt` も
  LazyColumn + `contentType` + TextStyle の hoist が入っている。**既に最適化された後の 10.11%** である。
  重いのは draw 記録と GPU 発行であってレイアウトではない、という切り分けまでが今回の到達点。
- **数値は debug ビルドのもの**（`flags=[ DEBUGGABLE ]` を実機で確認）。R8 未適用ぶん本番より悪く出る。
  release との比較は同じ「実使用」同士で取らないと意味を持たない。
- **交絡因子を落とさない**: 同端末では重量級ゲーム（HoYoverse）が並行稼働していた。P30 は 2019 年の
  Kirin 980・60Hz 固定で、熱と CPU/GPU の奪い合いが jank に乗る余地がある。アプリ単独の性能とは限らない。

関連: `/device-verify`（ColorOS 側の症状表）・[macrobenchmark-frametiming-scroll-pitfalls.md](macrobenchmark-frametiming-scroll-pitfalls.md)・
[device-screen-lock-breaks-benchmark-two-ways.md](device-screen-lock-breaks-benchmark-two-ways.md)。
