# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安: 60行以内）。
> **完了の履歴＝git log（コミットメッセージ）が正本**（ここには書かない）。やること＝`handover.md`。
> それ以外の置き場（知見・ADR・一次情報など）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——
> ここへ再掲すると片方だけ古くなる（2026-07-25 に実際そうなった＝知見の置き場が旧 `task_diary.md` のままだった）。
> **git から機械的に導出できる値（SHA・コミット数・差分行数・コミット表）はここに書かない**——書いた瞬間から陳腐化し、必要なら `git log` でその場で引ける。
> **ブランチ名も同様に書かない**（main へ統合された瞬間に嘘になる。状態＝「実装済み／目視待ち」だけを書く。2026-07-25 の stale-check で実際に踏んだ）。

## 0. 現在の状態

- **ブランチ**: main 一本（作業ブランチ・worktree とも全解消済み。再開時は main から切り直す）。

- **最優先の宿題**: 2026-07-24 UIラウンドの実機で**激しいスタック（もたつき）報告**あり → **まず計測**から着手する（体感の訴えは真の合図・決め打ち修正はしない）。手順と経緯は `handover.md` が正本。

- **デフォルトUI＝「明快K」**（`Skin.MEIKAI_K` が既定。既存の明示保存 D/M/P/J/C は不変・装いの間で相互選択可）。構造＝
  〈ラベル付き恒常ボトムナビ3タブ（本棚／さがす／設定）＋全画面の明示タイトル＋設定画面＋本棚グリッド（キャプション行に可視⋮）＋
  さがす（検索第一＋公式サイトへの逃げ道）＋目次（現在地チップ／ここから再開／既読✓）〉。読書画面はD構造を温存。
  タブは Pager 化（横スワイプ・`TabPagerHost`＝スロット契約は ADR 0022 追記が正本）。意匠の正本＝`docs/design-candidates/skins/*-K.html`、
  設計の一次情報＝`.claude/plans/` の `default-ui-clarity-K-2026-07-23.md`・`k-shape-propagation-2026-07-23.md`・`ui-density-swipe-round-2026-07-24.md`。
  **状態: main 統合済み・実機目視待ち（全スキン掃引）**。

- **Room v21**: `sourceUrl`/`sourceSite`（Web取込元＝再取得を同じ抽出器へ回す土台。PDF由来は NULL）。
  v20＝`books.sourceUri` 永続化＋本削除時に取込元PDFも削除（削除ダイアログの opt-in・既定OFF）。v19＝栞書影の個体差。
  ⚠️ **旧APKへの逆走は禁止**（migration N→N-1 が無くクラッシュ＝古い→新しいの一方向のみ）。変更手順＝`/db-migration`。

- **実機**: OPPO PGEM10 `192.168.1.210:5555`（切れたら `adb-bridge`）・v21 APK 導入済み。作法＝`/device-verify`（adb 前にユーザーへ一度確認）。

- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**（Chaquopy/Python は 2026-07-05 に完全撤去・復旧は git 履歴から）。
  本文解析は文書ごとの自動検出（`DetectedRules.detect`＝サイズ／列ピッチ／ページ番号座標を実測。検出不能時のみ `ParserRules` 定数へフォールバック）。
  精度回帰＝JVM `JvmGoldenRegressionTest`（golden **4本**を `testDebugUnitTest` で常時検証）＋実機 `PdfExtractorDeviceSpikeTest`（assets 手動配置時のみ）。

- **機能の現在地**（構成の詳細は `/architecture` とコードが正本）: PDF抽出＋ふりがな読書（テーマ／没入クローム／左右スワイプ章送り〔引っ張りプレビュー〕／読書位置・読了の永続化）
  ／**縦書きモード**（連続横スクロール×自前Compose組版・ADR 0020）／なろう発見・検索（ADR 0007・規約線 0010・取込導線 0011/0013）
  ／Web読書位置の記録と再開（ADR 0012）／新着通知（既定OFF・オプトイン）／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）
  ／着せ替え＝装いの間（スキン D/M/P/J/C/K・ADR 0021・0022）／高負荷スカイモード（星図M・debug 限定トグル・ADR 0023。release は常にOFF）。

- **汎用Web小説DL基盤**: `scrape/` のサイトアダプタ抽象＋規約3値ゲート（Supported／Blocked／Unsupported）。取込結果は PDF 蔵書とバイト同契約へ合流。
  対応＝**カクヨム**（JSON 系＝専用アダプタ）＋**暁**（`scrape/generic/` の SiteProfile 表駆動）。なろうグループ・アルファポリス・Pixiv・野いちご・ベリーズカフェは Blocked（公式へ送客）、ハーメルンは保留。
  **対応面の拡大はいったん打ち止め**（表駆動の新規候補ゼロ・ヒューリスティック案は不採用裁定）。実行時の構造破損監視（ScrapeIntegrity＋fixture ゴールデン）と
  per-host Crawl-delay ／429・503 の Full Jitter バックオフを実装済み。裁定の正本＝ADR 0024、設計＝`.claude/plans/scraping-foundation-design-2026-07-20.md`・`generic-adapter-design-2026-07-23.md`。

- **性能・リリース基盤**: Macrobenchmark（起動／本棚スクロール／章送り／大PDF取込の予算を P90/P99 で assert・設計と全実測＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`）。
  release は R8 収縮（minify＋shrinkResources）で出荷し、収縮起因の欠落が無いことは実機回帰で確認済み。

- **ゲート**（数値は測り直せば変わるので書かない＝疑わしければその場で回す）: `testDebugUnitTest` 緑／`tools/check_design_tokens.py` NG=0
  （＋余白スケール7段 {4,8,12,16,24,32,40} の Spacing lint＝ADR0014 §C）／`:app:lintDebug` errors=0（warnings は非ブロック）。

- **既知バグ: なし**。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
