# Compose 新規 Content の入力デッドウィンドウ（uiautomator 連打ベンチの操作不発）

★★★ 2026-07-18 — 画面部品が作り直された直後、`onSizeChanged` で実測サイズが入るまでの1〜2フレームは
サイズ依存 clamp（`min/max = ±bodyWidthPx` 等）が 0 に潰れ、**その間に注入したジェスチャは黙って無視される**。
この窓は a11y ツリーから観測不能＝uiautomator の wait では跨げず、固定マージンでしか回避できない。

## 症状

- ChapterFlipBenchmark（章送り連打）が**2回目以降のスワイプで間欠的に不発**（発生率 実測3〜5割/スワイプ）。
  fail 時の a11y ツリー診断は旧章タイトルのみ＝章送り自体が起きていない。**1回目は絶対に落ちない**。
- 注入方式を替えても症状不変: UiObject2.swipe（遅い注入）→ UiDevice.swipe（高速 steps）→ shell `input swipe` の
  3方式すべてで再現。一方、**手動の `input swipe` を +250〜700ms 間隔で打つと 10/10 全弾命中**＝アプリは健全。

## 真因（全観測と唯一整合する機序）

1. 章切替で `ChapterScreenContent` は**毎回作り直され**、`bodyWidthPx`（`onSizeChanged` で実測）は 0 から始まる。
2. draggable の clamp は `min = if (canGoNext) -bodyWidthPx else 0f`＝**幅未確定の初期化窓では min=max=0**。
   窓内に注入されたドラッグは全 delta が 0 に潰れ、settle は `offset==0` で発火せず＝「無視」に見える。
3. ベンチの「旧章タイトルの gone」検知は**スライドアニメ末尾（旧章が画面外に出た瞬間）で真になる**
   （Compose は画面外ノードを a11y ツリーから除外する＝`compose-offscreen-nodes-pruned-from-a11y-tree.md`）。
   これは `onNavigateTo`（recomposition＝新 Content 生成）より**手前**＝gone 直後のスワイプは初期化窓を直撃する。
4. 「失敗は常に2回目以降」の構造的裏付け: 初章の Content は画面入場時に生成され、最初のスワイプ時点で
   幅確定済み＝窓が存在しない。

## 対処（ベンチ側・main 無改変）

- コミット検知（gone＋次章 hasObject）の**後に固定マージン `Thread.sleep(400)`** を置いて窓を跨ぐ
  （400ms＝手動実証帯の中央値相当）。sleep 中は静止＝フレームが出ないため FrameTiming の分位を汚さない。
- 検知は「次章タイトルの出現」だけでは不可（引っ張りプレビューが settle 前から同テキストを描く＝偽陽性）。
  「旧章タイトルの gone」を主信号・次章 hasObject を従信号にする。`waitForIdle` は Compose のアニメを
  busy と見なさないため代替にならない。

## なぜそうなるか / 教訓

- 「UI が見えている」（a11y にノードが在る）と「入力を受け付ける」（サイズ依存 clamp が開いている）は
  **別のライフサイクル段階**。uiautomator が観測できるのは前者だけで、後者に相当する信号は存在しない。
- アプリ実装としては窓が1〜2フレーム＝実指では踏めないため製品バグではない（`NativeReadingScreen` の
  settle 内「破棄されない経路に備え防御的に戻す」コメントの裏面にあたる）。ベンチ・自動操作だけが踏む。
- 切り分けで役立った道具: fail 時に `findObjects(By.textStartsWith("第"))` で**ツリー内の実在テキストを列挙**
  （「操作不発」と「検知の偽 FAIL」を1発で弁別）／shell `input swipe` 連打による**アプリ健全性の対照実験**。
