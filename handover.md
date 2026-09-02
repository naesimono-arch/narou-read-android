# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。** 置くのは**「Claude が今すぐ着手できるやること」だけ**
> （人間の目視・裁定・外部手続きは `awaiting-human.md`／分割規則と迷ったときの既定＝**ADR 0028**）。
> **完了したら打ち消し線で残さず消す**（完了の正本は git log・現況は `STATUS.md`）。
> **凍結・見送りは `docs/backlog-frozen.md` へ退避**（捨てない・解凍条件つき）。
> 置き場の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**／**上限 8,000字＝〈消化〉の合図**
> ——縮める・逃がすのではなく**上から実行して消し込む**（移してよいのは知見・凍結など、そもそもやることでないものだけ）。

## 最優先A：デフォルトUI を「一目でわかる・迷わせない」設計へ

> 優先度の転換そのものの裁定＝**ADR 0032**。現在地＝新デフォルトUIスキン「明快K」（現況＝`STATUS.md` §0）。
> 設計の一次情報＝`.claude/plans/` の `default-ui-clarity-K-2026-07-23.md`・`k-shape-propagation-2026-07-23.md`・
> `ui-density-swipe-round-2026-07-24.md`。K 形は D/M/P/J へ伝播済み
> （正本＝`docs/design-candidates/skins/{bookshelf,discovery,toc,settings}-{D,M,P,J}.html`）。以下は**残り**のみ。

- **[ランキング横スワイプ＝B は縮んだ・次は `matchParentSize` の役割依存]**（2026-08-26 に実測。正本＝`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`）:
  枠を役割でなく**期間 ordinal の偶奇**で持つ変更を入れ、半ページ越えフレーム（B）の `Constructing StaticLayout` が
  **96個→9個**・フレーム長 61–76ms→**33–35ms**。4フリックの合成量 554→204（−63%）。
  ⚠️ **B は消えていない＝縮んだだけ**。残るスパイクは `measureAndLayout` 12.9–14.8ms で **StaticLayout がほぼ0なのに measure が残る**
  ＝原因はテキストでなく**レイアウトの測り直し**。ほぼ確実に `RankingPageLayerK` の `matchParentSize` が役割で入れ替わる
  （据わりだけが高さを決める）ため＝**次の一手はここ**。ここまでやって初めて B が平らになる見込み。
  ⚠️ **A（覗き行の新規合成・StaticLayout 42個×1フリック1枚）は想定どおり残存**。常駐化には
  テスト観測点を〈合成されていない〉から〈表示されていない〉へ強化するのが先。
  ⚠️ **`gfxinfo` の率（29.7%→11.0%）を成果として引用しない**＝同一APKの3走で total p50 が 33〜48ms と揺れる（エミュの GPU 負荷差）。
  ⚠️ **体感が直ったとはまだ言えない**＝2026-08-21 にユーザーが「ジャンクが気になる」と体感で確認した件の再判定は**実機ツアー待ち**。
## 最優先B：幅広いサイト対応＝汎用オフラインDL基盤（検索→DL→アプリ内で読む）

> 優先度の裁定＝**ADR 0032**／規約線と全裁定＝**ADR 0024（追記含む）**／設計＝`.claude/plans/scraping-foundation-design-2026-07-20.md`・`generic-adapter-design-2026-07-23.md`。
> **対応面の拡大はいったん打ち止め**（表駆動の新規候補は暁で尽き・ヒューリスティック G2 は不採用裁定）＝**今すぐ着手する項目は無い**。
> 将来の解放条件＝ハーメルン裁定 or グレー勢の再裁定 or 新規 SSR サイトの発見（表1行＋fixture で即追加可）。
> **再開するときに最初に開く＝`docs/reference/08-web-novel-site-survey.md`**（各サイトの生存・規約・robots・構造の実地照合結果。
> 着手時に効く実装前提〔注1 Pixiv のログイン必須・注2 アルファポリスの連続DL制限〕と競合スクレイピング解析へのポインタも
> 同ファイル末尾「着手時に使う温存メモ」に集約済み＝ADR 0024 が参照する「注1/注2」の名前はそのまま）。

## 未修正・調査中のバグ

- **[本文読書中の章遷移で「描画が上部にジャンプする」]**（実機ユーザー報告・**報告者自身も再現できていない**）:
  **調査済みで潰れた経路（＝同じ道を再探索しない）と次にやるべきこと＝`docs/knowledge/chapter-transition-scroll-jump-paths-ruled-out.md` が正本**。
  ⚠️ 機械の総当たりより**遭遇時の条件採取**が本筋＝`awaiting-human.md` §1-4。再現条件が取れたら Robolectric で赤を出してから直す。

## 独立再実装実験の持ち帰り（2026-08-30）

> 方法論・適用条件＝`docs/knowledge/independent-reimpl-anchoring-method.md`／
> 所見の詳細＝`.claude/plans/archive/indep-reimpl-experiment-2026-08-30.md`＋`~/indep-reimpl/diff-work/diff-report.md`。
> 第二実装 `~/naro-pdf-engine/` の扱いは awaiting-human 裁定待ち＝**裁定前に消さない**。

- **[web 原文オラクルの回帰テスト導入]**（最優先の方法論的修正）: golden は旧 Python 複製＝系譜内で、下記欠陥を検出できない構造。
- **[抽出コア欠陥6クラスの真因調査→修正]** S1 半角スペース脱落／S2a U+FFFD／S3 空行復元全滅／S4a ルビ親範囲／S4b 傍点ルビ／S7 `'` 行頭移動（症状・規模＝一次情報 §4。症状でなく真因から）。
- **[縦書き組版の規範突合]** `CharClass` ⇄ UTR#50・`LineBreaker` 禁則 ⇄ JLReq（＝`docs/knowledge/vertical-typeset-external-oracle-gap.md`。差分は棚卸し→人間裁定）。
- **[計測スクリプトの軽量突合]** 意思決定を左右した `measure_read_residency` / `measure_session_length_cost` 等2〜3本を、仕様文からの独立サブ再実装→数値突合（オラクル無し領域・69%→4.3% の前科）。
- **[裁定材料の web 検分]** S2b 字種写像・段落結合方針（現行の段落誤り率計測を含む）／「寸法自動検出」導入動機を git log・ADR で確認→前提の要否裁定。
- 小粒: scrape 暁ゴールデン 66 件の実装非依存カウント手段（次回 fixture 接触時に同梱）。

## Google Play 公開準備 — 技術トラック

> 一次情報＝`/mnt/c/Users/naesimono/Desktop/project/アプリ公開戦略/`。決定済み方針＝組織アカウント（個人事業主）／最初から API 36／
> スキン M/P/J は初回リリースに含めず課金アップデートの目玉に温存／ブランド名＝`Yosari`（2026-08-19 確定）。
> **鍵バックアップ・ストア素材・提出フォームはユーザー側＝`awaiting-human.md` §4**。
> **維持する設計上の守り3点**（一括/自動DL・無加工広告込みの毎回ユーザー操作・外部送信なし）とその規約解釈・残留リスク＝
> **ADR 0011「公開判断としての再確認」が正本**＝取込まわりを実装する便で開く。

- **[スコープ] 公開機能ゲート＝残るは解禁便だけ**（実装済み・フラグ `SKIN_SWITCHING_ENABLED`・正本＝**ADR 0027**）:
  **解禁（課金投入）便はフラグ反転＋R8 実機回帰＋公開ビルドでの見え方の実機目視が1セット**——release を初めて通る塊なので省略しない
  （見え方＝きせかえ行が無い・装いの間へ着けない・検証機が明快K で起動する。副作用と機序は ADR 0027 が正本）。
- **[ID] applicationId を `app.yosari.reader` へ（初回アップロード前・必須・着手＝§1 実機ツアーの後）**: `com.novelreader` は公開後**永久変更不可**。
  **作業は `android/app/build.gradle:113` の1行だけ**——2026-08-19 に grep 済みで**ハードコードなし**・`namespace`（Kotlin パッケージ）は**据え置きでよく**、
  FileProvider authority（`${applicationId}.fileprovider`）と benchmark の `applicationIdSuffix` は自動追従する。
  ⚠️ 順序の理由＝ID を変えると実機は**別アプリ扱いで空データ起動**（実蔵書は旧パッケージ側に残る）＝実蔵書前提の §1 ツアーが回せなくなる。
  ⚠️ ID とドメインは無関係＝`yosari.app` の取得は**待たない**。
- **[Play要件] プライバシーポリシー**: 公開の機械作業は `docs/store/pages/`（`stage-publish.py`）へ一発化済み。
  Claude 側の残り＝**公開後にアプリ内からのリンクを設置する**（現状アプリ内にリンクは無い）。
- **[内部テスト便] In-App Review の実表示確認と release R8 実機回帰を1便でやる**（2026-08-21 裁定＝公開前に内部テストトラックへ一度挑む）:
  In-App Review は**内部テストへ配信しないと確認できない**（sideload では ReviewManager が no-op）。実装とトリガ
  （初回読了の false/null→true 遷移・セッション1回＝`review/ReviewPrompter.kt`）は完了済み＝**残るのは配信して目で見ることだけ**。
  **どうせ AAB を上げる便**なので ①In-App Review の実表示 ②release R8 の実機回帰（収縮起因の欠落）を**同じ便で見る**。
  ⚠️ **上の [スコープ] 解禁便とは別便**（あちらはフラグ反転＝課金アップデート時）。共有するのは release R8 回帰の作法だけ。
  ⚠️ **[ID] の applicationId 変更より後**＝一度上げた ID は公開後に変更できない。

## エミュで踏む・撮る（2026-08-26 に awaiting-human §1 から移管）

> **なぜ移せたか**＝エミュは**実機で踏めなかった前提を作れる**（実蔵書7冊を壊す危険もない）。
> 発火条件と「何を作れば踏めるか」＝`docs/knowledge/device-visual-checks-blocked-by-fixture-preconditions.md` が正本／運用＝`/emulator-verify`。
> ⚠️ **判定（良し悪し）は人間**＝**撮って出すまでが Claude の職務**（スクショを出せばその場で裁定が回る）。

- **[① 未投入の意匠のうち、まだ踏めていない2つ]**（2026-08-26 に6件中4件は撮影済み＝`shots/b1/`）:
  (a) **端フェードの左端**＝実装上スクロール可（`BookshelfK.kt` のチップ行）なのに **adb 合成入力では踏めない**
  ——横ドラッグを親のタブ `HorizontalPager` が必ず奪う（機序と潰した手＝`docs/knowledge/emulator-screenrecord-and-synthetic-input-limits.md`）。
  ⚠️ **前提の問題ではなく入力手段の問題**＝実指なら踏める可能性がある（実機ツアーで見る側）。
  (b) **FAB の出没の「動き」**＝静止の前後2枚は撮れた。動きは蔵書0でしか消えず、`読了`フィルタ0件では空棚にならない。

- **[空棚で CTA と FAB が同一操作を二重に出す（D/M/P/J）]**（2026-08-26 のエミュ検分が発見・未着手）:
  明快K は `BookshelfK.kt:273` の `!isEmptyShelf` で**空棚では FAB を出さない**（2026-08-20 裁定②）＝被りは解消済み。
  ⚠️ **`isEmptyShelf` を持つのは K だけ**で、**D/M/P/J は空棚でも FAB が出る**＝D は CTA「PDFを追加する」と
  FAB「＋PDFを追加」が**同じ操作を2つ並べる**。実物＝`shots/b2/`（5スキン×fontScale 1.0/2.0 を撮影済み）。
  ⚠️ M/P/J は初回リリース非搭載（ADR 0027）だが **D は搭載**＝D だけでも先に直す価値がある。
- **[復旧ダイアログの AutoPdf 分岐だけ出せない]**（2026-08-26・前提の作り方が未確立）:
  3ボタン縦積み（場所から探す／自分で選ぶ／やめる）は踏めたが、**①AutoPdf 分岐だけ再現できず**
  ＝SAF(Downloads) 取込本は `PickPdfPermissionLost` に落ちるため。**次便の入口は
  `docs/knowledge/device-visual-checks-blocked-by-fixture-preconditions.md` に記載済み**。

## モック逆同期・意匠の宿題

> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。
> モック運用の**恒久ルール5つ**（逆同期・`mockview` 必須・下敷き正本・プレースホルダの色域・二段検分）は `/visual-language` skill が正本。

- **[`lineHeight` 欠落の全数掃討（残り 424 箇所）]**（本棚K・discovery のランキング行/ジャンルチップは是正済み）:
  `Text(fontSize = …)` が bodyLarge の `lineHeight = 28.sp` を行箱として継承する形。**全部が不具合ではない**＝
  **器（ピル・バッジ・行）が行箱の外周をなぞる要素だけが実害**なのでチップ/タグ/メタ行から潰す。直し方と機序＝
  `docs/knowledge/compose-lineheight-is-a-floor-not-css-line-height.md`（⚠️ 下限なので自然行高より小さい比は効かない）。
  正本の比は `em` で明示（ゴシックの `line-height:normal` は実測 1.6）。次の候補＝`DiscoveryResultScreen.kt:500,583`（正本 `.cd`）。
  ⚠️ **`NovelDetailScreen.kt` は別体が golden を調査中＝手を入れない**。

- **[ストア用 `phone-2-shelf.png` の撮り直し]**: 本棚K のチップ是正で見た目が変わったため、ストア画像が旧版のまま（エミュ撮影便で回収）。

- **[走査(c) の残り2ケースが赤（CI ブロッキング）]**（2026-09-02・NovelDetail 分は解消済み）:
  残るのは `BookshelfK_grid_mixed_{light,sepia}`（14→15）と `IntroOverlayK_about_intro_light`（0→1）。
  **どちらも同じ PNG を 1.0 粒度で測ると赤が消える**（grid は 1.0画像14/2.0画像11・intro は 0/0）＝
  走査(c) が**異なる粒度で測った本数を突き合わせている**のが機序で、dp 固定インクでは不公平になる。
  NovelDetail 側は「融合塊」という形で現れたので `MAX_ASPECT` で塞いだが、この2件は塊が正方形＝別の現れ方。
  ⚠️ **両粒度の AND を要求すると corpus の赤が 0 件になり感度を検証できない**（真陽性2件は 2026-08-07 に是正済みで
  corpus に残っていない）ので、緩め方は要裁定。BookshelfK は golden 再記録便で絵自体が変わる可能性あり。

## リファクタ / 技術的負債（deferred）

- **[perf] 本棚→目次 push の「尾」は計測系によって出方が違う**（2026-08-21 に予算とベンチ `TocPushBenchmark`／`TocPushBudget` を新設）:
  実機 gfxinfo では 12窓中11窓に 60ms 超が1枚出るのに、macrobenchmark では 60ms 超 0枚で尾が軽い（P90 は逆に macrobench 側が重い）。
  ⚠️ **極端な尾だけが再現しない＝要因未確定**。両者の全数値・食い違いの全表・潰した仮説＝
  `docs/knowledge/toc-push-tail-not-reproduced-in-macrobench.md` が正本。
  計測の作法（アニメが走っていない状態を率で比較しない）＝`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`。

- **[perf] 縦書き本文の組版＝A+C は完了・次は E**（2026-08-26 に A+C 実装。一次情報＝`.claude/plans/reading-render-perf-triage-2026-08-18.md` の「実装記録: A+C」節）:
  **スライダー操作の composition 段の組版は 0回・0グリフ**になった（旧＝フォント全振り1ドラッグで typeset 55回・グリフ 1,845）。
  ⚠️ **「0」が言えるのはドラッグ2種だけ**＝章送りは実測 `v_typeset_comp=26`（初回同期を残した設計どおり・下記）。
  版面キャッシュの寿命を LazyRow の item 寿命から**章の寿命**へ移し、再組版は `Dispatchers.Default` へ。
  ⚠️ **章送りの 28回は据え置き＝意図**。版面が無い item を幅0で置くと章頭で一斉に実寸へ跳ね、
  `scrollToItem(index, offset)` の offset が幅0の item に対して解決されて**着地がずれる**
  ＝報告バグ「章遷移で上部にジャンプ」と**同じ形の事故を自作する**ため、初回だけ同期を残した。
  **端末実測は 2026-08-26 に消化済み**（nr_b／demo01）＝スライダー2種で `v_typeset_comp=0`・3系統とも一致（ズレなし）。
  ⚠️ **照合の中身は「前回の記録との一致」ではない**——**2026-08-25 の数値はリポジトリのどこにも残っていなかった**
  （台帳が「A 前の挙動は記録済み」と書いていたのが**嘘**だった）。代わりに**同一実行内の不変条件**で照合した
  （復元が送り後と一致するか／位置なし章が章頭と同じ徴か）＝復元軸ではこちらの方が強い検査だが、**別物**である。
  ※ ベースラインが使った蔵書 `5e3cb10d` は **AVD `nr_d`** に在る（`nr_b` は demo01〜09 のみ）。
  ⚠️ **`measure_typeset_work.sh` の `scroll` モードは送り回数がハードコード4スワイプ**＝1章が3スワイプで尽きる短い章だと
  **章送りに化けて全スナップショットが `idx=0 off=0` になる**（退行に見えるが内容起因）。章長に合わせて回数を変えること。
  ⚠️ ただし**背景ぶんを含む総回数は増える**（先行組版のぶん）＝「回数が減った」と読まないこと。順序は **E → D → 8/9**。
- **[端末内診断 `diagnostics/` の書き出しUIが未実装]**（STATUS から移管＝2026-08-26。**モック先行が要る**）:
  診断の収集自体は実装済み（**外部送信ゼロ**）だが、端末内から書き出す UI が無い＝集めても取り出せない。
  UI 追加なのでモックが先。⚠️ 受け皿が無いという理由だけで STATUS に残置されていた項目＝
  STATUS は現在値を置く台帳で、やることを置く場所ではない（ADR 0028 の二分に従い handover へ）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。
> **実機で見れば決まるものは `awaiting-human.md` §1 のツアーへ移す**——ここに溜めても誰も見に来ないため。

- **[`check_context_budget.py` Stop hook を `settings.json` で恒久的に無効化する]**（2026-09-01 ユーザー裁定・201k到達の通告を受けて）:
  着手前に CLAUDE.md 規約どおり事前調査を経ること＝`task_diary.md` #26/#28・`docs/decisions/0004`・`0008`・
  auto-memory `hook-implementation-facts`。**撤去は「参照する側」まで含めて1セット**（撤去するフック名で
  リポジトリ全体を grep し、他フックのロジック・コメント・`.gitignore`・skill の記述に残骸が無いことを確認）。

