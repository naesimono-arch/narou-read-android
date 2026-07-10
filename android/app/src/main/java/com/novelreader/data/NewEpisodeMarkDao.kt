package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NewEpisodeMarkDao {
    // なぜ Flow ではなく suspend なのか: バックグラウンドの Worker 等から実行時に都度一括取得するだけでよく、UIへのリアルタイム通知や継続監視は不要なため。
    @Query("SELECT * FROM new_episode_marks")
    suspend fun getAll(): List<NewEpisodeMarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<NewEpisodeMarkEntity>)

    // なぜ pruneExcept が必要なのか: 紐付けが解除されたり、本棚から削除されたりした古い作品の通知用基準値がDB内にゴミとして残り続けるのを防ぐため。
    @Query("DELETE FROM new_episode_marks WHERE ncode NOT IN (:activeNcodes)")
    suspend fun pruneExcept(activeNcodes: List<String>)
}
