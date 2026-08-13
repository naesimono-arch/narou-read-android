# ランキング期間スワイプの平坦化（遷移 jank 残③・2026-08-06）

対象ブランチ: `review/code-health-2026-08-06`／対象: `ui/skins/k/DiscoveryHomeK.kt`・`ui/discovery/RankingSkeleton.kt`（＋新設）

## 真因（再調査不要・裁定済み）

1ページ＝取得件数ぶん（`DiscoveryQuery().limit` = 30）の**素の Column** が、外側 LazyColumn の
**単一 item**（≈2800dp）として抱かれている。LazyColumn が間引けるのは item 単位なので、
画面外の行まで全数が合成され display list に記録される＝`draw/record` 一点集中（max 131〜167ms）。
ドラッグで隣ページが可視化した最初のフレームでは、その一括合成が `animation` 段と同居する（max 106.4ms）。
`measure/layout`・GPU が無罪・縦スクロールが 2.29% であることと整合する（＝ページ実体化だけが重い）。

## 設計の柱

### ① 平坦化単位＝「順位1行」（＝外側 LazyColumn の1 item）

`items(count) { i -> 行スロット }`。可視行（実機で 5〜7 行）だけが合成・記録される。
- 単位を「行」にする根拠: LazyColumn の間引き粒度が item であること・行が
  `NovelListRow`／骨行という既存の再利用単位でそのまま切れること。
- Empty/Error（status 1行）は要素数1の同じスロットで包む＝分岐を増やさない。

### ② ページ高＝現在ページだけから決まる（構造で保証）

`HorizontalPager` は**行を持たなくなる**。ページ高という概念が消え、ランキング領域の高さは
「現在期間の行スロット群の総和」＝**現在期間だけ**で決まる。
`beyondViewportPageCount` の副作用（隣ページの高さに引きずられる／`docs/knowledge/pager-resident-pages-break-wrap-height.md`）は
原理的に起こり得なくなる。

隣期間の覗き（ドラッグ中に横から入ってくる行）は、各スロット内に**重ねて**描き
`Modifier.matchParentSize()` を与える＝**親（現在行）の高さ決定に参加しない**。
はみ出しはスロットの `clipToBounds()` が切る＝現状の Pager が「wrap 高＝現在ページ準拠で隣を
クリップして覗かせている」挙動と同じ性質を、行単位で再現する。

### ③ 状態の持ち方（既存の単一情報源を壊さない）

`rememberPagerState`（`rankingPagerState`）を**そのまま残す**。order との双方向同期
（`settledPage`→`onSelectOrder`／`LaunchedEffect(order)`→`animateScrollToPage`）と
タブ選択表示（`currentPage` 由来）は無改変。

- **ジェスチャ供給**: 各行スロットに
  `Modifier.nestedScroll(rankingEdgeSeal).scrollable(pagerState, Horizontal, flingBehavior = PagerDefaults.flingBehavior(pagerState))`。
  `PagerState` は `ScrollableState` なので、Pager 本体が無くても指のドラッグ・フリング・スナップを
  従来どおり駆動できる（＝操作感を自前実装で作り直さない）。
- **layoutInfo の供給源**: `PagerState` のページ換算は `layoutInfo.pageSize` に依存し、これは Pager の
  measure だけが供給する。よって**高さ0・中身空の `HorizontalPager`（アンカー）**を1つ置く。
  置き場所は外側 LazyColumn の**外**（固定トップの直下）＝縦スクロールで画面外へ出ても measure が
  止まらない（LazyColumn の item に置くと剥がれた瞬間 PagerState が凍る）。
- **隣期間の実体化条件**: `pagerState.isScrollInProgress`（離散 State＝ドラッグ開始/終了の2回しか
  変化せず毎フレーム再コンポーズを増やさない）。静止中は現在期間の可視行のみ。
- **translationX**: `graphicsLayer { }` ラムダ内で `currentPageOffsetFraction` を読む
  ＝State 読みが layer 更新に閉じ、composition/layout をスキップする（deferred read）。

### 期間タブ sticky 化（別途モック裁定待ち）への拡張性

`OrderTabsK` は従来どおり LazyColumn の独立 item に据え置く。行が同階層の item 群になったことで、
裁定が下りたら `item {}` → `stickyHeader {}` の置換だけで sticky 化できる（行側の変更が要らない）。

## 却下した案

- **`beyondViewportPageCount=1`**（2026-07-31 撤回済み）: 高さ規約と両立しない。機序は上記 knowledge。
- **`key`/`contentType` の付与だけ**: 対象が存在しない（ページ内は素の `Column`＋`forEachIndexed`）。
  平坦化後は items に付与する＝この案は「平坦化の従属物」として吸収される。
- **Pager 内に縦 LazyColumn を入れ、ランキング領域を固定高にする**: ページ全体が1本で縦スクロールする
  現状の見えが崩れる（ランキングだけ内部スクロールになる）＝見た目不変の原則に反する。
- **画面全体を〈固定ヘッダ＋各ページ独立 LazyColumn の Pager〉へ組み替える**: 気分/ジャンルの
  collapsing 連携が要り、かつ縦スクロール位置が期間ごとに独立してしまう（現状は共有）。工事規模も過大。
- **可視ウィンドウ外の行を Spacer へ置換（自前間引き）**: 行高が可変で初回は全行測定が要り、
  一番重い初回フレームが救われない。
- **`AnchoredDraggableState` で自前スナップ**: Experimental API 依存＋フリング/スナップ/端封止を
  再実装することになる。`PagerState` 温存（アンカー Pager）の方が既存の操作感と回帰テストを保てる。
- **隣期間の覗きを廃止（ドラッグ中は背景のみ）**: 最軽量だが操作感が変わる＝原則違反。採らない。

## 補助策（温存・今回は入れない）

案2（`isScrollInProgress` 連動の defer）は、③で「隣期間の実体化条件」として**必要最小限だけ**取り込む。
本体の遅延（現在期間の行まで defer する）は主対処が効けば不要なので入れない。

## a11y / テスト整合

- 隣期間の覗き行は `clearAndSetSemantics {}`（気分ゴースト格子と同じ流儀）＝TalkBack に二重に読ませない。
- `rankingPageTestTag(order)` は**現在期間の行スロット側**に付け直す（`hasAnyAncestor(hasTestTag(...))`
  で数える既存テストの契約を維持）。
- 骨は平坦化で行ごとに分かれるため、`RankingSkeletonDescription` を名乗るのは**先頭の骨行だけ**に限定し、
  残りは `clearAndSetSemantics {}` で黙らせる（30回読み上げの退行を作らない）。

## 実装で確かめた（2026-08-06・確定）

- 高さ0・中身空の `HorizontalPager`（アンカー）は `layoutInfo.pageSize` を正しく供給する。
  `DiscoveryHomeKRankingTest` の3件（横スワイプで期間送り／タブタップで追従／端での余りが外側タブへ伝播しない）が
  そのまま通ることが証拠＝ページ換算・スナップ・端の停止が標準実装のまま生きている。
- `scrollable` に渡す向きは `reverseDirection = true`（横 LTR で「左へ払う＝次ページ」）。
  Pager 内部の既定と一致し、上記テストの swipeLeft→次期間が成立する。
- `currentPageOffsetFraction > 0` のとき進行方向は次ページ（右隣）。覗きは
  `-fraction * step + (if (fraction > 0) step else -step)` に置く。
- 見た目の不変は K の golden 8 件（`DiscoveryHomeKScreenshotTest`）が通ることで担保された。

## 実機計測で見る点（次便）

- ジャンク率 11.89〜12.23% と `draw/record` max 131〜167ms が下がること（可視行だけの合成になったか）。
- ドラッグ開始フレーム（`isScrollInProgress` が立ち隣期間の覗きが実体化する瞬間）が新しい山にならないこと。
  ここが重ければ、温存してある補助策（案2＝覗き自体の defer）を足す余地がある。
