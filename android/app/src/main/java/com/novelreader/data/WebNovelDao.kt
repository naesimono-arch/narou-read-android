package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WebNovelDao {
    // なぜ Flow<List> か: UI側で「Web由来・未取込カード」の追加や削除をリアルタイムに本棚画面に反映させるため。
    // なぜ addedAt の降順か: ユーザーが直近に追加した作品を本棚の上位に表示させ、アクセスしやすくするため。
    @Query("SELECT * FROM web_novels ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WebNovelEntity>>

    // なぜ REPLACE か: 作品情報（話数やタイトルなど）が更新された場合に、同じ ncode の既存レコードを最新状態に上書きするため。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(webNovel: WebNovelEntity)

    // なぜ ncode 指定の削除か: ユーザーが本棚から特定の未取込カードを個別に削除できるようにするため。
    @Query("DELETE FROM web_novels WHERE ncode = :ncode")
    suspend fun deleteByNcode(ncode: String)
}
