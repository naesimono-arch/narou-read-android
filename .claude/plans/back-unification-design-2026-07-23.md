# 「戻る」操作の原則統一 設計調査書

調査対象: `/home/qingj/wt/feat-scraping-prep`（ブランチ `feat/scraping-prep`）
裁定（handover.md 2026-07-19・再検討禁止）: 横スワイプ戻る／左上戻るボタンとも、移動履歴（目次内の横移動等）を
**逆走せず「必ず一つ前の階層へ戻る」**へ統一する。実例の不一致＝本棚→本文で横スワイプは本棚・左上は目次に割れる。

---

## 真因（一行）

読書画面で **システム Back（横スワイプ）は「実経路の逆再生」モデル**（`ReadingBackStack.back()`＝末尾1枚 pop）、
**左上 ← ボタンは「一階層上へ」モデル**（章 ←＝目次を開く）で、**2つの異なるナビゲーションモデルが同居**している。
2026-07-12 は両者とも「一階層上」で統一されていたが（`NativeReadingScreen.kt:1706` のコメントが当時のまま残存）、
2026-07-15 の `ReadingBackStack` 再設計が **Back だけを経路逆再生へ変更**し、← を据え置いたため乖離が生じた。
本裁定は **Back を ← 側（一階層上）へ再統一**することを求める＝07/15 の「直行なら Back 1発で本棚」ショートカットの意図的撤回。

---

## ① 現状の全戻り経路 実測表（遷移元 × 操作 × 行き先・file:line 根拠つき）

「システム戻る」＝横スワイプ／端末 Back（本アプリは predictive back 未オプトイン＝`AndroidManifest.xml` に
`enableOnBackInvokedCallback` 無し。よって古典的 `BackHandler` がスワイプ Back を横取り。BackHandler が無い画面は
NavController の既定 pop）。

### A. 読書サブツリー（本裁定の主対象）

読書画面は NavController 上は 1 エントリ（`reading/{bookId}/{startFile}`・`MainActivity.kt:561`）で、
**画面内 Back は `ReadingScreen` が `BackHandler(enabled=true)` で全消費**（`NativeReadingScreen.kt:301-308`）。
内部状態は `ReadingBackStack`（`ReadingBackStack.kt`）＝辿った経路のファイル名列。

| 現在状態（内部スタック例） | システム戻る（BackHandler:301） | 左上 ← ボタン | 一致？ |
|---|---|---|---|
| **本文・直行入場** `[章]`（続きから/通知DL） | `back()`→null→**本棚**（`:302-306`, `ReadingBackStack.kt:78`） | `onNavigateTo("index.html")`→**目次**（`:1708`, forward `:252-256`） | **✗ 割れる**（実例の不一致） |
| **本文・目次経由** `[目次,章]` | `back()`→`[目次]`→**目次**（`:302`） | `onNavigateTo("index.html")`→**目次**（`:1708`） | ○ 偶然一致 |
| **本文・話送り後** `[目次,章B]`（話送りは置換） | `back()`→`[目次]`→**目次** | →**目次** | ○ |
| **本文・参照覗き** `[…,目次,覗章]` | `back()`→`[…,目次]`→**目次** | →**目次** | ○（続き復帰は「続きに戻る」チップ＝横移動・別導線） |
| **目次・目次経由入場** `[目次]` | `back()`→null→**本棚** | `onNavigateToBookshelf`→**本棚**（TOC:154-155 ほか） | ○ |
| **目次・直行入場後に目次を開いた** `[章,目次]` | `back()`→`[章]`→**本文（章）** | `onNavigateToBookshelf`→**本棚** | **✗ 割れる** |

補助導線（読書内）：下端「目次」ボタン `:1665`＝章 ← と同一（`onNavigateTo("index.html")`）／没入時 a11y「戻る」
アクション `:1403` も同一。前/次章 `:1658/:1680`＝横移動（話送り・階層移動でない）。
`onNavigateToBookshelf` の実体＝`popBackStack("bookshelf",false)`（`MainActivity.kt:602`）。
左上 ← のスキン別実体（章バーは全スキン共通の `NativeReadingScreen` TopAppBar・目次バーはスキン委譲）：
目次 ← は D/C=`NativeTableOfContentsScreen.kt:155`／M=`TocSkyM.kt:245`／P=`TocCartridgeP.kt:235`(`:177`)／
J=`TocPortalJ.kt:206`＝**全スキンとも本棚へ直行**で一致。

### B. 発見サブツリー（survey・本裁定の確定対象外。§③末で論点提示）

各発見画面は BackHandler を持たず**システム戻る＝NavController 既定 pop（＝`onBack`）**、左上 ← は画面ごとに `onUp`/`onBack`。

| 画面 | システム戻る（既定 pop＝onBack） | 左上 ← | 一致？ |
|---|---|---|---|
| 発見ホーム `discovery` | pop→本棚（`MainActivity.kt:418`） | `onBack`→本棚（Home:215） | ○ |
| 検索 `discovery/search` | pop→発見ホーム（`:448`） | `onBack`→発見ホーム（Search:191） | ○ |
| ジャンル `discovery/genre` | pop→発見ホーム（`:455`） | `onBack`→発見ホーム（Genre:66） | ○ |
| 結果一覧 `discovery/result` | pop→**直前画面**（検索/ジャンル/ホーム）（`:478`） | `onUp`→**発見ホーム固定**（`:477`, Result:206） | **✗**（検索経由時に割れる） |
| 作品詳細 `discovery/detail` | pop→**直前画面**（結果一覧/ホーム）（既定） | `onUp`→**発見ホーム固定**（`:523`, Detail:230） | **✗**（結果一覧経由時は ← が結果一覧を飛ばす） |
| PDF取込 `…/import` | BackHandler:WebView goBack→else pop（`PdfImportScreen.kt:105`） | 同一（`:117`） | ○ |
| web-reader | BackHandler:WebView goBack→else pop（`WebReaderScreen.kt:113`） | 同一（`:125`） | ○ |

発見の ✗ は **意図的設計**「D 統一（2026-07-12）＝← は経路に依らず発見ホームへ固定 Up」（`MainActivity.kt:520-523`,
`NovelDetailScreen.kt:134`）で、システム Back に履歴逆走を委ねる思想＝**本裁定と正反対**。§③論点参照。

### C. その他画面

| 画面 | システム戻る | 左上 ← | 一致？ |
|---|---|---|---|
| 本棚 `bookshelf`（root・L0） | 通常時＝アプリ終了（BackHandler 無し）／選択モード時のみ選択解除（`BookshelfScreen.kt:552`） | ←無し | ―（root） |
| 装いの間 `wardrobe`（設定的サブ画面） | `BackHandler{onBack()}`→本棚（`WardrobeScreen.kt:119`） | `onBack`→本棚（`:135`） | ○ |
| 設定 | 独立画面なし＝本棚⋮メニュー／読書の表示設定 ModalBottomSheet（シート dismiss＝Back で閉じる・階層移動でない） | ― | ― |

### D. deep-link 起点（曖昧ケースの実測）

- **通知タップ（`EXTRA_BOOK_ID`）**: `popUpTo("bookshelf",false)` して `reading/…` を積む（`MainActivity.kt:290-294`）。
  → NavController 親＝本棚固定・読書内部スタック＝`initial(getLastRead ?: "index.html")`。続きが章なら `[章]`＝
  **上表 A 1行目（直行入場）と同型＝現状 Back と ← が割れる**。
- **ACTION_VIEW / ACTION_SEND（対応サイトURL共有）**: `extractWebImportUrl`（`MainActivity.kt:218-222`）→`resolveWebImport`→
  `importWebNovel`/Blocked/Unsupported の Snackbar（`:306-323`）。**読書画面へ直着地しない**（Web カードを蔵書へ追加し
  本棚に留まる）。→ 戻り経路への影響なし（VIEW フィルタは manifest `:68`・ホスト限定）。

---

## ② 「常に一つ前の階層」階層定義案

### 階層（親＜子）

```
L0  本棚 bookshelf（root＝システム戻るでアプリ終了）
├─ L1  目次（book TOC）           ├─ L1  発見ホーム discovery      ├─ L1  装いの間 wardrobe
│   └─ L2  本文/章（body）           ├─ L2  検索/ジャンル/結果一覧/web-reader
│                                    │       └─ L3  作品詳細 detail
│                                    │             └─ L4  PDF取込 import
設定＝画面でなく本棚⋮メニュー／読書表示設定シート（階層に属さない・Back はシート dismiss）
```

### 「一つ前の階層」規則（読書サブツリー＝確定対象）

- **本文（章）→ 目次**（同一書籍の TOC）。話送り・参照覗き等の L2↔L2 横移動は**階層移動でない＝逆走しない**。
- **目次 → 本棚**。
- 二層のみ（目次 L1・本文 L2）。横スワイプと左上 ← は**この規則に一本化**（＝現在の ← 側へ Back を寄せる）。

### 曖昧ケースの裁定案

- **本棚→本文直行（続きから / 通知DL）**: 内部スタックに目次が無くても「一つ前の階層」＝**目次**。
  Back も ← も「まず目次を開き、次に本棚」（2 タップ）で一致させる。**07/15 の「直行は Back 1発で本棚」は撤回**
  （＝本裁定の核。既存ユーザーには 1→2 タップの可視変化＝意図された回帰）。
- **参照覗き（jumpOrigin）**: 覗き章（L2）→ 一つ前＝**目次**（退避元章へは戻さない＝履歴逆走禁止）。
  退避元章への復帰は「続きに戻る」チップ（横移動・別導線・`NativeReadingScreen.kt:328-344`）が担い続ける＝挙動不変。
- **ACTION_VIEW 直着地**: 読書へ着地しない（§①D）＝読書戻り規則の対象外。追加対応不要。
- **本棚（root）**: 一つ前の階層なし＝システム戻りはアプリ終了（現状維持）。選択モードは横断的解除（維持）。

---

## ③ 統一実装の設計案

### 中核方針

**システム Back を左上 ← と同一モデル（一つ前の階層）へ寄せる**。← 側は既に全画面（章・目次・全スキン）で
「章→目次・目次→本棚」に統一済みのため、**Back 側 1 点を直せば全経路が ← に収束**する。

### 推奨: Option A（最小・低リスク）— `ReadingBackStack.back()` を階層 up へ再定義

**変更ファイル**: `android/app/src/main/java/com/novelreader/ui/ReadingBackStack.kt`（`back()`＝`:78-79`）

現行 `back() = dropLast(1)`（経路逆再生）を、章なら目次へ・目次なら本棚（null）へ：

```
fun back(): ReadingBackStack? =
    if (current == INDEX) null              // 目次 → これ以上上位なし＝本棚へ（呼出側が onNavigateToBookshelf）
    else navigate(INDEX, lateral = false)   // 章 → 目次（既存目次へ巻戻し・無ければ積む＝openToc と同一）
```

これで各状態の Back：`[章]`→`[章,目次]`(目次表示)／`[目次,章]`→`[目次]`(目次)／`[章,目次]`→null(本棚)／
覗き `[…,目次,覗章]`→`[…,目次]`(目次)。**いずれも左上 ← ＝`navigate(INDEX)`／`onNavigateToBookshelf` と完全一致**。

- `BackHandler`（`NativeReadingScreen.kt:301-308`）は**無改修**（`back()` が null で本棚・非 null で内部遷移の既存分岐がそのまま機能）。
- 左上 ← ボタン（`:1708`）・下端目次（`:1665`）・a11y（`:1403`）も無改修（既に目次へ向かうため）。
- forward 系（`openChapter`/`sibling`/`returnTo`/`openToc`）は**無改修**（前進の巻戻し/置換規則は据え置き＝
  横移動の逆走防止という裁定の第2要件をそのまま満たす）。

### 既存 popUpTo / 置換規約との整合

- 読書内は NavController の popUpTo と無関係（内部スタックは `ReadingBackStack`）。`navigate(INDEX,lateral=false)` の
  「既出は巻戻し（popUpTo(inclusive=false)相当）」は温存＝目次の二重積み防止（テスト`:76`）も不変。
- MainActivity の `onNavigateToBookshelf = popBackStack("bookshelf",false)`（`:602`）は不変＝読書エントリを畳んで本棚へ。

### リスク / 副作用（要明記）

1. **可視 UX 変化（意図的）**: 直行入場のシステム Back が「1発で本棚」→「目次を経て本棚（2段）」に変わる。
   本裁定が明示的に求める撤回だが、既存ユーザーには体感変化。← と一致させる代償として受容（裁定どおり）。
2. **JVM テストの改訂必須**（`ReadingBackStackTest.kt`・自己検証ゲート対象）:
   - 反転して落ちる: `:20`（直行Back1発で本棚）・`:28`（直行話送りBack1発）・`:83`（直行→目次開後Backで本文へ）・
     `:148`（back は必ず縮小/現在地1枚）→ 期待値を「章→目次→本棚（2段）・back は章のとき目次を積み得る」へ書換。
   - 変更不要（そのまま緑）: `:37`/`:46`/`:55`/`:66`/`:76`/`:91`（前進・覗き深さ不変系）・`:137`（listSaver 契約）。
   - 追加推奨: 「直行入場でも Back と 左上 ← が同一遷移列を辿る」不変条件テスト。
3. **陳腐化ドキュメント／コメント（`/stale-check` 対象・同ターンで是正）**: 07/15 経路逆再生の根拠コメントが全て逆になる
   ＝`ReadingBackStack.kt:10-19`（旧 navHistory / 07/12 固定2階層の対比）・`NativeReadingScreen.kt:211-216`,
   `:285-300`（Back 再設計の根拠）・`:1706-1707`（← は Back と同じ・2階層統一＝**皮肉にも本裁定後に正しくなる**）。
   ADR 0019（遷移方向で階層を伝える）は方向規約自体は不変だが、07/15 経路逆再生の裁定 ADR があれば追補が要る。
4. **参照モード（jumpOrigin）**: Back で覗き章→目次へ抜けても `jumpOrigin` は残す設計（`:299-300`）＝目次上で無害・
   章再表示時のみチップ有効、は不変。回帰なし。

### Option B（不採用推奨・記録のみ）— 内部スタック撤廃

Back が経路非依存になると `ReadingBackStack` の多段スタックは back 目的では不要になり、`currentFile`＋`lastChapter`
＋`jumpOrigin` へ縮約可能。ただし forward 呼出全て（`navigateForward`/`onSelectChapterFromToc`/`onReturnToContinuation`）と
Saver・全テストへ波及し、裁定充足に不要な大改修＝**リスク超過**。A を推奨。

### 発見サブツリーの論点（**確定対象外・要ユーザー裁定**）

裁定の実例・括弧書き（「目次内の…横移動」）は読書フロー中心で、確定対象は本棚/目次/本文と解する。一方 §①B の
発見 ✗（結果一覧/詳細で ← ＝発見ホーム固定 Up、システム Back ＝履歴 pop）は **「D 統一 2026-07-12」（ADR 系）が
意図的に採る正反対の思想**。「常に一つ前の階層」を発見へも適用すると:
- 詳細 L3 の一つ前＝**結果一覧**（存在時）／無ければ発見ホーム＝**経路依存**＝**まさに現状のシステム Back（既定 pop）が実現**。
  逆に ← の「発見ホーム固定」は結果一覧を飛ばす**過剰 Up**になる。
- つまり発見では **← を Back（既定 pop）へ寄せる**のが「一つ前の階層」で、読書とは寄せる向きが逆。D 統一 ADR の撤回を伴う。

→ **推奨スコープ: 今回は読書フローのみ実装（Option A）。発見への拡張は D 統一 ADR の撤回を要する別件として、
適用可否をユーザーへ確認**（黙って発見の ← を変えない）。

### テスト方針まとめ

- ゲート: `cd android && …GradleWrapperMain testDebugUnitTest`（`ReadingBackStackTest` 改訂込み）。
- 新規不変条件: 全入場形（直行 `[章]`／目次経由 `[目次,章]`／覗き）で **Back の遷移列 ＝ 左上 ← の遷移列**。
- 手動確認（影響面の全組合せ・実機は別途）: {直行・目次経由・話送り後・参照覗き} × {横スワイプ・左上 ←} が
  全て 章→目次→本棚 に一致すること／全スキン（D/C/M/P/J）で目次 ← が本棚一致（コード上は共通経路＝退行なし）。
