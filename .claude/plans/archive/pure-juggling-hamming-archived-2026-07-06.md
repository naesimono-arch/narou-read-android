# Phase 3 コード配線: BookRepository を Chaquopy → PdfBookExtractor(PDFBox) 直呼へ切替

**対象ブランチ**: `kotlin`（現況の正本は plan `~/.claude/plans/kotrin-branch-python-kotrin-graceful-flute.md` の L174「★次はここから — Phase 3」）
**本セッション範囲**: コード配線(3a–3d) ＋ `testDebugUnitTest` 緑化 ＋ コミット計画提示で**停止**。
実機検証(3e)・シード本掃除は次段（ユーザーが端末前にいるとき）。

## Context（なぜやるか）

Phase 1＋垂直スライスで、Python パイプラインの Kotlin 忠実移植（`java/com/novelreader/pdf/`）は
完成し、実機で穴3（PDFBox-Android の CID→Unicode グリフ解決）も KILL 済み。だが**ランタイムで実際に
動くのは依然 Chaquopy(Python) 側**。Phase 3 でこの配線を切り替え、初めてネイティブ抽出が本番経路になる。

切替で得られるもの:
- **キャンセル可能化**: Python(JNI) は割り込み不能だったが、純 Kotlin 実行なら本文抽出中でも停止できる
  （handover A① の `NonCancellable` 制約を緩和）。
- 例外を**型**で分類（PDFBox は暗号化/破損を型で投げる）＝文字列マッチの脆さを解消。
- 将来 `Dispatchers.IO` での真の並列処理・APK 肥大解消（Phase 5 の Chaquopy 撤去）への前提。

Python は残置し `git revert` で即復旧可能に保つ（安全網）。

## 確定した設計判断（ユーザー承認済み）

1. **NonCancellable 緩和は「進捗コールバック相乗り」方式**（pdf/ 層・テスト fake は無改修）。
   `processPages` はコルーチン非依存の純ロジックとして構築されており、**既に本文ページ毎に進捗
   コールバックを呼ぶ**。よって `BookRepository` 側で進捗コールバックに `ensureActive()` を相乗りさせれば、
   プランの意図（本文抽出中のページ毎割り込み）を pdf/ 層ゼロ改修で達成できる。変更は BookRepository 1ファイルのみ。
2. **停止ボタン(ACTION_STOP)は今回いじらない＝能力確立のみ**。緩和により `onTimeout` の `scope.cancel()` は
   自動的に処理中PDFを即中断できるようになる（無償の改善）。停止ボタンを「即中断」に再配線するのは
   通知文・孤立ファイル掃除・UX を伴う別タスク（`PdfProcessingService` は本セッションでは触らない）。
3. **本セッションはコード＋単体テストまで**。実機検証は停止ポイント。

## 変更内容

### 3b. PDFBox 資産ローダの初期化（新規コミット1）
`NovelReaderApplication.onCreate` に配線する（MainActivity ではなく **Application**）。
- **なぜ Application か**: `PdfProcessingService` は MainActivity 無しでも走る（プロセス再生成・サービス起動経路）。
  `Application.onCreate` はプロセス起動時に必ず全コンポーネントより先に走るため、Service が最初のPDFを
  処理する前に確実に init 済みにできる。`applicationContext` 必須の API とも整合。
- 追加: `import com.tom_roush.pdfbox.android.PDFBoxResourceLoader` ＋ `onCreate` 冒頭で
  `PDFBoxResourceLoader.init(applicationContext)`（`super.onCreate()` 直後・`createNotificationChannel()` の前）。
- **なぜ**コメント: ToUnicode CMap 非搭載 CID フォントのグリフ解決に AAR 同梱資産を使う。あらゆる
  `PDDocument.load` より前に一度だけ必要（task_diary #31）。Service が Activity 無しで走るため Application で先行初期化。
- MainActivity の `Python.start()` は**残置**（Phase 5 で撤去。revert 可能性のため触らない）。
- ファイル: `android/app/src/main/java/com/novelreader/NovelReaderApplication.kt`

### 3a + 3c + 3d. BookRepository 切替（新規コミット2・1論理変更）
`android/app/src/main/java/com/novelreader/repository/BookRepository.kt` を書き換える。

**imports**: `com.chaquo.python.{PyException,Python}` と `kotlinx.coroutines.currentCoroutineContext` を削除。
`com.novelreader.pdf.{PdfBookExtractor,EncryptedPdfError,CorruptedPdfError,InsufficientStorageError}` を追加。

**3a: `addBook` の抽出呼び出し**（現 `:84-101` の `withContext(NonCancellable){ Python…process_pdf }`）を
`PdfBookExtractor.process(tempFile, bookId, outputDir, onProgress)` 直呼へ置換。`onProgress` は同形
（`(step,stepLocalPercent,phase,title)`）なのでそのまま渡せる。戻り値は `BookMeta(title,author)`。

**3d: NonCancellable 緩和 + 孤立ファイル掃除**（`addBook` 再構成）:
```
val extractionScope = this  // withContext(Dispatchers.IO) の CoroutineScope を捕捉
...
val meta = try {
    PdfBookExtractor.process(tempFile, bookId, outputDir) { step, pct, phase, title ->
        extractionScope.ensureActive()   // 進捗通知(=本文ページ毎)にキャンセル確認 → 割り込み停止可能に
        onProgress(step, pct, phase, title)
    }
} catch (e: Throwable) {
    outputDir.deleteRecursively()  // 中断/失敗時は書きかけ HTML を掃除（本棚に出ない孤立本を残さない）
    throw e
}
// Room 登録のみ NonCancellable で保護（HTML生成済み→DB登録前の一瞬の孤立を防ぐ・旧 NonCancellable の縮小）
val book = withContext(NonCancellable) {
    val b = BookEntity(bookId, meta.title, outputDir.absolutePath, meta.author, addedAt = System.currentTimeMillis())
    bookDao.insertBook(b); b
}
extractionScope.ensureActive()  // NonCancellable 内で握り潰したキャンセルを完了後に確定（旧実装踏襲）
```
- **なぜ掃除が必要か**: 旧 `NonCancellable` は抽出全体を包んで孤立ファイルを防いでいた。緩和で抽出中の
  キャンセルを許すため、その代替として明示 `deleteRecursively()` で担保する（掃除は catch 内＝DB登録後は発火しない）。
- `outputDir` は `try` の外（上部）で宣言し catch から参照できるようにする。`fold(onFailure)` の
  既存 `if (e is CancellationException) throw e` はそのまま（キャンセルを Unknown に化けさせない）。

**3c: `classifyError`（現 `:39-56`）を型分岐へ**:
```
internal fun classifyError(e: Throwable): Throwable = when (e) {
    is EncryptedPdfError        -> BookImportError.EncryptedPdf()
    is InsufficientStorageError -> BookImportError.InsufficientStorage()
    is CorruptedPdfError        -> BookImportError.CorruptedPdf()
    else -> { /* URI/ディレクトリ生成失敗は BookRepository 自身が投げる IOException メッセージで拾う */
        val msg = e.message ?: ""
        when {
            msg.contains("PDFファイルを開けません")     -> BookImportError.UriPermissionDenied()
            msg.contains("出力ディレクトリの作成に失敗") -> BookImportError.StorageWriteFailure()
            msg.contains("No space left on device")    -> BookImportError.InsufficientStorage()
            else                                       -> BookImportError.Unknown(msg)
        }
    }
}
```
- `PdfBookExtractor.process` は内部で `classifyPdfError` を通し暗号化/破損/容量不足を `PdfExtractionException`
  サブ型で投げる（`PdfExtractionException.kt` 既存）。よって型分岐で拾える。**なぜ**コメントに「Chaquopy版の
  PyException 文字列マッチ廃止・PDFBox は型で投げる」を残す。

### 3c テスト書換
`android/app/src/test/java/com/novelreader/repository/BookRepositoryTest.kt` の PyException モック4件を
`PdfExtractionException` サブ型の直接生成へ書換:
- `import com.chaquo.python.PyException`・`import io.mockk.every` 削除、
  `import com.novelreader.pdf.{EncryptedPdfError,CorruptedPdfError,InsufficientStorageError}` 追加（`mockk` は setUp で継続使用）。
- Encrypted/InsufficientStorage/Corrupted の3件 → `repository.classifyError(EncryptedPdfError("..."))` 等へ。
- 「未知」1件 → `repository.classifyError(RuntimeException("..."))` → `BookImportError.Unknown`。
- 非PyException 側3件（UriPermissionDenied/StorageWriteFailure/No space の IOException）は**現状維持**（新 else 節で通る）。

## 触らないもの（明示）
- `pdf/` パッケージ一式（`PdfBookExtractor`/`PdfExtractor`/`TextProcessor`/`ChapterProcessor`/`HtmlExporter`/
  `PdfExtractionException` 等）＝設計判断1により無改修。テスト fake（`PdfBookExtractorTest` の FakeEngine/FakeHandle）も無改修。
- `PdfProcessingService.kt`（設計判断2により ACTION_STOP 再配線せず）。
- `MainActivity.kt` の Chaquopy `Python.start()`（Phase 5 まで残置）。
- `.claude/skills/architecture/SKILL.md`・`CLAUDE.md`（M のまま持ち越し＝移植と無関係・main 帰属。`git add` で対象を明示しコミットに含めない）。

## 検証（自己検証必須）
1. **Kotlin 単体テスト**（`src/main`/`src/test` 変更後は必須）。Bash ツールは `.bashrc` 非ロードのため env 明示:
   ```
   export JAVA_HOME=/home/qingj/opt/jdk-17; export ANDROID_HOME=/home/qingj/Android/Sdk
   export ANDROID_SDK_ROOT=/home/qingj/Android/Sdk; export PATH=$JAVA_HOME/bin:$PATH
   cd /mnt/c/Users/qingj/Desktop/project/novel-reader_andloid/android
   sed -i '/^sdk\.dir/d' local.properties 2>/dev/null
   java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
     --no-daemon --console=plain --init-script /home/qingj/ext-build/novel-reader-init.gradle testDebugUnitTest
   ```
   期待: 全緑（現 104 件から BookRepositoryTest の4件書換後も件数維持・緑）。**フォアグラウンド実行**
   （background だとコミットゲートのセンチネル未生成＝`.kt` コミットが弾かれる／memory 参照）。
2. **コンパイル確認**: 上記に `compileDebugKotlin` が含まれる（`com.tom_roush.*` import 解決・BookRepository の新 import 解決）。
3. 実機フル疎通(3e)＝**本セッション対象外**（停止ポイント）。次段で `adb-bridge`→`PdfPipelineDeviceTest` 系を
   `am instrument` 実行（connectedAndroidTest 直叩き禁止＝蔵書DB消失／`/device-verify` スキル）で
   N6169DZ 長編を前景サービス経路で検証。

## コミット計画（承認を得てから実行・`Co-Authored-By` 無し）
1. `feat: PDFBox 資産ローダを Application 起動時に初期化`（3b・`NovelReaderApplication.kt`）
2. `refactor: PDF抽出を Chaquopy から PdfBookExtractor(PDFBox) 直呼へ切替`（3a+3c+3d・`BookRepository.kt`＋`BookRepositoryTest.kt`）
   - 本文抽出中の割り込み停止を可能化（NonCancellable 緩和）・例外を型分岐化を含む1論理変更。

各コミットはビルド緑・`testDebugUnitTest` 緑を確認し、**変更内容とテスト結果を提示して人間承認**を得てから実行する。
（プラン規約「BookRepository切替(Phase3)では必ず停止」に従い、実機検証へは進まず本セッションはここで一旦区切る。）

## 完了後の反映（同一ターン内で確認）
- `~/.claude/plans/kotrin-branch-python-kotrin-graceful-flute.md` の Phase 3 進捗・`STATUS.md`（kotlin 現況）を更新
  （実機検証 3e が残るため「配線済み・実機未検証」と明記）。
- architecture スキル/CLAUDE.md のパイプライン記述の全面書換は**Phase 5（Chaquopy 撤去）で実施**予定
  （今 Kotlin 単独に書くと現状=併存と乖離するため。撤去時反映は既に handover に予約済み）。
