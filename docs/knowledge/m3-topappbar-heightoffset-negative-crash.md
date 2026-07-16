# M3 TopAppBar: heightOffset に実高超の負値を入れると負サイズで即クラッシュ（初期フラッシュは state で消せない）

**重要度**: ★★★
**確定日**: 2026-07-16（PGEM10 実機・material3 1.3.1 で実測）

## 事実

- M3 の `TopAppBarLayout` は**自身の layout 高さを「バー実高 + state.heightOffset」で計算する**
  （AppBar.kt:2206・compiled 1.3.1 で確認）。offset が「-実高」より小さいと
  `IllegalStateException: Size(w x 負値) is out of range` で**最初の measure で即クラッシュ**。
- `rememberTopAppBarState()` の既定は **offset=0（＝バー表示位置）・limit=-Float.MAX_VALUE**。
  つまり state は必ず「表示」側で生まれ、実測（TopAppBar が limit を実負値へ更新）前に畳むことはできない。
- 帰結①: 「没入入場なのに実測完了までの数フレームだけバー/システムバーが見える」フラッシュは
  **state の初期値では消せない**（実高不明のまま負の仮値を入れる sentinel 案は上記クラッシュ＝本リポで実測）。
- 帰結②: 対処は**描画側**で行う＝退避完了フラグ（実測待ち Effect の完了）まで両バーを
  `graphicsLayer { alpha = 0f }` で隠し、systemBars の show/hide 同期も同フラグでゲートする
  （実装＝`NativeReadingScreen.kt` の `barsVisualReady`）。
- 覆い隠しに注意: このフラッシュは章切替時の Loading（無地フレーム）に隠れて長く潜伏し、
  遷移をシームレス化（章キャッシュ）した瞬間に顕在化した。「遷移を速くしたら別のバグが見える」クラス。

## 検知の勘所

crash ログの `Size(横 x 負値) is out of range` ＋ stack に `AppBarKt$TopAppBarLayout` があれば本件。
負値 ≒（仕込んだ offset + バー実高)。
