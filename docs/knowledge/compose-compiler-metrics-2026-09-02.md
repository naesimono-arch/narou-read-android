# Compose compiler metrics 実測（2026-09-02）と kotlinx-collections-immutable 要否の決着

計測トリアージ `.claude/plans/reading-render-perf-triage-2026-08-18.md` の**項目8**。
A+C（2026-08-26）・E（2026-09-02）実装後の状態で取り直した。

## 取り方（再現手順）

```bash
bash tools/gwlock.sh :app:compileDebugKotlin --rerun -PcomposeCompilerReports=true
```

⚠️ **`--rerun` は必須**。`-PcomposeCompilerReports=true` を足しても
`compileDebugKotlin` は **UP-TO-DATE で素通りしレポートが1つも生成されない**
（Compose コンパイラプラグインのオプションがタスク入力として追跡されないため）。
付け忘れると「BUILD SUCCESSFUL なのに成果物ゼロ」＝偽の成功になる。実際に1回踏んだ。

出力先は `layout.buildDirectory/compose_compiler`＝canonical では ext4 退避先の
`/home/qingj/ext-build/novel-reader/app/compose_compiler/`（`build/` 配下＝**clean で消える**）。

## 結論1: skippability は既に飽和している（改善余地ゼロ）

`app_debug-composables.csv` の**名前付き composable 408 個**（ラムダを除く）:

| 区分 | 実数 |
|---|---|
| restartable | 390 |
| うち skippable | **390（100%）** |
| restartable なのに skippable でない | **0** |
| 非 restartable | 18 |

非 restartable の 18 個は全て**値を返す composable**（`rememberShelfFace` 等の `remember*` 系、
`resolveAdjustingRow`、Modifier ファクトリ `phosGlow`・`horizontalScrollEdgeFade`・`emptyStatusSemantics`、
`animateSettingsPeek`）＝構造上そうなるもので、是正対象ではない。

ラムダは `memoizedLambdas 1531 / totalLambdas 1531`＝**全て memoize 済み**。
トリアージ入力の懸念「route→Content へ渡すコールバックが毎回再生成されて束の努力が無効化される」は**実測で陰性**。

`app_debug-module.json` の `featureFlags` は `StrongSkipping: true`（ほか IntrinsicRemember /
OptimizeNonSkippingGroups / PausableComposition）＝Kotlin 2.0.20+ の既定 ON を実データで確認。

## 結論2: kotlinx-collections-immutable は**外せない**（ただし理由は従来の記録と違う）

判定は反実仮想の実測で出した。専用 worktree で main の `ImmutableList`→`List`・
`persistentListOf`→`listOf`・`toImmutableList()` 除去を機械適用し（`@Immutable` は残置）、
同じレポートを取り直して差分を見た。**依存を外しても main はコンパイルが通る**。

差分（baseline → 依存なし）:

| 指標 | あり | なし |
|---|---|---|
| named restartable / skippable | 390 / **390** | 390 / **390** |
| knownUnstableArguments | 214 | 226 |
| inferredUnstableClasses | 130 | 133 |

⚠️ **skippable 数は1個も動かない**＝トリアージの問い「immutable へ替えると skippable になるものがあるか」
の答えは **無い**。strong skipping 下で skippability は既に飽和しており、依存はそこには効いていない。

効くのは**比較の意味論**（stable=`equals()` / unstable=`===`）。依存を外すと読書経路の 8 引数が `===` へ落ちる:

```
ChapterContent.content        VerticalChapterContent.content
ParagraphItem.paragraph       VerticalParagraphItem.paragraph
RubyText.segments             ChapterPeekPanel.peek
ChapterScreenContent.nextPeek / .prevPeek
```

### 真因は `ChapterPeek` の毎回生成（ここが唯一の実害）

段落系は無害だった: `paragraphs` は `remember(content) { content.segments.splitIntoParagraphs() }`
（`ChapterContent.kt:104` / `VerticalChapterContent.kt:124`）で章ごとに1回だけ作られ、
同じ instance が配られ続ける＝`===` でも成立する。

対して `ChapterPeek` は `ChapterScreen.kt:493/503` で `prevPreview?.let { ChapterPeek(...) }` と
**remember 無しで毎コンポジション新規生成**される data class。stable なら `equals()` で等価判定されて
skip できるが、unstable に落ちると `===` が毎回失敗する:

```
あり: stable   class ChapterPeek { stable   val content: ChapterContent }
なし: unstable class ChapterPeek { unstable val content: ChapterContent }
```

結果 `ChapterScreenContent`（読書画面の内容全体）と `ChapterPeekPanel` が
**親の再コンポジションのたびに必ず再コンポーズされる**＝読書画面が skip 能力を失う。これが外せない理由。

### 旧記録（`android/app/build.gradle`）の誤りを2点訂正した

2026-08-19 のコメントは結論（外せない）だけ正しく、**機序は現在の実態と合っていなかった**:

1. 「List へ戻すと StyledBlock が unstable に落ち、sealed な TextSegment 階層ごと unstable 化して
   ChapterContent へ伝播する」→ **起きない**。実測は
   `stable class TextSegment.StyledBlock { unstable val segments: List<TextSegment> }`＋
   `<runtime stability> = Stable`。`TextSegment` に付いた `@Immutable` が推論の循環を断つため階層は
   stable のまま。unstable になるのは `ChapterContent` **自身**で、経路は伝播ではなく
   自分のフィールドが `List` インタフェースであること（直接の理由）。
2. 「外すなら TextSegment 側へ `@Immutable` を付けるのが前提条件」→ **その注釈は既に付いている**
   （`model/ChapterContent.kt:12`）。つまり前提条件は充足済みで、それでも外せない＝
   この記述のままだと「注釈さえ付ければ外せる」と読み違える。

## 残った unstable 引数（46件）の位置づけ

是正していない。内訳は ①ViewModel 直渡し（画面ルート 8 件）＝Compose 的には
`===` で足り、実 instance は不変なので害が無い ②`Map<ReadingStatus, Int>` / `List<String>` など
本棚・さがすのチップ材料 ③`ResultContext`（`unstable val query: DiscoveryQuery`＝`Set<Int>` 4 本が根）。

③ の `ResultContext` は**さがす結果画面 4 スキン（J/K/M/P）すべての引数**に出るので候補ではあるが、
skippable 数は既に飽和しており、`ChapterPeek` のような「毎回生成される持ち主」が居るかを
確かめない限り実害は言えない。**着手するなら同じ反実仮想の手順で先に実測すること**（憶測で
`ImmutableSet` 化しない）。
