package com.novelreader.repository

import android.net.Uri
import com.novelreader.data.BookEntity
import com.novelreader.data.NewEpisodeMarkEntity
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.flow.Flow

/**
 * 本削除時の「取込元PDF本体も削除する」の結末。呼び出し側（BookshelfViewModel）は Failed の件数を集計して
 * Snackbar 通知するのに使う。NoSource/NotRequested は通知不要（正常系）。
 */
enum class SourceDeleteOutcome {
    /** 削除可能な取込元を持たない本（sourceUri==null＝旧蔵書・FileProvider 取込・権限非保持）。何もしない。 */
    NoSource,
    /** ユーザーが「取込元も削除」を選ばなかった（sourceUri はあるが本削除のみ）。取込元PDFは残す。 */
    NotRequested,
    /** 取込元PDF本体の削除に成功した。 */
    Deleted,
    /** 取込元PDF本体の削除を試みたが失敗した（既に移動/削除済み・権限失効・削除非対応プロバイダ等）。本削除は成立。 */
    Failed,
}

/**
 * 書籍データアクセス層の抽象。蔵書（books）・読書進捗（progress）・処理キュー（pending_jobs）への
 * アクセスと、PDF 取込（addBook）の窓口を定義する。
 *
 * なぜ interface か: 本番実装 [DefaultBookRepository] は AppDatabase.getDatabase（static シングルトン）や
 * PDFBox/Android コンテキストに依存し JVM 単体テストで直接生成できない。利用側（Application・ViewModel・
 * Service）をこの interface 型参照にしておくことで、テストではインメモリの FakeBookRepository へ差し替えて
 * ViewModel を検証できる（挙動は従来 BookRepository のまま不変＝純リファクタでの抽出）。
 */
interface BookRepository {

    val allBooks: Flow<List<BookEntity>>
    val allProgress: Flow<List<ProgressEntity>>

    /** U1 新着チェックの基準値（new_episode_marks 全行）。キーは正規化 ncode か "web:<bookId>"
     *  （[com.novelreader.narou.webNewEpisodeMarkKey] が正本）。
     *  なぜ UI から購読するのか: Web 蔵書の新着は Worker のサイト再フェッチでしか観測できず、この行が
     *  端末に残る唯一の観測結果＝本棚の「続きあり」バッジの Web 側データ源になるため（2026-07-31）。
     *  書き込みは Worker（AppDatabase の DAO 直参照）が持ち、本 interface は読み取りのみを公開する。 */
    val newEpisodeMarks: Flow<List<NewEpisodeMarkEntity>>

    /** (b) Web由来・未取込カード: 本棚に置いた Web 作品（未取込）の一覧（addedAt 降順）。 */
    val webNovels: Flow<List<WebNovelEntity>>

    /** Web 作品を本棚に置く。同一 ncode は最新情報で上書き（REPLACE）。
     *  ncode は保存キー正規化＝[com.novelreader.narou.model.Ncode.storageKey]（trim+大文字）で渡すこと
     *  （表記ゆれで同一作品が二重カード化するのを防ぐ。正規化の正本は Ncode のアクセサ＝手書き正規化を書かない）。 */
    suspend fun putWebNovel(novel: WebNovelEntity)

    /** Web 作品を本棚から外す（取込完了時の昇格削除にも使う）。 */
    suspend fun removeWebNovel(ncode: Ncode)

    /** 機能②: なろうWebView読書の読書位置一覧（ncode→最後に開いた話）。本棚カード・作品詳細の「続きから読む」表示に使う。
     *  なぜ web_novels と別 Flow か: 検索経由で開いただけの未配置作品も記録対象のため（WebReadingProgressEntity の why）。 */
    val webReadingProgress: Flow<List<WebReadingProgressEntity>>

    /** WebView 読書で話ページ(.../N/)に到達したときに読書位置を記録する（last-wins 上書き）。
     *  ncode は putWebNovel と同じ [com.novelreader.narou.model.Ncode.storageKey] 正規化で保存すること
     *  （表記ゆれで別作品扱いにしない。正規化の正本は Ncode のアクセサ）。 */
    suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int)

    /** 指定作品の現在の読書位置（未記録なら null）。WebReader 起動時の1件照会用。 */
    suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity?

    /** addBook の取込結果。同一PDFの二重取込（UX監査 F-G 公理3べき等性）を呼び出し側で
     *  区別できるよう、新規登録と重複スキップを型で分ける（Service の通知文面を分岐させる）。 */
    sealed interface AddBookResult {
        /** 新規に蔵書登録した本。restored=true は「既存行を保持したまま本文だけ再生成した」復元
         *  （本文欠落→再取込・2026-07-29 案B/C）。新サブタイプにしない理由: 既存の網羅 when
         *  （PdfProcessingService 等）を壊さず、復元は「本が使える状態になった」点で Added と同義のため。 */
        data class Added(val book: BookEntity, val restored: Boolean = false) : AddBookResult
        /** 既に同一の本が蔵書済みのため登録をスキップした（変換成果は破棄済み）。既存の本を返す。
         *  本文実体が欠落している既存本はこの分岐に入らず、復元（Added(restored=true)）へ回る。 */
        data class Duplicate(val existing: BookEntity) : AddBookResult
    }

    /** PDFをキャッシュにコピーし、ネイティブ(PDFBox)抽出でHTML生成後にRoomへ登録する。
     *  ncode: なろう縦書きPDF取り込み（ADR 0011）で、取り込む本に紐付ける Nコード。通常のファイル選択
     *  取り込みでは null（従来どおり紐付けはユーザーが後から NcodeLinkSheet で行う）。新規登録時のみ
     *  BookEntity.ncode へ書き込む（重複スキップ時は既存本を上書きしない＝下記実装コメント参照）。 */
    suspend fun addBook(
        pdfUri: Uri,
        ncode: Ncode? = null,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit = { _, _, _, _ -> },
    ): Result<AddBookResult>

    /** Web小説を取り込む（汎用Web小説DL基盤・P3）。任意の作品/話ページ URL を渡すと、対応サイトの
     *  アダプタで目次→各章本文を取得し、PDF 蔵書と同契約の index.html/chap_N.html を生成して Room へ登録する。
     *
     *  対応サイト以外（規約で自前DL不可の Blocked／未整備の Unsupported）は [Result.failure]（IllegalArgumentException・
     *  メッセージに種別）で返す。**UI 側は呼び出し前に [com.novelreader.scrape.SiteAdapterRegistry] を直接引いて
     *  Blocked/Unsupported を出し分ける前提**（公式サイト導線への誘導）ゆえ、repository では失敗で足りる。
     *
     *  同一作品 URL の再取込は [AddBookResult.Duplicate]（重い取得の前に sourceUrl で弾く＝PDF の hash 遮断と同位置）。
     *
     *  @param onProgress 「章 i/N 取得中」粒度の進捗（第1引数＝1始まりの章番号、第2引数＝表示文言）。null で無効。 */
    suspend fun addWebBook(
        inputUrl: String,
        onProgress: ((Int, String) -> Unit)? = null,
    ): Result<AddBookResult>

    /** enqueue の記帳。REPLACE のため再開時の再投入でも二重行にならない。 */
    suspend fun addPendingJob(uri: String, displayName: String)

    /** 未完了ジョブ一覧（enqueue 順）。起動時リカバリの検出用。 */
    suspend fun getPendingJobs(): List<PendingJobEntity>

    /** 再開不能と判明したジョブの除去（権限喪失時など）。永続権限も返す。 */
    suspend fun removePendingJob(uri: String)

    /** 全ジョブの除去（ユーザーの明示停止＝「再開してほしくない」意思の反映）。 */
    suspend fun clearPendingJobs()

    /** books テーブルに存在しない bookId の HTML ディレクトリを削除する（孤立HTML掃除）。 */
    suspend fun cleanOrphanHtmlDirs()

    /** 起動時クリーンアップ: 孤児（pending_jobs にも books.sourceUri にも紐付かない）永続 URI 権限を
     *  解放する（恒久リーク回収）。keepUris には「保持すべき URI」＝現在の pending_jobs URI ∪ books.sourceUri
     *  を渡すこと（呼び出し側で union）。 */
    suspend fun releaseOrphanedPermissions(keepUris: Set<String>)

    /** 取込元 URI を保持する（＝削除可能な取込元PDFを持つ）全蔵書の sourceUri 集合。
     *  releaseOrphanedPermissions の keepUris を組み立てる呼び出し側（NovelReaderApplication）が使う。 */
    suspend fun getPersistedSourceUris(): Set<String>

    /** 起動時クリーンアップ: どの棚項目（books.ncode / web_novels）にも紐付かない孤児の
     *  web_reading_progress 行を回収する（UX監査 privacy・削除の完全性）。@return 削除した行数。 */
    suspend fun pruneOrphanWebReadingProgress(): Int

    /** 起動時クリーンアップ: どの蔵書（books.ncode）にも対応せず pending_jobs からも参照されない
     *  取込時 cache PDF（cache/pdf_import/）を回収する（欠落本復旧 AutoCachePdf の資源は残す）。
     *  @return 削除したファイル数。 */
    suspend fun sweepOrphanNarouPdfCache(): Int

    /** 本を蔵書から削除する（DB行・進捗・本文HTML・紐付き Web 読書位置を掃除）。
     *  deleteSource=true かつ book.sourceUri!=null のとき、取込元 PDF 本体（SAF ドキュメント）も削除する。
     *  本削除に伴い book.sourceUri の永続 URI 権限は削除成否に関わらず解放する（本が消えれば保持不要のため）。
     *  @return 取込元PDF削除の結末（呼び出し側が失敗を Snackbar 通知するのに使う。[SourceDeleteOutcome] 参照）。 */
    suspend fun deleteBook(book: BookEntity, deleteSource: Boolean = false): SourceDeleteOutcome

    /** PDF↔Web継続読書: なろう作品との紐付け（null で解除）。 */
    suspend fun linkNcode(bookId: BookId, ncode: Ncode?)

    suspend fun getLastRead(bookId: BookId): String?

    suspend fun getProgress(bookId: BookId): ProgressEntity?

    /** 読了（最終章の末尾到達）を記録する（ssot Major 2026-07-12）。sticky＝一度立てたら読み直しでも維持。
     *  読書位置（lastReadFilename/scroll）には一切触れない（読了フラグ列だけを立てる）。 */
    suspend fun markReachedEnd(bookId: BookId)

    /** 章移動時の進捗保存（スクロール位置は 0,0＝章先頭にリセット）。reachedEnd は保持する。 */
    suspend fun saveProgress(bookId: BookId, filename: ChapterFilename)

    /** 章内スクロール位置の保存。 */
    suspend fun saveScrollPosition(
        bookId: BookId,
        filename: ChapterFilename,
        scrollIndex: Int,
        scrollOffset: Int,
    )
}
