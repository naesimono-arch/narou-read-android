# UX/Design 全層監査 一挙消化 — 実行記録＋fresh セッション引き継ぎ（2026-07-12）

> **対象ブランチ: `ui/polish`（worktree `~/wt/ui-polish`・ext4）**。ベース HEAD=`9def5ad`。
> 一次情報（全指摘 evidence）＝`.claude/plans/ux-design-full-audit-2026-07-12.md`／やること台帳＝`handover.md` ★節。
> 本ファイルは 2026-07-12 の一挙消化セッション（司令塔＋サブエージェント11体）の実行記録と、
> **fresh セッションが残タスクを消化するための正本**。

## 【セッション2・2026-07-12 続き】最新状態と次アクション（★fresh はまずここ）

> 下の「★実行セッション起動ブロック」以降はセッション1（一挙消化・未コミット時点）の記録で**一部 stale**
> （「未コミット・残り①ユーザー確認②実機検証③コミット④消し込み」は解消済み）。本節が**セッション2 後の最新正本**。

### 現況
- ブランチ `ui/polish`（worktree `~/wt/ui-polish`・ext4）＝**main の 18 コミット先**・working tree クリーン・全ゲート緑（testDebugUnitTest／`check_design_tokens.py` OK=116）。
- 監査バッチ本体はクラスタ12コミット済み（`031fbaf`〜`9935600`）。本セッションで**さらに6コミット追加**。
- 実機 PGEM10 は **v18 APK 導入済み**（v17→v18 migration 通過を実測）。

### セッション2 の6コミット
- `6f0ee4f` **E**: 進行類型Motion 400ms の 350ms 上限免除を ADR0014 に明記（+Motion.kt why）
- `c37947a` **G**: MigrationTest floor v7 を確定判断として KDoc 記録（v1/v2端末なし確定）
- `ad54232` **#11**: 読書画面の戻る/Up を「本棚>目次>本文」2階層に統一（navHistory 撤廃・全経路逆再生解消・章の ← も目次へ統一・死んだ pushNavHistory/NavHistoryTest 撤去）
- `69dcb00` **#12**: 読了印「了」を書影左下へ移動＋四角内中央寄せ（題名重なり解消・モック grid-D/consistency-D 同期）
- `c740fd6` 了スタンプ押下アニメの着想を handover backlog へ
- `4ebbac3` **D**: 作品詳細の ← を発見ホームへの固定Up に統一（Result同型・onBack→onUp）

### 実機検証（サブエージェント委譲＋ユーザー目視）＝完了
- 自律9項目: ① migration v18 PASS（蔵書/進捗生存・reachedEnd追加・`web_reading_progress` の孤児3行掃除は privacy Major の意図挙動）／② C1参照モード PASS／④ d-chrome PASS／⑥ ルビ PASS／⑨ C2 backup 非破壊 PASS。
- ユーザー目視OK: ⑤ WebReader・⑥ TalkBack・⑦ 通知トグル・**#12 了印「完璧」**・**#11 戻る/Up**・（D は実機未確認だが Result 同型で低リスク）。
- **残（低優先）**: ⑧ 回転レース精密突合・横向き/大フォント行長の可読性（主観）。
- **事象**: 検証中に削除/Undoテストで蔵書1冊誤削除（実験用PDFで不問）。再発防止＝auto-memory `device-verify-delegation-no-destructive-on-real-library`。

### ユーザー確認バッチ A〜H ＝全裁定
- A=クラスタ（既定）／B ルビmock改変=維持／C③ 範囲チップ先出し=維持／H 二層ソート=維持／C④ 蔵書内フィルタUI=保留（ロジックのみ）／C⑤ mock非表現追加=実機目視で容認／E・G・D=実装済。
- **C①** 読書ギア=撤去せず、ただし**代替（下端シート化）の試作モックを見たい**（残タスク）。
- **F** 余白=**モック作り直し**（離散スケール準拠へ再設計）＝§C 原則維持。
- **C②** 発見帯=**/design ですり合わせ**（撤去でなく相談）。

### 残タスク（fresh セッションはここから）
1. **重いデザイン系（/design・DesignSync は主セッション限定＝委譲不可）**:
   - **F**: 任意dp(11/5/14/18/20/26等)を使う全画面モックを離散スケール(4/8/16/24/40)へ再設計→`docs/design-candidates` 更新→Compose 再翻訳→`check_design_tokens.py` 更新。§C は維持。
   - **C②**: 本棚先頭の発見帯の配置/去就をHTMLモックで擦り合わせ（ADR0005/0014接地）。
   - **C①**: 読書ギア撤去→中央タップ→下端シートの代替導線の試作HTMLモックを作り chrome で見せる（探索・改善確約でない）。
2. **★特別監査項目の handover 消し込み**: `handover.md` ★節は今も全項目「やること」表記だが大半は実装＋コミット＋実機検証済み。完了行を `STATUS.md` へ移し ★節から消す（本節と突合しながら）。
3. **STATUS.md 更新**: ui/polish の監査バッチ＋セッション2 完了を §1 に反映（現状 STATUS は本ブランチ進捗に未言及）。
4. **低優先**: ⑧ 実機チェック／了スタンプ押下アニメ（handover backlog）。
5. **最終**: `ui/polish` → main の --no-ff 統合（ゲート緑確認・実機 v18 済み）。

---

## ★実行セッション起動ブロック（fresh はここから）

- **現況**: ★監査項目の実装は本体ほぼ完了・**全て未コミット**（working tree に 62ファイル・+2548/-464 が乗っている。
  `git status` で確認してから作業。**stash・checkout -- でツリーを消さないこと**）。
- **ゲート実績（この状態で全緑・2026-07-12）**: `testDebugUnitTest` **461件/失敗0**・`python3 tools/check_design_tokens.py` **OK=116/NG=0**・
  `:app:lintDebug` **0 errors**（warnings 31＝新規は ModifierParameter×3/UsableSpace×2 等・非ブロック）・`assembleDebug` 済み
  （APK=`android/app/build/outputs/apk/debug/app-debug.apk`）。
- **★次はここから**: ①ユーザーへ「確認バッチ」（下記）を提示し裁定を得る → ②実機検証（下記リスト・PushNotification→目視関門）
  → ③コミット（裁定済みの方式で）→ ④handover ★節の消し込み＋STATUS 記帳＋本ファイルへ結果追記。
- **最小読みセット**: ①本ファイル ②`handover.md` ★節 ③（コミット時）保全パッチ＝`~/.claude/ux-audit-20260712-artifacts/patches/`（45個・項目単位の累積diff）と同 `ledger.md`（司令塔台帳の生ログ）。
- **検証ゲート**: `cd android && testDebugUnitTest`（gw は非対話 Bash では効かない→ `JAVA_HOME=~/opt/jdk-17 ANDROID_HOME=~/Android/Sdk` を export し
  `$JAVA_HOME/bin/java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain testDebugUnitTest`。ext4 worktree＝init-script 不要）＋
  `python3 tools/check_design_tokens.py`。意匠変更時はモック同期。
- **実機**: PGEM10（`adb-bridge` 一発→ `adb devices`）。端末 DB=**v17**・**working tree は Room v18**（reachedEnd 追加）＝
  install -r で v17→v18 migration が走る。**逆走禁止**（v18 APK を入れた後に旧 APK へ戻すとクラッシュ）。install 前に必ずユーザーへ一声（memory `feedback-ask-before-device-testing`）。

## 消化サマリ

- **Critical 3/3 実装完了**: C1=参照モード（jumpOrigin/「続きに戻る」チップ/滞留昇格 4段落 or 20秒・自動保存抑止）／
  C2=層別 Auto Backup（**ADR 0015** 起票・manifest allowBackup=true・res/xml 2ファイル・graceful degrade「本文データがこの端末にありません…」＝進捗DB不触）／
  C3=新着通知 既定OFFオプトイン（`NewEpisodeNotificationPreference`＋`NewEpisodeNotificationToggle` を本棚⋮メニューへ配線・IMPORTANCE_LOW・OFFで日次照会も停止）。
- **Major 24/26・Minor 29/32 実装完了**。未実装＝意匠裁定待ち: ①読書ギア撤去（**モックにギア実在＝指示と正本が矛盾**・停止）
  ②本棚発見帯撤去（撤去非推奨の分析済み） ③範囲チップ先出し（モック正本由来と確認・保留） ④蔵書内フィルタ/series束ね UI（ロジック＝`filterBooksByQuery` 実装済み・UI はモック未表現で保留） ⑤series 束ねはスキーマ変更要（設計案のみ）。
- **重要な派生実装**: `else 1f` 是正で『了』印が死ぬ退行を検知→ **reachedEnd を Room v17→v18 で永続化**
  （`MIGRATION_17_18`・schemas/18.json 生成済み・**位置保存を insertIfAbsent＋updatePosition の2手化**＝REPLACE が reachedEnd を消す罠の根治・
  末尾可視化で事実点灯・参照モード中は点灯しない・MigrationTest 7→18 延長済み）。
- **d-token bulk 完了**: fontSize 直書き148箇所→ `Typography.kt` の役割スロット20個（`FontHomeTitle`〜`FontSealBadge`・
  **全値モック px と1:1＝見た目不変**）＋`check_design_tokens.py` に sp 突合（スロット値⇄モック px 集合）機械化。
  除外＝スライダー見本グリフ「あ」2・RubyText 読書本文系2・Int パラメータ1。
- **要検証16件**: 静的確定4（目次階層=フラット確定→折り畳みは前提データ欠如で不成立＝backlog／章題余白 top14<26=モック由来で意図的／
  nav←=詳細Back vs 結果固定Up の不統一を現物確定／privacy=C3で解消）・テスト固定1（章数二経路の不変条件テスト）・
  実機送り6（回転レース・回転オフセット・ノッチ横向き・TalkBack到達・ルビ掛け・大フォント行数）・
  保留4（WebView白画面=回線シミュ不可・下端誤発火=体感・v1/v2残存=人間知識・Macrobenchmark=新設タスク）・ADR裁定1（Motion 400ms）。
- **実機 before 証拠（採取済み）**: web_novels 0行 vs web_reading_progress 3行＝orphan 実害／新着 Worker 無条件登録。
  C1 の before 再現は**実読書位置を破壊するため意図的にスキップ**（コード確定済み・修正後の非破壊検証に切替）。
- **人間テスト送り7件**: `.claude/plans/usability-test-protocol-2026-07-12.md`（UX/17 Krug 式・T1〜T7）にタスク化済み。
- **ADR 新設**: 0015（層別バックアップ・旧 allowBackup=false 上書き）・0016（本棚二層ソート）。ADR 0012 に追補（決定A/B）。
  `docs/patterns/discovery-terminology.md` 新設（見つける/探す 2概念・発見/検索を表層から排す）。

## レビュー実績（監督による削除行込みレビュー済みの範囲）

A4(PdfProcessingService/NovelReaderApplication/Worker/build.gradle)・A5(ShelfItems/BookDao/WebBookCard/BookshelfScreen)・
A2(RubyText/ChapterContent/NcodeLinkSheet/ReadingErrorScreen/ShioriCover)・A3+2b(ProgressDao/Entity/AppDatabase/DefaultBookRepository)・
NativeReadingScreen(C1/chrome/読了/degrade)・NovelApiRepository(retry)・PdfImportViewModel・各VM・docs — **設計逸脱なし**。
未レビュー細部: BookCard/ContinuationCard の細部・テスト群の中身（ゲート緑で実走確認のみ）。

## コミット計画（クラスタ12案・ユーザー承認待ち）

> 1ファイルに複数項目が混在（NativeReadingScreen=C1+chrome+読了+degrade 等）のため厳密1項目1コミットは
> `~/.claude/ux-audit-20260712-artifacts/patches/` の45パッチから hunk 再構成が要る。推奨＝クラスタ（本文に含有項目列挙）。

1. refactor: motion トークン拡充＋カード押下バウンス除去 — `theme/Motion.kt`
2. refactor: fontSize 148箇所を役割スロット集約＋sp突合検査 — `theme/Typography.kt`・`tools/check_design_tokens.py`（UI側置換は各クラスタ同乗も可）
3. feat: 層別 Auto Backup（ADR 0015） — manifest・`res/xml/backup_rules.xml`・`res/xml/data_extraction_rules.xml`・docs
4. feat: 読了 reachedEnd 永続化 v17→v18＋位置保存2手化 — `data/`×3・`schemas/18.json`・MigrationTest
5. fix: Web進捗 furthest-wins＋削除経路/orphan掃除＋空き容量＋extractBook注入＋章数不変条件 — repository×2・DAO・Entity・tests
6. feat: リトライ単一集約点＋privacy logcat — `NovelApiRepository`・`PdfImportViewModel`・tests
7. feat: C3 オプトイン＋Service 通知改善5件＋リカバリ純関数 — App/Service/Worker・新規2ファイル・build.gradle・`StartupRecovery`・tests
8. fix: C1＋d-chrome 3件＋errtext/gesture/liveRegion/crossfade＋C2 degrade＋読了検出 — `NativeReadingScreen`・`ChapterContent`・`MainActivity`
9. fix: ルビ AA/fontScale/TTS＋ReadingError/NcodeLink トークン化 — `RubyText`・`Theme.kt`・ほか＋`reading-D.html` --ruby 同期（**モック改変＝要承認**）
10. feat: 本棚クラスタ（else1f・主タップ統一・二層ソート+ADR0016・FAB遅延・削除undo・priming・Adaptive・0件チップ・mergeDescendants・相対時刻・バナーmotion・C3トグル・🔍ラベル）
11. fix: 発見系/Web読書クラスタ（saveState・第N話表記+ADR0012追補・keepScreenOn・非ミラー裁定・用語辞書・目次既読・システムに従う・恒常ラベル・題字fade・InfoText残り）
12. docs: 記帳（handover ★消し込み・STATUS・人間テストプロトコル）— 実機 GREEN 後

## ユーザー確認バッチ（未裁定・fresh セッションが最初に提示する）

- **A** コミット方式: クラスタ12（推奨）vs 厳密1項目1コミット（45パッチ再構成）
- **B** モック改変承認: `reading-D.html` の `--ruby` 3値（ルビ AA 化の両建て同期・適用済み）
- **C** 意匠裁定: ①読書ギア撤去（モック矛盾→取り下げ or モック改変） ②発見帯撤去（非推奨） ③範囲チップ先出し（正本由来→維持推奨）
  ④蔵書内フィルタ/series UI の要否 ⑤モック非表現の追加実装の目視可否（目次既読淡色・「◯日前」・「システムに従う」チップ・削除undo・0件チップdim・⋮「なろうの目次を開く」）
- **D** nav← 意味論統一（詳細=Back vs 結果=固定Up）
- **E** Motion 400ms（進行類型免除の ADR 明記 or 300ms）
- **F** **ADR 0014 §C vs モック余白の矛盾**（モック自体が離散スケール不遵守と全数調査で判明）→ §C 改訂（値保持の命名集約へ）or モック再設計 or 例外登録
- **G** v1/v2 スキーマ実機の残存有無（人間知識・MigrationTest floor 判断）
- **H** 二層ソートのトレードオフ受容（ADR 0016・放置読みかけ本＞新刊）

## 実機検証リスト（帰還後・直列・PushNotification→目視関門→コミット）

install -r（蔵書9冊保持・現 v17→v18 migration が走る）→
① migration 通過（起動クラッシュ無し・`run-as` で DB pull し `user_version=18`・progress 全行 reachedEnd=0・蔵書/進捗生存）
② C1: 読みかけ本（例: af9a93ca シャンフロ chap_951）で目次→前章を開く→**DB の progress が不変**（非破壊確認）・「続きに戻る」チップ表示/復帰・滞留昇格（4段落 or 20秒）
③ 読了: 最終章末尾まで→本棚『了』＋読了フィルタ・前章へ戻っても sticky・参照モードの覗き見では点灯しない
④ d-chrome: 入場時バー無し・中央タップでシステムバー同期出入り・縁スワイプ復帰・離脱後に本棚で復元・長章無操作で消灯しない
⑤ WebReader: ep 読み進め→回転/ダーク切替で話・履歴保持（旧: 巻き戻り）
⑥ ルビ: 新3色の見え・OS フォント最大で追従・TalkBack 当て字読み（魔剣→つるぎ）
⑦ 本棚: 二層ソート・Adaptive（スマホ2列不変）・削除→snackbar Undo（復帰/確定/画面離脱確定）・FAB 直行＋変換開始時バッテリー案内・priming→権限→picker 続行・⋮通知トグル ON/OFF（ON で Worker 登録・OFF で cancel を dumpsys で確認）
⑧ 要検証残: 回転レース（読みつつ即回転→DB 突合）・ノッチ横向き（行頭欠け）・大フォント行長（字数基準の見え）
⑨ C2: `adb shell bmgr backupnow com.novelreader`（トランスポート可否確認）→データ消去→復元→蔵書メタ/位置生存・本文なし固定文言・再取込
※ 通知5件（前面スキップ・OnlyAlertOnce・deep link cancel・retryUri）は PDF 取込のついでに観察。
※ Wave2b 留保: 進捗バー%と『了』の非対称（バー~95%のまま）＝実害小・別タスク。contentSha256 再結合＝設計案のみ（C2 完成の残り）。

## 残 backlog（handover へ反映すべき新規知見）

- 目次の部/編折り畳み: 抽出パイプラインに階層データ無し＝**抽出側の新機能**として D 節へ。
- Macrobenchmark 新設（measure 要検証）＝独立タスク。
- lint 新 warnings: ModifierParameter×3（新設 composable）・UsableSpace×2（getAllocatableBytes 併用提案）＝任意改善。
- 発見系モックの InfoText 再分類（既存 handover「UI/UX 宿題」）に SearchConditionSheet/DiscoverySearch/TOC の3箇所追加分も同枠。
