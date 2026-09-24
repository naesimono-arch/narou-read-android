# 直列消費ループに「不定長の suspend」を入れると後続が詰まる（M3 Snackbar の actionLabel はその一例）

**事象（2026-07-20 実機観察→2026-07-23 真因確定）**: Web取込で「取り込み中です…」が取込完了後も
残留し、完了メッセージが表示されない（遅れて埋もれる）。

## 機序

**真因は「1本の collect で順に処理する経路に、完了時刻を自分で決められない suspend が入ること」**。
`actionLabel` はその suspend を作ってしまう入口の1つにすぎない（＝引き金であって真因ではない）。

1. Material3 の `SnackbarHostState.showSnackbar(message, actionLabel)` は、`actionLabel != null` のとき
   `duration` の既定が `SnackbarDuration.Indefinite` になる（明示指定しない限りユーザーが閉じるまで消えない）。
2. `showSnackbar` は表示中ずっと suspend する。単一の `collect { showSnackbar(...) }` でイベントを直列消費する
   設計（本アプリの `errorEvents`＝`Channel(BUFFERED)`）では、Indefinite 1件が collect を塞ぎ、
   後続イベント（完了通知など）が Channel バッファで待機し続ける。
3. 結果、「進行中」を Indefinite スナックバーで出す設計は〈残留＋後続埋没〉の複合を構造的に生む。
   複数重複時の「閉じた直後に即再表示」（2026-07-16 実機切り分け）も同根。

## 同じ機序の別の現れ方（actionLabel が無くても起きる）

- **`duration = Indefinite` を明示指定した場合**——`actionLabel` の有無は無関係。既定値の話は「気づかず
  Indefinite になる」経路であって、明示すれば同じことが起きる。
- **消費ループ内でユーザー操作の結果を待つ形すべて**: 確認ダイアログの結果を `CompletableDeferred.await()`
  で待つ／権限リクエストの結果を待つ／`animateTo` の完走を待つ／`awaitDispose`。
  いずれも**完了時刻を自分で決められない** suspend＝同じ詰まりを作る。
- **`Long` を連打した場合**——1件あたりは有限だが直列なので遅延が累積し、「遅れて出る」側の症状だけが出る。
- **バッファが有限だと症状が反転する**: `Channel(BUFFERED)` が満杯になれば送信側まで詰まり、
  `trySend` で送っていれば**黙って捨てられる**。「残留」が「取りこぼし」へ化けるだけで根は同じなので、
  取りこぼしを見て「Channel を大きくする」と対症療法になる。

## 判定軸（新しいコードを書くときにここだけ見る）

1. その経路は**直列で消費してよいのか**（イベントごとに独立なら直列にする理由が無い）。
2. ループの中で待つ suspend の**完了時刻を自分で決められるか**。決められない（ユーザー・外部要因）なら
   ループの外へ出す＝`launch` して別コルーチンで待つか、非ブロッキングの状態機構へ寄せる。

## 対処パターン（本アプリの採用形）

- **進行中表示をスナックバーに載せない**。継続状態は非ブロッキングの専用機構（本アプリでは
  `ProcessingState`→ProcessingBanner・全スキン `isProcessing` 駆動）へ寄せ、開始 set→`finally` で確実 clear。
- スナックバーは**完結した出来事の通知のみ**にし、自動消滅してよいものは actionLabel に頼らず
  Short を明示（本アプリでは `AppErrorEvent.transient` フラグで分岐）。
- 「閉じる」アクションが要るのはユーザーの判断・回復操作を伴うイベント（Blocked 公式送り等）だけ。

**根拠**: `BookshelfViewModel.importWebNovel`／`BookshelfScreen` の errorEvents collect（修正コミットの diff が正本）。
PDF 取込は当初から ProcessingBanner 方式でこの罠を回避しており症状が出ていなかった（対比が真因の傍証）。
