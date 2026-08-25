# 読書描画・画面遷移の性能提案トリアージ（2026-08-18）

- **対象ブランチ**: `perf/extract-hot-shape`（この便は**分析のみ**＝コード変更ゼロ。抽出エンジン側の計測とは別トラック）
- **出所**: 2026-08-18 02:34〜02:54 JST のセッションでの調査結果。**当日は台帳へ落ちていなかった**ため、
  2026-08-19 に transcript から復元してここへ一次情報として保存した（要点は `handover.md` 側へ短く出してある）。
- **入力**: ユーザー投入の性能提案（遷移層4件＋描画層5件）と、その直前の縦書き調査で挙がった改善案 A〜F。
- **状態**: 採否の裁定まで。**実装は未着手**。

## 結論（採否の要約）

| 区分 | 項目 | 裁定 |
|---|---|---|
| 遷移層 | 2 pop 側 defer・M の導出データ分離／3 隣ページ常駐の2段階制御 | **既に実装済み**＝対応不要 |
| 遷移層 | 1 差し替えフレームの遅延 | 条件付き採用（本棚は実測で陰性・**読書本文/目次は未測**） |
| 遷移層 | 1 alpha=0 で composition をアニメと並走 | **不採用**（実測で否定済みの配置へ戻るため） |
| 遷移層 | 4 星図の `drawWithCache`/Picture | 条件付き・優先度低（既定スキンでない） |
| 描画層 | 7 オフセットの deferred read／F `chapterCache` の eviction | **既に実装済み**＝対応不要 |
| 描画層 | **A 組版の composition 離脱＋章スコープキャッシュ ＋ C `BoxWithConstraints` 除去** | **採用・最優先**（5・6・B もここで閉じる）→ **2026-08-26 実装済み**（末尾「実装記録」） |
| 描画層 | E draw 段の `splitGraphemes` 事前化 | 採用（安価） |
| 描画層 | D advance キャッシュ | キャッシュのみ採用・**定数化は不採用**（書体差で版面がずれる＝KDoc が明示的に否定） |
| 基盤 | 8 Compose compiler metrics 取得 | 採用・**配線済みで走らせるだけ**（`kotlinx-collections-immutable` の要否判定もこれ待ち） |
| 基盤 | 9 Baseline Profile | 採用（profileinstaller だけ入っていて配るプロファイルが無い） |

**着手順序**: 0（計測）→ A+C → E → D → 8/9。**0 は 2026-08-25 完了・A+C は 2026-08-26 実装（末尾「実装記録」）＝次は E。**
0 の中身＝`ChapterFlipBenchmark` に `verticalMode` 軸が無いので追加（`FlipBudget` P50 15 / P90 20 / P99 50ms をそのまま流用）＋
設定スライダードラッグ経路の計測。

⚠️ **前提**: 縦書き経路には未再現の報告バグ（章遷移で描画が上部にジャンプ）が残る。A は組版の寿命とスクロール位置の
関係を変えるので、**このバグの再現条件採取を先に閉じるか、少なくとも「A 前の挙動」を記録してから**入る
（`docs/knowledge/chapter-transition-scroll-jump-paths-ruled-out.md`）。

---

以下は当時のやりとりの一次情報（原文）。

## 入力：性能提案（原文）

遷移層
deferHeavyContent の離散2値切替は、スケルトン→実内容の差し替えフレームに composition コストが集中する。enter アニメ終了直後の1フレームで subtree 全体を初回 composition するため、そこがジャンクの新たな発生点になり得る。差し替えを withFrameNanos で1フレーム遅延させるか、実内容の composition をアニメ中に開始して draw だけ抑止（graphicsLayer alpha=0）する方式との比較計測を推奨。後者は「重い composition をアニメと並走させる」ため逆効果の可能性もあり、計測なしに選べない。
深い画面から popToTab() で戻る際、"tabs" destination は一度 composition から外れているため TabPagerHost 全体が再 composition される。M星図のような初期化コストの高い面は、導出データ（星の座標群等）を VM/remember 外のキャッシュに持ち、composition 再構築と描画データ生成を分離しておくべき。pop 側にも deferHeavyContent 相当（popEnterTransition 連動)があるか確認対象。
HorizontalPager の隣ページ常駐 0 は遷移中の措置としては正しいが、定常時も 0 だとスワイプ開始時に隣ページの初回 composition が走りタッチ追従が落ちる。settle 後に beyondViewportPageCount を 1 へ戻す2段階制御が候補。
星図が Canvas 多要素描画なら、固定天球部分を drawWithCache か Picture 記録で1回描画に落とせる。フェードスルー遷移中に毎フレーム全星を再描画しているなら効果が大きい。
描画層
最大の論点は本文。文字単位・ルビ単位で Composable を生成しているなら、layout node 数がページあたり数百〜千超になり、composition/layout 両フェーズが支配的コストになる。ページ全体を単一 Canvas + TextMeasurer（または Paragraph 直接構築）で描く方式への移行が最も効く候補。縦書きは自前割付が前提だろうから、割付結果（各グリフの座標列）を immutable なデータとして事前計算し、draw フェーズは座標列の消化のみにする。
割付計算は Dispatchers.Default で先行実行し、表示ページ±1 をキャッシュ。キーは (章ID, フォント, 文字サイズ, 表示領域寸法)。フォント変更時の全章再計算を避けるため、割付は章単位の遅延評価に。
ページめくり中のオフセットを composition で読むと毎フレーム recomposition になる。graphicsLayer { translationX = ... } 内での deferred read に限定する。
SkinTokens 各束と Face 束の @Immutable は妥当。残る漏れは lambda の安定性で、route→Content へ渡すコールバックが毎回再生成されると束の努力が無効化される。Compose compiler metrics で Content 各層の skippable 判定を一度出す価値がある。
Baseline Profile を reading ルートのスクロール経路まで含めて生成しているか。初回ジャンクへの寄与が大きい

以上の最適化を未実装か確認して。
採否も一緒に


## 調査：縦書き描画6問の実コード確認（原文）

6問に順に答える。実コードを読んだ範囲で、確認できたことと確認できていないことを分けて書く。

### 1. VerticalChapterContent の実体

**Canvas 直描画。ただし段落単位**（`ui/VerticalChapterContent.kt`）。

`LazyRow(reverseLayout = true)` の各 item が 1 段落で、中身は `BoxWithConstraints` → 純データの組版結果 `ParagraphLayout` → `VerticalParagraph`（`ui/compose/VerticalParagraph.kt:72`）が `Canvas { drawIntoCanvas { ... } }` で `nativeCanvas` に直描画。1字＝1 `renderer.drawGlyph()` 呼び出しで、文字単位 Composable は存在しない。

横書き（`ChapterContent.kt`）とは方式が違う：あちらは `LazyColumn` + `BasicText`（AnnotatedString）で、折り返し・禁則を Compose のテキストエンジンに任せ、ルビだけ `drawWithContent` の Canvas オーバーレイで重ねる。縦書きだけ Compose のテキストレイアウトを一切使わず自前組版（ADR 0020＝公式 text-vertical が成熟したら差し替える「つなぎ」と明記）。

### 2. ページネーション計算のタイミング・スレッド・キャッシュ

「ページ」という概念は無い（連続スクロールで、割付単位は**列**）。

- **タイミング**: `BoxWithConstraints` の中で、つまり**composition 中**。`remember(paragraph, columnHeightPx, fontSizePx, rubyFontSizePx, columnAdvancePx)` の計算ブロックとして走る（`VerticalChapterContent.kt:407`）。
- **スレッド**: **UI スレッド**。組版は `DefaultVerticalTypesetter.typeset()` → `LineBreaker.breakIntoColumns()` の同期純関数で、`Dispatchers.Default` へは出していない。1 字ごとに `metrics.verticalAdvance()`（Paint 計測）を呼ぶ。
- **キャッシュ**: `remember` のみ＝LazyRow が item を破棄すれば消える。組版器インスタンス自体は章単位で `remember { DefaultVerticalTypesetter(PaintFontMetrics()) }`（Paint 生成の抑制のみ）。
- **フォント設定変更時**: `fontSizePx`/`columnAdvancePx` が remember の key なので、**可視段落は全部その場で再組版**。表示設定シートのスライダーはドラッグ中に毎値 `onFontSizeChange` を撃つ（`ReadingFace.kt` の `ReadingTypography` KDoc）ため、縦書きでスライダーを動かすと毎値×可視段落数の組版が UI スレッドで回る構造になっている。この経路のベンチマークは見当たらない（下記6参照）。

### 3. ページめくり方式とスクロール読み取りのフェーズ

- 章内の送りは Pager ではなく **LazyRow/LazyColumn の連続スクロール**。**章⇄章の送り**が自前ジェスチャで、`draggable(Horizontal)` + `nestedScroll(ChapterPullConnection)`（`NativeReadingScreen.kt:1060-1093`）。隣章の実物冒頭が端から覗く「引っ張りプレビュー」で、確定は距離 OR 速度の複合。`Animatable + launch { snapTo }` を使わず素の `var dragOffsetPx by remember { mutableFloatStateOf(0f) }` にしてあるのは、`draggable` の onDelta が同一フレームに複数回届き snapTo だと最後の1個しか効かない累積レースを実機で踏んだため（`NativeReadingScreen.kt:899-903`）。
- **読み取りフェーズは混在**。追従オフセットは `Modifier.graphicsLayer { translationX = dragOffsetPx }`（`:1101`）＝**draw 段の deferred read**。設定シートの退避割合 `settingsPeek` も「値は graphicsLayer 内でのみ読む（draw 段の deferred read）」と明記（`:874`）。一方で、覗きの出し分け判定は `derivedStateOf { dragOffsetPx > 0f }`（`:1216`）で**composition 段の読み**——ただし Boolean 化されているので符号が変わる瞬間だけ recompose する形。
- 位置保存は `snapshotFlow` で `firstVisibleItemIndex`/`ScrollOffset` を観測して debounce 保存（`:821`, `:841`）。

### 4. ルビの割付方式

**横書きと縦書きで別実装**。

- 横書き（`RubyLayoutHelper.calculateRubyPositions`）: `TextLayoutResult` に問い合わせて親文字範囲の bounding box 中央 X とベースライン Y を出す。行またぎは各行の親文字数に比例して読みを書記素分割（端数は最終行が吸収）。計算場所は **draw 段**（`drawWithContent` 内）だが、`RubyPositionCache` が `layout === lastLayout && ranges === lastRanges` の**インスタンス同一性**で前回結果を丸ごと再利用するので、実質はレイアウト変化時のみ。Compose 1.6 に行単位ベースライン API が無いため `lineBaseline()` を firstBaseline/lastBaseline から自前導出している（1.7 の `getLineBaseline` へ置換可と注記あり）。
- 縦書き（`typeset/RubyPlacer.kt`）: 組版時に列内の親文字スパンから位置を確定＝**composition 段の組版結果に同梱**。ルビも本文と同じ `CharClassifier` + renderer を通す（「ルビは仮名だから正立でよい」が誤りだった実機報告＝伸ばし棒が横向きになった件への対処）。

書記素分割は両方 `BreakIterator.getCharacterInstance(Locale.JAPANESE)`。縦書きは**描画のたびに** `drawParagraphLayout` 内でルビを `splitGraphemes` し直している（`VerticalParagraph.kt:101`）——ここは組版時に確定できるはずの計算が draw 段に残っている。

### 5. 章テキストの供給経路

**章単位の全文一括ロード＋前後1章の先読みあり**（`ui/ChapterScreen.kt`）。

`produceState` + `withContext(Dispatchers.IO)` で `ChapterHtmlParser.parse(File(htmlDirPath, currentFile))`（`:122-138`）。成功は `chapterCache`（LinkedHashMap、書き込みは main スレッドに戻してから＝非スレッドセーフのため）へ。`prevFile`/`nextFile` も同型の `produceState` で先読みし（`:173`, `:183`）、キャッシュヒットなら即返す。先読みの起動条件は「現在章のパース成功後」。ストリーミング/部分ロードは無い。

### 6. 計測データ

**Macrobenchmark は在る**（`android/macrobenchmark/`）。`StartupBenchmark` / `BookshelfScrollBenchmark` / `ChapterFlipBenchmark` / `TabSwipeBenchmark` / `PdfImportBenchmark` の5本で、いずれも `frameDurationCpuMs` の P50/P90/P99 を予算オブジェクトで判定する形（Flip: 15/20/50ms、Scroll: 15/20/30ms、TabSwipe: 11/18/50ms、Startup: TTID median 350 / max 500ms）。**縦書き専用のベンチはない**——`ChapterFlipBenchmark` はスワイプ章送りを測るもので、`verticalMode` の分岐は見当たらない。

実測で真因が確定した既知ジャンクは以下（コード側にも対処が入っている）:

| 症状 | 実測値・真因 | 対処 |
|---|---|---|
| 本棚→読書 push のジャンク | グリッド初回 measure 51ms/フレーム（Perfetto 2026-07-16） | enter アニメ窓だけスケルトン差替（`deferHeavyContent`） |
| タブ pop のジャンク | 隣ページ常駐時に冒頭2フレーム 445/402ms ↔ 非常駐 212/153ms（framestats 2026-07-26） | アニメ中 `beyondViewportPageCount=0`、settle 後 2 フレームずらして 1 へ |
| タブスワイプのジャンク | 既定0だと settle 毎に隣ページ破棄→再コンポーズ（framestats 2026-07-25） | 前後1ページ常駐化 |

計測の落とし穴は `docs/knowledge/macrobenchmark-frametiming-scroll-pitfalls.md` / `device-screen-lock-breaks-benchmark-two-ways.md` / `emui-p30-jank-log-collection.md` に知見化されている。

**Layout Inspector の recomposition counts / composables.txt（Compose compiler reports）を取った記録は見つからなかった。** 未再現の報告バグ「章遷移で描画が上部にジャンプする」は、否定済み経路の記録だけがあり真因未確定（報告者自身も再現できておらず、遭遇時に5軸を控える依頼が `awaiting-human.md` に出ている状態）。

### 気になった点

縦書き経路には、上の実測が示す「重い仕事をアニメ窓の外へ移す」対処が入っていない。組版が composition 段の UI スレッドで走り、キャッシュは LazyRow の item ライフサイクルと同じなので、(a) 横スクロールで列が入ってくるたび新規段落の組版が composition に乗る、(b) フォント/行間スライダーのドラッグ中は可視段落が毎値再組版される、の2つが構造上のリスクとして残っている。どちらも実測がないので「遅い」とは言えない——測るなら縦書き ON の ChapterFlip/スクロールと、設定スライダードラッグの追加が最短だと思う。



## 裁定：11項目＋A〜F の実装状況と採否（原文）

実装状況を全項目確認した。結論から：**11項目中5つは実装済み**（うち3つは提案と同じ機序）、A〜F では **F だけ実装済み**。

### 遷移層

| # | 提案 | 状態 | 採否 |
|---|---|---|---|
| 1 | 差し替えフレームの遅延 / alpha=0 並走 | 未実装・**比較枠として明示的に保留中** | 遅延=条件付き採用／並走=不採用 |
| 2 | pop 側 defer・M の導出データ分離 | **両方実装済み** | 対応不要 |
| 3 | 隣ページ常駐の2段階制御 | **実装済み**（前提が逆） | 対応不要 |
| 4 | 星図の drawWithCache / Picture | 未実装 | 条件付き・優先度低 |

**1について。** `NativeReadingScreen.kt:533` に裁定が残っている——settle+0ms を選んだのは「既存の離散信号だけで組めてフレームカウント機構が不要な上、**P2 framestats 実測で差し戻しフレームに 17ms 超のヒッチが無かった**ため。モック提案の settle+2f（+33ms）は後日の実測比較枠として残す」。つまり本棚グリッドでは差し替えフレーム集中は実測で陰性。ただし読書本文・目次の骨→実内容は未測なので、そこは測る価値がある。なお TabPagerHost だけは `withFrameNanos {}` ×2 の遅延が既に入っている（`TabPagerHost.kt:97`）——骨の差戻しと隣ページ初回 composition が同一フレームに来るのを防ぐため。

alpha=0 並走は不採用を推す。measure/layout は alpha=0 でも走るので、これは「重い composition+layout をアニメ窓と並走させる」配置そのもので、このプロジェクトが実測で否定した側（隣ページ常駐で pop 冒頭2フレーム 445/402ms ↔ 非常駐 212/153ms）に戻ることになる。

**2について。** pop enter でも defer は効いている——`MainActivity.kt:530` の `derivedStateOf` は `targetState == Visible && currentState != Visible` で push/pop 両方の enter 窓で真になり、TabPagerHost と BookshelfContent の両方に配られる。reading ルート側は「この route の上へ積むルートは無い＝popEnter で再入場する経路が存在しない」と明記済み（`MainActivity.kt:800`）。

M星図の導出データ分離も提案どおり入っている：`val field = remember { buildDeepSkyField() }`（`SkyBackdropM.kt:177`）＋固定 seed の Lcg で決定論生成＋drawBehind の遅延読み。`remember` を hidden の早期 return より前に置いてあるのは「読書本文への出入りで field を作り直さない（churn 回避）」ため。加えて backdrop は NavHost の**外**（`MainActivity.kt:486` の Box 直下）に居るので画面遷移では破棄されない。再 composition 対象なのは tabs 内の本棚星図面（`BookshelfSkyM`）だけ。

**4について。** field は remember 済みだが描画は毎フレーム全星ループ。ただしフェードが `graphicsLayer` alpha だけなら RenderNode が再利用され re-draw は走らない可能性がある。「本当に毎フレーム再描画されているか」の確認が先で、実装はその後。M は既定スキンではない（既定は K）ぶん優先度も下がる。

### 描画層

| # | 提案 | 状態 | 採否 |
|---|---|---|---|
| 5 | 単一 Canvas + 座標列事前計算 | **実装済み**（オフスレッド化を 2026-08-26 に追加） | A に合流 |
| 6 | Default 先行実行＋±1キャッシュ | **実装済み（2026-08-26）** | 採用（=A） |
| 7 | オフセットの deferred read | **実装済み** | 対応不要 |
| 8 | compiler metrics | 設定済み・**未実行** | 採用（即実行可） |
| 9 | Baseline Profile | 未実装 | 採用 |

8は `-PcomposeCompilerReports=true` のオプトインが `build.gradle:252` に配線済みで、走らせるだけ。しかも保留中の判断がひとつぶら下がっている——`kotlinx-collections-immutable` 依存のコメントに「要否の再判定（外せるか）は composeCompilerReports で skippable 状態を実測してから行うこと＝憶測で外すと LazyColumn の再コンポーズが静かに増える」とある。strong skipping は Kotlin 2.0.20 以降既定 ON なので、この依存が今も効いているかは metrics でしか分からない。

9は `profileinstaller:1.4.1` の依存だけがあり、`baselineprofile` プラグイン・generator・`baseline-prof.txt` のいずれも無い＝**profileinstaller が入っているのに配るプロファイルが無い**状態。

### A〜F

| 項目 | 状態 | 採否 |
|---|---|---|
| A 組版の composition 離脱＋章スコープキャッシュ | **実装済み（2026-08-26）** | 採用・最優先 |
| B ドラッグ中の再組版抑制 | **A に吸収され消滅（2026-08-26）** | A 優先に同意 |
| C BoxWithConstraints 除去 | **実装済み（2026-08-26）** | 採用（A と同時） |
| D advance キャッシュ | 未実装 | キャッシュ採用・**定数化は不採用** |
| E draw 段の splitGraphemes | 未実装 | 採用（安価） |
| F chapterCache の eviction | **実装済み** | 対応不要 |

**D の定数化だけは明確に不採用。** `FontMetricsProvider` の KDoc が正面から否定している——「P0-1 実測で serif の小書き仮名は advance が 64→65px に変わる書体があった。縦送りを『fontSizePx 一律』と決め打つと書体差で版面がずれる」。この interface はまさにその決め打ちを避けるために切られている。`(unitText, charClass, sizePx)` キーのキャッシュは実測値を保つので安全。

**E の確認事項への回答**：`VerticalParagraph` の draw ブロックは引数（layout / paint / renderer）しか読んでおらず snapshot state 読みは無い（`VerticalParagraph.kt:72-76`）＝RenderNode キャッシュは効く。よって定常スクロール中の実害は限定的で、初回 draw と invalidate 時のコスト＋`BreakIterator` 生成の除去という位置づけになる。

**F は提案そのものが入っていた**：`LinkedHashMap(8, 0.75f, true)` の accessOrder=true ＋ `removeEldestEntry { size > 6 }`（`NativeReadingScreen.kt:482`）。上限6の根拠も「現在章と前後の往復に十分な最小限」と記録あり。

**C の補足**：列高は全段落共通（itemModifier が `fillMaxHeight().padding(vertical = bodyMarginDp.dp)`）なので親で一度取れる。ただし章見出しとブロックは padding が別なので、そこだけ寸法の導出式を合わせる必要がある。

### 順序の提案

0（計測）→ A+C → E → D → 8/9 が素直だと思う。A を入れると B は collectLatest で自然消滅、5・6 も同時に閉じる。0 の「縦書きベンチ」は `ChapterFlipBenchmark` に `verticalMode` 軸が無い状態なので、`FlipBudget`（P50 15 / P90 20 / P99 50ms）の枠組みはそのまま流用できる。

ひとつ注意として、縦書き経路には未再現の報告バグ（章遷移で描画が上部にジャンプ）が残っていて、遭遇時の採取5軸に「縦書き／横書き」が入っている（`docs/knowledge/chapter-transition-scroll-jump-paths-ruled-out.md`）。A で組版の寿命とスクロール位置の関係が変わるので、着手するならこのバグの再現条件採取を先に閉じるか、少なくとも「A 前の挙動」を記録してから入るほうが安全だと思う。

---

## 実装記録: A+C（2026-08-26・ブランチ `feat/round-2026-08-25`）

### A と C が何だったか（この文書の裁定からの確定）

- **A**＝「組版の composition 離脱 ＋ 章スコープキャッシュ」。合わせて 5（座標列事前計算のオフスレッド化）と
  6（`Dispatchers.Default` 先行実行＋前後キャッシュ）を閉じ、B（ドラッグ中の再組版抑制）は
  「collectLatest で自然消滅」（本文「順序の提案」）。
- **C**＝「`BoxWithConstraints` 除去」。補足の「列高は全段落共通なので**親で一度取れる**。ただし章見出しと
  ブロックは padding が別なので、そこだけ寸法の導出式を合わせる必要がある」がそのまま実装指示。

### 何の寿命を何に変えたか

| | 変更前 | 変更後 |
|---|---|---|
| 版面キャッシュの寿命 | `remember` ＝ **LazyRow の item 寿命** | `ChapterTypesetStore` ＝ **章の寿命**（`remember(content)`） |
| 組版が走る場所 | **composition 段・UI スレッド**（`remember` の計算ブロック） | 初回表示だけ composition 段・**再組版はすべて `Dispatchers.Default`** |
| 列高の取得 | 段落 item ごとの `BoxWithConstraints`（N 回の subcomposition） | 親で 1 回（`視野高 − contentPadding − 2×bodyMargin`、ブロックは更に `− 2×S16`） |

新規: `typeset/ChapterTypesetStore.kt`（純 Kotlin＝Android 非依存）。改修: `ui/VerticalChapterContent.kt`、
`perf/TypesetWorkProbe.kt`（`v_typeset_comp` / `v_glyphs_comp` を追加）。**`NativeReadingScreen.kt` は無改変**
（composition 段の組版は `VerticalChapterContent.kt` 側にあった）。

### 回数（JVM で固定した値・`ChapterTypesetStoreTest`）

| シナリオ | 改善前（2026-08-25 実測） | 改善後 |
|---|---|---|
| フォント全振り1ドラッグ（離散10値 × 可視6段落） | `typeset()` **55回** / グリフ **1,845** ／ すべて composition 段 | **composition 段 0回・0グリフ**。背景の回数は端末速度で 1〜10 ラウンドに振れる |
| 列が視界を出入り（同じ段落の再入場） | 入るたびに再組版 | **0回**（章スコープのキャッシュに当たる） |
| 章送り5回 | `typeset()` 28回 | **据え置き 28回**（下記の意図的な線引き） |

**なぜ章送りは下がらないか（意図した線引き）**: 版面が無い item は幅0で置かれるため、(1) 章頭で全段落が
一斉に視界へ入ってから実寸へ跳ね、(2) 既読復元 `scrollToItem(index, offset)` の offset が幅0の item に対して
解決されて着地がずれる。未再現の報告バグ「章遷移で描画が上部にジャンプ」と**同じ形の事故を自分で作る**ので、
**そのスロットの初回だけは同期**に組んで寸法を確定させる。下がるのは反復ぶん（ドラッグ・再入場）で、
28 はもともと「可視段落を章あたり1回」＝すでに下限。

### 実装中に踏んで潰した罠（背景組版に固有）

1. **破棄後の公開がテスト間へ漏れる**。背景の取り消しは協調的で、`typeset()` 実行中に取り消されても1件ぶんは
   走り切る。その公開先が Compose のスナップショット状態なので、composition 破棄後に別スレッドから書くと
   1つの JVM で composition を張っては捨てる単体テストで**次のテストへ漏れる**
   （`VerticalChapterContentScreenshotTest` で、版面が同一な LIGHT が通り**後から走る DARK だけ**落ちる
   順番依存の形。内容起因なら両テーマとも落ちるはずで、そこが切り分けの決め手）。
   → store を `RememberObserver` にし、`onForgotten`/`onAbandoned` 後は組みも公開もしない。
2. **同じ版面での二重公開**。初回表示で同期組版と背景が同じスロットを狙うと、見た目が1ピクセルも変わらないのに
   item が1回よけいに再コンポーズされ、しかもそれが背景スレッドの都合で起きる＝描画の確定タイミングがぶれる。
   → 公開は「同じ寸法の版面を持っていなければ」だけ。加えて先行組版の窓の源を `firstVisibleItemIndex` でなく
   **`layoutInfo.visibleItemsInfo`** にして、レイアウト前は1件も依頼しない（＝初回表示と構造的に競争しない）。

### 端末実測（2026-08-26・AVD `nr_b`＝emulator-5556 / pixel_7 1080x2400 / 蔵書 `demo01`）

**① composition 段の組版**（`bash tools/measure_typeset_work.sh emulator-5556 demo01`）:

| シナリオ | v_typeset | v_typeset_comp | v_glyphs_comp | 分母 |
|---|---|---|---|---|
| vertical / font_drag | 134 | **0** | **0** | font_eff=10 |
| vertical / lineheight_drag | 69 | **0** | **0** | lh_eff=5 |
| vertical / chapter_flip | 30 | **26** | 560 | 章送り5回 |
| horizontal / 3種 | 0 | 0 | 0 | （縦組版を通らない＝横書き経路は無改変） |

スライダー2種で composition 段は**実機でも 0**＝A の主目的は達成。章送りの 26 は**初回同期を残した設計どおり**
（0 にはならない・上の「なぜ章送りは下がらないか」）。

⚠️ **総回数は増えている**（font_drag 134 ＝ 10値 × 約13.4スロット）。可視は6段落なので、旧構造の
「可視のみ×10値＝約60」に対し、**先行組版の窓（可視 ±(4,8)）ぶんが上乗せ**された形。UI スレッドの外へ出た
うえでの上乗せであり、スクロール先が既に組んであることの対価だが、**「回数が減った」ではない**。
しかもこの上乗せは端末が速いほど大きくなる（遅い端末では `collectLatest` が途中で捨てるため小さくなる）＝
**端末非依存に言えるのは `v_typeset_comp` の 0 だけ**。ドラッグ中だけ窓を可視範囲へ絞る余地は残っている（未着手）。

**② 3系統照合**: 既定の `scroll` モードは demo01 では成立しなかった（1章が約3スワイプで尽き、
ハードコードの4スワイプが章末を越えて章送りに化ける＝全スナップショットが idx=0 off=0 になる）。
送り回数だけ章の長さに合わせて同じ観測項目で採り直した結果:

| 系統 | 観測 | 判定 |
|---|---|---|
| 章頭 | `chap_1 idx=0 off=0` / 最右列右端x=975（見出し列が画面右端に届かない徴） | 期待どおり |
| 既読復元 | 送り後 `idx=7 off=91` 最右段落=04529f 列左端x=[990,872,754,518,400,46] → 次章→前章で**完全一致** | **ズレなし** |
| 位置なし章 | chap_2・chap_3 とも `idx=0 off=0` / 右端x=975＝章頭と同じ徴 | 期待どおり |

初回同期を残した判断（幅0 item で `scrollToItem(index, offset)` の着地がずれる懸念）は、既読復元が
`idx`/`off`・段落ハッシュ・列左端座標まで一致したことで**この範囲では裏づけられた**。

⚠️ **2026-08-25 の記録そのものはリポジトリに残っていない**（数値がどのファイルにも無い）ので、
突き合わせたのは**同一実行内の不変条件**（復元 D が送り後 B と一致するか／位置なし章が章頭と同じ徴か）。
復元軸についてはこちらの方が強い検査だが、「前回の記録と同じ値か」は確認できていない。
同一条件でやり直すなら、ベースラインが使った蔵書 `5e3cb10d` は **AVD `nr_d`（emulator-5562）** に在る。

- 章遷移ジャンプは**この便では何も主張しない**（未再現のまま）。
