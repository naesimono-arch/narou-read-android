# STATUS — 現況台帳（正本 / main）

> **今どうなっているか** の正本。状態・完了済み・既知不具合を記録する。
> **次に何をやるか**（作る予定・思いつき・取りこぼし）は `handover.md`。
> 詳細な一次情報（実コミット・座標・logcat証拠）は `.claude/plans/` のアーカイブhandoverに残し、ここからは参照リンクで誘導する（二重メンテ回避）。

## 0. 現在の状態（一次情報）

- branch=`main` / 統合ノード=`fd758df`＋後続docs2件（2026-07-08 に API系全ブランチを `integrate/merge-all-20260708` 経由で --no-ff 統合＝`api-lab-ai-2`(発見・検索の全実装＝トランク＋chrisbanes残指摘対応6コミット)＋`api-lab-ai`(Custom Tabs導線)＋`api-lab-ai-3`(なろう規約線docs)。`api-lab` は api-lab-ai に完全包含。統合に伴い **Room を v10 へ再退避**（実機の branch 版 v9 hash との合併衝突回避＝`236b32c`・task_diary #39 追補）、task_diary の二重採番3件を解消（なろうAPI系 #42→#46・#44→#47／フック配線側 #39→#48＝移設マッピング表）。未push＝origin より ahead 76。前身の統合ノード＝`788a18f`（2026-07-07・meta/tooling-improvements＋processing-resilience＋bookshelf-reflow-anim）・2026-07-06＝exec-fabrication-detector＋kotlin）
- **発見・検索機能（なろうAPI系）の現況は `STATUS-api-lab.md` が正本**（2026-07-08 統合で main に入った第二の現況台帳。★次アクション＝Phase 4 残り／監査残課題もそちら。本ファイルは全体の現況、api-lab 系の詳細はあちら）。
- **✅ `meta/hallucination-classifier-v3`（2026-07-07）＝実行捏造検知器 v3: 入力側捏造（正解データ事象H・I）対応の Tier D 新設**:
  存在しないユーザー発話を捏造しそれを根拠に行動する新型（幻の叱責への謝罪・不存在発話の引用符付き引用・幻の不具合報告での指示違反ピボット）への対応。v1/v2 の Tier A/B/C は全て出力側（実行報告⇄実結果）の照合で、入力側は構造的にカバー外だった。①実在人間入力の索引化 `collect_human_inputs`（user human 入力［task-notification 等ハーネス著者の user-str は除外］／queued_command(origin.kind=human)／**AskUserQuestion 回答**［含めないと正当応答が偽陽性化＝較正実測］／summary）②Tier D 3ルール（D1 `fabricated_user_quote`＝引用の正規化部分一致突合／D2 `fabricated_user_report`＝重要数値[2桁以上 or 小数]を human∪主張以前の result 層と突合／D3 `phantom_user_response`＝冒頭の同意・謝罪マーカー×直前入力区間の human 欠落）③軸2＝thinking signature 異常（G/H/I 共通前兆・通常比5〜30倍）は較正実測により**単独ルール化せず** D3 の active 昇格条件（baseline=先行発話 sig の p25、`sig≥max(15000, baseline×8)`）に限定 ④メタ議論降格（台帳・検知器を扱う本リポジトリでは実例引用が最大 FP 源）とクロスセッション参照降格。**検証**: 正解データ H 2/3（L43 パラフレーズ型は突合不能で設計上対象外＝台帳も加点扱い）・I 1/1・slug 全128ファイル走査（8MB超7本含む）で偽陽性ゼロ・**新規検知1件（177f88f3 L195＝人間レビューで確定し台帳 J 事象として登録・検知器起点の初事例・入力側捏造の初出は 2026-07-02 に遡ると判明）**・既存 A/B/C 判定は全セッションで不変・フックテスト97件緑。Stop フック昇格は未実施（CLI 運用実績を見てから＝段階導入）。詳細=ADR 0006 増補2／handover 検知器節。
- **✅ `meta/hallucination-detector-tune`（2026-07-07）＝実行捏造検知器 v2: misread 型（正解データ事象F）対応の Tier C 新設**:
  ペアは在るが報告が実結果と食い違う捏造への対応。①証拠の2層化（SHA・出力シグネチャの存在照合は tool_use.input とエコーバック行を除いた **result 層**のみ＝「捏造 SHA を自分で `git show` 調査すると自己免罪される」機序の遮断）②GIT_CONTEXT_RE にマージ/merge/ブランチ等を拡張 ③Tier C 4ルール新設（`completion_after_blocked_commit`/`fabricated_output_signature`/`unverified_write_claim`/`unverified_branch_delete_claim`・存在照合は主張**以前**の証拠のみ＝自己訂正のやり直し出力による免罪防止）④Tier B の gradle 件数免罪（c05efed0 偽陽性対策）⑤Stop フックへ Tier C 全4ルール＋A2 を昇格配線。**検証**: 正解データF 5/5 検知・全106セッション走査で偽陽性ゼロ・フックテスト100件緑。**事象E（対話捏造）は agy 意味監査で運用**（ブラインド実証済み・手順は ground-truth 追記手順 3.）。詳細=ADR 0006 増補／handover 検知器節。
- **✅ `feat/processing-resilience`（2026-07-07 統合）＝handover A の残り2件を実装（JVM113緑＋実機3/3合格・OPPO PGEM10）**:
  ① **停止ボタンをページ境界の即中断へ再配線**: 処理中の1冊を子 Job（`currentBookJob`）で起動し `ACTION_STOP` が cancel → `addBook` の進捗コールバック内 `ensureActive()` が次のページ境界で中断（ループ Job ごと cancel しないのは cancel〜finally 間の ACTION_START 取りこぼしレース回避）。停止時は ongoing 通知を `STOP_FOREGROUND_REMOVE` で確実に除去。
  ② **強制終了（OEM kill/OOM/onTimeout）時の通知＋再開**: Room **v7→v8** で `pending_jobs` 新設（enqueue で記帳／成否確定で削除／明示停止は全消し／記帳は `pendingJobDispatcher`(並列度1)で直列化）。SAF 選択時に `takePersistableUriPermission` 取得。起動時リカバリ `runStartupRecoveryOnce`（MainActivity.onCreate トリガー・プロセス毎1回）＝孤立HTML掃除→未完了ジョブ検出→snackbar 通知＋権限が生きる分を FGS 再投入。
  実機3/3（v7→v8 migration・停止2秒以内即中断・強制終了→再起動で自動再開完走）の詳細ログ・機序は handover A／task_diary 参照。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**。Chaquopy/Python は Phase 5（2026-07-05）で完全撤去＝`git revert` での即復旧は不可（git 履歴からの復元は可能）。APK 67.3→24.2MiB。
- テスト: `testDebugUnitTest` **301件緑**（2026-07-08 `handover/task-sweep` 時点。Robolectric の Compose UI テスト＝ADR 0009 を含む。統合直後時点は 113件）。実機の恒久精度回帰ゲート＝`PdfExtractorDeviceSpikeTest`（N6169DZ 含む3件通過済み）。
- 端末DB=`user_version 10`（2026-07-08 実機スモークで 9→10 通過・蔵書生存確認済み）→**コードは v11**（2026-07-08 `handover/task-sweep`＝F-G 恒久策で `books.contentSha256` を追加・`MIGRATION_10_11`＝ADD COLUMN のみ）＝次回実機 install で 10→11 migration が走る（**実機通過は未確認**＝handover「実機スモーク」項参照）。⚠️ **旧APKへ逆走すると `migration N→N-1 not found` でクラッシュ＝逆走禁止**（古い→新しいの一方向のみ）。
- 既知バグ: なし（**#1 ルビ位置ずれは 2026-07-02 解消**＝`90d037a`。根本原因と1.6系APIの制約は `task_diary.md` #43）。
- 実機: OPPO PGEM10 `192.168.1.210:5555` 接続済み（切れたら `adb-bridge`）。検証ワークフローは `[[workflow-autonomous-device-verification]]`（Claudeがadb自律駆動）/ `[[workflow-notify-each-step-visual-check]]`（各ステップで目視関門）。
- Kotlin 移植（Phase 1〜5）の経緯・実機検証の詳細 → §1 先頭項＋一次情報 plan `.claude/plans/kotrin-branch-python-kotrin-graceful-flute-archived-2026-07-06.md`。

## 1. 完了済み

- **handover 一括消化スイープ（2026-07-08・`handover/task-sweep`・並列サブエージェント4ウェーブ＝計10コミット・`testDebugUnitTest` 301件緑）**: UX監査繰り越し4件（F-G恒久策/F-J/M12/F-F）＋chrisbanes 系統レビュー残（系統1※読書画面除く・系統4・系統5）＋テスト投資①②③を消化。**副産物＝実バグ1件発見・修正**（空の本棚で透明な Lazy コンテナが「PDFを追加する」CTA のタップを hit test で遮蔽＝新設 Robolectric テストが検出。`c006f51`）。実機確認の残りは handover「実機スモーク」項。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | 系統1完遂(discovery) | `3234716` | discovery 4画面を route/Content 分割＋Content UIテスト12本（Genre は元から stateless） |
  | 系統4 Ncode | `07050ea` | `@JvmInline value class Ncode` 導入（Room/Moshi/Retrofit 境界は String 維持の段階導入） |
  | 空棚CTA遮蔽 fix | `c006f51` | 空棚時に Lazy コンテナがタップを奪う実バグを排他分岐で解消＋BookshelfContent テスト |
  | 系統1第一弾(本棚) | `2cd372e` | BookshelfScreen→route/Content 分割・NcodeLinkSheet の検索を BookshelfViewModel へ吊り上げ |
  | F-J ページング | `c66c913` | 「さらに読み込む」フッタ・`discoverPage`(st≤2000/lim≤500 上限検出)・PagingState 5状態・offset込み6hキャッシュ |
  | task_diary #50 | `9e5d51e` | Robolectric×ModalBottomSheet 不安定の機序と Content 分離の対処 |
  | テスト投資①②③ | `89fbe7a` | Robolectric 導入（ADR 0009）＋葉Composable UIテスト21本＋分岐@Preview 13本 |
  | 系統5 SSOT | `7d135ba` | 検索カスタム文字数/読了時間を SearchDraft へ一本化＋条件シートを SearchConditionSheet.kt へ純抽出（1348→606行） |
  | F-G 恒久策 | `5e1ec82` | Room v10→v11（`contentSha256`）・PDF内容ハッシュで別URI同内容の再取込を変換前遮断・MigrationTest 7→11 追従 |
  | F-F/M12 | `3b88075` | 読書画面シート開閉 rememberSaveable 化・没入ヒントを prefs で通算初回のみへ |

- **UX監査バックログ28件 全件修正・検証完了（2026-07-08・ui/polish）**: UX・導線フル監査で確定した指摘 **Critical 2/Major 14/Minor 12** を8実装エージェント＋検証2（全数突合・敵対的退行レビュー）＋フィックスアップ1で解消。**検証体制**: 全数突合で28件全て CONFIRMED（当初 PARTIAL の M1/M9＝読書画面継続カードの Custom Tabs 残件もフィックスアップで再入ガード＋背景同化解除＋open-in-new でクローズ）／敵対的退行レビューで Critical/Major 退行ゼロ（読書位置＝生命線・SSOT job cancel・ナビ骨格・取込パイプライン・Parcelize 型安全・BookshelfUiState 追随を現物確認）／`testDebugUnitTest` GREEN（新規 ActiveUriTrackerTest・BookCardProgressTest・NavHistoryTest＋既存4ファイルへ F-C/F-E/F-O/M8/権限回収の回帰追加）。**レビュー発見の恒久バグ1件も修正**＝取込失敗→再試行せず再起動で persistable URI permission がリーク → 起動時 `releaseOrphanedPermissions`（pending_jobs 非紐付き権限の回収）で根本対処。**主な構造変化**: kotlin-parcelize 導入（ResultContext/SearchDraft/DiscoveryQuery を SavedStateHandle 退避）／BookshelfUiState(Loading/Content)／TocState 4状態／AppErrorEvent(message, retryUri) 化／通知 deep link（EXTRA_BOOK_ID・launchMode=singleTop）／読書画面の内部 Back 履歴（navHistory・上限32）。**残タスク**（実機確認10項目＋繰り越し5件＝F-G 恒久策／F-J ページング／M12 ヒント永続化／意匠オーナー確認2点／F-F 軽微）は `handover.md` §E 参照。

- **Kotlin+PDFBox 移植（Chaquopy→ネイティブ・handover D）Phase 1〜5 完了・main へ統合（2026-07-06・--no-ff `75de07c`）**。
  一次情報 plan＝`.claude/plans/kotrin-branch-python-kotrin-graceful-flute-archived-2026-07-06.md`（再開手順・WSLビルドコマンド・環境メモ）／Phase 3 設計判断＝`.claude/plans/pure-juggling-hamming-archived-2026-07-06.md`／腐りにくい知見＝`task_diary.md` #30〜#38。
  - **Phase 1（純ロジック移植・9コミット全緑）**: `0a23d53` PDFBox依存追加 → `41c3b24` ParserRules・CharBox → `eae9892` TextProcessor → `53825f4` PdfExtractor → `dc7a090` splitIntoChapters → `cd6470e` ChapterProcessor → `c477d2d` HtmlExporter（**バイト等価ゴールデン＝穴1 KILL**） → `d40a225` PdfExtractionException → `12318eb` PdfBookExtractor facade。
  - **実機スパイク（穴3 KILL・2026-07-03）**: `PDFBoxResourceLoader.init` が実機の CID→Unicode 解決に効くことを実証（短中編 body_sha256 完全一致）。波ダッシュ正規化 `01175bb`（U+FF5E→U+301C・#35）。`PdfPipelineDeviceTest` で full facade 実機疎通＋リーダー目視 OK。素の androidTest は ColorOS に kill/freeze される（OSense=#37・Hans フリーザ=#38）／connectedAndroidTest 直叩きは蔵書DB消失（#36）。
  - **Phase 3（配線＋実機3e）**: `2944e84` init を Application.onCreate へ / `f5c8fcc` BookRepository を PdfBookExtractor 直呼へ切替（例外分類の型化・割り込み可能化）。実書フロー2冊で完走＝N2959KI 全132ファイルがバイト完全一致・N6169DZ(350万字/951章) は前景サービス＋WakeLock 経路で ColorOS kill を回避し完走・golden 完全一致。
  - **Phase 4（精度回帰ゲート昇格）**: スパイクを恒久ゲート化し実機3件通過（`OK (1 test)`）。章題ドリフトのグリフ写像9件を `normalizeGlyphUnicode` へ実装（2026-07-06 `fix/handover-singles`）＝ドリフト11→2件・短中編 sha256 維持。
  - **Phase 5（Chaquopy/Python 完全撤去）**: settings.gradle／build.gradle／MainActivity／`src/main/python` を撤去。`testDebugUnitTest` 106件緑・`assembleDebug` 成功・**APK 67.3→24.2MiB（64%減）**。関連ドキュメント/スキルも同ターンで追従。
  - 3e で検出した UX 課題（進捗バー非連動・2フェーズ誤認）は 2026-07-06 `fix/handover-singles` で解消（統合%表示）。**統合表示の最終目視のみ次回接続で再確認＝handover D 参照**。

- **agy(Antigravity)委譲の作業空間整備 完了**（2026-07-06・`agy-workspace` ブランチで実施し main へ統合）。
  `AGENTS.md` 新設（agy 実行者向けブリーフィング。`--dir` 登録で自動注入・記載の Gradle 手順は agy 実地検証済み＝testDebugUnitTest 50/50 パス）＋ `.agents/`（hooks.json＋guard_forbidden.py＝禁忌コマンドの PreToolUse 機械的 deny・発火実証済み）。
  モデル指針: 普段=flash（実測採点9.0/10）／pro=難解純粋推論のみ／agy経由 Claude Sonnet 4.6=深掘りレビューの第二の目。委譲運用の詳細は auto-memory `agy-workspace-agents-md-two-layers`・`agy-model-selection-guideline` が正本。**plan運用ルールの CLAUDE.md 正式化も完了（2026-07-06）**＝開発ルール「委譲判断 / plan運用」節に〈探索(read→digest)は agy／判断・統合は Claude・引用行 spot-check／plan のフェーズ二分＆実行セッション起動ブロック必須化／10ターン超は fresh 実行／edit-streak 誤発火は申告〉を明文化（実測根拠は上記 memory＋`agy-objective-minimize-claude-agy-free`・`workflow-plan-fresh-session-execution`。**2026-07-07 の指示予算減量で同節は要点のみへ圧縮**＝詳細はこれらの memory が正本）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | hooksガード | `6d5a626` | `.agents/` PreToolUse で git 書き込み系・adb・connectedAndroidTest・sudo を deny |
  | 手順fix | `5236932` | AGENTS.md の Gradle 手順修正（drvfs の sed -i EPERM 回避・java フルパス起動） |
  | AGENTS.md | `9a43b83` | agy 実行者向けブリーフィング新設 |

- **単発修正バッチ完了**（実機確認済, 2026-07-02・`cleanup-pre-uidesign` で実施し main へ統合）。Step 6（C-09 カバー色味微調整）は**コード変更なし＝現状維持で打ち切り**（起点値が旧HSL書影で陳腐化・書影は既にD様式へ刷新済みのため。詳細は handover C-09／プラン `.claude/plans/single-fix-batch-archived-2026-07-02.md`）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | 本文余白設定化 (旧C-05+06) | `c5959ae` | 10〜40dpスライダー＋`reading_body_margin` prefs・広幅端末は中央寄せ |
  | 設定シート磨き (A2残) | `39927b5` | 現在値を右寄せ藍数字化・スライダー目盛りドット非表示（`task_diary` #29） |
  | コメント整合 | `89683b3` | ルビ字面アンカー化に伴う行間レンジ why コメント更新 |
  | androidTest追従 | `8c75ec5` | ReadingScreen テーマ引数追加（`e93d2eb`）への追従漏れでコンパイル不能だったのを修正 |
  | god file 分割 | `2b7d9ba`/`4900b5c` | 純移動リファクタ。NativeReadingScreen 1018→608行（+ChapterContent/ReadingSettingsSheet/ReadingErrorScreen）、BookshelfScreen 963→417行（+BookCard/ProcessingBanner） |
  | **バグ#1 ルビ位置ずれ解消** | `90d037a` | ルビを行上端→字面上端アンカーへ（根本原因 = `task_diary` #43） |
  | 非推奨アイコン | `b71e672` | 目次アイコンを AutoMirrored 化 |

- **lab検証 CP1〜CP7 全完了**（実機確認済, 2026-06-25）。<!-- 詳細アーカイブ .claude/plans/lab-verification-HANDOVER-2026-06-23-v2.md は全git履歴に存在せず張替え先も無いため参照リンクを削除（存在しないファイルを指す台帳を放置しない, CLAUDE.md rule 18）。 -->
- **UI改善 01〜10 全完了**（各項目とも実機目視OK）。詳細 = `.claude/plans/ui-fixes-HANDOVER-2026-06-24.md`（アーカイブ）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | 09 | `43f13cb` | カバーのパレット調律＋textColorしきい値バグ修正 |
  | 06 | `5aefa4a` | 表示設定に行間スライダー |
  | 05 a+c | `045da9f` | 読書chrome没入（ボトムバー退避＋中央タップ切替） |
  | 05 b | `073a47f` | トップバー紙トーン化・上下バー色統一 |
  | 03 | `b4510b4` | 削除UIを長押し/⋮の2方式＋実行時トグル |
  | 02② | `1a89be5` | カバーから著者名削除しカード本文に一本化 |
  | 02① | `d54adc0` | カバーのウォーターマーク削除 |
  | 10 | `7848d8e`/`2f0c4e3` | グリッド下端FAB余白／章番号・進捗%テキスト |
  | 04 | `6165403` | 目次の現在章を左バー＋淡背景で強調 |
  | 01 | `3520324` | 設定チップ選択色を朱に統一 |
  | 08 | `661e6ac` | 章タイトル末尾「…」省略 |
  | 07 | — | 棄却（既に行送り一定・ルビ減光済） |

- ※ 旧backlogの「Phase2 文字サイズ変更」「章内スクロール位置永続化」は本検証(CP2/CP3)で**実装完了**。

- **変換まわり機能 A①③④ 完了**（実機目視OK, 2026-06-25）。handover A の②（強制終了時の再開）のみ未着手で残す。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | ④ | `018779c` | 本棚を最近の活動順ソート（Room v6→v7・addedAt/lastReadAt） |
  | ① | `97fcd5a` | 変換の全体停止ボタン（キュー破棄＋現在の本は完了して停止） |
  | ③-b | `841b5a8` | 変換中タイトルを進捗バナー/通知に表示 |
  | ③-a | `c1cb9b5` | 進捗の分母(n/m)を処理中もライブ反映 |

  - **①の制約**: 割り込み停止（処理中PDFの即中断）は当時の Chaquopy(Python/JNI)構成では不可能だった。前提の D（Kotlinネイティブ化）は 2026-07-05 に完了＝土台は充足済みで、停止ボタンのページ境界即中断への再配線は別タスク（未着手）。詳細は `handover.md` A①。

### UI-n ブランチ（見た目の白紙改装・別系統の実験ブランチ）
- **フェーズ0完了（2026-06-26）＝デフォルト視覚言語に D「和モダン・余白」を採用。** 本棚A〜J 10案を作り選定。設計判断の正本＝`docs/decisions/0005-ui-n-visual-language-D.md`、モック地図は `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md`（§6.1）に保全。
- **第2バッチ完了（2026-06-27）＝D の読書・目次・読書設定を HTMLモック化**（`ui-n-phase0/reading-D.html`・`toc-D.html`・`settings-D.html`）。これで D は本棚＋主要4画面が揃った。
- **方針確定（2026-06-27）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** スキン選択画面のモック・A〜J の Compose は将来送り（保留）。
- **D実機確認→調整ループ進行中（2026-06-30〜07-01）**: 実機スクショ↔Dモック突合でCompose翻訳を仕上げ中。完了分:
  - **① 本棚 D完全準拠**（フラット編集・明朝・書影下部タイトル・藍進捗/青磁未読、`461cf7c`）
  - **③ 読書本体に章見出し（明朝＋藍ルール）＋前書き後書きラベル藍**（`35eae10`）
  - **テーマ(ライト/セピア/ダーク)を本棚と読書で単一正本に同期**＋本棚⋮から切替可（`e93d2eb`）＝下記 handover B「11 本棚テーマ追従」を解消
  - **④⑤⑥ 完了（2026-07-02・実機ダーク目視OK）**: ④ 明朝トークン統一(`e791e97`)／⑤ 目次 toc-D 題字明朝＋字間(`f708739`)／⑥ 設定見出し明朝(`1bfb4a9`)。D実機ループの主要残件は解消（残るはスライダー目盛り等の任意微調整のみ＝handover A2）。手順書＝`.claude/plans/ui-n-D-completion-loop-HANDOVER-2026-06-30.md`。

## 2. 不具合・観察ログ

- ~~**#1 ルビ位置ずれ**~~ → **解消済み（2026-07-02・`90d037a`）**: 根本原因はルビY座標が `getLineTop`（行ボックス上端）基準で、lineHeight 余剰分だけ字面から浮いていたこと。字面上端アンカー（ベースライン＋フォントメトリクス導出）へ修正。実機目視OK（文字サイズ変更にも追尾）。詳細 = `task_diary.md` #43。
- **#2 章往復で章末着地**（⚠️未確認）: Claude側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
