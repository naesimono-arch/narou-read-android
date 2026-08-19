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

## 結末（2026-08-19 実機で測定）— **効果なし。入れなかった。**

上の直し方を実際に適用し、tab-swipe マクロベンチで前後を測った（`swipeTabs`・各走行5反復・**前2走行／後2走行**）。

| | P50 | P90 | P99 |
|---|---|---|---|
| 前 run1 / run2 | 6.498 / 5.886 | 11.148 / 10.052 | 18.837 / 19.338 |
| 後 run1 / run2 | 5.974 / 6.692 | 10.103 / 10.711 | 17.435 / 19.346 |

**前後の範囲がほぼ完全に重なる**（前 P50 5.886–6.498 ↔ 後 5.974–6.692）。平均差は P50 +0.14ms（悪化側）・
P90 −0.19ms・P99 −0.70ms で**符号が揃わず**、同一条件の走行間ばらつき（P50 で ±0.6ms）より小さい。
全4走行とも予算 `TabSwipeBudget` 内（最悪 P50 6.7 / P90 11.1 / P99 19.3）。**変更は revert 済み。**

⚠️ **「metrics で skip しない」＝「直せば速くなる」ではない**、の実例として残す。
上乗せは予告どおり1スワイプにつき `HorizontalPager` 1回ぶんで、`frameDurationCpuMs` の分位に出る大きさではなかった。
**再挑戦するなら、まず「分位に出る大きさか」を見積もってから**——診断が正しくても、効果が測定限界より小さいことはある。
（結線が壊れていないことは確認済み＝diff は純構造・`KTabNavigationTest` 6/6 通過・修正版 APK で `swipeTabs` 全弾コミット。）

