# STATUS — `api-lab` ブランチ現況台帳

> **このブランチ専用の現況正本**（`STATUS.md` は main 正本のため、api-lab の作業状態はここに記録する）。
> ブランチをまたぐ不変知見は auto-memory、main 全体の現況は `STATUS.md`、腐りにくい外部事実は `task_diary.md`。
> 一次情報（設計判断の全文・未決事項）は plan `~/.claude/plans/apimd-crystalline-cascade.md`。
> マージ時にここを `STATUS.md`／`handover.md §D` へ集約する。

## 0. 現在地（★次はここから）

- **Phase 0〜3 完遂（2026-07-07・branch=`api-lab-ai`・worktree `/home/qingj/wt/api-lab-ai`）**: 発見体験の意匠モック6画面＋発見コアC1〜C4＋発見強化D1〜D5＋**目玉①PDF↔Web継続読書**を実装。**テスト162件 GREEN・assembleDebug 25.6MiB・実機(PGEM10)スモーク全経路OK**。Phase 3 の実機エビデンス: シャングリラ・フロンティア（PDF 951章）で最終章末尾→「なろうで続きを探す」→紐付けシート（書名自動検索・候補=硬梨菜/連載中全955話）→タップ紐付け→継続カード「手元のPDFは第951話まで。なろうには第952〜955話（新着4話）」→藍ボタン→**Chrome で ncode.syosetu.com の 952/955 話に正確着地**。解除→プロンプト復帰→再紐付け・DB永続化（books.ncode='N6169DZ'・WAL込み確認）まで検証済み。コミット表は §1。
- **⚠️ Room version は 9 へ退避（v8 は使えない）**: 並列ブランチ `feat/processing-resilience` が v8（`pending_jobs`）を先取り消費し実機も v8 化済みのため、当初の v8 実装は identity hash 衝突で起動即クラッシュした（実測）。本ブランチは v9（`MIGRATION_8_9`=books.ncode）へ退避し、resilience の 7→8 を同一内容で複製してパスを繋いだ。**マージ時は「version 9＋両 migration＋両エンティティ」で合併すること**。機序と予防は `task_diary.md` #39・`/db-migration` スキル。
- **設計方針（2026-07-07・ユーザー表明）**: **Web 閲覧系の導線（継続読書・なろうで読む）は、行く行くはアプリ内 WebView で完結させる**（「なるべくこのアプリで完結したい」＝目玉④の"アプリ内で完結する発見〜読書"の徹底）。今すぐの作業指示ではなく**考え方**として固定: 現状 Phase 3 の外部ブラウザ送客（C4 と同方式）は暫定であり、B2「Webで読む」導線を実装する際はアプリ内 WebView 内包を既定の解とし、継続導線・C4 もそこへ統一する。
- **Phase 4 スライス1 完了（2026-07-07・`92da396`）**: 融合本棚のうち **(c) 見つける導線帯**（本棚先頭・グリッド/リスト両対応）と **(a) 続きありバッジ**（紐付け済みの本に「● 続き N話」＝青磁ドット＋藍文字。カード単位 produceState・6h TTL キャッシュ相乗り・失敗時は静かに非表示）を実装。**実機でグリッド/リスト両方の表示確認済み**（シャングリラに「続き 4話」）。テスト162件緑・assembleDebug 成功。
- **★次アクション**: **Phase 4 残り＝(b) Web由来・未取込カード（新データ概念＝Web作品を本棚に置く・DB変更を伴う）／U1 新着話チェック＋通知／U2 整理**。⚠️ DB 変更時は次版=v10 だが、**着手前に必ず全 worktree の version 先取りを確認**（task_diary #39・`/db-migration` 更新済み）。B2 実装時は上記方針＝アプリ内 WebView を適用。
- **旧記録（縦スライス第1本・5コミット済み 2026-07-06）**: `e19fe50`(依存＋権限)→`6783d9a`(メタ取得サブシステム＋テスト)→`ac2d575`(画面＋導線)→`3a4ec46`(調査資料docs)→`8b267c8`(ディスカバリVMテスト)。
- **やっていること**: なろう公式APIの**発見機能を「第2の柱」に育てる**。案A（本文は取らずメタのみ）を堅持しつつ、発見機能を「公式より丁寧・アプリ内で完結するレベル」に作り込む。目玉＝**PDF↔Web継続読書**と**静かな没入意匠**。競合5アプリ解析（`docs/reference/04-competitor-app-features.md`）が裏付け＝競合はどれも発見が弱い。**計画を策定・ユーザー承認済み（2026-07-06）**＝§3 の目標ロードマップが現在地・目標の正本、実装詳細の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。
- **既存本棚UIの刷新（融合本棚・目玉②）**: Phase 0 でモック `bookshelf-fusion-D.html`（見つける導線帯・続きありバッジ・Web由来カード並置）を作成済み。**Compose 実装は Phase 4**（Phase 3 の ncode 紐付けが前提のため）。見た目の正本は ADR0005＋HTMLモック（直書き禁止・`theme/Color.kt`／`MinchoFamily` 経由）。

## 1a. 完了（Phase 0〜3＝発見体験の意匠＋発見コア＋発見強化＋PDF↔Web継続読書・2026-07-07）

実装・単体テスト162件・assembleDebug・実機スモークの全レベルで GREEN。コミット表（新しい順）:

| Phase | commit | 内容 |
|---|---|---|
| 3 | `9431755` | fix: Room version 9 へ退避（並列ブランチが v8 先取り・identity hash 衝突の実測対処） |
| 3 | `8620cd3` | 継続カード＋紐付けシート＋読書画面配線（reading-continuation-Dモック翻訳・androidTest追従込み） |
| 3 | `649be4c` | 話数突き合わせ純関数 `ContinuationLogic`＋テスト12件 |
| 3 | `709b920` | 蔵書に ncode 列（BookDao 部分UPDATE・Repository/VM 経路） |
| D3 | `df91e4d` | 気分プリセット「きょうの気分」4種をホーム最上段に（範囲絞り込みの体験昇華・BINGEのみ累計順） |
| D1/D2/D4/D5 | `b2f7bfa` | 条件シート（type/期間/属性/文字数/読了時間/会話率/挿絵＝段階チップ）＋検索履歴・ピン留め（DataStore別系統・純関数ロジック） |
| C4 | `b89de20` | 作品詳細（BookCover流用ヒーロー・ステータス表・評価表）＋「なろうで読む」外部ブラウザ送客（B2先取り） |
| C2 | `114abc6` | フリーワード検索＋範囲チップ（BasicTextFieldの静かな入力欄） |
| C3 | `6690f64` | ジャンル画面＋結果一覧の共通着地（文脈見出し・条件チップ自動派生・総件数青磁）＋ホームのジャンル入口 |
| C1 | `c4d49a8` | 発見ホーム刷新（orderタブ6種=stickyHeader・明朝順位・読了目安併記・orderに応じたptラベル） |
| 土台 | `993b06f` | API層一般化（DiscoveryQuery・novelDetail・ジャンル表・キャッシュ上限50・全@Query） |
| Phase 0 | `c11ec02` | D意匠モック6画面（発見ホーム/検索/ジャンル/詳細/融合本棚/継続導線）＝以降の翻訳正本 |

- **画面構成**: `ui/discovery/` に Home/Search/Genre/Result/Detail の5画面＋Common（一覧行・状態表示）＋QueryLabels（条件チップ派生・純関数）。VM=DiscoveryViewModel（ホーム/結果/検索ドラフト/履歴を共有・遅延ロード）＋NovelDetailViewModel（ncode独立）。
- **実機スモーク（2026-07-07・上書きinstallで蔵書保持確認済み）**: 発見ホーム実APIランキング／タブ切替／詳細（ヒーロー・評価表・なろうで読むバー）／条件シート→「短編」のみで検索→結果一覧（チップ「短編」「週間順」・594,168作品・全行短編）を目視確認。
- **未検証（実機フィードバック待ち・優先度低)**: ライト/セピアテーマでの発見画面の見え方（スモークはダークのみ）／履歴チップのピン留め操作感／モックとの意匠突合の細部（余白・字間）。

## 1b. 完了（縦スライス第1本＝「週間ランキング一覧が出る」）

**実装・単体テスト・実機の全レベルで検証GREEN**（2026-07-06）。

- **新規（`com.novelreader.narou` に隔離＝既存の蔵書系 Room とは別系統・Roomに一切触れない）**:
  - `narou/model/NarouNovel.kt`（`@JsonClass(generateAdapter=true)`＋`@Json` フルネーム対応）・`DiscoveryResult.kt`
  - `narou/network/NarouApiService.kt`（Retrofit `@GET("novelapi/api/")`）・`NarouNetwork.kt`（OkHttp＋UAインターセプタ＋Moshi）
  - `narou/NovelApiRepository.kt`（allcount分離・6h TTLインメモリキャッシュ・`NarouApiException` 正規化。`timeSource` 注入でテスト可）
  - `viewmodel/DiscoveryViewModel.kt`（`DiscoveryUiState` sealed：Loading/Content/Empty/Error）
  - `ui/DiscoveryScreen.kt`（**最小プレーン意匠**＝トークン参照のみ。意匠は /design モック確定後に翻訳）
  - テスト3本＋golden JSON（`src/test/resources/narou/weekly_ranking.json`）
- **変更（結線）**: `build.gradle`（Retrofit2.11/Moshi1.15 codegen/OkHttp4.12＝**KSP相乗り・settings.gradle変更なし**）・`AndroidManifest.xml`（INTERNET/ACCESS_NETWORK_STATE）・`NovelReaderApplication.kt`（`novelApiRepository by lazy`）・`MainActivity.kt`（`composable("discovery")`）・`BookshelfScreen.kt`（TopAppBar先頭に🔍「小説を探す」導線）。
- **検証エビデンス**:
  - `./gradlew testDebugUnitTest` = **114件全通過**（BUILD SUCCESSFUL）。
  - `./gradlew assembleDebug` = **BUILD SUCCESSFUL、APK 25MiB**（24→25MiB。Moshi codegenでkotlin-reflect回避が効いた）。
  - **実機 PGEM10(ColorOS)**: 上書きインストール（蔵書DB保持）→「探す」→ **実APIから週間ランキング表示**（Re:ゼロ等・「連載中(783話) 778890pt 9513053文字」＝end意味/話数/pt/文字数マッピング正常）。**オフライン時は赤字「ネットワークに接続できません。通信環境を確認して再試行してください」＋再試行ボタン**（`NarouApiException` の正規化メッセージがそのまま表示）を目視確認。

## 2. 実装知見（腐りにくい／踏みやすい）

- **レスポンスJSONキーはフルネーム**（`title`/`ncode`/`global_point`/`general_all_no`/`length`…）。`of` の略号（t/n/gp）は**リクエストの項目選択用**でありレスポンスのキー名ではない。→ `@Json(name=...)` はフルネーム。
- **`end` の意味は直感と逆**: `end=0`→短編・完結済 / `end=1`→連載中（`narou_api_manual.md §5`）。作品種別は `noveltype`（of指定時のキー名。1=連載/2=短編）で識別。
- **VMで `withContext(Dispatchers.IO)` は不要**: Retrofit の suspend は main-safe。実IOへ切替えるとTestDispatcherの制御が及ばずテストが `Loading` のまま失敗する（この修正で解決）。
- **`assertThrows` に `runTest` を入れ子にしない**（`IllegalStateException`）。runTestスコープ内で直接 try/catch。
- 技術選定の理由（Moshi codegen＝KSP相乗り・reflect回避／別系統隔離／キャッシュ方式）は plan と（昇格予定の）ADR 参照。
- **（Phase 1〜2 で追加）検索範囲 `title/ex/keyword/wname` に 0 を送らない**: マニュアル§4.1は「1で指定・全未指定なら全項目」としか定義せず 0 送信は未定義＝選択項目のみ 1 を送る（`NovelApiRepository.kt` の why コメント参照）。
- **転生＋転移の同時指定は `istt=1` へ振替**: `istensei=1&istenni=1` は AND になり両立作品のみに絞られてしまうため、OR の意味を持つ istt を使う。
- **order は `weekly`（週間UU順）→`weeklypoint`（週間pt順）へ変更**: タブごとの表示pt（週間 N pt）と順位根拠を一致させるため。
- **ランキング一覧の of から story を外した**（`OF_LIST`）: 一覧はあらすじ非表示の意匠のため転送しない。詳細は `novelDetail(ncode)` が of 無指定で全項目取得。
- **意匠モックの置き場所（Phase 0 で二重化）**: 発見系モックの一次はリポジトリ `docs/design-candidates/discovery/*.html`（git管理）。claude.ai/design の `Novel Reader UI` プロジェクト `ui-n-phase0/` へも DesignSync で収蔵済み（Design System ペインで閲覧可）。従来の「モック現物はリポジトリに無い」前提は発見系については変わった。
- **モックのレンジスライダーは段階チップへ翻訳**: 文字数等はダイナミックレンジが広く線形スライダーは実用に耐えないため（操作系差分は ADR 0005 スコープ外規定）。
- **検索履歴は DataStore Preferences**（`narou_search_history`・蔵書Roomと別系統）。並び・上限の操作ロジックは `SearchHistory` 拡張の純関数に分離し純JVMテストで担保。VM 側は lazy＋WhileSubscribed で検索画面を開くまでディスク非接触。
- **（Phase 3 で追加）ncode 紐付けは人間確定必須**: title 一致だけの自動紐付けは同名別作品の誤誘導リスクがあるため、候補提示→タップ確定（or 手動 ncode 入力・`isValidNcode` 検証付き）のみ。解除導線（継続カード末尾の極小テキスト）が唯一の救済パス。
- **（Phase 3 で追加）PDF正規化済みタイトル（波ダッシュ U+301C）でも、なろう検索はヒットする**: シャングリラ・フロンティア（タイトルに 〜 を含む）の書名自動検索が候補1件を正しく返した（API 側が波ダッシュ差を吸収する模様。実測1件・保証ではない→ヒットしない場合の逃げ道が手動 ncode 入力）。
- **（Phase 3 で追加）継続情報の取得は最終章表示時のみ**（`novelDetail` は 6h TTL キャッシュ相乗り）。オフライン失敗時は静かに非表示＝読書の没入を通信エラーで壊さない（次回の最終章表示で自然に再試行）。

## 3. 目標ロードマップ（承認済み計画・2026-07-06）

> 正本の現在地・目標はこの節。実装詳細（差し込み点の file:line・アーキ方針）は plan `~/.claude/plans/api-agy-woolly-swan.md`。各段は **/design モック確定 → Compose翻訳 → `cd android && ./gradlew testDebugUnitTest` GREEN**。

**作る機能**: 発見コア（C1 order切替タブ／C2 検索〔word/notword＋範囲 title/ex/keyword/wname〕／C3 ジャンル〔biggenre/genre〕／C4 作品カード詳細）・発見強化（D1 検索履歴＋ピン留め／D2 属性フラグ〔異世界転生/転移等〕／D3 範囲絞り込み＝気分プリセット／D4 type／D5 期間）・目玉（①PDF↔Web継続読書／④静かな没入意匠／②融合本棚／③気分で探す導線）・橋渡し（B1 PDF取込接続／B2 Webで読む導線）・育成（U1 新着話チェック＋通知／U2 整理）。**あわせて既存デフォルト本棚UIの意匠も見直す（§0）**。

**Phase 進捗**:
- [x] **Phase 0** 発見体験の意匠設計（全画面モック6枚・2026-07-07 完了。※融合本棚/継続導線モックは Phase 3〜4 の翻訳元として作成済み・Compose 実装は未着手）
- [x] **Phase 1** 発見コア C1〜C4（2026-07-07 完了・§1a）
- [x] **Phase 2** 発見強化 D1〜D5（2026-07-07 完了・§1a）
- [x] **Phase 3** ★PDF↔Web継続読書（2026-07-07 完了・§0/§1a。Room は v8 衝突により **version 9** で実装。遷移先は暫定＝外部ブラウザ、B2 実装時にアプリ内 WebView へ統一の方針）
- [~] **Phase 4** 融合本棚②＋整理 U2＋新着通知 U1 ★次（スライス1＝導線帯・続きありバッジ完了 `92da396`。残＝Web由来カード(b)・U1・U2）
- [ ] **Phase 5** doc昇格（ADR・handover §D・03↔04 相互リンク）

## 4. 参照

- **承認済み計画（現行の目標・実装詳細の一次情報）: `~/.claude/plans/api-agy-woolly-swan.md`**
- 縦スライス第1本の設計判断（アーカイブ）: `~/.claude/plans/apimd-crystalline-cascade.md`
- API仕様: `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）
- 機能検討: `03-api-feature-analysis.md`（案A推奨）／競合解析: `04-competitor-app-features.md`
- 実機検証作法: `/device-verify` スキル（`adb-bridge`・`connectedAndroidTest` 直叩き禁止）
