# UIスキン機構（着せ替え骨格）— 実装プラン

> **対象ブランチ: `ui/skin-framework`**（worktree `/home/qingj/wt/ui-skin-framework`・ext4＝素の `gw testDebugUnitTest` が通る）
> 策定: 2026-07-17 主セッション（骨格裁定はユーザー3択で確定）。関連: ADR 0005 §C（スキン将来送りの解除）・handover A2。

## Context（なぜ）

フェーズ0で作った A〜J の10案スキン資産（claude.ai/design `Novel Reader UI`・projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93` の `ui-n-phase0/`）を、実際に切り替えられる機構として実装する。2026-06-27 の「まだ実装しない」判断（ADR 0005 §C）をユーザー指示（2026-07-17）で解除。**このブランチの主目的は「スキン機構の骨格」**＝Skin 抽象・選択UI・初弾1スキン（C 夜行）で、10案全部の移植ではない。

## ユーザー裁定（2026-07-17・3択で確定）

1. **スキン×読書テーマ = 各スキン1変種で開始**。D のみ3テーマ（ライト/セピア/ダーク）を持ち、C 夜行は固定1変種。将来変種を足せる構造にはしておく。
2. **適用範囲 = アプリ全体**（本棚・発見系・目次・設定・読書画面まで全部スキン準拠）。
3. **初弾スキン = C 夜行**（深炭×温白の没入。bookshelf-C / reading-C モックが既存）。
4. （提案承認扱い）**I 読書の旅路・J ポータルデッキはレイアウト構造ごと別物＝トークン着せ替えの枠外**。本機構の対象外と ADR に明記。

## 現状の要点（2026-07-17 探索済み・再調査不要）

- テーマは `enum ReadingTheme { LIGHT, SEPIA, DARK }`（`theme/Theme.kt:26`）単一軸。色は `Light/Sepia/DarkColorScheme`（`Theme.kt:177/243/208`）＋読書 `ReadingColors`（`Theme.kt:28`・`ReadingTheme.colors` getter の when 分岐）＋本棚 `ShelfColors`/`LocalShelfColors`（`Theme.kt:164-170`）に **when(theme) ハードコード**。
- 状態の正本＝`MainActivity.kt:116` の `appTheme`、永続化＝SharedPreferences `app_prefs` の `"reading_theme"`（enum name・キー削除=システム追従・`SETTINGS_SCHEMA_VERSION` 仕組みあり `MainActivity.kt:162-166`）。
- 設定UI＝`ReadingSettingsSheet.kt:140-186` の FilterChip＋FlowRow＋コールバック委譲（独立 SettingsScreen は無い）。
- 栞書影＝`ShioriCover.kt`。テーマ判定が `surface.luminance()<0.5` と `surface==BackgroundSepia` の**推定**（`:1666`・`shioriAccentFor :1636`）。栞は先端 tip 1軸のみ（栞「型」A箔/C小口/D蔵書印/E綴じ紐は未実装）。
- `tools/check_design_tokens.py` は **3値/3宣言ハードコード前提**（`parse_reading_colors :71` の `LIGHT|SEPIA|DARK`・`READING_ORDER`・reading-D の宣言数==3 で NG `:239`）。
- ADR 0014 禁止則「色は素地・墨・藍＋青磁で閉じる・装飾新色は ADR 改訂を要する」→ **スキン機構は 0014 改訂＋新 ADR が前提**。

## 骨格設計

### Skin 抽象（`theme/skins/`・1スキン=1ファイル）

```kotlin
// theme/Skin.kt（選択軸の enum。永続化は .name）
enum class Skin { WAMODERN_D, YAKO_C }

// 各スキンが提供するトークン束（interface か sealed。実装は SkinD.kt / SkinC.kt）
interface SkinTokens {
    val supportedThemes: List<ReadingTheme>   // D=3種・C=listOf(DARK 相当の固定1種)
    fun material(theme: ReadingTheme): ColorScheme
    fun reading(theme: ReadingTheme): ReadingColors
    fun shelf(theme: ReadingTheme): ShelfColors
    val typography: Typography                // 初弾は両スキンとも NovelReaderTypography 共有
    // 栞「型」スロットは切るだけ（実装は将来送り。初弾の栞は accent/paper 追従のみ）
}
```

- `ReadingTheme` は「スキン内の変種軸」へ降格（enum 自体・永続キー `"reading_theme"` は不変＝後方互換）。
- `NovelReaderTheme(skin, theme, content)` 化。**ShioriCover の luminance/BackgroundSepia 推定は根絶**し、Skin から paper/ink/accent 系を明示供給（推定は D 前提の暗黙結合＝スキン導入で必ず壊れる）。
- Motion・Spacing はスキン共有（原則5「静謐は機能」・禁止則の余白スケールは全スキン不変）。

### 状態・永続化

- `MainActivity` に `appSkin` state 追加。SharedPreferences 新キー `"app_skin"`（enum name・キー不在=D 既定）。
- C 選択中: テーマ3択チップ＋システム追従は**畳む/無効**（1変種スキンでは無意味）。`"reading_theme"` は温存し D 復帰時に復元。
- ダークシステムUI連携（status bar 等）は Skin の変種が暗色かで判定（luminance 推定の是正と同じ経路）。

### モック・検査・ADR の統治

- **新 ADR「UIスキン機構」**: スキン=トークン束（構造は共通・I/J 枠外）／各スキン1変種開始／スキン固有モックは本棚＋読書の2画面（署名検証用）で、他画面（発見・目次・設定）は **D 構造モックへスキントークンを写像**する規約。
- **ADR 0014 改訂**: 禁止則「色は藍＋青磁で閉じる」→「**スキンごとに閉じたパレット規範**を持つ（D=藍/青磁・C=深炭/温白系）。スキン内での装飾新色追加は当該スキンのパレット改訂を要する」へ再定義。
- **bookshelf-C.html / reading-C.html を DesignSync `get_file` で取得し `docs/design-candidates/skins/` へ収蔵**（正本化。claude.ai 側は収蔵コピーへ降格＝ADR 0005 の既定運用に合わせる）。⚠️ DesignSync は主セッション限定＝サブエージェントへ委譲不可。
- `check_design_tokens.py`: 3値前提を「スキン別期待表」へ一般化（D の既存検査は同値維持が合格条件・C 用の期待表を追加）。

## フェーズ（機械バッチ／判断ループの二分）

### P1: 挙動不変リファクタ（機械バッチ寄り・判断は設計済み）
Skin 抽象導入・D 値の `SkinD.kt` 移設・`NovelReaderTheme(skin=D固定)` 配線・ShioriCover 推定の明示化・check_design_tokens.py スキン対応。**合格条件: `gw testDebugUnitTest` 508件緑＋`check_design_tokens.py` OK=137/NG=0 と同値＋実機で見た目1px不変（スクリーンショット比較）**。ここまでで1コミット群。

### P2: 選択UI＋永続化（判断ループ＝モック先行）
settings-D モックに「装い（スキン選択）」節を追加した改版を**先に HTML で作り mockview で見せる**（選択肢=D/C・プレビュー付きカード等・意匠は自己判断せずモックループで確定）→ 承認後 Compose 翻訳・`"app_skin"` 永続化・C選択中のテーマUI畳み。実機確認→コミット。

### P3: C 夜行の実装（判断ループ）
モック収蔵（主セッションで get_file）→ C トークン表を起こす（Color.kt へ C 系 val 追加・モック CSS 変数と1:1）→ `SkinC.kt` 実装 → 全画面スモーク（本棚・発見・目次・設定・読書・没入・栞書影）→ 実機確認（PushNotification→目視OK）→コミット。
**C の「本棚=続きからヒーロー」等の構造要素はトークン枠外＝今回は D 構造のまま C の色/質感のみ**（ADR に明記）。

### 明示的に将来送り（handover A2 を更新）
- 栞「型」軸（A箔/C小口/D蔵書印/E綴じ紐）＝スロットのみ。
- D 以外のテーマ変種・E〜J スキンの移植・I/J の構造スキン。

## 検証（ゲート）

- 各フェーズ末: `cd android && gw testDebugUnitTest`（ext4＝init-script 不要）＋ `python3 tools/check_design_tokens.py`＋worktree 冒頭1回 `gw :app:lintDebug`（基準 0 errors/28 warnings）。
- P1 は見た目不変が本質＝実機 or スクショで D の3テーマが従前どおりであること。
- P2/P3 は実機目視（**実機投入前に一度ユーザーへ確認**＝memory `feedback-ask-before-device-testing`）。
- 台帳: STATUS「進行中ブランチ」へ本ブランチ追記・handover A2 の書き換えは、対応する論理変更コミットに同梱。

## 実行起動ブロック

```bash
cd /home/qingj/wt/ui-skin-framework && claude
# 初手: このプランを読む → git branch --show-current が ui/skin-framework であること確認 → P1 から着手
```
