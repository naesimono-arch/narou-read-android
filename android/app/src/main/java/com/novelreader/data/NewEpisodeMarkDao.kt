package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewEpisodeMarkDao {
    // なぜ suspend 版を残すのか: Worker（NewEpisodeCheckWorker）は1回の走行の中で基準値を一括読み→判定→upsert
    // するだけで、継続監視は要らない（購読したままだと自分の upsert で再発行を受けるだけ無駄）。
    @Query("SELECT * FROM new_episode_marks")
    suspend fun getAll(): List<NewEpisodeMarkEntity>

    // なぜ Flow 版が要るのか（2026-07-31 本棚「続きあり」バッジの Web 反映）: Web 蔵書の新着は
    // なろうのような実時間 API 照会ではなく、Worker が書いたこの基準値テーブルだけが唯一の観測結果。
    // 本棚が開いている最中に Worker が走って値が増えることがあるため、UI 側は購読で追従する必要がある。
    @Query("SELECT * FROM new_episode_marks")
    fun observeAll(): Flow<List<NewEpisodeMarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<NewEpisodeMarkEntity>)

    // なぜ pruneExcept が必要なのか: 紐付けが解除されたり、本棚から削除されたりした古い作品の通知用基準値がDB内にゴミとして残り続けるのを防ぐため。
    @Query("DELETE FROM new_episode_marks WHERE ncode NOT IN (:activeNcodes)")
    suspend fun pruneExcept(activeNcodes: List<String>)
}
