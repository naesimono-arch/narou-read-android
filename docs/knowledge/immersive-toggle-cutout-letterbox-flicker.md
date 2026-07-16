# 没入トグルの上下ちらつき: 真因はカットアウト letterbox の伸縮（色合わせでは治らない）

**重要度**: ★★★
**確定日**: 2026-07-16（PGEM10 / ColorOS・実機で根治確認）
**実装**: `MainActivity.kt`（layoutInDisplayCutoutMode）・`theme/Theme.kt`（バー透明化＋scrim 無効）

## 事実（実測）

- Edge-to-Edge（`setDecorFitsSystemWindows(false)`）配下で `systemBars` を hide/show トグルすると、
  **カットアウト搭載機では既定（`LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT`）だと上端カットアウト帯
  （PGEM10 実測 160px）が letterbox⇄アプリ描画で伸縮する**＝window リサイズの過渡フレームが
  「開閉のたびの上端ちらつき」に見える。発生は **8〜9割で確率的**（リサイズと描画の競合）・
  **横画面では無症状**（カットアウトが長辺＝左右に回り上下の伸縮が起きない）——この2つが機序の指紋。
- 下端は別機構: XML テーマ既定（Material）の不透明ナビバー色＋**API29+ が透明バーへ強制する
  contrast scrim** が、バー出没と同期して明滅する。
- アプリ内レイアウト対策（バーへの `*IgnoringVisibility` insets 適用）は「バー内パディングの振れ」
  には必要だが、**window レベルの伸縮には無力**＝これだけでは治らない。

## 対処（根治の組み合わせ）

1. `layoutInDisplayCutoutMode = ALWAYS`（API30+。28-29 は SHORT_EDGES）→ バー出没で window 幾何が不変になる。
2. status/navigationBarColor を常時 TRANSPARENT＋`isStatus/NavigationBarContrastEnforced = false`（API29+）。
3. バー系コンポーザブルの insets は可視追従でなく `IgnoringVisibility` 系を使う（本文側 `ChapterContent` と同じ）。

## Why-not（不採用の先行対策）

- **window 背景をテーマ紙色へ再定義**（`fc27ce2`）: letterbox の「色」を本文と同化させる緩和で、
  伸縮（幾何）が残るため確率的なちらつきは消えなかった。過渡フレームで window 面が露出した場合の
  保険として維持はする。

## 検証の落とし穴

- ColorOS は `screenrecord` を全パスで拒否（device-verify §4）＝動画での確認は不能。
  適用確認は `dumpsys window windows` の `layoutInDisplayCutoutMode=always` と、
  両状態 `screencap` の端領域ピクセル突合＋最終はユーザー目視で行った。
