package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    // なぜ Flow<List<LabelEntity>> かつ createdAt の昇順なのか: 本棚画面の絞り込み用チップ行において、ユーザーが作成した順序（時系列）のまま一貫して安定表示させ、UI 上のガタつきを防ぐため。
    @Query("SELECT * FROM labels ORDER BY createdAt ASC")
    fun getAll(): Flow<List<LabelEntity>>

    // なぜ IGNORE で Long を返すのか: 同名ラベルの重複登録を防ぐため name に unique index が張られており、競合時は挿入せず -1 を返すことで、呼び出し側（ViewModel/UI）が「既存のラベルを選択し直す」処理を可能にするため。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: LabelEntity): Long

    // なぜラベルID指定の削除なのか: ユーザーが不要になったラベルを選択して個別に削除できるようにするため（本との紐付け junction のクリーンアップは BookLabelDao で別途行う）。
    @Query("DELETE FROM labels WHERE id = :labelId")
    suspend fun delete(labelId: String)

    // なぜ name 逆引きが要るのか: 「作成と同時にその本へ付与」で、IGNORE 挿入後（新規/同名既存どちらでも）
    // 実際に紐付けるべき labelId を name から一意に解決するため（name は unique index）。
    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): LabelEntity?
}
