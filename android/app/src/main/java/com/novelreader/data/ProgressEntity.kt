package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val lastReadFilename: String,
)
