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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)
}
