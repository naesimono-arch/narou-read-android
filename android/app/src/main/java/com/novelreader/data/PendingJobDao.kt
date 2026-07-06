package com.novelreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingJobDao {

    /** enqueue 順（＝再開時の再投入順）で全件返す */
    @Query("SELECT * FROM pending_jobs ORDER BY enqueuedAt ASC")
    suspend fun getAll(): List<PendingJobEntity>

    /** REPLACE: 同一 URI の重複 enqueue・リカバリ再投入時の再記帳を1行に畳む */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: PendingJobEntity)

    @Query("DELETE FROM pending_jobs WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM pending_jobs")
    suspend fun deleteAll()
}
