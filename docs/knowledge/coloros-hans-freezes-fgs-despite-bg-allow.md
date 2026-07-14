# ColorOS: 「バックグラウンドアクティビティを許可」ON でも Hans が FGS を背面凍結する

**重要度**: ★★★
**確定日**: 2026-07-14（PGEM10 / Android 16 / ColorOS・実測）
**更新対象**: task_diary #4 の「根本的な解決はデバイス側の設定変更」——**この設定でも不十分**という上書き知見。

## 事実（実測）

- PDF 変換の dataSync FGS（startForeground + PARTIAL_WAKE_LOCK）実行中にホームへ戻ると、
  **「バックグラウンドアクティビティを許可」を ON にしていても** OplusHansManager が背面数秒でプロセスを凍結する。
- 標準 Android の緩和策も無効: doze whitelist 登録済み・`RUN_ANY_IN_BACKGROUND: allow` を dumpsys で確認済みの状態で凍結した。
- 凍結は kill ではなく freeze（#38 と同種）。アプリへ帰還すると解凍され、変換は**前面で**完走する:

```
OplusHansManager : uid=10601, pkg=com.novelreader enter SM ... D stay=5   ← 背面約5秒で凍結
OplusHansManager : unfreeze ... reason: Activity ... F exit(), F stay=15  ← アプリを開いた瞬間に解凍
```

## 含意

- **「背面でのみ完了通知を出す」型の設計は ColorOS では機能しない**（完了時点はほぼ常に前面＝帰還後）。
  変換完了通知を §86（前面抑制）から「常に通知」へ再裁定した根拠（2026-07-14 ユーザー裁定・PdfProcessingService の Added 分岐コメント）。
- 背面完走そのものをアプリ側で保証する手段は現状なし（FGS + WakeLock + 全設定 ON でも凍結）。
  変換は「アプリを開いている間に進む」前提で UX を設計する。
- 検証時の回避は #38 と同じ「前面化」（`adb shell monkey -p com.novelreader -c android.intent.category.LAUNCHER 1`）。

関連: task_diary #4（設定で解決とした旧知見・本ファイルが上書き）・#37（o-kill）・#38（instrumentation の Hans 凍結）。
