# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。判断・Why-not＝`docs/decisions/`（ADR）。
> 腐りにくい知見＝`task_diary.md`・`docs/patterns/`。一次情報の細部＝`.claude/plans/`。やること＝`handover.md`。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。

## 0. 現在の状態

- **UX/Design 全層監査**: 指摘（Critical 3/Major 24/Minor 29）＋派生改修（CTA一貫性=案A／没入時黒帯明滅=window背景をテーマ色へ再定義／複数選択削除=案B下端バー＋変種B「キャンセル」）まで実装・実機検証済み（ui/polish は main 統合・撤去済み）。残＝発見帯 collapse 退避アニメ体感の追い込み（deferred）・第三者人間テスト便・監査派生 backlog＝`handover.md` ★節が正本。監査の一次情報＝`.claude/plans/ux-design-full-audit-2026-07-12.md`（§A/§B）＋`.claude/plans/ux-audit-batch-execution-20260712.md`（実行記録）。
- **進行中ブランチ**: `reading/vertical-mode`＝縦書きリーディングモード（方針確定＝ADR 0020〔連続横スクロール×自前Compose組版〕・フェーズ/スパイク＝`.claude/plans/vertical-reading-mode.md`・実装待ち。worktree `~/wt/reading-vertical-mode`）／`ui/skin-framework`＝UIスキン機構（機構の裁定＝ADR 0021＋0022〔画面構造の二層化〕。**M星図・Pカートリッジ・Jポータルの3スキン×全5画面（本棚/読書/目次/設定/発見）を Compose 実装済み**＝M深空リッチ化（R1・粒の天の川/視差/流れ星）・Pテーマ3変種・遊び心6点（P1 CLEAR‼/P2 現像/P3 炎=データ源待ち非表示/J1 開く扉/J2 敷居光/J3 時刻大気）・J扉ambientパレット・装いの間snap修正まで作業ツリーに landing 済み。実機検証＝M/P全画面・J本棚は PASS 済み・**C3最終総合スモーク（J残り画面の初実機ほか）はユーザー指示で実機一時停止中＝再開待ち**。残＝C3スモーク＋人間目視の関門＋handover の改善案バックログ。worktree `~/wt/ui-skin-framework`）。
- **ゲート（2026-07-17 実測・全緑）**: `testDebugUnitTest` 全緑（630件規模＝M/P/J スキン各画面・遊び心・パレット・snap のテスト群を含む。正確な件数は実行出力が正）／`tools/check_design_tokens.py` OK=192/NG=0（M/P/J 3スキンの期待表を含む）（＋Spacing lint: 余白スケール7段 {4,8,12,16,24,32,40}＝ADR0014 §C・NG=0・WARN=0＝全Compose再翻訳完了で GRACE_FILES 空・以後 spacing直書きは NG）／`:app:lintDebug` 0 errors・28 warnings（ModifierParameter×3 は Compose 規約準拠〔modifier を先頭 optional へ〕で解消済み・挙動不変／残る非ブロックは UsableSpace×2＝抽出前の空き容量チェックは保守的 `usableSpace` を意図的に採用〔`getAllocatableBytes` は消去可能キャッシュ込みの楽観値で事前チェックが甘くなるため〕）。
- **Room v19**（栞書影の個体差 `shioriTipIndex`/`shioriLenFrac` 永続化＝取込時1回抽選〔先端**174種**から〕・既存行NULL→title由来へフォールバック）。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 不在でクラッシュ＝古い→新しいの一方向のみ）。no-op 再スタンプの機序＝`task_diary.md` #39 追補。
- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・**v19 APK 導入済み**（v18→v19 migration 通過を実測・蔵書生存・既存書影不変を確認済み）。検証ワークフロー＝memory `workflow-autonomous-device-verification`／`workflow-notify-each-step-visual-check`。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 Phase 5 で完全撤去。復旧は git 履歴から）。**本文解析は文書ごと自動検出（`DetectedRules.detect`＝サイズ/列ピッチ/ページ番号座標を実測・検出不能時は ParserRules 定数へフォールバック）**。精度回帰ゲート＝**JVM `JvmGoldenRegressionTest`（golden3本を `testDebugUnitTest` で常時検証・約10秒）**＋実機 `PdfExtractorDeviceSpikeTest`（同一合格ライン・assets 手動配置時のみ）。
- **機能の現在地**（構成の詳細は `/architecture` スキルとコードが正本）: PDF抽出＋ふりがな読書（テーマ3種・没入クローム＝タップトグル・左右スワイプ章送り〔引っ張りプレビュー＋章キャッシュ〕・読書位置/読了永続化）／なろう発見・検索（ADR 0007・規約線＝0010・PDF取込導線＝0011/0013）／Web読書位置記録・続きから再開（ADR 0012）／新着通知（既定OFFオプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）。意匠の正本構造＝ADR 0005/0014。
- **既知バグ: なし**（単話の嘘見出し問題は 2026-07-16 修正済み＝題名マーカー0件時は作品タイトルを単一章名へ流用・golden 第4本 N5368ML で恒久回帰）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
