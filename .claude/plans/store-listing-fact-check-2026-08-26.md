# ストア掲載文（詳細な説明）× 実装 の全数事実照合（2026-08-26）

対象＝`docs/store/listing-draft.md` §3「▼本文ここから▼」〜「▲本文ここまで▲」（1,255字）の**機能の主張のみ**。
文章の良し悪しは評価しない。実装は変更していない。照合先＝`android/app/src/main/`・`docs/store/privacy-policy-draft.md`・
`docs/store/data-safety-draft.md`・`STATUS.md`・ADR 0010/0011/0012/0015/0024/0027。

**判定件数: MATCH 27 ／ MISMATCH 2 ／ OVERSTATED 4 ／ UNVERIFIABLE 2（計 35 主張）**

⚠️ 公開ビルドの UI は **明快K 単独**（`BuildConfig.SKIN_SWITCHING_ENABLED=false`＝`Features.kt:30`、
`ui/theme/Skin.kt:64` が release では常に `Skin.MEIKAI_K` を返す）。UI 側の判定はすべて K 装いで確認した。

---

## 1. MISMATCH（＝要修正・実装と食い違う）

### M1. 「並べ替えて探せます」— 並べ替え UI は存在しない

- **本文**: 「・本棚で蔵書を管理　読みかけ・読了などの状態で絞り込み、**並べ替えて**探せます。」
- **事実**: 並び順は**固定**の「最近の活動順」二層キー（tier1=未接触は `addedAt` 降順／tier0=読んだ本は `lastReadAt` 降順＝ADR 0016）。
  ユーザーが選べるソート UI は K を含めどの装いにも無い。
  - `android/app/src/main/java/com/novelreader/domain/ShelfItems.kt:45`（`RecencyKey`）・`:53`・`:68`・`:171`（`mergeShelfItems`）
  - K は同純関数を再利用＝`android/app/src/main/java/com/novelreader/ui/skins/k/BookshelfK.kt:218`
  - 0件だった検索語（`android/app/src/main/` 全体）: `SortOrder` / `sortOption` / `SortKey` / `sortKey` / `SortSheet` /
    `SortMenu` / `昇順` / `降順` / `タイトル順` / `追加順` / `最近読` — いずれも 0 ヒット。
    `並べ替え`/`並び替え` の全ヒットは PDF 取込・API マージ・扉色の安定性に関するコメントのみ。
- **絞り込みの側は MATCH**: すべて／よみかけ／未読／読了 の4択（`BookshelfK.kt:704` `KStatusChips`、ラベル `:727`/`:731`/`:737`/`:742`、
  実行 `:221` `filterShelfByStatus`、状態3値の定義 `ShelfItems.kt:242`）。
- **代案文（84字・旧61字＝+23字）**:

```
　読みかけ・読了などの状態で絞り込んで探せます。並び順は、最後に読んだ本が上に来る「最近の活動順」です。目次では既読の話に印が付き、読みかけの位置からすぐ再開できます。
```

### M2. 「通信するのは…3場面だけ／いずれもあなたの操作に応じてその場で」— 背景通信と Play 通信が漏れている

- **本文**: 「通信するのは、作品を検索するとき・サイトを表示するとき・作品を取り込むときだけで、**いずれもあなたの操作に応じてその場で**行われます。」
- **事実（漏れ①＝背景の日次通信）**: 新着チェックは **WorkManager の 24h 定期実行**で、ユーザーの操作とは無関係に走る。
  接続先は**なろう公式APIだけでなくカクヨム・暁にも及ぶ**（Web 蔵書の目次再フェッチ）。
  - スケジュール＝`android/app/src/main/java/com/novelreader/NovelReaderApplication.kt:298-310`
    （`PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(24, TimeUnit.HOURS)`・`NetworkType.CONNECTED`・`KEEP`）
  - なろうAPI 照会＝`android/app/src/main/java/com/novelreader/NewEpisodeCheckWorker.kt:76`（`novelDetailsBulk`）
  - **カクヨム・暁への目次照会**＝同 `:213`（`supported.adapter.fetchToc(...)`）
- **事実（漏れ②＝Google Play への評価シート要求）**: 初回読了時に Play へ評価画面の表示を要求する。
  - `android/app/src/main/java/com/novelreader/review/ReviewPrompter.kt:30-33`（`requestReview()`→`launchReview()`）
  - 発火点＝`android/app/src/main/java/com/novelreader/viewmodel/BookshelfViewModel.kt:1160-1166`（`reachedEnd` false→true の one-shot）
    → `MainActivity.kt:408,411`
  - 依存＝`android/app/build.gradle:431`（`com.google.android.play:review-ktx:2.0.2`）
- **⚠️ これは自社ドキュメント間の不整合でもある**（＝Play の停止事由に最も近い形）。
  `docs/store/privacy-policy-draft.md` §2 は**通信場面を5つ**列挙しており（検索／サイト表示・PDF取得／対応サイト取込／
  **新着話チェック（1日1回）**／**アプリの評価の打診**）、ストア本文の「3場面だけ」と正面から食い違う。
- **代案文（124字・旧65字＝+59字）**:

```
通信するのは、作品を検索するとき・サイトを表示するとき・作品を取り込むとき、そして新着のお知らせをオンにしている場合の1日1回の確認だけです。このほか、初めて1作を読み終えたときに、Google Playの評価画面を1回だけ表示することがあります。
```

- **⚠️ 併せて直すべき（本照合の対象外だが同根）**: `privacy-policy-draft.md` §2 の新着話チェック行は通信先を
  「小説家になろう公式 API」だけと書いているが、実装は**カクヨム・暁へも目次照会する**（`NewEpisodeCheckWorker.kt:213`）。
  データセーフティ申告（`data-safety-draft.md` §0 の表）も同様に新着チェックをなろうAPIのみとして整理している。

---

## 2. OVERSTATED（＝言い過ぎ・条件付きなのに断定している）

### O1. 「アプリ独自の縦書き描画で表示します」— 既定は**横書き**。縦書きは設定でONにするオプション

- **本文**: 冒頭「…ふりがな（ルビ）が付いたまま、**アプリ独自の縦書き描画で表示します**。」
  ＋ 特長「・**ふりがな付きの縦書き表示**　…縦書きで読めます。…**横書き表示にも切り替えられます**。」
  ＝「縦書きが既定・横書きが代替」と読める。
- **事実**: 縦書きは `app_prefs` の Boolean トグルで**既定 false＝横書き**。
  - `android/app/src/main/java/com/novelreader/ui/NativeReadingScreen.kt:406-407`
    （`prefs.getBoolean(PrefKeys.READING_VERTICAL, false)`）
  - コメントで明言＝同 `:404-405`「縦書きモード…**既定 false＝横書き**で既存ユーザーの見た目は不変」
  - 切替 UI＝`android/app/src/main/java/com/novelreader/ui/ReadingSettingsSheet.kt:414-441`
    （読書設定シート内の FilterChip「縦書き」・`:432` で現在値を「縦書き」/「横書き」と表示）
  - キー定義＝`android/app/src/main/java/com/novelreader/PrefKeys.kt:61-62`
- **縦書きの実装そのものは実在する**（＝能力の主張は真）: `typeset/VerticalTypesetter.kt`・`typeset/RubyPlacer.kt`・
  `typeset/TateChuYoko.kt`・`ui/VerticalChapterContent.kt`・`ui/compose/VerticalParagraph.kt`（ADR 0020）。
  問題は「既定でそうなる」と読める書き方だけ。
- **代案文（冒頭 63字・旧59字／特長 106字・旧88字）**:

```
小説家になろうが配布している縦書きPDFを取り込み、ふりがな（ルビ）が付いたまま表示します。縦書き・横書きはお好みで選べます。
```

```
　なろうの縦書きPDFを取り込むと、ルビを保ったまま読めます。画像ではなく文字として組み直すので、文字サイズや行間を変えても崩れません。表示は横書きが初期設定で、読書中の設定からいつでも縦書きに切り替えられます。
```

### O2. 「アプリ内の広告表示そのものがありません」— アプリ内 WebView で表示するなろうのページには広告が載る

- **本文**: 「・広告が一切ありません　読書中に広告が割り込むことはありません。**アプリ内の広告表示そのものがありません**。」
- **事実**: アプリは**アプリ内 WebView** でなろうのページを表示し、そこには当然なろう側の広告が表示される。
  しかも PDF 取込導線は**広告付きの生成ページを構造的に必ず経由する設計**で、これは規約順守のための意図的な仕様。
  - なろう話ページの読書 WebView＝`android/app/src/main/java/com/novelreader/ui/discovery/WebReaderScreen.kt:150-195`
    （ルート＝`MainActivity.kt:865-880`）
  - PDF 取込 WebView＝`android/app/src/main/java/com/novelreader/ui/discovery/PdfImportScreen.kt:122,242-257`
  - ADR 0011「0010 との線引き（規約の厳守事項）」＝**「広告は絶対に残す」「生成ページ（広告＋出だし200字）を
    構造的に必ず経由する点はむしろ規約上プラス（広告が必ず表示される）」**
  - `privacy-policy-draft.md` §4 も「表示されるページ内の Cookie や**広告**等は、各サイトおよびその広告事業者によって
    取り扱われる」と、アプリ内に他社広告が出る前提で書かれている。
- **「アプリ自身が広告を出さない」は真**（広告SDKゼロ＝下の MATCH #24）。断定の範囲だけが広すぎる。
- **代案文（89字・旧41字＝+48字）**:

```
　読書中に広告が割り込むことはありません。このアプリが広告を表示することはありません（なろうのページをアプリ内で開いたときは、そのページに載っている広告がそのまま表示されます）。
```

### O3. 「蔵書・読書位置・設定はすべて端末の中に保存され」— Auto Backup で Google のサーバーへ載る

- **本文**: 「・アカウント登録が不要　…**蔵書・読書位置・設定はすべて端末の中に保存され**、こちらのサーバーへ送られることはありません。」
- **事実**: `allowBackup="true"` で Android の自動バックアップが有効。**include 列挙の3つ**が Google のサーバーへ載る
  ＝ Room DB（蔵書メタ・読書位置・しおり）／SharedPreferences（テーマ・文字サイズ等の設定）／DataStore（検索履歴）。
  - `android/app/src/main/AndroidManifest.xml:34,36,37`
  - `android/app/src/main/res/xml/backup_rules.xml`（`<include domain="database" path="."/>`・`sharedpref`・`file path="datastore/"`）
  - `android/app/src/main/res/xml/data_extraction_rules.xml`（`cloud-backup`・`device-transfer` とも同一3件）
  - 設計判断＝ADR 0015（層別 Auto Backup）
- **「こちらのサーバーへ送られない」は真**（自前サーバー不存在＝下の MATCH #23）。問題は「**すべて**端末の中」の断定。
- **⚠️ 掲載文自身の安全弁に違反している**: 同 `listing-draft.md` §2 に
  「**「完全オフライン」「端末内で完結」は書かない**（安全弁＝`../marketing/positioning-brief.md`。
  **自動バックアップで利用者の Google ドライブに載るため**）」という明示の注意があり、§3 本文がそれを踏んでいる。
  `privacy-policy-draft.md` §5 も対象・対象外を正しく書き分けているので、ここもドキュメント間不整合。
- **代案文（152字・旧68字＝+84字）**:

```
　メールアドレスも会員登録も必要ありません。蔵書・読書位置・設定は端末の中に保存され、こちらのサーバーへ送られることはありません。端末の「バックアップ」設定が有効な場合のみ、本棚の登録内容・読書位置・設定がAndroidの標準機能でご自身のGoogleアカウントへ保存されます（作品の本文は対象外です）。
```

### O4. 「本棚に入れた作品の新着話を…確認して」— 対象は本棚全部ではない

- **本文**: 「・新着のお知らせ（初期設定はオフ）　設定でオンにすると、**本棚に入れた作品**の新着話を1日1回だけ確認してお知らせします。」
- **事実**: 照会対象は2系統だけ＝①`books.ncode` が非 null のなろう紐付け蔵書 ②`books.sourceUrl` が非 null の Web 蔵書
  （カクヨム・暁）。**手元の PDF をファイル選択で取り込んだ本（ncode 未紐付け）は対象外**で、新着は一切通知されない。
  - `android/app/src/main/java/com/novelreader/NewEpisodeCheckWorker.kt:53-61`（`linkedBooks` は `book.ncode` から、
    `webBooks` は `it.sourceUrl != null` で絞る）・`:62-68`（両方空なら即 `Result.success()`）
  - なろう縦書きPDF取込経路だけが ncode を書き込む＝`repository/PdfBookImporter.kt:274,289`
    （「なろう縦書きPDF…は自動的に NULL になる」ケースも明記）
  - さらに Web 蔵書は「既読話数が取込済み章数へ追いついた本」だけ再フェッチ＝`NewEpisodeCheckWorker.kt:202`（`shouldCheckWebBookNow`）
- **「初期設定はオフ」「1日1回だけ」は MATCH**（下の #14・#15）。
- **代案文（60字・旧42字＝+18字）**:

```
　設定でオンにすると、作品ページと結び付いている本（なろう・カクヨム・暁）の新着話を1日1回だけ確認してお知らせします。
```

---

## 3. UNVERIFIABLE（＝コードからは確認できない）

### U1. 「文字サイズや行間を変えても崩れません」

- **確認できたこと（機構）**: 設定変更で再組版が走り、値は**安全な範囲へクランプ**される。
  - フォント 14〜24sp＝`ui/NativeReadingScreen.kt:359`（`.coerceIn(14, 24)`）
  - 行間 2.3〜2.8em＝同 `:379`（`.coerceIn(2.3f, 2.8f)`）。範囲を狭く保つ理由がコメントに明記＝
    同 `:373-377`「**狭めるとルビの描画領域（字面より上の leading）が前行と被るリスクは残る**」
    ＝「崩れない」は**設計で範囲を絞った結果**であって無条件の性質ではない。
  - 縦書き再組版の入口＝`typeset/VerticalTypesetter.kt:139`
- **確認できないこと**: 「崩れない」という**描画結果の品質**。機械的証拠は Roborazzi golden のみで、
  そのカバレッジは `fontScale = 1.0` の1点に限られる（`android/app/src/test/java/com/novelreader/ui/screenshot/VerticalParagraphScreenshotTest.kt:29-30,51`
  ＝「LIGHT/DARK の2テーマ・fontScale 1.0 のみ」と明記）。
- **どうすれば確認できるか**: ①端末の文字サイズを最大にした状態で、本文 24sp / 行間 2.8em・**縦横両モード**・
  ルビの多い章と約物（『』（）…—）の多い章で実機目視（`/device-verify` または `/emulator-verify`）。
  ②機械化するなら `fontScale = 2.0` と本文サイズ上下限の golden を追加する。
- **検証しないなら**の退避案（29字）:

```
　文字サイズや行間は、読みやすい範囲で細かく調整できます。
```

### U2. 「株式会社ヒナプロジェクトが運営する「小説家になろう」」

- 第三者の運営主体はコードにもリポジトリにも根拠が無い（`rg 'ヒナプロジェクト'` のヒットは
  `docs/store/listing-draft.md:254`＝**当の本文自身のみ**）。
- **どうすれば確認できるか**: なろう公式サイトの運営者情報／会社概要ページと、法人登記または公式プレスの2点で照合する
  （外部事実＝一次ソース2点照合のルール）。免責文の要である以上、社名の表記ゆれ（「株式会社ヒナプロジェクト」）まで含めて確認すること。

> なお冒頭の「紙の本のように」「落ち着いて読み進められます」等は体験の形容＝機能の主張ではないため判定対象外。

---

## 4. MATCH 一覧（27件）

| # | 主張 | 根拠（file:line） |
|---|---|---|
| 1 | なろう配布の縦書きPDFを取り込める | `ui/discovery/PdfImportScreen.kt:242-251`（`setDownloadListener`）→ `viewmodel/PdfImportViewModel.kt:89-208` → `PdfProcessingService.kt`／ADR 0011 |
| 2 | ふりがな（ルビ）を保ったまま表示 | `pdf/TextProcessor.kt:7`（座標からルビを紐付け）／`typeset/RubyPlacer.kt`／`parser/ChapterHtmlParser.kt` |
| 3 | 画像ではなく文字として組み直す | `pdf/PdfExtractor.kt:50,87,153`（`PDFTextStripper` を継承し `processTextPosition` で1文字ずつ `CharBox` 収集） |
| 4 | 横書き表示にも切り替えられる | `ui/ReadingSettingsSheet.kt:414-441`（FilterChip）／`ui/NativeReadingScreen.kt:409-412` |
| 5 | 行間・文字サイズを自分に合わせられる | `ui/NativeReadingScreen.kt:359,370,379,387`（sp と em の2スライダー・確定時のみ永続化） |
| 6 | 取り込んだ作品はオフラインで読める | 本文は端末内 HTML＝`data/BookEntity.kt:98,103`・`pdf/HtmlExporter.kt`（`filesDir/novels/<bookId>`）。読書は `ui/NativeReadingScreen.kt:423-424` でローカル File を解決。外部画像の読込は無し（`coil`/`glide`/`picasso`/`AsyncImage` すべて 0 ヒット） |
| 7 | 読書位置は自動記録され次に開いたところから再開 | 保存3経路＝`ui/ChapterScreen.kt:553,564-571`（debounce 400ms）・`:577`（ON_STOP）・`:585-594`（onDispose）。保存先＝Room `progress` の `lastReadFilename`/`scrollIndex`/`scrollOffset`（`data/ProgressDao.kt:30-40`）。復元＝`ui/NativeReadingScreen.kt:298-305,507,642-643` |
| 8 | 読書中に広告が割り込まない | 本文は Compose ネイティブ描画で広告挿入点が無い（`ui/ChapterContent.kt`・`ui/VerticalChapterContent.kt`）＋広告SDKゼロ（#24） |
| 9 | アカウント登録が不要（メール・会員登録なし） | 0 ヒット＝`login`/`signin`/`signup`/`oauth`/`accessToken`/`refreshToken`/`Bearer`/`Authorization`/`credential`/`AccountManager`/`EncryptedSharedPreferences`/`apiKey`（`src/main/java` 全体）。送信ヘッダは UA（`narou/network/NarouNetwork.kt:18`・`scrape/ScrapeHttpClient.kt:126,145`）と取込時 Cookie 転送（`viewmodel/PdfImportViewModel.kt:141-143`）のみ |
| 10 | こちらのサーバーへ送られない | 自前ドメイン不存在＝`src/main/java` の全 URL のホストは `api.syosetu.com`／`ncode.syosetu.com`／`yomou.syosetu.com`／`kakuyomu.jp`／`www.akatsuki-novels.com` のみ。`baseUrl` は `narou/network/NarouNetwork.kt:12` の1つ |
| 11 | 読みかけ・読了などの状態で絞り込める | `ui/skins/k/BookshelfK.kt:704,727,731,737,742`（すべて/よみかけ/未読/読了）・`:221`／`domain/ShelfItems.kt:242,361` |
| 12 | 目次では既読の話に印が付く | `ui/skins/k/TocK.kt:112,143`（`index < currentIndex` を既読と判定）・`:370`（題名を沈める）・`:394`（`Icons.Filled.Check`）。装い分岐＝`ui/NativeTableOfContentsScreen.kt:188` |
| 13 | 読みかけの位置からすぐ再開できる | `ui/skins/k/TocK.kt:255`（`HereBarK` 現在地チップ）・`:127`（該当行へスクロール）・`:378`（「▶ 再開」丸チップ） |
| 14 | 新着のお知らせは初期設定オフ | `NewEpisodeNotificationPreference.kt:22-23`（`getBoolean(..., false)`）／起動時は ON のときだけ登録＝`NovelReaderApplication.kt:275-277` |
| 15 | オンにすると1日1回だけ確認 | `NovelReaderApplication.kt:298-310`（24h の `PeriodicWorkRequest`・`KEEP`）。失敗しても `Result.retry()` にしない＝`NewEpisodeCheckWorker.kt:81-85`（「1日1回というレート自制の建付け」） |
| 16 | なろう＝公式APIによる検索 | `narou/network/NarouApiService.kt:8-44`（`@GET("novelapi/api/")` 1本）／`narou/NovelApiRepository.kt:240,252,265` |
| 17 | なろう＝ランキング閲覧 | `narou/NovelApiRepository.kt:363`（`order = query.order.apiValue`）／`narou/model/DiscoveryQuery.kt:10-12`（`NarouOrder`） |
| 18 | なろう＝公式サイトの表示 | アプリ内 WebView＝`ui/discovery/WebReaderScreen.kt:150-195`／Custom Tabs＝`ui/InAppBrowser.kt:21-29`（呼出 `ui/ChapterScreen.kt:441,452`）／外部ブラウザ委譲＝`MainActivity.kt:463` ほか |
| 19 | なろう＝公式の縦書きPDFの取り込み | #1 と同じ。取込は ADR 0011 の限定 WebView 経路 |
| 20 | カクヨム・暁＝作品の取り込み（アプリ内で読める） | Supported は2サイトのみ＝`scrape/SiteAdapterRegistry.kt:118`（`KakuyomuAdapter` + `SiteProfiles.ALL`）／`scrape/generic/SiteProfiles.kt:26-46,63`（`ALL = listOf(AKATSUKI)`）。取込後は PDF 蔵書と同契約の HTML へ合流＝`repository/WebBookImporter.kt:134-137`（ADR 0024 決定1） |
| 21 | なろうのページは加工せずそのまま表示 | 読書 WebView は `evaluateJavascript` を1度も呼ばない（`ui/discovery/WebReaderScreen.kt:183-190` は `shouldOverrideUrlLoading=false` の素通し）。0 ヒット＝`addJavascriptInterface`／`shouldInterceptRequest`／`insertRule`／`innerHTML`／`createElement`／`appendChild`／`loadDataWithBaseURL`／`userAgentString`。⚠️ **注記**: 取込画面のみ JS 注入があるが、内容は PDF 生成フォーム `.c-under-nav` への `scrollIntoView`＝**ビューポート移動のみ**（`ui/discovery/PdfImportScreen.kt:61-89,220,230`）。DOM改変・CSS注入・広告除去はゼロで、ADR 0011 が定めた線（「注入する JS はスクロールのみに限定」）どおり |
| 22 | 取り込みはその都度ユーザーの操作 | 起点＝`ui/discovery/NovelDetailScreen.kt:390,404`（ボタン）→ `MainActivity.kt:840,847-860` → ユーザーがなろう側のフローを手で辿って発生した DL を `PdfImportScreen.kt:242-251` が拾うだけ。複数話・複数作品を回すループは無い（`viewmodel/PdfImportViewModel.kt:89-208` は単一URLを1回・`:110` に二重投入ガード） |
| 23 | 利用者のデータを集めるサーバーを持っていない | #10 と同じ。診断データも端末内のみ＝`diagnostics/DiagnosticsStore.kt:27-31,52-58`（`filesDir/diagnostics/`）・回収経路ゼロ（`dumpAll()` の呼出はパッケージ外に 0件）・`diagnostics/DiagnosticEvent.kt:13`「Crashlytics 等の送信型 SDK は入れず」 |
| 24 | 分析SDKも広告SDKも入っていない | `android/app/build.gradle` の `com.google.*` は `:12`（KSP プラグイン）と `:431`（review-ktx）の2行のみ。`google-services.json` 不在。0 ヒット＝`admob`/`gms\.ads`/`applovin`/`unity3d`/`ironsource`/`vungle`/`firebase`/`crashlytics`/`google-analytics`/`analytics`/`adjust`/`appsflyer`/`sentry`/`mixpanel`/`amplitude`/`bugsnag`/`flurry`/`onesignal`/`braze`。⚠️ review-ktx は広告でも分析でもないが**通信は発生する**＝M2 参照 |
| 25 | 取り込んだ本文は端末の中だけに保存される | `files/novels/` は Auto Backup の include 列挙外＝構造的に対象外（`res/xml/backup_rules.xml` 冒頭コメントが正本・ADR 0015）。帰結の記録＝`domain/ReimportPlan.kt:11` |
| 26 | 他の人と共有する機能はない | 0 ヒット＝送信側 `ACTION_SEND` 生成／`ACTION_SEND_MULTIPLE`／`ACTION_SENDTO`／`ShareCompat`／`createChooser`／`EXTRA_STREAM`／`ClipboardManager`／`ClipData`／`setPrimaryClip`／`ACTION_CREATE_DOCUMENT`。`ACTION_SEND` の唯一のヒットは**受け側**（`MainActivity.kt:315` と `AndroidManifest.xml:60-66`）。FileProvider の公開範囲も `cacheDir/pdf_import/` のみ＝`res/xml/file_paths.xml:3-9` |
| 27 | サイトの仕様変更で取り込み・検索が一時的に使えなくなる場合がある | 破損は fixture ゴールデンで検知する設計＝ADR 0024 決定3／`scrape/AdapterHealthCheck.kt`／失敗は当該本だけスキップ＝`NewEpisodeCheckWorker.kt:214-220` |

---

## 5. ドキュメントの陳腐化：参照が1件切れている

`docs/store/listing-draft.md:5` が「機能の事実＝**`STATUS.md`「機能の現在地」**」を指しているが、
**現在の `STATUS.md` にその節は存在しない**（見出しは `## 0. 現在の状態` と `## 1. 観察ログ` の2つだけ）。
消えた便＝`d8be4c4 docs: STATUS を現在値だけに削ぎ落とす`（`git log -S'機能の現在地' -- STATUS.md`）。
`rg '機能の現在地'` のヒットはリポジトリ全体で `listing-draft.md:5` の**この参照1件のみ**＝完全な dead link。

**正しい参照先の候補**（機能の事実は現在1か所に集約されていないので、複数を指すのが実態に合う）:

| 候補 | 何の正本か |
|---|---|
| **`docs/decisions/0027-release-scope-feature-gate.md`** | **初回公開に何が載り何が載らないか**（`SKIN_SWITCHING_ENABLED`＝きせかえ・星図M・debug 限定機能の除外）。掲載文の §0 制約表「初回リリースに無い機能を書かない」が直接依拠すべき先 |
| **`android/app/src/main/`（実装）＋ ADR 0010/0011/0012/0015/0020/0024** | 機能の**動作**の正本。`privacy-policy-draft.md` の冒頭が既にこの形（「アプリ側の事実の裏取り＝`build.gradle`・`AndroidManifest.xml`・ADR 0010/0011/0012/0015/0024」）で書かれており、掲載文もこれに揃えるのが一貫する |
| `STATUS.md`（節指定なし） | 公開準備の**現在地**（残作業・実機状態）。機能の事実そのものではないので、残すなら「公開準備の現況＝`STATUS.md`」と役割を書き換える |

**推奨**＝5行目を次に差し替える:

```
> 機能の事実＝実装（`android/app/src/main/`）と ADR 0010/0011/0012/0015/0020/0024/0027／公開スコープ＝ADR 0027／
> 通信とデータの記述＝`privacy-policy-draft.md`・`data-safety-draft.md` と**必ず一致させる**（不整合はアプリ停止事由）。
```

---

## 6. 修正後の字数見通し

M1・M2・O1（冒頭＋特長）・O2・O3・O4 の代案文をすべて反映すると **1,255字 → 1,509字**（+254字）。
上限 4,000字に対して余裕は十分（残 2,491字）。
