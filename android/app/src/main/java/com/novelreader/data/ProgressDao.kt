package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress")
    fun getAllProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT lastReadFilename FROM progress WHERE bookId = :bookId")
    suspend fun getLastRead(bookId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
