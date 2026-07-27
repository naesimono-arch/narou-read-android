package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.narou.model.Ncode
import com.novelreader.pdf.BookMeta
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.pdf.PdfProgress
import com.novelreader.trace.Sections
import com.novelreader.repository.BookRepository.AddBookResult
// 栞の個体差抽選（純ロジック・Compose 非依存）。ShioriCover.kt（Compose 依存）は import しない
// ＝先端総数/レンジの正本は ShioriGenerator.kt 側にあり、repository はそこだけを参照する。
import com.novelreader.ui.components.drawPersistedShiori
import com.novelreader.viewmodel.BookImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random
import java.security.MessageDigest
import java.util.UUID

// 分割後も従来どおりのタグで出す（ログ検索の継続性＝挙動不変のため各協力クラスで同一タグを使う）。
private const val TAG = "BookRepository"

/**
 * 責務: PDF 取込（コピー→内容ハッシュ遮断→容量チェック→抽出→重複照合→Room 登録）と取込エラーの分類。
 *
 * [DefaultBookRepository] の責務分割（2026-07-27 構造リファクタ）で委譲抽出した協力クラス。
 * extractBook（抽出の差替継ぎ目）を注入で受ける理由は DefaultBookRepository のコンストラクタ引数コメント参照。
 */
internal class PdfBookImporter(
    private val context: Context,
    private val bookDao: BookDao,
    private val pendingJobs: PendingJobStore,
    private val extractBook: (pdfFile: File, bookId: String, outputDir: File, onProgress: PdfProgress) -> BookMeta,
) {

    /**
     * 抽出例外・IO 例外をユーザー向けエラー種別に変換する。
     *
     * ネイティブ PDFBox 経路は暗号化/破損/容量不足を [com.novelreader.pdf.PdfExtractionException] の
     * サブ**型**で投げる（facade PdfBookExtractor が内部で classifyPdfError 済み）ため型で分岐する。
     * Chaquopy 版は PyException のメッセージ文字列で分類していたが、型の方が堅牢なため文字列マッチは廃止した。
     * facade を通らない例外（URI 権限喪失・出力ディレクトリ生成失敗）は BookRepository 自身が投げる
     * IOException なので、従来どおりメッセージで拾う（else 節）。
     */
    fun classifyError(e: Throwable): Throwable = when (e) {
        is EncryptedPdfError        -> BookImportError.EncryptedPdf()
        is InsufficientStorageError -> BookImportError.InsufficientStorage()
        is CorruptedPdfError        -> BookImportError.CorruptedPdf()
        else -> {
            val msg = e.message ?: ""
            when {
                msg.contains("PDFファイルを開けません")      -> BookImportError.UriPermissionDenied()
                msg.contains("出力ディレクトリの作成に失敗")  -> BookImportError.StorageWriteFailure()
                msg.contains("No space left on device")     -> BookImportError.InsufficientStorage()
                else                                        -> BookImportError.Unknown(msg)
            }
        }
    }

    /** PDFをキャッシュにコピーし、ネイティブ(PDFBox)抽出でHTML生成後にRoomへ登録する。 */
    suspend fun addBook(
        pdfUri: Uri,
        ncode: Ncode?,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit,
    ): Result<AddBookResult> = withContext(Dispatchers.IO) {
        // withContext(Dispatchers.IO) の CoroutineScope を捕捉する。抽出の進捗コールバック（非 suspend）から
        // キャンセルを確認するために使う（下記 ③）。
        val extractionScope = this
        runCatching {
            val bookId = UUID.randomUUID().toString().take(8)

            // ① 一時ファイルにコピー（try-finally で確実に削除する）
            val tempFile = File(context.cacheDir, "temp_$bookId.pdf")
            // catch から参照するため try の外で宣言する（②で確定・③の失敗時に掃除）。
            // 置き場は BookEntity.resolveHtmlDir に一元化した決定的規約（filesDir/novels/<bookId>）を使う
            // ＝掃除・復元と同一導出（UX監査 portable の復元耐性下地）。
            val outputDir = BookEntity.resolveHtmlDir(context.filesDir, bookId)
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: throw IOException("PDFファイルを開けません（URI権限が失われた可能性があります）")
                // trace 区間（計測のためだけの挿入・ロジック不変）: 取込元 PDF を cache へコピーする I/O。
                // 同一コルーチン内の同期 I/O で begin/end は同一スレッドに閉じる（TraceSectionMetric が拾える）。
                Sections.trace("Import#copyToTemp") {
                    inputStream.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                // ①' 内容ハッシュによる変換前遮断（F-G 恒久策）: 取込元 PDF バイト列の SHA-256 を計算し、
                // 同一内容の本が既に蔵書にあれば「重い抽出（分オーダー）を一切走らせずに」重複確定する。
                // なぜここ（抽出前・最速地点）か: 既存の title＋author 照合（④）は抽出後にしか判定できず、
                // URI が変わる同一 PDF の再選択でも毎回フル変換が走ってしまう（F-G の旧修正の穴）。
                // ハッシュはコピー済み temp を1回読み直して計算する。なぜ DigestInputStream でコピーに
                // 相乗りする単一パスにしないか: ハッシュ計算を純関数 sha256Hex に閉じてテスト可能にする方を
                // 優先したため。cacheDir 上の temp を数十MB 読み直す実コストは変換の分オーダーに対し無視できる。
                // trace 区間: 取込前遮断用の SHA-256（temp を 8KiB ストリーミングで単一走査）。同期・同一スレッド。
                val contentSha256 = Sections.trace("Import#sha256") { tempFile.inputStream().use { sha256Hex(it) } }
                // 照合は facade の findExistingBookByHash と同一の DAO クエリ（分割前と同じ1回の SELECT）。
                val existingByHash = bookDao.findByContentSha256(contentSha256)
                if (existingByHash != null) {
                    // outputDir はまだ mkdirs していないので掃除不要。変換の成否が確定した（＝重複）ので
                    // 成功/重複時と同じく pending_jobs を落とし永続権限も返す（NonCancellable で保護）。
                    withContext(NonCancellable) { pendingJobs.settleJob(pdfUri) }
                    extractionScope.ensureActive()
                    // try 内から return しても finally（tempFile.delete）は走る＝一時ファイルはリークしない。
                    return@runCatching AddBookResult.Duplicate(existingByHash)
                }

                // ①'' 空き容量の事前チェック（UX監査 add・10-H「資源は起きる前に測る」）:
                // 抽出は分オーダーで一時展開＋出力HTMLを filesDir へ書くため、逼迫時は変換の終盤で ENOSPC
                // 失敗し、時間と cache を浪費する。重い抽出に入る前に filesDir の空きと概算所要を比べ、不足なら
                // 既存の容量不足エラー経路（InsufficientStorage の固定文言）へ落として無駄な変換を回避する。
                // outputDir はまだ mkdirs していないので掃除不要。settlePendingJob もしない＝容量が空けば
                // 再試行で成功しうる一過性失敗として、外側 fold の失敗経路（pending 行だけ落とし権限は残す）に委ねる。
                val pdfSizeBytes = tempFile.length()
                if (!hasEnoughStorageFor(context.filesDir.usableSpace, pdfSizeBytes)) {
                    throw InsufficientStorageError(
                        "変換に必要な空き容量が不足（PDF ${pdfSizeBytes}B・空き ${context.filesDir.usableSpace}B）"
                    )
                }

                // ② 出力先ディレクトリを確定
                if (!outputDir.mkdirs() && !outputDir.exists()) {
                    throw IOException("出力ディレクトリの作成に失敗しました: ${outputDir.absolutePath}")
                }

                // ③ ネイティブ(PDFBox)抽出で HTML を生成する。
                // Chaquopy(JNI) は割り込み不能だったが、純 Kotlin 実行なので中断可能。processPages は本文ページ
                // 毎に onProgress を呼ぶため、進捗通知のたびに ensureActive() を通せば本文抽出中でも「停止」で
                // 割り込める（handover A① の NonCancellable 制約を緩和）。processPages 自体はコルーチン非依存の
                // 純ロジックに保つため、既に全層へ通っている進捗コールバックへ相乗りしてキャンセルを確認する。
                val meta = try {
                    // trace 区間: 抽出本体（PdfBookExtractor.process＝取込の支配的コスト）。extractBook は非 suspend で
                    // 同期実行され、進捗コールバックも非 suspend のため begin/end は同一スレッドに閉じる。
                    // 内部の Extract#* 区間はこの Import#extract の子スライスとして入れ子になる。
                    Sections.trace("Import#extract") {
                        extractBook(tempFile, bookId, outputDir) { step, stepLocalPercent, phase, title ->
                            extractionScope.ensureActive()
                            onProgress(step, stepLocalPercent, phase, title)
                        }
                    }
                } catch (e: Throwable) {
                    // 抽出が中断/失敗したら書きかけの出力ディレクトリを消す（本棚に出ない孤立 HTML を残さない）。
                    // 旧実装は抽出全体を NonCancellable で包んで孤立を防いでいたが、緩和で抽出中のキャンセルを
                    // 許すため、その代替としてこの明示クリーンアップで担保する（DB 登録前のみ発火）。
                    outputDir.deleteRecursively()
                    throw e
                }

                // ④ べき等ガード（UX監査 F-G 公理3）: 抽出後のタイトル＋著者で既存蔵書を照合する。
                // 既に同じ本があれば二重登録しない。この段階で書きかけ HTML を破棄する（本棚に孤立本を残さない）。
                // 多層防御の最終層: ①Service のキュー重複ガード（同一 URI の連続投入を変換前に弾く）→
                // ①'内容ハッシュの変換前遮断（別 URI・同内容を変換前に弾く＝F-G 恒久策）→ ④ここ。
                // ①' を潜り抜けるのは「旧取込分（contentSha256 が NULL で照合不能）」の再取込のみで、
                // その受け皿としてタイトル＋著者で弾く（＝ハッシュ列導入前に入れた本の再取込も従来どおり弾ける）。
                val existing = bookDao.findByTitleAndAuthor(meta.title, meta.author)
                if (existing != null) {
                    outputDir.deleteRecursively()
                    // 変換の成否が確定した（＝重複と判明）ので pending_jobs を落とす。DB 書き込みを伴わない
                    // が settlePendingJob は権限解放も行うため、登録成功時と同じく NonCancellable で保護する。
                    withContext(NonCancellable) { pendingJobs.settleJob(pdfUri) }
                    extractionScope.ensureActive()
                    AddBookResult.Duplicate(existing)
                } else {
                    // ⑤ Room 登録のみ NonCancellable で保護する。
                    // HTML 生成済み→DB 登録前の一瞬でキャンセルされると本棚に出ない孤立本になるため、この最終確定
                    // だけは中断不能に保つ（抽出全体を包んでいた旧 NonCancellable の縮小）。
                    val book = withContext(NonCancellable) {
                        // addedAt に追加時刻をスタンプし、本棚の最近活動順ソート（未読本の基準）に使う。
                        // contentSha256 に①'で計算した内容指紋を保存する（次回以降の別URI・同内容の
                        // 再取込を、この本の存在によって変換前に遮断できるようにする＝F-G 恒久策の記録）。
                        // ncode は「新規登録時のみ」書き込む（ADR 0011 の縦書きPDF取り込み経路から渡る）。
                        // なぜ Duplicate 経路（上の hash 照合／下の title+author 照合）では設定しないか:
                        // 既にある本を上書きすると、ユーザーが手動で別作品へ紐付け直した ncode を勝手に潰しうる。
                        // 重複時は既存本をそのまま返し ncode に触れない（＝既存の紐付けを尊重する）。取り込み側は
                        // 必要なら NcodeLinkSheet で手動修正できるため、これで実害はない。
                        // 栞書影の先端種・棒長を取込時に真の乱数で1回だけ抽選し永続化する（以後この本は固定の絵になる）。
                        // なぜ取込時に1回か: 描画のたびに引くと本を開くたびに絵が変わってしまう。ここで確定させ DB に焼く。
                        // 発生源は真の乱数 Random.Default。総数/レンジの正本は ShioriGenerator（Compose 非依存）側。
                        val shiori = drawPersistedShiori(Random.Default)
                        // 取込元 PDF の SAF URI を「書き込み永続権限を実際に保持している本」に限って記録する
                        // （本削除時に取込元PDFも削除できる本の signal＝BookEntity.sourceUri の why）。
                        // なぜ書込権限保持が条件か: DocumentsContract.deleteDocument には書込権限が要り、読取だけの
                        // 本は削除が必ず失敗する＝記録しても削除チェックを出せない。権限取得は取込操作側
                        // （BookshelfViewModel.addBook が READ|WRITE を試み、非対応プロバイダは READ へフォールバック）
                        // で行われ、ここでは persistedUriPermissions を照会してその実結果を確定させる。これにより
                        // なろう縦書きPDF（FileProvider の content:// で永続権限を取れない経路）は自動的に NULL になる。
                        val sourceUri = pdfUri.toString().takeIf { uriStr ->
                            context.contentResolver.persistedUriPermissions.any {
                                it.uri.toString() == uriStr && it.isWritePermission
                            }
                        }
                        val b = BookEntity(bookId, meta.title, outputDir.absolutePath, meta.author, addedAt = System.currentTimeMillis(), contentSha256 = contentSha256, ncode = ncode?.value, shioriTipIndex = shiori.tipIndex, shioriLenFrac = shiori.lenFrac, sourceUri = sourceUri)
                        // trace 区間: DB 登録（books への1行 insert）。
                        // ⚠ 計測上の注意（区間名一致とは別問題）: insertBook は suspend で Room が自前 executor へ
                        // 再ディスパッチするため、begin と end が別スレッドになり得る（=スライスが分裂しうる）。
                        // 本番挙動・原子性は不変（begin/end は atrace マーカーのみ）だが、TraceSectionMetric での
                        // Import#insertDb の値は extract 系ほど信頼できない場合がある（実測で確認する）。
                        Sections.trace("Import#insertDb") { bookDao.insertBook(b) }
                        // 変換が確定したので永続キュー（pending_jobs）の記帳を落とす。insertBook と同じ
                        // NonCancellable 内で連続実行し、「本は登録されたのに pending が残る」→ 次回起動の
                        // リカバリが同じ本を再変換して重複登録する窓を最小化する（insertBook↔settle 間のプロセス
                        // kill 窓は DB 跨ぎの原子化が要るため残るが、数msで実用上無視できる）。pendingJobMutex
                        // （PendingJobStore 内・settleJob が取る）は並行する clearPendingJobs 等との pending_jobs
                        // 書き込み衝突を防ぐためで、kill 窓とは別問題。
                        pendingJobs.settleJob(pdfUri)
                        b
                    }
                    // NonCancellable ブロック完了後にキャンセルを確認
                    // （NonCancellable 内では ensureActive() が機能しないため必ず外側で呼ぶ）
                    extractionScope.ensureActive()
                    AddBookResult.Added(book)
                }
            } finally {
                if (!tempFile.delete()) Log.w(TAG, "一時ファイルの削除に失敗: ${tempFile.absolutePath}")
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                // コルーチンのキャンセルはエラーに変換せず素通しする。
                // runCatching は CancellationException も捕捉するため、ここで rethrow しないと
                // ensureActive() 等が投げたキャンセルが classifyError() で Unknown エラーに化け、
                // 呼び出し側で不要なエラー通知が出てキャンセルの静かな伝播が壊れる。
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "addBook 失敗", e)
                // 失敗が確定した本は再開対象から外す（破損PDF等は再試行しても失敗を繰り返すだけで、
                // 起動のたびに同じエラーが再走するループになる）。キャンセルは上で rethrow 済み＝対象外で、
                // 停止操作時の扱いは Service の ACTION_STOP（全消し）が決める。
                // なぜ settlePendingJob ではなく pending_jobs 行の削除のみか（M7 再試行の成立）:
                // settlePendingJob は永続 URI 権限も返すが、それだと失敗 Snackbar の「再試行」が
                // 同一 URI を再投入したとき openInputStream が権限喪失で必ず再失敗する（＝再試行が形骸化）。
                // 権限を残せばユーザー起点の再試行が機能する。再試行しない場合に権限が1件残るのは
                // 端末上限内の軽微なコストで、権限リーク回避より再試行の成立を優先する。
                // （成功/重複/停止時は従来どおり settlePendingJob で権限も返す＝ここだけの例外扱い。）
                withContext(NonCancellable) {
                    pendingJobs.deleteRowKeepingPermission(pdfUri.toString())
                }
                Result.failure(classifyError(e))
            },
        )
    }
}

// ── 取込前の空き容量チェック（UX監査 add・10-H）─────────────────────────────
// 変換1冊あたりの概算所要 = PDF サイズ × 係数 ＋ 最低フロア。
// なぜ係数2か（推定・厳密でなく防御的）: 出力HTMLは通常PDFより小さい（PDF はフォント/グリフを含むが抽出後は
// 本文テキストのみ）が、一時展開・中間バッファの headroom を見込んで2倍を要求する保守側の概算。実測で調整可
// （過大なら取込を過剰に拒否しうるが、逼迫端末で変換終盤に ENOSPC 浪費する方を重く見て安全側に倒す）。
internal const val STORAGE_SAFETY_FACTOR = 2L
// PDF が極小でも抽出の作業領域として最低これだけは空いていてほしい下限（8 MiB）。
internal const val STORAGE_MIN_FREE_BYTES = 8L * 1024 * 1024

/**
 * 取込に十分な空き容量があるか（純判定・テスト対象）。必要見込み = max(pdfSize×係数, フロア)。
 * usableSpace/getAllocatableBytes 等で得た実測空きバイト数を渡す（副作用はしない＝判定のみ切り出す）。
 */
internal fun hasEnoughStorageFor(usableBytes: Long, pdfSizeBytes: Long): Boolean =
    usableBytes >= maxOf(pdfSizeBytes * STORAGE_SAFETY_FACTOR, STORAGE_MIN_FREE_BYTES)

/**
 * InputStream 全体の SHA-256 を小文字16進文字列で返す（取込 PDF の内容指紋）。
 *
 * F-G 恒久策（内容ハッシュによる二重変換の変換前遮断）の中核。8KiB バッファのストリーミングで
 * 読むため数十MB でも定数メモリで済み、ワンパスで digest を確定する。ストリームの close は
 * 呼び出し側の責務（addBook は `tempFile.inputStream().use { sha256Hex(it) }` で閉じる）。
 * Android 非依存の純関数として切り出し、既知テストベクタで JVM 単体テストできるようにする。
 *
 * **pending_jobs（強制終了リカバリ）との相互作用に自己除外は不要**（設計判断の記録）:
 * contentSha256 は変換が成功して BookEntity を insert する時にしか books へ書かれない（addBook ⑤ の
 * NonCancellable 内で insertBook と一緒に確定）。未完了のまま kill されたジョブは自分のハッシュを
 * まだ books に持たないため、リカバリ再投入時に findExistingBookByHash は null を返し、自分自身を
 * 誤って遮断することはない。逆に「insert 済みだが settlePendingJob 直前に kill」された極小窓
 * （BookRepository ④/⑤ のコメント参照）では、リカバリ再投入がこのハッシュ照合でヒットして
 * 変換前に Duplicate 確定する＝旧実装（抽出後に title＋author で弾く）より二重変換窓が縮む改善であり、
 * 誤ブロックではない。よって「自分のジョブを除外」する防御は追加しない。
 */
internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    // 各バイトを符号なし2桁16進へ。and 0xFF で符号拡張を潰す（Byte は符号付きのため、
    // これが無いと 0x80 以上のバイトが "ffffffxx" に化ける＝ハッシュ文字列が壊れる）。
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
