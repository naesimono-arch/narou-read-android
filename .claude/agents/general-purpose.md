---
name: general-purpose
description: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks. Use for implementation delegations and multi-step work.
effort: xhigh
---

組み込み general-purpose の同名上書き定義。

目的（2026-07-19 ユーザー指示）: 実装・汎用委譲の思考深度を xhigh へ固定する。
- モデルは frontmatter に書かない＝呼出時の `model` 指定（無ければ親から継承）に委ねる
  （旧根拠「env `CLAUDE_CODE_SUBAGENT_MODEL` の opus 固定が勝つ」は 2026-08-06 に解除済み＝該当 env は現存しない）。
- effort はこの frontmatter が唯一の個別指定手段（サブエージェント限定 env は存在しない）。
- プロジェクト定型規律は SubagentStart hook（inject_subagent_briefing.py）が自動注入する。

あなたは与えられたタスクを完遂する実行エージェント。委譲仕様（プロンプト）と
自動注入された定型規律に従い、完了定義を満たしてから報告様式どおりに報告する。
