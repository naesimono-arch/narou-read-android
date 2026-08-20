# M3 AppBar: 実高が測れる前に heightOffset へ負値を書くと負サイズで即クラッシュ（clamp は当てにならない）

**重要度**: ★★★
**確定日**: 2026-07-16（PGEM10 実機・material3 1.3.1 で実測）
**再確認**: 2026-08-21（現行の material3 **1.3.2**＝compose-bom 2025.07.00 の bytecode で下記3点を再確認）

## 事実

- M3 の `TopAppBarLayout` は**自身の layout 高さを「バー実高 + state.heightOffset」で計算する**
  （AppBar.kt:2206・compiled 1.3.1 で確認）。offset が「-実高」より小さいと
  `IllegalStateException: Size(w x 負値) is out of range` で**最初の measure で即クラッシュ**。
- `rememberTopAppBarState()` の既定は **offset=0（＝バー表示位置）・limit=-Float.MAX_VALUE**。
  つまり state は必ず「表示」側で生まれ、実測（TopAppBar が limit を実負値へ更新）前に畳むことはできない。

## 引き金は sentinel ではなく「実測前の書き込み」全般（2026-08-21 に一般化）

初出は「実高不明のまま負の仮値を入れる sentinel 案」だったが、**sentinel 固有の話ではない**。
clamp が在るのに効かない／そもそも通らない、という穴が2つある。

1. **setter の clamp は limit 次第**: `TopAppBarState.heightOffset` の setter は
   `coerceIn(heightOffsetLimit, 0f)` を通す（1.3.2 bytecode: `RangesKt.coerceIn` 呼び出しを確認）。
   ところが `heightOffsetLimit` を**実負値へ書き換えるのは TopAppBar 自身**（`SingleRowTopAppBar` 内の
   ラムダが `setHeightOffsetLimit` を呼ぶ）で、それまでは既定の **-Float.MAX_VALUE**
   （`rememberTopAppBarState` の第1引数既定＝bytecode で `-3.4028235E38f` を確認）。
   **実測前はどんな負値も clamp をすり抜ける**＝sentinel でなくとも、実測前の書き込みは等しく危険。
2. **コンストラクタは clamp を一切通らない**: `TopAppBarState(limit, heightOffset, contentOffset)` は
   backing の `_heightOffset` へ**直代入**する（bytecode: `mutableFloatStateOf` の結果を `putfield`。
   setter を経由しない）。したがって以下は**タイミングに関係なく無検査**:
   - `rememberTopAppBarState(initialHeightOffset = <負値>)`
   - **`TopAppBarState.Saver` からの復元**（restore はこのコンストラクタを呼ぶ）。
     ＝**保存時のバーより低いバーへ復元されると負サイズになりうる**。回転・フォントスケール変更・
     バーのバリアント差し替え・プロセス death 復帰が該当（本リポでの実測は未実施＝機序からの帰結）。
- **同じ形が BottomAppBar にもある**: `BottomAppBarStateImpl` もコンストラクタ直代入＋setter だけ
  `coerceIn(heightOffsetLimit, 0f)`（1.3.1/1.3.2 bytecode で確認）。TopAppBar 限定の罠ではない。

## 帰結

- 帰結①: 「没入入場なのに実測完了までの数フレームだけバー/システムバーが見える」フラッシュは
  **state の初期値では消せない**（初期値経路がまさに上の②＝無検査で落ちる）。
- 帰結②: 対処は**描画側**で行う＝退避完了フラグ（実測待ち Effect の完了）まで両バーを
  `graphicsLayer { alpha = 0f }` で隠し、systemBars の show/hide 同期も同フラグでゲートする
  （実装＝`NativeReadingScreen.kt` の `barsVisualReady`）。
  **「レイアウトを畳んで隠す」ではなく「描かずに隠す」**が一般解——高さは実測後にしか触れないから。
- 覆い隠しに注意: このフラッシュは章切替時の Loading（無地フレーム）に隠れて長く潜伏し、
  遷移をシームレス化（章キャッシュ）した瞬間に顕在化した。「遷移を速くしたら別のバグが見える」クラス。

## 検知の勘所

crash ログの `Size(横 x 負値) is out of range` ＋ stack に `AppBarKt$TopAppBarLayout` があれば本件。
負値 ≒（仕込んだ offset + バー実高)。書いた側を探すときは sentinel だけでなく
**`heightOffset =` の直接代入 / `initialHeightOffset =` / Saver 復元**の3経路を疑う
（`rg -n 'heightOffset\s*=|rememberTopAppBarState\(' -g '*.kt'`）。
