---
name: visual-language
description: UIの見た目（配色・タイポ・余白・構図・アニメ）を触るときの入口。HTMLモック正本→Compose翻訳の分業、トークン層構造（直書き禁止）、機械検査、関連ADRを案内する。Composeで意匠を自己判断する前に必ず読む。
when_to_use: 「配色・色を変えたい」／「余白・レイアウトを調整したい」／「フォント・タイポを触る」／「モックと突き合わせたい」／「アニメーション・motion を足す」 などの依頼で使う。
---

# UI 視覚言語の正本と作法（WHERE-IS-TRUTH）

## 大原則: HTMLモックが正本・Compose は翻訳

見た目の思想設計は claude.ai の `/design`（HTMLデザインシステム）で行い、Compose はその**翻訳**として実装する
（UI-n 分業ワークフロー＝見た目の白紙再設計を HTML 正本で回した経緯のため）。
**Compose 側で意匠を自己判断しない**——まず下の ADR とモック現物に接地する。

## 正本の層構造（上位審級順・詳細は ADR 0014）

1. **原則** = `docs/decisions/0014-design-principles-and-source-layers.md`（5原則＋禁止則表。裁定は「原則N番との衝突」として書く）
2. **トークン** = `theme/Color.kt`（色）・`theme/Typography.kt`（明朝 `MinchoFamily`）・`theme/Motion.kt`（motion スロット）——**直書き禁止**
3. **モック** = リポジトリ内 `docs/design-candidates/` が一次正本（2026-07-12 一本化）
   - UI-n 主要4画面 `bookshelf/reading/toc/settings-D.html`・発見/検索系 `discovery/*.html`・本棚 目録/栞系も同基準
   - claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の同名は**収蔵コピー＝正本ではない**（正本上書きは承認制）。A〜J スキン案など未収蔵の探索資産のみ claude.ai 側に保持
4. **コード** = 上記の翻訳結果

視覚言語の採用判断（D「和モダン・余白」）と白紙設計のスコープ＝`docs/decisions/0005-ui-n-visual-language-D.md`。

## 機械検査

モック⇄トークンの同期は `python3 tools/check_design_tokens.py`（不一致で exit 1）。UI 変更のゲートに含めること。
**見た目・余白を変えるコミットは `recordRoborazziDebug` で golden 再記録を同梱する**（verify は既定ゲート非同乗＝
再記録を忘れると腐ったまま潜伏する。実例: 84d9501 の余白再翻訳で golden 24枚が陳腐化し 2026-07-17 まで未検出）。

## スコープ外（実機後詰め層）

操作感・組版の質感・アニメ・没入クロームの挙動は HTML→Compose で最も劣化する層＝モックで確定させず実機フィードバックで後詰めする（ADR 0005 §B）。

## 委譲時の統治（実測逸脱の再発防止）

- 意匠・規約に触れるバッチの委譲仕様書には**モック正本（ADR 0005）・関連 ADR の参照を必須記載**（A/B 実測で唯一の統治逸脱がここから出た）。
- UI/意匠の Compose 翻訳の委譲は3点セット〈モック現物・監督自作の Compose 正本1画面・厳密シグネチャ＋色対応表〉が揃ってから（正本1画面を監督が先に書く工程は省けない）。
- DesignSync は**主セッション限定**（サブエージェントへ伝播しない実測＝memory `designsync-main-session-only`）。モックの fetch・同期は委譲しない。

## ユーザーとの回し方

方向性を質問で止めない——候補は全部 HTML モック化して `chrome` コマンドで見せ、直すループで詰める（memory `feedback-build-dont-ask-iterate`・`feedback-ground-in-design-source-first`）。
