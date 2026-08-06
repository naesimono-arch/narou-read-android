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
- **[遷移 jank・残り＝実機での再計測だけ]** ランキング横スワイプの構造是正（行平坦化＋現在ページ基準の高さ測定）は
  実装済み＝残るのは実機での効果測定のみ（`awaiting-human.md` §1 のツアーで消化）。
  **突合するベースライン**（2026-07-30・PGEM10 60Hz・12フリック/16秒の統一条件）: ランキング横 **11.89%**（p99 150ms）／
  本棚グリッド 3.82%（p99 36ms）／さがす縦 2.29%／読書本文 1.88%。2026-07-31 の再測は 12.23%・8.90% で改善も退行もなし。
  設計と却下案の一次情報＝`.claude/plans/ranking-pager-flatten-2026-08-06.md`。
  **副次（未着手）**: 章送りは1回につき重フレーム1枚（48〜113ms・draw/record 59.6ms）。回数が少なく体感は一瞬だが、
  「ボタン送りのスライド化」を将来やるならこの1枚を先に軽くしないとアニメが必ず引っかかる。
- **[モック裁定待ち] ランキング期間の sticky 化**: 候補3案を `docs/design-candidates/skins/discovery-K-period-sticky-{A,B,C}.html`
  に作成済み＝`awaiting-human.md` §3-1 で mockview 裁定を待つ。裁定が出たら Compose へ翻訳する。

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

## golden 監査（2026-08-06）の残り

> 一次情報＝`.claude/plans/golden-and-docs-audit-2026-08-06.md` 第1部（＋末尾「1-2. 走査による追加検出」）。
> 実装の根因①〜⑤と再記録、機械検知3本＋網羅強制テストは消化済み。残るのは下記だけ。

- **[モック裁定待ち] 目次の「ここから再開」チップ×章題の行リフロー（K/M/J）**: 走査(c)が残す唯一の真の破綻。
  真因＝`TocK.kt:399-409` のチップが weight を持たない非加重子で実寸312pxを先取りし、内側Row 650px −
  ep200 − gap48 − チップ312 ＝ 題名に90pxしか残らず全角1字48px＝1行1文字（縦連の正体は話数ラベルでなく章題）。
  M/J は `TocSkyM.kt:388`・`TocPortalJ.kt:355` が `.width(52.dp)` 固定で ep 自体が3行割れ（走査は未検出）。
  ⚠️ **ep 幅だけ直すと題名が M:54px J:40px に落ちて悪化＝ep 幅修正と行リフローは同時に入れる**。
  唯一の合成解＝2.0 でチップを題名の下段へ落とす（1.0 不変を保つには BoxWithConstraints 採寸の条件分岐。
  weight 分割・dp 上限は 1.0 の版面を変えるため不可）＝意匠変更につきモック先行・`awaiting-human.md` 参照。
- **[CI 結線・二段構え] 走査3本を ci.yml へ**: まず `continue-on-error: true` で可視化のみ→上のリフロー修正で
  赤0になってからブロッキングへ（ktlint が 2026-08-05 に辿った経路と同じ）。
- **[機械で捕まらない穴・設計メモ]** 表示設定シートの `verticalScroll` 欠落（根因②）は**どの走査でも検出不能**
  ——非スクロール面の溢れは切れずに空白が残るだけで画素に痕跡が出ない＝実装側の不変条件で縛るしかない。
  等倍側の割れ（根因④の一部）も「1.0 との差」を証拠にする方式では原理的に出ない。
- **[撮影条件の追加候補]** `SettingsScreenK` の `followingSystem=true`（trailing 文言が最長・
  `SettingsScreenK.kt:137-148` は weight も maxLines も無し）は未撮影。


## docs 陳腐化監査（2026-08-06）の残り

> 一次情報＝`.claude/plans/golden-and-docs-audit-2026-08-06.md` 第2部・第3部。
> 確定 A 判定と検知への投資（数値突合・双方向注記・patterns 正本ヘッダ）は消化済み。

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

