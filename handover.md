# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
> **今どうなっているか**（状態・完了・既知不具合）は `STATUS.md`。一次情報の細部は `.claude/plans/` のアーカイブhandover。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ正式バックログに整理していないものをここへ。
> 育ったら下のA〜Dへ移す。

- **実行捏造検知器（`feat/exec-fabrication-detector`・2026-07-06 main 統合済み）の残タスク**（ADR 0006・エンジンは `.claude/hooks/detect_fabricated_execution_core.py`）:
  - ~~**Stop フックのライブ化**~~ **完了（2026-07-06 統合）**: `stop_guard_fabrication.py` を実装し `.claude/settings.json` の `Stop` に配線済み（last_turn の Tier B `confidence≥0.8`・非降格のみ `decision:"block"` で自己修正を促す）。※作業ブランチは全て main へ統合・削除済みのため「全作業ブランチへ配布」は不要化。
  - **Tier B 汎用主張の免罪の限界**: 「セッション内に成功実行が1回でもあれば免罪」で後半の汎用捏造を取りこぼす（事象D）。具体値主張は具体照合に絞ったが、汎用主張の掘り下げは将来課題。
  - **サブエージェント/オフロード全文の裏取り強化**（現状は読めなければ降格）。
  - **Stop アダプタ（stop_guard_fabrication.py）の陽性コントロールテストが無い**（2026-07-06 点検で確認。エンジンは36テスト＋既知陽性 c2e7a254 で回帰確認済みだが、アダプタの「blockers 抽出→decision:block 出力」経路は合成 last_turn 捏造トランスクリプトでの自動テスト未整備。手動スモーク＝素通し系 exit 0 は確認済み）。
  - **Tier C（帰属誤り・生成コード不具合・外部リサーチ捏造）は別系統検知器**が必要（正解データ事象A/B/C・スコープ外）。着想段階。
- **[kotlin/掃除] 実機の本棚にテスト用シード本2冊が残存**（2026-07-03 Task9 目視関門で `PdfPipelineDeviceTest` が投入）: `spike-N1453LW`/`spike-N2959KI`（+空 `spike-N6169DZ` dir）。掃除の可否をユーザーに確認中で未実施。掃除するなら `filesDir/novels/spike-*` と books テーブルの該当行のみ削除（手動追加のルビ本・他蔵書には触れない）。Phase 3 の実書取込で上書きされる想定でもある。
- ~~**[hooks/fix] マージ統合で露呈したコミットゲートの2つの穴**~~ → **両方解消済み（2026-07-06・大規模マージ後の stale-check フル点検と同時に実施）**。①は `--name-status` 化して D（削除）をゲート対象から除外（Python ゲート自体も Phase 5 追従で撤去）。②は `COMMIT_GENERATING_RE`（merge/rebase/cherry-pick 検知・--abort/--quit/--no-commit 除外）を guard/consume/granularity へ追加し `test_hooks.py` で回帰固定（ADR 0004 追記済み）。**残る既知の限界**: `git pull` と PowerShell 経由は従来どおり対象外。原文は下記に保全:
  - **① `check_commit_granularity.py` の Python/Kotlin ゲートが「削除」を「要テスト」と誤判定**（＝false-positive）。根本原因: ステージ一覧を `git diff --cached --name-only`（削除も列挙される）から取り、`python_staged`/`kotlin_staged` を**拡張子とパスだけ**で絞り、センチネル存在チェック（`if not os.path.exists(SENTINEL): exit(2)`）を**削除ファイルにも適用**している。stale チェック側は `os.path.exists(repo_root/f)` で削除を除外できているのに、存在チェック側だけ削除を区別しない非対称が原因。実害: kotlin マージは `src/main/python/*.py` を**全撤去**するため、`.python_tests_passed` センチネルが偶然残っていなければ「`python -m unittest test_logic` を実行せよ」と**削除済みで実行不能なファイルのテストを要求してコミット不能**になっていた（今回は既存センチネルが在り通過）。修正案: `--name-status` で拾い `D`（削除）ステータスを `python_staged`/`kotlin_staged` から除外する（追加・変更のみゲート対象＝削除にテストは不要）。
  - **② コミット系ガードが `git merge --continue` / 競合なしの `git merge`・`rebase`・`cherry-pick` を素通しする**。根本原因: `guard_commit_branch.py` と `check_commit_granularity.py` はどちらもコマンド文字列を**リテラル `git … commit` の正規表現でしか検知しない**（`COMMIT_CMD_RE` / `\bgit\s+commit\b`）。マージ完了・rebase 継続は "commit" トークンを含まず**コミットを生成する**ため、両ガードを通過する。今回は競合したため明示 `git commit` が必要になり両ガードが発火したが、**競合しない main 直マージは branch guard もテストゲートも素通り**する。CLAUDE.md も branch guard を「ソフトな防御網」と明記済み＝既知の限界だが、マージ完了経路が盲点である点を追記。修正案: 両フックの検知に `git\s+(merge|rebase|cherry-pick)\b.*--(continue|no-ff)` 等のコミット生成コマンドを加えるか、PreToolUse でなく実際のブランチ HEAD 移動を捕える PostToolUse 併用を検討。
  - 補足（運用ノウハウ・修正不要）: PreToolUse は実行前にコマンド文字列を検査するため、`echo > .allow_protected_commit && git commit` のように**同一 Bash 呼び出しにセンチネル作成とコミットを混ぜると失敗**する（検査時点でセンチネル未作成）。センチネル作成とコミットは**別の Bash 呼び出しに分ける**こと。

---

## A. 変換まわりの機能要望（残り②のみ・2026-06-23 lab検証中にユーザー発案）

> ①③④は **2026-06-25 実装完了**（実機目視OK・コミット表は `STATUS.md`）。残るは②のみ。
> ①の補足: 割り込み停止（処理中PDFの即中断）は旧 Chaquopy(Python/JNI)構成では不可能で、全体停止のみ実装した。前提だった **D. Kotlinネイティブ化は完了済み（2026-07-05）**＝土台は充足。停止ボタンをページ境界の即中断へ再配線するのは別タスク（未着手・`.claude/skills/architecture` Service層の項参照）。

2. **強制終了時の通知/再開**【未着手】: OEM kill/OOM/onTimeout で落ちた際に通知し可能なら再開。「処理キュー/進捗の永続化」＋「再起動時の未完了ジョブ検出」＋孤立HTML掃除が要る。content:// は現状 `FLAG_GRANT_READ_URI_PERMISSION`（一時権限）のみ＝再開には `takePersistableUriPermission` が必須。ColorOSは積極kill（`task_diary.md`）＝実害大。他3件と質的に重くリスク大のため別フェーズに分離した。

## A2. UI-n ブランチ（見た目の白紙改装・2026-06-26 フェーズ0完了）

- **フェーズ0完了＝D「和モダン・余白」をデフォルト視覚言語に採用。** 本棚案A〜Jの10案を作り横並び選定した結果。なぜDか等の恒久設計判断は `docs/decisions/0005-ui-n-visual-language-D.md` が正本、モック地図（A〜J署名要素）・与件・ワークフローは `.claude/plans/UI-n_DESIGN_PLAN-archived-2026-07-02.md`（§6.1）に保全。
- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design に保持するが、スキン切替機能は将来送り。
- **第2バッチ（2026-06-27 実施済み）**: ① D で読書・目次・読書設定をHTMLモック化＝**完了**（`ui-n-phase0/reading-D.html`・`toc-D.html`・`settings-D.html` を push）。読書は3テーマ（ライト/セピア/ダーク）＋クローム表示/没入の見た目を併記、目次は現在章=左藍ルール＋淡背景＋明朝太字＋空状態、設定シートはテーマ3択（藍選択）/文字サイズ/行間。
- **第3バッチ・D実機ループ（2026-06-30〜07-02・④⑤⑥完了で主要一段落）**: 実機スクショ↔Dモック突合でCompose翻訳を仕上げ。
  - 完了: `Color.kt`(`cb09392`)/`Theme.kt`(`c6da6cf`)/`BookCover.kt`(`20dcc00`)/読書hr(`cd4853f`) ＋ **① 本棚 D完全準拠**(`461cf7c`：フラット編集・明朝題字・書影下部タイトル・藍進捗/青磁未読・⋮メニュー化) ＋ **③ 読書 章見出し明朝＋藍ルール・前書き後書きラベル藍**(`35eae10`) ＋ **テーマ単一正本同期**(`e93d2eb`：本棚⋮/設定シートどちらでも切替→全体追従。セピアは本棚ライト流用)。
  - **④⑤⑥ 完了（2026-07-02・実機ダーク目視OK）**: ④ 明朝トークン統一(`e791e97`：`FontFamily.Serif`直書き14箇所→`MinchoFamily`) ／ ⑤ 目次 toc-D(`f708739`：題字明朝＋字間.12em・現在章淡背景0.06) ／ ⑥ 設定シート settings-D(`1bfb4a9`：見出し明朝＋字間.08em)。**D実機ループの主要残件はこれで解消。**
  - ~~**残る微調整（任意・後日）**~~ → **全て解消済み（2026-07-02 単発修正バッチ・コミット表=STATUS）**: スライダー目盛り消し（※「カスタム track が要る」は誤りで tickColor 透明化で足りた＝`task_diary` #29）／現在値の右寄せ藍数字化／AutoMirrored 警告解消。
  - 手順書＝`.claude/plans/ui-n-D-completion-loop-HANDOVER-2026-06-30.md`（**必ず UI-n 上で起動**＝ブランチ跨ぎ hook 破綻回避）。色は `Color.kt`/`Theme.kt` 経由＝直書き禁止／字面は `Typography.kt`。
  - 環境知見: WSLビルドを実機へ上書きinstallするには Windows debug.keystore の共有が要る → memory [[wsl-debug-keystore-share-for-install]]。
- **将来送り（保留・元第2バッチ②③）**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。スキン機能に着手する時はここから再開する。
- モック正本は claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の `ui-n-phase0/` 配下。`DesignSync: get_file` で再取得可。

## B. 本棚

- **案B 詰め直しアニメ復活**: 案Aで `animateItemPlacement()` を削除し重なりバグは解消済（代償で削除時の詰め直しアニメが消えた）。Compose BOM `2024.04.01`→`2024.09+` に上げ、削除箇所を新API `Modifier.animateItem()` で置換。**リスク大＝全画面回帰必須**（BOMは全Composeモジュールの版を一括決定）。対象 `android/app/build.gradle`(BOM 2か所), `BookshelfScreen.kt:252` 付近の理由コメントが正本。
- ~~**11 本棚テーマ追従**（見送り）~~ → **解消済み（2026-07-01・`e93d2eb`）**: テーマ正本を `MainActivity` へ巻き上げ、本棚(`NovelReaderTheme(darkTheme=theme==DARK)`)も読書も単一の `ReadingTheme` 正本に追従。本棚⋮メニュー/読書設定シートのどちらで変えても全体同期。セピアは本棚ライト流用（専用セピア本棚は将来拡張の余地）。

## C. UI見送りサブ（2026-06-25 スコープ外決定）

- ~~**05 本文左右余白/行長**・**06 本文余白の設定化**~~ → **解消済み（2026-07-02・`c5959ae`）**: 統合実装。`reading_body_margin` prefs（10〜40dp・既定15）＋設定シート3本目スライダー＋広幅端末の中央寄せ。
- ~~**09 グリフ太さ/紙質感/色味の追微調整**~~ → **打ち切り（現状維持・2026-07-02）**: 再開点メモの起点値（`saturation 0.38〜0.49`/`lightnessTop 0.46〜0.61`）は**旧HSL書影の値で陳腐化**していた。書影は既にD様式の暗色スラブ（彩度12〜21%/明度上26〜34%）へ刷新・調律済み（`20dcc00`→`9f00680`→`ede761f`＋パレット調律`6e16072`）。実機ベースライン（リスト/グリッド）確認の上ユーザー判断で現状維持を選択。以後、書影の見た目はモックD（`ui-n-phase0/bookshelf-D.html`）＋ADR `docs/decisions/0005-ui-n-visual-language-D.md` が正本で、Compose 側の自己判断色味調整は行わない（CLAUDE.md「UIの見た目は /design モックが正本」ルール準拠）。プラン一次情報＝`.claude/plans/single-fix-batch-archived-2026-07-02.md`。

## D. 長期・品質（旧handoverから保全）

- **Chaquopy→Kotlin(PDFBox)ネイティブ化【Phase 1〜5 全完了・2026-07-05／現況は STATUS.md 参照】**: A/B評価で **B案が技術的に優位**と判定済（`ab-review/submission-B` に完成形プロトを残置＝精度オラクルとして今後も温存）。知見 = `[[kotlin-pdfbox-migration-prototype]]` / 回帰基盤 = `[[golden-regression-baseline]]`。Phase 1 移植＋垂直スライス・Phase 3 配線＋実機検証・Phase 4 精度回帰ゲート・**Phase 5 Chaquopy/Python 完全撤去（APK 67→24MiB・64%減）**まで到達し、ランタイム抽出は純 Kotlin(PDFBox) 単独。**次段は `kotlin`→`main` 統合**（統合時に auto-memory・global `~/.claude/CLAUDE.md` の Python テスト記述も追従更新＝下記）。純 Kotlin 化により `Dispatchers.IO` での真の並列処理が可能な土台になった（複数冊同時処理の実装は将来送り）。残る抽出エッジは下記。
  - **[残タスク] 超長編PDFの抽出エッジ残差**（2026-07-03 検出・2026-07-05 Phase 4 で全容判明・優先度低）: N6169DZ(116650段/350万字)で pdfminer 比 **文字+434(+0.012%)・ルビ+110(+0.97%)・段落+5・blank+1・章題グリフ写像差 11件**の残差。短中編(N1453LW/N2959KI)は body_sha256 完全一致なので**超長編固有の抽出エッジ**（座標順・グリフ写像＝pdfminer が吸収していたケース）。
    - **章題11件の内訳（2026-07-05 Phase 4 で判明。旧記載「1件」は旧spikeが最初の差だけ表示した過少記録）**: ①**ダッシュ変種 `－`(U+FF0D)→`−`(U+2212) が6件** ②**矢印回転 `↑↓`(U+2191/2193)→`←→`(U+2190/2192) が3件**（PDFBox が矢印を90°回転誤読・golden の←→が意味的に自然） ③**アポストロフィ座標順 `兎'ｓ`↔`'鳥…` が2件**。
    - **[改善候補] ①②の9件は1:1コードポイント置換なので波ダッシュ(FF5E→301C)と同様 `PdfExtractor.normalizeGlyphUnicode` へ `FF0D→2212`・`2191→2190`・`2193→2192` を追加すれば golden に寄る**（→章題ドリフト 11→2件・忠実度向上）。ただし body にも同グリフが出れば正規化される＝短中編 body_sha256 への影響を要再検証（現状 exact なので破らないこと）。③のアポストロフィ座標順は 1:1置換不可（別途）。忠実度を上げるならこの順で。
    - 基準=`ab-review/golden_regression`、再現=`PdfExtractorDeviceSpikeTest`（Phase 4 で精度回帰ゲート化）、詳細=task_diary #35。※波ダッシュ(U+FF5E↔U+301C)は抽出時正規化(FF5E→301C)で対処済み。
  - ~~**[Phase 4 詰め] 精度回帰ゲートの ≤15 版クリーン合格ランを取り切る**~~ → **完了（2026-07-05）**: 実機で N6169DZ 含む3件がゲート通過＝`OK (1 test)`（Tests run:1 Failures:0）取得。合格ライン=全PDF title/author/章数=完全一致・短中編 body_sha256=完全一致・長編は数値許容帯（char±0.05%/ruby±1.5%/para±8/blank±8＋章題≤15件不一致）。**ハマりどころ(task_diary #38)**: ハングの真因は ColorOS の Hans フリーザ(OplusHansManager)が素の androidTest を **freeze** することで、#37 の fg_cpu **kill** とは別機構（端末操作/充電の有無に無関係）。**ゲートを再実行する場合の作法**: `am force-stop` で競合削減＋`am instrument -w -e class com.novelreader.pdf.PdfExtractorDeviceSpikeTest com.novelreader.test/androidx.test.runner.AndroidJUnitRunner` を回し、**実行中に `adb shell monkey -p com.novelreader -c android.intent.category.LAUNCHER 1` で MainActivity を前面化**してプロセスを perceptible に保つ（前面化で %CPU 0→250% へ復帰し完走）。資産は `androidTest/assets/spike/`（gitignore・`sample_pdfs/`＋`golden_regression/` から配置）。現況は STATUS.md 参照。
  - **[残タスク][UX] 本文抽出中に進捗バーが実時間連動しない**（2026-07-05 Phase 3 実機検証で N6169DZ 取込中にユーザー観察・優先度低・今すぐ修正不要）: 「本文を抽出しています…(ステップ2/4)」の間、下部バーが本文0%付近で止まって見え、抽出完了後に一気に進む。**原因**: `PdfExtractor.runFinalEngine`(`:152-159`) は `loadPages(doc)`＝全ページのグリフ抽出（PDFBox `PDFTextStripper.getText` の単一オペ＝超長編の支配的コスト）に**進捗コールバックが無く**、その後の `TextProcessor.processPages` だけが per-page 進捗を出すため。旧 pdfminer 経路はページ抽出と処理がインターリーブしていたが、PDFBox 移植で「全ロード→処理」に分離したことで生じた UX ギャップ（correctness ではない）。直すなら `loadPages` 内のページ走査に進捗フックを足す（GlyphStripper のページ境界でカウント）か、ロードと処理を再インターリーブする。
  - ~~**[予約] Phase 5（Chaquopy/Python 撤去）完了時に併せて更新するもの**~~ → **完了（2026-07-05・Phase 5 と同ターン）**: ①`.claude/skills/stale-check/check_machine.py` の `check_referenced_files()` の Python 必須ファイル hardcode を Kotlin `pdf/` へ差替・`check_versions()`/`check_test_commands()` の Chaquopy/Python 照合を撤去（撤去後 stale-check は high 0/info 0 のクリーンを確認済） ②CLAUDE.md 冒頭のビルド設定宣言を「PDFBox-Android 2.0.27.0」へ・自己検証ルールとドメイン知識の Python 参照を Kotlin へ ③build スキルの「Python PDFロジックの単体テスト」節を撤去し testDebugUnitTest へ集約 ④architecture スキルのパイプライン図を `PdfBookExtractor` 単独経路へ全面書換（二重構造記述を解消）。~~**残（main 統合時に実施）**: auto-memory の build 記述・global `~/.claude/CLAUDE.md` の Python 単体テスト節の追従更新~~ → **消化済み（2026-07-06・main 統合後の stale-check フルで実施）**: global `~/.claude/CLAUDE.md` の Python 単体テスト節を撤去し testDebugUnitTest へ統合・py シム行を歴史注記化。auto-memory は grep 確認で Python テスト手順の記述なし（pdfminer 言及は歴史的知見のみ＝維持）。
- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`(main未取り込み)。
- **BookRepository インターフェース化**（テスト可能化）: 具象直参照＋static シングルトン(PDFBoxResourceLoader の Application 初期化/Room)で JVM単体テスト不可。interface 抽出＋`FakeBookRepository`。影響 `BookRepository.kt`/`NovelReaderApplication.kt`。
- **Phase3 外部連携**: ①内部ブラウザからPDF直接取込＆動線追加 ②「小説家になろう」公式API連携・ランキング表示（`docs/reference/narou_api_manual.md` 参照）。
- ~~**doc アーキの main↔lab 乖離**（2026-07-02 発覚）~~ → **解消済み（2026-07-02）**: lab の実質新規（変換 A①③④・DB v7・想起フック修正・実装知見）を main へ論理単位で統合し、doc 正本を main へ一本化・lab を廃止した。規約と実態の逆転（旧「整頓は lab で」）は解消し、以後 doc 整理は main で行う。※main 側の綻び（STATUS.md デッドリンク・ADR 0001 二重採番）も 2026-07-02 に解消済み。
