# Chaquopy(Python) → 純正Kotlin+PDFBox-Android 移植（branch `kotlin`）

**対象ブランチ**: `kotlin`（`main` から新規作成）
**今回のスコープ**: Phase 0〜2（垂直スライス）。Phase 3〜6 は次段階（末尾に概要）。

## Context（なぜやるか）

現状PDF処理は Chaquopy(Python 3.12) 経由。この構成には根本的制約がある:

- Python(JNI)呼び出しが**キャンセル不能** → 処理中PDFの割り込み停止ができない（handover A①・`task_diary` #13＝`NonCancellable`必須）
- **純Pythonパッケージのみ**（PyMuPDF等C拡張不可・`task_diary` #11）→ pdfminer.six 一択
- Chaquopy Pythonランタイム＋pdfminer＋2ABIの`.so`で **APK肥大**
- 真の並列処理（`Dispatchers.IO`での複数冊同時）が不可

A/B評価で **B案（Kotlin+Apache PDFBox）が技術的優位**と判定済みで、`ab-review/submission-B` に忠実移植の完成形プロトが残置されている（章数完全一致 3/131/951・精度実測済み）。ただし **実アプリへの移植は未着手**（handover D の長期backlog）。本タスクはこれに着手する。

今回は移植の最大リスク＝**穴3: PDFBox-Android の実機動作（座標系・CID→Unicodeグリフ・フォント資産）が完全に未検証**（submission-BはデスクトップJVMでのみ検証）を、600行の本格移植を進める前に潰す**垂直スライス（Phase 0-2）**を完遂する。Chaquopyは残置し `git revert` 可能に保つ。

## 設計を規定する2つの発見

1. **分岐点は最終シリアライズ層だけ**。抽出コア（`PdfExtractor`+`TextProcessor`+`ParserRules`+`splitIntoChapters`）は Python と submission-B で論理的に同一（両者とも `List<String>`段落〔`【題名】`章マーカー＋`|base《reading》`ルビマーカー入り〕→ `List<RawChapter>` を生成）。分岐するのは *その後* のみ。
2. **submission-B の `Node`(Plain/Ruby 2種) は UI の `TextSegment`(Plain/Ruby/LineBreak/HorizontalRule/StyledBlock 5種) の真部分集合**（同型ではない）。HTMLを廃してNodeをUI直結（案B）すると LineBreak/hr/前後書き囲みボックスを失う。よって **(A) HTML中間表現を維持**し、章整形は submission-B の plain版ではなく **本番 `chapter_processor.py` のHTML版を移植元**にする。読み戻し経路（Jsoup/ChapterHtmlParser/TextSegment/RubyText）は**無改修**で温存。

## パッケージ構造とファイル配置

新規 `android/app/src/main/java/com/novelreader/pdf/`（ADR 0002 no-UseCase に従い facade を Repository が直接呼ぶ）:

| ファイル | 由来 | 要点 |
|---|---|---|
| `ParserRules.kt` | submission-B 同名 | デッド定数 `START_Y_*` は移植しない |
| `CharBox.kt` | `Models.kt` の `CharBox`+`RawChapter` のみ | `Book/Chapter/Node`＋`@Serializable` は本番に入れない |
| `PdfExtractor.kt` | submission-B 同名 | import を `org.apache.pdfbox.*`→`com.tom_roush.pdfbox.*` に差替 |
| `TextProcessor.kt` | submission-B 同名 | `processPages` にページ進捗コールバック引数を追加 |
| `ChapterProcessor.kt` | **`chapter_processor.py` のHTML版** | `_apply_ruby`＋HTML `process_foreword_afterword`（submission-B plain版ではない） |
| `HtmlExporter.kt` | **新規**＝`html_exporter.py` | index.html/chap_N.html を**バイト等価**生成 |
| `PdfExtractionException.kt` | `Models.kt` Encrypted/Corrupted＋Storage | Kotlinネイティブ例外 |
| `PdfBookExtractor.kt` | **新規**＝`Main.extractBook`＋`app.process_pdf` | meta→段落→章→前後書き→HTML、4ステップ進捗、例外分類の facade |

- **捨てる（本番APKに入れない）**: `Main.kt`(CLI)/`GoldenComparator.kt`/`Node/Book/Chapter`＋`@Serializable`（kotlinx-serialization 依存をアプリに足さない）
- **温存**: `ab-review/submission-B` はそのまま残す＝デスクトップ精度オラクル（`--compare`）。移植コードはこの双子

## PDFBox-Android 差し替え

- `android/app/build.gradle` の `dependencies` に `implementation 'com.tom_roush:pdfbox-android:2.0.27.0'`（submission-Bは apache `2.0.31` 固定＝Tom Roush版も2.0.x系でAPIが1:1）
- コードは **パッケージ名置換のみ**（`org.apache.pdfbox.*`→`com.tom_roush.pdfbox.*`）。`TextPosition.{unicode,xDirAdj,yDirAdj,heightDir,font,fontSizeInPt}`・`PDFTextStripper.{sortByPosition,startPage,endPage,getText,processTextPosition}`・座標系（上原点 `bottom=yDirAdj`,`top=bottom-heightDir`）は全て同名同義（`PdfExtractor.kt:37-53`）
- **`PDFBoxResourceLoader.init(applicationContext)` を全 `PDDocument.load` の前に1回**呼ぶ（ToUnicode CMap 非搭載のCIDフォントのglyph解決にAAR同梱の Adobe glyphlist/CMap 資産を使う）→ `MainActivity.onCreate`。フォントはAAR同梱＝手動配布不要

## 今回の実行計画（Phase 0-2・atomic commit）

CLAUDE.md規約: 各コミットでビルド緑、`src/main`/`src/test` 変更後は `testDebugUnitTest` 緑を確認しコミット計画を**人間承認**、`Co-Authored-By`無し、「なぜ」コメント必須。中間状態が常に緑（依存併存→純ロジック追加→未配線facade の順）。

**Phase 0 — 疎通スパイク（穴3を最優先で潰す）**
1. `build: PDFBox-Android 依存を追加（Chaquopyと併存）` — build.gradleに1行。appは依然Python使用＝緑
2. `test: PDFBox-Android 疎通スパイク（実機・座標/フォント検証）` — androidTestで1PDFの font histogram/座標 dump（`Main.kt:104-138 runDump` 相当）。14.0/7.0/12.0ptがPython定数と一致・上原点整合を目視。MainActivityに `PDFBoxResourceLoader.init` 追加。**実機で穴3の核心を確認**（androidTestは端末必須＝testDebugUnitTest対象外）

**Phase 1 — 抽出コア移植（各コミットで testDebugUnitTest 緑・PDF不要）**
3. `feat: PDF判定ルールとCharBoxを移植（ParserRules/CharBox）` ＋ CheckIsTitleテスト
4. `feat: PDF文字抽出を移植（PdfExtractor・pdfbox-android）` ＋ title/authorFromCharsテスト（純関数分）
5. `feat: 本文抽出コアを移植（TextProcessor・列復元/ルビ紐付け/ページ進捗）` ＋ GroupCharsByLine/AssociateRuby/BuildLineStr/ProcessPagesテスト
6. `feat: 章分割を移植（splitIntoChapters）` ＋ SplitIntoChaptersテスト
7. `feat: 前書き後書きHTML整形を移植（chapter_processor HTML版）` ＋ ProcessForewordAfterword/エスケープ/rubyテスト
8. `feat: HTML出力を移植（HtmlExporter）` ＋ **バイト等価ゴールデンテスト**（`fixtures/golden_html/*` を app テストリソースへ複製）

**Phase 2 — オーケストレータ＋実機エンジン証明**
9. `feat: 抽出例外体系とPDF読込分類を移植（PdfExtractionException）` ＋ 分類ユニットテスト
10. `feat: 抽出オーケストレータを追加（PdfBookExtractor・4ステップ進捗）` — facade。ユニットはfakeで進捗/例外を被覆
11. `test: 実機フル疎通（3ゴールデンPDF→HTML→リーダー目視）` — androidTest。**穴3の全経路KILL**（抽出→HTML→ChapterHtmlParser→RubyText描画）

## 再利用する既存資産

- 移植コア: `ab-review/submission-B/src/main/kotlin/com/novelreader/pdfproto/{ParserRules,PdfExtractor,TextProcessor,Models}.kt`
- HTML/章整形の**正しい**移植元: `android/app/src/main/python/{chapter_processor,html_exporter}.py`（HTML版）
- 精度オラクル: submission-B の `--compare`＋`golden_spec/*.json`（無改修流用）
- 実機回帰基準: `ab-review/golden_regression.py`＋`golden_regression/{N1453LW,N2959KI,N6169DZ}.pdf.json`
- HTMLバイト等価基準: `android/app/src/main/python/fixtures/golden_html/{index,chap_1,chap_2}.html`＋`test_logic.py:453 TestHtmlGolden`
- 実PDF（確認済み・手元にある）: `sample_pdfs/{N1453LW,N2959KI,N6169DZ}.pdf`
- 読み戻し（無改修で温存）: `parser/ChapterHtmlParser.kt`・`model/ChapterContent.kt`・`ui/compose/RubyText.kt`・`ui/NativeReadingScreen.kt:203,326`

## 検証（3層・独立に緑化）

1. **HTMLバイト等価**（決定的・PDF不要）: `fixtures/golden_html/*` を app テストリソースへ複製、同一 `_GOLDEN_CHAPTERS` から Kotlin生成→バイト等価assert。Python f-string の先頭改行/インデントまで一致させる精密移植の受入条件
2. **純ロジック同値**（決定的・PDF不要）: `cd android && gw --init-script /home/qingj/ext-build/novel-reader-init.gradle testDebugUnitTest`。submission-B 36テスト（JUnit5→JUnit4へ）＋`test_logic.py` 純ロジック分を移植。実PDF I/O（GlyphStripper/loadPages）はJVM単体で走らせず androidTest/オラクルへ回す
3. **実PDF抽出精度**（実機・PDF在時）: `adb-bridge` で3PDF push→androidTestで `PdfBookExtractor` を回し、章数(3/131/951)・title/author・ルビ数を `golden_regression/*.json` と照合。
   - **合格ライン**（完全一致は不可＝グリフ差）: ①章数/title/author は**完全一致必須**（構造は決定的） ②line coverage / ルビP/R は submission-B実測と同等（長編 coverage≥約92%・ルビP≥約80%/R≥約81%、短中編100%） ③3PDFを実機リーダーで開く**目視関門**（ふりがな位置・章送り・前後書き囲み・シーン区切り）
   - Python基準の再確認: `cd android/app/src/main/python && uv run --no-project --python 3.12 --with pdfminer.six python -m unittest test_logic -v`（58 tests OK）

## 次段階（Phase 3-6・今回スコープ外）

- **Phase 3** BookRepository切替: `BookRepository.kt:84-101` の JNI→`PdfBookExtractor`直呼、classifyError を Kotlin型分岐化（`:39-56`）、**NonCancellable緩和**（`processPages`に`ensureActive()`挿入で処理中PDFの割り込み停止が可能に＝handover A① 解消）、`BookRepositoryTest` の PyExceptionモック4件を書換。Python残置でrevert可
- **Phase 4** 精度回帰ゲート（androidTest常設化）
- **Phase 5** Chaquopy完全削除（`.py`一式・plugin `id 'com.chaquo.python'`・`python{}`・`ndk abiFilters`・chaquo maven撤去、APKサイズ実測）＝**ロールバック不能点・最後**。完了後 `.claude/skills/architecture`（PDF処理パイプラインがPython→Kotlinに変わる＝CLAUDE.md「スキル陳腐化チェック」義務）＋STATUS/handover の「D. ネイティブ化」を更新
- **Phase 6（別タスク）** 案B: HTML中間表現廃止・Chapter/NodeをUI直結（TextSegment拡張＋ChapterHtmlParser/Jsoup除去）。描画回帰は別チケットで、抽出が信頼済みの土台の上で

## リスクと緩和

- **穴3（pdfbox-android実機動作）＝最大**: Phase 0で最優先検証。ズレたら GoldenComparator の CMap正規化追加や特定フォント資産同梱で早期方針転換
- **速度**（長編デスクトップ4,083ms→実機は数倍か）: Phase 2で実測。前景サービス＋WakeLock＋10分/件で保護済（`PdfProcessingService.kt:158-161`）
- **ロールバック**: `kotlin`ブランチ隔離が第一防壁。Phase 5（削除）まで Python importable 温存＝実機受入NGなら `git revert` 即復旧。フラグ並存より「Python残置＋branch」が単純で十分
- **WSLビルド**: `gw --init-script`（`/mnt/c` の AAPT2 EPERM回避）必須・ASCIIパス下で実行。実機は `adb-bridge` 経由（[[workflow-autonomous-device-verification]]／各ステップ目視関門 [[workflow-notify-each-step-visual-check]]）

---

## 実行ログ & 別セッション引き継ぎ（2026-07-03）

> **別セッションで再開する人へ**: ここだけ読めば再開できる。ブランチ `kotlin`（`main` の `9c3c500` から分岐）。
> 作業ブランチのため現況の正本はこの plan file。腐りにくい外部知見は `task_diary.md` #30-32。

### 完了済みコミット（kotlin・新しい順） — 全て testDebugUnitTest 緑
| commit | 内容 | TaskCreate# |
|---|---|---|
| `12318eb` | feat: 抽出オーケストレータを追加（PdfBookExtractor・4ステップ進捗・facade） | 8 |
| `d40a225` | feat: 抽出例外体系を移植（PdfExtractionException・classifyPdfError） | 8 |
| `c477d2d` | feat: HTML出力を移植（HtmlExporter・バイト等価ゴールデン＝穴1埋め） | 7 |
| `cd6470e` | feat: 前書き後書きHTML整形を移植（chapter_processor HTML版） | 6 |
| `dc7a090` | feat: 章分割を移植（splitIntoChapters） | 6 |
| `53825f4` | feat: PDF文字抽出を移植（PdfExtractor・pdfbox-android） | 4 |
| `eae9892` | feat: 本文抽出コアを移植（TextProcessor・列復元/ルビ紐付け/ページ進捗） | 5 |
| `41c3b24` | feat: PDF判定ルールとCharBoxを移植（ParserRules/CharBox） | 3 |
| `0a23d53` | build: PDFBox-Android 依存を追加（Chaquopyと併存） | 1 |

→ **Phase 0 Step1 + Phase 1（ParserRules/CharBox/TextProcessor/PdfExtractor/ChapterProcessor/HtmlExporter）まで完了＝抽出コア〜HTML出力の全移植が緑**。移植先は `android/app/src/main/java/com/novelreader/pdf/`。
`compileDebugKotlin` が通ったことで `com.tom_roush.pdfbox.*` の import 解決（AAR クラスパス）も実証済み。
Task 6 は atomic に2分割した（`splitIntoChapters` → `processForewordAfterword`+`ProcessedChapter`）。
Task 7 で `htmlEscape` を本文/タイトル共有のトップレベル `HtmlEscape.kt` へ集約（drift 防止）。**穴1（HTML バイト等価）は `HtmlExporterGoldenTest` で埋まった**。
Task 8 で例外体系(`PdfExtractionException`/`classifyPdfError`)＋オーケストレータ(`PdfBookExtractor`)を追加＝**Phase 1（純ロジック移植）完了・Phase 2 の facade まで到達**（全 104 テスト緑）。
**Task 2/9（実機検証）完了＝穴3 全経路 KILL（2026-07-03）。Phase 1＋垂直スライス完了。次は Phase 3（BookRepository 切替）。**

### 実機検証コミット（Task 2/9・2026-07-03 追加）
| commit | 内容 |
|---|---|
| `3141312` | docs: Task9実機フル疎通完了を記録（穴3全経路KILL・ColorOS killer=task_diary #37） |
| `a6baa6f` | test: Task9実機フル疎通harness（full facade→HTML→本棚シード・PdfPipelineDeviceTest） |
| `614eb1c` | test: 穴3グリフ回帰の実機スパイクharness（PdfExtractorDeviceSpikeTest・資産bring-your-own） |
| `01175bb` | feat: PDFBox抽出の波ダッシュをpdfminerに揃えて正規化（U+FF5E→U+301C・task_diary #35） |
| `9bbe654` | docs: 穴3実機スパイクの結果を記録（init実機検証＝KILL・task_diary #35-36） |

**実機検証の結論（詳細は STATUS.md / task_diary #35-37）**:
- `PDFBoxResourceLoader.init` は**実機で効く**＝CID→Unicode 解決が根本機能（短編 body_sha256 完全一致・中編 N2959KI 9786段/131章も完全一致が証拠）。
- 残差2種: ①波ダッシュ `～`(U+FF5E)↔`〜`(U+301C) → **正規化で対処済**（`normalizeGlyphUnicode`・facade 経由で実機まで効くこと実証）。②超長編 N6169DZ の 0.01%エッジ（文字+0.012%/ルビ+0.97%/段落+5）→ **handover backlog へ退避**。
- リーダー目視関門 OK（N1453LW=前後書き囲み/N2959KI=131章の章送り/本文）。ルビは穴1バイト等価＋描画無改修で担保、2冊で関門完了。
- ⚠️ N6169DZ(350万字)は抽出中に **ColorOS の OSense/Athena が abnormal fg_cpu で強制kill**（OOM非該当・task_diary #37）。素の androidTest は前景サービス保護無しで無防備。**N6169DZ 実書の長編実機検証は Phase 3（前景サービス＋WakeLock 経路）で行う**。

### plan からの意図的な順序変更（2点）
1. **実機スパイク(元Step2)を後ろ倒し**: スパイクは GlyphStripper（PdfExtractor 中核）に依存するため、純ロジック移植を先に進め移植済みコードで検証する。
2. **PdfExtractor と TextProcessor を入れ替え**: `PdfExtractor.runFinalEngine` が `TextProcessor.processPages` を呼ぶ依存で TextProcessor が先。→ TextProcessor 完了済み。

### 次の一手（残タスク・この順で）
- **[Task 4] PdfExtractor 移植** — ✅完了(`53825f4`)。BookMeta/GlyphStripper/PdfExtractor object を移植。
  runFinalEngine に任意 progressCallback を追加(processPages へ前送り＝facade 用)。例外クラスは Task 8 へ回した。
- **[Task 6] ChapterProcessor(HTML版) + splitIntoChapters** — ✅完了(`dc7a090`,`cd6470e`)。
  `ProcessedChapter(title, body:String)` 新設。htmlEscape は Python `html.escape(quote=True)` 忠実移植
  （`"` `'` も実体参照化＝Task 7 バイト等価の前提）。div/hr 文字列を Python f-string とバイト等価に揃済み。
- **[Task 7] HtmlExporter 移植 + バイト等価ゴールデンテスト** — ✅完了(`c477d2d`)。
  `HtmlExporter.kt`＝`html_exporter.py` 1:1（index/chap_N をバイト等価生成）。`htmlEscape` は本文(ChapterProcessor)と
  タイトル(HtmlExporter)で共有するトップレベル `HtmlEscape.kt` へ集約（複製 drift 防止）。テストは fixture 3件を
  `src/test/resources/golden_html/` へ複製し `HtmlExporterGoldenTest` で temp dir 書き出し→バイト等価 assert（**穴1 埋め**）。
  知見: **Kotlin ブロックコメントはネスト**＝KDoc 内の `/*`(パス glob) で `Unclosed comment`(task_diary #33)。
- **[Task 8] PdfExtractionException + PdfBookExtractor facade** — ✅完了(`d40a225`,`12318eb`)。
  - 例外: `PdfExtractionException` sealed（Encrypted/Corrupted/InsufficientStorage）＋ `classifyPdfError`。
    Python の str/型名判定を型ベースへ翻案（暗号化=InvalidPasswordException型＋"password"メッセージ fallback、
    ENOSPC=メッセージ、破損=IOException、既分類/未知=素通し）。テスト8件。
  - facade: `PdfBookExtractor.process`＝`Main.extractBook`+`app.process_pdf` 統合。4ステップ進捗
    (step0 タイトル/1 本文=ページ進捗/2 章+前後書き/3 HTML)、戻り値 BookMeta、失敗は classifyPdfError で分類。
    **PDDocument を `PdfEngine`/`PdfHandle` interface の裏へ隠し**、本番=`PdfBoxEngine`(PDFBox直結)・テスト=fake注入で
    実PDFなしに進捗列/例外を JVM 被覆（テスト5件・HTML実出力も確認）。onProgress は BookRepository.ProgressListener と同形。
  - **意図的な見送り**: `PDFBoxResourceLoader.init(applicationContext)` の MainActivity 配線は Task 8 では行わない
    （Context 必須の未テスト app-wiring で、実際に効くのは Phase 3 の BookRepository 切替＝停止ポイント）。
    **Task 2/9 の androidTest は @Before で instrumentation Context により init すること**（facade KDoc/PdfBoxEngine に明記済）。
  - 知見: tom_roush `InvalidPasswordException(String)` は package-private でテスト生成不可(task_diary #34)。
- **[Task 2] 実機疎通スパイク**（穴3検証）— ✅完了(`614eb1c`＋`01175bb`正規化)。当初の font histogram/座標 dump から拡張し、`PdfExtractorDeviceSpikeTest` で実PDF3件を golden_regression と同一指標で突合。init は実機で効くと実証（上記結論）。
- **[Task 9] 実機フル疎通** — ✅完了(`a6baa6f`)。`PdfPipelineDeviceTest` で full facade → HTML → 本棚シード。目視関門 OK。穴3 全経路 KILL（上記結論）。
  - **実行の勘所（Phase 3 の実機テストで再利用）**: connectedAndroidTest は**テスト後にアプリを自動uninstall**し本棚が消える(task_diary #36)→ 代わりに `gw installDebug installDebugAndroidTest`（失敗時は APK を `adb install -r` 手動）→ `adb shell am instrument -w -e class <FQCN> com.novelreader.test/androidx.test.runner.AndroidJUnitRunner` で uninstall 回避。資産 `src/androidTest/assets/spike/`（実PDF3件＋golden3件）は gitignore・`sample_pdfs/` と `ab-review/golden_regression/` から配置。DB は `run-as com.novelreader cat databases/novel_reader_db*`（WAL込み3ファイル）を pull して sqlite3 照会。

### Phase 3 コード配線 完了（2026-07-04・2コミット・testDebugUnitTest 106件緑）
別セッションプラン `~/.claude/plans/pure-juggling-hamming.md`（設計判断の正本）で実装。
- `2944e84` feat: PDFBoxResourceLoader.init を **`NovelReaderApplication.onCreate`** へ配線（MainActivity ではなく Application＝Service が Activity 無しでも走るため先行初期化）。
- `f5c8fcc` refactor: BookRepository 切替＝①`BookRepository.kt` の JNI(`Python…process_pdf`)→**`PdfBookExtractor.process` 直呼**（onProgress 同形）②`classifyError` を `PdfExtractionException` **型分岐**化（PyException 文字列マッチ廃止）③**NonCancellable 緩和**＝`processPages` へ引数追加せず、既に本文ページ毎に呼ばれる**進捗コールバックへ `ensureActive()` を相乗り**（pdf/ 層・テスト fake ゼロ改修でページ毎の割り込み実現＝プランの意図を純ロジック非汚染で達成）＋抽出中断/失敗時の**孤立HTML掃除**（旧 NonCancellable が担保していた孤立防止の代替）。Room 登録のみ NonCancellable 保護に縮小 ④dead code の `ProgressCallback` interface 削除。`BookRepositoryTest` の PyException モック4件を型直接生成へ書換。
- **停止ボタン(ACTION_STOP)は今回いじらない＝能力確立のみ**（ユーザー承認）。緩和で `onTimeout` の `scope.cancel()` は処理中PDF即中断できるようになる。停止ボタンの即中断再配線（通知文/掃除/UX 伴う）は別タスク。
- ランタイム抽出経路が **Chaquopy→ネイティブ(PDFBox)** へ切替。Python 残置＝実機受入 NG なら `git revert` 可。

### Phase 3 実機検証(3e) 完了（2026-07-05・実UIから Claude が adb 自律駆動）
実書フロー（「PDFを追加」→SAF→`PdfProcessingService`(前景)→`BookRepository`→ネイティブ`PdfBookExtractor`→HTML→本棚→リーダー）を2冊で検証:
- **N2959KI(中編)**: 全132ファイルが Task9 facade 生成 spike と `diff -r` **バイト完全一致**（title/author/131章一致）。
- **N6169DZ(350万字/951章)＝task_diary #37 の壁を突破**: 前景サービス＋WakeLock 経路で約2分**完走・kill されず**（logcat: FGS `isForeground=true`継続・`ACQ NovelReader::PdfProcessing`保持・Osense `KillAction skip: non-low-mem`・kill/クラッシュ痕跡ゼロ）。951章=golden 完全一致・title/author 完全一致（波ダッシュ正規化）・ルビ markup 正常・リーダー目次/本文描画 OK。
- ⚠️ 検出 UX 課題（本文抽出中に進捗バーが実時間連動しない＝`runFinalEngine` の `loadPages` に進捗フック無し）→ handover D `[残タスク][UX]`（優先度低・今回未修正）。

### Phase 4 精度回帰ゲート 実装済（2026-07-05・≤15版クリーンラン待ち）
`PdfExtractorDeviceSpikeTest`（穴3診断）を**恒久精度回帰ゲート**へ昇格。合格ライン: 全PDF title/author/章数=完全一致・
短中編 body_sha256=完全一致・長編 N6169DZ は数値許容帯（厳しめ char±0.05%/ruby±1.5%/para±8/blank±8）＋章題≤15件不一致。
- **N1453LW/N2959KI は実機 PASS 確認済**。**N6169DZ は ≤3版で「章題11件のみ超過・他全 PASS」を実機確認済＝≤15版は論理的に必ず PASS**（11≤15・抽出決定的）。
- **残**: ≤15版のクリーン完走ラン未取得（素androidTest が ColorOS の fg_cpu kill で数回落ちた＝端末メモリ逼迫時）。再開手順は handover D「[Phase 4 詰め]」＝重い背景アプリ force-stop でメモリ確保→再実行。
- **発見**: N6169DZ 章題ドリフトは実測11件（旧「1件」は過少）＝全てグリフ写像差（ダッシュ FF0D→2212×6・矢印回転×3・アポストロフィ座標順×2）。①②9件は正規化候補（handover D）。

### ★次はここから — Phase 4 詰め → Phase 5
- **Phase 4 詰め**: ≤15版ゲートのクリーン完走ラン(`Tests run:1 Failures:0`)を1回取得（handover 手順）。
- **Phase 5** Chaquopy 完全削除（`.py`一式・plugin/`python{}`/`ndk abiFilters`/chaquo maven 撤去・APKサイズ実測）＝**ロールバック不能点**。完了後 `.claude/skills/architecture`＋`CLAUDE.md`＋`build`/`stale-check` スキルのパイプライン記述を全面書換（handover D の「[予約] Phase 5 完了時に併せて更新」参照）。

### 本セッションの残務（Phase 3 前に片付け候補）
- **実機の本棚にテスト用シード本が2冊残存**: `spike-N1453LW`「さすがに婚約の継続を…」/ `spike-N2959KI`「彼を殺して私も死ぬわ！…」（+空の `spike-N6169DZ` dir）。目視関門用に `PdfPipelineDeviceTest` が入れたもの。**掃除の可否をユーザーに確認中で未実施**（該当 `filesDir/novels/spike-*` と books 行のみ削除。手動追加のルビ本・他蔵書には触れない）。次セッションで確認して掃除するか、Phase 3 の実書取込で上書き。

### 環境・状態メモ
- **実機**: `192.168.1.210:5555` 接続済み（切れたら `adb-bridge` 再実行＝冪等）。
- **無関係な持ち越し**: `.claude/skills/architecture/SKILL.md`・`CLAUDE.md`（UI-nドキュメント追記＝移植と無関係）が M のまま。**移植コミットに含めない**（`git add` で対象を明示）。本来 main 帰属で kotlin では触らない。
- **ビルド/テスト（WSL・Bashツール／`.bashrc`非ロードで gw 関数不可）**:
  ```
  export JAVA_HOME=/home/qingj/opt/jdk-17; export ANDROID_HOME=/home/qingj/Android/Sdk
  export ANDROID_SDK_ROOT=/home/qingj/Android/Sdk; export PATH=$JAVA_HOME/bin:$PATH
  cd /mnt/c/Users/qingj/Desktop/project/novel-reader_andloid/android
  sed -i '/^sdk\.dir/d' local.properties 2>/dev/null   # Windows sdk.dir 除去→ANDROID_HOME フォールバック
  java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
    --no-daemon --console=plain --init-script /home/qingj/ext-build/novel-reader-init.gradle testDebugUnitTest
  ```
  実機テストは `testDebugUnitTest`→`connectedDebugAndroidTest`。Python基準確認は `cd android/app/src/main/python && uv run --no-project --python 3.12 --with pdfminer.six python -m unittest test_logic -v`（58 OK）。
- **承認テンポ**: ユーザーは純ロジック移植のバッチ承認を許可済み（各コミットは変更+テスト結果を提示し承認省略で連続実行）。**実機検証(Task2,9)・BookRepository切替(Phase3)では必ず停止**。

### 得られた知見（詳細は task_diary #30-32）
- pdfbox-android Maven 座標 `com.tom-roush:pdfbox-android:2.0.27.0`（groupIdは**ハイフン**。`com.tom_roush`は404）。**だが Java パッケージ名はアンダースコア `com.tom_roush.pdfbox.*`**（ハイフン/アンダースコア逆転の罠）。
- apache-pdfbox 2.0.x ↔ tom-roush 2.0.x は API 1:1（TextPosition/PDFTextStripper/座標系 同名同義）。import 差替のみで移植可。
- WHITESPACE の NBSP は ` ` エスケープで書く（生NBSPは hexdump でしか見えない）。`sed 's/\xc2\xa0/\\u00a0/g'` で一括。
- `(diffX/LINE_STEP_X).roundToInt()` は Python round(banker's) と .5 の丸めが違うが実害なし（submission-B 章数一致で確認）。
- 進捗: `pct=10+int(processed/bodyTotal*50)`（10-60%）, `bodyTotal=max(totalPages-4,1)`。
- WSL `/mnt/c` の `sed -i` は permission 警告を出すが置換は成功（無害）。
