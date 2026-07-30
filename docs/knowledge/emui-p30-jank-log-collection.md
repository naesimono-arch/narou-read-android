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

### 1b. release(R8) の実使用（2026-07-31 回収・稼働 2h21m の累積）

同じ端末・同じ「持ち主に普通に使ってもらう」条件で、release(R8) 版の累積を非破壊で吸った結果
（`flags` に DEBUGGABLE 無しで release を確認・`Stats since` が2回の読み取りで不変＝reset されていない全寿命窓）:

| 指標 | debug (1h45m30s) | **release (2h21m)** |
|---|---|---|
| Total frames | 1207 | **27976**（標本23倍） |
| **Janky frames** | **122 (10.11%)** | **253 (0.90%)** |
| 50th / 90th / 95th / 99th | 6 / 16 / 22 / **61**ms | 5 / 8 / 9 / **15**ms |
| Missed Vsync | 7 (0.58%) | 4 (0.014%) |
| Slow UI thread | 49 (4.06%) | 76 (0.27%)（実数増・率は15倍改善） |
| Frame deadline missed | 50 (4.14%) | 86 (0.31%) |
| HISTOGRAM 末尾 | 400=1 450=1 **500=2** | 350=1 400=1・**450/500 は 0** |

`data_app_crash`・`data_app_anr` は **0件**（dropbox 432件は全て system_server_wtf / keymaster / system_app_* ＝システム側）。

⇒ **0.4〜0.5秒級の停止は消滅し、追うべき jank は残っていない＝深追い不要**（画面別 `reset` 切り分けも Perfetto も不要）。

⚠️ **この差を「コード改善の効果」と読んではいけない**。R8 適用の寄与とビルド差が混ざっている。
また `framestats` の10フレームは回収時アプリが背面だったため代表性が低く、**証拠力は累積 HISTOGRAM の側にある**。
メモリ（PSS 48.2MB）も背面トリム後の値で、前面時の前回値 108MB とは単純比較できない。

### 2. 濡れ衣に注意

logcat に出た `Choreographer: Skipped 132 frames!` は PID が `com.huawei.phoneservice` のもので、
当アプリとは無関係だった。**Skipped frames は必ず PID を引き当ててから読む。**

## 対処（実機を触る順序）

0. **端末は `adb.exe -s <serial>` で名指しする。`adb-bridge` を打ってはいけない**（2026-07-31 に判明）。
   P30 を USB で挿すと **Windows 側 `adb.exe` からしか見えない**（WSL の素の `adb` には現れない）一方、
   PGEM10 は tcpip で WSL 側が掴んでいる。この状態で `adb-bridge` を実行すると**既存 TCP（PGEM10）を
   優先して早期リターンする**ため、P30 のつもりで**PGEM10 から統計を吸ってしまう**
   （memory `adb-bridge-stale-tcp-holds-wrong-device` と同じ罠。2台繋がっているときに顕在化する）。
   最初に `adb.exe -s <serial> shell getprop ro.product.model` で `ELE-L29` を確認してから本題に入る。
1. **`adb logcat -g` で現在のバッファ量を確認し、既定へ戻っていたときだけ `-G 16M` を打つ**。
   `-G` は永続せず端末再起動で既定へ戻るが、**再実行はバッファを再確保して保持中のログを捨てる**——
   持ち主が使ってくれた時間帯のログが目的なら、既に拡大済みの端末で打ち直すのは有害無益
   （2026-07-31 実測: uptime 14日で 07-29 の設定が生存しており、打ち直していれば 2h14m 分を失っていた）。
2. **`dumpsys gfxinfo <pkg>` を先に、非破壊で吸う**。累積統計はプロセスが死ぬと消えるため、
   `force-stop`・再インストール・アプリ再起動より前に取る（`--reset` は破壊的なので回収前に使わない）。
   吸えた窓が本当に全寿命かは **`Stats since` が2回の読み取りで不変**なことで確かめられる（誰も reset していない証拠）。
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
