# 縦書き本文は a11y に text を持たない＝`By.text*` は原理的に空振りする

## 結論

読書画面が**縦書き**のとき、本文も章見出しも **`text` ノードを1つも持たない**。
`contentDescription` にだけ出る。したがって UI 自動化（macrobenchmark・androidTest・uiautomator）で
`By.text` / `By.textStartsWith` / `hasText` 系を使うと、**画面に着地しているのに必ず空振り**する。
縦書き面を触る自動化は **`By.desc` / `By.descContains`** を使うこと。

## 実測（2026-08-21・PGEM10 / Android 16 ColorOS・benchmark ビルド）

縦書きで章本文へ着地させ `uiautomator dump` を取った実際の結果:

- `content-desc="第 一 話　第1章"` … **1件ヒット**
- `text="…第…"` … **0件**

見出しは「話数ラベル＋題」を1ノードに束ねた形で desc に出る。ゆえに前方一致（`descStartsWith`）ではなく
**部分一致（`descContains("第1章")`）**でないと掴めない。
なお `第11章` 等が `第1章` に誤一致することはない（"第1章" が連続部分列として現れないため）。

## なぜそうなっているか（意図的な設計）

`ui/VerticalChapterContent.kt` は縦組みを**自前で組版して Canvas へ描く**。段落も見出しも
`clearAndSetSemantics { contentDescription = … }` で**子のノードを畳んで1本の読み上げ単位**にしている。
狙いは読み上げ品質＝当て字を著者読みへ置換した spoken を与え、1マスずつ置かれた文字（縦組みのラベル等）を
文字単位でバラバラに読ませないため。`clearAndSetSemantics` は子の semantics を**消す**ので、
副作用として text ノードが消える。⚠️ **横書きの本文段落も同じく text ノードを持たない**（2026-08-25 実測で確認）。
`ui/compose/RubyText.kt:128` が `clearAndSetSemantics { contentDescription = spokenText }` で
BasicText を畳んでおり、しかもこれは縦書き実装より**先にあった**（縦書き側がこれの移植）。
text に出るのは**章見出しだけ**＝`ChapterHeader` の Compose `Text`（話数ラベルと題）で、
これは横書き経路にしか無い。`By.textStartsWith("第N章")` が横書きで効くのは**見出しを掴んでいる**からで、
本文段落を掴んでいるのではない。

＝書字方向で違うのは**本文**ではなく**見出しの有無**（本文はどちらも desc 1本）。これは不具合ではないので直らない。

⚠️ **書字方向を判定したいとき、text/desc の有無は使えない**（本文はどちらも desc のみ）。
確実な徴は**本文段落ノードの縦横比**＝縦書きは列（細長い縦棒 h>w）・横書きは行の束（w>h）。
実装例＝`tools/measure_typeset_work.sh` の `assert_orientation`。

## これで実際に壊れた例

- 2026-08-19 `TabSwipeBenchmark` の**全5走行の iter000** が「本文に着地しない」で fail した。
  真因は端末の `app_prefs / reading_vertical` が縦書きのまま残っていたこと（書字方向は**全書籍共通の単一 Boolean**で、
  `install -r` はデータを保つため端末の値が効く）。着地判定が `By.textStartsWith("第1章")` だったため、
  着地しているのに検知できず、fail 文言は無関係な「progress リセット未反映」を疑わせていた。

## 書くときの作法（2つセットで）

1. **面を固定する**: ベンチ/テストのシードで書字方向を明示する（`LibrarySeedReceiver` の `verticalMode` extra）。
   端末に残った値で測ると、同じテストが日によって別の面を測る。
2. **徴を書字方向で分ける**: 横書き＝`By.textStartsWith` / 縦書き＝`By.descContains`。
   実装例＝`ChapterFlipBenchmark.chapterMarker(chapter, verticalMode)`。

⚠️ **fail 文言に切り分けを載せること**。「text で見つからない」だけでは
〈着地していない〉と〈着地しているが縦書き〉が区別できない。desc 側に居るかを見て両者を分けて報告する
（`TabSwipeBenchmark.openBookAndReturn` / `ChapterFlipBenchmark` が採っている形）。

## 関連

- 縦書きの**章送りは横書きの鏡像**で、親の `draggable` ではなく LazyRow 終端の未消費デルタ
  （`ui/ChapterPullConnection.kt`）経由でしか起きない＝**1スワイプ＝1章送りにならない**。
  実測で1章あたり約15スワイプ（PGEM10・50章シード）。縦書き面の自動化を書くときはここも併せて効く。
- `vertical-lazyrow-fast-swipe-is-not-eaten-by-fling.md` — **縦書きで「スワイプが効いていない」と思ったら開く**。
  100ms スワイプは動く（500ms より大きく動く）／⚠️ 上の `assert_orientation` は**本棚の書影でも vertical を名乗る**
  ＝面の取り違えを止められない。
