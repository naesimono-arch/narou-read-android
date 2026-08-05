# AGP: sourceSets の srcDir(TaskProvider) は builtBy を落とす——「宣言的に繋いだつもり」の copy がグラフに乗らない

2026-08-06 確定（バイトコード実査＝AGP 8.6.1／8.9.1／8.10.1 で同一実装・ベンチ PDF 資産欠落の真因）。

- **機序**: AGP は `android{}` DSL の srcDir 群を variant 生成時に `DefaultSourcesProviderImpl.getSourceList` →
  `DefaultAndroidSourceDirectorySet.getSrcDirs()`＝`project.files(...).getFiles()` で**素の `Set<File>` に潰す**。
  この時点で FileCollection の builtBy（生産者タスク情報）が脱落し `FileBasedDirectoryEntryImpl`（静的）に包まれる＝
  **TaskProvider を渡しても依存は張られない**。バージョン非依存（少なくとも 8.6.1〜8.10.1）。
- **毒性＝「緑のまま壊れる」**: build ディレクトリに過去の copy 出力が残っていれば APK は正しく組める。
  2026-07-18 の「成功」は残存物による偶発で、宣言的結線は当時から死んでいた——fresh worktree／clean build で初めて顕在化。
  同名の実害クラス＝ADR 0029 の「AGP 同梱 R8 が Kotlin 2.2 メタデータを読めず緑のまま壊れる」。
- **正道＝variant API**: `androidComponents.onVariants(selector().withBuildType("benchmark")) {
  variant.sources.assets.addGeneratedSourceDirectory(copyTask) { it.outputDir } }`。
  受け側は AGP が出力先を注入する **settable な `@OutputDirectory DirectoryProperty` が必須**＝素の `Copy`
  （`destinationDir`＝File）では受けられず専用タスククラスにする（実装と理由コメント＝`android/app/build.gradle` の
  `CopyBenchmarkPdfAssetTask` と `onVariants` ブロック）。
- **検証の型**: ① `--dry-run` のタスクグラフに copy タスクが `mergeXxxAssets` の直前に載る ② APK を
  `python3 -m zipfile -l <apk>` で実在確認（unzip 非導入環境でも可）。
