# handover — やること台帳（main）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
> **今どうなっているか**（状態・完了・既知不具合）は `STATUS.md`。一次情報の細部は `.claude/plans/` のアーカイブhandover。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ正式バックログに整理していないものをここへ。
> 育ったら下のA〜Dへ移す。

- **[kotlin/掃除] 実機の本棚にテスト用シード本2冊が残存**（2026-07-03 Task9 目視関門で `PdfPipelineDeviceTest` が投入）: `spike-N1453LW`/`spike-N2959KI`（+空 `spike-N6169DZ` dir）。掃除の可否をユーザーに確認中で未実施。掃除するなら `filesDir/novels/spike-*` と books テーブルの該当行のみ削除（手動追加のルビ本・他蔵書には触れない）。Phase 3 の実書取込で上書きされる想定でもある。

---

## A. 変換まわりの機能要望（残り②のみ・2026-06-23 lab検証中にユーザー発案）

> ①③④は **2026-06-25 実装完了**（実機目視OK・コミット表は `STATUS.md`）。残るは②のみ。
> ①の補足: 割り込み停止（処理中PDFの即中断）は Chaquopy(Python/JNI)構成では不可能で、全体停止のみ実装。真の割り込みは **D. Kotlinネイティブ化** が前提。

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

- **Chaquopy→Kotlin(PDFBox)ネイティブ化**【`kotlin` ブランチ：Phase 1 移植＋垂直スライス・**Phase 3 配線＋実機検証 完了**・**Phase 4 精度回帰ゲート実装済（≤15版クリーンラン待ち＝上記[Phase 4 詰め]）**（いずれも 2026-07-05）／**次は Phase 5（Chaquopy 撤去＝ロールバック不能点）**／現況は STATUS.md 参照】: A/B評価で **B案が技術的に優位**と判定済（`ab-review/submission-B` に完成形プロトを残置）。知見 = `[[kotlin-pdfbox-migration-prototype]]` / 回帰基盤 = `[[golden-regression-baseline]]`。完了後 `Dispatchers.IO` で真の並列処理が可能になる。**次段階 Phase 3**＝BookRepository 切替（JNI→`PdfBookExtractor` 直呼・`PDFBoxResourceLoader.init` を MainActivity/Application へ配線・NonCancellable 緩和）。最大の難所は済み（縦書き列復元・ルビ対応付け）だが、pdfminer が吸収していた超長編エッジは残（下記）。
  - **[残タスク] 超長編PDFの抽出エッジ残差**（2026-07-03 検出・2026-07-05 Phase 4 で全容判明・優先度低）: N6169DZ(116650段/350万字)で pdfminer 比 **文字+434(+0.012%)・ルビ+110(+0.97%)・段落+5・blank+1・章題グリフ写像差 11件**の残差。短中編(N1453LW/N2959KI)は body_sha256 完全一致なので**超長編固有の抽出エッジ**（座標順・グリフ写像＝pdfminer が吸収していたケース）。
    - **章題11件の内訳（2026-07-05 Phase 4 で判明。旧記載「1件」は旧spikeが最初の差だけ表示した過少記録）**: ①**ダッシュ変種 `－`(U+FF0D)→`−`(U+2212) が6件** ②**矢印回転 `↑↓`(U+2191/2193)→`←→`(U+2190/2192) が3件**（PDFBox が矢印を90°回転誤読・golden の←→が意味的に自然） ③**アポストロフィ座標順 `兎'ｓ`↔`'鳥…` が2件**。
    - **[改善候補] ①②の9件は1:1コードポイント置換なので波ダッシュ(FF5E→301C)と同様 `PdfExtractor.normalizeGlyphUnicode` へ `FF0D→2212`・`2191→2190`・`2193→2192` を追加すれば golden に寄る**（→章題ドリフト 11→2件・忠実度向上）。ただし body にも同グリフが出れば正規化される＝短中編 body_sha256 への影響を要再検証（現状 exact なので破らないこと）。③のアポストロフィ座標順は 1:1置換不可（別途）。忠実度を上げるならこの順で。
    - 基準=`ab-review/golden_regression`、再現=`PdfExtractorDeviceSpikeTest`（Phase 4 で精度回帰ゲート化）、詳細=task_diary #35。※波ダッシュ(U+FF5E↔U+301C)は抽出時正規化(FF5E→301C)で対処済み。
  - **[残タスク][Phase 4 詰め] 精度回帰ゲートの ≤15 版クリーン合格ランを取り切る**（2026-07-05・優先度中）: `PdfExtractorDeviceSpikeTest` を診断→**恒久精度回帰ゲート**へ昇格済（合格ライン: 全PDF title/author/章数=完全一致・短中編 body_sha256=完全一致・長編は数値許容帯＝厳しめ char±0.05%/ruby±1.5%/para±8/blank±8＋章題≤15件不一致）。**N1453LW・N2959KI は実機で PASS 確認済**。**N6169DZ は ≤3 版で「章題11件のみ超過・他は全 PASS」を実機確認済＝≤15 版は論理的に必ず PASS**（11≤15・抽出は決定的）だが、**≤15 版のクリーンな完走ランは未取得**＝N6169DZ 素androidTest が ColorOS の fg_cpu kill で数回落ちた（task_diary #37・端末メモリ逼迫時）。**再開時**: 重い背景アプリを `am force-stop` でメモリ確保→`svc power stayon true`→`am instrument -w -e class com.novelreader.pdf.PdfExtractorDeviceSpikeTest com.novelreader.test/androidx.test.runner.AndroidJUnitRunner` を再実行し「Tests run:1 Failures:0」を1回取る。資産は `androidTest/assets/spike/`（gitignore・`sample_pdfs/`＋`golden_regression/` から配置）。
  - **[残タスク][UX] 本文抽出中に進捗バーが実時間連動しない**（2026-07-05 Phase 3 実機検証で N6169DZ 取込中にユーザー観察・優先度低・今すぐ修正不要）: 「本文を抽出しています…(ステップ2/4)」の間、下部バーが本文0%付近で止まって見え、抽出完了後に一気に進む。**原因**: `PdfExtractor.runFinalEngine`(`:152-159`) は `loadPages(doc)`＝全ページのグリフ抽出（PDFBox `PDFTextStripper.getText` の単一オペ＝超長編の支配的コスト）に**進捗コールバックが無く**、その後の `TextProcessor.processPages` だけが per-page 進捗を出すため。旧 pdfminer 経路はページ抽出と処理がインターリーブしていたが、PDFBox 移植で「全ロード→処理」に分離したことで生じた UX ギャップ（correctness ではない）。直すなら `loadPages` 内のページ走査に進捗フックを足す（GlyphStripper のページ境界でカウント）か、ロードと処理を再インターリーブする。
  - **[予約] Phase 5（Chaquopy/Python 撤去）完了時に併せて更新するもの**（2026-07-03 スキル陳腐化点検で予約。今直すと現状=併存中と乖離するため撤去時に実施）: ①`.claude/skills/stale-check/check_machine.py` — `check_referenced_files()` の Python 必須6ファイル hardcode（撤去後は偽の high「主要ファイル不存在」を出す）と `check_versions()` の Chaquopy/Python 版数照合 ②CLAUDE.md 冒頭の「Chaquopy 15.0.1 / Python 3.12」ビルド設定宣言 ③build スキルの「Python PDFロジックの単体テスト」節と Chaquopy 併存注記 ④architecture スキルのパイプライン図（Chaquopy 経路→`PdfBookExtractor` 直呼へ全面書換・二重構造記述の解消）。
- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`(main未取り込み)。
- **BookRepository インターフェース化**（テスト可能化）: 具象直参照＋static シングルトン(Chaquopy/Room)で JVM単体テスト不可。interface 抽出＋`FakeBookRepository`。影響 `BookRepository.kt`/`NovelReaderApplication.kt`。
- **Phase3 外部連携**: ①内部ブラウザからPDF直接取込＆動線追加 ②「小説家になろう」公式API連携・ランキング表示（`docs/reference/narou_api_manual.md` 参照）。
- ~~**doc アーキの main↔lab 乖離**（2026-07-02 発覚）~~ → **解消済み（2026-07-02）**: lab の実質新規（変換 A①③④・DB v7・想起フック修正・実装知見）を main へ論理単位で統合し、doc 正本を main へ一本化・lab を廃止した。規約と実態の逆転（旧「整頓は lab で」）は解消し、以後 doc 整理は main で行う。※main 側の綻び（STATUS.md デッドリンク・ADR 0001 二重採番）も 2026-07-02 に解消済み。
