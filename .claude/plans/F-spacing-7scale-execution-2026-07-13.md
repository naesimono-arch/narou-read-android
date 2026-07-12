# F余白 拡張7段 実装プラン（実行中）

**対象ブランチ: `ui/polish`**（worktree `/home/qingj/wt/ui-polish`・ext4＝素の `gw` 可）
裁定正本＝handover「残1[F]」＋比較モック `docs/design-candidates/spacing-scale-compare-D.html`（2026-07-13 確定）。
生データ＝`.claude/plans/F-spacing-audit-raw-2026-07-12.json`（**注意: `synth.unifiedInventory.proposedMapping` は旧5段基準＝失効。丸めは下の新表が正本**）。

## 確定裁定（再掲・不変）

- スケール＝**{4, 8, 12, 16, 24, 32, 40} dp/px**（12・32 のみ追加）
- 丸め＝**最近傍・等距離は大きい側（round-half-up）**
- **base scale 外＝不変**: 構造インセット（Compose実在: 60/96/+64/+80 → `Insets.*` 命名トークン。モック側 92/210 は allowlist・44/70/90 等の phone フレームはハーネス）・本文組版の em 値・変数（bodyMarginDp）・WindowInsets 加算軸・0・線太さ（border/ヘアライン1/1.5/2px は余白でない）

## 丸め表（7段＋round-half-up から機械導出＝委譲ブリーフの正本）

| 元値 | → | 備考 |
|---|---|---|
| 1, 2, 2.5, 3 | 4 | ただし gap:1px の一覧セパレータは spacing でなくヘアライン＝border 化を判断（audit注記） |
| 5, 6, 7 | 5→4・6→8(タイ↑)・7→8 | |
| 9 | 8 | |
| 10, 11, 13 | **12** | 旧提案(8/16)から変更。10はタイ↑ |
| 14, 15, 17, 18, 19 | 16 | 14はタイ↑ |
| 20, 21, 22, 26 | 24 | 20はタイ↑ |
| 28, 30, 34 | **32** | 旧提案(24/40)から変更。28はタイ↑ |
| 36, 50, 56 | 40 | 36はタイ↑ |

確定モック（reading/settings 18トークン）との突合＝全一致を確認済み。

## コミット列（各コミットで gates green・人間承認後にコミット）

1. **C1 `docs:`** ADR0014 §C改訂（7段）＋適用裁定追記（Why・丸め則・Insets除外・棄却案=厳格5段/full4px刻み）＋handover①消し
2. **C2 `feat:`** `theme/Spacing.kt`（`Spacing.S4..S40` 数値名＋`object Insets`）＋handover②消し
3. **C3 `refactor:`** 正本モック離散化（STANDARD+SHELF+reading-D の実対象・ハーネスマーカー `/*==harness==*/` 規約導入）＝**agy委譲**。検証=C4のdev版lint(a) green＋diff全量＋既存check PASS。handover④消し
4. **C4 `feat:`** `check_design_tokens.py` Spacing lint 2フェーズ（(a)正本モックpx集合membership・ハーネス区間除外／(b)ui/ .dpリテラル→Spacing/Insets参照・**grace list**=未翻訳Composeファイル明示）＝**agy委譲**（開発はC3と並行・コミットはC3後）。較正=NG集合が悉皆調査と整合。handover③消し
5. **C5 `refactor:`** DiscoverySearchScreen 再翻訳＝**Claude自作**（委譲統治の「監督自作の正本1画面」を兼ねる）
6. **C6/C7 `refactor:`** BookshelfScreen／ChapterContent 再翻訳＝**agy委譲**（C5 diffを見本に）。各コミットで grace list から除去
   - 残り約15 Composeファイルは grace list に負債として顕在化→handoverへ追記（本便スコープ外）

## ゲート

`cd android && ./gradlew testDebugUnitTest`（gw 相当・ext4 in-tree）＋ `python3 tools/check_design_tokens.py`。
視覚変更の実機目視は各画面コミット前にユーザーへ諮る（memory feedback-ask-before-device-testing）。
