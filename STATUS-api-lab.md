# STATUS — `api-lab` ブランチ現況台帳

> **このブランチ専用の現況正本**（`STATUS.md` は main 正本のため、api-lab の作業状態はここに記録する）。
> ブランチをまたぐ不変知見は auto-memory、main 全体の現況は `STATUS.md`、腐りにくい外部事実は `task_diary.md`。
> 一次情報（設計判断の全文・未決事項）は plan `~/.claude/plans/apimd-crystalline-cascade.md`。
> マージ時にここを `STATUS.md`／`handover.md §D` へ集約する。

## 0. 現在地（★次はここから）

- **branch=`api-lab`**（worktree `/home/qingj/wt/api-lab`、base=main）。**縦スライス第1本は5コミット済み**（2026-07-06）: `e19fe50`(依存＋権限)→`6783d9a`(メタ取得サブシステム＋テスト)→`ac2d575`(画面＋導線)→`3a4ec46`(調査資料docs)→`8b267c8`(ディスカバリVMテスト)。
- **やっていること**: なろう公式APIの**発見機能を「第2の柱」に育てる**。案A（本文は取らずメタのみ）を堅持しつつ、発見機能を「公式より丁寧・アプリ内で完結するレベル」に作り込む。目玉＝**PDF↔Web継続読書**と**静かな没入意匠**。競合5アプリ解析（`docs/reference/04-competitor-app-features.md`）が裏付け＝競合はどれも発見が弱い。**計画を策定・ユーザー承認済み（2026-07-06）**＝§3 の目標ロードマップが現在地・目標の正本、実装詳細の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。
- **★次アクション**: **Phase 0＝発見画面群の /design モック先行**（ランキング/検索/ジャンル/作品カード/融合本棚/継続読書導線）→ Phase 1 コア。**大規模なので fresh セッションで実行推奨**（cache 非対称・CLAUDE.md ⑤）。**コミットは worktree 内で起動した `claude` セッションから**（`guard_commit_branch` は cwd ブランチで判定）。
- **並行検討＝デフォルト本棚UIの見直し**: 既存 `BookshelfScreen` の意匠も刷新したい。融合本棚（目玉②＝発見と所有を同じ視覚言語に揃える）と地続きのため、**Phase 0 の /design モック設計に既存本棚も含める**（発見画面だけ作り込むと既存本棚との段差が出るのを避ける）。見た目の正本は ADR0005＋HTMLモック（直書き禁止・`theme/Color.kt`／`MinchoFamily` 経由）。

## 1. 完了（縦スライス第1本＝「週間ランキング一覧が出る」）

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

## 3. 目標ロードマップ（承認済み計画・2026-07-06）

> 正本の現在地・目標はこの節。実装詳細（差し込み点の file:line・アーキ方針）は plan `~/.claude/plans/api-agy-woolly-swan.md`。各段は **/design モック確定 → Compose翻訳 → `cd android && ./gradlew testDebugUnitTest` GREEN**。

**作る機能**: 発見コア（C1 order切替タブ／C2 検索〔word/notword＋範囲 title/ex/keyword/wname〕／C3 ジャンル〔biggenre/genre〕／C4 作品カード詳細）・発見強化（D1 検索履歴＋ピン留め／D2 属性フラグ〔異世界転生/転移等〕／D3 範囲絞り込み＝気分プリセット／D4 type／D5 期間）・目玉（①PDF↔Web継続読書／④静かな没入意匠／②融合本棚／③気分で探す導線）・橋渡し（B1 PDF取込接続／B2 Webで読む導線）・育成（U1 新着話チェック＋通知／U2 整理）。**あわせて既存デフォルト本棚UIの意匠も見直す（§0）**。

**Phase 進捗**:
- [ ] **Phase 0** 発見体験の意匠設計（全画面 /design モック先行・**既存本棚UIも含む**）★次
- [ ] **Phase 1** 発見コア C1〜C4
- [ ] **Phase 2** 発見強化 D1〜D5
- [ ] **Phase 3** ★PDF↔Web継続読書（目玉①・Room version 7→8 で `/db-migration` 必須）
- [ ] **Phase 4** 融合本棚②＋整理 U2＋新着通知 U1
- [ ] **Phase 5** doc昇格（ADR・handover §D・03↔04 相互リンク）

## 4. 参照

- **承認済み計画（現行の目標・実装詳細の一次情報）: `~/.claude/plans/api-agy-woolly-swan.md`**
- 縦スライス第1本の設計判断（アーカイブ）: `~/.claude/plans/apimd-crystalline-cascade.md`
- API仕様: `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）
- 機能検討: `03-api-feature-analysis.md`（案A推奨）／競合解析: `04-competitor-app-features.md`
- 実機検証作法: `/device-verify` スキル（`adb-bridge`・`connectedAndroidTest` 直叩き禁止）
