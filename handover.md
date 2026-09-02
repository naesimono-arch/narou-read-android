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

- **[ランキング横スワイプ＝B は平らになった・残るは A と実機ツアー]**（2026-09-02 に実測。正本＝`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`）:
  枠の高さの決め手を子の `matchParentSize` から**親の測り方**（`rankingSlotMeasurePolicy`）へ移し、
  半ページ越えフレーム（B）の `measureAndLayout` を **17.2–62.3ms → 0.35–5.6ms** へ。
  ⚠️ **A（覗き行の新規合成・StaticLayout 42–49個×1フリック1枚・measure 25–40ms）は想定どおり残存**＝**次に効く唯一の手**。
  常駐化には `DiscoveryHomeInvariantTest`・`DiscoveryHomeKRankingTest` の観測点を
  〈合成されていない〉から〈表示されていない〉へ強化するのが**先**。
  ⚠️ 各走に1回だけ残る重い B は**役割入れ替わりでなくデータ差替え**（VM の期間追従で新しい行文字列が届くフレーム＝
  是正前にも同じ位置に在る）。ここを追うなら対象は覗きでなく**取得の載せ替え**。
  ⚠️ **`gfxinfo` の率を成果として引用しない**＝同一APKの走間で `total` p50 が大きく揺れる（エミュの GPU 負荷差）。
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

- **[取込元PDF削除が権限失効で失敗しうる（仕様か不具合か未判断）]**（2026-09-02・エミュ検分の派生）:
  **症状（推定）**＝本を削除するとき取込元PDFも消す選択をしても、`DocumentsContract.deleteDocument` に要る書込権限が
  既に無く失敗しうる。**機序**＝`PendingJobStore.kt:107-110` の `settlePendingJob` が取込成功の直後に
  `releasePersistableUriPermission` を呼ぶ一方、同ファイル `releaseOrphanedPermissions` の keepUris ②は
  「books.sourceUri の権限は本の生存中ずっと保持する」前提で書かれている＝**両者が食い違っている**
  （実測: 取込直後の URI は `dumpsys activity permissions` で `persistable=0x3 persisted=0x0`）。
  ⚠️ **未確認**＝削除フローを実際に走らせて失敗するところまでは踏んでいない（権限側の観測だけ）。
  **確かめ方**＝エミュで SAF 取込→本削除で「取込元PDFも削除」を選び、`/sdcard/Download` の実体が消えるかを見る
  （消えなければ症状が確定。ここで初めて〈解放が早すぎる〉のか〈keepUris のコメントが古い〉のかを裁定できる）。

## 独立再実装実験の持ち帰り（2026-08-30）

> 方法論・適用条件＝`docs/knowledge/independent-reimpl-anchoring-method.md`／
> 所見の詳細＝`.claude/plans/archive/indep-reimpl-experiment-2026-08-30.md`＋`~/indep-reimpl/diff-work/diff-report.md`。
> 第二実装 `~/naro-pdf-engine/` の扱いは awaiting-human 裁定待ち＝**裁定前に消さない**。

- **[web 原文オラクルの回帰テスト導入]**（最優先の方法論的修正）: golden は旧 Python 複製＝系譜内で、下記欠陥を検出できない構造。
- **[抽出コア欠陥6クラスの真因調査→修正]** S1 半角スペース脱落／S2a U+FFFD／S3 空行復元全滅／S4a ルビ親範囲／S4b 傍点ルビ／S7 `'` 行頭移動（症状・規模＝一次情報 §4。症状でなく真因から）。
- **[縦書き組版の規範突合]** `CharClass` ⇄ UTR#50・`LineBreaker` 禁則 ⇄ JLReq（＝`docs/knowledge/vertical-typeset-external-oracle-gap.md`。差分は棚卸し→人間裁定）。
- **[計測値の訂正の波及]** `measure_read_residency.py` に欠陥2件（文字→トークン換算が 4.8 倍ずれ／分母が `cache_read` でなくその 15.6%）。knowledge 2本は訂正済み。残り＝①`CLAUDE.md`・`AGENTS.md` の「実測 4.3%」を補正値（約5.6%）へ差し替え＝**所管外・要監督** ②独立実装との突合は**完了**＝構成比は全区分 2.4pt 以内で一致・絶対値のみ3欠陥（換算係数／分母／分割イベントの usage 複製）③スクリプト自体を直すかの裁定（直すと過去実測との比較可能性が切れる＝いまは意図的に未修正）。`measure_session_length_cost.py` は再現性良好＝訂正不要。
- **[前提3件の裁定＝人間へ]** 材料は揃った（`docs/knowledge/extraction-charmap-diverges-from-web-source.md`・`naro-source-is-line-oriented.md`・`dimension-autodetect-was-preventive.md`）。裁定対象＝①字種写像の pdfminer 追従を撤去するか（golden 再採取と `CharClass` 改訂が道連れ）②行→段落結合を維持するか撤回するか ③寸法自動検出の去就。⚠️ **awaiting-human へ移す項目**＝Claude 側で動かせるのはここまで。なお現行の段落誤り率は**測れていない**（ルビのインライン記法が本文の《》と分離できず、比較可能な話が 1.8% に落ちるため）。
- 小粒: `tools/count_toc_fixture_chapters.sh` をゲート（CI か GoldenTest）へ結線する。暁66・カクヨム593 を実装非依存で数える手段は用意済みだが、**手動実行のみ＝誰も走らせない**状態。

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

- **[① 未投入の意匠のうち、まだ踏めていないもの]**（2026-09-02 時点で残るのは (a) だけ＝FAB の出没と AutoPdf 分岐は撮影済み）:
  (a) **端フェードの左端**＝実装上スクロール可（`BookshelfK.kt` のチップ行）なのに **adb 合成入力では踏めない**
  ——横ドラッグを親のタブ `HorizontalPager` が必ず奪う（機序と潰した手＝`docs/knowledge/emulator-screenrecord-and-synthetic-input-limits.md`）。
  ⚠️ **前提の問題ではなく入力手段の問題**＝実指なら踏める可能性がある（実機ツアーで見る側）。

## モック逆同期・意匠の宿題

> 棚卸しの一次情報＝`.claude/plans/mock-drift-inventory-2026-07-16.md`（正本モック全数の未反映リスト・優先順位）。
> モック運用の**恒久ルール5つ**（逆同期・`mockview` 必須・下敷き正本・プレースホルダの色域・二段検分）は `/visual-language` skill が正本。

- **[`lineHeight` 欠落の全数掃討（残り 417 箇所）]**（本棚K・discovery のランキング行/ジャンルチップ／件数行・APIフッタ・検索チップ3件は是正済み）:
  `Text(fontSize = …)` が bodyLarge の `lineHeight = 28.sp` を行箱として継承する形。**全部が不具合ではない**＝
  **器（ピル・バッジ・行）が行箱の外周をなぞる要素だけが実害**なのでチップ/タグ/メタ行から潰す。直し方と機序＝
  `docs/knowledge/compose-lineheight-is-a-floor-not-css-line-height.md`（⚠️ 下限なので自然行高より小さい比は効かない）。
  正本の比は `em` で明示（ゴシックの `line-height:normal` は実測 1.6）。**さがす配下は完了**＝ランキング行・
  ジャンルチップ・件数行・API上限フッタ・検索チップ3件・結果一覧の条件チップ（`.cd`）まで潰し切った。
  次に潰すなら本棚/目次/設定/読書の「器つき」要素から。⚠️ 比は毎回**正本の該当セレクタを実際に見て**決めること
  （`.cd`＝10.5px と `.cnt`＝11px のように、隣り合う要素でも font-size が違う）。
  ⚠️ **`NovelDetailScreen.kt` は別体が golden を調査中＝手を入れない**。

- **[検索画面の見出し行が fontScale 2.0 で折れて「すべて解除」と噛み合う]**（2026-09-02・golden の画素で確認）:
  **症状**＝選択キーワード帯の見出し「選択中のキーワード 3件」が2行（`選択中のキー` / `ワード 3件`）に折れ、
  同じ Row の右端にある「すべて解除」が縦センター配置ゆえ**その2行の谷間に挟まって見える**＝どちらの行にも
  属さない浮いた字面になる。**再現条件**＝`DiscoverySearchScreen`（さがす→検索）で選択キーワードが1件以上あり
  fontScale 2.0（golden `DiscoverySearchScreen_drafted_*_2.0` に写っている）。1.0 では折れないので出ない。
  **原因の当たり**＝見出し側 `Text` が `Modifier.weight(1f)` で可変幅・`FontMicroLabel`(10.5sp)・`maxLines` 無しのため、
  右の TextButton に幅を取られた残りで素直に2行へ折り返す。直すなら〈見出しを縮めて1行に収める〉か
  〈2.0 では Row を縦積みへ倒す〉のどちらかで、**正本モック `discovery-search-D.html` の `.sel-bar-ttl`
  （10.5px・`display` 指定なし＝ブロック）が折返しをどう規定しているかを先に読む**こと。
  ⚠️ **2026-09-02 の `lineHeight` 便が作った破綻ではない**——同便が触ったのは右の「すべて解除」（`.sel-bar-clear`）
  だけで、折れている見出しは `FontMicroLabel` 側＝**一切変更していない**。折返しは行箱でなく**幅**で決まるため、
  同便の前後で見出しの折れ方は変わっていない（変わったのはボタンの行箱の高さだけ）。

- **[ストア用 `phone-2-shelf.png` の撮り直し]**: 本棚K のチップ是正で見た目が変わったため、ストア画像が旧版のまま（エミュ撮影便で回収）。

## リファクタ / 技術的負債（deferred）

- **[perf] 本棚→目次 push の「尾」は計測系によって出方が違う**（2026-08-21 に予算とベンチ `TocPushBenchmark`／`TocPushBudget` を新設）:
  実機 gfxinfo では 12窓中11窓に 60ms 超が1枚出るのに、macrobenchmark では 60ms 超 0枚で尾が軽い（P90 は逆に macrobench 側が重い）。
  ⚠️ **極端な尾だけが再現しない＝要因未確定**。両者の全数値・食い違いの全表・潰した仮説＝
  `docs/knowledge/toc-push-tail-not-reproduced-in-macrobench.md` が正本。
  計測の作法（アニメが走っていない状態を率で比較しない）＝`docs/knowledge/ranking-pager-jank-slow-ui-thread.md`。

- **[perf] 縦書き本文の組版＝A+C+E は完了・次は D（advance キャッシュ）→ 8/9**
  （裁定と各項目の中身＝`.claude/plans/reading-render-perf-triage-2026-08-18.md`。⚠️ **D の「定数化」は不採用**＝
  書体差で版面がずれる。`(unitText, charClass, sizePx)` キーのキャッシュだけが安全）:
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
- **[端末内診断 `diagnostics/` の書き出しUI＝モック完成・A/B の目視裁定待ち]**（2026-09-02）:
  `mockview docs/design-candidates/skins/diagnostics-export-K.html`（案A シート／案B 専用画面／案A の空状態＝
  中身は3枚とも同一文）。入口の行は正本 `skins/settings-K.html`「データ」節へ直差分済み。
  **実装の申し送りはモック冒頭コメントが正本**（release 可視化の理由・シート/画面それぞれの必須条件）。
  ⚠️ 本来は `awaiting-human.md` の在庫（ADR 0028）だが、並行作業と衝突するため移設は監督が行う。
  裁定後は Compose 実装＋`CURRENT.md` の該当行から「裁定待ちドラフト」注記を落として正本化。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ上の各節に整理していないものをここへ。育ったら該当節へ移す。
> **実機で見れば決まるものは `awaiting-human.md` §1 のツアーへ移す**——ここに溜めても誰も見に来ないため。

