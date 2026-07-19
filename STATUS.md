# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。判断・Why-not＝`docs/decisions/`（ADR）。
> 腐りにくい知見＝`task_diary.md`・`docs/patterns/`。一次情報の細部＝`.claude/plans/`。やること＝`handover.md`。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。

## 0. 現在の状態

- **UX/Design 全層監査**: 指摘（Critical 3/Major 24/Minor 29）＋派生改修（CTA一貫性=案A／没入時黒帯明滅=window背景をテーマ色へ再定義／複数選択削除=案B下端バー＋変種B「キャンセル」）まで実装・実機検証済み（ui/polish は main 統合・撤去済み）。残＝発見帯 collapse 退避アニメ体感の追い込み（deferred）・第三者人間テスト便・監査派生 backlog＝`handover.md` ★節が正本。監査の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A/§B）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）。
- **直近の統合（このブランチ束ね）**: `reading/vertical-p4`（縦書き章送り P4・実機体感確認済み）・`build/r8-shrink`（release の R8 収縮＝minify+shrinkResources・APK 20.3→7.8MB）・`perf/macrobenchmark`（性能回帰基盤＝起動/本棚スクロール/章送り/大PDF取込の予算 assert・実機実証済み。設計と全実測＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`＋`docs/knowledge/coloros-*`／`macrobenchmark-frametiming-scroll-pitfalls.md`）・`hooks/fabrication-detector`（実行捏造検知器 Tier E3「先行実行フレーミング」）・`ui/skin-framework`（UIスキン機構＝ADR 0021＋0022〔画面構造の二層化〕。M星図/Pカートリッジ/Jポータルの3スキン×全5画面〔本棚/読書/目次/設定/発見〕を Compose 実装。**C3 実機スモーク完了＝J 全5画面掃引＋M/P 本棚を release R8 下で PASS（M/P 全画面は既存 PASS・C は色層）・5スキンとも shrinkResources による資産欠落なし**）を main へ統合。縦書き本体（P0〜P3・P5・P2.5）は統合済み＝縦書きはユーザー到達可能（ADR 0020〔連続横スクロール×自前Compose組版〕）。**R8 リリース収縮の実機回帰完了＝4重点経路（Moshi なろう検索/PDFBox 取込抽出/WorkManager クラス名復元/enum テーマ SEPIA 永続）全 PASS・収縮起因クラッシュ無し**（release APK を debug 署名し install -r で実蔵書DB保持のまま検証。PDFBox は取込時に日本語タイトルを抽出＝CID/CMap 経路通過を確認）。
- **ゲート（統合後 ext4 worktree 実測・2026-07-18・全緑）**: `testDebugUnitTest` **752件**（失敗0・5ブランチのテスト群を統合）／`tools/check_design_tokens.py` OK=192/NG=0（M/P/J 3スキンの期待表を含む・＋Spacing lint: 余白スケール7段 {4,8,12,16,24,32,40}＝ADR0014 §C・NG=0・WARN=0）／`:app:lintDebug` **0 errors・31 warnings**（+3 は新規スキン/bench ファイル由来の非ブロック警告＝未使用 param/Windows 非互換名など・ModifierParameter×3 と UsableSpace×2 は従前どおり意図的）。R8 リリース収縮・C3 実機スモークは実機検証完了（上記参照・OPPO PGEM10）。
- **Room v19**（栞書影の個体差 `shioriTipIndex`/`shioriLenFrac` 永続化＝取込時1回抽選〔先端**174種**から〕・既存行NULL→title由来へフォールバック）。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 不在でクラッシュ＝古い→新しいの一方向のみ）。no-op 再スタンプの機序＝`task_diary.md` #39 追補。
- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・**v19 APK 導入済み**（v18→v19 migration 通過を実測・蔵書生存・既存書影不変を確認済み）。検証ワークフロー＝memory `workflow-autonomous-device-verification`／`workflow-notify-each-step-visual-check`。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 Phase 5 で完全撤去。復旧は git 履歴から）。**本文解析は文書ごと自動検出（`DetectedRules.detect`＝サイズ/列ピッチ/ページ番号座標を実測・検出不能時は ParserRules 定数へフォールバック）**。精度回帰ゲート＝**JVM `JvmGoldenRegressionTest`（golden3本を `testDebugUnitTest` で常時検証・約10秒）**＋実機 `PdfExtractorDeviceSpikeTest`（同一合格ライン・assets 手動配置時のみ）。
- **機能の現在地**（構成の詳細は `/architecture` スキルとコードが正本）: PDF抽出＋ふりがな読書（テーマ3種・没入クローム＝タップトグル・左右スワイプ章送り〔引っ張りプレビュー＋章キャッシュ〕・読書位置/読了永続化）／なろう発見・検索（ADR 0007・規約線＝0010・PDF取込導線＝0011/0013）／Web読書位置記録・続きから再開（ADR 0012）／新着通知（既定OFFオプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）。意匠の正本構造＝ADR 0005/0014。
- **高負荷スカイモード（星図M・ADR 0023 試作）**: debug ビルド限定トグル（本棚⋮開発節）で ON。チャンク式無限プロシージャル
  の粒天の川＋天体系（流星/衛星/彗星/BH）＋奥行き層（空気遠近/暗黒雲/帯2層）＋検分ボタン6種。release は常に OFF・通常モード
  厳密不変（DeepSkyM の durationScale は既定1f恒等）。jank 2.56%（ON時実測）。裁定履歴と残ロードマップ（v8/v9・D展開）＝
  `.claude/plans/richness-expansion-round-2026-07-19.md`。
- **既知バグ: なし**（単話の嘘見出し問題は 2026-07-16 修正済み＝題名マーカー0件時は作品タイトルを単一章名へ流用・golden 第4本 N5368ML で恒久回帰）。

- **汎用Web小説DL基盤（最優先B・branch `feat/scraping-prep`）**: `scrape/` サイトアダプタ抽象（`NovelSiteAdapter`＋
  `SiteAdapterRegistry` の規約3値ゲート＝Supported/Blocked/Unsupported・なろうグループは Blocked で公式送り）＋
  **カクヨム抽出器**（TOC＝`__NEXT_DATA__` Apollo ストア／本文＝`.widget-episodeBody`・ルビ→中間記法 `|base《ruby》`＝
  既存 ChapterProcessor/HtmlExporter に合流し PDF 蔵書とバイト同契約）＋**fixture ゴールデン**（実HTMLスナップショットで
  構造破損を `testDebugUnitTest` 常時検知＝破損監視の核）が着地。**P3 パイプライン接続も着地**＝Room **v21**
  （v20 は `feat/delete-source-pdf` 先着＝19_20 複製でパス接続・sourceUrl/sourceSite 独立2列）＋`addWebBook`
  （アダプタ→既存HTML契約合流・sourceUrl 重複ガード・pending_jobs 不使用）＋取込導線（ACTION_SEND 全サイト受け／
  ACTION_VIEW は対応ホスト限定・Blocked は公式送りの逃げ道）。**P4 実行時破損監視も着地**＝取込時の構造疑い検知
  （ScrapeIntegrity 3条件・床値20字）→「公式サイトで読む」アクション付きスナックバー＋debug ヘルスボード
  （本棚⋮開発節・release 到達不能）。残＝P5 発見層refactor・P6 後始末。一次情報＝
  `.claude/plans/scraping-foundation-design-2026-07-20.md`／残タスクと裁定＝`handover.md`「汎用DL基盤 実装トラック」。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
