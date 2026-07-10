# STATUS — 現況台帳（正本 / main）

> **今どうなっているか** の正本。状態・完了済み・既知不具合を記録する。
> **次に何をやるか**（作る予定・思いつき・取りこぼし）は `handover.md`。
> 詳細な一次情報（実コミット・座標・logcat証拠）は `.claude/plans/` のアーカイブhandoverに残し、ここからは参照リンクで誘導する（二重メンテ回避）。

## 0. 現在の状態（一次情報）

- branch=`main` / 統合ノード=`45f9803`（2026-07-11 に最後の残作業レーン `meta/detector-improve`〔未統合3コミット＝Tier E 試作 `d1287f6`・実装用語FP修正 `d80190c`・docs `00775bd`〕を `integration/merge-detector-20260711` 経由で統合＝検知器増補5〔詳細は下記 ✅ 増補5 項。`_tier_b_reference` を前回裁定の破棄から部分採用へ転換し Tier B 本線へ補完組込み・Tier E 境界は `last_turn_start_order` へ付け替え・D4 は除外のまま・9f4d314d FP は v3.1 先行解消と確認〕。same-corpus 206ファイル走査で真陽性19件不変＝退行ゼロ。**同便でレーン後始末＝worktree(`~/wt/meta-detector-improve`)・ブランチとも撤去済み → 残作業レーンは無し**）。前身の統合ノード＝`82d9f3f`（2026-07-09 に残作業3ブランチを `integration/merge-batch` 経由で --no-ff 統合＝`task/hallucination-detector`〔検知器v3.2＝9c23aeb〕＋`meta/detector-improve`〔1fb2d1f。**検知器コードは v3.1/v3.2(main) を正本とし meta 実装（D4/current_turn/_tier_b_reference）は機能等価のため破棄**＝ADR 0006 増補3/4 に不採用注記付きで判断・較正実測を収蔵・事象M台帳・未対処知見2件〔事象M検知案・実装用語FP〕を handover 検知器節へ移植〕＋`task/device-feedback`〔並び順タブ縮小ほか実機フィードバック5コミット・testDebugUnitTest 緑〕。`hallucination-detector`/`device-check`/`verify/device-check` は main 内包済みでマージ不要と確認。⚠️ `meta/detector-improve` worktree の Tier E 試作（未コミット）はマージ対象外＝レーンに残置）。前々身の統合ノード＝`c1d3865`（2026-07-09 に完了3ブランチを `integrate/merge-20260709` 経由で統合＝`dismantle-status-api-lab`〔STATUS-api-lab 解体・ADR 0010・なろうAPI知見集約。5コミット・ff〕＋`verify/device-sweep`〔実機検証スイープ全GREEN〕＋`ui-design`〔本棚意匠再設計の台帳化〕。STATUS/handover の衝突は両追記の併存で解消。同便でブランチ整理＝統合済み `handover/task-sweep`・`ui/polish`・`sandbox`〔未コミットの handover 追記はハルシネーション産のため破棄〕の branch/worktree を撤去 → **残作業レーンは `meta/detector-improve` のみ**（Tier E 試作が未コミットで進行中）。未push＝origin より ahead 131）。前々々身の統合ノード＝`fd758df`＋後続docs2件（2026-07-08 に API系全ブランチを `integrate/merge-all-20260708` 経由で --no-ff 統合＝`api-lab-ai-2`(発見・検索の全実装＝トランク＋chrisbanes残指摘対応6コミット)＋`api-lab-ai`(Custom Tabs導線)＋`api-lab-ai-3`(なろう規約線docs)。`api-lab` は api-lab-ai に完全包含。統合に伴い **Room を v10 へ再退避**（実機の branch 版 v9 hash との合併衝突回避＝`236b32c`・task_diary #39 追補）、task_diary の二重採番3件を解消（なろうAPI系 #42→#46・#44→#47／フック配線側 #39→#48＝移設マッピング表）。さらに前身＝`788a18f`（2026-07-07・meta/tooling-improvements＋processing-resilience＋bookshelf-reflow-anim）・2026-07-06＝exec-fabrication-detector＋kotlin）
- **発見・検索機能（なろうAPI系）は Phase 0〜3＋Phase 4 スライス1 完了・main 統合済み**（現況・実装レンジは §1 の該当項が正本。api-lab フィーチャーブランチ専用の第二台帳だった旧 `STATUS-api-lab`（削除済み）は 2026-07-08 に解体し STATUS/handover/docs へ集約）。★次アクション（Phase 4 残り＝(b)Web由来カード／U1 新着通知／U2 整理）・監査残課題・リファクタは `handover.md`。
- **✅ 実行捏造検知器 増補5（2026-07-10 `meta/detector-improve`・2026-07-11 main 統合）＝Tier E 試作新設＋メタ免罪の実装用語較正**:
  ①**Tier E1 `unverified_completion_bundle`（試作・opt-in）**: 現ターン（`last_turn_start_order` 以降）に write/cleanup/test の完了主張が**2カテゴリ以上**集中 ∧ 対応する実行 tool_use が現ターンに皆無で発火＝事象L型①③④（照合キーを持たない汎用完了主張の総括捏造）への初の検知手段。**既定 tiers="ABCD" 非含の隔離 Tier**（真陽性 n=1 較正のため CLI `--tier ABCDE` で実績を積んでから既定化・Stop 昇格を判断＝handover 検知器節に宿題） ②**メタ免罪の実装用語漏れを閉鎖**: 引用体裁判定へ〈〉『』を追加＋**機構語限定**の `DETECTOR_IMPL_TERM_RE` を主張文近傍±120字の密度判定に合算（結果・成果語（降格/偽陽性/Tier 等）は真陽性 b4087931 を免罪する退行を probe 実測し除外＝較正の核心知見）。**統合時の意味解消**: 前回統合（`82d9f3f`）で「機能等価のため破棄」した `_tier_b_reference` は増補5 が Tier E の免罪＋②の対処先として上に構築していたため**部分採用へ転換**＝関数を取り込み、v3.1/v3.2 のインライン免罪（`meta_discussion`/`quoted_claim`）が拾えない〈実装用語で語られる仕様説明文・〈〉列挙〉FP の補完として **Tier B 本線（`detect_tier_b`）へも組込み**（handover 旧「実装用語漏れ」宿題の解消）。Tier E の現ターン境界は破棄済み `_last_user_turn_order` から main 正本 `last_turn_start_order`（v3.1 ③・同一基準のスーパーセット）へ付け替え。D4 呼び出しは前回裁定どおり統合から除外。meta 側が増補5 検証中に新発見・宿題登録した 9f4d314d FP（「テスト通過後…残します」）は **v3.1 ① `CONDITIONAL_EXCLUDE_RE` が先行解消済み**と統合時に確認（宿題化不要＝並行開発の同一FP独立発見）。**検証**: フックテスト129件緑（main 112＋meta 新規17）＋stop_guard 8件＋hooks 自己整合8件緑・**same-corpus 全206ファイル走査で ABCD active 20→20**（唯一の差分＝e4367031 の実装用語FP 1件が狙いどおり降格・**真陽性19件は完全一致＝退行ゼロ**）・**Tier E は事象L（b4087931）のみ検知＝新規偽陽性ゼロ**。詳細=ADR 0006 増補5＋増補4 不採用注記の 2026-07-11 追記。
- **✅ `/hallucination` 台帳自動記載パイプライン（2026-07-09・task/handover-cleanup）**:
  ユーザーが `/hallucination [メモ]` と打った**瞬間**に UserPromptSubmit フック `record_hallucination.py`（新設・settings.json 配線済み）が transcript を `~/.claude/hallucination-archive/` へ不変スナップショット＋正解データ台帳末尾の「⏳ 未確定キュー」へ機械的に1行記載。**設計原則＝証拠保全にモデル推論を介さない**（台帳L: 幻覚直後のセッションは Claude 自身の完了報告すら捏造しうる）。Claude の役割は後段のみ＝新設 `/hallucination` スキル（分類→スナップショット直読で行/uuid 確定→レター事象へ正式登録→キュー行昇格削除）。fail-open だが失敗は stdout コンテキスト注入で必ず可視化（サイレント失敗クラス回避）。検証＝合成ペイロード3ケース（非トリガー無干渉・成功・transcript不在の失敗可視化）。導線更新＝CLAUDE.md・台帳「追記手順」0項。
- **✅ 実行捏造検知器 v3.2（2026-07-09・task/hallucination-detector）＝Stop ライブ/スイープ実測FP 5系統の修正（機序2件は当初記録を実測で訂正）**:
  ①**SHA 誤抽出FP（891df1e6・Stop 実ブロック）**: 真因は handover 旧記載の「wt-new 表組み再掲の照合漏れ」ではなく、`task/device-feedback` 内の全 hex 文字断片 **`feedbac`** を `COMMIT_SHA_RE` が SHA と誤抽出＋入力エコーバック除去で証拠行が全滅する複合＝**`COMMIT_SHA_RE` に数字1つ以上を要求**して根治（実 SHA が数字ゼロの確率≈0.3%/7桁） ②**gitStatus 由来 SHA のFP（bcd69bb6・Stop 実ブロック）**: `5c3f32b` は transcript のどこにも無く出所は **system prompt の gitStatus（Recent commits）＝JSONL に記録されない領域**（台帳の旧機序「git diff 由来」を訂正）＝**リポジトリ実在 SHA 照合**（`hooks_common.make_sha_verifier`・`git cat-file -e`。core は callable 注入で純ロジック維持・Stop は hook cwd・CLI は `--repo`）で降格。捏造 SHA は全て実在しない実測（20d5aa3/9f3c2e1/3fbfe27/d5f8ecb）＝検知力不変 ③**メタ議論免罪を Tier A2/C1 へ**（c4b78e7d 検証セッションの引用発話FP）＋`unknown revision`（実在しないことの確認報告＝捏造の逆）をメタ語彙へ追加 ④**引用列挙FP（e4367031）**: claim マッチ全体が鉤括弧引用（「」『』）内なら `quoted_claim` 降格（強調括弧＝部分引用は検知維持） ⑤**実機テストランナー認識（441b9875）**: `am instrument`（`OK (N tests)`・失敗痕跡除外＝instrument はテスト失敗でも exit 0）と `connectedAndroidTest` を `TEST_RUNNER_CMD_RE`/`is_success_test_result` へ追加（当該発話は order=101 の実 instrument 成功直後＝完全FPと transcript 直読で確定）。**検証**: フックテスト147件緑（新規11件）・slug 全194ファイルスイープで active 30→19件（FP 11件解消・正解データ既知TP＝事象F 6件/A3 2件/D 系は全維持）・Stop 煙試験（891df1e6 非ブロック／捏造 SHA 合成陽性コントロールはブロック）。**副産物の新発見**: c4b78e7d rec#146「今度は本物です」の厳密検証報告自体が **HEAD SHA `9f3c2e1` を捏造**（reflog 含め不存在。マージ 4650e2b は実在＝完了は事実だが検証値が作文）＝残 active 4件は正当な検知として残置（詳細 handover 検知器節・人間確認待ち）。
- **✅ 実行捏造検知器 v3.1（2026-07-09・task/handover-cleanup）＝実測FP/検査窓の穴 4件修正**:
  ①**Tier B 将来計画文FP**（2026-07-09 Stop ライブ実測「テスト通過後の実機スイープ項目として残します」を完了報告と誤認）→ `CONDITIONAL_EXCLUDE_RE` に時制節「通過後/通った後」＋計画宣言「として残す/残します/これから」を追加＝claim 化自体を止める ②**Tier B メタ議論免罪**（2026-07-08 実測: 捏造検証の発話が捏造文言を引用すると偽陽性）→ Tier D と同型の `meta_discussion` 降格を Tier B に追加 ③**Stop 検査窓の穴（台帳L）**→ `scope=last_turn` を「最後のターン開始入力より後の全発話」に拡張（新関数 `last_turn_start_order`。AskUserQuestion 回答は境界にしない＝回答前の捏造発話が漏れる機序の遮断） ④**台帳K の部分対応**＝`[Request interrupted by user]` の地の文出現を `HARNESS_BLOCK_RE`（Tier A3）へ追加（K型のうちロールマーカー汎用検知・D3 降格ゲート再設計は handover に残）。⑤**画像添付入力でのクラッシュ修正**（2日分スイープで実測: 画像付き入力は queued_command の prompt が str でなく content ブロック list＝`_human_blob` の join で TypeError→当該セッションが解析不能だった。text ブロック抽出で文字列化）。**検証**: フックテスト120件緑（新規8件含む）・直近実トランスクリプト25件の last_turn スイープでブロッカー0（検査窓拡張によるライブ偽陽性なし）・2日分99ファイルの全域 ABCD スイープで正解データ既知事象（F/H/J/K）を検知（K は新ルールが初検知）。
- **✅ `meta/hallucination-classifier-v3`（2026-07-07）＝実行捏造検知器 v3: 入力側捏造（正解データ事象H・I）対応の Tier D 新設**:
  存在しないユーザー発話を捏造しそれを根拠に行動する新型（幻の叱責への謝罪・不存在発話の引用符付き引用・幻の不具合報告での指示違反ピボット）への対応。v1/v2 の Tier A/B/C は全て出力側（実行報告⇄実結果）の照合で、入力側は構造的にカバー外だった。①実在人間入力の索引化 `collect_human_inputs`（user human 入力［task-notification 等ハーネス著者の user-str は除外］／queued_command(origin.kind=human)／**AskUserQuestion 回答**［含めないと正当応答が偽陽性化＝較正実測］／summary）②Tier D 3ルール（D1 `fabricated_user_quote`＝引用の正規化部分一致突合／D2 `fabricated_user_report`＝重要数値[2桁以上 or 小数]を human∪主張以前の result 層と突合／D3 `phantom_user_response`＝冒頭の同意・謝罪マーカー×直前入力区間の human 欠落）③軸2＝thinking signature 異常（G/H/I 共通前兆・通常比5〜30倍）は較正実測により**単独ルール化せず** D3 の active 昇格条件（baseline=先行発話 sig の p25、`sig≥max(15000, baseline×8)`）に限定 ④メタ議論降格（台帳・検知器を扱う本リポジトリでは実例引用が最大 FP 源）とクロスセッション参照降格。**検証**: 正解データ H 2/3（L43 パラフレーズ型は突合不能で設計上対象外＝台帳も加点扱い）・I 1/1・slug 全128ファイル走査（8MB超7本含む）で偽陽性ゼロ・**新規検知1件（177f88f3 L195＝人間レビューで確定し台帳 J 事象として登録・検知器起点の初事例・入力側捏造の初出は 2026-07-02 に遡ると判明）**・既存 A/B/C 判定は全セッションで不変・フックテスト97件緑。Stop フック昇格は未実施（CLI 運用実績を見てから＝段階導入）。詳細=ADR 0006 増補2／handover 検知器節。
- **✅ `meta/hallucination-detector-tune`（2026-07-07）＝実行捏造検知器 v2: misread 型（正解データ事象F）対応の Tier C 新設**:
  ペアは在るが報告が実結果と食い違う捏造への対応。①証拠の2層化（SHA・出力シグネチャの存在照合は tool_use.input とエコーバック行を除いた **result 層**のみ＝「捏造 SHA を自分で `git show` 調査すると自己免罪される」機序の遮断）②GIT_CONTEXT_RE にマージ/merge/ブランチ等を拡張 ③Tier C 4ルール新設（`completion_after_blocked_commit`/`fabricated_output_signature`/`unverified_write_claim`/`unverified_branch_delete_claim`・存在照合は主張**以前**の証拠のみ＝自己訂正のやり直し出力による免罪防止）④Tier B の gradle 件数免罪（c05efed0 偽陽性対策）⑤Stop フックへ Tier C 全4ルール＋A2 を昇格配線。**検証**: 正解データF 5/5 検知・全106セッション走査で偽陽性ゼロ・フックテスト100件緑。**事象E（対話捏造）は agy 意味監査で運用**（ブラインド実証済み・手順は ground-truth 追記手順 3.）。詳細=ADR 0006 増補／handover 検知器節。
- **✅ `feat/processing-resilience`（2026-07-07 統合）＝変換まわり機能の残り2件（停止ボタン即中断・強制終了時の再開）を実装（JVM113緑＋実機3/3合格・OPPO PGEM10）**:
  ① **停止ボタンをページ境界の即中断へ再配線**: 処理中の1冊を子 Job（`currentBookJob`）で起動し `ACTION_STOP` が cancel → `addBook` の進捗コールバック内 `ensureActive()` が次のページ境界で中断（ループ Job ごと cancel しないのは cancel〜finally 間の ACTION_START 取りこぼしレース回避）。停止時は ongoing 通知を `STOP_FOREGROUND_REMOVE` で確実に除去。
  ② **強制終了（OEM kill/OOM/onTimeout）時の通知＋再開**: Room **v7→v8** で `pending_jobs` 新設（enqueue で記帳／成否確定で削除／明示停止は全消し／記帳は `pendingJobDispatcher`(並列度1)で直列化）。SAF 選択時に `takePersistableUriPermission` 取得。起動時リカバリ `runStartupRecoveryOnce`（MainActivity.onCreate トリガー・プロセス毎1回）＝孤立HTML掃除→未完了ジョブ検出→snackbar 通知＋権限が生きる分を FGS 再投入。
  実機3/3（v7→v8 migration・停止2秒以内即中断・強制終了→再起動で自動再開完走）の詳細ログ・機序は task_diary／`.claude/plans` 参照。
- **抽出パイプライン＝純 Kotlin（PDFBox-Android）単独**。Chaquopy/Python は Phase 5（2026-07-05）で完全撤去＝`git revert` での即復旧は不可（git 履歴からの復元は可能）。APK 67.3→24.2MiB。
- テスト: `testDebugUnitTest` **301件緑**（2026-07-08 `handover/task-sweep` 時点。Robolectric の Compose UI テスト＝ADR 0009 を含む。統合直後時点は 113件）。実機の恒久精度回帰ゲート＝`PdfExtractorDeviceSpikeTest`（N6169DZ 含む3件通過済み）。
- 端末DB=**`user_version 14`**（2026-07-10 U2 実機投入で **13→14 migration 通過を実測**＝`labels`/`book_labels` 新設・蔵書9冊全生存・起動即クラッシュ無し・`MigrationTest` 実機 am instrument OK(3 tests)＝7→14 チェーン。12→13 は同日 U1 実機E2E・11→12 は 2026-07-09 (b) 実機確認で通過済み）。⚠️ **旧APKへ逆走すると `migration N→N-1 not found` でクラッシュ＝逆走禁止**（古い→新しいの一方向のみ）。
- 既知バグ: なし（**#1 ルビ位置ずれは 2026-07-02 解消**＝`90d037a`。根本原因と1.6系APIの制約は `task_diary.md` #43）。
- 実機: OPPO PGEM10 `192.168.1.210:5555` 接続済み（切れたら `adb-bridge`）。検証ワークフローは `[[workflow-autonomous-device-verification]]`（Claudeがadb自律駆動）/ `[[workflow-notify-each-step-visual-check]]`（各ステップで目視関門）。
- Kotlin 移植（Phase 1〜5）の経緯・実機検証の詳細 → §1 先頭項＋一次情報 plan `.claude/plans/kotrin-branch-python-kotrin-graceful-flute-archived-2026-07-06.md`。

## 1. 完了済み

- **U2 ラベル整理 完了（2026-07-10・`feat/narou-phase4`・`30762aa`+`a7e403e`・実機目視OK）＝Phase 4 全消化**: フラットなラベル（タグ・多対多）で本棚を整理。Room **v14**（`labels`＝name unique／`book_labels`＝複合PK junction・FK なしでアプリ層掃除）＋付与UI（カード ⋮/長押し→「ラベル」シート＝チェックで付与/解除・**作成は対象の本へ即付与**〔ユーザー要望〕・ラベル削除は確認ダイアログ＋全蔵書から解除）＋本棚チップ行（モック `bookshelf-fusion-D` の `.label-row`/`.lchip` 翻訳＝検索の `FilterChipItem` 流用・「すべて」既定・未作成時は行ごと非表示・横スクロール）＋純関数絞り込み `filterShelfByLabel`（選択中は付与済み蔵書のみ・Web カード非表示・0件は「このラベルの本はありません」＝蔵書ゼロと区別）。テスト 348件 GREEN（シート Content 分離＝task_diary #50 準拠）。**これで Phase 4（融合本棚②＋U1/U2）完了**＝残る発見系宿題は handover 冒頭（話一覧ジャンプ・PDF複数選択/自動ソート・取込ボタン不安定）。
- **U1 新着話チェック＋通知 完了（2026-07-10・`feat/narou-phase4`・`2789512`+`0b2d2b7`・実機E2E全GREEN）**: 紐付け済み蔵書（books.ncode）の新着話を1日1回チェックし増分をローカル通知。Room **v13**（`new_episode_marks`＝「前回通知済み話数」基準値。章数基準だと取込むまで毎日再送されるため）＋`novelDetailsBulk`（ncode ダッシュ連結・`of=t-n-ga` 最小転送・鮮度優先でキャッシュ非搭載）＋`NewEpisodeCheckWorker`（24h 周期 KEEP・ネットワーク制約・初回は現在値で無音初期化・増分のみ通知・基準値は通知権限と独立に前進）＋通知チャネル `new_episode_channel` 分離＋WM on-demand 初期化（Robolectric 全滅回避＝Manifest で自動初期化 remove）。**実機実証（PGEM10・USB直経路）**: 実DB 12→13 移行（蔵書9冊生存）・MigrationTest `OK (3 tests)`・無音初期化（基準値=現在値・通知ゼロ）・基準値-3演出→増分通知1件のみ「続きが 3 話更新されています（全955話）」（変化なし作品 N8861MJ は無通知）・基準値952→955復帰・deep link タップで該当の本が開く（ユーザー目視OK）。周期 Work の強制発火は `cmd jobscheduler run -f` では不可＝workdb リセットが唯一手（task_diary #53）。
- **発見・検索系の実機UXフィードバック5件対応（2026-07-09・`task/device-feedback`・`ca5f706`〜`b86d3c3` の5コミット・testDebugUnitTest 緑・PGEM10 へ install -r 済み）**: ①探す画面に選択中キーワードの下部追従バー（`draft.word` トークンの派生表示・チップタップで個別解除・最大96dp内部スクロール）②同バーに「すべて解除」（word のみ空に・範囲/フィルタ維持）③作品詳細キーワードのトグル複数選択→「選択した N 件で検索」（`onKeywordTap:(String)`→`onSearchKeywords:(List<String>)` 置換）④結果一覧の検索語を `ChipKind.KEYWORD` チップ化（FlowRow 折り返しで見切れ解消・複数語 title は `resultTitle()`/MainActivity 両経路とも「キーワードN件」へ要約）⑤並び順タブを素の Material3 `Tab`（最小48dp強制＝モック乖離の真因）からモック `.tab` 寸法（padding 10×12px）の独自タブへ回帰。分割規則は `wordTokens`（SearchDraft.kt）に単一定義化し検索画面/結果チップのズレを構造的に防止。
- **なろう公式API 発見・検索機能（第2の柱）Phase 0〜3＋Phase 4 スライス1 完了・main 統合済み（2026-07-06〜08。旧台帳 `STATUS-api-lab`〔削除済み〕を 2026-07-08 に解体・集約）**: 案A（本文は取らずメタのみ）で発見体験を「公式より丁寧・アプリ内で完結」レベルに実装。目玉＝**PDF↔Web継続読書**（手元PDFの最終章→なろうで続きを探す→ncode紐付け→新着話数を継続カード表示→Chrome で該当話に着地）と**静かな没入意匠**。競合5アプリ解析（`docs/reference/04-competitor-app-features.md`）が「競合はどれも発見が弱い」を裏付け。
  - **実装レンジ**: 発見コア C1〜C4（order切替タブ／フリーワード検索＋範囲 title/ex/keyword/wname／ジャンル biggenre/genre／作品詳細）・発見強化 D1〜D5（検索履歴＋ピン留め／属性フラグ〔異世界転生/転移等〕／気分プリセット／type／期間）・目玉①PDF↔Web継続読書・Phase 4 スライス1（融合本棚の(c)見つける導線帯・(a)続きありバッジ＝紐付け済みの本に「● 続き N話」）。検索UXは実機フィードバック×競合5アプリ実機調査（`docs/reference/05-competitor-search-ui-field-report.md`）から**3原則（ADR 0007）**を固定し2ラウンド改善（範囲既定=タイトル・更新時期の語彙化・属性6軸の含む/除外・文字数/読了時間の段階チップ＋カスタム・結果画面のその場並び替え・ジャンルドリルダウン・キーワード公式準拠の全数収載）。**コミット詳細は git log が正本**（旧 §1a のコミット表は解体で削除）。
  - **画面構成**: `ui/discovery/` に Home/Search/Genre/Result/Detail の5画面（各 route/Content 分割済み＝系統1）＋Common（一覧行・状態表示）＋QueryLabels（条件チップ派生・純関数）。VM=DiscoveryViewModel（ホーム/結果/検索ドラフト/履歴を共有・遅延ロード）＋NovelDetailViewModel（ncode独立）。API層は `com.novelreader.narou` に隔離（蔵書 Room と別系統・Room に一切触れない）。実装 why の正本＝`docs/patterns/narou-api-discovery.md`。
  - **コード健全性監査（2026-07-07・5軸並列）で確定バグ4件を修正済み**: ①詳細経路の novel_type キー不一致（短編が「完結 1話」誤表示＝task_diary #47）②JsonDataException 素通りクラッシュ ③「短編+連載中」×新着順マージ破綻 ④ロード非キャンセルの応答逆転。回帰テスト追加込みで当時 200件緑。**構造系の監査残課題6項目・Phase 4 残り・技術的負債は `handover.md` へ移設**。
  - **実機エビデンス（PGEM10/ColorOS・上書きinstallで蔵書保持）**: 継続読書＝シャングリラ・フロンティア（PDF 951章）で最終章→紐付けシート（書名自動検索・候補提示→タップ確定）→継続カード「第952〜955話（新着4話）」→Chrome で ncode の該当話に正確着地・解除/再紐付け・DB永続化（books.ncode='N6169DZ'・WAL込み）まで検証済み。発見系＝実APIランキング・タブ切替・詳細（ヒーロー/評価表/なろうで読むバー）・条件シート検索（「短編」594,168作品・全行短編）を目視確認。**未検証（実機フィードバック待ち・優先度低）＝ライト/セピアでの発見画面の見え方（スモークはダーク中心）・履歴チップのピン留め操作感・モック意匠突合の細部**。
  - **なろう規約の線引き（2026-07-08 確認）**: 閲覧ページの"加工"表示・本文の機械的取得は運営が明文で禁止＝**加工なし送客（Chrome Custom Tabs）が既定の解**、独自UIを被せる WebView 内包は不可（設計判断＝ADR 0010／規約原文＝task_diary #45）。案A（本文非取得・メタのみ）の妥当性が規約面から補強された。
  - **一次情報**: 承認済み計画（目標ロードマップ・実装詳細）＝`~/.claude/plans/api-agy-woolly-swan.md`／縦スライス第1本の設計判断（アーカイブ）＝`~/.claude/plans/apimd-crystalline-cascade.md`。API仕様＝`docs/reference/narou_api_manual.md`（正本）・`02-narou-api-digest.md`（要点）／機能検討（案A推奨）＝`03-api-feature-analysis.md`。
- **実機検証スイープ全項目 GREEN（2026-07-08・`verify/device-sweep`・OPPO PGEM10）**: handover「実機検証待ち」4項目＋UX監査 E-要検証10項目を実機で消化。**①task-sweep分**＝v10→v11 migration実機通過（蔵書8冊生存）・`MigrationTest` am instrument OK(3)・M12没入ヒント通算初回のみ実証（prefs `immersive_hint_shown`・install -r で保持→本再オープンでも非再表示）・F-F設定シート＆F-C/F-E読書位置が process death（「アクティビティを保持しない」）で復元・F-J「さらに読み込む」で30→60件・系統1分割後の本棚/発見/検索/結果すべて D 様式で見た目不変・なろうAPIライブ稼働。**②UX監査 E-10**＝F-A経路依存Back/F-B読書Back（本棚へクリーン復帰）/M1二重pushガード/F-P・Mタッチ標的全て≥48dp（⋮=48×48dp実測・密度640）/フォント200%崩れ無し/cold start 732ms空フラッシュ無し/6h TTL（コード確認）/コントラスト（下記所見）。**③進捗%統合表示**＝「本文を処理しています…X%」＋4ステップステッパー（タイトル/本文/分割/HTML・ステップN/4）を実機確認＝2周目錯覚解消。**④統合ツリー残③**＝実PDF処理中に強制kill→再起動で snackbar「中断されていた変換1件を再開します」→FGS自動再launch（logcat `Background started FGS ... START_PROCESSING`）→本文0%→4%前進を実証＝resilience(applicationScope)×FGSスコープ再生成の共存を確認。停止ボタン即中断（pending_jobs全消し）・F-G重複取込のタイトル+著者層弾き（books不変8）も同便で実証。**検証後に端末を完全復元**（使い捨てPDF削除・font_scale/always_finish復元・蔵書8冊は開始時と同一・孤立HTMLゴミ無し）。**新規の気づき**（ランキングのジャンルタグ縦積み折返し=軽微実バグ／補助テキストのコントラスト通常文字AA未達／意匠2点の実機所見）は handover へ。

- **handover 一括消化スイープ（2026-07-08・`handover/task-sweep`・並列サブエージェント4ウェーブ＝計10コミット・`testDebugUnitTest` 301件緑）**: UX監査繰り越し4件（F-G恒久策/F-J/M12/F-F）＋chrisbanes 系統レビュー残（系統1※読書画面除く・系統4・系統5）＋テスト投資①②③を消化。**副産物＝実バグ1件発見・修正**（空の本棚で透明な Lazy コンテナが「PDFを追加する」CTA のタップを hit test で遮蔽＝新設 Robolectric テストが検出。`c006f51`）。実機確認の残りは handover「実機検証待ち」項。コミット表（新しい順）:

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

- **UX監査バックログ28件 全件修正・検証完了（2026-07-08・ui/polish）**: UX・導線フル監査で確定した指摘 **Critical 2/Major 14/Minor 12** を8実装エージェント＋検証2（全数突合・敵対的退行レビュー）＋フィックスアップ1で解消。**検証体制**: 全数突合で28件全て CONFIRMED（当初 PARTIAL の M1/M9＝読書画面継続カードの Custom Tabs 残件もフィックスアップで再入ガード＋背景同化解除＋open-in-new でクローズ）／敵対的退行レビューで Critical/Major 退行ゼロ（読書位置＝生命線・SSOT job cancel・ナビ骨格・取込パイプライン・Parcelize 型安全・BookshelfUiState 追随を現物確認）／`testDebugUnitTest` GREEN（新規 ActiveUriTrackerTest・BookCardProgressTest・NavHistoryTest＋既存4ファイルへ F-C/F-E/F-O/M8/権限回収の回帰追加）。**レビュー発見の恒久バグ1件も修正**＝取込失敗→再試行せず再起動で persistable URI permission がリーク → 起動時 `releaseOrphanedPermissions`（pending_jobs 非紐付き権限の回収）で根本対処。**主な構造変化**: kotlin-parcelize 導入（ResultContext/SearchDraft/DiscoveryQuery を SavedStateHandle 退避）／BookshelfUiState(Loading/Content)／TocState 4状態／AppErrorEvent(message, retryUri) 化／通知 deep link（EXTRA_BOOK_ID・launchMode=singleTop）／読書画面の内部 Back 履歴（navHistory・上限32）。**残タスク**（実機確認10項目＋繰り越し5件＝F-G 恒久策／F-J ページング／M12 ヒント永続化／意匠オーナー確認2点／F-F 軽微）は `handover.md` 「実機検証待ち」＋「意匠オーナー目視確認」参照。

- **Kotlin+PDFBox 移植（Chaquopy→ネイティブ・handover D）Phase 1〜5 完了・main へ統合（2026-07-06・--no-ff `75de07c`）**。
  一次情報 plan＝`.claude/plans/kotrin-branch-python-kotrin-graceful-flute-archived-2026-07-06.md`（再開手順・WSLビルドコマンド・環境メモ）／Phase 3 設計判断＝`.claude/plans/pure-juggling-hamming-archived-2026-07-06.md`／腐りにくい知見＝`task_diary.md` #30〜#38。
  - **Phase 1（純ロジック移植・9コミット全緑）**: `0a23d53` PDFBox依存追加 → `41c3b24` ParserRules・CharBox → `eae9892` TextProcessor → `53825f4` PdfExtractor → `dc7a090` splitIntoChapters → `cd6470e` ChapterProcessor → `c477d2d` HtmlExporter（**バイト等価ゴールデン＝穴1 KILL**） → `d40a225` PdfExtractionException → `12318eb` PdfBookExtractor facade。
  - **実機スパイク（穴3 KILL・2026-07-03）**: `PDFBoxResourceLoader.init` が実機の CID→Unicode 解決に効くことを実証（短中編 body_sha256 完全一致）。波ダッシュ正規化 `01175bb`（U+FF5E→U+301C・#35）。`PdfPipelineDeviceTest` で full facade 実機疎通＋リーダー目視 OK。素の androidTest は ColorOS に kill/freeze される（OSense=#37・Hans フリーザ=#38）／connectedAndroidTest 直叩きは蔵書DB消失（#36）。
  - **Phase 3（配線＋実機3e）**: `2944e84` init を Application.onCreate へ / `f5c8fcc` BookRepository を PdfBookExtractor 直呼へ切替（例外分類の型化・割り込み可能化）。実書フロー2冊で完走＝N2959KI 全132ファイルがバイト完全一致・N6169DZ(350万字/951章) は前景サービス＋WakeLock 経路で ColorOS kill を回避し完走・golden 完全一致。
  - **Phase 4（精度回帰ゲート昇格）**: スパイクを恒久ゲート化し実機3件通過（`OK (1 test)`）。章題ドリフトのグリフ写像9件を `normalizeGlyphUnicode` へ実装（2026-07-06 `fix/handover-singles`）＝ドリフト11→2件・短中編 sha256 維持。
  - **Phase 5（Chaquopy/Python 完全撤去）**: settings.gradle／build.gradle／MainActivity／`src/main/python` を撤去。`testDebugUnitTest` 106件緑・`assembleDebug` 成功・**APK 67.3→24.2MiB（64%減）**。関連ドキュメント/スキルも同ターンで追従。
  - 3e で検出した UX 課題（進捗バー非連動・2フェーズ誤認）は 2026-07-06 `fix/handover-singles` で解消（統合%表示）。**統合表示の最終目視のみ次回接続で再確認＝handover「実機検証待ち」参照**。

- **agy(Antigravity)委譲の作業空間整備 完了**（2026-07-06・`agy-workspace` ブランチで実施し main へ統合）。**※ `AGENTS.md`・`.agents/`（hooks.json・guard_forbidden.py）は 2026-07-10 に撤去（`integration/merge-20260710`）——agy委譲の運用ルール自体は継続（正本は auto-memory `agy-*`）。以下は導入時の記録。**
  `AGENTS.md` 新設（agy 実行者向けブリーフィング。`--dir` 登録で自動注入・記載の Gradle 手順は agy 実地検証済み＝testDebugUnitTest 50/50 パス）＋ `.agents/`（hooks.json＋guard_forbidden.py＝禁忌コマンドの PreToolUse 機械的 deny・発火実証済み）。
  モデル指針: 普段=flash（実測採点9.0/10）／pro=難解純粋推論のみ／agy経由 Claude Sonnet 4.6=深掘りレビューの第二の目。委譲運用の詳細は auto-memory `agy-workspace-agents-md-two-layers`・`agy-model-selection-guideline` が正本。**plan運用ルールの CLAUDE.md 正式化も完了（2026-07-06）**＝開発ルール「委譲判断 / plan運用」節に〈探索(read→digest)は agy／判断・統合は Claude・引用行 spot-check／plan のフェーズ二分＆実行セッション起動ブロック必須化／10ターン超は fresh 実行／edit-streak 誤発火は申告〉を明文化（実測根拠は上記 memory＋`agy-objective-minimize-claude-agy-free`・`workflow-plan-fresh-session-execution`。**2026-07-07 の指示予算減量で同節は要点のみへ圧縮**＝詳細はこれらの memory が正本）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | hooksガード | `6d5a626` | `.agents/` PreToolUse で git 書き込み系・adb・connectedAndroidTest・sudo を deny |
  | 手順fix | `5236932` | AGENTS.md の Gradle 手順修正（drvfs の sed -i EPERM 回避・java フルパス起動） |
  | AGENTS.md | `9a43b83` | agy 実行者向けブリーフィング新設 |

- **単発修正バッチ完了**（実機確認済, 2026-07-02・`cleanup-pre-uidesign` で実施し main へ統合）。Step 6（C-09 カバー色味微調整）は**コード変更なし＝現状維持で打ち切り**（起点値が旧HSL書影で陳腐化・書影は既にD様式へ刷新済みのため。詳細はプラン `.claude/plans/single-fix-batch-archived-2026-07-02.md`）。コミット表（新しい順）:

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

- **変換まわり機能 A①③④ 完了**（実機目視OK, 2026-06-25）。②（強制終了時の再開）のみ当時未着手で残した（→ 2026-07-07 `feat/processing-resilience` で実装済み＝§0）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | ④ | `018779c` | 本棚を最近の活動順ソート（Room v6→v7・addedAt/lastReadAt） |
  | ① | `97fcd5a` | 変換の全体停止ボタン（キュー破棄＋現在の本は完了して停止） |
  | ③-b | `841b5a8` | 変換中タイトルを進捗バナー/通知に表示 |
  | ③-a | `c1cb9b5` | 進捗の分母(n/m)を処理中もライブ反映 |

  - **①の制約**: 割り込み停止（処理中PDFの即中断）は当時の Chaquopy(Python/JNI)構成では不可能だった。前提の D（Kotlinネイティブ化）は 2026-07-05 に完了＝土台は充足済みで、停止ボタンのページ境界即中断への再配線は別タスク＝`feat/processing-resilience`（2026-07-07）で実装済み（§0）。

### UI-n ブランチ（見た目の白紙改装・別系統の実験ブランチ）
- **フェーズ0完了（2026-06-26）＝デフォルト視覚言語に D「和モダン・余白」を採用。** 本棚A〜J 10案を作り選定。設計判断の正本＝`docs/decisions/0005-ui-n-visual-language-D.md`、モック地図は `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md`（§6.1）に保全。
- **第2バッチ完了（2026-06-27）＝D の読書・目次・読書設定を HTMLモック化**（`ui-n-phase0/reading-D.html`・`toc-D.html`・`settings-D.html`）。これで D は本棚＋主要4画面が揃った。
- **方針確定（2026-06-27）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** スキン選択画面のモック・A〜J の Compose は将来送り（保留）。
- **D実機確認→調整ループ進行中（2026-06-30〜07-01）**: 実機スクショ↔Dモック突合でCompose翻訳を仕上げ中。完了分:
  - **① 本棚 D完全準拠**（フラット編集・明朝・書影下部タイトル・藍進捗/青磁未読、`461cf7c`）
  - **③ 読書本体に章見出し（明朝＋藍ルール）＋前書き後書きラベル藍**（`35eae10`）
  - **テーマ(ライト/セピア/ダーク)を本棚と読書で単一正本に同期**＋本棚⋮から切替可（`e93d2eb`）＝本棚のテーマ追従課題を解消
  - **④⑤⑥ 完了（2026-07-02・実機ダーク目視OK）**: ④ 明朝トークン統一(`e791e97`)／⑤ 目次 toc-D 題字明朝＋字間(`f708739`)／⑥ 設定見出し明朝(`1bfb4a9`)。D実機ループの主要残件は解消（スライダー目盛り等の微調整も 2026-07-02 に解消済み。スキン着せ替えの将来送りのみ＝handover A2）。手順書＝`.claude/plans/ui-n-D-completion-loop-HANDOVER-2026-06-30.md`。

## 2. 不具合・観察ログ

- ~~**#1 ルビ位置ずれ**~~ → **解消済み（2026-07-02・`90d037a`）**: 根本原因はルビY座標が `getLineTop`（行ボックス上端）基準で、lineHeight 余剰分だけ字面から浮いていたこと。字面上端アンカー（ベースライン＋フォントメトリクス導出）へ修正。実機目視OK（文字サイズ変更にも追尾）。詳細 = `task_diary.md` #43。
- **#2 章往復で章末着地**（⚠️未確認）: Claude側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
