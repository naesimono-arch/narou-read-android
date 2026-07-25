---
name: ai-mock
description: AI裁定モック作成の流れ。監督（Claude）自らモック・実機を目視裁定して UI を設計→モック→実装→検分まで回す型（明快Kラウンド 2026-07-23 確立）。呼出時の追加指示が常に本スキルより優先される「土台」として使う。
when_to_use: 「AI裁定でモックを作って」／「/ai-mock」／「自分で目視して UI を作って」／「監督裁定でモックから実装まで」 などの依頼で使う。
---

# /ai-mock — AI裁定モック作成

> 優先順位: 呼出時のユーザー指示 ＞ 本スキル ＞ 平時の規範。型であって拘束ではない（明示指示で差替え・省略可）。
> 平時の「監督は目視しない」（memory `feedback-orchestrate-dont-inspect-visuals`）は本スキルの明示発動で解除。

## 流れ（1ラウンド）

1. **接地**: `/visual-language` ゲート → UX正本（`/mnt/c/Users/qingj/Desktop/project/UX`）→ 現行実装の機能・導線棚卸し（委譲可）。
   競合の実機目視＝`adb shell monkey -p <pkg> … 1` 起動 → `adb exec-out screencap -p` → Read で自分の眼で見る。
   **裁定: 課題は「装飾」か「構造」か**——真因が構造なら回答も構造で（色いじりでは直らない）。
2. **確定事項を書く**: plan（`.claude/plans/`・冒頭に対象ブランチ）へ根拠つきで明文化。書かない委譲は逸脱の温床。
3. **モック生成（委譲）**: 下書きは `docs/design-candidates/**/candidates/<name>-draft/`（正本直書きしない）。
   小粒（既存画面への小さな追加）は**正本直差分方式**（/visual-language・新規ファイルを起こさない）。
   並列複数体の共通部品（ナビ等）は仕様にマークアップをインライン固定で体間の揺れを防ぐ。
   **プレースホルダは実データの色域を模す**（例: 栞書影の紙=地色同値。濃色ダミー板は一体化バグを素通しする）。
4. **監督目視①（モック）**: headless Chrome スクショ→Read で裁定（手順と2つの罠＝memory
   `workflow-supervisor-headless-visual-judgment`）。軽微是正は監督直・合格後に正本へ昇格。
5. **実装**: 基盤・統合点は監督自作、画面構造は委譲（`/orchestration` の規律・Gradle ゲートは監督一括）。
6. **監督目視②（実機）**: install -r → screencap → Read。**二段検分は役割別**——モック=構図の裁定・
   実機=実データ衝突の検出（縦題字×⋮衝突・地色同値の一体化はモックでは原理的に見えない）。
   検分で触ったユーザー状態（テーマ・スキン prefs）は原状復元してから返す。
7. **締め**: コード先行の視覚差分はモックへ逆同期 or「未反映」注記（恒久ルール）。**採用/棄却が確定した
   候補モック（candidates/・draft/）は削除**（git履歴が保管庫＝一時ファイル規約。保全指定・生きた宿題のみ残す）。
   台帳（plan/STATUS/handover）→ ユーザー目視依頼 → 承認後コミット。

## 参照

- 実例一式: `.claude/plans/default-ui-clarity-K-2026-07-23.md`（接地→確定→委譲→裁定→是正の記録）
- 正本の層構造・機械検査: `/visual-language`／委譲規律: `/orchestration`／実機の作法: `/device-verify`（adb-bridge・破壊フロー禁忌）
