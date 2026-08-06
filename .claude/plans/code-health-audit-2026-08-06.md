# コード健全性 全面監査 — 発見と処理計画（2026-08-06）

> **対象ブランチ: `review/code-health-2026-08-06`**（worktree `/home/qingj/wt/review-code-health-2026-08-06`・ext4＝素の `gw testDebugUnitTest`）
> 出自: `docs/known-bugs-registry.md` A表の**無防備 17 件（`[!] なし` 11＋`[!] 知見のみ` 6）は CI 7ゲートのどれも止めない**＝実質未検査、という投資判断から。
> 手段＝マルチエージェント監査（39体・軸15＋完全性批評の追加3軸・軸ごとに反証専任を1体）。
> **走査の主眼は「登録簿に既にある機序が、別の箇所で生きていないか」**——既修正箇所の再発見は禁止条件として全担当へ与えた。
>
> **範囲の判断（2つ補正した）**: ①「意匠の指摘は禁止」を*美的判断*に限り、**タップ不能・押し出し・はみ出し・状態消失は機能破綻として対象**に残した
> （`[!]` 11件のうち4件がこの帯にあり、一律禁止だと丸ごと落ちる）。②`ui/skins/` 18,108行を除外しない——
> 再発回数が最多の型（`skin-wiring-omission`・alpha 持ち越し・縦書き分類器の迂回3経路・route pop 2経路）は**まさにこの中**にある。
> 意匠は見ず「5系統の配線・状態の差分」だけを突合する軸として入れた。
>
> **機械検知（L2）への昇格設計は別紙** ＝ `code-health-mechanical-detection-2026-08-06.md`。
> 生の全 findings（34件・証拠と走査範囲つき）は監査時の scratchpad にのみ存在し、**本書と別紙が集約後の正本**。

---

**要約**: 反証を生き残った 34 件を真因単位へ束ねると **28 件**、うち **25 件を実バグ**と判定した（3 件却下）。25 件の内訳は **既存台帳 ID の再発が 16 件**・**新種が 9 件**。最重要は「無音のデータ欠落」3 件（章の消失／並行取込による本文破壊／章0件取込の成功確定）と「即クラッシュ」1 件（発見の追加読み込み）。なお `Features.skinSwitchingEnabled` が release で false ＝ 全ユーザーは K 固定のため、**M/P/J 固有の欠陥は debug 限定**として優先度を一段下げてある（K・D/C 共通・route 層の欠陥は release 到達）。

---

## A. 今すぐ直す（11 件）

### A1. 章題に「前書き/後書き」を含む実話が本文へ吸収されて消える
- **file:line** `android/app/src/main/java/com/novelreader/pdf/ChapterProcessor.kt:109,116`
- **機序**: 構造マーカー判定が完全一致でなく部分一致（`"前書き" in title`）。この関数を Web 取込も共有する（`repository/WebBookImporter.kt:103`）が、Web の title はサイト側の著者記述文字列で機械生成マーカーではない。該当話は前後章へ畳み込まれて章ごと消え、**先頭章なら `if (finalChapters.isNotEmpty())` が false で本文ごと破棄**、末尾章が前書き扱いなら `tempForeword` が誰にも消費されず消える。例外もログも出ない。
- **実害**: 「後書きにかえて」等を題に含む回のある作品を取り込む → その回が目次から消える。第1話が該当すれば本文が丸ごと失われる。さらに `WebBookImporter.kt:122` の `clampReadingPosition(..., finalChapters.size)` が縮んだ章数で読書位置を丸めるため位置も巻き込む。
- **直し方**: 判定を完全一致（`title.trim() == "前書き"`）へ、または `RawChapter` に `isForeword/isAfterword` の型付きフラグを持たせて文字列判定を廃す。汎用アダプタは `SiteProfile.forewordMarkers` で HTML パース時点に既に除外済み＝Web 経路でこの判定が正の役割を果たす場面は無く、副作用だけが残っている。
- **分類**: **A**（同時に B: 新 ID `structural-marker-substring-match`）

### A2. Web 取込に in-flight ガードが無く、復旧操作が本を壊す
- **file:line** `android/app/src/main/java/com/novelreader/repository/WebBookImporter.kt:70,99-104`
- **機序**: PDF 側は `PdfProcessingService.activeUris`（ReentrantLock）で同一 URI の並行到達を構造的に断つが、Web 側に同等物が無い。`findBySourceUrl` の重複判定と `outputDir.deleteRecursively()` / `exportToPwa` / `insertBook` の間に排他が無い check-then-act。復元経路では `bookId = restoreTarget?.id` なので **2 ジョブが同一 outputDir を解決する**。
- **実害**: 一括復旧（`BookshelfScreen.kt:708`）が走行中に同じ本のカードをタップ → 再取得ダイアログ（`BookshelfScreen.kt:644`。実行中ガード無し）→ 片方の `deleteRecursively()` が他方の生成途中に走り、index.html だけ消えた torn 本になる＝**復元操作が本を欠落状態へ戻す**。新規側では別 UUID で二重 insert され同一作品のカードが 2 枚並ぶ（sourceUrl に UNIQUE 無し）。
- **直し方**: Web 取込入口へ URL キーの in-flight セット（PDF 側 ActiveUriTracker と同型）を置くか、`addWebBook` 全体を Mutex 化。併せて再取得ボタンへ実行中ガード。
- **分類**: **A**（B: 新 ID。既存 `shared-mutable-state-without-mutex` の隣）

### A3. 章0件の PDF 取込が「成功」で確定し、hasContent が健全と誤認定して復旧導線が全滅する
- **file:line** `android/app/src/main/java/com/novelreader/data/BookEntity.kt:131`（＋ `pdf/TextProcessor.kt:147`, `pdf/HtmlExporter.kt:122`, `repository/PdfBookImporter.kt:174-188`）
- **機序**: `TextProcessor` の固定トリム `pageNum < 3 || pageNum >= totalPages - 1` が **総ページ数 4 以下の PDF で全ページを除外** → 章0件。`HtmlExporter` は chap_N.html を 0 枚しか書かないのに index.html だけ無条件に書く。`PdfBookImporter` は章数を検査せず `Added` を返し「変換完了」通知が出る。そして `isTornContent` は `findAll(...).any{}` がリンク0本で false を返すため **hasContent が true**。
- **実害**: 短ページ PDF を取り込むと開けない本が棚に残り、欠落バッジも欠落バナーも出ず、同じ PDF を選び直しても `Duplicate` へ落ちて再取込で直せない＝削除以外に回復手段が無い。Web 側は `ScrapeIntegrity.verify` で同じ穴を塞いでおり、双子の経路のうち PDF だけ検査が無い。
- **直し方**: `PdfBookImporter` の確定前に `finalChapters.isEmpty()` を `PdfExtractionException` 系で弾く（`ScrapeIntegrity.verify` が手本）。併せて `isTornContent` を「リンク0本かつ chap ファイル0枚」も torn 側へ倒すか、hasContent を章ファイル実在ベースへ。
- **分類**: **A**（B: 新 ID `import-commits-without-integrity-check`）

### A4. SAF ピッカー中の再生成で「1冊復旧」が「全件一括再取込」に化ける
- **file:line** `android/app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:170,185-201`
- **機序**: 行き先フラグ `pendingScanBook` が plain `remember`。一方 `rememberLauncherForActivityResult` は登録キーを rememberSaveable で持つため**結果は再生成をまたいで必ず届く**。MainActivity に `configChanges` が無い（回転・ダーク切替・fontScale 変更で再生成）。`target == null` が「一括」を意味する規約（`:662-666`）なので、値が消えると必ず一括側へ落ちる非対称。
- **実害**: 欠落本のカード→「場所から探す」→ピッカー表示中に回転→フォルダ選択で `runSweepReimport` が走り、**全 AutoPdf を FGS キューへ投入・全 AutoWeb を再スクレイプ**し、最後に `persistSweepSeenIds` で一括バナーの指紋まで消費する。プロセス死経路では逆に `reimportPlans` が null で無言 return ＝完全無反応。
- **直し方**: `book.id` を `rememberSaveable` の String で持ち直して books から引き直す（BookEntity は Parcelable でないので remember→rememberSaveable の単純置換では直らない）。
- **分類**: **A**（B: 既存 `remembersaveable-missing-state-loss` へ再発追記）

### A5. 発見の「さらに読み込む」がページ重複でクラッシュする
- **file:line** `android/app/src/main/java/com/novelreader/viewmodel/DiscoveryViewModel.kt:259-271`
- **機序**: `current.novels + next.novels` を無条件連結（本番の `distinctBy` は BookshelfViewModel のみ）。供給側は `st` オフセット窓＋`(query,offset)` 単位の 6h キャッシュ（`narou/NovelApiRepository.kt:298,308,103`）で、**「1ページ目＝旧スナップショット／2ページ目＝ライブ」が正規**。順序が動く order（NEW・ランキング）では窓が重なり同一 ncode が 2 回入る。Lazy は各アイテムを `SaveableStateProvider(key)` で包むため、重複キーが同時コンポーズされた瞬間に IllegalArgumentException。
- **実害**: ランキング/新着で末尾までスクロール→「さらに読み込む」でアプリが落ち、積み上げたページが全消失。落ちる描画点は 4 スキン同型（`DiscoveryResultScreen.kt:487` / `DiscoveryPortalJ.kt:347` / `DiscoveryHomeSkyM.kt:354` / `DiscoveryCartridgeP.kt:379`）＝**release でも到達する**。
- **直し方**: 連結を `distinctBy { it.ncode ?: it }` へ（1 行）。加えて VM 単体テストで一意性を assert。
- **分類**: **A**（B: 既存 `lazy-items-missing-key-contenttype` へ再発追記。真因は「キー供給側の一意性が誰にも保証されていない」）

### A6. AppDatabase の二重チェックロックが内側で再チェックせず DB が二重生成される
- **file:line** `android/app/src/main/java/com/novelreader/data/AppDatabase.kt:364-377`
- **機序**: `INSTANCE ?: synchronized(this) { build().also { INSTANCE = it } }` に内側の `INSTANCE ?:` が無い。`NovelReaderApplication.repository` の `by lazy` が大半の経路を直列化するが、**`NewEpisodeCheckWorker.kt:50` だけが lazy の外からバックグラウンドで getDatabase を呼ぶ**＝唯一の競合経路。
- **実害**: 二重生成が起きると、DAO（コンストラクタ既定引数で確定）とトランザクション境界（`DefaultBookRepository.kt:59-61` で呼び出し時に再解決）が別インスタンスへ割れ、`LibraryDeleter.deleteBook` の原子性が無音で外れる＝**孤児 progress 行の残留**。加えて InvalidationTracker の取りこぼしで本棚 Flow が更新されない、接続の恒久リーク。
- **直し方**: 内側にも `INSTANCE ?:` を置く（1 行）。発生確率は低いが労力が最小で被害がデータ層。
- **分類**: **A**（B: 新 ID `singleton-dcl-missing-inner-check`）

### A7. dataSync FGS の `onTimeout` が 1 引数版のみ＝ API35+ で一度も呼ばれない
- **file:line** `android/app/src/main/java/com/novelreader/PdfProcessingService.kt:231`
- **機序**: `onTimeout(int)` は API34 の **shortService 専用**。dataSync の実行時間上限（API35 新設）でシステムが呼ぶのは `onTimeout(int, int)` で、Service 側の 2 引数版は 1 引数版へ委譲しない（AOSP `callOnTimeLimitExceeded` を確認）。本サービスは dataSync 単一型・targetSdk 36 なので、この override は**全バージョンで dead code**。コメント `:226-230` の API 契約記述も事実と食い違う。
- **実害**: 上限到達で `stopSelf()` が呼ばれず `RemoteServiceException` でプロセス強制終了。そこに積まれた既修正 3 件（`cancelled-scope-reuse-silent-stop` の scope 再生成／`stale-generation-coroutine-finally` の世代ガード／キュー・状態リセットと中断案内）が**まとめて発火しない**。発火条件は狭い（前面復帰でタイマーがリセットされる）が、dead code である事実は常時成立。
- **直し方**: `override fun onTimeout(startId: Int, fgsType: Int)` を追加し既存本体へ委譲（1 引数版は shortService 用として残すか削除）。コメントを事実へ修正。
- **分類**: **A**（B: 新 ID `fgs-timeout-overload-mismatch`。台帳の targetSdk 感応 API 全数登録制と併せて）

### A8. 恒常ボトムナビが 64dp 固定高で、フォントスケール拡大時にラベルが切り落とされる
- **file:line** `android/app/src/main/java/com/novelreader/ui/skins/k/KBottomNav.kt:57-63`
- **機序**: `Row(.height(64.dp))` の中で `padding(top=8dp) → Box(height=32dp) → Text(padding(top=4dp))` の順に測るため、ラベルに渡る maxHeight は 20dp 固定。器は全部 dp・中身だけ sp という非対称。
- **実害**: `KBottomNav` は `MainActivity.kt:835` で全スキン共通に描かれ **release の全ユーザーが通る**。fontScale 1.3 で「本棚/さがす/設定」が欠け、2.0 で字形が判別できない。**リポジトリ自身のゴールデン `KBottomNav_bookshelf_light_2.0.png` が壊れた絵を固定している**（verifyRoborazzi は退行しか止めないので CI では落ちない）。
- **直し方**: `.height(64.dp)` を `.heightIn(min = 64.dp)` へ（アイコン Box は固定のまま）。ゴールデン再記録。
- **分類**: **A**（B: 既存 `fontscale-large-breaks-layout` へ再発追記）

### A9. 作品詳細の作者行に weight が無く、長い作者名がジャンルタグを幅0まで押し出す
- **file:line** `android/app/src/main/java/com/novelreader/ui/discovery/NovelDetailScreen.kt:519-548`
- **機序**: Row 直下第1子の `Text(novel.summary.author)` に weight も maxLines も無く、残り幅を丸ごと取る。`SpaceBetween` は余りの配り方を決めるだけで測定順の食い合いを止めない。
- **実害**: 長ハンドルの作者でジャンルタグが 1 文字ずつ縦積み、超過時は枠ごと消える。`NovelDetailScreen` は `LocalSkin` を読まない共通画面＝**6 スキン全部・release 到達**。同一機序・同一データ源の実機バグ記録が `ui/discovery/DiscoveryCommon.kt:256-261` に一次情報として残っているのに詳細画面へ伝播していない。
- **直し方**: `DiscoveryCommon` と同じく作者側へ `weight(1f, fill = false)` + `maxLines=1` + ellipsis、タグ側へ `maxLines=1, softWrap=false`。
- **分類**: **A**（B: 既存 `row-weight-missing-pushes-out-trailing` へ再発追記）

### A10. 検索履歴チップのピン/× がヒット幅 13dp で、隣の語タップを誤爆する
- **file:line** `android/app/src/main/java/com/novelreader/ui/discovery/DiscoverySearchScreen.kt:711-714, 734-737`
- **機序**: `clickable → padding(vertical) → size(13.dp)` の順で、横方向には 1dp も足されない＝ヒット域は幅 13dp。その真横に語側の広いヒット域（`:724-727`、padding が clickable の後段）が密着する。
- **実害**: ピンを狙って 7dp ずれると検索が実行されて結果画面へ遷移（ピン留めされない）、語を狙って × に落ちると**履歴が消えて取り消し導線が無い**。発見タブはスキン分岐が無く release 到達。同ファイル外の 3 画面は既に 48dp 手当て済みで、この画面だけ取り残されている。
- **直し方**: 両アイコンへ `sizeIn(minWidth=48.dp, minHeight=48.dp)` または `minimumInteractiveComponentSize()`。
- **分類**: **A**（B: 既存 `a11y-touch-target-below-48dp` へ再発追記）

### A11. 本棚の選択状態が支援技術へ一切届かないまま複数削除へ進む
- **file:line** `android/app/src/main/java/com/novelreader/ui/BookCard.kt:286-293, 682-687`（同型 5 構造）
- **機序**: 選択マークが `Icon(contentDescription = null)`、カード本体は `combinedClickable` で `Role` も `selected` も宣言せず、直前の `semantics(mergeDescendants = true) {}` が子を 1 ノードへ畳む。`stateDescription` はリポジトリ全体で 0 件、`this.selected =` は `ui/skins/k/DiscoveryHomeK.kt:500` の 1 件だけ（しかも「視覚だけに閉じないように」という why 付き）＝規約は存在し本棚だけが外に居る。
- **実害**: TalkBack で各カードが選択済みか未選択かを取得できず、削除確認ダイアログも件数のみ（`BookshelfScreen.kt:1398` ほか 5 箇所が `選択した${n}件を…`）＝**削除対象を確認する手段が存在しない**。D/C・K を含むため release 到達。
- **直し方**: カードの `combinedClickable` を `toggleable(role = Role.Checkbox)` 相当へ、または `semantics { selected = ...; stateDescription = ... }` を宣言。最低限 D/C・K を先に。
- **分類**: **A**（B: 新 ID `selection-state-not-exposed-to-a11y`）

---

## B. 台帳へ登録（既存 ID への再発追記が中心・10 件）

### B1. ShelfChrome の案C/案X 5 フィールドを M/P/J の 6 面が読み捨て、走査の進捗も停止も一括復旧も無い
- **file:line** `android/app/src/main/java/com/novelreader/ui/skins/ShelfFace.kt:79-87`（読み手は `BookshelfScreen.kt:1108-1147` と `BookshelfK.kt:258-286` のみ）
- **機序**: 束の必須引数化は「渡し忘れ」しか止めず「受け取って捨てる」は止まらない。台帳の検知手段欄が自ら「構造封鎖（必須引数。ただしシート色・クローム欠落は封鎖の外）」と書いている、その封鎖の外の帯そのもの。
- **実害**: 走査の**起動は route 層で全スキン共通**（`BookshelfScreen.kt:306-317 → :604 → startFolderScan`）なのに、進捗表示と停止ボタン（`onScanStop` の唯一の呼び口）が M/P/J で描かれない＝数十〜数百件のハッシュ照合が無フィードバックで中断不能。`sweepBannerVisible` の唯一の消費点も無いため一括再取込ダイアログが到達不能。release は K 固定＝debug 限定。
- **直し方**: 6 面へバナーを配線するか、走査バナー自体を route 層（全スキン共通）へ引き上げる。後者なら 1 箇所で済む。
- **分類**: **B**（既存 `skin-wiring-omission` へ再発追記。L2 テスト `ShelfFaceWiringCoverageTest` の新設を推奨）

### B2. Web カードの複数選択削除が M/P/J の一覧 3 面に未配線で、選択モード中のタップが画面遷移に化ける
- **file:line** `BookshelfLogM.kt:358-365` / `BookshelfListCartridgeP.kt:258-265` / `BookshelfGridJ.kt:272-280`（受け側は `selected=false` ハードコード・`onClick = if (hasProgress) onResume else onOpen`）
- **機序**: `domain/ShelfItems.kt:436 webNcodesInSelection` を import しているのは D/C と K だけ。3 面は `ShelfItem.Web` 枝へ選択の 4 引数を渡さず、受け側 composable がシグネチャに選択を持たない＝画面全体の状態機械を Web カードだけが知らない。
- **実害**: 選択モード中に Web カードをタップすると WebView へ遷移して選択作業が中断（K/D では選択トグル）。長押しもメニューが開く。全選択は Web を除外し、削除確認も Web を外せない。debug 限定。
- **直し方**: 3 面の Web 枝へ `selectionMode/selected/onToggleSelect/onEnterSelection` を渡し、受け側を共有 `WebBookCard.kt:96` と同じ分岐へ。`BookshelfListCartridgeP.kt:292` / `BookshelfGridJ.kt:305` の陳腐化コメント（D の現状と食い違う）も更新。
- **分類**: **B**（既存 `skin-wiring-omission` へ再発追記）

### B3. M/P/J の取込バナーが `ProcessingState.source` を見ず、Web 取込で進捗が凍結表示される
- **file:line** `ui/skins/m/BookshelfSkyM.kt:502`（overall が恒久 0f）／`ui/skins/p/BookshelfCartridgeP.kt:938,882`（4 段ドットが「題名」で固定・現像ラベル 2 段固定）／`ui/skins/j/BookshelfPortalJ.kt:987`（同型）
- **機序**: `stepIndex/stepLocalPercent/stepTotal` は PDF 経路専用の器で、Web は `BookshelfViewModel.kt:953-966` が phase しか更新しない（0/0f/4 が全期間固定）。共有 `ui/ProcessingBanner.kt:102` はこの器を `source == PDF` で囲い、コメントに「2026-07-29 裁定②の真因＝PDF 専用の器を Web にも無条件描画していた」と明記しているのに、その分岐が 3 スキン実装へ横展開されていない。
- **実害**: phase 行は「章 5/48 取得中」と進むのに、バーは 0%・ステッパーは「題名」段で止まる＝**処理が止まっているのか進んでいるのか判別できない**。停止するか待つかの判断を誤らせる。debug 限定。
- **直し方**: 3 実装へ `if (state.source == ProcessingSource.PDF)` の分岐を入れる（共有バナーからの複製）。真因対処としては段の器を共有部品へ切り出す。
- **分類**: **B**（既存 `progress-ui-diverges-from-work` へ再発追記。3 サイトを 1 行に束ねる）

### B4. J 没入デッキの Pager に key が無く、蔵書の増減で見ている扉が別作品へ入れ替わる
- **file:line** `android/app/src/main/java/com/novelreader/ui/skins/j/BookshelfPortalJ.kt:343-357`
- **機序**: `PagerState` はキーでページ同一性を追跡する（`findIndexByKey`/`matchPageWithKey`）。key を渡さないので remap が原理的に不可能。供給元 `visible` は `BookDao.getAllBooks()` の二層ソートで、取込完了で入った新刊は必ず index 0 に挿入され以降が +1 ずれる。本棚の他 6 面は全て安定 key 済み＝掃討の漏れ。
- **実害**: 取込完了・フィルタ切替で、指を触れていないのに扉が差し替わり、画面唯一の主導線「続きから読む」が別の本を開く。debug 限定。
- **直し方**: `key = { page -> visible.getOrNull(page)?.id ?: "find" }`（1 行）。
- **分類**: **B**（既存 `lazy-items-missing-key-contenttype` へ再発追記。Pager の key 全数登録制を推奨）

### B5. J デッキだけ `chrome.isLoading` を読み捨て、Loading 中に生成した pagerState が hero 着地を殺す
- **file:line** `android/app/src/main/java/com/novelreader/ui/skins/j/BookshelfPortalJ.kt:314, 330-344`
- **機序**: 7 面のうち J だけが `val isLoading = chrome.isLoading` を宣言して一度も参照しない。VM は必ず `Loading` から始まり（`BookshelfViewModel.kt:266`）ルーターは uiState を見ずに委譲するので、cold start の初回コンポーズを books=empty で通る。そのとき `rememberPagerState(initialPage = 0)` が確定し、後から heroIndex が本当の値になっても適用する `LaunchedEffect` が無い。
- **実害**: 「hero＝いま読みかけの先頭作＝最初に開く扉」という宣言済みの機能が計算だけされて死ぬ。cold start 数フレームの空フラッシュも同伴。debug 限定。
- **直し方**: `isLoading` を空状態の門として使う（他 6 面と同型）か、visible 確定後に `scrollToPage(heroIndex)` を一度だけ。
- **分類**: **B**（既存 `skin-wiring-omission` へ再発追記）

### B6. J/M/P の本棚カードが章数不明（0）を「全0話」と数で描く
- **file:line** `BookshelfLogM.kt:715,719` / `BookshelfSkyM.kt:649,650` / `BookshelfPortalJ.kt:485` / `BookshelfCartridgeP.kt:1214,1236` / `BookshelfListCartridgeP.kt:435,444`（全 9 箇所）
- **機序**: `chapterCountMap[book.id] ?: 0` の 0 は「章数不明／本文実体が無い」しか意味しないが、3 スキンは ReadingStatus 分岐だけで数を差し込む。D 共通は `progressFractionFor` が `totalChaps <= 0` で null を返す枝でしか話数を出さず、構造的に 0 話表示が起きない。
- **実害**: uninstall→Auto Backup で DB だけ復元された端末では全冊が「全0話・未読」、読了本は「全0話 · 読了」＝実在しない事実の表示。復旧導線自体は route 層で全スキン共通に生きているので操作不能ではない。debug 限定。
- **直し方**: `totalChaps > 0` のガード内でのみ数を描く（D の `BookProgressRow` が手本）。
- **分類**: **B**（既存 `missing-data-rendered-as-fabricated` へ再発追記）

### B7. 内容識別子（書名・作品URL・SAF パス）が release の logcat に残る
- **file:line** `NewEpisodeCheckWorker.kt:161` / `PdfTreeScanner.kt:43,52,62,92` / `BookshelfViewModel.kt:525` / `WebBookImporter.kt:166`（Throwable 経由の間接漏れ）／出所は `scrape/ScrapeHttpClient.kt:100-101` と `PdfTreeScanner.kt:33` の例外メッセージ
- **機序**: `proguard-rules.pro` に `-assumenosideeffects class android.util.Log` が無く BuildConfig.DEBUG ガードも無いため Log は release に残る。`docUri` は `primary:Download/なろう_〇〇.pdf` 形＝フォルダ構成とファイル名（≒書名）が平文。同じ機序は `viewmodel/PdfImportViewModel.kt:100-103` で「ログもまた保存層」として既に真因対処済みで、規約が 1 箇所にしか適用されていない。
- **実害**: バグレポート同梱／READ_LOGS を持つ OEM 診断アプリ／adb logcat 経由で端末外へ出うる。外部送信 SDK は無く、露出は端末所有者の操作に限られる。
- **直し方**: 定数ログ化（`PdfImportViewModel` が手本）。例外メッセージ側も URL を落とすか、ログへ渡す箇所で message を出さない。
- **分類**: **B**（新 ID `log-leaks-content-identifier`。7 箇所を 1 行に束ねる）

### B8. 本棚の選択モード BackHandler が他タブ表示中も生きて Back を 1 回黙って食う
- **file:line** `android/app/src/main/java/com/novelreader/ui/BookshelfScreen.kt:898`
- **機序**: `TabPagerHost` は `beyondViewportPageCount = 1` で隣ページを常駐コンポーズするため、現在ページが「さがす」でも本棚の `BackHandler(enabled = selectionMode)` が登録されたまま。OnBackPressedDispatcher は後着優先なので枠側（`TabPagerHost.kt:51`）に必ず勝つ。enabled 条件に「このページが前面か」が欠けているのが真因。
- **実害**: 選択モードのまま別タブへ移って Back を押すと画面が一切変化せず（見えない選択が解除されるだけ）、選択も失われる。2 回目で本棚へ戻れるので幽閉はしない。release 到達だが動線は稀。
- **直し方**: `enabled = selectionMode && pagerState.currentPage == 0` 相当の可視性の項を足す（ページ側へ可視フラグを渡す）。
- **分類**: **B**（新 ID `tab-page-backhandler-without-visibility`）

### B9. 取込 WebView が saveState/restoreState を持たず、構成変更で縦書きPDF生成フローが巻き戻る
- **file:line** `android/app/src/main/java/com/novelreader/ui/discovery/PdfImportScreen.kt:117,230-231`
- **機序**: 隣の `WebReaderScreen.kt:86-91,193` は同じ AndroidView(WebView) 構図に rememberSaveable の custom Saver を張って同機序を塞ぎ、KDoc に「2026-07-12 persist Major」と経緯まで書いてある。PdfImportScreen は plain remember＋無条件 `loadUrl(menuUrl)`。
- **実害**: なろうの多段フロー（作品ページ→縦書きPDF→書式設定→生成）の途中で回転・ダーク切替が起きると作品ページ先頭へ戻る。永続データは失われず数手のやり直しコスト。release 到達。
- **直し方**: `WebReaderScreen` の Saver をそのまま移植（ADR 0010/0012 の JS 注入禁止規約には触れない＝既に判断済み）。
- **分類**: **B**（既存 `webview-position-mis-record` / `remembersaveable-missing-state-loss` へ再発追記。直すなら低コスト）

### B10. fontScale 拡大で潰れる固定寸の器（残り 3 箇所）
- **file:line** `ui/NcodeLinkSheet.kt:215-258`（Column 120dp 固定高で再試行ボタンが数 dp へ痩せ 48dp 標的が消える）／`ui/skins/p/DiscoveryCartridgeP.kt:671-700`（66×88dp + `.clip` でジャンル名の最終行が無音で切られる）
- **機序**: A8 と同一——器が dp・中身が sp。`sizeIn(minHeight=48.dp)` は incoming constraints へ coerce されるので固定高の中では無効化される。`.clip` 付きは溢れが視認できず偽陰性が痛い。
- **実害**: 前者は fontScale 2.0＋通信不通でエラー文が 3 行になると再試行が押せない（同シート上部の検索ボタン 48dp が代替になるので操作不能ではない）。後者はどの背表紙がどのジャンルか判別できない（色は機械割り当てで意味を持たない）。いずれも fontScale 2.0（プロジェクトのゴールデン標準条件）で発生。
- **直し方**: 固定高を `heightIn(min=)` へ／テキスト側に `weight(1f)` を与えてボタン領域を先に確保／背表紙は実採寸か縦書き化。
- **分類**: **B**（既存 `fontscale-large-breaks-layout` へ再発追記。A8 と同じ 1 行に束ねる）

---

## C. 情報として記録（4 件）

### C1. 本棚の遷移ジャンク対策 `deferHeavyContent` が D/C 共通描画にしか届かず、release の既定スキン K では常に死んでいる
- `ui/BookshelfScreen.kt:1247`（唯一の読み口がスキンルーター `:949-957` の 288 行下流）。3 段渡ってきた引数が M/P/J/K で一度も読まれない。後発の目次側（`NativeTableOfContentsScreen.kt:121-124`）は同じ機構をルーターより上流に置き「骨をスキン共通の1式で済ませる裁定」と明記＝設計意図は全スキンで効くこと。影響はジャンク（実測 51ms/フレームは D のグリッド値で K の測定値ではない）で機能破綻ではないため C。直すなら骨差し替えをルーター上流へ移すだけ。

### C2. `reduceMotion` が無キー remember で凍結し「アニメを削除」が効かない
- `MainActivity.kt:425` ほか計 8 箇所が個別に `ANIMATOR_DURATION_SCALE` を読み、全て remember で固定。ContentObserver はリポジトリに 0 件。設定変更は構成変更を伴わないので Activity は再生成されず、プロセスを殺すまで反映されない。M の視差は `SkyParallaxController` がコンストラクタ束縛でさらに二重に固まる。実害は「後から ON にしても動きが止まらない」で、M は debug 限定。判定源を 1 ファイルへ集約し購読可能にするのが筋。

### C3. なろう紐付けシートの入力欄 2 本が remember＝シートは復元されるのに入力だけ消える
- `ui/NcodeLinkSheet.kt:92-93,137-139,355-357`。開閉フラグだけが `NativeReadingScreen.kt:881` で Saveable 化されており救済が半分しか効いていない。到達は「未紐付け本の最終章まで読み進める」限定（⋮メニュー経路は存在しない）、失われるのは検索語の編集分と N コード 8 文字。復元後の状態は自己整合。

### C4. per-host スロットルの Mutex がインスタンス局所で、実フェッチする registry が 2 つある
- `scrape/SiteAdapterRegistry.kt:92` が registry ごとに `ScrapeHttpClient()` を新規生成し、`ScrapeHttpClient.kt:48-50` の gate/lastRequestByHost はインスタンスフィールド。`DefaultBookRepository.kt:71` と `NewEpisodeCheckWorker.kt:148` が互いのロックを見ない。`defaultAdapters` の why コメントが「個別に new すると床が掛からない」と宣言した不変条件が registry 境界で破れている。ただし実際に起きるのは同一ホストへ瞬間 2 並列（実効 2req/s）までで、両者とも各自 1000ms 床を守る直列ループ・Worker は 24h 周期・オプトイン。**設計の不整合として記録に留める**（下記却下欄も参照）。

---

## 却下したもの

| 件 | 却下理由 |
|---|---|
| `scrape/SiteAdapterRegistry.kt:92` の「403 で取込が確定失敗し恒久ブロックへ向かう」 | 相手サーバ挙動の推測でコード上の裏付けが無い。実測できる影響は瞬間 2req/s のみで、データ破壊・クラッシュ・UI 破綻いずれも生じない。**不変条件の破れとしてのみ C4 に残す**（実害としては却下）。 |
| `ui/skins/m/TocSkyM.kt:377` / `ui/skins/j/TocPortalJ.kt:344` の話数ラベル 52dp 直書き | fontScale 1.0 では 4 桁話数まで収まる（K の 44dp とは前提が違う）。破綻は fontScale 1.5 以上で、しかも症状は「第132／話」の 2 行折り返し＝**情報は読める**（行高が跳ねる＝整列の乱れ）。機能破綻でなく整列の意匠寄りと判断。K の実採寸（`rememberEpLabelWidth`）を M/J へ伝播する価値はあるが、今回の優先度には載せない。 |
| `ui/skins/m/BookshelfLogM.kt:565` の選択ヘッダ「解除」「星を消す」23〜34dp | 横方向の誤爆が無く、外した結果は誤操作でなく**無反応**＝押し直せば当たる。× には `BookshelfScreen.kt:898` の BackHandler という独立の代替導線がある。M は debug 限定。48dp 規約違反ではあるので、台帳 `a11y-touch-target-below-48dp` の箇所列挙に含めるだけで足りる。 |

---

## 台帳（`docs/known-bugs-registry.md`）への反映案

- **新 ID 9 件**: `structural-marker-substring-match`（A1）／`web-import-check-then-act-no-inflight-guard`（A2）／`import-commits-without-integrity-check`（A3）／`singleton-dcl-missing-inner-check`（A6）／`fgs-timeout-overload-mismatch`（A7）／`selection-state-not-exposed-to-a11y`（A11）／`log-leaks-content-identifier`（B7）／`tab-page-backhandler-without-visibility`（B8）／`reduce-motion-frozen-in-remember`（C2）
- **既存 ID への「再発」追記 7 件**: `lazy-items-missing-key-contenttype`（A5・B4）／`fontscale-large-breaks-layout`（A8・B10）／`row-weight-missing-pushes-out-trailing`（A9）／`remembersaveable-missing-state-loss`（A4・B9・C3）／`skin-wiring-omission`（B1・B2・B5・C1）／`progress-ui-diverges-from-work`（B3）／`missing-data-rendered-as-fabricated`（B6）／`a11y-touch-target-below-48dp`（A10・却下 3 件目の箇所列挙）
- **状態列の見直しが要る行**: `skin-wiring-omission` は「構造封鎖（必須引数）」を防御に数えているが、B1/B5 が示すとおり**必須引数は「受け取って捨てる」を止めない**。検知手段欄へその限界を明記するか `[~] 部分` の根拠を書き換える。`stale-generation-coroutine-finally` と `cancelled-scope-reuse-silent-stop` は修正の所在が `onTimeout` 本体＝A7 により**両方まとめて dead** なので、A7 を直すまで状態を実質 `[!]` として扱う。

---

## 付録: この監査で確かめられていないこと（完全性批評の判定）

## 確かめられたこと

15軸のうち一覧に上がったのは14軸（A1・A2・A4・A5・A6・B1・B2・B3・B4・C1・C2・C3・C4・C5）で、**A3 が欠番**。A3 が何の軸だったのか、走査されて0件だったのか未実施なのかが分からず、その分の領域は空白のまま扱うしかない。まずここを監督が確認すべき。

ファイル面のカバレッジは、行数で見るとよく塗れている。全190ファイル53,081行のうち、C4 が pdf/ scrape/ narou/ data/ repository/ parser/ typeset/ の53ファイル約5,800行を全文 Read、B3 が domain/ model/ viewmodel/ を全文、C2 が @Composable を含む66ファイルの副作用サイトを全数列挙、A5 が Dispatchers 46行・広域 catch 14件を全数。**同じ機序が別箇所で生きているか**という今回の主眼に対しては、登録簿の主要 ID（ncode 正規化・孤児資源・世代ガード・runCatching のキャンセル握り潰し・OkHttp タイムアウト・route リテラル pop・HTML エスケープ・縦書き分類器の迂回）はいずれも複数軸から独立に再確認され、再発箇所は出ていない。これは信頼してよい結果だと思う。

## 原理的に確かめられていないこと

1. **誰も Gradle を回していない。** C1 は「15体並列稼働中の過負荷を避けて意図的に見送った」と明記し、A5 は「HazardousPatternScanTest が現時点で緑であることは実測していない」と書いた。つまり今回の全報告は「テストが緑である」ことすら前提にしていない。C1 の Compose 安定性判定（生存0件）は composables.txt / classes.txt なしの静的推定で、`./gradlew :app:assembleRelease -PcomposeCompilerReports=true` 1発で裏取りできる。
2. **実機でしか出ない層。** targetSdk 36 × Android 15/16 の挙動変化（gap 2 の主題）、fontScale 拡大時の実 clip 位置、48dp タップ標的の実指、OEM の FGS freeze、edge-to-edge 強制。Robolectric は全ファイル @Config(sdk=[34]) 固定で、build.gradle:426 自身が「SDK 35/36 固有の実行時変化はここでは絶対に捕まらない」と書いている。
3. **負荷でしか出ない層。** C4 は 8,668ページ PDF の OOM 候補をリポジトリ内の実測記録（約6秒で完走）を根拠に**棄却した**——これは正しい判断だが、裏返せばメモリ実測は今回誰も取っていない。A2 の fontScale 破綻5件もフォントメトリクスからの見積もりで、実測で裏が取れたのは KBottomNav の golden PNG 1件だけ。
4. **時間経過でしか出ない層。** FGS dataSync の6h/24h 上限、WorkManager 定期実行、pending_jobs の再開ループ（C4 が「決定的に落ちる変換の実在を立証できず棄却」＝棄却であって不在の証明ではない）、キャッシュ TTL。
5. **外部応答でしか出ない層。** なろう API のスキーマ変化・上限。B3/C4 は現在のモデル定義とパース経路の整合しか見ていない。

## 自己申告のうち、割り引いて読むべきもの

- **A2 は走査軸の自己定義が狭い。** 5本の走査に48dpタップ標的も contentDescription も入っておらず、それでいて「レイアウト機能破綻」を名乗っている。ブリーフが明示的に対象と宣言した項目が抜けている（gap 1 の根拠）。加えて Row 押し出し47件のうち45件を「個別に読んで押し出しが起きないと確認」で棄却しており、この棄却根拠は再現可能な形で残っていない。
- **A4 は範囲を狭めた上での「生存0件」。** 「ui/ 配下の LaunchedEffect 本文は全数読んでいない」「ui/discovery と ui/skins/{j,k,p} の LaunchedEffect は grep で launch/cancel を含まないことだけ確認して中身は未読」と自認したうえでの0件。しかも「DeepSkyM の流星ループは BuildConfig.DEBUG ゲート下」という前提が SkyBackdropM.kt:177/182 と矛盾する（gap 3 の根拠）＝範囲外に置いた理由そのものが誤っている。
- **B1・B3 が揃って同じ帯を担当外にした。** スキン分岐を持たない画面群（DiscoverySearchScreen 845行／NovelDetailScreen 883行／SearchConditionSheet 746行／WardrobeScreen 511行／PdfImportScreen 263行／WebReaderScreen）計3,400行超が、B1「1スキンだけ配線漏れが原理的に起きないため担当外」・B3「全文は未読」で構造レビューから抜けている。偵察ブリーフ自身が「ここは6スキン全部で同時に壊れる」と警告した帯。gap 1 の一次証拠（DiscoverySearchScreen.kt:711-714）がここから出たのは偶然ではないと思う。次ラウンドで gap を1つ増やせるならこの帯の本体精読。
- **C1・C2・A4 の「生存0件」は3件とも範囲限定つき。** 悪い仕事という意味ではなく、0件を「その軸は安全」と読むと誤る、という意味。
- 逆に **C4（全53ファイル全文 Read・OOM と pending_jobs 無限再開をどちらも証拠不足で自ら棄却）と A1（Compose のバイトコードまで一次確認）は、自己申告の検証可能性が高く、額面どおり読んでよい**と判断した。

## 調べたうえで gap にしなかったもの（次ラウンドで掘り直さなくてよい）

- **時刻・タイムゾーン依存**: 健全。`narou/model/DiscoveryQuery.kt:119` の lastupApiParam は zone 既定を `ZoneId.of(\"Asia/Tokyo\")` に固定し「なろうのプリセットはサーバ＝日本時間の暦で解釈されるため端末TZに依らず意味を揃える」と why 付き。`ui/discovery/DiscoveryCommon.kt:108` も同じ。systemDefault を使うのは diagnostics の2箇所（ログ表示のみ）。
- **ロケール依存の数値・書式**: 健全。`String.format` の全出現が Locale.US または Locale.JAPAN を明示（pdf/HtmlExporter.kt:181／ui/ReadingSettingsSheet.kt:505 には「既定ロケールだと欧州端末等で小数点が」という why まである）。本番に `Locale.getDefault()` での大小変換は0件、生 `uppercase()/lowercase()` はホスト名正規化用の5件のみ。
- **R8 / リフレクション破綻**: 健全。Moshi は codegen（KSP）で `KotlinJsonAdapterFactory` 不使用、proguard-rules.pro に `-keep class com.novelreader.**JsonAdapter` と `-keepnames @JsonClass` があり、逆アセンブルで機序を確認した記述まで残っている。Retrofit/Room/WorkManager/pdfbox は consumer rules で担保済みと実物確認済みの記載あり。enum も防御 keep 済み。
- **Room の TypeConverter 非対称**: 該当なし。`@TypeConverter` は本番に0件で、全カラムがプリミティブ。NOT NULL × DEFAULT の対も AppDatabase.kt の各 migration に揃っている。
- **predictive back の未オプトイン**: 該当なし。AndroidManifest.xml:30 で明示 true。ただし「オプトインしている」ことと「トグル用途の BackHandler が正しく振る舞う」ことは別で、後者は gap 2 の (2)(c) に含めた。