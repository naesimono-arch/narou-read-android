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

---

## 実行状態（2026-07-17 実行セッション1 終了時点・全て未コミット＝git status が正）

> 体制: Claude=司令塔（設計裁定・監督自作・diff 全量レビュー・ゲート自走）／委譲=Claude サブエージェント
> （agy 不使用=memory 準拠）。委譲仕様書は scratchpad（セッション消滅・判断内容は ADR 0022 とコード内コメントへ収蔵済み）。

### 完了（作業ツリー上・ゲート現在値: testDebugUnitTest **549件緑**・checker OK=184/NG=0・spacing NG=0）

1. **前提の設計判断 → ADR 0022 新設**（`docs/decisions/0022-skin-structural-layer.md`＋README索引）:
   二層化=画面入口の薄い when(skin) ルーター（本棚/目次/発見ホーム・結果=画面丸ごと・読書/設定=共通骨格＋部品）／
   supportedThemes: M=[DARK]・J=[DARK,LIGHT,SEPIA]（読書のみ変種）・**P=[LIGHT]開始**（プラン前提「P=3面あり」は
   事実誤認と確定＝モックに変種実体なし）／「現在地の脈動」類型承認（reduce 静止必須）／食い違い値は家系分離。
2. **C1 トークン層**: Color.kt に Seizu/Cartridge/Portal 3節（全 val モック由来＋焼き込み算式コメント）・
   SkinM/P/J.kt・enum 3値・checker M/P/J 行・SkinMPJTest 8件。LocalSkin（enum の CompositionLocal）新設。
3. **C2: M 星図 全5画面完了**:
   - 本棚 `ui/skins/m/BookshelfSkyM.kt`（監督自作・1作=1星座セル・hero 脈動星・星図⇄一覧トグル pref `m_sky_view`・
     一覧=D構造フォールバックが選択削除/Webカード操作を担保・⋮メニューのテーマ節畳み漏れも是正）
   - 読書 `ReadingChromeM.kt`（監督自作・上端結線進捗・章扉=星座片＋漢数字話数・シーン区切り・星屑地・ゴースト題字。
     本文エンジン無改変=既定引数のみ）
   - 目次 `TocSkyM.kt`＋共通 `SkyCanvas.kt`（委譲→検収済。現在章ドットのみ脈動=一画面一強調の監督裁定）
   - 設定 ReadingSettingsSheet の M 部品分岐（監督自作・観測パネルグラデ・テーマ固定表示行・星のつまみ/結線トラック）
   - 発見 `DiscoveryHomeSkyM.kt`（委譲→検収済。ホーム＋結果一覧・モック省略の D 機能は全数 M 意匠へ移植）
4. **P テーマ3変種の追補ドラフト**: `docs/design-candidates/skins/candidates/reading-P-themes-draft.html`
   （SEPIA/DARK 地色=settings-P スウォッチ実値昇格・WCAG 実算済み・承認後に reading-P 正本統合→SkinP 3テーマ化）。

### 人間の関門（3件・ここで停止中）

1. **コミット承認**: 提示順=①docs: ADR 0022 ②feat: C1 ③feat: C2-M 各画面（分割・台帳更新同梱）
2. **P テーマ3変種ドラフトの目視**（mockview 済み）→ 採否
3. **実機投入の許可**（M 全5画面を一括目視可能。adb-bridge→install -r の手前で停止中）

### 再開手順（fresh セッション）

1. このプランを読む→`git branch --show-current`=ui/skin-framework→`git status` で未コミット全量を確認
2. 人間の関門3件の裁定をもらう→コミット（1論理変更=1コミット・各コミット前に提示）
3. M の実機目視フィードバックを較正に **C2 P→J**（委譲パターンは toc/discovery と同型: 仕様書=〈モック現物・
   ADR 0022・BookshelfSkyM 等の正本実装・厳密写像表〉→委譲→diff 全量レビュー＋ゲート自走。J は wardrobe カード
   文言・P は 3テーマ化の裁定を織り込む）→ C3（装いの間 M/P/J カード=enum で自動反映済みのため実質は実機総合）

---

## 実行状態（2026-07-17 実行セッション2＝完遂セッション。上の「セッション1」節は履歴）

> 体制: Claude=司令塔／委譲=Claude サブエージェント並列（同一ツリー・所有権分割。較正＝memory `workflow-parallel-subagent-shared-tree`）。
> **完了の正本は git log（本ブランチ約30コミット）**。ここは現在地ポインタのみ。

### 完了（全て landing・コミット済み）

- **C2 完遂**: M/P/J 3スキン×全5画面。P=ラック/カートリッジクローム/ステージセレクト/システムメニュー/レトロゲームショップ、J=ポータルデッキ/読書クローム/廊下の道程/扉プレビュー設定/扉の回廊。
- **Pテーマ3変種**（LIGHT/SEPIA/DARK・reading-P 正本統合）／**M深空リッチ化 R1**（正本統合＋DeepSkyM 翻訳＋実機後詰め=星雲/暗黒帯）。
- **遊び心6点確定・正本統合・Compose追従**: P1 CLEAR‼（押印アニメ=骨格配線TODO）・P2 現像・P3 炎（streakデータ源なし=非表示）・J1 開く扉・J2 敷居光（canScrollForward 配線済み）・J3 時刻大気。
- **実機検証由来の改善**: J扉ambientパレット化（Amb*Portal 17トークン）・装いの間snap修正・M本棚下辺重なり修正。
- 実機目視: M全画面・P全画面・J本棚＝PASS（検証員レポ2巡）。改善案は handover「改善案バックログ」へ全収蔵。

### 残（再開時はここから）

1. **C3 最終総合スモーク＝実機一時停止中（ユーザー指示）**。再開したら: 最新APKは投入済み・検証項目=〈J読書/目次/設定/発見の初実機・J ambient色相の飛び判定・遊び心全点・M後詰め確認・装いの間snap体感〉（このセッションで中断した C3 エージェントのブリーフが仕様）。
2. 人間目視の関門（3スキンの署名が伝わるか）→ 要ユーザー裁定5件（handover「実機検証2026-07-17の改善案・要判断」）。
3. handover 改善案バックログの消化（任意・優先度はユーザーと相談）。
