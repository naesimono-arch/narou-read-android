package com.novelreader.repository

import android.net.Uri
import com.novelreader.data.BookEntity
import com.novelreader.data.BookLabelEntity
import com.novelreader.data.LabelEntity
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
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

    /** U2 ラベル整理: ラベル一覧（createdAt 昇順＝チップ行の安定表示順）。 */
    val labels: Flow<List<LabelEntity>>

    /** U2 ラベル整理: 本↔ラベルの付与全量。呼び出し側で bookId→labelId 集合の Map に畳む
     *  （規模が小さく全量 Flow で足りる＝BookLabelDao.getAll の why 参照）。 */
    val bookLabels: Flow<List<BookLabelEntity>>

    /** ラベルを新規作成する。名前は trim して保存。同名が既に在る場合は作成しない
     *  （labels.name の unique index＋IGNORE）。
     *  assignToBookId 非 null なら**その本へ即付与**する（同名既存でも付与は行う）。
     *  なぜ: 付与シートは本の⋮/長押しから開く＝「この本にこのラベルを付けたい」が作成の動機のため、
     *  作成→手動チェックの2手に分けない（2026-07-10 ユーザー要望）。 */
    suspend fun createLabel(name: String, assignToBookId: String? = null)

    /** ラベルを削除する。book_labels の紐付けも同時に掃除する（FK なし設計のアプリ層クリーンアップ）。 */
    suspend fun deleteLabel(labelId: String)

    /** 本へのラベル付与/解除。assigned=true で付与・false で解除。 */
    suspend fun setBookLabel(bookId: String, labelId: String, assigned: Boolean)

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

    suspend fun deleteBook(book: BookEntity)

    /** PDF↔Web継続読書: なろう作品との紐付け（null で解除）。 */
    suspend fun linkNcode(bookId: String, ncode: Ncode?)

    suspend fun getLastRead(bookId: String): String?

    suspend fun getProgress(bookId: String): ProgressEntity?

    /** 章移動時の進捗保存（スクロール位置は 0,0＝章先頭にリセット）。 */
    suspend fun saveProgress(bookId: String, filename: String)

    /** 章内スクロール位置の保存。 */
    suspend fun saveScrollPosition(
        bookId: String,
        filename: String,
        scrollIndex: Int,
        scrollOffset: Int,
    )
}
