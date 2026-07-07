# STATUS — `api-lab` ブランチ現況台帳

> **このブランチ専用の現況正本**（`STATUS.md` は main 正本のため、api-lab の作業状態はここに記録する）。
> ブランチをまたぐ不変知見は auto-memory、main 全体の現況は `STATUS.md`、腐りにくい外部事実は `task_diary.md`。
> 一次情報（設計判断の全文・未決事項）は plan `~/.claude/plans/apimd-crystalline-cascade.md`。
> マージ時にここを `STATUS.md`／`handover.md §D` へ集約する。

## 0. 現在地（★次はここから）

- **Phase 0〜3 完遂（2026-07-07・branch=`api-lab-ai`・worktree `/home/qingj/wt/api-lab-ai`）**: 発見体験の意匠モック6画面＋発見コアC1〜C4＋発見強化D1〜D5＋**目玉①PDF↔Web継続読書**を実装。**テスト162件 GREEN・assembleDebug 25.6MiB・実機(PGEM10)スモーク全経路OK**。Phase 3 の実機エビデンス: シャングリラ・フロンティア（PDF 951章）で最終章末尾→「なろうで続きを探す」→紐付けシート（書名自動検索・候補=硬梨菜/連載中全955話）→タップ紐付け→継続カード「手元のPDFは第951話まで。なろうには第952〜955話（新着4話）」→藍ボタン→**Chrome で ncode.syosetu.com の 952/955 話に正確着地**。解除→プロンプト復帰→再紐付け・DB永続化（books.ncode='N6169DZ'・WAL込み確認）まで検証済み。コミット表は §1。
- **⚠️ Room version は 9 へ退避（v8 は使えない）**: 並列ブランチ `feat/processing-resilience` が v8（`pending_jobs`）を先取り消費し実機も v8 化済みのため、当初の v8 実装は identity hash 衝突で起動即クラッシュした（実測）。本ブランチは v9（`MIGRATION_8_9`=books.ncode）へ退避し、resilience の 7→8 を同一内容で複製してパスを繋いだ。**マージ時は「version 9＋両 migration＋両エンティティ」で合併すること**。機序と予防は `task_diary.md` #39・`/db-migration` スキル。
- **設計方針（2026-07-07 固定 → 2026-07-08 なろう規約確認により縮小補正）**: Web 閲覧系の導線（継続読書・なろうで読む）の「アプリ内完結」で**許されるのは〈WebView 等で"加工することなくそのまま"表示〉のみ**。なろう運営が開発・運営者向けヘルプ183「よくある違反行為」で明文の線引きをしており、**広告除去"等の加工"を加えた表示・本文の機械的取得は違反（確認され次第 強制退会・削除等／根拠 14条20・22・23・24項。詳細と原文は `task_diary.md #45`）**。禁止の本体は"広告除去"ではなく**"加工"そのもの**＝フォント/配色/余白の CSS 注入やヘッダー整理も該当し、**独自UIを被せる WebView 内包は不可**。したがって当初の「アプリ内 WebView で完結」は **「加工なし表示（＝Custom Tabs 相当）どまり」へ縮小**する。構造的に加工不能な **Chrome Custom Tabs を既定の解**とする（用途が"加工なし表示"に限られる以上、フル WebView を自前で抱える利点はほぼ無い）。意匠・没入の作り込みは権利を自前で持つ PDF 読書面に集中し、なろうは「発見（メタ）＋加工なし送客」に徹する（**案A＝本文非取得の妥当性が規約面から補強された**）。現状 Phase 3 の外部ブラウザ送客（C4 と同方式）は規約セーフのまま維持でよく、引き戻すなら Custom Tabs へ。
- **Phase 4 スライス1 完了（2026-07-07・`92da396`）**: 融合本棚のうち **(c) 見つける導線帯**（本棚先頭・グリッド/リスト両対応）と **(a) 続きありバッジ**（紐付け済みの本に「● 続き N話」＝青磁ドット＋藍文字。カード単位 produceState・6h TTL キャッシュ相乗り・失敗時は静かに非表示）を実装。**実機でグリッド/リスト両方の表示確認済み**（シャングリラに「続き 4話」）。テスト162件緑・assembleDebug 成功。
- **検索UX改善（ADR 0007 の3原則適用）完了（2026-07-07・5コミット＋docs）**: ユーザー実機フィードバック×競合5アプリ実機調査（`docs/reference/05-competitor-search-ui-field-report.md`）から共通言語＝3原則（①見えている条件はその場で変えられる ②検索の仕組みを隠さない ③語彙を知らなくても絞り込める）を `docs/decisions/0007-search-ux-three-principles.md` に固定し、4バッチを agy へ委譲実装（仕様書 `.claude/plans/search-ux-three-principles-impl-2026-07-07.md`）。**テスト174件全緑・実機(PGEM10)スモーク全項目PASS**（範囲既定=タイトル／「更新された時期」7日以内・今月・先月／テーマ・除外6軸／文字数100万字刻み＋カスタム／結果画面「週間順⌄」→月間へその場切替＝ptラベル連動／「条件を変更」=検索発のみ／詳細キーワードタップ→「ほのぼの」keyword検索131,637作品／キュレーションチップ→word追加＋keyword範囲自動ON）。**agy委譲の品質欠陥2件を監督レビュー/実機スモークで検出・修正**: ①nottensei/nottenni の送出欠落（UIだけ在るサイレント無効）②「今月」「先月」チップのスコープ外削除（追加行だけのdiffレビューでは見えない退行）。
- **検索UX第2ラウンド（実機フィードバック②への対応）完了（2026-07-07・7コミット）**: ユーザーフィードバック6点を全消化。**テスト186件全緑・実機(PGEM10)スモーク全項目PASS・API送出は直叩き突合で件数完全一致を実証**（`type=ter&length=10000-500000`→188,349 一致／`lastup=unixtime範囲`→313 一致）。
  - ①**セピア差別化**: 読書 `ReadingColors.SEPIA` を琥珀紙へ再調律（`ccca2fa`）＋**真因＝セピア時の本棚・発見系がライト配色流用**だったのを `SepiaColorScheme` 新設で3値追従化（`fa24366`）。モック逆反映は handover 宿題。
  - ②**キーワード公式準拠・全数収載（案A）**: 旧4分類（舞台/主人公/展開/雰囲気＝公式由来でない独自抜粋）を、なろう公式検索ページ「検索ワードを選択」パネルの分類へ全面置換（作品傾向/登場キャラクター/舞台/時代設定/要素の5分類46語=常時表示＋ジャンル別17カテゴリ69語=折りたたみ）。全115語を公式HTML現物と機械照合で一字一句一致確認（`0606807`）。
  - ③**複数選択OR**: 作品の形態（type複合値 re/ter へマップ・**短編+連載中のみAPI表現不可→2クエリのクライアントマージ**・task_diary #42）／更新された時期（UNIX秒レンジ合成・JST固定・非連続組はトグルで間を自動点灯）＝`8d09e7a`。文字数・読了時間はプリセット連続段の結合（非隣接タップは間も点灯＝送る範囲とチップ点灯を一致）＝`088de15`。
  - ④**ジャンルドリルダウン**: 結果画面のジャンルチップを常設＋「すべてのジャンル→大→小」階層メニュー化。大ジャンル直選択後の小ジャンル選択と、キーワード検索発への後付けジャンル絞り込みの両課題を解消。SEARCH/KEYWORD発は見出し（検索語）維持（`0fb97e6`）。
  - **agy 委譲の品質欠陥を監督レビューで2件検出・修正**（`088de15` に含む）: (a)レンジ下端の段の消灯が全消しになる (b)合成レンジがカスタム入力誤判定でチップ非点灯。ほか KDoc 内 `[i..j]` のコンパイルエラー（task_diary #43）も検出・修正。仕様書=`.claude/plans/search-ux-round2-impl-2026-07-07.md`。
- **コード健全性監査（2026-07-07・5軸並列＝突貫の名残/アーキ整合/テスト網羅/エラー境界/依存規約）完了**: 検索・API系全ファイル＋既存画面への配線差分を監査。**確定バグ4件を修正済み（テスト200件全緑・assembleDebug 成功）**:
  - ①**詳細経路の novel_type キー不一致**: of 無指定（novelDetail）のレスポンスキーは `novel_type`（マニュアル§5注記・実API実測で確定）だが `noveltype` しかマップせず **novelType が詳細経路で常に null**＝詳細画面で短編が「完結 1話」誤表示・ContinuationLogic の短編ガードがサイレント無効。→ 両キーを別フィールドで受け `novelType` を合流アクセサ化（NarouNovel.kt）。
  - ②**JsonDataException 素通りクラッシュ**: wrapApiException が HttpException/IOException しか正規化せず、API の型不一致 JSON（RuntimeException 系）が「静かに非表示」前提の本棚バッジ・読書画面までクラッシュ波及。→ JsonDataException/JsonEncodingException を NarouApiException へ正規化（HTML応答の「ネットワークに接続できません」誤案内も解消）。
  - ③**「短編+連載中」×新着順マージ破綻**: OF_LIST にソートキー（novelupdated_at）が無く全件 null → 安定ソートが連結順のまま take で**短編だけ残り連載中が全滅**。→ OF_LIST へ `nu` 追加＋NEW マージキーを `novelupdatedAt` に（order=new は「新着更新順」＝novelupdated_at 降順のミラー）。
  - ④**ロード非キャンセルの応答逆転**: loadHome/loadResult が前ジョブを保持せず、遅い旧クエリの後着で「タブ・見出しは新、リストは旧」の食い違い。→ Job 保持＋cancel（DiscoveryViewModel）。
  - あわせて: カスタム数値入力の負数/Int桁あふれ/全角数字のサイレント無効を入力正規化＋Long計算で構造的に防止（`normalizeCustomRangeInput`・buildCustomRange 堅牢化）／novelDetail の NotFound を負キャッシュ化（削除済み作品で本棚表示のたび再通信するマナー違反を解消）／死コード・衛生（ACCESS_NETWORK_STATE 未使用権限・clearCache()・宙に浮いたコメント・陳腐化コメント・未使用 import・なろうURL手組み重複→narouWorkUrl 統一）。回帰テスト14件追加（二重キー・NEW順マージ・HttpException/JsonDataException 正規化・負キャッシュ・lastup 3種/非連続・DiscoveryCommon 純関数3つ・stale応答競合・カスタム入力境界）＝**計200件**。
- **監査残課題（構造系＝次にこの面を触るときに。優先度順）**:
  1. **[アーキ] PDF↔Web継続読書系だけ VM 不在**: `NcodeLinkSheet` が Repository をコンストラクタ引数で受け検索ステートマシンを remember で内蔵（**回転で検索結果・入力途中 ncode・シート開閉が全損**）／`BookCard` の続きありバッジが produceState から Repository 直撃（カード毎に novelDetail が発火・テスト不能）。→ NcodeLinkViewModel 新設＋バッジは BookshelfViewModel で一括照会し Map 配布へ。
  2. **[構造] DiscoverySearchScreen.kt 1187行の god file**: 条件シート約680行の抽出（`SearchConditionSheet.kt`）・カスタム範囲入力の約90行×2コピペの部品化・段階チップ値とラベルの平行定義統合（LENGTH_STEPS/TIME_STEPS とチップ文言が別ファイル）・カスタム2重フラグ（isXCustom×xCustomActive）の SearchDraft への一本化（第2ラウンド欠陥2件の震源）。
  3. **[型] 結果画面の条件チップが表示文字列マッチ＋位置規約で種別判定**（DiscoveryResultScreen）: `conditionChipLabels` を `List<ConditionChip(label, kind)>` へ格上げ。
  4. **[機能ずれ] notword が UI 未配線のデッドパス**（モデル・API・チップ文言だけ存在。§3 の C2 完了表記は「除外語UIを除き完了」が実態）／**ページング未実装**（`st` はサービス定義だけ・結果は30件打ち切りのまま総件数を提示。実装時に `@Query("st")` を活用）。
  5. **[将来の罠] NovelApiRepository のキャッシュは「全呼び出しが Main dispatcher」という暗黙不変条件で成立**（素の mutableMap）。U1 新着チェック（Worker 化が濃厚）で踏む→ ConcurrentHashMap 化 or Main 限定の why 明記が先。／cacheKey は trim・送信は非 trim の非対称（NcodeLinkSheet 経由の word が素通し）。
  6. **[小粒] SearchDraft の SavedStateHandle 非対応（プロセス死でドラフト全損＝外部ブラウザ遷移の多いアプリでは起きやすい）／NcodeLinkSheet「全0話」・DiscoveryCommon「連載中 1話」の欠損値捏造表示／NovelDetailScreen の日付手書きパース（catch(Exception) why なし・切り出してテスト可能に）／UA "NovelReader-Android/1.0" の BuildConfig 非連動（buildConfig 機能自体が無効のため見送り）／MockWebServer で実 URL エンコードを固定するテスト1本（全テストが mockk 差し替えで Retrofit 実エンコード未検証）。
- **★次アクション**: **Phase 4 残り＝(b) Web由来・未取込カード（新データ概念＝Web作品を本棚に置く・DB変更を伴う）／U1 新着話チェック＋通知／U2 整理**。⚠️ DB 変更時は次版=v10 だが、**着手前に必ず全 worktree の version 先取りを確認**（task_diary #39・`/db-migration` 更新済み）。B2 実装時は上記の補正方針＝**加工なし送客（Chrome Custom Tabs）**を適用（独自UIを被せる WebView 内包はなろう規約NG＝§0・task_diary #45）。⚠️ U1 着手時は監査残課題5（キャッシュの Main 前提）を先に解消すること。
- **旧記録（縦スライス第1本・5コミット済み 2026-07-06）**: `e19fe50`(依存＋権限)→`6783d9a`(メタ取得サブシステム＋テスト)→`ac2d575`(画面＋導線)→`3a4ec46`(調査資料docs)→`8b267c8`(ディスカバリVMテスト)。
- **やっていること**: なろう公式APIの**発見機能を「第2の柱」に育てる**。案A（本文は取らずメタのみ）を堅持しつつ、発見機能を「公式より丁寧・アプリ内で完結するレベル」に作り込む。目玉＝**PDF↔Web継続読書**と**静かな没入意匠**。競合5アプリ解析（`docs/reference/04-competitor-app-features.md`）が裏付け＝競合はどれも発見が弱い。**計画を策定・ユーザー承認済み（2026-07-06）**＝§3 の目標ロードマップが現在地・目標の正本、実装詳細の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。
- **既存本棚UIの刷新（融合本棚・目玉②）**: Phase 0 でモック `bookshelf-fusion-D.html`（見つける導線帯・続きありバッジ・Web由来カード並置）を作成済み。**Compose 実装は Phase 4**（Phase 3 の ncode 紐付けが前提のため）。見た目の正本は ADR0005＋HTMLモック（直書き禁止・`theme/Color.kt`／`MinchoFamily` 経由）。

## 1a. 完了（Phase 0〜3＝発見体験の意匠＋発見コア＋発見強化＋PDF↔Web継続読書・2026-07-07）

実装・単体テスト162件・assembleDebug・実機スモークの全レベルで GREEN。コミット表（新しい順）:

| Phase | commit | 内容 |
|---|---|---|
| UX改善 | `9bee55d` | fix: 「今月」「先月」チップ復元（委譲バッチ2のスコープ外削除の退行） |
| UX改善4 | `714980c` | キーワードタップ検索＋キュレーションキーワード（原則1×3） |
| UX改善3 | `f2cedb7` | 結果一覧の並び順・ジャンルその場変更＋「条件を変更」（原則1） |
| UX改善2 | `1f96e05` | 属性6軸の含む/除外＋文字数/読了時間の上位刻み・カスタム・相互排他（原則3） |
| UX改善1 | `4eeb5c7` | 検索範囲既定=タイトル・全解除禁止＋「更新された時期」sevenday化（原則2） |
| UX改善0 | `bc9edf6` | docs: ADR0007（3原則）・競合検索UI実機レポート05・実装仕様 |
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
- [x] **Phase 1** 発見コア C1〜C4（2026-07-07 完了・§1a。※C2 の notword は UI 未配線＝除外語入力は未実装。監査残課題4）
- [x] **Phase 2** 発見強化 D1〜D5（2026-07-07 完了・§1a）
- [x] **Phase 3** ★PDF↔Web継続読書（2026-07-07 完了・§0/§1a。Room は v8 衝突により **version 9** で実装。遷移先は暫定＝外部ブラウザ、B2 実装時にアプリ内 WebView へ統一の方針）
- [~] **Phase 4** 融合本棚②＋整理 U2＋新着通知 U1 ★次（スライス1＝導線帯・続きありバッジ完了 `92da396`。残＝Web由来カード(b)・U1・U2）
- [ ] **Phase 5** doc昇格（ADR・handover §D・03↔04 相互リンク・architecture スキルへ発見/検索層を追記）

## 4. 参照

- **承認済み計画（現行の目標・実装詳細の一次情報）: `~/.claude/plans/api-agy-woolly-swan.md`**
- 縦スライス第1本の設計判断（アーカイブ）: `~/.claude/plans/apimd-crystalline-cascade.md`
- API仕様: `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）
- 機能検討: `03-api-feature-analysis.md`（案A推奨）／競合解析: `04-competitor-app-features.md`
- 実機検証作法: `/device-verify` スキル（`adb-bridge`・`connectedAndroidTest` 直叩き禁止）
