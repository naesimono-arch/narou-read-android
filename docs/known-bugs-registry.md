# 既知バグレジストリ（再発防止 L4）

修正済みバグのうち**同じ機序が再発しうるもの**だけを ID 化し、「いま何がその再発を機械的に止めるか」を紐付けた台帳。
目的は網羅ではなく **投資判断**——「どのバグ型に検知を足すべきか」が一目で分かること。

## 前提（再発防止の層）

- **L1** = 登録簿を回して全実装に同じ不変条件をかけるテスト（例 `DiscoveryHomeInvariantTest`）
- **L2** = ソースを走査して「罠を踏み得る形」を機械列挙し、登録簿と突合するメタテスト（例 `DiscoveryHomeInvariantCoverageTest`）
- **L3** = 知見を編集時に自動注入するフック（**未着手・採否はこの台帳の無防備件数を見て判断する**）
- **L4** = この台帳（可視化）。**ここでテストは書かない**——無防備と判明したバグ型へ検知を足すのは別ラウンド。

## 採録基準

- **採録**: 同じ機序が別の画面・別のスキン・別の実装で**再び起こりうる**もの。実際に2回以上起きたものは特に強い採録理由。
- **除外**: 意匠の微調整（配色・寸法の較正）／一過性・環境固有の単発作業（依存アップグレード、撤去済み経路の修理）／ドキュメントの参照切れ修正／裁定変更に伴う仕様変更（バグではない）。
- 全数採録はしない。台帳が肥大すると「一目で投資判断」という唯一の効用が消える。

## 状態（検知の実効性）— この列が台帳の主眼

| 記号 | 意味 |
|---|---|
| `[!] なし` | **無防備**。再発しても何も落ちない。 |
| `[!] 知見のみ` | **無防備**。`docs/knowledge` や ADR に書いてあるだけ＝**機械は止めない**。 |
| `[~] 部分` | 個別回帰テストのみ。既存実装の退行は止まるが、**新しく書かれた実装が同じ罠を踏むのは止まらない**。 |
| `[o] 固定` | 不変条件テスト（L1/L2）・機械チェック・lint のいずれかが再発時に落ちる。 |

**`知見のみ` を防御に数えないのはこの層が生まれた実例そのもの**——K のランキングを Pager 化したとき、knowledge も
個別回帰テストも在ったのに同じ機序が再発した。知見は読まれて初めて効き、機械は読まれなくても止める。
「知見がある＝防御済み」と読める台帳は投資判断を確実に誤らせるので、`なし` と同じ無防備側に置く。

`部分` は無防備には数えないが、**L1 格上げ（登録簿＋走査）の第一候補**として区別できるようにしてある。

## 検知手段の語彙

`不変条件テスト名` / `個別回帰テスト名` / `lint ルール名（lint:Xxx）` / `check_machine.py のチェック名（check_xxx）` /
`CI ゲート（CI: Gradleタスク名）` / `知見のみ` / `なし`。テストクラス名・機械チェック名・参照パスは `` ` `` で囲って書く——
`check_machine.py` の `known-bugs-registry` チェックが**実在を機械照合**し、リネーム・削除で嘘になった行を落とす。

**CI ゲートだけは `` ` `` で囲まず素のテキストで `CI: <Gradleタスク名>` と書く**——Gradle のタスク名は
テストクラス名・`check_xxx`・パスのどの照合パターンにも当たらず、コードスパンにすると info が出るだけだから
（下の構造封鎖と同じ扱い）。素のテキストでも実在照合は効く: `check_machine.py` が `CI:` に続くタスク名を
`.github/workflows/ci.yml` の本文と突合し、**ステップを外した瞬間にその行を落とす**
（ワークフローは1行消せば消えるのに台帳は緑のまま「CI が守っている」と嘘をつく＝13日間 dead だったのと同じ形を塞ぐ）。

`lint:` 接頭辞の値だけは実在照合ができないため未検証として報告される。なお Android Lint は
**コミット時フックとしては撤去済み**（＝ローカルの日常ゲートには無い）が、CI の `lintDebug` ステップで
毎 push 走るため、lint 行は release の `lintVital` 経路と CI の2経路で効く。

**構造封鎖（必須引数・exhaustive when 等）は `[o] 固定` と同格に扱う**——コンパイラが止めるので、
テストより強い（実行すら要らず、見落としが原理的に起きない）。これを `[~] 部分` に埋めると
「まだテストを足すべき」と読めて投資判断を誤る。ただし実在照合ができないので **コードスパンで囲まず
素のテキストで書く**（`` ` `` で囲むと未知トークンとして info が出る）。封鎖が配線の一部にしか
及ばないなら状態は `[~] 部分` に留め、検知手段欄へ「構造封鎖（必須引数）」と併記する
（例＝`skin-wiring-omission`。スキン追加時のシート色・クローム欠落は封鎖の外に残っている）。

## 棚卸しの回し方

1. `/stale-check` の機械チェックが、この台帳の名指しが実在するかを毎回照合する（不在は「確度高」）。
2. 新しく `fix:` を書いたら、**その機序が再発しうるか**を判定し、該当すれば1行足す。既存 ID の再発なら
   「修正の所在」へ追記し、状態を見直す（再発した＝その検知手段は効かなかった、の証拠）。
3. 検知を足したら状態を `[!]` → `[~]` → `[o]` へ更新する。**逆に、テストを消したら状態を戻す**。
4. 完了の履歴は git log が正本。ここには SHA・コミット数・差分行数を書かない。

---

## A. アプリ本体（Kotlin / Compose / Room / scrape）

無防備（`[!]`）→ 部分（`[~]`）→ 固定（`[o]`）の順に並べてある。

| 状態 | ID | 症状 | 機序・バグ型 | 修正の所在（コミット件名の要約） | 検知手段 | 関連 knowledge |
|---|---|---|---|---|---|---|
| `[!] なし` | `lazy-items-missing-key-contenttype` | 一覧を並べ替え/絞り込むと状態が別の行に貼り付く・再利用が効かない | Lazy の `items` に安定 key / contentType が無く、位置がキーになる | 発見Kの大ジャンル列へ安定 key 付与／本棚リスト・グリッド9箇所へ contentType 全数付与 | なし | — |
| `[!] なし` | `row-weight-missing-pushes-out-trailing` | 長文で行末のスイッチ・×ボタンが押し出されタップ不能／文字が被る | Row の測定順で先行要素が幅を食い、`weight` 未付与の末尾に幅が残らない | ⋮メニュー通知節の説明 Column へ weight 付与（全スキン共有部品）／検索履歴チップの×幅を先確保 | なし | — |
| `[!] なし` | `a11y-touch-target-below-48dp` | 小さなアイコンがタップしづらい | 見た目の寸法のまま clickable を付け、最小標的 48dp を確保しない | NcodeLinkSheet／結果一覧／作品詳細のタップ標的を 48dp へ拡大 | なし | — |
| `[!] なし` | `fontscale-large-breaks-layout` | フォントスケール拡大時に要素が密着・はみ出す | 固定 dp の間隔が拡大した文字幅を吸収できない | 発見バーのテキストとシェブロンの密着解消 | なし | — |
| `[!] なし` | `stale-generation-coroutine-finally` | 新しいバッチの通知が旧処理の後始末で消える・サービスが道連れ停止する | 旧世代コルーチンの `finally` が世代を見ずに共有状態を書き戻す | onTimeout 後の道連れ停止を世代ガードで防止／processSingleUri 側 finally へ世代ガード横展開 | なし | `docs/patterns/service-queue-loop.md` |
| `[!] なし` | `cancelled-scope-reuse-silent-stop` | 一度タイムアウトすると以後の取込が無言で動かない | キャンセル済み `CoroutineScope` を再利用（launch が即死し例外も出ない） | FGS タイムアウト後にコルーチンスコープを再生成 | なし | — |
| `[!] なし` | `shared-mutable-state-without-mutex` | 記帳・キャッシュが並行アクセスで壊れる | `limitedParallelism(1)` は排他にならない（直列化≠相互排除）という誤解 | pending_jobs 記帳を Mutex 排他へ／NovelApiRepository キャッシュのスレッド安全化 | なし | — |
| `[!] なし` | `remembersaveable-missing-state-loss` | 回転・プロセス復帰でシートの開閉やヒント表示が戻る | `remember` のまま保存しない | 読書画面シート開閉の rememberSaveable 化・没入ヒントを通算初回のみへ | なし | — |
| `[!] なし` | `datastore-corruption-crash` | 永続層の破損・IOException でアプリが落ちる | DataStore の読み出しに復旧経路が無い | 検索履歴 DataStore の IOException/破損時クラッシュを防止 | なし | — |
| `[!] なし` | `queue-drop-on-concurrent-import` | 処理中に別の PDF を追加すると無言で捨てられる | 単一処理前提のサービスが同時投入を落とす | PDF 処理のキュー化 | なし | `docs/patterns/service-queue-loop.md` |
| `[!] なし` | `transition-window-first-compose-jank` | 画面遷移・タブ切替が 400ms 級で引っかかる | アニメ窓に初回コンポーズが同居する（隣ページ破棄→再コンポーズ等） | 隣ページ常駐化／遷移中は常駐0＋settle 後に移送／遷移中の構造プレースホルダ | なし（macrobenchmark は回帰固定へ未接続） | `docs/knowledge/emui-p30-jank-log-collection.md` |
| `[!] 知見のみ` | `fixed-bar-clearance-hardcoded-guess` | 固定バー・FAB の下に本文やラベルが潜って読めない | クリアランスを定数で当て推量（120dp・64/80dp・FAB 分）し、実高とずれる | M星図本棚のバー実高を実測クリアランス化／本棚下端の FAB 余白確保／縦書きのバー余白 64/80dp 誤翻訳を除去 | 知見のみ | `docs/knowledge/compose-lazy-mirror-contentpadding-axis-shift.md` |
| `[!] 知見のみ` | `oem-background-kill` | バックグラウンドの取込が OEM に殺される | ColorOS/EMUI の電池最適化が FGS でも凍結する | WakeLock を PDF 単位で再取得／FGS の onTimeout・startForeground 例外防御／電池最適化除外の誘導 | 知見のみ | `docs/knowledge/coloros-hans-freezes-fgs-despite-bg-allow.md` |
| `[!] 知見のみ` | `theme-invariant-surface-loses-contrast` | 片方のテーマだけ文字が白飛び／黒潰れする | テーマ追従色を「テーマ不変の面」へ載せる（面と content color の所属ずれ） | P設定シートの文字色をテーマ不変の墨に固定 | 知見のみ | `docs/knowledge/compose-root-surface-content-color.md` |
| `[!] 知見のみ` | `webview-position-mis-record` | Web 読書の位置が回転・戻り遷移で巻き戻る | WebView のスクロール位置を「戻り」でも記録してしまう／回転で state を捨てる | WebReader の回転巻き戻りを saveState で解消／戻り遷移では読書位置を記録しない | 知見のみ | `docs/decisions/0012-narou-reading-webview-position-tracking.md` |
| `[!] 知見のみ` | `snackbar-indefinite-blocks-queue` | スナックバーが閉じた直後に再表示され残留して見える | actionLabel 付き Indefinite が M3 の直列キューを塞ぐ | Web 取込中表示を ProcessingBanner へ収斂 | 知見のみ | `docs/knowledge/material3-snackbar-actionlabel-indefinite-blocks-serial-queue.md` |
| `[!] 知見のみ` | `benchmark-device-run-fragility` | マクロベンチが実機で完走せず全メトリクス0になる | broadcast の沈黙不達・新コンテンツの入力デッドウィンドウ・画面ロック・COLD 仕様衝突 | 本棚スクロール／章送り／大PDF取込の3ベンチを実機完走可能化 | 知見のみ | `docs/knowledge/coloros-broadcast-silent-drop.md`, `docs/knowledge/macrobenchmark-frametiming-scroll-pitfalls.md`, `docs/knowledge/compose-fresh-content-input-dead-window.md`, `docs/knowledge/device-screen-lock-breaks-benchmark-two-ways.md` |
| `[~] 部分` | `a11y-contrast-below-aa` | 文字・アイコンが薄くて読めない（4.5:1 未満） | 意匠の淡色をそのまま情報テキストへ使う | ルビ3色の AA 化／発見系メタ6箇所を情報テキスト役割トークンへ／履歴チップの未ピンアイコン可視化 | `tools/check_design_tokens.py`（a11y contrast check＝トークン層で「載る面が型で宣言された」前景×面 297組を WCAG 4.5:1／非テキスト 3:1 で判定・CI の Design token check で毎push）。**呼び出し側の `.copy(alpha=)` 合成・グラデーション面・Color.kt 直参照は未検査** | `docs/decisions/0014-design-principles-and-source-layers.md` |
| `[~] 部分` | `translucent-alpha-calibrated-for-d-leaks` | 暗色スキンで操作可能な面の背後が透けて読めない | D（明色）で較正した半透明 alpha を他スキンへ持ち越す | 「最上部へ」ピルの不透明化／P没入セーブバーのスライド退避／読書下部バーの nav 帯透け（alpha0.95 持ち越し） | `ReadingBarAlphaTest`（読書バーのみ） | — |
| `[~] 部分` | `progress-ui-diverges-from-work` | 進捗が「2周目」に見える・分母が動かない・1%に張り付く | 進捗の段と分母を表示側が独自に組み立てる／高頻度更新 × tween で毎回キャンセル | 抽出進捗の統合／分母のライブ反映／進捗バーの spring 追従化 | `PdfBookExtractorTest`（抽出側のみ） | `docs/patterns/processing-state.md` |
| `[~] 部分` | `vertical-glyph-bypasses-classifier` | 縦書きで約物・ルビ・題字が正立のまま死ぬ／列中心からずれる | 新しい描画経路が CharClassifier＋VertGlyphRenderer を迂回して素で描く（**3経路で再発**） | 本棚題字の1字描画を分類器経由へ／ルビも分類器経由へ／回転グリフをインク中央合わせへ／実機計測を分類表へ反映 | `CharClassifierTest`, `VerticalParagraphScreenshotTest`（既存経路のみ） | `docs/knowledge/vert-feature-pgem10-coverage.md`, `docs/knowledge/robolectric-vert-feature-noop.md` |
| `[~] 部分` | `route-literal-pop-silently-ignored` | ← が完全無反応・画面から出られない | 消えたルート名リテラルへ pop して黙殺される（**読書側で起きた後、発見側で再発**） | 目次→本棚の pop をタブ層へ単一化／発見側の pop 3箇所を popToTab へ一般化 | `ReadingEscapeNavigationTest`, `DiscoveryUpNavigationTest` | `docs/decisions/0022-skin-structural-layer.md` |
| `[~] 部分` | `back-up-hierarchy-drift` | 「戻る」の着地が到達経路によってばらつく | Back を経路の逆再生で実装すると画面ごとに解釈が割れる | 階層 up への再定義／作品詳細←の固定 Up／Web 読書←の階層 Up／singleTop で二重 push 防止 | `ReadingBackStackTest` | `docs/decisions/0026-discovery-back-hierarchy-up-unification.md` |
| `[~] 部分` | `reading-position-overwritten` | 読書位置が巻き戻る・参照ジャンプで不可逆に上書きされる | 位置の書き手が複数あり、破棄タイミングで最終値が書かれない | 参照ジャンプの上書き解消／進捗保存の単一チャネル化／章破棄時のフラッシュ | `ChapterScrollPersistenceTest` | — |
| `[~] 部分` | `ncode-identity-normalization-divergence` | 同一作品が2枚並ぶ・表示だけ食い違う・読書位置を誤削除する | ncode の正規化流儀が経路ごとに分裂する（**「第4流儀」まで発生**） | sameWorkAs を storageKey 突合へ統一／BookshelfSkyM の表示を urlSlug へ統一／孤児進捗 prune の無正規化突合を是正 | `BookIdentifiersTest`, `NcodeTest`, `BookRepositoryTest` | — |
| `[~] 部分` | `orphan-rows-and-permissions` | 削除した本の進捗行・URI 権限が残り続ける | 複数テーブル/複数資源の削除が原子的でない | 削除のトランザクション化／失敗取込の孤児 URI 権限を起動時に回収 | `BookRepositoryTest`, `ActiveUriTrackerTest`, `StartupRecoveryTest` | — |
| `[~] 部分` | `bookshelf-count-double-derivation` | ヘッダの冊数と実カード枚数がずれる | 同じ集合を供給点と表示点で二重に導出する | 取込済み web_novels のゴースト行を供給点で除外 | `ShelfItemsTest`, `BookshelfViewModelTest` | — |
| `[~] 部分` | `processing-state-hub-overwrite` | PDF と Web の取込表示が相互に上書きされる・停止が効かない | 単一 Hub を複数の生産者が無条件に書く | Hub の PDF/Web 分離／停止の Web 実効化／重複スナックバーの集約 | `ProcessingStateHubTest`, `ProcessingBannerTest`, `AggregateErrorEventsTest` | `docs/patterns/processing-state.md` |
| `[~] 部分` | `skin-wiring-omission` | 新スキンだけ機能が欠ける（読書状態フィルタ・件数集計） | スキンごとに同じ配線を手で複製する | webReadingStatusFor 新設＋全5スキン配線＋引数必須化 | `ShelfItemsTest` ＋構造封鎖（必須引数。ただしシート色・クローム欠落は封鎖の外） | `docs/decisions/0021-ui-skin-framework.md` |
| `[~] 部分` | `state-as-frame-lag` | タブ切替が稀に効かない | `collectAsState` 化した nav 状態は1フレーム遅れる | Kタブ切替の遷移判定をライブ currentDestination へ | `KTabNavigationTest` | `docs/knowledge/navigation-compose-currentbackstackentry-state-lag.md` |
| `[~] 部分` | `mock-code-drift` | 実装とモック正本が乖離して「どちらが正か」が分からなくなる | 人力写経の同期＝片側だけ変えても誰も気づかない | 正本モックへコード先行の視覚変更を逆同期 | `tools/check_design_tokens.py`（色・余白・書体トークンのみ／手動実行） | `docs/decisions/0018-derived-mock-drift-optin-sync-check.md` |
| `[~] 部分` | `content-height-jump-on-wrap` | 内容が2行になった瞬間に下部がガクンと動く | 内容依存の高さを予約せずレイアウトへ流す | さがすKの「今日の気分」を不可視ゴーストで高さ予約／M本棚セルを内容追従高へ | `DiscoveryHomeKMoodTest` | — |
| `[~] 部分` | `external-api-contract-drift` | なろう API の応答で件数が欠ける・パースが落ちる | 外部 API の上限（lim）とスキーマ差異を前提に書いていない | novelDetailsBulk の500件チャンク分割／novel_type 二重キー・nu 欠落・JSON 例外正規化・NotFound 負キャッシュ | `NovelApiRepositoryTest`, `NarouJsonParseTest` | `docs/reference/02-narou-api-digest.md` |
| `[~] 部分` | `async-response-inversion` | 古い検索結果が新しい結果を上書きする | 先行リクエストを cancel せず、遅い応答が後着する | 発見ロードの応答逆転を Job キャンセルで防止 | `DiscoveryViewModelTest` | — |
| `[~] 部分` | `missing-data-rendered-as-fabricated` | 欠損データが「全0話」「連載中 1話」と嘘表示される | 欠損を既定値 0/1 に落として描く | 話数欠損時に捏造表示しない | `DiscoveryCommonLabelsTest` | — |
| `[~] 部分` | `input-normalization-gaps` | 負数・桁あふれ・全角入力がサイレントに無効化される | 入力の正規化と境界検査が描画側に無い | カスタム数値入力の境界を堅牢化 | `SearchDraftTest` | — |
| `[~] 部分` | `html-escape-missing` | 抽出テキスト中の記号で本文 HTML が崩壊する | 生成時にエスケープしない出力経路が残る | HTML 生成時に抽出テキストをエスケープ | `HtmlEscapeTest`, `HtmlExporterChapterCountInvariantTest` | — |
| `[~] 部分` | `pdf-rule-detection-edge-case` | 単話 PDF で章タイトルが空になる | 題名マーカー0件を「章なし」と解釈する | 単話の単一章タイトルへ作品タイトルを流用（golden 第4本を追加） | `JvmGoldenRegressionTest`, `SplitIntoChaptersTest` | `docs/knowledge/narou-pdf-fullwidth-normalization.md` |
| `[~] 部分` | `transparent-overlay-eats-taps` | ボタンが見えているのに押せない | 透明な上位コンテナがヒットテストを食う | 空の本棚で Lazy コンテナとボタンを排他分岐へ | `BookshelfContentTest` | — |
| `[~] 部分` | `pager-snap-from-first-visible` | 高速フリングでカードが複数枚飛ぶ | Pager 既定 snap の丸め基準が firstVisiblePage（覗き構図では視覚中央−1） | 装いの間カルーセルへカスタム PagerSnapDistance | `WardrobeFlingTargetTest` | `docs/knowledge/pager-snap-rounds-from-first-visible-page.md` |
| `[~] 部分` | `a11y-offscreen-nodes-unreachable` | 没入中に TalkBack から戻る・目次・前後章へ到達できない | 画面外ノードが a11y ツリーから刈られる | 没入中も customActions で到達可能に（視覚不変） | `NativeReadingScreenA11yTest` | `docs/knowledge/compose-offscreen-nodes-pruned-from-a11y-tree.md` |
| `[o] 固定` | `lazylist-loading-full-replace-scroll-reset` | 再取得中に一覧が消え、スクロール位置が先頭へ飛ぶ | Loading 中に一覧を status 1行へ潰し、同一 LazyListState が縮んだ item 数で measure される（**knowledge も回帰テストも在ったのに新実装で再発**＝L1/L2 の発端） | 期間タブ切替リセットの全スキン横展開／K ランキング初訪ページのスケルトン化 | `DiscoveryHomeInvariantTest`（L1）, `DiscoveryHomeInvariantCoverageTest`（L2）, `DiscoveryHomeKSkeletonTest` | `docs/knowledge/lazylist-loading-full-replace-scroll-reset.md` |
| `[o] 固定` | `immersive-window-ownership` | 章を送るたび没入が壊れる・消灯抑止が外れる・バーが一瞬出る | ウィンドウ資源を章スコープが `DisposableEffect` で所有し、退場側の後始末が入場側に勝つ | 読書メニューのタップトグル固定化＋window 幾何固定／window 背景をテーマ背景へ／ステータスバー明暗を Theme へ一本化／所有権を画面スコープへ移動 | `ReadingWindowContractTest`（所有権をソース走査で固定） | `docs/knowledge/compose-animatedcontent-exit-dispose-outlives-enter.md`, `docs/knowledge/immersive-toggle-cutout-letterbox-flicker.md`, `docs/knowledge/m3-topappbar-heightoffset-negative-crash.md` |
| `[o] 固定` | `db-version-collision-parallel-branches` | 並列ブランチが同じ Room version を先取りし、実機の schema hash と衝突する | 連番の採番が分散する | Room version 9／10 への退避（no-op migration で再スタンプ） | `check_db`, `.claude/hooks/check_sequence_id_collision.py` | `docs/decisions/0025-version-numbering-scheme.md` |
| `[o] 固定` | `hardcoded-color-outside-tokens` | 同じ役割の色が2値に割れる・直書きが残る | トークン層を経由せず値を直書きする | ヘアライン役割2トークン化・直書き3件解消／章見出しルール色の正本化 | `tools/check_design_tokens.py` | `docs/decisions/0014-design-principles-and-source-layers.md` |
| `[o] 固定` | `intent-filter-data-attrs` | VIEW intent-filter のマッチ集合が意図とずれる | 1つの data タグへ複数属性を書くと直積で解釈される | data タグを1属性1タグへ分割 | `lint:IntentFilterUniqueDataAttributes`（CI の lintDebug で実行） | — |
| `[o] 固定` | `androidtest-not-compiled-by-default-gate` | androidTest が本番シグネチャ変更に追従せずコンパイル破綻したまま潜伏 | 既定ゲート `testDebugUnitTest` は androidTest をコンパイルしない | ReadingScreen のテーマ引数追加への追従／followingSystem 必須化への追従（**2回発生**） | CI: assembleDebugAndroidTest（ビルドのみ・実行は端末必須で対象外） | — |
| `[o] 固定` | `roborazzi-verify-not-in-default-gate` | golden が実装と乖離したまま何週間も潜伏する | 既定ゲートに `verify` が同乗していない＝記録だけして照合しない | 陳腐化していた golden 24枚を再記録 | CI: verifyRoborazziDebug（単体テストと同じ1パスで48枚を照合） | `docs/knowledge/golden-regression-baselines.md` |
| `[o] 固定` | `release-r8-only-build-break` | debug は通るのに release だけビルド不能になる | R8 が新依存の未知属性で落ちる。日常ゲートに release ビルドが無い | In-App Review 導入で停止した release R8 ビルドを dontwarn で復旧 | CI: assembleRelease（鍵不在でも未署名で通る＝R8 破綻のみを見る） | — |
| `[o] 固定` | `runcatching-swallows-cancellation` | キャンセルしたはずの処理が生き続ける／構造化並行性が壊れる | `runCatching` が `CancellationException` まで飲む | addBook のキャンセル例外の握り潰しを解消 | `HazardousPatternScanTest`（キャンセル文脈の runCatching を全数列挙し登録簿と突合。**機械が保証するのは「再送出が無い」ことまで**＝登録簿7件の免除根拠「本文が非 suspend」は人間の読み） | — |
| `[o] 固定` | `test-dispatcher-escape-flaky` | 単体テストがフレーキーに落ちる | 本番コードの `launch(Dispatchers.IO)` が TestDispatcher 管理外＝`advanceUntilIdle` が待てない | BookshelfViewModelTest をディスパッチャ注入で決定化 | `HazardousPatternScanTest`（launch/flowOn/shareIn/stateIn の Dispatchers 直書きを全数列挙。withContext は呼び出し元が待つので対象外） | — |
| `[o] 固定` | `fgs-notification-id-collision` | 完了・失敗の通知が出た瞬間に消える | 終端通知を FGS 通知と同一 ID へ投稿＝サービス停止の道連れ | PDF 終端通知を FGS 通知と別 ID へ分離 | `HazardousPatternScanTest`（notify/startForeground の投稿口を全数登録制にし、宣言 ID と実コード・TERMINAL 役と FGS の ID 一致・ID 定数の相互差分まで検査） | — |
| `[o] 固定` | `no-network-timeout` | 通信が返らないまま画面が固まる | OkHttp 既定の `callTimeout` は無制限 | OkHttp クライアントへタイムアウト設定 | `HazardousPatternScanTest`（OkHttpClient 生成式に callTimeout があるか） | — |

## B. tooling（`.claude/hooks` / skills / `tools/` / statusline）

アプリと同じく「検知手段の空白」が投資判断の対象。**tooling のバグはサイレント失敗クラスが多く、
無症状のまま何日も走り続ける**（撤去済みフックの残骸が13日間 dead だった実例あり）。

| 状態 | ID | 症状 | 機序・バグ型 | 修正の所在（コミット件名の要約） | 検知手段 | 関連 knowledge |
|---|---|---|---|---|---|---|
| `[~] 部分` | `stale-check-false-positive` | 機械チェックが偽陽性を出し、報告が信用されなくなる | 検査対象の除外条件を実態に追随させていない | hooks_common.py の死hook 誤検知を除外／test_*.py・MEMORY.md の抑制／block_destructive_migration の対象を .kt 限定／ref 検査の対象を docs 全体と plans 直下へ拡大し「もう無い」注記4形式の抑止則を新設（同時に、探索ルートが macrobenchmark を見ておらず実在ファイルを参照切れ扱いしていた穴も是正） | `check_machine.py` 自身の自己テスト（抑止則の境界ケース・名指し判定・実行時生成ファイル・メタ変数記法をインライン期待値で毎回検証。故障注入で「抑止しすぎ」「抑止漏れ」の両方向を検出できることを実証済み）。**抑止則に限る**＝他の検査項目の偽陽性には依然として検知手段が無い | — |
| `[~] 部分` | `checker-fail-open-skip` | 検査器が「SKIP」を黙って飲んで全通過する | 対象が見つからない＝合格、という fail-open 設計 | check_design_tokens の SKIP 内訳を列挙・ベースライン超過で exit 1／さらに総数ラチェットを鍵付き理由表へ移行（総数だけの監視は「照合が1件死んでも別の1件が復活すれば差し引き0」で素通りしていた） | `EXPECTED_SKIPS` の鍵照合＝未知の SKIP は NG・表にあるのに SKIP しなくなれば INFO・対象から消えれば NG（負のコントロール4本で実効を実証）。起動点は CI の Design token check ステップのみ＝**ローカルでは自動起動されない**のは従前どおり | `docs/decisions/0018-derived-mock-drift-optin-sync-check.md` |
| `[!] なし` | `wsl-path-translation` | WSL からの起動が空振りする／UNC を誤って開く | Linux パスを Windows 実行ファイルへそのまま渡す | open_in_vscode の wslpath 対応／ドライブパス限定化／JSON デコードエラー（**3回発生・当該フックは現在撤去済み**） | なし | — |
| `[!] なし` | `statusline-terminal-geometry` | statusline の2行目が見切れる・worktree 名が出ない | 端末の実表示域や git-dir を推定値でハードコード較正する。**2回発生** | 余白を実測 24 桁へ較正（2回）／worktree 名を --git-dir 基準へ／「(1M) 二重表示」解消 | なし | — |
| `[~] 部分` | `hook-output-not-delivered` | フックは動いているのに通告がモデルに一切届かない（主機能が無音で不達） | イベントごとに届く出力先が違う（stdout / stderr / additionalContext）のを取り違える。**7回発生** | schema・lint フックの additionalContext 化／センチネル状態遷移通知／コミット粒度チェック／remind_commit_plan の stderr 化／remind_task_diary の注入化／mark_kotlin_tests_passed の不達／センチネルが Bash 出力形式を読めず未更新／センチネル削除失敗通知の stderr 不達 | `check_hook_output_channel`（配線イベント×届く出力経路の突合）。**静的解析ゆえ判定不能が残り、SubagentStop 行は一次情報未確認の推測** | `docs/decisions/0008-no-hook-dispatcher.md` |
| `[~] 部分` | `delegation-deletes-out-of-scope` | 委譲した結果、指示範囲外の機能が黙って消える | 生成委譲の diff を追加行だけ見て承認する | 条件シートの「今月」「先月」チップを復元（委譲バッチのスコープ外削除の退行） | `.claude/hooks/warn_delegated_deletions.py`（PreToolUse・子限定で消える行を本人へ通告。閾値〈純削除≥2行 ∨ 削除≥15行〉は子 transcript 1955本の実測分布から発火率 5.94%＝1体あたり平均2.9回に置いた）。**ブロックせず通告のみ・閾値未満と Bash 経由の削除は素通り**＝監督の削除行込み diff レビューが引き続き要る | — |
| `[~] 部分` | `skill-frontmatter-nonstandard-field` | SKILL.md に書いたフィールドが実は何にも効いていない | 非公式フィールドを効くものと思い込む（`triggers:`） | 標準外 triggers: を公式 when_to_use へ移行（全8スキル） | `check_skill_frontmatter`（name/description のみ＝未知フィールドは見ない） | — |
| `[o] 固定` | `removed-hook-leaves-dead-consumer` | 撤去したフックの生成物に依存する判定が恒久 dead 化し、**テストは緑のまま13日間死ぬ** | 撤去コミットが「撤去する側」しか触らず、参照する側が残る | 恒久 dead だったセンチネル照合を退役／捏造検知のセンチネル参照を Kotlin 単一化 | `check_removed_hook_references`（撤去フックの名前に加え**旧ソースが作っていた生成物リテラル**を現ツリーの live 位置〔settings 配線・実コード・.gitignore 実エントリ・文書中の実行コマンド〕と突合）。**パスを変数で組み立てる参照とフック外の生成者撤去は対象外** | `docs/decisions/0006-detect-fabricated-execution-static-analysis.md` |
| `[o] 固定` | `hook-guard-regex-bypass` | ブランチガード・コミットゲートが表記揺れで素通りする | コマンド文字列を正規表現で見張る設計は、改行・グローバルオプション・複合 `switch && commit` で破れる。**複数回発生** | 偽陰性の是正（改行・グローバルオプション）／複合 switch+commit の穴を封鎖／COMMIT_CMD_RE 統一と merge 素通し封鎖 | `.claude/hooks/test_hooks.py`（`check_hook_smoke` が毎回実行） | `docs/decisions/0004-branch-aware-memory-and-doc-architecture.md` |
| `[o] 固定` | `fabrication-detector-calibration` | 実行捏造検知器が正当な作業を誤ブロックする／捏造を素通しする | 検知の閾値・免罪条件・検査窓を実データ無しで決めると必ず片側へ倒れる | v3.1／v3.2 較正・Tier B メタ議論免罪・Stop 検査窓の current_turn 拡張・画像貼付クラッシュ修正 | `.claude/hooks/test_detect_fabricated_execution.py`, `docs/reference/hallucination-ground-truth.md` | `docs/decisions/0006-detect-fabricated-execution-static-analysis.md` |
