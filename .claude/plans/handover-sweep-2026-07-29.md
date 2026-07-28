# handover 全消化スイープ（2026-07-29・/orchestration + /loop）

> **対象ブランチ: `release/play-prep`**（worktree `/home/qingj/wt/release-play-prep`）
> 実機: OPPO PGEM10 `192.168.2.47:5555`（ユーザー宣言「実機接続済み」＝この便の実機作業は事前承認済みと解釈）
> ユーザー指示: 「handoverをすべて処理しよう」＝自律処理できるものは実行・裁定/目視必須のものは材料を揃えて最終報告で一覧提示。

## トリアージ

### 自律処理する（バッチ順）
- **B1**: [読書]下部バー背景の navigation bar 塗り残し＋[目次]最終行ジェスチャーバー重なり（inset バグ2件・handover L215-216）→ Agent A
- **B2**: プライバシーポリシー下書き＋Data safety 申告下書き＋versionCode/versionName 採番規約 ADR 提案（handover Play節）→ Agent B（doc のみ・並走可）
- **B3**: In-App Review API 実装（読了/章送り満足直後・カスタムUI無しのためモック不要）→ B1 の Gradle 完了後に直列
- **B4**: 実機検証バッチ（/device-verify ゲート経由）: ①discovery pop ③是正の (a)-(d) チェックリスト ②B1 修正の機能確認（背景到達・最終行 padding）③popToTab 一般化の回帰
- **B5**: [読書]表示設定シート半透明ライブプレビューのモック起案（/visual-language 経由・全候補モック化→裁定材料まで）
- **余力あれば**: 残④ macrobenchmark タブスワイプ/遷移シナリオ・残③ perfetto さがす初回コンポーズ実名特定

### ユーザー待ち（触らない・最終報告に列挙）
- 鍵/local.properties バックアップ（ユーザー作業）／applicationId・ストア素材（ブランド名確定待ち）
- K実機目視・各種 要裁定 items（縦書きタイトル重なり(a)-(d)案・Result スクロールリセット・矢印方向 等）
- 「直行入場 Back 2段」の体感評価（数日運用中）

## 進行記録
- 2026-07-29: スイープ開始。B1(Agent A)+B2(Agent B) 並行起動。
