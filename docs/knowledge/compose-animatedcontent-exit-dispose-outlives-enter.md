# AnimatedContent: 退場側の onDispose は入場側の LaunchedEffect より**後**に走る

**重要度**: ★★
**確定日**: 2026-07-29（読書画面の章送りで実害として顕在化・修正済み）
**一行要約**: 遷移で作り直されるスコープに「ウィンドウ単位の共有資源」を持たせると、退場側の後始末が入場側の設定を上書きして壊す。

## 症状

没入読書中に章を送ると、**画面上端のシステムバーが復帰し、画面消灯抑止（`keepScreenOn`）も外れる**。
一度壊れると以後は再発火しないため、その章以降ずっと非没入のまま・読書中に画面が消えるようになる。

## 真因

`AnimatedContent` は遷移アニメーションのあいだ**退場側のサブコンポジションを1フレーム以上生かしたまま**、
入場側を先に構成する。したがって同じ資源を両側が触ると、実行順は必ずこうなる:

```
新章の LaunchedEffect        → controller.hide(systemBars)      … 入場側が先
旧章の DisposableEffect.onDispose → controller.show(systemBars)  … 退場側が後 ★これが勝つ
                                    view.keepScreenOn = false
```

最終状態は「バーが出ていて消灯抑止も切れた」状態になる。さらに悪いことに、
**状態が変わらないので以後どちらのエフェクトも再発火しない**（キー不変・`distinctUntilChanged` 等の抑止）。
＝一度壊れたら自然回復しない。

## 対処（構造の是正であって順序合わせではない）

**「スコープの寿命 < 資源の寿命」なら所有者が間違っている**、と判断する。
ウィンドウ単位の資源（`keepScreenOn`・`systemBarsBehavior`・離脱時のバー復帰）は
**画面スコープの単一の所有者へ持ち上げ、遷移で作り直されるスコープからは剥がす**。
本アプリでは `ChapterScreen`（章スコープ）から `ReadingWindowContract`（画面スコープ・章の
`AnimatedContent` より外側で呼ぶ）へ移した。**旧章の `onDispose` が存在しなくなるので、
「後から走って勝つ」余地そのものが消える**。

以下は**採ってはいけない**対処（症状を隠すだけで競合は残る）:
- 退場側の `onDispose` を条件分岐で握り潰す（どの条件が真かは遷移タイミング依存＝レースが残る）
- 入場側で遅延させて順序を待ち合わせる（フレーム数の仮定に依存し、端末速度で壊れる）
- 遷移完了後に再度 `hide()` を打ち直す（一瞬バーが見えるちらつきが残り、根本原因は不変）

「遷移する側が持つべき状態」（本アプリなら没入トグルの `collapsedFraction` 連動）は章スコープに残してよい。
分ける基準は**資源の寿命が画面と同じか、遷移単位か**。

## 検知（退行を止める）

振る舞いテストだけでは「章スコープが再び所有し直す」退行を捕まえられないため、
**ソースの形で不変条件を固定する**のが有効（`ChapterScreen.kt` に `keepScreenOn` が現れない／
`onDispose` でバーを戻さない／所有者の呼び出しが `AnimatedContent` より手前）。
ただし**生テキストへの `contains` は why を説明するコメント中の言及まで拾って偽陽性になる**
（2026-07-29 に実際に fail した）。`KotlinSourceScanner.stripComments` を通して実コードだけを検査すること。

## 一般化

`AnimatedContent`・`Crossfade`・`NavHost` の遷移など、**両側が同時に生きうる構造**すべてに同じ罠がある。
「入場で設定し、退場で戻す」対称なエフェクトを遷移するスコープに書いた時点で、この競合を作り込んでいる。

関連: [compose-fresh-content-input-dead-window.md](compose-fresh-content-input-dead-window.md)（遷移直後の入力死に窓）・
[compose-draggable-delta-race-launch-snapto.md](compose-draggable-delta-race-launch-snapto.md)（launch と snapTo のレース）。
