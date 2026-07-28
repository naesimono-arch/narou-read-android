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
- B2 完了→検分→コミット `9f84228`（Play ドキュメント3点＋handover 更新）。裁定残: Auto Backup「収集なし」解釈・ADR 0025 採否・プレースホルダ4点・ホスティング先。
- B1 完了（真因: 下部バー=alpha0.95 が機序〔WebView 期持ち越し〕・目次=K/P のみ navigationBars 未処理。D/C/M/J は処理済み実測確認）。監督ゲート再実行 GREEN（943件）。**コミットは B4 の実機機能確認 PASS 後**（handover L215-216 の行削除と同梱）。
- B5（表示設定ライブプレビュー候補モック）起動・走行中。
- B4（実機検証バッチ: inset 検証I①-④＋discovery pop 検証II(a)-(d)・取込/削除は実行しない縛り）起動・走行中。B3（In-App Review）は Gradle/実機の直列化のため B4 完了後。
- B5 完了→検分→コミット `da4ad11`（候補モック4案・mockview 裁定待ち）。
- B4 完了: II(a)(b) PASS・(c) 構造上検証不可（即取込＝詳細に入る経路なし・JVM固定で足りる）・検証I は**実機の全4蔵書本文欠落**（upload 鍵検証の uninstall→AutoBackup 片肺復元・knowledge 化済み）で全 SKIPPED。→ 対処: inset 修正は JVM 緑＋真因確定でコミット・**捨て本 Web 取込を監督承認**（追加は非破壊・削除は自分が作った捨て本のみ＝memory の捨て本原則）して検証I+II(d) を再実施・ユーザーへ蔵書欠落を PushNotification。
- **2026-07-29 ユーザー指示「実機はいったんストップ」**＝実機検証ラウンド2（捨て本取込→検証I+II(d)）は未着手のまま停止（再開指示の SendMessage は transcript 不在で不達＝実機未接触）。inset 修正コミット `d3cfd99` 済み・実機機能確認は再開待ち。B3（In-App Review・実機不使用）は走行継続。
- **2026-07-29 ユーザー「実機go」**＝ラウンド2を新規エージェントで起動（**ビルド/install 禁止＝07:58 投入済み APK のまま**・捨て本 Web 取込→検証I①〜④＋II(d)→捨て本のみ削除・B3 と Gradle 非干渉）。
