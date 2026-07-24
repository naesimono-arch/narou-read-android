# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。判断・Why-not＝`docs/decisions/`（ADR）。
> 腐りにくい知見＝`task_diary.md`・`docs/patterns/`。一次情報の細部＝`.claude/plans/`。やること＝`handover.md`。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。

## 0. 現在の状態

- **新デフォルトUI「明快K」（feat/ui-playground・2026-07-23・実装済み/コミット前）**: 最優先Aの核回答＝
  Skin 6値目 `MEIKAI_K` を新設し**既定スキンへ切替**（既存の明示保存 D/M/P/J/C は不変・装いの間で相互選択可）。
  構造＝〈ラベル付き恒常ボトムナビ3タブ（本棚/さがす/設定・NavHost外静止・タブ間crossfade）＋全画面明示タイトル＋
  **設定画面新設**（テーマ4択/きせかえ/新着通知/診断を集約）＋本棚3列グリッド（キャプション行に可視⋮）＋
  さがす（検索第一強調＋公式サイト逃げ道）＋目次（現在地チップ＋ここから再開＋既読✓）〉。読書はD構造温存。
  モック正本＝`docs/design-candidates/skins/*-K.html`（K専用・監督headless目視で裁定）・設計/裁定の一次情報＝
  `.claude/plans/default-ui-clarity-K-2026-07-23.md`。根拠＝競合4機の実機目視（全機ボトムナビ型）＋UX正本（自明性A0/
  You Are Here/UX15）。ゲート＝テスト861件緑・tokens OK192/NG0・lint 0err/33warn（+1=K設定のInlinedApi・既存同型）。
  実機（PGEM10）＝K全4画面＋読書/装いの間/テーマ切替を監督screencap検分でPASS・**ユーザー目視待ち**。

- **UX/Design 全層監査**: 指摘（Critical 3/Major 24/Minor 29）＋派生改修（CTA一貫性=案A／没入時黒帯明滅=window背景をテーマ色へ再定義／複数選択削除=案B下端バー＋変種B「キャンセル」）まで実装・実機検証済み（ui/polish は main 統合・撤去済み）。残＝発見帯 collapse 退避アニメ体感の追い込み（deferred）・第三者人間テスト便・監査派生 backlog＝`handover.md` ★節が正本。監査の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A/§B）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）。
- **前回の統合（2026-07-18 束ね）**: `reading/vertical-p4`（縦書き章送り P4・実機体感確認済み）・`build/r8-shrink`（release の R8 収縮＝minify+shrinkResources・APK 20.3→7.8MB）・`perf/macrobenchmark`（性能回帰基盤＝起動/本棚スクロール/章送り/大PDF取込の予算 assert・実機実証済み。設計と全実測＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`＋`docs/knowledge/coloros-*`／`macrobenchmark-frametiming-scroll-pitfalls.md`）・`hooks/fabrication-detector`（実行捏造検知器 Tier E3「先行実行フレーミング」）・`ui/skin-framework`（UIスキン機構＝ADR 0021＋0022〔画面構造の二層化〕。M星図/Pカートリッジ/Jポータルの3スキン×全5画面〔本棚/読書/目次/設定/発見〕を Compose 実装。**C3 実機スモーク完了＝J 全5画面掃引＋M/P 本棚を release R8 下で PASS（M/P 全画面は既存 PASS・C は色層）・5スキンとも shrinkResources による資産欠落なし**）を main へ統合。縦書き本体（P0〜P3・P5・P2.5）は統合済み＝縦書きはユーザー到達可能（ADR 0020〔連続横スクロール×自前Compose組版〕）。**R8 リリース収縮の実機回帰完了＝4重点経路（Moshi なろう検索/PDFBox 取込抽出/WorkManager クラス名復元/enum テーマ SEPIA 永続）全 PASS・収縮起因クラッシュ無し**（release APK を debug 署名し install -r で実蔵書DB保持のまま検証。PDFBox は取込時に日本語タイトルを抽出＝CID/CMap 経路通過を確認）。
- **直近の統合（2026-07-23）**: `feat/scraping-prep`（汎用Web小説DL基盤 P2〜P6＋暁＋G1・Room v21）と `feat/delete-source-pdf`（本削除時に取込元PDF本体も削除・Room v20）を main へ統合＝**MIGRATION_19_20 の並列複製を一本化**（19→20→21 パス接続・task_diary #39 の定石の後始末）。`ui/refine`・`ui/wa-modern` は main 同一（固有コミット0）のため worktree ごと削除＝**作業ブランチ全解消・main 一本**（リッチ化再開時は main から切り直す）。
- **ゲート（統合後 ext4 worktree 実測・2026-07-23・全緑）**: `testDebugUnitTest` **861件**（失敗0・両ブランチのテスト群を統合）／`compileDebugAndroidTestSources` 成功（MigrationTest 19→20→21 チェーン込み）／`tools/check_design_tokens.py` OK=192/NG=0（M/P/J 3スキンの期待表を含む・＋Spacing lint: 余白スケール7段 {4,8,12,16,24,32,40}＝ADR0014 §C・NG=0・WARN=0）／`:app:lintDebug` **0 errors・32 warnings**（非ブロック警告のみ）。R8 リリース収縮・C3 実機スモークは実機検証完了（上記参照・OPPO PGEM10）。
- **Room v21**（v20＝本削除時に取込元PDF本体も削除する機能＝取込元 SAF URI を `books.sourceUri` へ永続化。取込時に READ|WRITE 永続権限を本の生存中保持し〔起動時の孤児権限掃除 `releaseOrphanedPermissions` の keepUris に `books.sourceUri` を合流〕、本削除時に `DocumentsContract.deleteDocument` で消す。削除はダイアログの opt-in チェック〔既定OFF・`sourceUri` を持つ本が選択にある時のみ表示〕。書込非対応プロバイダ/なろう縦書きFileProvider取込/旧蔵書は `sourceUri=NULL` で対象外・削除失敗〔移動/削除済み・権限失効・削除非対応〕は Snackbar 通知し本削除は成立。**v21**＝`sourceUrl`/`sourceSite`〔Web取込元の作品URL・サイトアダプタキー＝再取得を同じ抽出器へ回す土台。PDF由来は両方NULL〕。v19＝栞書影の個体差 `shioriTipIndex`/`shioriLenFrac` 永続化〔取込時1回抽選・既存行NULL→title由来へフォールバック〕）。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 不在でクラッシュ＝古い→新しいの一方向のみ）。no-op 再スタンプの機序＝`task_diary.md` #39 追補。
- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・**v21 APK（feat/scraping-prep debug 版）導入済み**＝v19→21 migration 実測通過・汎用DL基盤の実機5点 PASS。統合後 main の v21 は entity 集合が scraping-prep 版と同一＝identity hash 互換（そのまま上書き install 可・再スタンプ不要）。**取込元PDF削除機能（v20 由来）の実機確認は未実施**＝handover 参照。検証ワークフロー＝memory `workflow-autonomous-device-verification`／`workflow-notify-each-step-visual-check`。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 Phase 5 で完全撤去。復旧は git 履歴から）。**本文解析は文書ごと自動検出（`DetectedRules.detect`＝サイズ/列ピッチ/ページ番号座標を実測・検出不能時は ParserRules 定数へフォールバック）**。精度回帰ゲート＝**JVM `JvmGoldenRegressionTest`（golden3本を `testDebugUnitTest` で常時検証・約10秒）**＋実機 `PdfExtractorDeviceSpikeTest`（同一合格ライン・assets 手動配置時のみ）。
- **機能の現在地**（構成の詳細は `/architecture` スキルとコードが正本）: PDF抽出＋ふりがな読書（テーマ3種・没入クローム＝タップトグル・左右スワイプ章送り〔引っ張りプレビュー＋章キャッシュ〕・読書位置/読了永続化）／なろう発見・検索（ADR 0007・規約線＝0010・PDF取込導線＝0011/0013）／Web読書位置記録・続きから再開（ADR 0012）／新着通知（既定OFFオプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）。意匠の正本構造＝ADR 0005/0014。
- **高負荷スカイモード（星図M・ADR 0023 試作）**: debug ビルド限定トグル（本棚⋮開発節）で ON。チャンク式無限プロシージャル
  の粒天の川＋天体系（流星/衛星/彗星/BH）＋奥行き層（空気遠近/暗黒雲/帯2層）＋検分ボタン6種。release は常に OFF・通常モード
  厳密不変（DeepSkyM の durationScale は既定1f恒等）。jank 2.56%（ON時実測）。裁定履歴と残ロードマップ（v8/v9・D展開）＝
  `.claude/plans/richness-expansion-round-2026-07-19.md`。
- **2026-07-23 バグ4件修正＋K形伝播（feat/ui-playground）**: ①Web作品が読書状態フィルタ/件数に分類されない
  →`webReadingStatusFor` 新設・全5スキン配線・必須引数化（読了＝最終話到達の近似と明記） ②Web読書の左上←＝
  階層Up固定（システムBack は WebView 履歴戻り温存＝発見系「←=Up/Back=履歴」と同型） ③PDF取込の自動スクロール
  ＝要素出現駆動化（onPageFinished 全読込待ちが真因） ④さがす→本棚の稀な遷移不能＝タブ判定をライブ
  `currentDestination` へ（スナップショットの1フレーム遅延＋DROP_OLDEST が機序・推定と明記）。
  **K形伝播**＝Kの構造装置（恒常ボトムナビ・明示タイトル・可視⋮・検索第一＋公式逃げ道・現在地チップ等）を
  D/M/P/J モックへ伝播、16枚を `skins/*-{D,M,P,J}.html` へ正本昇格（ユーザー合格 2026-07-23）。**Compose 実装
  完了（2026-07-24）**＝恒常ボトムナビ/設定の全スキン開放・目次チップ/再開/既読・さがす検索第一/公式逃げ道・
  本棚可視⋮/冊数・Web複数選択削除統合（機構裁定＝ADR 0021 追記）。一次情報＝
  `.claude/plans/k-shape-propagation-2026-07-23.md`。ゲート＝テスト882件緑。**残＝実機目視（全スキン掃引）**。
- **2026-07-24 UIラウンド（feat/ui-playground・実装済み/実機目視待ち）**: ユーザー4裁定＋構造化を実装＝
  ①K本棚グリッド**2列改A**（書影≈140dp・3:4・約5冊/画面）・リスト**圧縮S**（KList/KWebListBookCard 新設・D流用廃止）
  ②書影輪郭**線→影**（shadow 2dp 暫定・ダーク影値は実機検分）③未取込Web＝**D改破線**（白ピル廃止・青磁破線＋紙地沈め・
  取込済みは無印確定）④**タブPager化**＝tabs 単一ルート＋`TabPagerHost`（横スワイプ・crossfade廃止・navigateKTab退役・
  Back=page0へ・スロット契約＝**ADR 0022 追記が正本**）⑤**気分パターン3組×日替わり**（MoodPattern・K はページャ＋ドット・
  非KはCLASSIC固定）。モック正本昇格済み（bookshelf-K/bookshelf-list-K 新設/discovery-K）。UI追加はモック先行が恒久ルール化
  （memory `feedback-mock-before-any-ui-addition`）。一次情報＝`.claude/plans/ui-density-swipe-round-2026-07-24.md`。
  ゲート＝testDebugUnitTest 緑・golden 差分なし（対象コンポーネント外）・tokens OK192/NG0・lint 0err/33warn。
  **実機＝install 済みだが激しいスタック報告（2026-07-24）→ 最優先宿題として計測から（handover 正本・決め打ち修正しない）**。
- **既知バグ: なし**（単話の嘘見出し問題は 2026-07-16 修正済み＝題名マーカー0件時は作品タイトルを単一章名へ流用・golden 第4本 N5368ML で恒久回帰）。

- **汎用Web小説DL基盤（最優先B・main 統合済み）**: `scrape/` サイトアダプタ抽象（`NovelSiteAdapter`＋
  `SiteAdapterRegistry` の規約3値ゲート＝Supported/Blocked/Unsupported・なろうグループは Blocked で公式送り）＋
  **カクヨム抽出器**（TOC＝`__NEXT_DATA__` Apollo ストア／本文＝`.widget-episodeBody`・ルビ→中間記法 `|base《ruby》`＝
  既存 ChapterProcessor/HtmlExporter に合流し PDF 蔵書とバイト同契約）＋**fixture ゴールデン**（実HTMLスナップショットで
  構造破損を `testDebugUnitTest` 常時検知＝破損監視の核）が着地。**P3 パイプライン接続も着地**＝Room **v21**
  （v20 は `feat/delete-source-pdf` 先着＝19_20 複製でパス接続・sourceUrl/sourceSite 独立2列）＋`addWebBook`
  （アダプタ→既存HTML契約合流・sourceUrl 重複ガード・pending_jobs 不使用）＋取込導線（ACTION_SEND 全サイト受け／
  ACTION_VIEW は対応ホスト限定・Blocked は公式送りの逃げ道）。**P4 実行時破損監視も着地**＝取込時の構造疑い検知
  （ScrapeIntegrity 3条件・床値20字）→「公式サイトで読む」アクション付きスナックバー＋debug ヘルスボード
  （本棚⋮開発節・release 到達不能）。**P5 発見層の脱なろうも着地**＝サイト非依存 `WorkSummary`/`WorkDetail`
  （discovery/model/）を UI⇄API 境界に挿入し、main の narou/ 外から `NarouNovel` 型参照ゼロ（機械 grep で検証・
  NarouNovelType 検索語彙と ResultContext/DiscoveryQuery は D5 初期スコープどおり不変＝発見はなろうAPIのまま）。
  **P6 後始末も完了**（ADR 0024 追記・architecture/db-migration skill 追従・/stale-check 通過）＝**P2〜P6 全フェーズ着地**。
  実機検証は全5点 PASS 済み（handover 参照）。**アダプタ2サイト目＝暁（akatsuki-novels）着地（2026-07-23）**＝
  TOC=`table.list`／本文=`div.body-novel`（前書き/後書きブロックは本文純度優先で除外）・大文字 `<RUBY>` 対応・
  crawlDelay 3000ms・fixture golden（toc 66話・ルビ変換・前後書き除外）で常時回帰・ACTION_VIEW 対応ホストへ追加。
  **実機検証 全5点 PASS（2026-07-23・PGEM10）**＝①install -r 蔵書保持 ②ACTION_VIEW 解決（既定ブラウザ設定時は
  chooser 非表示が標準挙動＝コンポーネント明示で取込直行を確認）③66話完走・sourceSite='akatsuki' DB 焼付け
  ④目次66話フラット・ルビ実描画 ⑤ヘルスボード OK 章数66。ユーザー目視スクショ送付済み。
  **汎用アダプタ G1 着地（2026-07-23）**＝`scrape/generic/` に SiteProfile（Kotlin 定数表）＋GenericSiteAdapter
  （1プロファイル=1アダプタ・暁を表1行目へ移植し専用 AkatsukiAdapter 退役・golden 固定値不変で回帰昇格）＋
  **規約ゲート（2026-07-23 まとめ裁定反映済み）**＝ハーメルンのみ pendingHosts（保留）・アルファポリス/Pixiv/
  野いちご/ベリーズカフェは NG＝blockedHosts（**グレー領域の保守裁定＝ADR 0024 追記が正本**・公式送り）。
  カクヨムは JSON 系＝専用のまま温存。設計正本＝`.claude/plans/generic-adapter-design-2026-07-23.md`。
  G3 recon＝表駆動の新規候補ゼロ・**G2 ヒューリスティックは今はやらない（裁定済み）**＝対応面拡大はいったん打ち止め。**scrape HTTP 土台強化済み（2026-07-23）**＝per-host Crawl-delay
  （アダプタ宣言 `crawlDelayMs`・既定2500ms）＋グローバル床1s＋429/503 Full Jitter バックオフ（Retry-After 尊重・
  403/404 即中止）。一次情報＝`.claude/plans/scraping-foundation-design-2026-07-20.md`／
  裁定＝`handover.md`「汎用DL基盤 実装トラック」＋ADR 0024。
- **2026-07-23 自律ラウンド（main 統合済み・当時 JVM 828件緑）**: ①「戻る」階層統一 Option A 実装
  （`ReadingBackStack.back()` を経路逆再生→階層 up へ再定義・設計正本＝`.claude/plans/back-unification-design-2026-07-23.md`）
  ②Web取込スナックバー残留の真因対処（取込中＝ProcessingBanner へ収斂・完了＝transient Short 化）
  ③`inject_subagent_briefing.py` の自動テスト16件新設（hooks 全236件緑）。実機スモークは端末到達不能で未実施＝
  ユーザー便（戻る挙動・Web取込バナー表示）。裁定待ち4件＝handover 参照（縦書きタイトル・Web削除グリッド⋮導線・
  発見サブツリーへの戻る拡張可否・Web取込バナー割り切り）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
