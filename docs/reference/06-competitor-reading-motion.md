# 06 — 競合5アプリの「読書/目次」モーション横断解析

> 収蔵: 2026-07-15。`book-api-analysis` リポジトリ（`/mnt/c/Users/qingj/Desktop/project/book-api-analysis/06-competitor-reading-motion.md`）で
> 実施した静的解析レポートをそのまま収蔵したもの。本文中の `apks_decompiled/`・`_recon/` 配下の file:line は
> 同リポジトリのローカル作業成果でありここには非収蔵（04/05 と同じ扱い）。
> §4 冒頭の「自作アプリの実依存版は未確認」は収蔵時に突合済み＝自作は **compose-bom 2025.02.00
> （foundation/animation-core 1.7.8・material3 1.3.1）・navigation-compose 2.7.5**（`android/app/build.gradle`）で、
> §4 の既定値表（1.7〜1.11 で安定）がそのまま適用できる。
> 自作実装との突合結果と適用案 → `.claude/plans/reading-motion-apply-2026-07-15.md`。

**目的**: 自作 novel-reader（縦書きPDF読書 / Jetpack Compose）で報告されている、目次画面・本文まわりのスクロール/アニメの「違和感」を言語化するための**体感の物差し**。同ジャンル実運用アプリのデコンパイル済みソースから、目次相当の一覧画面・本文（ページ送り/スクロール）で実際に使われている**尺(duration)・カーブ(easing)・スクロール挙動**を抽出して比較する。

**調査日**: 2026-07-15 / **対象**: `apks_decompiled/` の5アプリ（jadx 生成 Java を静的解析）
**性格**: 事実収集レポート。決め打ち修正はしない。復元不能な箇所は「不能」と明記。

---

## 0. 3行サマリ

1. **前提が一部くつがえった**: 5本のうち「なろう公式アプリ(com.syosetu.android)」は Compose ではなく **Flutter 製**（UIはAOT Dartで復元不能）。**真正の Jetpack Compose 実装は カクヨム(kakuyomu) 1本だけ**。残り3本はクラシックView。＝**同ジャンルにネイティブCompose読書UIはほぼ存在しない**（本文はWebViewかView自前描画が主流）。
2. **どのアプリも読書/目次に「カスタムイージング・カスタムspring物理・カスタムPageTransformer」を使っていない**。ほぼ全て framework 既定に委譲し、明示的な独自尺はごく僅か（例: 話送り400ms、ツールバー畳み100ms、FastScrollerフェード150/300ms）。**＝凝ったアニメ設計は業界的に不要という強いエビデンス**。
3. よって自作アプリの「違和感」の主犯は**独自アニメの作り込み不足ではなく、Compose の"既定挙動"がViewベース各社の既定と食い違う点**（筆頭: **stretchオーバースクロールのバウンド**、次点: `animateScrollToItem/Page`の**spring settle** と ViewPager の**decelerate settle**の差、tween尺）。→ §6のチェックリストで切り分け。

---

## 1. 【重要】前提の訂正と描画方式マップ

recon(`_recon/*.md`)の記述に対し、今回の精読で以下を訂正・確定した。

| アプリ | recon記述 | **実態（今回確定）** | 本文描画 | 目次/一覧描画 |
|---|---|---|---|---|
| **com.syosetu.android**（なろう公式） | 「Compose単一Activity」 | ❌**誤認。Flutter製**（MainActivity=FlutterActivity, flutter_assets一式）。Composeは同梱Moloco広告SDK由来 | Flutter(AOT Dart, **復元不能**) | 同左 |
| **jp.kadokawa.el.kakuyomu**（カクヨム） | 「Compose+Apollo」 | ✅**真正Compose(M3)** ＋ Fragment/ViewPager2 混成 | **Compose器＋AndroidView上の自前WebView**(HTML) | 目次=RecyclerView / ホーム一覧=全画面Compose |
| **com.tscsoft.naroureader**（なろうリーダ） | クラシックView | ✅クラシックView | 横書き=WebView / 縦書き=**RecyclerView自前縦書き** / 話送り=旧ViewPager | RecyclerView |
| **com.sampleb3.novel**（Web小説リーダー） | クラシックView | ✅クラシックView。**自作と同じPDF+縦書き路線**（最重要参照） | PDF=ViewPager2+PdfRenderer / 縦書き=ViewPager2+**自前Canvas** / 横書き=WebView | RecyclerView / ListView |
| **com.zyunto.naroreader**（なろうブック） | 「Compose単一Activity」 | ❌**誤認。クラシックView**(recyclerview/viewpager2/databinding)。**本文も目次もWebView** | WebView(なろう公式サイト表示) | WebView / 一覧=RecyclerView |

> 訂正の根拠は §7 と各 file:line。`04-HANDOFF.md`/recon の「Compose単一Activity」表記は syosetu(Flutter) と zyunto(View) で誤り。

**含意**: 「Compose実装の読書アニメ実値」を大量に集める、という当初の狙いは物理的に成立しない（母数が実質1本、しかもその1本も本文はWebView、Compose部は難読化で内部数値が飛んでいる）。そこで本レポートは次の3本柱で「物差し」を構成する:

- **(A) 実測できた各社の尺・カーブ・スクロール挙動**（View/WebViewが主。§2/§3）
- **(B) Compose が"上書きしなければ継承する"既定モーション仕様**（同梱ライブラリの版から確定。§4）— 自作アプリも同じ既定を継承しているので、これ自体が最良の物差し
- **(C) (A)(B)を突き合わせた横断表と、違和感の切り分けチェックリスト**（§5/§6）

---

## 2. 目次一覧（一覧画面）の実測

| アプリ | 実装 | フリング/スクロール | 位置ジャンプ | 独自アニメ | 出典(file:line) |
|---|---|---|---|---|---|
| kakuyomu | RecyclerView+LinearLayoutManager（SnapHelper無） | **RecyclerView既定** | — | 無し（＝既定） | `my9.java:79-88` |
| tscsoft | RecyclerView+縦LLM＋StickyHeader＋FastScroller | **RecyclerView既定**（フリング減速＋端グロー/stretch） | `scrollToPositionWithOffset`＝**即ジャンプ** | FastScrollerつまみ: **フェードイン150ms/アウト300ms/1.5s後自動消去**（ObjectAnimator, interpolator既定=AccelerateDecelerate） | `ChapterListFragment.java:434,609` / `RecyclerViewFastScroller.java:22-24,124-147` |
| sampleb3 | PDF一覧=RecyclerView / 話一覧=**旧ListView** | **OS既定**（DefaultItemAnimator: add/remove≈120ms, move/change≈250ms） | 独自指定なし | 無し | `PdfListActivity.java:748-750` / `EpisodeListActivity.java:140` |
| zyunto | RecyclerView（＋検索はSwipeRefresh）※目次自体はWebView | RecyclerView既定 / SwipeRefresh標準 | `smoothScroll`のアプリ呼出**0件** | 無し | `RankingNovelListFragment.java:286` |
| syosetu(公式) | Flutter | 復元不能 | 復元不能 | 復元不能 | — |

**目次一覧の総括**:
- **5社とも一覧のスクロール/フリングは framework 既定そのまま**。独自のスクロール減衰・スナップ・イージングは誰も入れていない。
- 位置ジャンプ（しおり/読書位置へ移動）は**全社「即時・無アニメ」**（`scrollToPositionWithOffset` 等）。長距離をアニメで延々スクロールさせない、が共通解。
- 唯一の作り込みは tscsoft の **FastScrollerつまみのフェード（150/300ms）** 程度。

---

## 3. 本文（ページ送り/スクロール）の実測

本文は描画方式で挙動が根本的に違うので、方式別に並べる。

### 3-A. ページ送り型（ページャ）

| アプリ / モード | ページャ | ページ送りアニメ | ページトランスフォーマ | 位置ジャンプ | オーバースクロール | 出典 |
|---|---|---|---|---|---|---|
| **sampleb3 / PDF** | ViewPager2 | スワイプ・タップ・音量キー = `setCurrentItem(idx)`（**アニメ有り横スライド=既定settle**） | **無し**（curl/fade等なし） | スライダー/番号 = `setCurrentItem(idx,false)`＝**瞬間** | 指定なし=OS既定グロー。ページ内フリング`onFling=false`で**慣性殺し** | `PdfViewerActivity.java:1948/589/635` |
| **sampleb3 / 縦書き** | ViewPager2（RTL: next=idx−1） | 同上（既定settle横スライド） | 無し | スライダー = `,false`瞬間 | 指定なし | `TategakiActivity.java:1417/644` |
| **tscsoft / 話送り** | 旧ViewPager | `setCurrentItem(pos,true)`＝**既定settle**（quinticイーズアウト・距離依存・上限600ms） | 無し | — | **設定でON/OFF切替**(`setOverScrollMode(0/2)`)＝唯一の独自チューニング | `ViewerActivity.java:465,329` |
| **tscsoft / 縦書き** | RecyclerView+**PagerSnapHelper**(stock) | `smoothScrollBy`＝RecyclerView既定SmoothScroller(quintic) | SnapHelperのcreateScroller override無 | 即時`scrollBy`追従 | `OVER_SCROLL_NEVER` | `VerticalTextLayout.java:509-511,1413,1283` |
| **kakuyomu / 話送り** | **ページャではない**。Fragmentトランジション | **縦スライド `slide_in/out_up|down`＝400ms＋accelerate_decelerate** | — | — | — | `EpisodeViewerContainerFragment.java:189-199` / `res/anim/slide_*.xml` |
| zyunto / 話送り | ViewPager2 | 初期位置`setCurrentItem(i,false)`＝瞬間 | 無し | — | 指定なし | `NovelDetailPagerFragment.java:360` |

### 3-B. 連続スクロール型（WebView / スクロール）

| アプリ | 実装 | スクロール/フリング物理 | ページ送り相当 | オーバースクロール | 出典 |
|---|---|---|---|---|---|
| **kakuyomu / 本文** | Compose器＋AndroidViewに**自前サブクラスWebView**(`cr5 extends WebView`)。本文=`loadDataWithBaseURL(...baseurl.html, HTML)` | 自前 OverScroller+EdgeEffect+NestedScroll。**係数は標準**(386.0878×160×0.84 摩擦, 0.35 ドラッグ, `EdgeEffect.onAbsorb`) | 無し（**連続縦スクロール**） | EdgeEffect（端グロー/stretch）標準物理 | `cr5.java:13-30` / `cga.o:768-787` / `fl9.java:29` |
| **tscsoft / 横書き** | WebView（スクロールはJS制御） | ページ送り = JS `window.scrollBy` を**10ms間隔×分割数で反復**。スムーズON=**20分割≒約200ms・線形(等速)** / OFF=**1分割=即ジャンプ**。ページ量=ビューポート高の**95%** | 上記JSスクロール | `OVER_SCROLL_NEVER`（`NestedWebView`）。フリング=OverScroller既定、オーバーフリング距離=`height/2` | `ViewerPageFragment.java:651,974` / `NestedWebView.java:54-57,498` |
| sampleb3 / 横書き | WebView（`SafeEpisode2` は ScrollView.`smoothScrollTo`≒250ms） | OS既定 | — | ツールバー畳み `AppBarLinkedWebView`: `ValueAnimator setDuration(100L)`＋既定AccelDecel、`OVER_SCROLL_NEVER` | `AppBarLinkedWebView.java:174-209` |
| zyunto / 本文 | WebView（なろう公式サイト表示） | 本文設定の初期化 `R(View)` が**jadx復元不能**（JS有効化/スクロールリスナ等は不明） | サイト側委譲 | 復元不能 | `NovelEpisodeWebFragment.java:183-187,249` |

### 3-C. 本文の総括

- **ページ送りの"それらしさ"は、ほぼ ViewPager/ViewPager2 の既定settle か、RecyclerView+PagerSnapHelper の既定スナップだけで成立**。curl/fade/3D等の凝ったページめくりは**5社中0**。
- **共通の実装作法**: スワイプ/タップ送り=**アニメ有り**（`setCurrentItem(i,true)`）、スライダー/番号/端クランプ=**瞬間**（`,false`）。この「送りは滑らせ、ジャンプは瞬間」の二択が唯一の作り込み。
- **オーバースクロールは"読書面では消す"傾向**: tscsoft 横書き/縦書き=`OVER_SCROLL_NEVER`、sampleb3 ツールバーWebView=`NEVER`、tscsoft 話送りは**ユーザー設定でON/OFF**。＝端のバウンド/グローは読書中はノイズ、という各社の判断。
- 明示的な独自尺の全数（読書系）: **kakuyomu 話送り400ms** / **tscsoft JSスクロール≒200ms(20×10ms,線形)** / **sampleb3 ツールバー100ms** / **tscsoft FastScroller 150/300ms**。これ以外は全部 framework 既定。

---

## 4. Jetpack Compose「継承デフォルト」リファレンス

**これが自作アプリにとって最も直接的な物差し**。自作アプリが `tween(...)` や `spring(...)` を明示していない箇所は、同梱ライブラリ版の**既定値**がそのまま体感を決めている。以下は本レポート対象アプリの同梱版（kakuyomu: animation-core/foundation 1.11.0-beta02, material3 1.5.0-alpha17。syosetuは無関係=Flutter）に基づく確定値。値は 1.7〜1.11 で安定。

> ⚠ 自作アプリ側の実際の依存版は本リポジトリでは未確認。下表は「これらの競合が積んでいる版の既定」。自作の版と突き合わせて使うこと。
> （収蔵時追記: **自作は version catalog を使っておらず `libs.versions.toml` は存在しない**＝依存版の正本は `android/app/build.gradle`。突合結果は冒頭の収蔵注記に記載済み。）

### 4-1. animation-core（tween / spring / easing）
| 項目 | 既定値 | 備考 |
|---|---|---|
| `tween()` 既定 duration | **300ms** | `AnimationConstants.DefaultDurationMillis` |
| `tween()` 既定 easing | **FastOutSlowInEasing = CubicBezier(0.4, 0.0, 0.2, 1.0)** | いわゆる"標準"S字 |
| 標準イージング | LinearOutSlowIn(0,0,0.2,1) / FastOutLinearIn(0.4,0,1,1) / Linear | — |
| `spring()` 既定 | **dampingRatio=1.0(NoBouncy), stiffness=1500(Medium)** | バウンドなし |
| Stiffness定数 | High=10000 / Medium=1500 / **MediumLow=400** / Low=200 / VeryLow=50 | MediumLowが可視要素の既定に多用 |
| DampingRatio定数 | NoBouncy=1.0 / LowBouncy=0.75 / MediumBouncy=0.5 / HighBouncy=0.2 | — |

### 4-2. foundation（スクロール/フリング/オーバースクロール）— 体感の主犯候補
| 項目 | 既定挙動 | 物差しとして |
|---|---|---|
| `LazyColumn`/`verticalScroll` フリング | `ScrollableDefaults.flingBehavior()` = **spline decay**。摩擦係数は**クラシックViewと同一**（g×in/m×ppi×0.84, ドラッグ0.35）。kakuyomu 実コードでも確認(`cga.o` 386.0878/0.84/0.35) | **フリングの減速カーブはView各社と同じ**。フリングが違和感の主犯である可能性は低い |
| 既定オーバースクロール | **stretch（API31+, `EdgeEffect.onPullDistance`）** / API30以下は glow。**既定でON**。kakuyomu同梱foundationに実在(`al.java:194`) | **筆頭容疑**。View各社は読書面でこれを`OVER_SCROLL_NEVER`で消していた。Composeは黙って伸びるバウンドが出る |
| `animateScrollBy`/`animateScrollToItem` | 既定 **`spring()`（NoBouncy, stiffness Medium=1500）** ＝距離非依存のバネ settle | ViewPagerの「decelerate・距離依存・上限600ms」とは**カーブが別物**。目次ジャンプや吸着の"効き"が違って感じる原因になりうる |

### 4-3. foundation Pager（`HorizontalPager`/`VerticalPager`）
| 項目 | 既定挙動 | 物差し |
|---|---|---|
| フリング挙動 | `PagerDefaults.flingBehavior()`：**1フリングで最大1ページ**（`pagerSnapDistance = atMost(1)`）、snap-back spring で吸着 | 「速く弾いても1ページしか進まない」＝仕様。多ページ送りたいUXなら違和感になる |
| プログラム送り | `animateScrollToPage`＝アニメ / `scrollToPage`＝瞬間 | sampleb3等の `setCurrentItem(i, true/false)` と 1:1 対応 |
| snap settle | 既定 spring（`PagerDefaults.snapFlingBehavior` の内部spec） | ViewPager2既定settleとは体感差あり |

### 4-4. material3（kakuyomu 実確認: `di5.java` はM3標準モーショントークンそのもの）
| トークン | 値 | 用途 |
|---|---|---|
| Standard | CubicBezier(0.2, 0.0, 0.0, 1.0) | 汎用 |
| StandardAccelerate / Decelerate | (0.3,0,1,1) / (0,0,0,1) | 出/入 |
| **EmphasizedDecelerate** | **(0.05, 0.7, 0.1, 1.0)** | 大きな要素の"入り"（M3の"効いた"減速） |
| **EmphasizedAccelerate** | **(0.3, 0.0, 0.8, 0.15)** | 大きな要素の"出" |
| Duration スケール(トークン) | short 50/100/150/200 · medium 250/300/350/400 · long 450/500/550/600 · extraLong 700〜1000 | M3の画面遷移はこの離散値から選ぶ |

> kakuyomuは**素のM3モーション**（独自イージング係数は`di5.java`に無し）。＝Composeで「M3 Emphasized系を使う」だけで公式アプリ水準のカーブになる、という裏付け。

### 4-5. Navigation の画面遷移（要注意の落とし穴）
- kakuyomu(Fragment nav)の画面遷移は **AndroidX Navigation 既定のフェード**（`res/anim/nav_default_*` ＋ `config_navAnimTime`）。
- ⚠ **もし自作が `navigation-compose` を使っているなら**、`composable()` の既定 enter/exit が版によっては **`fadeIn/fadeOut(tween(700ms))`** と長め。目次⇄本文の遷移が"もっさり"なら真っ先に疑う（→ `enterTransition`/`exitTransition` を明示して 200〜300ms 程度に）。※自作の遷移実装未確認のため候補として提示。

---

## 5. 横断まとめ表（体感の物差し）

### 目次一覧
| 観点 | 各社の実値レンジ | Composeでの既定 | Composeでの再現 |
|---|---|---|---|
| スクロール/フリング | 全社 framework既定（弾きの減速はView=Compose同一） | spline decay（同一物理） | 何もしなくて可 |
| オーバースクロール | 一覧は既定グロー/stretchのまま | **stretch（既定ON）** | 一覧は既定でOK。読書面だけ後述で調整 |
| 位置ジャンプ | 全社**即時** | — | `scrollToItem`（アニメ無し）を使う。`animateScrollToItem`で長距離を流さない |
| リスト項目の追加/削除 | DefaultItemAnimator 120/250ms | `animateItemPlacement` = spring既定 | 既定で自然。過剰にtweenしない |

### 本文
| 観点 | 各社の実値 | Composeでの再現の勘所 |
|---|---|---|
| ページ送りカーブ | ViewPager既定settle（decelerate/quintic・距離依存・≤600ms）/ kakuyomu話送り**400ms accelerate_decelerate** | `HorizontalPager`のsnap既定でほぼ足りる。章送りに独自遷移を付けるなら**≈400ms**が実在の目安 |
| 送り vs ジャンプ | 送り=アニメ有り / スライダー・番号=**瞬間** | `animateScrollToPage` と `scrollToPage` を用途で使い分け |
| 縦書き綴じ方向 | sampleb3/tscsoft とも **RTL**（next=前indexへ） | `HorizontalPager(reverseLayout = true)` |
| ページめくり演出 | curl/fade/3D は**5社0**。素の横スライドのみ | PageTransformer相当を作り込まない＝過剰設計回避のエビデンス |
| オーバースクロール(読書面) | **消す**傾向（`OVER_SCROLL_NEVER`）or 設定でON/OFF | 読書面は overscroll を無効/減衰（§6） |
| 連続スクロールのフリング | OverScroller標準物理（=Compose同一） | 既定で可 |
| スクロール型ページ送り | tscsoft: 画面高**95%**を**約200ms・線形**で送る | 「1ページ＝画面高の95%を短時間・等速で送る」は移植価値あり |

---

## 6. 自作アプリの「違和感」切り分けチェックリスト

症状が未言語化なので、**上の実値に自作を当てて差分を探す**手順。根本原因を特定してから直す（対症・握り潰しはしない）。

**まず確認（Compose既定 vs View各社の食い違い＝主犯候補、上から疑う）**

1. **stretchオーバースクロールのバウンド**（最有力）
   - 症状: ページ端/リスト端で画面が"ゴムのように伸びて戻る"、縦書きページャの端が跳ねる。
   - なぜ: Compose foundation は API31+ で**既定ON**（§4-2）。View各社は読書面で `OVER_SCROLL_NEVER` にして消していた（§3-C）。
   - 確認/対処: 本文ページャ/スクロールで overscroll を無効化 or 減衰（`Modifier.overscroll(null)` 相当 / ラッパで `OverscrollEffect` を差し替え / 古いAPIなら `LocalOverscrollConfiguration provides null`）。一覧は残してよい。

2. **`animateScrollToItem/Page` の spring settle が"効き過ぎ/floaty"**
   - 症状: 目次で位置へ飛ぶ、ページ吸着の"止まり方"がView機と違う。
   - なぜ: Compose既定は **spring(NoBouncy, stiffness Medium=1500)**、ViewPagerは decelerate・距離依存・≤600ms（§4-2/§4-3）。カーブが別物。
   - 確認/対処: 長距離ジャンプは `scrollToItem`(瞬間)へ。吸着を締めたいなら snap の `AnimationSpec` を `spring(stiffness=StiffnessMediumLow=400〜Medium)` や `tween(250, FastOutSlowIn)` に明示。

3. **ページ送りが"1ページしか進まない/弾きが吸われる"**
   - なぜ: `PagerDefaults` は **1フリング最大1ページ**（§4-3）。
   - 確認/対処: 多ページ高速送りが要るなら `flingBehavior`/`pagerSnapDistance` を調整。逆に1ページ厳守が意図なら仕様通り。

4. **遷移/アニメの尺が長い**
   - なぜ: どこかに `tween()` 既定 **300ms**、または navigation-compose 既定 **700ms フェード**（§4-1/§4-5）が効いていないか。各社の読書系尺は **100〜250ms、章送りでも400ms**。
   - 確認/対処: 目次⇄本文の遷移・バー開閉の実尺を実測し、長ければ 200〜300ms・`FastOutSlowIn`/`EmphasizedDecelerate` に。

**次に確認（Compose固有のジャンク要因＝"アニメが悪い"ように見えるが実は描画）**

5. **縦書きCanvasの再コンポーズ/再描画過多によるフレーム落ち**
   - 症状: スクロール/送り中に"カクつく"→アニメのカーブではなく**フレームドロップ**。
   - 確認: Layout Inspector / `Modifier.drawWithCache` 化 / recomposition カウント / `System Trace` でジャンクを実測。カーブ調整では直らない種類。ここは別途プロファイルで根本特定。

6. **nested scroll の競合**（本文スクロール × 折り畳みツールバー × Pager）
   - 症状: 端で入力を取り合って"引っかかる"。kakuyomu が WebView 側で `requestDisallowInterceptTouchEvent` を入れて回避していた領域（`MediaWebView.java:145-151`）。
   - 確認: `nestedScroll` 接続とツールバー連動の消費順を確認。

> 症状が1つに絞れたら、その項目だけを実測ベースで直す。複数を同時にいじらない。

---

## 7. 確度・復元限界（正直な明記）

- **syosetu(公式)=Flutter**: `MainActivity extends FlutterActivity`、`flutter_assets/` 一式、`GeneratedPluginRegistrant`(webview_flutter/just_audio 等40近く)。UI/アニメは AOT Dart(`libapp.so`)にあり **jadx出力からは一切復元不能**。参考値が要るなら実機の目視計測か Dartスナップショット解析（別手法）。
- **kakuyomu の Compose 内部値**: foundation/animation-core が R8 でアプリ独自クラスへマージ・難読化され、`androidx/compose/foundation/lazy/` 等が生成物に存在しない。**tween(ms)/spring係数/独自CubicBezier は本体可読側から検出不能**。ただし**標準と異なる独自イージング係数は見つからなかった**（＝素のM3/foundation既定を使っている公算が高い）。ホーム一覧が LazyColumn か Column+scroll かも断定不能。
- **確実に実値が取れたのは**: kakuyomu 話送り anim(400ms/accelerate_decelerate, XML実値) と 自前WebViewスクロール物理、tscsoft の JSスクロール/FastScroller/overscroll設定、sampleb3 の ViewPager2挙動/ツールバー100ms、各社の描画方式。**View/WebView側は高確度、Compose内部数値は低確度〜不能**。
- §4 の Compose 既定値は同梱ライブラリ版（1.7〜1.11 / M3 1.5）に基づく確定値だが、**自作アプリの実依存版は本リポジトリ未確認**。突き合わせ要。

---

## 付録: 出典アプリと版
- kakuyomu v6.12.0 — compose animation-core/foundation/ui **1.11.0-beta02**, material3 **1.5.0-alpha17**（`.version` マーカーで確認）
- syosetu(公式) v1.6.5 — **Flutter**（同梱Composeは広告SDK: animation 1.7.0 / material 1.2.1 / ui 1.9.2 だが全てMoloco由来）
- tscsoft v1.36.17 / sampleb3 v2.638 / zyunto v4.10.0 — クラシックView（Compose不使用）
