# 機械検知（L1/L2）投資設計 — 2026-08-06 監査の全発見を走査ルールへ変換

> **対象ブランチ: `review/code-health-2026-08-06`**。発見そのものは `code-health-audit-2026-08-06.md` が正本。
> 出自: 台帳 `docs/known-bugs-registry.md` L4 の主眼＝「どのバグ型に検知を足すべきか」への回答。
> **今回の監査で `[!]` 型がまとめて複数インスタンスを出した＝無防備であることの実証**なので、その実測件数を投資の重みに使っている。

---

対象台帳: `docs/known-bugs-registry.md` / 実装先: `android/app/src/test/java/com/novelreader/sourcescan/HazardousPatternScanTest.kt`（既存4型）＋新規 L2 テストクラス
既存様式の踏襲点: `sources()` の下限 assert → 述語 → `Occurrence(path#member::snippet)` → `crossCheck(label, scanned, registry, howToFix)` の3方向（重複キー／未登録／陳腐化）→ **陽性 assert（候補0件＝述語破損）** → 登録簿は**理由文必須**。

---

## 0. 要約表 — 今回の監査で L2 へ昇格可能と判明した型

「本監査の件数」は今回同一機序が別箇所で見つかった数＝**この台帳が言う「再発回数」と同じ重み**（台帳の `[!]` 型は今回まとめて複数インスタンスが出た＝無防備の実証）。

| # | バグ型（走査ルール） | 台帳ID / 現在の状態 | 本監査 | 走査候補数（実測） | 走査可能性 | 推定工数 | 優先 |
|---|---|---|---|---|---|---|---|
| **R1** | 状態消失3点（launcher コールバック／TextField value／WebView restoreState が plain remember） | `remembersaveable-missing-state-loss` `[!] なし` | 4件 | 5 + 6 + 2 = **13** | ◎ 完全（識別子の一致だけ） | 120行＋登録13 | **S** |
| **R2** | Lazy/Pager のキー一意性は宣言制 | `lazy-items-missing-key-contenttype` `[!] なし` | 2件 | items* **28** + Pager **5** | ◎ 完全（全数登録） | 90行＋登録33 | **S** |
| **R3** | スキン束（ShelfChrome/ShelfData）フィールドの面横断消費カバレッジ | `skin-wiring-omission` `[~] 部分＋構造封鎖` | 5件 | 8面 × 11フィールドの直積 | ◎ 完全（機構は単純・初期登録が重い） | 140行＋登録~40 | **S** |
| **R4** | シングルトン DCL の内側再チェック | NEW | 1件 | **1**（`?: synchronized(` 全数） | ◎ 完全・偽陽性0 | 35行 | A |
| **R5** | FGS `onTimeout` overload × Manifest の `foregroundServiceType` 照合 | NEW（`oem-background-kill` 隣接） | 1件 | Service **1** | ◎ 完全（他のどの層からも見えない） | 70行 | A |
| **R6** | 取込確定（`insertBook`/`updateRestoredContent`）前の非空検査 | NEW（`pdf-rule-detection-edge-case` 隣接） | 1件 | **2**（PDF/Web の双子） | ◎ 完全 | 45行＋L1 1本 | A |
| **R7** | ネットワーク流量ゲートの単一インスタンス性 | `shared-mutable-state-without-mutex` `[!] なし` | 1件 | `SiteAdapterRegistry(` **4** | ◎ 完全 | 50行 | A |
| **R8** | 重複判定 DAO 読み → 行を作る DAO 書き は排他区間で包む | `shared-mutable-state-without-mutex` `[!] なし` | 1件 | **2** | ○（ロック包含判定は構文的） | 45行 | A |
| **R9** | 構造マーカー判定は完全一致（`"前書き" in title` 禁止） | `pdf-rule-detection-edge-case` `[~] 部分` | 1件 | **3** | ◎ 完全 | 30行＋L1 1本 | A |
| **R10** | 進捗器 `stepIndex/stepTotal` の消費者は供給元で分岐する | `progress-ui-diverges-from-work` `[~] 部分` | 3件 | 消費者 **4** | ◎ 完全 | 60行 | A |
| **R11** | スキン横断の「実採寸への直しが伝播したか」（`epLabel` 型） | `fontscale-large-breaks-layout` `[!] なし` | 2件 | **5**（Toc 5実装） | ◎ 完全・偽陽性0 | 50行 | A |
| **R12** | golden の「面 × fontScale 2.0」網羅表 | `fontscale-large-breaks-layout` `[!] なし` | 5件 | 面 ~20 / golden接頭辞 **24** | ◎ 完全（ファイル存在のみ） | 70行＋登録 | A |
| **R13** | reduce-motion の判定源は単一・remember で凍結させない | NEW（a11y） | 1件 | `ANIMATOR_DURATION_SCALE` **9** | ◎ 完全 | 35行 | B |
| **R14** | 選択状態の semantics 宣言（`selected`/`Role`/`stateDescription`） | NEW（`a11y-offscreen-nodes-unreachable` 隣接） | 1件 | 宣言 ~9 | ○（宣言の有無のみ） | 45行 | B |
| **R15** | 欠損を数で描かない（`"全${totalChaps}話"`） | `missing-data-rendered-as-fabricated` `[~] 部分` | 1件 | **9** | ○（ガード包含判定が要る） | 50行 | B |
| **R16** | ログ／例外 message への内容識別子の補間（全数登録） | NEW | 2件 | Log補間 **14**＋throw補間 | △（**識別子かの判定は人間**＝全数登録制で外出し） | 60行＋登録14 | B |
| **R17** | `BackHandler` 全数登録＋TAB_PAGE 帯は可視性の項を必須 | NEW | 1件 | **3** | △（帯の同定が弱い＝登録簿で補う） | 100行 | B |
| **R18** | Row 末尾要素が可変長 Text に押し出されない | `row-weight-missing-pushes-out-trailing` `[!] なし` | 1件 | Row直下Text **47** | △ 偽陽性大 | 130行＋登録~45 | C |
| **R19** | sp を載せる器を dp 固定寸で閉じない | `fontscale-large-breaks-layout` `[!] なし` | 5件 | 固定dp **190** | ✕ 実質不可（**R11+R12 へ分解を推奨**） | 150行＋登録~20 | C |
| **R20** | タップ標的 48dp | `a11y-touch-target-below-48dp` `[!] なし` | 2件 | clickable **136**（うち内容依存 82） | △ 二分割が必須 | 250行＋登録簿2本 | C |

**R19 / R20 についての判断**: どちらも本監査で複数件出た `[!] なし` の型だが、**そのままの述語では投資に見合わない**。R19 は候補190件に対し「収まるか」を静的に決められない（§4-1）。代替として **R11（伝播漏れ＝零偽陽性）と R12（golden 網羅＝ファイル存在検査）へ分解する**と、同じ5件の発見のうち4件を桁違いに安い機構で押さえられる。R20 は確定寸法10件／内容依存82件に**登録簿ごと二分**しないと登録簿が読めなくなる（§3-4）。

---

## 1. 上位ルールの設計（S 優先・実装スケッチ付き）

### 前提: 走査エンジンへ足す共通部品（3ルールが共有・約40行）

現行 `HazardousPatternScanTest` の `private fun splitTopLevelArgs` を **`KotlinSourceScanner` へ昇格**し、加えて「呼び出し括弧の直後に続く末尾ラムダ」を切り出すヘルパを足す。R1/R2 とも「`foo(...) { ... }` のラムダ本文だけを見る」を要求するため、ここを共通化しないと3箇所へ同じコードが写経される（＝このリポジトリが `skin-wiring-omission` として繰り返し踏んでいる形そのもの）。

```kotlin
// KotlinSourceScanner へ追加
/** [close]（呼び出しの `)`）に続く末尾ラムダの本文範囲。無ければ null。 */
fun trailingLambda(text: String, close: Int): IntRange? {
    var i = close + 1
    while (i < text.length && text[i].isWhitespace()) i++
    if (i >= text.length || text[i] != '{') return null
    val end = matchingClose(text, i) ?: return null
    return (i + 1) until end
}

/** 丸括弧内をトップレベルのカンマで分割（HazardousPatternScanTest から移設）。 */
fun splitTopLevelArgs(inner: String): List<String> { /* 既存実装をそのまま */ }
```

さらに、`crossCheck` は現在 `Map<String, String>` 専用。R2/R3 は構造化登録簿（`data class`）を使うので、**キー集合だけを取る overload** を足す（`registry: Map<String, *>` を受けて `.keys` で3方向を回す）。値の一致検査は型4（`notificationSites`）と同じくテスト側でインラインに書く。

---

### R1. 状態消失3点セット（最優先・ROI 最大）

**塞ぐバグ型**: `remembersaveable-missing-state-loss` `[!] なし`。本監査で3インスタンス（うち medium 1件＝**SAF ピッカー中の再生成で「1冊復旧」が「全欠落本の一括再取込」へ化ける**）。

**なぜ最優先か**: 述語が「同一ファイル内で plain `remember` 宣言された識別子名が、特定のラムダ本文に現れるか」という**識別子の一致だけ**で決まる。intrinsic 幅も実行時の値も一切要らない＝**偽陽性が原理的にほぼ出ない**。候補は合計13件で登録簿の初期コストも最小。1つの走査土台（plain remember 宣言名の集合）を3述語で共有できるので、行数あたりの守備範囲が最大。

**走査対象**: `sourceFiles(root)` 全ファイル（stripComments 済み）。
**述語（共通土台）**: `PLAIN_REMEMBER = Regex("""\b(?:val|var)\s+([A-Za-z0-9_]+)\s+by\s+remember\s*\{""")` でファイルごとに宣言名の集合を作る（`rememberSaveable` は `remember\s*\{` に当たらないので自動除外）。実測: 本番全体で 83 件。

| 述語 | 対象 | 現ツリー候補 | 違反（＝真因を直す） |
|---|---|---|---|
| (a) launcher コールバック | `rememberLauncherForActivityResult(...)` の**末尾ラムダ本文**に plain remember 名が現れる | 5 | `ui/BookshelfScreen.kt#BookshelfScreen::pendingScanBook` 1件 |
| (b) 入力欄 | `BasicTextField/OutlinedTextField/TextField` の `value =` の根が plain remember 名 | 6 | `NcodeLinkSheet.kt` の `inputText` / `manualNcode` 2件 |
| (c) WebView | `WebView(` を含むメンバ本文に `restoreState(` が無い | 2 | `ui/discovery/PdfImportScreen.kt#PdfImportScreen` 1件 |

**偽陰性の範囲（正直に書く）**:
- `var x = remember { mutableStateOf(...) }`（`by` を使わない形）は (a)(b) の宣言集合に入らない。現ツリーでは launcher/TextField 周辺に該当が無いことを実測して0を確認するが、**将来この書き方が入ると素通りする**。
- 上位から引数で降りてきた状態（呼び出し元が plain remember）は追わない＝**ファイル境界を越える巻き上げの誤りは見えない**。
- (c) は「復元経路が在るか」までで、**復元した内容が正しいか（フォーム選択の復元など）は見ない**。
- ActivityResult 以外の非同期コールバック（`DisposableEffect` の解除ハンドラ等）は対象外。

**実装スケッチ**:

```kotlin
// HazardousPatternScanTest へ型5として追加
@Test
fun `Activity 再生成をまたぐコールバックは非 Saveable な remember を読まない`() {
    val scanned = mutableListOf<KotlinSourceScanner.Occurrence>()
    var launcherTotal = 0
    for (file in sources()) {
        val text = file.text
        val members = KotlinSourceScanner.members(text)
        val transient = PLAIN_REMEMBER.findAll(text).map { it.groupValues[1] }.toSet()
        for (m in LAUNCHER_CALL.findAll(text)) {
            val close = KotlinSourceScanner.matchingClose(text, m.range.last) ?: continue
            val body = KotlinSourceScanner.trailingLambda(text, close) ?: continue
            launcherTotal++
            val callback = text.substring(body.first, body.last + 1)
            transient.filter { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(callback) }
                .forEach { name ->
                    scanned += KotlinSourceScanner.Occurrence(
                        relativePath = file.relativePath,
                        member = KotlinSourceScanner.memberAt(members, m.range.first)?.name ?: "<file>",
                        start = m.range.first, endExclusive = body.last + 1,
                        snippet = KotlinSourceScanner.snippet(name),
                    )
                }
        }
    }
    // 違反件数でなく「走査対象そのもの」で陽性を取る＝是正して0件になっても検知器の死と区別できる（型3の total>0 と同型）。
    assertTrue("rememberLauncherForActivityResult が1件も見つからない＝述語か走査が壊れている（現況5件）。", launcherTotal > 0)
    crossCheck(
        label = "launcher-callback-reads-transient-state",
        scanned = scanned,
        registry = HazardousPatternRegistry.launcherCallbacksReadingTransientState,
        howToFix = "ActivityResult のコールバックは Activity 再生成後も必ず再配送される（登録キーが rememberSaveable）。" +
            "一方 plain remember はそこで初期値へ戻る＝『結果は届くが行き先だけ消える』非対称が構造的に生じる。" +
            "直し方: (1) 行き先を rememberSaveable が扱える形へ持ち替える" +
            "（Parcelable でない実体は id を String で持ち、コールバック側で引き直す）。" +
            "(2) 消えて既定値になっても意味が同じなら、その根拠を書いて登録する。",
    )
}
```

**失敗メッセージ（未登録時に実際に出る文面）**:

```
[launcher-callback-reads-transient-state] 未登録の危険な形が 1 件見つかった。
  "ui/BookshelfScreen.kt#BookshelfScreen::pendingScanBook" to "（理由を書く）",
ActivityResult のコールバックは Activity 再生成後も必ず再配送される（登録キーが rememberSaveable）。…
```

**陽性確認（導入時に必ず実測）**: `pendingScanBook` を `rememberSaveable` の String 化へ直して**緑**になること、`showDeleteConfirm`（plain remember）をコールバック本文へ一時的に読ませて**赤**になることの両方向。

---

### R2. Lazy / Pager のキー一意性は宣言制（実害が最大＝クラッシュ）

**塞ぐバグ型**: `lazy-items-missing-key-contenttype` `[!] なし`。本監査で2インスタンス。片方は**即時クラッシュ**（`SaveableStateHolderImpl` の「… was used multiple times」による `IllegalArgumentException`＋積み上げ済みページの全消失）、もう片方は**操作対象の同一性破綻**（見ている扉が黙って別作品へ入れ替わり、そのまま「続きから読む」を押すと別の本が開く）。

**なぜ2番手か**: 実害は最大だが候補が33件あり、初期登録が R1 より重い。ただし**許容リストでなく全数登録**（型4＝`notificationSites` と同型）にする設計なので、33件は「一度書けば以後は差分だけ」の一回払い。

**走査対象**: `sourceFiles(root)` 全ファイル。
**述語**:
- `Regex("""(?<![A-Za-z0-9_.])(items|itemsIndexed)\s*\(""")` → `matchingClose` → `splitTopLevelArgs` で `key =` 引数の**式本文**を抽出。実測 **28件**（うち `key` 無し3件＝`ui/ChapterContent.kt:222` / `ui/VerticalChapterContent.kt:169` / `ui/discovery/DiscoveryHomeScreen.kt:538`）。
- `Regex("""\b(HorizontalPager|VerticalPager)\s*\(""")` → 同様。実測 **5件**（`ui/tabs/TabPagerHost.kt:70` / `ui/WardrobeScreen.kt:178` / `ui/skins/k/DiscoveryHomeK.kt:324,534` / `ui/skins/j/BookshelfPortalJ.kt:354`。可変長 DB 由来を供給しているのは最後の1件だけ＝**この規則を入れた瞬間に他4件は理由付きで登録でき、`BookshelfPortalJ` だけが真因修正を要求される**）。

**登録簿（構造化・理由なし禁止）**:

```kotlin
/** @param keyExpr 実コードの key 引数式（不一致なら陳腐化として落とす）。null＝key 引数なし。
 *  @param uniquenessProof キーが一意である**供給元**の同定。位置キーなら「なぜ位置で良いか」。 */
data class ListKeySite(val keyExpr: String?, val uniquenessProof: String)
val lazyItemKeys: Map<String, ListKeySite>   // items / itemsIndexed 全28件
val pagerPageKeys: Map<String, ListKeySite>  // Pager 全5件
```

**検査4本**（型4の流儀）:
1. 未登録＝赤（新しい一覧は必ずキーの一意性根拠を宣言するまで書けない）
2. 陳腐化（登録簿にあって実体が無い）＝赤
3. 宣言 `keyExpr` と実コードの式が不一致＝赤（宣言だけ直して実装が置き去り）
4. **`uniquenessProof` の供給元が「連結・マージで積み上がるリスト」（`+` / `plus` / `merge` で作られる）の場合、その VM に一意性の L1 テストが在ることをテストクラス名で書かせ、`check_machine.py` の実在照合に乗せる**

**この4番目が本命**。今回のクラッシュの真因は描画側でなく `DiscoveryViewModel.kt:269` の `current.novels + next.novels`（無条件連結・本番全体で `distinctBy` は `BookshelfViewModel.kt:808` の1箇所のみ）で、**走査だけでは絶対に届かない**。よって R2 は必ず **L1 とセット**にする:

```kotlin
// DiscoveryViewModelTest へ追加（現状 red・既存の loadMore 系5テストは一意性を見ていない）
@Test fun `追加読み込みでページが重なっても ncode は一意`() {
    // discoverPage(offset=30) が1ページ目と重なる ncode を返す偽 repository
    viewModel.loadMoreResults(); advanceUntilIdle()
    val ncodes = viewModel.resultState.value.novels.map { it.ncode }
    assertEquals("ページ連結が重複排除していない＝Lazy の一意キー契約違反でクラッシュする", ncodes.size, ncodes.distinct().size)
}
```

**偽陰性の範囲**: 機械が保証するのは「**キー式が宣言され、宣言と実装が一致していること**」まで。**その式が実際に一意であることは機械には決められない**（`uniquenessProof` は人間の読み＝型1の「本文が非 suspend」免除と同じ性格）。`LazyColumn` 以外の自作 `key(...)` スコープ、`stickyHeader`、`item(key=)` 単発は対象外。

---

### R3. スキン束フィールドの面横断消費カバレッジ（`ShelfFaceWiringCoverageTest`）

**塞ぐバグ型**: `skin-wiring-omission` `[~] 部分＋構造封鎖`。**台帳の検知手段欄が自ら「構造封鎖（必須引数。ただしシート色・クローム欠落は封鎖の外）」と書いている、その封鎖の外の帯そのもの**。本監査で5インスタンス（案C バナー／案X 走査進捗＋停止／`chrome.isLoading`／`webNcodesInSelection`／`deferHeavyContent`）。

**なぜ S 優先か**: このプロジェクトの構造的弱点（同じ機能を5〜8回書く）に直接効く唯一のルールで、**必須引数によるコンパイル時封鎖が「渡し忘れ」しか止められず「受け取って捨てる」を止められない**という穴を、機械が初めて塞ぐ。台帳の投資判断としては「2回以上再発した型」と同格。

**走査（3層）**:

1. **束のフィールド全数抽出**: `ui/skins/ShelfFace.kt` の `internal data class ShelfChrome(` / `ShelfData(` 本体を `matchingClose` で切り出し、`Regex("""\bval\s+([A-Za-z0-9_]+)\s*:""")` でプロパティ名を取る（`ShelfChrome` は現況 **11件**）。
2. **面の全数抽出**: `rememberShelfFace` の `when` 本体から `Regex("""\b(Bookshelf[A-Za-z0-9]+)\s*\(""")` で面 composable 名を列挙し、`declarations(root, "ui")` で宣言ファイルへ解決。既定描画 `ui/BookshelfScreen.kt#BookshelfContent` を固定で加えて **計8面**。
3. **直積の消費判定**: 各〈面ファイル, フィールド名〉について、stripComments 済み本文へ `Regex("""\b(?:chrome|data)\s*\.\s*<prop>\b""")` を当てる。**局所別名で受けて捨てる形**（`val isLoading = chrome.isLoading` の J デッキ）を消費と誤判定しないため、別名宣言を見つけたら**その位置以降に同名の出現が1回以上あるか**まで数える（0なら未消費）。

**登録簿**: `ShelfFaceWiringRegistry.intentionallyUnwired: Map<String, String>`（キー=`<面ファイル>#<面関数>::<フィールド名>`、値=理由文必須）。現ツリーの初期登録には「M/P/J はモック未裁定のため未表出」（`ShelfFace.kt:60-63` に既に書かれている意図）と「`onSelectAll` は M の観測野帳では意匠上未使用」（`ShelfFace.kt` の KDoc に既出）が入る。**このリポジトリは意図的な非消費を KDoc に書き残す習慣を既に持っており、その習慣を登録簿へ移すだけ**。

**判定を「1面以上が消費しているフィールド」に絞る**のが偽陽性の抑え方: 誰も消費していないフィールドは束の設計問題であって配線漏れではない（別の指摘になる）。

**実装スケッチ**:

```kotlin
class ShelfFaceWiringCoverageTest {
    @Test fun `1面以上が消費する束フィールドは全面が消費するか理由付きで登録されている`() {
        val root = KotlinSourceScanner.findModuleSourceRoot() ?: run { fail("走査根を解決できなかった…"); return }
        val props = chromeProps(root)          // ShelfChrome の val 名 11件
        val faces = shelfFaces(root)           // 面 composable → 宣言ファイル 8件
        assertTrue("面が8件・ShelfChrome のフィールドが11件見つからなければ走査が壊れている" +
            "（実測 面=${faces.size} / フィールド=${props.size}）。", faces.size >= 7 && props.size >= 10)

        val consumed = faces.associateWith { f -> props.filter { consumes(f, it) }.toSet() }
        val live = props.filter { p -> consumed.values.any { p in it } }   // 1面以上が消費＝生きた配線
        val gaps = faces.flatMap { f -> (live - consumed.getValue(f)).map { "${f.file}#${f.composable}::$it" } }
        crossCheck(
            label = "skin-wiring-omission",
            scanned = gaps.map { asOccurrence(it) },
            registry = ShelfFaceWiringRegistry.intentionallyUnwired,
            howToFix = "束の必須引数は『渡し忘れ』しか止めない——『受け取って捨てる』はコンパイラの外。" +
                "1面以上が使っているフィールドを使っていない面は、機能がその面にだけ無い状態。" +
                "直し方: (1) その面へ配線する（描画の意匠は面ごとに違ってよいが、状態・導線は同じでなければならない）。" +
                "(2) 意図的な差なら理由を書いて登録する（『モック未裁定』は正当な理由。" +
                "ただし代替導線が route 層に在るかまで書くこと＝案X の走査停止には代替が無かった）。",
        )
    }
}
```

**同クラスへ足す第2テスト（`webNcodesInSelection` の3面欠落を落とす）**: `domain/ShelfItems.kt` の public 宣言を `declarations` で機械抽出し、Listing 役の5実装それぞれで `Regex("""\b<NAME>\s*\(""")` の有無を表にして、「1実装以上が呼び、他が呼んでいない domain 関数」を同じ登録簿へ突合する。

**偽陰性の範囲**: 機械が保証するのは「**フィールド名／関数名がその面のソースに現れること**」まで。**現れた上で正しく描かれているか・正しい引数で呼ばれているかは見ない**（`is ShelfItem.Web ->` の枝へ `selectionMode` を渡しているかの補助述語を足しても、渡した先で `selected = false` とハードコードする形は落ちない）。束を経由しない配線（route 層で差し替える `onOpenBook` のような形）も対象外。

---

## 2. A 優先ルール群 — 「偽陽性0クラスタ」（合計 ~330行で6件を押さえる）

いずれも**走査候補が1〜4件**しかなく、述語が構文的に確定する＝登録簿がほぼ空のまま運用できる。R1〜R3 より守備範囲は狭いが、**行数あたりの確実性が最高**なので同一ラウンドでまとめて入れるのが得。

| ルール | 述語（核） | 候補 | 初期の赤 |
|---|---|---|---|
| **R4** DCL | `@Volatile ... var H: T? = null` を集め、`H ?: synchronized(` のブロック本文に**内側の `H ?:`** が無いもの | **1**（`data/AppDatabase.kt:365` のみ） | 1件（修正は1行） |
| **R5** onTimeout | `AndroidManifest.xml` の `<service>` を属性単位で読み、`foregroundServiceType` に `dataSync`/`mediaProcessing` を含むなら**2引数版 `onTimeout(Int, Int)` 必須**／`shortService` を含まない Service に**1引数版だけ在る＝赤**（＝「書いてあるのに絶対呼ばれない受け口」） | Service **1** | 1件 |
| **R6** 取込確定 | `declarations(root,"repository")` で戻り型に `AddBookResult` を持つ関数を取り、本文の最初の `insertBook(`/`updateRestoredContent(` より**前**に非空検査（`ScrapeIntegrity.verify` 等）が無いもの | **2** | PDF 側1件（Web 側は手本として緑） |
| **R7** 流量ゲート | `SiteAdapterRegistry(` の生成点を全数登録制にし、`fetches && !debugOnly` が **2件以上なら赤**（スロットル状態の分裂） | **4** | 1件 |
| **R8** 排他 | 同一メンバに `findBySourceUrl` 系と `insertBook`/`updateRestoredContent` の両方が在り、最初の重複判定が `withLock` の内側に無いもの | **2** | Web 側1件（PDF 側は Service の ActiveUriTracker を理由に登録） |
| **R9** 構造マーカー | `"前書き"/"後書き"/"【題名】"` を `in` / `.contains(` で部分一致しているもの | **3** | 2件＋L1（`processForewordAfterwordTest` へ「複合題で章数が減らない」を追加） |

**R5 だけは他のどの層からも原理的に見えない**ことを特記する: Robolectric は全ファイル `@Config(sdk=[34])` 固定（`build.gradle:424-427` が自認）、`lintDebug` は overload 未実装を見ない、`assembleRelease` も通る。**targetSdk 36 なのに API 35 の受け口が無い**という型は、Manifest とソースを突き合わせる L2 でしか落ちない。R5 には受け皿として **`targetSdkSensitiveApiSites`（全数登録・`affectedFrom <= targetSdk` の登録だけを必須化）** を併設し、`build.gradle` の `targetSdk (\d+)` を読んで**引き上げた瞬間に全使用点の再点検を強制**する形にすると、同型の潜伏（現ツリーの `statusBarColor` / `setDecorFitsSystemWindows` / `screenHeightDp` 等7件）へ横展開できる。

**R10（進捗器の供給元分岐）** も同格で入れる: `stepIndex/stepTotal/stepLocalPercent` の消費者（現況4件＝`ProcessingBanner` / `SkyProcessingBanner` / `WritingBanner` / `PortalProcessingBanner`）のうち、同一メンバ本文に `ProcessingSource.` が現れないものを列挙。**引数へ束ねて逃げる形**（`DevelopLabel(stepIndex = state.stepIndex, ...)`）も拾うため、述語に `stepIndex\s*=\s*state\.stepIndex` 形の実引数束縛を含める。M/P/J の3件が一度に赤くなる。

---

## 3. R19 / R20 の分解 — 「そのまま作ると失敗する2ルール」の代替設計

### 3-1. R19（sp × dp 固定器）を R11 + R12 へ分解する

素の述語は `.height/.size/.width(N.dp)` が **190件**当たり、しかも「収まるか」は静的に決められない（§4-1）。同じ5件の発見を、次の2つの零〜低偽陽性ルールで押さえる方が安い:

**R11「実採寸への直しがスキンへ伝播したか」（零偽陽性・50行）**
`declarations(root,"ui")` で引数名に `epLabel` を持つ関数を全数列挙（現況 Toc 5実装）→ `TocRegistry` に `Skin.entries` 全数ぶんの登録がある＋**各実装の本文が `rememberEpLabelWidth`（または `rememberTextMeasurer` 採寸）を通っている**ことを assert。`TocK.kt` が 44dp 下限＋実採寸へ直った（コミット 98dfc2c は `TocK.kt` しか触っていない）のに M/J へ伝播していない、という**まさにこの形**を落とす。`DiscoveryHomeInvariantCoverageTest` の「引数型で全数列挙 → 登録簿突合 → `Skin.entries` 全数を別テストで固定」をそのまま踏襲できる。

**R12「golden の面 × fontScale 網羅表」（ファイル存在検査・70行）**
プロジェクトは `ScreenshotConfig.FONT_SCALES = listOf(1.0f, 2.0f)` を**標準条件として既に採用**している。ところが `src/test/screenshots/` の 104枚・24接頭辞は D/K に偏り、**M/P/J の面（`BookshelfSkyM_*` / `TocSkyM_*` / `TocPortalJ_*` / `DiscoveryCartridgeP_*` / `NcodeLink*` / `NovelDetail_*`）は0枚**。つまり今回の5件は全て「golden が無いから CI が見ていない面」に集中している。
→ 面の登録簿（R3 の `ShelfFaceRegistry` と共用）に「fontScale 2.0 の golden を持つか／持たないなら理由」を持たせ、**ファイルの存在だけで**突合する。`verifyRoborazziDebug` との重複ではない（あちらは「在る絵の退行」、これは「絵が無い面の網羅」）。KBottomNav の件は逆に **golden が壊れた絵を固定していた**ので、R12 の理由欄に「fontScale 2.0 の絵を人間が見た日付」を書かせる運用を足す。

### 3-2. R18（Row 押し出し）は「登録簿の初期サイズ」で判断する
Row 直下に Text を持つ 47箇所のうち、固定文字列・固定幅・末尾が Text のみ、を除いた残りは実測2件（本件と `SelectedKeywordChip`）。**述語自体は使える**が、47件を全走査して2件へ絞る過程が3条件 AND の構文解析（直下子の列挙・第1引数がリテラルか・後続子の種別）で、実装130行のうち大半がここに掛かる。**R3/R12 を先に入れて、Row の押し出しは fontScale 2.0 golden で画として押さえる方が安い**（NovelDetail は golden 0枚＝R12 が先に赤くする）。R18 は C 優先で据え置き。

### 3-3. R20（タップ標的 48dp）は登録簿ごと二分する
`clickable` 系は **136件**。modifier チェーンの復元（後方は `Modifier` の頭まで `)`/`}`/`]` を対で畳みながら遡る／`.semantics{}` の末尾ラムダで止まらないよう注意／dp が識別子なら同ファイルの `val X = N.dp` で解決）が必須で、これだけで実装150行級。さらに:
- **確定寸法群（~10件）**: `size/width/height` が dp リテラルか解決可能な定数 → `allowedSmallTouchTargets: Map<String,String>`。
- **内容依存群（82件・padding のみでヒット域が決まる）**: 寸法が静的に出ない → **第2の登録簿 `contentSizedTouchTargets` へ「寸法の出所（内容の fontSize / Icon size）と実効高」を書かせる全数登録制**。

この二分をしないと「何を裁定したのか」が読めない登録簿が82件ぶん生まれ、台帳の唯一の効用（一目で投資判断）を殺す。**分割してなお C 優先**なのは、今回の2件（13dp ヒット幅・30dp 未満ヘッダ）がいずれも severity medium/low で、136件の走査を書く投資に見合わないため。先に R14（選択状態の semantics・宣言の有無だけ・45行）を入れる方が a11y の投資効率が高い。

---

## 4. 静的走査では原理的に止められないもの（正直な線引き）と代替策

「全数列挙して登録簿と突合する形に落ちない」ものを、代替（L1 不変条件テスト／実機ベンチ／人間の目視）付きで列挙する。

| 止められないもの | なぜ静的に無理か | 機械が保証できる境界 | 代替策 |
|---|---|---|---|
| **dp 固定器に sp の文字が収まるか** | 実描画幅は font・locale・fontScale・行分割アルゴリズム依存。`Noto Sans CJK` のメトリクスをテストへ焼くのは新しい嘘を作るだけ | 「器が dp・中身が sp」という**形**まで | **R12（fontScale 2.0 golden の網羅）＋ 記録した絵の人間目視**。KBottomNav は golden が既に壊れた絵を固定していた＝画は撮れても「壊れている」判定は人間 |
| **Row の測定順で実際に押し出されるか** | 先行 Text の intrinsic 幅がデータ依存（作者名の長さ） | 「weight も maxLines も無い可変長 Text の後ろに末尾要素が在る」形まで | fontScale 2.0 golden ＋ fixture に**長い作者名**を入れる（現行 `NovelDetailContentTest` は `author="作者名テスト"` 6文字固定＝この形を通らない） |
| **内容依存のタップ標的が 48dp に届くか（82件）** | ヒット域＝内容の実測寸＋padding。内容は Icon か Text か、Text なら実行時 fontScale 次第 | padding の合算値と、同メンバの `fontSize`/`Icon size` の**代理値**まで | 代表画面の Compose UI テストで `assertHeightIsAtLeast(48.dp)`（androidTest はコンパイルのみ CI＝実行は端末）／登録簿へ「寸法の出所」を書かせて**調べたことを機械が要求する** |
| **`uniquenessProof` が本当に一意か（R2）** | キー式の値域はデータ由来（`ncode` が null になる作品、API が重複を返す窓） | 「宣言があること・宣言と実装が一致すること」まで | **L1 必須**（`DiscoveryViewModelTest` の一意性 assert）。R2 の登録簿はこの L1 テストクラス名を書かせ `check_machine.py` の実在照合に乗せる |
| **二重生成・並行取込が実際に起きるか（R4/R7/R8）** | 競合窓の踏破は実行時のスケジューリング次第 | 「排他が構造として無い」ことまで | 形の検知で十分（真因が1行で直る）。競合の再現テストは書かない＝フレーキーの温床 |
| **ログの補間値が「内容識別子」か（R16）** | `$docUri` が書名を含むかは値の意味論 | 「補間がある／例外 message へ組み立てている」ことまで | **全数登録制＋理由文で人間へ外出し**（型4と同じ流儀）。`-assumenosideeffects` の有無で検査を緩めない（release 以外に残る） |
| **`stepIndex` が実進行を表すか（R10）** | 意味論。器を出すか否かの分岐は形で見えるが、値の正しさは供給元の契約 | 「供給元で分岐しているか」まで | 登録簿の免除理由に「この供給元でも stepIndex が実進行を表す」根拠の明記を必須にする |
| **遷移ジャンクが実際に減るか（`deferHeavyContent`）** | 51ms/フレームは D のグリッドの実測値で、K の面の値ではない | 「引数が3段渡ってきて誰にも読まれない＝配線の断線」まで（R3 の亜種として列挙可能） | **macrobenchmark**（台帳が「回帰固定へ未接続」と明記済み＝別投資）。静的には断線の検知どまり |
| **選択状態が TalkBack へ実際に届くか（R14）** | semantics ツリーの実マージ結果は実行時 | `selected =` / `Role.Checkbox` / `stateDescription` の**宣言の有無**まで | androidTest の semantics assertion（CI はコンパイルのみ）／実機 TalkBack は人間 |
| **reduce-motion 設定の変更に追従するか（R13）** | `ContentObserver` の有無は静的に見えるが、再購読が実効かは実行時 | 「`ANIMATOR_DURATION_SCALE` が remember に包まれている／判定源が複数ある」まで | 判定源を単一 provider へ寄せる真因修正＋実機目視 |
| **FGS が実際に 6h 上限へ到達するか（R5）** | ユーザーが前面へ戻すたびタイマーが再設定される＝到達条件が利用パターン依存 | 「Manifest の型と override の overload が食い違う」まで | 形の検知で十分（到達しない前提でも dead code とコメントの嘘は常時成立） |
| **OEM の背景 kill（`oem-background-kill`）** | 端末ベンダ固有の凍結挙動 | — | 実機検証（`/device-verify`）＝台帳の `[!] 知見のみ` のまま。この監査の射程外 |

**明示的に検知不能と結論するもの**: 「意匠が5系統で違ってよく、配線・状態・ロジックが違うのは欠陥」という線引き自体は機械に決められない。R3 が列挙するのは**フィールド名の消費有無**という代理指標で、「その面ではこの機能を出さないのが裁定」なのか「配線漏れ」なのかは**登録簿の理由文＝人間の判断**が引き受ける。これは弱点ではなく設計で、`ShelfFace.kt:60-63` のような判断が既にコメントとして散在しているのを一箇所へ集めるだけ。

---

## 5. 導入順の推奨（1ラウンドあたりの塊）

1. **ラウンド1（~200行）**: 共通部品の昇格（`splitTopLevelArgs` / `trailingLambda` / `crossCheck` の overload）＋ **R1**。最小コストで `[!] なし` を1つ `[o] 固定` へ動かせる。
2. **ラウンド2（~330行）**: **R4〜R10 の零FPクラスタ**を一括。7つの述語がすべて候補4件以下＝レビューが軽く、台帳の `[!]` を3つ・`[~]` を2つ動かす。**R5 は先に入れる価値が特に高い**（真因修正が2引数 override 1本で、放置すると既修正3件〔`stale-generation-coroutine-finally` / `cancelled-scope-reuse-silent-stop` / `oem-background-kill`〕がまとめて dead のまま）。
3. **ラウンド3（~250行＋登録33）**: **R2**（L1 の `DiscoveryViewModelTest` 追加を先に赤で入れて真因修正 → L2 の全数登録）。
4. **ラウンド4（~350行＋登録~60）**: **R3 + R11 + R12**。スキン束・実採寸伝播・golden 網羅は同じ「面の登録簿」を共有するので同一ラウンドが得。
5. **ラウンド5以降**: R13〜R17。R18〜R20 は R12 の golden 網羅が入った後に**残りの実害を再評価してから**着手（R12 で画として落ちる分だけ、走査の投資額が下がる）。

登録簿は `HazardousPatternRegistry` 1ファイルへ足し続けると型10近くで読めなくなるので、**ラウンド2の時点で型ごとにファイル分割**する（`HazardousPatternRegistry.kt` は索引のみ、`registry/StatePersistRegistry.kt` 等へ実体）。分割時は `docs/known-bugs-registry.md` の検知手段欄が名指しするクラス名を追随させること（`check_machine.py` の実在照合が落とす）。