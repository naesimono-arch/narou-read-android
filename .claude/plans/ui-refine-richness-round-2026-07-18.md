# リッチ化横展開＋UIデザインコード見直しラウンド（2026-07-18）

**対象ブランチ: `ui/refine`**（worktree `/home/qingj/wt/ui-refine`・コミットは本 worktree 内セッションから）

## 目的（ユーザー指示）

1. **リッチ化の横展開**（handover「思いつき・取りこぼし」節）: 深空リッチ化は本棚Mのみ→
   - M目次/発見（星空系）への展開
   - P（カートリッジ物性の質感リッチ化）
   - J（ポータル発光層）の「R1級」磨き込み
2. **各デザインUIのコード見直し**: UX 知識ベース（`/mnt/c/Users/qingj/Desktop/project/UX`）を参照しつつ、
   視認性・導線・視覚階層・a11y の観点でスキンコードを点検・修正
3. **実機目視検分**: OPPO PGEM10 接続済み（ユーザー明示 GO あり）。スクショ掃引→検分→修正ループ

## 規律

- 意匠の正本＝`docs/design-candidates/skins/*-{M,P,J}.html`（HTMLモック正本→Compose 翻訳。自己判断禁止）
- ゲート: `testDebugUnitTest`（基準752件全緑）・`check_design_tokens.py`（192/0）・`:app:lintDebug`（0 err/31 warn）
- 見た目を変えるコミットは `recordRoborazziDebug` golden 再記録を同梱
- サブエージェント積極活用・監督は Claude 本体（agy は使わない＝memory `feedback-avoid-agy-low-trust`）
- 実機ステップは PushNotification→目視OK→コミットの粒度

## 工程（2026-07-18 ユーザー裁定: リッチ化は検分完了後）

- [x] Phase 0: lint スイープ（0 errors/31 warnings＝基準どおり）・スキンコード構造把握・R1（本棚M深空）の型抽出済み
- [x] Phase 1: UX 資料からスキン検分チェックリスト蒸留（52項目＝scratchpad/skin-inspection-checklist.md）
- [x] Phase 2: 実機掃引29枚＋検分4体（M/P/J単面＋横並び一貫性）完了。主要所見:
  - [高] P設定シート＝未選択テーマボタンのディザ減光が可読性違反／J本棚＝表示モードトグルの意味論反転（デッキ化・アイコン隠喩も割れ）
  - [中] M読書クローム章題の沈みすぎ・M本棚ヒーローで「続きを結ぶ」に発見カード被り・M装い入口が✦で隠喩割れ・M設定にテーマ選択なし・D本棚「話」数の意味反転（総話数vs現在話）
  - [中・コード] BookshelfSkyM.drawSkyCell が毎フレーム全セル再構築（pulse の invalidate 範囲過剰）／没入型スキンの画面ルーター約30分岐が無音Dフォールバック（exhaustive when 化を推奨）
  - [低] M目次/発見の draw 内再生成・J章末 breathe の composition 読み・PixelFamily×5複製・MoodPackagePalette 再宣言・GlyphInkToc デッド・M構造色の置き場二分
  - グレースケール検分は全スキン合格（×なし）。読書クローム4スキン一致は模範
- [x] Phase 3 実装完了（2026-07-18・ゲート全緑=752/0・トークン192/0・lint 0/31。実機スモークはユーザー実機使用中で中断→ユーザー手元確認に切替）: 3a/3b の自律修正分＝ルーター when 化（5ファイル・読書/設定シートは加算的クローム構造のため対象外と裁定）・drawWithCache 隔離・M セル内容追従高・P設定シート墨字固定・M目次/発見 remember 化・J breathe provider 化・CartridgeParts 集約・GlyphInkToc 除去。D話数は突合員の「翻訳逸脱」判定を監督検算で棄却（mokuroku-D は総話数解釈で整合）＝コード無変更・裁定リストへ。裁定リストは handover「要裁定・2026-07-18 スキン検分ラウンド」節が正本。
- [ ] Phase 3 残: コミット（計画提示→承認待ち）・実機スモーク残（M目次/発見・P目次/発見・J章末の等価確認＝ユーザー実機返却後）
  - 3a（自律・見た目不変）: ルーター exhaustive when 化／drawSkyCell remember 隔離／低優先作法バッチ（remember化・breathe provider化・PixelFamily集約・同値パレット参照化・GlyphInkToc除去）→ゲート→コミット承認へ
  - 3b（モック突合）: P設定ディザ・M章題・表示設定金色・M被り・Jトグル・M✦・M設定テーマ・D話数を正本HTMLと突合→「翻訳逸脱=自律修正」「正本由来=ユーザー裁定リスト」へ二分
  - 3c（ユーザー裁定リスト）: 3b の正本由来分＋J敷居光 breathe の ADR0022 静止裁定との整合
- [ ] Phase 4: **リッチ化横展開（検分完了後に着手＝ユーザー指示）**: モック案生成→mockview 提示→裁定→Compose 翻訳
  - 起動途中で停止したモック生成3体の委譲仕様は本セッション履歴にあり・検分知見を織り込んで再発注する
- [ ] Phase 5: ゲート→実機再検分→コミット（1論理変更=1コミット・都度承認）
