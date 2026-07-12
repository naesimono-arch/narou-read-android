package com.novelreader.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

// ============================================================
// motion トークン（duration/easing/spring のスロット）
// 原則: motion はフィードバック（状態変化の伝達）のみ。装飾アニメ・自動ループは無し。
// なぜトークン化するか: ADR 0005-B の「後詰め層」に motion の正本がゼロという穴があり、
// 実測値が各画面へ散在・重複していた。値そのものは実機調整で更新してよいが、
// スロット（duration/easing/spring）と上記原則を正本化する＝ADR 0014 §motion。
// したがって値の変更は必ずこのファイル1箇所で行い、呼び出し側は直書きしない。
// ============================================================

// カードのタップ押下スケール（Apple Books 的な触感）。
// BookCard（書架/目録）・WebBookCard（Web 由来カードの書架/目録）の 4 箇所で共有する押下フィードバック。
// なぜ NoBouncy か: Design/08 禁止則③（overshoot/bounce/spring 振動の禁止）＋同 G 適用例
// 「押下フィードバックにカスタムのスケールバウンスを足さない」に旧値 dampingRatio=0.6f が抵触
// （復帰時にわずかに跳ねる＝2026-07-12 UX/Design 全層監査 d-motion Major）。stiffness=400f の
// 素早い追従は維持し、跳ねだけを除去する。押下の targetValue（0.96/0.98）は
// 呼び出し側の意匠差なのでトークン化しない（スロットは spring 仕様のみ）。
val MotionSpringCard: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

// 読書画面の没入バーを全表示/全非表示へ吸着させる settle 用 spring（NativeReadingScreen.settleTopBar）。
// StiffnessMediumLow のバウンシー挙動でバーの出没を軽快に見せる（自前 settle の触感復元。詳細は使用側コメント）。
val MotionSpringBarSettle: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

// PDF 処理中バナーの進捗バーが現在ステップの目標値へ伸びる時間（ms）。ProcessingBanner の tween に使う。
// ステップ切替時の瞬時リセット（snapTo）はアニメではないためトークン外。
const val MotionDurationProgress: Int = 400

// バナー等の入退場 duration（ms）。Design/08-C（enter/exit は別指定・exit は enter より短い＝加速して消える）
// に基づき2値で彫る。なぜ2値か: 出現は「気づかせる」ため長め、退場は「作業の邪魔をしない」ため短め、
// と目的が異なる（reveal 250 / dismiss 150。禁止則①の 350ms 上限内）。
const val MotionDurationReveal: Int = 250
const val MotionDurationDismiss: Int = 150

// ヒント・題字などの fade/crossfade 用 duration（ms）。復帰ヒント（NativeReadingScreen）・
// 詳細画面バー題字（NovelDetailScreen）等の「そっと現れて消える」同型演出で共有する。
// なぜトークン化するか: Design/08 禁止則②（duration/easing を野良既定に委ねずトークン経由）。
const val MotionDurationCrossfade: Int = 250
