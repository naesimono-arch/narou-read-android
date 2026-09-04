# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。** 置くのは**「Claude が今すぐ着手できるやること」だけ**
> （人間の目視・裁定・外部手続きは `awaiting-human.md`／分割規則と迷ったときの既定＝**ADR 0028**）。
> **完了したら打ち消し線で残さず消す**（完了の正本は git log・現況は `STATUS.md`）。
> **凍結・見送りは `docs/backlog-frozen.md` へ退避**（捨てない・解凍条件つき）。
> 置き場の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**／**上限 8,000字＝〈消化〉の合図**
> ——縮める・逃がすのではなく**上から実行して消し込む**（移してよいのは知見・凍結など、そもそもやることでないものだけ）。

## 最優先B：幅広いサイト対応＝汎用オフラインDL基盤（検索→DL→アプリ内で読む）

> 優先度の裁定＝**ADR 0032**／規約線と全裁定＝**ADR 0024（追記含む）**／設計＝`.claude/plans/scraping-foundation-design-2026-07-20.md`・`generic-adapter-design-2026-07-23.md`。
> ⚠️ **「最優先A」が無いのは定義の失効ではない**＝A（デフォルトUIの明快化）の残件は完了・移送済みで、最後の1件（ランキング横スワイプの体感再判定）は `awaiting-human.md` §1 の実機ツアーに在る（A/B の定義は ADR 0032 が正本）。
> **対応面の拡大はいったん打ち止め**（表駆動の新規候補は暁で尽き・ヒューリスティック G2 は不採用裁定）＝**今すぐ着手する項目は無い**。
> 将来の解放条件＝ハーメルン裁定 or グレー勢の再裁定 or 新規 SSR サイトの発見（表1行＋fixture で即追加可）。
> **再開するときに最初に開く＝`docs/reference/08-web-novel-site-survey.md`**（各サイトの生存・規約・robots・構造の実地照合結果。
> 着手時に効く実装前提〔注1 Pixiv のログイン必須・注2 アルファポリスの連続DL制限〕と競合スクレイピング解析へのポインタも
> 同ファイル末尾「着手時に使う温存メモ」に集約済み＝ADR 0024 が参照する「注1/注2」の名前はそのまま）。

## 未修正・調査中のバグ

- **[本文読書中の章遷移で「描画が上部にジャンプする」]**（実機ユーザー報告・**報告者自身も再現できていない**）:
  **調査済みで潰れた経路（＝同じ道を再探索しない）と次にやるべきこと＝`docs/knowledge/chapter-transition-scroll-jump-paths-ruled-out.md` が正本**。
  ⚠️ 機械の総当たりより**遭遇時の条件採取**が本筋＝`awaiting-human.md` §1-4。再現条件が取れたら Robolectric で赤を出してから直す。

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

## モック逆同期・意匠の宿題

> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。
> モック運用の**恒久ルール5つ**（逆同期・`mockview` 必須・下敷き正本・プレースホルダの色域・二段検分）は `/visual-language` skill が正本。

## リファクタ / 技術的負債（deferred）

- **[perf] 本棚→目次 push の「尾」は計測系によって出方が違う**（2026-08-21 に予算とベンチ `TocPushBenchmark`／`TocPushBudget` を新設）:
  実機 gfxinfo では 12窓中11窓に 60ms 超が1枚出るのに、macrobenchmark では 60ms 超 0枚で尾が軽い（P90 は逆に macrobench 側が重い）。
  ⚠️ **極端な尾だけが再現しない＝要因未確定**。両者の全数値・食い違いの全表・潰した仮説＝
  `docs/knowledge/toc-push-tail-not-reproduced-in-macrobench.md` が正本。
  計測の作法（アニメが走っていない状態を率で比較しない）＝`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`。

- **[perf] 縦書き本文の組版＝A+C+D+E と 8・9 は完了・残るは D の実機での効き幅の計測**
  （裁定と各項目の中身＝`.claude/plans/reading-render-perf-triage-2026-08-18.md`。
  **8＝Compose compiler metrics の取得は完了**＝知見は `docs/knowledge/compose-compiler-metrics-2026-09-02.md` が正本
  〔skippable は 390/390 で飽和・`kotlinx-collections-immutable` は外せない・実害と誤読された `ChapterPeek` は現行では stable〕。
  **9＝Baseline Profile も配線・生成・取り込みまで完了**〔生成 20,674 行・うち `Lcom/novelreader/` 2,375 行・
  `assets/dexopt/baseline.prof` が 5,247→9,235 バイトへ増えることまで確認〕。D は 2026-09-02 に
  `(unitText, charClass, sizePx)` キー・4,096 LRU で実装済み＝**実機での効き幅は未計測**。
  ⚠️ 覆いの寿命は組版器1個＝章1つ。**プロセス常駐にしてはいけない**＝書体が delegate の属性でキーに無く、
  共有すると D が不採用にした「書体差で版面がずれる」を自作する）:
  ⚠️ **章送りの composition 段の同期組版は据え置き＝意図**。版面が無い item を幅0で置くと章頭で一斉に実寸へ跳ね、
  `scrollToItem(index, offset)` の offset が幅0の item に対して解決されて**着地がずれる**
  ＝報告バグ「章遷移で上部にジャンプ」と**同じ形の事故を自作する**。D でもここを外さないこと。
  ⚠️ **照合に「前回の記録との一致」を使わない**——**2026-08-25 の数値はリポジトリのどこにも残っていなかった**
  （台帳が「記録済み」と書いていたのが**嘘**だった）。**同一実行内の不変条件**（復元が送り後と一致するか／
  位置なし章が章頭と同じ徴か）か、**同じ計測面を積んだ APK 2本の入れ替え**で見ること。
  ※ 蔵書 `5e3cb10d` は **AVD `nr_d`** に在る（`nr_b` は demo01〜09 のみ）。ルビ密度は蔵書差が大きく
  （`5e3cb10d` は章あたり約3・`92bb4f04` は約12）、**ルビ由来の効果は蔵書を選ばないと 0 になる**。
  ⚠️ `measure_typeset_work.sh` の `scroll` モードは**送り回数がハードコード4スワイプ**＝短い章だと
  **章送りに化けて全スナップショットが `idx=0 off=0`** になる（退行に見えるが内容起因）。
  `work` モードは `SCENARIOS`/`ORIENTATIONS`/`START_CHAP`/`SWIPES` で外から絞れる（`scroll_read`＝設定を触らない定常送り）。
  ⚠️ **背景ぶんを含む総回数は増える**（先行組版のぶん）＝「回数が減った」と読まないこと。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。
> **実機で見れば決まるものは `awaiting-human.md` §1 のツアーへ移す**——ここに溜めても誰も見に来ないため。

- **[縦書き] vert が実機で効くか未計測の 24 字**（`VertFeatureCoverage.UNMEASURED_IN_VERT_PROBE`）:
  実データに出るのに PGEM10 の vert 計測が無い字＝`〟`593 件・`“”‘’`7,319 件・`￣`4 件ほか。
  **分類の根拠が同族推定か「表に無いので既定落ち」で、実測ではない**。字ごとの件数・現在の分類・測り方＝
  `docs/knowledge/vert-feature-pgem10-coverage.md` の「未計測の字」節が正本（`/device-verify` 案件）。
  ⚠️ 実機を触る便があれば計測対象に入れる（`VertProbeUnmeasuredTest` が JSONL と突合していて、
  計測を足して台帳から消し忘れると赤くなる）。
  **24字は計測スパイクへ登録済み**（2026-09-04・`Probe1FontFeature.kt` の `TARGET_GROUPS`＝台帳と機械照合して差分ゼロ）
  ＝残るのは実機で走らせて JSONL を回収するだけ。手順＝`adb -s <dev> shell am start -n com.novelreader/com.novelreader.spike.SpikeActivity`
  → `getExternalFilesDir(null)/vert_probe_results.jsonl` を pull → `vert_probe_pgem10.jsonl` へマージ。
  接続の作法は `/device-verify` §0-a2（USB もペアリングコードも要らない）。
  ⚠️ **A 表（字の向き）の規範照合は 2026-09-03 に完了**＝`“”‘’` は UAX#50 で `〝〞〟` と同値の Tr と確定し
  `PUNCT_REPOSITION` へ移した（差分表 A-11）。**残るのは実機計測だけ**で、規範側の宿題ではない。

