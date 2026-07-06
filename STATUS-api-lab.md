# STATUS — `api-lab` ブランチ現況台帳

> **このブランチ専用の現況正本**（`STATUS.md` は main 正本のため、api-lab の作業状態はここに記録する）。
> ブランチをまたぐ不変知見は auto-memory、main 全体の現況は `STATUS.md`、腐りにくい外部事実は `task_diary.md`。
> 一次情報（設計判断の全文・未決事項）は plan `~/.claude/plans/apimd-crystalline-cascade.md`。
> マージ時にここを `STATUS.md`／`handover.md §D` へ集約する。

## 0. 現在地（★次はここから）

- **branch=`api-lab`**（worktree `/home/qingj/wt/api-lab`、base=main）。**縦スライス第1本は4コミット済み**（2026-07-06）: `e19fe50`(依存＋権限)→`6783d9a`(メタ取得サブシステム＋テスト)→`ac2d575`(画面＋導線)→`3a4ec46`(調査資料docs)。作業ツリーはこの STATUS 更新のみ。
- **やっていること**: なろう公式APIでディスカバリ機能（発見）を足す。方針は**案A＝ディスカバリ先行・本文は取得しない（メタデータのみ）**で確定。競合5アプリ解析（`docs/reference/04-competitor-app-features.md`）が裏付け。
- **★次アクション**: (1) 肉付け第①段=order切替タブ（TabRow）。(2) doc昇格（ADR＝Retrofit+Moshi採用理由・narou別系統隔離・キャッシュ方式／handover §D）。**コミットは worktree 内で起動した `claude` セッションから**（`guard_commit_branch` は cwd ブランチで判定）。

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

## 3. 次にやること

- **コミット分割（完了・2026-07-06）**: ①`e19fe50` 依存＋権限（build.gradle＋Manifest）／②`6783d9a` メタ取得サブシステム（model/network/repository＋テスト＋golden JSON）／③`ac2d575` 画面＋導線（VM/Screen/結線3ファイル）／④`3a4ec46` 調査資料 docs（01〜04 一式）。当初案の「04のみ＋03↔04リンク」は 01〜04 とも新規未追跡だったため調査資料一式1コミットに集約した。
- **肉付け（縦スライスの上に）**: ①order切替タブ（TabRow）→②フリーワード検索（word/notword＋範囲フラグ・type・lim/st）→③ジャンル絞り込み（genre/biggenre）→④作品カード詳細（of拡張・**本文は取らない**）。各段「最小UIで疎通GREEN→/designモック確定後にCompose翻訳」。
- **doc昇格**: ADR（`docs/decisions/`）＝Retrofit+Moshi採用理由・narou別系統隔離・キャッシュ方式。handover §D へ＝肉付け段と保留項目（gzip有効化・跨セッション永続キャッシュ・R18=novel18api）。
- **保留（別フェーズ・要判断）**: 本文取得（案B/C）＝規約リスク大。競合A/Bもなろう本文スクレイプから撤退済み（`04`）。R18対応。なろうログインCookie間借り同期。

## 4. 参照

- 設計判断の全文・未決事項: `~/.claude/plans/apimd-crystalline-cascade.md`
- API仕様: `docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）
- 機能検討: `03-api-feature-analysis.md`（案A推奨）／競合解析: `04-competitor-app-features.md`
- 実機検証作法: `/device-verify` スキル（`adb-bridge`・`connectedAndroidTest` 直叩き禁止）
