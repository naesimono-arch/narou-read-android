# 取込元PDF削除が権限失効で失敗する（実証・2026-09-02）

エミュ `nr_c`（API36・debug APK 09-02 11:05 版）で2アーム。**症状は確定**。

- **ArmB＝アプリ再起動後（実運用の通常経路）→ 失敗**（`05`〜`06`）。
  本を長押し→削除→「取込元のPDFファイルも削除する（1件）」を checked にして確定しても、
  `/sdcard/Download/N5368ML.pdf` は **29,895 バイト・mtime 2026-07-17 00:45 のまま不変**。DB の行だけ消える。
  logcat は `SecurityException: Permission Denial ... requires that you obtain access using ACTION_OPEN_DOCUMENT`
  を `LibraryDeleter.kt:97` から吐いている（`evidence.txt`）。
- **ArmA＝取込直後の同一プロセス → 成功**（`07`〜`08`）。`dumpsys` は `persistable=0x3 persisted=0x0` で
  `readOwners` に MainActivity＝**永続権限は返却済みだが一時グラントが残っている**間だけ通る。

**境界**: 取込元PDF削除が効くのは「取込したアクティビティが生きている間だけ」。閉じれば以後は必ず失敗する。

**真因**: `PendingJobStore.settlePendingJob` が取込成功の直後に `releasePersistableUriPermission` を呼ぶ。
同ファイル `releaseOrphanedPermissions` の keepUris ②「books.sourceUri は本の生存中ずっと保持する」は
**守る対象が既に無い空振り**になっている。

**副次の所見**: 失敗時のスナックバー文言は「移動/削除済みか、削除に対応しない保存先の可能性」で、
**実際の真因（権限失効）のどちらでもない**＝ユーザーを誤誘導する（`06`）。

裁定＝`awaiting-human.md` §3。
