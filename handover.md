# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> **ここに置くのは「Claude が今すぐ着手できるやること」だけ**——実装残・調査残・計測・ドキュメント作業、
> それに作る予定のものとあとで拾う思いつき（末尾の「思いつき・取りこぼし」へ追記して育てる）。
> **人間の実機目視・意匠/方針の裁定・外部手続き（ブランド名・鍵・Play Console・第三者）は `awaiting-human.md`**——
> 分けた理由と責務、**どちらに置くか迷ったときの規則（迷ったらこちら＝handover 側）**は **ADR 0028** が正本。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了の正本は **git log**・現況は `STATUS.md`）。
> それ以外の置き場（知見・ADR・一次情報など）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——
> ここへ再掲すると片方だけ古くなる（2026-07-25 に STATUS 側で実際に起きた）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。
>
> **凍結・見送り・won't-fix と決めた項目は `docs/backlog-frozen.md` へ退避する**（捨てない・解凍条件つき）。
> 打ち消し線と同じく、**着手しないと決まったものを置き続けると台帳は「やること」でなく「いつかやるかもリスト」に化ける**——
> 実際 2026-07-31 時点で凍結済み5節が 6,257 字を占めており、それが肥大の最大の単一要因だった。

## ★最優先方針（2026-07-20 転換 — デフォルトUI明快化＋幅広いサイト対応）

> **背景**: 第三者にアプリを触らせた結果、「豪奢なスキンより先に直すべき本質課題」が2つ判明——①**デフォルトUI（和モダンD）が一目で分からない**、②**なろう限定では求心力が足りない**。
> **豪奢スキン（M/P/J・星図リッチ化＝ui/refine 系）は否定しない＝温存**。ただし優先度は下記 A/B の下に置く。既存のスキン/リッチ化 backlog は消さずそのまま保持。
> 大原則＝memory `feedback-default-ui-legible-first`（デフォルトUIは「一目でわかる」最優先・豪奢さは opt-in の付加価値）。

### 最優先A：デフォルトUI を「一目でわかる・迷わせない」設計へ

> **現在地**: 回答＝新デフォルトUIスキン「明快K」（構造の要約＝`STATUS.md` §0）。設計と裁定の一次情報＝
> `.claude/plans/` の `default-ui-clarity-K-2026-07-23.md`・`k-shape-propagation-2026-07-23.md`・`ui-density-swipe-round-2026-07-24.md`。
> K 形は D/M/P/J へ伝播済み（正本＝`skins/{bookshelf,discovery,toc,settings}-{D,M,P,J}.html`）。以下は**残り**のみ。

- **[装いの間・K ミニチュア]** 現状はトークン D 委譲の自動描画で機能は成立。K らしさ（ナビ付きミニチュア）を出すかは磨き込み判断。
- **[遷移 jank・残り]**（実機スタック報告の計測ラウンドの残り。対処済み分の機序と why は `TabPagerHost` のコメントが正本）
  - **残③【相手は特定済み・有力2案とも潰れた・2026-07-31 に再測して裏づけ】**: 重いのは**さがすの「ランキング期間の横スワイプ」**。
    再測は **12.23%（34/278・p99 250ms）／8.90%（30/337・p99 150ms）** の2本で初回 11.89% を挟む＝**改善も退行もしていない**。
    `draw/record` 一点集中（max 131〜167ms）は健在で、**`measure/layout` は max 0.5ms・GPU も 17ms 止まり＝両方とも完全に無罪**。
    ⚠️ **再測で増えた所見**: 最悪フレームでは **`animation` 段（max 106.4ms・p99 76.6ms）と `draw/record` が同時に載る**。
    Compose は recomposition を Choreographer の ANIMATION コールバックで回すので、これは
    **コンポーズと記録の両方が UI スレッドで積み上がっている**ということ＝下の「真因側」の対処を実数で支持する。
    ※ 同じ「さがす」でも**縦スクロールは 2.29% と静か**なので、面全体が重いのではなくページ実体化が重い。
    ⚠️ **試した2案はいずれも使えないと判明**（同じ道を再探索しないこと）:
    ①**隣接ページの先行実体化（`beyondViewportPageCount=1`）は実機の退行を生む**——Pager の高さは測定した
    ページの最大高で決まるため、未訪問ページの骨30行にページャ高が引っ張られ、現在ページの下に空白が広がる
    （機序と、`TabPagerHost`/`WardrobeScreen` では無罪な理由＝`docs/knowledge/pager-resident-pages-break-wrap-height.md`）。
    ②**`key` 安定化＋`contentType` は対象が存在しない**——ページ内は LazyColumn/LazyRow ではなく素の `Column`＋`forEachIndexed`。
    **残る手は真因側**＝〈行を外側 LazyColumn の item へ平坦化して可視行だけ合成〉〈ページ高を現在ページだけから決める測定〉。
    いずれも**高さ設計の作り直しとセット**なので、着手するなら計測より先に構造の設計から。
    案2（`isScrollInProgress` 連動 defer）は補助策として温存。
    ✅ **採用裁定が出た（2026-08-06）＝着手可**——〈行を外側 LazyColumn の item へ平坦化〉〈ページ高を現在ページだけから
    決める測定〉の構造設計から（計測より先に設計）。sticky 化（下の採用条件タスク）と同じ画面＝便を分けるかは設計時に判断。
  - **副次**: 章送りは **1回につき重フレーム1枚**（48〜113ms・`draw/record 59.6ms`）。回数が少ないので体感は一瞬止まる程度だが、
    「ボタン送りのスライド化」を将来やるなら**この1枚を先に軽くしないとアニメが必ず引っかかる**。
  - **実測ベースライン（2026-07-30・PGEM10 60Hz・スキンK ライト・12フリック/16秒の統一条件）**:
    本棚グリッド **3.82%**（p99 36ms）／さがす縦 **2.29%**（p99 29ms）／読書本文 **1.88%**（p99 28ms）／
    ランキング横 **11.89%**（p99 150ms）。**遷移窓は浄化済みと確認**＝push 最悪 59.7ms・**100ms超ゼロ**、pop 最悪 80.4ms
    （旧ベースライン pop 177〜205ms から明確に改善＝「遷移中は軽量表示」の効果を実測で確認できた）。
    2026-07-31 の目視ツアー中の再測でも本棚グリッド **3.12%**（p99 34ms）で同等・**ツアー全体の累積は 13258 フレーム/2.13%**
    ＝**ランキング横以外に特筆すべき重さは無い**。

- **[採用裁定の条件・実装] ランキング横スワイプの現在地常時表示（期間の sticky 化）**（2026-08-06 に横スワイプ採用裁定・
  その条件として登録）: 期間行が流れて消えると現在地の手掛かりが検索窓だけになる（実機所見）。**UI 追加＝モック先行必須**
  ——〈期間タブ行を sticky に固定〉〈現在期間チップの常時表示〉等の候補を `skins/discovery-K.html` 下敷きでモック化→裁定→翻訳。

### 最優先B：幅広いサイト対応＝汎用オフラインDL基盤（検索→DL→アプリ内で読む）

- なろう限定を脱し、**汎用の取得/抽出基盤**でハーメルン/アルファポリス等も見据える（サイトごとに抽出器を分離できる設計）。オフラインDL＝手元の**蔵書コレクション**の位置づけ。
- **利用規約で禁止のサイトは除外・別対応**（一律スクレイピングしない＝ユーザー裁定）。なろうは公式API＝安全で優先。**公式サイト直行の逃げ道**も併設。
- スクレイピングは**サイトのHTML変更で壊れやすい**前提で設計（脆さは織り込み・公式直行の逃げ道がその保険）。

### 着手順序（2026-07-20 裁定）

- **まず最優先Aの明快さ・バグを一掃**（数日で"他者が困らない"状態へ）。最優先Bの中核設計は並行で下ごしらえ。

### 汎用DL基盤 — 残っている材料

> 設計判断 D1〜D6・カクヨム実構造・フェーズ順＝`.claude/plans/scraping-foundation-design-2026-07-20.md`／
> 汎用アダプタの設計正本＝`.claude/plans/generic-adapter-design-2026-07-23.md`／規約線と全裁定の正本＝**ADR 0024（追記含む）**。
> **対応面の拡大はいったん打ち止め**（表駆動の新規候補は暁で尽き・ヒューリスティック G2 は不採用裁定）。
> 将来の解放条件＝ハーメルン裁定 or グレー勢の再裁定 or 新規 SSR サイトの発見（表1行＋fixture で即追加可）。
> **再開するときに最初に開く表＝`docs/reference/08-web-novel-site-survey.md`**（各サイトの生存・規約・robots・構造の実地照合結果）。

- **[温存メモ・着手時に使う]**（ユーザー指示で保持・ADR 0024 が「handover の注1/注2」として参照している）:
  **注1 Pixiv**＝R-18 はログイン必須＝アプリ内ブラウザ認証（Cookie/セッション保持）が前提・メンバーページ登録もログイン要／
  **注2 アルファポリス**＝連続DL制限あり＝Crawl-delay 厚め＋制限検知バックオフ・リトライ（土台は `ScrapeHttpClient` に実装済み）。
- **[参照資料] 競合のスクレイピング実装解析**: `/mnt/c/Users/qingj/Desktop/project/book-api-analysis/07-competitor-scraping-techniques.md`
  （唯一の実スクレイプ競合 B・約38サイト・jsoup・3抽出戦略・per-host レート制御/WebView Cookie 間借り等の「作法」）。
  **内容が濃いため直読みせず、新アダプタ設計時に委譲ダイジェストで参照**（ユーザー指示）。

## コード健全性監査（2026-08-06）で出た未処理バグ

> **一次情報＝`.claude/plans/code-health-audit-2026-08-06.md`**（機序・実害・直し方・却下したものの理由まで）。
> 検知への投資設計は `.claude/plans/code-health-mechanical-detection-2026-08-06.md`（走査ルール20本を ROI 順・上位3本は実装スケッチ付き）。
> 台帳 `docs/known-bugs-registry.md` の**無防備 17 件が CI のどれにも守られていない**ことへの回答として実施。
> **処理したら消す**（完了の正本は git log）。`(rel)` ＝ release 到達・`(dbg)` ＝ `Features.skinSwitchingEnabled` が
> release で false のため debug 限定（M/P/J スキン）。

- **[1冊復旧が全件一括へ化ける] (rel)** `ui/BookshelfScreen.kt:170` の `pendingScanBook` が plain `remember`。
  launcher の登録キーは rememberSaveable なので**結果は再生成をまたいで必ず届く**非対称。SAF ピッカー表示中の回転で
  `target == null`＝一括の規約へ落ち、全 AutoPdf を FGS キューへ投入＋全 AutoWeb を再スクレイプする。
  `BookEntity` は Parcelable でないので remember→rememberSaveable の単純置換では直らない（`book.id` を持つ）。
- **[fontScale で潰れる器] (rel)** `ui/skins/k/KBottomNav.kt:61` の `.height(64.dp)` でラベル maxHeight が 20dp 固定。
  **リポジトリ自身の golden `KBottomNav_bookshelf_light_2.0.png` が切り落とされた絵を固定している**（verifyRoborazzi は
  退行しか止めない）。同型で `ui/NcodeLinkSheet.kt:215-258`（120dp 固定高で再試行ボタンの 48dp 標的が消える）。
  → `heightIn(min=)` へ＋golden 再記録。
- **[押し出し・誤爆] (rel)** `ui/discovery/NovelDetailScreen.kt:519-548` の作者行に weight が無くジャンルタグが幅0へ
  （同一機序の実機バグ記録が `DiscoveryCommon.kt:256-261` に一次情報として残っているのに詳細画面へ伝播していない）／
  `ui/discovery/DiscoverySearchScreen.kt:711-714,734-737` のピン・× がヒット幅 13dp で隣の語タップを誤爆
  （× に落ちると**履歴が消えて取り消し導線が無い**。同ファイル外の3画面は 48dp 手当て済み）。
- **[支援技術から削除対象を確認できない] (rel)** `ui/BookCard.kt:286-293,682-687`（同型5構造）が選択状態を
  `Role`・`selected`・`stateDescription` のいずれでも宣言せず、`semantics(mergeDescendants)` が子を畳む。
  削除確認ダイアログも件数のみ。規約自体は存在する（`ui/skins/k/DiscoveryHomeK.kt:500` に why 付きの唯一の宣言）。
- **[Back を1回黙って食う] (rel)** `ui/BookshelfScreen.kt:898` の `BackHandler(enabled = selectionMode)` に
  「このページが前面か」の項が無い。`TabPagerHost` は `beyondViewportPageCount = 1` で隣ページを常駐させ、
  OnBackPressedDispatcher は後着優先なので枠側に必ず勝つ。
- **[構成変更で取込フローが巻き戻る] (rel)** `ui/discovery/PdfImportScreen.kt:117,230-231` が plain remember＋無条件 `loadUrl`。
  隣の `WebReaderScreen.kt:86-91` は同じ構図に custom Saver を張って同機序を塞ぎ KDoc に経緯まで書いてある＝移植で済む。
- **[スキン配線落ち] (dbg)** `ui/skins/ShelfFace.kt:79-87` の案C/案X 5フィールドを M/P/J の6面が**受け取って捨てる**
  （走査の起動は route 層で全スキン共通なのに、進捗表示と停止ボタンの唯一の呼び口が描かれない＝中断不能）／
  Web カードの複数選択削除が M/P/J の一覧3面に未配線（選択モード中のタップが画面遷移に化ける）／
  取込バナーが `ProcessingState.source` を見ず Web 取込で進捗が凍結表示（共有 `ui/ProcessingBanner.kt:102` の分岐が横展開されていない）／
  `BookshelfPortalJ.kt:314` が `chrome.isLoading` を読み捨て、Loading 中に確定した pagerState が hero 着地を殺す。
  ⚠️ **必須引数の構造封鎖は「渡し忘れ」しか止めず「受け取って捨てる」を止めない**——台帳の当該行の検知手段欄はこの限界を明記すべき。
- **[その他 (dbg)]** `BookshelfPortalJ.kt:343-357` の Pager に key が無く蔵書の増減で見ている扉が別作品へ入れ替わる（1行）／
  J/M/P の本棚カード9箇所が章数不明（0）を「全0話」と数で描く（D 共通は `progressFractionFor` の枝で構造的に起きない）／
  `ui/skins/p/DiscoveryCartridgeP.kt:671-700` が 66×88dp＋`.clip` でジャンル名を無音で切る。
- **[記録に留めた4件]** `deferHeavyContent` が D/C 共通描画にしか届かず既定スキン K では常に死んでいる（ジャンク・機能破綻ではない）／
  `reduceMotion` が無キー remember で凍結し設定変更がプロセス再起動まで反映されない（判定源が8箇所に散在）／
  `NcodeLinkSheet` の入力欄2本が remember でシートだけ復元される／per-host スロットルの Mutex がインスタンス局所で
  実フェッチする registry が2つある（実効 2req/s 止まりで実害は薄いが、`defaultAdapters` の why が宣言した不変条件は破れている）。

## golden 監査（2026-08-06）— 104枚中20枚が壊れた絵を「正」として固定している

> **一次情報＝`.claude/plans/golden-and-docs-audit-2026-08-06.md`**（第1部）。上のコード監査で判明した構造的限界
> 「**golden は退行しか止めない＝初回記録時に既に壊れていた絵は永久に正として固定され CI は緑のまま**」を、
> 1件の事故でなく面として測った結果。**fontScale 2.0 に限れば 40枚中15枚（約4割）が破綻**。
>
> ⚠️⚠️ **作業順序が本質**: 直すとき **先に `recordRoborazziDebug` を打ってはいけない**——今の破綻がそのまま
> 新しい正解として焼き付く（`docs/knowledge/golden-record-bakes-in-regressions.md`）。**実装を直してから**再記録する。
> 実装4系統を直せば17枚＋KBottomNav 3枚＝計20枚の再記録で片が付く。

- **[根因① 目次の現在地バーが章一覧を押し出す（4スキン同型・golden 7枚）]** `ui/skins/k/TocK.kt:275` /
  `ui/NativeTableOfContentsScreen.kt:387` / `ui/skins/m/TocSkyM.kt:334` / `ui/skins/j/TocPortalJ.kt:295`。
  進捗 Text に weight / maxLines / softWrap のいずれも無く、2.0 で縦5行に膨張して同 Column の
  `LazyColumn(…weight(1f))` に残り高0が渡る（罫線走査で 1.0 は5本→2.0 は1本＝**章行0本**）。
  1240話の本で**目次のタップ対象が1件も存在しない**。
  ⚠️ この golden を撮った `TocKEpisodeDigitsScreenshotTest.kt:34` は「現在地バーが桁数の多い N で崩れる」を
  **赤くなる条件として明記**しており、**テストが自ら宣言した破綻を含む絵を、そのテストが正解にしている**。
  → 進捗 Text へ `weight(1f)` + `maxLines=1`。M/J は golden 0枚なので撮影条件も足さないと再発が見えない。
- **[根因② 表示設定シートの「行間」「本文余白」が 2.0 で到達不能（golden 3枚）]** `ui/ReadingSettingsSheet.kt:436-576`
  ——**ファイル全体に `verticalScroll` / `rememberScrollState` が0件**。文字を大きくして使う層＝行間と余白を最も
  調整したい層が、2項目を一切変更できない。実機は `ModalBottomSheet` でシート高が golden より低く実害はより大きい。
  同ファイル `:400-402` は「末尾に足すと画面外に切れて到達不能になり得る」と**危険を明文で認識しながら**
  縦書きトグルだけを上へ逃がしている。→ シート本体に `verticalScroll`。
- **[根因③ 縦中横の寸法と向きが不一致で字面が接触（golden 4枚）]** `typeset/render/PaintFontMetrics.kt:37-41` /
  `typeset/VerticalTypesetter.kt:161-163` / `typeset/render/GlyphRenderer.kt:51-52,98`。
  寸法側は「単一の半角 ASCII は90度回転させるので measureText がそのまま縦の占有」と書くが、長さ1ランは
  `CharClass.UPRIGHT` へ上書きされ**回転しない**。連結成分解析で「月」「3」「日」が単一成分＝**字面が接触**。
  「3日」「1人」「A社」はなろう本文に頻出＝縦書きで毎回踏む。
  ⚠️ 純層テストは `FakeMonospaceMetrics` が等幅フェイクのため検出不能で、**その KDoc 自身が「その差の吸収は
  golden で担保する」と委ねている**。委ねられた唯一のゲートが、壊れた絵を正として修正を阻む側に回っている。
  → 正立で描くなら縦送りを実インク基準へ、回転させるなら分類を戻す。`PaintFontMetrics.kt:37` の記述も but-for 条件込みで改稿。
- **[根因④ 固定幅・maxLines 無しのラベルが割れる（golden 6枚）]**
  `ui/skins/k/KBottomNav.kt:61`（既知・出発点／**実装便を破棄したので壊れた3枚は残ったまま**）／
  `ui/discovery/DiscoveryCommon.kt:226-237` の順位が `width(34.dp)` のみで**「10」が「1」と「0」に割れる**
  （なろうランキングは常に10件出る＝2.0 利用者は毎回踏む。同ファイル `:256-258` に同型の実機バグ対処コメントが
  既にあるのに同じ行の順位列へ及んでいない）／`TocK_ep4digits_light_1.0` は**1.0 なのに**「第1028話」が
  「第1028」「話」の2行に割れている（`rememberEpLabelWidth` が total=1240 の1本だけを採寸）。
  ⚠️ 後者は 2026-07-29 実機の「第132話が 44dp に収まらず割れる」退行を二度と通さないために**新設された golden**が、
  初回記録の時点で同じ折返しを焼き付けたもの＝**この穴は塞がっていない**。
- **[根因⑤ K の空棚 CTA が 2.0 で1文字ずつ縦積み＋下端切れ（golden 1枚）]** `ui/skins/k/BookshelfK.kt:1334-1338`
  （weight も折返しも無い Row）/ `:1315-1319`（verticalScroll 無し）。蔵書ゼロ＝新規ユーザーが最初に見る画面。
  ただし同画面の FAB は 2.0 でも読めるため機能ブロッカーではない。
- **[網羅の穴]** **画面ルート級で0枚**＝読書画面ルートとクローム（`NativeReadingScreen` / `ReadingChrome`）・
  装いの間（`WardrobeScreen`）。さがす配下5ルート＋シート2種も0枚。`VerticalChapterContent` は
  **虚偽の前提（「Canvas 直描きで fontScale 非依存」）で 2.0・sepia を落としている**が、実装のコメント自身が
  `// sp→px（fontScale 込み）` と書き話数ラベルは実 Compose Text。M/P/J/C スキンは全面0枚（ADR 0027 で出荷外＝優先度最下位。
  ただし目次 HereBar の同型4スキンだけは根因①として例外）。
- **[孤児検出が型として存在しない（現在0件＝潜在）]** PNG 104枚とテスト期待名は **104↔104 の全単射**で孤児は0。
  ただし Roborazzi 1.70.0 の sealed `CaptureResult` は Added/Changed/Recorded/Unchanged の4種のみで
  **PNG 側から走査する経路が実装にも型にも無い**。`cleanupOldScreenshots` も未設定でしかも無言 delete。
  → `Screenshot*Test` を1クラス消すと PNG は git に残り verify は緑＝台帳 B表 `removed-hook-leaves-dead-consumer` と同型。
  caseId 改名は record を打った瞬間に旧名が無言で孤児化する。
- **[検知への投資（推奨順）]** ①**2.0 破綻の走査を書く**——今回の20枚は3パターンに収まり純 Python の PNG デコードで
  検出できる（監査中に3人が独立に自作＝実装コストは実証済み）: (a) 1.0 に在った全幅罫線が 2.0 で減る（根因①の7枚を一撃）
  (b) キャンバス最終行にインクが残る＝下端クリップ (c) 1.0 で1帯だったインクが 2.0 で同一 x 範囲の2帯へ割れる（6枚）。
  **3本で20枚中14枚が機械検出できる**。②**網羅の機械強制**——`MainActivity` の `composable(...)` ルート一覧 ×
  THEMES × FONT_SCALES と golden 接頭辞を突合し、未撮影は理由付き除外リストに載せないと赤
  （`DiscoveryHomeInvariantCoverageTest` の `acknowledgedOutOfScope` が流用できる。同じスクリプトで孤児も閉じる）。

## docs 陳腐化監査（2026-08-06）— 37件・腐りやすさは台帳80% > patterns 56% > skills 33% > ADR 13% ≒ knowledge 9%

> **一次情報＝`.claude/plans/golden-and-docs-audit-2026-08-06.md`**（第2部・全37件の個別 finding つき）。
> **分岐点は「現在形で書いているか」**——ADR / knowledge は過去形の判断と機序を書くので時間に強く、
> 台帳 / patterns は現在値と手順を書くので実装が動くたび嘘になる。skills は密度3位だが
> **必須ゲートとして毎回読まれる＝誤りが即座に行動へ変換される**ため実害の期待値では最上位。

- **[最重・出荷を壊しうる] `docs/knowledge/apk-has-no-native-libs-16kb-page-not-applicable.md`** の
  「APK に .so は1本も入らない＝16KBページ非該当」が**誤り**。実 APK（release/debug 両方）に **.so は8本**入っている
  （`androidx.graphics:graphics-path` ＋ `androidx.datastore:datastore-core-android` の4ABI×2）。
  ⚠️ **偽測定の機序まで特定済み**——この環境に `unzip` が無く `unzip -l <apk> | grep '\.so$'` が無出力→0件を返していた
  （memory `bash-pipe-masks-exit-code-false-green` の型）。`build.gradle:208,226` は正しく「4ABI×2＝8本」
  「release APK は .so 8本すべて 0x4000」と書いており**同日付で正面から矛盾**している。
  実害＝依存バンプ担当が16KBページ整列確認を「非該当だから不要」と恒久的に飛ばし、上流が非対応版に差し替わった瞬間に
  **Android 15+ の16KBページ端末で起動不能な APK を無検査で出荷**する。→ 撤回か全面改稿し、実測コマンドを
  `python3 -m zipfile -l` へ（`unzip` 非導入環境でも可。作法は同ディレクトリ `agp-srcdir-taskprovider-drops-builtby.md:18`）。
- **[コード内コメントの正面矛盾] `domain/ShelfItems.kt:266-273` vs 同 `:295-307`**——上は「reachedEnd は
  ProgressEntity に無く FINISHED は未成立」と現在形で断定するが、`ProgressEntity.kt:26`（v18 追加）・
  `MIGRATION_17_18`・`:307` の判定・`ProgressDao.kt:46` の書込まで全て稼働中。**両コメントが同じ典拠を名乗って真逆**。
- **[必須ゲートの誤り] CI ゲートは6本なのに `/build` と STATUS が「5つ」「計5ゲート」と数えている**（ktlint 欠落）。
  `ci.yml:73-75` に `ktlintCheck` が実在し 2026-08-05 にブロッキング復帰済みだが、**md 側のどこにも書かれていない**
  （`grep -rn ktlint --include=*.md .` が0件）。この節どおり回した Claude は未使用 import 1つで CI を赤にする。
- **[禁止事項への誘導] 禁止中の agy への委譲を skill 2本＋参照台帳が今も規定**（`hallucination/SKILL.md:45-46` /
  `hallucination-ground-truth.md:34,42` / `shiori-tips/SKILL.md:32(b)`）。CLAUDE.md は「2026-07-24〜 使用禁止」だが
  **代替手段（Claude サブでの意味監査）がどこにも書かれていない**。`~/.local/bin/agy` は実在するので
  Bash 直叩きなら**ユーザー裁定に反した委譲が実際に成功してしまう**。
- **[存在しない env 前提が5箇所] `CLAUDE_CODE_SUBAGENT_MODEL`** — `~/.claude/settings.json` の env は6件で該当変数は無い。
  `orchestration/SKILL.md:88` / `shiori-tips/SKILL.md:32` / `.claude/agents/general-purpose.md:10` /
  `.claude/agents/device-verify.md:21` / handover 自身。⚠️ **handover は同じ段で「現状は env で opus 固定」と書いた直後に
  「2026-08-06 に opus固定指示は解除済み」と自己矛盾している**（下の workflow / tooling 節）。
- **[その他 A 判定]** `/build` が撤去済み機構と存在しない memory を根拠に「テストは前景で」と縛っている／
  `/build` の「`gw` が必須な理由＝CRLF」は誤り（gradlew は LF）／ADR 0005 §C「スキン着せ替えは実装しない」が
  現在形のまま撤回注記なし／ADR README 索引の2エントリが完了済み作業を未着手と書いている／
  `Motion.kt` のタブ切替コメントが廃止済み crossfade を説明／**handover の「章見出しの話数ラベルは描画側が誰も
  読んでいない」は false**（描画側5ファイルが使用中）／**STATUS の「公開準備は作業ブランチ（worktree）で進行中」
  ——そのブランチも worktree も存在しない**／`registry` の `mock-code-drift` 検知手段「手動実行」は誤り（CI が毎push）／
  `02-narou-api-digest` の order 値 `daily_point` は実在せず `weekly` は別指標／
  `registry:120` の「48枚」は 0ab7f70(2026-07-30) まで正しく、**同じ日の次便で85枚へ跳ねて9日間放置**され、
  同行の参照先 `golden-regression-baselines.md` も別系統（PDF抽出の基準値表）を指している。
- **[検知への投資（順位つき）]** ①**台帳・CI コメント・skill から「現在値の数値」を消すか機械生成にする**——
  今回の数値ズレ5件（48枚×2・1072件・297組・5ゲート）は全て `ls`/`wc`/checker 実行で1秒で出る。
  `.claude/skills/build/SKILL.md:73` が既に「枚数は増えるので書かない」と規約化しているので**適用範囲を全 md へ広げ、
  `/stale-check` に「md 中の『N枚 / N件 / 計Nゲート』を実測と突合する」検査を足す**。②**patterns に
  「正本コード: <path>」ヘッダを必須化**し、その path の公開シンボル集合が変わったら赤（patterns は9本＝初期コスト小・汚染率2位を直撃）。
  ③**双方向注記の義務化**——「後の ADR / コミットが前の記述を解除したら解除された側にも注記を打つ」を `/stale-check` へ
  （今回の片方向更新4件が対象）。**しないこと＝knowledge の全数照合**（46本中4本＝9%で費用対効果が低い。
  ただし「外部事実を実測と称して書いた knowledge」だけは別枠＝**使ったコマンドと環境の併記を必須**にする）。
- **[要再確認 8件（F-1〜F-8）]** 実装ワークフローが同時にファイルを編集していた時間帯の指摘で、**その編集は破棄済み**。
  行番号も根拠も現ツリーで取り直すこと。内容は一次情報の第3部（さがす配下の golden 0枚／`patterns/processing-state.md` が
  **再発を招く旧処方を規範として提示している**／`string-hashcode-low-bit-bias` が J で無効と判明済みの因果を M/P へ勧誘/
  `positioning-brief` の機能欠落／`discovery-terminology` の配線依頼2件が撤去済み UI を指す／`backlog-frozen` の
  file:line 3件が全て別の場所／`PdfBookImporter.kt:349-351` の参照先不在／KBottomNav 3枚の再記録）。

## 未修正・調査中のバグ

- **[本文読書中の章遷移で「描画が上部にジャンプする」]**（実機ユーザー報告・**報告者自身も再現できていない**＝再現手順の取得が先決＝`awaiting-human.md` §1-4）:
  - **調査済み＝同じ道を再探索しないこと**: 本文で章遷移時にスクロールが 0 へリセットされる経路は**実コードに存在しない**と判定。
    根拠 ①章→章は `AnimatedContent` が file でキーするため別サブコンポジション＝`LazyListState` は毎回新規（**新章が先頭から始まるのは設計どおり**）
    ②既読章へ戻る経路は `sessionScrollByFile`→`chapterRestore`→(0,0) の順で復元される
    ③本文経路の明示スクロールは「最上部へ」ピルと a11y アクションだけで、いずれもユーザー操作起点。
  - 既知知見 `docs/knowledge/lazylist-loading-full-replace-scroll-reset.md` とは**別系統**（本文の Loading は LazyColumn 自体が unmount される）。
  - **ユーザー確認済みの事実**: 没入読書中に章を送るとステータスバーが実際に出る（＝章送り時の没入破壊・2026-07-29 に構造是正）。
    **ただし「それによるジャンプとは限らない」と留保**＝バー復帰とジャンプの因果は未確定。修正後も残るなら別系統を追う。
  - 再現条件が取れたら Robolectric で赤を出してから直す。
  - **[2026-08-05 の機械ハントは空振り＝次にやるなら設計を変える]** adb で〈スキン×縦横書き×スワイプ/ボタン×章種別〉を
    総当たりし a/b 対を撮ったが、**判定不能**で終わった。回収した教訓が3つ:
    ①**ボタン送りが実行できていなかった**——`hunt-K-h-btn-*` `hunt-K-v-btn-back-*` の a/b が**バイト同一**＝
    座標タップが章送りボタンに当たっていない（＝ボタン送り系は未試行のまま。次回は uiautomator の resource-id 指定で押す）
    ②**期待値を記録しない撮り方では後から判定できない**——スクショだけ残っても「飛んだ」の基準（送る前のスクロール位置）が無い。
    次回は各試行で `dumpsys` か a11y ノードから**スクロール量を数値で控えてから**送ること。
    ③試せたのは**スキン K/D・横書き（K/D）と縦書き（K）だけ**で、**没入 ON/OFF は軸ごと未消化**。
    ⚠️ そもそも報告者自身が再現できていない＝機械の総当たりより**遭遇時の条件採取**が本筋（`awaiting-human.md` §1-4）。
- **[本棚] ⋮メニュー上端に背後文字が覗く**（2026-07-31 に再調査＝**旧記述「D/M 共通で再現」は stale**）:
  記録当時（2026-07-17）の対象だった **D のアプリバー ⋮ は既にコードから消えている**——`ui/BookshelfScreen.kt:1002-1017` の
  TopAppBar `actions` はグリッド/リスト切替1個だけで、同 1013-1016 行に「K 形伝播で⋮ボタンごと除いた」と明記されている。
  D に残る ⋮ はカード内（`ui/BookCard.kt:424`）＝書影の下でアプリバーから約200dp 離れており、「バーとポップアップの隙間」は
  原理的に作れない。**アプリバー直下に ⋮ が残るのは M と J だけ**。
  ⚠️ 着手には **M で再現手順（スキン・テーマ・向き）を1つ確定させるのが先**——隙間の出所は Material3 `DropdownMenu` 自身の
  位置決め × edge-to-edge インセットで**コードからは導けず**、offset の当て推量は過去に否定された手口。
  なお ADR 0027 の出荷スコープ（K 単独）に該当画面は無い＝優先度は低い。

## Google Play 公開準備 — 技術トラック

> 一次情報＝`/mnt/c/Users/qingj/Desktop/project/アプリ公開戦略/`（`Google Play公開戦略.md`・`Google Play 初回公開 完全フロー….md`・`外部リサーチ実査結果_2026-07-19.md`）。
> 決定済み方針＝組織アカウント（個人事業主）／最初から API 36／スキン M/P/J は初回リリースに含めず課金実装後のアップデート目玉に温存。
> **ブランド名・鍵バックアップ・ストア素材・提出フォームはユーザー側＝`awaiting-human.md` §4**（applicationId とストア素材はブランド名待ちの前置き）。

- **[前提・維持する設計上の守り]**（2026-07-19 ユーザー裁定「なろう縦書きPDF取込→独自描画は現設計のまま公開」の条件）:
  ①一括・自動 DL を実装しない ②取込までの導線は公式ページを無加工・広告込みで表示し DL ボタン押下は毎回ユーザー ③外部送信なし・端末内完結。
  裁定の根拠＝取込は**ユーザーの明示的手動操作**であり対象は**なろう公式が提供する PDF の端末内整形再表示**＝第14条23項の「自動化された手段による
  アクセス・データ収集」に当たらない、との解釈。残留リスク（記録のみ）＝明示安全圏（WebView 無加工表示）の外側である点・第14条24項の包括条項。
  必要が生じたら企業・団体向け窓口（syosetu.com/businessinquire/）への事前照会という選択肢は残る。
- **[スコープ] 公開機能ゲート＝残るは解禁便だけ**（実装は 2026-07-31 に完了・実測込みの正本＝**ADR 0027**）:
  フラグ `SKIN_SWITCHING_ENABLED`（debug=true / release・benchmark=false）＋適用点3つ＋両値テストが入った。
  **解禁（課金投入）便はフラグ反転＋R8 実機回帰が1セット**——release を初めて通る塊なので回帰を省略しない。
  公開ビルドでの見え方（きせかえ行が無い・装いの間へ着けない・検証機が明快K で起動する）の**実機目視も解禁前に一度要る**。
  ⚠️ 副作用として**検証機に release を入れると明快K で起動**する（prefs は温存＝debug に戻せば復帰）。
- **[ID] applicationId の変更（初回アップロード前・必須／ブランド名確定後に着手）**: `com.novelreader` は公開後**永久変更不可**。
  作業＝固有 ID へ変更 →`${applicationId}` 参照（FileProvider 等）は自動追従するので**ハードコードの有無を grep で確認**→
  benchmark の `applicationIdSuffix` 追従も確認。⚠️ 実機では**別アプリ扱い**＝既存検証端末のデータ引き継ぎは無い。
- **[Play要件] プライバシーポリシー**: 下書き＝`docs/store/privacy-policy-draft.md`（ホスティングは GitHub Pages で裁定済み）。
  Claude 側の残り＝**公開後にアプリ内からのリンクを設置する**（プレースホルダ確定と公開はブランド名待ち＝`awaiting-human.md`）。

## モック逆同期・意匠の宿題

- **[向き応答していない固定値の棚卸し]**: `Insets.ScrollBottomForFab` / `ChromeHintBottom` はいずれも縦向き前提の 96dp 固定。
  横向きの構造裁定（`awaiting-human.md` §3-1）のついでに見直す。
> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。

- **恒久ルール**（破ると実害が出た実績つき）:
  ①コード先行の視覚変更を入れたら**正本モックへの逆同期 or「未反映」注記をセット**で。
  ②モックのプレビューは必ず `mockview`（素の `chrome <file>` 禁止）。
  ③**本棚を下敷きにする候補モックは既定スキンの現行正本（`skins/bookshelf-K.html`）を下敷きに**——旧世代（`bookshelf-D` 直下系・`fusion-D`）は
  語彙参照のみ可・構造下敷き禁止（2026-07-29 の復旧導線候補が旧基盤で描かれ差し戻しになった実害。委譲時は下敷き正本のパスを仕様に明示する）。
  ④**モックのプレースホルダは実データの色域を模す**（栞紙＝地色同値を濃色板でごまかすと一体化バグを素通しする・実証済み）。
  ⑤**モック目視→実機目視の二段検分を必須**（モックは構図の裁定・実機は実データ衝突の検出＝役割が別）。
- **`reading-vertical-scroll-D.html` と縦書き実装の構造差**: モック正本は「非没入時は本文がバー下から開始（通常フロー＝重ならない）・
  没入時はバー消去」を規定する一方、実装は縦書き本文の上端クリアランスを**意図的に省略**している
  （`VerticalChapterContent.kt`・横書きの `ReadingBodyTopExtra=64dp` は「横画面で列高約4割潰れ＋クローム追従リフロー」を招くため不採用と理由コメント明記）。
  2026-07-29 の「縦書き時タイトル非表示」で重なりの実害は緩和したが**構造差は残る**＝実機目視の結果しだいでモック逆同期 or 実装是正のどちらかへ。
- **`fusion-D`**: 発見帯の未反映は **obsolete**（2026-07-30 実機で全スキンから帯の撤去を確認＝描き直す対象が消えた）。
  `bookshelf-D`・`fusion-D` とも旧世代＝**提案の構造下敷きに使わない**（語彙参照のみ可・下敷きは `skins/bookshelf-K.html`）。
- **richness モック正本の形状統一反映**: `toc-M-rich-R1` / `discovery-M-rich-R1` は画面別seed時代の空のまま（Compose は一枚化で統一済み）。
  正本昇格時に空レイヤを R1s 形へ差し替える（一次情報＝`.claude/plans/richness-expansion-round-2026-07-19.md` 差し戻し節）。

## 読書・目次まわりの残り

- **[縦書き] 章見出しの話数ラベル — 器は用意済み・残るは意匠裁定**（2026-07-31 にデータ/トークンを整備）:
  `domain/ChapterTitle.kt` の `splitChapterTitle` と `Typography.GothicFamily` が入り、
  **描画側はまだ誰もこの型を読んでいない＝見た目は1pxも変わっていない**（golden 緑がその証拠）。
  残るのは**どう組むかの意匠**＝`awaiting-human.md` §3-2 へ登録済み。全体像は ADR 0020・`.claude/plans/vertical-reading-mode.md`。
  スパイク計測器は `android/app/src/debug/` に収載済み（P6 の OPPO 較正で再利用できる）。

## リファクタ / 技術的負債（deferred）

- **[Kotlin2 マージ済み（2026-08-06 main へ ff 統合）の派生宿題]** Kotlin 2.2 の KT-73255 警告（Moshi の `@Json` 付き引数で多数）＝
  `-Xannotation-default-target` は挙動を変える指定なので方針を決めてから別便で（実測台帳＝
  `.claude/plans/macrobenchmark-kickoff-2026-07-17.md` ⑤・連鎖の正本＝ADR 0029。CI は次の push で新 toolchain を初走行）。

## workflow / tooling

- **[bestpractice 突合の回収候補]**: サブエージェントの部品別モデル配分（fan-out/読み=haiku・照合=sonnet・監査=opus。現状は env `CLAUDE_CODE_SUBAGENT_MODEL` で opus 固定＝見直しは settings 変更を伴う。
  2026-08-06 に opus固定指示は解除済み＝再設計の下地は整った——ユーザーの費用/品質選好が要るため実施は提案ベースで）。
  ※①だった migration ガード Bash 経路は 2026-08-06 に決着＝permissions の `if` は実在せず（hooks 側 `if` は fail-open で強制不成立）、
  フック内の正規化照合（env-prefix 展開・クォート除去・断片ペア）で対処済み・テスト＝`.claude/hooks/test_block_destructive_migration.py`。
- **[運用] worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す**: ローカルの自動コミットゲートは現存しない（かつての hook は撤去済み＝導入以来 fail-open だった）。
  2026-07-30 以降は **CI の Android Lint が毎 push で errors=0 を担保する**ので、このスイープの役目は**push 前に赤を見つける**前倒し検知。
  基準＝**0 errors**。warnings は非ブロックの参考値（2026-08-06 実測 92＝UseKtx 51・GradleDependency 22 など
  lint DB・依存新版検知の外部ドリフトで増える。意図的分＝ModifierParameter×3・UsableSpace×2）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。
> **実機で見れば決まるものは `awaiting-human.md` §1 のツアーへ移す**——ここに溜めても誰も見に来ないため。

