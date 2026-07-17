# Macrobenchmark 新設 — 起動メモ（骨格は未裁定＝このセッションで設計から）

> **対象ブランチ: `perf/macrobenchmark`**（worktree `/home/qingj/wt/perf-macrobenchmark`・ext4＝素の `gw testDebugUnitTest`）
> 出自: handover「監査派生 backlog」——**大PDF／10倍蔵書／長時間章送りの予算漸進劣化を P90/P99 で assert**。INTERNET 権限なしで出荷後テレメトリ不能なことの代替＝ローカル回帰基盤。

## 与件・制約（2026-07-17 主セッションで確認済み）

- 実体＝新規 Gradle モジュール `:macrobenchmark` 追加。既存改変は `android/settings.gradle` と `android/app/build.gradle`（`benchmark` ビルドタイプ・`<profileable>`）のみに留める（縦書き/スキンの並行 wt と競合させない）。
- AGP 8.6.1 / JDK 17 / compileSdk 等の正本は `android/app/build.gradle`。ビルド作法は `/build` スキル先行（必須ゲート）。
- **実機必須**（Macrobenchmark はエミュ非推奨・JVM 不可）: OPPO PGEM10 `192.168.1.210:5555`。実機に触る前に `adb-bridge`、そして**実機投入前に一度ユーザーへ確認**（memory `feedback-ask-before-device-testing`）。OPPO/ColorOS 固有の壁は `/device-verify` §4。
- 蔵書DBを壊す操作は捨て本で（memory `device-verify-delegation-no-destructive-on-real-library`）。「10倍蔵書」シナリオはテストデータ投入方式の設計が要る（実蔵書を汚さない）。
- 計測対象候補（handover の3本柱）: ①大PDF取込（抽出パイプライン）②蔵書10倍時の本棚起動/スクロール ③長時間章送り（スワイプ連続・jank%）。P50/P90/P99 で予算 assert。
- sample_pdfs/ に未追跡の実PDF数本あり（canonical 直下）。ベンチ用アセットの置き場・サイズは設計判断（git に大物を入れない工夫を検討）。

## 確定した設計（2026-07-17 Phase 1 実装済み）

- **`:macrobenchmark`＝`com.android.test` モジュール**（`android/macrobenchmark/`・self-instrumenting・benchmark variant のみ有効化）。依存＝`benchmark-macro-junit4:1.3.4`＋`uiautomator:2.3.0`（AGP 8.6.1 と整合・version catalog 不使用なので settings.gradle 集中宣言に `com.android.test 8.6.1` を追加）。
- **実蔵書保護が最優先** → `:app` の `benchmark` ビルドタイプに **`applicationIdSuffix ".benchmark"`**＝実機の `com.novelreader` と別アプリで共存し、計測用データ投入/wipe が実蔵書DBに触れない。随伴改変＝FileProvider authority を `${applicationId}.fileprovider`（manifest）／実行時 `context.packageName` 組み立て（PdfImportViewModel。**BuildConfig はこのアプリでは未生成**のため定数化不可だった）。
- **`<profileable android:shell="true">`** は `app/src/benchmark/AndroidManifest.xml` の overlay（main manifest 不変）。
- **予算較正方式**: 初回は計測のみ→OPPO 実測の P50/P90/P99 から予算を引き、assert は instrumentation 引数（例 `-e enableBudgetAssert true`）でゲートして有効化。
- 検分済み: benchmark APK＝`com.novelreader.benchmark`・profileable 反映・authority 追従（aapt2 実測）。ゲート全緑＝`:macrobenchmark:assembleBenchmark`／`:app:assembleBenchmark`／`testDebugUnitTest` 508件。

## 実機初回計測（2026-07-17 完了・OPPO PGEM10 / Android 16）

- **coldStartup timeToInitialDisplayMs（5反復）: 243.7 / 251.2 / 252.9 / 253.3 / 274.5 → median 252.9ms**
  （最大値は初回反復のみ・分布タイト。結果JSON=端末 `Android/media/com.novelreader.macrobenchmark/…benchmarkData.json`）
- 完走には互換修正3点＋運用回避2点が必要だった（詳細＝`docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md`）:
  ① profileinstaller 1.4.1 明示（1.3.1 は SDK36 非対応・app/build.gradle）② benchmark-macro-junit4 1.4.1（1.3.4 は SDK36 でハング）
  ③ APK は `install -r -g`（ColorOS が shell `pm grant` を遮断）④ **SIGQUIT 除細動ループ必須**（UiAutomation シェル完了待ちが
  ColorOS で永久ブロック→2秒周期 `run-as … kill -3` で完走）⑤ 走行前に perfetto/trace_processor 残骸ゼロ確認（残骸は再起動でのみ掃除可）。
- **予算候補（未確定・assert 実装時に採用判断）**: median ≤ 350ms・max ≤ 500ms（実測 median×1.4 / max×1.8 の余裕。
  ColorOS の外乱と端末温度ばらつき込み。`-e enableBudgetAssert true` ゲートで有効化する設計は既定どおり）。

## 起動予算 assert＋実行スクリプト（2026-07-17 実装済み・実機実走は未）

- **予算採用**: median ≤ 350ms / max ≤ 500ms（上の候補どおり）。`-e enableBudgetAssert true` のときのみ判定（既定＝計測のみ・従来挙動不変）。
- **値源＝benchmarkData.json**（`StartupBudget.kt`）: 1.4.1 実バイナリ確認で `measureRepeated` は全オーバーロード void・結果コールバック無し。一方 `ResultWriter.appendTestResult` が measureRepeated 毎リターン前に `*-benchmarkData.json` を累積書き出し（`androidx.benchmark.output.enable=true` が前提）→ 完了直後に JSON を読む方式が最も安定（experimental API 直呼び・RunListener 傍受は却下＝バージョン間で脆い）。
- **残骸 JSON の偽 PASS 防止**: measureRepeated 開始時刻より JSON の lastModified が古ければ fail（output.enable 無効時に前回走行の残骸を拾う穴をレビューで検出・封鎖）。
- **実行スクリプト＝`tools/run_macrobenchmark.sh`**（`--install`＝`install -r -g`／`--assert`／`--serial`）: 残骸チェック→`am instrument` 背景起動→PID ポーリング→SIGQUIT 除細動ループ（`ps -p` 生存判定）→出力本文で成否判定（exit code に頼らない）→JSON pull・median/max 表示→exit code 連動。約13分・タイムアウト30分。
- **実機実走で両経路を実証済み（2026-07-17）**:
  - PASS 経路＝`--install --assert` で完走 3.5分・median 277.7ms / max 305.4ms（予算内）・`OK (1 test)`。採用した引数列（基本形＋`output.enable true`）で問題なし。初回13分は初回特有で、2回目以降は約3.5分。
  - FAIL 経路＝`--assert --budget-median 100 --budget-max 100`（予算の instrumentation 引数上書き＝FAIL 実証・将来較正用に新設）で AssertionError「median=261.8ms > 予算 100.0ms…」・スクリプト exit 1 連動を確認＝効かないゲートでないことを実証。
  - 副次の実証2件: ①**`am instrument` はテスト失敗でも adb exit=0**＝出力本文judgeが実際に必須だった ②初回実走はスクリプトの set -e 地雷（プロセス起動前の `pidof` 失敗がコマンド置換経由で die を迂回し即 exit）で頓死→同型4箇所を全点検修正済み（冒頭に回避方針コメント）。

## ②10倍蔵書シーダー＋本棚スクロール jank（2026-07-17 実装・実機完走 PASS）

- **シーダー＝`app/src/benchmark/java/com/novelreader/bench/LibrarySeedReceiver.kt`**（benchmark variant にのみ存在・出荷物に不在）。ordered broadcast（action `com.novelreader.benchmark.action.SEED_LIBRARY`・extras `count`=100/`gridMode`/`clear`）で投入し、**resultCode＝投入後の bench_seed 件数**で完了を同期確認（ベンチ側は件数不一致で即 fail・resultData に失敗理由）。
- **フェイク本＝最小4値で成立**（調査で確定した事実）: `id=bench_seed_%04d`／title=長短10題プール巡回＋`　其の N` 一意化（書影は純 Canvas の title フォールバック描画＝画像不要）／htmlDirPath=実在しない `filesDir/novels/<id>`（章数えは `listFiles` null 安全で0話・未読カード）／addedAt=`1.7e12+i*60_000`（決定論＝並び安定）。progress 行なし＝全冊 UNREAD・addedAt 降順。冪等（REPLACE＋決定論 id）。
- **シードはシェル `am broadcast` でなく test app からの `sendOrderedBroadcast`**（ColorOS の UiAutomation シェルハングを経路ごと回避）＋CountDownLatch(60s)。
- **ベンチ＝`BookshelfScrollBenchmark`**: `scrollList`/`scrollGrid` の2本（モードはシーダーが prefs `app_prefs`/`is_grid_view` を書き分け）。FrameTimingMetric・iterations 5・COLD。スクロール対象は `By.scrollable(true)`（app に testTag 皆無のため。**main ソース完全無改変**）＋`setGestureMargin(幅/5)`・下フリング×3→上×3。予算 assert は較正方針どおり未実装（初回計測後に決める）。
- **スクリプト＝`--scenario startup|shelf-scroll`** 新設（既定 startup＝従来挙動不変・`--assert`/`--budget-*` を shelf-scroll と併用したら exit 2）。結果表示 python は benchmarks[] 全走査＋`metrics`/`sampledMetrics` 両対応（timeToInitialDisplayMs=median/max・frameDurationCpuMs/frameOverrunMs=P50/P90/P95/P99）。
- 検証済み: `:app:assembleBenchmark`／`:macrobenchmark:assembleBenchmark` BUILD SUCCESSFUL・`bash -n` OK・scenario ガード3経路 exit 2・testDebugUnitTest 緑（main 無改変）。
- **実機完走までに要した是正3点**（初版設計から差し替え。機序の正本＝`docs/knowledge/coloros-broadcast-silent-drop.md`・`macrobenchmark-frametiming-scroll-pitfalls.md`）:
  1. **シード＝app-to-app sendOrderedBroadcast は ColorOS で沈黙不達**（背景発×dead プロセス起動遮断／HANS 凍結中スキップの2様・いずれも result=0 正常完了に化ける）→ `force-stop → shell am broadcast`（`UiDevice.executeShellCommand`）へ差し替え・resultCode=投入後件数の検証は維持。
  2. **`startupMode=COLD` は setupBlock 後に対象を force-stop する仕様** → setup 内起動が無効化されホームを空フリング（`0 found for frameDurationCpuMs` で遠因死）→ `startupMode=null`＋setup 冒頭 `killProcess()` 自前、**前面ガード**（`By.pkg` 出現検証・launcher も scrollable を持つため scrollable 待ちは素通り）を新設。
  3. **UiObject2 使い回しは Compose ツリー変化で StaleObjectException** → フリング毎に取り直し＋最大高 scrollable 選択（チップ列誤爆回避）＋stale 1回だけ再試行。
- **実機初回計測（2026-07-17・OPPO PGEM10 / Android 16・100冊・各5反復・PASS）**:
  - scrollList: frameDurationCpuMs **P50 8.6 / P90 11.0 / P95 12.5 / P99 16.6ms**・frameOverrunMs P50 −7.0 / P99 +1.4ms
  - scrollGrid: frameDurationCpuMs **P50 10.4 / P90 13.3 / P95 14.2 / P99 17.5ms**・frameOverrunMs P50 −4.7 / P99 +2.8ms
  - 所見: P95 まで deadline 前完了＝100冊スクロールは健康。1反復≒60s・2テストで計12分前後。
- **予算候補（未採用・assert 追加時に判断）**: frameDurationCpuMs で **P50 ≤ 15ms・P90 ≤ 20ms・P99 ≤ 30ms**（両モード共通。実測×1.4〜1.8 の余裕＝起動予算と同じ流儀。frameOverrunMs は表示リフレッシュ依存が強く予算軸にしない）。

## ③長時間章送り jank（2026-07-17 実装済み・実機実走は未）

- **シーダー拡張（LibrarySeedReceiver）**: extra `chapterCount`（既定0＝従来挙動不変）。1以上で最新1冊（`bench_seed_{count-1}`＝addedAt 最大）を固定題「**章送り計測の書**」にし、`filesDir/novels/<id>/` へ実HTML（`chap_1..N.html`＝`<h1>第N章</h1>`＋`div.content`・`index.html`＝`ul.index-list li a` 全章列挙）を決定論生成で毎回上書き。**progress を chap_1.html 先頭へ強制リセット**（`insertIfAbsent`＋`updatePosition` の2手＝2回目以降も必ず戻る）。`clear` は novels ディレクトリ再帰削除＋progress 行削除も同梱。
- **設計根拠（調査で確定した事実）**: 読書画面の章順は listFiles でなく **index.html の目次リンク順が正本**（`ChapterHtmlParser.parseToc`）／progress 行が無いと `startFile="index.html"`＝目次着地で章送り不能／スワイプ確定は水平96dp 超 or 700dp/s（touch slop 未満はタップ＝没入クロームのトグルに化ける）。
- **ベンチ＝`ChapterFlipBenchmark.flipChapters`**: FrameTimingMetric・iterations 5・COLD 自前。setup 毎反復＝シード（progress リセット目的）→前面ガード→`By.text("章送り計測の書")` タップ→`第1章` 着地検証。measure＝左スワイプ×30（各回 `swipe(LEFT, 0.8f)`・最大高 scrollable 取り直し・次章タイトル `第{n}章` の出現を5s 待ち＝空振り即 fail）。予算 assert は初回実測後に較正（②と同じ流儀）。
- **スクリプト**: `--scenario chapter-flip` 新設（予算未較正のため `--assert`/`--budget-*` は exit 2）。

## ④大PDF取込 TraceSectionMetric（2026-07-17 実装済み・実機実走は未）

- **アセット**: push 方式は Android 11+ で不成立（/sdcard/Android/data へ adb push 不可・/data/local/tmp は SELinux で app 読取不可）→ **git 追跡済み `sample_pdfs/N6169DZ.pdf`（8.5MB・長編951章）を benchmark variant の assets に同梱**（`copyBenchmarkPdfAsset` Copy タスク→ TaskProvider を `sourceSets.benchmark.assets.srcDir` へ直接渡し全消費タスクへ依存自動配線。dir＋merge への手動 dependsOn は lint 系の implicit_dependency 検証エラーになることを実測）。出荷 APK 不変。
- **trace 区間**: `androidx.tracing:tracing-ktx:1.2.0` 新規導入。挿入は自前ラッパー `trace/Sections.kt` 経由＝クラスロード時に `android.os.Trace` 可用性を1回判定し **JVM 単体テストでは完全素通し**（ゴールデン回帰508件を巻き込まない。実測で緑確認）。⚠ ktx の `trace{}` は block が crossinline のため委譲不可（コンパイルエラー実測）→ `Trace.beginSection/endSection` 直呼び＋try/finally。区間＝`Import#copyToTemp/sha256/extract/insertDb`＋`Extract#meta/engine/splitChapters/exportHtml`（insertDb は suspend 再ディスパッチでスライス分裂しうる＝値は要実測評価）。
- **発火＝本番経路**: `ImportBenchReceiver`（src/benchmark 限定）`mode=start`＝assets→cacheDir コピー→`PdfProcessingService` ACTION_START（`data=file://`・StrictMode VmPolicy LAX は bench プロセス限定の割り切り）／`mode=clear`＝books/progress/pending_jobs 全削除＋novels/ 再帰削除（SHA重複遮断・べき等ガードを外し反復可能に）。
- **ベンチ＝`PdfImportBenchmark.importLargePdf`**: TraceSectionMetric×4・iterations 3・FrameTimingMetric なし（取込中 UI 静止＝frame 0件死の回避）。setup＝force-stop→clear（resultCode=0 検証）→前面起動。measure＝start broadcast（前面＝生存・非凍結へ配達）→本棚に「シャングリラ・フロンティア」（golden JSON 正本タイトルの先頭句・〜の U+301C/FF5E 取り違え回避で textContains）出現を最大10分待ち。
- **スクリプト**: `--scenario pdf-import` 新設（TIMEOUT 60分へ拡大・`--assert`/`--budget-*` は exit 2）。結果表示 python に未知メトリクス汎用フォールバック（`Import#…` 等を median/min/max 表示）を追加。

## ③実機初回計測（2026-07-18 完走 PASS・OPPO PGEM10 / Android 16・50章シード・30章送り×5反復・7.5分）

- **frameDurationCpuMs: P50 7.1 / P90 11.6 / P95 16.9 / P99 30.3ms**・frameOverrunMs P50 −7.9 / P99 +16.3ms・frameCount median 1207/反復。
  所見: P50〜P90 は本棚スクロールより軽いが **P95 以降の尾が跳ねる**（章切替の新章パース＋初回レイアウトのスパイク＝章送り固有の jank 源が分離できた）。
- **完走までの是正3点**（初版からの差し替え。機序の正本＝`docs/knowledge/compose-fresh-content-input-dead-window.md`）:
  1. **シーダーの progress は lastReadAt=0（未接触）で打つ**: >0 だと二層ソート（ADR 0016 層反転）の下層 tier0 へ沈み、
     LazyColumn 仮想化で By.text が本を発見できない（実機 FAIL で実証）。0 なら tier1×addedAt 最大＝本棚先頭に決定論で出る。
  2. **章送りコミットの検知＝旧章タイトルの gone を主信号に**: 次章タイトルの出現は引っ張りプレビューで settle 前から真になる偽陽性
     （waitForIdle も Compose アニメを busy と見なさない）。
  3. **コミット検知後に固定マージン 400ms**: 新章 Content の入力デッドウィンドウ（bodyWidthPx=0 の clamp 窓・a11y から観測不能）を跨ぐ。
     注入方式3種の切替では直らず、「失敗は常に2回目以降」と唯一整合する機序への対処。スワイプは手動実証と同形の shell `input swipe` を採用。
- 副次の実測: 中断した instrument が孤児化させた perfetto/trace_processor 残骸は今回は素の kill で掃除できた
  （既存 knowledge の「kill 不能なことがある」は port 保持ハング状態の話＝状態依存で矛盾しない）。

## ④実機初回計測（2026-07-18 完走 PASS・OPPO PGEM10 / Android 16・N6169DZ 8.5MB/951章・3反復クリーン）

- **Import#extract median 24.1s（23.7〜24.7s・分布タイト）／Extract#engine 22.7s（=94% 支配・設計どおりグリフ抽出が
  ボトルネック）／Extract#exportHtml 289ms／Import#insertDb 1.2ms**（懸念だった suspend 再ディスパッチのスライス分裂は非発現）。
- **完走までの是正2点**:
  1. **完了検知は作品タイトルでなく著者名で**: ProcessingBanner が meta 抽出直後から変換中タイトルを表示するため、
     タイトル待ちはバナーに即マッチ→measureBlock 早終了→capture が Extract#engine 途中で切れ**全メトリクス 0.0**
     （トレース本体に B マーカーだけ残存＝バイナリ検分で実証。初回走行で2反復だけ値が出た逆説＝案内ダイアログが
     偶然バナーを a11y から隠し検知を遅延させていた）。著者名はバナーに出ず DB 登録後のカードにのみ出る＝完了と1:1。
  2. **「バックグラウンド処理について」案内ダイアログの恒久抑止**: 取込開始（isProcessing 立ち上がり×電池最適化
     未除外）ごとにモーダル表示→背後の本棚を a11y から隠し完了検知を堰き止める。ImportBenchReceiver が
     `app_prefs`/`battery_dialog_dismissed=true` を事前書込（「二度と表示しない」ユーザーと同状態・計測対象に無影響）。

## 残（予算較正のみ・ユーザー裁定待ち）

- ③④の実測分布から予算を引いて assert 追加（②と同じ2段階）。候補（未採用）:
  ③ chapter-flip＝frameDurationCpuMs P50≤15/P90≤20/P99≤50ms（P99 実測 30.3ms は本棚より尾が重い＝章切替スパイクを許容する係数）
  ④ pdf-import＝Import#extract ≤ 35s / Extract#engine ≤ 33s（実測×1.4〜1.5。端末温度・ColorOS 外乱込み）。
