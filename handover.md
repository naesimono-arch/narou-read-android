# handover — やること台帳（lab）

> **次に何をやろうか悩んだら、まずここを見る。**
> 作る予定のもの・あとで拾う思いつき・その場から漏れた取りこぼしを書き溜める場所。
> 思いついたら「思いつき・取りこぼし」へ追記して育てる。
> **今どうなっているか**（状態・完了・既知不具合）は `STATUS.md`。一次情報の細部は `.claude/plans/` のアーカイブhandover。

## 思いつき・取りこぼし（随時追記）

> レビュー中・実装中に出た宿題や着想で、まだ正式バックログに整理していないものをここへ。
> 育ったら下のA〜Dへ移す。

- （まだなし）

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
  - **残る微調整（任意・後日）**: 設定シートのスライダーが Material の目盛りドット表示＝モックは目盛り無しの細線＋藍フィル（steps維持のまま目盛りだけ消すにはカスタム track が要る）。文字サイズ/行間の現在値を右寄せ藍数字にする案（現状はラベル内 `（14sp）`）。`Icons.Filled.List`→`AutoMirrored` 非推奨警告(NativeReadingScreen.kt:523・既存)。
  - 手順書＝`.claude/plans/ui-n-D-completion-loop-HANDOVER-2026-06-30.md`（**必ず UI-n 上で起動**＝ブランチ跨ぎ hook 破綻回避）。色は `Color.kt`/`Theme.kt` 経由＝直書き禁止／字面は `Typography.kt`。
  - 環境知見: WSLビルドを実機へ上書きinstallするには Windows debug.keystore の共有が要る → memory [[wsl-debug-keystore-share-for-install]]。
- **将来送り（保留・元第2バッチ②③）**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。スキン機能に着手する時はここから再開する。
- モック正本は claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の `ui-n-phase0/` 配下。`DesignSync: get_file` で再取得可。

## B. 本棚

- **案B 詰め直しアニメ復活**: 案Aで `animateItemPlacement()` を削除し重なりバグは解消済（代償で削除時の詰め直しアニメが消えた）。Compose BOM `2024.04.01`→`2024.09+` に上げ、削除箇所を新API `Modifier.animateItem()` で置換。**リスク大＝全画面回帰必須**（BOMは全Composeモジュールの版を一括決定）。対象 `android/app/build.gradle`(BOM 2か所), `BookshelfScreen.kt:252` 付近の理由コメントが正本。
- ~~**11 本棚テーマ追従**（見送り）~~ → **解消済み（2026-07-01・`e93d2eb`）**: テーマ正本を `MainActivity` へ巻き上げ、本棚(`NovelReaderTheme(darkTheme=theme==DARK)`)も読書も単一の `ReadingTheme` 正本に追従。本棚⋮メニュー/読書設定シートのどちらで変えても全体同期。セピアは本棚ライト流用（専用セピア本棚は将来拡張の余地）。

## C. UI見送りサブ（2026-06-25 スコープ外決定）

- **05 本文左右余白/行長**: スマホで `ChapterContent` の `widthIn(max=600.dp)` が効かず本文全幅。`ParagraphItem` の `padding(horizontal=15.dp)` 拡大 or 最大幅縮小。全ユーザーの見た目が変わるため why コメント必須。
- **06 本文余白の設定化**: `fontSize`/`lineHeightEm` と同パターンで本文余白も `prefs` 永続化＋スライダー化。05と統合実装が自然。
- **09 グリフ太さ/紙質感/色味の追微調整**: `BookCover.kt` の `saturation 0.38〜0.49` / `lightnessTop 0.46〜0.61` 起点。色味はキリがないため追って微調整。

## D. 長期・品質（旧handoverから保全）

- **Chaquopy→Kotlin(PDFBox)ネイティブ化**【実アプリ移植は未着手】: A/B評価で **B案が技術的に優位**と判定済（`ab-review/submission-B` に完成形プロトを残置）。**実アプリへの移植自体は未着手の長期backlog**。知見 = `[[kotlin-pdfbox-migration-prototype]]` / 回帰基盤 = `[[golden-regression-baseline]]`。完了後 `Dispatchers.IO` で真の並列処理が可能になる。最大の難所＝縦書き列復元・ルビ対応付け・pdfminer が吸収していたエッジケース。
- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`(main未取り込み)。
- **BookRepository インターフェース化**（テスト可能化）: 具象直参照＋static シングルトン(Chaquopy/Room)で JVM単体テスト不可。interface 抽出＋`FakeBookRepository`。影響 `BookRepository.kt`/`NovelReaderApplication.kt`。
- **Phase3 外部連携**: ①内部ブラウザからPDF直接取込＆動線追加 ②「小説家になろう」公式API連携・ランキング表示（`docs/reference/narou_api_manual.md` 参照）。
- **doc アーキの main↔lab 乖離解消**（2026-07-02 発覚）: doc 再編（task_diary 分割・`docs/decisions`/`patterns`/`reference` 移設・`.claude/plans` アーカイブ化）は **main 先行で lab 未反映**＝規約「整頓は lab で行い他ブランチと乖離させるな」と実態が逆転している。規約どおり lab を現況正本に戻すには、lab へ整った docs 構成を展開する専用 docs 作業が要る（工程を分けるべき未了タスク）。※main 側の綻び（STATUS.md デッドリンク・ADR 0001 二重採番）は 2026-07-02 に解消済み。
