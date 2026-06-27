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

## A. 変換まわりの機能要望（4件・2026-06-23 lab検証中にユーザー発案）

1. **変換キャンセル**: 誤変換時に完了を待たず途中停止。`PdfProcessingService`(FGS)で「実行中ジョブ中断＋キュー除去＋通知/UI停止導線」。WakeLockのPDF単位再取得(`b7e8d7c`)・FGS onTimeout(`6b10012`)と整合。本棚FABキュー(`daf17e4`)に停止ボタンを足す形が自然。②と表裏。
2. **強制終了時の通知/再開**: OEM kill/OOM/onTimeout で落ちた際に通知し可能なら再開。「処理キュー/進捗の永続化」＋「再起動時の未完了ジョブ検出」が要る。ColorOSは積極kill（`task_diary.md`）＝実害大。①と表裏。
3. **変換中タイトル表示**: 進捗バナー/通知に処理中タイトルを出す（現状は件数 `(n/m件)` のみ。かつ `n/m` が1冊完了後にしか反映されないUX課題も併記の改善余地）。進捗は `BookshelfViewModel` の単一 progressChannel(`d9d9a3c`)経由。進捗イベントに処理中タイトル（or ファイル名→タイトル解決）を載せる。
4. **最終読書順ソート**: 最後に読んだ本を本棚先頭へ自動更新。`ProgressEntity` に `lastReadAt` 追加＋本棚クエリ `ORDER BY lastReadAt DESC`。カラム追加＝**Room Migration 必須**（現行DB v6 → `/db-migration` スキル先行）。位置保存は単一チャネル統合済(`d9d9a3c`)なので位置更新時に `lastReadAt` を併記する形が自然。`BookshelfViewModel`/`BookDao` 周辺。

## A2. UI-n ブランチ（見た目の白紙改装・2026-06-26 フェーズ0完了）

- **フェーズ0完了＝D「和モダン・余白」をデフォルト視覚言語に採用。** 本棚案A〜Jの10案を作り横並び選定した結果。詳細・モック地図・なぜDかは `UI-n_DESIGN_PLAN.md`（§3 フェーズ0結果・§6.1）が正本。
- **方針確定（2026-06-27・ユーザー指示）＝UIスキン着せ替え（A〜J 選択）はまだ実装しない。main は現状 D のみ。** A〜J は資産として claude.ai/design に保持するが、スキン切替機能は将来送り。
- **第2バッチ（2026-06-27 実施済み）**: ① D で読書・目次・読書設定をHTMLモック化＝**完了**（`ui-n-phase0/reading-D.html`・`toc-D.html`・`settings-D.html` を push）。読書は3テーマ（ライト/セピア/ダーク）＋クローム表示/没入の見た目を併記、目次は現在章=左藍ルール＋淡背景＋明朝太字＋空状態、設定シートはテーマ3択（藍選択）/文字サイズ/行間。
- **次にやること**: D の HTMLモックが目視OKなら **D を Compose 翻訳**（`Theme.kt` の `ReadingColors` を D 寒色へ／`Typography.kt`／`BookshelfScreen.kt`・`NativeReadingScreen.kt`・`NativeTableOfContentsScreen.kt`・`BookCover.kt`。色は `Color.kt`/`Theme.kt` 経由＝直書き禁止）。
- **将来送り（保留・元第2バッチ②③）**: 「UI着せ替え」設定画面のモック化（選択肢=A〜J・既定=D・切替粒度の決定）／A〜J スキンの Compose 実装（スキン×読書テーマの関係・トークン体系）。スキン機能に着手する時はここから再開する。
- モック正本は claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の `ui-n-phase0/` 配下。`DesignSync: get_file` で再取得可。

## B. 本棚

- **案B 詰め直しアニメ復活**: 案Aで `animateItemPlacement()` を削除し重なりバグは解消済（代償で削除時の詰め直しアニメが消えた）。Compose BOM `2024.04.01`→`2024.09+` に上げ、削除箇所を新API `Modifier.animateItem()` で置換。**リスク大＝全画面回帰必須**（BOMは全Composeモジュールの版を一括決定）。対象 `android/app/build.gradle`(BOM 2か所), `BookshelfScreen.kt:252` 付近の理由コメントが正本。
- **11 本棚テーマ追従**（見送り）: 本棚=`NovelReaderTheme`(system dark追従)/読書=`ReadingTheme` で別系統。全画面回帰要。着手点=`BookshelfScreen` の `surface/primary` を読書LIGHT/DARKトーンへ寄せるか、温かい黒 `0xFF1C1916` 固定かをまず決める。

## C. UI見送りサブ（2026-06-25 スコープ外決定）

- **05 本文左右余白/行長**: スマホで `ChapterContent` の `widthIn(max=600.dp)` が効かず本文全幅。`ParagraphItem` の `padding(horizontal=15.dp)` 拡大 or 最大幅縮小。全ユーザーの見た目が変わるため why コメント必須。
- **06 本文余白の設定化**: `fontSize`/`lineHeightEm` と同パターンで本文余白も `prefs` 永続化＋スライダー化。05と統合実装が自然。
- **09 グリフ太さ/紙質感/色味の追微調整**: `BookCover.kt` の `saturation 0.38〜0.49` / `lightnessTop 0.46〜0.61` 起点。色味はキリがないため追って微調整。

## D. 長期・品質（旧handoverから保全）

- **Chaquopy→Kotlin(PDFBox)ネイティブ化**: プロト評価済（submission-B 採用・完全版完成）。知見 = `[[kotlin-pdfbox-migration-prototype]]` / 回帰基盤 = `[[golden-regression-baseline]]`。完了後 `Dispatchers.IO` で真の並列処理が可能になる。最大の難所＝縦書き列復元・ルビ対応付け・pdfminer が吸収していたエッジケース。
- **左右スワイプで章遷移**: 旧 `experiment`/`lab-old` は WebView 実装で流用不可。`HorizontalPager`/`pointerInput` で新規。チューニング知見＝軸ロック(`de60869`)/EMA+isDragging(`a07dd3e`)/距離OR速度複合(`4a0719b`)。元コミット `23b5f33`(main未取り込み)。
- **BookRepository インターフェース化**（テスト可能化）: 具象直参照＋static シングルトン(Chaquopy/Room)で JVM単体テスト不可。interface 抽出＋`FakeBookRepository`。影響 `BookRepository.kt`/`NovelReaderApplication.kt`。
- **Phase3 外部連携**: ①内部ブラウザからPDF直接取込＆動線追加 ②「小説家になろう」公式API連携・ランキング表示（`narou_api_manual.md` 参照）。
