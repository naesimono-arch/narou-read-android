package com.novelreader.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobDao
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelDao
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressDao
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.Ncode
import com.novelreader.pdf.BookMeta
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.pdf.PdfBookExtractor
import com.novelreader.pdf.PdfProgress
import com.novelreader.repository.BookRepository.AddBookResult
// 栞の個体差抽選（純ロジック・Compose 非依存）。ShioriCover.kt（Compose 依存）は import しない
// ＝先端総数/レンジの正本は ShioriGenerator.kt 側にあり、repository はそこだけを参照する。
import com.novelreader.ui.components.drawPersistedShiori
import com.novelreader.viewmodel.BookImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random
import java.security.MessageDigest
import java.util.UUID

/**
 * [BookRepository] の本番実装。Room（AppDatabase の DAO 群）に永続化し、PDFBox でネイティブ抽出する。
 *
 * なぜ interface [BookRepository] と分離したか: 本クラスは AppDatabase.getDatabase（static シングルトン）
 * と PdfProcessingService/PDFBoxResourceLoader の Android 依存を引くため JVM 単体テストで直接使えない。
 * 利用側（Application/ViewModel/Service）を interface 型参照にし、テストでは軽量な FakeBookRepository へ
 * 差し替えられるようにするための実装分離（挙動は従来 BookRepository のまま不変）。
 */
class DefaultBookRepository(
    private val context: Context,
    private val bookDao: BookDao = AppDatabase.getDatabase(context).bookDao(),
    private val progressDao: ProgressDao = AppDatabase.getDatabase(context).progressDao(),
    private val pendingJobDao: PendingJobDao = AppDatabase.getDatabase(context).pendingJobDao(),
    private val webNovelDao: WebNovelDao = AppDatabase.getDatabase(context).webNovelDao(),
    private val webReadingProgressDao: WebReadingProgressDao = AppDatabase.getDatabase(context).webReadingProgressDao(),
    // なぜトランザクション実行を関数注入にするか（テスト可能な原子性）:
    // deleteBook の books削除＋progress削除を1トランザクションに束ねて「孤児progress行」を防ぐ。だが本クラスは
    // DAO 個別注入で JVM 単体テストする設計（クラス doc 参照）のため、実 AppDatabase.withTransaction に直接依存すると
    // テストで Room に落ちてしまう。トランザクション境界だけ関数で受け、本番は Room の withTransaction、テストは
    // 素通しラムダ（block を即実行）へ差し替えられるようにする（DAO 分離注入と同じ思想の延長）。
    private val runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block ->
        AppDatabase.getDatabase(context).withTransaction(block)
    },
    // 抽出の差替継ぎ目（UX監査 measure・破損PDF隔離のテスト可能化）: 本番は PdfBookExtractor.process の
    // 実 PDFBox 経路（engine 固定の public 版）。JVM 単体テストでは例外を投げる fake を注入し、隔離
    // （書きかけ outputDir 削除・BookEntity 未 insert・pending_jobs 行削除）が repository 層で成立することを
    // assert できるようにする。DAO/runInTransaction と同じ「Android 依存を関数で受ける」注入思想の延長。
    private val extractBook: (pdfFile: File, bookId: String, outputDir: File, onProgress: PdfProgress) -> BookMeta =
        { pdfFile, bookId, outputDir, onProgress -> PdfBookExtractor.process(pdfFile, bookId, outputDir, onProgress) },
) : BookRepository {

    // pending_jobs（永続キュー）への全書き込みを直列化する排他ロック（タスク1の恒久策）。
    // なぜ Mutex か / なぜ limitedParallelism(1) では不成立だったか:
    //   旧設計は enqueue の記帳(insert)と明示停止の全消し(deleteAll)を Dispatchers.IO.limitedParallelism(1) の
    //   単一スロットへ launch して投入順を守らせる狙いだった。しかし各メソッド冒頭の withContext(Dispatchers.IO) が
    //   スロットを手放して汎用IOプールへ再ディスパッチするうえ、Room の suspend DAO も内部で自前 executor へ
    //   再ディスパッチするため、単一スロットは実際の DB 着地を1件も直列化できていなかった（coroutine 意味論として不成立）。
    //   帰結として「PDF投入直後に停止」で insert が deleteAll の後に着地し、破棄済みジョブが pending_jobs に復活
    //   → 次回起動の runStartupRecoveryOnce が勝手に再変換する窓が残った。
    //   Mutex はロックを「suspend な DAO 呼び出しの完了まで保持」するため、どのディスパッチャで走ろうと相手の
    //   pending_jobs 書き込みが割り込めない＝実際の DB 書き込みを相互排他できる（limitedParallelism に無い保証）。
    //   投入順は main スレッド(onStartCommand)からの launch 順＋Mutex の FIFO 公平性で保たれる。
    private val pendingJobMutex = Mutex()

    override val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    override val allProgress: Flow<List<ProgressEntity>> = progressDao.getAllProgress()
    override val webNovels: Flow<List<WebNovelEntity>> = webNovelDao.getAll()

    override suspend fun putWebNovel(novel: WebNovelEntity) = webNovelDao.insert(novel)

    // なぜ削除も trim().uppercase() 正規化か: 保存側（putWebNovel の契約）と同じ正規化を通さないと、
    // 表記ゆれの ncode で削除が空振りしてカードが残り続けるため（NcodeLinkSheet の保存正規化と同系）。
    // 併せて Web読書位置履歴も相乗り削除する（UX監査 privacy）: カードを外したのに位置履歴だけ端末へ
    // 残る穴を塞ぐ。ただし同 ncode を紐付けた蔵書がまだ在れば「続きから」に要るため残す（下記 helper が判定）。
    override suspend fun removeWebNovel(ncode: Ncode) = withContext(Dispatchers.IO) {
        val key = ncode.value.trim().uppercase()
        webNovelDao.deleteByNcode(key)
        cascadeDeleteWebProgressIfUnreferenced(key)
    }

    override val webReadingProgress: Flow<List<WebReadingProgressEntity>> = webReadingProgressDao.getAll()

    // なぜ trim().uppercase() 正規化か: 記録側と本棚カード/紐付け側の ncode 表記を一致させ、
    // 「読んだのに続きから読むが出ない」空振りを防ぐ（putWebNovel/removeWebNovel と同系の保存正規化）。
    //
    // なぜ furthest-wins（無条件 upsert でなく episode>既存のときだけ更新）か（UX監査 continuity・公理14/公理6）:
    // 目次から前の話（第10話）を「確認のため新規リンクで開いて」退出すると、その小さい話数が
    // 素の last-wins 上書きで再開ポインタを後退させ、読みかけ先端（第51話）が失われる。参照ジャンプで
    // 自動保存を後退させないため、到達済み最遠話より前進したときのみ記録する（しおりは覗き見で後退しない）。
    //   task_diary #56 の reachedByBack ガードは goBack の履歴遡行だけを抑止するが、目次から前の話を
    //   「新規リンク」で開く経路は forward 履歴が切り詰められ reachedByBack=false になるため素通りする。
    //   その後退経路をここ（全記録の単一集約点）で塞ぐ＝#56 と相補的で二重防御になる。
    // race について: onEpisodeReached は onPageFinished ごとに個別 launch するため read→write が
    //   IO 上で交錯し得るが、furthest-wins は単調なので最悪でも「一時的に低い話数が残り、次の前進で
    //   訂正される」だけ（先端の恒久喪失は起きない）。厳密原子性は複雑さに見合わないため許容する。
    override suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int) = withContext(Dispatchers.IO) {
        val key = ncode.value.trim().uppercase()
        val existing = webReadingProgressDao.get(key)
        if (existing == null || episode > existing.lastReadEpisode) {
            webReadingProgressDao.upsert(
                WebReadingProgressEntity(
                    ncode = key,
                    lastReadEpisode = episode,
                    lastReadAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity? =
        withContext(Dispatchers.IO) { webReadingProgressDao.get(ncode.value.trim().uppercase()) }

    /** べき等ガードの純判定を切り出したもの: 抽出後のタイトル＋著者に一致する既存蔵書を返す
     *  （無ければ null）。実 PDF 抽出を挟まず単体テストできるよう addBook 本体から分離する。 */
    internal suspend fun findExistingBook(title: String, author: String): BookEntity? =
        bookDao.findByTitleAndAuthor(title, author)

    /** 内容ハッシュ照合の純判定を切り出したもの（addBook の「変換前遮断」で使う）。
     *  実 PDF 抽出を挟まず単体テストできるよう addBook 本体から分離する（title＋author 版
     *  findExistingBook と対）。同一 SHA-256 を持つ既存蔵書があれば返す（無ければ null）。 */
    internal suspend fun findExistingBookByHash(contentSha256: String): BookEntity? =
        bookDao.findByContentSha256(contentSha256)

    /**
     * 抽出例外・IO 例外をユーザー向けエラー種別に変換する。
     *
     * ネイティブ PDFBox 経路は暗号化/破損/容量不足を [com.novelreader.pdf.PdfExtractionException] の
     * サブ**型**で投げる（facade PdfBookExtractor が内部で classifyPdfError 済み）ため型で分岐する。
     * Chaquopy 版は PyException のメッセージ文字列で分類していたが、型の方が堅牢なため文字列マッチは廃止した。
     * facade を通らない例外（URI 権限喪失・出力ディレクトリ生成失敗）は BookRepository 自身が投げる
     * IOException なので、従来どおりメッセージで拾う（else 節）。
     */
    internal fun classifyError(e: Throwable): Throwable = when (e) {
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
    override suspend fun addBook(
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
                inputStream.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // ①' 内容ハッシュによる変換前遮断（F-G 恒久策）: 取込元 PDF バイト列の SHA-256 を計算し、
                // 同一内容の本が既に蔵書にあれば「重い抽出（分オーダー）を一切走らせずに」重複確定する。
                // なぜここ（抽出前・最速地点）か: 既存の title＋author 照合（④）は抽出後にしか判定できず、
                // URI が変わる同一 PDF の再選択でも毎回フル変換が走ってしまう（F-G の旧修正の穴）。
                // ハッシュはコピー済み temp を1回読み直して計算する。なぜ DigestInputStream でコピーに
                // 相乗りする単一パスにしないか: ハッシュ計算を純関数 sha256Hex に閉じてテスト可能にする方を
                // 優先したため。cacheDir 上の temp を数十MB 読み直す実コストは変換の分オーダーに対し無視できる。
                val contentSha256 = tempFile.inputStream().use { sha256Hex(it) }
                val existingByHash = findExistingBookByHash(contentSha256)
                if (existingByHash != null) {
                    // outputDir はまだ mkdirs していないので掃除不要。変換の成否が確定した（＝重複）ので
                    // 成功/重複時と同じく pending_jobs を落とし永続権限も返す（NonCancellable で保護）。
                    withContext(NonCancellable) { pendingJobMutex.withLock { settlePendingJob(pdfUri) } }
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
                    extractBook(tempFile, bookId, outputDir) { step, stepLocalPercent, phase, title ->
                        extractionScope.ensureActive()
                        onProgress(step, stepLocalPercent, phase, title)
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
                    withContext(NonCancellable) { pendingJobMutex.withLock { settlePendingJob(pdfUri) } }
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
                        val b = BookEntity(bookId, meta.title, outputDir.absolutePath, meta.author, addedAt = System.currentTimeMillis(), contentSha256 = contentSha256, ncode = ncode?.value, shioriTipIndex = shiori.tipIndex, shioriLenFrac = shiori.lenFrac)
                        bookDao.insertBook(b)
                        // 変換が確定したので永続キュー（pending_jobs）の記帳を落とす。insertBook と同じ
                        // NonCancellable 内で連続実行し、「本は登録されたのに pending が残る」→ 次回起動の
                        // リカバリが同じ本を再変換して重複登録する窓を最小化する（insertBook↔settle 間のプロセス
                        // kill 窓は DB 跨ぎの原子化が要るため残るが、数msで実用上無視できる）。pendingJobMutex は
                        // 並行する clearPendingJobs 等との pending_jobs 書き込み衝突を防ぐためで、kill 窓とは別問題。
                        pendingJobMutex.withLock { settlePendingJob(pdfUri) }
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
                    pendingJobMutex.withLock { pendingJobDao.deleteByUri(pdfUri.toString()) }
                }
                Result.failure(classifyError(e))
            },
        )
    }

    // ── 処理キューの永続化（pending_jobs）───────────────────────────────
    // enqueue 時に記帳し、変換の成否確定時に削除する。残っている行 = 強制終了
    // （OEM kill/OOM/onTimeout）で中断された未完了ジョブとして起動時リカバリが検出する。

    /** enqueue の記帳。REPLACE のため再開時の再投入でも二重行にならない。 */
    // pendingJobMutex で全消し(clearPendingJobs)と直列化する。素の IO 並行だと「追加直後に停止」で
    // この insert が全消しの後に着地し、破棄済みジョブが復活する（フィールド pendingJobMutex の why 参照）。
    override suspend fun addPendingJob(uri: String, displayName: String) = withContext(Dispatchers.IO) {
        pendingJobMutex.withLock {
            pendingJobDao.insert(PendingJobEntity(uri, displayName, System.currentTimeMillis()))
        }
    }

    /** 未完了ジョブ一覧（enqueue 順）。起動時リカバリの検出用。 */
    override suspend fun getPendingJobs(): List<PendingJobEntity> =
        withContext(Dispatchers.IO) { pendingJobDao.getAll() }

    /** 再開不能と判明したジョブの除去（権限喪失時など）。永続権限も返す。 */
    // pending_jobs 書き込みは pendingJobMutex で一律直列化する（settlePendingJob 自体はロックを持たないため
    // 呼び出し側で取る＝Mutex は非再入なので二重取得によるデッドロックを避ける設計）。
    override suspend fun removePendingJob(uri: String) = withContext(Dispatchers.IO) {
        pendingJobMutex.withLock { settlePendingJob(Uri.parse(uri)) }
    }

    /** 全ジョブの除去（ユーザーの明示停止＝「再開してほしくない」意思の反映）。 */
    override suspend fun clearPendingJobs() = withContext(Dispatchers.IO) {
        // pendingJobMutex で enqueue の記帳(addPendingJob)と直列化する。これが無いと「追加直後に停止」で
        // insert が deleteAll をすり抜けて後着し、破棄済みジョブが復活する（フィールド pendingJobMutex の why 参照）。
        pendingJobMutex.withLock {
            // deleteAll の前に各行の永続権限を返す（行を先に消すと解放対象の URI が分からなくなる）
            pendingJobDao.getAll().forEach { releasePersistedPermission(Uri.parse(it.uri)) }
            pendingJobDao.deleteAll()
        }
    }

    /** books テーブルに存在しない bookId の HTML ディレクトリを削除する（孤立HTML掃除）。
     *  強制終了（OEM kill/OOM）ではプロセスごと消えるため addBook 内 catch のクリーンアップが
     *  走らず、書きかけの novels/<bookId>/ が残り得る。DB 登録（NonCancellable の最終確定）が
     *  完了の境界なので「DB に無い = 未完了の書きかけ」と判定して安全に消せる。
     *  【前提】Service 非稼働時に呼ぶこと（処理中の本の出力ディレクトリを誤削除しないため。
     *  呼び出し側 runStartupRecoveryOnce が processingState で判定する）。 */
    // 明示 : Unit — 式本体の最終式が `?.forEach`（Unit?）のため、interface の Unit と型を一致させる。
    override suspend fun cleanOrphanHtmlDirs(): Unit = withContext(Dispatchers.IO) {
        val novelsDir = File(context.filesDir, BookEntity.NOVELS_SUBDIR)
        val validIds = bookDao.getAllBookIds().toSet()
        novelsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in validIds) {
                if (dir.deleteRecursively()) Log.i(TAG, "孤立HTMLを掃除: ${dir.name}")
                else Log.w(TAG, "孤立HTMLの削除に失敗: ${dir.absolutePath}")
            }
        }
    }

    /** pending_jobs の記帳を消し、再開用に取得した永続 URI 権限も返す。
     *  変換の成否が確定した時点（成功=Room 登録済み／失敗=エラー通知確定）で呼ぶ。 */
    private suspend fun settlePendingJob(pdfUri: Uri) {
        pendingJobDao.deleteByUri(pdfUri.toString())
        releasePersistedPermission(pdfUri)
    }

    /** takePersistableUriPermission（BookshelfViewModel.addBook）の対。永続権限は端末全体で
     *  上限があるため用が済んだら返す。未取得（プロバイダ非対応等）だと SecurityException に
     *  なるので防御する（返せなくても実害は上限消費のみ）。 */
    private fun releasePersistedPermission(pdfUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /**
     * 起動時クリーンアップ: pending_jobs にも紐付かない「孤児」の永続 URI 権限を解放する（恒久リーク回収）。
     *
     * なぜこれが必要か（root cause）: 取込失敗時は M7 の「再試行」を成立させるため、addBook の失敗経路が
     * settlePendingJob（権限解放込み）ではなく pending_jobs 行の削除のみを行い、永続 URI 権限を意図的に
     * 残す。しかし再試行 Snackbar はプロセス生存中にしか出せないため、再試行されないまま終わった失敗分の
     * 権限は「pending_jobs 行が無い＝どの経路でも解放されない」恒久リークになり、端末上限(128件)へ向けて
     * 溜まり続ける。そこで次回アプリ起動時に「pending_jobs 非紐付けの永続権限＝もう再試行され得ない失敗
     * 取込の置き土産」として回収する。
     *
     * なぜ pending_jobs 非紐付けだけで孤児と断定できるか: 永続権限を取るのは PDF 取込の1経路のみ
     * （BookshelfViewModel.addBook の takePersistableUriPermission）。books は取込元 URI を持たない
     * （スキーマに無い）ため、変換完了済みの本は元 URI の権限を二度と要さない（成功時に settle 済み）。
     * よって「まだ処理が要る URI は必ず pending_jobs 行を持つ」不変条件が成り立ち、行が無ければ孤児。
     *
     * 【誤解放しない根拠】変換中の URI を誤って解放しないこと: 本メソッドは runStartupRecoveryOnce の
     * processingState==null ガード下（Service 非稼働）でのみ呼ばれ、かつ起動直後で新規取込より前に走る。
     * 稼働中に処理される URI は enqueue 時に必ず pending_jobs 行を持つため keepUris に含まれ、解放対象外。
     * 再開対象（resumable）の URI も pending_jobs 行を持つので keepUris で保護される。
     *
     * @param keepUris 現在の pending_jobs が保持する URI 集合（この集合に含まれる権限は残す）。
     */
    override suspend fun releaseOrphanedPermissions(keepUris: Set<String>) = withContext(Dispatchers.IO) {
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        orphanedPermissionUris(persistedReadUris, keepUris).forEach { uri ->
            releasePersistedPermission(Uri.parse(uri))
        }
    }

    override suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        // なぜトランザクションか（孤児progress行の恒久残留を防ぐ）: books 行を消してから progress 行を消すまでの間に
        // プロセスが kill されると、本体が消えたのに progress だけ残る「孤児」になる。progress は本削除時にしか
        // 掃除されない（books 経由でしか辿らない）ため掃除経路が無く恒久残留する。両削除を1トランザクションに束ね、
        // どちらも commit されるか一切行われないか（原子性）にして中間状態を消す。
        runInTransaction {
            bookDao.deleteById(book.id)
            progressDao.deleteByBookId(book.id)
        }
        // 紐付いていた Web読書位置履歴も相乗り削除する（UX監査 privacy）: 本を消したのに、その本に
        // 紐付いた ncode の WebView 読書位置だけ端末へ残る穴を塞ぐ。ただし同 ncode が web_novels カード
        // として独立に棚に在るなら、その Web 読書はまだ現役なので残す（helper が参照有無で判定）。
        book.ncode?.let { cascadeDeleteWebProgressIfUnreferenced(it.trim().uppercase()) }
        // HTMLディレクトリ削除は DB 外の副作用のためトランザクション外に置く（ファイルIO は Room の
        // トランザクションでロールバックできず、失敗しても DB 削除は成立させたい＝掃除は次回起動の
        // cleanOrphanHtmlDirs が拾う）。
        if (!File(book.htmlDirPath).deleteRecursively()) {
            Log.w(TAG, "HTMLディレクトリの削除に失敗: ${book.htmlDirPath}")
        }
    }

    /** ある ncode（正規化済み）が books.ncode / web_novels のどちらからも参照されなくなっていれば、
     *  その Web読書位置履歴を削除する（相乗り削除の安全弁）。削除後の現況を books/web_novels の
     *  スナップショットで確認し、まだ参照が残るなら「続きから」に要るため履歴を残す（過剰削除の防止）。
     *  なぜ Flow.first() で snapshot を取るか: 一度きりの現況照会に十分で、BookDao/WebNovelDao へ
     *  専用 suspend クエリを足さずに済む（呼び出しは削除操作の直後のみで高頻度でない）。 */
    private suspend fun cascadeDeleteWebProgressIfUnreferenced(normalizedNcode: String) {
        val referencedByBook = bookDao.getAllBooks().first()
            .any { it.ncode?.trim()?.uppercase() == normalizedNcode }
        val referencedByCard = webNovelDao.getAll().first()
            .any { it.ncode.trim().uppercase() == normalizedNcode }
        if (!referencedByBook && !referencedByCard) {
            webReadingProgressDao.deleteByNcode(normalizedNcode)
        }
    }

    /**
     * 起動時クリーンアップ: どの棚項目（books.ncode / web_novels）にも紐付かない「孤児」の
     * web_reading_progress 行を回収する（UX監査 privacy・削除の完全性）。
     *
     * なぜ相乗り削除だけでは足りないか: 相乗り削除は removeWebNovel/deleteBook の直後にしか走らないため、
     * その途中でプロセスが kill された場合や、過去バージョンで削除経路が無かった頃に溜まった履歴は残る。
     * cleanOrphanHtmlDirs（孤立HTML掃除）と同じ「起動時に不変条件を回復する」掃除でこれを完全化する。
     *
     * @return 削除した孤児行数（呼び出し側のログ用）。
     */
    override suspend fun pruneOrphanWebReadingProgress(): Int = withContext(Dispatchers.IO) {
        val keep = buildSet {
            bookDao.getAllBooks().first().forEach { b -> b.ncode?.let { add(it.trim().uppercase()) } }
            webNovelDao.getAll().first().forEach { add(it.ncode.trim().uppercase()) }
        }
        val all = webReadingProgressDao.getAll().first().map { it.ncode }.toSet()
        val orphans = orphanedWebProgressNcodes(all, keep)
        orphans.forEach { webReadingProgressDao.deleteByNcode(it) }
        orphans.size
    }

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。ユーザー確定操作からのみ呼ぶ。
    override suspend fun linkNcode(bookId: BookId, ncode: Ncode?) = withContext(Dispatchers.IO) {
        // 境界変換点: Room(BookDao) の bookId/ncode 列は String のまま＝ここで .value へほどく。
        // null（解除）は null のまま渡す。
        bookDao.updateNcode(bookId.value, ncode?.value)
    }

    // 永続化境界: DAO は String 引数のため bookId.value でほどく（戻り値の lastReadFilename は
    // ナビ経路の文字列組み立て等でそのまま使うため String のまま返す＝型付けは識別子引数に限定）。
    override suspend fun getLastRead(bookId: BookId): String? =
        withContext(Dispatchers.IO) { progressDao.getLastRead(bookId.value) }

    override suspend fun getProgress(bookId: BookId): ProgressEntity? =
        withContext(Dispatchers.IO) { progressDao.getProgress(bookId.value) }

    // 読了（最終章の末尾到達）の記録。reachedEnd 列だけを立て、位置には触れない（sticky）。
    override suspend fun markReachedEnd(bookId: BookId) = withContext(Dispatchers.IO) {
        // 永続化境界: Room の bookId 列は String のため .value でほどく。
        progressDao.markReachedEnd(bookId.value)
    }

    // 章を切り替えたときの進捗保存。スクロール位置は 0 にリセットする
    // （別の章へ移ったので前章のスクロール位置は引き継がない）。
    // lastReadAt を書き込み時刻でスタンプし、本棚の最近読書順ソートに使う。
    // なぜ insertIfAbsent＋updatePosition の2手か: 全列 REPLACE だと保存のたびに reachedEnd が
    // 既定へ戻り『了』印が消える。位置更新は reachedEnd を touch しない updatePosition に閉じ、
    // 行が無い初回だけ insertIfAbsent で作る（ProgressDao の why 参照）。
    override suspend fun saveProgress(bookId: BookId, filename: ChapterFilename) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 永続化境界: Room(ProgressEntity) は String 列のため .value でほどいて渡す。
        progressDao.insertIfAbsent(ProgressEntity(bookId.value, filename.value, lastReadAt = now))
        progressDao.updatePosition(bookId.value, filename.value, 0, 0, now)
    }

    // 章内スクロール位置の保存。lastReadFilename も一緒に書き込むことで
    // 「どの章のどの位置か」を1行で表現する。
    // lastReadAt も毎回スタンプ（単一チャネル統合の最終1書き込みに自然に乗る）。
    // saveProgress と同じ2手（reachedEnd を消さないための insertIfAbsent＋updatePosition）。
    override suspend fun saveScrollPosition(
        bookId: BookId,
        filename: ChapterFilename,
        scrollIndex: Int,
        scrollOffset: Int,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 永続化境界: Room(ProgressEntity) は String 列のため .value でほどいて渡す。
        progressDao.insertIfAbsent(
            ProgressEntity(bookId.value, filename.value, scrollIndex, scrollOffset, lastReadAt = now)
        )
        progressDao.updatePosition(bookId.value, filename.value, scrollIndex, scrollOffset, now)
    }

    companion object {
        private const val TAG = "BookRepository"
    }
}

/**
 * 起動時に解放すべき「孤児」の永続 URI 権限を判定する純関数（テスト対象）。
 * 持続化された読み取り権限のうち pending_jobs 非紐付けのもの＝もう再試行され得ない失敗取込の
 * 置き土産を差集合で選ぶ。UI・contentResolver 非依存で回収ロジックの中核を単体テストするため分離する
 * （releaseOrphanedPermissions が persistedUriPermissions 取得と実解放の副作用を担い、判定はここ）。
 */
internal fun orphanedPermissionUris(
    persistedReadUris: Set<String>,
    keepUris: Set<String>,
): Set<String> = persistedReadUris - keepUris

/**
 * どの棚項目にも紐付かない「孤児」の web_reading_progress を判定する純関数（テスト対象）。
 * 全 ncode から、books.ncode / web_novels に生きている ncode（keep）を差し引く。
 * orphanedPermissionUris と同じ流儀で、掃除の中核ロジックを contentResolver/Room 非依存で単体テストする
 * （pruneOrphanWebReadingProgress が snapshot 取得と実削除の副作用を担い、判定はここ）。
 */
internal fun orphanedWebProgressNcodes(
    allNcodes: Set<String>,
    keepNcodes: Set<String>,
): Set<String> = allNcodes - keepNcodes

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
