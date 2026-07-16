# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。判断・Why-not＝`docs/decisions/`（ADR）。
> 腐りにくい知見＝`task_diary.md`・`docs/patterns/`。一次情報の細部＝`.claude/plans/`。やること＝`handover.md`。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。

## 0. 現在の状態

- **ブランチ `ui/polish`**: UX/Design 全層監査バッチ（Critical 3/Major 24/Minor 29）消化済み・**main 未統合**。残タスク（C①案A＝読書ギア下端集約は翻訳完了・実機OK／C②＝発見帯 collapse は「完全退避」で再設計・翻訳完了・実機OK＝帯は restyle せず shrink+fade で高さ0へ畳み退避／フィルタ sticky／退避アニメ体感の追い込みのみ deferred＝残1／実機検証6件は 2026-07-16 消化済み〔5 PASS・TalkBack FAIL は同日是正〕／残＝人間テスト送り・意匠2件の裁定待ち（CTA一貫性・複数選択削除＝対比モックを `mockview` で）・main 統合）＝`handover.md` ★節が正本。監査の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A/§B）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）。
- **ゲート（2026-07-16 時点・全緑）**: `testDebugUnitTest` **499件**（失敗0・JVMゴールデン回帰3件＋DetectedRules 8件を含む）／`tools/check_design_tokens.py` OK=137/NG=0（＋Spacing lint: 余白スケール7段 {4,8,12,16,24,32,40}＝ADR0014 §C・NG=0・WARN=0＝全Compose再翻訳完了で GRACE_FILES 空・以後 spacing直書きは NG）／`:app:lintDebug` 0 errors・28 warnings（ModifierParameter×3 は Compose 規約準拠〔modifier を先頭 optional へ〕で解消済み・挙動不変／残る非ブロックは UsableSpace×2＝抽出前の空き容量チェックは保守的 `usableSpace` を意図的に採用〔`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなるため〕）。
- **Room v19**（栞書影の個体差 `shioriTipIndex`/`shioriLenFrac` 永続化＝取込時1回抽選〔先端**174種**から〕・既存行NULL→title由来へフォールバック）。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 不在でクラッシュ＝古い→新しいの一方向のみ）。no-op 再スタンプの機序＝`task_diary.md` #39 追補。
- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・**v19 APK 導入済み**（v18→v19 migration 通過を実測・蔵書生存・既存書影不変を確認済み）。検証ワークフロー＝memory `workflow-autonomous-device-verification`／`workflow-notify-each-step-visual-check`。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 Phase 5 で完全撤去。復旧は git 履歴から）。**本文解析は文書ごと自動検出（`DetectedRules.detect`＝サイズ/列ピッチ/ページ番号座標を実測・検出不能時は ParserRules 定数へフォールバック）**。精度回帰ゲート＝**JVM `JvmGoldenRegressionTest`（golden3本を `testDebugUnitTest` で常時検証・約10秒）**＋実機 `PdfExtractorDeviceSpikeTest`（同一合格ライン・assets 手動配置時のみ）。
- **機能の現在地**（構成の詳細は `/architecture` スキルとコードが正本）: PDF抽出＋ふりがな読書（テーマ3種・没入クローム・読書位置/読了永続化）／なろう発見・検索（ADR 0007・規約線＝0010・PDF取込導線＝0011/0013）／Web読書位置記録・続きから再開（ADR 0012）／新着通知（既定OFFオプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）。意匠の正本構造＝ADR 0005/0014。
- **既知バグ: 1件**——単話（章見出しグリフ0件）作品の変換で全本文が既定章「作品情報・プロローグ」へ流れ章題が出ない（真因確定済み・検体 `sample_pdfs/N5368ML.pdf`・修正方針裁定済み＝`handover.md`）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
