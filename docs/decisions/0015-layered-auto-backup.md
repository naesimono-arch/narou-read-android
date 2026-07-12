# 0015. 層別 Auto Backup（メタデータ層のみ・HTML 実体除外＝旧 allowBackup=false を上書き）

- 状態: 採用（2026-07-12・`ui/polish`＝UX/Design 全層監査 Critical C2 の消化）
- 関連: 実装 `AndroidManifest.xml`・`res/xml/backup_rules.xml`・`res/xml/data_extraction_rules.xml`／監査一次情報 `.claude/plans/ux-design-full-audit-2026-07-12.md` §B [portable] Critical／上書きする旧判断＝旧マニフェストコメント（ADR 未起票のインライン判断だった）

## 背景（要求と制約）

- 蔵書の本文は `filesDir/novels/{bookId}/*.html`、蔵書リスト・読書位置・しおり・Web読書進捗は Room DB、テーマ等の設定は SharedPreferences/DataStore に持つ。
- 旧判断は `allowBackup="false"`（全無効化）。理由は「Auto Backup の 25MB 上限で HTML だけ部分欠落 → DB だけ復元され、開くと実体が無い（`resolvedFile==null`）」という**不整合復元への恐れ**だった。
- しかしその代償として、**端末喪失・機種変更で蔵書リスト・読書位置・設定が全損**する（公理18 資産の端末独立性・2026-07-12 監査 Critical）。「何百時間の読書の記憶」が数十KB のメタデータなのに、25MB 級の本文と運命共同体にされていた。

## 決定

**バックアップを層別にする＝メタデータ層（数十KB）だけをクラウド/D2D へ運び、HTML 実体（25MB 超の要因）は運ばない。**

- `allowBackup="true"` に戻し、**API 31+ は `dataExtractionRules`、API 26-30 は `fullBackupContent`** の両方でルールを宣言する（minSdk 26）。
- **include＝Room DB（`database`）＋SharedPreferences（`sharedpref`）＋DataStore（`file: datastore/`）**。
- **exclude＝`file: novels/`**（include 方式ゆえ実効は冗長だが除外意図を明示）。
- cloud-backup と device-transfer（D2D）は**同一ルール**にする。D2D は容量上限が無く実体も運べるが、経路によって復元結果（本文の有無）が変わると予測不能な差を生むため、全経路で〈メタデータのみ・本文は再取込〉に統一する。
- 旧判断が恐れた不整合は、**復元後に実体が無いケースを graceful degrade（「再取込が必要」状態＝読書を開いてもクラッシュ/行き止まりにせず、位置は保持したまま再取込を促す）**で構造対処する。位置の自動復帰は `htmlDirPath` の bookId 再導出＋`contentSha256` 再結合キー昇格（監査 Minor portable）とセットで完成する。

## 却下した代替案

- **全量バックアップ（novels/ 含む）**: 25MB 上限で部分欠落が構造的に起きる＝旧判断の恐れがそのまま現実化する。却下。
- **allowBackup=false 継続（現状維持）**: 不整合は起きないが、端末喪失で資産全損。守っているのは「壊れたバックアップを見ない権利」だけで、守るべき資産を守っていない。却下。
- **独自エクスポート/インポート機能**: 将来の選択肢としては排除しないが、ユーザーの能動操作が前提＝「何もしていなくても守られている」公理18 の既定を満たさない。Auto Backup の層別が先。

## 帰結

- 復元直後の本棚には「実体なし（要再取込）」の蔵書が並び得る。graceful degrade（上記）が前提＝読書位置・しおりは保持され、同一 PDF を再取込すれば `contentSha256` で再結合して続きから読める、を目指す。
- 検証は実機で `adb shell bmgr backupnow com.novelreader` →データ消去→復元の一連で行う（バックアップトランスポート依存のため、不可なら D2D 相当の手動検証手順を STATUS へ記録）。
