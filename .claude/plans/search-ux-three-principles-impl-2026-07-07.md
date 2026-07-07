# 検索UX改善 実装仕様（ADR 0007 の3原則の適用）

- **対象ブランチ: `api-lab-ai`**（worktree `/home/qingj/wt/api-lab-ai`）
- 設計判断の正本: `docs/decisions/0007-search-ux-three-principles.md`（3原則）
- 出自: ユーザー実機フィードバック 2026-07-07 × `docs/reference/05-competitor-search-ui-field-report.md`
- 実行形態: 4バッチを agy へ順次委譲（bulk 生成）。各バッチ後に Claude が `testDebugUnitTest` を検証しコミット（1バッチ=1論理コミット）。
- 意匠規範: **新しい視覚要素を発明しない**。既存の意匠部品（`FilterChipItem`・セクション見出し・条件チップ・`MaterialTheme.colorScheme` トークン・`MinchoFamily`）を流用。色・書体の直書き禁止。

## バッチ1: 検索の透明化（原則2）— 範囲既定=タイトル＋「更新された時期」

### 1-1 検索範囲の既定をタイトルにし、全解除を禁止する
- `viewmodel/SearchDraft.kt`:
  - `SearchDraft.inTitle` の既定値を `true` へ。
  - `enum class SearchRange { TITLE, STORY, KEYWORD, WRITER }` を追加。
  - 純関数 `fun SearchDraft.withRangeToggled(range: SearchRange): SearchDraft` を追加: 対象フラグをトグルするが、**現在 ON が1つだけで、それ自身を OFF にする操作なら変更せず自身を返す**。why コメント必須: 「全解除＝なろうAPI仕様で暗黙の全項目対象（あらすじ・キーワード含む）となり、『なぜこの作品が出たか分からない』不透明が再発するため、最後の1つは外せない（ADR 0007 原則2）」。
- `ui/discovery/DiscoverySearchScreen.kt`:
  - 範囲チップの並びを「タイトル・キーワード・作者名・あらすじ」へ変更（あらすじは実用度が低い＝末尾へ降格。ユーザーフィードバック準拠）。
  - チップの onClick を `withRangeToggled` 経由に置き換え。
- 注意: `DiscoveryQuery.inTitle` の既定（false）は変えない（ホームランキング・ジャンル・気分プリセットは word 無しでクエリを作るため影響なしだが、モデル層の意味は「未指定」を保つ）。
- テスト: `withRangeToggled` の境界（最後の1つは外れない／2つON時は外れる／OFF→ONは常に可）＋ `SearchDraft()` 既定値で `toQuery().inTitle == true`。

### 1-2 「期間」→「更新された時期」＋ sevenday 置換
- `narou/model/DiscoveryQuery.kt` の `NarouLastup` を改定:
  - `SEVENDAY("sevenday", "7日以内")` を追加し、`THISWEEK` を**削除**（thisweek は日曜0時起点＝月曜に使うと1日分しか対象にならず誤解を生む。sevenday が生活感覚に一致）。THISMONTH/LASTMONTH は維持。宣言順は SEVENDAY, THISMONTH, LASTMONTH。
  - 削除に伴う参照追従（条件シートのチップ列挙・テスト）。
- `ui/discovery/DiscoverySearchScreen.kt`: 期間セクションの見出し文言「期間」→「更新された時期」。
- `ui/discovery/DiscoveryQueryLabels.kt`: lastup のチップ文言を `"${it.uiLabel}に更新"`（例:「7日以内に更新」「今月に更新」）へ。
- テスト追従: QueryLabels・Repository の lastup 変換（`sevenday` が送られること）。

## バッチ2: 絞り込み語彙の拡充（原則3）— 属性6軸×含む/除く＋文字数/読了時間の上位刻み＋カスタム

### 2-1 属性を `Set<NarouAttr>` の含める/除外へ一般化
- `narou/model/DiscoveryQuery.kt`:
  ```kotlin
  enum class NarouAttr(val isParam: String, val notParam: String, val uiLabel: String) {
      TENSEI("istensei", "nottensei", "異世界転生"),
      TENNI("istenni", "nottenni", "異世界転移"),
      R15("isr15", "notr15", "R15"),
      BL("isbl", "notbl", "ボーイズラブ"),
      GL("isgl", "notgl", "ガールズラブ"),
      ZANKOKU("iszankoku", "notzankoku", "残酷な描写"),
  }
  ```
  - `DiscoveryQuery` の `tensei`/`tenni`/`excludeZankoku` を**削除**し、`attrsInclude: Set<NarouAttr> = emptySet()` / `attrsExclude: Set<NarouAttr> = emptySet()` へ置換。`cacheKey()` も追従（enum 名をソートして連結）。
- `narou/NovelApiRepository.kt` の変換:
  - 矛盾指定（同一属性が include と exclude の両方）は両側から除外して無害化。why: UI 側でガードするが、万一混入すると `isX=1&notX=1` で結果が空になり原因を追えないため防御的に落とす。
  - include に TENSEI と TENNI が**両方**あるとき: `istt=1` を送り istensei/istenni は送らない（既存 why コメントの趣旨を維持: AND になり両立作品のみに絞られるため OR の istt へ振替）。
  - それ以外の include → `isParam=1`。exclude → `notParam=1`。値 0 は送らない（既存方針）。
- `viewmodel/SearchDraft.kt` の `SearchFilters`: `tensei`/`tenni`/`excludeZankoku` → `attrsInclude`/`attrsExclude`。`activeCount()` は両 Set の size 加算。`toQuery()` 追従。
- `ui/discovery/DiscoverySearchScreen.kt` 属性セクション:
  - 「属性」セクションを2グループへ: 見出し「テーマ（含める）」チップ6種（NarouAttr 全種）＋見出し「除外する」チップ6種。
  - タップで include へ追加する際、同属性が exclude にあれば exclude から自動除去（逆も同様）。矛盾選択を UI で構造的に防ぐ。
- `ui/discovery/DiscoveryQueryLabels.kt`:
  - include: TENSEI と TENNI 両方 →「転生・転移」1枚（既存表現維持）。それ以外は `uiLabel` を1枚ずつ。
  - exclude: `"${uiLabel}を除く"`。
- テスト: Repository 変換（istt 振替・矛盾無害化・not系送出）・QueryLabels・SearchFilters.activeCount。

### 2-2 文字数・読了時間の上位刻み＋カスタム入力＋相互排他
- チップ段階の改定（`ui/discovery/DiscoverySearchScreen.kt`）:
  - 文字数: すべて(null)／〜1万字(`"-10000"`)／1万〜10万字(`"10000-100000"`)／10万〜50万字(`"100000-500000"`)／50万〜100万字(`"500000-1000000"`)／100万字〜(`"1000000-"`)／カスタム
  - 読了時間: すべて(null)／〜30分(`"-30"`)／30分〜2時間(`"30-120"`)／2時間〜10時間(`"120-600"`)／10時間〜(`"600-"`)／カスタム
- カスタム入力:
  - 「カスタム」チップ選択で min/max の2つの数値入力欄を表示（文字数=**万字**単位・読了時間=**時間**単位。入力欄の脇に単位を表示）。片方空欄=その側は無制限。両方空欄= null（すべて扱い）。
  - 純関数を `viewmodel/SearchDraft.kt` に追加:
    - `fun buildCustomRange(minText: String, maxText: String, unitMultiplier: Int): String?` — 数値化できない入力は無視。`"min*mult-max*mult"` / `"min*mult-"` / `"-max*mult"` を生成。両方無効なら null。min>max の場合は入れ替えて救済。
    - `fun parseCustomRange(raw: String?, unitDivisor: Int): Pair<String, String>` — 入力欄の復元用（割り切れない値は切り捨てでよいが、往復で意味が壊れない範囲で）。
  - filters.length / filters.time がプリセットチップのどの値とも一致しない非 null 値のとき、「カスタム」チップを選択状態にし入力欄へ復元表示。
- **length と time の相互排他**: `SearchFilters` に `fun withLength(v: String?): SearchFilters`（length 設定時 time=null）/ `fun withTime(v: String?): SearchFilters`（逆）を追加し、シートの更新は必ずこれを経由。why コメント必須: 「なろうAPIは time と文字数指定の併用不可（マニュアル§4.4）＝両方送ったときの挙動が未定義のため、モデル層で同時に立たないことを保証する」。
- 会話率・挿絵チップは現状維持。
- テスト: buildCustomRange（空欄・非数値・min>max・単位換算）・parseCustomRange 往復・withLength/withTime の排他。

## バッチ3: 結果画面の条件その場変更（原則1の中核）

### 3-1 ResultContext に文脈種別を追加
- `viewmodel/DiscoveryViewModel.kt`:
  - `enum class ResultSource { SEARCH, KEYWORD, GENRE, MOOD }` を追加し、`ResultContext` に `val source: ResultSource` を追加（既定値なし＝全生成箇所で明示）。
  - `executeSearch()` → SEARCH。`MoodPreset.toResultContext()`（`viewmodel/MoodPreset.kt`）→ MOOD。`MainActivity.kt` のジャンル系生成（onPickBiggenre×2・onPickGenre）→ GENRE。
- VM に3関数を追加（いずれも現在の `_resultContext` を差し替えて再ロード。null なら no-op）:
  - `fun changeResultOrder(order: NarouOrder)` — query.copy(order=order)。title/subtitle/source 維持。
  - `fun changeResultBiggenre(code: Int)` — query.copy(biggenres=setOf(code), genres=emptySet())＋title を `NarouGenres.biggenreLabel(code)` へ更新（subtitle/source 維持）。
  - `fun changeResultGenre(code: Int)` — query.copy(genres=setOf(code), biggenres=emptySet())＋title を `NarouGenres.genreLabel(code)` へ更新。

### 3-2 結果画面のチップ可変化
- `ui/discovery/DiscoveryResultScreen.kt`:
  - **並び順チップ**（チップ列末尾の「◯◯順」）: タップ可能化し文言を「◯◯順 ⌄」へ。タップで `DropdownMenu`（NarouOrder 全6種・現在値を強調）→選択で `changeResultOrder`。
  - **ジャンルチップ**: `query.biggenres.size == 1` のとき大ジャンル6種のドロップダウン（`NarouGenres` の大ジャンル一覧）、`query.genres.size == 1` のとき詳細ジャンル全種のドロップダウン。文言に「 ⌄」付与。選択で `changeResultBiggenre`/`changeResultGenre`。上記以外（ジャンル未指定・複数指定）はジャンルチップ自体が無い/従来どおり非活性。
  - **「条件を変更」復帰リンク**: `source == ResultSource.SEARCH` のときのみチップ列の末尾にテキストボタン「条件を変更」を置き、タップで `onBack()`（検索画面へ戻る。ドラフトは VM 保持済みで条件が残っている）。競合定石（なろう公式の1タップ復帰）の translation。
  - タップ可能チップの意匠は既存条件チップの意匠を踏襲（枠・色トークン変更なし。「⌄」の付与のみ）。
- `NarouGenres` に大ジャンル/詳細ジャンルの一覧列挙が無ければ追加(`narou/model/NarouGenres.kt`)。
- テスト: VM の changeResultOrder/Biggenre/Genre（既存 DiscoveryViewModel テストの流儀＝fake repository で resultContext.title と query の変化・再ロードを検証）。

## バッチ4: キーワード起点の導線（原則1×3）

### 4-1 詳細画面キーワードチップ→キーワード検索
- `ui/discovery/NovelDetailScreen.kt`: キーワードチップに `clickable` を追加し、画面引数に `onKeywordTap: (String) -> Unit` を追加。
- `MainActivity.kt` の detail 配線:
  ```kotlin
  onKeywordTap = { kw ->
      discoveryViewModel.openResult(ResultContext(
          title = "「$kw」", subtitle = "キーワードから",
          source = ResultSource.KEYWORD,
          query = DiscoveryQuery(word = kw, inKeyword = true),
      ))
      navController.navigate("discovery/result") {
          popUpTo("discovery/result") { inclusive = true }
      }
  }
  ```
  - why コメント必須: 「resultContext は VM 単一保持のため、result をスタックに重ねると戻ったとき別の結果が表示される。popUpTo で result を常に1枚に保ち、状態と画面スタックを整合させる」。
- androidTest を含む全ソースがコンパイルできるようシグネチャ追従。

### 4-2 キュレーションキーワード（カクヨム式・原則3）
- 新規 `narou/model/NarouCuratedKeywords.kt`:
  ```kotlin
  data class CuratedKeywordCategory(val title: String, val words: List<String>)
  object NarouCuratedKeywords {
      val categories: List<CuratedKeywordCategory> = listOf(
          CuratedKeywordCategory("舞台", listOf("異世界", "現代", "学園", "ゲーム", "VRMMO", "ダンジョン", "和風", "西洋")),
          CuratedKeywordCategory("主人公", listOf("悪役令嬢", "勇者", "魔王", "聖女", "賢者", "冒険者", "騎士", "最強", "おっさん")),
          CuratedKeywordCategory("展開", listOf("追放", "成り上がり", "ざまぁ", "婚約破棄", "復讐", "無双", "チート", "スローライフ", "内政", "溺愛", "ハーレム")),
          CuratedKeywordCategory("雰囲気", listOf("ほのぼの", "コメディ", "シリアス", "ダーク", "切ない", "ハッピーエンド")),
      )
  }
  ```
  - ファイル冒頭に why コメント: 「なろうの keyword は作者が自由に付けるタグで語彙の相場を知らないと検索が組めない。頻出タグをカテゴリ別に先回り提示する（ADR 0007 原則3・カクヨムのキュレーションタグ方式）」。
- 純関数を `viewmodel/SearchDraft.kt` に追加:
  - `fun containsWordToken(word: String, token: String): Boolean` — 半角/全角スペース区切りのトークン集合として判定。
  - `fun toggleWordToken(word: String, token: String): String` — トークンを追加（末尾・半角スペース区切り）/除去。除去後の余分な空白は正規化。
- `ui/discovery/DiscoverySearchScreen.kt`: 検索履歴セクションの**下**に「キーワードから選ぶ」セクションを追加: カテゴリ見出し（既存セクション見出し意匠）＋FlowRow のチップ（既存 `FilterChipItem` 流用。`containsWordToken` で選択状態表示）。タップで:
  - `draft.word` を `toggleWordToken` でトグル。
  - **追加時**は `inKeyword = true` も併せてセット。why コメント必須: 「キュレーション語は作者タグの語彙のため、範囲に keyword を含めないとタイトル一致のみとなり大半を取りこぼす。範囲チップの状態変化として見える形で広げる（ADR 0007 原則2と両立）」。除去時は範囲を触らない。
- テスト: containsWordToken / toggleWordToken（追加・除去・全角スペース・重複なし）。

## 検証ゲート（各バッチ共通・Claude が実行）

```bash
cd /home/qingj/wt/api-lab-ai/android && ./gradlew testDebugUnitTest   # worktree=ext4 なので素で通る
```
全バッチ完了後: `assembleDebug` → `adb-bridge` → 実機上書きインストール → スモーク → PushNotification で目視確認依頼。

## コミット計画（1バッチ=1論理コミット・Claude が実施）

1. `feat: 検索範囲の既定をタイトルにし「更新された時期」へ語り直し（ADR0007 原則2）`
2. `feat: 属性6軸の含む/除外と文字数・読了時間の上位刻み＋カスタム入力（ADR0007 原則3）`
3. `feat: 結果一覧の並び順・ジャンルをその場変更可能に（ADR0007 原則1）`
4. `feat: キーワードのタップ検索とキュレーションキーワード（ADR0007 原則1×3）`
