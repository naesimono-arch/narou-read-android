# 単発修正バッチ＋UI god file 健全化

> **進捗（2026-07-02 完了・本プランはクローズ／リポジトリへアーカイブ）**: 全ステップ消化。
> Step 1〜5・7 **完了**（実機検証済み・main へ ff 統合済み HEAD=`1b22700`）。
> **Step 6（C-09 カバー色味微調整）= 現状維持で打ち切り**: 再開点メモの起点値（`saturation 0.38〜0.49`/`lightnessTop 0.46〜0.61`）は**旧HSL書影の値で陳腐化**していた（書影は既にD様式暗色スラブ＝彩度12〜21%/明度上26〜34%へ刷新済み。`20dcc00`→`9f00680`→`ede761f`＋パレット調律`6e16072`）。2026-07-02 に実機ベースライン（リスト/グリッド）を確認の上ユーザー判断で現状維持を選択。以後、書影の見た目はモックD＋`docs/decisions/0005-ui-n-visual-language-D.md` が正本（CLAUDE.md「UIの見た目は /design モックが正本」ルール準拠）。

**対象ブランチ: `cleanup-pre-uidesign`**（main と同一点から開始。作業ツリーはクリーン確認済み）

## Context

長期・高難易度タスク（Kotlin移植・A②再開機能・BOM更新）を除いた「単発の残タスク」を一掃する。
対象はユーザー選択で確定済み: **A2残微調整3件 ＋ 本文余白設定化(C-05+06) ＋ バグ#1ルビ位置ずれ ＋ C-09色味微調整 ＋ UI god file 分割**。

god file の実態: `NativeReadingScreen.kt` **1018行** / `BookshelfScreen.kt` **963行**（UI層コード4220行の半分がこの2ファイル）。

## 実行順（各ステップ＝1〜2 atomic commit・依存が薄い順に derisk）

### Step 1: `Icons.Filled.List` → `Icons.AutoMirrored.Filled.List`（1行・非推奨警告解消）
- `NativeReadingScreen.kt:523`（ボトムバーの目次ボタン）。import 追加。
- 参照実装: `BookshelfScreen.kt:208` が既に AutoMirrored 使用。UI ツリー内の非推奨 Icons はこの1箇所のみ（grep 確認済み）。
- 視覚変化なし → 実機目視ゲート不要。commit: `fix: 目次アイコンをAutoMirroredへ（非推奨警告解消）`

### Step 2: バグ#1 ルビ位置ずれ修正（唯一の実バグ＝優先）
**根本原因（コードから特定済み）**: ルビの基準Yが `layout.getLineTop(line)`（`RubyLayoutHelper.kt:111,134`）＝**行ボックスの上端**。lineHeight 2.3〜2.8em の余剰スペースが字面の上に分配されるため、ルビが親文字の字面からその分（数十%行分）浮く。lineHeight は em ベース＝フォントサイズに比例するので「文字サイズ非依存で常時ずれる」症状と一致。html_exporter.py 側ではない（ネイティブ描画のため）。

**修正**: 行上端ではなく**字面上端**にアンカーする。
- `RubyLayoutHelper.calculateRubyPositions`: `getLineTop` → `layout.getLineBaseline(line)` を返すよう変更（`RubyDrawInfo.baselineY` の意味を「親文字行のベースラインY」に変更・doc 更新）。ヘルパーは TextLayoutResult 純依存のまま維持。
- `RubyText.kt:98-107`: 描画側で
  - 親文字メトリクス用 Paint（`Typeface.SERIF`, `textSize = style.fontSize.value * density`）から `ascent()` を取得
  - 字面上端 = `lineBaseline + baseAscent`（ascent は負値）
  - ルビ描画ベースライン `y = 字面上端 - gap - rubyPaint.descent()`（gap は小さい調整定数、実機で微調整）
  - 既存の「`paint.textSize / 2` は…」コメントは実コード(0.2f)と食い違っている＝書き直し
- **副次効果**: 段落1行目のルビが Composable 上端より上（前の段落側）へはみ出す問題も同時解消（字面アンカーなら 2.5em 行ボックス内に収まる）。
- テスト: JVM テスト（`RubyLayoutHelperTest.kt`）は純粋関数のみで影響なし。`calculateRubyPositions` は instrumented 検証（`NativeReadingScreenTest.kt` があるため refactor 後に compile 確認）。**実機でルビ入り本を目視**（フォントサイズ14/18/24・行間2.3/2.8 で崩れないこと）。
- commit: `fix: ルビを行上端でなく字面上端にアンカー（位置ずれ#1解消）` ＋ why コメント必須。
- **task_diary 追記候補**: 「Compose の lineHeight 余剰は行上端と字面の間に分配される＝getLineTop はグリフ位置ではない」（外部プラットフォーム事実）。

### Step 3: god file 分割（純移動リファクタ・挙動変更ゼロ）
パッケージは `com.novelreader.ui` のまま（package 移動はしない＝import churn 最小化）。private → internal/ファイル内 private の可視性調整のみ。

**3a. NativeReadingScreen.kt (1018行) → 3ファイル追加**:
| 新ファイル | 移動対象（現行行） | 規模 |
|---|---|---|
| `ui/ReadingSettingsSheet.kt` | ReadingSettingsSheet (606-719) | ~115行 |
| `ui/ChapterContent.kt` | ChapterContent (720-770)・ChapterHeader (771-807)・ParagraphItem (808-912)・splitIntoParagraphs (961-1004) | ~290行 |
| `ui/ReadingErrorScreen.kt` | ReadingErrorScreen (913-960) | ~50行 |
| 残置 | ReadingScreen (122-)・ChapterScreen (303-)・settleTopBar | ~550行 |

**3b. BookshelfScreen.kt (963行) → 2ファイル追加**:
| 新ファイル | 移動対象（現行行） | 規模 |
|---|---|---|
| `ui/BookCard.kt` | GridBookCard (495-612)・ListBookCard (613-732)・BookProgressRow (450-494)・DeleteDropdownMenu (733-756) | ~300行 |
| `ui/ProcessingBanner.kt` | ProcessingBanner (813-919)・StepperIndicator (920-963)・EmptyBookshelf (757-812) | ~200行 |
| 残置 | BookshelfScreen (73-449) | ~430行 |

- 検証: `testDebugUnitTest` ＋ `compileDebugAndroidTestKotlin`（androidTest の参照切れ検知）＋ 実機スモーク1回。
- commit 2つ: `refactor: NativeReadingScreenを画面/設定シート/本文描画に分割` / `refactor: BookshelfScreenをカード/バナーに分割`

### Step 4: 設定シート磨き（A2残②③・分割後の `ReadingSettingsSheet.kt` 上で）
- **④-a 現在値の右寄せ藍数字**: ラベル `"文字サイズ（${fontSize}sp）"`（現606-719内、旧672,695行）を Row 化し、左＝ラベル、右＝`MaterialTheme.colorScheme.primary`（藍トークン。直書き禁止ルール準拠）の数値テキストへ。
- **④-b 目盛りドット消し**: 両 Slider（旧679,701行）に `colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)`。**steps 維持のままドットだけ消える＝カスタム track 不要**（handover の懸念は M3 1.2.1 では tickColor 透明化で足りると判明）。モックの「細線」まで寄せるかは実機目視で判断（必要なら track lambda 追加）。
- 実機目視ゲート → commit（2 atomic: 右寄せ数字 / 目盛り消し）。

### Step 5: 本文余白の設定化（C-05+06 統合・分割後ファイル上で）
- prefs パターンは fontSize と同型（`NativeReadingScreen.kt:170-178` が正本テンプレ）: key `"reading_body_margin"`・Int(dp)・**default 15**（現行見た目を保存）・coerce 10..40。
- 設定シートに3本目のスライダー「本文余白」: `valueRange 10f..40f, steps = 5`（10,15,…,40 の5dp刻み・現行値15がグリッドに乗る）＋ Step 4 と同じ右寄せ藍数字・目盛りなし。
- 値の適用: `ChapterContent` → `ParagraphItem`/`ChapterHeader` の `padding(horizontal = 15.dp)`（旧760-762, 777-780）を設定値に差し替え。
- **おまけ（1行）**: LazyColumn に `horizontalAlignment = CenterHorizontally` — `widthIn(max=600.dp)` がタブレットで左寄せになる既知問題の解消（why コメント付き）。
- 実機目視ゲート → commit: `feat: 本文左右余白を設定化（スライダー＋prefs永続化）`

### Step 6: C-09 色味微調整（主観・タイムボックス制）
- `BookCover.kt` の `saturation 0.38〜0.49` / `lightnessTop 0.46〜0.61` 起点。
- **キリがない系なので上限2周**: 候補パラメータを1〜2案ずつ実機ビルド→スクショ→ユーザー選択。OK が出た値で commit、出なければ現状維持で打ち切り（handover に見送り記録）。

### Step 7: 台帳更新（docs commit）
- `STATUS.md`: バグ#1 解消を記録（§0・§2）。完了項目へ本バッチのコミット表追加。
- `handover.md`: A2「残る微調整」消し込み・C-05/06/09 消し込み・god file 分割の記録。
- ルビの根本原因を `task_diary.md` へ（Compose lineHeight/getLineTop の外部事実）。
- `/architecture` スキルの陳腐化確認（ファイル分割＝構成変更のため。スキル陳腐化チェックルール準拠）。

## ビルド・検証手順（全ステップ共通）

- **Kotlin 変更→コミット前に必ずフォアグラウンドで** unit test（背景実行はセンチネル未生成で commit が弾かれる）:
  ```bash
  cd /mnt/c/Users/qingj/Desktop/project/novel-reader_andloid/android
  export JAVA_HOME=~/opt/jdk-17 ANDROID_HOME=~/Android/Sdk; export PATH="$JAVA_HOME/bin:$PATH"
  sed -i '/^sdk.dir=/d' local.properties 2>/dev/null
  java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
    --no-daemon --console=plain --init-script /home/qingj/ext-build/novel-reader-init.gradle testDebugUnitTest
  ```
  （Bash ツールは .bashrc 非ロードのため `gw` 不可＝明示 export。memory 準拠）
- **実機**: `assembleDebug` → adb は Windows adb サーバへ `adb connect` 前提。署名は Windows debug.keystore 共有済み前提で `install -r`（蔵書DB保持）。不一致なら keystore コピーから（memory `wsl-debug-keystore-share-for-install`）。
- **UI ステップ毎**: PushNotification でユーザーを呼び実機目視 OK → commit → 次へ（memory `workflow-notify-each-step-visual-check`）。Step 1・3 は視覚変化なしのためテスト＋スモークのみ。
- 各 commit 前に変更内容を提示して承認を得る（プロジェクト規約）。`Co-Authored-By` は付けない。

## スコープ外（今回やらない）
- BOM 2024.09+ 更新＆`animateItem()`（B案・全画面回帰必須のリスク大）
- A②強制終了再開・D 長期項目・UIスキン着せ替え
