# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
>
> **ここは「やること」だけを置く。** 完了したら打ち消し線で残さず**消す**（完了知識の正本は `STATUS.md`。
> 実コミット等の一次情報は `.claude/plans/` のアーカイブ・腐りにくい知見は `task_diary.md`／`docs/`）。
> 打ち消し線を溜めると「やったことリスト」に化けて台帳の役目を失う（運用: memory `docs-status-vs-handover-split`）。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ下の各節に整理していないものをここへ。育ったら該当節へ移す。

- **[機能②・要判断] U1 新着チェックとの整合**: 続きあり判定は現状 PDF 蔵書の generalAllNo 突合のまま。Web カードの既読話数（web_reading_progress）を U1 基準へ組み込むかは別タスク（機能②実装時からの持ち越し）。
- **[取込] 「PDFを取り込む」ボタンの不安定さ**（2026-07-10 ユーザー報告・**再現条件未特定**）: FAB/空状態ボタンからの picker 起動が不安定に感じられることがある。まず再現条件の聞き取り（どの画面のボタンか・無反応か遅延か・バッテリー最適化ダイアログ絡みか）→ logcat での再現観察から。推定候補: 通知権限→picker の2段ゲート・バッテリー最適化ダイアログの分岐（`launchPdfPicker` 周り）だが未確定のため決め打ち修正はしない。

---

## ★特別監査項目 — UX/Design 全層監査（2026-07-12・fresh セッションで一挙消化）

> **これは何か**: `/mnt/c/Users/qingj/Desktop/project/UX`（UX24層＋Design10層＋公理1〜18候補）に対し novel-reader 全体を多エージェント監査（45体・敵対的検証済み）した指摘の**やること台帳**。
> **一次情報（全指摘の evidence・検証ノート）の正本＝`.claude/plans/ux-design-full-audit-2026-07-12.md`**（§A 統合報告・§B 全指摘詳細）。ここは終終の action list（消化したら行を消す）。
> **件数**: Critical 3 / Major 26 / Minor 32（束ね後）＋ 要検証 16・人間テスト送り 7（コード修正でなく実機/人間検証の別バケツ）。良い点3は「維持・触るな」＝§下部。
> **重複注意（SSOT）**: 一部は既存台帳と同根＝各行に「既出Lxx」注記。新設せず格上げ/具体化として扱う。

### 実行起動ブロック（fresh セッションはここから）
- **対象ブランチ**: `ui/polish`（この worktree＝ext4）。plan 冒頭に対象ブランチ記録済み。
- **最小読みセット**: ①この節 ②`.claude/plans/ux-design-full-audit-2026-07-12.md`（着手項目の §B evidence）③着手項目が指す KB層ファイル（例: a11y→`UX/09`・chrome→`Design/09`）④触るコード現物。
- **検証ゲート**: `src/main`/`src/test` を触ったら必ず `cd android && gw testDebugUnitTest`（ext4 worktree は init-script 不要）。意匠を触ったら `python3 android/tools/check_design_tokens.py`。**a11y/没入/幅/回転は実機必須→`/device-verify`**（コード修正だけで閉じない）。
- **消化順序（推奨）**: Critical①→（②③は設計判断・着手前ユーザー確認）→ Major の「引く1箇所」系（d-motion bounce・d-chrome 3件・reach 適応/ギア・ia ソート）→ a11y ルビ3件→ トークン規律 bulk。
- **1項目1コミット**（Atomic・`fix/refactor: 要約`・commit 前に変更提示＋人間承認・Co-Authored-By 無し）。実機絡みは PushNotification→目視OK→コミット（Opus 運用チューニング）。
- **委譲可否**: 小口の「引く」修正は直接（小口委譲は損）。**sp直書き161ヒットのトークン化・Spacingトークン一括適用**は仕様（スロット表）固定後に bulk 委譲可（意匠バッチは委譲仕様書に ADR0005/0014 参照必須）。
- **意匠絡みは Compose で自己判断しない**: ia本棚ソート/発見帯・d-token/d-type/d-motion/d-chrome は**モック正本（ADR0005）＋トークン層（ADR0014）を先に確認**。モック改変が要るものは ADR/DesignSync 経由。

### Critical（3件・現物確認済み）
- **[C1] 目次ジャンプで読書位置が不可逆上書き**（`NativeReadingScreen.kt:149-156`／公理14・6）: `navigateForward` が index.html 以外への全遷移で無条件 `saveProgress`→目次から章を確認しに開くと読みかけ先端が恒久喪失。**やること**: eager saveProgress を削り debounce/ON_STOP フラッシュへ委譲＋目次ジャンプは `jumpOrigin` 退避＋「続きに戻る」チップ＋滞留昇格（参照ジャンプと読み進めを区別）。**最優先推奨**（中核タスク直撃・局所修正）。
- **[C2] 端末喪失で蔵書・位置・設定が全損**（`AndroidManifest.xml:23` allowBackup=false／公理18・portable2件束ね）／**方針確定（2026-07-12・層別バックアップ）**: **やること**: ①`allowBackup=true` に戻し `android:dataExtractionRules`(API31+)＋`android:fullBackupContent`(API<31) の両方を付す ②XML は **include＝Room DB(`databases/`)＋DataStore/SharedPreferences(`datastore/`・`shared_prefs/`)**／**exclude＝`files/novels/`(HTML実体・25MB超えの要因)**＝メタデータ層（位置・しおり・設定・蔵書リスト、数十KB）のみクラウド/D2Dへ ③**復元後に HTML 実体が無いケースを graceful degrade**（読書を開くと落ちるのでなく「再取込が必要」状態＝位置は保持）＝現 manifest コメントが恐れた `resolvedFile==null` クラッシュを構造対処。位置の自動復帰は下記 Minor portable（`htmlDirPath` を bookId 再導出・`contentSha256` 再結合）と**セットで完成**。**ADR 化必須**（`allowBackup=false` の既存判断を上書き＝番号は着手時に全レーン grep で衝突確認して採番）。
- **[C3] 日次新着通知が再訪促し**（`NovelReaderApplication.kt:142`無条件schedule/`:179`IMPORTANCE_DEFAULT/`NewEpisodeCheckWorker`／公理13）／**方針確定（2026-07-12・撤去せずオプトイン）**: U1 は 2026-07-10 build 済み機能ゆえ**撤去せず既定OFFのオプトイン化**（機能を捨てず公理13へ寄せる）。**やること**: ①設定に「新着話を通知する」トグル新設（**既定OFF**）②`scheduleNewEpisodeCheck()` を onCreate 無条件呼び出しから**トグルON時のみ enqueue／OFFで `cancelUniqueWork`** へ ③チャネルを `IMPORTANCE_DEFAULT`→**`IMPORTANCE_LOW`（無音）** ④初回 POST_NOTIFICATIONS 要求前に priming ⑤既定の更新提示は**既存の本棚バッジ（無音・in-app）に委ねる**（着手時にバッジ実在を1点確認してから push を降格）。これで**要検証 privacy（日次 ncode 群の syosetu 送信・停止トグルなし）も同時解消**（OFF なら背景照会も走らない）。**既出 L15**（U1新着チェック整合）と同機能。

### Major（26件・§B に evidence）
| 出所 | 公理/ルール | 場所 file:line | やること（修正・引く優先） |
|---|---|---|---|
| ssot | 公理8 | `ShelfItems.kt:108-111` | `else 1f` をやめ最終章スクロール中は<1f・READING側に留める（1行で100%・『了』・読了移動の是正） |
| a11y+d-type **両面** | 公理11(b)/WCAG1.4.3 | `Theme.kt:63,89,108`／`RubyText.kt:87-95` | ルビ3値を色相維持で暗化し全面4.5:1へ（実測 L2.89/S2.53/D4.05 未達） |
| a11y | 公理11C/WCAG1.4.4 | `RubyText.kt:90,101-106` | ルビのフォントスケール追従を手計算→sp変換へ委譲・baseAscent も fontScale 反映 |
| a11y | 公理11F semantics | `RubyText.kt:133,209` | 段落に読み置換 AnnotatedString/VerbatimTtsAnnotation（当て字を著者読みで読み上げ・`segment.reading`流用） |
| continuity | 公理14候補E/公理1 | `WebBookCard.kt:69-124`／`BookshelfScreen.kt:216` | 進捗ありWeb作品の主タップを再開へ統一（PDFと揃える）・目次は⋮へ降格（<48dp も解消） |
| continuity | 公理14候補D/公理6 (PLAUSIBLE) | `DefaultBookRepository.kt:94-102` | `recordWebReadingEpisode` を furthest-wins 化（目次確認で再開ポインタが後退しない） |
| persist | 公理6 構成変更 | `WebReaderScreen.kt:62,65,152` | 再生成時 startUrl を DB最終話へ差替（理想は saveState/restoreState を rememberSaveable で持ち回り） |
| ia | 15-§B 既定ソート | `ShelfItems.kt:49`／`BookDao.kt:14` | 既定ソートを lastReadAt 主キーへ（未読新刊が読みかけを押し下げない）※意匠絡み ADR経由 |
| ia | 15-§G②④ | `BookshelfScreen.kt:442`／`ShelfItems.kt:37` | 蔵書内 LIKE フィルタ＋series 束ね（数百冊で既知本への最短路） |
| add | 公理12 段差 | `BookshelfScreen.kt:171-181,246-285` | 初回FABからバッテリー最適化モーダルを外し背景変換の文脈まで遅延。**既出 L16**（取込ボタン不安定）の再現候補を確定 |
| add+errtext **束ね** | エラー分類10-C | `PdfImportViewModel.kt:105-155`／`NovelApiRepository.kt:102-132` | 単一集約点で retryable(IO/timeout/5xx/429)のみ指数バックオフ+Full Jitter 1-2回。429は Retry-After・4xx非リトライ維持 |
| idempo **束ね** | 公理4/UX16-H | `BookshelfScreen.kt:689-704`／`DefaultBookRepository.kt:385-394` | 削除確認撤去→snackbar『元に戻す』遅延削除へ（即・完全不可逆＝DB行+HTML物理削除を是正） |
| privacy | 公理15③/削除完全性 | `WebReadingProgressDao.kt:10-21`／`DefaultBookRepository.kt:87,94` | DAO に `deleteByNcode` 追加し削除経路から相乗り＋起動時 orphan 掃除。**既出 L72**（削除経路皆無）を Major へ格上げ・具体化 |
| privacy+measure **両面** | 公理15B②/22層§B | `PdfImportViewModel.kt:92` | DL URL(ncode)/Content-Disposition(書名)の logcat を定数化 or `if(BuildConfig.DEBUG)`。**既出 L64**（minifyEnabled false＝release に残る理由）と同根 |
| measure | 24層§E 回復パス発火 | `NovelReaderApplication.kt:87` | 起動リカバリの partition/順序を純関数抽出し JVMテストで固定（退行が緑を通る穴） |
| measure | 24層C#8 破損隔離 | `DefaultBookRepository.kt:198`／`PdfBookExtractor.kt:130` | `addBook` を `internal process(engine,…)` 経由へ差替可能化し fake engine で隔離を assert |
| d-token | ADR0014§A 字面SSOT | `BookCard.kt:113,227,345` ほか ui/ 161ヒット | `fontSize` 直書き(9.5〜16.5.sp)を typography.* スロット経由へ・sp突合を check_design_tokens へ追加（bulk 委譲候補） |
| d-token | ADR0014§C 離散スケール | `DiscoveryResultScreen.kt:247`／全域 | 任意dp余白(11/5/14/18/20/26)を離散(4/8/16/24/40)へ丸め SpacingTokens を彫る（bulk 委譲候補） |
| d-token+d-type **両面** | ADR0014§D/公理11 | `ReadingErrorScreen.kt:42,48` ほか | 意味テキストの `copy(alpha=0.75/0.7)` を削り InfoText/専用暗化シェード素値へ（AA割れ是正）。**既出 L29**（InfoText mock追従）と同系 |
| d-motion | 08 禁止則③ bounce | `Motion.kt:20`／`BookCard.kt:171,309` ほか | `dampingRatio` を `DampingRatioNoBouncy(1f)` へ（1箇所修正で4使用不変・本棚カードの跳ね除去） |
| d-chrome | Design/09D バー契約 | `NativeReadingScreen.kt:846-897`／`MainActivity.kt:75` | `WindowInsetsController.hide/show(systemBars())` を isChromeVisible と同フレーム駆動＋版面 inset を IgnoringVisibility へ |
| d-chrome | Design/09A 既定=無 | `NativeReadingScreen.kt:507` | 入場時 `heightOffsetLimit` で全退避し既定を「無」に（ChapterHeader が章題を担う） |
| d-chrome | Design/09F 消灯 | `NativeReadingScreen.kt`／`WebReaderScreen.kt` | 読書中 `DisposableEffect` で `FLAG_KEEP_SCREEN_ON`・onDispose で clear |
| reach | 21-C 到達性 | `NativeReadingScreen.kt:920-928` | 右上ギア撤去・表示設定の起動を中央タップ→下端シートへ（上端は Up＋章題のみ） |
| reach | 21-E 適応一次元化 | `BookshelfScreen.kt:569` | `GridCells.Fixed(2)`→`Adaptive(minSize)`（スマホ影響0・≥600dpで多列） |
| critic | UX/06⑥ 1フォーカス単位 | `BookCard.kt:315` ほか | カード行に `semantics(mergeDescendants=true)`（TalkBack が1冊を複数ノードに分割読み上げ） |

### Minor（32件・§B に evidence）
| 出所 | 場所 | やること |
|---|---|---|
| persist | `DiscoveryResultScreen.kt:385` | novels+paging も SavedStateHandle へミラー（プロセスdeath で積み上げ喪失） |
| persist | `WebReaderViewModel.kt:27` | Web再開が話冒頭までな旨を「第N話のはじめから」表記に（JS注入禁止で構造的・ADR0012へ明文化） |
| continuity | `BookCard.kt:54-96` | lastReadAt を READING カードに相対時刻で添える（「◯日前」の見当識） |
| add+notify **両面** | `BookshelfScreen.kt:152-165` | 通知権限に priming（理由ダイアログ）を挟む・取込文脈まで遅延も可 |
| add | `DefaultBookRepository.kt:143-204` | 取込前に `usableSpace`/`getAllocatableBytes` で空き容量チェック |
| errtext | `NativeReadingScreen.kt:442,275` | 章/目次読取例外の生メッセージ（絶対パス/ENOENT）を固定文言化・原因は Log.e へ |
| errtext | `PdfProcessingService.kt:376` | Encrypted/Corrupted は retryUri=null で『閉じる』のみ（決定的失敗に無効な再試行を出さない） |
| errtext | `PdfProcessingService.kt:225` | 「時間制限により中断」→「開き直すと再開します」へ短縮（FGS 実行上限は読み手に無関係） |
| ia | `DiscoveryResultScreen.kt:146` ほか | 「見つける/探す/検索/発見」4語を用語辞書1枚で統一 |
| ia | `BookshelfScreen.kt:582-643` | 本棚先頭の発見帯を撤去し🔍へ一本化（支配タスク先頭本の押し下げ）※モック正本 ADR0005 経由 |
| ia | `DiscoverySearchScreen.kt:270-321` | 範囲チップを既定(タイトル)で即検索・折り畳み or 結果画面へ（先出ししない）※意匠モック由来の可能性 |
| ia | `NativeTableOfContentsScreen.kt:216-246` | 目次の既読(index<current)をグレー+ウェイトで区別（currentIndex から導出・データ追加不要） |
| ia | `BookshelfScreen.kt:726-746` | 0件の状態チップを dim/非表示 or 各チップに件数 |
| gesture | `NativeReadingScreen.kt:960` | 復帰ヒント文言を「画面をタップでメニュー」へ（実領域＝全面と一致） |
| settings | `ReadingSettingsSheet.kt:121` | テーマチップに「システムに従う」追加・選択時 `remove("reading_theme")`（未宣言へ戻せる） |
| notify | `PdfProcessingService.kt:355-361` | ProcessLifecycleOwner で foreground 時は完了通知スキップ（本棚の反応に委ねる二重報告是正） |
| notify | `PdfProcessingService.kt:514` | 該当本を開いた/deep link 着地で通知 cancel（stale 通知の取り下げ） |
| notify | `PdfProcessingService.kt:467-489` | 取込進捗に `setOnlyAlertOnce(true)`＋progress 変化時のみ notify |
| portable | `BookEntity.kt:10,28` | 復元時 htmlDirPath を bookId から再導出・`contentSha256` を再結合キーへ昇格（C2 とセットで位置自動復帰） |
| a11y | `NativeReadingScreen.kt:793-839` | ReadingError/継続カードに `liveRegion=Polite`（非同期状態変化の告知） |
| a11y | `ChapterContent.kt:174-183` | 章タイトル・前/後書きラベルに `semantics{ heading() }`（見出しジャンプ） |
| evolve | `MigrationTest.kt:40-42` | MIGRATION_3_4 にデータ入り回帰テスト追加 or v3実機無しなら chain 削除で floor v7。**既出 L75**（16→17 coverage hole）と同系 |
| evolve | `MainActivity.kt:88,128` | prefs/DataStore に `settings_schema_version` を置く（enum 生保存の改名耐性・予防的） |
| d-token | `ShioriCover.kt:279` | 生 ARGB リテラル1件をヘアライントークン＋名前付き alpha 定数へ |
| d-token | `RubyText.kt:243,264,279` | @Preview の `Color(0xFF…)` を `ReadingTheme.LIGHT.colors.ruby` 参照へ |
| d-motion | `BookshelfScreen.kt:519-522` | バナー入退場を Motion トークン化・exit を enter より短く（reveal250/dismiss150） |
| d-motion | `NativeReadingScreen.kt:946`／`NovelDetailScreen.kt:186` | 復帰ヒント/題字 fade に crossfade トークンを渡し Motion.kt 経由へ |
| d-chrome | `WebReaderScreen.kt:84-104` | 読む面の chrome 規律を最低限近づける（媒体差 ADR0012 で大半正当・native側 A/D/F 是正が先） |
| d-type | `NcodeLinkSheet.kt:148,357` | placeholder/無効文字の `copy(alpha=0.6)` を専用シェードトークンへ（コード衛生・WCAG は概ね対象外） |
| reach | `ChapterContent.kt:142,73` | 本文最大幅 600.dp を「~40*fontSize」字数基準へ・行間 em も行長の関数へ |
| critic | `DiscoverySearchScreen.kt:217`／`NcodeLinkSheet.kt:121` | 検索/入力欄に恒常ラベル（画面内見出し or `contentDescription`）・placeholder は例示専用へ |

### 要検証（16件・実機/静的で確定不能＝`/device-verify` 送り。コード修正で閉じない）
- nav: 作品詳細の←が到達経路で別着地（発見ホーム↔結果一覧）。混乱を生むか実機目視→統一裁定なら Result 同型固定Upへ。
- persist: 回転直前の最終スクロールデルタ(≤400ms)が保存に間に合わずレース→章を読みつつ即回転反復。
- ssot: 章数が「chapファイル数」と「index目次数」の二経路導出（現状ロックステップで一致）→ 不変条件を testDebugUnitTest で固定。
- add: 取込WebView 初期ロード中の白画面露出→低速回線で実測、長ければ既存スピナー流用。
- gesture: 下端 BottomAppBar（章送り）がジェスチャナビ帯と近接し誤発火→3ボタン/ジェスチャ両式で確認。
- privacy: 日次で ncode 群を syosetu へ送信・停止トグルなし・既定ON（C3 と同根）→ 既定OFFオプトイン化＋Data safety 記載。
- a11y: 没入バー退避時 TalkBack が戻る/目次/前後章へ到達可能か→実機 TalkBack スワイプ走査。
- reach: 形態遷移（回転/折り畳み）で段落内 px オフセットが指す行がずれる→長段落で回転し何行ずれるか。
- evolve: v1/v2 スキーマ実機が実在すると移行未発見で起動時クラッシュ（fallback不在）→過去 v1/v2 投入端末の残存を人間知識で確認。
- measure: 大PDF/10倍蔵書/長時間送りの予算が漸進劣化、INTERNET無しで出荷後テレメトリ不能→Macrobenchmark 新設・P90/P99 で §F 予算を assert。
- d-token+d-motion **両面**: `Motion.kt:28` 400ms が 350ms 上限超（進行類型は免除余地大）→免除を ADR に明記 or 300へ。
- d-type: ルビの掛け・隣接ルビ間アキ制御なし→長ルビ実データで実機目視。
- d-type: 大フォント×広余白で1行字数が極端減（18sp≒18字/24sp≒11字）→各設定の体感リズム確認。
- d-chrome: 横向き/サイドノッチで行頭・行末がカットアウトに欠ける→ノッチ端末を横向き目視・`displayCutout` 合成。
- ia: 数百話作品で目次が部/編で畳めずフラット→実PDF→HTML の階層有無を確認。
- critic: 章題ブロックの余白逆転(top14<bottom26)→章オープナー意匠として意図的か確認・ADR記録。

### 人間テスト送り（7件・UX/17 プロトコルでタスク化。AI/実機で確定不能）
- nav: 通知テレポート着地直後に書名が無く章題が汎用文言のとき「どの本か」特定できるか→迷えば chrome に書名常設。
- settings: 「この本の読書画面」の設定でダークを選ぶと本棚も暗くなる。「この本だけ変えたつもり」の誤解を持つか→見出しで全書籍スコープ予告。
- gesture: 2週間後・無説明でネイティブ読書↔Web読みを交互に触らせ操作言語の混乱があるか（Web別モードは許容例外候補）。
- gesture: 2週間後・無説明で「メニューを出して」→通算初回のみのヒントで消えた中央タップトグルを再発見できるか。
- d-motion: 開発者オプション「アニメスケール0」でカード押下バウンス/バー settle/バナーが即時化するか目視。
- d-chrome: 少し上スクロールで上下バーが自動復帰し本文上端を覆う挙動を惜しむ声が出るか。
- ia: 各入口（目次/本棚/deep link/history）→本文の位置/スワイプ/継続挙動が同一か（読書継続性と重複可）。

### 良い点（維持・触るな。守れている公理）
1. 公理6 永続性の模範＝`NativeReadingScreen.kt:133-183`（rememberSaveable+DB正本+ON_STOP フラッシュ・章一致時のみジャンプ注入）。
2. 公理3 べき等性の三層防御＝ActiveUriTracker+contentSha256+title/author 照合（機械検証済み）。
3. 公理5 SSOT＝BookshelfViewModel CONFLATED 単一チャネル＋readingStatusFor 単一計算（※ssot Major の 1f は別途是正）。

> **統合メモ（束ね/棄却の詳細）**は §A §6。REFUTED 1件＝notify「お知らせチャネル無し」（実在ゆえ棄却）。ia「検索経由 vs 目次経由の本文分岐」は FTS 不在で不成立→人間テスト送りへ横移動済み。

---

## 本棚 書架（グリッド）ビュー — 栞書影 ✅全意匠課題消化（2026-07-12）

> 実装完了の詳細は **STATUS §1「栞意匠」項＋「栞整合」項が正本**。意匠の正本＝`docs/design-candidates/bookshelf-shiori-grid-D.html`（生成規則・スケール補正の詳細は `ShioriCover.kt` コメント）／整合＝`bookshelf-shiori-consistency-D.html`／色域確定記録＝`bookshelf-shiori-palette-D.html` フッター。
> 2026-07-11 オーナー裁定「本棚モックはすべて確定」を受け、残っていた3課題を消化:
> ①リスト⇄グリッド色相共有＝**実装完了**（2026-07-12・consistency-D 翻訳） ②色域＝**現行（全周和リング）維持で確定**（palette-D フッターに記帳） ③他4型（A箔/C小口/D蔵書印/E綴じ紐）＝スキン資産として保持（ADR 0005 C 方針どおり・「A2. UIスキン着せ替え」参照）。
> 探索の記録（採用前の参考・正本ではない）: `bookshelf-geo-D.html`／`bookshelf-generative-directions-D.html`／`bookshelf-shoka-D.html`／`bookshelf-cover-D.html`。

## UI/UX 宿題

- **[モック追従・構造] 発見系モックの情報/装飾テキスト再分類**（2026-07-12 `a9a6a5c` 実装時に留置）: `InfoText` トークン（実装済み＝発見系の情報メタ6箇所を AA(4.5:1) へ引き上げ・Light #5C606D／Sepia #6C6148／Dark #8A929B）の discovery/*.html モックへの追従は、`--ink-soft` を共有する **10〜16箇所/ファイルの情報・装飾テキストの個別再分類**＋`--info-ink` 変数の新設＋`tools/check_design_tokens.py` へのマッピング追加が必要＝構造的大改修と判定し留置。**現状の一致検査は InfoText を未トラッキングで PASS＝この層ズレ（コードが AA へ引き上げた面をモックが `--ink-soft` のまま持つ）は未検知**になる点に注意。

## なろうAPI 発見・検索機能（第2の柱・Phase 4/5 残り）

> なろう公式APIの発見機能を「第2の柱」に育てる計画（案A＝本文非取得・メタのみ）。Phase 0〜3＋Phase 4 スライス1 は完了・main 統合済み（現況は `STATUS.md` §1）。
> 目標ロードマップ・作る機能一覧の一次情報は plan `~/.claude/plans/api-agy-woolly-swan.md`。監査残課題（構造系）は下の「リファクタ / 技術的負債」へ移設済み。

- **Phase 4 完了（2026-07-10）**: 全項目消化＝STATUS §1 参照。
  - ~~(b) Web由来・未取込カード~~ → **完了**（2026-07-09 `a6569ee`+`15d9e1a`・実機目視OK＝STATUS §1）。
  - ~~U1 新着話チェック＋通知~~ → **完了**（2026-07-10 `2789512`+`0b2d2b7`・実機E2E全GREEN＝STATUS §1。強制発火の罠は task_diary #53）。
  - ~~U2 整理（ラベル分類）~~ → **完了**（2026-07-10 `30762aa`+`a7e403e`・Room v14・実機目視OK＝STATUS §1）。※その後 **2026-07-11 に機能ごと撤去し読書状態フィルタへ置換**（STATUS §1 先頭）＝「Web由来カードへのラベル付与」将来拡張は消滅。

## リファクタ / 技術的負債（deferred）

- ~~[発見系・構造] `SearchConditionSheet` の残リファクタ（監査残課題2の残り）~~ → **解消済み**（2026-07-11 `1b83684`＝カスタム範囲入力 約90行×2 を `CustomRangeInput` へ部品化・段階チップの値とラベルを `RangeStep` 対定義（`SearchDraft.kt`・既存 `LENGTH_STEPS`/`TIME_STEPS` は射影で温存＝呼び出し側無改修）へ統合。764→669行・見た目/挙動不変＝STATUS §1。※当初「discovery-search-D すり合わせ時に同ファイルを触るとき」予定だったが refactor/tech-debt レーンで先行消化。**モック追従＋案B翻訳のタスク（上記）自体は残存**）。
- ~~[発見系・将来の罠] `NovelApiRepository` のインメモリキャッシュの Main dispatcher 前提（監査残課題5）~~ → **解消済み**（2026-07-09 `13c97f2`＝Mutex 排他＋word trim 非対称の修正。U1 Worker 化の前提として先行実施）。

- ~~`saveScrollPosition(bookId, filename)` 等の String 連続の型付け~~ → **解消済み**（2026-07-11 `013b06a`＝`BookId`/`ChapterFilename` value class 新設（`model/BookIdentifiers.kt`・Ncode と同じ素通し方針）で saveProgress/saveScrollPosition/linkNcode/getLastRead/getProgress を Repository/VM/UI 貫通で型付け。Room Entity/DAO・Map キー・nav ルート文字列は String 維持＝境界 `.value` unwrap（線引きの why は BookIdentifiers.kt の KDoc）＝STATUS §1）。
### コード健全性監査の指摘（2026-07-11・`refactor/tech-debt` で6観点並列監査・挙動バグ3件は反証専門エージェントで CONFIRMED 済み）

> 監査体制: 観点別6エージェント（並行処理/エラー処理/Room/デッドコード/Compose/API・テスト）＋描画性能2＋検索画面深掘り1＋敵対的検証3。**クリーン確認済みの面**: デッドコード・撤去残骸ゼロ（TODO/FIXME 0件・未使用リソース/依存なし・ラベル/Chaquopy 残骸なし）・直近リファクタ4コミット退行なし・読書画面の描画設計は高水準（ルビ=1段落1BasicText+Canvasオーバーレイ・バー退避=graphicsLayerラムダ・スクロール観測=snapshotFlow・パース/AnnotatedString/TextStyle は remember 済み＝「ルビ数万ノード」「フォント変更で全文再パース」は不発生）。

**確定バグ（検証CONFIRMED・3件とも解消済み＝2026-07-11 一括消化バッチ・STATUS §1）**

- ~~**[クラッシュ経路] 検索履歴 DataStore の IOException 未処理**~~ → **解消済み**（`5815802`＝corruptionHandler＋読み Flow の IOException 空履歴フォールバック＋書き込み側許容。※監査記述と異なり CorruptionException は IOException のサブクラスだが、`.catch` では破損ファイルが残り毎回失敗し続けるため恒久復旧に corruptionHandler が別途必要——両対策で正）。
- ~~**[レース] pending_jobs 記帳の直列化が実は不成立**~~ → **解消済み**（`cccb4dc`＝DAO 呼び出し完了までロックを保持する `pendingJobMutex` で全 pending_jobs 書き込みを排他・`limitedParallelism(1)` 撤去。機序の一般知見は task_diary #55）。
- ~~**[FGS] onTimeout 後の旧ループ finally が新ループを道連れ**~~ → **解消済み**（`f70b937`＝ループ世代カウンタ。採番を launch 前の main スレッドで確定・閉じ込め、旧世代の finally は新世代の isLoopRunning/stopSelf に触れず退場。通常経路は世代が進まず挙動完全一致）。

**検索画面の重さ（ユーザー実体感あり・診断確定）**

- 正体＝「**カテゴリ展開状態での操作毎の全画面再コンポーズ**」（既定の全畳みは軽い＝「重いときがある」と整合）。キーワード22カテゴリ/115チップが非 Lazy Column（`DiscoverySearchScreen.kt:203-207`）上にあり、`SearchDraft` が Set 内包で unstable＋strong skipping 無効のため**毎キーストローク最大115チップ全再コンポーズ**、さらに選択判定 `containsWordToken` がチップ毎に Regex 再コンパイル（`SearchDraft.kt:223-224`・メモ化なし）。展開状態は rememberSaveable 保存→全展開のまま再訪すると**初回オープンから重い**第二経路。アニメ・履歴 DataStore・キーワード定義構築はシロ。
- 修正順: ~~**S1** 選択判定を `remember(draft.word)` の Set 化＋Regex をトップレベル定数化~~ → **解消済み**（`a3662c2`・全ケース同値テスト付き）→ ~~**S2** `experimentalStrongSkipping=true`＋`@Immutable`~~ → **解消済み**（`b1c0bfc`・stability レポート機械検証に加え **2026-07-11 実機全画面スモーク GREEN**＝本棚フィルタ/検索チップ/結果並替/詳細キーワードトグル/発見タブ/テーマ一括切替/目次で stale UI ゼロ・検索体感は軽快）→ **S3** 外側を LazyColumn 化（中・画面外チップの存在コストと全展開再訪の初回構成。**S1/S2 実機体感=軽快（2026-07-11 実測・引っかかりなし）を踏まえ要否判断**＝残。体感問題が再報告されるまで保留が妥当）。

**描画/ビルドの軽量化（読書画面以外）**

- **R8/リソース収縮が無効**（`android/app/build.gradle:22-25` `minifyEnabled false`・`shrinkResources` 無し）: 有効化が**単独最大の軽量化レバー**（APK 24MiB の dead code/未使用リソース分）。Moshi/Room は codegen/KSP で keep は軽微見込みだが PDFBox/Retrofit/OkHttp の keep 確認＋実機回帰（`/device-verify`・収縮起因クラッシュはリリースでしか出ない）必須。
- ~~ルビ描画パスの Paint×2 再生成＋`calculateRubyPositions` 再計算（`RubyText.kt`）~~ → **解消済み**（`8f452a3`＝Paint/ascent の remember 化＋位置計算を TextLayoutResult×rubyRanges のインスタンス同一性でキャッシュ）。
- ~~小粒: BookCover Brush・段落 TextStyle・LazyColumn key/contentType~~ → **解消済み**（`96a4c22`。※本文段落リストは一意な安定IDを持たないため key は付けず contentType（4描画種）のみ＝非一意 key の状態破壊を回避した意図的判断）。

**その他（中〜低）**

- ~~`deleteBook` 非トランザクション~~ → **解消済み**（`862954e`＝Room withTransaction の関数注入で原子化・HTMLディレクトリ削除はロールバック不能なファイルIOのためトランザクション外の既存設計を維持）。
- ~~**Web読書位置の ncode 正規化がテスト不能**~~ → **解消済み**（`3b151b2`＝保存する FakeWebReadingProgressDao を注入し、往復一致＋表記ゆれ正規化一致＋保存キー自体の正規化を回帰テストで固定。※webReadingProgressDao は既にコンストラクタ注入対象だった＝実際の穴はテスト側の未注入）。
- `web_reading_progress` に prune/削除経路が皆無（upsert のみの単調増加。`removeWebNovel`/`deleteBook` とも触れず、new_episode_marks の日次 `pruneExcept` と非対称）。個人スケールでは無害だが設計の穴として記録。
- ~~`novelDetailsBulk` の紐付け501件超サイレント欠落~~ → **解消済み**（`505dd03`＝500件チャンク分割・境界テスト2件付き）。
- ~~OkHttpClient がタイムアウト既定依存~~ → **解消済み**（`73246e5`＝API は callTimeout 30秒・PDF DL は connect 30秒＋read 60秒で「無進捗の停滞」のみ切る設計＝正当な長時間 DL は殺さない）。
- MigrationTest が「16.json 形状（web_reading_progress 無し）→17」経路を構造的に検証できない（chain テストは 14→15 でテーブルが生まれる系譜のみ通過）。既知の実機 v16→v17 未検証と同根の coverage-hole として記録。
- ~~`AndroidManifest.xml` INTERNET permission コメントの実態不整合~~ → **解消済み**（`20bf2ca`・文言のみ修正）。

- **worktree(ext4) 作業の冒頭で `gw :app:lintDebug` を回す運用**: Lint コミットゲート（`check_lint_on_commit.py`）は drvfs でスキップされる設計＝canonical 作業が続く限り事実上無効。ext4 worktree なら in-tree で回るので冒頭で1回スイープする（直近スイープ＝2026-07-12・UI/UX 宿題4件消化後も 0 errors/26 warnings＝基準同一で新規指摘なし。前回=2026-07-11・監査指摘12コミット後も 0/26。前々回=2026-07-08・0/21）。

## workflow / tooling

- **antigravity-delegate サブエージェントの同期実行が保証されない**（2026-07-07・委譲5件中3件で再発）: agy をバックグラウンド起動したまま「待機中」で終了し完了通知が来ない。プロンプト明記・SendMessage 再開でも再発。運用回避（CLAUDE.md 委譲判断節に反映済み）＝完了判定を報告でなく**成果物の存在**（`git status`/grep/`ps`）で行う。**根治候補**＝プラグイン側で agy 起動を同期実行へ強制するか wrapper にポーリング内蔵。優先度中（運用回避が効き非ブロッキング）。

## 実行捏造検知器（ADR 0006）残タスク

> エンジン＝`.claude/hooks/detect_fabricated_execution_core.py`。完了分（Stop ライブ化・Tier C misread 型・Tier D 入力側捏造・陽性コントロール）は STATUS/ADR が正本。以下は開きのみ。

- **Tier B 汎用主張の免罪の限界**（事象D）: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす。具体値主張は具体照合に絞ったが汎用主張の掘り下げは将来課題。**進捗（2026-07-11・増補6）**: Tier E カテゴリ別突合が**現ターン分**の同根系列（照合キー無しの汎用完了主張＝write/cleanup/test/create/config）を吸収した。**過去ターンの汎用主張の掘り下げは引き続き将来課題**（Tier E は現ターン境界での判定）。
- ~~**[2026-07-11 実測・台帳N] 幻の先行実行（phantom prior execution）型が全 tier(ABCDE) の検知外**~~ → **大半解消（2026-07-11・増補6）**: Tier E をカテゴリ別突合へ細分化（write/cleanup/test に **create/config** 新設＝「tool_use 皆無」を「主張カテゴリに対応する tool_use の不在」へ一般化＝別 tool_use 同居ターンの L81 も発火）＋`phantom_probe_output`（出力異常フレーム＋トークン非接地）新設で **L51/L65/L81 が active 化**（詳細=ADR 0006 増補6）。**残る L44/L55 型は別クラスとして下に新規登録**。
- **[2026-07-11・増補6 で切り出し] L44/L55 型「先行実行フレーミング」（完了主張形を持たない幻の先行実行参照）の検知**: 事象N の L44「出力が返っていないので結果を確認します」（存在しない先行 tool_use への参照）・L55「再作成します」（未実行の再作成宣言）は**完了・検証の断言ではなく段取り宣言**＝照合キーが無く Tier E の完了語トリガに当たらない。検知には「主張以前に対応する tool_use が無い先行実行参照」という別系統の突合が要る。要較正・真陽性は台帳N の L44/L55。
- ~~**サブエージェント/オフロード全文の裏取り強化**（現状は読めなければ降格）~~ → **解消（2026-07-11・増補6）**: read_offload を **tool_result 本文の `Full output saved to:` パス駆動**へ修正（旧 `<tuid>.txt` 命名前提の解決率 1/31→12/31）。埋め込みパス保有分は 12/12 全解決・残19は全文ファイル自体が不存在の別クラス＝truncated 維持が安全側で正。subagent 解決は実測 214/214=100%（多重フォールバックは投機的堅牢化として却下）。詳細=ADR 0006 増補6(3)。
- **[2026-07-11・増補6 保留設計] 案3＝委譲主張の E2 突合（opt-in）**: 「〜を委譲した／agy に生成させた」等の委譲完了主張を、**委譲先 transcript（`subagents/agent-<id>.jsonl`）の tool_use とカテゴリ突合**して裏取りする案。read_offload パス駆動化の検討中に案出したが、**真陽性サンプルが皆無のため保留**（設計要点のみ保全）。真陽性が観測されたら着手。
- ~~**Tier D の K型盲点・残り**（2026-07-08 台帳K）: 暴走 thinking 前兆を伴わない入力側捏造は `no_thinking_anomaly` で降格し active 化できない~~ → **解消（2026-07-11・増補6）**: 候補①を **D4 `phantom_turn_role_marker`**（行頭ロールマーカー構造・thinking 非依存・conf 0.85）として実装＝事象K L320 が active 化＋**未検知真陽性 370800c1 を新規捕捉**（`[Request interrupted for tool use]`＝"by user"無し変種→A3 の `HARNESS_BLOCK_RE` も同変種へ拡張・人間承認済み）。候補②（降格ゲート再設計／thinking 空セッション解禁）は**不採用**＝D3 単独は較正実測で正当作業の約20%誤発火・空セッション解禁は同FPクラスを復活させる。K型は D4 が thinking 非依存で拾うため盲点残らず。L328（幻叱責への謝罪＝下流反応）は精度優先で対象外＝幻生成そのもの（L320）を拾えば足りる。詳細=ADR 0006 増補6(2)。
- **[2026-07-11・増補6 副産物] 370800c1 の台帳レター事象化（人間確認待ち）**: D4 較正の slug 全走査で発見した未登録真陽性＝assistant text 内に `user[Request interrupted for tool use]`＋幻のユーザー発話を自己生成（幻発話テキストの実人間入力不在は transcript 直読で確認済み）。D4（CLI）と A3 変種拡張（live）の両方で active。人間確認が取れたら台帳の追記手順（事後検証モード）でレター事象化。
- ~~**[2026-07-08 実測・未対処] 事象M＝phantom-attribution（幻の同意帰属）が Tier D の語彙穴**~~ → **解消（2026-07-11・増補6）**: **D5 `phantom_agreement_attribution`**（同意帰属マーカー＋帰属対象語が実人間入力に不在・conf 0.75・軸2ゲート不使用＝突合が判定力を担う）として実装＝事象M L29 が active 化。slug 全走査で新規FP 0（e4367031 の分析引用4件は meta_discussion 降格＝意図どおりの分離）。詳細=ADR 0006 増補6(2)。
- **[2026-07-11・増補6 残課題] D5 対象語突合の字面依存FPクラス**: D5 は帰属対象の名詞（違和感/懸念/指摘…）の**字面**を実入力に探すため、ユーザーが真に指摘したが当該名詞を書かなかった場合「あなたの指摘は的を射て」型が潜在FP化しうる（現コーパス実測 0件・非ブロック Tier D で被害限定）。同義語・意味突合の導入は将来課題。
- ~~**[2026-07-08 実測・未対処] メタ議論免罪が「検知器の実装用語」文脈で漏れる**~~ → **解消済み（2026-07-11・`meta/detector-improve` d80190c の統合＝`_tier_b_reference` を Tier B 本線へ組込み）**: 対処2層＝(a) 成功語の引用体裁判定へ **〈〉『』を追加**（「」のみだった） (b) **機構語限定の `DETECTOR_IMPL_TERM_RE`** を主張文近傍±120字の密度判定に合算。v3.1/v3.2 のインライン免罪（`meta_discussion`/`quoted_claim`）が拾えない〈実装用語で語られる仕様説明文・〈〉列挙〉の補完として `detect_tier_b` へ組込んだ。**較正知見（重要）＝語彙は機構語（完了主張/カテゴリ/発火/tool_use/検査窓/主張文/current_turn 等）に限り、結果・成果語（降格/昇格/偽陽性/真陽性/誤検知/Tier）は入れてはならない**——真陽性 b4087931（事象L）自体が検知器開発セッションの完了報告で結果語が近傍に密集するため、入れると真陽性が免罪される退行（probe 実測）。`META_DISCUSSION_RE` 本体は D 系免罪6箇所で共用のため無改変。検証＝same-corpus 206ファイル走査で当該FP（e4367031）のみ降格・真陽性19件不変。詳細=ADR 0006 増補5。
- ~~**[2026-07-11 統合・較正待ち] Tier E（完了主張束・事象L型）の既定化/Stop 昇格判断**~~ → **判断完了（2026-07-11・増補6・人間承認済み）**: カテゴリ別突合への細分化で真陽性 n=1→**4**（事象L＋N×3）・全コーパスFP 0 の較正実績が揃い、**CLI 既定 tier を ABCD→ABCDE へ格上げ**。**Stop 昇格は見送り**＝E の conf 0.55-0.7 は Stop 閾値 0.8 未満（下記の再判断項へ）。詳細=ADR 0006 増補6(1)(4)。
- **[2026-07-11・増補6 で切り出し] Tier E の Stop 昇格の再判断**: 新既定 ABCDE での CLI 運用実績（真陽性の積み上がり・FP 率）が揃ったら再判断。昇格には conf 設計の引き上げ（現 0.55-0.7 → Stop 閾値 0.8）または Stop 側の per-rule 閾値の新設計が必要。
- ~~**c4b78e7d rec#146 の「厳密検証」報告の HEAD SHA 捏造＝新事象候補（人間確認待ち）**~~ → **解消済み＝2026-07-11 事象 O として台帳登録**（`docs/reference/hallucination-ground-truth.md` §O）。**クロスセッション経路も判明**＝実マージ 4650e2b は別セッション `a77a8a10` が 22:17 JST（捏造報告 20:47 の約1.5時間後）に実行、当該 c4b78e7d では L109 ブロック後にマージ再実行なし＝報告時点で 4650e2b は不存在。検知は現行検知器で active 4件（`fabricated_concrete_token` missing=9f3c2e1〔Tier A〕＋`completion_after_blocked_commit`×3〔Tier C〕）と実測＝**検知器が捕捉できた台帳事象**。
- ~~メタ議論免罪の Tier A2/C1 への未適用~~ / ~~Tier A2 の直前 tool_result 由来トークンFP~~ / ~~実機テストランナーの非認識~~ → **3件とも 2026-07-09 v3.2 で解消**（STATUS §0 参照。機序2件は当初記録と異なると実測で判明: 891df1e6 は `feedbac` 誤抽出・bcd69bb6 は gitStatus 由来＝台帳の偽陽性ログに訂正記録。441b9875 は実 instrument 成功直後の完全FPと transcript 直読で確定済み＝人間確認不要）。
- **意味照合系検知器**（着想段階・スコープ外構想）＝生成コード不具合・外部リサーチ捏造（正解データ事象B/C）。

## D. 長期・品質（backlog）

- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`（main 未取り込み）。
- **[抽出] 単話（1話完結）作品の縦書きPDF変換で、本文が「作品情報（プロローグ）」側に乗り章題名も出ない**（2026-07-09 PDF取り込み導線の実機通し検証中にユーザー観測・対象 n2959ki）: 単話作品は章見出し／目次構造が無いため、章分割が本文を作品情報ページの続き扱いで流し込むと推定（**未確定**・要調査）。**やること**: ①n2959ki の抽出結果現物（`novels/<id>/index.html`・`chap_N.html` 構成）で事象を再現確認 ②単話 PDF の構造に対する分割ルール（`ParserRules`/`ChapterProcessor`）の扱いを設計 ③**ゴールデン基準との整合に注意**＝N2959KI はゴールデン本（`ab-review/golden_regression`）であり、基準自体がこの挙動を「正」として固定している可能性がある——修正はゴールデン更新とセットで判断すること。
- **超長編抽出エッジ残差の③アポストロフィ座標順**（N6169DZ・章題ドリフト残2件）: `兎'ｓ`↔`'鳥…` の座標順ずれで**1:1コードポイント置換不可**＝実質 won't-fix。基準＝`ab-review/golden_regression`、詳細＝task_diary #35。①②のダッシュ/矢印9件は 2026-07-06 に `normalizeGlyphUnicode` で解消済み。

## A2. UIスキン着せ替え（将来送り・保留）

> フェーズ0で D「和モダン・余白」をデフォルト視覚言語に採用済み（設計判断＝`docs/decisions/0005-ui-n-visual-language-D.md`／モック地図は `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md` §6.1）。

- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design（プロジェクト `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`・`DesignSync: get_file` で再取得可）に保持。
- **着手時はここから**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。bookshelf-D へのセピア変種追加もスキン着せ替え実装時に再検討（現状は `SepiaColorScheme` が本棚セピアの正本）。

## 掃除

<!-- 検証残置データ（web_reading_progress 2行）とテスト用シード本 spike-* は 2026-07-11 にユーザー裁定のうえ掃除済み（詳細=STATUS §1 実機検証スイープ項。N9754MK 1行のみ実害なしで意図的残置）。 -->
