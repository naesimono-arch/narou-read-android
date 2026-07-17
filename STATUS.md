# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。判断・Why-not＝`docs/decisions/`（ADR）。
> 腐りにくい知見＝`task_diary.md`・`docs/patterns/`。一次情報の細部＝`.claude/plans/`。やること＝`handover.md`。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。

## 0. 現在の状態

- **UX/Design 全層監査**: 指摘（Critical 3/Major 24/Minor 29）＋派生改修（CTA一貫性=案A／没入時黒帯明滅=window背景をテーマ色へ再定義／複数選択削除=案B下端バー＋変種B「キャンセル」）まで実装・実機検証済み（ui/polish は main 統合・撤去済み）。残＝発見帯 collapse 退避アニメ体感の追い込み（deferred）・第三者人間テスト便・監査派生 backlog＝`handover.md` ★節が正本。監査の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A/§B）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）。
- **進行中ブランチ**: `reading/vertical-mode`＝縦書きリーディングモード（方針確定＝ADR 0020〔連続横スクロール×自前Compose組版〕・フェーズ/スパイク＝`.claude/plans/vertical-reading-mode.md`・実装待ち。worktree `~/wt/reading-vertical-mode`）／`perf/macrobenchmark`＝性能回帰基盤（コールド起動の**実機初回計測＝median 252.9ms**〔OPPO/Android16・5反復〕＋**起動予算 assert 実装済み**〔median≤350/max≤500ms・`-e enableBudgetAssert true` ゲート・実行スクリプト `tools/run_macrobenchmark.sh`＝除細動ループ同梱・PASS/FAIL 両経路を実機実証済み〕・**②10倍蔵書シーダー＋本棚スクロール jank＝実機完走 PASS・スクロール予算 assert 実装済み**〔100冊・frameDurationCpuMs は健康＝list P50 8.2〜8.7ms・grid P50 10.2〜10.3ms（P99 は走行間で 16.6〜26.4ms と揺れ大）。予算 `ScrollBudget`＝P50≤15/P90≤20/P99≤30ms 両モード共通・`--scenario shelf-scroll --assert`・PASS/FAIL 両経路を実機実証済み。ColorOS の broadcast 沈黙不達と COLD 仕様の是正3点＝`docs/knowledge/coloros-broadcast-silent-drop.md`ほか〕・**③長時間章送り jank＋④大PDF取込（TraceSectionMetric）＝実機完走 PASS・初回実測済み**〔③=左スワイプ30回×5反復・frameDurationCpuMs **P50 7.1 / P90 11.6 / P99 30.3ms**（P95 以降の尾＝章切替スパイクを分離）・④=N6169DZ 8.5MB/951章×3反復・**Import#extract median 24.1s／Extract#engine 22.7s（94% 支配）**／exportHtml 289ms。完走への是正＝シーダー progress は lastReadAt=0（二層ソート層落ち回避）・章送りコミット検知＝旧章 gone＋400ms マージン（入力デッドウィンドウ＝`docs/knowledge/compose-fresh-content-input-dead-window.md`）・取込完了検知＝著者名（バナーのタイトル早発火回避）・案内ダイアログは bench レシーバが `battery_dialog_dismissed` 事前書込。予算 assert は未実装＝実測から較正して追加（ユーザー裁定待ち）〕。ColorOS 3重の壁の回避作法＝`docs/knowledge/coloros-uiautomation-shell-pipe-eof-hang.md`・設計と実測＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`。worktree `~/wt/perf-macrobenchmark`）。
- **ゲート（2026-07-17 実測・全緑）**: `testDebugUnitTest` **508件**（失敗0・JVMゴールデン回帰4件＋DetectedRules 8件を含む）／`tools/check_design_tokens.py` OK=137/NG=0（＋Spacing lint: 余白スケール7段 {4,8,12,16,24,32,40}＝ADR0014 §C・NG=0・WARN=0＝全Compose再翻訳完了で GRACE_FILES 空・以後 spacing直書きは NG）／`:app:lintDebug` 0 errors・28 warnings（ModifierParameter×3 は Compose 規約準拠〔modifier を先頭 optional へ〕で解消済み・挙動不変／残る非ブロックは UsableSpace×2＝抽出前の空き容量チェックは保守的 `usableSpace` を意図的に採用〔`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなるため〕）。
- **Room v19**（栞書影の個体差 `shioriTipIndex`/`shioriLenFrac` 永続化＝取込時1回抽選〔先端**174種**から〕・既存行NULL→title由来へフォールバック）。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 不在でクラッシュ＝古い→新しいの一方向のみ）。no-op 再スタンプの機序＝`task_diary.md` #39 追補。
- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・**v19 APK 導入済み**（v18→v19 migration 通過を実測・蔵書生存・既存書影不変を確認済み）。検証ワークフロー＝memory `workflow-autonomous-device-verification`／`workflow-notify-each-step-visual-check`。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 Phase 5 で完全撤去。復旧は git 履歴から）。**本文解析は文書ごと自動検出（`DetectedRules.detect`＝サイズ/列ピッチ/ページ番号座標を実測・検出不能時は ParserRules 定数へフォールバック）**。精度回帰ゲート＝**JVM `JvmGoldenRegressionTest`（golden3本を `testDebugUnitTest` で常時検証・約10秒）**＋実機 `PdfExtractorDeviceSpikeTest`（同一合格ライン・assets 手動配置時のみ）。
- **機能の現在地**（構成の詳細は `/architecture` スキルとコードが正本）: PDF抽出＋ふりがな読書（テーマ3種・没入クローム＝タップトグル・左右スワイプ章送り〔引っ張りプレビュー＋章キャッシュ〕・読書位置/読了永続化）／なろう発見・検索（ADR 0007・規約線＝0010・PDF取込導線＝0011/0013）／Web読書位置記録・続きから再開（ADR 0012）／新着通知（既定OFFオプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）。意匠の正本構造＝ADR 0005/0014。
- **既知バグ: なし**（単話の嘘見出し問題は 2026-07-16 修正済み＝題名マーカー0件時は作品タイトルを単一章名へ流用・golden 第4本 N5368ML で恒久回帰）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
