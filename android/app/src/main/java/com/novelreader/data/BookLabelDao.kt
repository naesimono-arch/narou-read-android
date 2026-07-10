package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookLabelDao {
    // なぜ Flow で全量を取得するのか: 本棚絞り込みの処理において、メモリ上ですべての本×ラベルの紐付けマップを保持するのに十分なデータ規模であり、付与・解除のたびにリアルタイムで絞り込みを再計算しやすくするため。
    @Query("SELECT * FROM book_labels")
    fun getAll(): Flow<List<BookLabelEntity>>

    // なぜ REPLACE なのか: 本に対して同じラベルが再付与された場合に、主キーが衝突するため既存レコードを上書きして安全に更新するため。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookLabel: BookLabelEntity)

    // なぜ特定の組による削除なのか: ユーザーが特定の本から特定のラベルを個別に外す（紐付け解除）ことができるようにするため。
    @Query("DELETE FROM book_labels WHERE bookId = :bookId AND labelId = :labelId")
    suspend fun delete(bookId: String, labelId: String)

    // なぜラベル削除時の junction 掃除が必要なのか: 外部キー制約（ON DELETE CASCADE）を設定していないため、ラベル（labels）が削除されたときに、この中間テーブルに残る不要になった紐付けゴミレコードをアプリ層で手動清掃するため。
    @Query("DELETE FROM book_labels WHERE labelId = :labelId")
    suspend fun deleteForLabel(labelId: String)

    // なぜ本削除時の junction 掃除が必要なのか: 外部キー制約（ON DELETE CASCADE）を設定していないため、本（books）が削除されたときに、この中間テーブルに残る不要になった紐付けゴミレコードをアプリ層で手動清掃するため。
    @Query("DELETE FROM book_labels WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
