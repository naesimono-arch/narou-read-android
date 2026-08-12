# ランキング横スワイプ: 平坦化とページ高の構造設計

- **対象ブランチ**: `docs/agy-revival-design`
- **日付**: 2026-08-13 ／ **成果物の種別**: 設計のみ（コードは1行も変更していない）
- **前提（再調査・再計測しない確定事項）**: ランキング横スワイプ Janky 11.89%→12.23%／8.90%（改善も退行もなし）。
  `draw/record` 一点集中（max 131〜167ms）＋最悪フレームでは `animation` 段（max 106.4ms）が同時に載る。
  `measure/layout` max 0.5ms・GPU 17ms 止まり＝両方とも無罪。同じ面の縦スクロールは 2.29% と静か。
  潰れた案＝①`beyondViewportPageCount=1`（実機退行・機序は `docs/knowledge/pager-resident-pages-break-wrap-height.md`）
  ②`key`＋`contentType`（対象不在＝ページ内は素の `Column`＋`forEachIndexed`）。

---

## 0. 結論

**入れ子の向きを反転させる**。いまは〈縦 LazyColumn（外）→ 横 Pager（内・wrap 高）→ 素の Column 30行〉。
これを〈**非スクロールのヘッダ ＋ 横 Pager（外・ビューポート高固定）→ 縦 LazyColumn（内）→ 行 = item**〉にする。

- (a) 平坦化は「外側 LazyColumn の item へ行を置く」ではなく「**行を lazy コンテナの直下 item にする**」として達成する
  （＝可視行だけ合成する、という目的そのものは同一。literal 版を採らない理由は §2.7）。
- (b) ページ高は「現在ページだけから決める」よりも強く、**内容から決めない**（＝ビューポート高固定）。
  全ページが同高になるので ① の退行（未訪問ページの骨にページャ高が引かれる）は**構造的に起きえない**。
- (a) と (b) は同じ1つの構造変更の表と裏で、**分離できない**（§4）。段階は「構造 → プリフェッチ復活 → 実機計測」で切る。
- sticky 化（期間タブ固定）は**別便**にする。ただし本便が作る器の「畳む/畳まない」の境界パラメータが sticky の実装点そのものになる（§8）。

---

## 1. 現行構造の事実（行番号つき）

### 1.1 木の形（`android/app/src/main/java/com/novelreader/ui/skins/k/DiscoveryHomeK.kt`）

| 位置 | 実体 |
|---|---|
| L187-192 | ルート `Column`（`fillMaxSize` + background + `statusBarsPadding`） |
| L194 | `SearchHeaderK`（固定トップ・スクロールしない） |
| L196-200 | **画面唯一の縦スクロール** `LazyColumn(weight(1f))`、`contentPadding(start/end = S24, bottom = S24)` |
| L201 / L202 / L203 | item: `MoodSectionK` ／ `GenreSectionK` ／ `SectionHeadingK("ランキング")` |
| L204-212 | item: `OrderTabsK`（`Modifier.nestedScroll(rankingEdgeSeal)`） |
| L213-223 | item: `RankingPagerK`（同じく `rankingEdgeSeal`）← **ここが単一 item として 30 行を丸ごと抱く** |
| L224 | item: `OfficialLinkK` |

Pager 本体は L534-567。`pageSpacing = Spacing.S24`（L561）・`verticalAlignment = Alignment.Top`（L563）。
`beyondViewportPageCount` は**意図的に未指定＝0**で、L537-557 に「足してはいけない」理由が長文で残っている。

ページ1枚は `RankingPageK`（L600-639）＝`Column(fillMaxWidth + testTag(rankingPageTestTag))`（L610-617）で、
when 分岐（L618-637）が〈現在ページ×Content→`RankingRowsK`〉〈Empty/Error→1行 status〉〈控えあり→`RankingRowsK`〉
〈それ以外→`RankingListSkeleton()`〉を選ぶ。行の生成は `RankingRowsK`（L643-658）の
`content.novels.forEachIndexed { NovelListRow + HorizontalDivider }`（L648-657）＝**lazy でない全数展開**。

状態の所有:
- `rankingPagerState`（L136-139）＝画面スコープ。`order`（VM の `homeOrder`）と双方向同期（L142-161）。
- `rankingContents: SnapshotStateMap<NarouOrder, Content>`（L165-171）＝期間別 stale-while-revalidate の控え。
- `rankingEdgeSeal`（L178-186）＝横成分の余りを全量食って外側タブ Pager（`TabPagerHost`）へ渡さない封止。
  **タブ Pager とランキング Pager は別物**で、`TabPagerHost.kt` L70-79 の `beyondViewportPageCount`（L76）は
  全ページ `fillMaxSize`＝同高だから安全、という関係（`docs/knowledge/…` L28-33）。

### 1.2 いまページ高がどう決まっているか（① の再発を語るための正確な記述）

外側 `LazyColumn` は各 item を**縦方向 maxHeight=Infinity の制約**で測る。したがって `HorizontalPager` は交差軸（縦）を
wrap する＝**測定したページの最大高**になる。既定 `beyondViewportPageCount=0` では測定対象が現在ページ1枚だけなので、
結果として「ページャ高＝現在ページ高」に**たまたま**なっている。①はこの「たまたま」を壊した。

### 1.3 1ページの物量（事実）

- 行数 = `RankingSkeletonRowCount = DiscoveryQuery().limit`（`RankingSkeleton.kt` L56 → `DiscoveryQuery.kt` L62 = **30**）。
  実データも同 limit なので、Content でも骨でも **30 行**。
- 1行 = `NovelListRow`（`DiscoveryCommon.kt` L212-）で `Text` は最大 7（順位 L226・題名 L239・作者 L252・
  ジャンル L264・連載状態 L280・読了目安 L286・順位指標 L296。後3者は null 可）＋区切り線。
  ⇒ **1ページあたり最大 210 個のテキスト計測**。
- 骨（`RankingListSkeleton`・`RankingSkeleton.kt` L73-92）も `Column` + `repeat(30)` の全数展開。

**推定（数値）**: 行高は縦 padding S16×2=32dp ＋ 題名 21〜42dp ＋ 4+15 ＋ 8+15 ≒ **96〜117dp**。
30 行で **2,900〜3,500dp**、可視は 640dp 画面で **5〜7 行**。
（knowledge が「≈2800dp」と書いた値と同オーダー。以降この「可視は約 6 行」を設計の較正値として使う。）

### 1.4 なぜ `draw/record` に一点集中するのか

**事実**: `measure/layout` が 0.5ms しかないのに `draw/record` が 131〜167ms。`animation` 段が同時に載る。

**確度の高い部分（構造から断定できる）**:
1. ページが可視化された最初のフレームで **30 行ぶんの合成（composition）が丸ごと1フレームに乗る**。
   Compose の再合成は Choreographer の ANIMATION コールバックで回るので、これが `animation` 段の 106ms。
2. `Column` は lazy ではないので、**可視かどうかに関わらず 30 行すべてを測定・配置する**。
   Compose の測定/配置は View 階層の `onMeasure/onLayout` ではなく `AndroidComposeView.dispatchDraw` の中から回るため、
   gfxinfo の `measure/layout` バケットには乗らない——これが「Compose の測定が重いのに `measure/layout` 0.5ms」の説明。
   **確度: 高（推定）**。反証されても結論は変わらない（下の 3 が残る）。
3. 描画記録は可視外の行も**ノード木を全走査**する（Compose には View の quick-reject に相当するノード単位の間引きが無い。
   Skia のクリップ棄却は raster を省くだけで、走査とペイント準備は残る）。GPU が 17ms 止まりなのはこの「記録はするが
   ラスタしない」形と整合する。

**⇒ どの説明を採っても、効く手は1つ**: **可視行だけを実体化する**。1・2 は確実、3 は補強。

---

## 2. 案 (a): 平坦化の具体形

### 2.1 採る形＝入れ子の反転

```
Column(fillMaxSize, background, statusBarsPadding)
├ SearchHeaderK                                   ← 変更なし（固定トップ）
└ RankingScaffoldK                                ← 新設（SubcomposeLayout・§2.3）
   ├ header スロット（非スクロール・1回だけ合成）
   │   Column(padding horizontal = S24)
   │   ├ MoodSectionK / GenreSectionK / SectionHeadingK("ランキング")
   │   └ OrderTabsK                               ← 初手はここ（＝現行どおり畳まれて消える）
   └ body スロット（ビューポート高を固定で受ける）
       HorizontalPager(state = rankingPagerState,
                       modifier = padding(horizontal = S24).nestedScroll(rankingEdgeSeal))
       └ page → LazyColumn(fillMaxSize,
                           state = listStates[pageOrder],
                           contentPadding = PaddingValues(top = headerHeight, bottom = S24))
            ├ rankingRowsK(...)   ← itemsIndexed で行を item 化（§2.4）
            └ item { OfficialLinkK }
```

- **入れ子方向**: 現行 `LazyColumn(外) → Pager(内)` を **`Pager(外) → LazyColumn(内)`** へ反転。
- **外側 LazyColumn は廃止**。ヘッダは素の `Column`（スクロールしない代わりに、後述の折り畳みで画面外へ送る）。
- **横ジェスチャ封止（`rankingEdgeSeal`）は残す**——ランキング Pager が外側タブ Pager の子であることは変わらない。

### 2.2 ヘッダを「畳む」機構

ヘッダは Pager の上に**重ねて**置き、ページ側の `contentPadding(top = headerHeight)` で場所を空ける。
縦スクロールは `NestedScrollConnection` が先に受けて `headerOffsetPx`（`mutableFloatStateOf`・範囲 `[-headerH, 0]`）を動かし、
使い切れなかったぶんをページの `LazyColumn` へ流す（Compose の collapsing header の定石）。

肝は **`headerOffsetPx` を配置フェーズで読まないこと**。`placeWithLayer { translationY = headerOffsetPx }` の
**レイヤブロック内で読む**＝描画/レイヤフェーズの遅延読み取りになり、指追従の毎フレームは RenderNode のプロパティ
更新だけで済む（再合成も再測定も起きない）。ここを `Modifier.offset(y = …)` の値形で書くと毎フレーム再測定が走り、
いま倒そうとしているコストを別の形で復活させる。

### 2.3 ヘッダ高の受け渡しは `SubcomposeLayout`

`onSizeChanged` で高さを state に書き戻すと〈測定フェーズ→合成フェーズへの書き戻し〉になり、初回1フレームぶん
ページが頭からヘッダに隠れる。1パスで閉じる形にする:

```kotlin
SubcomposeLayout { c ->
    val header = subcompose(Slot.Header) { header() }.map { it.measure(c.copy(minHeight = 0, maxHeight = Infinity)) }
    val h = header.maxOfOrNull { it.height } ?: 0
    val body = subcompose(Slot.Body) { body(h.toDp()) }.map { it.measure(Constraints.fixed(c.maxWidth, c.maxHeight)) }
    layout(c.maxWidth, c.maxHeight) {
        body.forEach { it.place(0, 0) }
        header.forEach { p -> p.placeWithLayer(0, 0) { translationY = headerOffsetPx } } // 遅延読み
    }
}
```

`Constraints.fixed(...)` の一行が (b) の答えでもある（§3）。

### 2.4 ページ内の行の item 化

```kotlin
internal fun LazyListScope.rankingRowsK(content: Content, order: NarouOrder, onOpenDetail: (Ncode) -> Unit) =
    itemsIndexed(
        content.novels,
        key = { i, n -> n.ncode ?: i },          // ← ② が「対象不在」でなくなる
        contentType = { _, _ -> "rankingRow" },  // ← 行の再利用が効くようになる
    ) { i, n -> NovelListRow(rank = i + 1, …); HorizontalDivider(…) }
```

骨も同じく `LazyListScope.rankingSkeletonK(rowCount)` へ変える（`repeat` の Column のままだと骨が 30 行全数展開の
まま残り、初訪ページで同じ jank を再生産する）。**`RankingListSkeleton` の呼び出し元は K だけ**（`RankingSkeleton.kt`
と `DiscoveryHomeK.kt` L636 以外に本番の参照なし）なので、Column 版は残さず置き換えてよい。

**副産物**: 潰れたはずの案②（`key` 安定化＋`contentType`）は、平坦化によって**適用対象が出現する**。
本便では上のとおり最初から付ける（別便にしない＝行の再利用が効かないと平坦化の利得が目減りするため）。

### 2.5 スクロール状態の所有者と、期間を跨いだときの整合

- **所有者はページごと**: `remember { mutableMapOf<NarouOrder, LazyListState>() }` を画面スコープに置き、
  ページ合成時に `getOrPut(pageOrder) { LazyListState() }`（`rankingContents` L165 と同じ流儀＝期間別に持つ）。
  Pager がページを破棄しても位置が消えない。
- **ヘッダの折り畳み量は画面で1つ**（`headerOffsetPx`）。ここで整合を取らないと、A を送った状態（ヘッダ折り畳み済）で
  未訪問の B へスワイプすると、B のリストは先頭＝`contentPadding` の空白がヘッダの跡地として見える。
- **採る規則（規則B）**: ページが「現在ページ以外」の間は、現在ページの
  `firstVisibleItemIndex / firstVisibleItemScrollOffset` へ同期させる（`LaunchedEffect(targetPage)` で `scrollToItem`）。
  行と骨が同じ index 体系（1 item = 1 行）なので、**週間20位あたり → 月間20位あたり**という意味的にも自然な対応になり、
  ヘッダ折り畳み量とも自動的に一致する（index>0 なら双方とも最大折り畳み）。
  ⇒ **現行の「期間を変えても縦位置が飛ばない」体験を別機構で再現する**。
- 却下した規則: 「各期間が自分の位置を覚える」（ヘッダ跡地の空白が出る・現行体験の退行）／
  「非現在ページを常に先頭へ」（同上＋位置保持の放棄）。
  ⚠️ 規則Bは**体感の裁定余地がある**（§9-4）。実装点は `LaunchedEffect` 1つで、差し替えは数行。

### 2.6 なぜこれで `draw/record` が減るのか（機序）

1. **合成**: ページが可視化された最初のフレームで実体化されるのは、`LazyColumn` のビューポートに入る **約6行＋プリフェッチ1行**。
   30行 → 約7行＝**テキスト計測 210 → 約 49**。§1.4 の 1（＝`animation` 段 106ms の正体）が直接そのぶん縮む。
2. **測定・配置**: `LazyColumn` は可視域外の item を測らない。§1.4 の 2 が同じ比率で縮む。
3. **描画記録**: 走査対象のノードが可視域ぶんしか存在しない。§1.4 の 3 も同じ比率。
4. **指追従の毎フレーム**: ヘッダ移動もページ移動もレイヤ変換（`translationX/Y`）に閉じ、再合成も再測定も伴わない。
5. **プリフェッチが解禁される**（§4 の C2）: 全ページ同高になるので `beyondViewportPageCount=1` が
   `TabPagerHost` と同じ理由で安全になり、しかも隣ページの実体化コストが「30行」から「約6行」へ落ちているので、
   前倒しする仕事自体が軽い。①のときと違って、**前倒しの副作用（高さ汚染）も消えている**。

**⇒ 予測**: ランキング横の Janky は「さがす縦 2.29%／本棚 3.82%」の帯へ入る。合格線は §6 で数値化する。

### 2.7 採らなかった平坦化案（同じ道を再探索しないための記録）

| 案 | 形 | 却下理由 |
|---|---|---|
| a-Ⅰ **literal 平坦化** | 行を**外側 LazyColumn の item** に置き、`HorizontalPager` を廃して横ドラッグを自前実装 | Pager のページは自己完結した Composable でなければならず、「祖先 LazyColumn の item である行」をページにはできない。よって**ページャの運動（指追従・隣ページの覗き・フリング settle）を全部自前で書く**ことになる。さらにドラッグ中に隣期間を見せる置き場（ランキング区間を跨ぐ単一の親 Box）が存在せず、`onGloballyPositioned` で座標を拾うオーバーレイになる＝脆い。運動を諦めれば（＝ドラッグ中は現ページが滑るだけ）実装は縮むが、**意匠・運動の変更＝裁定とモックが必要**になり、性能改修の便に混ぜられない |
| a-Ⅱ' **行ペアリング** | item k に〈現ページの行k〉と〈次ページの行k〉を重ねて横に流す | 行高が期間で違うため item 高 = max(A,B) になり、**①と同じ「最大高に引かれる」病がページ単位から行単位へ移るだけ**。ドラッグ中に総コンテンツ高が動いて縦アンカーも揺れる |
| a-Ⅴ **窓出し（手製 lazy）** | ページを `Spacer + 可視行 + Spacer` で構成し高さは自前計算 | 行高が可変（題名1〜2行・fontScale）なので高さを**近似**するしかない＝近似禁止に抵触。実質 `LazyColumn` の再実装 |
| a-Ⅳ **wrap のまま内側 LazyColumn** | 外側 LazyColumn の item に `fillParentMaxHeight` のページ（内側 LazyColumn） | 同軸の入れ子スクロール。子が先に消費するので、リスト上でドラッグすると外側が進まず気分節が居座る（スクロールトラップ）。既知のアンチパターン |
| a-Ⅵ **行コストを削る** | `NovelListRow` の Text 統合／limit を 30→20 | 構造ではなく係数の改善。**補助レバーとして温存**（§9 の停止条件で使う）。単独では 30 行全数展開という形が残る |

---

## 3. 案 (b): ページ高の決め方

| 案 | 高さの決まり方 | ①（未訪問ページの骨に引かれる空白）| 評価 |
|---|---|---|---|
| **b-1 ビューポート高固定**（採用） | `Constraints.fixed(viewport)` で全ページ同一。内容に**依存しない** | **構造的に起きえない**（下記） | (a) と同じ変更で入る。プリフェッチも解禁 |
| b-2 `SubcomposeLayout` で現在ページだけ測る | 現在ページを測り、その高さを全ページに強制 | 現在ページ確定時は回避。ただしドラッグ中に「移る先の高さ」を出すには**移る先も測る**必要があり、そこで①が復活 | 高さ問題しか解かない（30行全数展開は残る＝jank は減らない）。**間に合わせにもならない** |
| b-3 固定高（行数×定数） | 定数 | 起きないが、題名の折返し・fontScale・Empty/Error で**内容が枠から溢れる／余る** | 近似禁止に抵触。却下 |
| b-4 親スクロールへ委譲 | 親が高さを与える | 起きない | 実体は b-1（誰が親かの言い換え） |
| b-5 現状維持 | 測定したページの最大高（=現在ページ1枚） | いまは起きていないが、`beyondViewportPageCount` を触った瞬間に壊れる**地雷付き** | 却下 |

**b-1 で ① が構造的に起きえない理由**: ① は「Pager の交差軸サイズ＝**測定したページの最大高**」という規則と、
「ページ高が中身の量で変わる」という性質の**積**で起きる。b-1 は後者を消す——ページは `Constraints.fixed` で測られるので、
中身が 1 行（Empty/Error）でも 30 行（骨）でも**測定結果は同じ値**になる。したがって max を取っても現在ページの高さと
一致し、`verticalAlignment` の設定も結果に効かない。knowledge が示した安全条件「全ページが同じ高さになるページャに限る」
（`pager-resident-pages-break-wrap-height.md` L33）を、**満たすように作り替える**のがこの案。

副次: `RankingListSkeleton` の存在理由が変わる。いまは「外側 LazyColumn の総高を保って先頭クランプを防ぐ」ための
高さ確保だが、b-1 後は**高さ確保としては不要**になる（ページ高は内容に依存しない）。ただし
「読み込み中の面を1行 status に畳まない／再訪の位置を保つ（規則B の index 同期先が骨の item である必要）」は残るので、
**骨は撤去せず lazy 化して残す**。この意味の変化は `RankingSkeleton.kt` L25-46 の設計コメントに追記が要る。

---

## 4. (a) と (b) の依存関係・段階導入

**両方セットが必須。片方だけでは動かない。**

- (a) だけ = ページを `LazyColumn` にしたい ⇒ 親の Pager が wrap 高（縦 maxHeight=Infinity）のままだと、
  `LazyColumn` は高さ 0（または「残り全部」を要求して破綻）になる。**lazy 化には有界な高さが要る**。
- (b) だけ（b-1）= ページをビューポート高に固定 ⇒ 中身が素の `Column` 30 行のままなら、ページ内に収まらない行が
  クリップされて**見えなくなる**（スクロール手段が無い）。**高さ固定には lazy 化が要る**。

⇒ **同一コミットでしか緑にならない**（§5 の C1 が不可分な理由）。
段階化は「(a)/(b) の分割」ではなく、**構造 → 効果の上積み → 計測**という別の軸で切る:

1. **C0**（src/test のみ・現行実装のまま緑）: L1 不変条件の観測点を「合成されていない」から「**表示されていない**」へ強化（§5.2）。
2. **C1**（src/main ＋ 追随するテスト）: 構造反転（(a)+(b)）。`beyondViewportPageCount` は **0 のまま**。
3. **C2**: `beyondViewportPageCount = 1` を復活（今度は安全）。
4. **C3**: 実機計測 → 合格なら終了、未達なら §9 の停止条件へ。

---

## 5. 移行手順とリスク

### 5.1 コミット割り（各コミットで緑を保てるか）

| # | 触る場所 | 内容 | 緑の保ち方 |
|---|---|---|---|
| C0 | `src/test` のみ | L1 の観測点強化（§5.2）。**現行実装に対して緑であることを確認してから入れる** | `testDebugUnitTest`。実装を変えないので golden も不変 |
| C1 | `src/main`（`DiscoveryHomeK.kt`・`RankingSkeleton.kt`）＋ `src/test` の追随 | `RankingScaffoldK` 新設・入れ子反転・行と骨の item 化・`listStates` と規則B・`key`/`contentType` 付与 | `testDebugUnitTest`（L1/L2/Skeleton/Ranking/golden）。**golden は再録が要るか要らないかをまず見る**（§5.3） |
| C2 | `src/main` 1行＋コメント | `beyondViewportPageCount = 1`。L537-557 の「足してはいけない」長文コメントを**「なぜ今は足してよいか」へ書き換える**（残すと嘘になる） | `testDebugUnitTest`。合成されるページ数が変わるので Skeleton/Ranking テストの前提コメントを同時に更新 |
| C3 | コミットなし | 実機計測（§6） | — |
| C4 | 別便 | sticky 化（モック裁定後・§8） | — |

**C1 が大きい**のは §4 のとおり不可分だから。分割するなら「純リファクタで関数境界だけ先に整える」ことは可能だが、
中間状態が動かないので**得るものが少なく、レビュー面も却って読みにくい**——C1 は1コミットで出し、
レビューは削除行込みの全量 diff で見る（品質保証サブの職務）。

### 5.2 既存テストへの影響（実名・具体）

- ⚠️ **`DiscoveryHomeInvariantTest`（L1）は現状のままだと K で偽陽性になる**。観測点が
  `assertNotComposed("きょうの気分")`（L128・L138・L152）＝「LazyColumn は可視域外 item を破棄する」という前提に乗っている。
  新構造ではヘッダは**常に合成されたまま画面外へ translate される**ので、この判定は必ず落ちる。
  → **C0 で「合成されていない」を「表示されていない」へ差し替える**（`onAllNodes(hasText(TOP) and isDisplayed())` が空、の形）。
  これは K のための緩和ではなく**強化**——「破棄された」も「画面外へ出た」も同じく通り、
  「潰れて先頭が見えている」だけを落とす、という**不変条件そのものの直接表現**になる。全実装に対して現行より厳密。
- `DiscoveryHomeKRankingTest`: `scrollListTo("累計")`（L140）・`scrollListTo("月間")`（L131）は、
  期間タブがヘッダ（常時可視）へ移るので**スクロール不要になり、探索が空振りして落ちる**。→ 該当行を削る。
  `scrollListTo` 自体（L110 の `onNode` ＝縦スクロールノードがちょうど1つ）は C1（`beyondViewportPageCount=0`）では
  1ページしか合成されないので成立するが、**C2 で 3 ページ＝縦スクロール 3 個になり `onNode` が落ちる**。
  → C2 で `onAllNodes(...).onFirst()` かページ tag での絞り込みへ。
- `DiscoveryHomeKSkeletonTest`: `scrollListTo` は既に `onAllNodes(...).onFirst()`（L80-82）なのでそのまま通る。
  ただし L90・L163-165 のコメントが「隣接ページは合成されない」を前提にしており、**C2 で事実でなくなる**。
  テスト③（L140-173）の「見る位置を変える」という判断そのものを、C2 で見直す。
- `DiscoveryHomeInvariantCoverageTest`（L2）: `DiscoveryUiState` を引数に持つ関数の**増減が両方向で落ちる**。
  `RankingPageK`/`RankingRowsK` の改名・`RankingScaffoldK` 等の新設に合わせ、
  `DiscoveryHomeRegistry.implementations` の K 行（`DiscoveryHomeRegistry.kt` L85-91）を更新する。
- `DiscoveryHomeKScreenshotTest`: `performScrollToNode(OFFICIAL_LINK)`（L115-117）は、公式リンクがページ内 LazyColumn の
  footer item になるので**そのまま通る**（縦スクロールノードは C1 時点で1つ）。C2 では上と同じ理由で絞り込みが要る。

**⚠️ いま `android/app/src/test/` は別エージェントが作業中**。上記のテスト追随は**本設計の実装便でまとめて行う**もので、
本便（設計）では一切触っていない。C0 を先に入れるか C1 に同梱するかは、監督が作業衝突を見て決める。

### 5.3 その他のリスク

- **golden 画像照合**: `home`（上端）は、ヘッダの横 padding を S24 のまま・ページ側 `contentPadding(top=headerH)` を
  正しく渡せば **y 座標が完全に一致するはず**＝差分なしを期待する。`ranking`（末尾送り）は
  `performScrollToNode` の停止位置が新旧でずれる可能性があり、**数 dp の差分が出る確度は中**。
  → 差分が出たら**必ず目視してから再録**（`docs/knowledge/golden-record-bakes-in-regressions.md`＝再録は退行を焼き込む）。
  「再録して緑」を作業の既定手順にしない。
- **`tools/check_design_tokens.py` / Spacing lint**: 新規に導入する寸法は
  「ヘッダ高（実測値・リテラルでない）」だけ。`contentPadding` は既存の `Spacing.S24`、pageSpacing も既存 S24 を踏襲＝
  **新しい raw dp を1つも増やさない**（増やすなら 7段スケール外の較正値として理由を書く必要がある）。
- **横方向の見た目**: 現行は Pager が外側 `contentPadding(horizontal=S24)` の内側にあるため、ドラッグ中のページは
  画面端から 24dp 内側でクリップされる。新構造でも **Pager 自身に `padding(horizontal = S24)` を付ける**ことで
  この運動を保つ（ページ側の contentPadding を横に持たせると、行が画面端までスライドして見え方が変わる）。
- **他スキンへの波及**: **無い**。ランキングの `HorizontalPager` は K だけ
  （`HorizontalPager` の本番利用は `TabPagerHost`・`WardrobeScreen`・`DiscoveryHomeK`・`BookshelfPortalJ` の4箇所で、
  M/P/J の発見面は単一 `LazyColumn` ＋ `itemsIndexed`）。共有部品で触るのは `RankingSkeleton.kt` だけで、
  その参照元も K のみ。ただし **P の `HiScoreBoard` は「親 LazyColumn の1 item に全行を抱く非遅延 Column」＝同型の形**
  （`DiscoveryHomeRegistry.kt` L80-82 が明記）なので、**同じ jank を持つ可能性がある**。本便の対象外だが、
  K の実測が良ければ P へ横展開する候補として記録しておく（**未計測＝断定しない**）。
- **a11y**: ヘッダは畳んでも合成されたまま（＝TalkBack の木に残る）。レイヤ変換は当たり判定にも効くので画面外の
  ヘッダを触ってしまうことは無いが、**TalkBack の読み上げ順に畳んだヘッダが残る**かは要確認（§9-3）。

---

## 6. 検証計画

### 6.1 JVM で守れるもの（＝ローカルゲート `testDebugUnitTest` と CI）

| 守る性質 | 担い手 |
|---|---|
| 期間切替で一覧が先頭へクランプしない／Empty・Error を骨や控えで覆い隠さない | `DiscoveryHomeInvariantTest`（C0 で観測点強化） |
| 期間別の控え・初訪ページの骨・期間を跨いだ控えの生存 | `DiscoveryHomeKSkeletonTest` |
| スワイプ↔タブ↔`order` の同期（発火はちょうど1回）・端ページでの外側タブ Pager への非伝播 | `DiscoveryHomeKRankingTest` |
| 版面（行の構造・タブの選択表示・気分枠の高さ・fontScale 2.0 の折返し） | `DiscoveryHomeKScreenshotTest`（golden） |
| 新実装の登録漏れ | `DiscoveryHomeInvariantCoverageTest`（L2） |
| トークン外の直書き寸法が増えていないこと | `tools/check_design_tokens.py` |
| public シグネチャ変更時の androidTest 追従 | `:app:assembleDebugAndroidTest` |

**新規に足す価値があるもの（C1 で追加を推奨）**: 「ランキング領域の可視行数が行総数より**少ない**こと」を
セマンティクス木のノード数で見るテスト。30 件の Content を与えて `NovelListRow` 相当のノードが 30 未満であることを
確認する＝**平坦化が効いていること自体の回帰**になり、将来「うっかり `Column` へ戻す」変更を落とせる。
（⚠️ ノード数は合成戦略に依存する脆い指標なので、閾値は「30 未満」の**一方向**だけにする。
`rankingPageTestTag` の KDoc L588-597 が戒めているのは「特定の数を期待する」形で、方向だけの検査はその戒めに反しない。）

### 6.2 JVM では守れない＝実機でしか見えないもの

- フレーム時間そのもの（Robolectric は実描画時間を測らない）。
- 折り畳みヘッダの指追従・フリングの引き継ぎの体感。
- 規則B（期間跨ぎの index 同期）の見え方。
- overscroll 表現（`rankingEdgeSeal` は余りを食うので端では静止＝現行どおりのはず）。

**計測条件（既存ベースラインに揃える・変更しない）**: PGEM10・60Hz・スキンK ライト・**12フリック/16秒**・gfxinfo framestats。
比較対象は 本棚グリッド 3.82%（p99 36ms）／さがす縦 2.29%（p99 29ms）／読書本文 1.88%（p99 28ms）／
**ランキング横 11.89%（p99 150ms）**。

**合格線（先に宣言する＝結果を見てから緩めない）**:
1. ランキング横 **Janky ≤ 4.0%** かつ **p99 ≤ 40ms** かつ **`draw/record` max ≤ 40ms**（＝縦スクロールの帯へ入る）。
2. さがす縦が **2.29% 帯を維持**（退行させていないこと）。
3. C1 単独と C2 適用後の**2点**で測る（プリフェッチの寄与を分離するため。①のときは分離しないまま撤回した）。

Macrobenchmark には該当便が無い（`TabSwipeBenchmark` はタブ層＝別物）。新設は**本便では提案しない**——
横スワイプのシナリオ記述コストに対し、まず1回の gfxinfo で構造の当否が判る段階だから。C3 で合格したら
「退行検知の常設」として別途検討。

---

## 7. `isScrollInProgress` 連動 defer を併用すべきか

**C1・C2 では採らない。捨てはせず、C3 の結果が未達だったときの第一候補として温存する。**

- 理由: defer が効くのは「ドラッグ中に重い実体化が走る」形。C1 で実体化は 30 行→約6行、C2 で**ドラッグ開始時点では
  隣ページが既に実体化済み**になる。つまり defer が消す対象そのものが構造的に消えている。
- 併用の害: ドラッグ中だけ中身を骨へ差し替える＝**ユーザーに見える表示の切り替わり**であり、意匠・運動の変更＝
  モックと裁定が要る。**まだ存在するか判らないコストのために、裁定コストと視覚的な副作用を先払いすることになる**。
- 例外的に併用が正当化される条件（C3 で観測されたら採る）: 平坦化後も `animation` 段が 30ms を超え、
  その中身が「ドラッグ中の隣ページ実体化」だと特定できたとき。そのときの適用先は**行ではなく気分節や画像**のような
  ヘッダ側の重い部品で、ランキング行ではない可能性が高い。

---

## 8. sticky 化（期間の現在地常時表示）と便を分けるか

**分ける（別便＝C4）。ただし本便で受け皿を作る。**

- **分ける理由**: sticky は **UI の追加**で、`skins/discovery-K.html` 下敷きのモック先行と裁定が必須
  （`/visual-language`・「UI追加は必ずモック先行」）。裁定待ちの意匠を性能改修と同じコミットに混ぜると、
  **golden 差分の原因が「構造反転」か「意匠変更」か切り分けられなくなる**——本便は golden 差分ゼロを期待している便なので、
  この切り分けを失うのは実害。
- **受け皿を作る理由**: 本便は「何が畳まれ、何が残るか」を必ず決める。sticky はまさにその境界の話なので、
  受け皿がないと後で**もう一度同じ構造を触る**ことになる。`RankingScaffoldK` は
  〈header スロット（畳む）／pinned スロット（畳まない）／body スロット〉の3スロット構成にし、
  **C1 では `OrderTabsK` を header 側に置く（＝現行どおりタブごと畳まれる・体験は不変）**。
  sticky の裁定が「期間タブ行を固定」で出たら、**C4 は `OrderTabsK` を pinned スロットへ移すだけ**になる。
- 裁定が別案（例: 現在期間チップを浮かせる）で出た場合も損はしない——pinned スロットは使わず、
  オーバーレイとして body の上に置けばよい。**どちらに転んでも構造の作り直しは発生しない**。
- ⚠️ 逆向きの依存は無い: sticky を先にやる合理性はない（現行構造で sticky を実装すると
  `stickyHeader` に依存した実装になり、C1 でそれごと作り直しになる）。**順序は C1 → C4 で固定**。

---

## 9. 未確定として残す点と停止条件

1. **`draw/record` の内訳の説明（§1.4 の 2）**は**推定**。断定していないし、断定しなくても対処は変わらない。
   ただし C3 で「可視行だけになったのに `draw/record` が 100ms 級で残る」なら、この推定が外れている＝
   **構造を積み増さずに停止**し、Layout Inspector の再合成カウント／Compose compiler report（`composables.txt`）で
   実体を取り直す。§2.7 の a-Ⅵ（行コスト削減・limit 30→20）はその後の話。
2. **golden の再録要否**は未確定（§5.3）。差分が出た場合、再録の前に必ず画像を目視し、
   意図した変化かを1件ずつ判定する。判定できない差分が1つでもあれば**再録せず停止**して監督へ上げる。
3. **TalkBack の読み上げ順**: 畳まれたヘッダ（常時合成・画面外）が読み上げ順に残るかは未確認。
   Compose のセマンティクス木は「クリップ後の領域が空なら除外」される挙動があるが、
   **レイヤ変換で画面外へ出た場合の扱いは実機で確かめるまで断定しない**。C3 の実機便で TalkBack 1周を含める。
4. **規則B（期間跨ぎの index 同期）は体感の裁定余地がある**（§2.5）。「20位あたりで揃う」が自然か、
   「期間ごとに位置を覚える」が自然かは、実機で触ってからユーザー裁定にかける。実装差し替えは `LaunchedEffect` 1つぶん。
5. **P の `HiScoreBoard` が同型の jank を持つか**は**未計測**（§5.3）。K の結果が良くても横展開は測ってから。
6. **ヘッダのフリング引き継ぎ**（リストのフリングがヘッダの折り畳みへ滑らかに連続するか）は実機確認事項。
   `onPreScroll` 経由でフリングのデルタも流れる想定だが、体感は測るまで断定しない。

**全体の停止条件**: C1 を入れて JVM ゲートが緑になっても、**C3 の実機計測を通すまで「直った」と言わない**。
合格線（§6.2）に届かない場合は、追加の構造変更を重ねる前に §9-1 の再計測へ戻る。
