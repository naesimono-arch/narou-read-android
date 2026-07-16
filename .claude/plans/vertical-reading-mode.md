# 縦書きリーディングモード 実装プラン

- **対象ブランチ: `reading/vertical-mode`**（worktree `/home/qingj/wt/reading-vertical-mode`）
- 方針の正本: ADR 0020（連続横スクロール × 自前Compose組版＝つなぎ・品質妥協なし）
- 起草: 2026-07-16（プランセッション＝pdf/parser-rules-relative 側で外部調査・触感モック・Plan設計を実施し合意）

## 確定済みの裁定（再議論しない）

| 論点 | 裁定 |
|---|---|
| 骨格 | A＝右→左 連続横スクロール（触感モックでユーザー選択） |
| 描画 | 自前Compose組版。`VerticalTypesetter` interface で隔離（将来 text-vertical へ差替可能に） |
| 縦中横 | **v1必須**（ユーザー裁定。スパイクは頻度測定＝テストコーパス作りのため） |
| 切替時位置 | 同じ段落を維持。正本＝(段落index, 段落内fraction 0..1)。同一モード内は現行(scrollIndex/scrollOffset)同型 |
| 設定 | 全書籍共通。`app_prefs` に `reading_vertical`（Boolean）＋ReadingSettingsSheet にトグル |
| 章送り | 縦書き時は横ドラッグ無効化→終端オーバースクロール（nestedScroll捕捉→ChapterPeekPanel/settleSwipe流用） |
| 文字クラス層 | 共有部品化。第2消費者＝`ShioriCover.drawShioriTitle`（本棚題字の（）～ー正立崩れを同部品で修正） |
| vert不安定への備え | CharClassifier 主導の純フォールバック経路（正立/回転/位置替えを自前オフセットで）を最初から用意 |

## レイヤ構成

```
[純Kotlin 組版ドメイン層＝JVMテスト対象]  新パッケージ typeset/
  CharClass(enum) + CharClassifier   … UPRIGHT/ROTATE/PUNCT_REPOSITION/TATE_CHU_YOKO/UNKNOWN→UPRIGHT
  FontMetricsProvider(interface)     … 実装=Paintラッパ／テスト=等幅フェイク
  LineBreaker                        … 行頭・行末禁則（まず追い込み、ぶら下げは後続）
  RubyPlacer                         … 縦書き＝列の右側。splitRubyReading(既存純関数)流用
  VerticalTypesetter(interface) + DefaultVerticalTypesetter → ParagraphLayout(純データ)
[Android描画層]
  GlyphRenderer(interface)           … Paint.fontFeatureSettings="vert" 第一候補＋Canvas回転フォールバック
                                        「どの文字を回すか」は純層が決定・描画層は実行のみ
  VerticalParagraph @Composable      … ParagraphLayout を Canvas に描くだけの薄い葉
[Compose統合層]
  VerticalChapterContent             … LazyRow(reverseLayout=true)・段落=1アイテム・先頭にヘッダアイテム
                                        （現行 ChapterContent と index 体系を一致させ位置保存を同型維持）
```

- 段落チャンク方式の成立根拠: `splitIntoParagraphs`（ui/ChapterContent.kt:329）は LineBreak 境界で切る＝段落は独立組版可能。アイテム幅＝列数×列送り。
- 横書き資産（ChapterContent/RubyText）は温存し**並置**。ChapterScreen 内で本文スロットのみ分岐。

## フェーズ（1論理変更=1コミット。各フェーズ完了でPushNotification→目視OK→コミット）

- **P0 スパイク — ✅完了（2026-07-17）**: 全実測結果＝`vertical-mode-p0-measurements-2026-07-17.md`（CharClassifier初期表の正本）。以下は当初の実施項目:
  1. `fontFeatureSettings="vert"` の実効性を実機で確認（句読点・括弧の縦字形が出るか）→ 効かない文字の実測リストで CharClassifier 表を確定【最重要】
  2. 縦中横対象（半角数字連・!?系）の実データ頻度を蔵書HTMLで計測（v1必須は確定済み＝目的はテストコーパス抽出）
  3. 最長級の章で LazyRow 巨大段落アイテムの measure コスト実測（閾値超なら列窓サブチャンク化を検討）
  4. LazyRow(reverseLayout) の firstVisibleItemScrollOffset 符号・原点の実挙動確認
- **P1 純組版ドメイン層**＋JVMテスト一式。完了定義: UIなしで `gw testDebugUnitTest` 緑（文字クラス網羅・禁則・縦中横・ルビ按分・位置変換の往復）。
- **P2 GlyphRenderer＋VerticalParagraph**。完了定義: Robolectric＋Roborazzi で単一段落（正立/回転/句読点/縦中横/ルビ）golden 化。
- **P2.5 本棚題字へ適用**: `drawShioriTitle` を CharClassifier/GlyphRenderer 経由に置換（実データのタイトルで文字クラス層を実戦検証する先行消費者）。完了定義: 実機で「（）」「～」「ー」入りタイトルの書影が自然。
- **P3 VerticalChapterContent 配線**（LazyRow・位置保存を既存(index,offset)同型で接続）。完了定義: 縦書き章が連続横スクロールし同一モード内で位置復元。
- **P4 ジェスチャ再配線**（横ドラッグ無効化＋終端オーバースクロール章送り。overscroll バウンドは無効化し未消費デルタを章送りへ）。完了定義: 次章/前章プレビュー→確定が動作し本文スクロールと非衝突。
- **P5 設定配線＋切替**（reading_vertical prefs・シートにトグル・(段落index,fraction) 相互変換）。完了定義: 切替後も同じ段落へ復帰。
- **P6 仕上げ**: 禁則ぶら下げ・OPPO実機較正・TTS/a11y再現の確認（`/device-verify` 経由）。完了定義: 実機で版面OK＋a11y退行なし。
- （任意 P7）切替跨ぎの再起動復元を厳密化する場合のみ ProgressEntity へ paragraphIndex/fraction 追加（`/db-migration` ゲート必須）。

## リスク（監督が張るべき場所）

- **最難所＝ルビ縦書き**: 現行 RubyText は折り返し/禁則を Compose に委譲しルビだけ overlay。縦書きは全部自前＝実質フルスクラッチ。a11y の肝（`clearAndSetSemantics`＋当て字の著者読み置換＝RubyText.kt:132-133,238-248）を縦書き経路でも必ず再現。列の読み上げ順（右→左・列内上→下）を semantics で明示。
- **読了検出**（NativeReadingScreen.kt:858-887）: 「末尾＝左端」への読み替えを誤ると読了が立たない/誤発火。
- **OPPO/ColorOS フォント差**: vert の効き・Serif 実体が割れる → 純フォールバック経路を厚めに。
- fraction 近似ゆえ切替で数行の誤差は仕様（厳密化はP7）。

## 参照

- 主要ファイル: ui/ChapterContent.kt・ui/compose/RubyText.kt・ui/compose/RubyLayoutHelper.kt・ui/NativeReadingScreen.kt・ui/ReadingSettingsSheet.kt・ui/components/ShioriCover.kt(:1721 drawShioriTitle)・data/ProgressEntity.kt・model/ChapterContent.kt
- 触感モック: docs/design-candidates/reading-vertical-scroll-D.html（採用A）／reading-vertical-paged-D.html（不採用B・記録）
- UI変更ゲート: `python3 tools/check_design_tokens.py`／スクショは ScreenshotTestSupport（Roborazzi・WSL記録が正）
