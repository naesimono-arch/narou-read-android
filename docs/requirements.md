# 要件定義書（as-built）

> **現行実装から逆算した要件**（2026-09-02 時点）。「これから作るもの」ではなく「今のアプリが満たしていること」を書く。
> ⚠️ **実装値の正本はこの文書ではない**——SDK・依存版は `android/app/build.gradle`、スキーマは `data/AppDatabase.kt`、
> 設計判断は `docs/decisions/`、現況は `STATUS.md` が正本。ここは**要件の全体像を1枚で見るための索引**であり、
> 数値を写した箇所は必ず出典を併記する（写しは腐るため、疑ったら出典を見る）。
> 未実装・今後の要件は書かない（＝`handover.md` / `awaiting-human.md` / `docs/backlog-frozen.md` の役目）。

## 1. プロダクト概要

日本語Web小説を**端末内に取り込んで、オフラインで縦書き／横書きで読む** Android アプリ。
ブランド名 `Yosari`（未公開・Google Play 公開準備中）。

二つの柱で成立している:

| 柱 | 内容 | 本文の取得 |
|---|---|---|
| A. 蔵書と読書 | PDF・Web小説を取り込み、ふりがな対応 HTML へ変換して自前レンダラで読む | する（端末内に保存） |
| B. 作品の発見 | 小説家になろう公式 API でランキング・検索・作品詳細を見る | **しない**（メタデータのみ） |

B で見つけた作品のうち、**なろう作品は本文を加工しない**（公式サイトへ送客するか、公式が配布する縦書き PDF を
取り込む）。本文を自前で取得するのは、規約上それが許される対応サイトに限る（§8）。

## 2. 利用者と利用環境

- **想定利用者**: 日本語Web小説（いわゆる「なろう系」）を読む個人。オフライン・通勤中・通信量節約を動機に持つ。
- **アカウント不要**: サインイン・サーバ側ユーザーデータを持たない。データは端末内に閉じる。
- **動作環境**（正本＝`android/app/build.gradle`）: minSdk 26 / targetSdk 36 / compileSdk 36。
- **主な検証端末**: OPPO PGEM10（ColorOS）。第三者端末 Huawei P30（EMUI）でも観測実績あり。

## 3. 機能要件

### 3.1 蔵書（本棚）— FR-SHELF

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-SHELF-01 | 取り込んだ作品を一覧表示する（グリッド／リストの切替を持つ） | `ui/BookshelfScreen.kt`・`ui/skins/ShelfViewToggle.kt` |
| FR-SHELF-02 | 既定の並びは二層＝**読書中は最終閲覧順・未読は追加順**（未読が下層） | ADR 0016 |
| FR-SHELF-03 | 状態チップで絞り込める（読書中・未読・読了 等）。溢れる条件では端をフェードして見切れを示す | ADR 0036 |
| FR-SHELF-04 | 各書影は**この本固有の栞**（先端意匠・棒長）を持ち、取込時に1回抽選して永続化する | `BookEntity.shioriTipIndex` / `shioriLenFrac` |
| FR-SHELF-05 | 蔵書が空のときは CTA を出し、FAB は引っ込める（明快K のみ） | ADR 0037 |
| FR-SHELF-06 | 本を削除できる。PDF 由来の本は**取込元 PDF の削除可否を選べる** | `ui/DeleteSourcePdfOption.kt` |

### 3.2 PDF 取込 — FR-PDF

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-PDF-01 | 端末内の PDF を取り込み、章分割された HTML（`index.html` + `chap_N.html`）へ変換する | `pdf/PdfBookExtractor.kt` |
| FR-PDF-02 | 選択は**標準ピッカー**で行い、なろう形式かどうかの判定は取込段で行う（複数選択可） | ADR 0013 |
| FR-PDF-03 | フォルダを走査してまとめて取り込める | `domain/PdfFolderScan.kt` |
| FR-PDF-04 | 抽出ルール（章見出し・ルビ・傍点等）は**文書ごとに自動検出**する。検出不能時のみ定数へフォールバック | `pdf/DetectedRules.kt` / `pdf/ParserRules.kt` |
| FR-PDF-05 | 変換は**フォアグラウンドサービス**で行い、進捗（4ステップ）と失敗理由を UI へ通知する | `PdfProcessingService.kt`・`ui/ProcessingBanner.kt` |
| FR-PDF-06 | 処理中の追加投入は**無音で捨てずキューへ積む**。停止操作は**ページ境界で即中断**する | `PdfProcessingService`（ReentrantLock + ArrayDeque） |
| FR-PDF-07 | なろう公式が配布する縦書き PDF は、アプリ内 WebView から**毎回ユーザー操作で**ダウンロードして取り込める（一括・自動DLはしない） | ADR 0011・`ui/discovery/PdfImportScreen.kt` |
| FR-PDF-08 | 同一作品の再取込は差分を計画してから実行する（丸ごと重複を作らない） | `domain/ReimportPlan.kt`・`ui/ReimportSweepBanner.kt` |

### 3.3 Web小説の取込 — FR-WEB

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-WEB-01 | 他アプリからの**共有（ACTION_SEND）**で任意サイトの URL を受け取れる | `AndroidManifest.xml`・`WebImportIntentParser.kt` |
| FR-WEB-02 | **対応サイトのリンクタップ（ACTION_VIEW）**を受け取れる（対象ホストは限定＝カクヨム・暁） | 同上 |
| FR-WEB-03 | URL は3値で裁定する＝**Supported（取込）／Blocked（公式サイト送り）／Unsupported（未対応案内）** | `scrape/SiteAdapterRegistry.kt`・ADR 0024 |
| FR-WEB-04 | 抽出結果は PDF 蔵書と**同一契約の HTML** に合流させる（読書側は出自を区別しない） | `HtmlExporter` 経由 |
| FR-WEB-05 | サイト側の HTML 変更による破損を**fixture ゴールデン**で機械検知する | `test/resources/scrape_fixtures/`・`scrape/ScrapeIntegrity.kt` |
| FR-WEB-06 | サーバ負荷を抑えるため HTTP クライアントに Crawl-delay を内蔵する | `scrape/ScrapeHttpClient.kt` |
| FR-WEB-07 | 対応サイトの健全性をアプリ内から確認できる | `ui/AdapterHealthBoardDialog.kt` |

### 3.4 作品の発見・検索 — FR-DISC

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-DISC-01 | なろう公式 API でランキング／ジャンル／気分プリセット／詳細検索を提供する（**本文は取得しない**） | `narou/NovelApiRepository.kt` |
| FR-DISC-02 | 検索・ジャンル・気分のいずれも**同一の結果一覧**へ着地する | `ui/discovery/DiscoveryResultScreen.kt` |
| FR-DISC-03 | 検索UXは3原則に従う＝①見えている条件はその場で変えられる ②仕組みを隠さない ③語彙を知らなくても絞り込める | ADR 0007 |
| FR-DISC-04 | 検索条件の下書きは画面を離れても保持する | `domain/SearchDraft.kt`（VM 保持） |
| FR-DISC-05 | 検索履歴を永続化する（蔵書 DB とは別系統＝DataStore） | `narou_search_history` |
| FR-DISC-06 | 作品詳細から公式サイトへ送客する。なろう本文の**閲覧は加工なし WebView**（JS 注入ゼロ・URL 観測のみ） | ADR 0010 / 0012・`ui/discovery/WebReaderScreen.kt` |
| FR-DISC-07 | 発見サブツリーの戻りは**階層 up 一本化**（詳細→直近の結果一覧→発見ホーム）。タブ間の Back も階層 up | ADR 0026（＋2026-08-14 追記） |
| FR-DISC-08 | 一覧は無限ページングし、API の上限（st/lim）に達したら打ち切る | `NovelApiRepository.discoverPage()` |

### 3.5 読書 — FR-READ

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-READ-01 | 本文は **WebView ではなく Compose ネイティブ**で描画する（ふりがな＝ルビ対応） | `ui/NativeReadingScreen.kt`・`ui/compose/RubyText.kt` |
| FR-READ-02 | **縦書き**モードを持つ。右→左の連続横スクロール × **自前 Compose 組版**（禁則・ルビ配置・縦中横を含む） | ADR 0020・`typeset/` |
| FR-READ-03 | 読書設定＝テーマ（システム／ライト／セピア／ダーク）・本文の向き・文字サイズ・行間・本文余白 | `ui/ReadingSettingsSheet.kt` |
| FR-READ-04 | 読書位置（章・スクロール位置）を永続化し、続きから再開する | `data/ProgressEntity.kt` |
| FR-READ-05 | **目次閲覧では進捗を上書きしない**（`index.html` はブロックリスト方式で除外） | `ui/NativeReadingScreen.kt` |
| FR-READ-06 | 読了状態を記録し、本棚のフィルタに反映する | `ProgressEntity.reachedEnd` |
| FR-READ-07 | 目次から章へ跳べる。章送りはスワイプで行う | `ui/NativeTableOfContentsScreen.kt`・`ui/ReadingSwipePeek.kt` |
| FR-READ-08 | 画面遷移は slide push に統一（進む右→左・戻る左→右・250ms）。章送りだけは瞬間据え置き | ADR 0019 |
| FR-READ-09 | 読み込み中は**遷移骨（スケルトン）**を出す。横書きと縦書きで骨を作り分ける | ADR 0040・`ui/TransitionSkeletons.kt` |

### 3.6 継続読書・新着 — FR-CONT

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-CONT-01 | 蔵書となろう作品を **ncode で紐付ける**（PDF の話数と Web の話数を突合する） | `ui/NcodeLinkSheet.kt`・`narou/ContinuationLogic.kt` |
| FR-CONT-02 | 蔵書の続きを Web で読む導線を出す（読み終えた話数の次から） | `ui/ContinuationCard.kt` |
| FR-CONT-03 | 紐付いた作品の**新着話を1日1回チェック**し、通知と本棚の印で知らせる | `NewEpisodeCheckWorker.kt`（PeriodicWork 24h・ネットワーク制約） |
| FR-CONT-04 | 新着通知は個別に無効化できる | `NewEpisodeNotificationPreference.kt` |

### 3.7 設定・外観 — FR-SET

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-SET-01 | 画面構成は3タブ（本棚／さがす／設定）を横スワイプで切り替える | `ui/tabs/TabPagerHost.kt` |
| FR-SET-02 | 設定は〈テーマ／きせかえ／文字と組版／取り込み状態の診断／操作の説明／バージョン〉を持つ | `ui/skins/k/SettingsScreenK.kt` |
| FR-SET-03 | **UIスキン（着せ替え）機構**を持つ。既定は「明快K」。構造骨格は全スキン共通で、差分は意匠と一部の画面構造 | ADR 0021 / 0022・`ui/theme/skins/` |
| FR-SET-04 | **初回公開ではスキン切替を機能ごと閉じる**（フラグ `SKIN_SWITCHING_ENABLED`。保存済みの選択は消さず読み替える） | ADR 0027・`Features.kt` |
| FR-SET-05 | 横向きでは **NavigationRail** へ切り替える（縦向け前提の固定分が横向きの縦幅を食い尽くすため） | ADR 0034 |
| FR-SET-06 | 初回読了を契機に In-App Review を1セッション1回だけ提示する | `review/ReviewPrompter.kt` |

### 3.8 復旧・診断 — FR-REC

| ID | 要件 | 実装の所在 |
|---|---|---|
| FR-REC-01 | 取込ジョブを `pending_jobs` に記帳し、**OEM kill / OOM / タイムアウト後の次回起動で再開**する | `StartupRecovery.kt`・`data/PendingJobEntity.kt` |
| FR-REC-02 | 起動時に**孤立 HTML**（DB に無い `novels/<id>/`）を掃除する | 同上 |
| FR-REC-03 | プロセスを跨いで読めるよう、取込元 URI の永続権限を取得し、記帳削除時に解放する | `BookshelfViewModel.addBook` |
| FR-REC-04 | 再試行の暴走を止めるため試行回数に上限を持つ | `PendingJobEntity.attempts`（Room v22） |
| FR-REC-05 | エラーは one-shot イベントで通知する（画面回転で再表示しない・複数購読で重複しない） | `NovelReaderApplication.errorEvents`（Channel ベース） |
| FR-REC-06 | 取り込みの状態をユーザー自身が診断できる画面を持つ | 設定「取り込み状態の診断」・`diagnostics/` |

## 4. 非機能要件

### 4.1 性能 — NFR-PERF

macrobenchmark で**予算を機械判定する**（値の正本＝`android/macrobenchmark/.../*Budget.kt`）。

| ID | 対象 | 予算 |
|---|---|---|
| NFR-PERF-01 | 起動（timeToInitialDisplay） | median 350ms / max 500ms |
| NFR-PERF-02 | 本棚スクロール（frameDurationCpu） | P50 15ms / P90 20ms / P99 30ms |
| NFR-PERF-03 | 章送り | P50 15ms / P90 20ms / P99 50ms |
| NFR-PERF-04 | タブ横スワイプ | P50 11ms / P90 18ms / P99 50ms |
| NFR-PERF-05 | 本棚→目次 push | P50 15ms / P90 35ms / P99 60ms |
| NFR-PERF-06 | PDF 取込（実PDF 1本） | extract 35s / engine 33s |
| NFR-PERF-07 | 起動経路は **Baseline Profile** を release へ取り込む | `:baselineprofile` モジュール |
| NFR-PERF-08 | 大PDF のページ範囲並列度 K は**固定値でなく `maxMemory()` から動的に決める**（低ヒープ機の OEM kill を避けるため） | ADR 0035 |

⚠️ **体感の「もっさり」は主観として流さない**——計測タスクへ翻訳してから判断する（gfxinfo の jank% を試作評価のゲートに含める＝ADR 0023）。

### 4.2 信頼性・データ保全 — NFR-REL

| ID | 要件 |
|---|---|
| NFR-REL-01 | 蔵書メタデータは Room（現行 **v22**）で管理し、全バージョン間の Migration を持つ（3→4 … 21→22）。**ダウングレードは不可**（旧 APK への逆走はクラッシュ） |
| NFR-REL-02 | HTML 実体は `filesDir/novels/{bookId}/` に置く（`index.html` + `chap_N.html`） |
| NFR-REL-03 | Auto Backup は**層別**＝メタデータ層（Room・SharedPreferences・DataStore）のみ。HTML 実体は除外し、復元後に実体が無い場合は「再取込が必要」へ graceful degrade する（読書位置は保持）＝ADR 0015 |
| NFR-REL-04 | 長時間処理中は `PARTIAL_WAKE_LOCK` を保持する（OEM のバックグラウンド強制停止対策） |
| NFR-REL-05 | 記帳の insert / 全消しは mutex で直列化する（「追加直後に停止」で破棄済みジョブが復活しない） |

### 4.3 セキュリティ・プライバシー — NFR-SEC

| ID | 要件 |
|---|---|
| NFR-SEC-01 | **外部への送信をしない**。ネットワーク通信は〈なろう公式 API のメタ取得〉〈WebView での表示〉〈対応サイトの本文取得〉のみ |
| NFR-SEC-02 | アカウント・ログイン・トラッキング SDK を持たない（API はキーレス） |
| NFR-SEC-03 | 要求する権限は INTERNET / POST_NOTIFICATIONS / FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC / WAKE_LOCK の5つのみ |
| NFR-SEC-04 | 一時 PDF の受け渡しは FileProvider（`exported=false`・公開範囲は `cacheDir/pdf_import/` 一点）で行う |
| NFR-SEC-05 | なろう作品の閲覧 WebView に **JS を注入しない**（加工なし・URL 観測のみ）＝ADR 0012 |

### 4.4 アクセシビリティ・表示 — NFR-A11Y

| ID | 要件 |
|---|---|
| NFR-A11Y-01 | 端末のフォントスケール拡大（1.3・2.0）で版面が破綻しないこと。golden で 2.0 の破綻を走査する（CI ブロッキング） |
| NFR-A11Y-02 | 意匠は**HTMLモックが正本・Compose は翻訳**。トークン層を経由し色・寸法の直書きを禁止する（機械検査 `tools/check_design_tokens.py`）＝ADR 0014 |
| NFR-A11Y-03 | 可読性は美学に優先する（デザイン原則5箇条）＝ADR 0014 |
| NFR-A11Y-04 | Predictive Back（戻りプレビュー）へオプトインする |

### 4.5 品質ゲート — NFR-QA

CI（`.github/workflows/ci.yml`）が結線を持つ。ローカルは `testDebugUnitTest` と、public シグネチャ変更時の `:app:assembleDebugAndroidTest` の2つ。

| ID | ゲート |
|---|---|
| NFR-QA-01 | デザイントークン検査（`tools/check_design_tokens.py`） |
| NFR-QA-02 | 目次章数の実装非依存突合（暁66話・カクヨム593話） |
| NFR-QA-03 | ktlint（未使用 import 検知） |
| NFR-QA-04 | ユニットテスト + **Roborazzi スクリーンショット golden**（葉 Composable の UI テストは Robolectric＝ADR 0009） |
| NFR-QA-05 | golden 走査（ラベル分割判定器の回帰・fontScale 2.0 の破綻走査） |
| NFR-QA-06 | androidTest の**コンパイル確認**（実行は端末必須のため対象外） |
| NFR-QA-07 | Android Lint |
| NFR-QA-08 | release ビルド（R8 収縮）が通ること |
| NFR-QA-09 | PDF 抽出精度は golden 回帰（`ab-review/golden_regression/`＋HTML バイト等価ゴールデン）で担保する |

### 4.6 アーキテクチャ制約 — NFR-ARCH

| ID | 制約 | 根拠 |
|---|---|---|
| NFR-ARCH-01 | **DI フレームワーク（Hilt）を使わない**。依存は Application が保持して手渡す | ADR 0001 |
| NFR-ARCH-02 | **UseCase 層を置かない**（ViewModel → Repository の2層） | ADR 0002 |
| NFR-ARCH-03 | 発見層と蔵書層は**別系統**（発見は Room に触らない） | `/architecture` |
| NFR-ARCH-04 | UI⇄API の境界はサイト非依存モデル（`discovery/model/WorkSummary`）。API DTO は `narou/` 内に閉じる | ADR 0024 追記 |
| NFR-ARCH-05 | 縦書き組版は interface（`VerticalTypesetter`）で隔離し、公式の縦書き成熟時に差し替え可能にする | ADR 0020 |

## 5. 外部インタフェース

| 相手 | 内容 |
|---|---|
| なろう小説API（`api.syosetu.com`） | エンドポイントは `novelapi/api/` **1本のみ**。一覧も詳細も引数で呼び分ける。6h TTL のインメモリキャッシュを持つ。メタデータのみ取得 |
| 対応小説サイト | カクヨム（専用アダプタ・JSON 系）／暁（表駆動 generic プロファイル）。追加は「表1行＋fixture」で行える |
| Android Intent | `ACTION_SEND`（text/plain・全サイト受け）／`ACTION_VIEW`（対応ホスト限定）／変換完了通知の deep link |
| Google Play | In-App Review（内部テスト配信でのみ実表示を確認できる） |

## 6. データ要件

Room v22・6 エンティティ。

| エンティティ | 役割 | 主キー |
|---|---|---|
| `BookEntity` | 蔵書1冊（題名・著者・HTML 保存先・出自 URI/URL/サイト・ncode・本文ハッシュ・栞の抽選値） | `id` |
| `ProgressEntity` | 読書進捗（最終章・スクロール位置・最終閲覧時刻・読了フラグ） | `bookId` |
| `PendingJobEntity` | 取込ジョブの記帳（強制終了からの再開・試行回数） | — |
| `WebNovelEntity` | 紐付いたなろう作品のメタ（題名・作者・全話数） | `ncode` |
| `WebReadingProgressEntity` | Web 側（なろう）の読書位置 | — |
| `NewEpisodeMarkEntity` | 新着話の印 | — |

**別系統の永続化**: 検索履歴・読書設定・スキン選択・通知設定は DataStore / SharedPreferences。

## 7. 制約・法務

- **自前で本文を取得してよいのは、規約がそれを禁じていないサイトのみ**。判定は `SiteAdapterRegistry` の3値ゲートに集約し、
  グレーは**保守側に倒して Blocked**（アルファポリス・pixiv・野いちご等）。なろう系は全て Blocked＝公式サイトへ送る。
- **維持する設計上の守り3点**（ADR 0011「公開判断としての再確認」が正本）:
  ①一括DL・自動DLをしない ②広告を含め**無加工**で、毎回ユーザー操作を起点にする ③外部送信をしない。
- **一律スクレイピングはしない**。対応サイトは明示列挙し、未対応は「未対応」と正直に案内する（黙って壊れない）＝ADR 0024。
- Google Play 公開に向けた識別子は `applicationId = com.novelreader`（現行）。**公開後は永久変更不可**のため、
  初回アップロード前に `app.yosari.reader` へ変更する必要がある（`handover.md`）。
- 版数採番は versionCode 通し連番 × versionName semver 風＝ADR 0025。

## 8. 現時点でスコープ外

as-built の要件として**満たしていないこと**を明示する（着手の可否・順序は台帳が正本）。

- **スキン M / P / J（および C・D）の一般提供**: 実装は存在するが初回公開では機能ごと閉じる（ADR 0027）。
- **対応サイトの更なる拡大**: 表駆動の候補は暁で尽き、ヒューリスティック自動対応は不採用裁定（`handover.md`）。
- **release 用の正式な署名鍵**: 未整備＝Gradle は未署名 APK しか出さない（ユーザー作業・`awaiting-human.md`）。
- **プライバシーポリシーのアプリ内リンク**: 未設置。
- **公式の縦書きテキスト API 待ち**: 現行の縦書きは自前組版によるつなぎ（ADR 0020）。

## 9. 正本の所在（この文書が写しているもの）

| 知りたいこと | 正本 |
|---|---|
| SDK・依存・ビルド設定 | `android/app/build.gradle` |
| DB スキーマ・Migration の why | `android/app/src/main/java/com/novelreader/data/AppDatabase.kt` |
| 画面のルート一覧 | `MainActivity.kt` の NavHost |
| 設計判断・Why-not | `docs/decisions/`（索引＝同 `README.md`） |
| どこを見るか・罠 | `/architecture` skill |
| 現況 | `STATUS.md` ／ やること `handover.md` ／ 人間待ち `awaiting-human.md` |
| 性能予算の実値 | `android/macrobenchmark/.../*Budget.kt` |
| 完了の履歴 | git log |
