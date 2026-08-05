---
name: shiori-anim
description: 栞先端の高負荷アニメ（振り付け）を増補・改稿する手順。1tip=1専用振り付け・相異4軸の機械照合・線追従の恒久規則・mockview裁定→Compose翻訳まで。「栞のアニメを増やしたい/直したい」「tip N に振り付けを付けたい」「高負荷アニメの9〜173を作り込む」等の依頼で使う。絵柄そのものの増補は /shiori-tips（棲み分け＝絵は tips・動きは anim）。
---

# 栞 高負荷アニメ（振り付け）の増補・改稿

高負荷モード（ADR 0023＝美しさ特化・負荷許容のプロダクト概念）を栞先端に適用したもの。
導線は星図M と同型＝**debug 限定トグル・release 常時 OFF・トグル OFF（既定）は完全静止＝golden 不変が絶対条件**。

## 正本

| 役割 | 場所 |
|---|---|
| 振り付けの正本（tip 0〜8＋恒久規則） | `docs/design-candidates/bookshelf-shiori-highload-K.html`（拡大表示9種＋in-situ 棚12冊。**冒頭コメントが規則の正本**） |
| 実機の描画（Compose 翻訳） | 振り付けデータ＝`android/app/src/main/java/com/novelreader/ui/components/ShioriHighLoadChoreo.kt`（keyframes の写し・線追従 bf・位相）／1フレーム描画＝同 `ShioriHighLoadTips.kt`／合成入口＝同 `ShioriCover.kt` の `highLoadAnim`（OFF=既存静止パス無改変） |
| 相異の機械照合 | モック側＝`.claude/skills/shiori-anim/tools/verify_anim_layers.py`／Kotlin 側鏡像＝`ShioriHighLoadChoreoTest`（testDebugUnitTest に常設） |
| 概念と前例（トグル配線・jank ゲート） | ADR 0023（星図M＝同型元） |

## 鉄則（破ると差し戻しになった実績つき）

1. **1 tip = 1 専用振り付け**。初稿「174種へ共通語彙20を割当」は「画一的」で差し戻された（2026-08-05）。
   強度を盛る場合も同じ——増幅の結果9種が同じ見え方に収斂したら差し戻しの再演。
2. **相異4軸の機械照合**: keyframes名／動かすプロパティ集合／周期／イージングをペア全比較で重複ゼロ。
   既製イージング（ease 等のキーワード）禁止＝全 tip 個別の cubic-bezier。**照合は tools を監督が実行**（自己申告 GREEN 不可）。
3. **恒久規則・線追従（2026-08-06 ユーザー裁定・全 tip 固定）**: 先端ワンポイントが左右に動くときは
   栞の線（棒）も自然に追従させる＝〈棒＋先端〉を `.flw` で束ね、**付け根（挿し込み点）を支点**に
   装飾と**同周期＋わずかな遅相**（`BF_LAG`）で傾がせる。横動が無い/左右対称で相殺される tip は
   縦張力の微伸び（scaleY）等の**最小追従**。**完全静的の例外は design 上のこだわりとして理由コメント必須**。
   線追従層（bf*）は相異照合の対象外（全 tip 共通の物理規則だから）。
4. **K の意匠語彙から逸脱しない**（和モダン・藍/青磁・「栞」という物性。別スキンの語彙を持ち込まない）。
   グロー等「光」の語彙は光が本質の tip だけ（全種配布は画一化の再演）。負荷は許容＝CSS の重さで自己検閲しない
   （ただし既存 jank ゲートは維持＝計測はトグル OFF の既定条件で回る前提を壊さない）。
5. **9〜173 は初稿語彙のまま `<details>` に畳んで残置**＝作り込む際は 0〜8 と同じ密度・同規則。
   ⚠️ tools の tip 抽出は現状1桁（`t\d`/`bf\d`）前提＝拡大時は多桁対応への改修が先。

## パイプライン

- **A. 生成/改稿（委譲可）**: spec に鉄則1〜4と正本モックを手本として読ませる・self-contained 維持・
  変更は正本モック1ファイルに閉じる。複数案比較は一時ドラフト（**裁定後削除**＝派手版の前例・git 履歴に残る）。
- **B. 機械照合**: `python3 .claude/skills/shiori-anim/tools/verify_anim_layers.py [モックパス]`
  ＝[A]装飾層4軸重複ゼロ・既製イージングなし [B]線追従の全 tip 適用（周期一致・遅相配布・拡大/in-situ 両ステージ）・外部参照なし。
- **C. 提示（人の審級）**: **必ず `mockview`**（素の chrome 禁止）。裁定形式＝〈全体 GO／個別番号の差し戻し〉。
  監督は目視しない（memory `feedback-orchestrate-dont-inspect-visuals`）。
- **D. Compose 翻訳**: 下の等価表。周期・角度・スケール値はモックが正本（算術参加値＝勝手に丸めない）。
  振り付けは data として持たせ**「9種相異」を assert する構造テスト**を同梱（画一化の回帰防止＝B の Kotlin 側鏡像）。
  トグル OFF 時は infiniteTransition を合成に入れない構造にする＝golden 不変。
- **E. ゲートと後詰め**: `testDebugUnitTest`／golden 不変確認は監督が一括。アニメの質感は実機後詰め層
  （ADR 0005 §B）＝最終 OK はユーザーの実機目視（PushNotification→目視 OK の通常フロー）。

## CSS → Compose 等価表（tip 0〜8 の実績で確認済み）

- `rotate/translateX/translateY/scale/scaleX/scaleY` → graphicsLayer or Canvas transform（支点＝`transform-origin` は pivot で再現）
- `skewX` → `Matrix` の skew
- `cubic-bezier(a,b,c,d)` → `CubicBezierEasing(a,b,c,d)`
- `stroke-dashoffset` → `PathEffect.dashPathEffect(intervals, phase)`
- `opacity` → `Color.copy(alpha)`／線幅の変化 → `Stroke(width)`
- `filter: brightness/saturate` → **等価物なし＝HSL で色を算出して渡す**（tip5 の前例・モックコメントに申し送りあり）
- `drop-shadow`（グロー）→ **等価物なし＝BlurMaskFilter の2パス前描き**（半径×マウント倍率。派手版検討の知見＝git 履歴 29b0096→60e6224）
- マウント時の位相分散（モックの JS 位相配布）→ 各栞の開始位相を title 由来の決定論で散らす（栞の乱数系列の流儀＝/shiori-tips）
