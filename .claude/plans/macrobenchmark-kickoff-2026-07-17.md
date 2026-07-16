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

## 残フェーズ（優先順）

1. **起動予算 assert の実装**（上の予算候補で instrumentation 引数ゲート）＋ベンチ実行スクリプト化（除細動ループ同梱）
2. **10倍蔵書シーダー＋本棚スクロール jank**: `app/src/benchmark/` ソースセットに投入手段（BroadcastReceiver 等）を新設し `FrameTimingMetric` で LazyVerticalGrid/LazyColumn スクロールを計測。BookEntity 描画に必要な最小フェイクデータ（htmlDirPath/書影）の設計が要る
3. **長時間章送り jank**: NativeReadingScreen のスワイプ章送りを uiautomator で連続駆動・漸進劣化を P90/P99 で観測（シード本＝実HTML章が要る→②のシーダーを拡張）
4. **大PDF取込**: `TraceSectionMetric`（抽出パイプラインに trace 区間追加）＋大PDF アセットの置き場設計（git に大物を入れない）
