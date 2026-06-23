package com.novelreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val lastReadFilename: String,
    // 最後に読んでいた章（lastReadFilename）内のスクロール位置。
    // LazyListState の firstVisibleItemIndex / ScrollOffset に対応する。
    // なぜ default 0 か: 既存行の Migration 補完値であり、未スクロール（章先頭）を表す。
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)
