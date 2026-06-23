package com.novelreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress")
    fun getAllProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT lastReadFilename FROM progress WHERE bookId = :bookId")
    suspend fun getLastRead(bookId: String): String?

    // 章ファイル名とスクロール位置をまとめて取得する（読書再開時の復元用）
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
