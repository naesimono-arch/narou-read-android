package com.novelreader.repository

import android.net.Uri
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.flow.Flow

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

    /** (b) Web由来・未取込カード: 本棚に置いた Web 作品（未取込）の一覧（addedAt 降順）。 */
    val webNovels: Flow<List<WebNovelEntity>>

    /** Web 作品を本棚に置く。同一 ncode は最新情報で上書き（REPLACE）。
     *  ncode は NcodeLinkSheet の紐付け保存と同じ `.trim().uppercase()` 正規化で渡すこと
     *  （表記ゆれで同一作品が二重カード化するのを防ぐ）。 */
    suspend fun putWebNovel(novel: WebNovelEntity)

    /** Web 作品を本棚から外す（取込完了時の昇格削除にも使う）。 */
    suspend fun removeWebNovel(ncode: Ncode)

    /** 機能②: なろうWebView読書の読書位置一覧（ncode→最後に開いた話）。本棚カード・作品詳細の「続きから読む」表示に使う。
     *  なぜ web_novels と別 Flow か: 検索経由で開いただけの未配置作品も記録対象のため（WebReadingProgressEntity の why）。 */
    val webReadingProgress: Flow<List<WebReadingProgressEntity>>

    /** WebView 読書で話ページ(.../N/)に到達したときに読書位置を記録する（last-wins 上書き）。
     *  ncode は putWebNovel と同じ `.trim().uppercase()` 正規化で保存すること（表記ゆれで別作品扱いにしない）。 */
    suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int)

    /** 指定作品の現在の読書位置（未記録なら null）。WebReader 起動時の1件照会用。 */
    suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity?

    /** addBook の取込結果。同一PDFの二重取込（UX監査 F-G 公理3べき等性）を呼び出し側で
     *  区別できるよう、新規登録と重複スキップを型で分ける（Service の通知文面を分岐させる）。 */
    sealed interface AddBookResult {
        /** 新規に蔵書登録した本。 */
        data class Added(val book: BookEntity) : AddBookResult
        /** 既に同一の本が蔵書済みのため登録をスキップした（変換成果は破棄済み）。既存の本を返す。 */
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

    /** 起動時クリーンアップ: pending_jobs 非紐付けの「孤児」永続 URI 権限を解放する（恒久リーク回収）。 */
    suspend fun releaseOrphanedPermissions(keepUris: Set<String>)

    /** 起動時クリーンアップ: どの棚項目（books.ncode / web_novels）にも紐付かない孤児の
     *  web_reading_progress 行を回収する（UX監査 privacy・削除の完全性）。@return 削除した行数。 */
    suspend fun pruneOrphanWebReadingProgress(): Int

    suspend fun deleteBook(book: BookEntity)

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
