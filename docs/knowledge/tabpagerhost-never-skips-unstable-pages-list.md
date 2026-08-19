# `TabPagerHost` が決して skip しない — 壊れているのはラムダでなく「それを包む List」

2026-08-19 の Compose compiler metrics で発見。**ラムダ安定性は健全なのに host が skip しない**という形なので、
「コールバックが不安定なのでは」と探すと外す。

## 事実（metrics 実測）

- `app_debug-composables.txt:2099`＝`restartable skippable … TabPagerHost(… unstable pages: List<Function2<Composer, Int, Unit>>)`
- 呼び出しは `MainActivity.kt:536-622` の `pages = listOf({…},{…},{…})`＝**毎コンポーズ新しい `List`**。
- **strong skipping は有効**（`app_debug-module.json` の `featureFlags.StrongSkipping: true`）。
  strong skipping 下の **unstable 引数は `===` 比較**なので、毎回別インスタンスの `List` は**必ず不一致**＝skip されない。
- ラムダ自体は全 1,487 個が memoize 済み＝**個々のラムダは安定**。壊れているのは**包み**だけ。

## 駆動源（なぜ毎回コンポーズされるのか）

同 542 行の `isFrontTab = tabPagerState.currentPage == KTab.BOOKSHELF.ordinal` が
**tabs ルート scope で `currentPage` を読む**ため、**スワイプの settle ごとにこの scope が recompose** し、
そのたびに新しい `List` が作られる。

## 直し方（決定済み・ただし効果測定が前提）

各面を局所 val（`@Composable () -> Unit`）へ持ち上げ、`remember(p0, p1, p2) { listOf(p0, p1, p2) }`。
strong skipping 下ではラムダは `rememberComposableLambda` で**インスタンス同一性が保たれ捕捉値だけ更新される**ので
`List` は1回だけ生成される。
⚠️ **捕捉値を手で並べるキーにしない**——将来ラムダが memoize されない形に変わったとき、
val をキーにしておけば同一性が自動で崩れて `List` が作り直される＝**現状より悪化しない側へ倒れる**。

## ⚠️ 期待値は小さい（入れる前に測ること）

上乗せは**1スワイプにつき `HorizontalPager` 1回の再コンポーズ**相当。ページ本体は各ラムダが自分の scope を
invalidate するので不変。**有意な改善が出なければ入れない**（決め打ちの性能修正はこのリポジトリの禁じ手）。
測定は tab-swipe マクロベンチ（予算 `TabSwipeBudget` P50 11 / P90 18 / P99 50ms）。
⚠️ そのベンチは現在**端末への投入が塞がれている**＝`docs/knowledge/coloros-blocks-adb-install-of-benchmark-apk.md`。
回帰の絵は JVM の `KTabNavigationTest`（スロット index 契約）＋`swipeTabs` と `swipeTabsWithTransition` の差分。
