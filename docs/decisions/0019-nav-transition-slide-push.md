# 0019. 画面遷移モーションを slide push に統一（fade/shared-axis Z 却下・目次⇄本文も同向き・章送りは瞬間据え置き）

- 状態: 採用（2026-07-15・`ui/polish`）
- 関連: 実装 `MainActivity.kt`（NavHost の enter/exit/popEnter/popExit）・`ui/NativeReadingScreen.kt`（目次⇄本文の AnimatedContent）・`ui/theme/Motion.kt`（`MotionDurationNavTransition=250`）／競合解析 `docs/reference/06-competitor-reading-motion.md`／適用案 `.claude/plans/reading-motion-apply-2026-07-15.md`（D1）
- 前提: motion は ADR 0005-B「実機後詰め層」＝HTMLモック非対象で、値・型とも実機フィードバックで決めてよい層。本 ADR はその層での型（アニメの種類）の裁定。

## 背景

- D1: NavHost に遷移指定が無く navigation-compose 2.7 系の野良既定 `fadeIn/fadeOut(tween(700))` が全ルート遷移に効いていた。競合5アプリ実測（06 §3）では読書系の実尺は 100〜250ms で、700ms は倍以上＝「もっさり」の第一容疑。まず尺だけ 250ms のフェードへ締めた（意匠は据え置きの後詰め）。
- その後ユーザーと「フェード以外の遷移も見たい」となり、同一尺 250ms で**種類だけを変数化**した3案（フェード／横スライド push／shared-axis Z＝scale+fade）を実機で触り比べた。

## 決定

**画面遷移を横スライド push に統一する（尺は `MotionDurationNavTransition`=250ms 共有）。**

- **向きルール**: 進む＝新画面が右から左へ潜り込む（`slideIntoContainer(Start)` / 旧画面は `slideOutOfContainer(Start)`）／戻る＝前画面が左から右へ戻る（pop 系は `End` 方向）。移動方向を身体感覚に合わせ、「一階層潜る／戻る」を方向で伝える。
- **適用範囲**: NavHost の全ルート遷移（本棚⇄読書・発見系・Web リーダー・詳細）。
- **目次⇄本文も同じ向き・尺で揃える**: 目次⇄本文は同一 nav ルート内の state 切替（`resolvedFile` の出し分け）で NavHost の遷移が効かないため、`NativeReadingScreen` で `AnimatedContent` に包んで同じ向きルール（目次→章＝潜る／章→目次＝戻る）を与える。
- **章→章（話送り）は瞬間のまま据え置き**: `AnimatedContent` の transitionSpec で「どちらも目次でない」ケースは `EnterTransition.None`＝無アニメ。演出化は P1（症状未指差しのため保留）。

## トレードオフ（自覚して受け入れたもの）

- フェードより**動きが主張する**（静謐寄りではなくなる）。ただし方向で移動の意味が伝わる利得を優先した。
- `AnimatedContent` は遷移中に退場側と入場側を**同時に compose** する（250ms・読書本文が一時的に2重 compose）。尺が短く破綻はないが、本文描画の一時コストは受け入れる。
- 目次⇄本文で章表示側の描画を「自身の state（`file`）」基準に組み替えた（退場側が新しい `resolvedFile` を読んで中身が入れ替わるのを防ぐため）。

## 却下した代替案

- **フェード（250ms へ締めるだけ）**: 静かだが**方向感が無い**。「進む/戻る」「潜る/戻る」の階層移動が伝わらず、目次⇄本文でも上下関係を示せない。実機比較で見劣り＝却下。
- **shared-axis Z（scale+fade の奥行き）**: 「潜る」は表現できるが scale が**装飾寄り**で原則「静謐＝フィードバックのための motion」（ADR 0014）と相性△。却下。
- **章→章（話送り）も即スライド化**: 業界作法は「送りは滑らせ・ジャンプは瞬間」の二択（06 §3-C）で筋は通るが、症状の指差しが無いうちに新規演出を足さない方針（P1）に従い保留。要望が出たら別途。
- **バウンド/overshoot 系の遷移**: 禁止則③（overshoot/bounce/spring 振動）に抵触＝不採用確定。
