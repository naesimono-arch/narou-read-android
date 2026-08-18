# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。** 置くのは**「Claude が今すぐ着手できるやること」だけ**
> （人間の目視・裁定・外部手続きは `awaiting-human.md`／分割規則と迷ったときの既定＝**ADR 0028**）。
> **完了したら打ち消し線で残さず消す**（完了の正本は git log・現況は `STATUS.md`）。
> **凍結・見送りは `docs/backlog-frozen.md` へ退避**（捨てない・解凍条件つき）。
> 置き場の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**／**上限 8,000字＝超えたら削らず正しい置き場へ移す**。

## 最優先A：デフォルトUI を「一目でわかる・迷わせない」設計へ

> 優先度の転換そのものの裁定＝**ADR 0032**。現在地＝新デフォルトUIスキン「明快K」（構造＝`STATUS.md` §0）。
> 設計の一次情報＝`.claude/plans/` の3本（ファイル名は `STATUS.md` §0 に一覧）。
> K 形は D/M/P/J へ伝播済み（正本＝`skins/{bookshelf,discovery,toc,settings}-{D,M,P,J}.html`）。以下は**残り**のみ。

- **[装いの間・K ミニチュア]** 現状はトークン D 委譲の自動描画で機能は成立。K らしさ（ナビ付きミニチュア）を出すかは磨き込み判断。
- **[ランキング横スワイプ＝残るのはページ送りアニメ自体のコスト]**: 据わり失敗は**解消済み**。アニメが走る状態での
  初めての正味の計測が **6.84%** で、残るのは **p90 53ms・p95 69ms が対照（本棚グリッド縦 13/15ms）比で明確に重い**こと。
  ⚠️ それ以前の値（ベースライン 11.89% 含む）はフリングが死んだ状態の計測＝**率で単純比較しない**。
  次に手を入れるならここ。**未着手の別案（入れ子反転）と残件1件（可視域外れでの再凍結の疑い）も含めて
  `docs/knowledge/ranking-pager-jank-slow-ui-thread.md` が正本**（同じ道をもう一周しない）。

## 最優先B：幅広いサイト対応＝汎用オフラインDL基盤（検索→DL→アプリ内で読む）

> 優先度の裁定＝**ADR 0032**／規約線と全裁定＝**ADR 0024（追記含む）**／設計＝`.claude/plans/scraping-foundation-design-2026-07-20.md`・`generic-adapter-design-2026-07-23.md`。
> **対応面の拡大はいったん打ち止め**（表駆動の新規候補は暁で尽き・ヒューリスティック G2 は不採用裁定）。
> 将来の解放条件＝ハーメルン裁定 or グレー勢の再裁定 or 新規 SSR サイトの発見（表1行＋fixture で即追加可）。
> **再開するときに最初に開く表＝`docs/reference/08-web-novel-site-survey.md`**（各サイトの生存・規約・robots・構造の実地照合結果）。

- **[温存メモ・着手時に使う]**（ADR 0024 が「handover の注1/注2」として参照）:
  **注1 Pixiv**＝R-18 はログイン必須＝アプリ内ブラウザ認証（Cookie/セッション保持）が前提・メンバーページ登録もログイン要／
  **注2 アルファポリス**＝連続DL制限あり＝Crawl-delay 厚め＋制限検知バックオフ・リトライ（土台は `ScrapeHttpClient` に実装済み）。
- **[参照資料] 競合のスクレイピング実装解析**: `/mnt/c/Users/qingj/Desktop/project/book-api-analysis/07-competitor-scraping-techniques.md`
  （唯一の実スクレイプ競合 B・約38サイト・jsoup・3抽出戦略・per-host レート制御/WebView Cookie 間借り等の「作法」）。
  **内容が濃いため直読みせず、新アダプタ設計時に委譲ダイジェストで参照**（ユーザー指示）。

## golden 監査（2026-08-06）の残り

> 一次情報＝`.claude/plans/golden-and-docs-audit-2026-08-06.md` 第1部。実装の根因①〜⑤と再記録、機械検知3本＋網羅強制テストは消化済み。

- **[非加重子の幅先取り＝残り1件]** 型・事例・直し方＝**`docs/knowledge/unweighted-trailing-steals-row-width.md` が正本**。
  残るのは **P の目次 HUD** だけ＝P の版面裁定待ちで凍結中（決めることは `docs/backlog-frozen.md`「カートリッジP の目次 HUD の幅配分」）。

## 未修正・調査中のバグ

- **[復旧ダイアログの3ボタンが2段に割れる]**（2026-08-17 実機・**真因まで確定**）: 実測 y は「場所から探す」1774–1855 に対し
  「自分で選ぶ」「やめる」が 2014–2095＝**確定ボタンだけが 240px 上の独立行**に乗り否定側2つが下段で揃う。
  真因＝`BookshelfScreen.kt:656-664`（復旧ダイアログの `dismissButton`）が `Row{自分で選ぶ, やめる}` と**2ボタンを1スロットに詰めている**こと
  ——M3 の `AlertDialogFlowRow` からは幅544px の巨大要素に見え、確定（336px）と並べると TextButton の contentPadding 込みで
  内寸1088px を超えて折り返す。⚠️ **直し方は意匠の裁定が要る**（M3 AlertDialog のスロットは確定/否定の2つしかなく、3アクションを
  1段に並べるには独自レイアウト／アクションを減らす／ラベルを詰めるの選択になる）＝**裁定の入口＝`awaiting-human.md` §1-1**。
- **[作品詳細のあらすじが4アクション版で初期可視0行]**（2026-08-17 実機で新規発見）: `lastReadEpisode>0` かつ未取込で
  アクションが4本になると**あらすじ本文がノードごと消失（可視0px）**・見出し「あらすじ」も h=80（3アクション版 h=112）＝**28.6%クリップ**。
  スクロールすれば本文 h=916（8.8行）が出る＝消失ではなく**初期ビューポート溢れ**。
  ⚠️ **直す前に意匠裁定のやり直しが要る**（`awaiting-human.md` §1-0＝旧案 (c) は3アクション版前提で足りない）。
  再現条件（**短編では作れない**）＝`docs/knowledge/device-visual-checks-blocked-by-fixture-preconditions.md`。
- **[本文読書中の章遷移で「描画が上部にジャンプする」]**（実機ユーザー報告・**報告者自身も再現できていない**）:
  **調査済みで潰れた経路（＝同じ道を再探索しない）と次にやるべきこと＝`docs/knowledge/chapter-transition-scroll-jump-paths-ruled-out.md` が正本**。
  ⚠️ 機械の総当たりより**遭遇時の条件採取**が本筋＝`awaiting-human.md` §1-4。再現条件が取れたら Robolectric で赤を出してから直す。

## 検証タスク（awaiting-human から機械側へ戻した分）

- **[検証] 残るのは⑥⑦だけ**（2026-08-19 棚卸し＝全43項目中17件が人間待ちではなかった。①〜⑤はテストで決着済み）:
  **⑥ U1 Web 新着の結線＝いまの構造では JVM 化できない**——`NewEpisodeCheckWorker.fetchWebSiteTotals` が内部で
  `SiteAdapterRegistry()` を new して実 HTTP に直結し、**フェイクを差す継ぎ目が無い**（`work-testing` 依存も無い）。
  「どの本が再フェッチ対象に選ばれたか」を観測する手段が原理的に無いので、やるなら**`src/main` に注入点を作る改修が先**
  ＝テストのために本番構造を変える是非の判断が要る。⚠️ `sourcescan` での代用は文字列照合＝脆いので採らない。
  **⑦ §1-4 push スケルトンの jank 数と §1-7 release の PDF 取込は実機の「数値計測」**（目視ではない。後者は SAF を uiautomator で叩ける）。
  **検分方法・既に決着していた分・台帳の誤記＝`docs/knowledge/awaiting-human-machine-decidable-triage.md` が正本**。
  ⚠️ 横向き固定トップは**実測 105.5dp**（Robolectric・insets 0／fontScale 1.0）＝モックの 108dp と整合。「116dp では」の疑いは否定済み。

## Google Play 公開準備 — 技術トラック

> 一次情報＝`/mnt/c/Users/qingj/Desktop/project/アプリ公開戦略/`。決定済み方針＝組織アカウント（個人事業主）／最初から API 36／
> スキン M/P/J は初回リリースに含めず課金アップデートの目玉に温存。
> **ブランド名・鍵バックアップ・ストア素材・提出フォームはユーザー側＝`awaiting-human.md` §4**。
> **維持する設計上の守り3点**（一括/自動DLを実装しない・取込導線は公式ページを無加工広告込みで毎回ユーザー操作・外部送信なし）と
> その規約解釈・残留リスク＝**ADR 0011「公開判断としての再確認」が正本**。

- **[スコープ] 公開機能ゲート＝残るは解禁便だけ**（実装済み・正本＝**ADR 0027**）: フラグ `SKIN_SWITCHING_ENABLED`。
  **解禁（課金投入）便はフラグ反転＋R8 実機回帰が1セット**——release を初めて通る塊なので回帰を省略しない。
  公開ビルドでの見え方（きせかえ行が無い・装いの間へ着けない・検証機が明快K で起動する）の**実機目視も解禁前に一度要る**。
  ⚠️ 副作用＝**検証機に release を入れると明快K で起動**する（prefs は温存＝debug に戻せば復帰）。
- **[ID] applicationId の変更（初回アップロード前・必須／ブランド名確定後）**: `com.novelreader` は公開後**永久変更不可**。
  作業＝固有 ID へ変更 →`${applicationId}` 参照（FileProvider 等）は自動追従するので**ハードコードの有無を grep で確認**→
  benchmark の `applicationIdSuffix` 追従も確認。⚠️ 実機では**別アプリ扱い**＝既存検証端末のデータ引き継ぎは無い。
- **[Play要件] プライバシーポリシー**: 下書き＝`docs/store/privacy-policy-draft.md`（ホスティングは GitHub Pages で裁定済み）。
  Claude 側の残り＝**公開後にアプリ内からのリンクを設置する**。

## モック逆同期・意匠の宿題

> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。
> モック運用の**恒久ルール5つ**（逆同期・`mockview` 必須・下敷き正本・プレースホルダの色域・二段検分）は `/visual-language` skill が正本。

- **[`skins/discovery-K.html` の実装ドリフト＝残り1件]**: **一覧行のメタが正本 1段に対し実装 `NovelListRow` は 3段**
  （題名／作者＋ジャンルタグ／状態・読了目安・**期間pt**）。⚠️ **前提が確定**＝sticky は A 案据え置き（ADR 0033）で
  案F（pt 昇格）は不採用＝pt は行メタに残る。**正本を 3段へ逆同期してよい**が、横向き T4 案（行メタを2段へ畳む）が
  採られると再び動くので `awaiting-human.md` §3-1 の裁定後に。
- **[向き応答していない固定値の棚卸し]**: `Insets.ScrollBottomForFab` / `ChromeHintBottom` はいずれも縦向き前提の 96dp 固定。
  横向きは NavigationRail で確定済み（ADR 0034）＝**意匠裁定（`awaiting-human.md` §3-1）が出たら Compose 翻訳と同じ便で見直す**。
- **`reading-vertical-scroll-D.html` と縦書き実装の構造差**: モック正本は「非没入時は本文がバー下から開始」を規定するが、
  実装は縦書き本文の上端クリアランスを**意図的に省略**している（不採用の理由＝`VerticalChapterContent.kt` のコメントが正本）。
  実害は緩和済みだが**構造差は残る**＝実機目視の結果しだいでモック逆同期 or 実装是正のどちらかへ。
- **`fusion-D`**: 発見帯の未反映は **obsolete**（全スキンから帯の撤去を確認＝描き直す対象が消えた）。
  `bookshelf-D`・`fusion-D` とも旧世代＝**提案の構造下敷きに使わない**（語彙参照のみ可・下敷きは `skins/bookshelf-K.html`）。

## リファクタ / 技術的負債（deferred）

- **[perf] ページ範囲並列 `loadPages` の本実装**（**採否は裁定済み＝採用**・正本 **ADR 0035**）:
  実機実測は長編8,668ページで **K=3 が 2.1x**（等価性は全走行一致）。**K は `maxMemory()` から動的に決める**
  ——K=3 のピーク 360.5MB に対し検証機の上限が 384MB＝残余 23.5MB しかなく、192〜256MB 機では確実に越えるため。
  ⚠️ **`OutOfMemoryError` の捕捉は効かない**（ART が投げる前に OEM が `o-kill`）＝事前にヒープから K を決めるのが唯一の防ぎ方。
  ⚠️ **K=1 は単一より遅い**（0.87〜0.96x）＝K=1 相当のときは従来の単一経路を通す分岐にする。
  残りは進捗の atomic カウンタ化とキャンセル再設計（変更が `loadPages` に閉じない）。

- **[perf] 縦書き本文の組版を composition から外す（A+C）— まず計測から**（採否の裁定済み・着手待ち）:
  縦書きは組版が **UI スレッドの composition 段**で走り、キャッシュが LazyRow の item 寿命と同じ＝列が入るたび、
  フォント/行間スライダーはドラッグの毎値で可視段落が再組版される構造。**ただし実測がまだ無い**——先に
  `ChapterFlipBenchmark` へ `verticalMode` 軸を足す（`FlipBudget` P50 15/P90 20/P99 50ms をそのまま流用）。
  順序は **0（計測）→ A+C → E → D → 8/9**。全16項目の実装状況・採否・不採用の根拠（alpha=0 並走／advance 定数化）＝
  `.claude/plans/reading-render-perf-triage-2026-08-18.md`。
  ⚠️ A は組版の寿命とスクロール位置の関係を変える＝**未再現の縦書き章遷移ジャンプの「A 前の挙動」を記録してから**入る。

- **[perf] Compose compiler metrics を1度取る**（配線済み＝`gw -PcomposeCompilerReports=true` で走らせるだけ）:
  route→Content のラムダ安定性・各層の skippable 判定が未計測。`kotlinx-collections-immutable` を外せるかの
  再判定もこれ待ち（`android/app/build.gradle` の同依存コメントが「憶測で外すな」と保留している）。

- **[Kotlin2 派生の宿題]** Kotlin 2.2 の KT-73255 警告（Moshi の `@Json` 付き引数で多数）＝
  `-Xannotation-default-target` は挙動を変える指定なので方針を決めてから別便で（連鎖の正本＝ADR 0029）。

## workflow / tooling

- **[運用] worktree 作業の冒頭で `gw :app:lintDebug` を回す**（基準＝**0 errors**・warnings は非ブロックの参考値）。
  ローカルの自動コミットゲートは現存せず CI が毎 push で担保するので、役目は**push 前に赤を見つける**前倒し検知。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。
> **実機で見れば決まるものは `awaiting-human.md` §1 のツアーへ移す**——ここに溜めても誰も見に来ないため。
