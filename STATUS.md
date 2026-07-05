# STATUS — 現況台帳（正本 / main）

> **今どうなっているか** の正本。状態・完了済み・既知不具合を記録する。
> **次に何をやるか**（作る予定・思いつき・取りこぼし）は `handover.md`。
> 詳細な一次情報（実コミット・座標・logcat証拠）は `.claude/plans/` のアーカイブhandoverに残し、ここからは参照リンクで誘導する（二重メンテ回避）。

## 0. 現在の状態（一次情報）

- branch=`main` / HEAD=`c5959ae`＋直後のdocsコミット（2026-07-02 単発修正バッチ＝`cleanup-pre-uidesign` を統合。ルビ#1修正・UI god file分割・設定シート磨き・本文余白設定化）
- **⚠️ 別ブランチ作業中: `kotlin`（Chaquopy→Kotlin+PDFBox 移植・handover D 着手／2026-07-03〜）** — main `9c3c500` から分岐。**Phase 1＋垂直スライス＋Phase 3（配線＋実機検証）＋Phase 4（精度回帰ゲート）＋Phase 5（Chaquopy 完全撤去）まで完了＝移植の全フェーズ完了**（2026-07-05）。**Chaquopy/Python は撤去済み＝`git revert` での即復旧は不可（git 履歴からの復元は可能）**。main への統合が次段。
  - **完了（9コミット・全て testDebugUnitTest 緑＝104件）**: `0a23d53` PDFBox-Android 依存追加（Chaquopy 併存） / `41c3b24` ParserRules・CharBox 移植 / `eae9892` TextProcessor 移植 / `53825f4` PdfExtractor 移植（`com.tom_roush.pdfbox.*` import 解決を compileDebugKotlin で実証） / `dc7a090` splitIntoChapters 移植 / `cd6470e` ChapterProcessor(HTML版：前後書き整形・ruby・html.escape 忠実移植） / `c477d2d` HtmlExporter 移植（`htmlEscape` を共有トップレベル化・**バイト等価ゴールデン＝穴1 埋め**） / `d40a225` PdfExtractionException（sealed 3型＋classifyPdfError） / `12318eb` PdfBookExtractor facade（4ステップ進捗・PdfEngine 継ぎ目で fake 注入テスト）。移植先 `android/app/src/main/java/com/novelreader/pdf/`。**抽出コア〜HTML出力〜facade まで純ロジック移植完了（Phase 1 完了）**。
  - **Task 2 完了＝穴3 KILL（2026-07-03 実機スパイク）**: OPPO PGEM10(ColorOS) で `PdfExtractorDeviceSpikeTest`（実PDF3件を golden_regression と同一指標で突合）を実行。**`PDFBoxResourceLoader.init` は実機で効く**＝CID→Unicode 解決が根本機能（証拠: 短編N1453LW は body_sha256 完全一致・中編N2959KI 9786段/131章 も body_sha256 完全一致）。残差は既知のグリフ差＝①波ダッシュ `〜`(U+301C pdfminer)↔`～`(U+FF5E PDFBox)（1:1・文字数不変・title/記号に出る／task_diary #35）②超長編N6169DZ のみ 0.01%オーダーのエッジ（文字+0.012%・ルビ+0.97%・段落+5）。スパイクの assert は「title完全一致」を厳格ゲートにしたため波ダッシュ1字で FAILED になったが、**穴3 の問い＝init が効くか、には YES**。スパイクの実装＝`src/androidTest/.../pdf/PdfExtractorDeviceSpikeTest.kt`（`614eb1c`・資産無ければ Assume でスキップ）＋資産 `src/androidTest/assets/spike/`（gitignore・正本は sample_pdfs/ と ab-review/golden_regression/）。副作用: connectedAndroidTest がアプリを自動 uninstall し実機の `com.novelreader` が消えた（task_diary #36・回避=`leaveApksInstalledAfterRun=true`）。
  - **波ダッシュ正規化 完了（`01175bb`）**: `GlyphStripper` のグリフ読取点で U+FF5E→U+301C 正規化し pdfminer に揃えた（`normalizeGlyphUnicode`・ホットパスの indexOf ガード付き・ユニットテスト2件＝計106件緑・task_diary #35）。
  - **Task 9 完了＝穴3 全経路 KILL（2026-07-03 実機フル疎通）**: `PdfPipelineDeviceTest`（`am instrument` 実行＝uninstall回避）で full facade `PdfBookExtractor.process`（meta→本文→章→前後書き→**HtmlExporter**）を実機で回し、実アプリの本棚（`filesDir/novels/<id>`＋books）へ2冊シード。**正規化が facade 経由で実機まで効くことを実証**（N2959KI 生成 index.html の title が `〜`=U+301C）。**リーダー目視関門 OK**（N1453LW=前後書き囲み/3章・N2959KI=131章の章送り/本文/シーン区切り）。**ルビ(ふりがな)は穴1バイト等価＋描画経路無改修で担保済**とし2冊で関門完了。⚠️ N6169DZ(350万字)は抽出中に **ColorOS の OSense/Athena が abnormal fg_cpu で強制kill**（OOM非該当・task_diary #37）＝素の androidTest は前景サービス保護無しで無防備。**N6169DZ 実書の長編実機検証は Phase 3（前景サービス＋WakeLock 経路）へ回す**。
  - **Phase 3 コード配線 完了（2コミット・testDebugUnitTest 106件緑／2026-07-04）**: `2944e84` PDFBoxResourceLoader.init を Application.onCreate へ配線（Service が Activity 無しでも走るため） / `f5c8fcc` BookRepository 切替（JNI `process_pdf`→`PdfBookExtractor.process` 直呼・classifyError を PdfExtractionException 型分岐化＝PyException 文字列マッチ廃止・NonCancellable 緩和＝進捗CB相乗りで本文抽出中も割り込み可＋孤立HTML掃除・dead code の ProgressCallback 削除）。**ランタイム抽出経路が Chaquopy→ネイティブ(PDFBox)へ切替**（Python 残置＝revert 可）。停止ボタン(ACTION_STOP)は能力確立のみで従来どおりPDF境界（再配線は別タスク）。設計判断の詳細プラン=`~/.claude/plans/pure-juggling-hamming.md`。
  - **Phase 3 実機検証(3e) 完了＝穴3の最後の宿題も KILL（2026-07-05・実UIから Claude が adb 自律駆動）**: OPPO PGEM10(ColorOS) で**実書フロー**（「PDFを追加」→SAF→`PdfProcessingService`(前景)→`BookRepository`→ネイティブ`PdfBookExtractor`→HTML→本棚→リーダー）を2冊で検証。①**N2959KI(中編)**: 実書フロー生成の全132ファイルが Task9 facade 生成 spike と `diff -r` **バイト完全一致**（title/author/131章一致）。②**N6169DZ(350万字/951章)＝task_diary #37 の壁を突破**: 前景サービス＋WakeLock 経路で約2分で**完走・ColorOS に kill されず**（logcat: FGS `isForeground=true` 継続・`ACQ NovelReader::PdfProcessing` 保持・Osense は `KillAction skip: non-low-mem` でスキップ・kill/クラッシュ痕跡ゼロ）。951章＝golden 完全一致・title/author 完全一致（波ダッシュ〜=U+301C 正規化）・ルビ markup 正常。リーダーで両書の目次/本文描画 OK。⚠️ 検出した UX 課題（本文抽出中に進捗バーが実時間連動しない＝`loadPages` に進捗フック無し）は handover D の [残タスク][UX] へ（優先度低）。
  - **Phase 4 完了＝≤15版クリーンラン取得（2026-07-05）**: `PdfExtractorDeviceSpikeTest` を診断→**恒久精度回帰ゲート**へ昇格（合格ライン: 全PDF title/author/章数=完全一致・短中編 body_sha256=完全一致・長編 N6169DZ は数値許容帯＝厳しめ char±0.05%/ruby±1.5%/para±8/blank±8 ＋章題≤15件不一致）。**実機で N6169DZ 含む3件がゲート通過＝`OK (1 test)`（Tests run:1 Failures:0）を取得**。**発見(task_diary #38)**: ≤15版ラン中に N6169DZ 抽出が「CPU凍結・no progress」でハングに見えたが、真因は **ColorOS の Hans フリーザ(OplusHansManager)が素の androidTest プロセスを freeze**（#37 の fg_cpu kill とは別機構＝kill ではなく凍結・端末操作/充電の有無に無関係）。**回避＝テスト対象アプリの MainActivity を前面化して perceptible 化**（`monkey … LAUNCHER` で前面化した瞬間 %CPU 0→250% へ復帰し完走）。freeze/thaw は cgroup の中断/再開でクリーン＝抽出無破損で PASS は有効。N6169DZ 章題ドリフトは実測**11件**（≤15）＝全てグリフ写像差（ダッシュFF0D→2212×6・矢印回転×3・アポストロフィ座標順×2）。9件は正規化候補（handover）。
  - **Phase 5 完了＝Chaquopy/Python 完全撤去（2026-07-05・ロールバック不能点通過）**: ①`settings.gradle` の chaquo maven×2・`com.chaquo.python` plugin 宣言 ②`app/build.gradle` の plugin・`ndk abiFilters`・`python{}` ブロック ③`MainActivity` の `Python.start()` 初期化＋import（Phase 3 切替後は dead code） ④`src/main/python/` の `.py` 5件＋`fixtures/golden_html/` 3件 を撤去。**検証: `testDebugUnitTest` 106件緑・`assembleDebug` 成功（`generateDebugPythonJniLibs` タスク消滅）**。**APK サイズ実測 67.3MiB→24.2MiB（43.1MiB・64%減）**＝Python/Chaquopy 起因の圧縮後42.81MiB（chaquopy 資産30.53＋JNI .so 12.28）が消え、撤去後 APK に Python/.so 資産ゼロ。ドキュメント/スキル（CLAUDE.md・architecture/build/stale-check）のパイプライン記述も同ターンで全面書換（handover D「[予約] Phase 5 完了時に更新」①〜④消化）。
  - **→ 移植の全フェーズ（Phase 1〜5）完了。次段は `kotlin`→`main` 統合。** plan `~/.claude/plans/kotrin-branch-python-kotrin-graceful-flute.md` 参照。
  - **再開手順の正本 = `~/.claude/plans/kotrin-branch-python-kotrin-graceful-flute.md` の「実行ログ & 別セッション引き継ぎ」**（残タスク詳細・WSLビルドコマンド・環境メモ）。腐りにくい知見は `task_diary.md` #30-32。実機 `192.168.1.210:5555` 接続済み（切れたら `adb-bridge`）。
- 端末DB=`user_version 7`（v6→v7 で `books.addedAt`／`progress.lastReadAt` を追加）。⚠️ **旧APKへ逆走すると `migration N→N-1 not found` でクラッシュ＝逆走禁止**（古い→新しいの一方向のみ）。
- 既知バグ: なし（**#1 ルビ位置ずれは 2026-07-02 解消**＝`90d037a`。根本原因と1.6系APIの制約は `task_diary.md` #28）。
- 検証ワークフローは `[[workflow-autonomous-device-verification]]`（Claudeがadb自律駆動）/ `[[workflow-notify-each-step-visual-check]]`（各ステップで目視関門）。

## 1. 完了済み

- **単発修正バッチ完了**（実機確認済, 2026-07-02・`cleanup-pre-uidesign` で実施し main へ統合）。Step 6（C-09 カバー色味微調整）は**コード変更なし＝現状維持で打ち切り**（起点値が旧HSL書影で陳腐化・書影は既にD様式へ刷新済みのため。詳細は handover C-09／プラン `.claude/plans/single-fix-batch-archived-2026-07-02.md`）。コミット表（新しい順）:

  | 項目 | commit | 内容 |
  |---|---|---|
  | 本文余白設定化 (旧C-05+06) | `c5959ae` | 10〜40dpスライダー＋`reading_body_margin` prefs・広幅端末は中央寄せ |
  | 設定シート磨き (A2残) | `39927b5` | 現在値を右寄せ藍数字化・スライダー目盛りドット非表示（`task_diary` #29） |
  | コメント整合 | `89683b3` | ルビ字面アンカー化に伴う行間レンジ why コメント更新 |
  | androidTest追従 | `8c75ec5` | ReadingScreen テーマ引数追加（`e93d2eb`）への追従漏れでコンパイル不能だったのを修正 |
  | god file 分割 | `2b7d9ba`/`4900b5c` | 純移動リファクタ。NativeReadingScreen 1018→608行（+ChapterContent/ReadingSettingsSheet/ReadingErrorScreen）、BookshelfScreen 963→417行（+BookCard/ProcessingBanner） |
  | **バグ#1 ルビ位置ずれ解消** | `90d037a` | ルビを行上端→字面上端アンカーへ（根本原因 = `task_diary` #28） |
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

  - **①の制約**: 割り込み停止（処理中PDFの即中断）は Chaquopy(Python/JNI)構成では不可能。真の割り込みは D（Kotlinネイティブ化）が前提。詳細は `handover.md` A①。

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

- ~~**#1 ルビ位置ずれ**~~ → **解消済み（2026-07-02・`90d037a`）**: 根本原因はルビY座標が `getLineTop`（行ボックス上端）基準で、lineHeight 余剰分だけ字面から浮いていたこと。字面上端アンカー（ベースライン＋フォントメトリクス導出）へ修正。実機目視OK（文字サイズ変更にも追尾）。詳細 = `task_diary.md` #28。
- **#2 章往復で章末着地**（⚠️未確認）: Claude側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
