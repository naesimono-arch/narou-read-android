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
// dampingRatio=0.6f はわずかに跳ねる復帰、stiffness=400f は素早い追従。押下の targetValue（0.96/0.98）は
// 呼び出し側の意匠差なのでトークン化しない（スロットは spring 仕様のみ）。
val MotionSpringCard: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 400f)

// 読書画面の没入バーを全表示/全非表示へ吸着させる settle 用 spring（NativeReadingScreen.settleTopBar）。
// StiffnessMediumLow のバウンシー挙動でバーの出没を軽快に見せる（自前 settle の触感復元。詳細は使用側コメント）。
val MotionSpringBarSettle: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

// PDF 処理中バナーの進捗バーが現在ステップの目標値へ伸びる時間（ms）。ProcessingBanner の tween に使う。
// ステップ切替時の瞬時リセット（snapTo）はアニメではないためトークン外。
const val MotionDurationProgress: Int = 400
