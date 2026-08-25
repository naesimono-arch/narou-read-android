# 縦書き本文の `input swipe … 100` は「fling に食われて動かない」わけではない（事実は逆）

## 結論（2026-08-26 実測で確定）

**「100ms の速いスワイプは fling として食われて1列も動かない」は誤り。**
縦書き本文（`ui/VerticalChapterContent.kt` の LazyRow）は 100ms のスワイプで**確実に動く**。
しかも 500ms のドラッグより**大きく動く**——速いスワイプは 0.6W のドラッグに fling が上乗せされるため。
`ChapterFlipBenchmark.flipChaptersVertical`（`swipeVerticalForward` が 100ms）は健全で、
過去の縦書き計測は「何も動いていないのに数字が出た」ものではない。

## 実測（emulator-5554 / 1080x1920・420dpi・縦書き・実 HTML 章 18KB の書籍）

スワイプは `input swipe 216 960 864 960 <dur>`（＝ベンチの 0.2W→0.8W・y=画面中央と同形）。
観測量は **LazyRow のスクロール位置**（`progress.scrollIndex`）と可視列 desc の SHA1 署名。

| 尺 | 1発ごとの scrollIndex | 1スワイプあたり |
|---|---|---|
| 100ms | 0 → 17 → 34 → 56 | **+約18〜20 index** |
| 100ms（`run_scroll_trace` と同じ「次章タップ後」文脈） | 0 → 21 → 40 → 56 → 74 | +約18〜21 index |
| 500ms（同文脈） | 0 → 4 → 9 → 14 → 20 | **+約5 index** |

- 章見出し（LazyRow item #0）の desc ノードは 100ms×3 で a11y ツリーから消える
  ＝ベンチ末尾の `Until.gone(chapterMarker(1, vertical=true))` は実際に真へ反転する。
- 100ms×30（各 400ms 間隔＝`advanceVerticalReading` の再現）で **chap_1 → chap_3**。
  これは 2026-08-21 の PGEM10 実機プローブ「30スワイプで第1章→第3章」と一致する
  ＝**エミュと実機で同じ挙動**（`ChapterFlipBenchmark.flipChaptersVertical` の KDoc の数値）。
- 別エミュ（emulator-5556 / 1080x2400・別書籍）でも 100ms で 0 → 6 → 14 → 22 と進む
  ＝端末・書籍に依らない。

## なぜ「速い方が大きく動く」のか

`input swipe` は DOWN→複数 MOVE→UP を注入し、UP 時点の速度が Compose の `scrollable` に渡って
fling になる。尺が短いほど同じ移動量 0.6W を高速で運ぶ＝UP 時の速度が大きい＝fling の減衰距離が伸びる。
500ms のドラッグはほぼ等速の低速移動で終わるので、運ぶのは**素のドラッグ分だけ**。
つまり「fling として食われる」という機序は存在しない——fling は消費でなく**上乗せ**。

なお `ChapterPullConnection` は `NestedScrollSource.UserInput` のデルタしか触らず、fling（SideEffect）は
素通しする＝章送りの引っ張り機構が本文スクロールを飲み込むこともない。

## 誤りの出どころ

`tools/measure_typeset_work.sh` の `run_scroll_trace` にある
「⚠️ 100ms の速いスワイプは fling として食われて**1列も動かない**」というコメント。
git で追うと**ファイルを新設したコミットと同じコミットで書かれており**、裏付けの実測記録が無い。
`handover.md` の「`ChapterFlipBenchmark` の縦書き軸が実は動いていない疑い」はこの一文だけを根拠にしていた。
⚠️ 500ms のドラッグ自体は「1スワイプの移動量を小さく固定したい」計測としては妥当なので、
直すべきは**尺ではなくコメントの理由**（現状は事実と逆のことを教えている）。

## ⚠️ 「動かない」を誤って作り込む観測の落とし穴（実際に踏んだ）

1. **面の取り違え**: 存在しない bookId で deep link すると本棚のまま前面に居座り、スワイプしても
   何も動かない＝「1列も動かない」が簡単に再現できてしまう。エミュごとに蔵書が違うので特に踏みやすい。
   ⚠️ `measure_typeset_work.sh` の `assert_orientation` は**この誤りを止められない**——
   判定が「desc 付き縦長ノードが横長より多いか」なので、**本棚の書影（縦長）でも `vertical` を名乗る**。
2. **章末に着いていた**: 短い章（数KB）では数スワイプで章末に達し、以後は継続導線や目次へ抜ける。
   そこから先のスワイプは本文を動かさない。
3. **`progress` の書き込みは遅延する**: スワイプ直後に読むと更新されていない（0.4s 間隔の連打では
   10発ぶん 0 のままだった）。**短時間の比較を `progress` だけで判定しない**——
   可視列 desc の署名（＝画面の実内容）と併用する。

## 教訓

「操作が効いていない」という主張は、**見た目の印象ではなくスクロール位置の観測量**で確かめる。
ジェスチャの尺を変えて直ったように見えたときは、真因が尺ではなく**面・章末・観測遅延**のどれかを疑う
（3つとも「尺を伸ばしたら動いた」ように見える偽相関を作る）。

## 関連

- `docs/knowledge/vertical-reading-has-no-text-nodes-for-ui-automation.md` — 縦書き面を掴む徴（desc）
- `docs/knowledge/compose-fresh-content-input-dead-window.md` — 章切替直後の入力デッドウィンドウ（別現象）
- `docs/knowledge/ranking-pager-jank-slow-ui-thread.md` — 「走っていないものを率で比べる」失敗の一般形
