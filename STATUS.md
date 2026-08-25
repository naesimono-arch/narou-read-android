# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安60行・**上限 6,000字＝現在値でなくなった記述を消す合図**。縮めて収めない）。
> **完了の履歴＝git log が正本**（ここには書かない）／やること＝`handover.md`／人間待ち＝`awaiting-human.md`。
> それ以外（知見・ADR・一次情報）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——再掲すると片方だけ古くなる。
> **git から導出できる値（SHA・コミット数・差分行数）とブランチ名は書かない**——書いた瞬間から嘘になる。

## 0. 現在の状態

- **公開準備（Google Play）**: main 統合済み。targetSdk/compileSdk 36・リリース署名＋AAB 経路・R8 実機回帰・In-App Review 実装まで済。
  プライバシーポリシー/Data safety 下書き済（裁定済み＝収集なし申告・GitHub Pages 公開）・採番規約 ADR 0025 採択（1.0.0）。
  **ブランド名＝`Yosari` 確定**。残＝applicationId 変更（`app.yosari.reader`・**§1 ツアー後**）／ポリシー公開＋鍵バックアップ（ユーザー作業）
  ／**ストア素材＝意匠の本線は裁定済み**（アイコン「A 栞書影」・FG「A 夜の帯」）で残るは PNG 書き出しとスクショ6枚＝**制作は積み**。

- **デフォルトUI＝「明快K」**（`Skin.MEIKAI_K` が既定。既存の明示保存 D/M/P/J/C は不変・装いの間で相互選択可）。構造＝
  〈ラベル付き恒常ボトムナビ3タブ（本棚／さがす／設定）＋全画面の明示タイトル＋設定画面＋本棚グリッド（キャプション行に可視⋮）＋
  さがす（検索第一＋公式サイトへの逃げ道）＋目次（現在地チップ／ここから再開／既読✓）〉。読書画面はD構造を温存。
  タブは Pager 化（横スワイプ・`TabPagerHost`＝スロット契約は ADR 0022 追記が正本）。意匠の正本＝`docs/design-candidates/skins/*-K.html`、
  設計の一次情報＝`.claude/plans/` の `default-ui-clarity-K-2026-07-23.md`・`k-shape-propagation-2026-07-23.md`・`ui-density-swipe-round-2026-07-24.md`。
  本棚は**フィルタ行のスクロール端フェード（強度「中」）**と**空棚で引っ込む FAB**を実装済み（ADR 0036/0037・D へも伝播）。
  作品詳細は**あらすじ案B**（副アクション横一列・バー注記なし）＋**書影は栞書影で本棚と1ピクセル同一**（案2-c・淡地は作品色から
  彩度14%/明度93%の固定窓へ圧縮＝どの色相でも墨は 13.9:1 以上で版面の明暗が動かない）。
  **状態: main 統合済み・実機目視待ち（全スキン掃引）**。
  **横向きは Rail＋T1 横一列を実装済み**（2026-08-25・ADR 0034＝固定トップ 120.5→56.0dp）。**状態: 実機目視待ち**。

- **Room v21**: `sourceUrl`/`sourceSite`（Web取込元＝再取得を同じ抽出器へ回す土台。PDF由来は NULL）。
  v20＝`books.sourceUri` 永続化＋本削除時に取込元PDFも削除（削除ダイアログの opt-in・既定OFF）。v19＝栞書影の個体差。
  ⚠️ **旧APKへの逆走は禁止**（migration N→N-1 が無くクラッシュ＝古い→新しいの一方向のみ）。変更手順＝`/db-migration`。

- **実機**: OPPO PGEM10（IP は DHCP で変動＝ハードコードせず `adb-bridge` で張り直す）・v21 APK 導入済み。作法＝`/device-verify`。
  **実蔵書7冊・全冊とも本文健在**（2026-08-17 に id・progress とも開始時バックアップと完全一致を確認＝無傷。**絶対に消さない**）。
  **検証用の残置物**＝捨て本2冊（`6c726cfe` カクヨム26話・`cf4ee71b` PDF18章。どちらも本文は復旧済み＝**欠落させ直せば欠落系を踏める**）／
  Web カード `N7415ML`。books は 7＋2＝9冊。
  ⚠️ **端末の APK は 2026-08-19 投入の debug＝以後の意匠・バグ修正はまだ端末に無い**（`awaiting-human.md` §1 の前置き）。
  ベンチは `.benchmark`/`.macrobenchmark` とも導入済み（⚠️ **新規**パッケージの投入は ColorOS が止める＝`/device-verify` §4）。
  ⚠️ 同一 WiFi 上に**第三者端末（Huawei P30）が居る**＝操作前に model を確認（機序＝memory `adb-bridge-stale-tcp-holds-wrong-device`・
  **他人の端末なので読み取り以外はしない**＝`docs/knowledge/emui-p30-jank-log-collection.md`）。
  **残るは実機で見ることだけ**＝`awaiting-human.md` §1（ローカルゲートは全て GREEN）。

- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**。
  本文解析は文書ごとの自動検出（`DetectedRules.detect`＝サイズ／列ピッチ／ページ番号座標を実測。検出不能時のみ `ParserRules` 定数へフォールバック）。
  精度回帰＝JVM `JvmGoldenRegressionTest`（golden **4本**を `testDebugUnitTest` で常時検証）＋実機 `PdfExtractorDeviceSpikeTest`（assets 手動配置時のみ）。
  **ページ範囲並列を採用済み**（K は端末のヒープから動的＝低ヒープ機は単一経路のまま。正本＝**ADR 0035**）。

- **機能の現在地**（構成の詳細は `/architecture` とコードが正本）: PDF抽出＋ふりがな読書（テーマ／没入クローム／左右スワイプ章送り〔引っ張りプレビュー〕／読書位置・読了の永続化）
  ／**縦書きモード**（連続横スクロール×自前Compose組版・ADR 0020）／なろう発見・検索（ADR 0007・規約線 0010・取込導線 0011/0013。
  **検索の既定範囲は4項目すべて有効**＝placeholder の約束どおり）／Web読書位置の記録と再開（ADR 0012）／新着通知（既定OFF・オプトイン）
  ／層別 Auto Backup（ADR 0015）／本棚＝栞書影・読書状態フィルタ・二層ソート（ADR 0016）
  ／着せ替え＝装いの間（スキン D/M/P/J/C/K・ADR 0021・0022。**入口は設定タブ「きせかえ」**・本棚の発見/装い導線は撤去）／高負荷スカイモード（星図M・debug 限定トグル・ADR 0023。release は常にOFF）
  ／In-App Review（初回読了トリガ・実表示確認は内部トラック待ち）／蔵書復旧導線（本文欠落バッジ＋起動時一括検出→再取込・3操作は縦3段）
  ／U1 新着チェックは Web 蔵書も対象（読了本のみ再フェッチ）／読書の表示設定はライブプレビュー（押下中一行残し・シートは全高で開き主役のスライダー3本を初手で見せる）／push 遷移スケルトン。
  ⚠️ 蔵書復旧の現在値: **案X 実装済み**（SAF フォルダ1回指定→ツリー走査→contentSha256 照合→復元・ツリー権限永続化＝2度目以降は無操作）。
  **なろうPDF 由来本（sourceUri 無し）は cache 内 PDF の直接再変換（AutoCachePdf）でも自動復旧**し、削除時は同 ncode 最後の1冊で cache を相乗り削除＋起動時の孤児掃除。
  手元にも cache にも PDF が無い本だけは救えない（前提＝`domain/PdfFolderScan.kt` ヘッダ）。**実機で実復旧まで確認済み**。

- **汎用Web小説DL基盤**: `scrape/` のサイトアダプタ抽象＋規約3値ゲート（Supported／Blocked／Unsupported）。取込結果は PDF 蔵書とバイト同契約へ合流。
  対応＝**カクヨム**（JSON 系＝専用アダプタ）＋**暁**（`scrape/generic/` の SiteProfile 表駆動）。なろうグループ・アルファポリス・Pixiv・野いちご・ベリーズカフェは Blocked（公式へ送客）、ハーメルンは保留。
  **対応面の拡大はいったん打ち止め**（表駆動の新規候補ゼロ・ヒューリスティック案は不採用裁定）。実行時の構造破損監視（ScrapeIntegrity＋fixture ゴールデン）と
  per-host Crawl-delay ／429・503 の Full Jitter バックオフを実装済み。裁定の正本＝ADR 0024、設計＝`.claude/plans/scraping-foundation-design-2026-07-20.md`・`generic-adapter-design-2026-07-23.md`。

- **性能・リリース基盤**: Macrobenchmark は**6予算**（起動／本棚スクロール／タブ遷移／章送り〔縦書き軸つき〕／大PDF取込／**本棚→目次 push**）を
  P90/P99 で assert。設計と全実測＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md`。
  シーダーは書字方向を固定するようになり **tab-swipe の窓は測れる**（縦書き端末で着地判定が空振りしていた真因は除去済み）。
  release は R8 収縮（minify＋shrinkResources）で出荷し、収縮起因の欠落が無いことは実機回帰で確認済み。

- **端末内診断＝`diagnostics/`（外部送信ゼロ）**: クラッシュ（既定ハンドラの前段に挟んで記録し必ず委譲）／異常終了の推定
  （前面セッションの開閉フラグ。`ApplicationExitInfo` は API30+ で日常検証機の Huawei P30＝API29 では使えないための代替・
  停電/再起動が混ざり過大に出る限界つき）／フレーム落ち（JankStats の画面別ヒストグラム・前面のみ収集）。
  保管＝`filesDir/diagnostics/`（events 最大30件・jank.txt 256KB 上限）、回収＝debug なら `adb shell run-as com.novelreader`。
  **書き出しUIは未実装**（UI追加はモック先行が要るため別ラウンド）。

- **ゲート**（数値は測り直せば変わるので書かない＝疑わしければその場で回す）: `testDebugUnitTest` 緑／`tools/check_design_tokens.py` NG=0
  （＋余白スケール7段 {4,8,12,16,24,32,40} の Spacing lint＝ADR0014 §C）／`:app:lintDebug` errors=0（warnings は非ブロック）。
  push 時は GitHub Actions（`.github/workflows/ci.yml`）が上記3つ＋**ktlint**＋**golden 画像照合**（`verifyRoborazziDebug`＝単体テストと同じ1パス）
  ＋**androidTest のコンパイル**＋**release R8 ビルド**の計6ゲートを自動実行（実機必須の androidTest 実行と macrobenchmark は対象外＝YAML コメントに理由）。
  **CI は全ゲート緑**。golden の破綻を画素から見る走査3本（`tools/check_golden_*.py`）と `GoldenCoverageTest`（網羅と孤児の突合）も**ブロッキング**。
  ⚠️ **Gradle は `tools/gwlock.sh <task>` 経由で回す**（同一ツリーの並列実行が出す偽の赤をツリー単位ロックで潰す。作法は `/build`）。
  ⚠️ **ゲートには構造的限界がある**（golden は退行しか止めない・孤児 golden は検出経路が無い＝`docs/knowledge/golden-record-bakes-in-regressions.md`／
  ドキュメントは名指しの実在だけが機械照合され、記述内容の食い違いは誰も見ていない＝`/stale-check` の腐敗検知3種が部分的に補う）。
  どのバグ型がどのゲートに守られているか（と**どこが無防備か**）の一覧＝`docs/known-bugs-registry.md`。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
