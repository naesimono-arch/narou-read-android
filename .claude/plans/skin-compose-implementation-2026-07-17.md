# スキン Compose 実装（M 星図・P カートリッジ・J ポータル）— 別セッション実行プラン

> **対象ブランチ: `ui/skin-framework`**（worktree `/home/qingj/wt/ui-skin-framework`・ext4＝素の `gw testDebugUnitTest` が通る）
> 策定: 2026-07-17 モック確定セッション。前提裁定=ADR 0021＋同追記（2026-07-17）。モック正本=`docs/design-candidates/skins/`（CURRENT.md 索引）。

## ゴール

実装対象3スキン（M/P/J）を実機で装着・全画面（本棚/読書/発見/目次/設定）で動く状態にし、人間目視の関門を通す。
モックが正本＝Compose は翻訳（/visual-language）。**モックとの1px単位の照合ではなく、思想・署名・トークン・機能全数の忠実な翻訳**が合格条件。

## 前提の設計判断（実装前に最初にやる・ここだけは監督=Claude 本体の判断工程）

1. **機構の二層化**: 現行 `SkinTokens`（色トークン束）に加え、**画面構造の切替**が要る（M/P/J は本棚〜各画面の構造ごと別物）。
   設計案: `SkinTokens` に画面ファクトリを足すのではなく、**画面単位の when(skin) 分岐で SkinX 用 Composable へ委譲**する薄い層
   （`BookshelfScreen(skin)` → `when(skin){ D→既存, M→BookshelfM(), … }`）から始め、共通化は3スキン実装後に抽出する
   （早すぎる抽象化の禁止。分岐先が無い enum 値を先出ししない=ADR 0021 却下案の原則を踏襲）。
2. **テーマ変種**: M=固定1変種（夜の相・設定のテーマ節は畳む=C と同型）／P・J=3テーマ（モックに3面あり）。`supportedThemes` で表現。
3. **モーションの再裁定**: モックに入れた演出のうち、①M の脈動星（進行類型の例外として仮承認・ADR0014 静謐則と要突合）
   ②Q 由来ではないが M toc の星座点火・P の演出——を実装時に個別裁定（reduce-motion 対応は全て必須）。

## フェーズ（機械バッチ／判断ループの二分）

### C1: トークン層（機械バッチ寄り）
- モック CSS 変数 → `Color.kt` へ M/P/J の系統別 val（1:1 対応表を委譲仕様書に全数列挙・較正値クラスの規律=memory `agy-mechanical-batch-calibrated-values` 準拠）
- `SkinM/SkinP/SkinJ.kt`（SkinTokens 実装）＋ enum `Skin` へ値追加（**実装と同一コミット**）
- `tools/check_design_tokens.py` のスキン別期待表へ M/P/J 行を追加
- ゲート: `gw testDebugUnitTest` 緑＋`check_design_tokens.py` OK

### C2: 画面構造（判断ループ・スキン×画面ごと）
- 順序: **本棚→読書→目次→設定→発見**（本棚が署名の中心・発見が最重）× スキンは M→P→J（M が最も canvas 依存＝Compose Canvas 翻訳の試金石）
- 各画面: モック読解→Compose 翻訳→JVM テスト→**PushNotification→人間目視 OK→コミット**（1画面=1コミット目安）
- 委譲統治: UI 翻訳の委譲は3点セット〈モック現物・監督自作の Compose 正本1画面・厳密シグネチャ＋色対応表〉が揃ってから（/visual-language）。**監督が最初の1画面（bookshelf-M）を自分で書く工程は省けない**
- サブエージェントのコンテキストが 15万トークン級に達したら交代（今回の実測知見: 30万超で品質劣化）

### C3: 装いの間との接続＋実機総合
- wardrobe カルーセルへ M/P/J カードを追加（`"app_skin"` 永続化は既存機構）
- 全画面スモーク（スキン×画面×テーマ）→ 実機（**投入前に一度ユーザー確認**=memory `feedback-ask-before-device-testing`）
- 人間目視の関門: 「これが星図/カートリッジ/ポータルだ」と見えること（C 夜行の教訓=色層だけでは伝わらない）

## 残タスク・保留（このプランのスコープ外）

- Q 読書の庭（差し戻し保留・モックは candidates/ に保全）／C 夜行の構造・演出層（handover A2 の既存項目）／L/N/O/R/S・hatchake 試作

## 検証ゲート（全フェーズ共通）

`cd android && gw testDebugUnitTest`＋`python3 tools/check_design_tokens.py`＋worktree 冒頭1回 `gw :app:lintDebug`（基準 0 errors/28 warnings）。
実行見込みが長い＝fresh セッション・約10ターン超で区切る（CLAUDE.md）。

## 実行起動ブロック

```bash
cd /home/qingj/wt/ui-skin-framework && claude
# 初手: このプランを読む → git branch --show-current が ui/skin-framework を確認 → 「前提の設計判断」から着手
```
