# スクロールで畳むヘッダ要素の2方式 ＋ AnimatedVisibility 予約スペース snap の罠

> ここは **コードが正本**。「なぜこのパターンか」とコード参照に絞る。
> 発端＝本棚発見帯『新しい物語を見つける』の「完全退避」再設計（C②・2026-07-14）。

## 問題

ヘッダ要素（発見帯）を、スクロールで畳んで退避させたい。ただし
**同一要素（位置も役割も同じ）をスクロールで restyle するのは違和感**（実機却下＝箱→1行・墨→藍の morph）。
退避しても状態フィルタは常時 top に残す（sticky）。

## 方式A: 閾値式 AnimatedVisibility（本棚で採用）

- 帯＋フィルタを **Lazy コンテナの外＝固定ヘッダ Column へ hoist**する。`LazyVerticalGrid` に
  `stickyHeader` が無く、本棚はグリッド/リスト2モードのため、Lazy 外への hoist が**両モード一律 sticky の素直な解**。
- 帯の可視は `derivedStateOf` で **先頭到達（先頭書影が最上部付近・8dp デッドゾーン）**を判定して駆動。
  Boolean を返す `derivedStateOf` なのでスクロール中の再コンポーズは真偽が反転する瞬間だけ（frame 毎に走らない）。
- 帯は **restyle せず `shrinkVertically`＋fade で高さ0へ畳む**（＝「フルの見た目のまま消えるだけ」）。
- 長所: 実装が軽く両モード共通。短所: **閾値トリガの退避アニメは指の動きと連動しない**ため、退避開始のタイミングに
  軽い不連続感が残る（体感『不足』＝deferred。handover ★残1）。

## 方式B: スクロール連動式（読書バーで採用）

指と完全連動で自然に畳みたいなら、**バー高∝スクロールオフセットを nestedScroll で連続縮小**する
collapsing header 本来型（`graphicsLayer{ translationY }`＋`collapsedFraction`）。自然だが nestedScroll 配線が要り重い。
実装＝[topappbar-overlay](topappbar-overlay.md)（没入バーの退避・中央タップトグル）。方式Aで体感が足りなければこちらへ再設計する。

## 罠（why-critical）: AnimatedVisibility にカスタム exit を渡すと既定の shrink が外れる

`AnimatedVisibility` の既定 exit は `fadeOut()+shrinkOut()`。**`slideOutVertically()+fadeOut()` のように
自前 exit を指定すると既定の size 縮小が置き換わって消える**。すると退避アニメ中は要素の**占有スペースが
フルのまま予約され続け、アニメ終了の瞬間に一括除去**される→下の sibling が最後にカクッと跳ねる
（2026-07-14 実機所見「消える瞬間のくっと」の真因）。**layout の高さを畳むには
`shrinkVertically`/`expandVertically` を必ず併記**する（fade だけ・slide だけでは size は動かない）。

- 同型の潜在issue: 同ファイルの `ProcessingBanner`（`slideIn/OutVertically` のみ＝バー直下で目立たないが同根）。
- 補足: 高さアニメでも閾値式は方式Aの短所（指と非連動）が残る＝snap 解消 ≠ 完全な自然さ。

コード: `BookshelfScreen.kt` の `FindGuideBand` と、それを包む hoist 済みヘッダの `AnimatedVisibility`（c10679e）。
