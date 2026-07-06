# STATUS — 現況台帳（正本 / main）

> **今どうなっているか** の正本。状態・完了済み・既知不具合を記録する。
> **次に何をやるか**（作る予定・思いつき・取りこぼし）は `handover.md`。
> 詳細な一次情報（実コミット・座標・logcat証拠）は `.claude/plans/` のアーカイブhandoverに残し、ここからは参照リンクで誘導する（二重メンテ回避）。

## 0. 現在の状態（一次情報）

- branch=`main` / 統合ノード=`788a18f`（2026-07-07 に完了3ブランチを --no-ff 統合＝`meta/tooling-improvements`(フック情報提示修正・architectureスキル圧縮・ADR0007)＋`feat/processing-resilience`(停止のページ境界即中断・強制終了リカバリ・Room v7→v8)＋`feat/bookshelf-reflow-anim`(animateItem詰め直しアニメ・Compose BOM 2025.02.00)。統合後にこの3本のローカルブランチ・worktree は削除済み。API系 `api-lab`/`api-lab-ai` は開発中のため不可侵で残置。未push＝origin より ahead 130。前身の 2026-07-06 統合＝`feat/exec-fabrication-detector`＝実行捏造検知フック＋`kotlin`＝Chaquopy→PDFBox 移植）
- **✅ `feat/processing-resilience`（2026-07-07 統合）＝handover A の残り2件を実装（JVM113緑＋実機3/3合格・OPPO PGEM10）**:
  ① **停止ボタンをページ境界の即中断へ再配線**: 処理中の1冊を子 Job（`currentBookJob`）で起動し `ACTION_STOP` が cancel → `addBook` の進捗コールバック内 `ensureActive()` が次のページ境界で中断（ループ Job ごと cancel しないのは cancel〜finally 間の ACTION_START 取りこぼしレース回避）。停止時は ongoing 通知を `STOP_FOREGROUND_REMOVE` で確実に除去。
  ② **強制終了（OEM kill/OOM/onTimeout）時の通知＋再開**: Room **v7→v8** で `pending_jobs` 新設（enqueue で記帳／成否確定で削除／明示停止は全消し／記帳は `pendingJobDispatcher`(並列度1)で直列化）。SAF 選択時に `takePersistableUriPermission` 取得。起動時リカバリ `runStartupRecoveryOnce`（MainActivity.onCreate トリガー・プロセス毎1回）＝孤立HTML掃除→未完了ジョブ検出→snackbar 通知＋権限が生きる分を FGS 再投入。
  実機3/3（v7→v8 migration・停止2秒以内即中断・強制終了→再起動で自動再開完走）の詳細ログ・機序は handover A／task_diary 参照。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**。Chaquopy/Python は Phase 5（2026-07-05）で完全撤去＝`git revert` での即復旧は不可（git 履歴からの復元は可能）。APK 67.3→24.2MiB。
- テスト: `testDebugUnitTest` 113件緑（統合済みツリーで確認）。実機の恒久精度回帰ゲート＝`PdfExtractorDeviceSpikeTest`（N6169DZ 含む3件通過済み）。
- 端末DB=`user_version 7`→**コードは v8**（`pending_jobs` 新設・上記②）＝次回実機 install で v7→v8 migration が走る（v6→v7 で `books.addedAt`／`progress.lastReadAt` 追加済み）。⚠️ **旧APKへ逆走すると `migration N→N-1 not found` でクラッシュ＝逆走禁止**（古い→新しいの一方向のみ）。
- 既知バグ: なし（**#1 ルビ位置ずれは 2026-07-02 解消**＝`90d037a`。根本原因と1.6系APIの制約は `task_diary.md` #43）。
- 実機: OPPO PGEM10 `192.168.1.210:5555` 接続済み（切れたら `adb-bridge`）。検証ワークフローは `[[workflow-autonomous-device-verification]]`（Claudeがadb自律駆動）/ `[[workflow-notify-each-step-visual-check]]`（各ステップで目視関門）。
- Kotlin 移植（Phase 1〜5）の経緯・実機検証の詳細 → §1 先頭項＋一次情報 plan `.claude/plans/kotrin-branch-python-kotrin-graceful-flute-archived-2026-07-06.md`。

## 1. 完了済み

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
