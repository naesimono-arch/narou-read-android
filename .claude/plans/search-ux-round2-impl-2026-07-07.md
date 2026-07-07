# 検索UX改善 第2ラウンド 実装仕様（実機フィードバック2026-07-07②）

- **対象ブランチ: `api-lab-ai`**（worktree `/home/qingj/wt/api-lab-ai`）
- 出自: ユーザー実機フィードバック第2弾（2026-07-07）。設計原則は `docs/decisions/0007-search-ux-three-principles.md` を継続適用。
- 実行形態: バッチA=Claude直接。バッチE・Bを agy へ並列委譲 → C → D を直列委譲（C/D/B は `DiscoverySearchScreen.kt` を共有するため）。各バッチ後に Claude が `testDebugUnitTest`＋diff全量レビュー（追加行だけでなく削除行も見る＝前回退行の教訓）＋API送出経路の全数突合。
- 意匠規範: 新しい視覚要素を発明しない。既存部品（`FilterChipItem`・`SectionHeader`・条件チップ意匠・`MaterialTheme.colorScheme`・`MinchoFamily`）流用。色・書体の直書き禁止（バッチAの Theme.kt を除く）。

## 前提となる実測・一次情報（2026-07-07 Claude 確認済み）

1. **`type` はハイフンOR不可**。`type=t-r` は無効値として無視され全件が返る（実測: t-r の allcount 1,222,053 ＝無指定と同値。t=594,243 / r=435,539 / er=192,254 の合計とも一致）。公式複合値は `re`（連載中+完結済）・`ter`（短編+完結済）のみ（マニュアル§4.5）。**短編+連載中の組合せだけ API 表現が無い** → 2クエリのクライアントマージで対応。
2. **`lastup` は「プリセット文字列 or UNIXタイムスタンプ（秒）のハイフン区切り（開始-終了）」**（マニュアル§4.5）→ 複数時期の OR は連続レンジとして表現可能。
3. **`length`/`time` は単一レンジのみ**（`minlen/maxlen` 等との併用不可・time と文字数系の併用不可＝既存 `withLength`/`withTime` の相互排他を維持）。
4. **公式キュレーション語彙**（https://yomou.syosetu.com/search.php 「検索ワードを選択」パネル・2026-07-07 Claude が HTML 直接取得で照合済み）: 下記バッチDのリストが全数。

## バッチA: セピアテーマの差別化（Claude 直接実装）

`ui/theme/Theme.kt` の `ReadingTheme.SEPIA -> ReadingColors(...)` を琥珀の紙トーンへ再調律（実装時に確定）。LIGHT（寒色白 #FBFAF8・ほぼ無彩色）に対し、SEPIA は彩度 ≈15-20% の黄褐色・文字は焦茶墨として知覚差を保証する。accent の藍鼠 #2E4A60 は D の骨格として維持。whyコメント: モック reading-D.html の .t-sepia 写経値はライトとの知覚差が不足（実機フィードバック 2026-07-07「ライトとセピアの色味に差がなく同じ色に見える」）のため意図的に逸脱。モック側への逆反映は handover 宿題。

## バッチE: ジャンルのドリルダウン（結果画面・原則1）

**課題**: 「ジャンルから」で大ジャンルを直接選ぶと小ジャンルを選ぶ余地がない。結果画面のジャンルチップのドロップダウンが大ジャンル6種のみ（`DiscoveryResultScreen.kt:173-194`）。キーワード検索発など**ジャンル未指定の結果にはジャンルチップ自体が無く**、後からジャンルで絞れない。

### E-1 VM: ジャンル変更を1関数へ統合（`viewmodel/DiscoveryViewModel.kt`）
- 既存の `changeResultBiggenre(code)` / `changeResultGenre(code)` を**削除**し、次で置換:
  ```kotlin
  fun changeResultGenreFilter(biggenres: Set<Int>, genres: Set<Int>)
  ```
  - `_resultContext` の query を `copy(biggenres = biggenres, genres = genres)` へ差し替えて `loadResult()`。
  - **title の書き換えは `source == ResultSource.GENRE` のときのみ**行う: `genres.size==1` → `NarouGenres.genreLabel`、それ以外で `biggenres.size==1` → `biggenreLabel`、両方空 → `"すべての作品"`。
  - why コメント必須: 「SEARCH/KEYWORD 発の結果の見出しは検索語（『「最強」』等）であり、ジャンルをその場変更しても“何を検索したか”は変わらないため見出しは維持する。GENRE 発のみ見出し＝ジャンル名なので追従させる」。
- 呼び出し元は `DiscoveryResultScreen` のみ（`MainActivity` は openResult 直組みのため無関係）。既存 VM テストの changeResultBiggenre/Genre 検証は新関数へ書き換え＋「SEARCH 発では title が変わらない」ケースを追加。

### E-2 結果画面: ジャンルチップの常設＋階層ドロップダウン（`ui/discovery/DiscoveryResultScreen.kt`）
- 現在の `isBiggenreChip`/`isGenreChip` の2種のドロップダウン（大ジャンル6種のみ／小ジャンル平坦22種）を**共通の階層メニュー**に置換。さらに **`query.biggenres.isEmpty() && query.genres.isEmpty()` のときは、並び順チップの直前に「ジャンル ⌄」チップを追加**（意匠は既存のクリック可能チップと同一＝藍枠・10.5sp）。
- 階層メニューの内容（`DropdownMenu`。上から順に）:
  1. 「すべてのジャンル」（ジャンル解除。未指定状態なら Bold）→ `changeResultGenreFilter(emptySet(), emptySet())`
  2. `NarouGenres.BIGGENRES` を順に: 大ジャンル行（SemiBold・現在の biggenre 単一指定なら Bold＋primary）→ `changeResultGenreFilter(setOf(bigCode), emptySet())`。続けて `GENRES_BY_BIG[bigCode]` の小ジャンル行（先頭に 16.dp インデント・fontSize 13.sp・現在の genre 単一指定なら Bold＋primary）→ `changeResultGenreFilter(emptySet(), setOf(genreCode))`。
- ジャンルが**複数指定**されているケース（気分プリセット等で `genres.size > 1`）は従来どおり非活性チップのまま（ドリルダウン対象外）。
- `DiscoveryQueryLabels.kt` は**変更しない**（バッチBが触るため。未指定時の「ジャンル ⌄」チップは ResultScreen 内で labels とは独立に追加する）。
- テスト: E-1 の VM テストで担保（UI は実機スモーク）。

## バッチB: 作品の形態・更新時期の複数選択OR（原則3）

### B-1 モデル（`narou/model/DiscoveryQuery.kt`）
- `val type: NarouNovelType?` → `val types: Set<NarouNovelType> = emptySet()`
- `val lastup: NarouLastup?` → `val lastups: Set<NarouLastup> = emptySet()`
- `cacheKey()` 追従（enum 名 sorted joinToString）。
- 同ファイルに送出用純関数を追加:
  ```kotlin
  /** types → API type パラメータ。null は「パラメータ無し」。SHORT+RENSAI だけは API に複合値が無いため
   *  特別扱いが要る（NovelApiRepository 側で2クエリマージ）。ここでは null を返す。 */
  fun typeApiParam(types: Set<NarouNovelType>): String?
  ```
  - 空 or 全3種 → null ／ {SHORT}→"t" ／ {RENSAI}→"r" ／ {KANKETSU}→"er" ／ {SHORT,KANKETSU}→"ter" ／ {RENSAI,KANKETSU}→"re" ／ {SHORT,RENSAI}→null
  - why コメント必須（実測事実を含める）: 「type にハイフンOR は使えない＝ `type=t-r` は無効値として無視され**全件が返る**（2026-07-07 実測: allcount が無指定と一致）。公式複合値 re/ter で表現できない SHORT+RENSAI のみ、呼び出し側で2クエリに分けてマージする」
  ```kotlin
  /** lastups → API lastup パラメータ。単一はプリセット文字列、複数は UNIX 秒の "start-end" 連続レンジ。 */
  fun lastupApiParam(lastups: Set<NarouLastup>, nowMs: Long, zone: java.time.ZoneId = ZoneId.of("Asia/Tokyo")): String?
  ```
  - 空 → null ／ 単一 → `apiValue` ／ 複数 → 各時期の区間（JST）: SEVENDAY=[now-7日, now]・THISMONTH=[今月1日0:00, now]・LASTMONTH=[先月1日0:00, 今月1日0:00-1秒] の **min(start)〜max(end)** を UNIX **秒**で "start-end"。
  - why コメント必須×2: 「複数時期の OR はプリセット文字列では表現できないが、lastup は UNIXタイムスタンプのハイフン区切りを受ける（マニュアル§4.5）ため連続レンジへ合成する。非連続な組（7日以内+先月）は UI 側で間（今月）を自動点灯して構造的に防ぐが、万一漏れても min-max の広い側に倒す（絞りすぎて作品が消えるより害が小さい）」「zone を Asia/Tokyo 固定にするのは、なろうのプリセット（thismonth 等）がサーバ＝日本時間の暦で解釈されるため。端末のタイムゾーンに依らず意味を揃える」

### B-2 送出（`narou/NovelApiRepository.kt`）
- `discover()` の `type = query.type?.apiValue` → `type = typeApiParam(query.types)`、`lastup = query.lastup?.apiValue` → `lastup = lastupApiParam(query.lastups, timeSource())`。
- **SHORT+RENSAI マージ経路**: `discover()` 冒頭で `query.types == setOf(SHORT, RENSAI)` のとき:
  ```kotlin
  val short = discover(query.copy(types = setOf(SHORT)))
  val rensai = discover(query.copy(types = setOf(RENSAI)))
  return DiscoveryResult(short.allcount + rensai.allcount,
      mergeByOrder(short.novels, rensai.novels, query.order).take(query.limit))
  ```
  - 各サブクエリは通常経路＝それぞれキャッシュに乗る。allcount は短編と連載中が**排反**なので単純加算で正確（why コメント）。
  - `mergeByOrder(a, b, order)`: 両リストを order のソートキーで降順マージする純関数（Repository 内 or DiscoveryQuery.kt 側。テスト可能な場所に）。ソートキー: DAILY→`dailyPoint` / WEEKLY→`weeklyPoint` / MONTHLY→`monthlyPoint` / QUARTER→`quarterPoint` / TOTAL→`globalPoint`（null は 0）／ NEW→`generalLastup` 文字列比較（"yyyy-MM-dd HH:mm:ss" 形式なので辞書順＝時系列。null は最小扱い）。why コメント: 「両サブクエリは API 側で既に同一 order でソート済みのため、同じキーでマージすれば全体の上位 limit 件が正しく得られる」

### B-3 ドラフト（`viewmodel/SearchDraft.kt`）
- `SearchFilters.type: NarouNovelType?` → `types: Set<NarouNovelType> = emptySet()`、`lastup: NarouLastup?` → `lastups: Set<NarouLastup> = emptySet()`。`activeCount()` は属性と同様に `types.size`・`lastups.size` を加算。`toQuery()` 追従。
- トグル純関数を追加:
  ```kotlin
  /** 全3種を選んだら空集合（=すべて）へ正規化。why: 全選択と未指定は同義であり、チップ表示も「すべて」に畳む。 */
  fun toggleType(current: Set<NarouNovelType>, tapped: NarouNovelType): Set<NarouNovelType>
  /** 非連続な組（SEVENDAY+LASTMONTH）を作らない: 追加でギャップが生まれるなら THISMONTH も点灯。
   *  THISMONTH の消灯で非連続になるなら LASTMONTH も消灯（直近側を残す方が「新しい作品を探す」文脈で自然）。
   *  全3種選択は（先月1日〜now の連続レンジとして意味があるので）空へは畳まない。 */
  fun toggleLastup(current: Set<NarouLastup>, tapped: NarouLastup): Set<NarouLastup>
  ```
  ※ toggleLastup の全選択は畳まない理由: 「すべて」（＝時期無条件）と「7日以内+今月+先月」（＝先月1日以降のみ）は**意味が異なる**ため。

### B-4 UI（`ui/discovery/DiscoverySearchScreen.kt` 条件シート a/b セクション）
- 「作品の形」: 「すべて」チップ selected=`types.isEmpty()`・onClick で `types = emptySet()`。短編/連載中/完結済チップは selected=`in types`・onClick=`toggleType` 経由。
- 「更新された時期」: 同様に `lastups` と `toggleLastup`。
- **「短編+連載中」も普通に選べる**（B-2 のマージで対応するため UI 制約なし）。

### B-5 ラベル（`ui/discovery/DiscoveryQueryLabels.kt`）
- `query.type?.let { labels.add(it.uiLabel) }` → types が非空なら宣言順（SHORT, RENSAI, KANKETSU）に `uiLabel` を「・」連結して**1枚**（例「短編・完結済」）。
- `lastup` → lastups が非空なら宣言順に「・」連結+「に更新」（例「今月・先月に更新」）。
- 既存テスト追従。

### B-6 テスト（新規・追従）
- `typeApiParam` 全8組合せ。`lastupApiParam`: 単一3種はプリセット文字列／{THISMONTH,LASTMONTH}→先月1日0:00(JST)秒〜now秒／{SEVENDAY,THISMONTH}→min(月初, now-7d)〜now／月初直後でも先月に食い込まない等の境界（nowMs を固定注入）。
- `toggleType`: 全選択→空正規化・単純トグル。`toggleLastup`: {SEVENDAY}+LASTMONTH タップ→3種全点灯／{SEVENDAY,THISMONTH,LASTMONTH} で THISMONTH タップ→{SEVENDAY}／単純ケース。
- マージ: fake service で SHORT+RENSAI 時に2コール・allcount 加算・order キー降順・limit 切り。
- QueryLabels の連結表示。既存テストの `type=`/`lastup=` 参照を全追従。

## バッチC: 文字数・読了時間のプリセット複数選択（原則3）

**方針**: `SearchFilters.length/time` は "min-max" 文字列のまま（API送出経路・相互排他 `withLength`/`withTime`・カスタム入力は不変）。**プリセットチップの複数選択は「連続する段の結合」として合成し、選択状態は文字列から分解して復元**する。API が単一レンジしか受けないため（前提3）、非隣接の選択は間の段も自動点灯して連続化する（見えている点灯＝送っている範囲。原則2）。

### C-1 純関数（`viewmodel/SearchDraft.kt`）
- ステップ定義をテスト可能な形で公開:
  ```kotlin
  // 連続階段（境界値が隣接段と一致していることが selectedStepIndices の分解可能性の前提）
  val LENGTH_STEPS = listOf("-10000", "10000-100000", "100000-500000", "500000-1000000", "1000000-")
  val TIME_STEPS = listOf("-30", "30-120", "120-600", "600-")
  ```
  ※ 既存の `DiscoverySearchScreen.kt:375/404` のローカル `lengthPresets`/`timePresets` はこれを参照する形に一本化。
- ```kotlin
  /** 合成レンジ文字列 → 選択中ステップの添字集合。ステップ列の連続部分列 [i..j] の外周
   *  （i の下端〜j の上端。開端は "-"）と一致しない文字列（カスタム入力値）は空集合。 */
  fun selectedStepIndices(raw: String?, steps: List<String>): Set<Int>
  /** 添字 index をトグルし、非隣接になったら間の段を全て点灯してから外周を合成。
   *  全段点灯は null（=すべて）へ正規化。空も null。 */
  fun toggleRangeStep(raw: String?, index: Int, steps: List<String>): String?
  ```
  - 例: raw="10000-100000" で index=2 をタップ → "10000-500000"。raw="-10000" で index=4 → 間の1,2,3も点灯＝"" 全段 → null。raw="100000-500000" で index=2 → null（自分を消して空）。
  - why コメント必須: 「なろうAPIの length/time は単一レンジしか受けない（minlen/maxlen 併用不可・マニュアル§4.4）ため、複数選択は連続区間の結合として表現する。非隣接選択は間の段を自動点灯し、点灯チップ＝実際に送る範囲を一致させる（ADR 0007 原則2）」

### C-2 UI（`ui/discovery/DiscoverySearchScreen.kt` 文字数/読了時間セクション）
- 各プリセットチップ: selected=`index in selectedStepIndices(filters.length, LENGTH_STEPS)`（カスタム active 時は従来どおりカスタム欄優先＝プリセット非点灯）・onClick=`withLength(toggleRangeStep(...))`＋`lengthCustomActive=false`。読了時間も同様。
- 「すべて」チップ・カスタム入力・相互排他は現行ロジック維持。
- ラベルは現行文言のまま（「〜1万字」「1万〜10万字」…）。
- `DiscoveryQueryLabels.rangeText` は合成レンジをそのまま表示できるため変更不要。

### C-3 テスト
- `selectedStepIndices`: プリセット単段・合成2段・開端含む合成・カスタム値("25000-80000")→空・null→空。
- `toggleRangeStep`: 隣接追加・非隣接追加（間の点灯）・端の消灯・中抜き消灯（→どうなるかを定義: 選択中 [i..j] の内側 k を消したら「タップした段だけを残す」のではなく **[i..k-1] を残す**…実装単純化のため「k を境に下側 [i..k-1] を残す」と仕様固定。why: 中抜きは連続制約上どちらかを捨てるしかなく、決定的で予測可能な挙動にする）・全段→null・全消し→null。
- 単体で `buildCustomRange`/`parseCustomRange` との干渉が無いこと（カスタム値はプリセット非点灯）。

## バッチD: キュレーションキーワードの公式準拠・全数収載（原則3・案A）

**方針**: 現行の独自4分類（舞台/主人公/展開/雰囲気）は**公式由来でない抜粋**であり「中途半端な一覧は不足が目立つ」というフィードバックの根因。なろう公式検索ページの「検索ワードを選択」パネルの分類・語彙へ**全数置換**する（ジャンル選択が公式準拠で好評だったのと同じ原理）。

### D-1 データ（`narou/model/NarouCuratedKeywords.kt` 全面書換）
```kotlin
// why: 語彙・分類はなろう公式検索ページ（yomou.syosetu.com/search.php）の「検索ワードを選択」
// パネルに準拠する（2026-07-07 取得・全数収載）。独自の抜粋をしないのは、ジャンル選択が公式分類
// 準拠で好評だった一方、独自抜粋の旧4分類は「中途半端に欠けた一覧は不足の方が記憶に残る」という
// 実機フィードバックを受けたため（入れるなら全部・入れないなら入れない、の前者を採る）。
data class CuratedKeywordCategory(val title: String, val words: List<String>)

object NarouCuratedKeywords {
    /** 公式「おすすめキーワード①-作品内容」（常時表示） */
    val basicCategories: List<CuratedKeywordCategory> = listOf(
        CuratedKeywordCategory("作品傾向", listOf("ギャグ", "シリアス", "ほのぼの", "ダーク")),
        CuratedKeywordCategory("登場キャラクター", listOf("男主人公", "女主人公", "人外", "魔王", "勇者")),
        CuratedKeywordCategory("舞台", listOf("和風", "西洋", "中華", "学園")),
        CuratedKeywordCategory("時代設定", listOf("戦国", "幕末", "明治/大正", "昭和", "平成", "古代", "中世", "近世", "近代", "現代", "未来")),
        CuratedKeywordCategory("要素", listOf("ロボット", "アンドロイド", "職業もの", "ハーレム", "逆ハーレム", "群像劇", "チート", "内政", "魔法", "冒険", "ミリタリー", "日常", "ハッピーエンド", "バッドエンド", "グルメ", "青春", "ゲーム", "超能力", "タイムトラベル", "ダンジョン", "パラレルワールド", "タイムリープ")),
    )

    /** 公式「おすすめキーワード②-ジャンル別」＋リプレイ用（既定は折りたたみ） */
    val genreCategories: List<CuratedKeywordCategory> = listOf(
        CuratedKeywordCategory("恋愛", listOf("異類婚姻譚", "身分差", "年の差", "悲恋")),
        CuratedKeywordCategory("異世界〔恋愛〕", listOf("ヒストリカル", "乙女ゲーム", "悪役令嬢")),
        CuratedKeywordCategory("現実世界〔恋愛〕", listOf("オフィスラブ", "スクールラブ", "古典恋愛")),
        CuratedKeywordCategory("ハイファンタジー", listOf("オリジナル戦記")),
        CuratedKeywordCategory("ローファンタジー", listOf("伝奇")),
        CuratedKeywordCategory("ヒューマンドラマ", listOf("ハードボイルド", "私小説", "ホームドラマ")),
        CuratedKeywordCategory("歴史", listOf("IF戦記", "史実", "時代小説", "逆行転生")),
        CuratedKeywordCategory("推理", listOf("ミステリー", "サスペンス", "探偵小説")),
        CuratedKeywordCategory("ホラー", listOf("スプラッタ", "怪談", "サイコホラー")),
        CuratedKeywordCategory("アクション", listOf("異能力バトル", "ヒーロー", "スパイ")),
        CuratedKeywordCategory("コメディー", listOf("ラブコメ")),
        CuratedKeywordCategory("SF", listOf("近未来", "人工知能", "電脳世界")),
        CuratedKeywordCategory("VRゲーム", listOf("VRMMO")),
        CuratedKeywordCategory("宇宙", listOf("スペースオペラ", "エイリアン")),
        CuratedKeywordCategory("空想科学", listOf("サイバーパンク", "スチームパンク", "ディストピア", "タイムマシン")),
        CuratedKeywordCategory("パニック", listOf("怪獣", "天災", "バイオハザード", "パンデミック")),
        CuratedKeywordCategory("リプレイ（TRPG）", listOf("SW2.0", "AR2E", "ダブルクロス3rd", "MGR", "グランクレスト", "ガーデンオーダー", "ナイトウィザード", "アルシャード", "NOVA", "dragonarms", "モノトーン", "BoA", "セブンフォートレス", "エースキラージーン", "MAG", "片道勇者", "アマデウス", "DLH", "ドラクルージュ", "コロッサルハンター", "スクハイ", "トワハイ", "ＴＮＭ", "アニマアニムス", "拳禅無双", "ルイブレ")),
    )

    /** 後方互換が要る場合のみ。既存参照は basic/genre へ置換するのでこの val は残さない。 */
}
```
- 旧 `categories` は削除し、参照箇所（`DiscoverySearchScreen.kt:322`）を追従。

### D-2 UI（`ui/discovery/DiscoverySearchScreen.kt` 「キーワードから選ぶ」セクション）
- `basicCategories` は現行と同じ描画（SectionHeader＋FlowRow＋`containsWordToken`/`toggleWordToken`＋追加時 `inKeyword=true`。この既存 why コメントは保持）。
- その下に展開トグル行を追加: テキストボタン「ジャンル別のキーワードを見る ⌄」（展開中は「たたむ ⌃」等・`remember { mutableStateOf(false) }`）→ 展開時 `genreCategories` を同じ描画で列挙。
  - why コメント: 「公式パネルは①作品内容と②ジャンル別の2段構成。②＋TRPG系は約80語あり常時表示すると検索画面が長大化するため、公式と同じ段構成のまま既定は畳む（全数収載と画面の静けさの両立）」
- 意匠: トグル行は「条件を変更」等と同じテキストリンク様式（primary・11sp 前後）。新様式を発明しない。

### D-3 テスト
- データ健全性: basic+genre の全カテゴリでカテゴリ内重複語なし・空文字なし（軽い純JVMテスト1本）。
- 既存の `containsWordToken`/`toggleWordToken` テストはそのまま有効。

## 検証ゲート（各バッチ後・Claude 実行）

```bash
cd /home/qingj/wt/api-lab-ai/android && ./gradlew testDebugUnitTest
```
- diff レビュー: **削除行を含む全量**（前回「今月/先月チップのスコープ外削除」退行の教訓）。
- API境界の全数突合: UI で選べる状態 ⇆ `NarouApiService.search()` に渡る引数の対応表を作り、送出漏れ（前回 nottensei/nottenni 欠落の教訓）・未定義値送出が無いことを確認。
- 全バッチ後: `assembleDebug` → `adb-bridge` → 実機上書きインストール → スモーク → PushNotification。

## コミット計画（1バッチ=1論理コミット・Claude が検証後に実施）

1. `feat: セピアテーマをライトから知覚差のある琥珀紙トーンへ再調律`（バッチA）
2. `feat: 結果一覧のジャンルを大→小へその場ドリルダウン可能に（ADR0007 原則1）`（バッチE）
3. `feat: 作品の形態・更新時期の複数選択OR（type複合値/クライアントマージ・lastup unixtimeレンジ）`（バッチB）
4. `feat: 文字数・読了時間プリセットの複数選択（連続段の結合）`（バッチC）
5. `feat: キュレーションキーワードをなろう公式分類の全数収載へ置換`（バッチD）
6. docs: STATUS-api-lab・handover 追記（モック逆反映宿題ほか）
