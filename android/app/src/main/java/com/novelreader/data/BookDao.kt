package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // 最近の活動順に並べる: 既読は progress.lastReadAt、未読は books.addedAt で評価し、
    // その大きい方の降順。どちらも 0（移行直後の既存行）なら同点となりタイトル昇順で安定化。
    @Query(
        "SELECT b.* FROM books b " +
        "LEFT JOIN progress p ON b.id = p.bookId " +
        "ORDER BY MAX(b.addedAt, COALESCE(p.lastReadAt, 0)) DESC, b.title ASC"
    )
    fun getAllBooks(): Flow<List<BookEntity>>

    /** 孤立HTML掃除（BookRepository.cleanOrphanHtmlDirs）の突合用。
     *  Flow の getAllBooks と違い一回きりのスナップショットで足りるため suspend で返す。 */
    @Query("SELECT id FROM books")
    suspend fun getAllBookIds(): List<String>

    /** 同一PDF二重取込のべき等ガード（UX監査 F-G 公理3）用の既存蔵書照合。
     *  なぜ title＋author か: books は取込元の content URI もファイルサイズも持たない
     *  （スキーマに無い）ため、抽出後に必ず得られる安定属性の組で「同じ本が既にあるか」を
     *  判定する。完全一致 1 件で足りるため LIMIT 1。 */
    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findByTitleAndAuthor(title: String, author: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。
    // なぜ部分 UPDATE か: insertBook(REPLACE) で全列上書きすると、読書中に保持している
    // 古い BookEntity スナップショットで他列（addedAt 等）を巻き戻すリスクがあるため。
    @Query("UPDATE books SET ncode = :ncode WHERE id = :bookId")
    suspend fun updateNcode(bookId: String, ncode: String?)
}
